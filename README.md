# 苍穹外卖 (Sky Takeout)

前后端分离的外卖系统（Spring Boot + Vue 3 学习项目）。

## 项目结构

```
.
├── docker-compose.yml     # 基础设施：MySQL + Redis
├── sky-admin/             # 前端管理后台 (Vue 3 + TypeScript + Vite)
│   ├── .env.development   # VITE_BASE_API=/api, 代理到后端 8080
│   └── vite.config.ts     # dev server 端口 8888
└── sky-backend/           # 后端 (Spring Boot 4 多模块聚合工程)
    ├── sky-common/        # 公共工具类、常量、异常、配置属性
    ├── sky-server/        # 启动模块，framework 基础设施，端口 8080
    ├── sky-employee/      # 员工/管理员模块
    ├── sky-menu/          # 分类 / 菜品 / 套餐模块
    ├── sky-user/          # 用户 / 购物车 / 地址簿模块
    ├── sky-order/         # 订单模块
    ├── sky-report/        # 报表 / 工作台模块
    └── sky-shop/          # 店铺营业状态模块
```

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 4.1, MyBatis-Plus 3.5, Druid, Redis, Spring Security + JWT, springdoc, WebSocket |
| 前端 | Vue 3.5, TypeScript, Element Plus, Pinia, Vite, axios, ECharts |
| 数据库 | MySQL 8, Redis 7 |

## 快速启动

1. 启动基础设施：

   ```bash
   docker compose up -d
   ```

2. 初始化数据库（脚本位于 `sky-backend/sky-server/src/main/resources/sql/`）：

   ```bash
   docker exec -i sky-mysql mysql -uroot -proot --default-character-set=utf8mb4 < sky-backend/sky-server/src/main/resources/sql/sky_take_out.sql
   docker exec -i sky-mysql mysql -uroot -proot --default-character-set=utf8mb4 < sky-backend/sky-server/src/main/resources/sql/sky_take_out_seed.sql
   ```

3. 启动后端：

   ```bash
   cd sky-backend && mvn -pl sky-server clean package -DskipTests
   java -jar sky-server/target/sky-server-1.0-SNAPSHOT.jar
   ```

4. 启动前端：

   ```bash
   cd sky-admin && pnpm install
   pnpm dev
   ```

5. 访问 http://localhost:8888 ，管理员账号 `admin` / `123456`

## 备注

- 环境要求：JDK 17+（已适配 JDK 25：Lombok 1.18.46 + `-proc:full`）、Node 20+、pnpm
- 微信支付为演示配置（`sky.wechat.pay-enabled` 未开启时，取消已支付订单不会调用真实微信退款接口），生产环境请替换真实商户证书
- 数据库连接：`localhost:3306/sky_take_out` (root/root)；Redis：`localhost:6379` (123456, db 10)

## 密码存储升级（MD5 → BCrypt）

员工密码已从 MD5 迁移为 BCrypt（`BCryptPasswordEncoder`）。若你的数据库在升级前已初始化过（仍存 MD5 哈希），需执行以下脚本将存量账号重置为 BCrypt，否则老账号无法登录：

```bash
docker exec -i sky-mysql mysql -uroot -proot --default-character-set=utf8mb4 sky_take_out -e "
UPDATE employee SET password = '\$2a\$10\$e./WtpvCl/1fClkIb1vGM.cY87zKJ0IS.ZVwbQFxUGwpiMKomEq6y'
WHERE password = 'e10adc3949ba59abbe56e057f20f883e';"
```

仅影响密码为默认 MD5（123456）的账号，改后密码仍为 `123456`。