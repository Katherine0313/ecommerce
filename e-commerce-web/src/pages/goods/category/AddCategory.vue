<template>
  <a-modal :title="modalTitle" v-model:visible="open" width="720px" @ok="handleCategorySubmit" wrap-class-name="drag-modal-addcat">
    <a-form :model="addCategoryForm" ref="categoryFormRef" :rules="categoryFormRules">
      <a-form-item label="分类层级">
        <!-- 编辑时不允许改层级：后端局部更新无法把 parentId 置空 -->
        <a-radio-group v-model:value="addCategoryForm.group" :disabled="editingId !== null">
          <a-radio value="1">一级分类</a-radio>
          <a-radio value="2">二级分类</a-radio>
        </a-radio-group>
      </a-form-item>
      <div v-if="addCategoryForm.group === '1'">
        <a-form-item label="分类名称" name="name">
          <a-input v-model:value="addCategoryForm.name" placeholder="填写分类名称" />
        </a-form-item>
        <a-form-item label="分类显示">
          <a-switch v-model:checked="addCategoryForm.status" />
        </a-form-item>
        <a-form-item label="分类图片">
          <div class="category-image-upload">
            <a-upload
                :action="uploadAction"
                :headers="uploadHeaders"
                :before-upload="beforeUpload"
                :show-upload-list="false"
                accept="image/*"
                @change="imageUpload"
            >
              <img v-if="addCategoryForm.image" :src="imagePreviewUrl"
                   class="category-image-preview" alt="分类图片"/>
              <a-button v-else>
                <UploadOutlined/>
                点击上传
              </a-button>
            </a-upload>
            <a v-if="addCategoryForm.image" @click="addCategoryForm.image = ''">移除</a>
            <span class="unit">建议尺寸 {{ categorySpecLabel }}</span>
          </div>
        </a-form-item>

      </div>
      <div v-if="addCategoryForm.group === '2'">
        <a-form-item label="上级分类" name="parentId">
          <a-select v-model:value="addCategoryForm.parentId" placeholder="请选择父级分类">
            <a-select-option
                v-for="item in parentList"
                :key="item.id"
                :value="item.id"
            >{{ item.name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="分类名称" name="name">
          <a-input v-model:value="addCategoryForm.name" placeholder="填写分类名称" />
        </a-form-item>
        <a-form-item label="分类显示">
          <a-switch v-model:checked="addCategoryForm.status" />
        </a-form-item>
        <a-form-item label="热门推荐">
          <a-switch v-model:checked="addCategoryForm.recommend" />
        </a-form-item>
      </div>
      <a-form-item label="规格模板">
        <div class="spec-template">
          <div class="spec-template-tip">
            新增商品选择该分类时，按下面顺序自动生成规格参数行，并填入默认值，商家只需微调或选择；模板项非必填。参数名可带单位，如「重量(kg)」。
          </div>
          <div class="spec-template-row" :class="{ 'is-select': item.type === 'select' }"
               v-for="(item, index) in addCategoryForm.specTemplate" :key="index">
            <a-input
                v-model:value="item.name"
                placeholder="参数名，如 屏幕尺寸 / 重量(kg)"
                :maxlength="32"
            />
            <a-select v-model:value="item.type" :options="templateTypeOptions"/>
            <a-input
                v-model:value="item.defaultValue"
                placeholder="默认值（自动填入商品表单）"
                :maxlength="128"
            />
            <a-input
                v-if="item.type === 'select'"
                v-model:value="item.optionsText"
                placeholder="可选值，逗号分隔"
            />
            <a-button type="text" danger @click="removeTemplateItem(index)">
              <DeleteOutlined/>
            </a-button>
          </div>
          <a-button size="small" type="dashed" @click="addTemplateItem"
                    :disabled="addCategoryForm.specTemplate.length >= 30">
            <PlusOutlined/>
            添加模板字段
          </a-button>
        </div>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import {computed, ref} from "vue";
import { useDragModal } from '@/composables/useDragModal'
import {addCategory, getCategoryFlat, updateCategoryTree} from '@/api/products.js'
import {message} from 'ant-design-vue'
import {UploadOutlined, PlusOutlined, DeleteOutlined} from '@ant-design/icons-vue'
import {getToken} from '@/utils/token'
import {compressImage} from '@/utils/compressImage'
import {getImageSpec, specLabel} from '@/utils/image-spec'
// 定义并初始化弹窗响应式变量
const open = ref(false)
// 当前编辑的分类 id，null 表示新增
const editingId = ref(null)
// 弹窗标题：新增/编辑
const modalTitle = computed(() => editingId.value === null ? '新增分类' : '编辑分类')
useDragModal('drag-modal-addcat')
// 定义上级分类
// 默认打开一级分类
const addCategoryForm = ref({
  group: '1',
  name: '',
  status: true,
  parentId: undefined,
  recommend: false,
  image: '',
  // 规格模板：字段名数组，顺序即商品表单中的展示顺序，模板项非必填
  specTemplate: [],
})
// 定义父级分类列表
const parentList = ref([])
// 子组件通知父组件刷新列表
const emit = defineEmits(['refresh'])
// 重置表单，避免上次填写的内容和校验提示残留
const resetCategoryForm = () => {
  addCategoryForm.value = {
    group: '1',
    name: '',
    status: true,
    parentId: undefined,
    recommend: false,
    image: '',
    specTemplate: [],
  }
  categoryFormRef.value?.clearValidate()
}
// 加载上级分类下拉（编辑时排除自己，避免把自己选成父级）
const loadParentList = () => {
  getCategoryFlat()
      .then(res => {
        if (res.data.success) {
          // 过滤子级分类，留下父级分类
          parentList.value = res.data.data.filter(c => c.parentId === null && Number(c.id) !== Number(editingId.value))
        }
      })
      .catch(err => {
        message.error(editingId.value === null ? '新增分类失败' : '编辑分类失败')
        console.log(err)
      })
}
// 父组件调用打开新增弹窗
const addCategoryModal = () => {
  editingId.value = null
  resetCategoryForm()
  open.value = true
  loadParentList()
}
// 父组件调用打开编辑弹窗，把已有分类数据回填到表单
const editCategoryModal = (record) => {
  editingId.value = record.id
  resetCategoryForm()
  addCategoryForm.value = {
    group: record.parentId == null ? '1' : '2',
    name: record.name,
    status: record.status === 1,
    parentId: record.parentId ?? undefined,
    recommend: record.recommend === 1,
    image: record.image || '',
    specTemplate: Array.isArray(record.specTemplate) ? record.specTemplate.map(toTemplateFormItem) : [],
  }
  open.value = true
  loadParentList()
}

defineExpose({
  addCategoryModal,
  editCategoryModal
})
// =============图片上传=================
// 分类图上传地址：type=category 让后端落到 upload/image/category/
const uploadAction = import.meta.env.VITE_API_BASE_URL + '/api/admin/upload?type=category'
const uploadHeaders = {
  Authorization: 'Bearer ' + getToken()
}
// 分类图按 400×400 小方图裁切，规格表见 utils/image-spec.js
const categorySpec = getImageSpec('category')
const categorySpecLabel = specLabel(categorySpec)
const beforeUpload = (file) => compressImage(file, categorySpec)
// 预览地址：开发环境后端与前端不同源，补上接口域名；生产环境为空，走同源相对路径
const imagePreviewUrl = computed(() => {
  const url = addCategoryForm.value.image
  if (!url) return ''
  return /^https?:\/\//.test(url) ? url : (import.meta.env.VITE_API_BASE_URL || '') + url
})
// 上传成功后把相对路径存进表单，提交时随分类一起保存
const imageUpload = (info) => {
  if (info.file.status === 'done') {
    addCategoryForm.value.image = info.file.response.data.url
  } else if (info.file.status === 'error') {
    const msg = (info.file.response && info.file.response.message) || '图片上传失败，请重试'
    message.error(msg)
  }
}
//=============规格模板=================
// 控件类型：文本 / 数字 / 选项（与后端 SpecTemplateItem 常量一致）
const templateTypeOptions = [
  {value: 'text', label: '文本'},
  {value: 'number', label: '数字'},
  {value: 'select', label: '选项'},
]
// 表单内的模板项带 optionsText（逗号分隔的可选值），提交前再转成 options 数组
const toTemplateFormItem = (item) => {
  if (typeof item === 'string') {
    return {name: item, type: 'text', defaultValue: '', optionsText: ''}
  }
  return {
    name: item.name || '',
    type: item.type || 'text',
    defaultValue: item.defaultValue || '',
    optionsText: Array.isArray(item.options) ? item.options.join(',') : '',
  }
}
const parseOptions = (text) => (text || '')
    .split(/[,，]/)
    .map((option) => option.trim())
    .filter(Boolean)
const addTemplateItem = () => {
  if (addCategoryForm.value.specTemplate.length >= 30) {
    message.warning('规格模板最多 30 项')
    return
  }
  addCategoryForm.value.specTemplate.push({name: '', type: 'text', defaultValue: '', optionsText: ''})
}

const removeTemplateItem = (index) => {
  addCategoryForm.value.specTemplate.splice(index, 1)
}

//=============表单校验=================
// 定义表单实例
const categoryFormRef = ref(null)
// 定义校验规则
const categoryFormRules = {
  name: [{required: true, message: '分类名称不能为空', trigger: 'blur'}],
  parentId: [{required: true, message: '请选择上级分类', trigger: 'blur'}],
}
// 弹窗中表单的提交：editingId 有值走更新，否则走新增
const handleCategorySubmit = () => {
  const isEdit = editingId.value !== null
  // 选项类型必须配置可选值，否则商品表单无法选择
  const invalidSelect = addCategoryForm.value.specTemplate
      .find((item) => (item.name || '').trim() && item.type === 'select' && !parseOptions(item.optionsText).length)
  if (invalidSelect) {
    message.warning(`「${invalidSelect.name}」类型为选项时必须填写可选值`)
    return
  }
  categoryFormRef.value.validate()
      .then(() => {
        const data = {
          name: addCategoryForm.value.name,
          parentId: addCategoryForm.value.group === '1' ? null : addCategoryForm.value.parentId,
          status: addCategoryForm.value.status ? 1 : 0,
          recommend: addCategoryForm.value.recommend ? 1 : 0,
          // 分类图只属于一级分类，二级分类不提交；编辑时传空串才能清空已有图片（null 会被后端忽略）
          image: addCategoryForm.value.group === '1'
              ? (addCategoryForm.value.image || (isEdit ? '' : null))
              : null,
          // 空数组表示清空模板；模板项带类型/默认值/可选值，商品表单按顺序生成并自动填值
          specTemplate: addCategoryForm.value.specTemplate
              .map((item) => ({
                name: (item.name || '').trim(),
                type: item.type || 'text',
                defaultValue: (item.defaultValue || '').trim(),
                options: item.type === 'select' ? parseOptions(item.optionsText) : [],
              }))
              .filter((item) => item.name)
        }
        return isEdit ? updateCategoryTree(editingId.value, data) : addCategory(data)
      })
      .then(() => {
        message.success(isEdit ? '编辑分类成功' : '新增分类成功')
        emit('refresh')
        open.value = false
      })
      .catch(err => {
        if (err && err.errorFields) {
          console.log('表单校验未通过', err)
        } else {
          message.error(err.response?.data?.message || (isEdit ? '编辑分类失败' : '新增分类失败'))
          console.log(err)
        }
      })
}

//


</script>

<style scoped lang="less">
.category-image-upload {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.unit {
  color: #999;
  font-size: 12px;
}

.spec-template {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.spec-template-tip {
  color: #999;
  font-size: 12px;
}

.spec-template-row {
  display: grid;
  grid-template-columns: 1fr 92px 1fr 32px;
  align-items: center;
  gap: 8px;
}

.spec-template-row.is-select {
  grid-template-columns: 1fr 92px 1fr 1fr 32px;
}

.category-image-preview {
  width: 88px;
  height: 88px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #d9d9d9;
  cursor: pointer;
}
</style>
