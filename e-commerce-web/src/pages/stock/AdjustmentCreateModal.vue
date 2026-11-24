<template>
  <a-modal
      :visible="visible"
      title="新建调整单"
      :width="900"
      :confirm-loading="submitting"
      @ok="submit"
      @cancel="close"
      @update:visible="v => emit('update:visible', v)"
  >
    <a-form layout="vertical">
      <a-form-item label="备注（选填）">
        <a-textarea v-model:value="remark" :rows="2" show-count maxLength="200" />
      </a-form-item>
    </a-form>

    <div class="section-title">
      调整明细
      <span style="color: #8c8c8c; font-weight: 400; font-size: 12px; margin-left: 8px">
        填写调整后库存数量，差异为 0 的行将被忽略
      </span>
      <a-button type="link" style="padding-left: 8px" @click="addRow">+ 添加行</a-button>
    </div>
    <a-table :dataSource="rows" :pagination="false" size="small" row-key="rowKey" bordered>
      <a-table-column title="SKU 搜索">
        <template #default="{ record }">
          <!-- 选中后改用紧凑 div 展示：a-select 的回显文案太长会把整个表格列宽挤变形 -->
          <div v-if="record.skuId" class="sku-picked">
            <span class="sku-picked-name" :title="record.skuName">{{ record.skuName || record.skuCode }}</span>
            <a-button type="link" size="small" @click="clearSku(record)">重选</a-button>
          </div>
          <a-select
              v-else
              v-model:value="record.skuId"
              show-search
              :filter-option="false"
              :loading="searchLoading"
              :dropdownMatchSelectWidth="false"
              :dropdownStyle="{ minWidth: '480px' }"
              placeholder="输入商品名 / SKU编码搜索"
              style="width: 100%"
              @search="onSkuSearch"
              @change="(v) => onSkuSelect(v, record)"
              allow-clear
          >
            <a-select-option
                v-for="opt in skuOptions"
                :key="opt.skuId"
                :value="opt.skuId"
            >
              {{ opt.productName }} - {{ opt.skuCode }} - {{ opt.specText }}（库存 {{ opt.currentStock }}）
            </a-select-option>
          </a-select>
        </template>
      </a-table-column>
      <a-table-column title="SKU编码">
        <template #default="{ record }">{{ record.skuCode || '—' }}</template>
      </a-table-column>
      <a-table-column title="规格">
        <template #default="{ record }">{{ record.specText || '—' }}</template>
      </a-table-column>
      <a-table-column title="当前库存" align="right">
        <template #default="{ record }">{{ record.currentStock ?? '—' }}</template>
      </a-table-column>
      <a-table-column title="调整后数量" align="center">
        <template #default="{ record }">
          <a-input-number
              v-model:value="record.afterQty"
              :min="0" :precision="0"
              :disabled="record.currentStock == null"
              style="width: 100px"
          />
        </template>
      </a-table-column>
      <a-table-column title="差异" align="center">
        <template #default="{ record }">
          <span v-if="diffOf(record) !== 0" :style="{ color: diffColor(diffOf(record)), fontWeight: 600 }">
            {{ diffText(diffOf(record)) }}
          </span>
          <span v-else style="color: #bfbfbf">0</span>
        </template>
      </a-table-column>
      <a-table-column title="操作" align="center">
        <template #default="{ $index }">
          <a-button type="link" danger size="small" @click="removeRow($index)">删除</a-button>
        </template>
      </a-table-column>
    </a-table>
    <a-empty v-if="rows.length === 0" description="请至少添加一行调整明细" />
  </a-modal>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import { createStockOrder, searchSku } from '@/api/stock.js'
import { formatSpec } from '@/utils/stock.js'

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['update:visible', 'created'])

const close = () => emit('update:visible', false)

const remark = ref('')
const rows = ref([])
let rowSeq = 0

const addRow = () => {
  rows.value.push({
    rowKey: ++rowSeq,
    skuId: undefined,
    skuCode: '',
    skuName: '',
    specText: '',
    currentStock: null,
    afterQty: null,
  })
}

const removeRow = (idx) => { rows.value.splice(idx, 1) }

// 差异计算（+N 绿色 / -N 红色）
const diffOf = (r) => {
  if (r.currentStock == null || r.afterQty == null) return 0
  return Number(r.afterQty) - Number(r.currentStock)
}
const diffColor = (d) => d > 0 ? '#3f8600' : d < 0 ? '#cf1322' : '#8c8c8c'
const diffText = (d) => d > 0 ? `+${d}` : `${d}`

// ====== SKU 搜索 ======
const searchLoading = ref(false)
const skuOptions = ref([])

const onSkuSearch = (keyword) => {
  if (!keyword) return
  searchLoading.value = true
  searchSku(keyword)
      .then(res => {
        const data = res.data?.data || {}
        const records = data.records || data.list || []
        const mapped = records.map(r => ({
          skuId: r.id,
          skuCode: r.skuCode || '',
          productName: r.productName || '',
          specText: formatSpec(r.specJson),
          currentStock: r.stock,
        }))
        // 保留已选中的选项，避免换关键词后已选行的回显退化成纯 ID
        const selectedIds = rows.value.map(x => x.skuId).filter(Boolean)
        const kept = skuOptions.value.filter(o => selectedIds.includes(o.skuId) && !mapped.some(m => m.skuId === o.skuId))
        skuOptions.value = [...kept, ...mapped]
      })
      .catch(() => {})
      .finally(() => { searchLoading.value = false })
}

const onSkuSelect = (skuId, record) => {
  if (!skuId) {
    record.skuCode = ''
    record.skuName = ''
    record.specText = ''
    record.currentStock = null
    record.afterQty = null
    return
  }
  const opt = skuOptions.value.find(o => o.skuId === skuId)
  if (!opt) return
  record.skuCode = opt.skuCode
  record.skuName = opt.productName
  record.specText = opt.specText
  record.currentStock = opt.currentStock
  record.afterQty = opt.currentStock
}

// 重选：清掉该行已选 SKU，回到搜索态
const clearSku = (record) => {
  record.skuId = undefined
  record.skuCode = ''
  record.skuName = ''
  record.specText = ''
  record.currentStock = null
  record.afterQty = null
}

// ====== 提交 ======
const submitting = ref(false)

const submit = () => {
  if (rows.value.length === 0) { message.warning('请至少添加一行明细'); return }
  const items = []
  for (const r of rows.value) {
    if (!r.skuId) { message.warning('有行未选择 SKU'); return }
    if (r.afterQty == null) { message.warning('有行未填写调整后数量'); return }
    const diff = diffOf(r)
    if (diff === 0) continue // 差异为 0 的跳过
    items.push({
      skuId: r.skuId,
      skuCode: r.skuCode,
      skuName: r.skuName,
      specText: r.specText,
      qty: diff,
      afterQty: r.afterQty,
    })
  }
  if (items.length === 0) { message.warning('所有行差异均为 0，不生成调整单'); return }

  submitting.value = true
  createStockOrder({
    type: 'ADJUST',
    remark: remark.value || undefined,
    items,
  })
      .then(() => {
        message.success('调整单创建成功')
        emit('created')
        close()
      })
      .catch(() => {})
      .finally(() => { submitting.value = false })
}

watch(() => props.visible, (v) => {
  if (v) {
    remark.value = ''
    rows.value = []
    skuOptions.value = []
  }
})

// 占位避免 unused 报错
void computed
</script>

<style scoped>
.section-title {
  font-weight: 600;
  color: #262626;
  margin-bottom: 8px;
}
/* 已选 SKU 的紧凑展示：名称超长省略号，避免撑变形表格 */
.sku-picked {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 4px;
}
.sku-picked-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
</style>
