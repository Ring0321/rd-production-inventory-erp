<template>
  <div v-loading="loading" class="dashboard-page">
    <ContentWrap>
      <div class="dashboard-header">
        <div>
          <h1>{{ productBrand.shortName }} · 生产仓储看板</h1>
          <p>{{ displayAsOfDate }}</p>
        </div>
        <div class="header-actions">
          <el-date-picker
            v-model="asOfDate"
            type="date"
            value-format="YYYY-MM-DD"
            :clearable="false"
            class="!w-150px"
            @change="loadOverview"
          />
          <el-tooltip content="刷新" placement="top">
            <el-button circle @click="loadOverview"><Icon icon="ep:refresh" /></el-button>
          </el-tooltip>
        </div>
      </div>

      <div class="metric-grid">
        <div v-for="metric in headlineMetrics" :key="metric.label" class="metric-item">
          <div class="metric-icon" :class="metric.tone">
            <Icon :icon="metric.icon" :size="19" />
          </div>
          <div class="metric-copy">
            <span>{{ metric.label }}</span>
            <strong>{{ formatNumber(metric.value) }}</strong>
            <small>{{ metric.unit }}</small>
          </div>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap>
      <div class="section-heading">
        <h2>当日出入库</h2>
        <span
          >净变化
          <strong :class="quantityClass(todayMovement.netChangeQty)">{{
            formatNumber(todayMovement.netChangeQty)
          }}</strong></span
        >
      </div>
      <div class="movement-grid">
        <div v-for="item in movementMetrics" :key="item.label" class="movement-item">
          <span>{{ item.label }}</span>
          <strong :class="item.className">{{ formatNumber(item.value) }}</strong>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap>
      <div class="chart-grid">
        <section class="chart-panel chart-wide">
          <div class="panel-heading">
            <h2>近 30 天库存流动</h2>
          </div>
          <Echart :height="300" :options="trendOptions" />
        </section>
        <section class="chart-panel">
          <div class="panel-heading">
            <h2>订单状态</h2>
          </div>
          <Echart :height="300" :options="orderStatusOptions" />
        </section>
        <section class="chart-panel">
          <div class="panel-heading">
            <h2>客户库存前十</h2>
          </div>
          <Echart :height="330" :options="customerOptions" />
        </section>
        <section class="chart-panel">
          <div class="panel-heading">
            <h2>型号库存前十</h2>
          </div>
          <Echart :height="330" :options="modelOptions" />
        </section>
      </div>
    </ContentWrap>

    <ContentWrap>
      <div class="section-heading">
        <h2>待完成销售订单</h2>
        <span>优先显示最早订单</span>
      </div>
      <el-table :data="overview?.openOrders || []" :stripe="true" :show-overflow-tooltip="true">
        <el-table-column label="订单号" prop="orderNo" min-width="150" fixed />
        <el-table-column label="订单日期" prop="orderDate" width="110" align="center">
          <template #default="scope">{{ normalizeDate(scope.row.orderDate) }}</template>
        </el-table-column>
        <el-table-column label="客户" prop="customerName" min-width="150" />
        <el-table-column label="型号" prop="productModel" min-width="120" />
        <el-table-column label="应入数量" prop="requiredQty" width="100" align="right" />
        <el-table-column label="实际产额" prop="actualOutputQty" width="100" align="right" />
        <el-table-column label="当前差额" prop="adjustedDifferenceQty" width="100" align="right">
          <template #default="scope">
            <span :class="quantityClass(-Number(scope.row.adjustedDifferenceQty || 0))">
              {{ formatNumber(scope.row.adjustedDifferenceQty) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="已完成" prop="fulfilledQty" width="100" align="right" />
        <el-table-column label="完成进度" width="170">
          <template #default="scope">
            <el-progress
              :percentage="orderProgress(scope.row.fulfilledQty, scope.row.requiredQty)"
              :stroke-width="10"
            />
          </template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'PARTIAL' ? 'warning' : 'info'">
              {{ orderStatusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </ContentWrap>
  </div>
</template>

<script setup lang="ts">
import type { EChartsOption } from 'echarts'
import { productBrand } from '@/config/brand'
import { formatDate } from '@/utils/formatTime'
import { RdInventoryDashboardApi } from '@/api/rd/dashboard'
import type { RdInventoryDashboardVO } from '@/api/rd/dashboard'

defineOptions({ name: 'RdInventoryDashboard' })

const loading = ref(false)
const asOfDate = ref(formatDate(new Date(), 'YYYY-MM-DD'))
const overview = ref<RdInventoryDashboardVO>()
const displayAsOfDate = computed(() => normalizeDate(overview.value?.asOfDate) || asOfDate.value)
const emptyMovement = {
  productionQty: 0,
  inboundQty: 0,
  outboundQty: 0,
  transferInQty: 0,
  transferOutQty: 0,
  returnQty: 0,
  netChangeQty: 0
}
const todayMovement = computed(() => overview.value?.todayMovement || emptyMovement)

const headlineMetrics = computed(() => [
  {
    label: '库房数量',
    value: overview.value?.stock?.warehouseQty || 0,
    unit: '包',
    icon: 'ep:box',
    tone: 'tone-green'
  },
  {
    label: '可用数量',
    value: overview.value?.stock?.availableQty || 0,
    unit: '包',
    icon: 'ep:circle-check',
    tone: 'tone-blue'
  },
  {
    label: '订单锁定',
    value: overview.value?.stock?.lockedQty || 0,
    unit: '包',
    icon: 'ep:lock',
    tone: 'tone-amber'
  },
  {
    label: '库存重量',
    value: overview.value?.stock?.stockWeight || 0,
    unit: 'kg',
    icon: 'ep:scale-to-original',
    tone: 'tone-teal'
  },
  {
    label: '待完成订单',
    value: overview.value?.order?.openOrderCount || 0,
    unit: '单',
    icon: 'ep:tickets',
    tone: 'tone-violet'
  },
  {
    label: '订单剩余差额',
    value: overview.value?.order?.remainingQty || 0,
    unit: '包',
    icon: 'ep:warning',
    tone: 'tone-red'
  }
])

const movementMetrics = computed(() => [
  { label: '生产产额', value: todayMovement.value.productionQty, className: 'quantity-blue' },
  { label: '入库', value: todayMovement.value.inboundQty, className: 'quantity-positive' },
  { label: '出库', value: todayMovement.value.outboundQty, className: 'quantity-negative' },
  { label: '调入', value: todayMovement.value.transferInQty, className: 'quantity-blue' },
  { label: '调出', value: todayMovement.value.transferOutQty, className: 'quantity-amber' },
  { label: '退货', value: todayMovement.value.returnQty, className: 'quantity-violet' }
])

const trendOptions = reactive({
  tooltip: { trigger: 'axis', confine: true },
  legend: { data: ['入库', '出库', '调入', '调出'], bottom: 0 },
  grid: { left: 28, right: 20, top: 22, bottom: 46, containLabel: true },
  xAxis: { type: 'category', boundaryGap: false, data: [] },
  yAxis: { type: 'value' },
  series: [
    {
      name: '入库',
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: [],
      itemStyle: { color: '#16865f' }
    },
    {
      name: '出库',
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: [],
      itemStyle: { color: '#d14343' }
    },
    {
      name: '调入',
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: [],
      itemStyle: { color: '#2878bd' }
    },
    {
      name: '调出',
      type: 'line',
      smooth: true,
      showSymbol: false,
      data: [],
      itemStyle: { color: '#c58119' }
    }
  ]
}) as EChartsOption

const orderStatusOptions = reactive({
  tooltip: { trigger: 'item' },
  legend: { bottom: 0 },
  series: [
    {
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      label: { formatter: '{b}\n{c}' },
      data: [],
      color: ['#c58119', '#2878bd', '#16865f', '#6b7280', '#d14343']
    }
  ]
}) as EChartsOption

const customerOptions = reactive(rankingChartOptions('#16865f')) as EChartsOption
const modelOptions = reactive(rankingChartOptions('#2878bd')) as EChartsOption

function rankingChartOptions(color: string): EChartsOption {
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, confine: true },
    grid: { left: 16, right: 24, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value' },
    yAxis: { type: 'category', data: [], axisLabel: { width: 110, overflow: 'truncate' } },
    series: [{ type: 'bar', data: [], barMaxWidth: 18, itemStyle: { color, borderRadius: 2 } }]
  }
}

const loadOverview = async () => {
  loading.value = true
  try {
    overview.value = await RdInventoryDashboardApi.getOverview(asOfDate.value)
    updateCharts()
  } finally {
    loading.value = false
  }
}

const updateCharts = () => {
  const data = overview.value
  if (!data) return
  ;(trendOptions.xAxis as any).data = data.trend.map((item) => normalizeDate(item.bizDate).slice(5))
  const trendSeries = trendOptions.series as any[]
  trendSeries[0].data = data.trend.map((item) => Number(item.inboundQty || 0))
  trendSeries[1].data = data.trend.map((item) => Number(item.outboundQty || 0))
  trendSeries[2].data = data.trend.map((item) => Number(item.transferInQty || 0))
  trendSeries[3].data = data.trend.map((item) => Number(item.transferOutQty || 0))
  ;(orderStatusOptions.series as any[])[0].data = data.orderStatuses.map((item) => ({
    name: orderStatusLabel(item.status),
    value: item.orderCount
  }))
  updateRankingChart(customerOptions, data.customerRanking)
  updateRankingChart(modelOptions, data.modelRanking)
}

const updateRankingChart = (
  options: EChartsOption,
  data: RdInventoryDashboardVO['customerRanking']
) => {
  const reversed = [...data].reverse()
  ;(options.yAxis as any).data = reversed.map((item) => item.name)
  ;(options.series as any[])[0].data = reversed.map((item) => Number(item.warehouseQty || 0))
}

const formatNumber = (value?: number) =>
  new Intl.NumberFormat('zh-CN', {
    maximumFractionDigits: 3
  }).format(Number(value || 0))

const normalizeDate = (value?: string | number[]) => {
  if (!value) return ''
  if (typeof value === 'string') return value
  return value
    .slice(0, 3)
    .map((part, index) => (index === 0 ? String(part) : String(part).padStart(2, '0')))
    .join('-')
}

const orderProgress = (fulfilled?: number, required?: number) => {
  const total = Number(required || 0)
  if (total <= 0) return 0
  return Math.min(100, Math.round((Number(fulfilled || 0) / total) * 100))
}

const orderStatusLabel = (status: string) =>
  ({
    OPEN: '待处理',
    PARTIAL: '部分完成',
    FULFILLED: '已满足',
    CLOSED: '已关闭',
    CANCELLED: '已取消'
  })[status] || status

const quantityClass = (value?: number) => {
  const number = Number(value || 0)
  if (number > 0) return 'quantity-positive'
  if (number < 0) return 'quantity-negative'
  return ''
}

onMounted(loadOverview)
</script>

<style scoped>
.dashboard-page {
  min-height: 520px;
}

.dashboard-header,
.section-heading,
.panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.dashboard-header h1,
.section-heading h2,
.panel-heading h2 {
  margin: 0;
  letter-spacing: 0;
  color: var(--el-text-color-primary);
}

.dashboard-header h1 {
  font-size: 22px;
  line-height: 30px;
}

.dashboard-header p {
  margin: 3px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.metric-grid {
  display: grid;
  margin-top: 20px;
  gap: 16px;
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.metric-item {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 14px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
}

.metric-icon {
  display: flex;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
}

.tone-green {
  color: #13795b;
  background: #e8f5ef;
}

.tone-blue {
  color: #246fa8;
  background: #eaf2f8;
}

.tone-amber {
  color: #a96812;
  background: #fbf1df;
}

.tone-teal {
  color: #147d83;
  background: #e6f5f5;
}

.tone-violet {
  color: #7156a5;
  background: #f0ecf8;
}

.tone-red {
  color: #bd3f3f;
  background: #faeaea;
}

.metric-copy {
  min-width: 0;
}

.metric-copy span,
.metric-copy small {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.metric-copy strong {
  display: inline-block;
  max-width: 100%;
  margin: 2px 4px 0 0;
  overflow: hidden;
  font-size: 20px;
  line-height: 26px;
  color: var(--el-text-color-primary);
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.section-heading {
  min-height: 30px;
  margin-bottom: 12px;
}

.section-heading h2,
.panel-heading h2 {
  font-size: 16px;
  line-height: 24px;
}

.section-heading span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.movement-grid {
  display: grid;
  border-top: 1px solid var(--el-border-color-lighter);
  border-left: 1px solid var(--el-border-color-lighter);
  grid-template-columns: repeat(6, minmax(0, 1fr));
}

.movement-item {
  min-width: 0;
  padding: 14px 18px;
  border-right: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.movement-item span {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.movement-item strong {
  font-size: 18px;
}

.chart-grid {
  display: grid;
  gap: 28px 32px;
  grid-template-columns: 1fr 1fr;
}

.chart-panel {
  min-width: 0;
}

.chart-wide {
  grid-column: span 1;
}

.panel-heading {
  min-height: 28px;
  margin-bottom: 4px;
}

.quantity-positive {
  font-weight: 600;
  color: var(--el-color-success);
}

.quantity-negative {
  font-weight: 600;
  color: var(--el-color-danger);
}

.quantity-blue {
  color: #2878bd;
}

.quantity-amber {
  color: #c58119;
}

.quantity-violet {
  color: #7156a5;
}

@media (width <= 1280px) {
  .metric-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (width <= 900px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }

  .movement-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (width <= 640px) {
  .dashboard-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .metric-grid,
  .movement-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .header-actions,
  .header-actions :deep(.el-date-editor) {
    width: 100% !important;
  }
}
</style>
