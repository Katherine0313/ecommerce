<template>
  <div class="item">
    <div class="item-title">变更记录</div>
    <a-timeline v-if="logs && logs.length">
      <a-timeline-item v-for="log in logs" :key="log.id"
                       :color="log.afterStatus === 'rejected' ? 'red' : 'blue'">
        <div class="log-head">
          <span class="log-title">{{ log.operateTypeDesc }}</span>
          <a-tag color="default">{{ log.userTypeDesc }}</a-tag>
          <span class="log-time">{{ log.createdAt }}</span>
        </div>
        <div class="log-content">{{ log.content }}</div>
        <div v-if="log.merchantRemark" class="log-remark">内部备注：{{ log.merchantRemark }}</div>
      </a-timeline-item>
    </a-timeline>
    <a-empty v-else description="暂无变更记录" />
  </div>
</template>

<script setup>
// 售后变更流水时间线（后台版：带商家内部备注）
defineProps({
  logs: {type: Array, default: () => []}
})
</script>

<style scoped>
.log-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-title {
  font-size: 14px;
  font-weight: 600;
}

.log-time {
  color: #999;
  font-size: 12px;
}

.log-content {
  margin-top: 4px;
  font-size: 13px;
}

.log-remark {
  margin-top: 4px;
  font-size: 12px;
  color: #d46b08;
}
</style>
