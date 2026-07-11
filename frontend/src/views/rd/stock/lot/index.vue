<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="日期" prop="lastBizDate">
        <el-date-picker
          v-model="queryParams.lastBizDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="货位" prop="locationCode">
        <el-input
          v-model="queryParams.locationCode"
          placeholder="请输入货位"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <RdCustomerProductFields
        v-model:customer-name="queryParams.customerName"
        v-model:product-model="queryParams.productModel"
        query-mode
      />
      <el-form-item label="炉号" prop="furnaceNo">
        <el-input
          v-model="queryParams.furnaceNo"
          placeholder="请输入生产炉号"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="炉层" prop="furnaceLayer">
        <el-input
          v-model="queryParams.furnaceLayer"
          placeholder="请输入所在炉层"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-240px">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="primary"
          plain
          @click="openOpeningForm"
          v-hasPermi="['rd:stock-lot:opening']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增期初
        </el-button>
        <el-button
          type="success"
          plain
          @click="handleExport"
          :loading="exportLoading"
          v-hasPermi="['rd:stock-lot:export']"
        >
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="货位" align="center" prop="locationCode" width="100" />
      <el-table-column label="客户" align="center" prop="customerName" min-width="150" />
      <el-table-column label="型号" align="center" prop="productModel" min-width="140" />
      <el-table-column label="生产炉号" align="center" prop="furnaceNo" width="130" />
      <el-table-column label="所在炉层" align="center" prop="furnaceLayer" width="100" />
      <el-table-column label="期初" align="right" prop="openingQty" width="100" />
      <el-table-column label="调入" align="right" prop="transferInQty" width="100" />
      <el-table-column label="调出" align="right" prop="transferOutQty" width="100" />
      <el-table-column label="入库" align="right" prop="inboundQty" width="100" />
      <el-table-column label="出库" align="right" prop="outboundQty" width="100" />
      <el-table-column label="退货" align="right" prop="returnQty" width="100" />
      <el-table-column label="库房数量" align="right" prop="warehouseQty" width="110" />
      <el-table-column label="锁定数量" align="right" prop="lockedQty" width="110" />
      <el-table-column label="可用数量" align="right" prop="availableQty" width="110" />
      <el-table-column label="单包重量" align="right" prop="packageWeight" width="110" />
      <el-table-column label="库存重量" align="right" prop="stockWeight" width="110" />
      <el-table-column label="最后日期" align="center" prop="lastBizDate" width="110" />
      <el-table-column label="状态" align="center" prop="status" width="110">
        <template #default="scope">
          <el-tag :type="statusMap[scope.row.status]?.type || 'info'">
            {{ statusMap[scope.row.status]?.label || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="160">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openTransactionDrawer(scope.row)"
            v-hasPermi="['rd:stock-transaction:query']"
          >
            流水
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="scope.row.status === 'CLOSED'"
            @click="openAdjustmentForm(scope.row)"
            v-hasPermi="['rd:stock-lot:adjust']"
          >
            调整
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <StockTransactionDrawer ref="transactionDrawerRef" />
  <OpeningForm ref="openingFormRef" @success="getList" />
  <AdjustmentForm ref="adjustmentFormRef" @success="getList" />
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import download from '@/utils/download'
import { useMessage } from '@/hooks/web/useMessage'
import { RdStockLotApi } from '@/api/rd/stock/lot'
import type { RdStockLotVO } from '@/api/rd/stock/lot'
import StockTransactionDrawer from './StockTransactionDrawer.vue'
import OpeningForm from './OpeningForm.vue'
import AdjustmentForm from './AdjustmentForm.vue'
import RdCustomerProductFields from '@/components/RdCustomerProductFields/index.vue'

defineOptions({ name: 'RdStockLot' })

const message = useMessage()
const loading = ref(true)
const list = ref<RdStockLotVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const exportLoading = ref(false)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  lastBizDate: [] as string[],
  locationCode: undefined as string | undefined,
  customerName: undefined as string | undefined,
  productModel: undefined as string | undefined,
  furnaceNo: undefined as string | undefined,
  furnaceLayer: undefined as string | undefined,
  status: undefined as string | undefined
})
const statusOptions: Array<{ label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }> = [
  { label: '可用', value: 'AVAILABLE', type: 'success' },
  { label: '锁定', value: 'LOCKED', type: 'warning' },
  { label: '已空', value: 'EMPTY', type: 'info' },
  { label: '已关闭', value: 'CLOSED', type: 'danger' }
]
const statusMap = statusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }>
)

const getList = async () => {
  loading.value = true
  try {
    const data = await RdStockLotApi.getPage(queryParams)
    list.value = data.list
    total.value = data.total
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
  handleQuery()
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await RdStockLotApi.exportExcel(queryParams)
    download.excel(data, '库存台账.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

const transactionDrawerRef = ref()
const openTransactionDrawer = (stockLot: RdStockLotVO) => {
  transactionDrawerRef.value?.open(stockLot)
}

const openingFormRef = ref()
const openOpeningForm = () => {
  openingFormRef.value?.open()
}

const adjustmentFormRef = ref()
const openAdjustmentForm = (stockLot: RdStockLotVO) => {
  adjustmentFormRef.value?.open(stockLot)
}

onMounted(() => {
  getList()
})
</script>
