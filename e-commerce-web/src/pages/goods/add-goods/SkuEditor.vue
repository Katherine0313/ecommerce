<template>
  <div class="sku-editor">
    <!-- 规格名平铺 tab：点一下就加一个维度 -->
    <div class="spec-picker">
      <span class="picker-label">选择规格名：</span>
      <a-tag
          v-for="g in specGroups"
          :key="g.id"
          :color="isSelectedGroup(g.id) ? 'blue' : 'default'"
          :bordered="isSelectedGroup(g.id)"
          class="spec-chip"
          :class="{ disabled: dimensions.length >= maxDimensions && !isSelectedGroup(g.id) }"
          @click="toggleGroup(g)"
      >
        {{ g.name }}
      </a-tag>
      <span v-if="maxDimensions > 3" class="picker-hint">（当前分类可用规格 {{ maxDimensions }} 个，需全部选择）</span>
      <span v-else-if="dimensions.length >= maxDimensions" class="picker-hint">（最多选 {{ maxDimensions }} 个）</span>
    </div>

    <!-- 已选维度展开，候选值平铺（默认全勾） -->
    <template v-if="activeDimensions().length">
      <a-divider orientation="left" style="margin:12px 0 8px">
        勾选候选值（已全选，可取消不想卖的值）
      </a-divider>
      <div v-for="(dim, idx) in dimensions.filter(d => d.groupId)" :key="dim.key" class="dim-block">
        <div class="dim-head">
          <span class="dim-name">{{ dim.name }}</span>
          <a-button type="link" size="small" danger @click="removeDim(idx)">移除</a-button>
        </div>
        <div v-if="getGroupValues(dim.groupId).length" class="dim-values">
          <a-checkbox-group v-model:value="dim.values" @change="rebuildRows">
            <a-checkbox
                v-for="v in getGroupValues(dim.groupId)"
                :key="v.id"
                :value="v.value"
            >{{ v.value }}</a-checkbox>
          </a-checkbox-group>
        </div>
        <div v-else class="dim-tip">规格库中暂无候选值，请先到「销售规格库」添加</div>
      </div>
    </template>

    <!-- SKU 汇总表格（实时笛卡尔积） -->
    <template v-if="rows.length">
      <a-divider orientation="left" style="margin:14px 0 10px">
        SKU 汇总（共 {{ rows.length }} 行）
      </a-divider>
      <div class="batch-bar">
        <span class="batch-title">批量填充：</span>
        <a-input-number v-model:value="batch.price" addon-before="￥" :min="0" :precision="2" placeholder="售价" style="width:140px" />
        <a-input-number v-model:value="batch.marketPrice" addon-before="￥" :min="0" :precision="2" placeholder="市场价" style="width:140px" />
        <a-input-number v-model:value="batch.costPrice" addon-before="￥" :min="0" :precision="2" placeholder="成本价" style="width:140px" />
        <a-input-number v-model:value="batch.stock" :min="0" :precision="0" placeholder="库存(不可手动改)" disabled style="width:150px" />
        <a-button type="primary" size="small" @click="applyBatch">应用到全部</a-button>
        <span style="color: #8c8c8c; font-size: 12px">库存变动请通过库存模块入库单/调整单操作</span>
      </div>
      <div class="sku-stock-tip">⚠ 库存列不可手动编辑，请前往「库存管理 → 入库单 / 调整单」调整</div>
      <a-table :data-source="rows" :pagination="false" row-key="key" size="small" :scroll="{ x: 980 }">
        <a-table-column title="规格组合" key="combo" width="200" fixed="left">
          <template #default="{ record }"><b>{{ comboText(record) }}</b></template>
        </a-table-column>
        <a-table-column title="售价" width="150">
          <template #default="{ record }">
            <a-input-number v-model:value="record.price" addon-before="￥" :min="0" :precision="2" style="width:120px" />
          </template>
        </a-table-column>
        <a-table-column title="市场价" width="150">
          <template #default="{ record }">
            <a-input-number v-model:value="record.marketPrice" addon-before="￥" :min="0" :precision="2" style="width:120px" />
          </template>
        </a-table-column>
        <a-table-column title="成本价" width="150">
          <template #default="{ record }">
            <a-input-number v-model:value="record.costPrice" addon-before="￥" :min="0" :precision="2" style="width:120px" />
          </template>
        </a-table-column>
        <a-table-column title="库存" width="120">
          <template #default="{ record }">
            <a-input-number v-model:value="record.stock" :min="0" :precision="0" style="width:90px" disabled />
          </template>
        </a-table-column>
        <a-table-column title="商家编码" width="160">
          <template #default="{ record }">
            <a-input v-model:value="record.skuCode" placeholder="选填" />
          </template>
        </a-table-column>
        <a-table-column title="状态" width="80">
          <template #default="{ record }">
            <a-tag :color="record.status === 1 ? 'green' : 'default'">
              {{ record.status === 1 ? '启用' : '停用' }}
            </a-tag>
          </template>
        </a-table-column>
      </a-table>
    </template>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getSpecLibrary } from '@/api/specs.js'

// skuType 永远是 1（多规格），默认就显示 SKU 编辑器
const skuType = ref(1)
// 规格维度：{ key, groupId, name, values: [] }
const dimensions = ref([])
// SKU 行：{ key, id, specJson, price, marketPrice, costPrice, stock, skuCode, status }
const rows = ref([])

// 硬编码兜底规格库：即使网络没通，用户选分类/勾值也能正常操作。
// 后端 specCatalogService 拉到数据后会覆盖掉这些默认值。
const DEFAULT_SPEC_GROUPS = [
  { id: -1001, name: '颜色', status: 1, categoryId: null,
    values: [{ id: -2001, value: '曜石黑' }, { id: -2002, value: '雪域白' }, { id: -2003, value: '晴空蓝' }] },
  { id: -1002, name: '存储容量', status: 1, categoryId: null,
    values: [{ id: -2004, value: '128G' }, { id: -2005, value: '256G' }, { id: -2006, value: '512G' }, { id: -2007, value: '1TB' }] },
  { id: -1003, name: '运行内存', status: 1, categoryId: 2,
    values: [{ id: -2008, value: '8G' }, { id: -2009, value: '16G' }, { id: -2010, value: '32G' }] },
  { id: -1004, name: '表带材质', status: 1, categoryId: 5,
    values: [{ id: -2011, value: '运动表带' }, { id: -2012, value: '皮革表带' }, { id: -2013, value: '金属表链' }] },
  { id: -1005, name: '套装类型', status: 1, categoryId: 6,
    values: [{ id: -2014, value: '单机身' }, { id: -2015, value: '标准套机' }] },
  { id: -1006, name: '轴体', status: 1, categoryId: 7,
    values: [{ id: -2016, value: '红轴' }, { id: -2017, value: '青轴' }, { id: -2018, value: '茶轴' }] },
  { id: -1007, name: '版本', status: 1, categoryId: 8,
    values: [{ id: -2019, value: '标准版' }, { id: -2020, value: '水箱版' }] },
]
// 规格库：先塞兜底值，请求成功再覆盖
const specGroups = ref([...DEFAULT_SPEC_GROUPS])
// 规格数量上限：规格库里的规格名全部必选，所以上限至少等于「有候选值的规格名个数」，
// 否则手表/相机/智能家居/数码配件这类分类会因「必选 4~7 个但最多只能选 3 个」而永远提交不了
const maxDimensions = computed(() => Math.max(3, specGroups.value.filter(g => (g.values || []).length).length))
// 当前已拉取过的分类 id（null = 全量），用于避免重复请求
let lastLoadedCategoryId = '__unloaded__'
const batch = ref({ price: null, marketPrice: null, costPrice: null, stock: null })
let dimSeq = 0
// 编辑回显时按规格组合缓存已有 SKU，重建表格时按 key 匹配
let pendingSkus = new Map()

// 哪些 groupId 已被选中（用于 chip 高亮）
const isSelectedGroup = (groupId) => dimensions.value.some(d => d.groupId === groupId)

// 平铺 chip 点击：选中 → 加维度（自动全勾候选值）；已选中 → 移除维度
const toggleGroup = (g) => {
  if (isSelectedGroup(g.id)) {
    const idx = dimensions.value.findIndex(d => d.groupId === g.id)
    if (idx >= 0) dimensions.value.splice(idx, 1)
    rebuildRows()
    return
  }
  if (dimensions.value.length >= maxDimensions.value) {
    message.warning(`最多选 ${maxDimensions.value} 个规格`)
    return
  }
  dimensions.value.push({
    key: ++dimSeq,
    groupId: g.id,
    name: g.name,
    values: (g.values || []).map(v => v.value), // 自动全勾
  })
  rebuildRows()
}

// 拉规格库：未选分类时传 null 会拉全量；选了分类就只拉该分类 + 全局通用的
const loadLibrary = (categoryId) => {
  // categoryId 相同就不重复拉（切换分类时才会重新请求）
  const key = categoryId ?? null
  if (lastLoadedCategoryId === key) return Promise.resolve()
  lastLoadedCategoryId = key
  return getSpecLibrary(categoryId)
      .then(res => {
        const data = res.data?.data
        if (Array.isArray(data)) specGroups.value = data
      })
      .catch(() => {
        specGroups.value = []
      })
}

const getGroupValues = (groupId) => {
  const g = specGroups.value.find(g => g.id === groupId)
  return g?.values || []
}

const removeDim = (index) => {
  dimensions.value.splice(index, 1)
  rebuildRows()
}

// 只要选了规格名就算 active，不管 values 有没有勾值
const activeDimensions = () =>
    dimensions.value.filter(d => d.groupId != null && d.name)

const buildKey = (parts) => parts.map(p => `${p.name}:${p.value}`).join('|')

const rebuildRows = () => {
  const dims = activeDimensions()
  if (!dims.length) {
    rows.value = []
    return
  }
  // values 为空的维度用一个 "—" 占位，保证至少生成一行 SKU
  let combos = [[]]
  for (const dim of dims) {
    const vals = dim.values && dim.values.length ? dim.values : ['—']
    combos = combos.flatMap(combo =>
        vals.map(value => [...combo, { name: dim.name.trim(), value }]))
  }
  const oldByKey = new Map(rows.value.map(row => [row.key, row]))
  rows.value = combos.map(parts => {
    const key = buildKey(parts)
    const prev = oldByKey.get(key)
    if (prev) return { ...prev, specJson: Object.fromEntries(parts.map(p => [p.name, p.value])) }
    const cached = pendingSkus.get(key)
    return {
      key,
      id: cached?.id ?? null,
      specJson: Object.fromEntries(parts.map(p => [p.name, p.value])),
      price: cached?.price ?? null,
      marketPrice: cached?.marketPrice ?? null,
      costPrice: cached?.costPrice ?? null,
      stock: cached?.stock ?? 0,
      skuCode: cached?.skuCode ?? '',
      status: cached?.status ?? 1,
    }
  })
}

const comboText = (row) => Object.values(row.specJson || {}).join(' / ')

const applyBatch = () => {
  const b = batch.value
  if (b.price == null && b.marketPrice == null && b.costPrice == null && b.stock == null) {
    message.warning('请先填写至少一个基准值')
    return
  }
  rows.value.forEach(row => {
    if (b.price != null) row.price = b.price
    if (b.marketPrice != null) row.marketPrice = b.marketPrice
    if (b.costPrice != null) row.costPrice = b.costPrice
    if (b.stock != null) row.stock = b.stock
  })
}

// 校验：至少 1 个维度并勾值、组合完整、价格库存合法
const validate = () => {
  const dims = activeDimensions()
  if (!dims.length) {
    message.warning('多规格商品至少需要 1 个规格维度并勾选候选值')
    return false
  }
  if (dimensions.value.length > maxDimensions.value) {
    message.warning(`规格维度最多 ${maxDimensions.value} 个`)
    return false
  }
  // 规格库里的规格名全部必选：当前分类可用、且有候选值的规格名，都必须选为 SKU 维度
  const missingRequired = specGroups.value
      .filter(g => (g.values || []).length
          && !dims.some(d => (g.id != null && d.groupId === g.id) || d.name === g.name))
      .map(g => g.name)
  if (missingRequired.length) {
    message.warning(`请选择必选规格：${missingRequired.join('、')}`)
    return false
  }
  const names = new Set()
  for (const dim of dims) {
    if (names.has(dim.name)) {
      message.warning(`规格名重复：${dim.name}`)
      return false
    }
    names.add(dim.name)
    if (!dim.values || !dim.values.length) {
      message.warning(`规格【${dim.name}】尚未勾选任何值`)
      return false
    }
  }
  const expected = dims.reduce((acc, dim) => acc * dim.values.length, 1)
  if (rows.value.length !== expected) {
    message.warning('SKU 表格尚未生成完整，请检查规格值')
    return false
  }
  for (const row of rows.value) {
    if (row.price == null || row.price <= 0) {
      message.warning(`请为「${comboText(row)}」填写大于 0 的售价`)
      return false
    }
    if (row.stock == null || row.stock < 0) {
      message.warning(`请为「${comboText(row)}」填写不小于 0 的库存`)
      return false
    }
  }
  return true
}

const getPayload = () => ({
  skuType: 1,
  skuSpecs: activeDimensions().map(dim => ({
    groupId: dim.groupId ?? null,
    name: dim.name.trim(),
    values: [...dim.values],
  })),
  skus: rows.value.map(row => ({
    id: row.id ?? null,
    specJson: row.specJson,
    price: row.price,
    marketPrice: row.marketPrice,
    costPrice: row.costPrice,
    stock: row.stock ?? 0,
    skuCode: row.skuCode || null,
    status: row.status ?? 1,
  })),
})

// 商品级价格/库存的聚合值，提交时兜底填到商品字段（后端会再按 SKU 重算）
const getAggregate = () => {
  if (!rows.value.length) return null
  const priced = rows.value.filter(row => row.price != null)
  const lowest = priced.length
      ? priced.reduce((min, row) => (row.price < min.price ? row : min), priced[0])
      : null
  return {
    salesPrice: lowest?.price ?? null,
    marketPrice: lowest?.marketPrice ?? lowest?.price ?? null,
    costPrice: lowest?.costPrice ?? null,
    stock: rows.value.reduce((sum, row) => sum + (row.stock ?? 0), 0),
    productCode: rows.value.find(row => row.skuCode)?.skuCode || null,
  }
}

const reset = () => {
  dimensions.value = []
  rows.value = []
  batch.value = { price: null, marketPrice: null, costPrice: null, stock: null }
  pendingSkus = new Map()
}

// 编辑回显：skuType（忽略，永远多规格）/ skuSpecs / skus
const setData = (_type, specs, skus) => {
  dimensions.value = (specs || []).map(spec => {
    let group = null
    if (spec.groupId != null) {
      group = specGroups.value.find(g => g.id === spec.groupId)
    }
    if (!group && spec.name) {
      group = specGroups.value.find(g => g.name === spec.name)
    }
    const groupId = group ? group.id : null
    const name = group ? group.name : (spec.name || '')
    return {
      key: ++dimSeq,
      groupId,
      name,
      values: Array.isArray(spec.values) ? [...new Set(spec.values)] : [],
    }
  })
  pendingSkus = new Map()
  ;(skus || []).forEach(sku => {
    const names = dimensions.value.map(dim => dim.name)
    const json = sku.specJson || {}
    const parts = names.map(name => ({ name, value: json[name] }))
    if (parts.every(p => p.value != null)) {
      pendingSkus.set(buildKey(parts), sku)
    }
  })
  rows.value = []
  rebuildRows()
}

// 不再提前拉规格库：等用户选了分类后 setCategory(categoryId) 再拉对应分类的规格
// 兜底 DEFAULT_SPEC_GROUPS 保留，网络失败时仍可作为 fallback

// 由 AddGoods/EditModal 在用户选了分类后调用；categoryId 为商品分类叶节点 id
const setCategory = (categoryId) => {
  const id = categoryId ?? null
  const changed = lastLoadedCategoryId !== '__unloaded__' && lastLoadedCategoryId !== id
  if (changed && dimensions.value.length) {
    message.info('分类已变更，请重新选择销售规格')
  }
  // 切换分类时清空旧维度和 SKU 行，防止不同分类的规格混在一起
  dimensions.value = []
  rows.value = []
  loadLibrary(id)
}

defineExpose({ validate, getPayload, getAggregate, setData, reset, setCategory })
</script>

<style scoped>
.sku-editor {
  width: 100%;
}

/* 顶部规格名平铺 picker */
.spec-picker {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}
.picker-label {
  color: #262626;
  font-weight: 600;
  margin-right: 4px;
}
.spec-chip {
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
  font-size: 13px;
}
.spec-chip:not(.disabled):hover {
  transform: translateY(-1px);
}
.spec-chip.disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.picker-hint {
  color: #8c8c8c;
  font-size: 12px;
  margin-left: 4px;
}

/* 已选维度块 */
.dim-block {
  padding: 10px 12px;
  margin: 8px 0;
  background: #fff;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
}
.dim-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.dim-name {
  font-weight: 600;
  color: #262626;
}
.dim-values {
  line-height: 2.2;
}
.dim-tip {
  color: #faad14;
  font-size: 12px;
}

/* SKU 汇总表格 */
.batch-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px;
  margin-bottom: 10px;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
}
.batch-title {
  font-weight: 600;
  color: #595959;
}
.sku-stock-tip {
  padding: 8px 12px;
  margin-bottom: 10px;
  background: #fffbe6;
  border: 1px solid #ffe58f;
  border-radius: 4px;
  color: #ad6800;
  font-size: 13px;
}
</style>
