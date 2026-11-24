/**
 * 工作台数据聚合：父组件一次拉取，通过 props 分发给子组件，避免订单数据被重复请求 4 次。
 * 口径常量集中在这里，以后改口径只改这一处。
 */
import { computed, ref, watch } from 'vue';
import dayjs from 'dayjs';
import { message } from 'ant-design-vue';
import { getAllOrders } from '@/api/orders.js';
import { getAfterSales } from '@/api/afterSale.js';
import { getDashboard } from '@/api/products.js';
import { getUserStatistics } from '@/api/user.js';
import {
  getDashboardStats,
  getHeadline,
  getSalesRanking,
  getTurnover,
  getTurnoverReport,
} from '@/api/report.js';
import { shop } from '@/utils/shop.js';
import { orderNotifyTick } from '@/utils/orderNotify.js';
import { formatThousand } from '@/utils/formatter.js';

// ==================== 口径常量 ====================
// 计入「销售额」的状态（OrderStatusEnum 数字 code）：待付款(1)还没付钱、已取消(6)不产生成交，都排除
const PAID_STATUS = [3, 4, 5]; // 待发货/已发货/已完成
// 计入「订单量」时排除的状态（数字 code）
const COUNTED_EXCLUDE = [6]; // 已取消
// 待商家处理的售后状态
const AFTER_SALE_TODO = 'reviewing';
const SALES_RANKING_LIMIT = 8; // 销量排名取前几名

// 报表时间维度：营业额卡片/折线、销量排名共用
export const PERIODS = [
  { value: 'yesterday', label: '昨日' },
  { value: 'week', label: '近7天' },
  { value: 'month', label: '本月' },
];

// ==================== 纯函数 ====================
/** created_at 正常是 'YYYY-MM-DD HH:mm:ss'，兜底兼容 Unix 秒 */
const toDate = (v) => {
  if (!v) return null;
  return typeof v === 'number' ? dayjs(v * 1000) : dayjs(String(v));
};

export const isPaidOrder = (o) => PAID_STATUS.includes(o.orderStatus);
export const isCountedOrder = (o) => !COUNTED_EXCLUDE.includes(o.orderStatus);

/**
 * 环比涨跌幅。上期为 0 时无法计算（除零得 Infinity），返回 null → 调用处不显示涨跌幅。
 * 返回字段名与 OverviewTitle / MiniStatisticCard 的 props 对齐，可以整对象传
 */
export function growth(current, previous) {
  if (!previous) return null;
  const pct = Math.round(((current - previous) / previous) * 100);
  return { change: `${pct >= 0 ? '+' : ''}${pct}%`, up: pct >= 0, down: pct < 0 };
}
// 拿不到环比时的占位，模板里直接展开就行
const NO_GROWTH = Object.freeze({ change: '', up: false, down: false });

// ==================== 主 composable ====================
export function useWorkplaceData() {
  // ---------------- 原始状态 ----------------
  const orders = ref([]); // 本店全部订单（非 admin 时是本人订单，后端已过滤 is_deleted）
  const productStat = ref({ totalProducts: 0, totalStock: 0, totalValue: 0 });
  const afterSaleTodo = ref(0);
  // 用户增长图表用：buckets/newUsers/cumulativeUsers 都是后端按时间桶返回的原始数据
  const userGrowth = ref({
    bucketType: '',
    buckets: [],
    newUsers: [],
    cumulativeUsers: [],
    totalUsers: 0,
  });
  // 用户统计接口是否加载失败：接口 500 时图表没有数据，
  // 不区分就会显示成「暂无数据」，看起来像「这段时间真没注册」，实际是接口挂了
  const userGrowthError = ref(false);
  // 营业额（后端口径：pay_at 实收 − 退款），维度 yesterday / week / month
  const turnover = ref({ timeDesc: '', turnover: 0 });
  const turnoverReport = ref({ dateList: [], turnoverList: [] });
  // 全局报表时间维度：营业额、订单统计、用户增长、销量排名共用
  const reportPeriod = ref('week');
  // 销量排名：[{ productId, productName, image, salesQuantity }]
  const salesRanking = ref([]);
  // 工作台四张统计卡（order-service /api/admin/report/dashboard）
  const dashboardStats = ref({
    todayTurnover: 0,
    yesterdayTurnover: 0,
    todayOrders: 0,
    yesterdayOrders: 0,
    pendingShip: 0,
  });
  // 顶部累计销售额与订单量（后端聚合，不受订单分页影响）
  const headlineStats = ref({
    totalPaid: 0,
    totalRefund: 0,
    netAmount: 0,
    totalOrders: 0,
  });

  // ---------------- 报表时间窗口 ----------------
  // dayOffsetKey(0)=今天
  const dayOffsetKey = (daysAgo) =>
    dayjs().subtract(daysAgo, 'day').format('YYYY-MM-DD');

  // 订单统计的时间桶：昨日按小时（00:00~23:00），近7天/本月按天
  const orderBuckets = computed(() => {
    if (reportPeriod.value === 'yesterday') {
      return Array.from({ length: 24 }, (_, h) => {
        const key = String(h).padStart(2, '0');
        return { key, label: `${key}:00` };
      });
    }
    // 近7天=含今天往前 7 天；本月=月初到今天
    const days = reportPeriod.value === 'week' ? 7 : dayjs().date();
    return Array.from({ length: days }, (_, i) => {
      const d = dayjs().subtract(days - 1 - i, 'day');
      return { key: d.format('YYYY-MM-DD'), label: d.format('MM-DD') };
    });
  });

  // ---------------- 顶部三个 a-statistic ----------------
  const headline = computed(() => ({
    products: Number(productStat.value.totalProducts) || 0,
    // 累计订单量与销售额都走后端聚合，不再依赖前端拉取的订单分页
    orders: Number(headlineStats.value.totalOrders) || 0,
    amount: Number(headlineStats.value.netAmount) || 0,
  }));

  // ---------------- 订单统计折线 ----------------
  // 只统计订单量：待付款（1）、已付款（2/3/4/5）、已取消（6），不算营业额
  const orderStatsSeries = computed(() => {
    const buckets = orderBuckets.value;
    const index = new Map(buckets.map((b, i) => [b.key, i]));
    const hourly = reportPeriod.value === 'yesterday';
    const pending = buckets.map(() => 0);
    const paid = buckets.map(() => 0);
    const canceled = buckets.map(() => 0);
    orders.value.forEach((o) => {
      const d = toDate(o.createdAt);
      if (!d || !d.isValid()) return;
      const i = index.get(d.format(hourly ? 'HH' : 'YYYY-MM-DD'));
      if (i === undefined) return;
      if (o.orderStatus === 1) pending[i]++;
      else if (isPaidOrder(o)) paid[i]++;
      else if (o.orderStatus === 6) canceled[i]++;
    });
    return [
      { name: '待付款订单', data: buckets.map((b, i) => [b.label, pending[i]]) },
      { name: '已付款订单', data: buckets.map((b, i) => [b.label, paid[i]]) },
      { name: '取消订单', data: buckets.map((b, i) => [b.label, canceled[i]]) },
    ];
  });

  // 订单量环比：当前时间段 vs 上一等长/上一自然月（排除已取消）
  const orderPeriodGrowth = computed(() => {
    const today = dayjs().startOf('day');
    let curStart;
    let curEnd;
    let prevStart;
    let prevEnd;
    if (reportPeriod.value === 'yesterday') {
      curStart = today.subtract(1, 'day');
      curEnd = today;
      prevStart = today.subtract(2, 'day');
      prevEnd = curStart;
    } else if (reportPeriod.value === 'week') {
      curStart = today.subtract(6, 'day');
      curEnd = today.add(1, 'day');
      prevStart = today.subtract(13, 'day');
      prevEnd = curStart;
    } else {
      // 本月对比上月：按本月已过天数对齐，上月天数不足则截到月初
      curStart = today.startOf('month');
      curEnd = today.add(1, 'day');
      prevStart = curStart.subtract(1, 'month');
      const curDays = today.diff(curStart, 'day') + 1;
      prevEnd = prevStart.add(curDays, 'day');
      if (prevEnd.isAfter(curStart)) prevEnd = curStart;
    }
    const count = (start, end) =>
      orders.value.filter((o) => {
        if (!isCountedOrder(o)) return false;
        const d = toDate(o.createdAt);
        return (
          d &&
          d.isValid() &&
          d.valueOf() >= start.valueOf() &&
          d.valueOf() < end.valueOf()
        );
      }).length;
    return growth(count(curStart, curEnd), count(prevStart, prevEnd)) ?? NO_GROWTH;
  });

  // ---------------- 今日待办 ----------------
  const todo = computed(() => ({
    pendingShip: orders.value.filter(
      (o) => o.orderStatus === 3
    ).length,
    afterSale: afterSaleTodo.value,
  }));

  // ---------------- 四张统计卡（数据全部来自后端）----------------
  const cards = computed(() => {
    const d = dashboardStats.value;
    return [
      {
        title: '今日销售额',
        value: `￥${formatThousand(d.todayTurnover, 2)}`,
        ...(growth(d.todayTurnover, d.yesterdayTurnover) || {}),
        icon: 'dollar-circle-filled',
        iconClass: 'text-blue-100',
      },
      {
        title: '今日订单量',
        value: formatThousand(d.todayOrders, 0),
        ...(growth(d.todayOrders, d.yesterdayOrders) || {}),
        icon: 'shopping-filled',
        iconClass: 'text-green-100',
      },
      {
        title: '待发货订单',
        value: formatThousand(d.pendingShip, 0),
        icon: 'carry-out-filled',
        iconClass: 'text-purple-100',
      },
      {
        title: '用户总量',
        value: formatThousand(userGrowth.value.totalUsers, 0),
        icon: 'team-outlined',
        iconClass: 'text-primary-100',
      },
    ];
  });

  // ---------------- 取数 ----------------
  /** 销量排名：时间维度与营业额共用，切换周期时一起刷新 */
  async function loadSalesRanking(period = reportPeriod.value) {
    try {
      const res = await getSalesRanking(period, SALES_RANKING_LIMIT);
      salesRanking.value = res.data.data || [];
    } catch (err) {
      console.error('销量排名加载失败', err);
    }
  }

  /** 用户增长：按当前时间维度向后端要时间桶数据 */
  async function loadUserStatistics(period = reportPeriod.value) {
    try {
      const res = await getUserStatistics(period);
      const data = res.data.data || {};
      userGrowth.value = {
        bucketType: data.bucketType || 'day',
        buckets: data.buckets || [],
        newUsers: data.newUsers || [],
        // 用户总量曲线（存量累计）由后端给出；老接口没返回时留空数组，组件里会用 totalUsers 反推兜底
        cumulativeUsers: data.cumulativeUsers || [],
        totalUsers: Number(data.totalUsers) || 0,
      };
      userGrowthError.value = false;
    } catch (err) {
      console.error('用户统计加载失败', err);
      // 保留上一次的数据，图表不至于塌成空白；同时标记错误，由组件显示「加载失败」
      userGrowthError.value = true;
    }
  }

  /** 营业额：卡片 + 图表。昨日用「前日 vs 昨日」双柱对比，复用近7天报表 */
  async function loadTurnover(period = reportPeriod.value) {
    // 销量排名跟随同一时间维度，并行请求
    loadSalesRanking(period);
    try {
      const res = await getTurnover(period);
      turnover.value = res.data.data || { timeDesc: '', turnover: 0 };
      if (period === 'yesterday') {
        // 近7天报表含今天；前日/昨日按日期取，不依赖数组位置
        const reportRes = await getTurnoverReport('week');
        const data = reportRes.data.data || { dateList: [], turnoverList: [] };
        const beforeKey = dayOffsetKey(2);
        const yesterdayKey = dayOffsetKey(1);
        const pick = (key) => {
          const i = (data.dateList || []).indexOf(key);
          return i >= 0 ? Number(data.turnoverList[i]) || 0 : 0;
        };
        turnoverReport.value = {
          dateList: [beforeKey, yesterdayKey],
          turnoverList: [pick(beforeKey), pick(yesterdayKey)],
        };
        return;
      }
      const reportRes = await getTurnoverReport(period);
      turnoverReport.value =
        reportRes.data.data || { dateList: [], turnoverList: [] };
    } catch (err) {
      console.error('营业额数据加载失败', err);
    }
  }

  /** 切换全局报表时间维度：营业额/销量排名、用户增长一起刷新，订单统计算算即可 */
  function setReportPeriod(period) {
    if (period === reportPeriod.value) return;
    reportPeriod.value = period;
    loadTurnover(period);
    loadUserStatistics(period);
  }

  /** 只刷新统计卡（来单事件触发，避免全量拉订单） */
  async function refreshDashboardStats() {
    try {
      const res = await getDashboardStats();
      dashboardStats.value = res.data.data || dashboardStats.value;
    } catch (err) {
      console.error('工作台统计加载失败', err);
    }
  }

  /** 来单 WebSocket 推送后自动刷新统计卡 + 当前营业额，无需手动刷新页面 */
  watch(orderNotifyTick, () => {
    refreshDashboardStats();
    loadTurnover(reportPeriod.value);
  });

  /** 业务数据：每次进入工作台都刷新 */
  function loadAll() {
    return Promise.all([
      // 工作台要全量订单做时间段统计，后端是分页接口，pageSize 传大值一次拉全
      getAllOrders({ pageSize: 1000 })
        .then((res) => {
          // 后端返回分页结构 { list, total, page, pageSize }，取 list 数组
          orders.value = res.data.data?.list || [];
        })
        .catch((err) => {
          // 401/403/404/500 已由 request.js 拦截器统一提示，这里只兜网络错误
          if (!err.response) message.error('网络连接超时，请检查网络后再试');
          console.error('订单数据加载失败', err);
        }),
      getDashboard()
        .then((res) => {
          productStat.value = res.data.data || productStat.value;
        })
        .catch((err) => console.error('商品统计加载失败', err)),
      getAfterSales({ status: AFTER_SALE_TODO })
        .then((res) => {
          afterSaleTodo.value = (res.data.data || []).length;
        })
        .catch((err) => console.error('售后数据加载失败', err)),
      loadUserStatistics(reportPeriod.value),
      refreshDashboardStats(),
      loadTurnover(reportPeriod.value),
      getHeadline()
        .then((res) => {
          headlineStats.value = res.data.data || headlineStats.value;
        })
        .catch((err) => console.error('累计销售额加载失败', err)),
    ]);
  }

  return {
    shopInfo: computed(() => shop.value),
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
    dashboardStats,
    loadAll,
    loadTurnover,
    setReportPeriod,
  };
}
