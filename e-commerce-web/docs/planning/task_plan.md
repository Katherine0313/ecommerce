# 任务计划：全站性能优化

将此文件作为任务的持久化路线图。在开始复杂工作前创建，并在阶段变化时及时更新。

## 目标

系统性地优化整个网站性能：降低首屏/切换页面的 JS 体积、解决图片全量请求排队，覆盖所有页面而非单点。

## 下一步

先做「阶段 10：清理遗留与修错」——2026-10-30 的全面探索发现 6 处硬伤（vite proxy 死配置、`.env.example` 端口过期、`postinstall` 指向不存在的目录、`/profile` 死链、购物车 store 契约不一致、文档停留在 Node 后端时代），这些不修会持续误导后续开发。
阶段 9 剩余的「工作台（dashboard/upload）对接」实际上后端接口已全部就绪（`/api/admin/report/*` 6 个 + `/api/admin/products/dashboard` + `/api/admin/users/statistics`），只剩前端页面对接与验收。

## 当前阶段

阶段 10：清理遗留与修错（2026-10-30 探索产出）

## 各阶段

将任务拆分为三到七个可验证的阶段。状态只能使用 `pending`、`in_progress` 或 `complete`，并在工作推进时更新。

### 阶段 1：需求与发现
- [x] 理解用户意图（全站卡顿，不止某页面）
- [x] 定位根因（主入口 1.9MB、缩略图全量请求排队、后端无压缩/缩略图）
- [x] 将发现记录到 findings.md
- **状态：** complete

### 阶段 2：规划与结构
- [x] 确定四层优化方案（打包瘦身 / 图片 / 加载策略 / 服务端 nginx）
- [x] 记录决策及理由
- **状态：** complete

### 阶段 3：图片源头优化
- [x] 新建 compressImage.js 上传压缩
- [x] AddGoods/EditModal 主图上传加压缩
- [x] Editor.vue 富文本上传改 customUpload 压缩
- [x] Mall.vue 详情富文本 img 懒加载
- **状态：** complete

### 阶段 4：全站列表缩略图懒加载
- [x] 新建全局 v-lazy 指令（IntersectionObserver）
- [x] main.js 注册指令 + 删除 vue-ueditor-wrap 死代码
- [x] 批量替换 14 个文件的列表缩略图为 v-lazy
- **状态：** complete

### 阶段 5：打包优化
- [x] 删除 vue-ueditor-wrap 死代码
- [x] vite manualChunks 拆 vue/antd 独立 chunk
- [x] antd 样式按需 —— 判定不可行（stepin 动态主题依赖 antd.variable.less）
- **状态：** complete

### 阶段 6：服务端 nginx（生产）
- [x] 分析 nginx 配置：确认 /image 反代已配好、HTTP/2 已开启
- [x] 提供 gzip + js/css immutable + 图片缓存配置（nginx-optimization.md）
- [x] 用户已打包上线
- **状态：** complete

### 阶段 7：测试与验证
- [ ] 验证列表缩略图滚动才加载
- [ ] 验证上传压缩生效
- [ ] 验证 nginx gzip/缓存生效（响应头）
- **状态：** in_progress

### 阶段 8：商品列表后端分页
- [x] 定位根因：商品列表接口无分页，onActivated 全量拉取
- [x] productDB.getAll 加 LIMIT/OFFSET（pageSize>0 才分页，兼容旧调用）
- [x] productController.getProducts 透传 page/pageSize
- [x] List.vue 接入受控分页器（:pagination + @change + row-key）
- [x] 搜索重置回第一页
- [ ] 验证翻页/改每页条数/搜索正确
- **状态：** in_progress

### 阶段 9：后台管理端对接
- [x] 商品/分类/标签 CRUD 对接（路径 /api/admin/** + 统一驼峰契约）
- [x] 订单模块对接（列表 4 tab 后端分页 + 状态数字 code + 日期筛选）
- [x] 公共时间选择器组件 date-range-select.vue（订单/发货/售后复用）
- [x] 库存预警对接（api/stock.js 路径 /api/admin/stock + StockList 页 + 工作台 Projects 字段驼峰）
- [x] 售后对接（api/afterSale.js 审核/删除走 /api/admin/after-sales + 6 售后页字段蛇形→驼形 + 列表搜索/日期筛选）
- [ ] 工作台（dashboard/upload）对接（shops 接口已下线）
- **状态：** in_progress

### 阶段 10：清理遗留与修错（2026-10-30 全面探索产出）

- [ ] **删掉坏的 `/api` dev proxy 规则**：`vite.config.js` 的 `target: env.VITE_API_URL` 变量不存在（应统一用 `VITE_API_BASE_URL`），且 `rewrite` 会剥掉 `/api` 前缀（Spring 网关期望 `/api/**`）。当前 axios 走 `baseURL` 直连 8080 绕过了它，属于死配置
- [ ] **`.env.example` 对齐新后端**：`VITE_API_BASE_URL` 改成 `http://127.0.0.1:8080`，删掉 `PORT` / `JWT_SECRET`（Node 时代残留）；`POST=3000` 同理
- [ ] **删掉 `postinstall` 与 `api` 脚本**：`server/`、`service/` 目录已不存在，`cd server && npm install` 会让全新克隆的 `npm install` 报错
- [ ] **修 `/profile` 死链**：`App.vue` 顶栏「个人中心」改为 `router.push('/personal')`
- [ ] **`src/store/cart.js` 改驼峰**：`product_id`→`productId`、`insurance_detail`→`insuranceDetail`、`insurance_fee`→`insuranceFee`；并给 `addItem/removeItem/updateQuantity/clear` 加 `isAdminToken()` 短路（管理员预览链路不该打 C 端接口）
- [ ] **清理死 API**：`getProductTags`（后端无此接口，一用就 404）、`getCourierList`（`getCouriers` 的重复别名）、`getAdminUser`（后端有但无人用）
- [ ] **删 `src/components/layout/CommonView.vue`**（stepin 模板残留，链接全指向 `/front/login`）
- [ ] **文档去 Node 化**：`ddos-attack-and-defense.md`、`sandbox-and-launch.md`、本文件与 `findings.md` 中所有 `server/*`、`productDB.*` 路径已失效，标注为历史存档或重写
- [ ] **补质量门禁**：加 `.prettierrc` + `.editorconfig`（prettier 已装但没配置）；清理 `mockjs` / `typescript` 冗余依赖
- **状态：** pending

### 阶段 11：菜单双数据源治理（待决策）

- [ ] 明确 `admin_system_menu` 表与前端 `router/index.js` 的同步策略：
  - 方案 A：以后端表为准，前端启动时拉表动态生成路由（改动大，stepin 框架适配成本高）
  - 方案 B：保持前端路由为准，把后端菜单表降级为「角色授权的权限标识集合」，去掉 `path` 列的重复维护
  - 方案 C：加一个 CI 脚本比对两边的 `path` + `permission` 是否一致，不一致就失败
- [ ] 无论选哪个，都要在「菜单管理」页加提示：改菜单需同步改前端路由
- **状态：** pending

### 阶段 12：后台商品评价管理页面（2026-11-02，代码完成，已发版）

> 后端在 `e-commerce-boot`（order-service），方案见 `e-commerce-boot/review`。本页只记前端部分。

- [x] 新建 `src/api/review.js`：列表 / 详情 / 新增虚拟 / 编辑虚拟 / 回复 / 显示隐藏 / 删除 / 导出
- [x] 新建 `src/pages/orders/review/ReviewList.vue`：筛选（商品名 / 订单号 / 星级 / 类型 / 显示状态 / 时间区间）
      + 表格 + 分页 + 导出 + 新增虚拟评价 + 行内回复 / 隐藏显示 / 编辑 / 删除
- [x] 新建 `src/pages/orders/review/ReviewEditModal.vue`：商品远程搜索下拉 + 星级 + 标签 + 内容 + 晒图上传
- [x] `src/router/index.js` 加 `/orders/review`（`meta.permission = product:review:list`，
      **必须与 `admin_menu` 的 `path`/`perms` 一致**，否则路由守卫会把页面跳走）
- [x] `src/utils/image-spec.js` 补 `review` 档（最长边 800 / contain）
- [x] 真实评价的「编辑」按钮 **禁用**（不是隐藏）+ 点击提示；后端另有 400 硬拦
- [x] 导出走 blob 下载（照 `Workplace.vue:158-182` 样板），API 层 `responseType: 'blob'`
- [x] 视频不进缩略图九宫格，只标一个「含视频」标签
- [x] `npx vite build` 通过
- [x] **发版**（2026-11-02）：`./deploy/release-web.sh`（在 e-commerce-boot 仓库根目录执行；
      **同一次带上了阶段 27 的图片压缩改造**）→ 生产 `/www/wwwroot/jiemids.com`，产物 `index-87362cae.js`（ReviewList 14.56 kB）
- [x] 发版后只读补验：`GET /api/admin/reviews` 200（3 条）、筛选全生效、详情 200/404、
      导出 200 且魔数 `PK\x03\x04`、无权限令牌 **403**、SPA 路由 `www.jiemids.com/orders/review` 200
- [ ] 发版后用运营账号确认「订单管理 → 商品评价」菜单可见可进（**需人工**，库里权限已确认）
- [ ] 后台页面写操作交互（回复/隐藏/删除/新增虚拟评价）**故意没在生产跑**（生产上是真实买家评价，不改线上数据），
      本地已全链路实测通过；要在生产验请先用测试数据

## 关键问题（2026-10-30 更新）

1. 生产 nginx 的 gzip/缓存优化是否要做？（需用户改宝塔 nginx 配置并 reload）
2. **菜单到底以哪边为准？** 前后端两份菜单数据，靠人工对齐（见阶段 11）。需要产品/开发一起定。
3. **管理员的「商品预览 → 加购 → 结算」链路要不要真打通？** 现在打的是 C 端接口、拿 admin token，必然 403。
   - 选项 A：只做只读预览（展示商品 + 详情），砍掉加购/结算/支付
   - 选项 B：后台预览链路改成纯前端 mock（不落库），仅作演示
   - 选项 C：后端给 admin 身份开一条预览专用链路（成本最高）

## 已做决策

| 决策 | 理由 |
|------|------|
| 前端上传压缩（1200px/0.8）而非后端 sharp | 不动后端、风险低、全站受益 |
| 列表缩略图统一换原生 `<img v-lazy>` | antd a-image 不透传 loading |
| 全局 v-lazy 指令（IntersectionObserver 单例） | 一次封装，全站复用 |
| manualChunks 拆 vue/antd | 长期缓存 |
| 删 vue-ueditor-wrap | 全项目零使用，纯死代码 |
| 放弃「antd 样式按需」 | stepin 用 AntdvLessPlugin 动态主题，删 antd.variable.less 破坏换肤 |
| 放弃「图片挪出 public」 | 生产 nginx 反代 /image 到后端，dist 图片拷贝本就无人访问，迁移收益≈0、代价大 |

## 遇到的错误

| 错误 | 尝试次数 | 解决方案 |
|------|---------|---------|
| dev 环境 /image/upload 404（迁移后） | 1 | 回退迁移，图片保留在 public/image |

## 备注
- 随着工作推进，将阶段状态从 `pending` 更新为 `in_progress`，再更新为 `complete`。
- 做重大决策前，重新读取目标和下一步。
- 及时记录错误，避免重复失败的方法。
