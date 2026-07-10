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
          <el-form-item label="调拨单号" prop="transferNo">
            <el-input v-model="formData.transferNo" placeholder="请输入调拨单号" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="调拨日期" prop="transferDate">
            <el-date-picker
              v-model="formData.transferDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="请选择调拨日期"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">调入订单</el-divider>
      <el-form-item label="目标销售订单" prop="toOrderId">
        <el-select
          v-model="formData.toOrderId"
          filterable
          placeholder="请选择需要补足的销售订单"
          class="!w-1/1"
          @change="handleToOrderChange"
        >
          <el-option
            v-for="order in availableTargetOrders"
            :key="order.id"
            :label="formatOrderLabel(order)"
            :value="order.id"
          />
        </el-select>
      </el-form-item>
      <el-descriptions v-if="selectedToOrder" :column="3" border class="mb-18px">
        <el-descriptions-item label="客户">{{ selectedToOrder.customerName }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ selectedToOrder.productModel }}</el-descriptions-item>
        <el-descriptions-item label="剩余差额">
          <span class="quantity-emphasis">{{ selectedToOrder.adjustedDifferenceQty }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">调出库存</el-divider>
      <el-form-item label="来源库存批次" prop="fromStockLotId">
        <el-select
          v-model="formData.fromStockLotId"
          filterable
          placeholder="请选择不同客户的同型号可用库存"
          class="!w-1/1"
          :loading="candidateLoading"
          :disabled="!formData.toOrderId"
          @change="handleStockLotChange"
        >
          <el-option
            v-for="candidate in candidateList"
            :key="candidate.id"
            :label="formatCandidateLabel(candidate)"
            :value="candidate.id"
          />
        </el-select>
      </el-form-item>
      <el-descriptions v-if="selectedStockLot" :column="4" border class="mb-18px">
        <el-descriptions-item label="调出客户">{{ selectedStockLot.customerName }}</el-descriptions-item>
        <el-descriptions-item label="货位">{{ selectedStockLot.locationCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="炉号/炉层">
          {{ selectedStockLot.furnaceNo }} / {{ selectedStockLot.furnaceLayer }}
        </el-descriptions-item>
        <el-descriptions-item label="可用数量">
          <span class="quantity-emphasis">{{ selectedStockLot.availableQty }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form-item label="来源关联订单" prop="fromOrderId">
        <el-select
          v-model="formData.fromOrderId"
          filterable
          clearable
          placeholder="可选：关联被调出客户的销售订单"
          class="!w-1/1"
          :disabled="!selectedStockLot"
        >
          <el-option
            v-for="order in sourceOrderOptions"
            :key="order.id"
            :label="formatOrderLabel(order)"
            :value="order.id"
          />
        </el-select>
      </el-form-item>

      <el-row :gutter="20">
        <el-col :xs="24" :md="12">
          <el-form-item label="调拨数量" prop="quantity">
            <el-input-number
              v-model="formData.quantity"
              :min="0.000001"
              :max="maxTransferQty || undefined"
              :precision="6"
              class="!w-1/1"
              placeholder="请输入调拨数量"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="调拨重量">
            <el-input :model-value="transferWeightPreview" disabled>
              <template #append>单包 {{ selectedStockLot?.packageWeight || 0 }}</template>
            </el-input>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="调拨原因" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
          placeholder="请输入调拨原因"
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
import type { RdOrderStockCandidateVO } from '@/api/rd/order/fulfillment'
import { RdStockLotApi } from '@/api/rd/stock/lot'
import { RdTransferOrderApi } from '@/api/rd/transfer'
import type { RdTransferOrderSaveReqVO } from '@/api/rd/transfer'

defineOptions({ name: 'RdTransferOrderForm' })

const message = useMessage()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const candidateLoading = ref(false)
const formType = ref<'create' | 'update'>('create')
const formRef = ref()
const orderList = ref<RdSaleOrderVO[]>([])
const candidateList = ref<RdOrderStockCandidateVO[]>([])
const formData = ref<Partial<RdTransferOrderSaveReqVO>>({})

const availableTargetOrders = computed(() => {
  return orderList.value.filter((order) => {
    const editable = order.status !== 'CLOSED' && order.status !== 'CANCELLED'
    return editable && Number(order.adjustedDifferenceQty) > 0
  })
})
const selectedToOrder = computed(() => {
  return orderList.value.find((order) => order.id === formData.value.toOrderId)
})
const selectedStockLot = computed(() => {
  return candidateList.value.find((stockLot) => stockLot.id === formData.value.fromStockLotId)
})
const sourceOrderOptions = computed(() => {
  const stockLot = selectedStockLot.value
  if (!stockLot) return []
  return orderList.value.filter((order) => {
    const editable = order.status !== 'CLOSED' && order.status !== 'CANCELLED'
    return (
      editable &&
      order.id !== formData.value.toOrderId &&
      order.customerName === stockLot.customerName &&
      order.productModel === stockLot.productModel
    )
  })
})
const maxTransferQty = computed(() => {
  const availableQty = Number(selectedStockLot.value?.availableQty || 0)
  const differenceQty = Number(selectedToOrder.value?.adjustedDifferenceQty || 0)
  return Math.min(availableQty, differenceQty)
})
const transferWeightPreview = computed(() => {
  const quantity = Number(formData.value.quantity || 0)
  const packageWeight = Number(selectedStockLot.value?.packageWeight || 0)
  return (quantity * packageWeight).toFixed(6)
})

const validateQuantity = (_rule: unknown, value: number | undefined, callback: (error?: Error) => void) => {
  if (!value || Number(value) <= 0) {
    callback(new Error('调拨数量必须大于 0'))
    return
  }
  if (Number(value) > maxTransferQty.value) {
    callback(new Error(`调拨数量不能超过 ${maxTransferQty.value}`))
    return
  }
  callback()
}
const formRules = reactive({
  transferNo: [{ required: true, message: '调拨单号不能为空', trigger: 'blur' }],
  transferDate: [{ required: true, message: '调拨日期不能为空', trigger: 'change' }],
  toOrderId: [{ required: true, message: '目标销售订单不能为空', trigger: 'change' }],
  fromStockLotId: [{ required: true, message: '来源库存批次不能为空', trigger: 'change' }],
  quantity: [{ required: true, validator: validateQuantity, trigger: 'blur' }]
})

const resetForm = () => {
  formData.value = {
    id: undefined,
    transferNo: `TR-${formatDate(new Date(), 'YYYYMMDDHHmmss')}`,
    transferDate: formatDate(new Date(), 'YYYY-MM-DD'),
    fromOrderId: undefined,
    toOrderId: undefined,
    fromStockLotId: undefined,
    quantity: undefined,
    remark: undefined
  }
  candidateList.value = []
  formRef.value?.resetFields()
}

const loadOrderOptions = async () => {
  const data = await RdSaleOrderApi.getPage({ pageNo: 1, pageSize: 200 })
  orderList.value = data.list
}

const loadCandidateList = async (selectedStockLotId?: number) => {
  if (!formData.value.toOrderId) {
    candidateList.value = []
    return
  }
  candidateLoading.value = true
  try {
    const data = await RdOrderFulfillmentApi.getCandidatePage({
      pageNo: 1,
      pageSize: 200,
      orderId: formData.value.toOrderId,
      includeCrossCustomer: true
    })
    candidateList.value = data.list.filter((item: RdOrderStockCandidateVO) => !item.sameCustomer)
    if (selectedStockLotId && !candidateList.value.some((item) => item.id === selectedStockLotId)) {
      const stockLot = await RdStockLotApi.get(selectedStockLotId)
      candidateList.value.unshift({
        ...stockLot,
        sameCustomer: stockLot.customerName === selectedToOrder.value?.customerName
      })
    }
  } finally {
    candidateLoading.value = false
  }
}

const open = async (type: 'create' | 'update', id?: number, toOrderId?: number) => {
  resetForm()
  formType.value = type
  dialogTitle.value = type === 'create' ? '新增调拨单' : '编辑调拨单'
  dialogVisible.value = true
  formLoading.value = true
  try {
    await loadOrderOptions()
    if (id) {
      const transferOrder = await RdTransferOrderApi.get(id)
      formData.value = transferOrder
      await loadCandidateList(transferOrder.fromStockLotId)
    } else if (toOrderId) {
      formData.value.toOrderId = toOrderId
      await loadCandidateList()
    }
  } finally {
    formLoading.value = false
  }
}
defineExpose({ open })

const handleToOrderChange = async () => {
  formData.value.fromOrderId = undefined
  formData.value.fromStockLotId = undefined
  formData.value.quantity = undefined
  await loadCandidateList()
}

const handleStockLotChange = () => {
  formData.value.fromOrderId = undefined
  formData.value.quantity = maxTransferQty.value > 0 ? maxTransferQty.value : undefined
}

const formatOrderLabel = (order: RdSaleOrderVO) => {
  return `${order.orderNo} / ${order.customerName} / ${order.productModel} / 差额 ${order.adjustedDifferenceQty}`
}

const formatCandidateLabel = (candidate: RdOrderStockCandidateVO) => {
  const location = candidate.locationCode || '未分配货位'
  return `${candidate.customerName} / ${location} / ${candidate.furnaceNo}-${candidate.furnaceLayer} / 可用 ${candidate.availableQty}`
}

const emit = defineEmits(['success'])
const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    const data = formData.value as RdTransferOrderSaveReqVO
    if (formType.value === 'create') {
      await RdTransferOrderApi.create(data)
      message.success('调拨单新增成功')
    } else {
      await RdTransferOrderApi.update(data)
      message.success('调拨单修改成功')
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
