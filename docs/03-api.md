# 苍穹外卖 v2 · 接口约定

> 本文是**人读**的接口约定:只写通用规则、错误码表与接口清单索引,**不重复**逐个接口的字段细节。
> **`docs/openapi.yaml` 是接口的唯一真相**;两者冲突时以 OpenAPI 为准,并回头修正本文。
> 语义来源是 `docs/01-domain.md`(领域模型);三者冲突时以领域文档为准。

- 版本:`2.0.0`
- 基础前缀:`/api/v1`
- 规范文件:`docs/openapi.yaml`(OpenAPI 3.1.0)
- 接口总数:**79** 个 HTTP endpoint(另有 1 个 WebSocket 端点,见第 4 节)

---

## 1. 通用约定

### 1.1 路径与受众

| 前缀 | 受众 | 认证方式 | 令牌受众声明 |
|---|---|---|---|
| `/api/v1/admin/**` | 员工(管理端 Web) | `Authorization: Bearer <accessToken>` | `aud: admin` |
| `/api/v1/customer/**` | 顾客(小程序) | `Authorization: Bearer <accessToken>` | `aud: customer` |
| `/api/v1/notify/**` | 支付平台 | 平台签名校验 + APIv3 报文解密 | 不适用 |

- 资源用**复数小写名词**;路径段多词用 `-` 连接(如 `/status-counts`、`/top-dishes`、`/wechat-login`)。
- 顾客实体叫 `Customer`,前缀是 `/api/v1/customer`,**不是 `user`**(领域文档 D2)。
- 状态迁移类操作按"产生的事实"命名子资源,而不是动词:`/acceptance`、`/rejection`、`/delivery`、`/completion`、`/cancellation`。
- 批量操作复用集合路径,id 列表放查询参数或请求体,不为批量单独开路径。

### 1.2 认证与令牌

- 请求头:`Authorization: Bearer <accessToken>`。
- **员工令牌与顾客令牌互不通用**:两套独立签发,令牌载荷带 `aud`(受众)。顾客令牌访问 admin 接口、或员工令牌访问 customer 接口,一律 **403 `AUTH_AUDIENCE_MISMATCH`**。
- 令牌载荷:`aud`(受众 `admin` / `customer`)、`sub`(主体 id:员工 id / 顾客 id)、`role`(`ADMIN` / `STAFF` / `CUSTOMER`)、`iat` / `exp` / `jti`。
- access token **有效期 2 小时**(7200 秒);refresh token **有效期 7 天**,存 Redis、**可撤销**。
- 刷新采用 **refresh token 旋转**:一次刷新同时换发新的 access 与 refresh,旧 refresh 立即失效。
- 登出撤销当前 refresh token;access token 依靠短有效期自然失效。修改密码、员工被禁用后,该员工全部 refresh token 撤销。
- 令牌无效/过期 → 401(`AUTH_TOKEN_INVALID` / `AUTH_TOKEN_EXPIRED`);已认证但角色不足 → 403(`AUTH_PERMISSION_DENIED`,员工管理接口仅 `ADMIN`)。
- `/api/v1/notify/**` **不使用 Bearer**:鉴权走微信支付平台签名校验(`Wechatpay-Signature` / `Wechatpay-Timestamp` / `Wechatpay-Nonce` / `Wechatpay-Serial` 请求头)+ APIv3 报文解密。

### 1.3 时间

| 用途 | 格式 | 示例 |
|---|---|---|
| 时间戳字段 | ISO-8601 **带偏移** | `2025-01-01T12:00:00+08:00` |
| 日期参数 / 报表日期 | `YYYY-MM-DD` | `2025-01-01` |
| 营业时间(`openTime`/`closeTime`) | `HH:mm:ss` | `09:00:00` |

- 区间参数语义统一为**含首含尾**(如 `beginDate=2025-01-01&endDate=2025-01-07` 含 7 日全天)。
- 时间列语义:`placedAt` / `paidAt` / `acceptedAt` / `deliveringAt` / `completedAt` / `cancelledAt` 各记录**首次进入该状态**的时间,可空;不存在旧实现的"四列时间语义混乱"问题。
- "今日"按门店时区 `Asia/Shanghai` 自然日计算;服务端与数据库统一按该时区解释。

### 1.4 金额

- 金额一律是**整数,单位分**;字段名以 `Cents` 结尾(D1)。
- 典型字段:`priceCents`、`totalAmountCents`、`packAmountCents`、`deliveryAmountCents`、`discountAmountCents`、`payAmountCents`、`amountCents`、`revenueCents`。
- 展示层才除以 100;任何请求体都不接受浮点金额。
- `payAmountCents = totalAmountCents + packAmountCents + deliveryAmountCents − discountAmountCents`;v2 的 `discountAmountCents` 恒为 `0`(字段预留,不做优惠券/满减)。
- **服务端重算**:下单、试算、支付、退款一律以数据库当前价格为准,请求体中的金额只用于"变化检测"(`expectedTotalAmountCents`、`expectedAmountCents`),不作为计算依据(R5)。

### 1.5 分页

- 参数:`?page=1&pageSize=20`;`page` 从 1 开始,`pageSize` 默认 20。
- 响应信封(**仅分页接口**):

```json
{ "records": [], "page": 1, "pageSize": 20, "total": 137 }
```

- `total` 为满足筛选条件的总条数(不是当前页条数)。
- 非分页的列表接口直接返回数组(如顾客端分类列表、地址列表)。

**选择:超限为「钳制」而非报错。**
`pageSize>100` 时静默按 `100` 处理;**不**返回 400。理由:

1. 分页大小是"性能护栏",不是业务语义。钳制仍返回正确数据,而报错会让前端翻页逻辑在边界上炸掉,得为"用户手滑"写额外分支。
2. 只有非法**类型/符号**(`pageSize=abc`、`page=0`、`pageSize=-1`)返回 400 `COMMON_VALIDATION_FAILED`,即"能理解但越界"的参数钳制,"无法理解"的参数报错。
3. 同理,报表的 `topNumber>20` 也钳制为 20;`page<1` 按 1 处理。
4. `page` 超出总页数不报错,返回空 `records`。

### 1.6 排序

- 参数:`?sort=字段,asc|desc`;缺省方向视为 `asc`;不传 `sort` 时用各接口的默认排序。
- **字段白名单**:排序字段必须在该接口的白名单内,否则 **400 `COMMON_SORT_FIELD_NOT_ALLOWED`**。格式非法(如 `sort=placedAt,sideways`)→ 400 `COMMON_VALIDATION_FAILED`。
- 选择"白名单 + 报错"而不是"忽略未知字段":排序字段最终会拼进 SQL,白名单是**防注入与防全表扫描**的第一道闸;静默忽略会让前端以为排序生效而实际没有。
- 各接口白名单:

| 接口 | 白名单 | 默认 |
|---|---|---|
| `GET /admin/employees` | `createdAt`、`username`、`lastLoginAt` | `createdAt,desc` |
| `GET /admin/categories` | `sortOrder`、`name`、`createdAt` | `sortOrder,asc` |
| `GET /admin/dishes` | `sortOrder`、`priceCents`、`createdAt`、`name` | `sortOrder,asc` |
| `GET /admin/setmeals` | `priceCents`、`createdAt`、`name` | `createdAt,desc` |
| `GET /customer/orders` | `placedAt`、`payAmountCents` | `placedAt,desc` |
| `GET /admin/orders` | `placedAt`、`payAmountCents` | `placedAt,desc` |
| `GET /admin/refunds` | `createdAt`、`refundedAt`、`amountCents` | `createdAt,desc` |

- 需要稳定分页时,服务端在各排序后**追加主键做次级排序**,避免同值行在翻页时重复/丢失。
- 顾客端商品列表(分类下菜品、套餐)排序固定为服务端定义(`sortOrder` 升序),不开放 `sort` 参数。

### 1.7 命名与编码

- 请求与响应 JSON 字段一律 **camelCase**;数据库 `snake_case` 只在持久层出现。
- 枚举值是 `SCREAMING_SNAKE_CASE` 字符串(如 `PENDING_ACCEPTANCE`、`SETMEAL`),**不使用魔法数字**。
- 启用/禁用类标志是整数 `1` / `0`(`status`、`isDefault`),与领域模型 `TINYINT` 对齐;`isOpen` 是布尔。
- 请求/响应编码 `UTF-8`,请求体 `Content-Type: application/json`(上传接口为 `multipart/form-data`)。
- 顾客 id 与员工 id 都是 `BIGINT`,JSON 中为数字。**BIGINT 超过 2^53 时前端会丢精度**,v2 使用自增 id 且远小于该量级;若未来改用雪花 id,需把 id 序列化为字符串(留待 `02-database.md` 确认)。

### 1.8 其他

- 每个响应都带 `X-Trace-Id` 响应头,与错误体的 `traceId` 一致,便于串联日志。
- 幂等性:`DELETE` 与"置默认地址"等操作天然幂等;下单不幂等但通过"购物车清空 + 15 分钟关单"约束副作用范围。
- 不提供 CORS 通配配置;管理端与小程序各自同源/白名单访问,具体见部署文档。
- 接口不做软删除(D13):"禁用"用 `status=0`,"删除"是真删除,历史留存由订单快照保证。

---

## 2. 响应格式

### 2.1 成功响应

成功与失败**刻意不同构**:成功直接给资源,不套壳。

| 场景 | 状态码 | 响应体 |
|---|---|---|
| 查询单资源 | 200 | 资源对象本身 |
| 列表(非分页) | 200 | 数组本身 |
| 列表(分页) | 200 | `{records, page, pageSize, total}` |
| 创建资源 | 201 | 创建后的资源(少数接口返回轻量投影,如 `submitOrder` 返回 `OrderSubmitResult`) |
| 全量更新 | 200 | 更新后的资源 |
| 无返回体的写操作(删除、状态切换、登出、取消、退款受理等) | **204** | **无** |
| 图片上传 | 201 | `{url, size, contentType}` |

```json
// GET /api/v1/customer/catalog/categories?type=DISH → 200
[ { "id": 10, "name": "川湘菜", "type": "DISH", "sortOrder": 1, "status": 1 } ]
```

```json
// GET /api/v1/admin/dishes?page=1&pageSize=20 → 200
{ "records": [ { "id": 2001, "name": "水煮牛肉", "priceCents": 4800, "status": 1 } ],
  "page": 1, "pageSize": 20, "total": 137 }
```

### 2.2 失败响应

用**正确的 HTTP 状态码**(D10),响应体统一为:

```json
{
  "code": "ORDER_INVALID_TRANSITION",
  "message": "订单当前状态不允许该操作",
  "details": [ { "field": "status", "reason": "DELIVERING 不能取消" } ],
  "traceId": "0f3a1c2b4d5e6f70"
}
```

| 字段 | 必填 | 说明 |
|---|---|---|
| `code` | 是 | `SCREAMING_SNAKE_CASE` 错误码,前端据此分支 |
| `message` | 是 | 面向用户的中文提示,可直接展示 |
| `details` | 否 | 字段级细节数组,元素 `{field?, reason}`;主要用于 400 / 422 |
| `traceId` | 是 | 链路追踪 id,报障时提供 |

状态码语义:

| 状态码 | 含义 | 典型错误码 |
|---|---|---|
| 400 | 参数校验失败、排序字段不在白名单 | `COMMON_VALIDATION_FAILED`、`COMMON_SORT_FIELD_NOT_ALLOWED`、`UPLOAD_*` |
| 401 | 未认证、令牌无效或过期、密码错误 | `AUTH_TOKEN_INVALID`、`AUTH_TOKEN_EXPIRED`、`AUTH_REFRESH_TOKEN_INVALID`、`AUTH_BAD_CREDENTIALS` |
| 403 | 已认证但无权限(受众不匹配、角色不足、账号被禁用) | `AUTH_AUDIENCE_MISMATCH`、`AUTH_PERMISSION_DENIED`、`EMPLOYEE_DISABLED`、`CUSTOMER_DISABLED` |
| 404 | 资源不存在(或不属于当前顾客) | `COMMON_RESOURCE_NOT_FOUND`、`EMPLOYEE_NOT_FOUND`、`DISH_NOT_FOUND`、`ORDER_NOT_FOUND`、`ADDRESS_NOT_FOUND`、`CART_ITEM_NOT_FOUND` 等 |
| 409 | 唯一键冲突、重复请求 | `*_NAME_TAKEN`、`*_USERNAME_TAKEN`、`PAY_DUPLICATE_PAYMENT`、`PAY_REFUND_ALREADY_EXISTS`、`ORDER_URGE_TOO_FREQUENT`、`CUSTOMER_DUPLICATE_OPENID` |
| 422 | 业务规则不满足(状态机、打烊、价格变动、金额不符等) | `ORDER_INVALID_TRANSITION`、`ORDER_SHOP_CLOSED`、`ORDER_PRICE_CHANGED`、`PAY_ORDER_NOT_REFUNDABLE` 等 |
| 500 | 未预期错误 | `COMMON_INTERNAL_ERROR` |
| 502 | 外部支付渠道调用失败(Refund 受理失败) | `PAY_REFUND_FAILED` |

关于 502 的补充:退款受理失败必须让"取消订单"整体失败(R6),不能假装成功,因此需要一个区别于 500 的状态码表达"上游渠道不可用"。这是本规范唯一超出你给定清单的状态码;若你希望收敛到 500,只影响 `PAY_REFUND_FAILED` 一个码。

`/api/v1/notify/**` 的应答**不遵循**上表:它必须返回微信支付规定的 `{"code":"SUCCESS","message":"成功"}`,失败返回 500 + `FAIL` 以触发微信重试。

### 2.3 完整错误码表

按上下文前缀分组。同一错误码在不同接口上语义一致。

#### `COMMON_*` — 通用

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `COMMON_VALIDATION_FAILED` | 400 | 请求参数缺失/类型错误/越界/路径变量非数字 | 请求参数有误,请检查后重试 |
| `COMMON_SORT_FIELD_NOT_ALLOWED` | 400 | `sort` 字段不在该接口白名单内 | 不支持按该字段排序 |
| `COMMON_METHOD_NOT_ALLOWED` | 405 | 用错了 HTTP 方法(如对只读路径发 POST) | 请求方式不正确 |
| `COMMON_RESOURCE_NOT_FOUND` | 404 | 通用资源不存在(无更具体错误码时使用) | 请求的资源不存在 |
| `COMMON_INTERNAL_ERROR` | 500 | 未预期异常(已记日志与 traceId) | 服务器开小差了,请稍后重试 |
| `COMMON_CONFLICT` | 409 | 唯一键冲突等并发写冲突(无更具体错误码时使用) | 数据冲突,请刷新后重试 |

#### `AUTH_*` — 认证与授权(员工 / 顾客共用)

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `AUTH_TOKEN_INVALID` | 401 | `Authorization` 头缺失、格式错误、签名无效 | 登录状态无效,请重新登录 |
| `AUTH_TOKEN_EXPIRED` | 401 | access token 超过 2 小时 | 登录状态已过期,请重新登录 |
| `AUTH_REFRESH_TOKEN_INVALID` | 401 | refresh token 不存在、已撤销、已旋转失效或超过 7 天 | 登录状态已失效,请重新登录 |
| `AUTH_REFRESH_TOKEN_EXPIRED` | 401 | refresh token 超过 7 天 | 登录已过期,请重新登录 |
| `AUTH_BAD_CREDENTIALS` | 401 | 用户名或密码错误;或修改密码时旧密码错误 | 用户名或密码错误 |
| `AUTH_AUDIENCE_MISMATCH` | 403 | 令牌 `aud` 与接口受众不符(顾客令牌打 admin 接口,或反之) | 当前登录身份无权访问该功能 |
| `AUTH_PERMISSION_DENIED` | 403 | 已认证但角色不足(如 `STAFF` 访问员工管理) | 没有该操作权限 |
| `AUTH_SUBJECT_NOT_FOUND` | 401 | 令牌 `sub` 对应的主体已被删除 | 账号不存在,请重新登录 |

#### `EMPLOYEE_*` — 员工

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `EMPLOYEE_NOT_FOUND` | 404 | 员工 id 不存在 | 员工不存在 |
| `EMPLOYEE_USERNAME_TAKEN` | 409 | 新增员工时用户名已存在 | 该用户名已被使用 |
| `EMPLOYEE_DISABLED` | 403 | 员工 `status=0`(禁用)却尝试登录或调用接口 | 账号已被禁用,请联系管理员 |
| `EMPLOYEE_SELF_DISABLE` | 422 | ADMIN 试图禁用自己 | 不能禁用当前登录账号 |
| `EMPLOYEE_SELF_ROLE_CHANGE` | 422 | ADMIN 试图把自己的角色降为 STAFF | 不能修改自己的角色 |

#### `CUSTOMER_*` — 顾客

> 说明:领域文档未给顾客分配错误码前缀(它只在角色表里出现)。为保持"前缀按上下文"的一致性,本规范扩展了 `CUSTOMER_*`;若你希望顾客相关错误并入 `AUTH_*` / `COMMON_*`,这是需要你拍板的一处偏离。`CUSTOMER_*` 未列入你给定的前缀清单。

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `CUSTOMER_NOT_FOUND` | 404 | 顾客 id / openid 不存在 | 用户不存在 |
| `CUSTOMER_DISABLED` | 403 | 顾客 `status=0`(封禁) | 账号已被封禁,请联系客服 |
| `CUSTOMER_WECHAT_CODE_INVALID` | 422 | 微信 `code2Session` 返回 code 失效 | 微信登录凭证已失效,请重试 |
| `CUSTOMER_WECHAT_API_ERROR` | 500 | 调用微信接口失败/超时 | 微信登录暂时不可用,请稍后重试 |
| `CUSTOMER_DUPLICATE_OPENID` | 409 | 同一 openid 并发首登,唯一键冲突 | 登录中,请重试 |

#### `CATEGORY_*` — 分类

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `CATEGORY_NOT_FOUND` | 404 | 分类 id 不存在 | 分类不存在 |
| `CATEGORY_NAME_TAKEN` | 409 | 同类型下分类名重复 | 同类型下已存在同名分类 |
| `CATEGORY_IN_USE` | 422 | 分类被菜品/套餐引用,不可删除 | 该分类下仍有商品,无法删除 |
| `CATEGORY_TYPE_IMMUTABLE` | 422 | 编辑时试图修改分类类型 | 分类类型不可修改 |
| `CATEGORY_DISABLED` | 422 | 向已禁用分类下新增商品 | 分类已禁用,无法添加商品 |

#### `DISH_*` — 菜品

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `DISH_NOT_FOUND` | 404 | 菜品 id 不存在(含批量删除中的任一 id) | 菜品不存在 |
| `DISH_NAME_TAKEN` | 409 | 同分类内菜品名重复 | 该分类下已存在同名菜品 |
| `DISH_CATEGORY_TYPE_MISMATCH` | 422 | 菜品挂在 `type=SETMEAL` 的分类下 | 菜品只能归属于菜品分类 |
| `DISH_OFF_SALE` | 422 | 顾客加购/下单时菜品已停售 | 商品已下架,请重新选择 |

#### `SETMEAL_*` — 套餐

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `SETMEAL_NOT_FOUND` | 404 | 套餐 id 不存在(含批量删除中的任一 id) | 套餐不存在 |
| `SETMEAL_NAME_TAKEN` | 409 | 同分类内套餐名重复 | 该分类下已存在同名套餐 |
| `SETMEAL_CATEGORY_TYPE_MISMATCH` | 422 | 套餐挂在 `type=DISH` 的分类下 | 套餐只能归属于套餐分类 |
| `SETMEAL_ITEMS_EMPTY` | 422 | 套餐组成明细为空 | 套餐必须至少包含一个菜品 |
| `SETMEAL_ITEMS_DUPLICATED` | 409 | 组成明细里同一菜品重复出现 | 同一菜品不能重复添加 |
| `SETMEAL_PRICE_EXCEEDS_ITEMS` | 422 | 套餐定价 > 所含菜品单价×份数之和 | 套餐定价不能高于所含菜品合计 |
| `SETMEAL_DISH_NOT_ON_SALE` | 422 | 套餐起售但所含菜品存在停售 | 所含菜品中有停售商品,无法起售 |
| `SETMEAL_CONTAINS_DISH` | 422 | 删除菜品时该菜品被套餐引用 | 菜品已被套餐引用,请先从套餐移除 |
| `SETMEAL_OFF_SALE` | 422 | 顾客加购/下单时套餐已停售 | 套餐已下架,请重新选择 |

#### `CART_*` — 购物车

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `CART_ITEM_NOT_FOUND` | 404 | 购物车行 id 不存在或不属于当前顾客 | 购物车项不存在 |
| `CART_ITEM_OFF_SALE` | 422 | 加购的商品当前不在起售状态(R2) | 商品已下架,无法加入购物车 |
| `CART_ITEM_TYPE_MISMATCH` | 422 | `itemType` 与商品实际类型不符 | 商品类型不匹配 |
| `CART_FLAVOR_REQUIRED` | 422 | 商品有口味配置但未选择口味 | 请选择商品口味 |
| `CART_FLAVOR_INVALID` | 422 | 提交的口味维度或选项不在配置中 | 所选口味已变更,请重新选择 |
| `CART_QUANTITY_INVALID` | 422 | 数量非正整数,或合并后超过上限 99 | 数量不合理,请重新输入 |

#### `ORDER_*` — 订单

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `ORDER_NOT_FOUND` | 404 | 订单 id / 订单号不存在,或不属于当前顾客(R9) | 订单不存在 |
| `ORDER_CART_EMPTY` | 422 | 下单或试算时购物车为空(R4) | 购物车是空的,请先选择商品 |
| `ORDER_SHOP_CLOSED` | 422 | 门店打烊时下单(R1);浏览不受限 | 门店已打烊,暂时无法下单 |
| `ORDER_ITEM_NOT_ON_SALE` | 422 | 下单时购物车内有商品已停售或已删除(R4) | 有商品已下架,请刷新购物车 |
| `ORDER_PRICE_CHANGED` | 422 | 服务端重算金额与客户端 `expectedTotalAmountCents` 不一致(R4/R5) | 商品价格已变化,请刷新后重新提交 |
| `ORDER_ADDRESS_INVALID` | 422 | 下单地址不存在、不属于本人或缺少必填项(R4) | 收货地址无效,请重新选择 |
| `ORDER_INVALID_TRANSITION` | 422 | 状态迁移不在状态机允许集合内(R10,如 `DELIVERING→CANCELLED`) | 订单当前状态不允许该操作 |
| `ORDER_CANNOT_CANCEL` | 422 | 顾客取消非待付款订单;或商家取消 `DELIVERING` 订单 | 订单当前状态不可取消 |
| `ORDER_ALREADY_PAID` | 422 | 对已支付订单执行仅限未支付的操作 | 订单已支付 |
| `ORDER_PAY_TIMEOUT` | 422 | 订单已因 15 分钟超时被系统关单,再发起支付 | 订单已超时关闭,请重新下单 |
| `ORDER_URGE_NOT_ALLOWED` | 422 | 对 `PENDING_PAYMENT` / 终态订单催单 | 当前订单状态不支持催单 |
| `ORDER_URGE_TOO_FREQUENT` | 409 | 同一订单 5 分钟内重复催单 | 已提醒商家,请勿重复催单 |
| `ORDER_DUPLICATE_SUBMIT` | 409 | 同一顾客短时间(10 秒)内重复提交内容一致的订单 | 订单正在处理中,请勿重复提交 |
| `ORDER_STATUS_COUNT_FAILED` | 500 | 各状态订单数量统计查询失败 | 数据加载失败,请稍后重试 |

#### `ADDRESS_*` — 地址

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `ADDRESS_NOT_FOUND` | 404 | 地址 id 不存在或不属于当前顾客(R9) | 地址不存在 |
| `ADDRESS_LIMIT_EXCEEDED` | 422 | 顾客地址数达到上限 20 | 地址数量已达上限,请先删除 |
| `ADDRESS_DEFAULT_DUPLICATED` | 409 | 并发设置默认地址导致出现两条默认 | 操作冲突,请刷新后重试 |
| `ADDRESS_IN_USE` | 422 | 地址被未完成订单引用(仅作提示,删除仍允许) | 该地址有未完成订单正在使用 |

#### `PAY_*` — 支付与退款

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `PAY_NOT_FOUND` | 404 | 支付记录 id 不存在 | 支付记录不存在 |
| `PAY_ORDER_NOT_PAYABLE` | 422 | 对非 `PENDING_PAYMENT` 订单发起支付 | 订单当前状态无法支付 |
| `PAY_AMOUNT_MISMATCH` | 422 | 支付金额与订单 `payAmountCents` 不一致(R5) | 支付金额与订单不符,请刷新后重试 |
| `PAY_DUPLICATE_PAYMENT` | 409 | 订单已支付成功,或存在有效的进行中支付 | 订单已支付,请勿重复支付 |
| `PAY_NOTIFY_SIGNATURE_INVALID` | 400 | 回调签名校验失败 | (仅日志,不面向用户) |
| `PAY_NOTIFY_DECRYPT_FAILED` | 400 | 回调报文解密失败 | (仅日志,不面向用户) |
| `PAY_ORDER_NOT_FOUND` | 404 | 回调中的 `out_trade_no` 查不到订单 | (仅日志,不面向用户) |
| `PAY_ORDER_NOT_PAID` | 422 | 对未支付订单发起退款 | 订单未支付,无法退款 |
| `PAY_ORDER_NOT_REFUNDABLE` | 422 | `COMPLETED` 订单退款(R8);或订单不可退款状态 | 订单已完成,不支持退款 |
| `PAY_REFUND_NOT_FOUND` | 404 | 退款单不存在 | 退款记录不存在 |
| `PAY_REFUND_AMOUNT_EXCEEDED` | 422 | 退款金额 > 已支付金额,或与实付不一致 | 退款金额不正确 |
| `PAY_REFUND_ALREADY_EXISTS` | 409 | 该订单已有成功或处理中的退款 | 该订单已申请退款 |
| `PAY_REFUND_FAILED` | 502 | 支付渠道退款受理失败,订单保持原状态并告警(R6) | 退款受理失败,请稍后重试 |

#### `SHOP_*` — 门店

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `SHOP_STATUS_NOT_FOUND` | 404 | 单行配置缺失(未初始化) | 门店信息不存在 |
| `SHOP_BUSINESS_HOURS_INVALID` | 400 | `openTime` / `closeTime` 格式非法或逻辑矛盾 | 营业时间设置不合理 |

#### `REPORT_*` — 报表

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `REPORT_DATE_RANGE_INVALID` | 400 | 日期区间非法(起 > 止,或缺失一半) | 日期区间不正确 |
| `REPORT_DATE_RANGE_TOO_LARGE` | 400 | 区间跨度 > 366 天 | 统计区间不能超过 366 天 |
| `REPORT_STATISTICS_FAILED` | 500 | 统计查询失败 | 统计加载失败,请稍后重试 |

#### `UPLOAD_*` — 上传

| 错误码 | HTTP | 触发条件 | 面向用户提示建议 |
|---|---|---|---|
| `UPLOAD_EMPTY_FILE` | 400 | `file` 字段缺失或为空文件 | 请选择要上传的图片 |
| `UPLOAD_FILE_TOO_LARGE` | 400 | 文件超过 5MB | 图片不能超过 5MB |
| `UPLOAD_TYPE_NOT_ALLOWED` | 400 | 扩展名或内容类型不在 `jpg/jpeg/png/webp` | 仅支持 jpg/png/webp 格式 |
| `UPLOAD_STORE_FAILED` | 500 | 存储写入失败 | 图片上传失败,请稍后重试 |

合计 **88** 个错误码(与 79 个 endpoint 不是同一个数字:错误码按上下文收敛,一个码会被多个接口复用)。

### 2.4 阈值与自主拍板项

领域文档没有规定的数值与取舍,列在这里供复核。改这些只影响文档与实现,不动领域语义。

| # | 项目 | 本文的选择 | 说明 / 若你要改 |
|---|---|---|---|
| 1 | `pageSize` 超限 | **钳制为 100**,不报错 | 见 1.5 的理由;类型/符号非法才 400 |
| 2 | `sort` 未知字段 | **400 `COMMON_SORT_FIELD_NOT_ALLOWED`** | 见 1.6;静默忽略会让前端以为排序生效 |
| 3 | 未支付订单超时 | **15 分钟**,系统定时任务关单(`CANCELLED` + `cancelSide=SYSTEM`) | 领域文档 §4 只写了"15 分钟超时",与之一致 |
| 4 | 重复下单防护 | 同顾客 **10 秒**内相同内容 → 409 `ORDER_DUPLICATE_SUBMIT` | 领域文档未提;防连点。建议保留 |
| 5 | 催单节流 | 同订单 **5 分钟**一次 → 409 `ORDER_URGE_TOO_FREQUENT` | 领域文档未提;防骚扰 |
| 6 | 地址数量上限 | 每顾客 **20** 条 → 422 `ADDRESS_LIMIT_EXCEEDED` | 领域文档未提 |
| 7 | 上传限制 | `jpg/jpeg/png/webp`,单文件 **5MB** | 领域文档未提 |
| 8 | 报表区间跨度 | 上限 **366 天**;`topNumber` 上限 20(钳制) | 领域文档未提 |
| 9 | 报表缺省区间 | `beginDate`/`endDate` 都省略时取**最近 7 天** | 领域文档未提 |
| 10 | `total` 口径 | 顾客总数截至 `endDate` 当日 23:59:59;"今日"按 `Asia/Shanghai` | 领域文档未提时区 |
| 11 | 取消原因 | 顾客取消的 `reason` **可空**;商家取消/拒单 **必填** | 领域文档未提 |
| 12 | 外键失败的状态码 | 并发下外键失败统一 **409 `COMMON_CONFLICT`**,不使用 500 | 领域文档未提 |
| 13 | 退款受理失败 | **502 `PAY_REFUND_FAILED`**(唯一超出你给定状态码清单的一项) | 收敛到 500 只需改这一个码 |
| 14 | `traceId` 形式 | 16 位十六进制字符串(如 `0f3a1c2b4d5e6f70`) | 领域文档未规定 |
| 15 | **`CUSTOMER_*` 前缀** | 新增该前缀给顾客相关错误(领域文档只列了员工/顾客两个角色,未给顾客错误码前缀) | 若并入 `AUTH_*`/`COMMON_*`,需改 5 个码 |
| 16 | 报表接口不返回 `*_COMMENT` 对比文案 | 只返回数值,文案由前端拼 | 旧实现的 `dateList/orderCount` 对比文案属于展示层 |
| 17 | 顾客端不暴露 `openid` | 顾客资料不返回 `openid` | 领域文档未提;避免敏感标识外泄 |
| 18 | 支付参数中的 `null` 字段 | 非微信渠道(`MOCK`)时 `timeStamp`/`nonceStr`/`package`/`signType`/`paySign` 可为 `null` | 避免为 MOCK 造一套假签名 |
| 19 | `payMethod` 未支付时的值 | 用**空字符串** `""`(领域文档写"未支付时为空") | 若你想要 `null`,改 `PayMethod` 枚举 |

---

## 3. 接口清单索引

认证受众列:`员工` = `aud: admin` 令牌(其中标 `ADMIN` 的还需 `role=ADMIN`);`顾客` = `aud: customer` 令牌;`公开` = 无需 Bearer。

### 3.1 身份 Identity — 员工(管理端)

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| POST | `/api/v1/admin/auth/login` | 员工登录 | 公开 | `COMMON_VALIDATION_FAILED`、`AUTH_BAD_CREDENTIALS`、`EMPLOYEE_DISABLED` |
| POST | `/api/v1/admin/auth/logout` | 员工登出 | 员工 | `AUTH_TOKEN_INVALID`、`AUTH_REFRESH_TOKEN_INVALID` |
| POST | `/api/v1/admin/auth/refresh` | 刷新令牌(旋转 refresh) | 公开 | `AUTH_REFRESH_TOKEN_INVALID`、`AUTH_REFRESH_TOKEN_EXPIRED` |
| PUT | `/api/v1/admin/auth/password` | 修改自己密码 | 员工 | `AUTH_BAD_CREDENTIALS`、`COMMON_VALIDATION_FAILED` |
| GET | `/api/v1/admin/auth/me` | 查询当前登录员工 | 员工 | `AUTH_TOKEN_EXPIRED`、`AUTH_SUBJECT_NOT_FOUND` |
| GET | `/api/v1/admin/employees` | 员工分页查询 | 员工 `ADMIN` | `AUTH_PERMISSION_DENIED`、`COMMON_SORT_FIELD_NOT_ALLOWED` |
| POST | `/api/v1/admin/employees` | 新增员工 | 员工 `ADMIN` | `EMPLOYEE_USERNAME_TAKEN`、`COMMON_VALIDATION_FAILED` |
| GET | `/api/v1/admin/employees/{id}` | 按 id 查询员工 | 员工 `ADMIN` | `EMPLOYEE_NOT_FOUND` |
| PUT | `/api/v1/admin/employees/{id}` | 编辑员工 | 员工 `ADMIN` | `EMPLOYEE_NOT_FOUND`、`EMPLOYEE_SELF_ROLE_CHANGE` |
| PATCH | `/api/v1/admin/employees/{id}/status` | 启用/禁用员工 | 员工 `ADMIN` | `EMPLOYEE_NOT_FOUND`、`EMPLOYEE_SELF_DISABLE` |

### 3.2 身份 Identity — 顾客(小程序)

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| POST | `/api/v1/customer/auth/wechat-login` | 微信登录(code 换 openid),首次自动建档 | 公开 | `CUSTOMER_WECHAT_CODE_INVALID`、`CUSTOMER_DISABLED`、`CUSTOMER_DUPLICATE_OPENID` |
| POST | `/api/v1/customer/auth/refresh` | 刷新令牌 | 公开 | `AUTH_REFRESH_TOKEN_INVALID` |
| POST | `/api/v1/customer/auth/logout` | 登出 | 顾客 | `AUTH_REFRESH_TOKEN_INVALID` |
| GET | `/api/v1/customer/profile` | 查询当前顾客资料 | 顾客 | `AUTH_AUDIENCE_MISMATCH`、`CUSTOMER_DISABLED` |
| PUT | `/api/v1/customer/profile` | 更新资料(昵称/头像/手机号) | 顾客 | `COMMON_VALIDATION_FAILED` |

### 3.3 门店 Shop

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| GET | `/api/v1/admin/shop/status` | 管理端查询营业状态 | 员工 | `SHOP_STATUS_NOT_FOUND` |
| PUT | `/api/v1/admin/shop/status` | 切换营业状态/公告/营业时间 | 员工 | `SHOP_STATUS_NOT_FOUND`、`SHOP_BUSINESS_HOURS_INVALID` |
| GET | `/api/v1/customer/shop/status` | 顾客端查询营业状态与公告 | 顾客 | `SHOP_STATUS_NOT_FOUND`(打烊仍可浏览,R1;配置缺失不算"打烊") |

### 3.4 商品 Catalog — 管理端:分类

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| GET | `/api/v1/admin/categories` | 分类分页(按名称/类型/状态) | 员工 | `COMMON_SORT_FIELD_NOT_ALLOWED` |
| POST | `/api/v1/admin/categories` | 新增分类 | 员工 | `CATEGORY_NAME_TAKEN` |
| GET | `/api/v1/admin/categories/options` | 按类型列出分类(下拉用) | 员工 | `COMMON_VALIDATION_FAILED` |
| GET | `/api/v1/admin/categories/{id}` | 按 id 查询分类 | 员工 | `CATEGORY_NOT_FOUND` |
| PUT | `/api/v1/admin/categories/{id}` | 编辑分类 | 员工 | `CATEGORY_NAME_TAKEN`、`CATEGORY_TYPE_IMMUTABLE` |
| DELETE | `/api/v1/admin/categories/{id}` | 删除分类 | 员工 | `CATEGORY_IN_USE` |
| PATCH | `/api/v1/admin/categories/{id}/status` | 启用/禁用分类 | 员工 | `CATEGORY_NOT_FOUND` |

### 3.5 商品 Catalog — 管理端:菜品

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| GET | `/api/v1/admin/dishes` | 菜品分页(按名称/分类/状态) | 员工 | `COMMON_SORT_FIELD_NOT_ALLOWED` |
| POST | `/api/v1/admin/dishes` | 新增菜品(含口味配置) | 员工 | `DISH_NAME_TAKEN`、`DISH_CATEGORY_TYPE_MISMATCH`、`CATEGORY_DISABLED` |
| DELETE | `/api/v1/admin/dishes?ids=1,2` | 删除菜品(批量) | 员工 | `DISH_NOT_FOUND`、`SETMEAL_CONTAINS_DISH` |
| PATCH | `/api/v1/admin/dishes/status` | 起售/停售(批量,连带停售含它的套餐) | 员工 | `DISH_NOT_FOUND` |
| GET | `/api/v1/admin/dishes/{id}` | 按 id 查询菜品(含口味) | 员工 | `DISH_NOT_FOUND` |
| PUT | `/api/v1/admin/dishes/{id}` | 编辑菜品(口味整体替换) | 员工 | `DISH_NAME_TAKEN` |

### 3.6 商品 Catalog — 管理端:套餐

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| GET | `/api/v1/admin/setmeals` | 套餐分页 | 员工 | `COMMON_SORT_FIELD_NOT_ALLOWED` |
| POST | `/api/v1/admin/setmeals` | 新增套餐(含组成明细) | 员工 | `SETMEAL_NAME_TAKEN`、`SETMEAL_CATEGORY_TYPE_MISMATCH`、`SETMEAL_PRICE_EXCEEDS_ITEMS`、`SETMEAL_ITEMS_DUPLICATED` |
| DELETE | `/api/v1/admin/setmeals?ids=1,2` | 删除套餐(批量) | 员工 | `SETMEAL_NOT_FOUND` |
| PATCH | `/api/v1/admin/setmeals/status` | 起售/停售(批量) | 员工 | `SETMEAL_NOT_FOUND`、`SETMEAL_DISH_NOT_ON_SALE` |
| GET | `/api/v1/admin/setmeals/{id}` | 按 id 查询套餐(含所含菜品) | 员工 | `SETMEAL_NOT_FOUND` |
| PUT | `/api/v1/admin/setmeals/{id}` | 编辑套餐(组成整体替换) | 员工 | `SETMEAL_NAME_TAKEN`、`SETMEAL_PRICE_EXCEEDS_ITEMS` |

### 3.7 商品 Catalog — 顾客端

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| GET | `/api/v1/customer/catalog/categories?type=DISH` | 按类型查询分类列表(仅启用) | 顾客 | `COMMON_VALIDATION_FAILED` |
| GET | `/api/v1/customer/catalog/dishes?categoryId=10` | 按分类查询菜品列表(仅起售) | 顾客 | `CATEGORY_NOT_FOUND` |
| GET | `/api/v1/customer/catalog/dishes/{id}` | 菜品详情(含口味选项) | 顾客 | `DISH_NOT_FOUND`、`DISH_OFF_SALE` |
| GET | `/api/v1/customer/catalog/setmeals` | 套餐列表(可按分类) | 顾客 | `COMMON_VALIDATION_FAILED` |
| GET | `/api/v1/customer/catalog/setmeals/{id}` | 套餐详情(含所含菜品) | 顾客 | `SETMEAL_NOT_FOUND`、`SETMEAL_OFF_SALE` |

### 3.8 购物车 Cart — 顾客端

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| GET | `/api/v1/customer/cart/items` | 查询购物车(按分类分组,实时价) | 顾客 | `AUTH_TOKEN_EXPIRED` |
| POST | `/api/v1/customer/cart/items` | 加入购物车(同菜同口味自动合并) | 顾客 | `CART_ITEM_OFF_SALE`、`CART_FLAVOR_REQUIRED`、`CART_FLAVOR_INVALID`、`CART_QUANTITY_INVALID`、`DISH_NOT_FOUND`、`SETMEAL_NOT_FOUND` |
| DELETE | `/api/v1/customer/cart/items` | 清空购物车 | 顾客 | — |
| PUT | `/api/v1/customer/cart/items/{id}/quantity` | 修改数量(覆盖式;0 即删除) | 顾客 | `CART_ITEM_NOT_FOUND`、`CART_QUANTITY_INVALID` |

### 3.9 订单 Order — 顾客端

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| POST | `/api/v1/customer/orders/preview` | 订单预览/金额试算(不落库) | 顾客 | `ORDER_CART_EMPTY`、`ORDER_SHOP_CLOSED`、`ORDER_ITEM_NOT_ON_SALE` |
| POST | `/api/v1/customer/orders` | 下单提交 | 顾客 | `ORDER_CART_EMPTY`、`ORDER_SHOP_CLOSED`、`ORDER_ITEM_NOT_ON_SALE`、`ORDER_PRICE_CHANGED`、`ORDER_ADDRESS_INVALID`、`ORDER_DUPLICATE_SUBMIT` |
| GET | `/api/v1/customer/orders` | 我的订单分页(按状态筛选) | 顾客 | `COMMON_SORT_FIELD_NOT_ALLOWED` |
| GET | `/api/v1/customer/orders/{id}` | 订单详情(含不可变明细) | 顾客 | `ORDER_NOT_FOUND` |
| POST | `/api/v1/customer/orders/{id}/cancellation` | 取消订单(仅待付款) | 顾客 | `ORDER_INVALID_TRANSITION`、`ORDER_CANNOT_CANCEL` |
| POST | `/api/v1/customer/orders/{id}/reorder` | 再来一单(重新加购,不自动下单) | 顾客 | `ORDER_NOT_FOUND` |
| POST | `/api/v1/customer/orders/{id}/reminders` | 催单(推送 WebSocket) | 顾客 | `ORDER_URGE_NOT_ALLOWED`、`ORDER_URGE_TOO_FREQUENT` |

### 3.10 订单 Order — 管理端

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| GET | `/api/v1/admin/orders` | 订单分页(状态/时间区间/订单号/手机号) | 员工 | `COMMON_SORT_FIELD_NOT_ALLOWED`、`REPORT_DATE_RANGE_INVALID` |
| GET | `/api/v1/admin/orders/status-counts` | 各状态订单数量统计 | 员工 | `ORDER_STATUS_COUNT_FAILED` |
| GET | `/api/v1/admin/orders/{id}` | 订单详情 | 员工 | `ORDER_NOT_FOUND` |
| POST | `/api/v1/admin/orders/{id}/acceptance` | 接单 | 员工 | `ORDER_INVALID_TRANSITION` |
| POST | `/api/v1/admin/orders/{id}/rejection` | 拒单(已支付必须退款) | 员工 | `ORDER_INVALID_TRANSITION`、`PAY_REFUND_FAILED` |
| POST | `/api/v1/admin/orders/{id}/delivery` | 开始派送 | 员工 | `ORDER_INVALID_TRANSITION` |
| POST | `/api/v1/admin/orders/{id}/completion` | 完成订单(终态) | 员工 | `ORDER_INVALID_TRANSITION` |
| POST | `/api/v1/admin/orders/{id}/cancellation` | 取消订单(`DELIVERING` 不可取消) | 员工 | `ORDER_INVALID_TRANSITION`、`ORDER_CANNOT_CANCEL`、`PAY_REFUND_FAILED` |

### 3.11 支付与退款 Payment

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| POST | `/api/v1/customer/orders/{orderId}/payments` | 发起支付(返回小程序支付参数) | 顾客 | `PAY_ORDER_NOT_PAYABLE`、`PAY_DUPLICATE_PAYMENT`、`PAY_AMOUNT_MISMATCH`、`ORDER_PAY_TIMEOUT` |
| GET | `/api/v1/customer/orders/{orderId}/payments/status` | 查询支付结果(轮询) | 顾客 | `ORDER_NOT_FOUND` |
| GET | `/api/v1/admin/refunds` | 查询退款记录(分页) | 员工 | `COMMON_SORT_FIELD_NOT_ALLOWED` |
| POST | `/api/v1/admin/refunds` | 发起退款(整单全额退) | 员工 `ADMIN` | `PAY_ORDER_NOT_PAID`、`PAY_ORDER_NOT_REFUNDABLE`、`PAY_REFUND_ALREADY_EXISTS`、`PAY_REFUND_AMOUNT_EXCEEDED`、`PAY_REFUND_FAILED` |

### 3.12 支付平台回调 Notify

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| POST | `/api/v1/notify/wechat/pay` | 微信支付结果回调(幂等,R7) | 公开(平台签名/解密) | `PAY_NOTIFY_SIGNATURE_INVALID`、`PAY_NOTIFY_DECRYPT_FAILED`、`PAY_ORDER_NOT_FOUND` |
| POST | `/api/v1/notify/wechat/refund` | 微信退款结果回调(幂等) | 公开(平台签名/解密) | 同上 |

### 3.13 顾客资料 Profile — 地址簿

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| GET | `/api/v1/customer/addresses` | 地址列表 | 顾客 | — |
| POST | `/api/v1/customer/addresses` | 新增地址 | 顾客 | `ADDRESS_LIMIT_EXCEEDED` |
| GET | `/api/v1/customer/addresses/{id}` | 按 id 查询地址 | 顾客 | `ADDRESS_NOT_FOUND` |
| PUT | `/api/v1/customer/addresses/{id}` | 编辑地址 | 顾客 | `ADDRESS_NOT_FOUND`、`COMMON_VALIDATION_FAILED` |
| DELETE | `/api/v1/customer/addresses/{id}` | 删除地址 | 顾客 | `ADDRESS_NOT_FOUND` |
| PATCH | `/api/v1/customer/addresses/{id}/default` | 设为默认(先清空其它默认) | 顾客 | `ADDRESS_NOT_FOUND`、`ADDRESS_DEFAULT_DUPLICATED` |

### 3.14 数据洞察 Insights — 管理端

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| GET | `/api/v1/admin/insights/turnover-stats` | 营业额统计(逐日明细 + 总计) | 员工 | `REPORT_DATE_RANGE_INVALID`、`REPORT_DATE_RANGE_TOO_LARGE` |
| GET | `/api/v1/admin/insights/user-stats` | 用户统计(新增数/总量) | 员工 | `REPORT_DATE_RANGE_INVALID` |
| GET | `/api/v1/admin/insights/order-stats` | 订单统计(有效订单数/总数) | 员工 | `REPORT_DATE_RANGE_INVALID` |
| GET | `/api/v1/admin/insights/top-dishes` | 销量 Top10 | 员工 | `REPORT_DATE_RANGE_INVALID` |
| GET | `/api/v1/admin/insights/workbench` | 工作台概览(今日数据汇总) | 员工 | `REPORT_STATISTICS_FAILED` |

### 3.15 其他

| 方法 | 路径 | 用途 | 认证受众 | 主要错误码 |
|---|---|---|---|---|
| POST | `/api/v1/admin/uploads` | 图片上传(返回可访问 URL) | 员工 | `UPLOAD_EMPTY_FILE`、`UPLOAD_FILE_TOO_LARGE`、`UPLOAD_TYPE_NOT_ALLOWED`、`UPLOAD_STORE_FAILED` |
| WS | `/ws/admin/notifications?token=...` | 管理端实时通知(来单提醒/催单) | 员工 | 见第 4 节 |

---

## 4. WebSocket 通知协议

OpenAPI 3.1 无法描述 WebSocket,故在此约定。**通知上下文没有持久化状态**(领域文档 §2):它是"来单提醒/催单"的实时推送,消息丢失不回补,权威数据永远在 REST 接口里。

### 4.1 连接

| 项 | 约定 |
|---|---|
| 路径 | `/ws/admin/notifications` |
| 完整地址 | `ws://<host>/ws/admin/notifications?token=<accessToken>`(生产 `wss://`) |
| 鉴权 | 浏览器 WebSocket 不能自定义请求头,故令牌放**查询参数 `token`**;服务端校验方式与 REST 完全一致(签名 + `exp` + `aud=admin` + 员工状态) |
| 受众 | 仅员工令牌;`aud != admin` 一律拒绝连接(关闭码 `4403`) |
| 角色 | `ADMIN` 与 `STAFF` 都可连接(接单/派送是日常操作) |
| 多端 | 同一员工可多端连接,全部收到广播 |
| 集群 | 服务端用 Redis Pub/Sub 广播,保证多实例下所有连接都能收到 |

**连接失败关闭码**

| 关闭码 | 含义 | 客户端建议 |
|---|---|---|
| `4401` | 未携带 `token` 或令牌无效/过期 | 刷新令牌后重连;刷新失败则跳登录 |
| `4403` | 受众不匹配或员工被禁用 | 不重连,提示无权限 |
| `1000` | 服务端主动正常关闭(如重启、心跳超时) | 指数退避重连 |

### 4.2 消息结构

所有消息都是一个 JSON 对象,顶层固定 `type` + `timestamp`,负载放在 `payload`:

```json
{
  "type": "ORDER_NEW",
  "messageId": "b7f1c2d3-4e5f-6789-abcd-ef0123456789",
  "timestamp": "2025-01-01T12:00:05+08:00",
  "payload": { }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| `type` | string | 消息类型,见 4.3 |
| `messageId` | string(UUID) | 消息唯一 id,客户端按它**去重**(重连/集群广播可能重复投递) |
| `timestamp` | string | 服务端发出时间,ISO-8601 带偏移 |
| `payload` | object | 与 `type` 对应的载荷;`PONG` 的 `payload` 可为空对象 |

### 4.3 消息类型

#### 服务端 → 客户端

**`ORDER_NEW` — 来单提醒**(支付回调成功、订单进入 `PENDING_ACCEPTANCE` 时推送)

```json
{
  "type": "ORDER_NEW",
  "messageId": "b7f1c2d3-4e5f-6789-abcd-ef0123456789",
  "timestamp": "2025-01-01T12:00:05+08:00",
  "payload": {
    "orderId": 4001,
    "orderNo": "202501011200000001",
    "payAmountCents": 10400,
    "consignee": "张三",
    "phone": "13800138000",
    "detail": "望京街道 1 号院 2 号楼 3 单元 401",
    "itemCount": 3,
    "remark": "不要香菜",
    "placedAt": "2025-01-01T12:00:00+08:00",
    "paidAt": "2025-01-01T12:00:05+08:00"
  }
}
```

客户端建议:播放提示音 + 弹窗,点击后用 `orderId` 拉 `GET /api/v1/admin/orders/{id}` 取权威详情。

**`ORDER_URGE` — 催单**(顾客调 `POST /api/v1/customer/orders/{id}/reminders` 时推送)

```json
{
  "type": "ORDER_URGE",
  "messageId": "1a2b3c4d-5e6f-7890-abcd-ef0123456789",
  "timestamp": "2025-01-01T12:03:20+08:00",
  "payload": {
    "orderId": 4001,
    "orderNo": "202501011200000001",
    "status": "ACCEPTED",
    "message": "请尽快派送",
    "urgedAt": "2025-01-01T12:03:20+08:00"
  }
}
```

**`PONG` — 心跳应答**(回复客户端的 `PING`)

```json
{
  "type": "PONG",
  "messageId": "0f9e8d7c-6b5a-4321-fedc-ba9876543210",
  "timestamp": "2025-01-01T12:00:30+08:00",
  "payload": {
    "serverTime": "2025-01-01T12:00:30+08:00"
  }
}
```

#### 客户端 → 服务端

**`PING` — 心跳**

```json
{ "type": "PING", "timestamp": "2025-01-01T12:00:30+08:00" }
```

- 客户端每 **30 秒**发一次 `PING`;服务端 **90 秒**未收到任何帧则关闭连接(码 `1000`)。
- 服务端收到 `PING` 立即回 `PONG`。客户端 **60 秒**未收到 `PONG` 视为断线,主动重连。

### 4.4 客户端行为约定

1. **重连**:断线后按指数退避重连(1s、2s、4s、8s……上限 30s),退避期间检查令牌是否将过期;关闭码 `4403` 不重连。
2. **去重**:按 `messageId` 去重,窗口建议 5 分钟。
3. **不对通知做业务判断**:例如判断"是否已接单"必须重新拉 REST 数据,通知只是提示信号。
4. **令牌刷新**:重连时使用最新 access token;令牌过期应先用 `POST /api/v1/customer/auth/refresh`(顾客侧)或管理端刷新接口换新,再重连。
5. 服务端**不保证**消息不丢、不保证顺序:这是提醒通道,不是状态同步通道。

---

## 5. 与旧实现的差异说明

本节对应领域文档 §6 的决策表,只列出**对接口消费方(两个前端)有影响**的部分。

| # | 旧实现 | v2 约定 | 前端要改什么 |
|---|---|---|---|
| 1 | 恒定 HTTP 200 + `{code, msg, data}` 信封;成功 `code=1`,失败 `code=0` | 成功**直接返回资源**,失败用**正确状态码** + `{code, message, details, traceId}`(D10) | 删掉 `res.data.data` 解包;axios/uni.request 拦截器改为按 HTTP 状态码分支;错误码从数字比较改为字符串比较 |
| 2 | 路径前缀 `/admin/**`、`/user/**`、`/notify/**` | 统一 `/api/v1/**`,顾客前缀是 `/customer/**`(D2) | api 层路径全部重写;`user` → `customer` |
| 3 | 顾客实体叫 `user`,员工账号也叫 `user`(二义) | 顾客是 `Customer`;员工是 `Employee` | 类型命名、路由命名、状态管理模块命名 |
| 4 | 登录靠 `name.equals("admin")` 判超管 | 显式 `role` 字段 + 令牌载荷 `role`(D3) | 菜单/按钮权限改为按 `role` 判断;不再有"用户名是 admin"的隐式约定 |
| 5 | 金额用浮点/`Decimal`,`amount`、`packageFee`、`deliveryFee` 三列 | 整数分 + `Cents` 后缀五列(D1) | 所有金额展示除以 100;表单输入的元要转分;校验用整数 |
| 6 | 购物车存了 `name`/`image`/`amount` 快照 | 购物车**不存快照**,读取时联表实时值(D4) | 购物车字段来自实时数据,商家改价后列表立即变;不要再缓存购物车价格 |
| 7 | 订单只存地址 id,改地址后历史订单跟着变 | 地址**快照进订单**(D5) | 订单详情从订单字段读地址,不再 join 地址表 |
| 8 | 订单时间列 `order_time`/`checkout_time`/`delivery_time`/`cancel_time` 语义混乱 | 每个状态一个时间戳:`placedAt`/`paidAt`/`acceptedAt`/`deliveringAt`/`completedAt`/`cancelledAt` | 时间轴展示改写;不再靠 `status` 猜哪个时间有效 |
| 9 | 订单状态是数字 `1..6`,判断散落在 service | 字符串状态机 `PENDING_PAYMENT`…`CANCELLED`(D10/领域 §4) | 状态判断改为字符串常量;非法迁移统一 422 `ORDER_INVALID_TRANSITION` |
| 10 | 口味选项是逗号拼接字符串 `"不辣,微辣"` | `options` 是 JSON 字符串数组(D7) | 口味组件直接消费数组,不再 `split(',')` |
| 11 | 登录只发一个长效 token,登出靠前端删本地存储 | access(2h) + refresh(7d, 可撤销, 存 Redis)(D11) | 拦截器增加 401 自动刷新 + 重放;新增登出接口调用 |
| 12 | 分页返回 `{total, records}`,参数 `page`/`pageSize` | 信封增加 `page`/`pageSize` 回显;`pageSize` 上限 100(钳制) | 分页组件读取回显的 `page`/`pageSize`;不要传 >100 |
| 13 | 排序参数各不相同或没有 | 统一 `?sort=字段,asc\|desc` + 白名单 | 列表排序抽成公共参数 |
| 14 | 删除是软删除(`is_deleted`) | **不做软删除**;禁用用 `status`,真删除靠外键(D13) | "删除"后资源确实消失(404),不要依赖再次查询到已删除数据 |
| 15 | 支付信息内嵌在订单表 | `Payment` / `Refund` 独立资源(D6) | 支付/退款查询改为独立接口;订单上只有冗余 `payStatus` |
| 16 | 员工/顾客共用一套令牌 | 两套独立签发、`aud` 区分 | 小程序与管理端各自独立的 token 存储与刷新逻辑 |

---

## 6. OpenAPI 文件的使用说明

`docs/openapi.yaml` 是 OpenAPI **3.1.0** 文档,是接口的唯一真相。两个前端都从它生成 TypeScript 类型,不手写接口类型。

### 6.1 生成 TypeScript 类型

**管理端(Vue3 + TypeScript)** —— 用 `openapi-typescript`(只生成类型,零运行时):

```bash
# 仓库根目录
pnpm dlx openapi-typescript docs/openapi.yaml -o apps/admin/src/types/api.d.ts
```

**小程序(uni-app + TypeScript)** —— 同样用 `openapi-typescript`,输出到小程序侧:

```bash
pnpm dlx openapi-typescript docs/openapi.yaml -o apps/miniapp/src/types/api.d.ts
```

生成后按受众切分命名空间,避免把两套接口混在一个文件里:

```ts
import type { paths, components } from '@/types/api'

type Schemas = components['schemas']

// 资源类型
export type Order = Schemas['OrderDetail']
export type Dish  = Schemas['DishDetail']

// 接口响应类型(按 operationId 取更稳,路径变了也不影响)
// openapi-typescript 会为每个 operation 生成 operationId 索引
export type OrderSubmitResult = Schemas['OrderSubmitResult']
```

**若偏好客户端 SDK**:`orval` / `openapi-generator` 也支持 3.1:

```bash
pnpm dlx orval --input docs/openapi.yaml --output src/api/generated.ts
```

### 6.2 校验与预览

```bash
# 规范校验(不联网安装也可用仓库内已有的 linter;需要时用 npx)
npx @redocly/cli lint docs/openapi.yaml

# 本地预览接口文档(可选)
npx @redocly/cli preview-docs docs/openapi.yaml
```

### 6.3 变更流程

1. 先改 `docs/01-domain.md`(语义变了才改接口);
2. 再改 `docs/openapi.yaml`(字段细节的唯一真相);
3. 最后同步本文的第 2.3 节错误码表与第 3 节索引表;
4. 重新生成两个前端的 `api.d.ts` 并提交,让类型变更在编译期暴露。

**兼容性约定**:新增可选字段是兼容变更;删除字段、改字段名、改金额单位、改状态枚举值都是破坏性变更,需要同步两个前端并升 `info.version`。

### 6.4 代码生成注意事项

- **错误响应不在成功 schema 里**:成功响应是资源本身,错误是 `ErrorResponse`。生成器不会把两者合并,前端需要区分 `200/201/204` 与 `4xx/5xx` 两条路径。
- **204 无响应体**:生成器会产出 `void`,调用方不要尝试解析 JSON。
- **`nullable: true`**:OpenAPI 3.1 里等价于 `type: [X, 'null']`,`openapi-typescript` 会生成 `X | null`。
- **分页类型是按接口具名的**(`PagedOrder`、`PagedDish`…),不是泛型,以避免部分生成器对泛型 `allOf` 支持不佳。
- **`/api/v1/notify/**` 的应答**是 `WechatNotifyAck`,不是 `ErrorResponse`;两个前端都不需要消费这两个接口。
- **WebSocket 不在 OpenAPI 内**,类型需在各自前端手写(见第 4 节的 JSON 结构)。
