# 苍穹外卖 · 管理端(admin)

Vue 3.5 + TypeScript + Element Plus + Pinia + Vue Router + Tailwind CSS v4 + Vite,接口类型由
`docs/openapi.yaml` 生成。**接口真相只有一份**:`docs/openapi.yaml`;前端不手写接口类型。

## 快速开始

```bash
pnpm install
pnpm gen:api    # 生成 src/types/api.d.ts(改了契约必须重跑)
pnpm dev        # http://localhost:5173,已把 /api、/files、/ws 代理到 :8080
```

登录用种子账号:`admin / 123456`(管理员)、`zhangsan / 123456`(员工)。
后端先起来:`cd ../backend && java -jar target/sky-takeout-backend-2.0.0.jar`。

## 目录与分层

```
src/
├── api/            # 每个后端上下文一个文件;http.ts 是唯一的 axios 实例
│   ├── http.ts     # 令牌注入、401 单飞刷新重试、错误归一成 ApiError
│   ├── tokens.ts   # 令牌仓库(localStorage + 内存降级),http 与 store 共用
│   ├── notificationSocket.ts  # WebSocket 协议客户端(心跳/退避/去重)
│   └── <context>.ts
├── stores/         # Pinia:auth(会话与资料)、notification(通知中心)
├── router/         # routes.ts(路由表 + meta.roles)、guard.ts(导航决策,纯函数)
├── layouts/        # AdminLayout:侧边栏 / 头部 / 通知铃铛
├── views/          # 页面(按上下文分子目录)
├── components/     # ImageUpload、charts/*
├── utils/          # 金额(分)、门店时区时间、状态字典、错误提示、通知文案
└── types/          # api.d.ts(生成)+ index.ts(友好别名)
```

几条约束:

- **视图不直接调 axios**,只调 `src/api/**`;
- **金额一律整数分**(字段 `_cents`),只在展示时 `formatCents` 除 100;
- **时间一律按门店时区 `Asia/Shanghai` 渲染**(`utils/datetime.ts`),不跟随浏览器本地时区 ——
  否则 UTC 机器上看到的"今日营业额"会与后端口径错位;
- **状态字典集中在 `utils/dict.ts`**,页面里不散落 `status === 'PENDING_ACCEPTANCE'` 之类的字符串。

## 三层测试

| 层 | 命令 | 环境 | 证明什么 |
|---|---|---|---|
| 单元/组件 | `pnpm test` | jsdom,mock 掉 HTTP | 逻辑与组件本身对:拦截器、守卫、字典、表单交互 |
| 集成 | `pnpm test:integration` | Node,**打真后端** | 契约对:路径/方法/参数名、错误码、令牌刷新、往返一致性 |
| 端到端 | `pnpm test:e2e` | 真浏览器 + 真后端 | 跑起来对:路由守卫、页面渲染、写操作 |

```bash
pnpm test:coverage   # 覆盖率门禁(见下)
pnpm verify          # typecheck + typecheck:test + test:coverage + build
```

**集成测试**(`tests/integration/`)要求后端在 `:8080` 跑着(可用 `SKY_BACKEND` 覆盖)。它会在真库里
造数据:订单/退款是凭证(契约没有删除接口),**故意保留**;分类/菜品/套餐/地址/购物车则登记在
`onCleanup` 里,收尾时按依赖顺序删干净,不碰种子数据。

**端到端测试**(`e2e/`)的浏览器:本仓库开发环境是 WSL,里面没有浏览器,Playwright 自带的浏览器又需要
`libnspr4` / `libnss3` 等系统库(要 root 才能装)。所以 `e2e/global-setup.ts` 直接拉起 **Windows 侧已安装的 Edge**
(`--remote-debugging-port`),测试通过 CDP 连上去 —— 零下载、零 root,而且测的是真实浏览器内核。
换机器时用 `SKY_EDGE_PATH` 指到本机浏览器,或 `pnpm exec playwright install chromium` 后设 `SKY_E2E_BUNDLED=1`。

> 跑 E2E 前建议先手动 `pnpm dev` 起好开发服务器:`reuseExistingServer` 会复用它,比让 Playwright 自己拉更稳。

### 覆盖率门禁为什么只圈"逻辑层"

`vite.config.ts` 的 `coverage.include` 只包含 `api / stores / utils / components / router/guard.ts`,
`src/views/**` 不参与门禁。理由:视图是"取数 + 模板",它的正确性由集成测试与 E2E 证明;
在 jsdom 里用 mock 数据刷出来的视图行覆盖率数字好看,但说明不了它对。视图覆盖率仍可在
`coverage/index.html` 里查看。

## 关键实现说明

- **令牌存放**:后端签发的是 Bearer 令牌(不是 httpOnly Cookie),只能由前端保存,这里选 `localStorage`
  (会话级存储换标签页就要重登,管理端体验太差)。风险用 CSP + 不引入不可信脚本 + access 2 小时
  + refresh 旋转来兜。**这是有意识的取舍,不是疏忽。**
- **401 刷新是单飞的**:首屏并发多个请求同时过期时共用一个刷新 Promise;否则会用同一个 refresh token
  打多次,后端旋转令牌后第二次被判失效,用户被莫名踢下线。
- **错误提示直接用后端的 `message`**:契约里它就是面向用户的中文文案,自己再编一句会把
  "该分类下仍有商品,无法删除"这类有用信息丢掉。
- **通知只是提示信号**(契约 4.4):收到 `ORDER_NEW` 后不做任何业务判断,点通知只跳详情页,状态一律回 REST 查。
- **来单弹窗会自己消失**:来单 10 秒、催单 8 秒,屏幕右侧最多同时挂 3 条(超了关最老的),点一下即关并跳详情。
  漏看的提醒由头部的通知中心(铃铛)兜着 —— 弹窗赖着不走反而会在午高峰把订单列表挡死。
  `e2e/admin.spec.ts` 里有一条用例真下一单来验"出现 → 自动消失"。
- **Element Plus 全量引入 + 图标全量注册**:换来模板里直接写组件名,代价是打包体积(见下)。

## 已知取舍与待办

| 项 | 现状 | 说明 |
|---|---|---|
| 打包体积 | `index` 约 870KB、`Insights` 约 540KB(gzip 后 278KB / 183KB) | Element Plus 全量引入所致;真要压可上 `unplugin-vue-components` 按需引入。ECharts 已按需注册(只引折线/柱状 + 网格/图例/提示框,比全量少约 580KB) |
| 图片占位 | 种子数据的 `imageUrl` 指向不存在的 `/files/**` | 列表里用 `el-image` 的 `error` 插槽兜底显示"无图" |
| 真实微信支付 | 未接入 | 后端只有 mock 渠道(`SKY_PAYMENT_GATEWAY=wechat` 会在启动时报错),退款受理走的也是 mock |
| 视图层单测 | 未覆盖 | 由集成测试 + E2E 覆盖,见上文说明 |

## 相关文档

- `../docs/01-domain.md` —— 领域语义(订单状态机、R1–R10)
- `../docs/03-api.md` —— 接口约定、错误码表、WebSocket 协议
- `../docs/openapi.yaml` —— 接口真相(前端类型的唯一来源)
