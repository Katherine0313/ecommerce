# 发现与决策

## 需求
- 商品列表页、详情页图片多，打开卡顿、「卡成马赛克」，需要做图片性能优化。

## 研究发现
- 列表 `src/pages/goods/list/List.vue:28` 用 `<a-image :src="record.image" :width="80">` 显示 80px 缩略图，`src` 却是原图 URL。
- 后端 `server/middleware/upload.js` 用 multer 原样保存文件：无大小限制、无压缩、无缩略图；白名单还放行了 `.bmp`、`.mp4` 等大文件。
- 详情 `Mall.vue` 用 `v-html="product.body"` 渲染富文本，body 内常嵌几十张原图，打开弹窗全量加载。
- 上传接口返回 `/image/upload/{filename}` 或 `/image/body/{filename}`（相对路径）；后端 `server/index.js` 将 `/image/*` 映射到 `public/image/*` 静态目录。
- ant-design-vue 的 `a-image` 组件 `inheritAttrs: false`，且显式透传的属性列表里没有 `loading`，所以 `loading="lazy"` 不生效，列表图不能直接靠 a-image 懒加载。
- wangeditor `uploadImage` 的 `customUpload` 签名为 `(file: File, insertFn: (url, alt, href) => void) => void`。
- `src/utils/file.js` 的 `getBase64` 已被 `AuthorTable.vue`、`ProjectTable.vue` 复用，前端引入 `file.js` 安全（`fs`/`path` 仅构建期脚本使用，会被 tree-shake）。
- 项目 axios 实例 `@/api/request.js` 已设置 `baseURL` 并在拦截器自动加 `Authorization`，上传无需手动带 token。
- 用户实测性能数据（Network 面板）：DOM 骨架 3.76s；商品缩略图一次性全量请求，超出浏览器同源并发上限 6 个，排队 11~13s，Load 事件定格 7.03s。核心瓶颈是「缩略图无懒加载 + 全量并发请求」。
- 构建产物体积（`dist/assets` 实量）：主入口 `index.js` 1.9MB、工作台 `Workplace.js` 1.1MB、`SalesInfo.js` 800KB，而商品列表 `List.js` 仅 32KB——说明主入口全量引了重依赖，且列表静态引了编辑弹窗链。
- `vue-ueditor-wrap` 全项目零使用（仅 main.js 注册），是纯死代码，白白占主入口体积。
- `main.js` 全量 `import stepin from 'stepin/es'` + 全量 `ant-design-vue/dist/antd.variable.less`，是主入口 1.9MB 的主要来源。
- 生产 nginx（宝塔）：`/image/upload`、`/image/body` 已反代到后端、HTTP/2 已开启；但未开 gzip、js/css 缓存仅 12h、上传图无浏览器缓存。
- 商品列表接口无分页：`productDB.getAll` 已算 `total`（注释写"分页用"）但未加 `LIMIT/OFFSET`，一次性查全部商品返回大 JSON；前端 `List.vue` 的 `onActivated` 每次切页全量拉取，是菜单切换 3 秒卡顿的主因（与图片懒加载是两回事）。

## 技术决策
| 决策 | 理由 |
|------|------|
| 前端压缩最长边 1200px、质量 0.8 | 商品图/详情图足够清晰，2~5MB 原图 → 100~200KB |
| gif 动图跳过压缩 | canvas 会把 gif 压成静态图，需跳过 |
| 原图边长 ≤1200px 时不压缩 | 避免对小图做无谓的重新编码 |
| 压缩失败兜底返回原图 | 保证上传流程不被中断 |
| 统一转 jpeg | 商品图/详情图均为照片，jpeg 压缩率最佳 |
| 复用 `file.js` 的 `getBase64` | 优先复用已有函数，不重复造轮子 |
| 列表缩略图统一换原生 `<img v-lazy>` | a-image 不透传 loading，原生懒加载最简可靠 |
| 全局 `v-lazy` 指令（IntersectionObserver 单例） | 一次封装、全站 14 个文件复用，滚动进视口才加载 |
| 删 `vue-ueditor-wrap` | 全项目零使用，纯死代码 |
| `manualChunks` 拆 vue/antd 独立 chunk | 依赖 chunk hash 稳定，长期缓存，业务改动不重下大包 |
| 放弃「antd 样式按需」 | stepin 用 `AntdvLessPlugin`（动态主题）+ `antd.variable.less` 实现运行时换主题，删全量 less 会破坏换肤，只能靠缓存而非减体积 |
| 放弃「上传图挪出 public」 | 生产 nginx 反代 /image 到后端，dist 图片拷贝本就无人访问；迁移收益≈0，且引入 dev 404、生产迁移成本 |

## 后台管理端对接（2026-10-19）

### 统一驼峰契约
- 后端 Java VO 驼峰，前端字段从旧 Node 的 snake_case 改成 camelCase，前后端 + C 端（小程序）三套对齐，不用 @JsonNaming 翻译层。
- 后台接口路径走 `/api/admin/**`（JwtInterceptor 按此分流 admin 身份）。

### 分页策略（按模块）
- 商品列表：后端真分页（page/pageSize → PageVO{list,total}），前端受控分页器。
- 订单列表：4 个状态 tab 独立请求后端分页（status + page），不做前端 filter；PageVO 含 page/pageSize，前端从响应读 pageSize 不写死。
- 发货页：3 个状态 tab 全量（status 筛选 + pageSize 给足），订单量少不分页。

### 状态契约
- 订单状态从旧 Node 字符串（pending_pay）改为数字 code（1-6），前端 filter/tag 用数字；后端 OrderStatusEnum 提供 adminDescMap()/colorMap()。

### 时间字段
- 后端 LocalDateTime 加 @JsonFormat(pattern="yyyy-MM-dd HH:mm:ss")，避免 ISO 格式（T 分隔）前端不好读。

### 公共组件
- date-range-select.vue：时间字段下拉（下单/支付/发货时间）+ 日期范围，change 吐 {dateField,startDate,endDate}，reset() 供刷新清空；订单/发货/售后复用。

### 库存预警 / 售后对接（2026-10-20）
- 库存预警：路径 /api/admin/stock（列表/状态常量/创建 upsert），字段统一驼峰（stockId/alertEnabled/minStock/maxStock/productId）。
- 售后：C 端申请/列表/详情/状态常量/提交退货 + 后台审核/删除（/api/admin/after-sales）；前端售后页字段蛇形→驼形（afterSaleNo/createdAt/afterSaleType/payTypeDesc 等）。
- 支付方式：payType 存英文 code，payTypeDesc 中文，前端表格用 payTypeDesc 展示（不是 payType）。

## a-table 列宽规则（2026-11-02）

后台表格（ant-design-vue 3.2.20 / vc-table）横向布局的实测结论（无头 Chrome 加载 antd UMD 渲染量出来的）：

- 表格实际宽度**按「所有列 width 之和」算**。`:scroll="{ x: 1600 }"` 而列宽和是 1870 时，`x` 是死数字，表格就是 1870。
- `scroll.x` **是数字** → `table-layout: fixed`，列宽严格按写的来；写 `'max-content'` → `table-layout: auto`，
  列会被内容撑开（本项目 24 位订单号 ≈ 219px），反而容易把表格顶出内容区。**列宽写死时只能用数字**。
- 表格永远带 `min-width: 100%`：内容区比列宽和宽 → 列被等比拉宽，**不会在右侧留白**；
  内容区比列宽和窄 → **横向溢出**（出滚动条），且右侧 `fixed: 'right'` 的列会 sticky 压在相邻列上
  （评价列表就出现过「评价时间」列被「操作」列盖住）。
- **推荐做法（评价列表已改成这样）**：**列不写死宽度、也不写 `:scroll`**，只保留操作列的 `fixed: 'right'`。
  - 有固定列 → antd 走 `table-layout: fixed` → 列宽由**表头文字**决定，不会被长订单号撑爆；
  - 表格宽度 = 容器宽（`width:100%`），任何屏幕都不溢出、不留白（实测 1920/2560/1366 三档都对齐）。
  - 代价：各列宽度不可控，长文本列（如评价内容）只能靠单元格自身的 `line-clamp` 裁剪。
- **规矩**：写死列宽时，`:scroll.x` 必须是数字且等于列宽之和，且列宽和 ≤ 内容区宽度（1920 屏约 1648）。
- 以后改后台表格，别靠肉眼看有没有留白，量一下 `table` 与 `.ant-table-content` 的 `clientWidth`/`scrollWidth` 即可。

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| 列表缩略图加载原图 | 上传时前端压缩原图 |
| 详情富文本全量加载原图 | 给每个 `<img>` 补 `loading="lazy"` |
| 富文本 `server` 直传无法做上传前压缩 | 改 `customUpload` 手动压缩后再上传 |

## 资源
- 后端上传：`server/middleware/upload.js`、`server/controllers/uploadController.js`、`server/routes/uploadRoutes.js`
- 上传接口：`POST /api/upload`（商品主图）、`POST /api/upload?type=body`（详情图）
- 前端上传入口：`AddGoods.vue`、`EditModal.vue`（a-upload）、`Editor.vue`（wangeditor uploadImage）

## 视觉/浏览器发现

每完成两次查看、浏览器或搜索操作，就更新本节。
将图片、PDF、截图等多模态发现立即转述为文本，以免上下文丢失。

- （本次任务未进行浏览器/截图操作）

## 店铺信息静态化（2026-10-29）

多租户沙箱已下线，后端删除 `shop-service` 与 `/api/admin/shops/**`：

- 店铺信息唯一来源是 `src/utils/shop.js` 的静态 `shop` ref：`{id:1, name: VITE_SHOP_NAME || '杰米小店', isTemplate:1}`。
- `loadShop()` / `expireText()` / 到期强制登出定时器已删除；不再请求店铺接口，也不再有店名加载空窗期。
- `src/api/shop.js`、`src/utils/sandboxVisitor.js` 已删除；`sandbox-and-launch.md` 仅作历史存档。
- 仍读取 `shop` 的组件：App 顶栏、StoreCard、Workplace/AboutUs，均为同步静态值，无需 loading 处理。

## 管理端全面探索（2026-10-30）

### 技术栈与骨架

- 仓库实为 **stepin-template**（Vue 3.2 + ant-design-vue 3.2 + Vite 4 + Tailwind 3 + Pinia 2 + vue-router 4）。
- **整个后台外壳来自 npm 包 `stepin`**（`import stepin from 'stepin/es'`），不是本地代码：
  侧边栏、顶栏、多标签、主题切换、移动端抽屉都是 `stepin-view` 组件提供的。
  本仓库只提供 `src/router/index.js` 的路由表 + `src/pages/**` 的业务页面。
- `App.vue` 挂载点是 **`#stepin-app`**（不是常见的 `#app`）；`index.html` 内联了首屏加载层 `#boot-loader`，
  `main.js` 在 `router.isReady()` 后淡出，避免白屏闪烁。
- 规模：169 个源文件（82 `.vue` / 48 `.js` / 10 `.less`）。
- 页面 62 个，分布在 `goods / orders / after-sales / stock / business / system / personal / workplace / login` 九个域。

### 与后端的对接现状（已逐条核对）

- 管理端调用的 60+ 个 `/api/admin/**` 接口与后端 `Admin*Controller` **基本一一对应**，包括：
  - 商品/分类/标签/促销/库存/上传（product-service）
  - 订单/优惠券/报表（order-service）
  - 售后（aftersale-service）
  - 管理员账号/角色/菜单（auth-service）
  - 会员/会员等级/用户统计（user-service）
- 报表 6 个接口（`turnover` / `turnover-report` / `sales-ranking` / `dashboard` / `headline` / `export`）
  与后端 `ReportController` 完全对应 ✅
- `request.js` **不拆包**：响应拦截器直接 `return response`，页面统一写 `res.data.data.xxx`（双层解包）。
  与商城端 `res.data` 单层解包风格不同，但各自自洽，不要混用。
- 401 → 清 token + 整页跳 `/front/login`，原因用 `sessionStorage` 带过去在登录页展示；
  400/403/404/429/500 各有对应 message 提示。**这块做得完整** ✅
- 路由守卫 `src/router/permission.js` 三层校验：token 过期本地预判 → 必须是 `type=admin` 且含 `permissions` →
  路由 `meta.permission` 细粒度校验；无任何可访问页面时清 token 退回登录页，避免死循环 ✅

### 菜单的双数据源（架构性注意点）

- **侧边栏渲染读的是前端静态路由**：`App.vue` 的 `buildMenu(router.options.routes, ...)`，
  按 `meta.renderMenu` + `meta.permission` 过滤生成 `menuList` 给 `stepin-view`。
- **后端 `admin_system_menu` 表不参与渲染**，只用于「菜单管理」页和「角色-菜单授权」。
  种子数据在 `e-commerce-boot/docs/sql/rbac/01-rbac-schema.sql`，带 `path` + `permission` 两列。
- 两边靠 `path` + `permission` 标识**人工对齐**（如 `/workplace` ↔ `workplace:view`）。
  → **改菜单必须前后端同时改**，否则会出现「角色授权了但菜单不出现」或反之。

### 探索中发现的问题

| # | 问题 | 影响 |
|---|---|---|
| 1 | `vite.config.js` 的 `/api` 代理规则是**坏的**：`target: env.VITE_API_URL`，但 `.env` / `.env.example` 里只有 `VITE_API_BASE_URL`，**没有 `VITE_API_URL`** → target 为 `undefined`；且 `rewrite: p => p.replace(/^\/api/,'')` 会剥掉 `/api` 前缀，而 Spring 网关期望的是 `/api/**` | 目前 dev 能通是因为 axios 直接用 `baseURL=VITE_API_BASE_URL`（.env 里是 `http://127.0.0.1:8080`），**完全绕过了 proxy**。一旦有人把 baseURL 改成空（走同源 proxy）就会全线 404。属于**死配置**，应删掉或改对 |
| 2 | `.env.example` 整体停留在**旧 Node 后端**：`VITE_API_BASE_URL=http://127.0.0.1:3000`、`PORT=3000`、`JWT_SECRET` | 后端已迁到 Spring Cloud 网关 **8080**。照抄 example 会连错端口 |
| 3 | `postinstall: cd server && npm install`、`api: node service/index.js`，但 **`server/` 和 `service/` 目录都不存在**（`git ls-files server` 为空，Node 后端已删） | 全新克隆 `npm install` 会在 postinstall 阶段报错；`npm run api` 直接失败 |
| 4 | `App.vue` 顶栏菜单「个人中心」`router.push('/profile')`，但路由表里是 **`/personal`** | 点「个人中心」落到 404 兜底页 |
| 5 | `src/store/cart.js` **仍是 snake_case，与后端驼峰契约不一致** | 后端 `AddCartDTO{productId,quantity,insuranceDetail,insuranceFee}`、`CartVO{productId,...}`，而 store 收发都是 `product_id` / `insurance_detail` → 加购请求后端拿不到 productId；`items.find(i => i.product_id === ...)` 永远匹配不上（响应是 `productId`），导致重复加购不累加。`load()` 有 `isAdminToken()` 短路，但 `addItem/removeItem/updateQuantity/clear` 仍带 admin token 打 C 端接口 → 后端按 user 身份拦截 → **403「无权限操作」** → 回滚。**预览购物车实际不可用** |
| 6 | `getProductTags` → `GET /api/admin/tags/products/{productId}/tags`，**后端没有这个接口**（后端只有 `GET /{tagId}/products`） | 当前无调用点，是死代码，一用就 404 |
| 7 | 只有 4 个 API 函数未被引用：`getAdminUser`（后端有）、`getCourierList`（`getCouriers` 的重复别名）、`getProductTags`（后端无此接口）、`getPromotionStats`（后端有） | 前两个可直接删；`getProductTags` 要么删、要么改成 `GET /{tagId}/products` 的用法 |
| 8 | 文档整体停留在 **Node 后端时代**：`ddos-attack-and-defense.md`（Express + sql.js + pm2）、`sandbox-and-launch.md`（沙箱多租户），以及本文件「资源」段里的 `server/middleware/upload.js`、`server/index.js`、`productDB.getAll` 等路径**全部已不存在** | 新人按文档找文件会全部落空 |
| 9 | `src/components/layout/CommonView.vue` 是 stepin 模板残留：顶部「文档 / API / 关于 / 商业合作 / 注册」全部指向 `/front/login` | 无实际作用，可删 |
| 10 | 无 ESLint / Prettier 配置、无 CI（无 `.github/`、无 `.prettierrc`、无 `.editorconfig`），但 `prettier` / `mockjs` / `typescript` 已装进 devDependencies（`mockjs` 的 `import '@/mock'` 在 main.js 里是注释掉的，且无 `src/mock` 目录） | 无质量门禁；三个依赖是纯冗余 |

### 与商城端（ecommerce-mall）的横向差异

| 维度 | 管理端 `e-commerce` | 商城端 `ecommerce-mall` |
|---|---|---|
| UI 库 | ant-design-vue 3 + stepin 框架 | Element Plus 2（全量注册） |
| 路由表 | 静态 `router/index.js`，**驱动侧边栏渲染** | 静态 `router/index.js`，仅驱动页面 |
| 请求解包 | `res.data.data`（双层） | `res.data`（单层） |
| 菜单权限 | JWT `permissions` + `meta.permission` 双层过滤 | 无（P2 才有登录态） |
| 布局外壳 | 来自 npm 包 `stepin` | 自己写 `DefaultLayout.vue` |
| 部署 | www.jiemids.com | www.xiaojiecpn.com |

---

*每执行2次查看/浏览器/搜索操作后更新此文件*
*防止视觉信息丢失*
