<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="业务日期" prop="bizDate">
        <el-date-picker
          v-model="queryParams.bizDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="流水类型" prop="transactionType">
        <el-select
          v-model="queryParams.transactionType"
          placeholder="请选择流水类型"
          clearable
          class="!w-240px"
        >
          <el-option
            v-for="item in transactionTypeOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
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
      <el-form-item label="客户" prop="customerName">
        <el-input
          v-model="queryParams.customerName"
          placeholder="请输入客户"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="型号" prop="productModel">
        <el-input
          v-model="queryParams.productModel"
          placeholder="请输入型号"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
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
      <el-form-item label="来源单号" prop="sourceNo">
        <el-input
          v-model="queryParams.sourceNo"
          placeholder="请输入来源单号"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="success"
          plain
          :loading="exportLoading"
          @click="handleExport"
          v-hasPermi="['rd:stock-transaction:export']"
        >
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="业务日期" align="center" prop="bizDate" width="110" />
      <el-table-column label="流水类型" align="center" prop="transactionType" width="120">
        <template #default="scope">
          <el-tag :type="transactionTypeMap[scope.row.transactionType]?.type || 'info'">
            {{ transactionTypeMap[scope.row.transactionType]?.label || scope.row.transactionType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="批次编号" align="center" prop="stockLotId" width="100" />
      <el-table-column label="货位" align="center" prop="locationCode" width="100" />
      <el-table-column label="客户" align="center" prop="customerName" min-width="150" />
      <el-table-column label="型号" align="center" prop="productModel" min-width="130" />
      <el-table-column label="生产炉号" align="center" prop="furnaceNo" width="130" />
      <el-table-column label="所在炉层" align="center" prop="furnaceLayer" width="100" />
      <el-table-column label="变动前" align="right" prop="beforeQty" width="100" />
      <el-table-column label="变动数量" align="right" prop="quantity" width="100" />
      <el-table-column label="变动后" align="right" prop="afterQty" width="100" />
      <el-table-column label="单包重量" align="right" prop="packageWeight" width="110" />
      <el-table-column label="变动重量" align="right" prop="weight" width="110" />
      <el-table-column label="来源类型" align="center" prop="sourceType" width="150">
        <template #default="scope">
          {{ sourceTypeMap[scope.row.sourceType] || scope.row.sourceType || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="来源单号" align="center" prop="sourceNo" width="120" />
      <el-table-column label="关联订单" align="center" prop="relatedOrderId" width="100" />
      <el-table-column label="操作人" align="center" prop="operatorName" width="100" />
      <el-table-column label="备注" align="left" prop="remark" min-width="170" />
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import download from '@/utils/download'
import { useMessage } from '@/hooks/web/useMessage'
import { RdStockTransactionApi } from '@/api/rd/stock/transaction'
import type { RdStockTransactionVO } from '@/api/rd/stock/transaction'

defineOptions({ name: 'RdStockTransaction' })

const message = useMessage()
const loading = ref(true)
const list = ref<RdStockTransactionVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const exportLoading = ref(false)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  bizDate: [] as string[],
  transactionType: undefined as string | undefined,
  locationCode: undefined as string | undefined,
  customerName: undefined as string | undefined,
  productModel: undefined as string | undefined,
  furnaceNo: undefined as string | undefined,
  furnaceLayer: undefined as string | undefined,
  sourceNo: undefined as string | undefined
})
const transactionTypeOptions = [
  { label: '期初', value: 'OPENING', type: 'info' },
  { label: '生产入库', value: 'PRODUCTION_IN', type: 'success' },
  { label: '入库', value: 'INBOUND', type: 'success' },
  { label: '出库', value: 'OUTBOUND', type: 'danger' },
  { label: '调入', value: 'TRANSFER_IN', type: 'success' },
  { label: '调出', value: 'TRANSFER_OUT', type: 'warning' },
  { label: '退货入库', value: 'RETURN_IN', type: 'success' },
  { label: '盘盈调整', value: 'ADJUST_IN', type: 'success' },
  { label: '盘亏调整', value: 'ADJUST_OUT', type: 'danger' }
]
const transactionTypeMap = transactionTypeOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: string }>
)
const sourceTypeMap: Record<string, string> = {
  PRODUCTION_OUTPUT: '生产炉层产量',
  STOCK_OPENING: '期初库存',
  STOCK_ADJUSTMENT: '库存调整',
  STOCK_IN: '入库单',
  STOCK_OUT: '出库单',
  TRANSFER: '调拨单',
  RETURN: '退货单',
  ADJUSTMENT: '库存调整'
}

const getList = async () => {
  loading.value = true
  try {
    const data = await RdStockTransactionApi.getPage(queryParams)
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
    const data = await RdStockTransactionApi.exportExcel(queryParams)
    download.excel(data, '库存流水.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>
