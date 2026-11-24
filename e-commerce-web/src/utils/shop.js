// ==================== 店铺信息（单店模式，静态配置） ====================
// 多租户沙箱已下线，店铺信息不再请求后端，统一用这里的静态对象。
// 顶栏、商品预览/购物车/结算里的店铺卡片都从这里取店名。
import {ref} from 'vue'

export const shop = ref({
  id: 1,
  // 需要按环境改名时在 .env.development / .env.production 配 VITE_SHOP_NAME
  name: import.meta.env.VITE_SHOP_NAME || '杰米小店',
  isTemplate: 1,
})
