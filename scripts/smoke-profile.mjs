// Profile(顾客地址簿)链路的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用(Flyway 已建表并灌入种子数据)。
// 用法:node scripts/smoke-profile.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 先清空脚本用过的两个顾客的地址簿(同一 mock code 每次映射到同一个顾客),结束时同样清空。

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

const ADDRESSES = '/api/v1/customer/addresses';

function sample(overrides = {}) {
  return {
    consignee: '张三', phone: '13800138000', province: '北京市', city: '北京市',
    district: '朝阳区', detail: '望京街道 1 号院 2 号楼 3 单元 401', label: '家',
    ...overrides,
  };
}

async function wipe(token) {
  const list = await call('GET', ADDRESSES, { token });
  for (const address of list.json ?? []) {
    await call('DELETE', `${ADDRESSES}/${address.id}`, { token });
  }
}

// ---- 0. 令牌与干净状态 ----
const adminLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = adminLogin.json?.accessToken;
const c1 = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-profile-1' } });
const c2 = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-profile-2' } });
const token1 = c1.json?.accessToken;
const token2 = c2.json?.accessToken;
check('拿到 admin 与两个顾客令牌', !!adminToken && !!token1 && !!token2,
  `admin=${adminLogin.status} c1=${c1.status} c2=${c2.status}`);

await wipe(token1);
await wipe(token2);
const empty = await call('GET', ADDRESSES, { token: token1 });
check('清空后地址列表为空', empty.status === 200 && empty.json.length === 0, `count=${empty.json?.length}`);

// ---- 1. 新增 ----
const a = await call('POST', ADDRESSES, { token: token1, body: sample({ consignee: '地址A' }) });
check('新增普通地址返回 201、isDefault=0、回显全部字段',
  a.status === 201 && a.json?.isDefault === 0 && a.json?.consignee === '地址A'
  && a.json?.customerId != null && a.json?.createdAt != null,
  `${a.status} ${JSON.stringify(a.json && { id: a.json.id, isDefault: a.json.isDefault })}`);
const aId = a.json?.id;

const b = await call('POST', ADDRESSES, { token: token1, body: sample({ consignee: '地址B', isDefault: 1 }) });
check('新增默认地址返回 isDefault=1', b.status === 201 && b.json?.isDefault === 1,
  `${b.status} isDefault=${b.json?.isDefault}`);
const bId = b.json?.id;

const c = await call('POST', ADDRESSES, { token: token1, body: sample({ consignee: '地址C', isDefault: 1 }) });
const listAfterDefaults = await call('GET', ADDRESSES, { token: token1 });
const defaults = listAfterDefaults.json.filter((x) => x.isDefault === 1);
check('再设一条默认会清掉上一条默认(始终至多一条)',
  c.status === 201 && defaults.length === 1 && defaults[0].id === c.json?.id,
  `defaults=${JSON.stringify(defaults.map((x) => x.consignee))}`);
check('默认地址排在列表最前', listAfterDefaults.json[0]?.id === c.json?.id,
  `first=${listAfterDefaults.json[0]?.consignee}`);
const cId = c.json?.id;

const badPhone = await call('POST', ADDRESSES, { token: token1, body: sample({ phone: '123' }) });
check('手机号格式错误 → 400 且带字段级 details',
  badPhone.status === 400 && badPhone.json?.code === 'COMMON_VALIDATION_FAILED'
  && (badPhone.json?.details?.length ?? 0) > 0,
  `details=${JSON.stringify(badPhone.json?.details)}`);

const missingConsignee = await call('POST', ADDRESSES, {
  token: token1, body: sample({ consignee: '' }),
});
check('收货人为空 → 400', missingConsignee.status === 400
  && missingConsignee.json?.code === 'COMMON_VALIDATION_FAILED',
  `${missingConsignee.status} ${missingConsignee.json?.code}`);

const illegalFlag = await call('POST', ADDRESSES, {
  token: token1, body: sample({ isDefault: 2 }),
});
check('isDefault 非 0/1 → 400 COMMON_VALIDATION_FAILED',
  illegalFlag.status === 400 && illegalFlag.json?.code === 'COMMON_VALIDATION_FAILED',
  `${illegalFlag.status} ${illegalFlag.json?.code}`);

// ---- 2. R9:他人地址不可见/不可改 ----
const other = await call('POST', ADDRESSES, { token: token2, body: sample({ consignee: '别人的地址' }) });
const otherId = other.json?.id;

const crossGet = await call('GET', `${ADDRESSES}/${otherId}`, { token: token1 });
const crossPut = await call('PUT', `${ADDRESSES}/${otherId}`, {
  token: token1, body: sample({ consignee: '改名', isDefault: 0 }),
});
const crossDelete = await call('DELETE', `${ADDRESSES}/${otherId}`, { token: token1 });
const crossDefault = await call('PATCH', `${ADDRESSES}/${otherId}/default`, { token: token1 });
check('他人地址的查/改/删/设默认一律 404 ADDRESS_NOT_FOUND(不泄露存在性)',
  [crossGet, crossPut, crossDelete, crossDefault].every((r) => r.status === 404 && r.json?.code === 'ADDRESS_NOT_FOUND'),
  `${crossGet.status}/${crossPut.status}/${crossDelete.status}/${crossDefault.status}`);

const unknown = await call('GET', `${ADDRESSES}/999999`, { token: token1 });
check('不存在的地址 → 404 ADDRESS_NOT_FOUND',
  unknown.status === 404 && unknown.json?.code === 'ADDRESS_NOT_FOUND',
  `${unknown.status} ${unknown.json?.code}`);

// ---- 3. 编辑 ----
const updated = await call('PUT', `${ADDRESSES}/${aId}`, {
  token: token1, body: sample({ consignee: '地址A改名', detail: '新详细地址 9 号', isDefault: 1 }),
});
const listAfterUpdate = await call('GET', ADDRESSES, { token: token1 });
const defaultsAfterUpdate = listAfterUpdate.json.filter((x) => x.isDefault === 1);
check('编辑时置默认同样会清掉其它默认',
  updated.status === 200 && updated.json?.consignee === '地址A改名' && updated.json?.detail === '新详细地址 9 号'
  && defaultsAfterUpdate.length === 1 && defaultsAfterUpdate[0].id === aId,
  `defaults=${JSON.stringify(defaultsAfterUpdate.map((x) => x.consignee))}`);

const unsetDefault = await call('PUT', `${ADDRESSES}/${aId}`, {
  token: token1, body: sample({ consignee: '地址A改名', isDefault: 0 }),
});
const listAfterUnset = await call('GET', ADDRESSES, { token: token1 });
check('允许把默认地址改回普通地址(默认数 0 是合法状态)',
  unsetDefault.status === 200 && unsetDefault.json?.isDefault === 0
  && listAfterUnset.json.every((x) => x.isDefault === 0),
  `defaults=${listAfterUnset.json?.filter((x) => x.isDefault === 1).length}`);

// ---- 4. 设为默认 ----
const setDefault = await call('PATCH', `${ADDRESSES}/${cId}/default`, { token: token1 });
const listAfterSet = await call('GET', ADDRESSES, { token: token1 });
check('PATCH 设为默认后列表首条是该地址且只有一条默认',
  setDefault.status === 204 && listAfterSet.json[0].id === cId
  && listAfterSet.json.filter((x) => x.isDefault === 1).length === 1,
  `status=${setDefault.status} first=${listAfterSet.json[0]?.consignee}`);

const setDefaultAgain = await call('PATCH', `${ADDRESSES}/${cId}/default`, { token: token1 });
check('重复设为默认是幂等的(204)', setDefaultAgain.status === 204, `${setDefaultAgain.status}`);

// ---- 5. 删除 ----
const deleted = await call('DELETE', `${ADDRESSES}/${bId}`, { token: token1 });
const afterDelete = await call('GET', ADDRESSES, { token: token1 });
const deleteAgain = await call('DELETE', `${ADDRESSES}/${bId}`, { token: token1 });
check('删除返回 204、列表里消失,重复删除 404',
  deleted.status === 204 && !afterDelete.json.some((x) => x.id === bId) && deleteAgain.status === 404,
  `delete=${deleted.status} again=${deleteAgain.status} count=${afterDelete.json?.length}`);

// ---- 6. 数量上限 20 ----
await wipe(token1);
const created = [];
for (let i = 0; i < 20; i++) {
  const res = await call('POST', ADDRESSES, { token: token1, body: sample({ consignee: `批量${i}` }) });
  created.push(res.status);
}
const overLimit = await call('POST', ADDRESSES, { token: token1, body: sample({ consignee: '第21条' }) });
const listed = await call('GET', ADDRESSES, { token: token1 });
check('可以建到 20 条,第 21 条 → 422 ADDRESS_LIMIT_EXCEEDED',
  created.every((s) => s === 201) && overLimit.status === 422
  && overLimit.json?.code === 'ADDRESS_LIMIT_EXCEEDED' && listed.json.length === 20,
  `created=${created.filter((s) => s === 201).length} over=${overLimit.status}/${overLimit.json?.code} listed=${listed.json?.length}`);

// ---- 7. 鉴权 ----
const wrongAudience = await call('GET', ADDRESSES, { token: adminToken });
check('员工令牌打地址簿返回 403 AUTH_AUDIENCE_MISMATCH',
  wrongAudience.status === 403 && wrongAudience.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${wrongAudience.status} ${wrongAudience.json?.code}`);

const noToken = await call('GET', ADDRESSES);
check('无令牌返回 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 8. 清理 ----
await wipe(token1);
await wipe(token2);
const finalCheck = await call('GET', ADDRESSES, { token: token1 });
const finalOther = await call('GET', ADDRESSES, { token: token2 });
check('清理:两个顾客的地址簿都清空',
  finalCheck.json.length === 0 && finalOther.json.length === 0,
  `c1=${finalCheck.json?.length} c2=${finalOther.json?.length}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
