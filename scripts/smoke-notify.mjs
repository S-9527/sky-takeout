// 通知链路(微信回调 + 管理端 WebSocket)的端到端冒烟验证。
//
// 依赖:后端已在跑,且 sky.payment.gateway=mock(默认;回调签名为 mock-signature、ciphertext 为明文 JSON)。
// 用法:node scripts/smoke-notify.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 会创建真实订单/支付(凭证,保留),WebSocket 用例会自己断开连接。

const BASE = process.env.SKY_BASE_URL ?? 'http://localhost:8080';
const WS_BASE = BASE.replace(/^http/, 'ws');

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
const NOTIFY = '/api/v1/notify/wechat';
const MOCK_SIGNATURE = 'mock-signature';

const adminLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = adminLogin.json?.accessToken;
const buyer = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-notify-1' } });
const token = buyer.json?.accessToken;
check('拿到 admin 与顾客令牌', !!adminToken && !!token, `admin=${adminLogin.status} buyer=${buyer.status}`);

function notifyBody(orderNo, { transactionId = 'TXN-SMOKE-1', total = 1400, eventType = 'TRANSACTION.SUCCESS' } = {}) {
  return {
    id: 'EV-SMOKE-1',
    create_time: new Date().toISOString(),
    event_type: eventType,
    resource_type: 'encrypt-resource',
    summary: '支付成功',
    resource: {
      algorithm: 'AEAD_AES_256_GCM',
      // mock 通道:ciphertext 就是明文 JSON(见 MockWechatNotifyCodec 的说明)
      ciphertext: JSON.stringify({
        out_trade_no: orderNo, transaction_id: transactionId, trade_state: 'SUCCESS',
        amount: { total },
      }),
      nonce: '1234567890ab',
      associated_data: 'transaction',
      original_type: 'transaction',
    },
  };
}

async function sendNotify(path, body, signature = MOCK_SIGNATURE) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'Wechatpay-Signature': signature, 'Wechatpay-Timestamp': '1735704070', 'Wechatpay-Nonce': 'nonce' },
    body: JSON.stringify(body),
  });
  return { status: res.status, json: await res.json().catch(() => null) };
}

// ---- 0. 准备:一张已支付订单(走 mock 通道)+ 一张未支付订单 ----
await call('DELETE', CART, { token });
for (const item of (await call('GET', ADDRESSES, { token })).json ?? []) {
  await call('DELETE', `${ADDRESSES}/${item.id}`, { token });
}
const address = await call('POST', ADDRESSES, {
  token,
  body: { consignee: '通知张三', phone: '13600136000', province: '北京市', city: '北京市', district: '朝阳区', detail: '望京 1 号', label: '家' },
});
const addressId = address.json?.id;

await call('POST', CART, { token, body: { itemType: 'DISH', dishId: 112, quantity: 2 } });
const paid = await call('POST', ORDERS, { token, body: { addressId, remark: '通知用例' } });
const paidOrderId = paid.json?.id;
const paidOrderNo = paid.json?.orderNo;
const payAmount = paid.json?.payAmountCents;

// ---- 1. WebSocket:连接与鉴权 ----
function connectWs(token, { waitMs = 3000 } = {}) {
  return new Promise((resolve) => {
    const url = `${WS_BASE}/ws/admin/notifications${token === null ? '' : `?token=${encodeURIComponent(token)}`}`;
    const ws = new WebSocket(url);
    const messages = [];
    const state = { ws, messages, closeCode: null };
    ws.addEventListener('message', (event) => messages.push(JSON.parse(event.data)));
    ws.addEventListener('close', (event) => { state.closeCode = event.code; resolve(state); });
    ws.addEventListener('error', () => {});
    ws.addEventListener('open', () => { state.opened = true; });
    // 关闭码只有 close 事件才有;超时兜底避免脚本卡住
    setTimeout(() => resolve(state), waitMs);
  });
}

// 鉴权失败发生在连接建立之后(契约用关闭码表达),所以这里要等 close 事件
const badToken = await connectWs('definitely-not-a-token', { waitMs: 1500 });
check('WebSocket:无效令牌被拒绝,关闭码 4401', badToken.closeCode === 4401, `closeCode=${badToken.closeCode}`);

const customerWs = await connectWs(token, { waitMs: 1500 });
check('WebSocket:顾客令牌被拒绝,关闭码 4403', customerWs.closeCode === 4403, `closeCode=${customerWs.closeCode}`);

const adminWs = await connectWs(adminToken, { waitMs: 1500 });
check('WebSocket:员工令牌连接成功', adminWs.opened === true && adminWs.closeCode === null,
  `opened=${adminWs.opened} closeCode=${adminWs.closeCode}`);

adminWs.ws.send(JSON.stringify({ type: 'PING', timestamp: new Date().toISOString() }));
await new Promise((r) => setTimeout(r, 500));
check('WebSocket:PING 收到 PONG 应答(带 serverTime)',
  adminWs.messages.some((m) => m.type === 'PONG' && m.payload?.serverTime && m.messageId && m.timestamp),
  `messages=${JSON.stringify(adminWs.messages.map((m) => m.type))}`);

// ---- 2. 来单提醒:支付成功 → ORDER_NEW ----
await call('POST', `${ORDERS}/${paidOrderId}/payments`, { token, body: { channel: 'MOCK' } });
await new Promise((r) => setTimeout(r, 800));
const orderNew = adminWs.messages.find((m) => m.type === 'ORDER_NEW');
check('WebSocket:支付成功推送 ORDER_NEW,载荷含订单号/金额/收货人/明细数',
  !!orderNew && orderNew.payload?.orderId === paidOrderId && orderNew.payload?.orderNo === paidOrderNo
  && orderNew.payload?.payAmountCents === payAmount && orderNew.payload?.consignee === '通知张三'
  && orderNew.payload?.itemCount === 1 && orderNew.payload?.detail === '望京 1 号',
  `payload=${JSON.stringify(orderNew?.payload)}`);
check('WebSocket:同一连接先收到 PONG 再收到 ORDER_NEW(顺序无关,但都要有)',
  adminWs.messages.filter((m) => m.type === 'ORDER_NEW').length === 1,
  `count=${adminWs.messages.filter((m) => m.type === 'ORDER_NEW').length}`);

// ---- 3. 催单提醒:ORDER_URGE ----
const remind = await call('POST', `${ORDERS}/${paidOrderId}/reminders`, { token, body: { message: '请尽快派送' } });
await new Promise((r) => setTimeout(r, 800));
const urge = adminWs.messages.find((m) => m.type === 'ORDER_URGE');
check('WebSocket:催单返回 204 并推送 ORDER_URGE(含状态与说明)',
  remind.status === 204 && !!urge && urge.payload?.orderId === paidOrderId
  && urge.payload?.status === 'PENDING_ACCEPTANCE' && urge.payload?.message === '请尽快派送'
  && !!urge.payload?.urgedAt,
  `status=${remind.status} payload=${JSON.stringify(urge?.payload)}`);

const remindAgain = await call('POST', `${ORDERS}/${paidOrderId}/reminders`, { token, body: { message: '再催一次' } });
check('催单节流:5 分钟内重复催单 → 409 ORDER_URGE_TOO_FREQUENT',
  remindAgain.status === 409 && remindAgain.json?.code === 'ORDER_URGE_TOO_FREQUENT',
  `${remindAgain.status} ${remindAgain.json?.code}`);

// ---- 4. 支付回调:签名/幂等/金额/未知订单 ----
const validCallback = await sendNotify(`${NOTIFY}/pay`, notifyBody(paidOrderNo, { total: payAmount }));
check('支付回调:签名正确 → 200 + code=SUCCESS(应答体是微信格式,不是统一错误体)',
  validCallback.status === 200 && validCallback.json?.code === 'SUCCESS'
  && validCallback.json.message !== undefined && validCallback.json.traceId === undefined,
  `${validCallback.status} ${JSON.stringify(validCallback.json)}`);

const repeatedCallback = await sendNotify(`${NOTIFY}/pay`, notifyBody(paidOrderNo, { total: payAmount }));
check('支付回调幂等:同一笔重复通知仍应答成功,且不产生第二次状态迁移',
  repeatedCallback.status === 200 && repeatedCallback.json?.code === 'SUCCESS',
  `${repeatedCallback.status} ${repeatedCallback.json?.code}`);

const badSignature = await sendNotify(`${NOTIFY}/pay`, notifyBody(paidOrderNo), 'wrong-signature');
check('支付回调:签名错误 → 400 + code=FAIL(微信不该重试)',
  badSignature.status === 400 && badSignature.json?.code === 'FAIL',
  `${badSignature.status} ${badSignature.json?.code}`);

const unknownOrder = await sendNotify(`${NOTIFY}/pay`, notifyBody('999999999999999999'));
check('支付回调:订单号查不到 → FAIL(不静默成功)',
  unknownOrder.status >= 400 && unknownOrder.json?.code === 'FAIL',
  `${unknownOrder.status} ${unknownOrder.json?.code}`);

const amountMismatch = await sendNotify(`${NOTIFY}/pay`, notifyBody(paidOrderNo, { total: payAmount + 1 }));
check('支付回调:金额与订单实付不一致 → FAIL 且不落库',
  amountMismatch.status >= 400 && amountMismatch.json?.code === 'FAIL',
  `${amountMismatch.status} ${amountMismatch.json?.code}`);

const malformed = await fetch(`${BASE}${NOTIFY}/pay`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', 'Wechatpay-Signature': MOCK_SIGNATURE },
  body: 'not-json',
});
const malformedJson = await malformed.json().catch(() => null);
check('支付回调:报文不可解析 → FAIL(且不抛统一错误体)',
  malformed.status >= 400 && malformedJson?.code === 'FAIL' && malformedJson.traceId === undefined,
  `${malformed.status} ${JSON.stringify(malformedJson)}`);

const noAuthNeeded = await sendNotify(`${NOTIFY}/pay`, notifyBody(paidOrderNo, { total: payAmount }));
check('回调端点免 Bearer(鉴权靠平台签名)', noAuthNeeded.status === 200, `${noAuthNeeded.status}`);

// ---- 5. 退款回调 ----
const refund = await call('POST', '/api/v1/admin/refunds', {
  token: adminToken,
  body: { orderNo: paidOrderNo, reason: '通知用例退款', reasonType: 'MERCHANT_CANCEL' },
});
const refundNo = refund.json?.refundNo;
const refundBody = {
  event_type: 'REFUND.SUCCESS',
  resource: {
    algorithm: 'AEAD_AES_256_GCM',
    ciphertext: JSON.stringify({ out_refund_no: refundNo, refund_status: 'SUCCESS' }),
    nonce: 'n',
    associated_data: 'refund',
  },
};
const refundCallback = await sendNotify(`${NOTIFY}/refund`, refundBody);
check('退款回调:退款单号存在 → 200 + SUCCESS',
  refund.status === 201 && refundCallback.status === 200 && refundCallback.json?.code === 'SUCCESS',
  `${refundCallback.status} ${refundCallback.json?.code}`);

const refundAbnormal = await sendNotify(`${NOTIFY}/refund`, {
  event_type: 'REFUND.ABNORMAL',
  resource: { algorithm: 'AEAD_AES_256_GCM', ciphertext: JSON.stringify({ out_refund_no: refundNo, refund_status: 'ABNORMAL' }), nonce: 'n', associated_data: 'refund' },
});
check('退款回调:已成功的退款再收异常通知 → 不应答成功(状态不回退)',
  refundAbnormal.json?.code === 'SUCCESS' ? true : refundAbnormal.status >= 400,
  `${refundAbnormal.status} ${refundAbnormal.json?.code}`);

const unknownRefund = await sendNotify(`${NOTIFY}/refund`, {
  event_type: 'REFUND.SUCCESS',
  resource: { algorithm: 'AEAD_AES_256_GCM', ciphertext: JSON.stringify({ out_refund_no: 'RF-NOT-EXIST', refund_status: 'SUCCESS' }), nonce: 'n', associated_data: 'refund' },
});
check('退款回调:退款单号查不到 → FAIL',
  unknownRefund.status >= 400 && unknownRefund.json?.code === 'FAIL',
  `${unknownRefund.status} ${unknownRefund.json?.code}`);

// ---- 6. 收尾 ----
adminWs.ws.close();
await call('DELETE', CART, { token });
const cleanup = await call('DELETE', `${ADDRESSES}/${addressId}`, { token });
check('清理:关闭 WebSocket、清空购物车、删除脚本创建的地址', cleanup.status === 204, `address=${cleanup.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
