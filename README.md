# 苍穹外卖 v2

前后端分离的外卖系统:Spring Boot 后端 + Vue 3 管理端 + uni-app 小程序用户端。

> **这是 `rewrite/v2` 分支上的完全重写。** 旧实现完整保留在 `main` 分支,仅作参考,不参与构建。
> 重写不继承旧代码,但沿用技术栈;领域模型、数据库表结构、接口契约全部重新设计。

## 仓库结构

```
.
├── docker-compose.yml   # 基础设施:MySQL 8.4 + Redis 7(仅此二者)
├── docs/                # 设计文档 —— 先读这里
│   ├── 01-domain.md     # 领域模型、订单状态机、业务规则、设计决策(唯一语义来源)
│   ├── 02-database.md   # 表结构设计
│   ├── 03-api.md        # 接口约定与错误码表
│   ├── openapi.yaml     # 接口契约(唯一真相,前端类型由此生成)
│   └── 04-backend.md    # 后端架构与分层规则
├── backend/             # Spring Boot 4.1 模块化单体
├── admin/               # 管理端:Vue 3 + TypeScript + Element Plus
└── miniapp/             # 用户端:uni-app + Vue 3 + TypeScript
```

**文档的优先级**:`01-domain.md` 是语义来源,`openapi.yaml` 是接口真相。两者冲突时以领域文档为准并回头修正接口;代码与文档冲突时,先判定谁错了,不要默默让代码漂移。

## 阅读顺序

1. `docs/01-domain.md` —— 搞清有哪些实体、订单怎么流转、哪些规则不能破
2. `docs/02-database.md` —— 表怎么建、索引服务哪些查询
3. `docs/03-api.md` + `docs/openapi.yaml` —— 接口长什么样
4. `docs/04-backend.md` —— 代码怎么分层、边界由什么强制

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 4.1、MyBatis-Plus 3.5、Druid、MySQL 8、Redis 7、Flyway、Spring Security + JWT、springdoc、WebSocket |
| 管理端 | Vue 3.5、TypeScript、Element Plus、Pinia、Vite、ECharts |
| 用户端 | uni-app、Vue 3、TypeScript、Pinia |

## 快速启动

### 1. 基础设施

```bash
docker compose up -d
```

**表结构不需要手工导入。** 后端启动时由 Flyway 自动执行 `backend/src/main/resources/db/migration/` 下的迁移脚本建表并灌入开发种子数据。这是唯一的建表路径。

### 2. 后端

```bash
cd backend
mvn clean package -DskipTests
java -jar target/sky-takeout-backend-2.0.0.jar
```

服务在 `http://localhost:8080`;接口文档在 `http://localhost:8080/swagger-ui/index.html`。

### 3. 管理端

```bash
cd admin
pnpm install
pnpm dev
```

### 4. 用户端

```bash
cd miniapp
pnpm install
pnpm dev:h5         # H5 调试
pnpm dev:mp-weixin  # 微信开发者工具
```

## 本地开发配置

后端默认连本机 `localhost` 的 MySQL(root/root)与 Redis(123456,db 0),与小节 1 的 `docker-compose.yml` 一致,开箱即用。需要覆盖时用环境变量,不必改代码:

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SKY_DB_HOST` / `SKY_DB_PORT` / `SKY_DB_NAME` | `localhost` / `3306` / `sky_take_out` | 数据库 |
| `SKY_DB_USER` / `SKY_DB_PASSWORD` | `root` / `root` | 数据库账号 |
| `SKY_REDIS_HOST` / `SKY_REDIS_PORT` / `SKY_REDIS_PASSWORD` | `localhost` / `6379` / `123456` | Redis |
| `SKY_JWT_SECRET` | 开发用默认值 | **生产必须覆盖** |
| `SKY_PAYMENT_GATEWAY` | `mock` | `mock` 发起支付即成功,本地联调不需要微信商户号 |
| `SKY_STORAGE_TYPE` / `SKY_STORAGE_DIR` | `local` / `./data/upload` | 图片落盘位置 |

默认账号:`admin` / `123456`(管理员),`zhangsan` / `123456`(普通员工)。

## 环境要求

- JDK 21+
- Maven 3.9+
- Node 20+ 与 pnpm
- Docker(用于本地 MySQL/Redis)

## 与 v1 的关系

`main` 分支保留旧实现的全部代码与历史,可随时查阅:

```bash
git show main:sky-backend/sky-server/src/main/resources/application.yml   # 看旧配置
git log main --oneline                                                    # 看旧提交
```

v2 相对 v1 的关键设计偏离逐条记录在 `docs/01-domain.md` 的「待确认的设计决策」一节,不在本文件重复。
