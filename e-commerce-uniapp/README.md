# 杰米多多 · 微信小程序（e-commerce-uniapp）

「杰米多多」电商的 **微信小程序端**。与 PC 商城共用同一套后端数据（同一张 `user` 表、同一套 C 端业务接口），登录走 `wx.login()` 静默换 openid，免登录即可浏览商品。

后端主仓库是 [`e-commerce-boot`](https://github.com/Katherine0313/e-commerce-boot.git)（Java 微服务），线上接口地址 `https://api.jiemids.com`。

## 仓库清单

| 仓库 | 内容 |
|---|---|
| **[e-commerce-uniapp](https://github.com/Katherine0313/e-commerce-uniapp.git)** | **微信小程序（uni-app），本仓库** |
| [e-commerce-boot](https://github.com/Katherine0313/e-commerce-boot.git) | Java 后端微服务（核心） |
| [e-commerce](https://github.com/Katherine0313/e-commerce.git) | 后台管理系统（Vue 3 + Ant Design Vue） |
| [ecommerce-mall](https://github.com/Katherine0313/ecommerce-mall.git) | PC 商城前端（Vue 3 + Vite） |
| [ecommerce-python](https://github.com/Katherine0313/ecommerce-python.git) | AI 客服服务（Python FastAPI + LangGraph + 知识库检索） |

## 技术栈

| | |
|---|---|
| 框架 | uni-app + Vue 3 |
| UI | uView Plus + uni-ui |
| 状态 | Pinia |
| 小程序 appid | `wxa18a22c8c5c1fa12` |

## 功能

| 模块 | 说明 |
|---|---|
| 首页 | 轮播、分类入口、猜你喜欢、秒杀 |
| 商品 | 分类、列表、搜索、详情、评价、规格选择 |
| 交易 | 购物车、确认订单、支付、未支付提醒、订单列表与详情、物流轨迹 |
| 售后 | 申请售后、仅退款、退货退款、换货、售后进度 |
| 我的 | 个人资料、收货地址、收藏、优惠券、会员、反馈 |
| 其他 | 文章详情、客服浮窗（AI 客服 + 转人工） |

订单相关页面（确认 / 支付成功 / 待支付 / 待发货 / 待收货 / 物流 / 评价 / 申请售后 / 仅退款）走分包加载，首屏更轻。

## 目录

```
pages/
├── index/              首页
├── category/           分类
├── search/             搜索（分包）
├── order/              订单：列表/详情/确认/支付/物流/评价/售后
├── cart/               购物车
├── my/                 个人中心
├── after-sales/        售后
└── article/            文章详情

api/                    接口封装（product/order/cart/after-sale/agent…）
components/             通用组件（Guess、custom-swiper、floating-chat、order-card…）
store/                  Pinia 状态（用户、购物车…）
utils/                  请求封装、静默登录、格式化
static/                 图片与图标资源
docs/
├── planning/           规划三件套：findings / task_plan / progress
├── avatar-upload.md    头像上传闭环说明
└── images/             文档配图
```

关键根文件：`pages.json`（路由与分包）、`manifest.json`（小程序 appid 与平台配置）、`App.vue`（全局生命周期）、`main.js`（入口）、`uni.scss`（主题变量）。

## 本地运行

用 **HBuilderX** 打开本项目 → 运行 → 运行到小程序模拟器。

> ⚠ **「运行」包指向 `127.0.0.1:8080`（本地 gateway），「发行」包固定指向线上 `api.jiemids.com`** —— 想测本地服务必须用「运行」，用「发行」包测不到。

静默登录：启动时 `wx.login()` 换 openid，**浏览商品不需要登录**；加购、下单、查看个人数据才需要。

## 发布

小程序端用 HBuilderX 的「发行 → 小程序-微信」上传，再在微信公众平台提交审核。接口地址由 `manifest.json` / 构建环境决定，正式包固定指向线上。
