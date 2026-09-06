# Apifox 接口文档

后端运行中通过 Knife4j(Springfox Swagger 2.0) 实时导出：

| 文件 | 分组 | 接口数 |
|---|---|---|
| `sky-admin-api.json` | 管理端接口 (com.sky.controller.admin) | 39 |
| `sky-user-api.json` | 用户端接口 (com.sky.controller.user) | 21 |

重新生成方式（后端须运行在 8080）：

```bash
curl -s "http://localhost:8080/v2/api-docs?group=${GROUP_URL_ENCODED}" -o sky-xxx-api.json
# 管理端分组名：管理端接口
curl -s "http://localhost:8080/v2/api-docs?group=%E7%AE%A1%E7%90%86%E7%AB%AF%E6%8E%A5%E5%8F%A3" -o sky-admin-api.json
# 用户端分组名：用户端接口
curl -s "http://localhost:8080/v2/api-docs?group=%E7%94%A8%E6%88%B7%E7%AB%AF%E6%8E%A5%E5%8F%A3" -o sky-user-api.json
```

## 导入 Apifox

1. Apifox → 「新建导入」→ 选择「Swagger / OpenAPI」
2. 导入 `sky-admin-api.json`，页面提示导入到新的项目后创建项目「苍穹外卖」
3. 再导入 `sky-user-api.json`，选择导入到刚创建的「苍穹外卖」项目（自动合并为同一项目）
4. 也可以直接访问 Knife4j 在线文档：http://localhost:8080/doc.html

## 鉴权说明

- 管理端接口除 `POST /admin/employee/login` 外，请求头需携带 **`token`**：`{员工登录返回的 token}`
- 用户端接口除 `POST /user/user/login`、`GET /user/shop/status` 外，请求头需携带 **`authentication`**：`{用户登录返回的 token}`
- 登录接口：管理端 `admin/123456`；用户端需通过微信登录（模拟用途）

在 Apifox 中可为环境变量配置 `token` / `authentication`，并在认证中关联，便于批量调试。