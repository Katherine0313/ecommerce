// ==================== 导入路由实例 ====================
// 必须导入同目录 index.js 导出的同一个 router 实例
// 否则 beforeEach 守卫无法生效（守卫必须注册在使用的实例上）
import router from './index.js'
import { getToken, removeToken } from '@/utils/token'
import { getCurrentUser, hasPermi } from '@/utils/auth'
import { message } from 'ant-design-vue'

/** 解析 JWT 的 exp（秒）；解析失败交给后续 payload 校验 */
function isTokenExpired(token) {
    try {
        const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
        // base64url 可能缺 padding，补齐后再 atob
        const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=')
        const payload = JSON.parse(atob(padded))
        return payload.exp ? payload.exp * 1000 <= Date.now() : false
    } catch (e) {
        return true
    }
}

/** 拼接父子路由路径 */
function resolvePath(base, path) {
    if (!path) return base || '/'
    if (path.startsWith('/')) return path
    if (path.startsWith(':')) return base || '/'
    return `${base === '/' ? '' : base}/${path}`
}

/** 扁平化静态路由，收集需要登录、有组件的叶子页面 */
function collectLeafRoutes(routes, basePath = '', acc = []) {
    (routes || []).forEach((route) => {
        if (!route || !route.path) return
        const fullPath = resolvePath(basePath, route.path)
        if (route.children && route.children.length) {
            collectLeafRoutes(route.children, fullPath, acc)
            return
        }
        if (route.component && route.meta?.isLogin && route.meta?.renderMenu !== false) {
            acc.push({ path: fullPath, permission: route.meta.permission })
        }
    })
    return acc
}

/**
 * 找第一个当前账号有权限的页面，避免缺少某个权限时在 /workplace 上死循环。
 * 返回 null 表示没有任何可访问页面。
 */
function firstAccessiblePath(currentPath) {
    const leaves = collectLeafRoutes(router.options.routes)
    const target = leaves.find((route) =>
        route.path !== currentPath
        && (!route.permission || hasPermi(route.permission)))
    return target ? target.path : null
}

/** 清掉 token 并记录原因，交给登录页展示 */
function clearLogin(reason) {
    removeToken()
    sessionStorage.setItem('loginMessage', reason)
    // 守卫里直接弹一次：被踢回登录页是同路由跳转，Login.vue 的 onMounted 不会重跑，
    // 只靠 sessionStorage 拿不到提示，用户就只看到「登录成功」却不知道发生了什么。
    try { message.warning(reason) } catch (e) { /* antd 未就绪时忽略 */ }
}

/**
 * 路由全局守卫(beforeEach:在每次路由跳转之前执行)
 * 作用：实现登录权限控制，防止未登录用户访问需要权限的页面
 */
router.beforeEach((to, from, next) => {
    // 从 sessionStorage 读取 token（用户登录成功后的身份凭证，关掉页面即失效）
    let token = getToken()
    // token 过期后先清掉，避免刷新页面时先闪一下后台再被 401 踢走
    if (token && isTokenExpired(token)) {
        clearLogin('登录已过期，请重新登录')
        token = null
    }
    // 兼容历史 token：缺少 permissions 字段或不是管理员令牌时直接清掉，
    // 否则权限校验永远失败，会在登录页/工作台之间无限重定向。
    if (token) {
        const user = getCurrentUser()
        if (!user || user.type !== 'admin' || !Array.isArray(user.permissions)) {
            clearLogin('登录状态异常，请重新登录')
            token = null
        }
    }
    // 1. 已登录访问登录页：去第一个有权限的页面；没有可访问页面则退出登录
    if (to.name === 'Login' && token) {
        const fallback = firstAccessiblePath(to.path)
        if (fallback) {
            next(fallback)
        } else {
            clearLogin('当前账号没有可访问的页面，请联系管理员')
            next()
        }
    }
    // 2. 页面需要登录但没有 token：回登录页
    else if (to.meta.isLogin && !token) {
        next('/front/login')
    }
    // 3. 已登录：细粒度权限校验（菜单 meta.permission）
    else if (to.meta && to.meta.permission && !hasPermi(to.meta.permission)) {
        const fallback = firstAccessiblePath(to.path)
        if (fallback) {
            next(fallback)
        } else {
            clearLogin('当前账号没有可访问的页面，请联系管理员')
            next('/front/login')
        }
    }
    // 4. 其他所有正常情况直接放行
    else {
        next()
    }
})
