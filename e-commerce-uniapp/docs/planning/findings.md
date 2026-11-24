# 发现与决策

## 需求

- 小程序端微信登录：`wx.login()` 拿 code → 后端换 token → 后续请求带 `Bearer {token}`。
- 头像闭环：`chooseAvatar` 只给本地临时路径，必须用 `uni.uploadFile` 传到后端，拿回可访问 URL。
- 昵称闭环：小程序 `type="nickname"` 输入框拿到昵称后，调 `POST /api/user/name` 存库。
- 前端所有请求路径必须与后端实际接口一致（这是本轮暴露出的主要问题）。
- **游客模式**：打开小程序先进首页浏览，加购/下单才要求登录；微信登录走静默 `uni.login`，
  不弹授权框，需要用户主动操作的只有头像（`chooseAvatar`）和昵称（`type="nickname"`）。

## 研究发现

### 后端微服务架构（2026-09-30 拆分，本次对接确认）

后端 `e-commerce-boot` 已拆成 Maven 多模块 7 服务（1 网关 + 6 业务 + common），shop-service 已于 2026-10-29 随多租户下线删除。端口规划：

| 服务 | 端口 | 职责 |
|------|------|------|
| gateway | 8080 | API 网关，`/api/**` → `lb://user-service` |
| auth-service | 9001 | 管理员登录（占位） |
| product-service | 9003 | 商品（占位） |
| order-service | 9004 | 订单（占位） |
| aftersale-service | 9005 | 售后（占位） |
| user-service | 9006 | 会员/小程序登录（**已承载业务**） |
| file-service | 9007 | 文件（占位） |

- 小程序用户模块从根 `src/` 迁入 `user-service/`，包名不变 `ecommerce.user`。
- **前端对接约定**（后端 `task_plan.md` 已定）：接口路径 `/api/**` 不变，前端 baseURL 指向网关 8080；
  各微服务端口对前端透明，前端**无需也不应**直连各服务端口。
- gateway 路由前缀为 `spring.cloud.gateway.server.webflux.routes`（Gateway 5.x 新前缀，替代旧的
  `spring.cloud.gateway.routes`），已通过本地 maven 仓库 `spring-cloud-gateway-server-webflux-5.0.3.jar`
  的配置元数据确认合法。

### 后端接口（权威来源：各服务的 springdoc Swagger，见 `e-commerce-boot/docs/swagger-addresses.md`）

> 手写的 `docs/openapi.yaml` **已废弃并移除**，接口文档改由 springdoc 按代码注解实时生成：
> 统一入口 `http://localhost:8080/swagger-ui.html`（网关聚合，可下拉切服务）。接口有疑问以**源码**为准。

| 接口 | 方法 | 入参 | 返回 |
|------|------|------|------|
| `/api/wxlogin` | POST | `{ code }` | `Result<WxLoginVO>`：`token` / `id` / `avatar`（**无 `name`**） |
| `/api/user/profile` | GET | 无（id 从 token 取） | `Result<UserProfileVO>`：`id` / `name` / `username` / `avatar` / `phone` / `sex` / `createTime`（**无 `idNumber`**） |
| `/api/user/avatar` | POST | `multipart/form-data` 字段 `file` | `Result<String>`：完整头像 URL |
| `/api/user/name` | POST | `application/json`：`{ name }` | `Result<String>`：去除首尾空格后的昵称 |
| `/api/cart` | POST | `application/json`：`{ productId, quantity, insuranceDetail, insuranceFee }` | `Result<Void>`：无 data（**需登录**，已在购物车则累加数量） |
| `/api/cart` | GET | 无 | `Result<List<CartVO>>`：含 `insuranceDetail` / `insuranceFee` / `stock` / `subTotal` 字段 |

- 统一响应体 `Result<T> { success, data, message, code }`。
- **失败时返回真实 HTTP 状态码**（400/401/403/413/500），不是恒 200，响应体仍是 `Result`。
- 两个 `avatar` 字段都是**完整 URL**（`UserController.fullUrl` 按当前请求 `scheme://Host` 拼），前端不要再拼域名。

### 后端实现（`e-commerce-boot/user-service`，包 `ecommerce.user`）

- 目录：`ecommerce.user.controller.UserController`（`@RequestMapping("/api/user")`）、
  `WxLoginController`（`@RequestMapping("/api")`）、`UserServiceImpl`、`UserMapper`、`User`、`UserProfileVO`。
- **没有 `member` 包**，`Member*` 相关命名全是早期推演的产物。
- 鉴权：`JwtInterceptor` 拦 `/api/**`，豁免清单 `WebMvcConfig.EXCLUDE_AUTH_PATHS`
  = `/api/wxlogin`、`/api/login`、`/api/logout` + C 端只读商品路径（本轮新增，见下节）。
- token：`JwtUtil.generateUserToken(userId)`，有效期 7 天（`jwt.expire: 604800`）。
- 当前用户 id：`UserContext.getUserId()`，由拦截器解析 token 后写入，**不由前端传参**。
- 上传落盘：`upload/avatar/`，文件名 `{userId}_{时间戳}.{后缀}`。
- 类型白名单：`image/png`→png、`image/jpeg`→jpg、`image/gif`→gif、`image/webp`→webp。
  不用 `startsWith("image/")` 是因为会放行 `image/svg+xml`，配合 `/upload/**` 静态直出即 XSS。
- 大小限制：`application.yml` 的 `spring.servlet.multipart` 单文件 2MB、整请求 3MB，超限走
  `GlobalExceptionHandler` 转 413。
- 静态映射：`WebMvcConfig` 把 `/upload/**` → `file:upload/`。
- 上传成功后会删旧头像文件（只处理 `/upload/avatar/` 开头的本地相对路径）。
- `UserMapper`：`findByOpenid` / `insertUser` / `findById` / `updateAvatar`；
  XML 里表名用反引号包 `` `user` ``（`user` 是 MySQL 关键字）。
- **后端 `.gitignore` 里没有 `upload/`**，头像文件会被 git 跟踪。
- ~~**后端没有任何更新昵称的代码**~~ **已过时**：0928 后端补了昵称链路，见下节。

### 后端昵称链路（本轮新增，工作区未提交）

- `ecommerce.user.dto.UpdateNameDTO`（新文件）：单个 `name` 字段，`@Schema` 标注 1~32 字符。
- `UserController.updateName`：`@PostMapping("/name")` → `@RequestBody UpdateNameDTO`，
  用户 id 取自 `UserContext.getUserId()`（**不由前端传**），返回 `Result<String>`。
- `UserServiceImpl.updateName` 四步校验：
  `null` / `isBlank` → 400「昵称不能为空」；`trim()` 后 `length() > 32` → 400「昵称不能超过 32 个字符」；
  `findById` 为 null → 401「用户不存在」；通过后写库并**返回 trim 后的昵称**。
- 上下限与库表对齐：`name varchar(32)`，所以长度上限是 32 个字符而不是字节。
- `UserMapper.updateName` + `UserMapper.xml`：`update \`user\` set name = #{name}, update_time = now() where id = #{id}`。
- `openapi.yaml`：新增 `/api/user/name`（401/403/400/500 均列出），
  `ResultString.data` 的描述改成「头像完整访问 URL 或去除首尾空格后的昵称」。

### 后端游客模式改动（本轮检查，工作区未提交）

按「打开小程序先进首页浏览，加购/下单才要登录」的需求，后端在 common 模块扩了免登录清单：

- `WebMvcConfig.java:28-34` `EXCLUDE_AUTH_PATHS` 新增 C 端只读商品路径：
  `/api/products`、`/api/products/**`、`/api/categories`、`/api/categories/**`、
  `/api/tags`、`/api/tags/**`（裸路径=列表，`/**`=详情等子路径）；同时补了 `/api/logout`。
- 清单是 Ant 风格匹配，`OpenApiConfig.isExcludedFromAuth`（`:130-134`）引用同一份
  `EXCLUDE_AUTH_PATHS`，两处不会各自漂移。
- 后台商品管理走 `/api/admin/**`（`JwtInterceptor.ADMIN_PATH`），不在豁免内。

**商品链路（后端本轮已完成）**：

| 环节 | 状态 | 位置 |
|------|------|------|
| 免登录白名单 | ✅ | `WebMvcConfig.java:28-34` |
| 商品接口实现 | ✅ | `ProductController.java`：`/api/products`、`/api/products/{id}`、`/api/categories`、`/api/tags`，返回 `Result<List<VO>>` |
| 网关路由 | ✅ | `gateway/application.yml` 新增 product-service，放在 `Path=/api/**` 通配**之前**（具体规则优先匹配） |
| 接口文档 | ✅ | `docs/openapi.yaml` 补了上述 4 个商品接口 |

**401 / 403 语义**（`JwtInterceptor.java`，前端状态码处理即按此）：

- 无 token / token 无效或过期 → **401**（`preHandle:49-60`）
- token 类型不匹配（如顾客 token 调 `/api/admin/**`）→ **403**（`:63-68`）
- 失败响应体仍是 `Result{success,data,message}`，只是 HTTP 状态码为 401/403（`:20-21` 注释）

### 前端实现（`e-commerce-uniapp`）

- `utils/token.js`：`getToken` / `setToken` / `clearToken`，key 为 `'token'`。
- `utils/server-api.js`：`BASE_URL = 'http://127.0.0.1:8080'`，
  导出 `request` / `wxLogin` / `getProfile` / `updateName` / `uploadAvatar`。
- `request()` 已补 HTTP 状态码处理（本轮）：401/403 → `clearToken()` 并抛
  `{ statusCode, message }`；其余非 2xx → 抛 `{ statusCode, message: '请求失败（{code}）' }`；
  2xx 才看 body 的 `success`。此前只判 body，状态码全部丢弃。
- `utils/product-api.js`（本轮新建）：商品/分类浏览接口 `getProducts` / `getProductDetail` /
  `getCategories`，内部复用 `server-api.js` 的 `request`。单开一个文件，跟用户模块的接口分开。
- `utils/auth.js`（本轮新建）：`ensureLogin()` —— 需要登录的操作前调用，未登录则
  `uni.login` 静默拿 code → `wxLogin` 换 token → 写 store。全程无授权弹窗。
- `pages/index/index.vue`（本轮重写为商品橱窗）：顶部 `swiper` 轮播图 + 商品列表。
  - 轮播图**后端没有接口**，用三个渐变色块占位（不依赖图片资源，`static/` 里也没图）。
  - 商品列表 `onLoad` 调 `getProducts()`（免登录）。
  - 「加入购物车」按钮 `@click` 里先 `await ensureLogin()` 再调加购接口 ——
    ~~**加购接口（order-service）尚未实现**，目前只弹 toast 占位。~~
    **已过时（2026-10-10）**：order-service 的 `CartController` / `OrderController` 已落地，加购链路已贯通，
    见下文「登录弹窗与加购链路」；首页目前也没有加购入口（`pages/index/component/ProductPopup.vue` 是纯预览弹窗）。
- `pages/my/my.vue`（本轮重写）：承接原 index.vue 的登录 UI（首页不再有登录入口）。
  未登录显示「微信登录」按钮（静默登录）；已登录显示头像 + 昵称 + 退出登录，
  点头像触发 `chooseAvatar` 换头像（微信要求必须用户点击）→ `uploadAvatar` 上传换 URL。
- `App.vue` 的 `globalData.userInfo` 是 `null`，全项目未使用。
- 底部导航（本轮新增）：`pages.json` 加 `tabBar`（首页/分类/购物车/我的），
  新建 `pages/category/category.vue`、`pages/cart/cart.vue` 两个占位页。
  `static/` 下没有图标文件，先出纯文字版；补图后给每项加 `iconPath` / `selectedIconPath`。
  tabBar 页面之间跳转必须用 `uni.switchTab`，`navigateTo` 会失败。
- `store/user.js`（本轮补齐）：Pinia options store —— state `{ token, userInfo }`、
  getter `isLogin`、action `setToken` / `setUserInfo` / `setAvatar` / `logout`，
  与 `index.vue` 实际用到的 6 个成员一一对应。两个坑：
  `isLogin` 基于 state 而非直接读 Storage（Pinia getter 不依赖 state 会永久缓存，登出后不刷新）；
  `userInfo` 初始 `{}` 而非 `null`（模板里读 `userInfo.avatar` 会报错）。

### 顶部导航栏机型适配（2026-10-01，`pages/index/component/TopBar.vue`）

- 自定义导航栏（`navigationStyle: custom`）需自行避让状态栏与微信胶囊按钮，尺寸一律运行时现取：
  `statusBarHeight`、`getMenuButtonBoundingClientRect()`、`windowWidth`、`env(safe-area-inset-bottom)`。
- **胶囊反推内容区高度**：`navContentHeight = (menuRect.top - statusBarHeight) * 2 + menuRect.height`
  （胶囊在内容区垂直居中，上留白×2 + 胶囊高）。华为部分机型 `menuRect.top` 异常偏小 →
  反推高度 < 搜索框 30px → 上下裁切。修复：`Math.max(44, ...)` 兜底。
- **右侧避让胶囊**：`rightSafeArea = windowWidth - menuRect.left` 让搜索框右边缘精确等于胶囊左边缘
  （间距 0）。华为胶囊偏右时贴边明显。修复：`+ 8` px 固定间距。
- **兜底分两层**：默认值兜底（`data` 里 `navContentHeight: 44`、`rightSafeArea: 0`，非微信/无胶囊时用）
  + 下限兜底（`Math.max(44, ...)` 抗异常偏小）。
- `safeAreaInsets` 未引入：顶部=statusBarHeight、底部=env() 已覆盖，左右竖屏为 0。

### 轮播图组件（2026-10-02，`components/CustomSwiper.vue`）

- 把首页轮播图抽成全局组件 `CustomSwiper`，`main.js` 全局注册后各页面直接写 `<CustomSwiper />`
  （`pages/index/index.vue:7` 已用）。
- 组件 props：`list`（图片路径数组，默认 4 张 `/static/image/swiper/1~4.webp`）、`height`、
  `autoplay`、`interval`、`duration`、`circular`、`indicatorDots`、`indicatorColor`、
  `indicatorActiveColor`；事件 `change`（抛出当前下标）、`click`（抛出点击下标）。
- **Vue3 全局注册踩坑**：正确 API 是 `app.component(name, comp)`，**不是** `app._component`。
  `_component` 是编译产物 `app.js` 里出现过的内部命名，误抄会导致运行期
  `app._component is not a function`（`main.js:37`）。
- **组件改名后编译缓存不会自动清**：`Swiper.vue` → `CustomSwiper.vue` 后，
  `unpackage/dist/dev/.sourcemap/mp-weixin/components/` 残留旧 `Swiper.js.map`，增量编译仍去找
  `Swiper.wxml` → `ENOENT`。需关微信开发者工具（否则 `Device or resource busy`）删 `unpackage/dist` 重编译。

### 前台分类组件（2026-10-02，`components/CategoryPanel.vue`）

- 首页「金刚区」分类面板：6 个分类（手机/笔记本/平板/相机/手表/耳机），3 列 × 2 行宫格。
- **图标方案演进**：先试 iconfont 字体（`static/category/iconfont.css`），后因字体图标单色、无
  "原有颜色"（颜色全靠 CSS `color`），且与全局 `static/icon/iconfont.css` 同名 `font-family` 冲突，
  最终改用 png（`static/image/category/*.png` 6 张），`<image mode="aspectFit">` + `border-radius: 30rpx` 圆角。
- **注册方式**：`pages/index/index.vue` 局部注册（`import` + `components`），未进 `main.js` 全局注册
  —— 组件只在首页用，且当时 `main.js` 状态不稳。全局/局部/easycom 的选择标准是「复用范围」。
- **交互**：点击某项 `$emit('select', item)` 把整项抛给页面，`handleCategorySelect` 用 `switchTab` 跳分类页。
- 数据源已后端化（2026-10-06）：`mounted` 调 `getCategories()`，只取 `parentId == null` 的一级分类，
  名称/图片用后端 `CategoryVO` 的 `name`/`image`；`image` 是 `/image/category/xxx.png` 相对路径，补 `BASE_URL`。
  分类接口单独拆到 `api/categories.js`（原混在 `api/product.js`；分类与商品是两个资源，按资源拆开）。
- 相关坑：`@import` 相对路径从 `components/` 到根是 `../` 不是 `../../`；微信开发者工具
  「过滤无依赖文件」会误删 uni-app 懒加载组件（报 `Component is not found`）。

### 商品预览弹窗（2026-10-03，`pages/index/component/HotPanel.vue` + `ProductPopup.vue`）

- 首页「热门推荐」面板 `HotPanel`：4 色块，每块左右各一个商品位（写死商品 id 的占位数据），
  点图只 `$emit('select', id)`，不拉数据 —— 纯展示组件，详情与弹窗都留给页面。
- 弹窗组件 `ProductPopup`：无状态展示组件（props `show`/`product` 进、`$emit('close')` 出），
  商品图 / 名 / 价 / 描述 / 关闭按钮；数据与显隐状态都归 `pages/index/index.vue` 的 data。
- **弹窗放页面层不放 HotPanel 内的理由**：详情接口 + show 开关归页面；弹窗是整页遮罩，
  复用方不止热门推荐（分类页/搜索页以后也能用）；挂进 flex 列布局的列表组件里语义和层叠都不对。
- **商品图片链路（本轮踩坑）**：后端 `products.image` 存 `/image/upload/N.webp` 相对路径，
  由网关路由到 product-service，`common/WebMvcConfig` 把 `/image/**` 映射到 `file:upload/image/`。
  小程序里 `<image>` 的 src 以 `/` 开头会被解析成**包内路径**，不是服务器地址 → 空白。
  前端需补 `BASE_URL`（`http://127.0.0.1:8080`）；`/static/image/upload/N.webp` 是前端本地占位图，另一回事。
- **弹窗组件实现演进**：先自写（v-if + fixed 遮罩 + flex 居中），后按用户要求改用 `uni-popup`。
  uni-ui 的 `uni-popup` 走 pages.json 已配的 easycom 规则（`^uni-(.*)`）免注册，
  遮罩 / 居中 / 点遮罩关闭（`is-mask-click`）/ H5 滚动穿透都内置。
- **uni-popup 是命令式 API**：`ref.open()` / `ref.close()`，而页面是 data 驱动，需 `watch: show`
  桥接，并用内部 `opened` 标记对齐状态，避免 `change` 事件和 watch 来回触发死循环。
- **z-index 冲突**：uni-popup 自带 `position: fixed; z-index: 99`，自定义 TabBar 是 999，会被压住。
  通过 `<uni-popup class="product-popup">`（class 经 uni-app `u-c` 透传到根节点）+ 非 scoped 样式
  `.product-popup.uni-popup { z-index: 1000; }`（双类名提高优先级，不依赖加载顺序）解决。

### AI 导购 Agent（后端 ai-agent-service + 前端悬浮导购）

**后端（2026-10-03 已建骨架并实测）**：

- 新增微服务 `ai-agent-service`（端口 9008），定位「完整 Agent 而非 LLM 聊天壳」，保留
  Graph 工作流（意图路由）/ RAG / MCP / Function Calling 四大能力。
- 技术选型：Spring AI 2.0.1（官方，非 Alibaba）+ 硅基流动 DeepSeek（OpenAI 兼容）+
  SimpleVectorStore 起步 + OpenFeign 调 product-service + MCP（spring-ai-starter-mcp-server-webmvc）。
- 已实测通过：启动 + Nacos 注册、对话、Tool Calling、SSE 流式、MCP；
  真实推荐待 product-service 启动（当前返回 DeepSeek 幻觉商品）、下单待 order-service。

**前端对接契约（免登录，游客可用）**：

- 网关已加路由 `Path=/api/agent,/api/agent/**` → `lb://ai-agent-service`（放在 `/api/**` 通配之前）；
  common `WebMvcConfig.EXCLUDE_AUTH_PATHS` 已放行 `/api/agent`、`/api/agent/**`。
- `POST /api/agent/chat`：body `{message}`，返回 `Result<ChatReplyVO>`（`ChatReplyVO{reply}`）。
- `POST /api/agent/chat/stream`：`produces=text/event-stream`，body `{message}`，返回 `Flux<String>` 打字机。
- 意图路由（`IntentRouter` 规则关键词）：RECOMMEND（推荐/查询，走 `ProductTool` 查 product-service）/
  ORDER（下单，走 `OrderTool` HITL）/ QA（售后/规格，走 RAG）/ CHAT（闲聊）。

**前端要做**：

- 悬浮式导购：悬浮球 + 点击展开聊天抽屉/半屏面板，消息列表 + 输入框 + 发送。
- 流式渲染打字机（SSE）；若小程序端 SSE 支持受限，先降级用非流式 `/chat`。
- 游客可直接对话（免登录）；涉及下单等操作再走现有 `utils/auth.js` 的 `ensureLogin`。

⚠️ **小程序端 SSE 坑（待真机验证）**：

- 微信 `wx.request` 默认不支持流式响应；需 `enableChunked: true` + `onChunkReceived` 回调
  （基础库 2.20.1+），**开发者工具不支持，需真机**。uni-app `uni.request` 透传该能力。
- 若真机仍不可用，回退方案：非流式 `/api/agent/chat`，整段回复一次性渲染。

### 首页「猜你喜欢」模块（Python 推荐服务，2026-10-04）

> 小程序首页新增「猜你喜欢」区块，依据**订单表下单记录**（`orders` + `order_items` 的
> `user_id → product_id` 关系）推荐商品。推荐算法**用 Python 写**（独立服务），Java 只做桥接；
> Spring AI 是给聊天机器人（ai-agent-service）用的，与本模块无关。

**后端方案（已定案，详见后端 findings「推荐服务（Python）方案」）**：

- Python 推荐项目：`D:\study\my-workspace\wechat-workspace\ecommerce-python`（独立 Python 项目，
  后端 findings 记为 `recommend-service/`，FastAPI 端口 9009）。
- 算法：Item-Item 商品共现（「买了又买」）+ 销量热门兜底；协同过滤暂不做（用户×商品矩阵太稀疏，仅 3 个用户有下单记录）。
- 数据源：先读原 Node 的 SQLite `orders/order_items`（有效下单 22 条、在售商品 30 个、3 个用户），order-service 落地后切 MySQL（`datasource` 抽象层）。
- 冷启动兜底：未登录 / 未下单 → 按销量降序推荐全量在售商品（销量 = `sum(quantity)`，无销量商品按 id 排后补位）。

**接口契约（前端对接依据）**：

| 接口 | 方法 | 入参 | 返回 |
|------|------|------|------|
| `/api/products/recommend` | GET | `?limit=10`（可选） | `Result<List<ProductVO>>`，字段与商品列表一致（`id`/`name`/`image`/`price`/`description` 等） |

- 免登录（复用 `/api/products/**` 白名单，网关路由零改动）；该接口已端到端跑通（Python → Java 桥接 → 网关）。
- 登录态：后端按 `UserContext.getUserId()` 调 Python `/recommend?user_id`；未登录 → 请求 Python
  不带 `user_id` → 走热门兜底。前端**无需**自己判断登录态，统一带 token 请求即可。
- 底层 Python 接口 `GET /recommend?user_id=1&limit=10` → `{"product_ids":[...],"scores":[...]}`，
  前端不直接接触，只经 Java 桥接接口拿 `ProductVO` 列表。

**前端要做**（已完成，2026-10-05）：

- 接口封装：`api/product.js` 新增 `getRecommend(limit)` → `GET /api/products/recommend`。
- 首页新增「猜你喜欢」区块组件（复用 `ProductVO` 卡片：图/名/价），点商品图复用
  `handleProductSelect` 弹 `ProductPopup` 预览。
- `pages/index/index.vue` 在热门推荐（`HotPanel`）之后插入该区块。
- 商品图相对路径 `/image/upload/N.webp` 仍要补 `BASE_URL`（与 `ProductPopup.cover` 同一处理）。

**实现现状（2026-10-05）**：

- 组件 `components/Guess.vue` 已重建（原文件模板是坏的：多根节点、`<scroll scroll-y>` 拼写错误），
  现为「标题 + 两列商品卡片网格（图/名/价）」，`mounted` 调 `getRecommend(10)` 拉后端商品。
- 商品名称/价格/图片全部来自后端 `ProductVO`（id/name/image/price）；图片经 `imgUrl(item)` 补 `BASE_URL`，
  无图时回退本地 `/static/image/upload/{id}.webp`。
- 标题样式：左右渐变装饰线 + 两侧小圆点，纯 CSS 实现，未引入图片资源（项目无现成 icon）。

**与「热门推荐（HotPanel）」的区别**：

- `HotPanel` 是写死商品 id 的「栏目/精选」占位面板（阶段 12），与个性化无关；
- 「猜你喜欢」是**按用户下单记录**的个性化推荐，数据来自 Python 推荐服务 + Java 桥接接口。

### 商品参数与二级分类（2026-10-29）

- 后端 `ProductVO` 新增 `specs`：有序数组 `[{name,value}]`（非 SKU），详情页渲染「商品参数」表；空数组不显示。
- 后端 `/api/products?categoryId=` 已支持一级/二级任意层级；分类页右侧用 chips 展示二级分类，传二级 id 过滤，「全部」传一级 id。
- 分类页缓存 key 改为实际筛选 id（一级或二级），避免切换二级时串数据。
- 首页分类面板/热门推荐无需改代码，自动显示后端新的 8 个一级分类与 300 个商品。

### 首页下拉刷新（2026-10-06）

- 用 `scroll-view` 的 `refresher-enabled`，不用页面原生 `onPullDownRefresh`：首页是自定义 fixed 导航栏
  （`TopBar.vue` 的 `.navbar-fixed { position: fixed; z-index: 99 }`），原生下拉转圈固定显示在页面顶部会被
  导航栏盖住；`scroll-view` 转圈在滚动区顶部（导航栏下方），不被遮挡，且可自定义 `refresher-default-style` /
  `refresher-background`。
- **「看不到动画」根因**：`onRefresh` 重拉的「分类 + 猜你喜欢」都走本地网关 `127.0.0.1:8080` 秒回，
  `refreshing` 在 `true` 后几十毫秒就被置回 `false`，转圈没渲染出来就复位。修复：`Promise.all` 内联
  `setTimeout` 延时，保证动画至少展示一段时间。
- **现状（2026-10-29 更新）**：热门推荐优先取「推荐分类」商品：`api/product.js` 的 `getHotProducts(limit, excludeIds)`
  调后端 `GET /api/products/hot`（`categories.recommend=1` 或其父分类推荐的商品优先，不足 `limit` 用随机上架商品补足，
  `excludeIds` 排除当前列表）。首屏 `loadHot()` 与下拉刷新共用该接口，刷新**只替换「热门推荐」`HotPanel` 的 8 个商品位**，
  「猜你喜欢」「轮播图」不动。
- **为什么不能直接重拉 `/api/products/recommend`**：推荐结果有 60 秒 Redis 缓存（按 `用户 + limit`），游客还走
  「销量前 10」兜底，60 秒内刷新拿到的必然是同一批；随机接口特意绕开该缓存。
- **HotPanel 商品位**：栏目名（爆款推荐/人气热卖/…）写死，商品默认 8 个 id 写死；页面传 `:list` 时按顺序替换商品，
  不足回退默认 id。`HotPanel.currentIds()` 给 `onRefresh` 排除当前批次用。
- **后端待提供（等 Python 推荐服务写好再继续）**：
  - 轮播图 Banner 接口 `GET /api/banners` → `Result<List<BannerVO{id,image,link,sort}>>`；
  - 热门推荐接口 `GET /api/products/hot` → `Result<List<HotSlotVO{title,product}>>`（或商品加 `is_hot` 标记、
    前端写死栏目名映射）；两接口都进免登录白名单，图片沿用「相对路径 + 前端补 `BASE_URL`」。

### 分类页右侧商品（2026-10-06，`pages/category/category.vue`）

- 左侧分类后端化：`mounted` 调 `getCategories()`，按 `parentId` 拆一/二级；一级分类记下 `subIds`（二级分类 id）。右侧由「二级分类宫格」改为「商品网格」后，二级分类名不再展示，`subs` 字段成死代码已删。
- **商品与分类的关系**：商品挂在**二级分类**上（`products.category_id` → 二级分类 id），一级分类下无直接商品；后端种子数据还有 9 个 `category_id = NULL` 的商品（iPhone 15 Pro、华为 Pura 70 等），不属于任何分类，分类页不显示（首页热门/猜你喜欢仍可见）。
- **商品过滤方式**：后端 `/api/products` 返回**全量上架商品**、无按分类过滤参数，前端拉全量后按「当前一级分类 id + 其二级分类 id 集合」过滤出对应商品（`currentProducts` 计算属性）。
- **跳转联动**：`switchTab` 跳 tab 页不能带 query，用 `uni.setStorageSync('selectedCategoryId', id)` 传选中分类；分类页 `applySelectedCategory()` 读 Storage 定位到对应下标后 `removeStorageSync` 清掉（避免下次直接进分类页还停在旧分类）。
- **tabBar 页二次进入**：分类页被 tabBar 缓存，`mounted` 只在首次触发，二次切回靠 `onShow` 定位；首次进入 `onShow` 先于分类数据返回，`findIndex` 找不到就**不清** Storage，等 `fetchCategories` 完成后再定位。
- **卡片等高**：商品名 1/2 行导致卡片总高不一致、下一行错位；卡片固定 `height` + 名称区固定两行高度、价格行贴底解决。
- **购物车图标**：复用全局 iconfont `icon-gouwuche1`（`App.vue` 已全局引入 `static/icon/iconfont.css`），橙色圆底做成加购按钮样子；目前纯展示，未接加购逻辑（加购需登录 + order-service 接口，未开发；加购链路见下节）。

### 登录弹窗与加购链路（2026-10-10）

**后端现状（order-service，工作区未提交）**：

- `CartController`（`@RequestMapping("/api/cart")`）：`GET` 列表、`POST` 加购、`PUT /{productId}` 改数量、
  `DELETE /{productId}` 移除、`DELETE` 清空（可选 body `productIds` 批量）。**需登录**：
  `/api/cart/**` 不在 `EXCLUDE_AUTH_PATHS` 内，`JwtInterceptor` 自动要求 user 令牌，当前用户取 `UserContext.getUserId()`。
- `AddCartDTO` **只有 `productId` / `quantity`**；`CartServiceImpl.addItem` 硬编码
  `.insuranceDetail("[]")` / `.insuranceFee(BigDecimal.ZERO)`（源码注释也写着「小程序暂未接入，字段保留供后续扩展」）。
  而 `CartVO` / `Cart` 实体是有 `insuranceDetail` / `insuranceFee` 的（读列表时会 `parseInsurance`）。
  → **前端选了增值保障也存不进去**，这是当前的功能缺口，不是前端 bug。

**前端实现**：

- `api/cart.js`（新建）：`addToCart(productId, quantity)` → `POST /api/cart`。
- `components/LoginPopup.vue`（新建，全局注册）：**打开即静默登录**。
  - 触发点是「未登录时点加入购物车」，不需要用户找登录入口。
  - `watch.show` → `popup.open()` + `startLogin()`：`ensureLogin()` 拿到 token，**再总是补一次
    `getProfile()`** 写回 store。原因：`ensureLogin()` 已有 token 时直接 return，而重开小程序后
    `store.userInfo` 是 `{}`，不补这一次头像永远是空的（这是本轮修掉的一个真 bug）。
  - 头像：能自动带出**后端已存的**头像；要换必须用户点 `<button open-type="chooseAvatar">` ——
    微信不允许代码触发头像选择框，`getUserProfile` / `getUserInfo` 也已废弃（只返回灰色匿名头像）。
  - 不收集昵称（用户明确要求）。
  - 非 scoped 样式抬到 `z-index: 1100`，盖住商品弹窗（1000）和 tabBar（999）。
- `pages/category/component/ProductPopup.vue`：加购逻辑收进组件自身，不再由页面代劳。
  - `handleAdd()`：未登录 → `$emit('needLogin')`（**商品弹窗不关**，数量/增值保障选择留在原地）；
    已登录 → `addToCart(product.id, quantity)`；`adding` 期间按钮置灰 + 文案「加入中…」防连点。
  - 成功 → toast「已加入购物车」→ 播飞入动画 → 动画跑完 `$emit('close')` 收尾。
- **飞入购物车动画**：`uni.createSelectorQuery()` 量 `.cover`（弹窗商品图）与 `.icon-gouwuche`
  （tabBar 购物车，注意卡片上的图标是 `icon-gouwuche1`，类名不同不会误选）的中心点算位移；
  抛物线的实现是**两层 transform 合成**——外层 `.fly-wrap` 横向 `linear` 匀速，内层 `.fly-img`
  纵向用 `cubic-bezier(0.5, -0.6, 1, 1)`（y1 取负 → 先上后下）+ `scale(0.2)`。
  飞入层必须放在 `uni-popup` **外面**（弹窗收起自带 transform，放里面会被带着动），`z-index: 1200`。
- `category.vue`：`@add` → `@needLogin`，只负责 `showLoginPopup = true`；加购相关的 `handleAddCart` /
  `doAddCart` 及 `addToCart`、`useUserStore`、`store` 一并删掉（判断已在组件内）。
- `pages/index/index.vue`：删掉被注释的登录入口及其方法链、以及已登录的用户信息展示块；import 收窄到
  `getProductDetail`。首页没有加购入口，故不渲染 `LoginPopup`。

**增值保障（对齐后台 `Mall.vue`）**：`ProductPopup` 补了「服务保障」四项与「增值保障」三张卡片。
价格为售价的 1% / 2% / 电池无忧 ¥20（与后台计费口径一致），选中态为橙边框 + 浅橙底。
后端 `AddCartDTO` 已补 `insuranceDetail` / `insuranceFee` 字段（2026-10-11），前端 `addToCart` 已随请求带上。
「电池无忧」按类别过滤：耳机（401/402）、音箱（403）不显示（2026-10-14）。

### C 端免登录（2026-10-10）

用户拍板：**小程序 C 端前后端免登录，电商后台管理系统保持登录**。

- 前端：`App.vue` onLaunch 启动即 `ensureLogin()`（`uni.login` 换 openid，无授权框）；`my.vue` 删登录/退出按钮、去 isLogin 分支，`onShow` 补拉 `getProfile()`。
- 后端：C 端商品浏览本就在 `EXCLUDE_AUTH_PATHS` 免登录；加购/下单靠静默登录自动带的 token 定位 userId，接口无需改。
- 手机号登录（`getPhoneNumber` → `POST /api/user/phone`）后端已实现、前端未接入，保留备用。
- 头像历史问题：`upload/avatar/` 空 + `user.avatar` 恒 NULL + 账号 id 12→13 重建，即头像从未成功落库，非前端 bug。

### 下单支付链路（2026-10-14）

后端 order-service 的下单/支付接口已就绪（`POST /api/orders`、`PUT /api/orders/{id}/pay`），
小程序侧把「去结算 → 下单 → 支付」贯通。

**下单确认页 `pages/order/confirm.vue`**：

- 从 `store/cart.js` 的 `checkoutItems` 读待结算商品（购物车「去结算」或商品弹窗「立即购买」写入）。
- 收货地址：`listAddress` 取默认地址展示，点地址卡弹 `AddressPopup` 切换/新增。
- 底部按钮「提交订单」（2026-10-15 前叫「我已支付」）：下单 + 立即支付一步完成 ——
  `createOrder` → `payOrder(orderId, payType)` → `clearCart` → 跳回。
  **仍未接微信支付**；`payType` 现在由页面选中的支付方式给出（见下节），后端会把它写进 `orders.pay_type`。
- 清购物车失败不影响支付成功提示（静默忽略，购物车页 `onShow` 会重新拉列表）。

**幂等键（对齐后端下单幂等）**：

- `api/order.js` 的 `genIdempotencyKey()`：小程序基础库新则 `crypto.randomUUID()`，否则手写 v4 兜底。
- 确认页 `onLoad` 生成一次、本页内失败重试复用同一个 key（后端对相同 key 只真正下单一次，防弱网重试重复下单）。

**三条下单入口（共用 `checkoutItems` + 确认页）**：

| 入口 | 来源 | 商品 |
|---|---|---|
| 首页/分类页商品弹窗「立即购买」 | `ProductPopup.vue` handleBuy | 单商品（含数量/增值保障） |
| 购物车「去结算」 | `cart.vue` handleSettle | 选中的多个商品 |
| 购物车「猜你喜欢」点商品 | `cart.vue` handleGuessSelect → ProductPopup | 单商品 |

- 「立即购买」/「猜你喜欢」都先 `getProductDetail(id)` 拉详情 → 打开 `ProductPopup`，再走弹窗里的加购/立即购买。

**增值保障按类别过滤（2026-10-14）**：

- `ProductPopup.vue` 新增 `showBattery` computed：`![401, 402, 403].includes(Number(product.categoryId))`。
- 「电池无忧」卡片 `v-if="showBattery"`，`insuranceDetail`/`insuranceFee` 也加 `showBattery &&` 兜底。
- 即耳机（401 头戴式、402 无线）与音箱（403）不显示「电池无忧」，其余类别（手机/笔记本/平板/手表/相机）保留。
- 依赖 `ProductVO.categoryId`（叶子分类 id）；后续新增耳机/音箱子类需同步加进列表。

### 确认订单页支付方式（2026-10-15）

用户给了别人的示例代码（`<script setup>` + `ref` + 写死红色的单选圈），要求「加个支付方式的选择，
用微信支付」，并强调**不要照抄**。按本项目风格重写后落地如下。

**落地形态（`pages/order/confirm.vue`）**：

- 金额汇总卡之后新增一张 `.card`：`v-for` 遍历 `payList`，左侧 `uni-icons`（`weixin` + `#07C160`）+ 名称，
  右侧单选圈 `.pay-radio`，选中时填品牌橙 `#FF6700` + 白勾（`checkmarkempty`）。
- `data` 加 `payList`（当前 `[{ code: 'wechat', name: '微信支付', icon: 'weixin', color: '#07C160' }]`）
  与 `activePay`（选中下标）。
- `handlePay` 里原来写死的 `payOrder(order.id, 'wechat')` → `payOrder(order.id, this.payList[this.activePay].code)`。
  **将来加支付宝等渠道，只往 `payList` 追加一项，模板和下单逻辑都不用动。**
- 底部按钮文案「我已支付」→「提交订单」。**只改文案，逻辑与提示未动**（点击仍是下单 + 立即支付一步走）。

**与示例代码的四处差异**（都为了「结合本项目」）：

| 示例 | 本项目 | 原因 |
|---|---|---|
| `<script setup>` + `ref` | Options API 的 `data` | 全项目都是 Options API |
| 另写 `.pay-wrap` 等自有样式 | 复用页面已有的 `.card` | 本页卡片的间距/圆角/白底已统一 |
| 选中色 `#e64340` | `#FF6700` | 项目品牌色（本页 `.addr-default` 也在用） |
| `size="36"` | `size="40rpx"` | `uni-icons` 的 `size` 默认单位是 px，36 会变成 72rpx |

- 图标名没猜：`weixin` / `checkmarkempty` 都在 `uniicons.css` 里 grep 确认存在
  （顺带确认 `checkmarkempty-filled` **不存在**，别照示例里的名字瞎写）。

**`payType` 后端确实会落库（已核实，非推测）**：

- `OrderController.payOrder` 收下 → `OrderServiceImpl.payOrder` → `OrderMapper.updateStatus`，
  `OrderMapper.xml` 里是 `<if test="payType != null">, pay_type = #{payType}</if>`，写入 `orders.pay_type`；
  `OrderVO.payType` 也会返给前端。
- 下单接口 `CreateOrderDTO` **没有** `payType` 字段，所以 `pay_type` 是**支付那一步**才写上，不是下单时。
  换句话说前端这个「支付方式」不是摆设，后台能查到。
- 前端 `api/order.js` 的 `payOrder(id, payType)` 本来就有默认值 `payType || 'wechat'`，
  所以「抽成变量」这个改动不会改变既有行为。

**遗留**：还没接 `uni.requestPayment`，点「提交订单」不会调起微信收银台。要真做成
「先下单 → 唤起收银台 → 支付成功」，得等后端给统一下单参数（prepay_id 那一套）才能接。

### 订单列表页 + 待付款页 + 模拟支付弹窗（2026-10-16）

- 订单列表页 `pages/order/list.vue`：全部/待发货/待收货按 `orderStatus` 过滤；已取消（status=6）灰胶囊标签 + 「删除订单/再来一单」
- 待付款页 `pages/order/pending-pay.vue`：倒计时（`createdAt + 30min`，分秒补零）+ 取消/付款
- 模拟微信支付弹窗（`confirm.vue`）：6 位密码框 + 底部数字键盘，输满 6 位自动 `payOrder`；关闭弹窗订单留待付款
- 删除订单走软删 `DELETE /api/orders/{id}`；再来一单复用 `addToCart` 重新加购 + 跳购物车
- 详情页底部购物车 icon 加数量角标（读 `useCartStore().count`）
- vue 文件统一短横线命名（9 个组件文件重命名，同步 `main.js`/`index.vue` import；注册名/name 不变，运行时行为不变）
- 待付款倒计时依赖后端 `createdAt` 时区修复（MySQL 容器 UTC vs JVM UTC+8 差 8h，倒计时会误判「超时关闭」，见后端 findings「订单软删 + 订单时间时区」）

### 订单模块重构 + 搜索 + 待发货详情 + 商品搜索 + 地址管理（2026-10-16）

**订单列表页重做为 6 Tab + 搜索**：

- `pages/order/list.vue` 改为页内 Tab（全部/待付款/待发货/待收货/评论/售后），一次 `listOrders` 拉全量，前端按 `orderStatus` 过滤（不再靠单状态页 `?status=` 进入）。
- 搜索框（导航栏下、Tab 上）调 `listOrders(keyword)`，后端按订单号/收货人/商品名模糊匹配；搜索在「后端 keyword 过滤 + 前端 Tab 过滤」两层组合。
- `onLoad` 按 `status` 参数 `findIndex` 定位 Tab（「我的」页「待发货/待收货」入口跳 `list?status=3/4`）。
- 状态分组语义：待发货 Tab = status 2+3（「待确认」是商家后台视角，顾客付款后就是「等发货」）；订单卡片里的状态字走后端 `orderStatusDesc`（待确认→「待发货」、待发货→「待发货」，阶段 24 已把「处理中」统一为「待发货」），Tab 名是「待发货」这个分组，不拆「处理中」独立 Tab。

**OrderCard 模块内组件**：

- 订单卡片抽成 `pages/order/order-card.vue`（list + pending-pay 复用），**局部注册，不进全局 `components/`**——全局只放跨模块组件（Guess/AddressPopup/QuantityStepper 等）。和 `CategoryPanel` 局部注册同一个道理：复用范围决定放哪。
- 卡片底部用 `#foot` 插槽，按钮由父页面按状态填；卡片本体 `@click` emit 跳详情，foot 按钮 `@click.stop` 阻止冒泡。

**待发货详情 / 商品搜索 / 地址管理**：

- 待发货详情 `pending-ship.vue`：按状态拆详情页，一个状态一个文件（待发货一个、已发货/售后/评论后续各自建），不堆一个通用几百行的 detail。
- 商品搜索 `search.vue`：首页/分类页顶部 `top-bar` 的假搜索框之前「点了没反应」是三层缺口（没人接 `@search`、没搜索页、后端没 keyword），本次补齐三层。
- 地址管理 `address.vue` + `address-edit.vue`：独立页面（不用难看的 AddressPopup 弹窗），复用 `api/address.js`；编辑页用 `listAddress` 按 id 回显（后端无单条地址接口）。

**支付方式 / 状态文案（后端配合）**：

- `payType` 存英文 code（前端 `confirm.vue` 已统一传大驼峰 `WeChat`，对齐 `PayTypeEnum`），后端 `fromCode` 仍用 `equalsIgnoreCase` 兜底历史小写数据，`OrderVO.payTypeDesc` 直接给中文，前端不再硬编码映射。
- 订单状态双文案（desc=顾客 / adminDesc=商家），前端只用 `orderStatusDesc`。

### 订单物流轨迹（假数据）（2026-10-17）

- 后端新增 `CourierEnum`（快递公司 key→中文，照旧 Node COURIER_MAP）、`LogisticsVO`/`LogisticsTraceVO`、`GET /api/orders/{id}/logistics`（假轨迹，用订单 id 做种子生成城市/路线/快递员，`SHIPPED` 5 条 / `FINISHED` 7 条含「已签收」）。
- `OrderVO` 加 `logisticsStatus`（已揽件/运输中/派送中/已签收）+ `latestTrace`（最新轨迹 desc），`toVO` 里 courier 从英文 key 转中文——列表卡片直接展示，不额外发请求。
- 前端 `api/order.js` 加 `getLogistics`；订单卡片 `order-card.vue` 商品明细下加物流信息行（`v-if="order.logisticsStatus"`，点击跳 `logistics?id=`）；待发货详情 `pending-ship.vue` 状态下方加物流卡（`v-if="order.trackingNumber"`）。
- 新建 `pages/order/logistics.vue`：快递卡片（📮 公司+单号+复制）+ 轨迹时间线（倒序、最新高亮）。
- 物流轨迹是假数据（参考旧 Node 后端的 `getLogistics`），真实物流 API（微信物流助手）未接，后端 `task_plan` 挂着待办。

### 个人信息页 + 商品收藏（2026-10-23）

**个人信息页**
- 「我的」页用户卡片右上角加齿轮（`uni-icons type="gear"`），跳 `/pages/my/personal-info`；卡片 `position: relative`，`user-meta` 留右内边距，昵称过长不压图标。
- `pages/my/personal-info.vue`：进页 `ensureLogin()` → `GET /api/user/profile`；头像 `open-type="chooseAvatar"` → `uploadAvatar`；昵称失焦/回车 → `POST /api/user/name`（空值回退、未改动不发请求、`savingName` 防 confirm+blur 重复提交）；未绑定手机号显示「去绑定」，`open-type="getPhoneNumber"` → `POST /api/user/phone`；注册时间只读，兼容 Jackson 3 的字符串/数组两种序列化。
- 手机号显示 `user.phone`（账号手机号），不是 `orders.phone`（订单收货人快照，可能不是本人的、一单一号、且跨服务）。

**商品收藏**
- 后端 product-service `product_favorites`（`shop_id + user_id + product_id` 唯一键），接口：`GET /api/favorites`、`GET /api/favorites/check?productId=`、`POST/DELETE /api/favorites/{productId}`；需 user 令牌。
- 详情页 `pages/goods/detail.vue`：删掉 `uni.setStorageSync('favoriteIds')` 假收藏；已登录 `checkFavorite` 回显；点击先 `ensureLogin()`（静默）再增删，`favoriteLoading` 防连点，成功后才翻转星标。
- 新增 `pages/my/favorites.vue`：`ensureLogin` → 列表；卡片展示图/名称/售价/划线价，右上角星标取消收藏（成功后本地移除）；空态「去逛逛」；下架商品打「已下架」但仍可点进详情（后端列表不按 `listing_status` 过滤）。
- 「我的」页「收藏与浏览」入口接通 `/pages/my/favorites`，名称按用户要求保留，本期只做收藏（浏览记录未做）。
- 商品图统一用 `utils/format.js` 的 `goodsImgUrl` 补 `BASE_URL`，空图回退本地占位图。

## 技术决策

| 决策 | 理由 |
|------|------|
| 以 `openapi.yaml` + 后端源码为接口权威依据 | 文档与实际代码一致，而 `avatar-upload.md` 是过时推演 |
| `/api/member/*` → `/api/user/*` | 后端无 `member` 包，不改就是 404 |
| 重写而非删除 `avatar-upload.md` | 其中白名单、大小限制、删旧文件等实现细节仍有参考价值 |
| 检查出的 8 条问题不修 | 用户明确「能跑通就行了」 |
| 昵称用 `type="nickname"` 原生输入框，不用 `getUserProfile` | `getUserProfile` 自 2022-10 起昵称返回「微信用户」占位，官方指定用 `type="nickname"` |
| 两个分支各放一个昵称输入框（而非抽成一个） | 两个分支本就互斥，抽出去要额外的 `v-if` 判断位置，不如就地各放一行直观 |
| 昵称提交走 `request` 而非 `uploadFile` | 纯 JSON 字段，不需要 multipart |
| 前端统一走网关 8080，不直连各微服务端口 | 后端已定「路径 `/api/**` 不变，前端 baseURL 指向网关」；端口差异由网关收敛 |
| `navContentHeight` 用 `Math.max(44, 反推值)` 兜底 | 华为部分机型 `menuRect.top` 异常偏小，反推高度小于搜索框会上下裁切；44 与微信标准内容区高度及 data 默认值一致 |
| `rightSafeArea` 在胶囊避让基础上 +8px | 原公式让搜索框右边缘精确贴胶囊（间距 0），华为胶囊偏右时贴边明显；4~8px 为推荐区间，取 8 保底 |
| Vue3 全局注册组件用 `app.component()` | `app._component` 不是合法 API，运行期直接 `is not a function` |
| 分类图标用 png 而非 iconfont 字体 | 字体图标单色、无原色，png 彩色更好看；也避开两套 iconfont 同名 `font-family` 冲突 |
| `CategoryPanel` 局部注册 | 只在首页用，不污染全局 |

| 商品预览弹窗用 `uni-popup` 而非自写遮罩 | 项目已装 uni-ui 且 easycom 已配；遮罩 / 居中 / 点遮罩关闭 / 滚动穿透都内置，自写要重复造且要处理 z-index 与 TabBar 层级 |
| 商品图相对路径统一补 `BASE_URL` | 后端存 `/image/upload/N.webp`，小程序把 `/` 当包内路径；`/static/**` 只是本地占位，跟后端图不是同一份 |
| 弹窗显隐用 `watch` 桥接命令式 `open/close` | uni-popup 是 ref 调用的命令式 API，页面是 data 驱动 show；加内部 `opened` 标记防 change 与 watch 互相触发 |

| AI 导购 Agent 前端统一走网关 `/api/agent/**` | 后端 ai-agent-service 在 9008，前端只连 8080；白名单已放行，游客可直连 |
| 悬浮导购先做「对话 + 商品推荐」，下单走 HITL 留后 | 后端 `OrderTool.placeOrder` 依赖 order-service 未就绪；前端先引导，下单仍走原加购/下单链路 + `ensureLogin` |
| 流式优先、非流式兜底 | 后端两个接口都提供；小程序端 SSE 依赖 `enableChunked` 需真机，不确定时用 `/chat` 一次回整段 |

| 猜你喜欢推荐算法用 Python 写，不用 Spring AI | 推荐是独立的 Python FastAPI 服务；Spring AI 只用于聊天机器人 ai-agent-service，两者职责不同 |
| 猜你喜欢接口免登录、未登录走热门兜底 | 复用 `/api/products/**` 白名单，游客也能看首页；个性化只在已登录时生效 |
| 下拉刷新用 `scroll-view` 的 `refresher-enabled`，不用原生 `onPullDownRefresh` | 首页自定义 fixed 导航栏会盖住原生转圈（固定在页面顶部）；scroll-view 转圈在导航栏下方，不被遮挡且可自定义样式 |

| 分类页商品按「一级分类 + 二级分类 id 集合」前端过滤 | 后端 `/api/products` 无分类过滤参数、返回全量；商品挂二级分类，一级分类无直接商品 |
| 首页跳分类页用 Storage 传 `selectedCategoryId` | `switchTab` 不能带 query；存 id → 分类页 `onShow`/加载后定位 → 用完即删 |
| 分类页定位选中分类放 `onShow` 且找不到不删 Storage | tabBar 页缓存，`mounted` 只跑一次；首次进入 `onShow` 先于数据返回，找到再删避免选不中/残留 |

| 登录弹窗做成全局组件（`components/` + `main.js` 注册） | 加购入口以后不止分类页（详情页、购物车页都会用）；登录 UI 此前散在 `index.vue` / `my.vue` 两处且写法不同，抽成全局组件后复用一套 |
| 登录弹窗「打开即静默登录」，不做「微信登录」按钮 | `POST /api/wxlogin` 在免登录白名单、`uni.login` 不弹授权框，登录全程可无感；加购只要 token，没有需要用户额外授权的信息 |
| 登录后总是补一次 `getProfile()` | `ensureLogin()` 已有 token 时直接 return，而重开小程序 `store.userInfo` 是 `{}`；不补就永远没头像 |
| 不收集昵称 | 用户明确要求；加购链路用不到昵称 |
| 头像不追求「打开弹窗自动获取」 | 平台限制：`chooseAvatar` 只在真实点击时触发，`getUserProfile`/`getUserInfo` 已废弃（返回灰色匿名头像）。能自动的只有「拉后端已存的头像」，要换必须用户点一下 |
| 加购的登录判断放 `ProductPopup` 组件内 | 请求本来就在组件里发，「未登录」是同一条链路里的分支；放页面会让页面同时管弹窗 + 请求 + 登录态三件事 |
| 未登录点加购时商品弹窗不关 | 用户选的数量/增值保障留在原地，静默登录回来再点一次即可；关掉重开会触发 `watch.show` 的重置，选择白丢 |
| 飞入动画用两层 `transform` 合成，不用 JS 逐帧算 | 抛物线拆成「外层匀速横向 + 内层负 y1 贝塞尔纵向 + 缩放」，交给合成层跑，不掉帧也不用定时器改坐标 |
| 飞入层放 `uni-popup` 外层，`z-index: 1200` | 弹窗收起自带 `transform`，飞入层在弹窗内会被带着一起动；另需盖过弹窗遮罩（1000）与 tabBar（999） |
| 首帧与位移分两次 `setTimeout`，不用 `$nextTick` | 小程序里 `$nextTick` 不保证首次样式已渲染到视图层，两次样式落在同一次渲染就没有补间、动画直接闪到终点 |
| 收货地址弹窗 `AddressPopup` 抽全局组件（列表 + 新增/编辑两个视图） | 下单/我的等多页面都要选地址；组件内含列表和编辑，emit select/saved/close 供页面接 |
| 收货地址用微信原生 picker mode="region"，region 存「省 市 区」字符串 | 数据微信内置、零包体积、自动级联；地址只是展示+发货，不拆字段不建省市区表 |
| UI 库用 uview-plus（Vue3 版），不用官方 uview-ui | 官方 uview-ui 2.x 仍是 Vue2 写法（Vue.filter），Vue3 报错；uview-plus 是社区维护的 Vue3 版 |
| uview-plus 组件用 easycom 按需加载 + prop 定制颜色，不用 CSS 覆盖 | app.use 全量注册占包体积；组件颜色是内联 style（active-color/inactive-color 等 prop），CSS 覆盖无效 |
| 支付方式做成 `payList` 数组 + `activePay` 下标，不写死微信 | 示例是写死一项 + 固定选中；改成数组后「加渠道只追加一项、模板不动」，`handlePay` 也从写死 `'wechat'` 变成从选中项取 `code`；只有一项时点它等于选它自己，行为与改造前一致 |
| 支付方式卡复用 `.card`，选中色用 `#FF6700`，`size` 写 `40rpx` | 示例另起了 `.pay-wrap` 和 `#e64340`、`size="36"`；前者与其它卡片样式漂移，后者是别家品牌色 + 单位踩坑（`size` 默认 px，36 = 72rpx） |
| 底部按钮改文案不改逻辑 | 用户只要求改「我已支付」→「提交订单」；真正拆成「先下单 → 唤起收银台」要等后端统一下单参数，现在改了逻辑也跑不通 |

## 遇到的问题

| 问题 | 解决方案 |
|------|---------|
| 前端请求 `/api/member/profile`、`/api/member/avatar` 会 404 | 改为 `/api/user/profile`、`/api/user/avatar` |
| `avatar-upload.md` 按 `MemberController` / `/api/member/**` 记录，与后端不符 | 按实际接口与包名重写，并加更正记录 |
| `uni.setStorageSync('userInfo', ...)` 写了从不读（死代码） | 已记录，用户决定不修 |
| ~~昵称永远显示「微信用户」~~ | **已解决**：后端补 `/api/user/name`，前端补 `type="nickname"` 输入框 |
| 切华为机型搜索框贴边 | `rightSafeArea` 补 +8px 间距 |
| 切华为机型搜索框上下被裁切 | `navContentHeight` 加 `Math.max(44, ...)` 下限 |
| 组件改名后 `ENOENT: components/Swiper.wxml` | 编译缓存残留，关开发者工具删 `unpackage/dist` 重编译 |
| `app._component is not a function` | `main.js` Vue3 分支误写，改为 `app.component()` |
| `@import` 相对路径 `../../` 找不到 css | 组件在 `components/` 下，到根 `static` 是 `../` |
| 微信开发者工具 `Component is not found` | 「过滤无依赖文件」误删懒加载组件，关闭该功能 |
| iconfont 字体图标"没有原有颜色" | 字体图标是单色字符，颜色靠 CSS `color`；改用 png |

| 商品预览弹窗看不到图片 | 后端 `products.image` 存 `/image/upload/N.webp` 相对路径，前端直接当 src，小程序把 `/` 解析成包内路径 → 空白；补 `BASE_URL` 前缀解决 |

| 小程序端 SSE 流式接入方式待确认 | 微信 `wx.request` 需 `enableChunked` + `onChunkReceived`（基础库 2.20.1+，开发者工具不支持、需真机）；不确定则降级非流式 `/api/agent/chat` |

| 猜你喜欢图片底部圆角"被剪掉" | 卡片 `overflow:hidden` + `border-radius` 只裁卡片外边界（图片顶角），图片底边在卡片内部不贴底边，需给 `.goods-img` 单独设 `border-radius: 0 0 16rpx 16rpx` |
| 猜你喜欢标题 `text-align: center` 不生效 | `.guess-header` 是 flex 容器，`text-align` 只管行内文本，flex 子项居中要用 `justify-content: center` |

| 首页下拉刷新看不到动画 | 本地接口秒回导致 `refreshing` 瞬间 true→false，转圈没渲染就复位；`Promise.all` 内联 `setTimeout` 延时保底展示 |

| 分类页商品卡片高度不一致、对不齐 | 商品名 1/2 行导致卡片总高不同；卡片固定高度 + 名称区固定两行高度、价格行贴底 |

| 加购飞入动画不播，商品图直接闪到购物车 | 初版用 `$nextTick` 改位移，小程序里首帧样式未必已渲染到视图层，两次样式落在同一次渲染就没有补间；改成 `setTimeout(..., 30)` 确保首帧立住 |
| 编辑 `index.vue` 样式块时误把尾部规则一起替换掉，出现两条重复的 `.scroll-inner` | 替换串选得过宽，把尾部规则一起吞了；读文件复查发现后用后续 Edit 修掉（Edit 不报错不代表改对了，改动后要回读） |

| 照抄示例的 `uni-icons size="36"` 会画成 72rpx | `size` 默认单位是 px（纯数字走 `getVal()` 补 `px`），与 iconfont 的 `rpx` 不是一个量纲；本项目一律写成带单位的字符串 `size="40rpx"`（带单位原样透传） |
| 改「我的」页图标尺寸时一度以为「改动没生效」 | 实测是两套字体的字形墨迹不同：`uni-icons` 约 74.8%×74.6% em，自建 iconfont 的 `wuliuchaxun` 达 92.8%×95.5%，**同一 font-size 下 iconfont 画出来大 27%**；「两边写同一个 rpx」不等于视觉同大。结论：两套混排要各自设值（本轮最终 iconfont 40rpx / uni-icons 46rpx） |

## 资源

### 项目根目录

| 项目 | 绝对路径 |
|------|---------|
| 后端（Spring Boot） | `D:\study\my-workspace\wechat-workspace\e-commerce-boot` |
| 前端（uni-app） | `D:\study\my-workspace\wechat-workspace\e-commerce-uniapp` |
| 推荐服务（Python） | `D:\study\my-workspace\wechat-workspace\ecommerce-python` |

### 关键文件

后端（`D:\study\my-workspace\wechat-workspace\e-commerce-boot`）：
- 接口文档：`docs/openapi.yaml`
- 源码（已拆微服务，user 模块在 `user-service`）：`user-service/src/main/java/ecommerce/user/`
  （含 `dto/UpdateNameDTO.java`）
- 配置：`user-service/src/main/resources/application.yml`（multipart 大小限制）、
  `user-service/src/main/resources/application-dev.yml`（DB / 微信 / JWT 密钥，已被 gitignore）、
  网关路由：`gateway/src/main/resources/application.yml`

前端（`D:\study\my-workspace\wechat-workspace\e-commerce-uniapp`）：
- 请求底座：`utils/server-api.js`（`request` / `BASE_URL`）、`utils/token.js`、`utils/auth.js`（`ensureLogin`）
- 接口层（按资源拆）：`api/login.js`、`api/product.js`、`api/categories.js`、`api/cart.js`
- 全局组件（`main.js` 注册）：`components/LoginPopup.vue`、`components/CustomSwiper.vue`、
  `components/Guess.vue`、`pages/index/component/TopBar.vue`、`pages/index/component/TabBar.vue`
- 页面：`pages/index/index.vue`、`pages/category/category.vue`（含 `component/ProductPopup.vue`）、
  `pages/cart/cart.vue`（占位）、`pages/my/my.vue`
- 状态：`store/user.js`（Pinia）
- 前端侧记录：`avatar-upload.md`

### 本地服务地址

- 网关（前端 baseURL）：`http://127.0.0.1:8080`（路由 `/api/**` → user-service 9006）
- 头像静态资源：`http://127.0.0.1:8080/upload/avatar/{userId}_{时间戳}.{后缀}`
- 各微服务端口见上文「后端微服务架构」表（前端无需直连）

## 待办（用户决定暂不处理）

`pages/index/index.vue` 检查出的 8 条，按「跑不通」优先级排序后仅第 3 条之外均为可选：

> ⚠️（2026-10-10）`index.vue` 的登录相关死代码已整段清理，下面第 1/2/7/8 条的出处代码**已不存在**，
> 第 3~6 条仍对应现存文件（`store/user.js`、`utils/server-api.js`、`components/LoginPopup.vue`、`pages/my/my.vue`），
> 只是行号已过期。保留本表只为记录当时的排查结论，不再逐条维护行号。

1. ~~昵称永远不会显示（功能未完成，需前后端都动）~~ **已解决**（后端 0928 补接口，前端本轮接完）
2. ~~已登录用户无换头像入口（`chooseAvatar` 按钮只在未登录分支）~~ **实际已有**：`pages/my/my.vue`
   的已登录分支就带 `chooseAvatar` 换头像，原条目指的是已被清理掉的 `index.vue` 旧代码。
3. `uni.setStorageSync('userInfo', ...)` 是死代码，从无读取
4. `uploadAvatar` 的 `JSON.parse` 未兜底，非 JSON 响应会静默失败（`server-api.js:61`）
5. `uploadAvatar` 未判断 token 是否存在（`server-api.js:57`）
6. `.avatar-btn` 的 `border: none` 去不掉小程序 button 的 `::after` 边框（`index.vue:146`）
7. ~~`fetchProfile` 失败无条件 `clearToken()`，网络抖动会误清有效 token（`index.vue:101`）~~
   **已解决**（本轮）：`request()` 补状态码后，`index.vue` 的 catch 改为只在 `statusCode`
   为 401/403 时 `logout()`，网络抖动/网关 502 保留登录态。
8. `doLogin` 遗留调试日志 `console.log`（`index.vue:74`）—— **代码已随登录死代码清理一并删除**

### 本轮新增（需后端配合，2026-10-10）

9. ~~**增值保障选了不落库**~~ **已解决（2026-10-11）**：`AddCartDTO` 已加 `insuranceDetail` / `insuranceFee`，
   `CartServiceImpl.addItem` 快照落库（不选则空数组 / 0）；前端 `ProductPopup` 勾选随 `addToCart` 请求带上。
   购物车 `subTotal` 与合计均含保障费（`subTotal = (price + insuranceFee) × quantity`）。
10. ~~`utils/auth.js` 的 `name: res.name` 恒为 `undefined`（`WxLoginVO` 无 `name` 字段，只有
    `token` / `id` / `avatar`）。~~ **已解决（2026-11-14）**：前端不再读 `res.name`，
    登录只取 `token` / `id` / `avatar`，昵称与手机号统一由 `/api/user/profile` 补齐
    （与 `my.vue` / `login-popup.vue` 现有写法一致）。`avatar-upload.md` 的接口字段表同步更正。

## 小程序端全面探索（2026-10-30）

### 技术栈与骨架

- **uni-app（Vue 3，`createSSRApp`）+ uview-plus 3.8 + Pinia 4 + @dcloudio/uni-ui**。
- **没有 `src/` 目录** —— 根目录直接就是 `pages/ api/ components/ store/ utils/`（与管理端、商城端的目录约定都不同）。
- 规模：79 个源文件，38 个页面 `.vue`。
- `main.js` 全局注册 9 个自定义组件：`CustomNavbar` / `CustomTabBar` / `CustomSwiper` / `Guess` /
  `LoginPopup` / `FlyToCart` / `QuantityStepper` / `AddressPopup` / `FloatingChat`。
- `pages.json`：**主包 15 页 + 4 个分包**（`pages/goods` / `pages/search` / `pages/order` / `pages/after-sales`）。
- **原生导航栏和 tabBar 全部关掉自绘**：所有页面 `navigationStyle: "custom"` + `tabBar.custom: true`，
  自绘 `CustomNavbar` / `CustomTabBar`（字体图标，避免矢量模糊）。品牌橙 `#FF6700`。
- `manifest.json` 微信 appid `wxa18a22c8c5c1fa12`，与后端 `wechat.appid` 一致 ✅。
- 有 `.editorconfig`，无 ESLint / Prettier / CI。

### 请求层与登录

- `utils/server-api.js`：`BASE_URL` 按 `process.env.NODE_ENV` 切
  （dev `http://127.0.0.1:8080` 本机网关 / prod `https://api.jiemids.com`），所有请求统一走网关，不直连微服务端口 ✅
- 请求自动带 `Authorization: Bearer`；响应**单层解包** `res.data.data` → 返回 data（与商城端一致，与管理端不同）
- `utils/auth.js` `ensureLogin()`：`uni.login` 拿 code → `POST /api/wxlogin` 换 token，**全程无感不弹授权框**
  - `loginPromise` 复用，避免并发重复登录
  - `manualLogout` 标记：用户主动退出后不再自动静默登录
- `utils/features.js` 功能开关：vip ✅ / coupon ✅ / points ❌ / agreement ❌ / privacy ❌
  —— 未开通的**隐藏入口**，避免审核判定「点击无响应」，这是小程序特有的合规考量，做得好 ✅

### 与后端的契约核对（逐条比对）

- 小程序调用的接口**全部在后端存在** ✅：
  - `/api/products/recommend` → `RecommendController`（`GET /api/products/recommend`）
  - `/api/products/random` / `/hot` / `/page` → `ProductController`
  - `/api/feedback` → `FeedbackController`（POST）
  - `/api/agent/chat`、`/api/agent/chat/stream` → Python `ecommerce-python/app/api/agent.py`
  - 购物车 / 订单 / 地址 / 收藏 / 会员 / 优惠券 / 售后 / 用户资料 全部齐
- **小程序是三个客户端里唯一把 C 端接口用全的**（商城端 `ecommerce-mall` 目前只用了商品浏览几个接口，
  管理端只管 admin 侧）。

### 探索中发现的问题

| # | 问题 | 影响 |
|---|---|---|
| 1 | **`api/agent.js` 的 SSE 流式客户端零调用**。`chatStream()` 写得很完整（分片缓冲、按 `\n\n` 边界切分避免中文截断、手写 UTF-8 解码、首字 15s 看门狗、低版本基础库降级到非流式），但全仓库**没有任何页面引用**。悬浮球和「专属客服」页实际都走 `store/ai-chat.js` + `utils/auto-reply.js`（**仅 83 行的本地规则表**） | 后端 Python AI 服务 + 网关 `/api/agent/**` 路由**完全闲置**，前端在用本地假客服。`chatStream` 是死代码 |
| 2 | **游客用不了后端 AI**。`ecommerce-python/app/services/auth.py` 的 `require_user()` **强制要求 `type=user` 令牌**（与 Java 共用 `JWT_SECRET`，只校验不签发），无 token 直接 401。而本文件上文「AI 导购 Agent」章节写的是 Java 版 `ai-agent-service` 时代的设计——那时 `/api/agent` 是免登录放行的，后端改成 Python 后**契约已经变了** | 接通后端 AI 前必须先定游客策略（放宽 Python 鉴权 / 前端先强制登录 / 双通道）。这是接通前的前置阻塞项 |
| 3 | **`<FlyToCart>` 组件完全没被使用**。只在 `main.js` 注册，全仓库无任何页面引用 | 阶段 17「加购链路贯通：服务保障 + 全局登录弹窗 + **飞入动画**」的成果在当前代码里已经不存在。飞入购物车动画实际缺失 |
| 4 | **401 与 403 被混为一谈**。`utils/server-api.js` 里 `code === 401 || code === 403` 都会 `clearToken()` | 后端 403 的语义是「身份类型不匹配 / 无权访问」（`JwtInterceptor` 的 `requiredType != actualType`），**不是登录过期**。例如后台 admin 令牌误调 C 端接口会 403，此时清 token 属于误伤。应该只对 401 清 token，403 只提示 |
| 5 | **5 个 API 导出未被引用**：`getProducts` / `getProductsByCategory` / `searchProducts`（老的不分页接口，注释已标注「仅兼容旧版本」）、`getRandomProducts`（首页「换一批」实际用的是 `getHotProducts` + `excludeIds`，不是 random）、`chatStream` | 前三个是有意保留的兼容层；后两个是纯死代码 |
| 6 | ~~`utils/auth.js` 的 `name: res.name` 恒为 `undefined`（`WxLoginVO` 只有 `token`/`id`/`avatar`，没有 `name`）~~ | **已解决（2026-11-14）**：前端不再读 `res.name`，登录只取 `token`/`id`/`avatar`，昵称与手机号统一走 `/api/user/profile`（与待办第 10 条一致） |
| 7 | 本文件上文「AI 导购 Agent」章节描述的是 **Spring AI 版 `ai-agent-service`（端口 9008）**，而当前后端实际是 **Python `ecommerce-python`**，网关路由也已从 `lb://ai-agent-service` 改成静态地址 `${AI_SERVICE_URL}` | 章节已过时，需要重写为 Python 版现状 |
| 8 | `uploadAvatar` 的 `JSON.parse(res.data)` 未兜底、非 JSON 响应会静默失败；且未判断 token 是否存在 | 与待办第 4/5 条重复，仍未处理 |

### 三个客户端的横向对比

| 维度 | 小程序 `e-commerce-uniapp` | 管理端 `e-commerce` | 商城端 `ecommerce-mall` |
|---|---|---|---|
| 框架 | uni-app (Vue 3) | Vue 3 + stepin 框架包 | Vue 3 + Vite 裸搭 |
| UI 库 | uview-plus + uni-ui | ant-design-vue 3 | Element Plus 2（全量） |
| 请求解包 | 单层 `res.data.data` | 双层 `res.data.data` | 单层 `res.data` |
| 登录方式 | 微信 `uni.login` **静默无感** | 账号 + 密码 + 图形验证码 | ❌ 尚无（后端缺 C 端登录接口） |
| 接口覆盖 | **C 端全量** | admin 全量 | 仅商品浏览几个 |
| 目录约定 | 根目录平铺（无 `src/`） | `src/` 标准 | `src/` 标准 |
| AI 客服 | 本地规则表（后端 SSE 闲置） | 无 | 无 |
| 成熟度 | **最高**（链路全通） | 高（脏配置多） | 最低（购物链路未通） |

---

*每执行2次查看/浏览器/搜索操作后更新此文件*
*防止视觉信息丢失*
