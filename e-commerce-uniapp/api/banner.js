import { request } from '../utils/server-api.js'

// ===== 轮播图接口 =====
// 免登录，游客也能访问（后端 WebMvcConfig 白名单放行）。

// 获取轮播图，对应 GET /api/banners?position=x
// position：1 小程序首页、2 小程序分类页、11 PC 商城首页
export function getBanners(position){
	return request('/api/banners?position=' + position, { method: 'GET' })
}

// Banner 点击量 +1，对应 POST /api/banners/{id}/browse
export function reportBannerBrowse(id){
	return request('/api/banners/' + id + '/browse', { method: 'POST' })
}
