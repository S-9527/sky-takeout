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
| 管理端 | Vue 3.5、TypeScript、Element Plus、Pinia、Vite、Tailwind CSS v4、ECharts |
| 用户端 | uni-app、Vue 3、TypeScript、Pinia、Tailwind CSS v4 |
| 测试 | 后端 JaCoCo(行覆盖率门禁 60%)+ ArchUnit;前端 Vitest + v8 覆盖率 |

> **Tailwind CSS v4 在小程序端是实验性能力。** 走 `weapp-tailwindcss` 的 v4 方案:
> `@tailwindcss/vite` 只处理独立 `.css` 文件(不能写在 `App.vue` 的 `<style>` 里),
> 且需要 `UnifiedViteWeappTailwindcssPlugin` 把原子类翻译成小程序可用的选择器。
> H5 端无此限制。详见 [官方 uni-app v4 指引](https://sonofmagic.github.io/weapp-tailwindcss/docs/quick-start/v4/uni-app-vite)。

## 快速启动

### 1. 基础设施

```bash
docker compose up -d
```

**表结构不需要手工导入。** 后端启动时由 Flyway 自动执行 `backend/src/main/resources/db/migration/` 下的迁移脚本建表并灌入开发种子数据。这是唯一的建表路径。

<details>
<summary><b>如果 Docker 跑在 WSL 里(本仓库当前开发环境)</b></summary>

`.wslconfig` 使用 `networkingMode=Mirrored` 时,WSL 内**普通进程**监听的端口能从 Windows 的 localhost 访问,但 Docker 经 bridge 网络 + iptables 发布的端口**不行**——实测 Windows 连 `127.0.0.1:3306` 被直接拒绝,换 `network_mode: host` 的容器同端口就能连通。因为后端跑在 Windows 上,必须加上覆盖文件:

```bash
wsl docker compose \
  -f /mnt/d/projects/sky-takeout/docker-compose.yml \
  -f /mnt/d/projects/sky-takeout/docker-compose.wsl.yml up -d
```

`docker-compose.wsl.yml` 只是把两个服务改成 `network_mode: host` 并清掉端口映射,不改任何数据卷。不在这个环境时不要加它。

另一个替代做法是在 `.wslconfig` 的 `[experimental]` 段加 `hostAddressLoopback=true` 并 `wsl --shutdown` 重启,那样标准 compose 文件即可直连;但那会影响整机 WSL 行为,本仓库选择用覆盖文件把影响限制在项目内。

**注意 WSL 空闲休眠。** WSL2 在发行版空闲一段时间后会挂起 VM,容器随之停摆;此时 Windows 侧连 `127.0.0.1:3306` 会被拒绝,后端启动报
`Communications link failure`。这个报错具有误导性——它看起来像配置错了数据库地址,实际只是 WSL 睡了。判断方法:跑一句 `wsl docker ps`,如果容器状态显示 `Up N seconds`(而不是 `Up N minutes`),就是刚被唤醒重建。

唤醒并等就绪:

```bash
wsl docker compose \
  -f /mnt/d/projects/sky-takeout/docker-compose.yml \
  -f /mnt/d/projects/sky-takeout/docker-compose.wsl.yml up -d
```

嫌麻烦可以在 WSL 里挂一个常驻进程压住休眠(开发期间有效),例如 `wsl bash -lc "sleep infinity"` 放在一个后台任务里。

**另外:WSL 里没有 JDK。** 本机 JDK/Maven 装在 Windows 侧(`D:\scoop\apps\openjdk25`),Maven 在 WSL 里是通过 `/mnt/d/...` 访问到的 Windows 版。所以后端要在 Windows 上跑;若要在 WSL 内跑后端,需先在发行版里装 JDK 21+ 与 Maven。

</details>

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

### 5. 端到端冒烟(可选,后端已启动时)

`mvn test` 是不依赖任何外部服务的单元测试 + 架构测试;跨真实 MySQL/Redis 的验证在脚本里:

```bash
node scripts/smoke-identity.mjs   # 身份:登录/令牌轮换/受众隔离/自我保护,19 项
node scripts/smoke-shop.mjs       # 门店:营业状态读写/时间校验/顾客端视图,15 项
node scripts/smoke-catalog.mjs    # 商品-分类:分页/唯一性/类型不可改/启停用可见性/外键删除保护,30 项
node scripts/smoke-dish.mjs       # 商品-菜品:口味 JSON 往返/整体替换/批量子状态/分类启用约束,37 项
```

脚本打的是 `http://localhost:8080`(可用 `SKY_BASE_URL` 覆盖),失败时以非零码退出,可直接串进 CI。
三个脚本会写库,但都自行还原:`smoke-shop.mjs` 结束时把营业状态写回原值,`smoke-catalog.mjs` /
`smoke-dish.mjs` 只创建 / 删除自己造的 `smoke-` 记录,不碰种子数据。

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
