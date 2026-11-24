<template>
  <div>
    <a-tabs v-model:activeKey="activeTab" @change="handleTabChange">
      <a-tab-pane key="members" tab="会员列表">
        <div class="head">
          <a-form layout="inline">
            <a-form-item>
              <a-input v-model:value="query.keyword" placeholder="昵称 / 手机号" allow-clear
                       @pressEnter="handleSearch" />
            </a-form-item>
            <a-form-item>
              <a-select v-model:value="query.levelId" style="width: 160px" placeholder="会员等级" allow-clear
                        :options="levelOptions" />
            </a-form-item>
            <a-form-item>
              <a-select v-model:value="query.status" style="width: 130px" placeholder="状态"
                        :options="statusOptions" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" @click="handleSearch">
                <SearchOutlined />
                搜索
              </a-button>
            </a-form-item>
          </a-form>
        </div>

        <a-table :dataSource="members" :columns="memberColumns" :pagination="pagination" row-key="id"
                 @change="handleTableChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'phone'">
              {{ maskPhone(record.phone) }}
            </template>
            <template v-else-if="column.key === 'avatar'">
              <a-avatar :src="record.avatar" :size="32">{{ (record.name || '用')[0] }}</a-avatar>
            </template>
            <template v-else-if="column.key === 'levelName'">
              <a-tag color="gold">{{ record.levelName || '普通会员' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'totalPaidAmount'">
              ¥{{ money(record.totalPaidAmount) }}
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="record.isDeleted ? 'default' : 'green'">
                {{ record.isDeleted ? '已注销' : '正常' }}
              </a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button v-if="!record.isDeleted" v-permission="'business:member:refresh'"
                        type="primary" size="small" :loading="refreshingId === record.id"
                        @click="handleRefresh(record)">刷新成长值</a-button>
              <a-button v-else size="small" @click="openDetail(record)">查看</a-button>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="levels" tab="等级配置">
        <a-alert type="info" show-icon :closable="false" class="tip"
                 message="成长值 = 已支付实付 − 已退款，1 元 = 1 成长值；等级只升不降。用户打开会员中心时自动刷新。" />
        <a-table :dataSource="levels" :columns="levelColumns" :pagination="false" row-key="id">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <a-input v-model:value="record.name" :maxlength="16" style="width: 140px" />
            </template>
            <template v-else-if="column.key === 'growthThreshold'">
              <a-input-number v-model:value="record.growthThreshold" :min="0" :precision="0"
                              style="width: 120px" />
            </template>
            <template v-else-if="column.key === 'benefits'">
              <a-input v-model:value="record.benefits" :maxlength="120" style="width: 100%" />
            </template>
            <template v-else-if="column.key === 'sortOrder'">
              <a-input-number v-model:value="record.sortOrder" :min="0" :precision="0"
                              style="width: 90px" />
            </template>
            <template v-else-if="column.key === 'action'">
              <a-button v-permission="'business:member:edit'" type="primary" size="small"
                        :loading="savingId === record.id" @click="saveLevel(record)">保存</a-button>
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>

    <!-- 已注销会员只读详情 -->
    <a-modal v-model:visible="detailVisible" title="会员详情" :footer="null" :width="560">
      <a-descriptions v-if="detail" :column="1" bordered size="small">
        <a-descriptions-item label="用户 ID">{{ detail.id }}</a-descriptions-item>
        <a-descriptions-item label="昵称">{{ detail.name || '—' }}</a-descriptions-item>
        <a-descriptions-item label="手机号">{{ maskPhone(detail.phone) || '—' }}</a-descriptions-item>
        <a-descriptions-item label="状态">
          <a-tag :color="detail.isDeleted ? 'default' : 'green'">
            {{ detail.isDeleted ? '已注销' : '正常' }}
          </a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="会员等级">{{ detail.levelName || '普通会员' }}</a-descriptions-item>
        <a-descriptions-item label="成长值">{{ detail.growthValue || 0 }}</a-descriptions-item>
        <a-descriptions-item label="累计消费">¥{{ money(detail.totalPaidAmount) }}</a-descriptions-item>
        <a-descriptions-item label="已支付订单">{{ detail.paidOrderCount || 0 }}</a-descriptions-item>
        <a-descriptions-item label="注册时间">{{ detail.createTime || '—' }}</a-descriptions-item>
      </a-descriptions>
    </a-modal>
  </div>
</template>

<script setup>
import { computed, onActivated, onMounted, ref } from 'vue'
import { SearchOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getMemberDetail,
  getMemberLevels,
  getMemberList,
  refreshMember,
  updateMemberLevel,
} from '@/api/member.js'
import { maskPhone } from '@/utils/mask.js'

const activeTab = ref('members')
const members = ref([])
const levels = ref([])
const savingId = ref(null)
const refreshingId = ref(null)
const detailVisible = ref(false)
const detail = ref(null)
const query = ref({ keyword: '', levelId: undefined, status: 'NORMAL' })

const statusOptions = [
  { value: 'NORMAL', label: '正常' },
  { value: 'DELETED', label: '已注销' },
]

const pagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
  showSizeChanger: true,
  showTotal: (total) => `共 ${total} 条`,
})

const levelOptions = computed(() =>
  levels.value.map((level) => ({ value: level.id, label: level.name }))
)

const memberColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '头像', key: 'avatar' },
  { title: '昵称', dataIndex: 'name', key: 'name' , width: 100},
  { title: '手机号', dataIndex: 'phone', key: 'phone' },
  { title: '会员等级', key: `levelName`},
  { title: '成长值', dataIndex: 'growthValue', key: 'growthValue', width: 100 },
  { title: '累计消费', key: 'totalPaidAmount' },
  { title: '已支付订单', dataIndex: 'paidOrderCount', key: 'paidOrderCount', width: 110 },
  { title: '注册时间', dataIndex: 'createTime', key: 'createTime' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action', width: 130 },
]

const levelColumns = [
  { title: '等级 ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '等级名称', key: 'name', width: 170 },
  { title: '成长值门槛', key: 'growthThreshold', width: 140 },
  { title: '权益文案', key: 'benefits' },
  { title: '排序', key: 'sortOrder', width: 110 },
  { title: '操作', key: 'action', width: 90 },
]

function loadMembers() {
  getMemberList({
    page: pagination.value.current,
    pageSize: pagination.value.pageSize,
    keyword: query.value.keyword || undefined,
    levelId: query.value.levelId || undefined,
    status: query.value.status || undefined,
  }).then((res) => {
    members.value = res.data.data.list || []
    pagination.value.total = res.data.data.total || 0
  })
}

function loadLevels() {
  getMemberLevels().then((res) => {
    levels.value = res.data.data || []
  })
}

function handleSearch() {
  pagination.value.current = 1
  loadMembers()
}

function handleTableChange(pager) {
  pagination.value.current = pager.current
  pagination.value.pageSize = pager.pageSize
  loadMembers()
}

function handleTabChange(key) {
  if (key === 'members') {
    loadMembers()
  } else {
    loadLevels()
  }
}

function openDetail(record) {
  getMemberDetail(record.id).then((res) => {
    detail.value = res.data.data
    detailVisible.value = true
  })
}

function handleRefresh(record) {
  refreshingId.value = record.id
  refreshMember(record.id)
    .then(() => {
      message.success('成长值已刷新')
      loadMembers()
    })
    .finally(() => {
      refreshingId.value = null
    })
}

function saveLevel(record) {
  if (!record.name) {
    message.warning('等级名称不能为空')
    return
  }
  savingId.value = record.id
  updateMemberLevel(record.id, {
    name: record.name,
    growthThreshold: record.growthThreshold,
    benefits: record.benefits,
    sortOrder: record.sortOrder,
  })
    .then(() => {
      message.success('等级已更新')
      loadLevels()
    })
    .finally(() => {
      savingId.value = null
    })
}

function money(value) {
  if (value === null || value === undefined || value === '') return '0.00'
  return Number(value).toFixed(2)
}

onMounted(() => {
  loadLevels()
  loadMembers()
})
onActivated(() => {
  loadMembers()
})
</script>

<style scoped>
.head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin: 16px 0;
}

.tip {
  margin-bottom: 16px;
}
</style>
