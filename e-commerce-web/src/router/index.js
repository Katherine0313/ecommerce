// 从vue-router库导入两个核心函数：创建路由实例、创建history模式路由
import {createRouter, createWebHistory} from 'vue-router'

/**
 * createRouter：用于创建路由管理器实例，管理页面跳转、路由守卫、路由匹配等核心功能
 * createWebHistory：创建HTML5 History模式的路由模式
 特点：地址栏不带 # 哈希符号，标准干净URL，需要后端配置支持刷新不404
 对比 createWebHashHistory：哈希模式自带#，无需后端配置
 */


const routes = [
    {
        path: '/',
        redirect: '/workplace',
        meta: {
            renderMenu: false,
        },
    },
    {
        // 登录页独立成整屏路由（view: blank 时 stepin 不渲染后台布局），不再套 FrontView 的营销头部
        path: '/front/login',
        name: 'Login',
        meta: {
            icon: 'LoginOutlined',
            view: 'blank',
            target: '_blank',
            cacheable: false,
            // 登录页是整屏独立页面，不进入侧边导航
            renderMenu: false,
        },
        component: () => import('@/pages/login'),
    },
    {
        path: '/front',
        name: '前端',
        meta: {
            renderMenu: false,
        },
        component: () => import('@/components/layout/FrontView.vue'),
        children: [
            {
                path: 'home',
                name: 'Home',
                meta: {
                    view: 'blank',
                },
                component: () => import('@/pages/home'),
            },
        ],
    },
    {
        path: '/workplace',
        name: 'Workplace',
        meta: {
            isLogin: true,
            icon: 'DashboardOutlined',
            title: '工作台',
            permission: 'workplace:view',
            renderMenu: true,
            cacheable: true,
        },
        component: () => import('@/pages/workplace/Workplace.vue'),
    },
    {
        path: '/goods',
        name: 'Goods',
        meta: {
            isLogin: true,
            title: '商品管理',
            icon: 'ReconciliationOutlined',
            renderMenu: true,
            cacheable: true,
        },
        children: [
            {
                path: 'list',
                name: 'List',
                meta: {
                    title: '商品列表',
                    permission: 'product:product:list',
                },
                component: () => import('@/pages/goods/list/List.vue')
            },
            {
                path: 'add-goods',
                name: 'addGoods',
                meta: {
                    title: '添加商品',
                    permission: 'product:product:add',
                },
                component: () => import('@/pages/goods/add-goods/AddGoods.vue')
            },
            {
                path: 'specs',
                name: 'Specs',
                meta: {
                    title: '销售规格库',
                    permission: 'product:product:list',
                },
                component: () => import('@/pages/goods/spec/SpecList.vue')
            },
            {
                path: 'categories',
                name: 'Categories',
                meta: {
                    title: '商品分类',
                    permission: 'product:category:list',
                },
                component: () => import('@/pages/goods/category/Category.vue')
            },
            {
                path: 'tag',
                name: 'Tag',
                meta: {
                    title: '商品标签',
                    permission: 'product:tag:list',
                },
                component: () => import('@/pages/goods/tag/Tag.vue')
            },
        ]
    },
    {
        path: '/orders',
        name: 'Orders',
        meta: {
            isLogin: true,
            title: '订单管理',
            icon: 'ShoppingCartOutlined',
            renderMenu: true,
            cacheable: true,
        }, children: [
            {
                path: 'order-list',
                name: 'OrderList',
                meta: {
                    title: '订单列表',
                    permission: 'order:order:list',
                },
                component: () => import('@/pages/orders/OrderList.vue'),
            },
            {
                path: 'review',
                name: 'Review',
                meta: {
                    title: '商品评价',
                    permission: 'product:review:list',
                },
                component: () => import('@/pages/orders/review/ReviewList.vue'),
            }
            ]
    },
    {
        path: '/delivery',
        name: 'Delivery',
        meta: {
            isLogin: true,
            title: '发货管理',
            icon: 'SendOutlined',
            renderMenu: true,
            cacheable: true,
        }, children: [
            {
                path: 'list',
                name: 'DeliveryList',
                meta: {
                    title: '快递发货列表',
                    permission: 'order:delivery:list',
                },
                component: () => import('@/pages/delivery/DeliveryList.vue'),
            },
            {
                path: 'express',
                name: 'ExpressList',
                meta: {
                    title: '快递公司',
                    permission: 'order:express:list',
                },
                component: () => import('@/pages/delivery/ExpressList.vue'),
            }
            ]
    },
    {
        path: '/after-sales',
        name: 'AfterSales',
        meta: {
            isLogin: true,
            title: '售后管理',
            icon: 'CustomerServiceOutlined',
            renderMenu: true,
            cacheable: true,
        }, children: [
            {
                path: 'after-sale-list',
                name: 'AfterSaleList',
                meta: {
                    title: '售后订单',
                    permission: 'aftersale:aftersale:list',
                },
                component: () => import('@/pages/after-sales/AfterSaleList.vue'),
            }
        ]
    },
    {
        path: '/stock',
        name: 'Stock',
        meta: {
            isLogin: true,
            title: '库存管理',
            icon: 'InboxOutlined',
            renderMenu: true,
            cacheable: true,
        },children: [
            {
                path: 'dashboard',
                name: 'StockDashboard',
                meta: { title: '库存看板', permission: 'stock:dashboard:view' },
                component: () => import('@/pages/stock/Dashboard.vue')
            },
            {
                path: 'stock-list',
                name: 'SkuStockList',
                meta: { title: 'SKU 库存', permission: 'product:sku-stock:list' },
                component: () => import('@/pages/stock/StockList.vue')
            },
            {
                path: 'transactions',
                name: 'StockTransactions',
                meta: { title: '库存流水', permission: 'stock:transaction:view' },
                component: () => import('@/pages/stock/Transactions.vue')
            },
            {
                path: 'receipts',
                name: 'StockReceipts',
                meta: { title: '入库单', permission: 'stock:receipt:manage' },
                component: () => import('@/pages/stock/Receipts.vue')
            },
            {
                path: 'shipments',
                name: 'StockShipments',
                meta: { title: '出库单', permission: 'stock:shipment:view' },
                component: () => import('@/pages/stock/Shipments.vue')
            },
            {
                path: 'adjustments',
                name: 'StockAdjustments',
                meta: { title: '调整单', permission: 'stock:adjust:manage' },
                component: () => import('@/pages/stock/Adjustments.vue')
            }
        ]
        },
    {
        path: '/business',
        name: 'Business',
        meta: {
            isLogin: true,
            title: '经营工具',
            icon: 'FundOutlined',
            renderMenu: true,
            cacheable: true,
        },
        children: [
            {
                path: 'promotion',
                name: 'Promotion',
                meta: {
                    title: '促销活动',
                    permission: 'business:promotion:list'
                },
                component: () => import('@/pages/business/promotion/PromotionList.vue'),
            },
            {
                path: 'banner',
                name: 'Banner',
                meta: {
                    title: 'Banner管理',
                    permission: 'business:banner:list'
                },
                component: () => import('@/pages/business/banner/BannerList.vue'),
            },
            {
                path: 'article',
                name: 'Article',
                meta: {
                    title: '文章管理',
                    permission: 'business:article:list'
                },
                component: () => import('@/pages/business/article/ArticleList.vue'),
            },
            {
                path: 'coupon',
                name: 'Coupon',
                meta: {
                    title: '优惠券',
                    permission: 'business:coupon:list'
                },
                component: () => import('@/pages/business/coupon/CouponList.vue'),
            },
            {
                path: 'member',
                name: 'Member',
                meta: {
                    title: '会员',
                    permission: 'business:member:list'
                },
                component: () => import('@/pages/business/member/MemberList.vue'),
            }
        ]
        },
    {
        path: '/finance',
        name: 'Finance',
        meta: {
            isLogin: true,
            title: '财务管理',
            icon: 'AccountBookOutlined',
            renderMenu: true,
            cacheable: true,
        },
        children: [
            {
                path: 'dashboard',
                name: 'FinanceDashboard',
                meta: {
                    title: '财务看板',
                    permission: 'finance:dashboard:view',
                },
                component: () => import('@/pages/finance/dashboard/FinanceDashboard.vue'),
            },
            {
                path: 'receipt-list',
                name: 'FinanceReceiptList',
                meta: {
                    title: '收款单',
                    permission: 'finance:receipt:list',
                },
                component: () => import('@/pages/finance/receipt/ReceiptList.vue'),
            },
            {
                path: 'account-list',
                name: 'FinanceAccountList',
                meta: {
                    title: '收款账户',
                    permission: 'finance:account:list',
                },
                component: () => import('@/pages/finance/account/AccountList.vue'),
            }
        ]
        },
    {
        path: '/system',
        name: 'System',
        meta: {
            isLogin: true,
            title: '系统管理',
            icon: 'SettingOutlined',
            renderMenu: true,
            cacheable: true,
        },
        children: [
            // 分组节点只用于侧边栏归组，不承载页面；子页用绝对路径，保证 URL 与 admin_menu.path 不变
            {
                path: 'permission',
                name: 'SystemPermission',
                meta: {
                    title: '权限管理',
                    icon: 'SafetyCertificateOutlined',
                },
                children: [
                    {
                        path: '/system/users',
                        name: 'SystemUsers',
                        meta: {
                            title: '管理员账号',
                            permission: 'system:user:list',
                        },
                        component: () => import('@/pages/system/users/UserList.vue'),
                    },
                    {
                        path: '/system/roles',
                        name: 'SystemRoles',
                        meta: {
                            title: '角色管理',
                            permission: 'system:role:list',
                        },
                        component: () => import('@/pages/system/roles/RoleList.vue'),
                    },
                    {
                        path: '/system/menus',
                        name: 'SystemMenus',
                        meta: {
                            title: '菜单管理',
                            permission: 'system:menu:list',
                        },
                        component: () => import('@/pages/system/menus/MenuList.vue'),
                    }
                ]
            },
            {
                path: 'log',
                name: 'SystemLog',
                meta: {
                    title: '日志管理',
                    icon: 'FileTextOutlined',
                },
                children: [
                    // 路径与 admin_menu.path 保持一致（/system/logs、/system/logs/login）
                    {
                        path: '/system/logs',
                        name: 'SystemLogs',
                        meta: {
                            title: '操作日志',
                            permission: 'system:log:list',
                        },
                        component: () => import('@/pages/system/logs/OperationLogList.vue'),
                    },
                    {
                        path: '/system/logs/login',
                        name: 'SystemLoginLogs',
                        meta: {
                            title: '登录日志',
                            permission: 'system:log:login:list',
                        },
                        component: () => import('@/pages/system/logs/LoginLogList.vue'),
                    }
                ]
            },
            {
                path: 'develop',
                name: 'SystemDevelop',
                meta: {
                    title: '开发管理',
                    icon: 'ToolOutlined',
                },
                children: [
                    {
                        path: '/system/database',
                        name: 'SystemDatabase',
                        meta: {
                            title: '数据库管理',
                            permission: 'system:db:list',
                        },
                        component: () => import('@/pages/system/database/DatabaseManage.vue'),
                    },
                    {
                        path: '/system/api',
                        name: 'SystemApiDocs',
                        meta: {
                            title: '接口管理',
                            permission: 'system:api:list',
                        },
                        component: () => import('@/pages/system/api/ApiManage.vue'),
                    }
                ]
            }
        ]
    },
    {
        // 系统监控：独立一级目录，与「系统管理」平级；路径与 admin_menu.path 保持一致
        path: '/monitor',
        name: 'Monitor',
        meta: {
            isLogin: true,
            title: '系统监控',
            icon: 'MonitorOutlined',
            renderMenu: true,
            cacheable: true,
        },
        children: [
            {
                path: 'server',
                name: 'MonitorServer',
                meta: {
                    title: '服务监控',
                    permission: 'monitor:server:list',
                },
                component: () => import('@/pages/monitor/server/ServerMonitor.vue'),
            },
            {
                path: 'online',
                name: 'MonitorOnline',
                meta: {
                    title: '在线用户',
                    permission: 'monitor:online:list',
                },
                component: () => import('@/pages/monitor/online/OnlineUserList.vue'),
            },
            {
                path: 'cache',
                name: 'MonitorCache',
                meta: {
                    title: '缓存监控',
                    permission: 'monitor:cache:list',
                },
                component: () => import('@/pages/monitor/cache/CacheMonitor.vue'),
            },
            {
                path: 'task',
                name: 'MonitorTask',
                meta: {
                    title: '定时任务',
                    permission: 'monitor:task:list',
                },
                component: () => import('@/pages/monitor/task/TaskList.vue'),
            },
            {
                path: 'database',
                name: 'MonitorDatabase',
                meta: {
                    title: '数据库监控',
                    permission: 'monitor:db:list',
                },
                component: () => import('@/pages/monitor/database/DatabaseMonitor.vue'),
            }
        ]
    },
    {
        // 个人中心：登录后可访问，不出现在侧边栏，只有顶栏头像下拉是入口
        path: '/profile',
        name: 'Profile',
        meta: {
            isLogin: true,
            renderMenu: false,
            cacheable: false,
            title: '个人中心',
        },
        component: () => import('@/pages/profile/Profile.vue'),
    },
    {
        path: '/:pathMatch(.*)*',
        name: '404',
        props: true,
        meta: {
            icon: 'CreditCardOutlined',
            renderMenu: false,
            cacheable: false,
            _is404Page: true,
        },
        component: () => import('@/pages/Exp404.vue'),
    },
    {
        path: '/ai',
        name: 'Ai',
        meta: {
            isLogin: true,
            title: 'AI 客服',
            icon: 'RobotOutlined',
            renderMenu: true,
            cacheable: true,
        },
        children: [
            {
                path: 'chat',
                name: 'AiChat',
                meta: {
                    title: 'AI 对话',
                    permission: 'ai:chat:use',
                },
                component: () => import('@/pages/ai/AiChat.vue'),
            },
            {
                path: 'copy',
                name: 'AiCopy',
                meta: {
                    title: '商品文案助手',
                    permission: 'ai:copy:generate',
                },
                component: () => import('@/pages/ai/CopyAssistant.vue'),
            },
            {
                path: 'human',
                name: 'AiHumanService',
                meta: {
                    title: '人工客服',
                    permission: 'ai:human:use',
                },
                component: () => import('@/pages/ai/HumanService.vue'),
            },
            {
                path: 'kb',
                name: 'AiKbManage',
                meta: {
                    title: '知识库',
                    permission: 'ai:chat:use',
                },
                component: () => import('@/pages/ai/KbManage.vue'),
            }
        ]
    },
];
// ==================== 路由实例创建 ====================
// 1. 创建一个【真正能用的路由工具】
const router = createRouter({
    // 2.设置网址风格
    history: createWebHistory(import.meta.env.BASE_URL),
    // 3.把你写的【路线地图】交给路由工具，让它认识所有路径
    routes
})
export default router;
