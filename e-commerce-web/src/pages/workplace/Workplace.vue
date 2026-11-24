<template>
  <div class="workplace grid grid-rows-none gap-4 mt-xxs">
    <div
      class="hero bg-container p-base rounded-b-lg rounded-tr-lg pt-8 flex items-end justify-between"
    >
      <div class="flex items-center">
        <!-- JWT 里只有 {username, role}，没有头像字段，沿用顶栏同一张默认图 -->
        <div class="relative w-16 h-16 flex-none">
          <!-- 兔耳挂件：按 64px 头像等比缩放，耳朵画在圆形下层 -->
          <svg
            class="absolute left-0 z-0 w-full h-auto pointer-events-none"
            style="top: -45.5%"
            viewBox="0 -20 44 64"
            aria-hidden="true"
            focusable="false"
          >
            <g transform="rotate(-12 12.2 4)">
              <ellipse cx="12.2" cy="-6" rx="6" ry="13" fill="#f8dbe2" />
              <ellipse cx="12.2" cy="-5" rx="2.8" ry="9.4" fill="#f3b6c6" />
            </g>
            <g transform="rotate(12 31.8 4)">
              <ellipse cx="31.8" cy="-6" rx="6" ry="13" fill="#f8dbe2" />
              <ellipse cx="31.8" cy="-5" rx="2.8" ry="9.4" fill="#f3b6c6" />
            </g>
          </svg>
          <div class="relative z-10 w-full h-full rounded-full overflow-hidden bg-white">
            <img
              src="@/assets/avatar/zongjie.webp"
              alt="杰米多多"
              class="block w-full h-full object-cover"
            />
          </div>
        </div>
        <div class="ml-base">
          <div class="text-title font-bold text-lg">{{ greeting }}</div>
          <div class="text-subtext font-bold text-sm">{{ roleText }}</div>
        </div>
      </div>
      <div class="flex flex-col items-end gap-2">
        <div class="hero-stats flex items-start">
          <a-statistic
            class="w-20"
            :valueStyle="{ fontWeight: 500 }"
            title="商品"
            :value="headline.products"
          ></a-statistic>
          <a-statistic
            class="w-20"
            :valueStyle="{ fontWeight: 500 }"
            title="订单"
            :value="headline.orders"
          ></a-statistic>
          <a-statistic
            :valueStyle="{ fontWeight: 500 }"
            title="累计销售额"
          >
            <template #formatter>
              <div class="whitespace-nowrap">
                <span class="text-base">￥</span
                >{{ formatThousand(headline.amount, 2) }}
              </div>
            </template>
          </a-statistic>
        </div>
      </div>
    </div>
    <div class="card-grid grid grid-cols-12 gap-6">
      <mini-statistic-card
        class="card col-span-12 mdx:col-span-6 xlx:col-span-3"
        v-for="(item, i) in cards"
        :key="i"
        :title="item.title"
        :value="item.value"
        :change="item.change"
        :up="item.up"
        :down="item.down"
      >
        <template #icon>
          <component
            :class="`text-[50px] -translate-x-2 -translate-y-2 opacity-75 ${item.iconClass}`"
            v-bind:is="item.icon"
          />
        </template>
      </mini-statistic-card>
    </div>
    <!-- 统一下方所有报表的时间维度：营业额、订单统计、用户增长、销量排名 -->
    <div class="report-toolbar flex flex-wrap items-center justify-center gap-8">
      <a-radio-group
        class="period-selector"
        :value="reportPeriod"
        button-style="solid"
        @change="(e) => setReportPeriod(e.target.value)"
      >
        <a-radio-button
          v-for="item in PERIODS"
          :key="item.value"
          :value="item.value"
        >
          {{ item.label }}
        </a-radio-button>
      </a-radio-group>
      <!-- 运营数据导出：跟随时间维度选择器放在一起 -->
      <a-button type="primary" :loading="exporting" @click="handleExport" size="large">
        <template #icon><download-outlined /></template>
        数据导出
      </a-button>
    </div>
    <div class="overview grid grid-cols-12 gap-4">
      <turnover-panel
        class="col-span-12 xlx:col-span-6 xxlx:col-span-5 drop-shadow-sm"
        :turnover="turnover"
        :report="turnoverReport"
        :period="reportPeriod"
      />
      <order-stats
        class="col-span-12 xlx:col-span-6 xxlx:col-span-7 drop-shadow-sm"
        :series="orderStatsSeries"
        :period="reportPeriod"
        :change="orderPeriodGrowth.change"
        :up="orderPeriodGrowth.up"
        :down="orderPeriodGrowth.down"
      />
    </div>
    <div class="project-list grid grid-cols-12 gap-4">
      <user-growth
        class="col-span-12 xlx:col-span-7 xxlx:col-span-8 drop-shadow-sm"
        :data="userGrowth"
        :period="reportPeriod"
        :error="userGrowthError"
      />
      <sales-ranking
        class="col-span-12 xlx:col-span-5 xxlx:col-span-4 drop-shadow-sm"
        :list="salesRanking"
        :period="reportPeriod"
      />
    </div>
    <about-us :shop-info="shopInfo" :todo="todo" />
  </div>
</template>

<script setup>
  import { computed, onActivated, ref } from 'vue';
  import dayjs from 'dayjs';
  import { message } from 'ant-design-vue';
  import { DownloadOutlined } from '@ant-design/icons-vue';
  import { exportOperationReport } from '@/api/report.js';
  import MiniStatisticCard from '@/components/statistic/MiniStatisticCard.vue';
  import TurnoverPanel from './TurnoverPanel.vue';
  import OrderStats from './OrderStats.vue';
  import UserGrowth from './UserGrowth.vue';
  import SalesRanking from './SalesRanking.vue';
  import AboutUs from './AboutUs.vue';
  import { useUnbounded } from '@/utils/useTheme';
  import { formatThousand } from '@/utils/formatter';
  import { getCurrentUser } from '@/utils/auth';
  import { PERIODS, useWorkplaceData } from './useWorkplaceData';

  useUnbounded();

  const {
    shopInfo,
    headline,
    cards,
    todo,
    turnover,
    turnoverReport,
    reportPeriod,
    orderStatsSeries,
    orderPeriodGrowth,
    salesRanking,
    userGrowth,
    userGrowthError,
    loadAll,
    setReportPeriod,
  } = useWorkplaceData();

  // 业务数据每次进入都刷新（首次挂载也会触发 onActivated）
  onActivated(loadAll);

  const userName = getCurrentUser()?.username || '访客';

  // 运营数据报表导出
  const exporting = ref(false);
  async function handleExport() {
    if (exporting.value) return;
    exporting.value = true;
    try {
      const res = await exportOperationReport();
      const blob = new Blob([res.data], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      });
      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `运营数据报表_${dayjs().format('YYYYMMDD')}.xlsx`;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      URL.revokeObjectURL(url);
    } catch (err) {
      console.error('导出运营数据报表失败', err);
      message.error('导出失败，请稍后重试');
    } finally {
      exporting.value = false;
    }
  }

  const greeting = computed(() => {
    const h = dayjs().hour();
    const word =
      h < 6
        ? '凌晨好'
        : h < 12
        ? '上午好'
        : h < 14
        ? '中午好'
        : h < 18
        ? '下午好'
        : '晚上好';
    return `${word}，${userName}，欢迎回来`;
  });

  // 顶部展示真实 RBAC 角色 + 店铺名
  const ROLE_NAMES = {
    admin: '店铺管理员',
    operator: '运营',
    service: '客服',
    warehouse: '仓储发货',
  };
  const roleText = computed(() => {
    const roles = getCurrentUser()?.roles || [];
    const names = roles.map((role) => ROLE_NAMES[role] || role);
    return [names.join('/') || '后台用户', shopInfo.value?.name]
      .filter(Boolean)
      .join(' · ');
  });
</script>

<style scoped lang="less">
  .workplace {
    // 时间维度选择器：水平居中 + 按原 small 尺寸放大一倍
    .report-toolbar {
      :deep(.period-selector .ant-radio-button-wrapper) {
        height: 40px;
        line-height: 40px;
        padding: 0 14px;
        font-size: 20px;
      }
    }

    // 笔记本：顶部信息栏允许换行；指标卡/报表按可用宽度自动分列
    @media (max-width: 1200px) {
      .hero {
        flex-wrap: wrap;
        gap: 16px;
      }
      .hero-stats {
        margin-left: auto;
      }
      .card-grid {
        grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
        gap: 16px;
      }
      .overview,
      .project-list {
        grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
        gap: 16px;
      }
      // 子项原本写的是 col-span-12 / xlx:col-span-*，重置后才能交给 auto-fit
      .card-grid > *,
      .overview > *,
      .project-list > * {
        grid-column: auto !important;
        min-width: 0;
      }
    }

    // 手机：顶部信息栏纵向排列，时间选择器缩回正常大小
    @media (max-width: 767px) {
      .hero {
        flex-direction: column;
        align-items: stretch;
        // 手机端留出 32px，避免兔耳顶出卡片背景
        padding-top: 32px;
      }
      .hero-stats {
        width: 100%;
        justify-content: space-between;
        margin-left: 0;
      }
      .hero-stats :deep(.ant-statistic) {
        flex: 1;
      }
      .card-grid {
        grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
        gap: 12px;
      }
      .overview,
      .project-list {
        grid-template-columns: 1fr;
        gap: 12px;
      }
      :deep(.period-selector .ant-radio-button-wrapper) {
        height: 32px;
        line-height: 32px;
        padding: 0 10px;
        font-size: 14px;
      }
    }
  }
</style>
