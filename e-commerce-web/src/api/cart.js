import axios from './request.js'

// 获取当前用户购物车
export function getCart() {
  return axios.get('/api/cart')
}

// 加入购物车
export function addCartItem(data) {
  return axios.post('/api/cart', data)
}

// 更新某个商品数量
export function updateCartItem(product_id, data) {
  return axios.put(`/api/cart/${product_id}`, data)
}

// 移除某个商品
export function removeCartItem(product_id) {
  return axios.delete(`/api/cart/${product_id}`)
}

// 清空购物车（可传 { product_ids: [...] } 批量删除）
export function clearCart(data) {
  return axios.delete('/api/cart', { data })
}
