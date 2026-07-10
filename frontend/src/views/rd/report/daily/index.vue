<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="日期" prop="bizDate">
        <el-date-picker
          v-model="queryParams.bizDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="客户" prop="customerName">
        <el-input
          v-model="queryParams.customerName"
          placeholder="请输入客户"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="型号" prop="productModel">
        <el-input
          v-model="queryParams.productModel"
          placeholder="请输入型号"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="货位" prop="locationCode">
        <el-input
          v-model="queryParams.locationCode"
          placeholder="请输入货位"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="炉号" prop="furnaceNo">
        <el-input
          v-model="queryParams.furnaceNo"
          placeholder="请输入生产炉号"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="炉层" prop="furnaceLayer">
        <el-input
          v-model="queryParams.furnaceLayer"
          placeholder="请输入所在炉层"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="warning"
          plain
          :loading="rebuildLoading"
          @click="handleRebuild"
          v-hasPermi="['rd:daily-stock-summary:rebuild']"
        >
          <Icon icon="ep:refresh-right" class="mr-5px" /> 重算
        </el-button>
        <el-button
          type="success"
          plain
          :loading="exportLoading"
          @click="handleExport"
          v-hasPermi="['rd:daily-stock-summary:export']"
        >
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div class="report-heading">
      <span class="report-title">每日库存变化趋势</span>
      <div class="report-totals">
        <span>入库 <strong>{{ totals.inbound }}</strong></span>
        <span>出库 <strong>{{ totals.outbound }}</strong></span>
        <span>调入 <strong>{{ totals.transferIn }}</strong></span>
        <span>调出 <strong>{{ totals.transferOut }}</strong></span>
        <span>净变化 <strong :class="netChangeClass(totals.netChange)">{{ totals.netChange }}</strong></span>
      </div>
    </div>
    <el-skeleton :loading="trendLoading" animated>
      <Echart :height="300" :options="chartOptions" />
    </el-skeleton>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="日期" prop="bizDate" align="center" width="110" />
      <el-table-column label="货位" prop="locationCode" align="center" width="90" />
      <el-table-column label="客户" prop="customerName" min-width="140" />
      <el-table-column label="型号" prop="productModel" min-width="120" />
      <el-table-column label="生产炉号" prop="furnaceNo" min-width="120" />
      <el-table-column label="炉层" prop="furnaceLayer" align="center" width="90" />
      <el-table-column label="入库" prop="inboundQty" align="right" width="90" />
      <el-table-column label="出库" prop="outboundQty" align="right" width="90" />
      <el-table-column label="调入" prop="transferInQty" align="right" width="90" />
      <el-table-column label="调出" prop="transferOutQty" align="right" width="90" />
      <el-table-column label="退货" prop="returnQty" align="right" width="90" />
      <el-table-column label="净变化" prop="netChangeQty" align="right" width="100">
        <template #default="scope">
          <span :class="netChangeClass(scope.row.netChangeQty)">{{ scope.row.netChangeQty }}</span>
        </template>
      </el-table-column>
      <el-table-column label="库房数量" prop="warehouseQty" align="right" width="105" />
      <el-table-column label="库存重量" prop="stockWeight" align="right" width="105" />
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getPage"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import type { EChartsOption } from 'echarts'
import download from '@/utils/download'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import { RdDailyStockSummaryApi } from '@/api/rd/report/daily'
import type {
  RdDailyStockSummaryVO,
  RdDailyStockTrendVO
} from '@/api/rd/report/daily'

defineOptions({ name: 'RdDailyStockSummary' })

const message = useMessage()
const loading = ref(false)
const trendLoading = ref(false)
const rebuildLoading = ref(false)
const exportLoading = ref(false)
const list = ref<RdDailyStockSummaryVO[]>([])
const trendList = ref<RdDailyStockTrendVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const endDate = new Date()
const startDate = new Date()
startDate.setDate(endDate.getDate() - 29)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  bizDate: [formatDate(startDate, 'YYYY-MM-DD'), formatDate(endDate, 'YYYY-MM-DD')] as string[],
  locationCode: undefined as string | undefined,
  customerName: undefined as string | undefined,
  productModel: undefined as string | undefined,
  furnaceNo: undefined as string | undefined,
  furnaceLayer: undefined as string | undefined
})

const chartOptions = reactive<EChartsOption>({
  tooltip: { trigger: 'axis', confine: true },
  legend: { data: ['入库', '出库', '调入', '调出'], bottom: 0 },
  grid: { left: 48, right: 24, top: 24, bottom: 44, containLabel: true },
  xAxis: { type: 'category', boundaryGap: false, data: [] },
  yAxis: { type: 'value', name: '数量' },
  series: [
    { name: '入库', type: 'line', smooth: true, data: [], itemStyle: { color: '#16a34a' } },
    { name: '出库', type: 'line', smooth: true, data: [], itemStyle: { color: '#dc2626' } },
    { name: '调入', type: 'line', smooth: true, data: [], itemStyle: { color: '#2563eb' } },
    { name: '调出', type: 'line', smooth: true, data: [], itemStyle: { color: '#d97706' } }
  ]
}) as EChartsOption

const totals = computed(() => {
  return trendList.value.reduce(
    (result, item) => ({
      inbound: result.inbound + Number(item.inboundQty || 0),
      outbound: result.outbound + Number(item.outboundQty || 0),
      transferIn: result.transferIn + Number(item.transferInQty || 0),
      transferOut: result.transferOut + Number(item.transferOutQty || 0),
      netChange: result.netChange + Number(item.netChangeQty || 0)
    }),
    { inbound: 0, outbound: 0, transferIn: 0, transferOut: 0, netChange: 0 }
  )
})

const getPage = async () => {
  loading.value = true
  try {
    const data = await RdDailyStockSummaryApi.getPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const getTrend = async () => {
  trendLoading.value = true
  try {
    trendList.value = await RdDailyStockSummaryApi.getTrend(queryParams)
    ;(chartOptions.xAxis as any).data = trendList.value.map((item) => item.bizDate)
    const series = chartOptions.series as any[]
    series[0].data = trendList.value.map((item) => Number(item.inboundQty || 0))
    series[1].data = trendList.value.map((item) => Number(item.outboundQty || 0))
    series[2].data = trendList.value.map((item) => Number(item.transferInQty || 0))
    series[3].data = trendList.value.map((item) => Number(item.transferOutQty || 0))
  } finally {
    trendLoading.value = false
  }
}

const loadData = async () => {
  await Promise.all([getPage(), getTrend()])
}

const handleQuery = () => {
  queryParams.pageNo = 1
  loadData()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const handleRebuild = async () => {
  if (!queryParams.bizDate || queryParams.bizDate.length !== 2) {
    message.warning('请先选择需要重算的日期范围')
    return
  }
  await message.confirm(`确认重算 ${queryParams.bizDate[0]} 至 ${queryParams.bizDate[1]} 的每日统计吗？`)
  rebuildLoading.value = true
  try {
    const count = await RdDailyStockSummaryApi.rebuild(queryParams.bizDate[0], queryParams.bizDate[1])
    message.success(`每日统计重算完成，共更新 ${count} 条`)
    await loadData()
  } finally {
    rebuildLoading.value = false
  }
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await RdDailyStockSummaryApi.exportExcel(queryParams)
    download.excel(data, '每日出入库统计.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

const netChangeClass = (value: number) => {
  const quantity = Number(value)
  if (quantity > 0) return 'quantity-positive'
  if (quantity < 0) return 'quantity-negative'
  return ''
}

onMounted(loadData)
</script>

<style scoped>
.report-heading {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 8px;
}

.report-title {
  font-size: 16px;
  font-weight: 600;
}

.report-totals {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px 20px;
  color: var(--el-text-color-secondary);
}

.report-totals strong {
  margin-left: 4px;
  color: var(--el-text-color-primary);
}

.quantity-positive {
  color: var(--el-color-success);
  font-weight: 600;
}

.quantity-negative {
  color: var(--el-color-danger);
  font-weight: 600;
}

@media (max-width: 767px) {
  .report-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .report-totals {
    justify-content: flex-start;
  }
}
</style>
