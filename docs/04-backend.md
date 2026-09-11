# 苍穹外卖 v2 · 后端架构

> 技术栈:Spring Boot 4.1 + MyBatis-Plus 3.5 + MySQL 8 + Redis 7 + Flyway + JWT。
> 形态:**模块化单体**。按限界上下文分包,包内分层;不拆微服务,不建 9 个 Maven 模块。

## 1. 为什么是单模块而不是多 Maven 模块

旧实现把后端拆成 `sky-common`/`sky-auth`/`sky-employee`/`sky-menu`/`sky-user`/`sky-order`/`sky-report`/`sky-shop`/`sky-server` 九个 Maven 模块。代价是:

- 改一个字段要在 3~4 个 `pom.xml` 之间来回跳,跨模块调用要么开 `public` 要么再包一层;
- 模块边界靠人记,编译期只能挡住"没加依赖",挡不住"依赖方向错了";
- 模块粒度与限界上下文并不一致(`sky-user` 里同时有顾客、地址、购物车)。

v2 用**一个 Maven 模块 + 包结构 + 架构测试**表达同样的边界:边界可见、违规会被测试直接打红,而不用付出多模块的构建与导航成本。等真的需要独立部署时再拆——那时按上下文拆,而不是按 `common/auth/xxx` 拆。

## 2. 目录结构

```
backend/
├── pom.xml
└── src/main/
    ├── java/com/sky/
    │   ├── SkyApplication.java
    │   ├── common/                 跨上下文共享的**技术**设施(不含业务概念)
    │   │   ├── error/              ErrorCode / BusinessException / GlobalExceptionHandler / ErrorResponse
    │   │   ├── web/                PageResponse / PageQuery
    │   │   ├── persistence/        审计字段填充 / MyBatis-Plus 配置辅助
    │   │   └── util/
    │   ├── config/                 Spring 配置装配(Security / MyBatis / Redis / WebSocket / OpenAPI / Web / Jackson)
    │   ├── security/               认证与授权(JWT、令牌服务、过滤器、当前主体)
    │   └── <context>/              限界上下文,取值见下
    │       ├── api/                控制器 + 请求/响应 DTO
    │       ├── service/            应用服务(事务边界、编排)
    │       ├── domain/             实体、枚举、领域异常、状态机、策略接口
    │       ├── mapper/             MyBatis-Plus Mapper
    │       └── gateway/            外部系统适配(仅需要的上下文有)
    └── resources/
        ├── application.yml
        ├── db/migration/           Flyway 迁移脚本(表结构的唯一来源)
        └── mapper/<context>/*.xml  复杂 SQL
```

上下文取值:`identity`、`shop`、`catalog`、`cart`、`order`、`profile`、`insights`、`notification`。

## 3. 分层规则(由 ArchUnit 强制)

| 规则 | 说明 |
|---|---|
| L1 | `api` 只能依赖同上下文的 `service` 与 `domain`,**不得**依赖 `mapper`、`gateway` |
| L2 | `service` 可以依赖同上下文的 `domain`、`mapper`、`gateway` |
| L3 | `domain` 不得依赖 `api`、`service`、`mapper`、`gateway`,也不得依赖 Spring Web / MyBatis-Plus 之外的框架 |
| L4 | 跨上下文调用**只能**经由对方 `service` 包中的类型;不得直接引用别人的 `domain`、`mapper`、`api` |
| L5 | `common`、`security` 不得依赖任何业务上下文 |
| L6 | 禁止字段注入(`@Autowired` 在字段上),统一构造器注入 |
| L7 | 控制器不得直接使用 `mapper` |

**刻意的折中**:实体类直接放在 `domain` 上并标注 MyBatis-Plus 注解(`@TableName`/`@TableId`),不再额外维护一层 PO + 转换器。理由是这一层映射在本项目里只增加代码量、不增加隔离价值;真正的隔离价值(禁止控制器碰 mapper、禁止跨上下文乱引用)由 L1/L4/L7 保证。

## 4. 横切关注点

### 4.1 错误模型

- `ErrorCode`:枚举,实现 `code()` / `httpStatus()` / `defaultMessage()` 三件事,取值与 `docs/03-api.md` 的错误码表**逐字对应**
- `BusinessException extends RuntimeException`:携带 `ErrorCode` + 可选的 `details`(字段级原因)
- `GlobalExceptionHandler`(`@RestControllerAdvice`)把以下都翻译成统一错误体:
  - `BusinessException` → 其 `ErrorCode` 决定的状态码
  - `MethodArgumentNotValidException` / `ConstraintViolationException` → 400 + `COMMON_VALIDATION_FAILED`
  - `HttpMessageNotReadableException` → 400
  - `NoResourceFoundException` / `NoHandlerFoundException` → 404
  - `DuplicateKeyException` → 409
  - 其他 `Exception` → 500 + `COMMON_INTERNAL_ERROR`(打完整堆栈,**不外泄**内部信息)
- 错误体形状固定:`{ code, message, details[], traceId }`。`traceId` 由过滤器生成并放入 MDC,日志与响应共用同一个值

### 4.2 认证与授权

- 无状态,不使用 Session;`SecurityFilterChain` 按路径前缀划分受众:
  - `/api/v1/admin/**` 需要 `ADMIN` 或 `STAFF` 角色
  - `/api/v1/admin/employees/**` 仅 `ADMIN`
  - `/api/v1/customer/**` 需要 `CUSTOMER` 角色
  - `/api/v1/notify/**` 免认证,改为校验平台签名
  - 登录接口、静态资源、`/v3/api-docs/**`、`/swagger-ui/**` 放行
- **令牌受众必须在签名载荷里区分**(`aud`),不能只靠路径判断:否则顾客令牌能直接打管理端接口
- access token 2h;refresh token 7d,`jti` 存 Redis,登出即删,续期时轮换(旧的立即失效)
- 401 与 403 各自的自定义 handler 都要输出统一错误体,不能返回 Spring 默认的空响应

### 4.3 审计字段

- `created_at` / `updated_at` 由**数据库默认值**负责(`DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE`),应用层不赋值
- `created_by` / `updated_by` 由 MyBatis-Plus `MetaObjectHandler` 从当前主体填充;系统任务填 `0`
- `CurrentPrincipal` 从 `SecurityContext` 取,不通过方法参数层层传递

### 4.4 事务

- 事务边界**只在** `service` 层,`@Transactional` 标在应用服务的写方法上
- 领域事件(状态变更后通知)用 `@TransactionalEventListener(phase = AFTER_COMMIT)`,避免推送发出去了而事务回滚
- 跨上下文写操作(下单一并清空购物车)放在一个 `order` 上下文的服务方法里,通过 `cart` 上下文的服务接口完成任务——单一事务,不做分布式事务

### 4.5 时间与金额

- 数据库存 `DATETIME`,JVM 与容器时区固定 `Asia/Shanghai`
- 实体用 `LocalDateTime`;**对外 DTO 用 `OffsetDateTime`**,Jackson 输出 `2025-01-01T12:00:00+08:00`。转换集中在 `common/util` 的一个工具方法里
- 金额全链路整数分;实体、DTO、计算都不出现 `double`/`float`/`BigDecimal`

### 4.6 持久化约定

- Mapper 继承 `MyBatis-Plus` 的 `BaseMapper<T>`;复杂查询写在 `resources/mapper/<context>/*.xml`
- 分页统一用 `PaginationInnerInterceptor(DbType.MYSQL)`,返回 `PageResponse<T>`
- 列表接口一律**显式**给出排序,不依赖数据库默认顺序
- 不启用 MyBatis-Plus 的逻辑删除(`@TableLogic`)——见领域文档 D13

### 4.7 外部系统适配

统一用 `gateway` 包下的接口 + 多实现,靠配置切换,本地联调不依赖外部账号:

| 能力 | 接口 | 实现 | 本地开关 |
|---|---|---|---|
| 支付 | `PaymentGateway` | `MockPaymentGateway` / `WechatPaymentGateway` | `sky.payment.gateway=mock` |
| 文件存储 | `StorageService` | `LocalStorageService`(落盘 + `/files/**`) | `sky.storage.type=local` |
| 微信登录 | `WechatAuthClient` | `MockWechatAuthClient` / `RealWechatAuthClient` | `sky.wechat.mock=true` |

**约束**:真实实现不得让应用启动失败。缺少密钥时,真实实现要么不注册,要么在首次调用时抛出带明确错误码的异常。

### 4.8 WebSocket

- 端点 `/ws/admin`,握手阶段校验 access token(通过 `Sec-WebSocket-Protocol` 或查询参数传递)
- 按员工维度维护会话;推送消息为 `{ "type": "ORDER_NEW" | "ORDER_REMIND", "payload": {...} }`
- 推送由订单状态变更事件在**事务提交后**触发

## 5. 测试策略

| 类型 | 范围 | 是否依赖外部服务 |
|---|---|---|
| 架构测试 | ArchUnit 校验 §3 的 L1~L7 | 否 |
| 领域单元测试 | `OrderStateMachine` 全部合法/非法迁移、金额计算、口味归一化 | 否 |
| 服务测试 | 应用服务 + Mock Mapper,覆盖 D1 关键规则(如 R5 金额重算、R3 事务) | 否 |
| 接口测试 | `@SpringBootTest` + MockMvc,覆盖鉴权与错误码 | 需 MySQL/Redis(`docker compose up -d`) |

`mvn test` 必须能在只起了 `docker compose` 的机器上全绿;不引入 Testcontainers。

## 6. 与旧实现的差异

| v2 | 旧实现 | 原因 |
|---|---|---|
| 单模块 + 包边界 + ArchUnit | 9 个 Maven 模块 | 见 §1 |
| `common/error` 统一错误码枚举 | 各模块自带异常与错误信息 | 错误码可枚举、可对前端文档、可测试 |
| 令牌区分 `aud` 受众 | 两套配置但载荷未区分 | 顾客令牌不能打管理端 |
| refresh token + Redis 撤销 | 仅 access token | 支持登出与安全续期 |
| `@TransactionalEventListener` 提交后推送 | 事务内直接推送 | 避免推送成功但事务回滚 |
| 金额整数分 | `BigDecimal`/`double` 混用 | 消除舍入 |
| 无逻辑删除 | 部分表 `is_deleted` | 见领域文档 D13 |
