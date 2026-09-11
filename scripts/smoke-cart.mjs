// Cart(购物车)链路的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用(Flyway 已建表并灌入种子数据)。
// 用法:node scripts/smoke-cart.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 只读种子商品;会改状态的部分用自造菜品,结束时清理,并清空脚本用过的顾客购物车。

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

const CART = '/api/v1/customer/cart/items';
const ADMIN_DISHES = '/api/v1/admin/dishes';

function flavor(name, option) {
  return { name, option };
}

// ---- 0. 令牌 ----
const adminLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = adminLogin.json?.accessToken;
const customerLogin = await call('POST', '/api/v1/customer/auth/wechat-login', {
  body: { code: 'smoke-cart-1' },
});
const customerToken = customerLogin.json?.accessToken;
const otherLogin = await call('POST', '/api/v1/customer/auth/wechat-login', {
  body: { code: 'smoke-cart-2' },
});
const otherToken = otherLogin.json?.accessToken;
check('拿到 admin / 两个顾客令牌', !!adminToken && !!customerToken && !!otherToken,
  `admin=${adminLogin.status} c1=${customerLogin.status} c2=${otherLogin.status}`);

// 从干净状态开始(同一 mock code 每次映射到同一个顾客,购物车会跨次运行保留)
await call('DELETE', CART, { token: customerToken });
await call('DELETE', CART, { token: otherToken });
const empty = await call('GET', CART, { token: customerToken });
check('空购物车返回空分组与 0 合计',
  empty.status === 200 && empty.json?.groups?.length === 0
  && empty.json.totalQuantity === 0 && empty.json.totalAmountCents === 0,
  JSON.stringify(empty.json));

// ---- 1. 加购与合并 ----
const addRice = await call('POST', CART, {
  token: customerToken, body: { itemType: 'DISH', dishId: 112, quantity: 2 },
});
check('首次加购同类商品返回 201,且带实时名称/单价/小计',
  addRice.status === 201 && addRice.json?.name === '米饭'
  && addRice.json?.unitPriceCents === 300 && addRice.json?.amountCents === 600
  && addRice.json?.available === true,
  `${addRice.status} ${JSON.stringify(addRice.json && { name: addRice.json.name, unit: addRice.json.unitPriceCents, amount: addRice.json.amountCents })}`);
const riceId = addRice.json?.id;

const addRiceAgain = await call('POST', CART, {
  token: customerToken, body: { itemType: 'DISH', dishId: 112, quantity: 1 },
});
check('同菜无口味自动合并,返回 200 且数量累加',
  addRiceAgain.status === 200 && addRiceAgain.json?.id === riceId && addRiceAgain.json?.quantity === 3,
  `${addRiceAgain.status} qty=${addRiceAgain.json?.quantity}`);

const addFlavored = await call('POST', CART, {
  token: customerToken,
  body: { itemType: 'DISH', dishId: 101, quantity: 1, flavorChoice: [flavor('辣度', '微辣'), flavor('忌口', '无')] },
});
check('带口味加购成功并回显口味',
  addFlavored.status === 201 && addFlavored.json?.flavorChoice?.length === 2
  && addFlavored.json.flavorChoice.some((f) => f.name === '辣度' && f.option === '微辣'),
  `flavors=${JSON.stringify(addFlavored.json?.flavorChoice)}`);

const reversed = await call('POST', CART, {
  token: customerToken,
  body: { itemType: 'DISH', dishId: 101, quantity: 1, flavorChoice: [flavor('忌口', '无'), flavor('辣度', '微辣')] },
});
check('口味顺序不同的同一组选择合并到同一行(归一化键与顺序无关)',
  reversed.status === 200 && reversed.json?.id === addFlavored.json?.id && reversed.json?.quantity === 2,
  `${reversed.status} sameRow=${reversed.json?.id === addFlavored.json?.id} qty=${reversed.json?.quantity}`);

const otherFlavor = await call('POST', CART, {
  token: customerToken,
  body: { itemType: 'DISH', dishId: 101, quantity: 1, flavorChoice: [flavor('辣度', '中辣'), flavor('忌口', '无')] },
});
check('不同口味选项落到不同行', otherFlavor.status === 201 && otherFlavor.json?.id !== addFlavored.json?.id,
  `${otherFlavor.status} differentRow=${otherFlavor.json?.id !== addFlavored.json?.id}`);

const addSetmeal = await call('POST', CART, {
  token: customerToken, body: { itemType: 'SETMEAL', setmealId: 201, quantity: 1 },
});
check('加购套餐成功', addSetmeal.status === 201 && addSetmeal.json?.name === '单人川味套餐',
  `${addSetmeal.status} ${addSetmeal.json?.name}`);

// ---- 2. 口味与数量校验 ----
const noFlavor = await call('POST', CART, {
  token: customerToken, body: { itemType: 'DISH', dishId: 101, quantity: 1 },
});
check('有口味配置却不选 → 422 CART_FLAVOR_REQUIRED',
  noFlavor.status === 422 && noFlavor.json?.code === 'CART_FLAVOR_REQUIRED',
  `${noFlavor.status} ${noFlavor.json?.code}`);

const partialFlavor = await call('POST', CART, {
  token: customerToken, body: { itemType: 'DISH', dishId: 101, quantity: 1, flavorChoice: [flavor('辣度', '微辣')] },
});
check('只选部分口味维度 → 422 CART_FLAVOR_REQUIRED',
  partialFlavor.status === 422 && partialFlavor.json?.code === 'CART_FLAVOR_REQUIRED',
  `${partialFlavor.status} ${partialFlavor.json?.code}`);

const badFlavor = await call('POST', CART, {
  token: customerToken,
  body: { itemType: 'DISH', dishId: 101, quantity: 1, flavorChoice: [flavor('辣度', '变态辣'), flavor('忌口', '无')] },
});
check('选项不在配置里 → 422 CART_FLAVOR_INVALID',
  badFlavor.status === 422 && badFlavor.json?.code === 'CART_FLAVOR_INVALID',
  `${badFlavor.status} ${badFlavor.json?.code}`);

const setmealFlavor = await call('POST', CART, {
  token: customerToken,
  body: { itemType: 'SETMEAL', setmealId: 201, quantity: 1, flavorChoice: [flavor('辣度', '微辣')] },
});
check('给没有口味配置的套餐传口味 → 422 CART_FLAVOR_INVALID',
  setmealFlavor.status === 422 && setmealFlavor.json?.code === 'CART_FLAVOR_INVALID',
  `${setmealFlavor.status} ${setmealFlavor.json?.code}`);

const zeroQuantity = await call('POST', CART, {
  token: customerToken, body: { itemType: 'DISH', dishId: 112, quantity: 0 },
});
check('加购数量为 0 → 422 CART_QUANTITY_INVALID',
  zeroQuantity.status === 422 && zeroQuantity.json?.code === 'CART_QUANTITY_INVALID',
  `${zeroQuantity.status} ${zeroQuantity.json?.code}`);

const mismatch = await call('POST', CART, {
  token: customerToken, body: { itemType: 'DISH', setmealId: 201, quantity: 1 },
});
check('itemType 与商品 id 矛盾 → 400 COMMON_VALIDATION_FAILED',
  mismatch.status === 400 && mismatch.json?.code === 'COMMON_VALIDATION_FAILED',
  `${mismatch.status} ${mismatch.json?.code}`);

const unknownDish = await call('POST', CART, {
  token: customerToken, body: { itemType: 'DISH', dishId: 999999, quantity: 1 },
});
check('菜品不存在 → 404 DISH_NOT_FOUND',
  unknownDish.status === 404 && unknownDish.json?.code === 'DISH_NOT_FOUND',
  `${unknownDish.status} ${unknownDish.json?.code}`);

const unknownSetmeal = await call('POST', CART, {
  token: customerToken, body: { itemType: 'SETMEAL', setmealId: 999999, quantity: 1 },
});
check('套餐不存在 → 404 SETMEAL_NOT_FOUND',
  unknownSetmeal.status === 404 && unknownSetmeal.json?.code === 'SETMEAL_NOT_FOUND',
  `${unknownSetmeal.status} ${unknownSetmeal.json?.code}`);

// 合并后超过 99 → 422(用没有口味配置的菜品 110,否则会先撞上 CART_FLAVOR_REQUIRED)
const bulk = await call('POST', CART, { token: customerToken, body: { itemType: 'DISH', dishId: 110, quantity: 99 } });
const overflow = await call('POST', CART, { token: customerToken, body: { itemType: 'DISH', dishId: 110, quantity: 1 } });
check('合并后超过上限 99 → 422 CART_QUANTITY_INVALID',
  bulk.status === 201 && overflow.status === 422 && overflow.json?.code === 'CART_QUANTITY_INVALID',
  `bulk=${bulk.status} overflow=${overflow.status}/${overflow.json?.code}`);

// ---- 3. 购物车视图 ----
const view = await call('GET', CART, { token: customerToken });
const allItems = view.json?.groups?.flatMap((g) => g.items) ?? [];
const sumQuantity = allItems.reduce((sum, i) => sum + i.quantity, 0);
const sumAmount = allItems.reduce((sum, i) => sum + i.amountCents, 0);
check('购物车按分类分组,组内分类名与商品实时信息齐全',
  view.status === 200 && view.json.groups.length >= 3
  && view.json.groups.every((g) => g.categoryName && g.items.length > 0)
  && allItems.every((i) => i.name && i.unitPriceCents > 0 && i.amountCents === i.unitPriceCents * i.quantity)
  && allItems.some((i) => i.dishId === 101) && allItems.some((i) => i.setmealId === 201),
  `groups=${JSON.stringify(view.json?.groups?.map((g) => g.categoryName))}`);
check('合计与各行之和一致',
  view.json.totalQuantity === sumQuantity && view.json.totalAmountCents === sumAmount,
  `qty=${view.json?.totalQuantity}/${sumQuantity} amount=${view.json?.totalAmountCents}/${sumAmount}`);

// ---- 4. 改量 ----
const updated = await call('PUT', `${CART}/${riceId}/quantity`, {
  token: customerToken, body: { quantity: 5 },
});
check('覆盖式改量返回 200 并带新的小计',
  updated.status === 200 && updated.json?.quantity === 5 && updated.json?.amountCents === 1500,
  `${updated.status} qty=${updated.json?.quantity} amount=${updated.json?.amountCents}`);

const badQuantity = await call('PUT', `${CART}/${riceId}/quantity`, {
  token: customerToken, body: { quantity: 100 },
});
const negativeQuantity = await call('PUT', `${CART}/${riceId}/quantity`, {
  token: customerToken, body: { quantity: -1 },
});
check('改量超过 99 或为负 → 422 CART_QUANTITY_INVALID',
  badQuantity.status === 422 && badQuantity.json?.code === 'CART_QUANTITY_INVALID'
  && negativeQuantity.status === 422 && negativeQuantity.json?.code === 'CART_QUANTITY_INVALID',
  `${badQuantity.status}/${badQuantity.json?.code} ${negativeQuantity.status}/${negativeQuantity.json?.code}`);

// R9:另一个顾客的行不可操作
const otherAdd = await call('POST', CART, { token: otherToken, body: { itemType: 'DISH', dishId: 112, quantity: 1 } });
const otherRowId = otherAdd.json?.id;
const crossCustomer = await call('PUT', `${CART}/${otherRowId}/quantity`, {
  token: customerToken, body: { quantity: 2 },
});
check('操作他人购物车行 → 404 CART_ITEM_NOT_FOUND(不泄露存在性)',
  crossCustomer.status === 404 && crossCustomer.json?.code === 'CART_ITEM_NOT_FOUND',
  `${crossCustomer.status} ${crossCustomer.json?.code}`);

const missingRow = await call('PUT', `${CART}/999999/quantity`, {
  token: customerToken, body: { quantity: 2 },
});
check('改量不存在的行 → 404 CART_ITEM_NOT_FOUND',
  missingRow.status === 404 && missingRow.json?.code === 'CART_ITEM_NOT_FOUND',
  `${missingRow.status} ${missingRow.json?.code}`);

const removed = await call('PUT', `${CART}/${riceId}/quantity`, { token: customerToken, body: { quantity: 0 } });
const viewAfterRemove = await call('GET', CART, { token: customerToken });
const rowStillThere = viewAfterRemove.json.groups.flatMap((g) => g.items).some((i) => i.id === riceId);
check('数量置 0 → 204 且该行消失',
  removed.status === 204 && !rowStillThere,
  `status=${removed.status} stillThere=${rowStillThere}`);

// ---- 5. 下架商品在视图里保留但标记不可用 ----
const smokeDish = await call('POST', ADMIN_DISHES, {
  token: adminToken, body: { categoryId: 1, name: 'smoke-购物车菜', priceCents: 1200 },
});
const smokeDishId = smokeDish.json?.id;
const addOffSale = await call('POST', CART, {
  token: customerToken, body: { itemType: 'DISH', dishId: smokeDishId, quantity: 1 },
});
check('停售商品不能加购 → 422 CART_ITEM_OFF_SALE',
  addOffSale.status === 422 && addOffSale.json?.code === 'CART_ITEM_OFF_SALE',
  `${addOffSale.status} ${addOffSale.json?.code}`);

await call('PATCH', `${ADMIN_DISHES}/status`, { token: adminToken, body: { ids: [smokeDishId], status: 1 } });
const addThenOnSale = await call('POST', CART, {
  token: customerToken, body: { itemType: 'DISH', dishId: smokeDishId, quantity: 1 },
});
await call('PATCH', `${ADMIN_DISHES}/status`, { token: adminToken, body: { ids: [smokeDishId], status: 0 } });
const viewWithOffSale = await call('GET', CART, { token: customerToken });
const offSaleRow = viewWithOffSale.json.groups.flatMap((g) => g.items).find((i) => i.dishId === smokeDishId);
check('下架后行仍在,但 available=false 且带原因',
  addThenOnSale.status === 201 && offSaleRow && offSaleRow.available === false
  && typeof offSaleRow.unavailableReason === 'string' && offSaleRow.unavailableReason.length > 0,
  `available=${offSaleRow?.available} reason=${offSaleRow?.unavailableReason}`);

await call('PUT', `${CART}/${offSaleRow.id}/quantity`, { token: customerToken, body: { quantity: 0 } });

// ---- 6. 清空 ----
const cleared = await call('DELETE', CART, { token: customerToken });
const afterClear = await call('GET', CART, { token: customerToken });
const clearedAgain = await call('DELETE', CART, { token: customerToken });
check('清空购物车 204、幂等,清空后为空视图',
  cleared.status === 204 && afterClear.json.groups.length === 0
  && afterClear.json.totalQuantity === 0 && clearedAgain.status === 204,
  `clear=${cleared.status} again=${clearedAgain.status} groups=${afterClear.json?.groups?.length}`);

// ---- 7. 鉴权 ----
const wrongAudience = await call('GET', CART, { token: adminToken });
check('员工令牌打购物车返回 403 AUTH_AUDIENCE_MISMATCH',
  wrongAudience.status === 403 && wrongAudience.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${wrongAudience.status} ${wrongAudience.json?.code}`);

const noToken = await call('GET', CART);
check('无令牌返回 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 8. 清理 ----
await call('DELETE', CART, { token: otherToken });
const cleanupDish = await call('DELETE', `${ADMIN_DISHES}?ids=${smokeDishId}`, { token: adminToken });
check('清理:两个顾客的购物车已清空,自造菜品已删除', cleanupDish.status === 204, `dish=${cleanupDish.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
