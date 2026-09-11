// identity 链路的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用(Flyway 已建表并灌入种子数据)。
// 用法:node scripts/smoke-identity.mjs   (可用 SKY_BASE_URL 覆盖地址)

const BASE = process.env.SKY_BASE_URL ?? 'http://localhost:8080';

async function call(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json = null;
  if (text) {
    try { json = JSON.parse(text); } catch { json = text; }
  }
  return { status: res.status, json, traceId: res.headers.get('x-trace-id') };
}

const results = [];
function check(name, ok, detail) {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `   [${detail}]` : ''}`);
}

// ---- 1. 登录 ----
const login = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
check('员工登录返回 200 与令牌对',
  login.status === 200 && !!login.json?.accessToken && !!login.json?.refreshToken,
  `status=${login.status} tokenType=${login.json?.tokenType} expiresIn=${login.json?.expiresIn}`);
const adminToken = login.json?.accessToken;

// ---- 2. 当前员工 ----
const me = await call('GET', '/api/v1/admin/auth/me', { token: adminToken });
check('GET /auth/me 返回 admin/ADMIN 且体中没有密码散列',
  me.status === 200 && me.json?.username === 'admin' && me.json?.role === 'ADMIN'
  && !Object.keys(me.json ?? {}).some((k) => k.toLowerCase().includes('password')),
  JSON.stringify(me.json));

// ---- 3. 密码错误不泄露账号是否存在 ----
const badPassword = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: 'definitely-wrong' },
});
const noSuchUser = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'nobody-here', password: 'definitely-wrong' },
});
check('密码错误与账号不存在返回同一个错误码',
  badPassword.status === 401 && badPassword.json?.code === 'AUTH_BAD_CREDENTIALS'
  && noSuchUser.json?.code === 'AUTH_BAD_CREDENTIALS',
  `${badPassword.status}/${badPassword.json?.code} vs ${noSuchUser.status}/${noSuchUser.json?.code}`);

// ---- 4. 排序白名单 ----
const badSort = await call('GET', '/api/v1/admin/employees?sort=passwordHash,asc', { token: adminToken });
check('排序字段不在白名单返回 400 COMMON_SORT_FIELD_NOT_ALLOWED',
  badSort.status === 400 && badSort.json?.code === 'COMMON_SORT_FIELD_NOT_ALLOWED',
  `${badSort.status} ${badSort.json?.code}`);

// ---- 5. 员工分页 ----
const pageResult = await call('GET', '/api/v1/admin/employees?page=1&pageSize=10', { token: adminToken });
check('员工分页返回 2 条种子员工',
  pageResult.status === 200 && pageResult.json?.total === 2 && Array.isArray(pageResult.json?.records),
  `total=${pageResult.json?.total} records=${pageResult.json?.records?.length}`);

// ---- 6. 分页超限被钳制而不是报错 ----
const clamped = await call('GET', '/api/v1/admin/employees?page=1&pageSize=9999', { token: adminToken });
check('pageSize 超限被钳制为 100 而非 400',
  clamped.status === 200 && clamped.json?.pageSize === 100,
  `status=${clamped.status} pageSize=${clamped.json?.pageSize}`);

// ---- 7. 顾客微信登录(mock) ----
const customerLogin = await call('POST', '/api/v1/customer/auth/wechat-login', {
  body: { code: 'smoke-code-1' },
});
check('顾客微信登录返回令牌', customerLogin.status === 200 && !!customerLogin.json?.accessToken,
  `status=${customerLogin.status}`);
const customerToken = customerLogin.json?.accessToken;

// ---- 8. 同一 code 复用同一个顾客 ----
const customerLoginAgain = await call('POST', '/api/v1/customer/auth/wechat-login', {
  body: { code: 'smoke-code-1' },
});
const profileA = await call('GET', '/api/v1/customer/profile', { token: customerToken });
const profileB = await call('GET', '/api/v1/customer/profile', { token: customerLoginAgain.json?.accessToken });
check('同一 code 稳定映射到同一个顾客',
  profileA.json?.id != null && profileA.json?.id === profileB.json?.id,
  `id=${profileA.json?.id} vs ${profileB.json?.id}`);

// ---- 9. 受众隔离 ----
const mismatch = await call('GET', '/api/v1/admin/employees', { token: customerToken });
check('顾客令牌打管理端接口返回 403 AUTH_AUDIENCE_MISMATCH',
  mismatch.status === 403 && mismatch.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${mismatch.status} ${mismatch.json?.code}`);

// ---- 10. 未认证 ----
const noToken = await call('GET', '/api/v1/admin/employees');
check('无令牌返回 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 11. traceId 贯通 ----
check('错误响应的 traceId 与 X-Trace-Id 响应头一致',
  !!noToken.traceId && noToken.traceId === noToken.json?.traceId, noToken.traceId);

// ---- 12. 自我保护 ----
const adminId = me.json?.id;
const selfDisable = await call('PATCH', `/api/v1/admin/employees/${adminId}/status`, {
  token: adminToken, body: { status: 0 },
});
check('禁用自己返回 422 EMPLOYEE_SELF_DISABLE',
  selfDisable.status === 422 && selfDisable.json?.code === 'EMPLOYEE_SELF_DISABLE',
  `${selfDisable.status} ${selfDisable.json?.code}`);

const selfDemote = await call('PUT', `/api/v1/admin/employees/${adminId}`, {
  token: adminToken, body: { name: '管理员', role: 'STAFF', status: 1 },
});
check('把自己降级为 STAFF 返回 422 EMPLOYEE_SELF_ROLE_CHANGE',
  selfDemote.status === 422 && selfDemote.json?.code === 'EMPLOYEE_SELF_ROLE_CHANGE',
  `${selfDemote.status} ${selfDemote.json?.code}`);

// ---- 13. 校验与冲突 ----
const duplicate = await call('POST', '/api/v1/admin/employees', {
  token: adminToken,
  body: { username: 'admin', password: 'abc123456', name: '重复', role: 'STAFF' },
});
check('用户名重复返回 409 EMPLOYEE_USERNAME_TAKEN',
  duplicate.status === 409 && duplicate.json?.code === 'EMPLOYEE_USERNAME_TAKEN',
  `${duplicate.status} ${duplicate.json?.code}`);

const invalidBody = await call('POST', '/api/v1/admin/employees', {
  token: adminToken, body: { username: '1bad', password: '123', name: '', role: 'STAFF' },
});
check('请求体校验失败返回 400 且带字段级 details',
  invalidBody.status === 400 && invalidBody.json?.code === 'COMMON_VALIDATION_FAILED'
  && (invalidBody.json?.details?.length ?? 0) > 0,
  `details=${JSON.stringify(invalidBody.json?.details)}`);

const invalidStatus = await call('PATCH', `/api/v1/admin/employees/${adminId}/status`, {
  token: adminToken, body: { status: 7 },
});
check('非法 status 值返回 400', invalidStatus.status === 400, `${invalidStatus.status} ${invalidStatus.json?.code}`);

// ---- 14. refresh 旋转 ----
const refreshed = await call('POST', '/api/v1/admin/auth/refresh', {
  body: { refreshToken: login.json?.refreshToken },
});
check('刷新令牌返回新的令牌对', refreshed.status === 200 && !!refreshed.json?.accessToken,
  `status=${refreshed.status}`);
const reuseOld = await call('POST', '/api/v1/admin/auth/refresh', {
  body: { refreshToken: login.json?.refreshToken },
});
check('旧 refresh token 旋转后立即失效', reuseOld.status === 401,
  `${reuseOld.status} ${reuseOld.json?.code}`);

// ---- 15. 登出 ----
const logout = await call('POST', '/api/v1/admin/auth/logout', {
  token: refreshed.json?.accessToken, body: { refreshToken: refreshed.json?.refreshToken },
});
const refreshAfterLogout = await call('POST', '/api/v1/admin/auth/refresh', {
  body: { refreshToken: refreshed.json?.refreshToken },
});
check('登出后 refresh token 不可用',
  logout.status === 204 && refreshAfterLogout.status === 401,
  `logout=${logout.status} refresh=${refreshAfterLogout.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
