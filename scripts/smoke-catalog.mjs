// catalog(商品分类)链路的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用(Flyway 已建表并灌入种子数据)。
// 用法:node scripts/smoke-catalog.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 脚本只创建/删除自己造的分类(名字带 smoke- 前缀),结束时清理;不动种子数据。

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

const ADMIN = '/api/v1/admin/categories';
const CUSTOMER = '/api/v1/customer/catalog/categories';

// ---- 0. 令牌 ----
const login = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = login.json?.accessToken;
const customerLogin = await call('POST', '/api/v1/customer/auth/wechat-login', {
  body: { code: 'smoke-catalog-1' },
});
const customerToken = customerLogin.json?.accessToken;
check('员工与顾客各自拿到令牌', !!adminToken && !!customerToken,
  `admin=${login.status} customer=${customerLogin.status}`);

// ---- 1. 分页与排序 ----
const page = await call('GET', `${ADMIN}?page=1&pageSize=50`, { token: adminToken });
check('分类分页返回种子分类且默认按 sortOrder 升序',
  page.status === 200 && page.json?.total >= 10 && page.json?.records?.[0]?.name === '川湘菜',
  `total=${page.json?.total} first=${page.json?.records?.[0]?.name} sortOrder=${page.json?.records?.[0]?.sortOrder}`);

const setmealPage = await call('GET', `${ADMIN}?type=SETMEAL&pageSize=50`, { token: adminToken });
check('type=SETMEAL 只返回套餐分类',
  setmealPage.status === 200 && setmealPage.json.records.length > 0
  && setmealPage.json.records.every((r) => r.type === 'SETMEAL'),
  `types=${[...new Set(setmealPage.json?.records?.map((r) => r.type))]}`);

const filtered = await call('GET', `${ADMIN}?name=${encodeURIComponent('套餐')}&pageSize=50`, { token: adminToken });
check('name 模糊过滤生效',
  filtered.status === 200 && filtered.json.records.length > 0
  && filtered.json.records.every((r) => r.name.includes('套餐')),
  `names=${filtered.json?.records?.map((r) => r.name)}`);

const badStatus = await call('GET', `${ADMIN}?status=2`, { token: adminToken });
check('非法 status 返回 400 而不是被当成禁用',
  badStatus.status === 400 && badStatus.json?.code === 'COMMON_VALIDATION_FAILED',
  `${badStatus.status} ${badStatus.json?.code}`);

const badSort = await call('GET', `${ADMIN}?sort=id,asc`, { token: adminToken });
check('排序字段不在白名单返回 400 COMMON_SORT_FIELD_NOT_ALLOWED',
  badSort.status === 400 && badSort.json?.code === 'COMMON_SORT_FIELD_NOT_ALLOWED',
  `${badSort.status} ${badSort.json?.code}`);

// ---- 2. 下拉与详情 ----
const options = await call('GET', `${ADMIN}/options?type=DISH`, { token: adminToken });
check('options 只返回启用中的菜品分类且按 sortOrder 升序',
  options.status === 200 && options.json.length >= 6
  && options.json.every((r) => r.type === 'DISH' && r.status === 1)
  && options.json.every((r, i, all) => i === 0 || all[i - 1].sortOrder <= r.sortOrder),
  `count=${options.json?.length}`);

const missingType = await call('GET', `${ADMIN}/options`, { token: adminToken });
check('options 缺 type 返回 400',
  missingType.status === 400 && missingType.json?.code === 'COMMON_VALIDATION_FAILED',
  `${missingType.status} ${missingType.json?.code}`);

const detail = await call('GET', `${ADMIN}/1`, { token: adminToken });
check('按 id 查询分类返回详情', detail.status === 200 && detail.json?.name === '川湘菜' && detail.json?.type === 'DISH',
  JSON.stringify(detail.json));

const notFound = await call('GET', `${ADMIN}/999999`, { token: adminToken });
check('分类不存在返回 404 CATEGORY_NOT_FOUND',
  notFound.status === 404 && notFound.json?.code === 'CATEGORY_NOT_FOUND',
  `${notFound.status} ${notFound.json?.code}`);

// ---- 3. 新增 ----
const created = await call('POST', ADMIN, {
  token: adminToken,
  body: { name: 'smoke-分类', type: 'DISH', sortOrder: 99 },
});
check('新增分类返回 201、默认启用、带审计时间',
  created.status === 201 && created.json?.id > 0 && created.json?.status === 1
  && typeof created.json?.createdAt === 'string',
  `${created.status} ${JSON.stringify(created.json)}`);
const createdId = created.json?.id;

const duplicate = await call('POST', ADMIN, {
  token: adminToken, body: { name: 'smoke-分类', type: 'DISH' },
});
check('同类型下重名返回 409 CATEGORY_NAME_TAKEN',
  duplicate.status === 409 && duplicate.json?.code === 'CATEGORY_NAME_TAKEN',
  `${duplicate.status} ${duplicate.json?.code}`);

// 名称唯一性是"同类型内"的:不同类型可以同名
const sameNameOtherType = await call('POST', ADMIN, {
  token: adminToken, body: { name: 'smoke-分类', type: 'SETMEAL', sortOrder: 98 },
});
check('不同类型下同名可以创建(唯一性只在同类型内)',
  sameNameOtherType.status === 201, `${sameNameOtherType.status} ${sameNameOtherType.json?.code}`);
const sameNameId = sameNameOtherType.json?.id;

const badType = await call('POST', ADMIN, { token: adminToken, body: { name: 'smoke-x', type: 'NOT_A_TYPE' } });
check('非法 type 返回 400', badType.status === 400 && badType.json?.code === 'COMMON_VALIDATION_FAILED',
  `${badType.status} ${badType.json?.code}`);

const missingName = await call('POST', ADMIN, { token: adminToken, body: { type: 'DISH' } });
check('缺 name 返回 400 且带字段级 details',
  missingName.status === 400 && missingName.json?.code === 'COMMON_VALIDATION_FAILED'
  && (missingName.json?.details?.length ?? 0) > 0,
  `details=${JSON.stringify(missingName.json?.details)}`);

// ---- 4. 编辑 ----
const renamed = await call('PUT', `${ADMIN}/${createdId}`, {
  token: adminToken, body: { name: 'smoke-分类改名', sortOrder: 97, status: 1 },
});
check('编辑分类成功且类型保持不变',
  renamed.status === 200 && renamed.json?.name === 'smoke-分类改名'
  && renamed.json?.sortOrder === 97 && renamed.json?.type === 'DISH',
  JSON.stringify(renamed.json));

const typeChange = await call('PUT', `${ADMIN}/${createdId}`, {
  token: adminToken, body: { name: 'smoke-分类改名', type: 'SETMEAL', sortOrder: 97, status: 1 },
});
check('试图修改分类类型返回 422 CATEGORY_TYPE_IMMUTABLE',
  typeChange.status === 422 && typeChange.json?.code === 'CATEGORY_TYPE_IMMUTABLE',
  `${typeChange.status} ${typeChange.json?.code}`);

const sameType = await call('PUT', `${ADMIN}/${createdId}`, {
  token: adminToken, body: { name: 'smoke-分类改名', type: 'DISH', sortOrder: 97, status: 1 },
});
check('type 传当前值视为"不改类型",正常通过', sameType.status === 200, `${sameType.status}`);

const nameTakenOnUpdate = await call('PUT', `${ADMIN}/${createdId}`, {
  token: adminToken, body: { name: '川湘菜', sortOrder: 97, status: 1 },
});
check('编辑成同类型下已存在的名称返回 409 CATEGORY_NAME_TAKEN',
  nameTakenOnUpdate.status === 409 && nameTakenOnUpdate.json?.code === 'CATEGORY_NAME_TAKEN',
  `${nameTakenOnUpdate.status} ${nameTakenOnUpdate.json?.code}`);

// ---- 5. 启停用与顾客端可见性 ----
const customerBefore = await call('GET', `${CUSTOMER}?type=DISH`, { token: customerToken });
check('顾客端分类列表只含启用分类',
  customerBefore.status === 200 && customerBefore.json.every((c) => c.status === 1),
  `count=${customerBefore.json?.length}`);

const disabled = await call('PATCH', `${ADMIN}/${createdId}/status`, { token: adminToken, body: { status: 0 } });
const customerAfterDisable = await call('GET', `${CUSTOMER}?type=DISH`, { token: customerToken });
check('禁用分类后顾客端不再返回它',
  disabled.status === 204 && !customerAfterDisable.json.some((c) => c.id === createdId),
  `patch=${disabled.status} customerCount=${customerAfterDisable.json?.length}`);

const adminStillSees = await call('GET', `${ADMIN}?status=0&pageSize=50`, { token: adminToken });
check('管理端仍能按 status=0 查到已禁用分类',
  adminStillSees.status === 200 && adminStillSees.json.records.some((c) => c.id === createdId),
  `count=${adminStillSees.json?.records?.length}`);

const enabled = await call('PATCH', `${ADMIN}/${createdId}/status`, { token: adminToken, body: { status: 1 } });
const customerAfterEnable = await call('GET', `${CUSTOMER}?type=DISH`, { token: customerToken });
check('重新启用后顾客端又能看到',
  enabled.status === 204 && customerAfterEnable.json.some((c) => c.id === createdId),
  `patch=${enabled.status}`);

// ---- 6. 受众与鉴权 ----
const wrongAudience = await call('GET', CUSTOMER, { token: adminToken });
check('员工令牌打顾客端分类接口返回 403 AUTH_AUDIENCE_MISMATCH',
  wrongAudience.status === 403 && wrongAudience.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${wrongAudience.status} ${wrongAudience.json?.code}`);

const noToken = await call('GET', ADMIN);
check('无令牌返回 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

const customerSetmeal = await call('GET', `${CUSTOMER}?type=SETMEAL`, { token: customerToken });
check('顾客端可按 SETMEAL 查套餐分类',
  customerSetmeal.status === 200 && customerSetmeal.json.every((c) => c.type === 'SETMEAL'),
  `count=${customerSetmeal.json?.length}`);

// ---- 7. 删除 ----
const inUse = await call('DELETE', `${ADMIN}/1`, { token: adminToken });
check('删除被菜品引用的分类返回 422 CATEGORY_IN_USE(外键 RESTRICT)',
  inUse.status === 422 && inUse.json?.code === 'CATEGORY_IN_USE',
  `${inUse.status} ${inUse.json?.code}`);

const deleteMissing = await call('DELETE', `${ADMIN}/999999`, { token: adminToken });
check('删除不存在的分类返回 404 CATEGORY_NOT_FOUND',
  deleteMissing.status === 404 && deleteMissing.json?.code === 'CATEGORY_NOT_FOUND',
  `${deleteMissing.status} ${deleteMissing.json?.code}`);

const deleted = await call('DELETE', `${ADMIN}/${createdId}`, { token: adminToken });
const afterDelete = await call('GET', `${ADMIN}/${createdId}`, { token: adminToken });
check('删除自己造的空分类返回 204,随后查询 404',
  deleted.status === 204 && afterDelete.status === 404,
  `delete=${deleted.status} get=${afterDelete.status}`);

// ---- 8. 清理 ----
const cleanup = await call('DELETE', `${ADMIN}/${sameNameId}`, { token: adminToken });
check('清理:删除另一个造出来的分类', cleanup.status === 204, `status=${cleanup.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
