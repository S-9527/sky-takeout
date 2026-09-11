// shop(门店营业状态)链路的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用(Flyway 已建表并灌入种子数据)。
// 用法:node scripts/smoke-shop.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 脚本会修改 shop_status,结束时把原值写回,便于反复执行。

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

const ADMIN_PATH = '/api/v1/admin/shop/status';
const CUSTOMER_PATH = '/api/v1/customer/shop/status';

// ---- 1. 令牌 ----
const login = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = login.json?.accessToken;
const customerLogin = await call('POST', '/api/v1/customer/auth/wechat-login', {
  body: { code: 'smoke-shop-1' },
});
const customerToken = customerLogin.json?.accessToken;
check('员工与顾客各自拿到令牌', !!adminToken && !!customerToken,
  `admin=${login.status} customer=${customerLogin.status}`);

// ---- 2. 管理端读取单行配置 ----
const baseline = await call('GET', ADMIN_PATH, { token: adminToken });
const original = baseline.json;
check('管理端返回单行配置且字段名是 isOpen(不是 open)',
  baseline.status === 200 && original?.isOpen === true && 'isOpen' in original,
  `status=${baseline.status} body=${JSON.stringify(original)}`);
check('营业时间按 HH:mm:ss 输出且公告非空',
  original?.openTime === '09:00:00' && original?.closeTime === '22:00:00'
  && typeof original?.notice === 'string' && original.notice.length > 0,
  `openTime=${original?.openTime} closeTime=${original?.closeTime}`);
check('管理端响应带 updatedAt', typeof original?.updatedAt === 'string', original?.updatedAt);

// ---- 3. 受众隔离:两端的营业状态接口互不通用 ----
const wrongAudience1 = await call('GET', CUSTOMER_PATH, { token: adminToken });
const wrongAudience2 = await call('GET', ADMIN_PATH, { token: customerToken });
check('员工令牌打顾客端 / 顾客令牌打管理端都返回 403 AUTH_AUDIENCE_MISMATCH',
  wrongAudience1.status === 403 && wrongAudience1.json?.code === 'AUTH_AUDIENCE_MISMATCH'
  && wrongAudience2.status === 403 && wrongAudience2.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${wrongAudience1.status}/${wrongAudience1.json?.code} vs ${wrongAudience2.status}/${wrongAudience2.json?.code}`);

const noToken = await call('GET', ADMIN_PATH);
check('无令牌返回 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 4. 顾客端视图 ----
const customerView = await call('GET', CUSTOMER_PATH, { token: customerToken });
const viewKeys = Object.keys(customerView.json ?? {});
check('顾客端只暴露 isOpen/openTime/closeTime/notice,不带审计字段',
  customerView.status === 200
  && JSON.stringify(viewKeys.sort()) === JSON.stringify(['closeTime', 'isOpen', 'notice', 'openTime']),
  `keys=${JSON.stringify(viewKeys)}`);

// ---- 5. 切换营业状态:未传的字段保持原值 ----
const closed = await call('PUT', ADMIN_PATH, { token: adminToken, body: { isOpen: false } });
check('PUT 只传 isOpen 即可打烊,其余字段保持原值',
  closed.status === 200 && closed.json?.isOpen === false
  && closed.json?.openTime === original.openTime && closed.json?.closeTime === original.closeTime
  && closed.json?.notice === original.notice,
  JSON.stringify(closed.json));

const viewWhileClosed = await call('GET', CUSTOMER_PATH, { token: customerToken });
check('打烊时顾客端仍返回 200 且 isOpen=false(R1:可浏览、不可下单)',
  viewWhileClosed.status === 200 && viewWhileClosed.json?.isOpen === false,
  `${viewWhileClosed.status} isOpen=${viewWhileClosed.json?.isOpen}`);

// ---- 6. 营业时间校验 ----
const badOrder = await call('PUT', ADMIN_PATH, {
  token: adminToken, body: { isOpen: true, openTime: '23:00', closeTime: '22:00' },
});
check('开始时间晚于结束时间返回 400 SHOP_BUSINESS_HOURS_INVALID',
  badOrder.status === 400 && badOrder.json?.code === 'SHOP_BUSINESS_HOURS_INVALID',
  `${badOrder.status} ${badOrder.json?.code}`);

const badFormat = await call('PUT', ADMIN_PATH, {
  token: adminToken, body: { isOpen: true, openTime: '9am' },
});
check('营业时间格式非法返回 400 SHOP_BUSINESS_HOURS_INVALID(不是 COMMON_VALIDATION_FAILED)',
  badFormat.status === 400 && badFormat.json?.code === 'SHOP_BUSINESS_HOURS_INVALID',
  `${badFormat.status} ${badFormat.json?.code}`);

// ---- 7. 请求体校验 ----
const noIsOpen = await call('PUT', ADMIN_PATH, { token: adminToken, body: { notice: '缺 isOpen' } });
check('缺 isOpen 返回 400 COMMON_VALIDATION_FAILED',
  noIsOpen.status === 400 && noIsOpen.json?.code === 'COMMON_VALIDATION_FAILED',
  `${noIsOpen.status} ${noIsOpen.json?.code}`);

const longNotice = await call('PUT', ADMIN_PATH, {
  token: adminToken, body: { isOpen: true, notice: 'x'.repeat(256) },
});
check('公告超过 255 字返回 400 COMMON_VALIDATION_FAILED',
  longNotice.status === 400 && longNotice.json?.code === 'COMMON_VALIDATION_FAILED',
  `${longNotice.status} ${longNotice.json?.code}`);

// ---- 8. HH:mm 被接受并归一化成 HH:mm:ss ----
const shortTime = await call('PUT', ADMIN_PATH, {
  token: adminToken, body: { isOpen: false, openTime: '08:30' },
});
check('openTime 接受 HH:mm 并归一化为 HH:mm:ss',
  shortTime.status === 200 && shortTime.json?.openTime === '08:30:00',
  `status=${shortTime.status} openTime=${shortTime.json?.openTime}`);

// ---- 9. 还原,避免冒烟脚本污染开发库 ----
const restored = await call('PUT', ADMIN_PATH, {
  token: adminToken,
  body: {
    isOpen: original.isOpen,
    openTime: original.openTime,
    closeTime: original.closeTime,
    notice: original.notice,
  },
});
check('脚本结束时把营业状态还原为执行前的值',
  restored.status === 200 && restored.json?.isOpen === original.isOpen
  && restored.json?.openTime === original.openTime
  && restored.json?.closeTime === original.closeTime
  && restored.json?.notice === original.notice,
  JSON.stringify(restored.json));

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
