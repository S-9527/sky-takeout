// catalog(菜品 + 口味)链路的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用(Flyway 已建表并灌入种子数据)。
// 用法:node scripts/smoke-dish.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 只创建/删除自己造的菜品(名字带 smoke- 前缀),结束时清理;停用分类也会还原。

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

const DISHES = '/api/v1/admin/dishes';
const CATEGORIES = '/api/v1/admin/categories';

// ---- 0. 令牌 ----
const login = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = login.json?.accessToken;
const customerLogin = await call('POST', '/api/v1/customer/auth/wechat-login', {
  body: { code: 'smoke-dish-1' },
});
const customerToken = customerLogin.json?.accessToken;
check('员工与顾客各自拿到令牌', !!adminToken && !!customerToken,
  `admin=${login.status} customer=${customerLogin.status}`);

// ---- 1. 分页、过滤、排序 ----
const page = await call('GET', `${DISHES}?page=1&pageSize=50`, { token: adminToken });
check('菜品分页返回种子菜品且默认按 sortOrder 升序',
  page.status === 200 && page.json?.total === 21 && page.json?.records?.[0]?.name === '宫保鸡丁',
  `total=${page.json?.total} first=${page.json?.records?.[0]?.name}`);
check('列表项带 categoryName(批量取分类名,不是 N+1)',
  page.json?.records?.every((r) => typeof r.categoryName === 'string' && r.categoryName.length > 0),
  `sample=${page.json?.records?.[0]?.categoryName}`);

const byCategory = await call('GET', `${DISHES}?categoryId=1&pageSize=50`, { token: adminToken });
check('按分类过滤只返回该分类菜品',
  byCategory.status === 200 && byCategory.json.records.length === 4
  && byCategory.json.records.every((r) => r.categoryId === 1),
  `count=${byCategory.json?.records?.length}`);

const byName = await call('GET', `${DISHES}?name=${encodeURIComponent('水煮')}&pageSize=50`, { token: adminToken });
check('name 模糊过滤生效',
  byName.status === 200 && byName.json.records.some((r) => r.name === '水煮牛肉'),
  `names=${byName.json?.records?.map((r) => r.name)}`);

const byStatus = await call('GET', `${DISHES}?status=1&pageSize=50`, { token: adminToken });
check('status 过滤生效', byStatus.status === 200 && byStatus.json.records.every((r) => r.status === 1),
  `count=${byStatus.json?.records?.length}`);

const byPrice = await call('GET', `${DISHES}?sort=priceCents,desc&pageSize=5`, { token: adminToken });
check('按价格降序排序生效', byPrice.status === 200 && byPrice.json.records[0].priceCents === 5800,
  `first=${byPrice.json?.records?.[0]?.name}/${byPrice.json?.records?.[0]?.priceCents}`);

const badSort = await call('GET', `${DISHES}?sort=id,asc`, { token: adminToken });
check('排序字段不在白名单返回 400 COMMON_SORT_FIELD_NOT_ALLOWED',
  badSort.status === 400 && badSort.json?.code === 'COMMON_SORT_FIELD_NOT_ALLOWED',
  `${badSort.status} ${badSort.json?.code}`);

const badStatus = await call('GET', `${DISHES}?status=2`, { token: adminToken });
check('非法 status 返回 400', badStatus.status === 400 && badStatus.json?.code === 'COMMON_VALIDATION_FAILED',
  `${badStatus.status} ${badStatus.json?.code}`);

// ---- 2. 详情 ----
const detail = await call('GET', `${DISHES}/101`, { token: adminToken });
check('菜品详情含口味配置且选项是数组',
  detail.status === 200 && detail.json?.name === '宫保鸡丁'
  && detail.json?.flavors?.length === 2
  && Array.isArray(detail.json.flavors[0].options)
  && detail.json.flavors[0].options.includes('重辣'),
  `flavors=${JSON.stringify(detail.json?.flavors?.map((f) => f.name))}`);

const missing = await call('GET', `${DISHES}/999999`, { token: adminToken });
check('菜品不存在返回 404 DISH_NOT_FOUND',
  missing.status === 404 && missing.json?.code === 'DISH_NOT_FOUND',
  `${missing.status} ${missing.json?.code}`);

// ---- 3. 新增 ----
const created = await call('POST', DISHES, {
  token: adminToken,
  body: {
    categoryId: 1, name: 'smoke-菜品', priceCents: 1234, imageUrl: '/files/dish/smoke.jpg',
    description: '冒烟用', sortOrder: 90,
    flavors: [
      { name: '辣度', options: ['不辣', '微辣,少油'], sortOrder: 1 },
      { name: '忌口', options: ['不要葱'], sortOrder: 2 },
    ],
  },
});
check('新增菜品返回 201、默认停售、口味带 id 回显',
  created.status === 201 && created.json?.id > 0 && created.json?.status === 0
  && created.json?.flavors?.length === 2 && created.json?.flavors[0].id > 0,
  `${created.status} status=${created.json?.status} flavors=${created.json?.flavors?.length}`);
const createdId = created.json?.id;
check('口味选项里的逗号被完整保留(D7)',
  created.json?.flavors?.[0]?.options?.[1] === '微辣,少油',
  JSON.stringify(created.json?.flavors?.[0]?.options));

const wrongType = await call('POST', DISHES, {
  token: adminToken, body: { categoryId: 7, name: 'smoke-菜品', priceCents: 100 },
});
check('菜谱挂到 SETMEAL 分类返回 422 DISH_CATEGORY_TYPE_MISMATCH',
  wrongType.status === 422 && wrongType.json?.code === 'DISH_CATEGORY_TYPE_MISMATCH',
  `${wrongType.status} ${wrongType.json?.code}`);

const unknownCategory = await call('POST', DISHES, {
  token: adminToken, body: { categoryId: 999999, name: 'smoke-菜品', priceCents: 100 },
});
check('分类不存在返回 404 CATEGORY_NOT_FOUND',
  unknownCategory.status === 404 && unknownCategory.json?.code === 'CATEGORY_NOT_FOUND',
  `${unknownCategory.status} ${unknownCategory.json?.code}`);

const duplicateName = await call('POST', DISHES, {
  token: adminToken, body: { categoryId: 1, name: '宫保鸡丁', priceCents: 100 },
});
check('同分类内重名返回 409 DISH_NAME_TAKEN',
  duplicateName.status === 409 && duplicateName.json?.code === 'DISH_NAME_TAKEN',
  `${duplicateName.status} ${duplicateName.json?.code}`);

const duplicateFlavor = await call('POST', DISHES, {
  token: adminToken,
  body: {
    categoryId: 1, name: 'smoke-重口味', priceCents: 100,
    flavors: [{ name: '辣度', options: ['不辣'] }, { name: '辣度', options: ['微辣'] }],
  },
});
check('同一菜品口味维度重名返回 400 且带字段级 details',
  duplicateFlavor.status === 400 && duplicateFlavor.json?.code === 'COMMON_VALIDATION_FAILED'
  && (duplicateFlavor.json?.details?.length ?? 0) > 0,
  `details=${JSON.stringify(duplicateFlavor.json?.details)}`);

const emptyOptions = await call('POST', DISHES, {
  token: adminToken,
  body: { categoryId: 1, name: 'smoke-空选项', priceCents: 100, flavors: [{ name: '辣度', options: [] }] },
});
check('口味选项为空返回 400', emptyOptions.status === 400 && emptyOptions.json?.code === 'COMMON_VALIDATION_FAILED',
  `${emptyOptions.status} ${emptyOptions.json?.code}`);

const negativePrice = await call('POST', DISHES, {
  token: adminToken, body: { categoryId: 1, name: 'smoke-负价', priceCents: -1 },
});
check('负价格返回 400', negativePrice.status === 400 && negativePrice.json?.code === 'COMMON_VALIDATION_FAILED',
  `${negativePrice.status} ${negativePrice.json?.code}`);

// ---- 4. 编辑与口味整体替换 ----
const replaced = await call('PUT', `${DISHES}/${createdId}`, {
  token: adminToken,
  body: {
    categoryId: 1, name: 'smoke-菜品改名', priceCents: 2222, status: 1, sortOrder: 91,
    flavors: [{ name: '份量', options: ['小份', '大份'] }],
  },
});
check('编辑菜品成功且口味被整体替换',
  replaced.status === 200 && replaced.json?.name === 'smoke-菜品改名'
  && replaced.json?.priceCents === 2222 && replaced.json?.status === 1
  && replaced.json?.flavors?.length === 1 && replaced.json?.flavors[0].name === '份量',
  `flavors=${JSON.stringify(replaced.json?.flavors?.map((f) => f.name))}`);

const keptFlavors = await call('PUT', `${DISHES}/${createdId}`, {
  token: adminToken,
  body: { categoryId: 1, name: 'smoke-菜品改名', priceCents: 2222, status: 1, sortOrder: 91 },
});
check('请求体不带 flavors 时口味保持不变',
  keptFlavors.status === 200 && keptFlavors.json?.flavors?.length === 1
  && keptFlavors.json.flavors[0].name === '份量',
  `flavors=${JSON.stringify(keptFlavors.json?.flavors?.map((f) => f.name))}`);

const clearedFlavors = await call('PUT', `${DISHES}/${createdId}`, {
  token: adminToken,
  body: { categoryId: 1, name: 'smoke-菜品改名', priceCents: 2222, status: 1, sortOrder: 91, flavors: [] },
});
check('请求体传空数组时口味被清空',
  clearedFlavors.status === 200 && clearedFlavors.json?.flavors?.length === 0,
  `flavors=${JSON.stringify(clearedFlavors.json?.flavors)}`);

// ---- 5. 分类启用状态对写操作的影响 ----
const dishInCategory2 = await call('POST', DISHES, {
  token: adminToken, body: { categoryId: 2, name: 'smoke-家常菜', priceCents: 1500 },
});
const dishInCategory2Id = dishInCategory2.json?.id;
check('先在启用分类下建一个菜品', dishInCategory2.status === 201, `${dishInCategory2.status}`);

await call('PATCH', `${CATEGORIES}/2/status`, { token: adminToken, body: { status: 0 } });
const createInDisabled = await call('POST', DISHES, {
  token: adminToken, body: { categoryId: 2, name: 'smoke-禁用分类菜', priceCents: 100 },
});
check('向已禁用分类新增菜品返回 422 CATEGORY_DISABLED',
  createInDisabled.status === 422 && createInDisabled.json?.code === 'CATEGORY_DISABLED',
  `${createInDisabled.status} ${createInDisabled.json?.code}`);

const updateInDisabled = await call('PUT', `${DISHES}/${dishInCategory2Id}`, {
  token: adminToken,
  body: { categoryId: 2, name: 'smoke-家常菜改名', priceCents: 1600, status: 1, sortOrder: 0 },
});
check('分类被禁用后,仍可编辑该分类下已有菜品(不换分类)',
  updateInDisabled.status === 200, `${updateInDisabled.status} ${updateInDisabled.json?.code}`);

const moveOutOfDisabled = await call('PUT', `${DISHES}/${dishInCategory2Id}`, {
  token: adminToken,
  body: { categoryId: 1, name: 'smoke-家常菜改名', priceCents: 1600, status: 1, sortOrder: 0 },
});
check('可以把它迁到启用中的分类', moveOutOfDisabled.status === 200 && moveOutOfDisabled.json?.categoryId === 1,
  `status=${moveOutOfDisabled.status} categoryId=${moveOutOfDisabled.json?.categoryId}`);

await call('PATCH', `${CATEGORIES}/2/status`, { token: adminToken, body: { status: 1 } });

// ---- 6. 批量起售 / 停售 ----
const started = await call('PATCH', `${DISHES}/status`, {
  token: adminToken, body: { ids: [createdId], status: 1 },
});
check('批量起售返回 204', started.status === 204, `${started.status}`);

const stopped = await call('PATCH', `${DISHES}/status`, {
  token: adminToken, body: { ids: [createdId, dishInCategory2Id], status: 0 },
});
const stoppedDetail = await call('GET', `${DISHES}/${createdId}`, { token: adminToken });
check('批量停售生效', stopped.status === 204 && stoppedDetail.json?.status === 0,
  `patch=${stopped.status} status=${stoppedDetail.json?.status}`);

const statusUnknownId = await call('PATCH', `${DISHES}/status`, {
  token: adminToken, body: { ids: [999999], status: 0 },
});
check('批量操作里有不存在的 id 返回 404 DISH_NOT_FOUND',
  statusUnknownId.status === 404 && statusUnknownId.json?.code === 'DISH_NOT_FOUND',
  `${statusUnknownId.status} ${statusUnknownId.json?.code}`);

const emptyIds = await call('PATCH', `${DISHES}/status`, { token: adminToken, body: { ids: [], status: 0 } });
check('批量 ids 为空返回 400', emptyIds.status === 400 && emptyIds.json?.code === 'COMMON_VALIDATION_FAILED',
  `${emptyIds.status} ${emptyIds.json?.code}`);

// ---- 7. 删除 ----
const inUse = await call('DELETE', `${DISHES}?ids=101`, { token: adminToken });
check('删除被套餐引用的菜品返回 422 SETMEAL_CONTAINS_DISH',
  inUse.status === 422 && inUse.json?.code === 'SETMEAL_CONTAINS_DISH',
  `${inUse.status} ${inUse.json?.code}`);

const malformedIds = await call('DELETE', `${DISHES}?ids=101,abc`, { token: adminToken });
check('ids 格式非法返回 400', malformedIds.status === 400 && malformedIds.json?.code === 'COMMON_VALIDATION_FAILED',
  `${malformedIds.status} ${malformedIds.json?.code}`);

const deleteMissing = await call('DELETE', `${DISHES}?ids=999999`, { token: adminToken });
check('删除不存在的菜品返回 404 DISH_NOT_FOUND',
  deleteMissing.status === 404 && deleteMissing.json?.code === 'DISH_NOT_FOUND',
  `${deleteMissing.status} ${deleteMissing.json?.code}`);

const deleted = await call('DELETE', `${DISHES}?ids=${createdId}`, { token: adminToken });
const afterDelete = await call('GET', `${DISHES}/${createdId}`, { token: adminToken });
check('删除自己造的菜品返回 204,连口味一起删掉,随后查询 404',
  deleted.status === 204 && afterDelete.status === 404,
  `delete=${deleted.status} get=${afterDelete.status}`);

// ---- 8. 鉴权 ----
const wrongAudience = await call('GET', DISHES, { token: customerToken });
check('顾客令牌打管理端菜品接口返回 403 AUTH_AUDIENCE_MISMATCH',
  wrongAudience.status === 403 && wrongAudience.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${wrongAudience.status} ${wrongAudience.json?.code}`);

const noToken = await call('GET', DISHES);
check('无令牌返回 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 9. 清理 ----
const cleanup = await call('DELETE', `${DISHES}?ids=${dishInCategory2Id}`, { token: adminToken });
check('清理:删除第二个造出来的菜品', cleanup.status === 204, `status=${cleanup.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
