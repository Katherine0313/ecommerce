<template>
  <div class="rich-editor-wrap">
    <Toolbar :editor="editorRef" :defaultConfig="toolbarConfig" class="rich-editor-toolbar" />
    <Editor
        :defaultConfig="editorConfig"
        class="rich-editor-body"
        @onCreated="handleCreated"
        @onChange="handleChange"
    />
  </div>
</template>
<script setup>
import {ref, shallowRef, watch, onBeforeUnmount} from 'vue'
import {Editor, Toolbar} from '@wangeditor/editor-for-vue'
import {SlateEditor} from '@wangeditor/editor'
import '@wangeditor/editor/dist/css/style.css'
import {message} from 'ant-design-vue'
import axios from '@/api/request.js'
import {compressImage} from '@/utils/compressImage'
import {getImageSpec} from '@/utils/image-spec'

// 正文图按最长边 1200 压缩：富文本满宽展示，2x 够用，规格表见 utils/image-spec.js
const bodySpec = getImageSpec('body')

const props = defineProps({
  modelValue: {type: String, default: ''},
  placeholder: {type: String, default: '请输入商品图文详情...'}
})
const emit = defineEmits(['update:modelValue'])

// 暴露给父组件：AI 生成详情文案/详情图时原地插入（不离开添加商品页）
// wangEditor 坑：编辑器从未被聚焦过时 selection 为 null，dangerouslyInsertHtml 会
// 静默丢弃内容（不报错也不插入），表现为「点填入后结果消失」；先把光标定位到文末再插
defineExpose({
  insertHtml: (html) => {
    const e = editorRef.value
    if (!e || !html) return
    if (!e.selection) {
      e.select(SlateEditor.end(e, []))
    }
    e.dangerouslyInsertHtml(html)
  }
})

const editorRef = shallowRef(null)

const toolbarConfig = {
  toolbarKeys: ['headerSelect', 'bold', 'italic', 'underline', 'color', 'bgColor',
    'justifyLeft', 'justifyCenter', 'justifyRight', 'divider', 'uploadImage',
    'insertTable', 'insertLink', 'undo', 'redo', 'clearStyle']
}
const editorConfig = {
  placeholder: props.placeholder,
  height: 300,
  MENU_CONF: {
    uploadImage: {
      async customUpload(file, insertFn) {
        // 超过 30MB 直接拒绝：canvas 解码超大图会吃爆浏览器内存
        if (file.size > 30 * 1024 * 1024) {
          message.error('图片不能超过 30MB')
          return
        }
        try {
          // 先压缩原图再上传，避免详情页富文本里全是原图导致卡顿
          const compressed = await compressImage(file, bodySpec)
          // 体积上限放在压缩之后判：原图 12MB 压完可能只剩 200KB，
          // 放在压缩前判会把这种图误拒
          if (compressed.size > 5 * 1024 * 1024) {
            message.error('图片压缩后仍超过 5MB，请换一张')
            return
          }
          const formData = new FormData()
          formData.append('file', compressed)
          const res = await axios.post('/api/admin/upload?type=body', formData)
          insertFn(res.data.data.url, '', res.data.data.url)
        } catch (err) {
          // 上传失败（如文件类型不被允许）：把后端返回的具体原因提示给用户
          message.error((err.response && err.response.data && err.response.data.message) || '图片上传失败，请重试')
        }
      }
    }
  }
}

// 父组件更新 modelValue 时（如编辑回填），同步到编辑器
watch(() => props.modelValue, (val) => {
  if (editorRef.value && editorRef.value.getHtml() !== val) {
    editorRef.value.setHtml(val)
  }
})

const handleCreated = (editor) => {
  editorRef.value = editor
  // 初始值回填
  if (props.modelValue) {
    editor.setHtml(props.modelValue)
  }
}

// 内容变化时回传给父组件
const handleChange = (editor) => {
  emit('update:modelValue', editor.getHtml())
}

onBeforeUnmount(() => {
  editorRef.value?.destroy()
})
</script>
<style scoped>
.rich-editor-wrap {
  width: 100%;
  /* 确保编辑区不被 a-card 标题栏/按钮层覆盖 */
  position: relative;
  z-index: 1;
  /* 把 wangEditor 自带的白底配色接到 stepin 主题变量上，切换夜间/暗色主题时编辑器一起变色 */
  --w-e-textarea-bg-color: var(--color-bg-container, #fff);
  --w-e-textarea-color: var(--color-text-1, rgba(0, 0, 0, 0.85));
  --w-e-textarea-border-color: var(--color-border-1, #d9d9d9);
  --w-e-textarea-slight-border-color: var(--color-border-2, #e8e8e8);
  --w-e-textarea-slight-color: var(--color-text-3, #d4d4d4);
  --w-e-textarea-slight-bg-color: var(--color-fill-3, #f5f2f0);
  --w-e-toolbar-color: var(--color-text-2, #595959);
  --w-e-toolbar-bg-color: var(--color-bg-container, #fff);
  --w-e-toolbar-active-color: var(--color-text-1, #333);
  --w-e-toolbar-active-bg-color: var(--color-bg-hover, #f1f1f1);
  --w-e-toolbar-disabled-color: var(--color-text-4, #999);
  --w-e-toolbar-border-color: var(--color-border-1, #e8e8e8);
  --w-e-modal-button-bg-color: var(--color-bg-container-light, #fafafa);
  --w-e-modal-button-border-color: var(--color-border-1, #d9d9d9);
}

.rich-editor-toolbar {
  border: 1px solid var(--color-border-1, #d9d9d9);
  border-bottom: none;
  border-radius: 6px 6px 0 0;
  position: relative;
  z-index: 2;
}

.rich-editor-body {
  border: 1px solid var(--color-border-1, #d9d9d9);
  border-radius: 0 0 6px 6px;
  min-height: 300px;
  overflow-y: auto;
  /* 关键：给 body 明确的层叠上下文和尺寸，防止被父元素 overflow 剪裁或被兄弟元素覆盖 */
  position: relative;
  z-index: 1;
}

/* wangEditor 的 contenteditable 内部区也要确保能点 */
.rich-editor-body :deep(.w-e-textarea) {
  position: relative;
  z-index: 1;
  min-height: 280px;
  cursor: text;
}
</style>
