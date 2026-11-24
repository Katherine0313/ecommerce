# 进度日志

## 发布（小程序没有 npm 脚本，用 HBuilderX 编译）

### 一条命令打包（推荐，比点菜单快）

```bash
/d/Software/tools/HbuilderX/cli.exe publish mp-weixin \
  --project "D:/study/my-workspace/wechat-workspace/e-commerce-uniapp" --upload false
```

- 产物输出到 `unpackage/dist/build/mp-weixin`，**编译完会自动拉起微信开发者工具**
- `--upload false` = 只打包不上传；上传仍需在开发者工具里点「上传」（CI 自动上传要 `--privatekey` 密钥文件）
- 等价的 GUI 路径：HBuilderX → 发行 → 小程序-微信 → 输出到 `unpackage/dist/build/mp-weixin`
- 打包前先确认 HBuilderX 已启动（CLI 靠它跑），项目已导入

### 上传与提审

```
HBuilderX 编译 → unpackage/dist/build/mp-weixin
  → 微信开发者工具「导入项目」选 unpackage/dist/build/mp-weixin
  → 上传版本 → 提交审核
```

- ⚠️ **必须传 `build` 目录，不能传 `dev`**（2026-10-25 踩过：`unpackage/dist/dev` 里 `BASE_URL` 是 `http://127.0.0.1:8080`，真机白屏）
- ⚠️ **改完源码一定要重新编译再传包**（2026-10-31 踩过：源码改了但没编译，线上包还是旧的 —— 表现为商品图旧、修复不生效；查产物时间戳 `unpackage/dist/build/mp-weixin` 即可确认）
- `utils/server-api.js` 里 `DEV_BASE_URL` 只在 `NODE_ENV === 'development'` 时生效，发行版走 `PROD_BASE_URL = https://api.jiemids.com`
- 编译报 `Device or resource busy`：微信开发者工具占用了 `unpackage/dist`，先关工具再删目录重编
- 后端接口改动**必须先发后端**（`./deploy/release.sh <svc>`，auth-service 最后），再传小程序包
- 提审需要：完整录屏 + 三个合法域名（request / uploadFile / downloadFile）都指向 `https://api.jiemids.com`

## 会话 32：2026-11-14（静默登录响应口径对齐）

### 背景

`WxLoginVO` 只有 `token` / `id` / `avatar`——微信静默登录只能换 openid，昵称和手机号拿不到，
但前端 `utils/auth.js` 一直读 `res.name`，恒为 `undefined`；`docs/avatar-upload.md` 的接口字段表
也还是旧契约（写有 `name`、`idNumber`），并指向已废弃的 `e-commerce-boot/docs/openapi.yaml`。

### 已执行操作

1. `utils/auth.js`：`setUserInfo` 去掉 `name: res.name`，登录只取 `token` / `id` / `avatar`；
   昵称与手机号统一由 `/api/user/profile` 补齐（`my.vue`、`login-popup.vue` 已是这个写法）。
2. `docs/avatar-upload.md`：更正接口字段表（`/api/wxlogin` **无 `name`**；`UserProfileVO` 是 `username` 不是 `idNumber`），
   补「登录响应只拿 token/id/avatar，资料一律走 `/api/user/profile`」的说明；
   `openapi.yaml` 引用改为后端 Swagger 入口（`e-commerce-boot/docs/swagger-addresses.md`）。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `utils/auth.js` | 去掉 `name: res.name` |
| `docs/avatar-upload.md` | 接口字段表更正 + Swagger 引用替换 |
| `docs/planning/findings.md` | 待办第 10 条、探索问题第 6 条标记为已解决 |
| `docs/planning/progress.md` | 会话 16 遗留事项标记为已解决 + 本条 |

### 评估后决定不动

- **后端 `WxLoginVO` 不加 `name` 字段**：静默登录那一刻 `User.name` 大概率就是 null（首次建档昵称没值），
  加回去仍是空，治标不治本。
- **后端 `WxLoginVO` 与 `PcLoginVO` 不合并**：两端凭据不同（微信 openid vs 账号密码/短信验证码），
  字段可得性不同，属于各自独立的接口契约，不是重复造轮子。

### 遗留事项

- `docs/avatar-upload.md` 的「前端实现要点」一节仍写着 `utils/server-api.js` 里放 `wxLogin` / `getProfile`，
  实际这些已挪到 `api/login.js`（`server-api.js` 现在只导出 `BASE_URL` 和 `request`）；
  `pages/index/index.vue` 的登录代码也早已清理。属另一批文档漂移，本轮未动。

---

## 会话 31：2026-11-02（商品详情页评价区）

### 当前状态
- **阶段：** 阶段 28（商品详情页评价展示）代码完成，**后端已发版，小程序构建已出包，待人工上传**。
- 后端在 `e-commerce-boot`（order-service），方案见 `e-commerce-boot/review`（v3）。

### 已执行操作
- 新增 `api/review.js`：`getProductReviews(productId, page, pageSize)` → `/api/reviews/product/{id}`，
  `getReviewSummary(productId)` → `/api/reviews/product/{id}/summary`（后端新增，免登录）。
- 新增 `utils/review-media.js`：`parseReviewMedia(images)` 解析媒体 JSON（失败安全返回 `[]`）、
  `reviewMediaUrl(url, baseUrl)` 补 `BASE_URL`（小程序把 `/` 当包内路径）。
- `pages/goods/detail.vue` 加「商品评价」section，**插在「商品参数」之前** ——
  详情页三块顺序定为 **商品评价 → 商品参数 → 图文详情**。
  内容：评分条（1~5 星分布）+ 平均分 + 好评率 + 评价列表（昵称 / 星级 / 标签 / 内容 / 晒图 / 商家回复）+ 时间。
- 虚拟评价在 C 端标「系统评价」灰标签、昵称显示「系统」；真实评价昵称是后端脱敏过的（手机号打码成 `138****8000`）。
- **默认只展开前 3 条** + 「查看全部评价」按钮（接口一次拉 20 条，展开不再请求）——
  评价区排在详情页最前面，一上来铺 20 条会把图文详情顶没。
  不用 `onReachBottom`（商品详情页已有其它滚动逻辑）。
- 一条评价最多一个视频 → **单独一行 `<video controls>`**，视频不进图片九宫格（`<image>` 播不了 mp4）。
- 评价接口失败用 `.catch()` 兜住，**评价挂了不影响详情页其它内容**。

### 验证
- `@vue/compiler-sfc` 解析 + 模板编译通过；JS `node --check` 通过。
- **未真机验证**（需重新构建上传后验）。

### 发版与生产验证（2026-11-02）
- 后端：`./deploy/release.sh order-service` + `./deploy/release.sh gateway`（C 端路径要过网关）
  → 镜像 `20261102-83aa72c-dirty`，容器 running。
  生产只读补验：`GET /api/reviews/product/7` 免登录 200，响应体无 `orderId`/`orderNo`/`userId` ✅。
- 小程序：HBuilderX 编译成功，12:55 导出 `unpackage/dist/build/mp-weixin`（**必须传 build 不能传 dev**）。
- ⚠️ **微信开发者工具自动拉起失败**（`cli.bat` 不在 PATH），**需人工导入 `unpackage/dist/build/mp-weixin` 并上传发布**。
- 真机验证（评价区渲染 / 晒图预览 / 视频播放）仍未做，等上传后验。

## 会话 30：2026-10-30（小程序端全面探索：架构摸底 + 接口契约核对）

### 当前状态
- **阶段：** 新建「阶段 27：AI 客服接通后端 + 遗留清理」；阶段 26（个人信息页 + 商品收藏）已完成。
- 工作区干净（`git status` 无改动），本次只产出文档，未改任何代码。

### 已执行操作
- 通读骨架 + 关键文件：`main.js` / `App.vue` / `pages.json` / `manifest.json` /
  `utils/server-api.js` / `utils/auth.js` / `utils/token.js` / `utils/features.js` /
  `store/user.js` / `store/cart.js` / `store/ai-chat.js` / `api/*.js` 全部 13 个 / `api/agent.js`。
- 提取小程序端调用的全部接口路径，与后端各 Controller 的 `@*Mapping` 双向差集比对 →
  **全部命中，小程序端是三个客户端里唯一把 C 端接口用全的** ✅
- 交叉核对 Python AI 服务 `ecommerce-python`：`/chat`、`/chat/stream` 两个路由存在；
  `app/services/auth.py` 的 `require_user()` 强制要求 `type=user` 令牌（与 Java 共用 `JWT_SECRET`，只校验不签发）。
- 统计未被引用的 API 导出（5 个）与组件（`<FlyToCart>` 0 引用）。
- 发现并记录 8 类问题（详见 findings.md「小程序端全面探索」章节）：
  1. `api/agent.js` 的 `chatStream()` SSE 客户端写得很完整但**零调用** —— 实际跑 `utils/auto-reply.js`（83 行本地规则表），后端 Python AI 服务与网关 `/api/agent/**` 完全闲置
  2. **游客用不了后端 AI**：Python `require_user()` 强制要令牌，与 Java 版 ai-agent-service 时代「免登录放行」的契约已变 → 接通前的阻塞项
  3. `<FlyToCart>` 组件只在 `main.js` 注册、全仓库无引用 → 阶段 17 的飞入动画成果当前不存在
  4. `server-api.js` 把 401 与 403 混为一谈（都清 token），后端 403 语义是「无权访问」不是「登录过期」，清 token 属误伤
  5. 5 个未被引用的 API 导出：`getProducts` / `getProductsByCategory` / `searchProducts`（有意保留的兼容层）、`getRandomProducts`（换一批实际用 `getHotProducts`）、`chatStream`
  6. `utils/auth.js` 的 `name: res.name` 恒为 undefined（与待办第 10 条一致，未改）
  7. findings.md 的「AI 导购 Agent」章节描述的是 Spring AI 版 `ai-agent-service`（9008 端口），与当前实际的 Python 服务不符
  8. `uploadAvatar` 的 `JSON.parse` 未兜底、未判 token 存在（与待办第 4/5 条重复）
- 摸清架构要点：uni-app 无 `src/` 目录、根目录平铺；主包 15 页 + 4 分包（goods/search/order/after-sales）；
  原生导航栏与 tabBar 全部关掉自绘；`utils/features.js` 功能开关隐藏未开通入口（规避审核判定「点击无响应」）。
- 产出：`findings.md` 新增「小程序端全面探索（2026-10-30）」章节 + 三端横向对比表；
  `task_plan.md` 新增阶段 27 并更新「下一步 / 当前阶段」；本文件新增本会话记录。

### 验证
- 未改代码，无需构建验证。
- 接口核对方式：提取 `api/*.js` 中全部 `request(...)` 路径，与后端各 Controller 的 `@RequestMapping` + `@*Mapping` 双向差集比对；AI 服务侧另查 `ecommerce-python/app/api/agent.py` 的路由装饰器。

### 待决策
- AI 客服游客鉴权策略（阶段 27 第一项，三选一）：放宽 Python 鉴权 / 游客走本地规则 / 前端强制登录。

## 会话 29：2026-10-29（商品扩容前端：二级分类 + 参数表）

### 当前状态
分类页右侧增加二级分类横向切换；商品详情增加「商品参数」表，数据来自后端 specs。

### 已执行操作
- `pages/category/category.vue`：只保留左侧一级分类，二级分类相关代码/样式已全部移除（用户后续自行加）；缓存按一级 id 区分
- `pages/goods/detail.vue`：新增 specs 参数表（有序 name/value，空值过滤），位于增值保障与图文详情之间
- `pages/index/component/category-panel.vue` 新增 `limit` 参数，首页传 6（3 列 × 2 行，避免第 3 行空一块）；分类页仍显示全部 8 个一级
- 首页热门推荐无需改代码，自动使用新的 300 个商品

### 待验证
- HBuilderX 运行小程序，检查分类页 8 个一级、详情参数表
- 后端种子商品已按型号去重（126 件），名称补品类（如「JBL Flip 6 蓝牙音箱」），副标题改为类目卖点文案

## 会话 28：2026-10-29（热门推荐优先取推荐分类）

### 当前状态
首页「热门推荐」8 个商品位改为：优先取 `categories.recommend=1` 的分类（含父分类推荐）下的商品，不够 8 个时用随机上架商品补足；首屏和下拉刷新共用该逻辑。

### 已执行操作
- 后端 product-service 新增 `GET /api/products/hot?limit=8&excludeIds=`：`ProductMapper.listRandomOnSaleByRecommendedCategory` + `ProductService.listHotProducts`（推荐分类优先，不足随机补，排除当前批次）
- 小程序 `api/product.js` 新增 `getHotProducts`；`pages/index/index.vue` 首屏 `loadHot()`、下拉刷新改调该接口

### 验证
- `hot?limit=8` 返回 8 个推荐分类商品；`limit=30` 返回 24 推荐 + 6 随机；带 `excludeIds` 无重复

## 会话 27：2026-10-29（多租户下线：登录不再传 shopId）

### 当前状态
后端多租户已彻底下线，小程序登录只传 `code`。

### 已执行操作
- `utils/server-api.js` 删除 `SHOP_ID` 常量与 shop 端口说明
- `api/login.js` `wxLogin(code)`，请求体只 `{ code }`
- `utils/auth.js` 登录不再传 `SHOP_ID`
- 旧版本小程序继续多传 `shopId` 由后端 `@JsonIgnoreProperties(ignoreUnknown = true)` 忽略

## 会话 26：2026-10-29（首页下拉刷新换一批商品）

### 当前状态
下拉刷新只换「热门推荐」`HotPanel` 的 8 个商品位，每次随机一批且排除当前展示的；「猜你喜欢」等其余模块不动。

### 已执行操作
- 后端（product-service）：`ProductMapper.listRandomOnSale`（`ORDER BY RAND() LIMIT`，`id NOT IN excludeIds`）、
  `ProductService.listRandomProducts`、`ProductController` 新增 `GET /api/products/random?limit=&excludeIds=`。
  免登录白名单已由 `/api/products/**` 覆盖，无需改网关。
- 前端：`api/product.js` 新增 `getRandomProducts(limit, excludeIds)`；`hot-panel.vue` 支持 `list` 入参（不足回退默认 8 个 id）
  并暴露 `currentIds()`；`pages/index/index.vue` 的 `onRefresh` 只替换 `hotList`，保留 600ms 最短转圈。

### 改动的文件
| 文件 | 改动 |
|---|---|
| `api/product.js` | 新增 `getRandomProducts` |
| `pages/index/component/hot-panel.vue` | 商品位支持外部 `list`，暴露 `currentIds()` |
| `pages/index/index.vue` | 下拉刷新只换热门推荐 |

## 会话 25：2026-10-23（个人信息页 + 商品收藏）

### 当前状态
「我的」页齿轮入口 → 个人信息页已完成（头像/昵称/手机号/注册时间）；商品收藏由本地 Storage 假收藏改为后端落库，新增收藏列表页。

### 已执行操作
- 后端（product-service）：`product_favorites` 表 + `FavoriteController/Service/Mapper`，`/api/favorites` 列表/check/收藏/取消 4 接口；`shop_id+user_id+product_id` 唯一键幂等，取消物理删除，下架商品保留并下发 `status`；网关补 `/api/favorites/**` 路由。
- 前端：新增 `api/favorite.js`；`pages/goods/detail.vue` 收藏改走接口（已登录 `checkFavorite` 回显，点击 `ensureLogin` 后增删，`favoriteLoading` 防连点）；新增 `pages/my/favorites.vue`（列表/取消/空态/下架标记）并注册；「我的」页「收藏与浏览」入口接通。
- 个人信息页：`pages/my/personal-info.vue`（`getProfile` 回显，头像 `chooseAvatar` 上传、昵称 `updateName`、手机号 `getPhoneNumber` + `bindPhone`、注册时间只读）；「我的」页用户卡片右上角加齿轮入口。

### 改动的文件
| 文件 | 改动 |
|---|---|
| `api/favorite.js` | 新建：收藏 4 接口 |
| `pages/goods/detail.vue` | Storage 假收藏换真接口 + 静默登录 |
| `pages/my/favorites.vue` | 新建：收藏列表页 |
| `pages/my/personal-info.vue` | 新建：个人信息页 |
| `pages/my/my.vue` | 齿轮入口 + 「收藏与浏览」接通 |
| `pages.json` | 注册 `pages/my/favorites` |

### 错误
| 错误 | 解决方案 |
|------|---------|
| （暂无） | |

## 会话 24：2026-10-20（多租户沙箱 C 端 shopId）

### 当前状态
多租户沙箱 C 端 shopId 支持完成：登录时把店铺 shopId 签进 user 令牌，后端按店铺隔离下单/购物车/地址。

### 已执行操作
- `utils/server-api.js` 加 `SHOP_ID` 常量（默认 1 = 模板店，测沙箱店改成对应 shopId）
- `api/login.js` 的 `wxLogin(code, shopId)` 加 shopId 参数
- `utils/auth.js` 的 `ensureLogin` 登录传 `SHOP_ID`

### 改动的文件
- `utils/server-api.js`、`api/login.js`、`utils/auth.js`

### 错误
| 错误 | 解决方案 |
|------|---------|
| （暂无） | |

## 会话 23：2026-10-17（订单物流轨迹 + 状态文案统一 + 支付方式大小写）

### 当前状态

订单物流轨迹（假数据）已接入：订单卡片物流行、待发货详情物流卡、物流详情页全部完成；订单状态文案统一（`PENDING_SHIP` 顾客视角「处理中」→「待发货」）；支付方式 code 统一大驼峰 `WeChat`。

### 已执行操作

- 后端：`CourierEnum`（快递公司 key→中文）、`LogisticsVO`/`LogisticsTraceVO`、`GET /api/orders/{id}/logistics`（假轨迹，用订单 id 做种子，参考旧 Node getLogistics）
- 后端：`OrderVO` 加 `logisticsStatus`/`latestTrace`，`toVO` 里 courier 转中文；`OrderServiceImpl` 时间戳从 MySQL `NOW()` 改 Java 传参（修 created_at > pay_at 时区 bug）
- 前端：`api/order.js` 加 `getLogistics`；订单卡片 `order-card.vue` 加物流信息行（点击跳物流详情）；待发货详情 `pending-ship.vue` 加物流卡（状态下方）；新建 `logistics.vue`（快递卡片 + 轨迹时间线）
- 订单状态文案统一：`OrderStatusEnum.PENDING_SHIP` C 端「处理中」→「待发货」+ `list.vue` 注释同步
- 支付方式大小写：`confirm.vue` payList code `'wechat'` → `'WeChat'`；后端下单默认 `PayTypeEnum.WECHAT.getCode()`
- OrderCard 组件从 `pages/order/component/` 移到 `pages/order/`（模块根目录，删掉 component 子目录）

### 改动的文件

- 前端：`api/order.js`、`pages/order/order-card.vue`、`pages/order/pending-ship.vue`、`pages/order/logistics.vue`（新建）、`pages/order/list.vue`（注释）、`pages/order/confirm.vue`（payType 大写）
- 后端：`enums/CourierEnum.java`、`vo/LogisticsVO.java`、`vo/LogisticsTraceVO.java`、`vo/OrderVO.java`、`service/OrderService.java`、`service/impl/OrderServiceImpl.java`、`controller/OrderController.java`

### 遗留事项

- 售后 Tab 占位、售后前端页面未做
- 「申请退款/申请售后」入口只显示文字，点击行为未接
- 已发货/评论/售后详情页未建
- 物流轨迹是假数据，真实物流 API（微信物流助手）未接

## 会话 22：2026-10-16（订单模块重构 + 搜索 + 待发货详情 + 商品搜索 + 地址管理）

### 当前状态

订单列表页重做为 6 Tab + 搜索、待发货详情页、商品搜索页、收货地址管理页均完成。

### 已执行操作

- 订单列表页 `pages/order/list.vue` 重做：6 Tab（全部/待付款/待发货/待收货/评论/售后）+ 搜索框 + `onLoad` 按 status 定位 Tab；订单卡片抽模块内组件 `pages/order/component/order-card.vue`（局部注册，非全局）
- 待发货详情页 `pages/order/pending-ship.vue`（按状态拆详情页，一个状态一个文件）
- 商品搜索页 `pages/search/search.vue` + 首页/分类页顶部假搜索框 `@search` 跳转（补齐三层缺口：@search、搜索页、后端 keyword）
- 收货地址管理页 `pages/my/address.vue`（列表 + 设默认/删除）+ `pages/my/address-edit.vue`（新增/编辑），「我的」页入口接通
- `utils/format.js` 抽公共函数；`api/order.js` 加 finishOrder、listOrders(keyword)、payOrder 默认 WeChat；`api/product.js` 加 searchProducts
- 后端配套：订单/商品 keyword 搜索、PayTypeEnum + payTypeDesc、OrderStatusEnum 双文案（待确认→顾客「待发货」、待发货→顾客「处理中」）

### 遗留事项

- 售后 Tab 占位（「售后功能开发中」），售后前端页面未做
- 「申请退款/申请售后」入口只显示文字，点击行为未接
- 已发货/评论/售后详情页未建（按状态拆详情页，后续各自建文件）
- 端到端未验证

## 会话 21：2026-10-16（订单列表页 + 待付款页 + 模拟支付弹窗）

### 当前状态
订单列表页、待付款页、模拟支付弹窗、删除订单/再来一单、购物车角标、vue 文件短横线重命名、我的页白底均完成。

### 已执行操作
- 订单列表页 `pages/order/list.vue`：全部/待发货/待收货按 `orderStatus` 过滤，已取消订单「删除订单/再来一单」
- 待付款页 `pages/order/pending-pay.vue`：倒计时（分秒补零）+ 取消/付款
- 模拟微信支付弹窗：`confirm.vue` 加 6 位密码框 + 底部数字键盘，输满 6 位自动 `payOrder`
- 提交订单拆成「下单 → 弹支付弹窗 → 不支付进待付款」（修正「提交订单」文案与行为不一致）
- 删除订单（软删 `DELETE /api/orders/{id}`）+ 再来一单（`addToCart` 重新加购 + 跳购物车）
- 详情页底部购物车 icon 加数量角标
- vue 文件统一短横线命名（9 个组件文件重命名）
- 「我的」页用户卡片改白底

### 改动的文件
- 新增 `pages/order/list.vue`、`pages/order/pending-pay.vue`、`pages/goods/detail.vue`
- 修改 `pages/order/confirm.vue`、`pages/my/my.vue`、`api/order.js`、`pages.json`、`main.js`、`pages/cart/cart.vue`、`pages/category/category.vue`、`pages/index/index.vue`
- 重命名 9 个组件文件为短横线（`components/` 5 个 + `pages/index/component/` 4 个）

### 遗留事项
- 订单详情页未做（点订单列表里的订单暂不跳）
- 真实微信支付未接（模拟弹窗占位，待后端统一下单参数 prepay_id）
- 「再来一单」未恢复增值保障（`OrderItemVO` 未返回 insuranceDetail/insuranceFee）

## 会话 20：2026-10-15（确认订单页加支付方式选择）

### 当前状态

已完成。`pages/order/confirm.vue` 金额汇总卡之后多了一张「支付方式」卡，目前只有微信支付一项、默认选中。

### 背景

用户给了别人的示例代码（`<script setup>` + 写死的红色单选圈），要求「加个支付方式的选择，用微信支付」，
并强调**不要照抄**。示例只保留微信支付、固定选中不可切换。

### 已执行操作

1. **先核实了三件事**，没有直接套示例：
   - `confirm.vue` 是 **Options API + `<style scoped>`**（示例是 `script setup`），已有通用 `.card` 卡片样式可复用。
   - `handlePay` 里微信支付**本来就是写死的**：`payOrder(order.id, 'wechat')` —— 要做的就是把它抽出来。
   - 查 `api/order.js` 的 `payOrder(id, payType)` 签名：`data: { payType: payType || 'wechat' }`，**支持传值**，
     默认值正好也是 `wechat`，所以改造不影响既有行为。
   - 查 `uniicons.css` 确认 `weixin`（`#07C160` 微信绿）和 `checkmarkempty` 两个 font_class 都存在，不用猜图标名。
2. **按本页风格重写**，与示例的差异：Options API 而非 `script setup`；复用页面已有的 `.card`（示例另写了 `.pay-wrap`）；
   选中色用项目品牌橙 `#FF6700`（示例用 `#e64340`）；`<style scoped>` 与本页一致。
3. 模板（金额汇总卡之后）加支付方式卡：`v-for` 遍历 `payList`，左侧微信图标 + 名称，右侧单选圈，选中时填橙 + 白色对勾。
4. `data` 加两项：`payList`（一项 `{ code: 'wechat', name: '微信支付', icon: 'weixin', color: '#07C160' }`）
   和 `activePay: 0`（当前选中下标）。
5. `handlePay` 里 `payOrder(order.id, 'wechat')` → `payOrder(order.id, this.payList[this.activePay].code)`，
   写死的支付方式变成数据驱动。**将来加支付宝等渠道，只需往 `payList` 追加一项，模板不用动。**
6. CSS 加 `.pay-item` / `.pay-name` / `.pay-radio`（含 `.checked` 态）。
7. 底部按钮文案「我已支付」→「**提交订单**」（`handlePay` 上方注释同步改）。
   **只改了文案，逻辑没动** —— 点击仍是「下单 + 立即支付 + 清购物车」一步走，成功提示也还是「支付成功」。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `pages/order/confirm.vue` | 新增支付方式卡（模板 + `payList`/`activePay` + 3 条样式）；`handlePay` 的支付方式改为从 `payList` 取，不再写死；底部按钮文案改「提交订单」 |
| `task_plan.md` | 新增「阶段 21」，更新「当前阶段」「下一步」；阶段 19 补「后续修正」标注；「已做决策」补 3 条（payList 数据驱动 / 复用 `.card` / 改文案不改逻辑） |
| `findings.md` | 新增「确认订单页支付方式（2026-10-15）」小节（含与示例的差异表、`payType` 落库链路）；更新「下单支付链路」里的按钮描述；「技术决策」「遇到的问题」各补 3 条和 2 条 |
| `progress.md` | 本会话记录；回改会话 19 里过时的「我已支付」与「`payType` 传占位」描述 |

### 遗留事项

- **还没接 `uni.requestPayment`**，点「提交订单」不会调起微信收银台，只是把 `payType` 透传给后端记账，语义与改造前一致。
  按钮虽已叫「提交订单」，但**仍在同一次点击里把订单置为已支付**、提示也仍是「支付成功」——要真做成
  「先下单、再唤起收银台」，得等后端给统一下单参数后一起改。
- `payType` 后端**确实会落库**（已核实）：`OrderController.payOrder` 收下后交给 `OrderServiceImpl.payOrder`，
  再经 `OrderMapper.updateStatus` 写进 `orders.pay_type`（`OrderMapper.xml` 里是
  `<if test="payType != null">, pay_type = #{payType}</if>`），`OrderVO.payType` 也会返给前端。
  注意下单接口 `CreateOrderDTO` **没有** `payType` 字段，所以 `pay_type` 是支付那一步才写上，不是下单时。
- 渠道多了以后要做成真正的单选（现在只有一项，点了也是选它自己）。

---

## 会话 19：2026-10-15（「我的」页 UI 重做，纯 UI）

### 当前状态
- **阶段：** 20 - 「我的」页 UI 重做（完成）
- **开始时间：** 2026-10-15

### 背景

「我的」页此前只有一个头像 + 昵称，内容单薄。用户给了另一项目的参考代码（用户卡 / VIP 卡 / 优惠券积分 /
订单入口 / 功能列表 / 好物推荐），要求**结合本项目改造**，并明确本轮**只看纯 UI、暂不接后端**，
页面定稿后再对接。

### 已执行操作

1. **保留既有登录逻辑不动**：`onShow` 的 `ensureLogin()` + `getProfile()`、`chooseAvatar` 换头像上传
   全部原样保留，只在其上叠加 UI 区块。
2. **用户卡片**：白色文字改到品牌橙渐变底上（`#FF6700` → `#FF8A33`），顶边与 `CustomNavbar`
   同色接成一片、底边 32rpx 圆角收口；头像加半透明白描边。
3. **我的订单卡**：卡头「我的订单」+「全部订单」入口，下面 5 个等分状态入口
   （待付款/待发货/待收货/待评价/退款售后）。
4. **功能入口卡**：5 行列表（物流查询/收藏与浏览/收货地址/意见反馈/关于我们），图标 + 文字 + 右箭头，
   末行去下边框。
5. **好物推荐**：直接挂全局 `Guess` 组件，未另写商品卡。
6. **跳转占位**：统一 `handleNav(url)`，`url` 为空弹「功能开发中」；路由将来填进 `orderTabs` /
   `funcList` 的数据即可接通，页面代码不用改。
7. **根节点样式修正**：`.page` 原为 `align-items/justify-content: center` 的居中布局，会把新卡片压成
   内容宽，改为与 `cart.vue` 一致的 `flex-direction: column`；底部留白加 `env(safe-area-inset-bottom)`。
8. **图标接入第二套 iconfont**：用户提供 `static/myvue-icon/`（待发货 `icon-31daifahuo`、待收货
   `icon-daishouhuo`、售后 `icon-shouhou`、物流查询 `icon-wuliuchaxun`）。
   - `my.vue` 图标写法改为模板里直写 `<text class="myvue-icon icon-xxx"></text>`；数据里加 `fontIcon`
     字段，有值走 iconfont，为空则 `uni-icons` 兜底 —— 后续把类名填进 `fontIcon` 即自动切换。
   - `App.vue` 全局 `@import` 引入（与第一套并列）。
   - 两套 css 的 `@font-face` 都叫 `"iconfont"`，同名会互相覆盖（详见下节「图标方案」）。
9. **修复图标渲染错乱（用户换图标引发的回归）**：用户重新从 iconfont.cn 下载 `static/myvue-icon/`
   覆盖了目录，导致：
   - 上一轮我直接改用户 css 的字体名（`iconfont` → `myvue-icon`）**被覆盖回去**，`my.vue` 里
     `.myvue-icon` 基类不存在了 → 4 个图标无字体可用；
   - 该 css 的 `@font-face` 又变回 `"iconfont"`，与 `static/icon/iconfont.css` 再次同名 →
     底部导航等第一套图标也被顶掉。
   **改法改为不动用户的下载文件**：`static/myvue-icon/iconfont.css` 保持原样（`@font-face` 仍是
   `iconfont`，`icon-shouhou` 的字形已由用户改为 `\e70b`），改在 `App.vue` 的 `<style>` 里，于两行
   `@import` 之后重新用名字注册一次：
   - `@font-face { font-family: "iconfont"; src: ./static/icon/** }` —— 把被顶掉的第一套抢回来；
   - `@font-face { font-family: "myvue-icon"; src: ./static/myvue-icon/** }` —— 第二套独立注册；
   - `.myvue-icon { font-family: "myvue-icon" !important; ... }` —— 补基类（用户 css 里只有 `.iconfont`）。
   这样以后重新下载 `myvue-icon` 不会再破坏任何东西，`my.vue` 一个字都不用改。
   注意：两套 `.iconfont` 基类同值同性，都指向 `"iconfont"`（= 第一套），无副作用。
10. **补回 VIP / 优惠券·积分 / 专属客服三块占位**：用户表示这三块是**他将来要做的**，要求先占住位置
    （推翻了此前「整块不做」的决定）。只做 UI、不接数据：
    - **VIP 会员卡**：用户卡下方一张金色横条卡（`linear-gradient(90deg,#F7E3B5,#E9C87A)`），
      左 `uni-icons` 的 `vip-filled`、中间「杰米电商VIP会员」+ 副标题、右「立即开通」半透明胶囊按钮。
    - **优惠券 / 积分 / 专属客服**：订单卡下方一张白卡**三栏等分**，非首栏加左边框作分隔。
      优惠券、积分显示数字 + 标签，数字先写死 0；专属客服没有数字，用 `uni-icons` 的 `headphones`
      顶在数字位上。三栏的数字位/图标位包在固定高度（50rpx）的 `.stat-body` 里，
      否则图标与文字高度不同会让三栏下方的标签错行。数据在 `statList`，对接时换成接口值。
      （专属客服最初放在 `funcList` 里，用户要求与优惠券/积分排同一行后改到这里。）
    - 三块的点击一律走 `handleNav('')`，弹「功能开发中」。
11. **修图标大小不一致 / 专属客服图标偏小**：用户反馈「物流查询~关于我们的 icon 大小不一致」
    「专属客服的 icon 太小」。查 `node_modules/@dcloudio/uni-ui/lib/uni-icons/uni-icons.vue` 后确认两点：
    - `uni-icons` 根节点是 `<text>`，**没有宽度**（组件样式里只有 `font-family` / `text-decoration` /
      `text-align`），宽度就是字形 advance → 两套图标占位不同宽，**列表各行文字起点会错开**。
    - 它把 `font-size` 写在**行内 style** 上（`styleObj` 计算属性），CSS 无法直接覆盖。
    - 另：`size` 走 `getVal()`，纯数字才补 `px`，**带单位的字符串原样透传** → 可以直接写 `size="44rpx"`，
      不必在 px/rpx 之间换算。
    对策：
    - 功能列表图标包一层定宽定高的 `.func-icon-box`（flex 居中）→ 各行文字起点对齐。
    - 两套图标的名义尺寸统一成同一个 rpx 值（具体数值第 12 项又被用户改过）。
12. **图标字号按用户指定改回 30rpx；并实测两套字体的字形尺寸**：第 11 项「两边统一成同一个 rpx 值」
    改完，用户仍反馈「没有变动」。用 `fontTools` 直接量了两套字体的字形墨迹范围（占 `unitsPerEm` 的比例）：
    `static/myvue-icon` 的 `wuliuchaxun` 是 **92.8% × 95.5%**，`uni-icons` 的 star / chat / info 等只有
    **≈ 74.8% × 74.6%**。也就是说**同样 font-size 下，iconfont 的墨迹比 uni-icons 大 27% 左右**——
    根因不是 font-size 没生效（核对过 `my.js` / `my.wxss`，改动确实进了编译产物），而是**两套字体的字形
    留白不同**，「两边都写 44rpx」自然看着还是不一致。
    随后在 30 / 40 / 46rpx 之间目测调了三轮，**最终态**：
    - 物流查询（iconfont，`.func-icon`）：`font-size: 40rpx` —— 用户明确要求这一项**保持不变**，
      只把 uni-icons 那侧调大去追它，问题才收敛。
    - 功能列表 uni-icons（收藏与浏览 ~ 关于我们）：`size="46rpx"`；`.func-icon-box` 48rpx。
    - 专属客服：uni-icons `size="46rpx"`；`.stat-body` 高度 60rpx → 48rpx。
    - 订单宫格（`size="52rpx"` / `.order-icon` 52rpx）用户未提，保持不动。
    这正印证了上面的比例差：iconfont 40rpx 的墨迹 ≈ 38rpx，uni-icons 要 46rpx 才追得上看齐，
    所以「两边写同一个 rpx 值」从来不是答案 —— 关键是各自乘上自己的字形覆盖率。

### 图标方案

「我的」页 10 个图标分两套：

- **第一套（`static/icon/iconfont.css`）**：只有 5 个图标（首页/分类/购物车/购物车实心/我的），
  不足以覆盖本页，未用。
- **第二套（`static/myvue-icon/iconfont.css`）**：用户本轮提供，4 个图标已接入 4 个位置
  （待发货/待收货/售后/物流查询）。
- **`uni-icons` 兜底**：剩余 6 项（待付款/待评价/收藏与浏览/收货地址/意见反馈/关于我们）暂用
  `uni-ui` 的 `uni-icons`（`uni-ui` 已装、easycom 已配、`ProductPopup` 已在用），图标名从
  `uniicons_file_vue.js` 的 `font_class` 清单逐条核对过，确保存在。

**两套 iconfont 的同名冲突与解法**（本项目第二次踩，阶段 16 第一次）：两个 css 各自
`@font-face { font-family: "iconfont" }`，同名时后定义的胜出，另一套字形全变方框。前期试过直接改
`myvue-icon/iconfont.css` 里的字体名，但那是 iconfont.cn 的下载产物，用户一重新下载就前功尽弃
（本轮就是这样炸的）。最终解法是**把覆盖写在自己的文件里**：`App.vue` 的 `<style>` 在两行
`@import` 之后重新声明两个 `@font-face`，用 `myvue-icon` 给第二套单独命名，再补一个同名基类。
`@import` 保持在样式表最前（CSS 规范要求），`@font-face` 在其后，顺序合法。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `pages/my/my.vue` | 用户卡改橙色渐变 + 新增订单卡/功能入口卡/好物推荐；**补 VIP 卡、优惠券·积分卡、专属客服行（三块占位）**；`handleNav` 占位跳转；`.page` 布局修正；图标改 `myvue-icon` + `uni-icons` 兜底 |
| `static/myvue-icon/iconfont.css` | **未改动**（用户重新下载的版本，保持原样；字体名 `iconfont`、基类 `.iconfont` 都不动） |
| `App.vue` | 全局 `@import` 两套 iconfont；并在其后重新注册 `@font-face`（`iconfont` 指回第一套、`myvue-icon` 指向第二套）+ 补 `.myvue-icon` 基类，从根上避开同名覆盖 |
| `task_plan.md` | 新增阶段 20，更新「当前阶段」「下一步」，补「已做决策」（含修正已作废的图标方案） |

### 遗留事项

- `orderTabs` / `funcList` 的 `url` 全为空，点击只弹提示；等订单列表页、地址页等建好后填路由。
- **三块占位都还没接数据**，用户明确说将来要做：
  - VIP 卡：文案「妙选VIP会员」和副标题写死在模板里；权益、开通流程、会员等级全未做。项目当前
    无 VIP 相关表 / 接口。
  - 优惠券 / 积分：`statList` 的 `value` 写死 0，后端无 coupons / points 表，等做的时候换成接口值。
  - 专属客服：和优惠券、积分同排在订单卡下方那三栏里，点进去是「功能开发中」；
    实际客服接入方式（客服会话 / 电话 / 第三方）未定。
- 订单入口的 `type`（0~4）按常见约定占位，未核对后端 `OrderVO.status` 实际取值，对接时需校正。
- 「收货地址」入口尚未接已建好的全局 `AddressPopup` 组件（本轮纯 UI，不做交互）。
- 好物推荐区块依赖 `GET /api/products/recommend`，后端未启动时该区块只有标题、网格为空。
- **图标还缺 6 个**：待付款 / 待评价 / 收藏与浏览 / 收货地址 / 意见反馈 / 关于我们 仍在用
  `uni-icons` 兜底，视觉风格与 `myvue-icon` 那 4 个不一致；用户补上类名后填进 `fontIcon` 即可统一。
- 从 iconfont.cn 重新下载 `myvue-icon` 已**不会再出问题**（改名逻辑在 `App.vue`，不在下载文件里）；
  但要满足两个前提：字体文件名仍是 `iconfont.woff2/woff/ttf`，且图标类名没变。类名变了就得同步改
  `my.vue` 里的 `fontIcon` 值。
- 换图标后若界面没变，是字体文件名没变、开发者工具用了旧缓存，清一次缓存重新编译即可。
- progress.md 缺阶段 19（下单支付链路）的会话记录，待补。

---

## 会话 18：2026-10-10（小程序 C 端免登录）

### 当前状态
- **阶段：** 18 - 小程序 C 端免登录（完成）
- **开始时间：** 2026-10-10

### 背景

登录链路越做越复杂：头像「退出登录后不显示」（实为后端从没存过头像）、手机号登录要真机 + 代码包超 4MB。
用户拍板：**小程序 C 端前后端免登录，电商后台管理系统保持登录**。

### 已执行操作

1. **App.vue onLaunch 静默登录**：启动即 `ensureLogin()`（`uni.login` 换 openid），无授权框、用户无感，失败不阻塞。
2. **my.vue 去登录/退出按钮**：删「微信登录」「退出登录」和登录/未登录两套分支，直接展示头像+昵称；`onShow` 补拉一次 `getProfile()`。
3. **撤销手机号登录（前端）**：去掉 `getPhoneNumber` 按钮、`bindPhone` 调用和手机号展示；后端 `/api/user/phone` 保留未接入。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `App.vue` | onLaunch 加静默登录 `ensureLogin()` |
| `pages/my/my.vue` | 删登录/退出按钮、去 isLogin 分支、onShow 补拉资料 |

### 遗留事项

- 退出登录入口没了，无法在「我的」页切换账号（免登录的自然代价）。
- `static/image/category`（6.3MB）、`static/image/body`（4.9MB）未被引用，撑大代码包致真机调试超 4MB（80051）；用户决定自己处理，尚未删除。
- 手机号后端接口（`POST /api/user/phone`）已实现但前端未接入，保留备用。

---

## 会话 17：2026-10-10（加购链路贯通：服务保障 + 全局登录弹窗 + 飞入动画）

### 当前状态
- **阶段：** 17 - 商品加购链路（完成）
- **开始时间：** 2026-10-10

### 背景

分类页商品弹窗（`ProductPopup`）里那个「加入购物车」按钮此前只是 `$emit('add')`，页面收到后只弹一句
toast 占位，既没有登录判断也没有真请求；同时 `pages/index/index.vue` 里那段被注释掉的
`chooseAvatar` 登录入口（注释写着「等加购时再触发登录」）已经成了死代码。

本轮把这条链路做通：**对齐后台 Mall.vue 补齐服务保障/增值保障 → 抽出全局登录弹窗 → 加购真正落库 → 加飞入动画**。

### 已执行操作

1. **服务保障**：分类页 `ProductPopup.vue` 补四项（正品保证 / 极速退款 / 七天无理由 / 全国联保），
   照后台 `Mall.vue` 的 `.assurance-row` 排布；小程序没有 ant 图标，用 uni-ui 的 `uni-icons`
   （`auth` / `refreshempty` / `calendar` / `location`）对应，橙色 `#FF6700`。
2. **增值保障**：同上补「一年意外保（售价 2%）/ 全面保修（售价 1%）/ 电池无忧（¥20）」三张卡片，
   点卡片切换选中（橙边框 + 浅橙底），计费口径与后台一致。
3. **全局登录弹窗组件**：新建 `components/LoginPopup.vue`，`main.js` 两个分支都注册
   （`Vue.component` / `app.component`）。
   - **打开即静默登录**：`watch.show` 里直接调 `ensureLogin()`，全程无授权框。
   - **头像自动带出**：登录后**总是**再调一次 `getProfile()` 补资料 —— `ensureLogin()` 在已有 token 时
     会直接 return，而重开小程序后 `store.userInfo` 是空的，不补这一次就永远没头像。
   - 头像可选：微信不允许自动弹头像选择框，要换得用户点 `chooseAvatar`。
   - z-index 抬到 1100（盖住商品弹窗的 1000）。
4. **加购接口封装**：新建 `api/cart.js`，`addToCart(productId, quantity)` → `POST /api/cart`（需登录）。
5. **加购逻辑收进 `ProductPopup`**：未登录 `$emit('needLogin')`（商品弹窗不关，数量/保障选择留在原地，
   登录回来再点一次即可）；已登录直接 `addToCart`。`adding` 标志防连点、按钮置灰 + 文案「加入中…」。
6. **飞入购物车动画**：加购成功后商品图飞向 tabBar 购物车图标。两层 `transform` 合成抛物线 ——
   外层 `.fly-wrap` 匀走横向（`linear`），内层 `.fly-img` 用带负 y1 的贝塞尔走纵向 + `scale(0.2)`；
   起点终点由 `uni.createSelectorQuery()` 量 `.cover` 与 `.icon-gouwuche` 的中心点得到。
7. `category.vue` 适配：`@add` → `@needLogin`，删 `handleAddCart` / `doAddCart` 及随之失效的
   `addToCart`、`useUserStore` import 和 `store` data（登录判断已收进组件）。
8. `index.vue` 清理死代码：删掉注释掉的登录入口及其整条方法链（`onChooseAvatar` / `handleLogin` /
   `doLogin` / `saveAvatar` / `avatarUrl` / 相关样式），删掉首页的已登录 `user-box`（判为多余设计），
   import 收窄到只留 `getProductDetail`。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `pages/category/component/ProductPopup.vue` | 补服务保障/增值保障；加购逻辑自包含（登录判断 + 请求 + loading + 飞入动画） |
| `components/LoginPopup.vue` | **新建**：全局登录弹窗，打开即静默登录 + 自动带出头像 |
| `api/cart.js` | **新建**：`addToCart` 封装 `POST /api/cart` |
| `main.js` | 全局注册 `LoginPopup`（Vue2 / Vue3 两分支） |
| `pages/category/category.vue` | 事件改 `@needLogin`，删已下沉到组件的加购方法与失效 import |
| `pages/index/index.vue` | 清理登录相关死代码 + 删已登录 user-box（净减约 155 行） |
| `task_plan.md` / `findings.md` / `progress.md` | 记录本轮 |

### 测试结果

- 三个 `<script>` 块过 `node --check` 语法检查，全部 OK。
- **未在微信开发者工具实机验证**：项目只能用 HBuilderX 编译（`package.json` 无 scripts、无 vite），
  需手动跑；且加购成功路径要求后端 order-service 与网关已启动。

### 遗留事项

- **增值保障没有落库**：`AddCartDTO` 只有 `productId` / `quantity`，`CartServiceImpl.addItem` 里
  `insuranceDetail("[]")` / `insuranceFee(BigDecimal.ZERO)` 是硬编码的。`ProductPopup` 的
  `insuranceDetail` / `insuranceFee` 两个 computed 先留着，等后端补 DTO 字段后直接加进请求体。
- ~~`utils/auth.js` 的 `name: res.name` 恒为 `undefined`（`WxLoginVO` 只有 `token` / `id` / `avatar`，
  没有 `name`）。~~ **已解决（2026-11-14）**：前端不再读 `res.name`，登录只取 `token` / `id` / `avatar`，
  昵称与手机号统一由 `/api/user/profile` 补齐。
- `pages/cart/cart.vue` 仍是占位页，购物车列表未开发（后端 `GET /api/cart` 已有）。

---

## 会话 16：2026-10-06（分类页右侧商品网格）

### 当前状态
- **阶段：** 16 - 分类页右侧商品网格（完成）
- **开始时间：** 2026-10-06

### 背景

用户要求把分类页做完整：左侧分类从后端读取，右侧渲染对应分类的商品（每行两个，图片 + 价格 + 购物车图标），并支持首页分类面板点击跳转到对应分类。

### 已执行操作

1. 分类页左侧分类后端化：`category.vue` 的 `mounted` 调 `getCategories()`，按 `parentId` 拆一/二级分类，一级分类记下 `subIds`（二级分类 id）。
2. 首页跳转联动：`index.vue` 的 `handleCategorySelect` 用 `uni.setStorageSync('selectedCategoryId', item.id)` 传选中分类再 `switchTab`；分类页 `applySelectedCategory()` 读 Storage 定位到对应下标后删除。
3. 右侧商品网格：`mounted` 调 `getProducts()` 拉全量商品，`currentProducts` 按「一级分类 id + 其二级分类 id 集合」过滤；模板由「二级分类宫格」改为「商品网格」（每行两个，图片 + 名称 + 价格 + 购物车图标）。
4. 卡片等高：商品名 1/2 行导致卡片高度不一致，卡片固定 `height` + 名称区固定两行高度、价格行贴底。
5. 清理冗余：删未使用的 `subs` 字段、`currentCategory` 计算属性、`main-title` 标题，更新过时注释。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `pages/category/category.vue` | 左侧分类后端化 + 右侧商品网格 + 卡片等高 + 清理冗余 |
| `pages/index/index.vue` | `handleCategorySelect` 用 Storage 传选中分类 id 再跳转 |
| `task_plan.md` / `findings.md` / `progress.md` | 记录本轮 |

### 遗留事项

- 购物车图标是纯展示，未接「加购」逻辑（加购需登录 + order-service 接口，尚未开发）。
- 部分商品后端 `category_id` 为 NULL（未挂到分类），分类页不显示，属后端数据现状。

---

## 会话 15：2026-10-06（首页下拉刷新动画修复）

### 当前状态
- **阶段：** 15 - 首页下拉刷新（动画修复完成，接后端数据待后续）
- **开始时间：** 2026-10-06

### 背景

用户反馈首页下拉刷新看不到动画。排查发现不是配置问题，而是本地接口返回太快导致转圈动画来不及渲染。

### 已执行操作

1. 定位根因：`onRefresh` 里 `refreshing` 在 `true` 后几十毫秒（本地网关两个接口秒回）就被置回 `false`，
   原生转圈动画没渲染出来就复位，肉眼看不到。
2. 修复：`onRefresh` 的 `Promise.all` 里内联一个 `setTimeout` 延时（至少转满一段时间再收起），保证动画可见。
3. 整合代码：去掉中间变量 `minDelay`，延时 Promise 直接内联进 `Promise.all`。
4. 澄清为何不用微信原生 `onPullDownRefresh`：自定义 fixed 导航栏（z-index 99）会盖住原生转圈
   （原生转圈固定在页面顶部），故用 `scroll-view` 的 `refresher-enabled` 把转圈放到导航栏下方滚动区。
5. 梳理后端待提供支持：轮播图 Banner 接口、热门推荐接口（见 findings.md）。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `pages/index/index.vue` | `onRefresh` 加最短展示延时、去掉多余中间变量 |
| `task_plan.md` / `findings.md` / `progress.md` | 记录本轮 |

### 遗留事项

- 下拉刷新目前只重拉「分类 + 猜你喜欢」；「轮播图」「热门推荐」仍写死，等后端提供 Banner 接口和热门推荐接口后再接进刷新。
- 等 Python 推荐服务（及后端轮播图/热门推荐接口）写好再继续。

---

## 会话 14：2026-10-06（前台分类数据后端化）

### 当前状态
- **阶段：** 11 - 前台分类组件（金刚区数据后端化完成，跳转联动未做）
- **开始时间：** 2026-10-06

### 背景

用户要求首页「金刚区」分类不再写死，改为从后端 `categories` 表拉取，名称用一级分类名称、图片用后端存的图。

### 已执行操作

1. 确认后端接口：`GET /api/categories`（`ProductController.listCategories`）返回 `List<CategoryVO>`
   （id/name/parentId/image/status/...），`parentId == null` 为顶级分类，`image` 为相对路径 `/image/category/xxx.png`。
2. 新建 `api/categories.js`：单独放 `getCategories()`（原来混在 `api/product.js`，分类与商品是两个资源，按资源拆开）。
3. `api/product.js`：删除 `getCategories()`。
4. `pages/index/component/CategoryPanel.vue`：删写死的 6 项，改为 `mounted` 调 `getCategories()`，
   过滤 `parentId == null` 的一级分类；`imgUrl(item)` 给 `image` 补 `BASE_URL`；`:key` 用 `id`。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `api/categories.js` | 新建：`getCategories()` |
| `api/product.js` | 删除 `getCategories()` |
| `pages/index/component/CategoryPanel.vue` | 写死数据改为 `mounted` 调接口，过滤一级分类 + 图片补 BASE_URL |
| `task_plan.md` / `findings.md` / `progress.md` | 记录本轮 |

### 遗留事项

- 分类页 `category.vue` 仍是占位；跳转联动（`switchTab` 不能带 query，需 store/Storage 传选中分类）。
- 原写死的 `static/image/category/*.png` 本地图已无引用，待用户确认是否清理。

---

## 会话 13：2026-10-05（猜你喜欢接后端推荐接口）

### 当前状态
- **阶段：** 14 - 首页「猜你喜欢」模块（完成）
- **开始时间：** 2026-10-05

### 背景

用户告知后端推荐接口已就绪，要求猜你喜欢的商品名称/价格/图片全部从后端拿，不再写死。

### 已执行操作

1. 确认后端接口：`RecommendController` → `GET /api/products/recommend?limit=10`，返回 `Result<List<ProductVO>>`
   （免登录，未登录走销量热门兜底）；`ProductVO` 字段含 id/name/image/price/description，image 为相对路径 `/image/upload/N.webp`。
2. `api/product.js` 新增 `getRecommend(limit)`，默认 limit=10。
3. `components/Guess.vue` 删掉写死的 products 数组，改为 `mounted` 调 `getRecommend(10)` 填充；
   新增 `imgUrl(item)` 给 image 补 `BASE_URL`（无图回退本地占位图）；名称/价格/图片全部来自后端。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `api/product.js` | 新增 `getRecommend(limit)` |
| `components/Guess.vue` | 写死数据改为 `mounted` 调接口，图片补 BASE_URL |
| `task_plan.md` / `findings.md` / `progress.md` | 记录本轮 |

### 遗留事项

- 端到端未实测：需后端 gateway + product-service + Python 推荐服务都起来后验证真实商品拉取。
- 购物车页 `main.js` 注释写「猜你喜欢用于首页和购物车」，但购物车页仍占位，未挂 `<Guess>`。

---

## 会话 12：2026-10-05（首页「猜你喜欢」模块前端 UI）

### 当前状态
- **阶段：** 14 - 首页「猜你喜欢」模块（前端 UI 完成，写死商品占位，接口未接）
- **开始时间：** 2026-10-05

### 背景

用户要求完善「猜你喜欢」模块（已在 `main.js` 全局注册为 `<Guess>`）。Python 推荐服务还没写，
先写死几个商品占位把 UI 跑起来，后续再换接口。

### 已执行操作

1. 重建 `components/Guess.vue`：原文件模板是坏的（多根节点、`<scroll scroll-y>` 拼写错误），
   重写为「标题 + 两列商品卡片网格（图/名/价）」，写死 8 个占位商品，字段对齐后端 `ProductVO`
   （id/name/image/price），图片用本地 `/static/image/upload/N.webp`，点图 `$emit('select', id)`。
2. `pages/index/index.vue`：在热门推荐（HotPanel）之后插入 `<Guess @select="handleProductSelect" />`，
   复用现有 `handleProductSelect`（拉详情弹 ProductPopup），无需 import（全局注册）。
3. 修图片底部圆角：卡片 `overflow:hidden` + `border-radius` 只裁卡片外边界（图片顶角），
   图片底边在卡片内部不贴底边，给 `.goods-img` 单独加 `border-radius: 0 0 16rpx 16rpx`。
4. 修标题居中：`.guess-header` 是 flex 容器，`text-align: center` 无效，改 `justify-content: center`。
5. 标题装饰：左右渐变线 + 两侧小圆点（纯 CSS，品牌橙 #FF6700），未引入图片资源。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `components/Guess.vue` | 重写：标题 + 两列商品卡片网格，写死 8 个商品占位 |
| `pages/index/index.vue` | HotPanel 之后插入 `<Guess>`，复用 handleProductSelect |
| `task_plan.md` / `findings.md` / `progress.md` | 记录本轮 |

### 遗留事项

- 接口 `getRecommend(limit)` 未接（Python 推荐服务未落地）；后续替换组件 `products` 数组即可。
- 图片目前用本地 `/static/image/upload/N.webp`，换接口后要按 `ProductPopup.cover` 补 `BASE_URL`。
- 购物车页 `main.js` 注释写「猜你喜欢用于首页和购物车」，但购物车页仍占位，未挂 `<Guess>`。

---

## 会话 11：2026-10-04（重新探索后端三个 md，同步推荐服务进展）

### 当前状态
- **阶段：** 14 - 首页「猜你喜欢」模块（规划中，前端未动）
- **开始时间：** 2026-10-04

### 背景

用户要求重新探索后端 `e-commerce-boot` 三个 md，把推荐服务（猜你喜欢）的最新进展同步到前端三个规划文件。

### 已执行操作

1. 重读后端 `task_plan.md` 阶段 9：Python 服务 + Java 桥接接口 `GET /api/products/recommend`
   均已打勾，状态从「Java 桥接待落地」变为「Python 服务 + Java 桥接已端到端跑通，仅剩前端 UI / 订单表迁 MySQL」。
2. 重读后端 `findings.md`「推荐服务（Python）方案」：新增「实现状态」节 —— `recommend-service/`
   已落地 9 个文件（main/matrix/recommender/datasource），数据源读原 SQLite（有效下单 22 条、
   在售商品 30 个、3 个用户）；冷启动口径定案为「未下单按销量降序推荐全量在售商品，无销量按 id 补位」。
3. 同步前端三个规划文件：阶段 14 状态改为「后端已跑通，仅剩前端 UI」；findings 更新冷启动口径与数据现状。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `task_plan.md` / `findings.md` / `progress.md` | 同步后端推荐服务最新进展（Java 桥接已跑通 + 冷启动口径），无代码改动 |

### 遗留事项

- 前端「猜你喜欢」区块与接口接入未动工（阶段 14 pending）；后端已就绪，前端可开始。

---

## 会话 10：2026-10-04（首页「猜你喜欢」模块规划）

### 当前状态
- **阶段：** 14 - 首页「猜你喜欢」模块（规划中，前端未动）
- **开始时间：** 2026-10-04

### 背景

后端已建 Python `recommend-service`（9009）推荐服务骨架并实测跑通，用户要求小程序首页新增
「猜你喜欢」模块（按订单下单记录推商品）。本轮先探索后端三个 md 的推荐服务部分，结合后端接口契约
更新前端三个规划文件，代码不动。

### 已执行操作

1. 读后端 `findings.md`「推荐服务（Python）方案」：推荐算法用 Python 写（Item-Item 商品共现
   「买了又买」+ 销量热门兜底），数据源 `orders`/`order_items`（先 SQLite 后 MySQL），
   冷启动未登录走热门；Java 桥接放 product-service，复用网关 `/api/products/**` 路由零改动。
2. 读后端 `task_plan.md` 阶段 9：Python 服务 + 共现算法已完成；Java 桥接接口 `/api/products/recommend`、
   前端区块、订单表迁 MySQL 均未做。
3. 核对前端现状：`api/product.js`（getProducts/getProductDetail/getCategories）、
   `pages/index/index.vue`（已接 HotPanel + ProductPopup）、`ProductPopup` 卡片字段
   （id/name/image/price/description，image 相对路径补 BASE_URL）。
4. 明确职责区分：推荐用 Python（非 Spring AI）；Spring AI 只用于聊天机器人 ai-agent-service。
5. 更新前端三个规划文件（本文件 / `task_plan.md` / `findings.md`），新增阶段 14 规划。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `task_plan.md` / `findings.md` / `progress.md` | 新增阶段 14 规划 + 猜你喜欢接口契约，无代码改动 |

### 测试结果

| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 后端推荐服务 | Python 可跑通 | 后端已实测（/health /recommend /train OK） | 通过（后端侧） |
| 前端猜你喜欢区块 | — | 未开始，本轮只做规划 | 未开始 |

### 遗留事项

- 前端「猜你喜欢」区块与接口接入未动工（阶段 14 pending）。
- 依赖后端 Java 桥接接口 `GET /api/products/recommend` 落地（后端 task_plan 阶段 9 未做）。
- 「猜你喜欢」与「热门推荐（HotPanel）」是两个模块：前者按订单记录个性化，后者写死 id 的栏目占位。

---

## 会话 9：2026-10-03（悬浮式 AI 导购 Agent 前端规划）

### 当前状态
- **阶段：** 13 - 悬浮式 AI 导购 Agent（规划中，前端未动）
- **开始时间：** 2026-10-03

### 背景

后端已建 ai-agent-service（9008）导购 Agent 骨架并实测跑通，用户要求小程序侧新增「悬浮式导购 Agent」。
本轮先探索后端三个 md 的 Agent 部分，结合后端接口契约更新前端三个规划文件，代码不动。

### 已执行操作

1. 读后端 `findings.md`「AI 导购 Agent 服务」：定位完整 Agent（非 LLM 聊天壳），
   Graph 工作流 / RAG / MCP / Function Calling 四大能力，Spring AI 2.0.1 + 硅基流动 DeepSeek。
2. 读后端 `task_plan.md` 阶段 8 / `progress.md` 阶段 I：骨架已建、启动 / 对话 / Tool Calling / SSE / MCP 实测通过；
   真实推荐待 product-service 启动（当前幻觉商品）、下单待 order-service。
3. 读后端源码确认前端接口契约：`AiChatController` → `POST /api/agent/chat`（`Result<ChatReplyVO{reply}>`）、
   `POST /api/agent/chat/stream`（SSE `Flux<String>`）；入参 `ChatRequestDTO{message}`；
   网关已加 `/api/agent/**` 路由到 ai-agent-service，白名单已放行（免登录）。
4. 更新前端三个规划文件（本文件 / `task_plan.md` / `findings.md`），新增阶段 13 规划。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `task_plan.md` / `findings.md` / `progress.md` | 新增阶段 13 规划 + Agent 接口契约，无代码改动 |

### 测试结果

| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 后端对话接口 | 可调通 | 后端已实测（对话 / SSE / MCP 通过） | 通过（后端侧） |
| 前端悬浮 Agent | — | 未开始，本轮只做规划 | 未开始 |

### 遗留事项

- 前端悬浮组件与接口接入未动工（阶段 13 pending）。
- 小程序端 SSE 流式支持待真机验证（`enableChunked` + `onChunkReceived`），不确定时降级非流式 `/chat`。
- 真实商品推荐依赖 product-service 启动（当前后端返回 DeepSeek 幻觉商品）；下单闭环依赖 order-service。

---

## 会话 8：2026-10-03（商品预览弹窗 + uni-popup）

### 当前状态
- **阶段：** 12 - 商品预览弹窗（已完成）
- **开始时间：** 2026-10-03

### 背景

首页「热门推荐」面板点商品图要弹预览。先做了自写弹窗组件，随后发现图片不显示（后端 image 相对路径问题），
修完后又按用户要求把自写弹窗换成 uni-ui 的 `uni-popup`。

### 已执行操作

1. 新建 `pages/index/component/HotPanel.vue`：热门推荐面板（4 色块 × 左右商品位，写死 id），点图 `$emit('select', id)`。
2. 新建 `pages/index/component/ProductPopup.vue`：商品预览弹窗（图 / 名 / 价 / 描述 / 关闭），props `show`/`product`、`$emit('close')`。
3. `pages/index/index.vue` 接入：`handleProductSelect(id)` → `getProductDetail(id)` → `showProductPopup = true`；弹窗显隐与商品数据都留在页面 data。
4. 排查「弹窗看不到图片」：后端 `products.image` 存 `/image/upload/N.webp`（网关映射到 upload/image/），
   小程序把开头的 `/` 当包内路径 → 空白；`cover` 补 `BASE_URL` 前缀解决。
5. 按用户要求把弹窗外壳换成 `uni-popup`（easycom 免注册）：`watch: show` 桥接 `ref.open()/close()`，
   内部 `opened` 标记防事件循环；z-index 99 → 1000 压过自定义 TabBar（`class="product-popup"` 双类名非 scoped 样式）。
6. 排查发现 `swiper_preview.png`（项目根目录）是死文件：600×600 四宫格素材合图，无代码引用、不在 `static/` 下、
   不会打进小程序包，却被 git 跟踪（369KB），待用户确认删除。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `pages/index/component/HotPanel.vue` | 新建：热门推荐面板，点图抛 id |
| `pages/index/component/ProductPopup.vue` | 新建：商品预览弹窗，后用 uni-popup 重写外壳 |
| `pages/index/index.vue` | 接 HotPanel + ProductPopup，`handleProductSelect` 拉详情弹窗 |
| `task_plan.md` / `findings.md` / `progress.md` | 记录本轮 |

### 测试结果

| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 点热门推荐商品图弹预览 | 弹窗显示商品图 / 名 / 价 | 待微信开发者工具验证 | 待验证 |
| 弹窗内图片显示 | 显示后端商品图 | 已修（补 BASE_URL），待编译验证 | 待验证 |
| 点遮罩 / 关闭按钮 | 都能关闭弹窗 | 待验证 | 待验证 |
| 弹窗盖住底部导航栏 | 遮罩在 TabBar 之上 | 已修（z-index 1000），待验证 | 待验证 |

### 遗留事项

- `swiper_preview.png` 死文件待删（`git rm`，用户尚未确认）。
- `HotPanel` 商品位写死 id、用本地 `/static/image/upload/N.webp` 占位，后续接 `getProducts` 换后端 `image`。
- 金刚区数据后端化、分类页跳转联动仍属阶段 11 遗留，未做。

---

## 会话 7：2026-10-02（前台分类组件）

### 当前状态
- **阶段：** 11 - 前台分类组件（进行中，展示层已完成）
- **开始时间：** 2026-10-02

### 背景

首页需要「金刚区」分类入口。先做前台分类面板 `CategoryPanel` 的展示层，数据后端化与跳转联动留待后续。

### 已执行操作

1. 新建 `components/CategoryPanel.vue`：6 分类（手机/笔记本/平板/相机/手表/耳机）宫格，3 列 × 2 行。
2. 图标方案两版：先试 iconfont 字体（`static/category/iconfont.css`，改 `font-family` 避开全局同名冲突），
   用户嫌字体图标单色难看，改用 png（`static/image/category/*.png` 6 张），
   `<image mode="aspectFit">` + `border-radius: 30rpx` 圆角。
3. `pages/index/index.vue` 局部注册 `CategoryPanel`（`import` + `components`），插到轮播图下方，
   加 `handleCategorySelect` 用 `switchTab` 跳分类页。
4. 排查两个编译坑：`@import` 相对路径（`../../` → `../`）；微信开发者工具「过滤无依赖文件」
   误删 uni-app 懒加载组件（报 `Component is not found`）。
5. 确认 `main.js` 的 `_component` 已改为 `component`（会话 6 遗留解决），`CustomSwiper` 全局注册正确。
6. 讨论前台分类后续三步：数据后端化 / 分类页 / 跳转联动；用户先聚焦"占位"（骨架屏方案已给出，未落地）。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `components/CategoryPanel.vue` | 新建：6 分类宫格，png 图标 + 圆角，`$emit('select')` |
| `pages/index/index.vue` | 局部注册 CategoryPanel + 模板插入 + `handleCategorySelect` |
| `main.js` | 全局注册 CustomSwiper（`app.component`，会话 6 遗留已解决） |
| `static/image/category/*.png` | 6 张分类图标 |
| `task_plan.md` / `findings.md` / `progress.md` | 记录本轮 |

### 测试结果

| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 首页显示分类面板 | 6 个分类图标 + 中文名 | 待微信开发者工具验证 | 待验证 |

### 遗留事项

- 金刚区数据后端化：接 `getCategories()` + 建 分类 id→png 映射（当前写死 6 项）。
- 分类页 `category.vue` 仍是占位；跳转联动（`switchTab` 不能带 query，需 store/Storage 传选中分类）。
- 骨架屏占位方案已讨论，未落地。

---

## 会话 6：2026-10-02（轮播图组件改名编译报错）

### 当前状态
- **阶段：** 10 - 轮播图组件改名编译报错（进行中）
- **开始时间：** 2026-10-02

### 背景

把首页轮播图抽成全局组件 `CustomSwiper`（原 `Swiper.vue` 改名），在 `main.js` 全局注册、
`pages/index/index.vue` 用 `<CustomSwiper />` 后，微信开发者工具先报编译错、再报运行错。

### 已执行操作

1. 定位第一个错误 `ENOENT: components/Swiper.wxml`：项目源码已无 `Swiper` 引用，但
   `unpackage/dist/dev/.sourcemap/mp-weixin/components/` 残留旧 `Swiper.js.map`
   （记录 `components/Swiper.vue`）—— 组件改名后编译缓存未清，增量编译仍去找旧组件。
   尝试删 `unpackage/dist` 被拒（`Device or resource busy`，微信开发者工具占用），
   结论为需关工具后清理重编译。
2. 定位第二个错误 `app._component is not a function`：`main.js:37` Vue3 分支误写
   `app._component('CustomSwiper', CustomSwiper)`，Vue3 正确 API 是 `app.component()`。
3. 用户要求更新三个规划文件（本文件 / `task_plan.md` / `findings.md`）。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `main.js` | 第 37 行 `app._component` 应改 `app.component`（已定位，**代码未改**，待应用） |
| `task_plan.md` / `findings.md` / `progress.md` | 记录本轮两个错误与解决方案 |

### 测试结果

| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 组件改名后编译 | 不再报 `Swiper.wxml` | 需清缓存重编译 | 待验证 |
| 首页轮播图运行 | 无 `_component` 报错 | `main.js:37` 修复后待验证 | 待验证 |

### 遗留事项

- ~~`main.js:37` 的 `app._component` → `app.component` 修复尚未应用~~ **已解决**（后续已改为 `app.component`）。
- 需关闭微信开发者工具，删除 `unpackage/dist` 后重新编译。

---

## 会话 5：2026-10-01（顶部导航栏机型适配）

### 当前状态
- **阶段：** 9 - 顶部导航栏机型适配（已完成）
- **开始时间：** 2026-10-01

### 背景

用户反馈自定义顶部导航栏 `TopBar.vue` 切换机型（尤其华为）时出现两个问题：搜索框贴边、搜索框被上下裁切。先澄清 `safeAreaInsets` 与本组件的关系，再定位到胶囊按钮反推逻辑在部分机型的数据异常。

### 已执行操作

1. 澄清 `safeAreaInsets`：顶部安全区已由 `statusBarHeight` 覆盖，底部由 `TabBar.vue` 的
   `env(safe-area-inset-bottom)` 覆盖，本项目无需再引入。
2. 定位「搜索框贴边」：`rightSafeArea = windowWidth - menuRect.left` 使搜索框右边缘精确等于
   胶囊左边缘（间距 0），华为胶囊偏右时贴边更明显。
3. 修复贴边：`rightSafeArea = sysInfo.windowWidth - menuRect.left + 8`（补 8px 间距）。
4. 定位「搜索框上下裁切」：`navContentHeight = (menuRect.top - statusBarHeight) * 2 + menuRect.height`
   反推依赖 `menuRect.top`，华为部分机型返回偏小，内容区高度 < 搜索框（60rpx=30px），上下被裁切。
5. 修复裁切：`navContentHeight = Math.max(44, ...)`，设 44px 下限（与 data 默认值一致）。
6. 更新三个规划文件。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `pages/index/component/TopBar.vue` | `rightSafeArea` 加 8px 间距；`navContentHeight` 加 `Math.max(44, ...)` 下限；补注释 |
| `task_plan.md` / `findings.md` / `progress.md` | 按本轮改动更新 |

### 测试结果

| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 华为机型搜索框与胶囊有间距 | 不贴边 | 待验证 | 待用户验证 |
| 华为机型搜索框完整显示 | 不被裁切 | 待验证 | 待用户验证 |

### 遗留事项

- 需在微信开发者工具切华为机型实测确认。
- 「四周留白」问题用户暂缓，未定位。

---

## 会话 4：2026-09-30（前端对接微服务）

### 当前状态
- **阶段：** 8 - 前端对接微服务（已完成）
- **开始时间：** 2026-09-30

### 背景

用户告知「后端已启用微服务，不同模块有不同端口」，要求前端对接，避免前端项目无法启动。

### 已执行操作

1. 探索后端 `e-commerce-boot`：已拆成 Maven 多模块 8 服务（gateway + 7 业务），端口规划
   gateway 8080 / auth 9001 / shop 9002 / product 9003 / order 9004 / aftersale 9005 / user 9006 / file 9007；
   其中仅 user-service 承载小程序登录/用户资料业务，其余为占位空壳。
2. 确认后端 `task_plan.md` 的前端对接约定：接口路径 `/api/**` 不变，前端 baseURL 指向网关 8080。
3. 核对前端 `utils/server-api.js`：`BASE_URL = 'http://127.0.0.1:8080'` 已指向网关，
   四个接口（`/api/wxlogin`、`/api/user/profile`、`/api/user/avatar`、`/api/user/name`）路径与
   user-service 的 `WxLoginController` / `UserController` 完全一致 → **前端无需改端口，已对接好**。
4. **`utils/server-api.js` 补注释**：在 `BASE_URL` 上方固化「统一走网关、各微服务端口对前端透明」
   的约定与端口规划表，方便后续对接商品/订单等模块时照此扩展（无逻辑改动）。
5. 验证 gateway 路由前缀：解压本地 maven 仓库 `spring-cloud-gateway-server-webflux-5.0.3.jar`，
   其 `spring-configuration-metadata.json` 含 `spring.cloud.gateway.server.webflux.routes`，
   → 后端 `gateway/application.yml` 的 `server.webflux.routes` 配置合法，路由能生效。
6. 顺带确认 `utils/` 已纳入 git（`git ls-files utils/` 含 `server-api.js`、`token.js`），
   早先记录的「utils 未跟踪」遗留已消除。
7. 更新 `task_plan.md` / `findings.md` / `progress.md`。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `utils/server-api.js` | `BASE_URL` 上方补注释（端口规划表 + 统一走网关约定），无逻辑改动 |
| `task_plan.md` / `findings.md` / `progress.md` | 按本轮改动更新 |

### 测试结果

| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 前端 `BASE_URL` vs 网关端口 | 8080 | `http://127.0.0.1:8080` 匹配 | 通过 |
| 前端四接口路径 vs user-service Controller | 一致 | 全部匹配 | 通过 |
| gateway 路由前缀是否合法 | `spring.cloud.gateway.server.webflux.routes` | gateway 5.0.3 元数据含该属性 | 通过 |
| 端到端（前端→网关→user-service） | 可跑通 | **未执行**（依赖后端 Nacos + user-service + gateway 启动） | 待用户验证 |

### 遗留事项

- **端到端未实测**：后端需先起 Nacos（`docker-compose.yml`）+ user-service（依赖 MySQL）+ gateway，
  前端才能从 8080 走通登录链路。前端侧无需再改。
- `index.vue` 剩余问题与 `avatar-upload.md` 接口表未加 `/api/user/name` 等旧遗留不变。

---

## 会话 3：2026-09-30（昵称前端接入）

### 当前状态
- **阶段：** 7 - 昵称前端接入（已完成）
- **开始时间：** 2026-09-30

### 背景

用户告知「后端已经把微信小程序登录的存储用户昵称的工作搞定了」，要求前端跟上，并更新三个规划文件。

### 已执行操作

1. 读后端工作区 diff（`git diff` + `git status`），确认本轮后端改动：
   - 新增 `src/main/java/ecommerce/user/dto/UpdateNameDTO.java`（未跟踪新文件）；
   - `UserController` 新增 `@PostMapping("/name")` → `updateName(@RequestBody UpdateNameDTO)`；
   - `UserService` / `UserServiceImpl` 新增 `updateName(Long userId, String name)`；
   - `UserMapper` + `UserMapper.xml` 新增 `updateName`（`set name = #{name}, update_time = now()`）；
   - `docs/openapi.yaml` 新增 `/api/user/name` 路径与 `UpdateNameDTO` schema。
2. 读前端 `pages/index/index.vue`、`utils/server-api.js`、`utils/token.js` 确认现状。
3. **`utils/server-api.js` 新增 `updateName(name)`**：`POST /api/user/name`，body `{ name }`，
   复用既有 `request` 封装（自动带 `Bearer {token}` + 统一错误 toast）。
4. **`pages/index/index.vue`**：
   - 模板：未登录分支、已登录分支各加一个 `<input type="nickname">`（微信昵称填写能力）；
   - `data` 加 `nickname` 字段，同时作为两个输入框的值来源；
   - `onNicknameBlur`：trim 后空值/无改动直接返回；已登录调 `saveName()`，未登录只暂存；
   - `saveName()`：调 `updateName`，成功回写 `nickname` / `userInfo.name` / Storage；
   - `fetchProfile()`：回填 `nickname`（后端有值优先），并在「本地填过且与后端不同」时提交；
   - `handleLogout()`：补 `this.nickname = ''`；
   - 样式：加 `.nickname-input`。
5. 全项目 grep `nickname|updateName|/api/user/name` 复查，无遗漏调用点。
6. 更新 `task_plan.md` / `findings.md` / `progress.md`。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `utils/server-api.js` | 新增导出 `updateName(name)`（约 8 行） |
| `pages/index/index.vue` | 模板 2 处输入框、script 4 处（import / data / 3 个方法 / fetchProfile / logout）、样式 1 条 |
| `task_plan.md` / `findings.md` / `progress.md` | 按本轮改动更新 |

### 测试结果

| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 前端路径 vs 后端 `@PostMapping("/name")` | 一致 | `POST /api/user/name` 匹配 | 通过 |
| 请求体字段 vs `UpdateNameDTO` | 一致 | 均为 `name`，`request` 默认 JSON content-type，`@RequestBody` 可绑定 | 通过 |
| 返回值处理 vs `ResultString` | `data` 为 trim 后的昵称 | `request` 返回 `res.data.data`，前端回写 `userInfo.name` | 通过 |
| 长度/空值校验是否前端也做了 | 不必做 | 空值前端直接跳过不提交；超长由后端 400，`request` 统一 toast 提示 | 通过 |
| 端到端实际调用（真机/模拟器） | — | **未执行**（本轮只做静态对齐，未跑开发者工具） | 待用户验证 |

### 遗留事项

- **端到端未实测**：需在微信开发者工具里走一遍「填昵称 → 选头像 → 登录 → 昵称入库」。
- `utils/` 目录仍未纳入 git（`utils/server-api.js`、`utils/token.js` 未跟踪），待用户确认是否提交。
- 后端 `.gitignore` 缺 `upload/`，头像文件会被 git 跟踪（后端侧问题）。
- `index.vue` 剩余问题：已登录无换头像入口、`setStorageSync('userInfo')` 死代码等（见 `findings.md`）。
- `avatar-upload.md` 的接口表**未**同步加 `/api/user/name`（用户本轮只要求更新三个规划文件）。

---

## 会话：2026-09-29

### 当前状态
- **阶段：** 6 - 收尾（阶段 1–5 已完成）
- **开始时间：** 2026-09-29

### 已执行操作

1. 定位前端登录/头像相关文件，读 `utils/token.js`、`utils/server-api.js`、`pages/index/index.vue`、`App.vue`。
2. 全项目 grep `getProfile`，确认定义在 `utils/server-api.js:45`，被 `pages/index/index.vue:29` 导入。
3. 读后端接口文档 `e-commerce-boot/docs/openapi.yaml`。
4. 对照后端源码验证：`UserController.java`（`@RequestMapping("/api/user")`、`@GetMapping("/profile")`、
   `@PostMapping("/avatar")`）、`WxLoginController.java`、`UserServiceImpl.java`、`WebMvcConfig.java`、
   `UserMapper.java` + `UserMapper.xml`、`application.yml`。
5. 确认后端**没有 `member` 包**（`ls src/main/java/ecommerce/` → `user` / `common` / `configuration` /
   `exception` / `interceptor` / `properties` / `utils`）。
6. **修正 `utils/server-api.js` 两处路径**：`/api/member/profile` → `/api/user/profile`；
   `/api/member/avatar` → `/api/user/avatar`。
7. 全项目复查，确认代码中已无 `/api/member`（仅 `avatar-upload.md` 残留两处）。
8. **重写 `avatar-upload.md`**：新增接口对照表，更正类名/包名（`Member*` → `User*`），
   把「待实施方案」改写为「实现要点」，补充文档更正记录与剩余待办。
9. 检查 `pages/index/index.vue`，列出 8 条问题。
10. 用户回复「能跑通就行了」，8 条全部不修。
11. 生成 `task_plan.md` / `findings.md` / `progress.md`。

### 改动的文件

| 文件 | 改动 |
|------|------|
| `utils/server-api.js` | 接口路径 2 处：`/api/member/*` → `/api/user/*` |
| `avatar-upload.md` | 按实际接口/包名/实现重写（此前为过时推演） |
| `task_plan.md` / `findings.md` / `progress.md` | 新建 |

`pages/index/index.vue` 本轮**未改动**（会话开始时 git 已标记为 modified，是此前的工作）。

### 测试结果

| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 前端请求路径 vs 后端 `@RequestMapping` 比对 | 完全一致 | `/api/user/profile`、`/api/user/avatar` 均匹配 | 通过 |
| 后端返回字段 vs 前端取值比对 | 字段名一致 | `id` / `name` / `avatar` 均存在 | 通过 |
| 上传失败路径的提示链路 | 用户能看到提示 | 401/403/413 后端均返回 `Result` JSON，`uploadFile` 走 success → `data.success=false` → `reject` → toast | 通过 |
| 端到端实际调用（真机/模拟器） | — | **已执行**，见下方「会话 2」 | 通过 |
| 头像上传落盘 | 生成 `{userId}_{时间戳}.{后缀}` 文件 | 实际得到 `4_1790670510773.jpg`，与 `UserServiceImpl:132` 命名规则一致 | 通过 |

### 错误

| 错误 | 解决方案 |
|------|---------|
| 前端路径 `/api/member/*` 会 404 | 读 `openapi.yaml` 后改为 `/api/user/*` |
| 照抄 `avatar-upload.md` 的过时结论（该文件按 `MemberController` 推演） | 按后端源码与文档重写该文件，并加更正记录 |
| 执行 `init-session.sh --help` 时误将 `--help` 当作任务名初始化了三个文件 | 无实际影响，已直接覆写为真实内容 |

### 遗留事项

- `utils/` 目录仍未纳入 git（`utils/server-api.js`、`utils/token.js` 未跟踪），待用户确认是否提交。
- 后端 `.gitignore` 缺 `upload/`，头像文件会被 git 跟踪。
- `index.vue` 的 8 条问题（见 `findings.md`）用户决定不修。

---

## 会话 2：2026-09-29（403 排查 + 端到端跑通）

### 现象

微信开发者工具控制台报：

```
GET http://127.0.0.1:8080/api/user/profile 403
```

### 排查

1. 读 `JwtInterceptor.java`，确认 403 只在 `:65-68` 的**身份类型比对**失败时抛出；
   缺头 / 签名错 / 过期走的是 `:50` / `:59`，抛的是 **401**。
   → 403 说明 token 签名和过期校验**都通过了**，只是 `type` 声明不对。
2. 查后端 git 历史，`JwtUtil` 的 `TYPE_*` 常量改过三次：
   `"wxuser"`（`4a1022c`，0921）→ `"member"`（`c1ae944`，0926）→ `"user"`（`ee90659`，0927 起）。
3. 排除「后端跑的是旧版本」：旧版没有 `/api/user/profile` 映射，那会是 404；能返回 403
   说明 handler 存在、拦截器已放行到类型检查，路径是对的。

### 结论

本地 Storage 里的 token 是 0926 前后旧后端签发的（`type: "member"`），
而当前后端要求 `"user"`。JWT_SECRET 未变、7 天未到期，所以签名照旧合法但类型卡死。

### 解决

清微信开发者工具 Storage 里的 `token` 项，重新登录。用户确认已跑通。

（`index.vue:101-107` 的 catch 里本就有 `clearToken()`，重启小程序通常能自愈。）

### 端到端验证结果

- 登录链路：**跑通**
- 头像上传：**跑通**，后端实际生成 `4_1790670510773.jpg`，命名规则与 `UserServiceImpl:132` 一致
- 图片展示：见下方遗留事项

### 遗留事项（新增）

- **`<image>` 加载 HTTP 图片的警告**：`http://127.0.0.1:8080/upload/avatar/...` 不被小程序支持。
  本地开发**不用管**（127.0.0.1 不可能配 HTTPS 证书），开发者工具勾选
  「不校验合法域名、web-view、TLS 版本以及 HTTPS 证书」即可正常显示。
  上线时才需要处理，而 `UserController.fullUrl` 是按请求的 `Host` 头拼 URL 的，
  后端换 HTTPS 域名后返回的地址自动就是 https，**前后端都不用改代码**。

---

## 会话 3：2026-10-11（购物车全链路 + 增值保障 + 角标）

### 后端（order-service）

- 购物车 `subTotal` 只读派生：`CartVO` + `calcSubTotal = (price + insuranceFee) × quantity`
- 增值保障接入：`AddCartDTO` 加 `insuranceDetail`/`insuranceFee`，`addItem` 快照
- 库存校验：`Cart`/`CartVO` 加 `stock`，`CartMapper.listByUser` join products 带实时库存，`addItem`/`updateQuantity` 超库存抛 400
- 商品副标题重写：`products.sub_title` 全部「商品名+卖点」≤35 字（`docs/sql/product-subtitle.sql`，已执行）

### 前端

- `api/cart.js` 补全 getCart/updateQuantity/removeItem/clearCart，`addToCart` 加保险参数
- `pages/cart/cart.vue` 购物车页完整实现（列表/单选全选/数量/删除/批量删除/结算占位/空态/猜你喜欢/增值保障/小计/库存上限）
- `ProductPopup` 抽成全局组件 `components/ProductPopup.vue`（首页+分类页共用），删两个旧局部版本
- `QuantityStepper` 全局数量步进器（可输入 + 加减 + max 库存上限）
- 购物车角标：`store/cart.js` + TabBar 角标 + App onLaunch 初始化 + 加购/增删改同步
- 加购弹窗显示副标题 subTitle
- 加购弹窗右上角加叉叉关闭按钮（`handleClose`）；底部改双按钮并排：加入购物车用图标（`icon-gouwuche1`）、新增「立即购买」文字按钮（`handleBuy` 占位，下单链路后续接）
- 收货地址模块：`api/address.js`（listAddress/addAddress/updateAddress/deleteAddress）+ 全局组件 `components/AddressPopup.vue`（底部弹窗，列表 + 新增/编辑，省市区用微信原生 picker mode="region"，设默认用 uview-plus 的 u-switch）；购物车「去结算」触发地址弹窗
- 引入 uview-plus（Vue3 UI 库）：main.js app.use + App.vue/uni.scss 引样式 + pages.json easycom 配 `^u-(.*)`；踩坑：官方 uview-ui 2.0.38 是 Vue2 写法（Vue.filter），Vue3 报错，换 uview-plus

### 踩坑

- 删/移文件（ProductPopup 全局化）后 HBuilderX 增量编译残留旧产物，报 `ENOENT: .../ProductPopup.wxml`，需删 `unpackage/dist` 全量重编译
- 小程序原生 `input` 层级高于普通 view，步进器按钮热区用「负 margin + padding」扩展会被 input 挡住点不到；最终回退紧凑 52rpx 按钮 + 增大符号反馈

---

## 会话 19：2026-10-14（下单支付链路）

### 当前状态
- **阶段：** 19 - 下单支付链路（完成）
- **开始时间：** 2026-10-14

### 背景

后端 order-service 的下单/支付接口已就绪，小程序侧「去结算 → 下单 → 支付」此前只有占位
（购物车「去结算」只弹地址、商品弹窗「立即购买」/「猜你喜欢」都是 toast 占位）。本轮贯通下单链路。

### 已执行操作

1. 新建 `api/order.js`：下单/列表/详情/支付/取消 + `genIdempotencyKey`（幂等键，对齐后端下单幂等）。
2. 新建 `pages/order/confirm.vue` 下单确认页：地址卡（默认地址 + 弹 AddressPopup 切换）、商品列表、
   配送/买家留言、金额汇总、底部按钮（当时文案是「我已支付」，会话 20 已改为「提交订单」）。
3. `pages.json` 注册 `pages/order/confirm`。
4. `store/cart.js` 加 `checkoutItems` + `setCheckoutItems`：购物车「去结算」/商品「立即购买」写入待结算商品。
5. `cart.vue`：「去结算」改为存选中商品 → 跳确认页；「猜你喜欢」点商品改为拉详情弹 ProductPopup（复用加购/下单）。
6. `ProductPopup.vue`：「立即购买」从占位改为组装单商品 → 跳确认页；「电池无忧」按类别过滤（耳机/音箱不显示）。
7. 底部按钮逻辑：`createOrder` → `payOrder('wechat')` → `clearCart` → 跳回，一步完成；清购物车失败不影响支付成功提示。
   （此处 `payOrder` 第一版是写死 `'wechat'`；会话 20 已改为从 `payList[activePay].code` 取，并新增支付方式卡片。）

### 改动的文件

| 文件 | 改动 |
|---|---|
| `api/order.js` | 新建：下单/支付等接口 + 幂等键生成 |
| `pages/order/confirm.vue` | 新建：下单确认页 |
| `pages.json` | 注册 confirm 页 |
| `store/cart.js` | 加 checkoutItems + setCheckoutItems |
| `pages/cart/cart.vue` | 去结算跳确认页 + 猜你喜欢弹 ProductPopup |
| `components/ProductPopup.vue` | 立即购买接下单 + 电池无忧按类别过滤 |

### 遗留事项

- 订单列表页未做（支付成功先跳回购物车）。
- 暂未接微信支付（`uni.requestPayment` 未接）；`payType` 最初写死 `'wechat'`，**会话 20 已改为从页面选中的支付方式取**，仍是 `wechat` 一项。
- 「电池无忧」过滤按 categoryId 写死 [401,402,403]，新增耳机/音箱子类需同步。

