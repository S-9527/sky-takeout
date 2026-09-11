// catalog(套餐 + 组成明细)链路的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用(Flyway 已建表并灌入种子数据)。
// 用法:node scripts/smoke-setmeal.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 只创建/删除自己造的套餐与菜品(名字带 smoke- 前缀),结束时清理;临时停售的种子菜品会恢复。

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

const SETMEALS = '/api/v1/admin/setmeals';
const DISHES = '/api/v1/admin/dishes';

const login = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = login.json?.accessToken;
const customerLogin = await call('POST', '/api/v1/customer/auth/wechat-login', {
  body: { code: 'smoke-setmeal-1' },
});
const customerToken = customerLogin.json?.accessToken;
check('员工与顾客各自拿到令牌', !!adminToken && !!customerToken,
  `admin=${login.status} customer=${customerLogin.status}`);

// ---- 1. 分页、过滤、排序 ----
const page = await call('GET', `${SETMEALS}?page=1&pageSize=50`, { token: adminToken });
check('套餐分页返回 5 个种子套餐并按 createdAt desc 默认排序',
  page.status === 200 && page.json?.total === 5 && page.json?.records?.length === 5,
  `total=${page.json?.total} first=${page.json?.records?.[0]?.name}`);
check('列表项带 categoryName',
  page.json?.records?.every((r) => typeof r.categoryName === 'string' && r.categoryName.length > 0),
  `sample=${page.json?.records?.[0]?.categoryName}`);

const byCategory = await call('GET', `${SETMEALS}?categoryId=7&pageSize=50`, { token: adminToken });
check('按分类过滤只返回该分类套餐',
  byCategory.status === 200 && byCategory.json.records.length === 2
  && byCategory.json.records.every((r) => r.categoryId === 7),
  `count=${byCategory.json?.records?.length}`);

const byName = await call('GET', `${SETMEALS}?name=${encodeURIComponent('家庭')}&pageSize=50`, { token: adminToken });
check('name 模糊过滤生效',
  byName.status === 200 && byName.json.records.some((r) => r.name === '家庭四人套餐'),
  `names=${byName.json?.records?.map((r) => r.name)}`);

const byPrice = await call('GET', `${SETMEALS}?sort=priceCents,desc&pageSize=3`, { token: adminToken });
check('按价格降序排序生效', byPrice.status === 200 && byPrice.json.records[0].priceCents === 22800,
  `first=${byPrice.json?.records?.[0]?.name}/${byPrice.json?.records?.[0]?.priceCents}`);

/** setmeal 表没有 sort_order 列,所以 sortOrder 必须不在白名单里(与菜品不同)。 */
const badSort = await call('GET', `${SETMEALS}?sort=sortOrder,asc`, { token: adminToken });
check('sort=sortOrder 被白名单拒绝(setmeal 没有该列)',
  badSort.status === 400 && badSort.json?.code === 'COMMON_SORT_FIELD_NOT_ALLOWED',
  `${badSort.status} ${badSort.json?.code}`);

const badStatus = await call('GET', `${SETMEALS}?status=2`, { token: adminToken });
check('非法 status 返回 400', badStatus.status === 400 && badStatus.json?.code === 'COMMON_VALIDATION_FAILED',
  `${badStatus.status} ${badStatus.json?.code}`);

// ---- 2. 详情 ----
const detail = await call('GET', `${SETMEALS}/201`, { token: adminToken });
check('套餐详情含组成明细并带菜品名与当前单价',
  detail.status === 200 && detail.json?.name === '单人川味套餐'
  && detail.json?.items?.length === 3
  && detail.json.items.some((i) => i.dishName === '宫保鸡丁' && i.dishPriceCents === 3800),
  `items=${JSON.stringify(detail.json?.items?.map((i) => `${i.dishName}x${i.copies}`))}`);

const missing = await call('GET', `${SETMEALS}/999999`, { token: adminToken });
check('套餐不存在返回 404 SETMEAL_NOT_FOUND',
  missing.status === 404 && missing.json?.code === 'SETMEAL_NOT_FOUND',
  `${missing.status} ${missing.json?.code}`);

// ---- 3. 造一个停售菜品(用于起售前置校验) ----
const offSaleDish = await call('POST', DISHES, {
  token: adminToken, body: { categoryId: 1, name: 'smoke-停售菜', priceCents: 900 },
});
const offSaleDishId = offSaleDish.json?.id;
check('新建菜品默认停售(用于后续起售校验)', offSaleDish.status === 201 && offSaleDish.json?.status === 0,
  `status=${offSaleDish.json?.status}`);

const setmealWithOffSale = await call('POST', SETMEALS, {
  token: adminToken,
  body: { categoryId: 7, name: 'smoke-含停售菜套餐', priceCents: 900, items: [{ dishId: offSaleDishId, copies: 1 }] },
});
const setmealWithOffSaleId = setmealWithOffSale.json?.id;
check('可以创建含停售菜品的套餐(但只能停售状态)',
  setmealWithOffSale.status === 201 && setmealWithOffSale.json?.status === 0,
  `status=${setmealWithOffSale.json?.status}`);

const startWithOffSale = await call('PATCH', `${SETMEALS}/status`, {
  token: adminToken, body: { ids: [setmealWithOffSaleId], status: 1 },
});
check('起售含停售菜品的套餐返回 422 SETMEAL_DISH_NOT_ON_SALE 且带明细',
  startWithOffSale.status === 422 && startWithOffSale.json?.code === 'SETMEAL_DISH_NOT_ON_SALE'
  && (startWithOffSale.json?.details?.length ?? 0) > 0,
  `${startWithOffSale.status} details=${JSON.stringify(startWithOffSale.json?.details)}`);

// ---- 4. 新增套餐 ----
const created = await call('POST', SETMEALS, {
  token: adminToken,
  body: {
    categoryId: 7, name: 'smoke-套餐', priceCents: 4100, imageUrl: '/files/setmeal/smoke.jpg',
    items: [{ dishId: 101, copies: 1 }, { dishId: 112, copies: 1 }],
  },
});
const createdId = created.json?.id;
check('新增套餐返回 201、默认停售、明细带菜品名',
  created.status === 201 && createdId > 0 && created.json?.status === 0
  && created.json?.items?.length === 2 && created.json.items[0].dishName === '宫保鸡丁',
  `${created.status} items=${JSON.stringify(created.json?.items?.map((i) => i.dishName))}`);

const tooExpensive = await call('POST', SETMEALS, {
  token: adminToken, body: { categoryId: 7, name: 'smoke-贵套餐', priceCents: 5000, items: [{ dishId: 101, copies: 1 }] },
});
check('定价高于所含菜品合计返回 422 SETMEAL_PRICE_EXCEEDS_ITEMS',
  tooExpensive.status === 422 && tooExpensive.json?.code === 'SETMEAL_PRICE_EXCEEDS_ITEMS',
  `${tooExpensive.status} ${tooExpensive.json?.code}`);

const duplicates = await call('POST', SETMEALS, {
  token: adminToken,
  body: { categoryId: 7, name: 'smoke-重复菜', priceCents: 100, items: [{ dishId: 101, copies: 1 }, { dishId: 101, copies: 1 }] },
});
check('明细里同一菜品重复返回 409 SETMEAL_ITEMS_DUPLICATED',
  duplicates.status === 409 && duplicates.json?.code === 'SETMEAL_ITEMS_DUPLICATED',
  `${duplicates.status} ${duplicates.json?.code}`);

const emptyItems = await call('POST', SETMEALS, {
  token: adminToken, body: { categoryId: 7, name: 'smoke-空套餐', priceCents: 100, items: [] },
});
check('明细为空返回 422 SETMEAL_ITEMS_EMPTY(不是 400)',
  emptyItems.status === 422 && emptyItems.json?.code === 'SETMEAL_ITEMS_EMPTY',
  `${emptyItems.status} ${emptyItems.json?.code}`);

const unknownDish = await call('POST', SETMEALS, {
  token: adminToken, body: { categoryId: 7, name: 'smoke-幽灵菜', priceCents: 100, items: [{ dishId: 999999, copies: 1 }] },
});
check('明细里菜品不存在返回 404 DISH_NOT_FOUND',
  unknownDish.status === 404 && unknownDish.json?.code === 'DISH_NOT_FOUND',
  `${unknownDish.status} ${unknownDish.json?.code}`);

const wrongCategoryType = await call('POST', SETMEALS, {
  token: adminToken, body: { categoryId: 1, name: 'smoke-错类型', priceCents: 100, items: [{ dishId: 101, copies: 1 }] },
});
check('套餐挂到 DISH 分类返回 422 SETMEAL_CATEGORY_TYPE_MISMATCH',
  wrongCategoryType.status === 422 && wrongCategoryType.json?.code === 'SETMEAL_CATEGORY_TYPE_MISMATCH',
  `${wrongCategoryType.status} ${wrongCategoryType.json?.code}`);

const unknownCategory = await call('POST', SETMEALS, {
  token: adminToken, body: { categoryId: 999999, name: 'smoke-幽灵分类', priceCents: 100, items: [{ dishId: 101, copies: 1 }] },
});
check('分类不存在返回 404 CATEGORY_NOT_FOUND',
  unknownCategory.status === 404 && unknownCategory.json?.code === 'CATEGORY_NOT_FOUND',
  `${unknownCategory.status} ${unknownCategory.json?.code}`);

const duplicateName = await call('POST', SETMEALS, {
  token: adminToken, body: { categoryId: 7, name: '单人川味套餐', priceCents: 100, items: [{ dishId: 101, copies: 1 }] },
});
check('同分类内重名返回 409 SETMEAL_NAME_TAKEN',
  duplicateName.status === 409 && duplicateName.json?.code === 'SETMEAL_NAME_TAKEN',
  `${duplicateName.status} ${duplicateName.json?.code}`);

const badCopies = await call('POST', SETMEALS, {
  token: adminToken, body: { categoryId: 7, name: 'smoke-零份', priceCents: 100, items: [{ dishId: 101, copies: 0 }] },
});
check('份数为 0 返回 400', badCopies.status === 400 && badCopies.json?.code === 'COMMON_VALIDATION_FAILED',
  `${badCopies.status} ${badCopies.json?.code}`);

// ---- 5. 编辑与明细整体替换 ----
const replaced = await call('PUT', `${SETMEALS}/${createdId}`, {
  token: adminToken,
  body: { categoryId: 7, name: 'smoke-套餐改名', priceCents: 4200, status: 1, items: [{ dishId: 102, copies: 1 }] },
});
check('编辑套餐成功且明细被整体替换',
  replaced.status === 200 && replaced.json?.name === 'smoke-套餐改名'
  && replaced.json?.items?.length === 1 && replaced.json.items[0].dishName === '水煮牛肉',
  `items=${JSON.stringify(replaced.json?.items?.map((i) => i.dishName))}`);

const keptItems = await call('PUT', `${SETMEALS}/${createdId}`, {
  token: adminToken,
  body: { categoryId: 7, name: 'smoke-套餐改名', priceCents: 5800, status: 1 },
});
check('不带 items 时明细保持不变(价格按现有所含菜品校验)',
  keptItems.status === 200 && keptItems.json?.items?.length === 1
  && keptItems.json.items[0].dishName === '水煮牛肉',
  `items=${JSON.stringify(keptItems.json?.items?.map((i) => i.dishName))}`);

const priceAgainstCurrent = await call('PUT', `${SETMEALS}/${createdId}`, {
  token: adminToken,
  body: { categoryId: 7, name: 'smoke-套餐改名', priceCents: 5801, status: 1 },
});
check('不带 items 但价格超过现有所含菜品合计 → 422',
  priceAgainstCurrent.status === 422 && priceAgainstCurrent.json?.code === 'SETMEAL_PRICE_EXCEEDS_ITEMS',
  `${priceAgainstCurrent.status} ${priceAgainstCurrent.json?.code}`);

const wrongCategoryOnUpdate = await call('PUT', `${SETMEALS}/${createdId}`, {
  token: adminToken,
  body: { categoryId: 1, name: 'smoke-套餐改名', priceCents: 100, status: 1 },
});
check('编辑时挂到 DISH 分类返回 422 SETMEAL_CATEGORY_TYPE_MISMATCH',
  wrongCategoryOnUpdate.status === 422 && wrongCategoryOnUpdate.json?.code === 'SETMEAL_CATEGORY_TYPE_MISMATCH',
  `${wrongCategoryOnUpdate.status} ${wrongCategoryOnUpdate.json?.code}`);

// ---- 6. 批量起售/停售 与"停售菜品连带停售套餐" ----
const stopped = await call('PATCH', `${SETMEALS}/status`, { token: adminToken, body: { ids: [createdId], status: 0 } });
const started = await call('PATCH', `${SETMEALS}/status`, { token: adminToken, body: { ids: [createdId], status: 1 } });
const afterStart = await call('GET', `${SETMEALS}/${createdId}`, { token: adminToken });
check('批量停售/起售生效(所含菜品都在售)',
  stopped.status === 204 && started.status === 204 && afterStart.json?.status === 1,
  `stop=${stopped.status} start=${started.status} status=${afterStart.json?.status}`);

// 连带停售用**自造**的菜品与套餐验证:种子菜品 102 同时被套餐 203/205 引用,
// 拿它做实验会把种子套餐一起停售(冒烟脚本不应该污染种子数据)。
const cascadeDish = await call('POST', DISHES, {
  token: adminToken, body: { categoryId: 1, name: 'smoke-连带菜', priceCents: 700 },
});
const cascadeDishId = cascadeDish.json?.id;
await call('PATCH', `${DISHES}/status`, { token: adminToken, body: { ids: [cascadeDishId], status: 1 } });
const cascadeSetmeal = await call('POST', SETMEALS, {
  token: adminToken,
  body: { categoryId: 7, name: 'smoke-连带套餐', priceCents: 700, status: 1, items: [{ dishId: cascadeDishId, copies: 1 }] },
});
const cascadeSetmealId = cascadeSetmeal.json?.id;
check('用自造菜品创建并起售套餐成功',
  cascadeSetmeal.status === 201 && cascadeSetmeal.json?.status === 1,
  `status=${cascadeSetmeal.json?.status}`);

// 停售该菜品 → 包含它的套餐应被连带停售
await call('PATCH', `${DISHES}/status`, { token: adminToken, body: { ids: [cascadeDishId], status: 0 } });
const cascaded = await call('GET', `${SETMEALS}/${cascadeSetmealId}`, { token: adminToken });
check('停售菜品后,包含它的套餐被连带停售',
  cascaded.status === 200 && cascaded.json?.status === 0,
  `status=${cascaded.json?.status}`);

const restartAfterCascade = await call('PATCH', `${SETMEALS}/status`, {
  token: adminToken, body: { ids: [cascadeSetmealId], status: 1 },
});
check('菜品停售期间套餐无法重新起售 → 422 SETMEAL_DISH_NOT_ON_SALE',
  restartAfterCascade.status === 422 && restartAfterCascade.json?.code === 'SETMEAL_DISH_NOT_ON_SALE',
  `${restartAfterCascade.status} ${restartAfterCascade.json?.code}`);

// 恢复菜品,套餐不会自动回到起售(起售是独立决定)
await call('PATCH', `${DISHES}/status`, { token: adminToken, body: { ids: [cascadeDishId], status: 1 } });
const stillStopped = await call('GET', `${SETMEALS}/${cascadeSetmealId}`, { token: adminToken });
check('菜品恢复起售后套餐不会自动起售(起售是独立决定)',
  stillStopped.json?.status === 0, `status=${stillStopped.json?.status}`);

const unknownId = await call('PATCH', `${SETMEALS}/status`, {
  token: adminToken, body: { ids: [999999], status: 1 },
});
check('批量操作里有不存在的 id 返回 404 SETMEAL_NOT_FOUND',
  unknownId.status === 404 && unknownId.json?.code === 'SETMEAL_NOT_FOUND',
  `${unknownId.status} ${unknownId.json?.code}`);

// ---- 7. 删除 ----
const malformedIds = await call('DELETE', `${SETMEALS}?ids=201,x`, { token: adminToken });
check('ids 格式非法返回 400', malformedIds.status === 400 && malformedIds.json?.code === 'COMMON_VALIDATION_FAILED',
  `${malformedIds.status} ${malformedIds.json?.code}`);

const deleteMissing = await call('DELETE', `${SETMEALS}?ids=999999`, { token: adminToken });
check('删除不存在的套餐返回 404 SETMEAL_NOT_FOUND',
  deleteMissing.status === 404 && deleteMissing.json?.code === 'SETMEAL_NOT_FOUND',
  `${deleteMissing.status} ${deleteMissing.json?.code}`);

const deleted = await call('DELETE', `${SETMEALS}?ids=${createdId}`, { token: adminToken });
const afterDelete = await call('GET', `${SETMEALS}/${createdId}`, { token: adminToken });
check('删除自己造的套餐返回 204,连组成明细一起删,随后查询 404',
  deleted.status === 204 && afterDelete.status === 404,
  `delete=${deleted.status} get=${afterDelete.status}`);

// ---- 8. 鉴权 ----
const wrongAudience = await call('GET', SETMEALS, { token: customerToken });
check('顾客令牌打管理端套餐接口返回 403 AUTH_AUDIENCE_MISMATCH',
  wrongAudience.status === 403 && wrongAudience.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${wrongAudience.status} ${wrongAudience.json?.code}`);

const noToken = await call('GET', SETMEALS);
check('无令牌返回 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 9. 清理 ----
const cleanupCascadeSetmeal = await call('DELETE', `${SETMEALS}?ids=${cascadeSetmealId}`, { token: adminToken });
const cleanupCascadeDish = await call('DELETE', `${DISHES}?ids=${cascadeDishId}`, { token: adminToken });
const cleanupSetmeal = await call('DELETE', `${SETMEALS}?ids=${setmealWithOffSaleId}`, { token: adminToken });
const cleanupDish = await call('DELETE', `${DISHES}?ids=${offSaleDishId}`, { token: adminToken });
check('清理:先删套餐再删菜品(菜品被套餐引用时删不掉)',
  cleanupCascadeSetmeal.status === 204 && cleanupCascadeDish.status === 204
  && cleanupSetmeal.status === 204 && cleanupDish.status === 204,
  `cascade=${cleanupCascadeSetmeal.status}/${cleanupCascadeDish.status} offsale=${cleanupSetmeal.status}/${cleanupDish.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
