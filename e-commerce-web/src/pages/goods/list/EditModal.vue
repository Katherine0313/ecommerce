<template>
<a-modal title="编辑商品" v-model:visible="editOpen" :width="800" wrap-class-name="drag-modal-editgoods">
  <div class="add-goods-container">
    <a-card title="商品信息">
      <a-form :model="addGoodsForm" :rules="addGoodsRules" ref="addGoodsFormRef"
              :label-col="{span:3, style:{paddingRight:'5px'}}">
        <a-form-item label="商品分类" >
          <a-cascader
              :key="editingId"
              :options="categoryTree"
              :field-names="{ value: 'id', label: 'name', children: 'children' }"
              v-model:value="addGoodsForm.categoryId"
              @change="handleCategoryChange"
              :change-on-select="false"
              placeholder="请选择商品分类"
          />
        </a-form-item>
        <a-form-item label="商品名称" name="name">
          <a-input placeholder="请输入商品名称" v-model:value="addGoodsForm.name" style="width:40%" />
        </a-form-item>
        <a-form-item label="商品品牌" name="brand">
          <a-select placeholder="请选择商品品牌" v-model:value="addGoodsForm.brand">
            <a-select-option v-for="brand in brandOptions" :value="brand" :key="brand" label="option"></a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="商品副标题" name="subTitle">
          <a-input v-model:value="addGoodsForm.subTitle" placeholder="请输入商品副标题" />
        </a-form-item>
        <a-form-item label="主标题">
          <a-textarea v-model:value="addGoodsForm.title" show-count maxLength="30" />
        </a-form-item>
        <a-form-item label="商品图片">
          <a-upload
              :action="uploadAction"
              :headers="uploadHeaders"
              :before-upload="beforeUpload"
              v-model:file-list="fileList"
              :maxCount="1"
              @change="imageUpload"
          >
            <a-button>
              <UploadOutlined />
              点击上传
            </a-button>
          </a-upload>
        </a-form-item>
        <a-form-item label="商品详情">
          <Editor v-model="addGoodsForm.body" />
        </a-form-item>
      </a-form>
    </a-card>
    <a-card title="商品规格" class="card">
      <SpecsEditor ref="specsRef" />
      <a-divider>销售规格（SKU）</a-divider>
      <SkuEditor ref="skuEditorRef" />
    </a-card>
    <a-card title="销售信息" class="card">
      <SalesInfo ref="salesInfoRef" />
    </a-card>
  </div>
  <template #footer>
    <div class="addGoods-btn">
      <a-button type="primary" @click="cancel">取消</a-button>
      <a-button type="primary" @click="handleSubmit">提交</a-button>
    </div>
  </template>
</a-modal>
</template>
<script setup>
import {nextTick, onMounted, ref} from 'vue'
import { useDragModal } from '@/composables/useDragModal'
import {updateGoods, getBrandList, getCategoryTree, getGoodsDetail} from '@/api/products.js'
// 导入消息组件
import {message} from 'ant-design-vue'
import {UploadOutlined} from "@ant-design/icons-vue";
import Editor from "@/pages/goods/add-goods/Editor.vue";
import SalesInfo from "@/pages/goods/add-goods/SalesInfo.vue";
import SpecsEditor from "@/pages/goods/add-goods/SpecsEditor.vue";
import SkuEditor from "@/pages/goods/add-goods/SkuEditor.vue";
import {getToken} from '@/utils/token'
import {compressImage} from '@/utils/compressImage'
import {getImageSpec} from '@/utils/image-spec'
const editOpen = ref(false)
const editingId = ref(null)  // 正在编辑的商品 id
const fileList = ref([])
const uploadAction = import.meta.env.VITE_API_BASE_URL + '/api/admin/upload'
// 商品主图压成 800×800 白底方，与新增商品页保持同一规格
const productImageSpec = getImageSpec('upload')
const beforeUpload = (file) => compressImage(file, productImageSpec)
useDragModal('drag-modal-editgoods')
const salesInfoRef =ref(null)
// 规格参数子组件实例
const specsRef = ref(null)
// 销售规格（SKU）子组件实例
const skuEditorRef = ref(null)
// 添加商品对话框表单实例ref，用于触发校验、重置
const addGoodsFormRef = ref(null)
// 定义商品品牌响应式变量数组
const brandOptions = ref([])
// 定义层级分类的数据源
const categoryTree = ref([])
// 数据表单（统一放对象里，和a-form的model对应）
const addGoodsForm = ref({
  name: '',
  image: undefined,
  body: '',
  // brand用undefined,否则placeholder不显示
  brand: undefined,
  subTitle: '',
  title: '',
  categoryId: [],
})

// ====================== 把值存到表单里 ======================
// 详情接口比弹窗内容渲染快时，先暂存 detail，等 SkuEditor 挂载后再回填
let pendingDetail = null

// 回填销售规格（SKU）：组件已挂载 + 规格库加载完成，才能按 groupId 正确匹配
const applySkuDetail = () => {
  const detail = pendingDetail
  const editor = skuEditorRef.value
  if (!detail || !editor) return
  pendingDetail = null
  const id = editingId.value
  const loading = editor.setCategory(detail.categoryId ?? null)
  const fill = () => {
    // 加载期间切换了编辑对象就丢弃，避免串数据
    if (editingId.value !== id || !skuEditorRef.value) return
    skuEditorRef.value.setData(detail.skuType, detail.skuSpecs || [], detail.skus || [])
  }
  if (loading && typeof loading.then === 'function') {
    loading.then(fill, fill)
  } else {
    fill()
  }
  if (salesInfoRef.value?.salesInfoForm) {
    salesInfoRef.value.salesInfoForm.listingStatus = detail.listingStatus ?? 1
  }
  specsRef.value?.setSpecs(detail.specs || [], categoryTemplate(detail.categoryId))
}

// 定义打开弹窗的方法，接收 record 并回填到表单
const open = (record) => {
  const id = record.id
  editingId.value = id  // 存下要编辑的商品 id
  pendingDetail = null
  // 0. 先清空上一件商品的 SKU 状态，避免串数据
  skuEditorRef.value?.reset()
  // 1. 回填商品基本信息
  addGoodsForm.value.name = record.name
  addGoodsForm.value.brand = record.brand
  addGoodsForm.value.subTitle = record.subTitle
  addGoodsForm.value.title = record.title
  addGoodsForm.value.image = record.image
  addGoodsForm.value.body = record.body
  // 回填规格参数（商品列表接口已返回 specs 数组；分类模板决定参数顺序，模板外的旧参数保留）
  specsRef.value?.setSpecs(record.specs || [], categoryTemplate(record.categoryId))
  // 回填已上传的图片到上传组件
  fileList.value = record.image
    ? [{ uid: '-1', name: record.image.split('/').pop() || record.image, status: 'done', url: record.image }]
    : []
  // 2. 打开弹窗
  editOpen.value = true
  // 3. 等弹窗渲染后回填（cascader 需要 DOM 就绪再设值）
  nextTick(() => {
    addGoodsForm.value.categoryId = findCategoryPath(categoryTree.value, record.categoryId) || []
    // 弹窗内容首次渲染时组件才存在，这里再回填一次规格参数
    specsRef.value?.setSpecs(record.specs || [], categoryTemplate(record.categoryId))
    if (salesInfoRef.value?.salesInfoForm) {
      salesInfoRef.value.salesInfoForm.listingStatus = record.listingStatus ?? 1
    }
    // 详情可能已经先到，此时组件才挂载，补一次 SKU 回填
    applySkuDetail()
  })
  // 4. 列表接口不带 SKU 明细，单独查详情回填销售规格（skuType / skuSpecs / skus）
  getGoodsDetail(id)
      .then(res => {
        const detail = res.data?.data
        // 请求返回前已切换到别的商品，丢弃旧数据
        if (!detail || editingId.value !== id) return
        pendingDetail = detail
        applySkuDetail()
      })
      .catch(() => {
        message.warning('商品规格明细加载失败，请刷新后重试')
      })
}

// 根据叶子节点 id 反查出完整路径，例如 101 → [1, 101]
const findCategoryPath = (tree, targetId) => {
  for (const node of tree) {
    if (node.id === targetId) return [node.id]
    if (node.children) {
      const childPath = findCategoryPath(node.children, targetId)
      if (childPath) return [node.id, ...childPath]
    }
  }
  return null
}

// 按 id 找分类节点，取该分类配置的规格模板字段
const findCategoryNode = (tree, targetId) => {
  for (const node of tree) {
    if (node.id === targetId) return node
    if (node.children) {
      const hit = findCategoryNode(node.children, targetId)
      if (hit) return hit
    }
  }
  return null
}

const categoryTemplate = (categoryId) => findCategoryNode(categoryTree.value, categoryId)?.specTemplate || []

// 切换分类：按新分类模板重建参数行 + 刷新销售规格库（只展示该分类相关的规格名）
const handleCategoryChange = (value) => {
  const id = Array.isArray(value) ? value[value.length - 1] : value
  specsRef.value?.applyTemplate(categoryTemplate(id))
  skuEditorRef.value?.setCategory(id ?? null)
}

// 加载全部品牌下拉选项数据
const loadBrand = () => {
  getBrandList()
      .then(res => {
        // 将接口返回的品牌数组赋值给下拉响应式变量，渲染a-select-option
        brandOptions.value = res.data.data
      })
      .catch(err => {
        message.error('网络连接超时，请检查网络后再试')
        console.log('商品品牌列表加载失败', err)
      })
}
// 渲染商品列表的表格,在生命周期钩子中调用，确保DOM已就绪再拉取接口
onMounted(() => {
  loadBrand()   // 加载品牌下拉选项数据
  getCategoryTree()
      .then(res => {
        categoryTree.value = res.data.data
      })
})
// ====================== 按钮 ======================

//提交按钮
const handleSubmit = () => {
  // 0. 规格参数：参数名不能为空（后端也会拦，这里先提示更友好）
  if (!specsRef.value?.validate()) {
    return
  }
  // 0.1 销售规格（SKU）：多规格时校验组合、价格与库存
  if (!skuEditorRef.value?.validate()) {
    return
  }
  // 1. 先校验父组件表单
  addGoodsFormRef.value.validate()
      .then(() => {
        // 纯图详情提醒：body 去掉 HTML 标签后纯文字不足 30 字时警告（不拦截提交），
        // AI 知识库/检索读的是纯文字，纯图详情对 AI 等于没有详情
        const bodyText = String(addGoodsForm.value.body || '')
            .replace(/<[^>]+>/g, '').replace(/&nbsp;/gi, '').trim()
        if (bodyText.length < 30) {
          message.warning('商品详情纯文字不足 30 字（纯图详情），AI 与检索将读不到详情内容', 5)
        }
        const path = addGoodsForm.value.categoryId || []
        const goodsData = {
          ...addGoodsForm.value,
          categoryId: path.length ? Number(path[path.length - 1]) : null,
          ...(salesInfoRef.value?.salesInfoForm || {}),
          specs: specsRef.value?.getSpecs() ?? [],
          ...skuEditorRef.value?.getPayload(),
        }
        // 多规格时商品级价格/库存由 SKU 聚合兜底，后端保存时会再按 SKU 重算
        const aggregate = skuEditorRef.value?.getAggregate()
        if (aggregate) {
          goodsData.salesPrice = aggregate.salesPrice
          goodsData.marketPrice = aggregate.marketPrice
          goodsData.costPrice = aggregate.costPrice
          // 商品级库存不再由前端提交，后端按 SKU 自动重算（库存变动只能走库存模块）
          goodsData.productCode = goodsData.productCode || aggregate.productCode || `SPU${editingId.value}`
        }
        // 4. 调用更新商品接口
        return updateGoods(editingId.value, goodsData)
      })
      .then(res => {
        console.log(res)
        message.success('商品编辑成功')
        editOpen.value = false
        emit('refresh')
      })
      .catch(err => {
        //  errorFields（存储所有出错字段的数组）
        if (err && err.errorFields) {
          console.log('表单校验未通过', err)
        } else {
          // 只有接口请求报错才提示
          message.error('网络连接异常，请检查网络后再试')
          console.log('产品新增失败', err)
        }
      })
}
// 取消按钮
const cancel = () => {
  editOpen.value = false
  fileList.value = []
  addGoodsFormRef.value.resetFields()
  specsRef.value?.resetForm()
  skuEditorRef.value?.reset()
}
// ====================== 表单校验 =====================


// 校验规则
const addGoodsRules = {
  categoryId: [
    {required: true, type: 'array', message: '商品分类不能为空', trigger: 'change'}
  ],
  name: [
    {required: true, message: '商品名称不能为空', trigger: 'blur'}
  ],
  brand: [
    {required: true, message: '商品品牌不能为空', trigger: 'change'}
  ],
  subTitle: [
    {required: true, message: '商品副标题不能为空', trigger: 'blur'}
  ]
}
// ====================== 图片上传 =====================
const imageUpload = (info) => {
  fileList.value = info.fileList
  if (info.file.status === 'done') {
    // 把url存进image
    addGoodsForm.value.image = info.file.response.data.url
  } else if (info.file.status === 'error') {
    // 上传失败（如文件类型不被允许）：取后端返回的具体原因提示用户
    const msg = (info.file.response && info.file.response.message) || '图片上传失败，请重试'
    message.error(msg)
  }
}
const uploadHeaders = {
  Authorization: 'Bearer ' + getToken()
}

// 把打开弹窗的方法暴露给父组件
defineExpose({open})
const emit = defineEmits(['refresh'])
</script>


<style scoped>
.add-goods-container {
  display: flex;
  flex-direction: column;
}

.card {
  margin-top: 20px;
}

.addGoods-btn {
  display: flex;
  gap: 20px;
}

/*卡片标题样式*/
:deep(.ant-card-head-title ) {
  font-weight: bold;
}
</style>