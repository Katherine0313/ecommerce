# 进度日志

## 发布（发布命令在 e-commerce-boot 仓库，别搞混站点）

后台管理端 → **www.jiemids.com**，站点目录 `/www/wwwroot/jiemids.com`：

```bash
# 在 e-commerce-boot 仓库根目录执行
./deploy/release-web.sh
```

- 参数：`--no-build`（只发现有 dist）/ `--dry-run`（只打印命令）/ `--rollback`（回滚上一版）
- 脚本会 `npm run build` → tar 上传 → rsync 同步，自动留 3 份备份（`/www/backup/web/jiemids.com.bak.<时间戳>`）
- 发布后要**清 EdgeOne 缓存**（腾讯云 EdgeOne 控制台 → 站点 → 缓存刷新；至少刷 `index.html` / `/assets/*`），否则老用户可能仍是旧页面
  - 自查是否命中边缘缓存：`curl -I https://www.jiemids.com/ | grep -i "EO-Cache-Status\|Age"`
- ⚠️ **PC 商城（xiaojiecpn.com）不要用这个脚本**，它用 `./deploy/release-mall.sh`（2026-10-31 新增）
- ⚠️ 发布后端服务是另一套命令：`./deploy/release.sh <svc>`（auth-service 最后发）

## 会话：2026-11-02（后台商品评价管理页面）

### 当前状态
- **阶段：** 阶段 12（后台商品评价管理页面）代码完成，**2026-11-02 已发版**。
- 后端在 `e-commerce-boot`（order-service），方案见 `e-commerce-boot/review`（v3）。

### 已执行操作
- 新增 `src/api/review.js`：列表 / 详情 / 新增虚拟 / 编辑虚拟 / 回复 / 显示隐藏 / 删除 / 导出（导出 `responseType: 'blob'`）。
- 新增 `src/pages/orders/review/ReviewList.vue`：
  筛选区（商品名 / 订单号 / 星级 / 评价类型 / 显示状态 / 时间区间 `a-range-picker`）+
  表格（商品 / 星级 / 评价内容 / 媒体 / 类型 / 评价人 / 订单号 / 创建人 / 状态 / 商家回复 / 评价时间 / 操作）+
  分页（表格自带）+ 导出 + 新增虚拟评价；行内回复弹窗 / 隐藏显示 / 编辑 / 删除。
- 新增 `src/pages/orders/review/ReviewEditModal.vue`：商品远程搜索下拉（走 `getGoodsList`）+ `a-rate` 星级 +
  标签（预设 + 自定义）+ 内容 + 晒图上传（`/api/admin/upload?type=review`，接 `compressImage(file, getImageSpec('review'))`）。
- `src/router/index.js` 加 `/orders/review`，`meta.permission = product:review:list`。
- `src/utils/image-spec.js` 补 `review` 档（最长边 800 / contain）。
- 真实评价的「编辑」按钮 **禁用**（不是隐藏），点击提示「买家真实评价不可编辑」—— 真正的拦截在后端（400）。
- 视频不进缩略图九宫格，单独标「含视频」标签。

### 踩坑
- `:fallback="\"...\""` 这种**双引号属性里塞 `\"` 转义**会直接编译失败
  （`Error parsing JavaScript expression: Unterminated string constant`）。
  SVG 占位图要放 script 级 `const PLACEHOLDER = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(...)`，
  模板里用 `:fallback="PLACEHOLDER"`。

### 验证
- `npx vite build` 通过（ReviewList 14.36 kB）。

### 发版与生产验证（2026-11-02）
- `./deploy/release-web.sh`（**在 `e-commerce-boot` 仓库根目录执行**；同一次带上了阶段 27 的图片压缩改造）
  → 生产 `/www/wwwroot/jiemids.com`，产物 `index-87362cae.js`（ReviewList 14.56 kB）。
- 生产只读补验全过：`GET /api/admin/reviews` 200（3 条）、筛选（keyword / rating / isVirtual / visible / 时间区间）各自生效、
  orderNo 精确 1·截断 1·不存在 0、详情 200 / 404、导出 200 且魔数 `PK\x03\x04`（真 xlsx）、
  无 `product:review:*` 的令牌 **403「无权访问该功能」**、SPA 路由 `www.jiemids.com/orders/review` 200。
- **故意没在生产跑写操作**（回复/隐藏/删除/新增虚拟评价）—— 生产上是真实买家评价，不改线上数据；
  这些路径已在本地全链路实测通过。
- 仍需人工：Excel 打开看内容、运营账号菜单可见性。
- 发版后用运营账号确认「订单管理 → 商品评价」菜单可见可进。

### 评价列表表格列宽 + 操作按钮精简（2026-11-02，晚于上面那次发版）
- 现象：评价列表右侧出现异常留白/横向滚动条，「评价时间」列被右侧固定的「操作」列盖住。
- 根因（用无头 Chrome 实测 antd 3.2.20 渲染）：12 列列宽之和 **1870**，而 1920 屏的内容区只有 **约 1648**
  → 表格实际宽度按「列宽之和」算（`scroll.x` 写 1600 是死数字，形同虚设）→ 表格比内容区宽 222px，
  横向溢出出滚动条，右下固定列（sticky）压在「评价时间」列上。
- **最终改法（用户拍板）**：**所有列都不写死宽度、也不写 `:scroll`**。
  - 只保留操作列的 `fixed: 'right'` —— 有固定列时 antd 走 `table-layout: fixed`，
    列宽由**表头文字**决定（实测 1920 屏：12 列 = 117/117/171/…/117，合计正好 1648 = 内容区宽），
    **24 位订单号不会把列撑爆**（这点比写死宽度更稳）。
  - 实测：table=1648 = 容器宽，无横向滚动条，12 列全露出；2560 屏 table=2288=容器宽；1366 屏 1328=容器宽。
  - 反例别再踩：`scroll.x` 写 `'max-content'` 会按内容实际宽度撑开（24 位订单号 ≈219px），能把表格顶出内容区。
- **操作列精简**：按产品要求去掉「编辑」「显示/隐藏」两个按钮，**只剩「回复」「删除」**；
  连带删掉 `openEdit` / `toggleVisible` 两个函数和 `updateReviewVisible` 的导入。
  后端接口与 `product:review:edit` 权限码**没动**（要恢复入口改一个 `.vue` 就行）。
  **副作用**：虚拟评价建好后不能改；后台没法再隐藏评价（下架只能靠删除）。
- **规矩**：后台表格列宽写死时，`:scroll.x` 必须是数字且等于列宽之和；不写死宽度时靠 `fixed` 列兜住 `table-layout`。
- 本次只改了前端，**未发版**。

## 会话：2026-10-30（管理端全面探索：架构摸底 + 接口契约核对）

### 当前状态
- **阶段：** 新建「阶段 10：清理遗留与修错」；阶段 9（后台管理端对接）剩余工作台对接。
- 工作区干净（`git status` 无改动），本次只产出文档，未改任何代码。

### 已执行操作
- 通读 `src/` 全部 169 个源文件的骨架 + 关键文件（`main.js` / `App.vue` / `router/index.js` / `router/permission.js` / `api/request.js` / `api/*.js` 全部 15 个 / `store/cart.js` / `utils/auth.js` / `utils/token.js` / `utils/shop.js` / `utils/orderNotify.js`）。
- 与 `e-commerce-boot` 后端**逐条核对** 60+ 个 `/api/admin/**` 接口：管理端调用与后端 `Admin*Controller` 基本一一对应 ✅（含报表 6 个接口、RBAC 三套、商品/分类/标签/促销/库存/售后/会员）。
- 提取管理端调用的全部接口路径，与后端 `Admin*Controller` 的 `@*Mapping` 做差集比对。
- 核对 `.env` / `.env.example` 的键名（只看键名不打印值）、`vite.config.js` 代理规则、`package.json` 脚本与实际目录是否存在。
- 发现并记录 10 类问题（详见 findings.md「管理端全面探索」章节）：
  1. `vite.config.js` 的 `/api` 代理 target 变量不存在 + rewrite 剥掉 `/api` 前缀 → 死配置
  2. `.env.example` 指向已废弃的 Node 后端 3000 端口
  3. `postinstall` / `npm run api` 指向不存在的 `server/`、`service/` 目录
  4. `App.vue`「个人中心」跳 `/profile`，实际路由是 `/personal` → 404
  5. `src/store/cart.js` 仍是 snake_case，与后端驼峰契约不一致；admin token 打 C 端接口必然 403
  6. `getProductTags` 打后端不存在的接口
  7. 4 个未使用的 API 函数
  8. 文档（ddos / sandbox / findings / task_plan）整体停留在 Node 后端时代，路径全部失效
  9. `CommonView.vue` 是 stepin 模板残留
  10. 无 ESLint / Prettier 配置、无 CI，prettier / mockjs / typescript 冗余
- 摸清架构要点：后台外壳来自 npm 包 `stepin`；侧边栏渲染读前端静态路由，后端 `admin_system_menu` 表只用于菜单管理与角色授权 → **菜单双数据源**，靠 path + permission 人工对齐。
- 产出：`findings.md` 新增「管理端全面探索（2026-10-30）」章节；`task_plan.md` 新增阶段 10 / 阶段 11 并更新「下一步 / 当前阶段 / 关键问题」；本文件新增本会话记录。

### 验证
- 未改代码，无需构建验证。
- 接口契约核对方式：提取 `src/api/*.js` 中全部 `axios.get/post/put/delete` 路径，与后端各 `Admin*Controller` 的 `@RequestMapping` + `@*Mapping` 双向差集比对。

### 待决策
- 菜单双数据源以哪边为准（阶段 11 三选一）。
- 管理员「商品预览 → 加购 → 结算」链路要不要真打通（选项 A 只读 / B 纯前端 mock / C 后端开预览专用链路）。

## 会话：2026-10-29（多租户下线：店铺信息静态化）

### 当前状态
- 后端已删除 shop-service 与 `/api/admin/shops/**`，前端店铺信息改为静态配置，不再发任何店铺请求。

### 已执行操作
- `src/utils/shop.js` 重写为静态 `shop`（id=1、`VITE_SHOP_NAME` 或「杰米小店」），删除 `loadShop` / `expireText` / 到期定时器。
- 删除 `src/api/shop.js`、`src/utils/sandboxVisitor.js`（沙箱入口此前已从登录页移除）。
- `App.vue` / `Login.vue` / `useWorkplaceData.js` / `Workplace.vue` 去掉 `loadShop` 调用；`StoreCard.vue` 直接取静态店名。
- `AboutUs.vue` 删除店铺 ID/沙箱标签/有效期展示，固定显示「自营店铺」。
- `request.js` / `permission.js` / `orderNotify.js` 清理沙箱相关注释。

### 验证
- `npm run build` 通过。

## 会话：2026-10-12

### 当前状态
- **阶段：** 7 - 测试与验证（in_progress）
- **开始时间：** 2026-10-12

### 已执行操作
- 排查图片卡顿根因：列表 80px 缩略图加载原图、后端上传无压缩、详情富文本全量加载原图。
- 确定方案：详情懒加载 + 前端上传压缩（方案 A，不动后端）。
- 新建 `src/utils/compressImage.js`（canvas 压缩，最长边 1200px、质量 0.8、gif/小图跳过、失败兜底）。
- `AddGoods.vue` 主图 `a-upload` 加 `:before-upload="compressImage"`。
- `EditModal.vue` 主图 `a-upload` 加 `:before-upload="compressImage"`。
- `Editor.vue` 富文本上传从 `server` 直传改为 `customUpload`（先压缩再上传，沿用 axios 自动带 token）。
- `Mall.vue` 详情富文本每个 `<img>` 补 `loading="lazy"`。

### 全站性能优化（会话续）
- 新建全局 `v-lazy` 懒加载指令（IntersectionObserver 单例 + 占位图）。
- `main.js` 删除 `vue-ueditor-wrap` 死代码（零使用），注册 `v-lazy` 指令。
- 批量把 14 个文件、16 处列表缩略图从 `<a-image>`/`<img>` 改为 `<img v-lazy>`（商品列表、库存、分类关联、标签、订单、售后、工作台）。
- `vite.config.js` 加 `manualChunks`，拆 vue/antd 独立 chunk。

### 图片目录迁移（public → uploads）
- `server/middleware/upload.js`：上传目录改 `uploads/upload`、`uploads/body`。
- `server/index.js`：静态 serve 改 `uploads/`。
- `server/sqlite/shopDB.js`：`PUBLIC_IMAGE_DIR` 改 `UPLOAD_DIR`（含删店铺时删磁盘文件的逻辑）。
- 迁移 101 张上传图（upload 33 + body 68）到 `uploads/`，保留 public/image 下 3 张静态图（QRcode/store-logo/price-background）。
- `.gitignore` 加 `uploads/`。

> ⚠️ 上述「图片目录迁移」已整体回退：因生产 nginx 反代 /image 到后端、dist 图片拷贝无人访问，迁移对页面性能无实质帮助，且引入 dev 环境 404。已恢复为图片存 public/image，后端 serve 路径、dev proxy、gitignore 全部还原。

### nginx 优化 + UI 调整 + 上线（会话续 2）
- 分析生产 nginx：确认 `/image/upload`、`/image/body` 已反代到后端、HTTP/2 已开、缺 gzip、js/css 缓存仅 12h。
- 生成 `nginx-optimization.md`（完整 www server 块：gzip + js/css immutable + 图片缓存）。
- `Mall.vue` 购物车弹窗按钮高度 20px → 40px（修复按钮被裁切）。
- `Cart.vue`、`Settlement.vue` 支付/继续购物/返回/提交订单 4 个按钮统一 120×40、左右并排居中（.btn-row）。
- 用户已打包上线。

### 创建/修改的文件
- 新建：`src/utils/compressImage.js`
- 修改：`src/pages/goods/add-goods/AddGoods.vue`
- 修改：`src/pages/goods/list/EditModal.vue`
- 修改：`src/pages/goods/add-goods/Editor.vue`
- 修改：`src/pages/goods/list/Mall.vue`

### 测试结果
| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 上传大图后体积 | 压缩到 ≤1200px、100~200KB | 待验证 | pending |
| 详情富文本懒加载 | 首屏只加载可见图片 | 待验证 | pending |

### 错误
| 错误 | 解决方案 |
|------|---------|
| （暂无） | |

## 会话：2026-10-13

### 当前状态
- **阶段：** 商品列表后端分页（代码完成，待验证）

### 已执行操作
- 复核豆包性能分析，确认「商品列表接口无分页」是菜单切换 3 秒卡顿的元凶（图片/富文本懒加载、manualChunks 上一轮已完成）。
- 后端真分页：`productDB.getAll` 加 `LIMIT/OFFSET`（pageSize>0 才分页，兼容旧调用）、`productController.getProducts` 透传 page/pageSize。
- 前端 `List.vue` 接入受控分页器（:pagination + @change + row-key），loadData 携带分页参数，搜索重置回第一页。

### 创建/修改的文件
- 修改：`server/sqlite/productDB.js`
- 修改：`server/controllers/productController.js`
- 修改：`src/pages/goods/list/List.vue`

### 测试结果
| 测试 | 预期 | 实际 | 状态 |
|------|------|------|------|
| 商品列表接口分页 | 只返回当前页 13 条 + total | 待验证 | pending |

### 错误
| 错误 | 解决方案 |
|------|---------|
| （暂无） | |

## 会话：2026-10-19

### 当前状态
- **阶段：** 后台管理端对接（商品/分类/标签/订单，完成）

### 已执行操作
- 统一驼峰契约：前后端 + C 端三套字段对齐（snake_case → camelCase），后端不用 @JsonNaming 翻译层。
- 商品：api/products.js 路径加 /admin；List/EditModal/AddGoods/SalesInfo/Mall 字段驼峰 + 列表读 data.list。
- 分类：api 路径加 /admin；Category（label→name、sort_index→sort、product_count→productCount）、AddCategory（parent_id→parentId）、RelateGoods/AddGoodsModal 字段适配 + 读 data.list。
- 标签：api/tags.js 路径加 /admin + productIds 驼峰；Tag/TagModal 字段适配。
- 订单：api/orders.js 拆分前后台（下单/支付/详情走 /api/orders，其余走 /api/admin/orders）；10 个订单页面字段驼峰 + 状态字符串→数字 code。
- 订单列表分页：OrderList 4 tab（全部/待付款/待确认/已取消）独立请求后端分页（status+page）；Ship 发货页 3 tab 全量（订单少不分页）。
- 日期筛选：抽公共组件 date-range-select.vue（时间字段下拉 + 日期范围，默认下单/支付/发货时间），OrderList/Ship 复用；后端 dateField 白名单。

### 创建/修改的文件
- 新建：src/components/date-range-select.vue
- 修改：api/products.js、api/tags.js、api/orders.js；goods/*、orders/* 共 20 余个页面

### 错误
| 错误 | 解决方案 |
|------|---------|
| 订单列表 filter 报 not a function | getAllOrders 改分页后读 data.list |
| 日期筛选不生效 | 前端参数名 snake_case 改 camelCase 对齐后端 @RequestParam |

## 会话：2026-10-20

### 当前状态
- **阶段：** 后台管理端对接（库存预警、售后完成，工作台未做）

### 已执行操作
- 库存预警：api/stock.js 路径 /api/stock → /api/admin/stock；StockList.vue 字段蛇形→驼形（stockId/alertEnabled/minStock/maxStock/productId）；utils/stock.js + useWorkplaceData.js + Projects.vue 字段对齐。
- 售后：api/afterSale.js 审核/删除走 /api/admin/after-sales；6 售后页面（only-refund/return_refund/exchange 的 detail/review）字段蛇形→驼形（afterSaleNo/createdAt/afterSaleType/orderNo/payTypeDesc/payAt/payAmount 等）；售后列表搜索/日期筛选参数。
- 订单支付方式：6 个订单表格 dataIndex payType → payTypeDesc（英文 code 改中文）。
- 分页：PageVO 后端加 page/pageSize，OrderList 从响应读 pageSize 不写死。

### 创建/修改的文件
- 修改：api/stock.js、api/afterSale.js、utils/stock.js；stock/StockList.vue、workplace/useWorkplaceData.js、workplace/Projects.vue、orders/OrderList.vue + 4 订单表格、after-sales/* 共 10 余个页面

### 错误
| 错误 | 解决方案 |
|------|---------|
| 订单列表支付方式显示英文 | dataIndex payType → payTypeDesc |
| 售后页面字段蛇形后端读不到 | 前端字段统一驼形对齐后端 VO |

## 五问重启检查
| 问题 | 答案 |
|------|------|
| 我在哪里？ | 后台管理端对接（库存预警、售后完成，工作台未做） |
| 我要去哪里？ | 工作台（dashboard/shops/upload）对接 |
| 目标是什么？ | e-commerce 前端对接 Spring 后端 /api/admin/**，统一驼峰 |
| 我学到了什么？ | 见 findings.md |
| 我做了什么？ | 见上方「已执行操作」 |

---
*每个阶段完成后或遇到错误时更新此文件*
