<template>
  <a-modal v-model:visible="addressVisible" class="address-modal" :width="800">
    <!--栅格总分为 24  :label-col="{ span: 3 }"是指标签名占3格-->
    <a-form :model="addressForm" class="address-form" :label-col="{ span: 3 }" ref="addressFormRef" :rules="addressRules">
      <a-form-item label="收件人" name="consignee" required>
        <a-input placeholder="请输入收件人姓名" v-model:value="addressForm.consignee" />
      </a-form-item>
      <a-form-item label="联系电话" name="phone" required>
        <a-input placeholder="请输入手机号" v-model:value="addressForm.phone" />
      </a-form-item>
      <a-form-item label="所在地区" name="region" required>
          <span><a-input placeholder="省、市、区、乡镇/街道" style="width:200px; margin-right:20px; " class="place"
                         @click="handlePlace" v-model:value="addressForm.region" /></span>
        <span><EnvironmentOutlined /></span> <span>定位</span>
      </a-form-item>
      <a-form-item label="详细地址" name="detail_address" required>
        <a-input placeholder="请输入详细地址信息，如道路、门牌号、小区、楼栋号、单元等信息。"
                 v-model:value="addressForm.detail_address" />
      </a-form-item>
    </a-form>

    <div class="default" @click="isDefault = !isDefault">
      <span>设为默认地址</span>
      <span class="dot" :class="{ active: isDefault }"></span>
    </div>
    <div v-if="editingId" class="delete" :class="{ disabled: !addressForm.consignee }" @click="addressForm.consignee && handleDeleteAddress()">删除地址</div>
    <div class="save" :class="{ disabled: saving }" @click="handleSaveAddress">{{ saving ? '保存中...' : '保存地址' }}</div>
    <template #footer><span></span></template>
  </a-modal>
  <!--省、市、区、乡镇/街道下拉列表的弹窗-->
  <Place ref="placeRef" @confirm="onRegionConfirm" />
</template>
<script setup>
import {ref} from 'vue'
import {EnvironmentOutlined} from '@ant-design/icons-vue'
import Place from './Place.vue'
import {addAddress, updateAddress, deleteAddress} from '@/api/address.js'
import {message, Modal} from "ant-design-vue";

const addressVisible = ref(false)
const editingId = ref(null)
// =============== 表单 =======================
const addressForm = ref({
  consignee: '',
  phone: '',
  region: '',
  detail_address: ''
})
// 定义打开弹窗的方法，可选传入已有地址用于编辑
const addressVisibleOpen = (data = null) => {
  if (data) {
    editingId.value = data.id || null
    addressForm.value = {
      consignee: data.consignee || '',
      phone: data.phone || '',
      region: data.region || '',
      detail_address: data.detail_address || ''
    }
    isDefault.value = data.is_default === 1
  } else {
    editingId.value = null
    addressForm.value = { consignee: '', phone: '', region: '', detail_address: '' }
    isDefault.value = false
  }
  addressVisible.value = true
}
// 暴露打开弹窗的方法给父组件Settlement.vue使用
defineExpose({addressVisibleOpen})
// =============== 默认地址 ==================
const isDefault = ref(false)
// ===================== 省、市、区、乡镇/街道下拉列表的弹窗 ==============
// 创建实例，便于调用打开弹窗的方法
const placeRef = ref(null)
// 通过输入框的点击事件，调用方法
const handlePlace = () => {
  placeRef.value.placeVisibleOpen()
}
// 接收 Place 组件选择完成的地区
const onRegionConfirm = (region) => {
  addressForm.value.region = region
}
const emit = defineEmits(['saved'])
// ====================== 删除地址 ======================
const handleDeleteAddress = () => {
  Modal.confirm({
    title: '确认删除',
    content: '删除后不可恢复，确定要删除该地址吗？',
    okText: '确定删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: () => {
      return deleteAddress(editingId.value).then(() => {
        message.success('地址已删除')
        editingId.value = null
        addressVisible.value = false
        emit('saved')
      }).catch(() => {
        message.error('删除失败')
      })
    }
  })
}
// ====================== 保存为地址按钮 =====================
const saving = ref(false)
const handleSaveAddress = () => {
  if (saving.value) return
  saving.value = true
  addressFormRef.value.validate()
    .then(() => {
      const data = { ...addressForm.value, is_default: isDefault.value ? 1 : 0 }
      return editingId.value
        ? updateAddress(editingId.value, data)
        : addAddress(data)
    })
    .then(() => {
      addressForm.value = { consignee: '', phone: '', region: '', detail_address: '' }
      isDefault.value = false
      editingId.value = null
      message.success('地址保存成功')
      emit('saved')
      addressVisible.value = false
    })
    .catch((err) => {
      if (err?.errorFields) return  // 校验失败，表单已自动提示，不做额外处理
      message.error('地址保存失败')
      console.log(err)
    })
    .finally(() => {
      saving.value = false
    })
}
// ==================== 校验规则 =====================
// 定义表单实例
const addressFormRef = ref(null)
// 定义规则
const addressRules = {
  consignee: [{required:true, "message": "收件人姓名不能为空",trigger: "blur"}],
  phone: [{required:true, "message": "联系电话不能为空",trigger: "blur"},
    {min:11,max:11, "message": "联系电话格式不正确，请输入11位手机号"},
    { pattern: /^1\d{10}$/, message: '请输入正确的11位手机号', trigger: 'blur' }],
  region: [{required:true, "message":  "所在地区不能为空",trigger: "change"}],
  detail_address: [{required:true, "message": "详细地址不能为空",trigger: "blur"}],
}
//
</script>


<style scoped>
.address-form {
  margin-top: 30px;
  padding: 0 10px;
}

.address-form :deep(.ant-form-item-label) {
  text-align: left;
}

.address-form :deep(.ant-form-item-label > label) {
  font-size: 16px;
}

.address-form span {
  font-size: 16px;
}

.address-form .place {
  cursor: text; /*鼠标放上去是光标效果*/
}

.address-modal .default {
  display: flex;
  justify-content: space-between;
  padding: 10px 22px;
  font-size: 16px;

}

.address-modal .default .dot {
  width: 25px;
  height: 25px;
  border-radius: 50%;
  border: 1px solid #ccc;
  cursor: pointer;
  transition: background-color .2s;
  display: flex;
  align-items: center;
  justify-content: center;
}
.address-modal .default .dot.active {
  background-color: #1890ff;
  border-color: #1890ff;
}
.address-modal .default .dot.active::after {
  content: '';
  display: block;
  width: 7px;
  height: 12px;
  border: 2px solid #fff;
  border-top: none;
  border-left: none;
  transform: rotate(45deg);
  margin-top: -2px;
}

.address-modal .save {
  margin: 10px auto;
  width: 300px;
  height: 50px;
  line-height: 50px;
  text-align: center;
  border: 1px solid #ccc;
  font-size: 20px;
  border-radius: 15px;
  cursor: pointer;
}
.address-modal .save.disabled {
  opacity: 0.5;
  pointer-events: none;
}
.address-modal .delete {
  margin: 0 auto;
  width: 300px;
  height: 44px;
  line-height: 44px;
  text-align: center;
  font-size: 14px;
  color: #999;
  cursor: pointer;
}
.address-modal .delete:hover {
  color: #ff4d4f;
  border-color: #ff4d4f;
}
.address-modal .delete.disabled {
  color: #d9d9d9;
  cursor: not-allowed;
  pointer-events: none;
}
</style>