# Apifox 接口文档

后端运行中通过 springdoc-openapi (OpenAPI 3.1) 实时导出：

| 文件 | 分组 | 接口数 |
|---|---|---|
| `sky-admin-api.json` | 管理端接口 (com.sky 下 /admin/**) | 40 |
| `sky-user-api.json` | 用户端接口 (/user/**) | 21 |
| `sky-notify-api.json` | 支付回调接口 (/notify/**) | 1 |

重新生成方式（后端须运行在 8080）：

```bash
curl -s "http://localhost:8080/v3/api-docs/%E7%AE%A1%E7%90%86%E7%AB%AF%E6%8E%A5%E5%8F%A3" -o sky-admin-api.json
curl -s "http://localhost:8080/v3/api-docs/%E7%94%A8%E6%88%B7%E7%AB%AF%E6%8E%A5%E5%8F%A3" -o sky-user-api.json
curl -s "http://localhost:8080/v3/api-docs/%E6%94%AF%E4%BB%98%E5%9B%9E%E8%B0%83%E6%8E%A5%E5%8F%A3" -o sky-notify-api.json
```

分组配置位于 `sky-server/.../framework/config/WebMvcConfiguration.java`（`GroupedOpenApi`），分组名需 URL 编码。亦可用 `jq '.paths | length' <file>` 核对接口数。

## 导入 Apifox

1. Apifox → 「新建导入」→ 选择「Swagger / OpenAPI」
2. 导入 `sky-admin-api.json`，页面提示导入到新的项目后创建项目「苍穹外卖」
3. 再导入 `sky-user-api.json`、`sky-notify-api.json`，选择导入到刚创建的「苍穹外卖」项目（自动合并为同一项目）
4. 也可以直接访问 Swagger UI 在线文档：http://localhost:8080/swagger-ui/index.html

## 鉴权说明

- 管理端接口除 `POST /admin/employee/login` 外，请求头需携带 **`Authorization`**：`Bearer {员工登录返回的 token}`
- 用户端接口除 `POST /user/user/login`、`GET /user/shop/status` 外，请求头需携带 **`Authorization`**：`Bearer {用户登录返回的 token}`
- 登录接口：管理端 `admin/123456`；用户端需通过微信登录（模拟用途）
- 支付回调接口为微信服务端回调，无需登录态
- 令牌登出后即加入服务端黑名单，同一令牌后续请求返回 401

在 Apifox 中可为环境变量配置 `token` / `authentication`（值含 `Bearer ` 前缀），并在认证中关联，便于批量调试。