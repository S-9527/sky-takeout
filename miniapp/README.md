# 苍穹外卖 · 用户端(miniapp)

uni-app + Vue 3 + TypeScript + Pinia + Tailwind CSS v4,接口类型由 `docs/openapi.yaml` 生成。
一份代码同时出 **H5** 与 **微信小程序**,后续可扩展到其它小程序平台。

## 快速开始

```bash
pnpm install
pnpm gen:api            # 生成 src/types/api.d.ts(改了契约必须重跑)
pnpm dev:h5             # http://localhost:5174,已把 /api、/files 代理到 :8080
pnpm dev:mp-weixin      # 产物在 dist/dev/mp-weixin,用微信开发者工具打开
```

后端先起来:`cd ../backend && java -jar target/sky-takeout-backend-2.0.0.jar`。

**登录**:顾客端所有接口(含目录、门店状态)都要令牌 —— 契约的全局 `security` 是 `bearerAuth`,
只有登录、刷新、支付回调例外。H5 开发环境没有微信,登录页用固定 code 走后端 mock 换 openid;
小程序端用 `uni.login()` 现取真实 code。**平台判断用 `uni.getSystemInfoSync().uniPlatform`**,
不能用"有没有 `wx.login`":H5 里 `window.wx` 是空桩(发行摇树)或 `uni` 自身(开发模式),
而后者的 `uni.login` 是"当前平台不支持"的桩,一调就失败。

**小程序端的接口地址**:小程序没有代理概念,`vite.config.ts` 里的 `server.proxy` 只对 H5 生效。
构建时会把默认根地址经 `define` 注入 `__API_BASE_URL__`:微信端默认指到 `SKY_BACKEND`
(默认 `http://localhost:8080`),所以开发者工具里导入产物即可直接发请求;发布时用
`SKY_BACKEND=https://api.example.com pnpm build:mp-weixin`(或 `VITE_API_BASE_URL`)覆盖。
注意 `wx.request` 只认真实 URL —— 传相对路径会以 `request:fail invalid url` 直接失败,
网络面板里连请求都不会出现,只会看到"无法连接服务器"。

## 目录

```
src/
├── api/
│   ├── runtime.ts   # 跨端适配层:调用点直写 uni.xxx(编译期重写),单测在此注入替身
│   ├── http.ts      # uni.request 封装:令牌注入、401 单飞刷新、错误归一成 ApiError
│   ├── tokens.ts    # uni.setStorageSync 令牌仓库(非 uni 环境退化为内存)
│   ├── auth.ts      # 登录/刷新/登出/资料
│   └── customer.ts  # 目录、购物车、订单、支付、地址簿
├── stores/          # session(会话与资料)、cart(购物车视图缓存)
├── pages/           # menu 点餐、order/* 下单与订单、address 地址簿、mine 我的、login
├── utils/           # 金额(分)、门店时区时间、顾客端状态字典
└── types/           # api.d.ts(生成)+ index.ts(别名)
```

约定与管理端一致:**金额一律整数分**(只在展示时除 100);时间按门店时区 `Asia/Shanghai` 渲染;
状态文案集中在 `utils/dict.ts`,与商家端用同一套中文名(客服沟通时两边对得上)。

购物车**不做本地乐观累加**:行合并、口味归一化、数量上限 99 都是后端规则(R2/R7),
前端每次写完都重拉一次,避免界面显示一个服务端并不认可的数量的。

## 测试

```bash
pnpm test              # 单元:uni.request 桩驱动真实代码路径(令牌/刷新单飞/错误归一/store)
pnpm test:coverage     # 同上 + v8 覆盖率门禁(只圈 api/stores/utils)
pnpm test:integration  # 集成:用小程序自己的 api 层与 store 打真后端(需后端在 :8080)
pnpm verify            # typecheck + test:coverage + build:h5
```

集成测试是这只眼睛最尖的一层,首版就当场抓出 api 层三处契约错误:顾客端目录接口**也要令牌**、
菜品列表是**分页对象**而不是裸数组、`categoryId` 是**必填**参数。这类错误 mock 永远测不出来。

## 工具链约束(为什么会看到"过时"的版本)

uni-app 当前发布线的 `@dcloudio/vite-plugin-uni` 把 `vite` 的 peer 锁在 **5.2.8**:

```bash
npm view @dcloudio/vite-plugin-uni@vue3 peerDependencies   # → { vite: '5.2.8' }
```

因此本包**故意**用 Vite 5,并连带把依赖压到对应版本:

| 选择 | 原因 |
|---|---|
| `vite@5.2.8` | uni 插件的 peer 就是它,换不得 |
| `pinia@2.3.1` | pinia 4 在 Vite 5 + Rollup 下构建失败 |
| `vitest@2.1.9` | vitest 5 依赖 `vite/module-runner`,Vite 5 里没有 |
| 配置不声明 `"type": "module"` | uni 插件是 CJS;而 `@tailwindcss/vite` 是纯 ESM,于是 `vite.config.ts` 用 async 配置 + 动态 import 让两者共存 |

### 升级到 Vite 8 的路径

uni-app 仓库有 `uni-app-vue3-dev-vite8` 分支(把 Vite / `@vitejs/plugin-vue` 升到 8 / 6),
但**尚未发到 npm**:`@dcloudio/vite-plugin-uni` 的所有 dist-tag(含最新的 `vue3`)peer 仍写着 5.2.8。
所以现在要吃得从源码构建 uni-app 并 link,代价与风险都不值当。

等发布线跟上后,升级清单:

1. 确认 `npm view @dcloudio/vite-plugin-uni@<tag> peerDependencies` 里的 vite 变成 8.x;
2. 升 `vite@8` + `vitest@5` + `@vitest/coverage-v8@5` + `pinia@4`,与管理端对齐;
3. `package.json` 加回 `"type": "module"`,`vite.config.ts` 里的动态 import 改成静态导入
   (同时确认 `@dcloudio/vite-plugin-uni` 的默认导出在 ESM 下不再是命名空间);
4. 重跑 `pnpm verify` 与 `pnpm test:integration`,并构建一次 `build:mp-weixin` 确认原子类仍被正确翻译。

## Tailwind v4 + 小程序

- H5 走 `@tailwindcss/vite`,和普通 Vue 应用一样;
- 微信小程序端接 `weapp-tailwindcss`(**v5**,导出名是 `weappTailwindcss`;v4 时代的
  `UnifiedViteWeappTailwindcssPlugin` 已移除),它把原子类翻译成小程序可用的选择器;
- Tailwind 入口**必须是独立 CSS 文件**(`src/assets/tailwind.css`),不能写在 `App.vue` 的 `<style>` 里,
  否则小程序端会出现"类名在、样式没了"。

### 已实跑验证

`pnpm build:mp-weixin` 通过,产物 `dist/build/mp-weixin`(用微信开发者工具导入即可):

- `weapp-tailwindcss` 识别到 Tailwind v4.3.3,并把原子类翻译进了 `assets/tailwind.wxss`(约 10KB);
- 该插件在**生成模式**下会主动移除 `@tailwindcss/vite` 并打印提示
  (`已移除该插件以避免 Tailwind CSS 重复生成`)—— 这是它的正常行为,不是配置错误:
  小程序端由它自己跑 Tailwind 并按需翻译,重复挂 `@tailwindcss/vite` 会把样式生成两遍;
- 注意 `app.wxss` 只是 `@import "./assets/tailwind.wxss"`,真正的原子类在那个文件里。

## 待办

- `build:mp-weixin` 的产物人工过一遍 + 微信开发者工具真机预览(CI 里只能验构建成功);
- H5 端 E2E:可复用管理端那套"Windows Edge + CDP"方案,覆盖"登录 → 点餐 → 下单 → 支付 → 订单详情";
- 门店公告展示、套餐单独入口、订单倒计时到点自动关单后的提示。
