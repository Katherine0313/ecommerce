<template>
  <a-modal v-model:visible="placeVisible" :style="{top:'300px'}" :width="800" title="选择所在地区">
    <div class="place-select">
      <a-select placeholder="省" v-model:value="province" @change="onProvinceChange" :loading="loading">
        <a-select-option v-for="item in provinces" :key="item.code" :value="item.name">
          {{ item.name }}
        </a-select-option>
      </a-select>
      <a-select placeholder="市" v-model:value="city" @change="onCityChange" :loading="loading">
        <a-select-option v-for="item in cityOptions" :key="item" :value="item">
          {{ item }}
        </a-select-option>
      </a-select>
      <a-select placeholder="区" v-model:value="district">
        <a-select-option v-for="item in districtOptions" :key="item" :value="item">
          {{ item }}
        </a-select-option>
      </a-select>
    </div>
    <template #footer>
      <a-button @click="placeVisible = false">取消</a-button>
      <a-button type="primary" @click="onConfirm">确定</a-button>
    </template>
  </a-modal>
</template>
<script setup>
import { ref } from 'vue'

const emit = defineEmits(['confirm'])
const placeVisible = ref(false)

const placeVisibleOpen = () => { placeVisible.value = true }
defineExpose({ placeVisibleOpen })

// ========== 省份列表（挂载时加载一次） ==========
const provinces = ref([])
const loading = ref(false)

fetch('/region/provinces.json')
  .then(res => res.json())
  .then(data => { provinces.value = data })

// ========== 市、区数据 ==========
const province = ref(undefined)
const city = ref(undefined)
const district = ref(undefined)

const cityData = ref({})        // 当前省的完整数据 { 市名: [区名数组] }
const cityOptions = ref([])     // 当前省下的市列表
const districtOptions = ref([]) // 当前市下的区列表

// 选省 → fetch 该省 JSON
const onProvinceChange = (val) => {
  city.value = undefined
  district.value = undefined
  cityOptions.value = []
  districtOptions.value = []

  if (!val) return
  loading.value = true
  fetch(`/region/${encodeURIComponent(val)}.json`)
    .then(res => res.json())
    .then(data => {
      cityData.value = data
      cityOptions.value = Object.keys(data)
    })
    .finally(() => { loading.value = false })
}

// 选市 → 筛选区列表
const onCityChange = (val) => {
  district.value = undefined
  districtOptions.value = val ? cityData.value[val] || [] : []
}

// ========== 确定 ==========
const onConfirm = () => {
  const result = [province.value, city.value, district.value].filter(Boolean).join(' ')
  emit('confirm', result)
  placeVisible.value = false
}
</script>

<style scoped>
.place-select {
  display: flex;
  gap: 10px;
  padding-left: 100px;
  padding-bottom: 20px;
}
.place-select :deep(.ant-select-selector) {
  width: 120px;
}

/* 手机：三个下拉纵向排列并铺满 */
@media (max-width: 767px) {
  .place-select {
    flex-direction: column;
    padding-left: 0;
  }
  .place-select :deep(.ant-select-selector) {
    width: 100%;
  }
}
</style>
