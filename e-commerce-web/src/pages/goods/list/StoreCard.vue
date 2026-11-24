<template>
  <!-- 店铺信息卡片：商品预览 / 购物车 / 结算三处共用 -->
  <div class="store-card">
    <img :src="`${baseUrl}image/store-logo.webp`" class="store-avatar" />
    <div class="store-info">
      <div class="store-name-row">
        <span class="store-name">{{ shopName }}</span>

      </div>
      <div class="store-subtitle">自营店铺 · 正品保障</div>
    </div>
    <div class="store-card-right">
      <span class="store-badge">官方认证</span>
      <a-button class="store-enter-btn">进入店铺</a-button>
    </div>
  </div>
</template>

<script setup>
import {computed} from 'vue'
import {shop} from '@/utils/shop'

const baseUrl = import.meta.env.BASE_URL
// 单店模式：店名直接取静态店铺配置
const shopName = computed(() => shop.value?.name || '店铺')
</script>

<style scoped lang="less">
.store-card { /* 卡片容器 */
  display: flex;
  align-items: center;
  gap: 20px;
  background: #fff;
  border-radius: 18px;
  box-shadow: 0 8px 30px rgba(0, 0, 0, .05);
  padding: 4px 18px;
  margin-bottom: 8px;
}

.store-avatar { /* 圆形 Logo：72×72 + 浅灰描边 */
  max-width: 52px;
  height: 52px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid #e8e8e8;
  flex-shrink: 0;
}

.store-info { /* Logo 右侧信息区（店名+Badge+副标题） */
  flex: 1;
  min-width: 0; /* 关键：允许 flex 子项收缩，否则沙箱长店名会把卡片撑破 */
}

.store-name-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.store-name { /* 店铺名称：沙箱店名较长（杰米小店（体验 #N）），单行省略号防挤坏布局 */
  flex: 1;
  min-width: 0;
  font-size: 22px;
  font-weight: bold;
  color: #222;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.store-badge { /* 橙色官方认证 Badge：店名省略时不能被压扁 */
  display: inline-block;
  flex-shrink: 0;
  font-size: 12px;
  color: #FF6700;
  background: #FFF3E6;
  border: 1px solid #FFB380;
  border-radius: 999px;
  padding: 4px 12px;
  line-height: 1.6;
  text-align: center;
}

.store-subtitle { /* 灰色副标题 */
  font-size: 14px;
  color: #999;
}

.store-enter-btn { /* 右侧进入店铺按钮 */
  flex-shrink: 0;
  height: 36px;
  padding: 0 28px;
  border-radius: 999px;
  background: #fff;
  color: #FF6700;
  border: 1px solid #FF6700;
  font-size: 15px;
  cursor: pointer;
  transition: background .2s;
  margin-top:10px;
}
.store-card-right{
  display: flex;
  flex-direction: column;
}
.store-enter-btn:hover {
  background: #FFF3E6;
}

</style>
