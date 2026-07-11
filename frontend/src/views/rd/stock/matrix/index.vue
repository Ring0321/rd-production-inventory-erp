<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="日期" prop="dateRange">
        <el-date-picker
          v-model="queryParams.dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          :disabled-date="disableFutureDate"
          class="!w-240px"
        />
      </el-form-item>
      <RdCustomerProductFields
        v-model:customer-name="queryParams.customerName"
        v-model:product-model="queryParams.productModel"
        query-mode
      />
      <el-form-item label="货位" prop="locationCode">
        <el-input
          v-model="queryParams.locationCode"
          clearable
          placeholder="请输入货位"
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="炉号" prop="furnaceNo">
        <el-input
          v-model="queryParams.furnaceNo"
          clearable
          placeholder="请输入生产炉号"
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" /> 查询
        </el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <div class="matrix-toolbar">
      <div>
        <h2>库存动态明细</h2>
        <span>{{ queryParams.dateRange[0] }} 至 {{ queryParams.dateRange[1] }}</span>
      </div>
      <div class="matrix-totals">
        <span>库房数量 <strong>{{ formatQty(result.totals.warehouseQty, true) }}</strong></span>
        <span>库存重量 <strong>{{ formatQty(result.totals.stockWeight, true) }}</strong></span>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="result.list"
      border
      stripe
      height="620"
      show-summary
      :summary-method="getSummaries"
      :show-overflow-tooltip="true"
      class="stock-matrix-table"
    >
      <el-table-column label="货位" prop="locationCode" fixed="left" width="82" />
      <el-table-column label="客户" prop="customerName" fixed="left" width="150" />
      <el-table-column label="型号" prop="productModel" fixed="left" width="135" />
      <el-table-column label="生产炉号" prop="furnaceNo" fixed="left" width="120" />
      <el-table-column label="所在炉层" prop="furnaceLayer" fixed="left" width="100" />
      <el-table-column label="期初" prop="openingQty" align="right" width="88">
        <template #default="scope"><span :class="movementClass(scope.row.openingQty)">{{ formatQty(scope.row.openingQty) }}</span></template>
      </el-table-column>
      <el-table-column label="调拨" prop="transferQty" align="right" width="88">
        <template #default="scope"><span :class="movementClass(scope.row.transferQty)">{{ formatQty(scope.row.transferQty) }}</span></template>
      </el-table-column>
      <el-table-column label="入库" prop="inboundQty" align="right" width="88">
        <template #default="scope"><span :class="movementClass(scope.row.inboundQty)">{{ formatQty(scope.row.inboundQty) }}</span></template>
      </el-table-column>
      <el-table-column label="出库" prop="outboundQty" align="right" width="88">
        <template #default="scope"><span :class="movementClass(scope.row.outboundQty)">{{ formatQty(scope.row.outboundQty) }}</span></template>
      </el-table-column>
      <el-table-column label="退货" prop="returnQty" align="right" width="88">
        <template #default="scope"><span :class="movementClass(scope.row.returnQty)">{{ formatQty(scope.row.returnQty) }}</span></template>
      </el-table-column>
      <el-table-column label="库房数量" prop="warehouseQty" align="right" width="105">
        <template #default="scope"><strong>{{ formatQty(scope.row.warehouseQty, true) }}</strong></template>
      </el-table-column>
      <el-table-column label="单包重量" prop="packageWeight" align="right" width="105" />
      <el-table-column label="库存重量" prop="stockWeight" align="right" width="115">
        <template #default="scope"><strong>{{ formatQty(scope.row.stockWeight, true) }}</strong></template>
      </el-table-column>

      <el-table-column v-for="date in result.dates" :key="date" :label="formatDateLabel(date)" align="center">
        <el-table-column :prop="dailyProp(date, 'inboundQty')" label="入库" align="right" width="74">
          <template #default="scope"><span :class="movementClass(dailyValue(scope.row, date, 'inboundQty'))">{{ formatQty(dailyValue(scope.row, date, 'inboundQty')) }}</span></template>
        </el-table-column>
        <el-table-column :prop="dailyProp(date, 'outboundQty')" label="出库" align="right" width="74">
          <template #default="scope"><span :class="movementClass(dailyValue(scope.row, date, 'outboundQty'))">{{ formatQty(dailyValue(scope.row, date, 'outboundQty')) }}</span></template>
        </el-table-column>
        <el-table-column :prop="dailyProp(date, 'transferQty')" label="调拨" align="right" width="74">
          <template #default="scope"><span :class="movementClass(dailyValue(scope.row, date, 'transferQty'))">{{ formatQty(dailyValue(scope.row, date, 'transferQty')) }}</span></template>
        </el-table-column>
        <el-table-column :prop="dailyProp(date, 'returnQty')" label="退货" align="right" width="74">
          <template #default="scope"><span :class="movementClass(dailyValue(scope.row, date, 'returnQty'))">{{ formatQty(dailyValue(scope.row, date, 'returnQty')) }}</span></template>
        </el-table-column>
      </el-table-column>
    </el-table>

    <Pagination
      :total="result.total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import RdCustomerProductFields from '@/components/RdCustomerProductFields/index.vue'
import {
  RdStockMatrixApi,
  type RdStockMatrixDailyVO,
  type RdStockMatrixPageVO,
  type RdStockMatrixRowVO
} from '@/api/rd/stock/matrix'

defineOptions({ name: 'RdStockMatrix' })

type MovementField = 'inboundQty' | 'outboundQty' | 'transferQty' | 'returnQty'

const message = useMessage()
const loading = ref(false)
const queryFormRef = ref()
const today = new Date()
const monthStart = new Date(today.getFullYear(), today.getMonth(), 1)
const initialRange = [formatDate(monthStart, 'YYYY-MM-DD'), formatDate(today, 'YYYY-MM-DD')]

const queryParams = reactive({
  pageNo: 1,
  pageSize: 20,
  dateRange: [...initialRange] as string[],
  customerName: undefined as string | undefined,
  productModel: undefined as string | undefined,
  locationCode: undefined as string | undefined,
  furnaceNo: undefined as string | undefined
})

const emptyRow = (): RdStockMatrixRowVO => ({
  openingQty: 0,
  transferQty: 0,
  inboundQty: 0,
  outboundQty: 0,
  returnQty: 0,
  warehouseQty: 0,
  packageWeight: 0,
  stockWeight: 0,
  dailyMovements: []
})

const result = reactive<RdStockMatrixPageVO>({ dates: [], list: [], total: 0, totals: emptyRow() })

const getList = async () => {
  if (!queryParams.dateRange || queryParams.dateRange.length !== 2) {
    message.warning('请选择日期范围')
    return
  }
  const days = Math.floor(
    (new Date(queryParams.dateRange[1]).getTime() - new Date(queryParams.dateRange[0]).getTime()) / 86400000
  )
  if (days < 0 || days > 30) {
    message.warning('日期范围最多选择 31 天')
    return
  }
  loading.value = true
  try {
    const data = await RdStockMatrixApi.getPage({
      pageNo: queryParams.pageNo,
      pageSize: queryParams.pageSize,
      startDate: queryParams.dateRange[0],
      endDate: queryParams.dateRange[1],
      customerName: queryParams.customerName,
      productModel: queryParams.productModel,
      locationCode: queryParams.locationCode,
      furnaceNo: queryParams.furnaceNo
    })
    result.dates = data.dates
    result.list = data.list
    result.total = data.total
    result.totals = data.totals || emptyRow()
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.dateRange = [...initialRange]
  queryParams.customerName = undefined
  queryParams.productModel = undefined
  handleQuery()
}

const dailyValue = (row: RdStockMatrixRowVO, date: string, field: MovementField) =>
  Number(row.dailyMovements?.find((item) => item.bizDate === date)?.[field] || 0)

const dailyProp = (date: string, field: MovementField) => `daily:${date}:${field}`

const formatDateLabel = (date: string) => date.slice(5).replace('-', '/')

const formatQty = (value?: number, showZero = false) => {
  const amount = Number(value || 0)
  if (!showZero && amount === 0) return ''
  return amount.toLocaleString('zh-CN', { maximumFractionDigits: 6 })
}

const movementClass = (value?: number) => ({
  'movement-positive': Number(value || 0) > 0,
  'movement-negative': Number(value || 0) < 0
})

const dailyTotalsMap = () =>
  new Map<string, RdStockMatrixDailyVO>(
    (result.totals.dailyMovements || []).map((item) => [item.bizDate, item])
  )

const getSummaries = ({ columns }: { columns: Array<{ property?: string }> }) => {
  const totalsByDate = dailyTotalsMap()
  const fixedTotals: Record<string, number> = {
    openingQty: result.totals.openingQty,
    transferQty: result.totals.transferQty,
    inboundQty: result.totals.inboundQty,
    outboundQty: result.totals.outboundQty,
    returnQty: result.totals.returnQty,
    warehouseQty: result.totals.warehouseQty,
    stockWeight: result.totals.stockWeight
  }
  return columns.map((column, index) => {
    if (index === 0) return '汇总'
    const property = column.property || ''
    if (property in fixedTotals) return formatQty(fixedTotals[property], true)
    if (property.startsWith('daily:')) {
      const [, date, field] = property.split(':') as [string, string, MovementField]
      return formatQty(Number(totalsByDate.get(date)?.[field] || 0), true)
    }
    return ''
  })
}

const disableFutureDate = (date: Date) => date.getTime() > Date.now()

onMounted(getList)
</script>

<style scoped>
.matrix-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.matrix-toolbar h2 {
  margin: 0 0 4px;
  font-size: 18px;
  line-height: 24px;
}

.matrix-toolbar span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.matrix-totals {
  display: flex;
  gap: 20px;
  white-space: nowrap;
}

.matrix-totals strong {
  color: var(--el-text-color-primary);
  margin-left: 6px;
}

.movement-positive {
  color: #d92d20;
  font-weight: 600;
}

.movement-negative {
  color: #175cd3;
  font-weight: 600;
}

@media (max-width: 768px) {
  .matrix-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .matrix-totals {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
