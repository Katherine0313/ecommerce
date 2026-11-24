# 任务计划：微信登录 + 用户资料闭环 → 前台购物链路

将此文件作为任务的持久化路线图。在开始复杂工作前创建，并在阶段变化时及时更新。

## 目标

前端 `e-commerce-uniapp` 的微信登录与用户资料链路，与后端 `e-commerce-boot` 的 `/api/wxlogin`、
`/api/user/profile`、`/api/user/avatar`、`/api/user/name` 四个接口完全对齐，
登录后能拉取、展示并修改含头像和昵称的用户资料。

**主线延伸（阶段 7 起）**：登录闭环已完成后，工作重心转到前台购物链路 ——
浏览（分类 / 热门 / 猜你喜欢）→ 看详情 → 加购（登录 + `POST /api/cart`）→ 购物车 → 下单 → 支付。
「浏览 → 加购 → 购物车 → 下单支付」已贯通（阶段 17~19）。

## 下一步

小程序 C 端已免登录。下单支付链路已通，订单列表页 6 Tab + 搜索 + 待发货详情 + 商品搜索 + 地址管理已做（阶段 23），
订单物流轨迹（假数据）已接入（阶段 24），「我的」页收货地址入口已接通。

接下来：**售后**（前端售后 Tab / 申请退款·售后入口点击行为）；首页「轮播图 Banner / 热门推荐」接口等后端提供后再接进下拉刷新。

2026-10-30 全面探索新增结论：**AI 客服的 SSE 链路端到端没接通**（前端 SSE 客户端零调用，实际跑 83 行本地规则表，
后端 Python `ecommerce-python` 完全闲置），且 Python 端 `require_user()` 强制要求 user 令牌，
与「游客可用客服」的旧设计冲突 → 阶段 27，第一步先定游客鉴权策略。

## 当前阶段

阶段 27：AI 客服接通后端 + 遗留清理（pending）

## 各阶段

### 阶段 1：现状排查
- [✓] 定位前端登录/头像相关文件
- [✓] 确认 `utils/server-api.js`、`utils/token.js` 的完整实现
- [✓] 确认 `pages/index/index.vue` 的调用链
- [✓] 确认后端包结构与实际接口
- **状态：** complete

### 阶段 2：接口对齐
- [✓] 读后端接口文档 `e-commerce-boot/docs/openapi.yaml`
- [✓] 对照后端源码 `UserController` / `WxLoginController` 验证文档
- [✓] 发现前端路径与后端不符（`/api/member/*` vs `/api/user/*`）
- **状态：** complete

### 阶段 3：前端修正
- [✓] `getProfile` 路径改为 `/api/user/profile`
- [✓] `uploadAvatar` 路径改为 `/api/user/avatar`
- [✓] 全项目复查，确认无残留 `/api/member`
- **状态：** complete

### 阶段 4：文档更正
- [✓] 重写 `avatar-upload.md`，按实际接口/类名/包名更正
- [✓] 补充文档更正记录，说明早先按 `/api/member/**` 记错的原因
- **状态：** complete

### 阶段 5：代码检查
- [✓] 检查 `pages/index/index.vue` 完整逻辑
- [✓] 比对后端返回结构与前端取值
- [✓] 列出 8 条问题（功能缺口 2 条 + 代码问题 6 条）
- [✓] 用户决定「能跑通就行了」，全部不修
- **状态：** complete

### 阶段 6：收尾
- [✓] 生成 `task_plan.md` / `findings.md` / `progress.md`
- [✓] 将 `utils/` 纳入 git 跟踪（已确认：`git ls-files utils/` 已含两个文件）
- **状态：** complete

### 阶段 7：昵称前端接入
- [✓] 探查后端本次改动，确认新接口 `POST /api/user/name`
- [✓] `utils/server-api.js` 新增 `updateName(name)`
- [✓] `pages/index/index.vue` 加 `type="nickname"` 输入框（未登录/已登录两个分支）
- [✓] 回写展示 + 退出登录时清理
- [✓] 更新三个规划文件
- **状态：** complete

### 阶段 8：前端对接微服务
- [✓] 探索后端微服务拆分：8 服务（gateway 8080 + 7 业务 9001~9007），仅 user-service 承载业务
- [✓] 确认前端对接方式：统一走网关 8080，路径 `/api/**` 不变，不直连各服务端口
- [✓] 确认 `utils/server-api.js` 的 `BASE_URL` 已指向网关，四个接口路径与 user-service 一致
- [✓] 在 `BASE_URL` 上方补注释，固化「统一走网关」约定与端口规划表
- [✓] 验证 gateway 路由前缀 `spring.cloud.gateway.server.webflux.routes` 合法（gateway 5.0.3 配置元数据确认）
- [✓] 更新三个规划文件
- **状态：** complete

### 阶段 9：顶部导航栏机型适配
- [✓] 澄清 `safeAreaInsets` 与本组件关系（顶部=statusBarHeight、底部=env()，无需引入）
- [✓] 修复搜索框贴边：`rightSafeArea = windowWidth - menuRect.left + 8`
- [✓] 修复搜索框上下裁切：`navContentHeight = Math.max(44, ...)`
- [✓] 更新三个规划文件
- **状态：** complete

### 阶段 10：轮播图组件改名编译报错
- [✓] 定位 `ENOENT: components/Swiper.wxml` —— 组件 `Swiper.vue` 改名 `CustomSwiper.vue` 后编译缓存残留（`.sourcemap` 里还留 `Swiper.js.map`）
- [✓] 定位 `app._component is not a function` —— `main.js` Vue3 分支误写 `_component`（正确 API 为 `app.component`）
- [✓] 修复 `main.js`：`app._component('CustomSwiper', ...)` → `app.component(...)`（已改）
- [✓] 清缓存重编译验证
- **状态：** complete

### 阶段 11：前台分类组件
- [✓] 新建 `components/CategoryPanel.vue`（6 分类宫格，3 列 × 2 行）
- [✓] 图标方案从 iconfont 字体改为 png（`static/image/category/*.png` 6 张）
- [✓] `pages/index/index.vue` 局部注册 + 插入轮播图下方，点击 `switchTab` 到分类页
- [✓] 图标加 `border-radius` 圆角裁剪
- [✓] 金刚区数据后端化：`CategoryPanel` 改为 `mounted` 调 `getCategories()`，只取 `parentId == null` 的一级分类，名称/图片用后端 `name`/`image`（补 `BASE_URL`），删除写死的 6 项
- [✓] 分类页 + 首页跳转联动（带选中分类，Storage 传 `selectedCategoryId`）
- **状态：** complete

### 阶段 12：商品预览弹窗（热门推荐 + uni-popup）
- [✓] 新建 `pages/index/component/HotPanel.vue`：热门推荐面板，4 色块 × 左右各一个商品位，点图 `$emit('select', id)`
- [✓] 新建 `pages/index/component/ProductPopup.vue`：商品预览弹窗（图 + 名 + 价 + 描述 + 关闭），props `show`/`product` 进、`$emit('close')` 出
- [✓] `pages/index/index.vue`：接 HotPanel 的 select → `getProductDetail` 拉详情 → `showProductPopup = true`，弹窗状态与商品数据留在页面 data
- [✓] 修复弹窗图片不显示：后端 image 字段是 `/image/upload/N.webp` 相对路径，小程序把开头的 `/` 当包内路径，前端补 `BASE_URL` 前缀
- [✓] 弹窗外壳改用 `uni-popup`（uni-ui，easycom 免注册）：`watch: show` ↔ `ref.open()/close()` 桥接；z-index 99 → 1000 压过自定义 TabBar
- **状态：** complete

### 阶段 13：悬浮式 AI 导购 Agent（前端）
- [ ] 新建悬浮组件（悬浮球 + 聊天抽屉/半屏面板），全局或各 tabBar 页引入
- [ ] 接 `POST /api/agent/chat`（非流式，body `{message}` → `Result<ChatReplyVO{reply}>`）
- [ ] 接 `POST /api/agent/chat/stream`（SSE 流式 `Flux<String>` 打字机渲染；小程序端支持受限时降级非流式）
- [ ] 消息列表 + 输入框 + 发送/清空；游客可用（后端白名单已放行 `/api/agent`）
- [ ] 下单等敏感操作仍走 `ensureLogin`（`utils/auth.js`），Agent 只做导购引导
- **状态：** pending（后端 Agent 骨架已跑通，前端未动；本轮仅更新三个 md）

### 阶段 14：首页「猜你喜欢」模块（Python 推荐服务对接）
- [✓] 接口封装：`api/product.js` 新增 `getRecommend(limit)`，对应 `GET /api/products/recommend`（免登录，未登录走热门兜底）
- [✓] 新建首页「猜你喜欢」区块组件 `components/Guess.vue`：两列商品卡片（图/名/价），`mounted` 调 `getRecommend` 拉后端商品，点图 `$emit('select', id)`
- [✓] `pages/index/index.vue` 在热门推荐（`HotPanel`）之后插入 `<Guess @select="handleProductSelect" />`（全局注册，复用弹预览逻辑）
- [✓] 已登录带 token 走个性化（后端按 `UserContext.getUserId()` 调 Python），未登录自动热门兜底（前端统一带 token 请求，无需自己判断登录态）
- **状态：** complete（前端已接 `getRecommend`，商品名称/价格/图片全部来自后端）

### 阶段 15：首页下拉刷新
- [✓] 定位「看不到动画」根因：本地接口秒回，`refreshing` 瞬间 true→false，转圈没渲染就复位
- [✓] 修复：`onRefresh` 的 `Promise.all` 内联 `setTimeout` 延时，保证动画至少展示一段时间
- [✓] 整合代码：去掉 `minDelay` 中间变量，延时 Promise 直接内联
- [✓] 确认用 `scroll-view` 的 `refresher-enabled` 而非原生 `onPullDownRefresh`（自定义 fixed 导航栏会盖住原生转圈）
- [✓] 下拉刷新「换一批」：后端新增 `GET /api/products/random`（随机 + 排除当前列表），前端 `onRefresh` 只换 `HotPanel` 的 8 个商品位（猜你喜欢不动）
- [ ] 接「轮播图 Banner 接口」进刷新（等后端 `GET /api/banners`）
- [ ] 接「热门推荐接口」进刷新（等后端 `GET /api/products/hot`）
- **状态：** in_progress（刷新换一批已完成；轮播图/热门推荐仍等后端接口）

### 阶段 16：分类页右侧商品网格
- [✓] 分类页 `category.vue` 左侧分类后端化：`mounted` 调 `getCategories()`，按 `parentId` 拆一/二级，一级分类记下 `subIds`（二级分类 id）
- [✓] 右侧「二级分类宫格」改为「商品网格」：`mounted` 调 `getProducts()` 拉全量商品，按「一级分类 id + 其二级分类 id 集合」前端过滤
- [✓] 商品卡片：每行两个，图片 + 名称 + 价格 + 购物车图标（iconfont `icon-gouwuche1`）
- [✓] 卡片固定高度 + 名称固定两行，解决不同名称长度下卡片等高对齐
- [✓] 清理冗余：删未使用的 `subs` 字段、`currentCategory` 计算属性、`main-title` 标题
- **状态：** complete

### 阶段 17：商品加购链路
- [✓] 对齐后台 `Mall.vue`，`ProductPopup` 补「服务保障」四项（uni-icons 替代 ant 图标）
- [✓] 同上补「增值保障」三张卡片（一年意外保 2% / 全面保修 1% / 电池无忧 ¥20，选中橙边框）
- [✓] 新建全局登录弹窗 `components/LoginPopup.vue` + `main.js` 两分支注册
- [✓] 弹窗打开即静默登录（`ensureLogin`），并总是补一次 `getProfile()` 自动带出头像
- [✓] 新建 `api/cart.js` 封装 `POST /api/cart`
- [✓] 加购逻辑收进 `ProductPopup`（未登录 `$emit('needLogin')`，已登录直接落库 + `adding` 防连点）
- [✓] 加购成功播「飞入购物车」动画（两层 transform 合成抛物线）
- [✓] `category.vue` 改 `@needLogin`，删已下沉到组件的加购方法
- [✓] `index.vue` 清理登录死代码 + 删多余的用户信息展示
- [✓] 更新三个规划文件
- **状态：** complete（增值保障暂未落库，见下方「关键问题」）

### 阶段 18：C 端免登录
- [✓] App.vue onLaunch 静默登录；my.vue 去登录/退出按钮，onShow 补拉资料
- **状态：** complete

### 阶段 19：下单支付链路
- [✓] `api/order.js`：createOrder / listOrders / getOrder / payOrder / cancelOrder + genIdempotencyKey（幂等键）
- [✓] `pages/order/confirm.vue`：下单确认页（地址卡 + 商品 + 配送/留言 + 金额汇总 + 底部按钮；按钮文案阶段 21 已由「我已支付」改为「提交订单」）
- [✓] `pages.json` 注册 `pages/order/confirm`
- [✓] `store/cart.js` 加 `checkoutItems` + `setCheckoutItems`（待结算商品快照）
- [✓] `cart.vue`：「去结算」存选中商品 → 跳确认页；「猜你喜欢」点商品拉详情弹 ProductPopup
- [✓] `ProductPopup.vue`：「立即购买」组装单商品 → 跳确认页；「电池无忧」按类别过滤（耳机/音箱不显示）
- **状态：** complete（下单 + 立即支付一步完成，暂未接微信支付；订单列表页未做）
- **后续修正（阶段 21）：** 底部按钮文案改为「提交订单」；支付方式抽出 `payList` 数据驱动

### 阶段 20：「我的」页 UI 重做（纯 UI，暂不接后端）
- [✓] 保留已有登录/头像逻辑（`onShow` 的 `ensureLogin` + `getProfile`、`chooseAvatar` 上传），只在其上叠加 UI 区块
- [✓] 用户卡片：改品牌橙渐变（`#FF6700` → `#FF8A33`），顶边与 `CustomNavbar` 同色接成一片，底边圆角收口
- [✓] 我的订单卡：标题 + 「全部订单」入口 + 5 个状态入口（待付款/待发货/待收货/待评价/退款售后），图标走 `uni-icons`
- [✓] 功能入口卡：5 行列表（物流查询/收藏与浏览/收货地址/意见反馈/关于我们），图标 + 文字 + 右箭头
- [✓] 好物推荐：直接复用全局 `Guess` 组件，不另写一套商品卡
- [✓] 跳转统一走 `handleNav(url)`：`url` 为空给「功能开发中」提示，路由补进数据即可接通
- [✓] 根节点 `.page` 去掉原来的 `align-items/justify-content: center`（居中布局会把卡片压成内容宽），改回与 `cart.vue` 一致的 `flex-direction: column`
- [✓] 「妙选VIP会员」「优惠券/积分」「专属客服」三块补回，**只做 UI 占位**（VIP 金色横条卡 / 优惠券·积分两栏数字 / 专属客服进功能列表），数字写死 0、跳转留空走「功能开发中」
- **状态：** complete（UI 完成；跳转目标页、真实数据待后续）

### 阶段 21：确认订单页支付方式选择
- [✓] 加「支付方式」卡（金额汇总卡之后）：`v-for` 遍历 `payList`，左侧图标 + 名称，右侧单选圈（选中填橙 + 白勾）
- [✓] `data` 加 `payList`（当前一项 `{ code: 'wechat', name: '微信支付', icon: 'weixin', color: '#07C160' }`）+ `activePay`（选中下标）
- [✓] `handlePay` 里写死的 `payOrder(order.id, 'wechat')` → `payOrder(order.id, this.payList[this.activePay].code)`
- [✓] 底部按钮文案「我已支付」→「提交订单」（只改文案，逻辑未动）
- **状态：** complete（用户给的示例是 `<script setup>` + 写死红色单选圈，未照抄，按本项目 Options API + 复用 `.card` 重写）

### 阶段 22：订单列表页 + 待付款页 + 模拟支付弹窗（2026-10-16）
- [✓] 订单列表页 `pages/order/list.vue`：全部/待发货/待收货按 `orderStatus` 过滤；已取消订单（status=6）灰胶囊标签 + 「删除订单/再来一单」
- [✓] 待付款页 `pages/order/pending-pay.vue`：倒计时（分秒补零）+ 取消/付款；`my.vue` 待付款入口跳此页
- [✓] 模拟微信支付弹窗（`confirm.vue`）：6 位密码框 + 底部数字键盘，输满 6 位自动 `payOrder`；未接真实微信支付
- [✓] 提交订单拆成「下单 → 弹支付弹窗 → 不支付进待付款」（修正关键问题 5：文案与行为一致了）
- [✓] 删除订单（软删 `DELETE /api/orders/{id}`）+ 再来一单（复用 `addToCart` 重新加购 + 跳购物车）
- [✓] 详情页底部购物车 icon 加数量角标（读 `useCartStore().count`）
- [✓] vue 文件统一短横线命名（9 个组件文件重命名，同步 main.js/index.vue import）
- [✓] 「我的」页用户卡片改白底（阶段 20 的橙渐变改白底，与功能卡片统一）
- **状态：** complete（待付款倒计时需后端 createdAt 时区修复配合，已修；订单详情页未做）

### 阶段 23：订单模块重构 + 搜索 + 待发货详情 + 商品搜索 + 地址管理（2026-10-16）
- [✓] 订单列表页重做为 6 Tab（全部/待付款/待发货/待收货/评论/售后）+ 搜索框（订单号/收货人/商品名）+ `onLoad` 按 status 定位 Tab（「我的」页跳转）
- [✓] 订单卡片抽成模块内组件 `pages/order/order-card.vue`（list + pending-pay 复用，局部注册，不进全局 `components/`）
- [✓] 待发货订单详情页 `pages/order/pending-ship.vue`（状态/收货地址/商品/金额/订单信息/支付方式）
- [✓] 商品搜索页 `pages/search/search.vue`（首页/分类页顶部假搜索框 `@search` 跳转；后端 `/api/products?keyword=` 匹配 name/sub_title/brand）
- [✓] 收货地址管理页 `pages/my/address.vue`（列表 + 设默认/删除）+ `pages/my/address-edit.vue`（新增/编辑）；「我的」页入口接通
- [✓] `utils/format.js` 抽公共函数（fmtMoney/formatOrderTime/parseOrderTime/goodsImgUrl），消除 list/pending-pay 重复
- [✓] 后端配套：订单/商品 keyword 搜索、支付方式枚举 payTypeDesc、订单状态双文案（待确认→顾客「待发货」、待发货→顾客「待发货」，阶段 24 统一）
- **状态：** complete（前端页面已做；售后 Tab、「申请退款/申请售后」入口点击行为未接，端到端未验证）

### 阶段 24：订单物流轨迹 + 状态文案统一 + 支付方式大小写（2026-10-17）
- [✓] 后端：`CourierEnum`（快递公司 key→中文）、`LogisticsVO`/`LogisticsTraceVO`、`GET /api/orders/{id}/logistics`（假轨迹，参考旧 Node getLogistics）
- [✓] 后端：`OrderVO` 加 `logisticsStatus`/`latestTrace`，courier 转中文返回；`OrderServiceImpl` 时间戳从 MySQL `NOW()` 改 Java 传参（修 created_at > pay_at 时区 bug）
- [✓] 前端 `api/order.js` 加 `getLogistics(id)`
- [✓] 订单卡片 `order-card.vue` 加物流信息行（有物流时显示最新轨迹，点击跳物流详情）
- [✓] 待发货详情 `pending-ship.vue` 加物流信息卡（状态下方，点击跳物流详情）
- [✓] 新建物流详情页 `pages/order/logistics.vue`（快递卡片 + 轨迹时间线，最新在前高亮）
- [✓] 订单状态文案统一：`PENDING_SHIP` C 端「处理中」→「待发货」（`OrderStatusEnum` + `list.vue` 注释）
- [✓] 支付方式大小写统一：`confirm.vue` payList code `'wechat'` → `'WeChat'`（对齐 `PayTypeEnum`）
- **状态：** complete（物流轨迹为假数据，真实物流 API 未接）

### 阶段 25：多租户沙箱 C 端 shopId 支持（2026-10-20）——已废弃
- [✓] 原实现：`SHOP_ID` 常量、`wxLogin(code, shopId)`、登录传 shopId
- [✓] **2026-10-29 已移除**：多租户下线，登录只传 `code`
- **状态：** canceled（单店模式）

### 阶段 26：个人信息页 + 商品收藏（2026-10-23）
- [✓] 「我的」页用户卡片右上角齿轮 → `/pages/my/personal-info`
- [✓] `pages/my/personal-info.vue`：头像 `chooseAvatar` 上传、昵称失焦/回车保存（`POST /api/user/name`）、未绑定手机号走 `getPhoneNumber` → `POST /api/user/phone`、注册时间只读；数据来自 `GET /api/user/profile`，改完同步 Pinia
- [✓] 商品详情页假收藏（`uni.setStorageSync('favoriteIds')`）换成后端接口：已登录 `checkFavorite` 回显，点击 `ensureLogin` 静默登录后 `addFavorite/removeFavorite`，`favoriteLoading` 防连点
- [✓] 新增 `api/favorite.js`（列表/check/收藏/取消）
- [✓] 新增 `pages/my/favorites.vue` + `pages.json` 注册：收藏列表/取消收藏/空态，下架商品打「已下架」；「我的」页「收藏与浏览」入口接通（名称保留，本期只做收藏）
- **状态：** complete（浏览记录未做）

### 阶段 27：AI 客服接通后端 + 遗留清理（2026-10-30 全面探索产出）

> 探索发现：前端 `api/agent.js` 的 SSE 客户端写完了但**零调用**，实际跑的是 83 行本地规则表；
> 后端 Python `ecommerce-python` 的 `/chat` 与 `/chat/stream` 完全闲置。

- [ ] **先定游客鉴权策略（阻塞项）**：Python 端 `app/services/auth.py` 的 `require_user()` 强制要求
  `type=user` 令牌，与 Java 版 `ai-agent-service` 时代的「免登录放行」契约已经变了。三选一：
  - A. Python 端放宽为「有 token 就校验、没 token 放行匿名会话」（改后端）
  - B. 前端游客先走本地规则、登录用户走后端 AI（只改前端，体验割裂）
  - C. 前端统一要求先登录（改前端 + 页面文案）
- [ ] **接通 SSE**：`store/ai-chat.js` 的 `send()` 改为调用 `api/agent.js` 的 `chatStream()`，
  保留 `utils/auto-reply.js` 作为断流/超时的兜底话术
- [ ] **确认部署形态**：网关 `/api/agent/**` → `${AI_SERVICE_URL}`（默认 `http://127.0.0.1:8000`），
  生产环境需确认 `AI_SERVICE_URL` 指向真实 Python 服务地址，且 `JWT_SECRET` 与 Java 侧一致
- [ ] **补回飞入购物车动画**：`components/fly-to-cart.vue` 已存在且已在 `main.js` 全局注册，但**全仓库无任何页面引用**，
  阶段 17 的成果当前不存在。需在商品详情页/商品预览弹窗加购成功后调 `play()`
- [ ] **401/403 分开处理**：`utils/server-api.js` 现在 `401 || 403` 都清 token。后端 403 是「无权访问」而非「登录过期」
  （`JwtInterceptor` 身份类型不匹配），清 token 属于误伤 → 改成只在 401 清 token
- [ ] **删死代码**：`getRandomProducts`（首页换一批实际用 `getHotProducts` + `excludeIds`）、`chatStream`（若确定不接后端 AI）
- [ ] **重写 findings 的「AI 导购 Agent」章节**：现描述的是 Spring AI 版 `ai-agent-service`（9008 端口），
  与当前实际的 Python `ecommerce-python` 不符
- [ ] **`uploadAvatar` 兜底**：`JSON.parse(res.data)` 未判非 JSON 响应会静默失败；且未判断 token 是否存在（对应待办第 4/5 条）
- **状态：** pending

### 阶段 28：商品详情页评价展示（2026-11-02，代码完成，后端已发版，小程序待人工上传）

> 后端在 `e-commerce-boot`（order-service），方案见 `e-commerce-boot/review`（v3）。
> 后端补了 C 端只读接口 `GET /api/reviews/product/{id}` + `/summary`（免登录）。

- [x] 新增 `api/review.js`：`getProductReviews` / `getReviewSummary`
- [x] 新增 `utils/review-media.js`：`parseReviewMedia`（JSON 解析，失败安全返回 `[]`）+ `reviewMediaUrl`（补 `BASE_URL`）
- [x] `pages/goods/detail.vue` 加「商品评价」section，**插在「商品参数」之前**
      —— 详情页三块顺序定为 **商品评价 → 商品参数 → 图文详情**
- [x] 展示：评分条 + 平均分 + 好评率 + 评价列表（昵称 / 星级 / 标签 / 内容 / 晒图 / 商家回复 / 时间）
- [x] 虚拟评价标「系统评价」灰标签、昵称「系统」；真实评价昵称由后端脱敏（手机号打码）
- [x] **默认只展开前 3 条** + 「查看全部评价」按钮（接口一次拉 20 条），不用 `onReachBottom`
- [x] 一条评价最多一个视频 → 单独一行 `<video controls>`，视频不进图片九宫格
- [x] 评价接口失败 `.catch()` 兜住，不影响详情页其它内容
- [x] SFC 解析 + 模板编译 + `node --check` 通过
- [x] **发版 · 后端**（2026-11-02）：`./deploy/release.sh order-service` + `gateway`
      → 镜像 `20261102-83aa72c-dirty`，容器 running；生产只读补验 `GET /api/reviews/product/7` 200、
      VO 无 `orderId`/`orderNo`/`userId` ✅
- [x] **发版 · 小程序构建**（2026-11-02）：HBuilderX 编译成功，12:55 导出 `unpackage/dist/build/mp-weixin`
      （**必须传 build 不能传 dev**）；⚠️ **微信开发者工具自动拉起失败**（`cli.bat` 不在 PATH）
- [ ] **【需人工】小程序上传**：把 `unpackage/dist/build/mp-weixin` 导入微信开发者工具并上传发布
- [ ] 真机验证：评价区渲染、晒图预览、视频播放

## 关键问题

1. **昵称要不要做？** ~~后端无更新昵称的接口，前端也没有 `nickname` 输入入口。~~
   **已解决**：后端已补 `POST /api/user/name`，前端已接 `type="nickname"` 输入框。
2. **已登录用户怎么换头像？** ~~目前 `chooseAvatar` 按钮只在未登录分支渲染，登录后没有入口。~~
   **实际已有入口**：`pages/my/my.vue` 的已登录分支就带 `chooseAvatar` 换头像；原结论指的是
   已被清理掉的 `index.vue` 旧代码。登录弹窗里的 `chooseAvatar` 只服务未登录场景。
3. ~~**`utils/` 未纳入 git**，是否提交？~~ **已解决**：`git ls-files utils/` 确认两个文件已跟踪。
4. ~~**增值保障选了不落库。**~~ **已解决（2026-10-11）**：后端 `AddCartDTO` 已加 `insuranceDetail` /
   `insuranceFee`，`CartServiceImpl.addItem` 快照落库；前端 `addToCart` 随请求带上，购物车/下单均含保障费。
5. ~~**底部按钮叫「提交订单」，但行为仍是「下单 + 立即支付」。**~~ **已解决（2026-10-16）**
   阶段 22 已拆成「下单 → 弹模拟微信支付弹窗（6 位密码框 + 数字键盘）→ 输满 6 位才 `payOrder`；
   关闭弹窗则订单留在待付款」。文案与行为一致了。真实 `uni.requestPayment` 仍待后端统一下单参数（prepay_id），届时把模拟弹窗换成真实收银台即可。

## 已做决策

| 决策 | 理由 |
|------|------|
| 以**后端 Swagger**（由 `@Schema`/`@Operation` 注解自动生成，入口见 `e-commerce-boot/docs/swagger-addresses.md`）+ 后端源码为接口权威依据 | 手写的 `e-commerce-boot/docs/openapi.yaml` 已废弃；`avatar-upload.md` 早先按 `MemberController` 推演，与实际不符，导致了 404 —— 手写文档同样不能当权威 |
| 前端路径 `/api/member/*` 改为 `/api/user/*` | 后端无 `member` 包，原路径会 404，属于"跑不通"，必须修 |
| 重写 `avatar-upload.md` 而非删除 | 文件仍有参考价值（记录了白名单、大小限制等实现细节），但内容必须准确 |
| 检查出的 8 条问题一律不修 | 用户明确表示「能跑通就行了」 |
| 所有规划文件放在项目根目录 | `init-session.sh` 的默认行为，本项目尚无 `.planning/` 目录 |
| 昵称输入框在未登录/已登录两个分支各放一个 | 与既有的「登录前选头像、登录后上传」模式对称；只放未登录分支的话，登录后想改昵称只能先退出 |
| 未登录时填的昵称只暂存在 `nickname`，登录后再提交 | 提交接口要带 token，登录前调用必然 401 |
| 昵称与后端一致时不重复提交 | 避免每次 `onLoad` 恢复登录态都白跑一次写库 |
| 前端统一走网关 8080，不直连各微服务端口 | 后端已定「路径 `/api/**` 不变，前端 baseURL 指向网关」；各模块端口由网关收敛，前端无需感知 |
| 分类图标用 png 而非 iconfont 字体 | 字体图标单色、无"原有颜色"，png 彩色更好看；也避开两套 iconfont 同名 `font-family` 冲突 |
| `CategoryPanel` 用局部注册而非全局 | 只在首页用，不污染全局；且当时 `main.js` 状态不稳，局部注册更省事 |

| 弹窗放 `index.vue` 页面层，不放 HotPanel 内 | 数据（详情接口 + show 开关）归页面；弹窗是无状态展示组件，页面拉数据 props 传、事件回传；HotPanel 只抛 id 保持纯展示 |
| 商品预览弹窗改用 `uni-popup` | 项目已装 uni-ui 且 easycom 已配，遮罩 / 居中 / 点遮罩关闭 / 滚动穿透都内置；用户要求换成 uni-popup |
| 商品图 URL 统一补 `BASE_URL`，不拼 `/static` | 后端 `products.image` 存 `/image/upload/N.webp` 相对路径（网关映射到 upload/image/），小程序把 `/` 当包内路径；`/static/**` 只是前端本地占位图，与后端图不是同一份 |

| AI 导购 Agent 前端统一走网关 `/api/agent/**` | 后端 ai-agent-service 在 9008，前端只连 8080；白名单已放行 `/api/agent`，游客可直连 |
| 悬浮导购先做「对话 + 商品推荐」，下单留 HITL | 后端 `OrderTool.placeOrder` 依赖 order-service 未就绪；前端先引导，下单仍走原加购/下单链路 + `ensureLogin` |
| 流式接口优先、非流式兜底 | 后端 `/chat` 与 `/chat/stream` 都有；小程序端 SSE 依赖 `enableChunked` 需真机，不确定时用 `/chat` 整段渲染 |

| 猜你喜欢推荐算法用 Python 写，不用 Spring AI | 推荐是独立的 Python FastAPI 服务（Item-Item 共现 + 热门兜底）；Spring AI 只用于聊天机器人 ai-agent-service，两者职责不同 |
| 猜你喜欢接口免登录、未登录走热门兜底 | 复用 `/api/products/**` 白名单，游客也能看首页；个性化只在已登录时生效，前端无需自己判断登录态 |
| 分类接口单独放 `api/categories.js`，不与商品混在 `product.js` | 分类/商品是两个不同资源，按资源拆开；后端 `categories` 虽挂在 `ProductController` 下，前端按语义独立成文件 |
| 分类页商品按「一级分类 + 二级分类 id 集合」前端过滤 | 后端 `/api/products` 无分类过滤参数、返回全量；商品挂在二级分类上（`products.category_id` → 二级分类 id），一级分类下无直接商品 |
| 首页跳分类页用 Storage 传 `selectedCategoryId` | `switchTab` 跳 tab 页不能带 query；存 id → 分类页定位后删除 |
| 分类页定位选中分类放 `onShow` 且找不到不删 Storage | tabBar 页缓存，`mounted` 只跑一次；首次进入 `onShow` 先于数据返回，找到再删避免选不中/残留 |

| 加购的登录判断放 `ProductPopup` 组件内，不放页面 | 加购请求本来就在组件里发，「未登录 → 抛事件让页面弹窗」是同一条链路里的判断；放页面等于让页面同时管弹窗显隐 + 请求 + 登录态三件事 |
| 未登录点加购时商品弹窗不关 | 用户选的数量/增值保障留在原地，静默登录（无感）回来再点一次即可；关掉重开会触发 `watch.show` 里的重置，选择白丢 |
| 全局登录弹窗「打开即静默登录」，不做「微信登录」按钮 | `POST /api/wxlogin` 在免登录白名单、`uni.login` 不弹授权框，登录可全程无感；加购只需要 token，没有需要用户额外授权的信息 |
| 登录后总是补一次 `getProfile()` | `ensureLogin()` 已有 token 时直接 return，而重开小程序后 `store.userInfo` 是空的；不补这一次就永远没头像 |
| 静默登录响应只给 `token`/`id`/`avatar`，昵称与手机号统一走 `/api/user/profile` | 微信静默登录只能换 openid，首次建档时 `User.name` 本来就是 null，后端补 `name` 字段也是空，治标不治本；登录只管令牌、资料单独取，与 `my.vue`/`login-popup.vue` 现有写法一致 |
| 头像不做成「弹窗打开自动获取微信头像」 | 平台限制：`chooseAvatar` 只在真实用户点击时触发，`getUserProfile` / `getUserInfo` 已废弃（只返回灰色匿名头像）。能自动的只有「拉后端已存的头像」，要换必须用户点一下 |
| 加购飞入动画用两层 `transform` 合成，不用 JS 逐帧算坐标 | 抛物线拆成「外层匀速横向 + 内层负 y1 贝塞尔纵向 + 缩放」两个 CSS transition，交给合成层跑，比定时器逐帧改坐标省事且不掉帧 |
| 飞入层放 `uni-popup` 外层，且 `z-index: 1200` | 弹窗收起自带 `transform` 动画，飞入层在弹窗内会被带着一起动；另需盖过弹窗遮罩（1000）和自定义 tabBar（999） |
| 首帧与位移分两次 `setTimeout`，不用 `$nextTick` | 小程序里 `$nextTick` 不保证首次样式已渲染到视图层；两次样式若落在同一次渲染就没有补间，动画会直接闪到终点 |
| 数量步进器抽全局组件 `QuantityStepper`（受控：value 进、change 出） | 购物车页与加购弹窗两处用同一套「输入 + 加减 + 库存上限」；受控让父组件决定何时/是否接受（购物车页调后端、弹窗本地改） |
| 购物车角标用 Pinia `store/cart.js` 全局维护件数 | 加购、购物车增删改、启动初始化多处会改总数，TabBar 全局组件读 store 自动同步，避免各页面各自算 |
| 加购弹窗 `ProductPopup` 抽全局组件（`components/`），首页+分类页共用 | 首页原本是纯预览弹窗、分类页是加购弹窗，两份重复；统一成加购版后首页也能直接加购 |
| 加购弹窗底部「加入购物车」用图标、「立即购买」用文字按钮并排 | 图标省空间、主次分明：立即购买是主行动（文字醒目），加购是次行动（图标够用）。立即购买当前占位，下单链路后续接 |
| 收货地址弹窗 `AddressPopup` 抽全局组件（列表 + 新增/编辑两个视图） | 下单/我的等多页面都要选地址；组件内含列表和编辑，emit select/saved/close 供页面接 |
| 收货地址用微信原生 picker mode="region"，region 存「省 市 区」字符串 | 数据微信内置、零包体积、自动级联；地址只是展示+发货，字符串够用，不拆字段不建省市区表 |
| UI 库用 uview-plus（Vue3 版），不用官方 uview-ui | 官方 uview-ui 2.x 仍是 Vue2 写法（Vue.filter），Vue3 报错；uview-plus 是社区维护的 Vue3 版 |
| uview-plus 组件用 easycom 按需加载 + prop 定制颜色，不用 CSS 覆盖 | app.use 全量注册占包体积；组件颜色是内联 style（active-color/inactive-color 等 prop），CSS 覆盖无效 |

| 「我的」页先做纯 UI，暂不接后端 | 用户要先看视觉定稿，页面结构确认后再对接，避免 UI 返工白写请求代码 |
| 「我的」页跳转用数据里留空的 `url` + `handleNav(url)` | 目标页（订单列表/地址/反馈等）都还没建，写死路由一点就跳失败；留空给「功能开发中」提示，页面建好只需补数据 |
| 「妙选VIP会员」「优惠券/积分」「专属客服」先做 UI 占位，功能后补 | **修正**：原定「整块不做」（理由是本项目当时没有 VIP / 优惠券 / 积分表与接口）。用户明确表示这三块**是他将来要做的**，要求先把位置占住，遂补回为纯 UI 占位。与「不做假功能」不冲突——位置和样式是将来要复用的，只是数字与跳转暂缺 |
| 三块占位的形态：VIP 独立金卡 + 优惠券/积分/专属客服合成三栏卡 | VIP 权益要突出，给一张金色横条卡；优惠券、积分、专属客服都是「一格入口」，合进同一张白卡三栏等分最省版面。**修正**：专属客服最初并进 `funcList`，用户要求与优惠券/积分排同一行，遂改为该卡第三栏。它没有数字，用图标顶在数字位上，数字位/图标位用固定高度的 `.stat-body` 包住，否则三栏标签会错行 |
| 图标尺寸一律用 `rpx` 写，列表图标外加定宽盒；**名义字号相同 ≠ 视觉大小相同** | 踩过三次坑：① `uni-icons` 的根节点是无宽度的 `<text>`，宽度=字形 advance，两套字体字形宽窄不同 → 列表各行文字起点错开，故包一层定宽定高的 `.func-icon-box`；② `uni-icons` 的 `size` 默认单位是 px，与 iconfont 的 `rpx` 混用要换算（`size="20"` = 40rpx），易错。查组件源码确认 `size` 走 `getVal()`，**带单位的字符串原样透传**，可直接写 `size="46rpx"`；③ 用 fontTools 量过字形墨迹：用户那套 iconfont 的 `wuliuchaxun` 占 em 92.8%×95.5%，`uni-icons` 只有 ≈74.8%×74.6%，**同一 font-size 下 iconfont 画出来大 27%**。两套混排时"都写同一个 rpx"并不等于看着一样大，要视觉齐平，iconfont 那侧字号得再乘约 0.78 |
| 「我的」页图标改用第二套 iconfont（`static/myvue-icon`），`uni-icons` 退为兜底 | **修正**：原写「全用 `uni-icons`」，因用户随后提供了自绘图标而作废。项目自建 iconfont（`static/icon/`）只有 5 个图标不够用，但用户新给了一套（`static/myvue-icon/`，4 个：待发货/待收货/售后/物流查询），改用它；`uni-icons` 只兜底补未给齐的 6 项 |
| 第二套 iconfont 的独立字体名写在 `App.vue`，不改 iconfont.cn 的下载文件 | **修正**：最初直接改 `static/myvue-icon/iconfont.css` 的字体名，但那是下载产物，用户一重新下载就被覆盖（本轮真炸了一次：`.myvue-icon` 基类消失 + `@font-face` 又同名顶掉第一套，两套图标一起乱）。改为在 `App.vue` 里于两行 `@import` 之后重新注册 `@font-face`——`iconfont` 指回第一套、`myvue-icon` 指向第二套，再补 `.myvue-icon` 基类。下载文件保持原样，以后重下载不再破坏任何东西 |
| 「我的」页好物推荐复用全局 `Guess` 组件，不照抄一份商品卡 | `Guess` 已是首页/购物车在用的「猜你喜欢」，样式与数据来源都现成；再写一套等于制造重复 |
| 订单状态入口的 `type` 先按 0~4 占位，与后端订单状态对齐待核 | 纯 UI 阶段不读接口，`type` 只是渲染用的 key；对接时按后端 `OrderVO.status` 实测值校正 |
| 支付方式做成 `payList` 数组 + `activePay` 下标，不写死微信支付 | 用户给的示例是写死一项 + 固定选中；改成数组后「加渠道只追加一项、模板不动」，`handlePay` 也从写死的 `'wechat'` 变成从选中项取 `code`。目前只有一项，点它等于选它自己，行为与改造前一致 |
| 支付方式卡复用页面已有的 `.card`，不照抄示例的 `.pay-wrap` | 本页所有卡片共用 `.card` 的间距/圆角/白底；另起一套样式只会与其它卡片漂移 |
| 底部按钮文案「我已支付」→「提交订单」，但**逻辑不动** | 用户只要求改文案。当前点击仍是「下单 + 立即支付」一步走、成功提示仍是「支付成功」，**文案与行为暂不一致**；等接 `uni.requestPayment`（需后端先给统一下单参数）时再一并改成「先下单 → 唤起收银台」 |

## 遇到的错误

| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| 前端请求路径 `/api/member/profile` 导致 404 | 1 | 改读 `docs/openapi.yaml`，按 `/api/user/profile` 修正 |
| 照抄 `avatar-upload.md` 的过时结论 | 1 | 该文件按 `MemberController` 记的，已按实际接口重写 |
| `utils/server-api.js` 被截断，`uploadAvatar` 整个丢失 | 1 | 未自行改回，报告后由用户恢复 |
| `GET /api/user/profile` 返回 **403**（非 401） | 1 | token 签名/过期都通过，只是 `type` 声明不符：本地存的是旧版后端签发的 `"member"` token，当前要求 `"user"`。清 Storage 重登解决 |
| 组件改名后报 `ENOENT: components/Swiper.wxml` | 1 | 编译缓存残留：`.sourcemap` 里还留旧 `Swiper.js.map`，增量编译仍去找旧组件。关微信开发者工具删 `unpackage/dist` 重编译 |
| 运行报 `app._component is not a function` | 1 | `main.js` Vue3 分支误写 `_component`，Vue3 全局注册应 `app.component()`，修正该行即可 |
| `@import url("../../static/category/iconfont.css")` 编译报找不到 | 1 | 组件在 `components/` 下，到根 `static` 只上一级 `../`，多写一级跑到 `my-workspace/` 去了 |
| 微信开发者工具报 `Component is not found` | 1 | 「过滤无依赖文件」误删 uni-app 懒加载组件（`() => "路径"`），关闭该功能即可 |
| iconfont 字体图标"没有原有颜色 / 难看" | 1 | 字体图标本质是单色字符，颜色全靠 CSS `color`，无内建颜色；改用 png |
| 用户重下载 `myvue-icon` 后「前端 icon 渲染乱了」 | 1 | 我上轮改的是 iconfont.cn 的下载文件，被新下载整体覆盖：字体名回 `iconfont`（与第一套同名 → 两套互顶），基类回 `.iconfont`（`my.vue` 要的 `.myvue-icon` 不存在了）。改为覆盖逻辑写进 `App.vue`，下载文件一字不动 |

| 商品预览弹窗看不到图片 | 1 | 后端 `products.image` 存 `/image/upload/N.webp`，前端直接当 `image` 的 src，小程序把开头的 `/` 解析成包内路径 → 空白。补 `BASE_URL` 前缀（`http://127.0.0.1:8080/image/upload/N.webp`）解决 |

| 加购飞入动画不播，商品图直接闪到购物车 | 1 | 初版用 `$nextTick` 改位移，小程序里首帧样式未必已渲染到视图层，两次样式落在同一次渲染就没有补间。改成 `setTimeout(..., 30)` 确保首帧立住 |
| 编辑 `index.vue` 时把样式块尾部写重，出现两条一样的 `.scroll-inner` | 1 | 删 `.avatar-btn` 等样式时替换串选得过宽，把尾部规则一起吞了。读文件复查发现后用后续 Edit 修掉 —— 改动后需回读确认，不能只信 Edit 没报错 |

## 备注
- 随着工作推进，将阶段状态从 `pending` 更新为 `in_progress`，再更新为 `complete`。
- 做重大决策前，重新读取目标和下一步。
- 及时记录错误，避免重复失败的方法。
- **本项目约定：能跑通就行，不主动堆健壮性/边界处理类改动。**
