<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="订单日期" prop="orderDate">
        <el-date-picker
          v-model="queryParams.orderDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="订单号" prop="orderNo">
        <el-input
          v-model="queryParams.orderNo"
          placeholder="请输入订单号"
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
          @click="openForm('create')"
          v-hasPermi="['rd:sale-order:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
        <el-button
          type="success"
          plain
          :loading="exportLoading"
          @click="handleExport"
          v-hasPermi="['rd:sale-order:export']"
        >
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="订单号" align="center" prop="orderNo" min-width="150" />
      <el-table-column label="订单日期" align="center" prop="orderDate" width="110" />
      <el-table-column label="客户" align="center" prop="customerName" min-width="150" />
      <el-table-column label="型号" align="center" prop="productModel" min-width="130" />
      <el-table-column label="订单量" align="right" prop="requiredQty" width="100" />
      <el-table-column label="实际产额" align="right" prop="actualOutputQty" width="100" />
      <el-table-column label="调入量" align="right" prop="transferInQty" width="90" />
      <el-table-column label="调出量" align="right" prop="transferOutQty" width="90" />
      <el-table-column label="差额" align="right" prop="differenceQty" width="100">
        <template #default="scope">
          <span :class="differenceClass(scope.row.differenceQty)">{{ scope.row.differenceQty }}</span>
        </template>
      </el-table-column>
      <el-table-column label="调拨后差额" align="right" prop="adjustedDifferenceQty" width="115">
        <template #default="scope">
          <span :class="differenceClass(scope.row.adjustedDifferenceQty)">
            {{ scope.row.adjustedDifferenceQty }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="满足数量" align="right" prop="fulfilledQty" width="100" />
      <el-table-column label="状态" align="center" prop="status" width="100">
        <template #default="scope">
          <el-tag :type="statusMap[scope.row.status]?.type || 'info'">
            {{ statusMap[scope.row.status]?.label || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="500">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openDetail(scope.row.id)"
            v-hasPermi="['rd:sale-order:query']"
          >
            详情
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="!isEditable(scope.row)"
            @click="openFulfillment(scope.row.id)"
            v-hasPermi="['rd:order-fulfillment:query']"
          >
            匹配库存
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="!canTransfer(scope.row)"
            @click="openTransfer(scope.row.id)"
            v-hasPermi="['rd:transfer-order:create']"
          >
            调拨补足
          </el-button>
          <el-button
            link
            type="success"
            :disabled="!canOutbound(scope.row)"
            @click="openOutbound(scope.row.id)"
            v-hasPermi="['rd:outbound-order:create']"
          >
            出库上线
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="!isEditable(scope.row)"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['rd:sale-order:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="!isEditable(scope.row)"
            @click="handleRecalculate(scope.row)"
            v-hasPermi="['rd:sale-order:recalculate']"
          >
            重算
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="!isEditable(scope.row)"
            @click="handleCancel(scope.row)"
            v-hasPermi="['rd:sale-order:cancel']"
          >
            取消
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

  <SaleOrderForm ref="formRef" @success="getList" />
  <SaleOrderDetailDrawer ref="detailDrawerRef" />
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import download from '@/utils/download'
import { useMessage } from '@/hooks/web/useMessage'
import { RdSaleOrderApi } from '@/api/rd/order/sale'
import type { RdSaleOrderVO } from '@/api/rd/order/sale'
import SaleOrderForm from './SaleOrderForm.vue'
import SaleOrderDetailDrawer from './SaleOrderDetailDrawer.vue'
import RdCustomerProductFields from '@/components/RdCustomerProductFields/index.vue'

defineOptions({ name: 'RdSaleOrder' })

const message = useMessage()
const router = useRouter()
const loading = ref(true)
const list = ref<RdSaleOrderVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const formRef = ref()
const exportLoading = ref(false)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  orderDate: [] as string[],
  orderNo: undefined as string | undefined,
  customerName: undefined as string | undefined,
  productModel: undefined as string | undefined,
  status: undefined as string | undefined
})
const statusOptions: Array<{ label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }> = [
  { label: '待满足', value: 'OPEN', type: 'info' },
  { label: '部分满足', value: 'PARTIAL', type: 'warning' },
  { label: '已满足', value: 'FULFILLED', type: 'success' },
  { label: '已关闭', value: 'CLOSED', type: 'info' },
  { label: '已取消', value: 'CANCELLED', type: 'danger' }
]
const statusMap = statusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }>
)

const getList = async () => {
  loading.value = true
  try {
    const data = await RdSaleOrderApi.getPage(queryParams)
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

const openForm = (type: 'create' | 'update', id?: number) => {
  formRef.value?.open(type, id)
}

const detailDrawerRef = ref()
const openDetail = (id: number) => {
  detailDrawerRef.value?.open(id)
}

const openFulfillment = (orderId: number) => {
  router.push({ name: 'RdOrderFulfillment', query: { orderId } })
}

const openTransfer = (toOrderId: number) => {
  router.push({ name: 'RdTransferOrder', query: { toOrderId } })
}

const openOutbound = (orderId: number) => {
  router.push({ name: 'RdOutboundOrder', query: { orderId } })
}

const isEditable = (order: RdSaleOrderVO) => {
  return order.status !== 'CLOSED' && order.status !== 'CANCELLED'
}

const canTransfer = (order: RdSaleOrderVO) => {
  return isEditable(order) && Number(order.adjustedDifferenceQty) > 0
}

const canOutbound = (order: RdSaleOrderVO) => {
  return isEditable(order) && Number(order.allocatedQty) > 0
}

const differenceClass = (value: number) => {
  const difference = Number(value)
  if (difference > 0) return 'difference-shortage'
  if (difference < 0) return 'difference-over'
  return 'difference-met'
}

const handleRecalculate = async (order: RdSaleOrderVO) => {
  await RdSaleOrderApi.recalculate(order.id)
  message.success('订单差额重算成功')
  await getList()
}

const handleCancel = async (order: RdSaleOrderVO) => {
  await message.confirm('确认取消该销售订单吗？取消后不能再修改。')
  await RdSaleOrderApi.cancel(order.id)
  message.success('销售订单已取消')
  await getList()
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await RdSaleOrderApi.exportExcel(queryParams)
    download.excel(data, '销售订单.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.difference-shortage {
  color: var(--el-color-danger);
  font-weight: 600;
}

.difference-over {
  color: var(--el-color-warning);
  font-weight: 600;
}

.difference-met {
  color: var(--el-color-success);
  font-weight: 600;
}
</style>
