<template>
  <div class="add-tag">
    <a-button v-permission="'product:tag:add'" type="primary" @click="handleAddTag">新增标签
      <PlusOutlined />
    </a-button>
    <a-modal v-model:visible="newTagModal" title="新增标签" @ok="submitAddTag"
             okText="确定" cancelText="取消" wrap-class-name="drag-modal-tag">
      <a-form :model="tagForm" ref="tagFormRef" :rules="tagFormRules">
        <a-form-item label="标签名称" name="name" required>
          <a-input placeholder="请输入标签名称" v-model:value="tagForm.name"></a-input>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
  <!-- 关联产品弹窗放顶层，通过 ref 控制显隐 -->
  <TagModal ref="tagModalRef" @refresh="loadTagList" />
  <!-- 表格，首行复选框由 row-selection 控制 -->
  <a-table :row-key="(record) => record.id" :data-source="tagList" class="tag-table" :row-selection="{}">
    <a-table-column title="标签名称" data-index="name" />
    <a-table-column title="关联产品数" data-index="productCount" align="center" />
    <a-table-column title="在网店显示" align="center">
      <template #default="{record}">
        <a-switch :checked="record.status === 1"
                  @change="(checked) => statusChange(record,checked)" />
      </template>
    </a-table-column>
    <a-table-column title="操作" align="center">
      <template #default="{record}">
        <a-button v-permission="'product:tag:edit'" type="primary" style="margin-right: 10px"
                  @click="tagModalRef.firstOpen(record)">关联产品</a-button>
        <a-button v-permission="'product:tag:remove'" type="primary" style="margin-right: 10px"
                  @click="handleDeleteTag(record)">删除</a-button>
      </template>
    </a-table-column>
  </a-table>

</template>

<script setup>
import {ref, onMounted} from 'vue'
import { useDragModal } from '@/composables/useDragModal'
useDragModal('drag-modal-tag')
import {getTagList, updateTag, addTag, deleteTag} from '@/api/tags.js'
import {message, Modal} from 'ant-design-vue'
import TagModal from '@/pages/goods/tag/TagModal.vue'
import {PlusOutlined} from '@ant-design/icons-vue';
// 定义新增标签的对话框响应式变量
const newTagModal = ref(false)
//
// 关联产品弹窗的引用，通过 tagModalRef.value.open(record) 打开
const tagModalRef = ref(null)
//
const tagList = ref([])
// 新增标签
const tagForm = ref({
  name: '',
})
const tagFormRef = ref(null)
const tagFormRules = {
  name: [
    {required: true, message: '标签名不能为空', trigger: 'blur'},
  ]
}
// 新增标签,点击新增标签，打开弹窗
const handleAddTag = () => {
  newTagModal.value = true
}
// 新增标签弹窗的ok事件
const submitAddTag = () => {
  tagFormRef.value.validate()
      .then(() => addTag({name: tagForm.value.name}))
      .then(() => {
        message.success('标签新增成功')
        console.log(tagForm.value.name)
        newTagModal.value = false
        tagForm.value.name = ''
        loadTagList()
      })
      .catch(() => {
        // validate 失败（红字那种）或 addTag 抛错都进这里，
        // 表单校验失败时错误已经显示在 a-form-item 下方，这里吞掉就行
      })
}


// 加载标签列表
const loadTagList = () => {
  getTagList()
      .then(res => {
        tagList.value = res.data.data
      })
      .catch(err => {
        console.log(err)
      })
}


onMounted(() => {
  loadTagList()
})
// 标签开关状态
const statusChange = (record, checked) => {
  // 保存旧值，请求失败时回滚，保证 UI 和数据库一致
  const oldStatus = record.status
  record.status = checked ? 1 : 0
  updateTag(record.id, {status: checked ? 1 : 0})
      .then(() => {
        message.success(checked ? '已在网店显示' : '已从网店隐藏')
      })
      .catch(err => {
        // 回滚开关，否则页面显示已改而库里没改，商城那边实际状态对不上
        record.status = oldStatus
        // 401/403/404/500 已由 request.js 拦截器统一提示，这里只兜网络错误，避免弹两条
        if (!err.response) message.error('网络异常，请稍后重试')
        console.log(err)
      })
}
// 删除操作的点击事件
const handleDeleteTag = (record) => {
  Modal.confirm({
    title: '确认删除',
    content: `请问确认删除标签「 ${record.name} 」吗?`,
    onOk: () => {
      deleteTag(record.id)
          .then(() => {
            message.success(`标签 "${record.name}" 删除成功`)
            loadTagList()
          })
          .catch(err => {
            message.error('标签不存在')
            console.log(err)
          })
    }
  })
}


</script>

<style scoped>
/*新增标签按钮*/
.add-tag {
  margin-bottom: 16px;
}

/*标签页首页*/

/*行元素*/
.tag-table :deep(.ant-table-tbody > tr > td) {
  font-size: 14px;
}


</style>

