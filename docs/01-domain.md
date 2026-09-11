# 苍穹外卖 v2 · 领域模型

> 本文是 v2 重写的**唯一语义来源**。`02-database.md`(表结构)与 `03-api.md`(接口契约)都必须能从本文推导出来。
> 三者冲突时以本文为准,并回头修正另两份。

## 1. 角色与系统边界

| 角色 | 入口 | 说明 |
|---|---|---|
| 员工 Employee | 管理端 Web | 门店经营者,分 `ADMIN` / `STAFF` 两级 |
| 顾客 Customer | 小程序 | 微信登录的消费者,只能访问自己的数据 |
| 支付平台 | 回调 | 微信支付结果通知,独立鉴权通道 |
| 系统 System | 定时任务 | 超时未支付自动关单 |

四个入口共用一个后端、一个数据库。后端是**模块化单体**,不拆微服务——本项目没有需要独立伸缩或独立部署的边界,拆服务只会把一次本地调用变成一次网络调用。

**命名决定**:顾客实体统一叫 `Customer`(表 `customer`,接口前缀 `/api/v1/customer`),不再叫 `user`。
旧代码里 `user` 同时指"员工账号"和"顾客",是长期歧义的来源。

## 2. 限界上下文

| 上下文 | 聚合根 | 职责 |
|---|---|---|
| 身份 Identity | Employee / Customer | 登录、令牌、角色 |
| 门店 Shop | ShopStatus | 营业状态 |
| 商品 Catalog | Category / Dish / Setmeal | 分类、菜品、口味、套餐 |
| 购物车 Cart | CartItem | 加购、改量、清空 |
| 订单 Ordering | Order | 下单、支付、状态流转、退款 |
| 顾客资料 Profile | UserAddress | 地址簿 |
| 洞察 Insights | *(无)* | 由订单派生的统计,**无独立存储** |
| 通知 Notification | *(无)* | WebSocket 来单提醒/催单 |

"洞察"与"通知"刻意没有持久化状态:报表是订单的投影,不建报表表,避免出现第二份会漂移的真相。

## 3. 实体

### 3.1 员工 Employee

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| username | VARCHAR(32) | 登录名,唯一 |
| password_hash | VARCHAR(100) | BCrypt |
| name | VARCHAR(32) | 姓名 |
| phone | VARCHAR(20) | 手机号 |
| role | VARCHAR(16) | `ADMIN` / `STAFF` |
| status | TINYINT | 1 启用 / 0 禁用 |
| last_login_at | DATETIME | 可空 |
| 审计四件套 | | created_at / updated_at / created_by / updated_by |

**决定**:角色是显式字段,不是"用户名等于 admin 就是管理员"。旧代码靠 `name.equals("admin")` 判断超管,权限模型无法扩展也无法审计。

### 3.2 顾客 Customer

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| openid | VARCHAR(64) | 微信 openid,唯一 |
| nickname | VARCHAR(64) | |
| avatar_url | VARCHAR(255) | |
| phone | VARCHAR(20) | 可空,下单时可补 |
| status | TINYINT | 1 正常 / 0 封禁 |
| last_login_at | DATETIME | |
| 审计四件套 | | |

顾客**没有密码**:唯一登录方式是微信授权。

### 3.3 地址 UserAddress

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| customer_id | BIGINT | |
| consignee | VARCHAR(32) | 收货人 |
| phone | VARCHAR(20) | |
| province / city / district | VARCHAR(32) | 三级行政区 |
| detail | VARCHAR(255) | 详细地址 |
| label | VARCHAR(16) | 家 / 公司 / 学校 |
| is_default | TINYINT | 每顾客至多一条为 1(应用层保证) |
| 审计四件套 | | |

**规则**:地址只能被本人读写;设置默认地址时先清空本人其它默认。

### 3.4 分类 Category

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| name | VARCHAR(32) | |
| type | VARCHAR(16) | `DISH` / `SETMEAL` |
| sort_order | INT | 越小越前 |
| status | TINYINT | 1 启用 / 0 禁用 |
| 审计四件套 | | |

**规则**:同类型下名称唯一;被菜品/套餐引用的分类不可删除(数据库外键 `RESTRICT` 兜底)。

### 3.5 菜品 Dish 与口味 DishFlavor

| Dish | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| category_id | BIGINT | 必须是 `type=DISH` 的分类 |
| name | VARCHAR(64) | 同分类内唯一 |
| price_cents | BIGINT | **单位:分** |
| image_url | VARCHAR(255) | |
| description | VARCHAR(255) | |
| status | TINYINT | 1 起售 / 0 停售 |
| sort_order | INT | |
| 审计四件套 | | |

| DishFlavor | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| dish_id | BIGINT | |
| name | VARCHAR(32) | 口味维度名,如"辣度"、"忌口" |
| options | JSON | 选项数组,如 `["不辣","微辣","中辣","重辣"]` |
| sort_order | INT | |

**决定**:口味选项存 JSON 数组。
旧 schema 把选项用逗号拼成一个字符串(`"不辣,微辣,中辣"`),选项里含逗号就会静默损坏,且无法表达"必选/多选"。

**规则**:停售菜品保留口味配置;删除菜品连带删除口味。

### 3.6 套餐 Setmeal 与组成 SetmealItem

| Setmeal | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| category_id | BIGINT | 必须是 `type=SETMEAL` 的分类 |
| name | VARCHAR(64) | 同分类内唯一 |
| price_cents | BIGINT | **单位:分**,手工定价 |
| image_url / description | | |
| status | TINYINT | 1 起售 / 0 停售 |
| 审计四件套 | | |

| SetmealItem | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| setmeal_id | BIGINT | |
| dish_id | BIGINT | 唯一约束 `(setmeal_id, dish_id)` |
| copies | INT | 份数,≥1 |

**规则**:
- 套餐定价必须 **≤** 所含菜品单价×份数之和,不允许凭空加价。
- 套餐起售的前提是其所含菜品全部处于起售状态;停售任一菜品会连带停售相关套餐(同一事务内)。
- 套餐是**独立商品**,不是"菜品打折组合":它可以有自己的图片、描述、价格。

### 3.7 购物车 CartItem

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| customer_id | BIGINT | |
| item_type | VARCHAR(16) | `DISH` / `SETMEAL` |
| dish_id / setmeal_id | BIGINT | 按类型二选一 |
| quantity | INT | ≥1 |
| flavor_choice | JSON | 顾客选中的口味,如 `[{"name":"辣度","option":"微辣"}]` |
| flavor_key | VARCHAR(64) | `flavor_choice` 的归一化哈希,用于唯一约束 |
| 审计四件套 | | |

唯一约束:`(customer_id, item_type, dish_id, setmeal_id, flavor_key)` → 同菜同口味自动合并数量。

**决定**:购物车**不冗余**商品名称/图片/价格,读取时联表取实时值。
旧 schema 把 `name`/`image`/`amount` 快照进购物车,结果商家改价后购物车仍显示旧价,下单时价格又变,是真实的资损与投诉来源。购物车是"意图",不是"订单",不该有快照。
真正的快照只发生在**下单那一刻**,落在订单明细上(见 3.9)。

### 3.8 订单 Order

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| order_no | VARCHAR(32) | 业务单号,唯一,顾客可见 |
| customer_id | BIGINT | |
| status | VARCHAR(24) | 见 §4 状态机 |
| total_amount_cents | BIGINT | 商品合计 |
| pack_amount_cents | BIGINT | 打包费 |
| delivery_amount_cents | BIGINT | 配送费 |
| discount_amount_cents | BIGINT | 优惠,默认 0,v2 预留 |
| pay_amount_cents | BIGINT | 实付 = total + pack + delivery − discount |
| pay_status | VARCHAR(16) | `UNPAID` / `PAID` / `REFUNDED` / `PARTIAL_REFUNDED` |
| pay_method | VARCHAR(16) | `WECHAT` / `MOCK`,未支付时为空 |
| **地址快照** | | consignee / phone / province / city / district / detail |
| source_address_id | BIGINT | 下单时选用的地址 id,仅作溯源 |
| remark | VARCHAR(255) | 顾客备注 |
| tableware_count | INT | 餐具份数,默认 1 |
| placed_at | DATETIME | 下单时间 |
| estimated_delivery_at | DATETIME | 预计送达,可空 |
| paid_at / accepted_at / delivering_at / completed_at / cancelled_at | DATETIME | 各状态首次进入时间,可空 |
| cancel_side | VARCHAR(16) | `CUSTOMER` / `MERCHANT` / `SYSTEM` |
| cancel_reason | VARCHAR(255) | |
| 审计四件套 | | |

**决定**:
- **金额一律用"分"的整数**(`BIGINT` + `_cents` 后缀),存储与传输都是整数。浮点/`Decimal` 在前端 JS 里必然丢精度,整数分从根上消除舍入问题;展示时才除以 100。
- **地址快照进订单**。旧 schema 订单只存地址 id,顾客改地址后历史订单地址跟着变,是审计事故。
- **每个状态一个时间戳列**,而不是旧 schema 里 `order_time`/`checkout_time`/`delivery_time`/`cancel_time` 语义混乱的四列。

### 3.9 订单明细 OrderItem

| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| order_id | BIGINT | |
| item_type | VARCHAR(16) | `DISH` / `SETMEAL` |
| dish_id / setmeal_id | BIGINT | 原商品引用,可能已被删除 |
| name_snapshot | VARCHAR(64) | 下单时的名称 |
| image_snapshot | VARCHAR(255) | 下单时的图片 |
| unit_price_cents | BIGINT | 下单时的单价 |
| quantity | INT | |
| amount_cents | BIGINT | 单价 × 数量 |
| flavor_snapshot | JSON | 下单时选中的口味 |
| combo_snapshot | JSON | 套餐所含菜品明细快照,菜品行为 null |

**规则**:订单明细是**不可变**的。这是全系统唯一允许(也要求)存快照的地方——订单是法律/财务凭证,必须能还原下单那一刻的真相。

### 3.10 支付 Payment 与退款 Refund

| Payment | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| order_id / order_no | | |
| channel | VARCHAR(16) | `WECHAT` / `MOCK` |
| status | VARCHAR(16) | `PENDING` / `SUCCESS` / `FAILED` / `CLOSED` |
| amount_cents | BIGINT | |
| transaction_id | VARCHAR(64) | 支付平台单号,唯一,可空 |
| prepay_id | VARCHAR(64) | 可空 |
| paid_at | DATETIME | |
| raw_notify | JSON | 原始回调报文,排障用 |
| 审计四件套 | | |

| Refund | 类型 | 说明 |
|---|---|---|
| id | BIGINT | |
| payment_id / order_id | | |
| refund_no | VARCHAR(32) | 唯一 |
| amount_cents | BIGINT | |
| status | VARCHAR(16) | `PENDING` / `SUCCESS` / `FAILED` |
| reason / reason_type | | |
| refunded_at | DATETIME | |
| raw_notify | JSON | |
| 审计四件套 | | |

**决定**:支付与退款独立成表,订单上只冗余 `pay_status` 便于列表查询。
旧 schema 把支付信息塞在订单表的几个列里,导致一个订单无法表达"部分退款""重复支付回调""退款失败重试"。

### 3.11 店铺营业状态 ShopStatus

单行配置表:`id=1`, `is_open`, `open_time`, `close_time`, `notice`(公告),审计四件套。

**规则**:打烊时顾客端**可以浏览、不可下单**;打烊是显式状态,不由时间推导。

## 4. 订单状态机

```
                  支付成功
  PENDING_PAYMENT ──────────────→ PENDING_ACCEPTANCE
   待付款 │                          待接单 │  │ 商家拒单(需退款)
          │ 顾客取消 / 15分钟超时            │  ↓
          ↓                                │ CANCELLED
      CANCELLED                            │
                                           ↓ 商家接单
                                        ACCEPTED ──商家取消(需退款)──→ CANCELLED
                                       已接单 │
                                              ↓ 开始派送
                                        DELIVERING
                                        派送中 │
                                              ↓ 确认送达
                                        COMPLETED
```

| 状态 | 含义 | 终态 |
|---|---|---|
| `PENDING_PAYMENT` | 待付款 | 否 |
| `PENDING_ACCEPTANCE` | 待接单 | 否 |
| `ACCEPTED` | 已接单,待派送 | 否 |
| `DELIVERING` | 派送中 | 否 |
| `COMPLETED` | 已完成 | **是** |
| `CANCELLED` | 已取消 | **是** |

**合法迁移**(唯一真相,后端 `OrderStateMachine` 强制,DB 不做约束):

| 从 | 到 | 触发者 | 前置条件 |
|---|---|---|---|
| PENDING_PAYMENT | PENDING_ACCEPTANCE | 支付回调 | 支付成功 |
| PENDING_PAYMENT | CANCELLED | 顾客 / 系统 | 未支付,无需退款 |
| PENDING_ACCEPTANCE | ACCEPTED | 商家 | — |
| PENDING_ACCEPTANCE | CANCELLED | 商家 | 已支付 → 必须发起退款 |
| ACCEPTED | DELIVERING | 商家 | — |
| ACCEPTED | CANCELLED | 商家 | 已支付 → 必须发起退款 |
| DELIVERING | COMPLETED | 顾客 / 商家 | — |

**明确不允许**:`DELIVERING → CANCELLED`(已出餐,只能走售后,本期不做);`COMPLETED`/`CANCELLED` 是终态,不可再迁移;任何状态不得跳级。

**决定**:状态迁移集中在一个状态机里判定并抛出领域异常。
旧代码把 `if (status == 2) ... else if (status == 3)` 散落在各 service,状态图只存在于作者脑中,新增状态必然漏改。

## 5. 关键业务规则

| 编号 | 规则 |
|---|---|
| R1 | 打烊时禁止下单;可浏览商品 |
| R2 | 加入购物车的商品必须处于起售状态,且属于 `DISH` 类型分类 / `SETMEAL` 类型分类各自匹配 |
| R3 | 下单 = 校验 + 快照 + 清空购物车 + 生成待付款订单,全程同一事务 |
| R4 | 下单校验:购物车非空;所有商品仍起售且价格未变(变了则提示顾客刷新);地址属于本人 |
| R5 | 订单金额由服务端按**当前数据库价格**重算,请求体里的金额一律不信任 |
| R6 | 取消已支付订单必须发起退款,退款失败则订单保持原状态并告警,不允许"假取消" |
| R7 | 支付回调必须幂等:同一 `transaction_id` 重复通知只生效一次 |
| R8 | 订单完成后不可退款、不可取消 |
| R9 | 顾客只能访问 `customer_id` 等于自己的订单、地址、购物车 |
| R10 | 订单状态迁移只能经状态机,非法迁移抛 `IllegalOrderStateTransition` |

## 6. 待确认的设计决策

以下是本次重写相对旧实现的**有意偏离**,逐条列出供你确认或推翻:

| # | 决策 | 理由 | 推翻的代价 |
|---|---|---|---|
| D1 | 金额用整数**分**(`BIGINT` + `_cents`) | 消除浮点舍入;前端只做展示层除法 | 全后端 + 两前端都要改格式化和表单校验 |
| D2 | 顾客实体叫 `Customer`,接口前缀 `/api/v1/customer` | 消除 `user` 的二义 | 接口路径、两前端 api 层 |
| D3 | 员工角色是显式 `role` 字段,不是 `name == "admin"` | 权限可扩展、可审计 | 影响鉴权与菜单 |
| D4 | 购物车不存商品快照,实时联表 | 避免旧价下单 | 购物车查询 SQL 稍复杂 |
| D5 | 订单快照地址、商品名价图、口味 | 历史订单不可被后续修改污染 | 订单表列更多 |
| D6 | 支付/退款独立成表 | 支持部分退款、回调重放、退款重试 | 订单查询需 join 或冗余 `pay_status` |
| D7 | 口味选项存 JSON 数组 | 修掉逗号拼接的数据损坏 | 前端口味组件解析方式 |
| D8 | 报表是对订单的投影,不建报表表 | 单一真相 | 统计查询稍重(可加索引/缓存) |
| D9 | **沿用** Flyway 管理表结构,并让 README 与实际流程一致 | 旧实现已有 `V1__init_schema.sql`/`V2__seed_admin.sql`/`V3__seed_data.sql`,这是对的,保留;但旧 README 仍在教用户手工 `docker exec ... < sky_take_out.sql`,一个仓库里两套建表路径,说明文档已经和代码漂移——v2 只保留 Flyway 一条路 | 表结构变更需要写迁移脚本而非改 DDL |
| D10 | 响应体用**正确的 HTTP 状态码** + 统一错误体,不是恒定 200 + `code=1` | 前端拦截器可直连语义 | 前端响应处理与错误提示 |
| D11 | 登录令牌 = access(2h) + refresh(7d),refresh 存 Redis 可撤销 | 支持登出与续期 | 管理员与顾客两套令牌流程 |
| D12 | 不做库存扣减 | 原系统无此需求,引入会牵动下单/退款/取消全链路 | 若要做需重开设计 |
| D13 | **不做软删除**,禁用状态用 `status` 表达,真删除靠外键级联/限制;历史可追溯性由订单快照保证 | 软删除会让所有唯一索引失效(MySQL 唯一索引视多个 NULL 为不同值,`(name, deleted_at)` 根本挡不住重名),并且每个查询都要记得带 `deleted_at IS NULL`——漏一处就是幽灵数据。禁用、删除、历史留存是三个不同诉求,不该用一个 `is_deleted` 混着表达 | 若后续需要"回收站"语义,需重新引入并改造全部唯一索引 |

## 7. 明确不做的范围

写在这里是为了让它成为**决定**而不是**遗漏**:

- 库存与超卖控制(见 D12)
- 优惠券、满减、会员等级
- 顾客评价与商家回复
- 骑手端 App 与真实配送调度
- 多门店 / 连锁
- 售后工单流(退款只覆盖"整单全额退")
- 商品多规格 SKU(口味维度已能覆盖现有需求)

## 8. 与旧实现的对应关系

| v2 | 旧实现 | 变化 |
|---|---|---|
| `employee.role` | `name == "admin"` | 新增显式角色 |
| `customer` | `user` | 重命名 |
| `dish_flavor.options` (JSON) | `dish_flavor.value` (逗号串) | 结构化 |
| `cart_item` 无冗余 | `shopping_cart.name/image/amount` | 去掉快照 |
| `order` 金额五列 + 快照 | `orders.amount/package_fee/delivery_fee` | 金额模型重定义 |
| `payment` / `refund` | 订单表内嵌列 | 独立成表 |
| `order.status` 字符串状态机 | `orders.status` 1..6 + 散落 if | 集中状态机 |
| Flyway 迁移 `V1__init_schema.sql` / `V2__seed_dev.sql` | `db/migration/V1__init_schema.sql` 等三个脚本 | 保留机制,重写内容;README 不再提供手工建表路径 |
