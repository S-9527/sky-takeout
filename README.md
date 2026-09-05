# 苍穹外卖 (Sky Takeout)

前后端分离的外卖系统（Spring Boot + Vue 2 学习项目）。

## 项目结构

```
.
├── docker-compose.yml     # 基础设施：MySQL + Redis
├── sky-admin/             # 前端管理后台 (Vue 2 + TypeScript)
│   ├── .env.development   # VUE_APP_BASE_API=/api, 代理到后端
│   └── vue.config.js      # dev server 端口 8888
└── sky-backend/           # 后端 (Spring Boot 2.7 多模块聚合工程)
    ├── sky-common/        # 公共工具类、配置属性
    ├── sky-pojo/          # 实体 / DTO / VO
    └── sky-server/        # 启动模块，端口 8080
```

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 2.7.3, MyBatis, PageHelper, Druid, Redis, JWT, Knife4j, WebSocket |
| 前端 | Vue 2.6, TypeScript, Element UI, Vuex, axios, ECharts |
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
   cd sky-admin && npm install
   npm run serve
   ```

5. 访问 http://localhost:8888 ，管理员账号 `admin` / `123456`

## 备注

- 环境要求：JDK 8+（已适配 JDK 25：Lombok 1.18.46 + `-proc:full`）、Node 16+
- 微信支付为演示配置（`sky.wechat.pay-enabled` 未开启时，取消已支付订单不会调用真实微信退款接口），生产环境请替换真实商户证书
- 数据库连接：`localhost:3306/sky_take_out` (root/root)；Redis：`localhost:6379` (123456, db 10)