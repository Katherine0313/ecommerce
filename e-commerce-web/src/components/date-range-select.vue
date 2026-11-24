<template>
  <div class="date-range-select">
    <a-select v-model:value="field" style="width: 110px" @change="onChange">
      <a-select-option v-for="opt in options" :key="opt.value" :value="opt.value">{{ opt.label }}</a-select-option>
    </a-select>
    <a-range-picker v-model:value="range" :show-time="{ format: 'HH:mm' }" style="margin-left: 8px"
                    @change="onChange" :placeholder="['开始日期', '结束日期']" />
  </div>
</template>
<script setup>
import {ref} from 'vue'

const props = defineProps({
  // 时间字段选项（value 为 DB 列名，label 为展示文案），默认下单/支付/发货时间
  options: {
    type: Array,
    default: () => [
      { value: 'created_at', label: '下单时间' },
      { value: 'pay_at', label: '支付时间' },
      { value: 'ship_at', label: '发货时间' },
    ],
  },
})

const emit = defineEmits(['change'])

const field = ref(props.options[0]?.value)
const range = ref([])

const onChange = () => {
  emit('change', {
    dateField: field.value,
    startDate: range.value?.[0]?.format('YYYY-MM-DD HH:mm') || '',
    endDate: range.value?.[1]?.format('YYYY-MM-DD HH:mm') || '',
  })
}

// 清空（刷新时重置）
const reset = () => {
  field.value = props.options[0]?.value
  range.value = []
  onChange()
}

defineExpose({ reset })
</script>
<style scoped>
.date-range-select {
  display: inline-flex;
  align-items: center;
  padding:4px;
}
:deep(.ant-picker-range){
  padding:4px;
}
</style>
