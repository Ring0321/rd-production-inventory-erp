<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible" width="880px">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="112px"
      v-loading="formLoading"
    >
      <el-row :gutter="20">
        <el-col :xs="24" :md="12">
          <el-form-item label="出库单号" prop="outboundNo">
            <el-input v-model="formData.outboundNo" placeholder="请输入出库单号" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="出库日期" prop="outboundDate">
            <el-date-picker
              v-model="formData.outboundDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择出库日期"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">销售订单</el-divider>
      <el-form-item label="销售订单" prop="orderId">
        <el-select
          v-model="formData.orderId"
          filterable
          placeholder="请选择已有库存分配的销售订单"
          class="!w-1/1"
          @change="handleOrderChange"
        >
          <el-option
            v-for="order in availableOrders"
            :key="order.id"
            :label="formatOrderLabel(order)"
            :value="order.id"
          />
        </el-select>
      </el-form-item>
      <el-descriptions v-if="selectedOrder" :column="4" border class="mb-18px">
        <el-descriptions-item label="客户">{{ selectedOrder.customerName }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ selectedOrder.productModel }}</el-descriptions-item>
        <el-descriptions-item label="订单量">{{ selectedOrder.requiredQty }}</el-descriptions-item>
        <el-descriptions-item label="满足数量">
          <span class="quantity-emphasis">{{ selectedOrder.fulfilledQty }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">出库批次</el-divider>
      <el-form-item label="已分配库存" prop="fulfillmentId">
        <el-select
          v-model="formData.fulfillmentId"
          filterable
          placeholder="请选择待出库的炉号与炉层"
          class="!w-1/1"
          :loading="fulfillmentLoading"
          :disabled="!formData.orderId"
          @change="handleFulfillmentChange"
        >
          <el-option
            v-for="fulfillment in availableFulfillments"
            :key="fulfillment.id"
            :label="formatFulfillmentLabel(fulfillment)"
            :value="fulfillment.id"
          />
        </el-select>
      </el-form-item>
      <el-descriptions v-if="selectedFulfillment" :column="4" border class="mb-18px">
        <el-descriptions-item label="货位">{{ selectedFulfillment.locationCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="炉号/炉层">
          {{ selectedFulfillment.furnaceNo }} / {{ selectedFulfillment.furnaceLayer }}
        </el-descriptions-item>
        <el-descriptions-item label="已分配">{{ selectedFulfillment.allocatedQty }}</el-descriptions-item>
        <el-descriptions-item label="待出库">
          <span class="quantity-emphasis">{{ remainingQty }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="库房数量">{{ selectedStockLot?.warehouseQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="锁定数量">{{ selectedStockLot?.lockedQty ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="单包重量">{{ selectedStockLot?.packageWeight ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="库存状态">{{ selectedStockLot?.status || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-row :gutter="20">
        <el-col :xs="24" :md="12">
          <el-form-item label="出库数量" prop="quantity">
            <el-input-number
              v-model="formData.quantity"
              :min="0.000001"
              :max="remainingQty || undefined"
              :precision="6"
              class="!w-1/1"
              placeholder="请输入出库数量"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="出库重量">
            <el-input :model-value="outboundWeightPreview" disabled>
              <template #append>单包 {{ selectedStockLot?.packageWeight || 0 }}</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
          placeholder="请输入出库备注"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :disabled="formLoading" @click="submitForm">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import { RdSaleOrderApi } from '@/api/rd/order/sale'
import type { RdSaleOrderVO } from '@/api/rd/order/sale'
import { RdOrderFulfillmentApi } from '@/api/rd/order/fulfillment'
import type { RdOrderFulfillmentVO } from '@/api/rd/order/fulfillment'
import { RdStockLotApi } from '@/api/rd/stock/lot'
import type { RdStockLotVO } from '@/api/rd/stock/lot'
import { RdOutboundOrderApi } from '@/api/rd/outbound'
import type { RdOutboundOrderSaveReqVO } from '@/api/rd/outbound'

defineOptions({ name: 'RdOutboundOrderForm' })

const message = useMessage()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const fulfillmentLoading = ref(false)
const formType = ref<'create' | 'update'>('create')
const currentFulfillmentId = ref<number>()
const formRef = ref()
const orderList = ref<RdSaleOrderVO[]>([])
const fulfillmentList = ref<RdOrderFulfillmentVO[]>([])
const selectedStockLot = ref<RdStockLotVO>()
const formData = ref<Partial<RdOutboundOrderSaveReqVO>>({})

const availableOrders = computed(() => {
  return orderList.value.filter((order) => {
    const editable = order.status !== 'CLOSED' && order.status !== 'CANCELLED'
    return editable && Number(order.allocatedQty) > 0
  })
})
const selectedOrder = computed(() => {
  return orderList.value.find((order) => order.id === formData.value.orderId)
})
const availableFulfillments = computed(() => {
  return fulfillmentList.value.filter((fulfillment) => {
    const remaining = Number(fulfillment.allocatedQty) - Number(fulfillment.outboundQty || 0)
    return (
      (fulfillment.status === 'ALLOCATED' && remaining > 0) ||
      fulfillment.id === currentFulfillmentId.value
    )
  })
})
const selectedFulfillment = computed(() => {
  return fulfillmentList.value.find(
    (fulfillment) => fulfillment.id === formData.value.fulfillmentId
  )
})
const remainingQty = computed(() => {
  const fulfillment = selectedFulfillment.value
  if (!fulfillment) return 0
  return Math.max(0, Number(fulfillment.allocatedQty) - Number(fulfillment.outboundQty || 0))
})
const outboundWeightPreview = computed(() => {
  const quantity = Number(formData.value.quantity || 0)
  const packageWeight = Number(selectedStockLot.value?.packageWeight || 0)
  return (quantity * packageWeight).toFixed(6)
})

const validateQuantity = (_rule: unknown, value: number | undefined, callback: (error?: Error) => void) => {
  if (!value || Number(value) <= 0) {
    callback(new Error('出库数量必须大于 0'))
    return
  }
  if (Number(value) > remainingQty.value) {
    callback(new Error(`出库数量不能超过待出库数量 ${remainingQty.value}`))
    return
  }
  callback()
}
const formRules = reactive({
  outboundNo: [{ required: true, message: '出库单号不能为空', trigger: 'blur' }],
  outboundDate: [{ required: true, message: '出库日期不能为空', trigger: 'change' }],
  orderId: [{ required: true, message: '销售订单不能为空', trigger: 'change' }],
  fulfillmentId: [{ required: true, message: '已分配库存不能为空', trigger: 'change' }],
  quantity: [{ required: true, validator: validateQuantity, trigger: 'blur' }]
})

const resetForm = () => {
  formData.value = {
    id: undefined,
    outboundNo: `OUT-${formatDate(new Date(), 'YYYYMMDDHHmmss')}`,
    outboundDate: formatDate(new Date(), 'YYYY-MM-DD'),
    orderId: undefined,
    fulfillmentId: undefined,
    quantity: undefined,
    remark: undefined
  }
  currentFulfillmentId.value = undefined
  fulfillmentList.value = []
  selectedStockLot.value = undefined
  formRef.value?.resetFields()
}

const loadOrderOptions = async (selectedOrderId?: number) => {
  const data = await RdSaleOrderApi.getPage({ pageNo: 1, pageSize: 200 })
  orderList.value = data.list
  if (selectedOrderId && !orderList.value.some((order) => order.id === selectedOrderId)) {
    orderList.value.unshift(await RdSaleOrderApi.get(selectedOrderId))
  }
}

const loadFulfillments = async (selectedFulfillmentId?: number) => {
  if (!formData.value.orderId) {
    fulfillmentList.value = []
    return
  }
  fulfillmentLoading.value = true
  try {
    const data = await RdOrderFulfillmentApi.getPage({
      pageNo: 1,
      pageSize: 200,
      orderId: formData.value.orderId
    })
    fulfillmentList.value = data.list
    currentFulfillmentId.value = selectedFulfillmentId
  } finally {
    fulfillmentLoading.value = false
  }
}

const loadSelectedStockLot = async () => {
  const stockLotId = selectedFulfillment.value?.stockLotId
  selectedStockLot.value = stockLotId ? await RdStockLotApi.get(stockLotId) : undefined
}

const open = async (type: 'create' | 'update', id?: number, orderId?: number) => {
  resetForm()
  formType.value = type
  dialogTitle.value = type === 'create' ? '新增出库单' : '编辑出库单'
  dialogVisible.value = true
  formLoading.value = true
  try {
    if (id) {
      const outboundOrder = await RdOutboundOrderApi.get(id)
      await loadOrderOptions(outboundOrder.orderId)
      formData.value = outboundOrder
      await loadFulfillments(outboundOrder.fulfillmentId)
      await loadSelectedStockLot()
    } else {
      await loadOrderOptions(orderId)
      if (orderId) {
        formData.value.orderId = orderId
        await loadFulfillments()
      }
    }
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open })

const handleOrderChange = async () => {
  formData.value.fulfillmentId = undefined
  formData.value.quantity = undefined
  selectedStockLot.value = undefined
  currentFulfillmentId.value = undefined
  await loadFulfillments()
}

const handleFulfillmentChange = async () => {
  await loadSelectedStockLot()
  formData.value.quantity = remainingQty.value > 0 ? remainingQty.value : undefined
}

const formatOrderLabel = (order: RdSaleOrderVO) => {
  return `${order.orderNo} / ${order.customerName} / ${order.productModel} / 已分配 ${order.allocatedQty}`
}

const formatFulfillmentLabel = (fulfillment: RdOrderFulfillmentVO) => {
  const location = fulfillment.locationCode || '未分配货位'
  const remaining = Number(fulfillment.allocatedQty) - Number(fulfillment.outboundQty || 0)
  return `${location} / ${fulfillment.furnaceNo}-${fulfillment.furnaceLayer} / 待出库 ${remaining}`
}

const emit = defineEmits(['success'])
const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    const data = formData.value as RdOutboundOrderSaveReqVO
    if (formType.value === 'create') {
      await RdOutboundOrderApi.create(data)
      message.success('出库单新增成功')
    } else {
      await RdOutboundOrderApi.update(data)
      message.success('出库单修改成功')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>

<style scoped>
.quantity-emphasis {
  color: var(--el-color-primary);
  font-weight: 600;
}
</style>
