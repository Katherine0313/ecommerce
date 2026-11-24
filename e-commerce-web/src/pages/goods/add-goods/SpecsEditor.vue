<template>
  <div class="specs-editor">
    <div class="specs-toolbar">
      <a-button size="small" type="dashed" @click="addRow" :disabled="specsForm.length >= MAX_ITEMS">
        <PlusOutlined />
        添加参数
      </a-button>
    </div>
    <div class="specs-row" v-for="(row, index) in specsForm" :key="row.key">
      <a-input
          v-if="row.fixed"
          :value="row.name"
          disabled
          class="specs-name"
      />
      <a-input
          v-else
          v-model:value="row.name"
          placeholder="参数名，如 屏幕尺寸"
          :maxlength="MAX_NAME_LENGTH"
          class="specs-name"
      />
      <a-select
          v-if="row.fixed && row.type === TYPE_SELECT && row.options.length"
          v-model:value="row.value"
          :options="selectOptions(row)"
          allow-clear
          placeholder="请选择"
          class="specs-value"
      />
      <a-input-number
          v-else-if="row.fixed && row.type === TYPE_NUMBER"
          v-model:value="row.value"
          placeholder="已自动带出默认值，可微调"
          class="specs-value"
      />
      <a-input
          v-else
          v-model:value="row.value"
          :placeholder="`参数值${row.name ? '，如 ' + row.name : ''}`"
          :maxlength="MAX_VALUE_LENGTH"
      />
      <a-button type="text" danger @click="removeRow(index)">
        <DeleteOutlined />
      </a-button>
    </div>
    <div class="specs-empty" v-if="!specsForm.length">
      暂无规格参数：选择商品分类会自动带出模板，也可点「添加参数」手动录入
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { message } from 'ant-design-vue'
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons-vue'

// 与后端 ProductSpecsCodec 的上限保持一致
const MAX_ITEMS = 30
const MAX_NAME_LENGTH = 32
const MAX_VALUE_LENGTH = 128

// 模板项类型：文本 / 数字 / 选项（与后端 SpecTemplateItem 常量一致）
const TYPE_TEXT = 'text'
const TYPE_NUMBER = 'number'
const TYPE_SELECT = 'select'

// 行结构：{ key, name, value, fixed, type, options }；fixed 为分类模板字段，参数名只读
const specsForm = ref([])
let keySeq = 0

const makeRow = ({ name = '', value = '', fixed = false, type = TYPE_TEXT, options = [] } = {}) => ({
  key: ++keySeq,
  name,
  value,
  fixed,
  type,
  options,
})

const isNumeric = (value) => value !== '' && value != null && Number.isFinite(Number(value))

/** 历史模板是纯字符串，统一成模板项结构，避免旧数据不兼容 */
const normalizeTemplate = (template) => (template || [])
    .map((item) => {
      if (typeof item === 'string') {
        return { name: item, type: TYPE_TEXT, defaultValue: '', options: [] }
      }
      return {
        name: item?.name || '',
        type: item?.type || TYPE_TEXT,
        defaultValue: item?.defaultValue ?? '',
        options: Array.isArray(item?.options) ? item.options : [],
      }
    })
    .map((item) => ({ ...item, name: (item.name || '').trim() }))
    .filter((item) => item.name)

/** 模板默认值转成控件需要的值：数字类型转 number，其余保持字符串 */
const defaultValueOf = (item) => {
  if (item.type === TYPE_NUMBER) {
    return isNumeric(item.defaultValue) ? Number(item.defaultValue) : undefined
  }
  return item.defaultValue
}

/**
 * 按分类模板重建规格行：
 * - 模板项固定在前、参数名只读，值优先保留已填写的，未填过的自动用模板默认值
 * - 数字类型遇到非数字旧值时降级为文本，避免控件显示异常
 * - 模板之外的旧参数（历史数据）和用户自定义参数继续保留为可编辑行，避免丢数据
 */
const applyTemplate = (template) => {
  const items = normalizeTemplate(template)
  const names = items.map((item) => item.name)
  const current = specsForm.value
  const rows = items.map((item) => {
    const hit = current.find((row) => row.name === item.name)
    const useNumber = item.type === TYPE_NUMBER && (!hit || isNumeric(hit.value))
    const value = useNumber
        ? (hit ? (isNumeric(hit.value) ? Number(hit.value) : undefined) : defaultValueOf(item))
        : (hit ? hit.value : defaultValueOf(item))
    return makeRow({
      name: item.name,
      value,
      fixed: true,
      type: useNumber ? TYPE_NUMBER : (item.type === TYPE_SELECT ? TYPE_SELECT : TYPE_TEXT),
      options: item.options,
    })
  })
  const rest = current
      .filter((row) => !names.includes(row.name))
      .map((row) => makeRow({ name: row.name, value: row.value }))
  specsForm.value = [...rows, ...rest].slice(0, MAX_ITEMS)
}

const selectOptions = (row) => row.options.map((option) => ({ label: option, value: option }))

// 编辑回填：先恢复已存参数，再按分类模板重建
const setSpecs = (list, template) => {
  specsForm.value = (list || []).map((item) => makeRow({
    name: item.name || '',
    value: item.value || '',
  }))
  if (template && template.length) {
    applyTemplate(template)
  }
}

const addRow = () => {
  if (specsForm.value.length >= MAX_ITEMS) {
    message.warning(`规格参数最多 ${MAX_ITEMS} 项`)
    return
  }
  specsForm.value.push(makeRow())
}

const removeRow = (index) => {
  specsForm.value.splice(index, 1)
}

// 只校验自定义参数名；模板字段非必填，值留空即忽略
const validate = () => {
  const invalid = specsForm.value.find((row) => !row.fixed && !(row.name || '').trim())
  if (invalid) {
    message.warning('自定义规格参数的参数名不能为空')
    return false
  }
  return true
}

// 提交用：丢掉空值项（模板项非必填），名称和值都 trim
const getSpecs = () => specsForm.value
    .map((row) => ({
      name: (row.name || '').trim(),
      value: (row.value ?? '').toString().trim(),
    }))
    .filter((item) => item.name && item.value)

const resetForm = () => {
  specsForm.value = []
}

defineExpose({ specsForm, applyTemplate, setSpecs, addRow, removeRow, validate, getSpecs, resetForm })
</script>

<style scoped>
.specs-editor {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.specs-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.specs-row {
  display: grid;
  grid-template-columns: 220px 1fr 32px;
  align-items: center;
  gap: 8px;
}

.specs-value {
  width: 100%;
}

.specs-empty {
  color: #bfbfbf;
  font-size: 12px;
}
</style>
