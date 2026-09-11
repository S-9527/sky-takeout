// 管理端订单(查询/统计/接单/拒单/派送/完成/取消)的端到端冒烟验证。
//
// 依赖:后端已在跑,支付渠道为 mock(默认),MySQL/Redis 可用。
// 用法:node scripts/smoke-admin-orders.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 会创建真实订单、支付与退款(凭证,不清除),但会清空购物车、删除脚本创建的地址。

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
const ADMIN_ORDERS = '/api/v1/admin/orders';
const PHONE = '13700137000';

const adminLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const staffLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'zhangsan', password: '123456' },
});
const adminToken = adminLogin.json?.accessToken;
const staffToken = staffLogin.json?.accessToken;
const buyer = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-admin-order-1' } });
const token = buyer.json?.accessToken;
// 登录响应只有令牌;资料要另外拿(这是契约的刻意设计)
const buyerProfile = await call('GET', '/api/v1/customer/profile', { token });
const buyerId = buyerProfile.json?.id;
check('拿到 admin / staff / 顾客令牌', !!adminToken && !!staffToken && !!token,
  `admin=${adminLogin.status} staff=${staffLogin.status} buyer=${buyer.status}`);

await call('DELETE', CART, { token });
for (const item of (await call('GET', ADDRESSES, { token })).json ?? []) {
  await call('DELETE', `${ADDRESSES}/${item.id}`, { token });
}
const address = await call('POST', ADDRESSES, {
  token,
  body: {
    consignee: '管理端张三', phone: PHONE, province: '北京市', city: '北京市',
    district: '朝阳区', detail: '望京街道 1 号院', label: '家',
  },
});
const addressId = address.json?.id;

/** 下单并(可选)用 mock 通道支付,返回订单对象。 */
async function newOrder({ pay = true, remark } = {}) {
  await call('POST', CART, { token, body: { itemType: 'DISH', dishId: 112, quantity: 1 } });
  const created = await call('POST', ORDERS, { token, body: { addressId, remark } });
  const orderId = created.json?.id;
  if (pay) {
    await call('POST', `${ORDERS}/${orderId}/payments`, { token, body: { channel: 'MOCK' } });
  }
  return { id: orderId, orderNo: created.json?.orderNo };
}

// ---- 1. 造单:已完成 / 拒单 / 商家取消 / 未支付 / 派送中 ----
const toComplete = await newOrder({ remark: '走完整流程' });
const toReject = await newOrder({ remark: '将被拒单' });
const toCancel = await newOrder({ remark: '将被商家取消' });
const unpaid = await newOrder({ pay: false, remark: '未支付' });
const delivering = await newOrder({ remark: '派送中不可取消' });
check('准备 5 张订单(4 张已支付 + 1 张未支付)',
  [toComplete, toReject, toCancel, unpaid, delivering].every((o) => o.id > 0),
  `ids=${[toComplete.id, toReject.id, toCancel.id, unpaid.id, delivering.id].join(',')}`);

// ---- 2. 接单 → 派送 → 完成(STAFT 也可以操作) ----
const acceptByStaff = await call('POST', `${ADMIN_ORDERS}/${toComplete.id}/acceptance`, { token: staffToken });
check('STAFF 可以接单 → 204', acceptByStaff.status === 204, `${acceptByStaff.status}`);

const acceptAgain = await call('POST', `${ADMIN_ORDERS}/${toComplete.id}/acceptance`, { token: adminToken });
check('重复接单 → 422 ORDER_INVALID_TRANSITION',
  acceptAgain.status === 422 && acceptAgain.json?.code === 'ORDER_INVALID_TRANSITION',
  `${acceptAgain.status} ${acceptAgain.json?.code}`);

const acceptUnpaid = await call('POST', `${ADMIN_ORDERS}/${unpaid.id}/acceptance`, { token: adminToken });
check('待付款订单接单 → 422 ORDER_INVALID_TRANSITION(必须先支付)',
  acceptUnpaid.status === 422 && acceptUnpaid.json?.code === 'ORDER_INVALID_TRANSITION',
  `${acceptUnpaid.status} ${acceptUnpaid.json?.code}`);

const delivery = await call('POST', `${ADMIN_ORDERS}/${toComplete.id}/delivery`, { token: adminToken });
const complete = await call('POST', `${ADMIN_ORDERS}/${toComplete.id}/completion`, { token: adminToken });
const completedDetail = await call('GET', `${ADMIN_ORDERS}/${toComplete.id}`, { token: adminToken });
check('接单→派送→完成:状态与各阶段时间戳都写入',
  delivery.status === 204 && complete.status === 204 && completedDetail.json?.status === 'COMPLETED'
  && !!completedDetail.json?.acceptedAt && !!completedDetail.json?.deliveringAt
  && !!completedDetail.json?.completedAt,
  `status=${completedDetail.json?.status}`);

const completeEarly = await call('POST', `${ADMIN_ORDERS}/${toReject.id}/completion`, { token: adminToken });
check('待接单直接完成 → 422 ORDER_INVALID_TRANSITION(不得跳级)',
  completeEarly.status === 422 && completeEarly.json?.code === 'ORDER_INVALID_TRANSITION',
  `${completeEarly.status} ${completeEarly.json?.code}`);

// ---- 3. R8:已完成订单不可退款 ----
const refundCompleted = await call('POST', '/api/v1/admin/refunds', {
  token: adminToken, body: { orderNo: toComplete.orderNo, reason: '已完成还想退' },
});
check('R8:已完成订单退款 → 422 PAY_ORDER_NOT_REFUNDABLE',
  refundCompleted.status === 422 && refundCompleted.json?.code === 'PAY_ORDER_NOT_REFUNDABLE',
  `${refundCompleted.status} ${refundCompleted.json?.code}`);

// ---- 4. R6:拒单必须先退款 ----
const rejectNoReason = await call('POST', `${ADMIN_ORDERS}/${toReject.id}/rejection`, {
  token: adminToken, body: {},
});
check('拒单缺原因 → 400 且带字段级 details',
  rejectNoReason.status === 400 && rejectNoReason.json?.code === 'COMMON_VALIDATION_FAILED'
  && (rejectNoReason.json?.details?.length ?? 0) > 0,
  `details=${JSON.stringify(rejectNoReason.json?.details)}`);

const rejected = await call('POST', `${ADMIN_ORDERS}/${toReject.id}/rejection`, {
  token: adminToken, body: { reason: '菜品已售完' },
});
const rejectedDetail = await call('GET', `${ADMIN_ORDERS}/${toReject.id}`, { token: adminToken });
const rejectRefund = await call('GET', `/api/v1/admin/refunds?orderNo=${toReject.orderNo}`, { token: adminToken });
check('拒单成功:订单取消(MERCHANT)且已支付订单产生退款记录',
  rejected.status === 204 && rejectedDetail.json?.status === 'CANCELLED'
  && rejectedDetail.json?.cancelSide === 'MERCHANT' && rejectedDetail.json?.cancelReason === '菜品已售完'
  && rejectedDetail.json?.payStatus === 'REFUNDED'
  && rejectRefund.json?.records?.length === 1
  && rejectRefund.json.records[0].reasonType === 'MERCHANT_REJECT',
  `status=${rejectedDetail.json?.status} payStatus=${rejectedDetail.json?.payStatus} refunds=${rejectRefund.json?.records?.length}`);

const rejectAgain = await call('POST', `${ADMIN_ORDERS}/${toReject.id}/rejection`, {
  token: adminToken, body: { reason: '再拒一次' },
});
check('已取消订单再拒单 → 422 ORDER_INVALID_TRANSITION',
  rejectAgain.status === 422 && rejectAgain.json?.code === 'ORDER_INVALID_TRANSITION',
  `${rejectAgain.status} ${rejectAgain.json?.code}`);

// ---- 5. 商家取消 ----
const cancelAccepted = await call('POST', `${ADMIN_ORDERS}/${toCancel.id}/acceptance`, { token: adminToken });
const cancelled = await call('POST', `${ADMIN_ORDERS}/${toCancel.id}/cancellation`, {
  token: adminToken, body: { reason: '顾客电话取消' },
});
const cancelledDetail = await call('GET', `${ADMIN_ORDERS}/${toCancel.id}`, { token: adminToken });
const cancelRefund = await call('GET', `/api/v1/admin/refunds?orderNo=${toCancel.orderNo}`, { token: adminToken });
check('已接单订单可被商家取消:退款 + CANCELLED + MERCHANT_CANCEL',
  cancelAccepted.status === 204 && cancelled.status === 204
  && cancelledDetail.json?.status === 'CANCELLED' && cancelledDetail.json?.payStatus === 'REFUNDED'
  && cancelRefund.json?.records?.[0]?.reasonType === 'MERCHANT_CANCEL',
  `status=${cancelledDetail.json?.status} payStatus=${cancelledDetail.json?.payStatus}`);

const cancelUnpaid = await call('POST', `${ADMIN_ORDERS}/${unpaid.id}/cancellation`, {
  token: adminToken, body: { reason: '商家想取消未支付单' },
});
check('商家取消待付款订单 → 422 ORDER_CANNOT_CANCEL(那是顾客/系统的事)',
  cancelUnpaid.status === 422 && cancelUnpaid.json?.code === 'ORDER_CANNOT_CANCEL',
  `${cancelUnpaid.status} ${cancelUnpaid.json?.code}`);

await call('POST', `${ADMIN_ORDERS}/${delivering.id}/acceptance`, { token: adminToken });
await call('POST', `${ADMIN_ORDERS}/${delivering.id}/delivery`, { token: adminToken });
const cancelDelivering = await call('POST', `${ADMIN_ORDERS}/${delivering.id}/cancellation`, {
  token: adminToken, body: { reason: '已经出餐还想取消' },
});
check('派送中不可取消 → 422 ORDER_INVALID_TRANSITION(且不产生退款)',
  cancelDelivering.status === 422 && cancelDelivering.json?.code === 'ORDER_INVALID_TRANSITION'
  && (await call('GET', `/api/v1/admin/refunds?orderNo=${delivering.orderNo}`, { token: adminToken })).json.records.length === 0,
  `${cancelDelivering.status} ${cancelDelivering.json?.code}`);

// ---- 6. 分页与统计 ----
const byStatus = await call('GET', `${ADMIN_ORDERS}?status=COMPLETED&pageSize=50`, { token: adminToken });
check('按状态分页包含刚完成的订单,并带明细条数',
  byStatus.status === 200 && byStatus.json.records.some((o) => o.id === toComplete.id)
  && byStatus.json.records.find((o) => o.id === toComplete.id)?.itemCount === 1,
  `total=${byStatus.json?.total}`);

const byOrderNo = await call('GET', `${ADMIN_ORDERS}?orderNo=${toComplete.orderNo}`, { token: adminToken });
check('按订单号精确匹配只返回一单',
  byOrderNo.status === 200 && byOrderNo.json.records.length === 1 && byOrderNo.json.records[0].id === toComplete.id,
  `count=${byOrderNo.json?.records?.length}`);

const byPhone = await call('GET', `${ADMIN_ORDERS}?phone=${PHONE}&pageSize=50`, { token: adminToken });
check('按收货人手机号(地址快照)检索',
  byPhone.status === 200 && byPhone.json.records.length >= 5
  && byPhone.json.records.every((o) => o.phone === PHONE),
  `count=${byPhone.json?.records?.length}`);

const counts = await call('GET', `${ADMIN_ORDERS}/status-counts`, { token: adminToken });
const sum = counts.json
  ? counts.json.pendingPayment + counts.json.pendingAcceptance + counts.json.accepted
    + counts.json.delivering + counts.json.completed + counts.json.cancelled
  : -1;
check('各状态计数:all 等于各状态之和,且包含刚完成/取消的订单',
  counts.status === 200 && counts.json.all === sum && counts.json.completed >= 1
  && counts.json.cancelled >= 2 && counts.json.all > 0,
  `all=${counts.json?.all} sum=${sum} completed=${counts.json?.completed} cancelled=${counts.json?.cancelled}`);

const badSort = await call('GET', `${ADMIN_ORDERS}?sort=id,asc`, { token: adminToken });
check('排序字段不在白名单 → 400 COMMON_SORT_FIELD_NOT_ALLOWED',
  badSort.status === 400 && badSort.json?.code === 'COMMON_SORT_FIELD_NOT_ALLOWED',
  `${badSort.status} ${badSort.json?.code}`);

// 报表规则:起止必须同时给、起 ≤ 止、跨度 ≤ 366 天
const halfRange = await call('GET', `${ADMIN_ORDERS}?beginDate=2025-01-01`, { token: adminToken });
const reversedRange = await call('GET', `${ADMIN_ORDERS}?beginDate=2025-02-01&endDate=2025-01-01`, { token: adminToken });
const tooLarge = await call('GET', `${ADMIN_ORDERS}?beginDate=2024-01-01&endDate=2025-01-01`, { token: adminToken });
check('日期区间规则:缺一半/起>止 → 400 REPORT_DATE_RANGE_INVALID,跨度超 366 天 → TOO_LARGE',
  halfRange.status === 400 && halfRange.json?.code === 'REPORT_DATE_RANGE_INVALID'
  && reversedRange.status === 400 && reversedRange.json?.code === 'REPORT_DATE_RANGE_INVALID'
  && tooLarge.status === 400 && tooLarge.json?.code === 'REPORT_DATE_RANGE_TOO_LARGE',
  `${halfRange.json?.code}/${reversedRange.json?.code}/${tooLarge.json?.code}`);

const isoDay = (date) => date.toISOString().slice(0, 10);
const today = new Date();
const weekAgo = new Date(today.getTime() - 7 * 24 * 3600 * 1000);
const validRange = await call('GET', `${ADMIN_ORDERS}?beginDate=${isoDay(weekAgo)}&endDate=${isoDay(today)}&pageSize=1`, { token: adminToken });
check('合法区间(7 天,含首含尾)正常返回', validRange.status === 200, `${validRange.status} ${validRange.json?.code ?? ''}`);

// ---- 7. 管理端不受 R9 限制 ----
const adminDetail = await call('GET', `${ADMIN_ORDERS}/${toComplete.id}`, { token: adminToken });
check('管理端可以看任意顾客的订单详情(不受 R9 限制)',
  adminDetail.status === 200 && adminDetail.json?.customerId === buyerId && buyerId > 0,
  `customerId=${adminDetail.json?.customerId}`);

const missing = await call('GET', `${ADMIN_ORDERS}/999999`, { token: adminToken });
const missingAccept = await call('POST', `${ADMIN_ORDERS}/999999/acceptance`, { token: adminToken });
check('不存在的订单 → 404 ORDER_NOT_FOUND(查询与操作一致)',
  missing.status === 404 && missing.json?.code === 'ORDER_NOT_FOUND'
  && missingAccept.status === 404 && missingAccept.json?.code === 'ORDER_NOT_FOUND',
  `${missing.status}/${missingAccept.status}`);

// ---- 8. 鉴权 ----
const customerOnAdmin = await call('GET', ADMIN_ORDERS, { token });
check('顾客令牌打管理端订单 → 403 AUTH_AUDIENCE_MISMATCH',
  customerOnAdmin.status === 403 && customerOnAdmin.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${customerOnAdmin.status} ${customerOnAdmin.json?.code}`);

const noToken = await call('GET', ADMIN_ORDERS);
check('无令牌 → 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 9. 清理 ----
await call('DELETE', CART, { token });
const cleanupAddress = await call('DELETE', `${ADDRESSES}/${addressId}`, { token });
check('清理:删除脚本创建的地址', cleanupAddress.status === 204, `address=${cleanupAddress.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
