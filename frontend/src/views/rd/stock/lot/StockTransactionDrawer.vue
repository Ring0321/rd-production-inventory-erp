<template>
  <el-drawer v-model="drawerVisible" :title="drawerTitle" size="88%">
    <el-descriptions v-if="currentLot" :column="2" border class="mb-16px">
      <el-descriptions-item label="货位">{{ currentLot.locationCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="客户">{{ currentLot.customerName }}</el-descriptions-item>
      <el-descriptions-item label="型号">{{ currentLot.productModel }}</el-descriptions-item>
      <el-descriptions-item label="生产炉号">{{ currentLot.furnaceNo }}</el-descriptions-item>
      <el-descriptions-item label="所在炉层">{{ currentLot.furnaceLayer }}</el-descriptions-item>
      <el-descriptions-item label="库房数量">{{ currentLot.warehouseQty }}</el-descriptions-item>
      <el-descriptions-item label="单包重量">{{ currentLot.packageWeight }}</el-descriptions-item>
      <el-descriptions-item label="库存重量">{{ currentLot.stockWeight }}</el-descriptions-item>
    </el-descriptions>

    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="业务日期" align="center" prop="bizDate" width="110" />
      <el-table-column label="流水类型" align="center" prop="transactionType" width="120">
        <template #default="scope">
          <el-tag :type="transactionTypeMap[scope.row.transactionType]?.type || 'info'">
            {{ transactionTypeMap[scope.row.transactionType]?.label || scope.row.transactionType }}
          </el-tag>
        </template>
      </el-table-column>
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
      <el-table-column label="来源行号" align="center" prop="sourceLineNo" width="120" />
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
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { RdStockTransactionApi } from '@/api/rd/stock/transaction'
import type { RdStockTransactionVO } from '@/api/rd/stock/transaction'
import type { RdStockLotVO } from '@/api/rd/stock/lot'

defineOptions({ name: 'RdStockTransactionDrawer' })

const drawerVisible = ref(false)
const loading = ref(false)
const currentLot = ref<RdStockLotVO>()
const list = ref<RdStockTransactionVO[]>([])
const total = ref(0)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  stockLotId: undefined as number | undefined
})
const drawerTitle = computed(() => {
  if (!currentLot.value) {
    return '库存流水'
  }
  return `库存流水 - 批次 ${currentLot.value.id}`
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
  if (!queryParams.stockLotId) {
    return
  }
  loading.value = true
  try {
    const data = await RdStockTransactionApi.getPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const open = async (stockLot: RdStockLotVO) => {
  currentLot.value = stockLot
  queryParams.stockLotId = stockLot.id
  queryParams.pageNo = 1
  drawerVisible.value = true
  await getList()
}

defineExpose({ open })
</script>
