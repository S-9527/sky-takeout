// 支付与退款(mock 通道)的端到端冒烟验证。
//
// 依赖:后端已在跑,且 MySQL/Redis 可用;支付渠道为 mock(默认),发起即成功。
// 用法:node scripts/smoke-payments.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 会创建真实订单与退款记录(都是凭证,不清除),但会清空购物车、删除脚本创建的地址。

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
const REFUNDS = '/api/v1/admin/refunds';

const adminLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const staffLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'zhangsan', password: '123456' },
});
const adminToken = adminLogin.json?.accessToken;
const staffToken = staffLogin.json?.accessToken;
const c1 = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-pay-1' } });
const c2 = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-pay-2' } });
const token = c1.json?.accessToken;
const otherToken = c2.json?.accessToken;
check('拿到 admin / staff / 两个顾客令牌', !!adminToken && !!staffToken && !!token && !!otherToken,
  `admin=${adminLogin.status} staff=${staffLogin.status} c1=${c1.status} c2=${c2.status}`);

// ---- 0. 造两单:一单用来支付+退款,一单保持未支付 ----
await call('DELETE', CART, { token });
const existingAddresses = await call('GET', ADDRESSES, { token });
for (const item of existingAddresses.json ?? []) {
  await call('DELETE', `${ADDRESSES}/${item.id}`, { token });
}
const address = await call('POST', ADDRESSES, {
  token,
  body: {
    consignee: '支付张三', phone: '13800138000', province: '北京市', city: '北京市',
    district: '朝阳区', detail: '望京街道 1 号院', label: '家',
  },
});
const addressId = address.json?.id;

await call('POST', CART, { token, body: { itemType: 'DISH', dishId: 112, quantity: 2 } });
const paidOrder = await call('POST', ORDERS, { token, body: { addressId } });
const paidOrderId = paidOrder.json?.id;
const paidOrderNo = paidOrder.json?.orderNo;
const payAmount = paidOrder.json?.payAmountCents;

await call('POST', CART, { token, body: { itemType: 'DISH', dishId: 112, quantity: 1 } });
const unpaidOrder = await call('POST', ORDERS, { token, body: { addressId, remark: '第二单' } });
const unpaidOrderId = unpaidOrder.json?.id;
check('准备一单待支付与一单保持未支付', paidOrder.status === 201 && unpaidOrder.status === 201
  && payAmount > 0, `paid=${paidOrderId}/${paidOrderNo} pay=${payAmount} unpaid=${unpaidOrderId}`);

const PAYMENTS = `${ORDERS}/${paidOrderId}/payments`;

// ---- 1. 发起支付前 ----
const beforePay = await call('GET', `${PAYMENTS}/status`, { token });
check('未发起支付时轮询:payment 为 null,payStatus=UNPAID',
  beforePay.status === 200 && beforePay.json?.payment === null && beforePay.json?.payStatus === 'UNPAID',
  `status=${beforePay.status} payStatus=${beforePay.json?.payStatus}`);

const wrongChannel = await call('POST', PAYMENTS, { token, body: { channel: 'WECHAT' } });
check('请求未启用的渠道(WECHAT)→ 400(当前部署只有 mock)',
  wrongChannel.status === 400 && wrongChannel.json?.code === 'COMMON_VALIDATION_FAILED',
  `${wrongChannel.status} ${wrongChannel.json?.code}`);

// ---- 2. 发起支付(mock 发起即成功) ----
const started = await call('POST', PAYMENTS, { token, body: { channel: 'MOCK' } });
check('mock 通道发起支付即成功:201、status=SUCCESS、金额等于订单实付',
  started.status === 201 && started.json?.status === 'SUCCESS'
  && started.json?.payAmountCents === payAmount && started.json?.paymentId > 0,
  `${started.status} status=${started.json?.status} amount=${started.json?.payAmountCents}`);
check('不谎报不存在的模拟回调地址(mockPayUrl 为 null)', started.json?.mockPayUrl === null,
  `mockPayUrl=${started.json?.mockPayUrl}`);

const afterPay = await call('GET', `${PAYMENTS}/status`, { token });
check('轮询返回最近一笔支付与订单支付状态',
  afterPay.json?.payment?.status === 'SUCCESS' && afterPay.json?.payment?.transactionId
  && afterPay.json?.payStatus === 'PAID',
  `payment=${afterPay.json?.payment?.status} payStatus=${afterPay.json?.payStatus}`);

const paidDetail = await call('GET', `${ORDERS}/${paidOrderId}`, { token });
check('支付成功后订单进入待接单,写 payStatus/payMethod/paidAt',
  paidDetail.json?.status === 'PENDING_ACCEPTANCE' && paidDetail.json?.payStatus === 'PAID'
  && paidDetail.json?.payMethod === 'MOCK' && !!paidDetail.json?.paidAt,
  `status=${paidDetail.json?.status} payStatus=${paidDetail.json?.payStatus} payMethod=${paidDetail.json?.payMethod}`);

const payAgain = await call('POST', PAYMENTS, { token, body: { channel: 'MOCK' } });
check('已支付订单再次发起支付 → 409 PAY_DUPLICATE_PAYMENT',
  payAgain.status === 409 && payAgain.json?.code === 'PAY_DUPLICATE_PAYMENT',
  `${payAgain.status} ${payAgain.json?.code}`);

const unpaidPay = await call('GET', `${ORDERS}/${unpaidOrderId}/payments/status`, { token });
check('未支付订单仍可正常轮询(payStatus=UNPAID)', unpaidPay.json?.payStatus === 'UNPAID',
  `payStatus=${unpaidPay.json?.payStatus}`);

// ---- 3. R9:他人订单的支付接口 ----
const foreignStatus = await call('GET', `${PAYMENTS}/status`, { token: otherToken });
const foreignStart = await call('POST', PAYMENTS, { token: otherToken, body: { channel: 'MOCK' } });
check('R9:他人订单的支付发起/轮询一律 404 ORDER_NOT_FOUND',
  foreignStatus.status === 404 && foreignStatus.json?.code === 'ORDER_NOT_FOUND'
  && foreignStart.status === 404 && foreignStart.json?.code === 'ORDER_NOT_FOUND',
  `${foreignStatus.status}/${foreignStart.status}`);

// ---- 4. 退款权限:仅 ADMIN ----
const staffList = await call('GET', REFUNDS, { token: staffToken });
const staffCreate = await call('POST', REFUNDS, {
  token: staffToken, body: { orderNo: paidOrderNo, reason: '员工尝试退款' },
});
check('退款接口仅 ADMIN:STAFF 令牌 → 403 AUTH_PERMISSION_DENIED',
  staffList.status === 403 && staffList.json?.code === 'AUTH_PERMISSION_DENIED'
  && staffCreate.status === 403,
  `${staffList.status}/${staffList.json?.code} create=${staffCreate.status}`);

// ---- 5. 退款的校验 ----
const unknownOrder = await call('POST', REFUNDS, {
  token: adminToken, body: { orderNo: '999999999999999999', reason: '不存在的订单' },
});
check('退款订单号不存在 → 404 PAY_ORDER_NOT_FOUND',
  unknownOrder.status === 404 && unknownOrder.json?.code === 'PAY_ORDER_NOT_FOUND',
  `${unknownOrder.status} ${unknownOrder.json?.code}`);

const unpaidRefund = await call('POST', REFUNDS, {
  token: adminToken, body: { orderNo: unpaidOrder.json?.orderNo, reason: '未支付却退款' },
});
check('未支付订单退款 → 422 PAY_ORDER_NOT_PAID',
  unpaidRefund.status === 422 && unpaidRefund.json?.code === 'PAY_ORDER_NOT_PAID',
  `${unpaidRefund.status} ${unpaidRefund.json?.code}`);

const amountMismatch = await call('POST', REFUNDS, {
  token: adminToken,
  body: { orderNo: paidOrderNo, reason: '金额不符', expectedAmountCents: payAmount + 1 },
});
check('退款金额与实付不一致 → 422 PAY_REFUND_AMOUNT_EXCEEDED',
  amountMismatch.status === 422 && amountMismatch.json?.code === 'PAY_REFUND_AMOUNT_EXCEEDED',
  `${amountMismatch.status} ${amountMismatch.json?.code}`);

const missingReason = await call('POST', REFUNDS, { token: adminToken, body: { orderNo: paidOrderNo } });
check('缺原因 → 400 且带字段级 details',
  missingReason.status === 400 && missingReason.json?.code === 'COMMON_VALIDATION_FAILED'
  && (missingReason.json?.details?.length ?? 0) > 0,
  `details=${JSON.stringify(missingReason.json?.details)}`);

// ---- 6. 发起退款(mock 受理即成功) ----
const refund = await call('POST', REFUNDS, {
  token: adminToken,
  body: {
    orderNo: paidOrderNo, reason: '商家拒单', reasonType: 'MERCHANT_REJECT',
    expectedAmountCents: payAmount,
  },
});
check('退款受理成功:201、SUCCESS、金额等于订单实付、单号形如 RF+时间+序号',
  refund.status === 201 && refund.json?.status === 'SUCCESS'
  && refund.json?.amountCents === payAmount && /^RF[0-9]{18}$/.test(refund.json?.refundNo ?? '')
  && refund.json?.orderNo === paidOrderNo,
  `${refund.status} refundNo=${refund.json?.refundNo} amount=${refund.json?.amountCents}`);

const afterRefund = await call('GET', `${ORDERS}/${paidOrderId}`, { token });
check('退款成功后订单 payStatus=REFUNDED(订单状态不动)',
  afterRefund.json?.payStatus === 'REFUNDED' && afterRefund.json?.status === 'PENDING_ACCEPTANCE',
  `payStatus=${afterRefund.json?.payStatus} status=${afterRefund.json?.status}`);

const refundAgain = await call('POST', REFUNDS, {
  token: adminToken, body: { orderNo: paidOrderNo, reason: '再退一次' },
});
check('已有成功退款再发起 → 409 PAY_REFUND_ALREADY_EXISTS',
  refundAgain.status === 409 && refundAgain.json?.code === 'PAY_REFUND_ALREADY_EXISTS',
  `${refundAgain.status} ${refundAgain.json?.code}`);

// ---- 7. 退款列表 ----
const refundPage = await call('GET', `${REFUNDS}?page=1&pageSize=50&orderNo=${paidOrderNo}`, { token: adminToken });
check('按订单号查退款记录,行里带 orderNo 与状态',
  refundPage.status === 200 && refundPage.json.records.length === 1
  && refundPage.json.records[0].refundNo === refund.json?.refundNo
  && refundPage.json.records[0].status === 'SUCCESS',
  `count=${refundPage.json?.records?.length} orderNo=${refundPage.json?.records?.[0]?.orderNo}`);

const refundByStatus = await call('GET', `${REFUNDS}?status=SUCCESS&pageSize=50`, { token: adminToken });
check('按状态筛选退款记录',
  refundByStatus.status === 200 && refundByStatus.json.records.every((r) => r.status === 'SUCCESS')
  && refundByStatus.json.records.some((r) => r.refundNo === refund.json?.refundNo),
  `count=${refundByStatus.json?.records?.length}`);

const unknownOrderRefunds = await call('GET', `${REFUNDS}?orderNo=999999999999999999`, { token: adminToken });
check('按不存在的订单号查退款 → 空页而不是 404',
  unknownOrderRefunds.status === 200 && unknownOrderRefunds.json.records.length === 0,
  `status=${unknownOrderRefunds.status} count=${unknownOrderRefunds.json?.records?.length}`);

const badSort = await call('GET', `${REFUNDS}?sort=id,asc`, { token: adminToken });
check('退款列表排序字段不在白名单 → 400 COMMON_SORT_FIELD_NOT_ALLOWED',
  badSort.status === 400 && badSort.json?.code === 'COMMON_SORT_FIELD_NOT_ALLOWED',
  `${badSort.status} ${badSort.json?.code}`);

// ---- 8. 鉴权边界 ----
const customerOnRefunds = await call('GET', REFUNDS, { token });
check('顾客令牌打退款接口 → 403 AUTH_AUDIENCE_MISMATCH',
  customerOnRefunds.status === 403 && customerOnRefunds.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${customerOnRefunds.status} ${customerOnRefunds.json?.code}`);

const noToken = await call('GET', REFUNDS);
check('无令牌 → 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 9. 清理(订单与退款是凭证,保留) ----
await call('DELETE', CART, { token });
const cleanupAddress = await call('DELETE', `${ADDRESSES}/${addressId}`, { token });
check('清理:删除脚本创建的地址(订单地址快照不受影响)', cleanupAddress.status === 204,
  `address=${cleanupAddress.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
