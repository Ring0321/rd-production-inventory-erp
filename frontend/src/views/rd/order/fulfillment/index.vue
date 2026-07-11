<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="orderQuery"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="订单号" prop="orderNo">
        <el-input
          v-model="orderQuery.orderNo"
          placeholder="请输入订单号"
          clearable
          @keyup.enter="handleOrderQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="客户" prop="customerName">
        <el-input
          v-model="orderQuery.customerName"
          placeholder="请输入客户"
          clearable
          @keyup.enter="handleOrderQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="型号" prop="productModel">
        <el-input
          v-model="orderQuery.productModel"
          placeholder="请输入型号"
          clearable
          @keyup.enter="handleOrderQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="orderQuery.status" placeholder="请选择状态" clearable class="!w-240px">
          <el-option label="待满足" value="OPEN" />
          <el-option label="部分满足" value="PARTIAL" />
          <el-option label="已满足" value="FULFILLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleOrderQuery">
          <Icon icon="ep:search" class="mr-5px" /> 搜索
        </el-button>
        <el-button @click="resetOrderQuery">
          <Icon icon="ep:refresh" class="mr-5px" /> 重置
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-row :gutter="16">
      <el-col :xs="24" :lg="10" class="matching-pane">
        <div class="pane-header">
          <span class="pane-title">销售订单</span>
        </div>
        <el-table
          v-loading="orderLoading"
          :data="orderList"
          highlight-current-row
          height="360"
          @current-change="handleOrderSelect"
        >
          <el-table-column label="订单号" prop="orderNo" min-width="145" />
          <el-table-column label="客户" prop="customerName" min-width="120" />
          <el-table-column label="型号" prop="productModel" min-width="100" />
          <el-table-column label="剩余差额" prop="adjustedDifferenceQty" align="right" width="100" />
        </el-table>
        <Pagination
          :total="orderTotal"
          v-model:page="orderQuery.pageNo"
          v-model:limit="orderQuery.pageSize"
          @pagination="getOrderList"
        />
      </el-col>

      <el-col :xs="24" :lg="14" class="matching-pane candidate-pane">
        <div class="pane-header">
          <span class="pane-title">可用库存批次</span>
          <el-button
            type="primary"
            plain
            :disabled="!canAllocate"
            :loading="autoLoading"
            @click="handleAutoAllocate"
            v-hasPermi="['rd:order-fulfillment:auto']"
          >
            <Icon icon="ep:magic-stick" class="mr-5px" /> 自动匹配
          </el-button>
        </div>
        <el-table v-loading="candidateLoading" :data="candidateList" height="360">
          <el-table-column label="货位" prop="locationCode" width="90" />
          <el-table-column label="客户" prop="customerName" min-width="130" />
          <el-table-column label="生产炉号" prop="furnaceNo" width="120" />
          <el-table-column label="炉层" prop="furnaceLayer" width="90" />
          <el-table-column label="可用数量" prop="availableQty" align="right" width="100" />
          <el-table-column label="操作" align="center" fixed="right" width="80">
            <template #default="scope">
              <el-button
                link
                type="primary"
                :disabled="!canAllocate || !scope.row.sameCustomer"
                @click="openAllocationForm(scope.row)"
                v-hasPermi="['rd:order-fulfillment:allocate']"
              >
                匹配
              </el-button>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          :total="candidateTotal"
          v-model:page="candidateQuery.pageNo"
          v-model:limit="candidateQuery.pageSize"
          @pagination="getCandidateList"
        />
      </el-col>
    </el-row>
  </ContentWrap>

  <ContentWrap>
    <div class="pane-header">
      <span class="pane-title">已匹配库存</span>
      <span v-if="selectedOrder" class="selected-order">
        {{ selectedOrder.orderNo }} / {{ selectedOrder.customerName }} / {{ selectedOrder.productModel }}
      </span>
    </div>
    <el-table v-loading="fulfillmentLoading" :data="fulfillmentList" :stripe="true">
      <el-table-column label="货位" prop="locationCode" width="90" />
      <el-table-column label="客户" prop="customerName" min-width="140" />
      <el-table-column label="型号" prop="productModel" min-width="120" />
      <el-table-column label="生产炉号" prop="furnaceNo" width="120" />
      <el-table-column label="所在炉层" prop="furnaceLayer" width="100" />
      <el-table-column label="匹配数量" prop="matchedQty" align="right" width="100" />
      <el-table-column label="已分配量" prop="allocatedQty" align="right" width="100" />
      <el-table-column label="来源" prop="sourceType" align="center" width="90">
        <template #default="scope">{{ sourceTypeMap[scope.row.sourceType] || scope.row.sourceType }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="status" align="center" width="100">
        <template #default="scope">
          <el-tag :type="fulfillmentStatusMap[scope.row.status]?.type || 'info'">
            {{ fulfillmentStatusMap[scope.row.status]?.label || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="备注" prop="remark" min-width="150" />
      <el-table-column label="操作" align="center" fixed="right" width="90">
        <template #default="scope">
          <el-button
            link
            type="danger"
            :disabled="scope.row.status !== 'ALLOCATED'"
            @click="handleCancelAllocation(scope.row)"
            v-hasPermi="['rd:order-fulfillment:cancel']"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="fulfillmentTotal"
      v-model:page="fulfillmentQuery.pageNo"
      v-model:limit="fulfillmentQuery.pageSize"
      @pagination="getFulfillmentList"
    />
  </ContentWrap>

  <AllocationForm ref="allocationFormRef" @success="handleAllocationSuccess" />
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { RdSaleOrderApi } from '@/api/rd/order/sale'
import type { RdSaleOrderVO } from '@/api/rd/order/sale'
import { RdOrderFulfillmentApi } from '@/api/rd/order/fulfillment'
import type {
  RdOrderFulfillmentVO,
  RdOrderStockCandidateVO
} from '@/api/rd/order/fulfillment'
import AllocationForm from './AllocationForm.vue'

defineOptions({ name: 'RdOrderFulfillment' })

const route = useRoute()
const message = useMessage()
const queryFormRef = ref()
const orderLoading = ref(false)
const orderList = ref<RdSaleOrderVO[]>([])
const orderTotal = ref(0)
const selectedOrder = ref<RdSaleOrderVO>()
const orderQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  orderNo: undefined as string | undefined,
  customerName: undefined as string | undefined,
  productModel: undefined as string | undefined,
  status: undefined as string | undefined
})

const candidateLoading = ref(false)
const candidateList = ref<RdOrderStockCandidateVO[]>([])
const candidateTotal = ref(0)
const candidateQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  orderId: undefined as number | undefined,
  includeCrossCustomer: false
})

const fulfillmentLoading = ref(false)
const fulfillmentList = ref<RdOrderFulfillmentVO[]>([])
const fulfillmentTotal = ref(0)
const fulfillmentQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  orderId: undefined as number | undefined
})
const autoLoading = ref(false)
const allocationFormRef = ref()
const canAllocate = computed(() => {
  return Boolean(
    selectedOrder.value &&
      selectedOrder.value.status !== 'CLOSED' &&
      selectedOrder.value.status !== 'CANCELLED' &&
      Number(selectedOrder.value.adjustedDifferenceQty) > 0
  )
})
const sourceTypeMap: Record<string, string> = {
  STOCK: '库存匹配',
  PRODUCTION: '生产匹配',
  TRANSFER: '调拨'
}
const fulfillmentStatusOptions: Array<{ label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }> = [
  { label: '已匹配', value: 'MATCHED', type: 'info' },
  { label: '已分配', value: 'ALLOCATED', type: 'success' },
  { label: '已出库', value: 'OUTBOUNDED', type: 'info' },
  { label: '已取消', value: 'CANCELLED', type: 'danger' }
]
const fulfillmentStatusMap = fulfillmentStatusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }>
)

const getOrderList = async () => {
  orderLoading.value = true
  try {
    const data = await RdSaleOrderApi.getPage(orderQuery)
    orderList.value = data.list
    orderTotal.value = data.total
  } finally {
    orderLoading.value = false
  }
}

const handleOrderQuery = () => {
  orderQuery.pageNo = 1
  getOrderList()
}

const resetOrderQuery = () => {
  queryFormRef.value?.resetFields()
  handleOrderQuery()
}

const handleOrderSelect = async (order?: RdSaleOrderVO) => {
  selectedOrder.value = order
  candidateQuery.orderId = order?.id
  candidateQuery.pageNo = 1
  fulfillmentQuery.orderId = order?.id
  fulfillmentQuery.pageNo = 1
  await Promise.all([getCandidateList(), getFulfillmentList()])
}

const getCandidateList = async () => {
  if (!candidateQuery.orderId) {
    candidateList.value = []
    candidateTotal.value = 0
    return
  }
  candidateLoading.value = true
  try {
    const data = await RdOrderFulfillmentApi.getCandidatePage(candidateQuery)
    candidateList.value = data.list
    candidateTotal.value = data.total
  } finally {
    candidateLoading.value = false
  }
}

const getFulfillmentList = async () => {
  if (!fulfillmentQuery.orderId) {
    fulfillmentList.value = []
    fulfillmentTotal.value = 0
    return
  }
  fulfillmentLoading.value = true
  try {
    const data = await RdOrderFulfillmentApi.getPage(fulfillmentQuery)
    fulfillmentList.value = data.list
    fulfillmentTotal.value = data.total
  } finally {
    fulfillmentLoading.value = false
  }
}

const refreshSelectedOrder = async () => {
  if (!selectedOrder.value) return
  selectedOrder.value = await RdSaleOrderApi.get(selectedOrder.value.id)
}

const openAllocationForm = (candidate: RdOrderStockCandidateVO) => {
  if (!selectedOrder.value) return
  allocationFormRef.value?.open(selectedOrder.value, candidate)
}

const handleAllocationSuccess = async () => {
  await refreshSelectedOrder()
  await Promise.all([getOrderList(), getCandidateList(), getFulfillmentList()])
}

const handleAutoAllocate = async () => {
  if (!selectedOrder.value) return
  await message.confirm('确认按业务日期优先顺序自动匹配可用库存吗？')
  autoLoading.value = true
  try {
    const allocatedQty = await RdOrderFulfillmentApi.autoAllocate(selectedOrder.value.id)
    message.success(`自动匹配完成，匹配数量 ${allocatedQty}`)
    await handleAllocationSuccess()
  } finally {
    autoLoading.value = false
  }
}

const handleCancelAllocation = async (fulfillment: RdOrderFulfillmentVO) => {
  await message.confirm('确认取消该库存匹配并释放锁定数量吗？')
  await RdOrderFulfillmentApi.cancel(fulfillment.id)
  message.success('库存匹配已取消')
  await handleAllocationSuccess()
}

onMounted(async () => {
  await getOrderList()
  const routeOrderId = Number(route.query.orderId || 0)
  if (routeOrderId > 0) {
    const order = await RdSaleOrderApi.get(routeOrderId)
    await handleOrderSelect(order)
  } else if (orderList.value.length > 0) {
    await handleOrderSelect(orderList.value[0])
  }
})
</script>

<style scoped>
.matching-pane {
  min-width: 0;
}

.candidate-pane {
  border-left: 1px solid var(--el-border-color-lighter);
  padding-left: 16px;
}

.pane-header {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.pane-title {
  font-size: 16px;
  font-weight: 600;
}

.selected-order {
  color: var(--el-text-color-secondary);
}

@media (max-width: 1199px) {
  .candidate-pane {
    margin-top: 20px;
    border-left: 0;
    padding-left: 0;
  }
}
</style>
