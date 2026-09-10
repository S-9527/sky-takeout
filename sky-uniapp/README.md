# 苍穹外卖 - 用户端小程序（uni-app）

苍穹外卖用户端，基于 uni-app（Vue 2 版本线）实现，对接同仓 `sky-backend` 的 `/user/**` 接口。一套代码可编译到 H5 与微信小程序。

## 技术栈

- uni-app `2.0.2-5020420260813001`（Vue 2 / Vuex 3）
- @vue/cli-service 5 + webpack 5
- sass（dart-sass）
- Node.js：H5 与小程序端已在 v24.18.0 验证通过。Node 23+ 移除了 `util.isRegExp` 等废弃别名，小程序端构建需要 `scripts/node-polyfill.cjs` 兜底（已通过 `NODE_OPTIONS=--require` 挂到 `dev:mp-weixin` / `build:mp-weixin` 脚本上）；若使用 Node 16/18 可无视该文件。

## 目录结构

```
sky-uniapp/
├── src/                    # 源码（uni-app 约定目录）
│   ├── pages/              # 页面：首页/下单/订单详情/支付/成功/地址/备注/我的/历史订单等
│   │   └── api/api.js      # 全部后端接口封装
│   ├── components/         # 业务组件
│   ├── utils/              # request.js / env.js / 日期与格式化工具
│   ├── store/              # vuex
│   ├── static/             # 静态资源
│   ├── image/              # 页面 CSS 引用的图标（address / phone / time）
│   ├── pages.json          # 路由与全局窗口配置
│   ├── manifest.json       # 应用配置
│   ├── App.vue / main.js   # 入口
│   └── uni_modules/        # uni-ui 等第三方组件（厂商代码，勿改）
├── design/  image/         # README 截图
├── public/index.html       # H5 构建模板
├── scripts/node-polyfill.cjs # Node 23+ 的 util.isRegExp 兜底
├── package.json
├── vue.config.js           # H5 端口与后端代理
├── postcss.config.js       # rpx 编译期转换（关键，见下文）
└── babel.config.js
```

## 快速开始

```bash
npm install
```

### H5 调试（推荐，免微信开发者工具）

```bash
npm run dev:h5      # http://localhost:8081
```

`vue.config.js` 已把 `/user`、`/notify`、`/files` 代理到 `http://localhost:8080`，浏览器访问时**建议用移动端尺寸**（DevTools 设备模拟 390x844），否则页面布局会失真。

### 微信小程序

```bash
npm run dev:mp-weixin   # 产物 dist/dev/mp-weixin
```

用微信开发者工具导入 `dist/dev/mp-weixin`；开发阶段勾选「不校验合法域名」（后端是本地地址）。

生产构建：`npm run build:h5` / `npm run build:mp-weixin`。

## 后端依赖

```bash
cd ../sky-backend
mvn -q package -DskipTests   # 改了 Java 源码必须先重新打包，run.sh 跑的是既有 jar
./run.sh restart             # 端口 8080，日志 /tmp/sky-server.log
```

接口返回统一为 `{ code: 200, data, msg }`，前端 `request.js` 统一处理 `Authorization: Bearer <token>`。

### 关于登录

后端 `/user/**` 除登录/店铺状态外都需要 USER 令牌，而原课程实现要求真实微信 `jscode2session`，本地无法拿到 jsCode，因此后端提供了 mock 登录开关：

- `sky.wechat.login=mock`（默认）：不调微信接口，`openid = "dev-" + code`，H5 端固定以 `h5-dev-user` 登录；
- 切换真实微信：`WECHAT_LOGIN=wechat`（需配置真实 appid/secret）。

> 注意：改完 Java 源码要重新 `mvn package`，`run.sh` 直接跑 `target/` 下的 jar。

## 与原版课程代码的主要差异

原课程接口是「桌台/扫码点餐」模型，本仓后端是「外卖下单」模型，主要改动如下：

| 内容 | 说明 |
| --- | --- |
| 移除开桌流程 | 删除 `src/utils/webscoket.js`、`src/utils/stomp.js`（无任何调用方），api 层不再保留桌台/开桌接口 |
| 接口对齐 | `api.js` 全部改用 `/user/shoppingCart/**`、`/user/order/submit`、`/user/order/payment`、`/user/order/historyOrders`、`/user/order/orderDetail/{id}`、`/user/order/repetition/{id}` 等 |
| 令牌读取 | 后端登录返回 `accessToken`/`refreshToken`，前端原先读 `token` 字段已修正 |
| `getEstimatedDeliveryTime` | 后端无此接口，前端本地按「下单时间 + 30 分钟」估算预计送达时间 |
| 历史订单 | 后端无退款/支付状态查询接口，移除对应 tab |
| 响应码 | 原代码混用 `code === 1`，统一改为 `200` |
| 日期格式 | `estimatedDeliveryTime` 去掉秒（后端 `LocalDateTime` 反序列化格式为 `yyyy-MM-dd HH:mm`） |

### 前端已知保留的降级

- `/user/shop/getMerchantInfo` 后端未实现（仅首页获取店铺电话），失败静默处理，首页地址栏无数据时不显示。
- 图片地址：`getNewImage` 对完整 URL（如 OSS 外链）原样返回，相对路径拼 `baseUrl + /files/`。

## 编译排坑记录

1. **`rpx` 属性值无效 / 整个页面样式崩坏**：uni-app H5 的 `rpx` 必须在编译期转成 `%?N?%` 占位符，运行时再由 `h5-vue-style-loader` 换成 px/rem。该转换由 `@dcloudio/vue-cli-plugin-uni/packages/postcss` 完成，必须写进 `postcss.config.js`，否则所有 `font-size: 50rpx` 之类声明全部失效、图片回退到原始像素导致横向溢出。
2. **`/deep/` 语法**：sass 编译失败，已改为 `::v-deep`。
3. **vue.config.js 的 `/ws` 代理**：会拦截 webpack-dev-server 自己的 HMR WebSocket（路径也是 `/ws`），导致控制台大量 `Invalid frame header`，已移除该代理。
4. **`<icon></icon>`**：非 uni-app 合法组件，会触发 `Missing required prop: "type"` 告警，已改为 `<view class="...">` + CSS 背景图。
5. **导航栏留白**：H5 无胶囊按钮，`getMenuButtonBoundingClientRect` 不可用；首页改为运行时测量 `.navBar` 高度作为 `padding-top`，navbar 内「个人中心」位置改用 `uni.upx2px(100)`。
6. **图片路径**：原项目把应用图标放在仓库根 `image/`（与 README 截图混放），已拆分为 `src/image/`（应用图标）与根 `image/`（README 截图）。
7. **小程序端构建报 `util.isRegExp is not a function`**：Node 23+ 删除了 `util.isRegExp/isString/isObject`，而 `@dcloudio/uni-cli-shared` 依赖的 `postcss-urlrewrite` 仍在用。已加 `scripts/node-polyfill.cjs` 并挂到两个小程序脚本的 `NODE_OPTIONS` 上。
8. **小程序端构建报 `Cannot find module 'regenerator-runtime'`**：uni-app CLI 模板自带但常被漏装的 `regenerator-runtime`、`regenerator-transform`，已补进 devDependencies。
