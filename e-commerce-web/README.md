# 杰米多多 · 后台管理系统（e-commerce）

「杰米多多」电商的 **PC 端后台管理系统**。管理端使用 `/api/admin/**` 路径、JWT `type=admin`，与 C 端（`/api/**`、`type=user`）完全分开。

线上地址 `https://www.jiemids.com`

## 仓库清单

| 仓库 | 内容 |
|---|---|
| **[e-commerce](https://github.com/Katherine0313/e-commerce.git)** | **后台管理系统（Vue 3 + Ant Design Vue），本仓库** |
| [e-commerce-boot](https://github.com/Katherine0313/e-commerce-boot.git) | Java 后端微服务（核心） |
| [ecommerce-mall](https://github.com/Katherine0313/ecommerce-mall.git) | PC 商城前端（Vue 3 + Vite） |
| [e-commerce-uniapp](https://github.com/Katherine0313/e-commerce-uniapp.git) | 微信小程序（uni-app） |
| [ecommerce-python](https://github.com/Katherine0313/ecommerce-python.git) | AI 客服服务（Python FastAPI + LangGraph + 知识库检索） |

> 本项目原本是 Vue3 + Ant Design Vue 的通用中后台模板（配套 Node + SQLite 后端）。2026-10 起后端整体迁到 Spring Boot 微服务，前端逐模块对接到 `/api/admin/**`，模板自带的 Express/SQLite 相关内容已全部移除。

## 技术栈

| | |
|---|---|
| 框架 | Vue 3（`<script setup>`） |
| 构建 | Vite 5 |
| UI | Ant Design Vue 4 + 自定义主题 |
| 状态 | Pinia |
| 路由 | Vue Router（动态路由 + 按钮级权限指令） |
| 请求 | Axios |
| 图表 | ECharts |
| 富文本 | TinyMCE / WangEditor |
| 样式 | Tailwind CSS + Less |

## 功能模块

| 路由 | 模块 | 内容 |
|---|---|---|
| `/workplace` | 工作台 | 销售统计、订单量、待办 |
| `/goods` | 商品 | 商品管理、分类、规格模板、评价、Banner、文章、标签 |
| `/orders` | 订单 | 订单列表、发货与包裹管理 |
| `/after-sales` | 售后 | 售后审核：仅退款 / 退货退款 / 换货补发 |
| `/stock` | 库存 | 库存预警、库存看板、库存流水、入出库单据 |
| `/delivery` | 配送 | 快递公司与运费模板 |
| `/business` | 业务 | 会员、优惠券、促销活动、收藏、评价管理 |
| `/finance` | 财务 | 收款账户、收款单与审核 |
| `/ai` | AI | AI 客服工作台、文案助手、知识库导入、AI 生图 |
| `/system` | 系统 | 管理员、角色、菜单、操作日志、登录日志、接口管理、数据库管理 |
| `/monitor` | 监控 | 服务器、缓存、在线用户、动态定时任务、慢 SQL |

## 目录

```
src/
├── main.js             入口
├── api/                接口封装（按业务模块：goods/orders/stock/ai/system…）
├── assets/             图片与静态资源
├── components/         通用组件
├── composables/        组合式函数
├── directives/         自定义指令（v-permission 按钮级权限）
├── pages/              页面（按模块分目录）
├── plugins/            插件注册
├── router/             路由表 + 权限守卫
├── store/              Pinia 状态
├── theme/              主题
└── utils/              请求封装、鉴权、格式化
docs/
├── planning/           规划三件套：findings / task_plan / progress
├── ai-module.md        后台 AI 模块方案
├── system-management.md 系统管理模块（数据库 / 接口管理）方案
├── ddos-attack-and-defense.md   攻击防范说明
└── sandbox-and-launch.md        沙箱与上线手册（已废弃，仅存档）
```

## 本地运行

```bash
npm install
npm run dev        # 开发
npm run build      # 构建到 dist/
npm run preview    # 预览构建产物
```

接口地址通过环境变量配置（`.env` / `.env.production`，均已 gitignore）：

| 变量 | 说明 |
|---|---|
| `VITE_API_BASE_URL` | 后端接口前缀。**留空 = 同源相对路径**，由 Nginx 反代到网关 `127.0.0.1:8080`，不触发 CORS |
| `VITE_SHOP_NAME` | 页面标题用的店铺名 |

> `VITE_*` 变量会被打包进前端产物，属于公开配置，不要往里放密钥。
>
> WebSocket 连 `wss://<当前域名>/ws/order?token=...`，Nginx 需要同时反代 `/ws/` 并带 `Upgrade` 头。

## 权限与登录

- 登录：`POST /api/admin/login`，需带图形验证码（`captchaId` / `captchaCode`，300 秒 / 5 分钟一次性）
- 失败锁定：同一「用户名 + IP」连续错 5 次锁 10 分钟
- 令牌存在 **sessionStorage**（不是 localStorage）：关掉标签页即登出，离开工位不会残留登录态
- 菜单与按钮权限由后端下发，`v-permission` 指令控制按钮级显示；前端路由守卫拦截无权限直链
- 「记住密码」功能独立存在，用 localStorage 存 30 天

## 部署

构建产物 `dist/` 由 `e-commerce-boot` 的 `./deploy/release-web.sh` 发布（npm build → tar 上传 → rsync 到站点目录，保留 `.env` 等文件，自动留备份）。Nginx 配置见后端仓库的 `deploy/`。

> 后台端与 PC 商城**必须用各自的脚本** —— 后台管理端用 `release-web.sh`，PC 商城用 `release-mall.sh`，漏带环境变量会发错站点。
