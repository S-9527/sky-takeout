// Insights(营业额/用户/订单/销量排行/工作台)的端到端冒烟验证。
//
// 依赖:后端已在跑,支付渠道为 mock,MySQL/Redis 可用。
// 用法:node scripts/smoke-insights.mjs   (可用 SKY_BASE_URL 覆盖地址)
//
// 报表是订单投影,断言只做"与刚完成的订单一致"的相对判断,不依赖库里的历史数据;
// 会创建真实订单(凭证,保留),但清空购物车并删除脚本创建的地址。

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
const INSIGHTS = '/api/v1/admin/insights';

const isoDay = (date) => date.toISOString().slice(0, 10);
const today = new Date();
const weekAgo = new Date(today.getTime() - 6 * 24 * 3600 * 1000);
const BEGIN = isoDay(weekAgo);
const END = isoDay(today);

const adminLogin = await call('POST', '/api/v1/admin/auth/login', {
  body: { username: 'admin', password: '123456' },
});
const adminToken = adminLogin.json?.accessToken;
const buyer = await call('POST', '/api/v1/customer/auth/wechat-login', { body: { code: 'smoke-insights-1' } });
const token = buyer.json?.accessToken;
check('拿到 admin 与顾客令牌', !!adminToken && !!token, `admin=${adminLogin.status} buyer=${buyer.status}`);

// ---- 0. 造一张"今天已完成"的订单(报表口径要能看到它) ----
await call('DELETE', CART, { token });
for (const item of (await call('GET', ADDRESSES, { token })).json ?? []) {
  await call('DELETE', `${ADDRESSES}/${item.id}`, { token });
}
const address = await call('POST', ADDRESSES, {
  token,
  body: { consignee: '报表张三', phone: '13500135000', province: '北京市', city: '北京市', district: '朝阳区', detail: '望京 3 号', label: '家' },
});
await call('POST', CART, { token, body: { itemType: 'DISH', dishId: 112, quantity: 3 } });
const order = await call('POST', ORDERS, { token, body: { addressId: address.json?.id, remark: '报表用例' } });
const orderId = order.json?.id;
const payAmount = order.json?.payAmountCents;
await call('POST', `${ORDERS}/${orderId}/payments`, { token, body: { channel: 'MOCK' } });
await call('POST', `${ADMIN_ORDERS}/${orderId}/acceptance`, { token: adminToken });
await call('POST', `${ADMIN_ORDERS}/${orderId}/delivery`, { token: adminToken });
const completed = await call('POST', `${ADMIN_ORDERS}/${orderId}/completion`, { token: adminToken });
check('准备一张今日已完成订单(用于报表口径验证)',
  completed.status === 204 && payAmount > 0, `orderId=${orderId} pay=${payAmount}`);

// ---- 1. 营业额统计 ----
const turnover = await call('GET', `${INSIGHTS}/turnover-stats?beginDate=${BEGIN}&endDate=${END}`, { token: adminToken });
const todayRow = turnover.json?.daily?.find((d) => d.date === END);
check('营业额统计:区间含首含尾、逐日补齐、sum 等于逐日之和',
  turnover.status === 200 && turnover.json.daily.length === 7
  && turnover.json.beginDate === BEGIN && turnover.json.endDate === END
  && turnover.json.sum === turnover.json.daily.reduce((sum, d) => sum + d.revenueCents, 0),
  `days=${turnover.json?.daily?.length} sum=${turnover.json?.sum}`);
check('今日营业额包含刚完成的订单,且客单价 = 营业额 / 完成单数',
  todayRow && todayRow.revenueCents >= payAmount
  && todayRow.orderCount >= 1
  && todayRow.averageOrderCents === Math.floor(todayRow.revenueCents / todayRow.orderCount),
  `today=${JSON.stringify(todayRow)}`);

const defaultRange = await call('GET', `${INSIGHTS}/turnover-stats`, { token: adminToken });
check('不传区间时默认最近 7 天(含今日)',
  defaultRange.status === 200 && defaultRange.json.daily.length === 7
  && defaultRange.json.endDate === END,
  `range=${defaultRange.json?.beginDate}~${defaultRange.json?.endDate}`);

const reversed = await call('GET', `${INSIGHTS}/turnover-stats?beginDate=${END}&endDate=${BEGIN}`, { token: adminToken });
const tooLarge = await call('GET', `${INSIGHTS}/turnover-stats?beginDate=2024-01-01&endDate=2025-01-01`, { token: adminToken });
const half = await call('GET', `${INSIGHTS}/turnover-stats?beginDate=${BEGIN}`, { token: adminToken });
check('日期区间规则:起>止/缺一半 → 400 REPORT_DATE_RANGE_INVALID,跨度超限 → TOO_LARGE',
  reversed.json?.code === 'REPORT_DATE_RANGE_INVALID' && half.json?.code === 'REPORT_DATE_RANGE_INVALID'
  && tooLarge.json?.code === 'REPORT_DATE_RANGE_TOO_LARGE',
  `${reversed.json?.code}/${half.json?.code}/${tooLarge.json?.code}`);

// ---- 2. 用户统计 ----
const userStats = await call('GET', `${INSIGHTS}/user-stats?beginDate=${BEGIN}&endDate=${END}`, { token: adminToken });
const lastUserDay = userStats.json?.daily?.[userStats.json.daily.length - 1];
check('用户统计:区间新增≥1(脚本刚建的顾客),总数≥新增,逐日累计不回退',
  userStats.status === 200 && userStats.json.newUserCount >= 1
  && userStats.json.totalUserCount >= userStats.json.newUserCount
  && userStats.json.daily.length === 7
  && userStats.json.daily.every((d, i, all) => i === 0 || all[i - 1].totalUserCount <= d.totalUserCount),
  `new=${userStats.json?.newUserCount} total=${userStats.json?.totalUserCount}`);
check('用户统计:末日累计总数等于 totalUserCount', lastUserDay?.totalUserCount === userStats.json?.totalUserCount,
  `last=${lastUserDay?.totalUserCount} total=${userStats.json?.totalUserCount}`);

// ---- 3. 订单统计 ----
const orderStats = await call('GET', `${INSIGHTS}/order-stats?beginDate=${BEGIN}&endDate=${END}`, { token: adminToken });
const expectedRate = orderStats.json?.totalOrderCount
  ? orderStats.json.validOrderCount / orderStats.json.totalOrderCount : 0;
check('订单统计:有效订单≤总数,validOrderRate = 有效/总数,逐日补齐',
  orderStats.status === 200 && orderStats.json.validOrderCount <= orderStats.json.totalOrderCount
  && orderStats.json.totalOrderCount >= 1 && orderStats.json.validOrderCount >= 1
  && Math.abs(orderStats.json.validOrderRate - expectedRate) < 1e-9
  && orderStats.json.daily.length === 7,
  `total=${orderStats.json?.totalOrderCount} valid=${orderStats.json?.validOrderCount} rate=${orderStats.json?.validOrderRate}`);

// ---- 4. 销量排行 ----
const topDishes = await call('GET', `${INSIGHTS}/top-dishes?beginDate=${BEGIN}&endDate=${END}`, { token: adminToken });
const rice = topDishes.json?.items?.find((i) => i.name === '米饭');
check('销量排行:默认前 10、名次从 1 递增、销量降序',
  topDishes.status === 200 && topDishes.json.topNumber === 10
  && topDishes.json.items.every((item, index) => item.rank === index + 1)
  && topDishes.json.items.every((item, index, all) => index === 0 || all[index - 1].copies >= item.copies),
  `topNumber=${topDishes.json?.topNumber} items=${JSON.stringify(topDishes.json?.items?.slice(0, 3))}`);
check('刚完成的订单里的菜品出现在榜上(按明细快照归集,数量≥3)',
  !!rice && rice.copies >= 3, `rice=${JSON.stringify(rice)}`);

const clamped = await call('GET', `${INSIGHTS}/top-dishes?topNumber=999`, { token: adminToken });
check('topNumber 超上限钳制为 20(不报错)', clamped.status === 200 && clamped.json?.topNumber === 20,
  `topNumber=${clamped.json?.topNumber}`);

// ---- 5. 工作台 ----
const workbench = await call('GET', `${INSIGHTS}/workbench`, { token: adminToken });
const overview = Object.fromEntries((workbench.json?.orderOverview ?? []).map((i) => [i.name, i.value]));
const dishes = Object.fromEntries((workbench.json?.dishOverview ?? []).map((i) => [i.name, i.value]));
check('工作台:今日营业额/有效单数包含刚完成的订单',
  workbench.status === 200 && workbench.json.today.turnoverCents >= payAmount
  && workbench.json.today.validOrderCount >= 1 && workbench.json.today.totalOrderCount >= 1
  && workbench.json.today.newUserCount >= 1,
  `today=${JSON.stringify(workbench.json?.today)}`);
check('工作台:订单概览键齐全且 allOrders = 各状态之和',
  ['allOrders', 'validOrders', 'cancelledOrders', 'pendingPayment', 'pendingAcceptance',
    'accepted', 'delivering', 'completed'].every((key) => key in overview)
  && overview.allOrders === overview.pendingPayment + overview.pendingAcceptance + overview.accepted
    + overview.delivering + overview.completed + overview.cancelledOrders,
  `overview=${JSON.stringify(overview)}`);
check('工作台:菜品概览键齐全,在售菜品>0,今日零销量≤在售',
  ['onSaleDishes', 'offSaleDishes', 'onSaleSetmeals', 'offSaleSetmeals', 'soldOutDishes']
    .every((key) => key in dishes)
  && dishes.onSaleDishes > 0 && dishes.soldOutDishes <= dishes.onSaleDishes,
  `dishes=${JSON.stringify(dishes)}`);

const statusCounts = await call('GET', `${ADMIN_ORDERS}/status-counts`, { token: adminToken });
check('工作台与订单状态计数口径一致(待接单/待派送/已完成)',
  workbench.json.today.pendingAcceptanceCount === statusCounts.json?.pendingAcceptance
  && workbench.json.today.pendingDeliveryCount === statusCounts.json?.accepted
  && overview.completed === statusCounts.json?.completed,
  `workbench=${workbench.json?.today?.pendingAcceptanceCount}/${workbench.json?.today?.pendingDeliveryCount}/${overview.completed} counts=${statusCounts.json?.pendingAcceptance}/${statusCounts.json?.accepted}/${statusCounts.json?.completed}`);

// ---- 6. 鉴权 ----
const customerOnInsights = await call('GET', `${INSIGHTS}/workbench`, { token });
check('顾客令牌打报表接口 → 403 AUTH_AUDIENCE_MISMATCH',
  customerOnInsights.status === 403 && customerOnInsights.json?.code === 'AUTH_AUDIENCE_MISMATCH',
  `${customerOnInsights.status} ${customerOnInsights.json?.code}`);

const noToken = await call('GET', `${INSIGHTS}/turnover-stats`);
check('无令牌 → 401 AUTH_TOKEN_INVALID',
  noToken.status === 401 && noToken.json?.code === 'AUTH_TOKEN_INVALID',
  `${noToken.status} ${noToken.json?.code}`);

// ---- 7. 清理 ----
await call('DELETE', CART, { token });
const cleanup = await call('DELETE', `${ADDRESSES}/${address.json?.id}`, { token });
check('清理:清空购物车并删除脚本创建的地址', cleanup.status === 204, `address=${cleanup.status}`);

const failed = results.filter((r) => !r.ok);
console.log(`\n${results.length - failed.length}/${results.length} passed`);
if (failed.length) {
  console.log('failed:');
  failed.forEach((f) => console.log(`  - ${f.name}`));
}
process.exit(failed.length ? 1 : 0);
