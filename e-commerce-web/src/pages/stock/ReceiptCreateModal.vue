<template>
  <a-modal
      :visible="visible"
      title="新建入库单"
      :width="900"
      :confirm-loading="submitting"
      @ok="submit"
      @cancel="close"
      @update:visible="v => emit('update:visible', v)"
  >
    <a-form layout="vertical">
      <a-row :gutter="16">
        <a-col :span="16">
          <a-form-item label="入库类型" required>
            <a-radio-group v-model:value="form.bizType">
              <a-radio value="PURCHASE">采购入库</a-radio>
              <a-radio value="RETURN">退货归还</a-radio>
              <a-radio value="MANUAL">手工补录</a-radio>
            </a-radio-group>
          </a-form-item>
        </a-col>
        <a-col :span="8">
          <a-form-item label="关联单号（选填）">
            <a-input v-model:value="form.relatedNo" placeholder="如采购单号 / 退货单号" />
          </a-form-item>
        </a-col>
      </a-row>
      <a-form-item label="备注">
        <a-textarea v-model:value="form.remark" :rows="2" show-count maxLength="200" />
      </a-form-item>
    </a-form>

    <!-- 明细行 -->
    <div class="section-title">
      入库明细
      <a-button type="link" style="padding-left: 8px" @click="addRow">+ 添加行</a-button>
    </div>
    <a-table :dataSource="rows" :pagination="false" size="small" row-key="rowKey" bordered>
      <a-table-column title="SKU 搜索（商品 / 编码）">
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
              :dropdownStyle="{ width: '480px' }"
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
      <a-table-column title="入库数量" align="center">
        <template #default="{ record }">
          <a-input-number v-model:value="record.qty" :min="1" :precision="0" style="width: 90px" />
        </template>
      </a-table-column>
      <a-table-column title="成本价(选填)" align="center">
        <template #default="{ record }">
          <a-input-number v-model:value="record.costPrice" :min="0" :precision="2" addon-before="￥" style="width: 110px" />
        </template>
      </a-table-column>
      <a-table-column title="操作" align="center">
        <template #default="{ $index }">
          <a-button type="link" danger size="small" @click="removeRow($index)">删除</a-button>
        </template>
      </a-table-column>
    </a-table>
    <a-empty v-if="rows.length === 0" description="请至少添加一行入库明细" />
  </a-modal>
</template>

<script setup>
import { ref, reactive, watch } from 'vue'
import { message } from 'ant-design-vue'
import { createStockOrder, searchSku } from '@/api/stock.js'
import { formatSpec } from '@/utils/stock.js'

const props = defineProps({ visible: Boolean })
const emit = defineEmits(['update:visible', 'created'])

const close = () => emit('update:visible', false)

const form = reactive({
  bizType: 'PURCHASE',
  relatedNo: '',
  remark: '',
})

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
    qty: 1,
    costPrice: null,
  })
}

const removeRow = (idx) => { rows.value.splice(idx, 1) }

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
    return
  }
  const opt = skuOptions.value.find(o => o.skuId === skuId)
  if (!opt) return
  record.skuCode = opt.skuCode
  record.skuName = opt.productName
  record.specText = opt.specText
  record.currentStock = opt.currentStock
}

// 重选：清掉该行已选 SKU，回到搜索态
const clearSku = (record) => {
  record.skuId = undefined
  record.skuCode = ''
  record.skuName = ''
  record.specText = ''
  record.currentStock = null
}

// ====== 提交 ======
const submitting = ref(false)

const submit = () => {
  if (!form.bizType) { message.warning('请选择入库类型'); return }
  if (rows.value.length === 0) { message.warning('请至少添加一行明细'); return }
  for (const r of rows.value) {
    if (!r.skuId) { message.warning('有行未选择 SKU'); return }
    if (!r.qty || r.qty <= 0) { message.warning('入库数量必须大于 0'); return }
  }
  submitting.value = true
  const payload = {
    type: 'RECEIPT',
    bizType: form.bizType,
    relatedNo: form.relatedNo || undefined,
    remark: form.remark || undefined,
    items: rows.value.map(r => ({
      skuId: r.skuId,
      skuCode: r.skuCode,
      skuName: r.skuName,
      specText: r.specText,
      qty: r.qty,
      costPrice: r.costPrice ?? undefined,
    })),
  }
  createStockOrder(payload)
      .then(() => {
        message.success('入库单创建成功')
        emit('created')
        close()
      })
      .catch(() => {})
      .finally(() => { submitting.value = false })
}

// 弹窗打开时重置内部状态
watch(() => props.visible, (v) => {
  if (v) {
    form.bizType = 'PURCHASE'
    form.relatedNo = ''
    form.remark = ''
    rows.value = []
    skuOptions.value = []
  }
})
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
:deep(.ant-table-container){
  overflow:hidden;
}
</style>
