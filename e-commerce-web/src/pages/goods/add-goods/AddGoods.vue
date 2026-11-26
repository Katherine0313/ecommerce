<template>
  <div class="add-goods-container">
    <!-- 顶部步骤条 -->
    <a-steps :current="currentStep" class="steps" @change="onStepChange">
      <a-step title="基础信息" description="分类 / 名称 / 图片 / 详情" />
      <a-step title="规格与销售" description="商品参数 / SKU / 上架状态" />
    </a-steps>

    <!-- Step 1：商品基础信息 -->
    <!-- 用 v-show 而不是 v-if：提交按钮在 Step 2，v-if 会把 Step 1 的 a-form 卸载，
         addGoodsFormRef 变成 null，点「提交商品」时 validate() 直接报 TypeError -->
    <a-card v-show="currentStep === 0" title="商品基础信息">
      <a-form :model="addGoodsForm" :rules="addGoodsRules" ref="addGoodsFormRef"
              :label-col="{span:3, style:{paddingRight:'5px'}}" :wrapper-col="{span:21}">
        <a-form-item label="商品分类" name="categoryId" >
          <a-cascader
              :options="categoryTree"
              :field-names="{ value: 'id', label: 'name', children: 'children' }"
              v-model:value="addGoodsForm.categoryId"
              @change="handleCategoryChange"
              :change-on-select="false"
              style="max-width:270px"
          />
        </a-form-item>
        <a-form-item label="商品名称" name="name">
          <a-input placeholder="请输入商品名称" v-model:value="addGoodsForm.name" style="width:40%" />
        </a-form-item>
        <a-form-item label="商品品牌" name="brand">
          <a-select placeholder="请选择商品品牌" v-model:value="addGoodsForm.brand" @change="onBrandChange">
            <a-select-option v-for="brand in brandOptions" :value="brand" :key="brand" label="option"></a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="主标题">
          <a-textarea v-model:value="addGoodsForm.title" show-count maxLength="30" />
        </a-form-item>
        <a-form-item label="商品副标题" name="subTitle">
          <a-input v-model:value="addGoodsForm.subTitle" placeholder="请输入商品副标题" />
        </a-form-item>
        <!-- AI 文案助手：内联模式（商品未入库也能用），生成结果一律先进预览区，逐项点「填入」才写表单 -->
        <a-form-item label="AI 文案">
          <a-space wrap>
            <a-input v-model:value="aiSellingPoints" placeholder="卖点（选填，供 AI 参考）" style="width:200px" allow-clear />
            <a-button size="small" :loading="aiGenerating === 'name'" @click="generateAiCopy('name')">名称</a-button>
            <a-button size="small" :loading="aiGenerating === 'subTitle'" @click="generateAiCopy('subTitle')">副标题</a-button>
            <a-button size="small" :loading="aiGenerating === 'title'" @click="generateAiCopy('title')">主标题</a-button>
            <a-button size="small" :loading="aiGenerating === 'description'" @click="generateAiCopy('description')">详情</a-button>
            <a-button size="small" type="primary" ghost :loading="aiGenerating === 'all'" @click="generateAll">AI生成</a-button>
            <a-popconfirm
                title="清空 AI 生成的文案、主图和详情图？分类、规格等信息会保留"
                ok-text="清空"
                cancel-text="取消"
                ok-type="danger"
                @confirm="clearAiGenerated"
            >
              <a-button size="small" danger :disabled="aiGenerating === 'all'">一键清空</a-button>
            </a-popconfirm>
          </a-space>
        </a-form-item>
        <a-form-item v-if="aiResults.length" label="生成预览">
          <div class="ai-preview">
            <div v-for="r in aiResults" :key="r.key" class="ai-preview-row">
              <span class="ai-preview-label">{{ r.label }}</span>
              <span class="ai-preview-value">{{ r.value }}</span>
              <a-button size="small" type="link" @click="fillField(r)">填入</a-button>
            </div>
          </div>
        </a-form-item>
        <a-form-item label="商品图片">
          <a-upload
              :action="uploadAction"
              :headers="uploadHeaders"
              :before-upload="beforeUpload"
              v-model:file-list="fileList"
              @change="imageUpload"
          >
            <a-button class="upload-btn" >
              <UploadOutlined />
              点击上传
            </a-button>
          </a-upload>
          <a-button type="link" size="small" @click="openImageModal('main')">AI 生成主图</a-button>
        </a-form-item>
        <a-form-item label="商品详情">
          <template #label>
            商品详情
            <a-button type="link" size="small" style="padding:0 4px" @click="openImageModal('detail')">AI 详情图</a-button>
          </template>
          <Editor ref="editorRef" v-model="addGoodsForm.body" />
        </a-form-item>
      </a-form>
      <div class="step-actions">
        <a-button type="primary" @click="nextToStep2">下一步：填写规格</a-button>
      </div>
    </a-card>

    <!-- Step 2：商品规格 + 销售信息 -->
    <template v-if="currentStep === 1">
      <a-card title="商品规格" class="card">
        <SpecsEditor ref="specsRef" />
        <a-divider>销售规格（SKU）</a-divider>
        <SkuEditor ref="skuEditorRef" />
      </a-card>
      <a-card title="销售信息" class="card">
        <SalesInfo ref="salesInfoRef" />
      </a-card>
      <div class="step-actions">
        <a-button @click="currentStep = 0">上一步</a-button>
        <a-button type="primary" @click="handleSubmit">提交商品</a-button>
      </div>
    </template>

    <!-- AI 生图弹窗：主图/详情图共用，原地生成原地用，不离开本页 -->
    <a-modal v-model:visible="imgModal.open" :title="imgModal.target === 'main' ? 'AI 生成主图' : 'AI 生成详情图'"
             :footer="null" width="640px">
      <a-textarea v-model:value="imgModal.prompt" :rows="3"
                  placeholder="画面描述（选填，默认按商品名称 + 卖点生成）" allow-clear />
      <div class="ai-img-toolbar">
        <a-radio-group v-model:value="imgModal.model">
          <a-radio-button value="">默认（Kolors）</a-radio-button>
          <a-radio-button value="fast">快速</a-radio-button>
        </a-radio-group>
        <a-button type="primary" :loading="imgModal.loading" @click="generateAiImage">生成</a-button>
      </div>
      <div v-if="imgModal.images.length" class="ai-img-grid">
        <div v-for="(img, i) in imgModal.images" :key="i" class="ai-img-item">
          <img :src="imageHost + img.url" alt="AI 生成图" />
          <a-button size="small" type="primary" block style="margin-top:8px" @click="adoptImage(img.url)">使用这张图</a-button>
        </div>
      </div>
    </a-modal>
  </div>
</template>
<script setup>
import {nextTick, onActivated, onMounted, ref, watch} from 'vue'
import {getGoodsList, getBrandList, getCategoryTree, addGoods} from '@/api/products.js'
import {UploadOutlined} from '@ant-design/icons-vue'
// 导入消息组件
import {message} from 'ant-design-vue'
// 导入富文本组件
import Editor from './Editor.vue'
import SalesInfo from './SalesInfo.vue'
import SpecsEditor from './SpecsEditor.vue'
import SkuEditor from './SkuEditor.vue'
import {getToken} from '@/utils/token'
import {compressImage} from '@/utils/compressImage'
import {getImageSpec} from '@/utils/image-spec'
import {generateCopy, generateImage} from '@/api/ai.js'

const imageHost = import.meta.env.VITE_API_BASE_URL


const data = ref([])
const total = ref(0)
const current = ref(1)
const pageSize = ref(10) // 每页条目默认10条
// 定义输入框的search
const search = ref('')

// 添加商品对话框表单实例ref，用于触发校验、重置
const addGoodsFormRef = ref(null)
// 销售信息子组件实例
const salesInfoRef = ref(null)
// 规格参数子组件实例
const specsRef = ref(null)
// 销售规格（SKU）子组件实例
const skuEditorRef = ref(null)
// 当前步骤：0 = 基础信息，1 = 规格与销售
const currentStep = ref(0)
// 商品图片上传列表（提交成功后清空缩略图）
const fileList = ref([])
// 定义商品品牌响应式变量数组
const brandOptions = ref([])
// 定义层级分类的数据源
const categoryTree = ref([])

// 按 id 找到分类节点（叶子节点的 specTemplate 用于自动生成规格参数行）
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

// 切换分类：按新分类的规格模板重建参数行 + 重新拉销售规格库（只展示该分类相关的规格名）
const handleCategoryChange = (value) => {
  const id = Array.isArray(value) ? value[value.length - 1] : value
  const node = findCategoryNode(categoryTree.value, id)
  specsRef.value?.applyTemplate(node?.specTemplate || [])
  // SKU 编辑器按分类过滤规格库：避免手机类也看到"轴体/色温"等无关规格名
  skuEditorRef.value?.setCategory(id ?? null)
}
const uploadAction = import.meta.env.VITE_API_BASE_URL + '/api/admin/upload'
// 商品主图压成 800×800 白底方：与历史商品图（python 脚本产物）口径一致，
// 商品卡和详情页都是方图位，规格表见 utils/image-spec.js
const productImageSpec = getImageSpec('upload')
const beforeUpload = (file) => compressImage(file, productImageSpec)
// 加载商品数据，分页复用，便于统一维护
const loadData = () => {
  // handlePageChange事件触发时，加载的商品要对应到点击的那一页，所以
  // 获取商品列表的函数一定要传参，参数是当前页（当前页的值由current接收）和每页条目数
  getGoodsList({
    page: current.value,
    pageSize: pageSize.value,
    search: search.value,
  })
      .then(res => {
        data.value = res.data.data
        total.value = res.data.total
      })
      .catch(err => {
        message.error('网络连接超时，请检查网络后再试')
        console.log('商品列表加载失败', err)
      })
}
// 加载全部品牌下拉选项数据
const loadBrand = () => {
  getBrandList()
      .then(res => {
        // 将接口返回的品牌数组赋值给下拉响应式变量，渲染a-select-option
        brandOptions.value = res.data.data
        // 品牌列表返回时商品名可能已经填了，补一次自动匹配
        autoMatchBrand()
      })
      .catch(err => {
        message.error('网络连接超时，请检查网络后再试')
        console.log('商品品牌列表加载失败', err)
      })
}
// 渲染商品列表的表格,在生命周期钩子中调用，确保DOM已就绪再拉取接口
onMounted(() => {
  loadData()    // 加载商品分页列表数据
  loadBrand()   // 加载品牌下拉选项数据

})
onActivated(() => {
  getCategoryTree()
      .then(res => {
        categoryTree.value = res.data.data
      })
  .catch(err => {
    console.log(err)
  })
})
// ====================== 步骤切换 ======================
// 点击步骤条切换：从 0 → 1 要校验；从 1 → 0 不需要
const onStepChange = (step) => {
  if (step === currentStep.value) return
  if (step > currentStep.value) {
    nextToStep2()
  } else {
    currentStep.value = step
  }
}
// Step 1 → Step 2：校验基础信息表单必须过
const nextToStep2 = () => {
  addGoodsFormRef.value.validate()
      .then(() => {
        currentStep.value = 1
        // v-if 导致 SkuEditor / SpecsEditor 首次挂载，handleCategoryChange 里的调用可能白跑（ref 为 null）
        // 所以切到 Step 2 后主动再调一次，确保规格模板和规格库都已按分类过滤加载
        const path = addGoodsForm.value.categoryId || []
        const id = path.length ? Number(path[path.length - 1]) : null
        const node = findCategoryNode(categoryTree.value, id)
        nextTick(() => {
          specsRef.value?.applyTemplate(node?.specTemplate || [])
          skuEditorRef.value?.setCategory(id ?? null)
        })
      })
      .catch(() => {
        message.warning('请先完善基础信息（分类、名称、品牌、副标题）')
      })
}

// ====================== 按钮 ======================

//提交按钮
const handleSubmit = () => {
  // 0. 规格参数：参数名不能为空（后端也会拦，这里先提示更友好）
  if (!specsRef.value?.validate()) {
    return
  }
  // 0.1 销售规格（SKU）：校验组合、价格与库存
  if (!skuEditorRef.value?.validate()) {
    return
  }
  // 1. 校验主表单
  addGoodsFormRef.value.validate()
      .then(() => {
        // 纯图详情提醒：body 去掉 HTML 标签后纯文字不足 30 字时警告（不拦截提交），
        // AI 知识库/检索读的是纯文字，纯图详情对 AI 等于没有详情
        const bodyText = String(addGoodsForm.value.body || '')
            .replace(/<[^>]+>/g, '').replace(/&nbsp;/gi, '').trim()
        if (bodyText.length < 30) {
          message.warning('商品详情纯文字不足 30 字（纯图详情），AI 与检索将读不到详情内容', 5)
        }
        // 2. 级联框的值是路径数组(如[1,101])，后端需要单个 number，取最后一级 id
        const path = addGoodsForm.value.categoryId || []
        const goodsData = {
          ...addGoodsForm.value,
          categoryId: path.length ? Number(path[path.length - 1]) : null,
          ...salesInfoRef.value?.salesInfoForm,
          specs: specsRef.value?.getSpecs() ?? [],
          ...skuEditorRef.value?.getPayload(),
        }
        // 商品级价格/库存由 SKU 聚合兜底，后端保存时会再按 SKU 重算
        const aggregate = skuEditorRef.value?.getAggregate()
        if (aggregate) {
          goodsData.salesPrice = aggregate.salesPrice
          goodsData.marketPrice = aggregate.marketPrice
          goodsData.costPrice = aggregate.costPrice
          // 商品级库存不再由前端提交，后端按 SKU 自动重算（库存变动只能走库存模块）
          goodsData.productCode = goodsData.productCode || aggregate.productCode || `SPU${Date.now()}`
        }
        // 3. 调用添加商品接口
        return addGoods(goodsData)
      })
      .then(() => {
        message.success('商品新增成功')
        resetAll()
        loadData()
      })
      .catch(err => {
        if (err && err.errorFields) {
          console.log('表单校验未通过', err)
        } else {
          message.error('网络连接异常，请检查网络后再试')
          console.log('产品新增失败', err)
        }
      })
}
// 清空整页表单：主表单有 name 的字段由 resetFields 清，其余手动清
const resetAll = () => {
  currentStep.value = 0
  addGoodsFormRef.value?.resetFields()
  brandTouched.value = false
  addGoodsForm.value.name = ''
  addGoodsForm.value.brand = undefined
  addGoodsForm.value.subTitle = ''
  addGoodsForm.value.title = ''
  addGoodsForm.value.categoryId = []
  addGoodsForm.value.image = undefined
  addGoodsForm.value.body = ''
  fileList.value = []
  specsRef.value?.resetForm()
  skuEditorRef.value?.reset()
}
// 取消按钮
const cancel = () => {
  resetAll()
}
// ====================== 表单校验 =====================
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

// ====================== 品牌自动匹配 =====================
// 用户手动选过品牌后，不再用商品名去覆盖他的选择
const brandTouched = ref(false)
const onBrandChange = () => {
  brandTouched.value = true
}

// 商品名包含品牌名就自动选中（忽略大小写和空格；多个命中取最长的，避免短品牌抢先）
const matchBrandByName = (name) => {
  const target = String(name || '').toLowerCase().replace(/\s+/g, '')
  if (!target) return undefined
  return brandOptions.value
      .filter(b => b && target.includes(String(b).toLowerCase().replace(/\s+/g, '')))
      .sort((a, b) => b.length - a.length)[0]
}

const autoMatchBrand = () => {
  if (brandTouched.value) return
  addGoodsForm.value.brand = matchBrandByName(addGoodsForm.value.name)
}

watch(() => addGoodsForm.value.name, autoMatchBrand)
// ====================== AI 文案 / 生图 =====================
// Editor 组件实例（暴露 insertHtml，详情文案/详情图原地插入）
const editorRef = ref(null)
const aiSellingPoints = ref('')
const aiGenerating = ref('')
const aiResults = ref([])
const AI_FIELD_DEFS = [
  {key: 'name', label: '名称'},
  {key: 'subTitle', label: '副标题'},
  {key: 'title', label: '主标题'},
  {key: 'description', label: '详情'},
]

// 当前选中分类的叶子节点名称（供 AI 参考分类语境）
const currentCategoryName = () => {
  const path = addGoodsForm.value.categoryId || []
  const node = path.length ? findCategoryNode(categoryTree.value, path[path.length - 1]) : null
  return node?.name || ''
}

// 内联模式调 /copy/generate：商品未入库，不带 productId
const generateAiCopy = (scene) => {
  if (!addGoodsForm.value.name?.trim()) {
    message.warning('请先填写商品名称（大致名称即可），AI 以此为依据生成')
    return
  }
  aiGenerating.value = scene
  generateCopy({
    product: {
      name: addGoodsForm.value.name.trim(),
      categoryName: currentCategoryName(),
      subTitle: addGoodsForm.value.subTitle,
      sellingPoints: aiSellingPoints.value,
    },
    scene,
  })
      .then(res => {
        const fields = res.data?.data?.fields || {}
        aiResults.value = AI_FIELD_DEFS
            .filter(f => fields[f.key])
            .map(f => ({...f, value: fields[f.key]}))
        if (!aiResults.value.length) {
          message.warning('生成结果为空，请重试')
        }
      })
      .catch(() => {})
      .finally(() => { aiGenerating.value = '' })
}

const escapeHtml = (s) => String(s)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;')

// 逐项「填入」：文本字段直接赋值；详情追加进富文本（不覆盖已有内容）
const fillField = (r) => {
  if (r.key === 'description') {
    const html = r.value.split(/\n+/).filter(Boolean)
        .map(p => `<p>${escapeHtml(p)}</p>`).join('')
    editorRef.value?.insertHtml(html)
  } else {
    addGoodsForm.value[r.key] = r.value
  }
  aiResults.value = aiResults.value.filter(item => item.key !== r.key)
  message.success(`${r.label}已填入`)
}

// 文本转富文本段落（详情文案统一走这个，保证和「填入」口径一致）
const toBodyHtml = (text) => String(text || '').split(/\n+/).filter(Boolean)
    .map(p => `<p>${escapeHtml(p)}</p>`).join('')

// 全部生成：只生成文案（名称/副标题/主标题/详情）并自动回填；主图/详情图走各自的生图入口
const generateAll = async () => {
  if (!addGoodsForm.value.name?.trim()) {
    message.warning('请先填写商品名称（大致名称即可），AI 以此为依据生成')
    return
  }
  if (aiGenerating.value) {
    return
  }
  aiGenerating.value = 'all'

  // 1. 文案
  let fields = {}
  try {
    const res = await generateCopy({
      product: {
        name: addGoodsForm.value.name.trim(),
        categoryName: currentCategoryName(),
        subTitle: addGoodsForm.value.subTitle,
        sellingPoints: aiSellingPoints.value,
      },
      scene: 'all',
    })
    fields = res.data?.data?.fields || {}
  } catch (e) {
    aiGenerating.value = ''
    message.error('文案生成失败，请稍后重试')
    return
  }
  let filled = 0
  for (const key of ['name', 'subTitle', 'title']) {
    if (fields[key]) {
      addGoodsForm.value[key] = fields[key]
      filled++
    }
  }
  if (fields.description) {
    addGoodsForm.value.body = toBodyHtml(fields.description)
    filled++
  }
  // 已自动回填，清空旧的预览区，避免还要再点一次「填入」
  aiResults.value = []
  aiGenerating.value = ''

  if (!filled) {
    message.warning('没有生成任何内容，请补充卖点后重试')
    return
  }
  message.success('已生成并回填文案')
}

// 一键清空：只清 AI 生成/回填的内容，保留分类、规格、SKU、销售信息等手动维护部分
const clearAiGenerated = () => {
  // 只移除 AI 主图（uid = -1），用户自己上传的图片保留
  const hadAiMain = fileList.value.some(f => f.uid === '-1')
  fileList.value = fileList.value.filter(f => f.uid !== '-1')
  if (hadAiMain) {
    // 当前主图若指向 AI 图，回退到剩余上传图（没有就置空）
    const rest = fileList.value.find(f => f.status === 'done')
    addGoodsForm.value.image = rest?.response?.data?.url || undefined
  }
  addGoodsForm.value.name = ''
  addGoodsForm.value.subTitle = ''
  addGoodsForm.value.title = ''
  addGoodsForm.value.body = ''
  aiSellingPoints.value = ''
  aiResults.value = []
  message.success('已清空 AI 生成的内容')
}

// AI 生图弹窗状态：target=main 设主图 / detail 插详情
const imgModal = ref({open: false, target: 'main', prompt: '', model: '', loading: false, images: []})

const openImageModal = (target) => {
  imgModal.value = {open: true, target, prompt: '', model: '', loading: false, images: []}
}

const generateAiImage = () => {
  imgModal.value.loading = true
  generateImage({
    prompt: imgModal.value.prompt,
    scene: imgModal.value.target,
    model: imgModal.value.model || undefined,
    count: 1,
    product: addGoodsForm.value.name?.trim()
        ? {name: addGoodsForm.value.name.trim(), sellingPoints: aiSellingPoints.value}
        : undefined,
  })
      .then(res => {
        imgModal.value.images = res.data?.data?.images || []
        if (!imgModal.value.images.length) {
          message.warning('未生成图片，请调整描述后重试')
        }
      })
      .catch((e) => {
        // 400/401/403/429/500 已由 axios 拦截器统一提示，这里补 502 和网络异常
        const status = e?.response?.status
        if (!status || status === 502) {
          message.error('图片生成失败，请稍后重试')
        }
      })
      .finally(() => { imgModal.value.loading = false })
}

// 采用生成图：主图写表单 + 回显缩略图；详情图原地插入富文本（相对 url，与上传图口径一致）
const adoptImage = (url) => {
  if (imgModal.value.target === 'main') {
    addGoodsForm.value.image = url
    fileList.value = [{uid: '-1', name: 'AI 主图', status: 'done', url: imageHost + url}]
  } else {
    editorRef.value?.insertHtml(`<img src="${url}" style="max-width:100%" alt="详情图"/>`)
  }
  imgModal.value.open = false
  message.success('已使用 AI 生成图')
}
// ====================== 图片上传 =====================
const imageUpload = (info) => {
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
// ====================== 富文本 =====================


</script>


<style scoped>
.add-goods-container {
  max-width:900px;
  display: flex;
  flex-direction: column;
  margin: 0 auto;
}

.card {
  margin-top: 20px;
}

.steps {
  margin-bottom: 24px;
  max-width: 600px;
}

.step-actions {
  display: flex;
  gap: 12px;
  margin: 20px 0;
  padding-top: 16px;
  border-top: 1px dashed #f0f0f0;
  justify-content: center;
}

/* AI 文案预览区 */
.ai-preview {
  width: 100%;
  border: 1px dashed #d9d9d9;
  border-radius: 6px;
  padding: 8px 12px;
  background: #fafafa;
}
.ai-preview-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 6px 0;
}
.ai-preview-row + .ai-preview-row {
  border-top: 1px solid #f0f0f0;
}
.ai-preview-label {
  flex: none;
  width: 48px;
  color: #8c8c8c;
  font-size: 12px;
  line-height: 22px;
}
.ai-preview-value {
  flex: 1;
  min-width: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

/* AI 生图弹窗 */
.ai-img-toolbar {
  margin: 12px 0;
  display: flex;
  gap: 12px;
  align-items: center;
}
.ai-img-grid {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}
.ai-img-item {
  width: 240px;
}
.ai-img-item img {
  width: 100%;
  border-radius: 4px;
  display: block;
}

/*卡片标题样式*/
:deep(.ant-card-head-title ) {
  font-weight: bold;
}
/* antd 默认规则 `.ant-steps-horizontal:not(.ant-steps-label-vertical) .ant-steps-item-description`
   的优先级(3 个 class)高于 `:deep(.ant-steps-item-description)`(1 个 class + 1 个 scoped 属性)，
   所以必须把选择器写全，否则 max-width 会被 140px 覆盖 */
:deep(.ant-steps-horizontal:not(.ant-steps-label-vertical) .ant-steps-item-description) {
  max-width: 240px;
  white-space: normal;
}
/* 笔记本 */
@media (max-width: 1200px) {
  .steps {
    max-width: 100%;
  }
}

/* 手机：表单标签改为上下结构 */
@media (max-width: 767px) {
  .add-goods-container {
    max-width: 100%;
    width: 100%;
  }

  .add-goods-container :deep(.ant-form-item-row) {
    display: block;
  }

  .add-goods-container :deep(.ant-form-item-label),
  .add-goods-container :deep(.ant-form-item-control) {
    width: 100%;
    max-width: none;
    flex: none;
    text-align: left;
  }

  .add-goods-container :deep(.ant-cascader),
  .add-goods-container :deep(.ant-input),
  .add-goods-container :deep(.ant-select) {
    width: 100% !important;
    max-width: 100% !important;
  }
}
</style>