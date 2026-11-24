<template>
  <a-form :model="salesInfoForm" ref="salesInfoFormRef"
          :label-col="{span:3,style:{paddingRight:'5px'}}">
    <a-form-item label="上架状态">
      <a-radio-group v-model:value="salesInfoForm.listingStatus">
        <a-radio :value="1">上架</a-radio>
        <a-radio :value="0">下架</a-radio>
      </a-radio-group>
    </a-form-item>
  </a-form>
</template>
<script setup>
import {ref} from 'vue'
// 销售信息只保留上架状态；价格/库存/编码全在 SKU 表格里填
const salesInfoFormRef = ref()
const salesInfoForm = ref({ listingStatus: 1 })
// 暴露给父组件的 salesInfoForm（handleSubmit 里展开 goodsData 用）
defineExpose({salesInfoForm})
</script>
