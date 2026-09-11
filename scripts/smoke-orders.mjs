// 顾客端订单(试算/下单/查询/取消/再来一单/催单)的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用(Flyway 已建表并灌入种子数据)。
// 用法:node scripts/smoke-orders.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 会创建真实订单(契约没有"删除订单"接口,订单是凭证,脚本不清除它们),但会清空购物车、
// 删除自造菜品与脚本创建的地址。用固定的 mock code,断言只针对本次创建的订单。

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
const ADDRESSES = '/api/v1/customer/addresses';
const ORDERS = '/api/v1/customer/orders';
const DISHES = '/api/v1/admin/dishes';
const SHOP = '/api/v1/admin/shop/status';

const address = {
  consignee: '订单张三', phone: '13800138000', province: '北京市', city: '北京市',
  district: '朝阳区', detail: '望京街道 1 号院 2 号楼', label: '家',
};

const admin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = admin.json?.accessToken;
const c1 = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-order-1' } });
const c2 = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-order-2' } });
const token = c1.json?.accessToken;
const otherToken = c2.json?.accessToken;
check('拿到 admin 与两个顾客令牌', !!adminToken && !!token && !!otherToken,
  `admin=${admin.status} c1=${c1.status} c2=${c2.status}`);

// ---- 0. 干净起点:清空购物车 + 准备一条地址 ----
await call('DELETE', CART, { token });
await call('DELETE', CART, { token: otherToken });
const existing = await call('GET', ADDRESSES, { token });
for (const item of existing.json ?? []) {
  await call('DELETE', `${ADDRESSES}/${item.id}`, { token });
}
const created = await call('POST', ADDRESSES, { token, body: address });
const addressId = created.json?.id;
check('准备一条本人收货地址', created.status === 201 && addressId > 0, `status=${created.status}`);

// 别的顾客也有一条地址,用于验证"地址不属于本人"
const otherAddress = await call('POST', ADDRESSES, { token: otherToken, body: address });
const otherAddressId = otherAddress.json?.id;

// ---- 1. 空购物车试算/下单 ----
const emptyPreview = await call('POST', `${ORDERS}/preview`, { token, body: { addressId } });
check('空购物车试算 → 422 ORDER_CART_EMPTY',
  emptyPreview.status === 422 && emptyPreview.json?.code === 'ORDER_CART_EMPTY',
  `${emptyPreview.status} ${emptyPreview.json?.code}`);

const emptySubmit = await call('POST', ORDERS, { token, body: { addressId } });
check('空购物车下单 → 422 ORDER_CART_EMPTY',
  emptySubmit.status === 422 && emptySubmit.json?.code === 'ORDER_CART_EMPTY',
  `${emptySubmit.status} ${emptySubmit.json?.code}`);

// ---- 2. 加购后试算 ----
await call('POST', CART, { token, body: { itemType: 'DISH', dishId: 112, quantity: 2 } });
await call('POST', CART, {
  token,
  body: { itemType: 'DISH', dishId: 101, quantity: 1, flavorChoice: [{ name: '辣度', option: '微辣' }, { name: '忌口', option: '无' }] },
});

const preview = await call('POST', `${ORDERS}/preview`, { token, body: { addressId } });
check('试算按当前库价算金额(商品合计 + 打包 200 + 配送 600)',
  preview.status === 200 && preview.json?.totalAmountCents === 4400
  && preview.json?.packAmountCents === 200 && preview.json?.deliveryAmountCents === 600
  && preview.json?.discountAmountCents === 0 && preview.json?.payAmountCents === 5200,
  `total=${preview.json?.totalAmountCents} pack=${preview.json?.packAmountCents} delivery=${preview.json?.deliveryAmountCents} pay=${preview.json?.payAmountCents}`);
check('试算返回实时明细与地址,且不落库',
  preview.json?.items?.length === 2 && preview.json.items.some((i) => i.name === '米饭')
  && preview.json?.address?.id === addressId && preview.json?.shopOpen === true,
  `items=${JSON.stringify(preview.json?.items?.map((i) => `${i.name}x${i.quantity}`))}`);

const previewNoAddress = await call('POST', `${ORDERS}/preview`, { token, body: {} });
check('试算不传地址时用默认地址', previewNoAddress.status === 200 && previewNoAddress.json?.address?.id === addressId,
  `status=${previewNoAddress.status} address=${previewNoAddress.json?.address?.id}`);

const previewForeignAddress = await call('POST', `${ORDERS}/preview`, { token, body: { addressId: otherAddressId } });
check('试算用他人地址 → 422 ORDER_ADDRESS_INVALID',
  previewForeignAddress.status === 422 && previewForeignAddress.json?.code === 'ORDER_ADDRESS_INVALID',
  `${previewForeignAddress.status} ${previewForeignAddress.json?.code}`);

// ---- 3. 下单 ----
const mismatch = await call('POST', ORDERS, {
  token, body: { addressId, expectedTotalAmountCents: 9999 },
});
check('客户端声明的合计与服务端重算不一致 → 422 ORDER_PRICE_CHANGED',
  mismatch.status === 422 && mismatch.json?.code === 'ORDER_PRICE_CHANGED',
  `${mismatch.status} ${mismatch.json?.code}`);

const submitted = await call('POST', ORDERS, {
  token, body: { addressId, remark: '不要香菜', tablewareCount: 2 },
});
const orderId = submitted.json?.id;
check('下单成功:201、待付款、金额由服务端重算、单号形如 yyyyMMddHHmm + 6 位序号',
  submitted.status === 201 && submitted.json?.status === 'PENDING_PAYMENT'
  && submitted.json?.payAmountCents === 5200 && submitted.json?.needPay === true
  && /^[0-9]{18}$/.test(submitted.json?.orderNo ?? ''),
  `${submitted.status} orderNo=${submitted.json?.orderNo} pay=${submitted.json?.payAmountCents}`);

const cartAfterSubmit = await call('GET', CART, { token });
check('R3:下单后购物车被清空',
  cartAfterSubmit.status === 200 && cartAfterSubmit.json.totalQuantity === 0,
  `qty=${cartAfterSubmit.json?.totalQuantity}`);

// 重复提交防护:重新加入同样内容再提交(10 秒内)
await call('POST', CART, { token, body: { itemType: 'DISH', dishId: 112, quantity: 2 } });
await call('POST', CART, {
  token,
  body: { itemType: 'DISH', dishId: 101, quantity: 1, flavorChoice: [{ name: '辣度', option: '微辣' }, { name: '忌口', option: '无' }] },
});
const duplicate = await call('POST', ORDERS, { token, body: { addressId, remark: '不要香菜', tablewareCount: 2 } });
check('10 秒内相同内容重复提交 → 409 ORDER_DUPLICATE_SUBMIT',
  duplicate.status === 409 && duplicate.json?.code === 'ORDER_DUPLICATE_SUBMIT',
  `${duplicate.status} ${duplicate.json?.code}`);
// 409 发生在落库之前,购物车不会被动过,这里清掉以便后面的断言从空车开始
await call('DELETE', CART, { token });

// ---- 4. 详情与快照 ----
const detail = await call('GET', `${ORDERS}/${orderId}`, { token });
const dishItem = detail.json?.items?.find((i) => i.dishId === 101);
check('详情含不可变明细快照(名/价/量/口味)',
  detail.status === 200 && detail.json?.items?.length === 2
  && dishItem?.nameSnapshot === '宫保鸡丁' && dishItem?.unitPriceCents === 3800
  && dishItem?.amountCents === 3800 && dishItem?.flavorSnapshot?.length === 2,
  `items=${JSON.stringify(detail.json?.items?.map((i) => i.nameSnapshot))}`);
check('详情含地址快照与金额构成',
  detail.json?.consignee === '订单张三' && detail.json?.phone === '13800138000'
  && detail.json?.deliveryAmountCents === 600 && detail.json?.sourceAddressId === addressId
  && detail.json?.remark === '不要香菜' && detail.json?.tablewareCount === 2
  && detail.json?.payStatus === 'UNPAID',
  `consignee=${detail.json?.consignee} sourceAddressId=${detail.json?.sourceAddressId}`);

const detailAgain = await call('GET', `${ORDERS}/${orderId}`, { token });
check('删除地址后历史订单的地址快照不受影响', detailAgain.json?.consignee === '订单张三',
  `consignee=${detailAgain.json?.consignee}`);

// ---- 5. 列表与 R9 ----
const page = await call('GET', `${ORDERS}?page=1&pageSize=50`, { token });
check('订单分页包含本次创建的订单,并带明细条数',
  page.status === 200 && page.json.records.some((o) => o.id === orderId)
  && page.json.records.find((o) => o.id === orderId)?.itemCount === 2,
  `total=${page.json?.total} itemCount=${page.json?.records?.find((o) => o.id === orderId)?.itemCount}`);

const filtered = await call('GET', `${ORDERS}?status=PENDING_PAYMENT&pageSize=50`, { token });
check('按状态筛选只返回该状态订单',
  filtered.status === 200 && filtered.json.records.every((o) => o.status === 'PENDING_PAYMENT')
  && filtered.json.records.some((o) => o.id === orderId),
  `count=${filtered.json?.records?.length}`);

const badSort = await call('GET', `${ORDERS}?sort=id,asc`, { token });
check('排序字段不在白名单 → 400 COMMON_SORT_FIELD_NOT_ALLOWED',
  badSort.status === 400 && badSort.json?.code === 'COMMON_SORT_FIELD_NOT_ALLOWED',
  `${badSort.status} ${badSort.json?.code}`);

const foreignDetail = await call('GET', `${ORDERS}/${orderId}`, { token: otherToken });
const foreignCancel = await call('POST', `${ORDERS}/${orderId}/cancellation`, { token: otherToken });
const foreignReorder = await call('POST', `${ORDERS}/${orderId}/reorder`, { token: otherToken });
check('R9:他人订单的查/取消/再来一单一律 404 ORDER_NOT_FOUND',
  [foreignDetail, foreignCancel, foreignReorder].every((r) => r.status === 404 && r.json?.code === 'ORDER_NOT_FOUND'),
  `${foreignDetail.status}/${foreignCancel.status}/${foreignReorder.status}`);

const missing = await call('GET', `${ORDERS}/999999`, { token });
check('订单不存在 → 404 ORDER_NOT_FOUND',
  missing.status === 404 && missing.json?.code === 'ORDER_NOT_FOUND',
  `${missing.status} ${missing.json?.code}`);

// ---- 6. 催单(待付款不可催) ----
const remindPending = await call('POST', `${ORDERS}/${orderId}/reminders`, { token, body: { message: '快点' } });
check('待付款订单催单 → 422 ORDER_URGE_NOT_ALLOWED',
  remindPending.status === 422 && remindPending.json?.code === 'ORDER_URGE_NOT_ALLOWED',
  `${remindPending.status} ${remindPending.json?.code}`);

// ---- 7. 取消 ----
const cancelled = await call('POST', `${ORDERS}/${orderId}/cancellation`, {
  token, body: { reason: '不想吃了' },
});
const afterCancel = await call('GET', `${ORDERS}/${orderId}`, { token });
check('待付款订单可被顾客取消:204 且写入取消方/原因/时间',
  cancelled.status === 204 && afterCancel.json?.status === 'CANCELLED'
  && afterCancel.json?.cancelSide === 'CUSTOMER' && afterCancel.json?.cancelReason === '不想吃了'
  && !!afterCancel.json?.cancelledAt,
  `status=${afterCancel.json?.status} side=${afterCancel.json?.cancelSide}`);

const cancelAgain = await call('POST', `${ORDERS}/${orderId}/cancellation`, { token });
check('重复取消(终态)→ 422 ORDER_INVALID_TRANSITION',
  cancelAgain.status === 422 && cancelAgain.json?.code === 'ORDER_INVALID_TRANSITION',
  `${cancelAgain.status} ${cancelAgain.json?.code}`);

// ---- 8. 再来一单 ----
const reorder = await call('POST', `${ORDERS}/${orderId}/reorder`, { token });
const cartAfterReorder = await call('GET', CART, { token });
check('再来一单把明细加回购物车(不自动下单)',
  reorder.status === 200 && reorder.json?.addedCount === 2 && reorder.json?.skippedItems?.length === 0
  && cartAfterReorder.json.totalQuantity === 3,
  `added=${reorder.json?.addedCount} cartQty=${cartAfterReorder.json?.totalQuantity}`);

const reorderAgain = await call('POST', `${ORDERS}/${orderId}/reorder`, { token });
check('再来一单会把同一口味合并到已有行(数量累加而不是新增行)',
  reorderAgain.status === 200 && reorderAgain.json?.addedCount === 2,
  `added=${reorderAgain.json?.addedCount}`);
await call('DELETE', CART, { token });

// ---- 9. 打烊:R1 只约束下单,试算返回 shopOpen=false ----
await call('POST', CART, { token, body: { itemType: 'DISH', dishId: 112, quantity: 1 } });
await call('PUT', SHOP, { token: adminToken, body: { isOpen: false } });
const previewClosed = await call('POST', `${ORDERS}/preview`, { token, body: { addressId } });
const submitClosed = await call('POST', ORDERS, { token, body: { addressId } });
check('打烊时试算仍 200 且 shopOpen=false,但下单 → 422 ORDER_SHOP_CLOSED',
  previewClosed.status === 200 && previewClosed.json?.shopOpen === false
  && submitClosed.status === 422 && submitClosed.json?.code === 'ORDER_SHOP_CLOSED',
  `preview=${previewClosed.status}/${previewClosed.json?.shopOpen} submit=${submitClosed.status}/${submitClosed.json?.code}`);
await call('PUT', SHOP, { token: adminToken, body: { isOpen: true } });

// ---- 10. 商品停售:下单与试算都拒绝 ----
const smokeDish = await call('POST', DISHES, {
  token: adminToken, body: { categoryId: 1, name: 'smoke-订单菜', priceCents: 1200 },
});
const smokeDishId = smokeDish.json?.id;
await call('PATCH', `${DISHES}/status`, { token: adminToken, body: { ids: [smokeDishId], status: 1 } });
await call('POST', CART, { token, body: { itemType: 'DISH', dishId: smokeDishId, quantity: 1 } });
await call('PATCH', `${DISHES}/status`, { token: adminToken, body: { ids: [smokeDishId], status: 0 } });

const previewOffSale = await call('POST', `${ORDERS}/preview`, { token, body: { addressId } });
const submitOffSale = await call('POST', ORDERS, { token, body: { addressId } });
check('购物车里有已停售商品时,试算与下单都 → 422 ORDER_ITEM_NOT_ON_SALE',
  previewOffSale.status === 422 && previewOffSale.json?.code === 'ORDER_ITEM_NOT_ON_SALE'
  && submitOffSale.status === 422 && submitOffSale.json?.code === 'ORDER_ITEM_NOT_ON_SALE',
  `preview=${previewOffSale.status}/${previewOffSale.json?.code} submit=${submitOffSale.status}/${submitOffSale.json?.code}`);
await call('DELETE', CART, { token });

// ---- 11. 鉴权 ----
const wrongAudience = await call('GET', ORDERS, { token: adminToken });
check('员工令牌打顾客订单接口 → 403 AUTH_AUDIENCE_MISMATCH',
  wrongAudience.status === 403 && wrongAudience.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${wrongAudience.status} ${wrongAudience.json?.code}`);

const noToken = await call('GET', ORDERS);
check('无令牌 → 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 12. 清理(订单本身是凭证,保留) ----
const cleanupDish = await call('DELETE', `${DISHES}?ids=${smokeDishId}`, { token: adminToken });
const cleanupAddress = await call('DELETE', `${ADDRESSES}/${addressId}`, { token });
const cleanupOtherAddress = await call('DELETE', `${ADDRESSES}/${otherAddressId}`, { token: otherToken });
check('清理:删除自造菜品与两个顾客的地址(订单保留为凭证)',
  cleanupDish.status === 204 && cleanupAddress.status === 204 && cleanupOtherAddress.status === 204,
  `${cleanupDish.status}/${cleanupAddress.status}/${cleanupOtherAddress.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
