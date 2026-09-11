// 顾客端商品目录(categories / dishes / setmeals)的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用(Flyway 已建表并灌入种子数据)。
// 用法:node scripts/smoke-customer-catalog.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 只读种子数据;所有会改状态的验证都用自造的分类/菜品/套餐,结束时清理。

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

const CATALOG = '/api/v1/customer/catalog';
const ADMIN_CATEGORIES = '/api/v1/admin/categories';
const ADMIN_DISHES = '/api/v1/admin/dishes';
const ADMIN_SETMEALS = '/api/v1/admin/setmeals';

// ---- 0. 令牌 ----
const login = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = login.json?.accessToken;
const customerLogin = await call('POST', '/api/v1/customer/auth/wechat-login', {
  body: { code: 'smoke-customer-catalog-1' },
});
const customerToken = customerLogin.json?.accessToken;
check('员工与顾客各自拿到令牌', !!adminToken && !!customerToken,
  `admin=${login.status} customer=${customerLogin.status}`);

// ---- 1. 顾客端分类 ----
const categories = await call('GET', `${CATALOG}/categories?type=DISH`, { token: customerToken });
check('顾客端分类只返回启用中的菜品分类且按 sortOrder 升序',
  categories.status === 200 && categories.json.length >= 6
  && categories.json.every((c) => c.type === 'DISH' && c.status === 1)
  && categories.json.every((c, i, all) => i === 0 || all[i - 1].sortOrder <= c.sortOrder),
  `count=${categories.json?.length}`);

const missingType = await call('GET', `${CATALOG}/categories`, { token: customerToken });
check('顾客端分类缺 type 返回 400',
  missingType.status === 400 && missingType.json?.code === 'COMMON_VALIDATION_FAILED',
  `${missingType.status} ${missingType.json?.code}`);

// ---- 2. 顾客端菜品列表 ----
const dishes = await call('GET', `${CATALOG}/dishes?categoryId=1&pageSize=50`, { token: customerToken });
check('按分类返回起售菜品,并按 sortOrder 升序',
  dishes.status === 200 && dishes.json?.total === 4
  && dishes.json.records.every((d) => d.categoryId === 1 && d.status === 1)
  && dishes.json.records.every((d, i, all) => i === 0 || all[i - 1].sortOrder <= d.sortOrder),
  `total=${dishes.json?.total} first=${dishes.json?.records?.[0]?.name}`);
check('顾客端菜品列表带 categoryName',
  dishes.json?.records?.every((d) => d.categoryName === '川湘菜'),
  `sample=${dishes.json?.records?.[0]?.categoryName}`);

const missingCategoryId = await call('GET', `${CATALOG}/dishes`, { token: customerToken });
check('菜品列表缺 categoryId 返回 400',
  missingCategoryId.status === 400 && missingCategoryId.json?.code === 'COMMON_VALIDATION_FAILED',
  `${missingCategoryId.status} ${missingCategoryId.json?.code}`);

const unknownCategory = await call('GET', `${CATALOG}/dishes?categoryId=999999`, { token: customerToken });
check('菜品列表分类不存在返回 404 CATEGORY_NOT_FOUND',
  unknownCategory.status === 404 && unknownCategory.json?.code === 'CATEGORY_NOT_FOUND',
  `${unknownCategory.status} ${unknownCategory.json?.code}`);

// ---- 3. 顾客端菜品详情 ----
const dishDetail = await call('GET', `${CATALOG}/dishes/101`, { token: customerToken });
check('菜品详情含口味选项',
  dishDetail.status === 200 && dishDetail.json?.name === '宫保鸡丁'
  && dishDetail.json?.flavors?.length === 2 && Array.isArray(dishDetail.json.flavors[0].options),
  `flavors=${JSON.stringify(dishDetail.json?.flavors?.map((f) => f.name))}`);

const dishNotFound = await call('GET', `${CATALOG}/dishes/999999`, { token: customerToken });
check('菜品不存在返回 404 DISH_NOT_FOUND',
  dishNotFound.status === 404 && dishNotFound.json?.code === 'DISH_NOT_FOUND',
  `${dishNotFound.status} ${dishNotFound.json?.code}`);

// ---- 4. 自造分类 + 菜品:停售隐藏、分类禁用隐藏 ----
const dishCategory = await call('POST', ADMIN_CATEGORIES, {
  token: adminToken, body: { name: 'smoke-顾客分类', type: 'DISH', sortOrder: 95 },
});
const dishCategoryId = dishCategory.json?.id;
const smokeDish = await call('POST', ADMIN_DISHES, {
  token: adminToken, body: { categoryId: dishCategoryId, name: 'smoke-顾客菜', priceCents: 1500, sortOrder: 1 },
});
const smokeDishId = smokeDish.json?.id;

const hiddenWhileOffSale = await call('GET', `${CATALOG}/dishes?categoryId=${dishCategoryId}`, { token: customerToken });
check('停售菜品不出现在顾客端列表',
  hiddenWhileOffSale.status === 200 && hiddenWhileOffSale.json?.total === 0,
  `total=${hiddenWhileOffSale.json?.total}`);

const offSaleDetail = await call('GET', `${CATALOG}/dishes/${smokeDishId}`, { token: customerToken });
check('直接访问停售菜品详情返回 422 DISH_OFF_SALE',
  offSaleDetail.status === 422 && offSaleDetail.json?.code === 'DISH_OFF_SALE',
  `${offSaleDetail.status} ${offSaleDetail.json?.code}`);

await call('PATCH', `${ADMIN_DISHES}/status`, { token: adminToken, body: { ids: [smokeDishId], status: 1 } });
const visibleAfterStart = await call('GET', `${CATALOG}/dishes?categoryId=${dishCategoryId}`, { token: customerToken });
check('起售后出现在顾客端列表',
  visibleAfterStart.status === 200 && visibleAfterStart.json?.total === 1,
  `total=${visibleAfterStart.json?.total}`);

await call('PATCH', `${ADMIN_CATEGORIES}/${dishCategoryId}/status`, { token: adminToken, body: { status: 0 } });
const hiddenByCategory = await call('GET', `${CATALOG}/dishes?categoryId=${dishCategoryId}`, { token: customerToken });
const hiddenByCategoryDetail = await call('GET', `${CATALOG}/dishes/${smokeDishId}`, { token: customerToken });
check('分类被禁用后,其菜品对顾客不可见(列表空页 + 详情 422)',
  hiddenByCategory.status === 200 && hiddenByCategory.json?.total === 0
  && hiddenByCategoryDetail.status === 422 && hiddenByCategoryDetail.json?.code === 'DISH_OFF_SALE',
  `list=${hiddenByCategory.status}/${hiddenByCategory.json?.total} detail=${hiddenByCategoryDetail.status}`);

await call('PATCH', `${ADMIN_CATEGORIES}/${dishCategoryId}/status`, { token: adminToken, body: { status: 1 } });
const visibleAgain = await call('GET', `${CATALOG}/dishes/${smokeDishId}`, { token: customerToken });
check('分类恢复启用后详情又能访问', visibleAgain.status === 200, `${visibleAgain.status}`);

// ---- 5. 顾客端套餐列表 ----
const setmeals = await call('GET', `${CATALOG}/setmeals?pageSize=50`, { token: customerToken });
check('不传 categoryId 返回全部起售套餐',
  setmeals.status === 200 && setmeals.json?.total === 5
  && setmeals.json.records.every((s) => s.status === 1),
  `total=${setmeals.json?.total}`);

const setmealsByCategory = await call('GET', `${CATALOG}/setmeals?categoryId=7&pageSize=50`, { token: customerToken });
check('按分类过滤套餐',
  setmealsByCategory.status === 200 && setmealsByCategory.json?.total === 2
  && setmealsByCategory.json.records.every((s) => s.categoryId === 7),
  `total=${setmealsByCategory.json?.total}`);

/** categoryId 是可选筛选条件,给了不存在的分类应该是空页而不是 404。 */
const setmealsUnknownCategory = await call('GET', `${CATALOG}/setmeals?categoryId=999999`, { token: customerToken });
check('套餐列表给了不存在的分类返回空页(不是 404)',
  setmealsUnknownCategory.status === 200 && setmealsUnknownCategory.json?.total === 0,
  `${setmealsUnknownCategory.status} total=${setmealsUnknownCategory.json?.total}`);

// ---- 6. 顾客端套餐详情 ----
const setmealDetail = await call('GET', `${CATALOG}/setmeals/201`, { token: customerToken });
check('套餐详情含所含菜品与当前单价',
  setmealDetail.status === 200 && setmealDetail.json?.name === '单人川味套餐'
  && setmealDetail.json?.items?.length === 3
  && setmealDetail.json.items.some((i) => i.dishName === '宫保鸡丁' && i.dishPriceCents === 3800),
  `items=${JSON.stringify(setmealDetail.json?.items?.map((i) => i.dishName))}`);

const setmealNotFound = await call('GET', `${CATALOG}/setmeals/999999`, { token: customerToken });
check('套餐不存在返回 404 SETMEAL_NOT_FOUND',
  setmealNotFound.status === 404 && setmealNotFound.json?.code === 'SETMEAL_NOT_FOUND',
  `${setmealNotFound.status} ${setmealNotFound.json?.code}`);

// ---- 7. 自造套餐:停售隐藏、分类禁用隐藏 ----
const setmealCategory = await call('POST', ADMIN_CATEGORIES, {
  token: adminToken, body: { name: 'smoke-顾客套餐分类', type: 'SETMEAL', sortOrder: 95 },
});
const setmealCategoryId = setmealCategory.json?.id;
const smokeSetmeal = await call('POST', ADMIN_SETMEALS, {
  token: adminToken,
  body: { categoryId: setmealCategoryId, name: 'smoke-顾客套餐', priceCents: 3800, items: [{ dishId: 101, copies: 1 }] },
});
const smokeSetmealId = smokeSetmeal.json?.id;

const setmealHiddenOffSale = await call('GET', `${CATALOG}/setmeals?categoryId=${setmealCategoryId}`, { token: customerToken });
const setmealOffSaleDetail = await call('GET', `${CATALOG}/setmeals/${smokeSetmealId}`, { token: customerToken });
check('停售套餐不出现在列表,详情返回 422 SETMEAL_OFF_SALE',
  setmealHiddenOffSale.status === 200 && setmealHiddenOffSale.json?.total === 0
  && setmealOffSaleDetail.status === 422 && setmealOffSaleDetail.json?.code === 'SETMEAL_OFF_SALE',
  `list=${setmealHiddenOffSale.json?.total} detail=${setmealOffSaleDetail.status}`);

await call('PATCH', `${ADMIN_SETMEALS}/status`, { token: adminToken, body: { ids: [smokeSetmealId], status: 1 } });
const setmealVisible = await call('GET', `${CATALOG}/setmeals?categoryId=${setmealCategoryId}`, { token: customerToken });
check('起售后出现在顾客端套餐列表',
  setmealVisible.status === 200 && setmealVisible.json?.total === 1,
  `total=${setmealVisible.json?.total}`);

await call('PATCH', `${ADMIN_CATEGORIES}/${setmealCategoryId}/status`, { token: adminToken, body: { status: 0 } });
const setmealHiddenByCategory = await call('GET', `${CATALOG}/setmeals?categoryId=${setmealCategoryId}`, { token: customerToken });
const setmealHiddenDetail = await call('GET', `${CATALOG}/setmeals/${smokeSetmealId}`, { token: customerToken });
check('套餐分类被禁用后,套餐对顾客不可见(列表空页 + 详情 422)',
  setmealHiddenByCategory.status === 200 && setmealHiddenByCategory.json?.total === 0
  && setmealHiddenDetail.status === 422 && setmealHiddenDetail.json?.code === 'SETMEAL_OFF_SALE',
  `list=${setmealHiddenByCategory.json?.total} detail=${setmealHiddenDetail.status}`);

// ---- 8. 鉴权边界 ----
const wrongAudience = await call('GET', `${CATALOG}/dishes?categoryId=1`, { token: adminToken });
check('员工令牌打顾客端目录返回 403 AUTH_AUDIENCE_MISMATCH',
  wrongAudience.status === 403 && wrongAudience.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${wrongAudience.status} ${wrongAudience.json?.code}`);

const customerOnAdmin = await call('GET', ADMIN_DISHES, { token: customerToken });
check('顾客令牌打管理端接口返回 403 AUTH_AUDIENCE_MISMATCH',
  customerOnAdmin.status === 403 && customerOnAdmin.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${customerOnAdmin.status} ${customerOnAdmin.json?.code}`);

const noToken = await call('GET', `${CATALOG}/setmeals`);
check('无令牌返回 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 9. 清理 ----
const cleanupSetmeal = await call('DELETE', `${ADMIN_SETMEALS}?ids=${smokeSetmealId}`, { token: adminToken });
const cleanupSetmealCategory = await call('DELETE', `${ADMIN_CATEGORIES}/${setmealCategoryId}`, { token: adminToken });
const cleanupDish = await call('DELETE', `${ADMIN_DISHES}?ids=${smokeDishId}`, { token: adminToken });
const cleanupDishCategory = await call('DELETE', `${ADMIN_CATEGORIES}/${dishCategoryId}`, { token: adminToken });
check('清理:删掉自造的套餐/菜品/分类',
  cleanupSetmeal.status === 204 && cleanupSetmealCategory.status === 204
  && cleanupDish.status === 204 && cleanupDishCategory.status === 204,
  `${cleanupSetmeal.status}/${cleanupSetmealCategory.status}/${cleanupDish.status}/${cleanupDishCategory.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
