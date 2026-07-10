<template>
  <Dialog title="手动匹配库存" v-model="dialogVisible" width="680px">
    <el-descriptions v-if="saleOrder && stockLot" :column="2" border class="mb-16px">
      <el-descriptions-item label="订单号">{{ saleOrder.orderNo }}</el-descriptions-item>
      <el-descriptions-item label="订单剩余差额">
        {{ saleOrder.adjustedDifferenceQty }}
      </el-descriptions-item>
      <el-descriptions-item label="货位">{{ stockLot.locationCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="库存可用数量">{{ stockLot.availableQty }}</el-descriptions-item>
      <el-descriptions-item label="生产炉号">{{ stockLot.furnaceNo }}</el-descriptions-item>
      <el-descriptions-item label="所在炉层">{{ stockLot.furnaceLayer }}</el-descriptions-item>
    </el-descriptions>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      v-loading="formLoading"
    >
      <el-form-item label="匹配数量" prop="quantity">
        <el-input-number
          v-model="formData.quantity"
          :min="0.000001"
          :max="maxQuantity"
          :precision="6"
          class="!w-1/1"
          placeholder="请输入匹配数量"
        />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
          placeholder="请输入备注"
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
import { RdOrderFulfillmentApi } from '@/api/rd/order/fulfillment'
import type {
  RdOrderFulfillmentAllocateReqVO,
  RdOrderStockCandidateVO
} from '@/api/rd/order/fulfillment'
import type { RdSaleOrderVO } from '@/api/rd/order/sale'

defineOptions({ name: 'RdOrderAllocationForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const saleOrder = ref<RdSaleOrderVO>()
const stockLot = ref<RdOrderStockCandidateVO>()
const formData = ref<Partial<RdOrderFulfillmentAllocateReqVO>>({})
const formRules = reactive({
  quantity: [{ required: true, message: '匹配数量不能为空', trigger: 'blur' }]
})
const maxQuantity = computed(() => {
  const orderDifference = Number(saleOrder.value?.adjustedDifferenceQty || 0)
  const stockAvailable = Number(stockLot.value?.availableQty || 0)
  return Math.min(orderDifference, stockAvailable)
})

const open = (order: RdSaleOrderVO, candidate: RdOrderStockCandidateVO) => {
  saleOrder.value = order
  stockLot.value = candidate
  formData.value = {
    orderId: order.id,
    stockLotId: candidate.id,
    quantity: maxQuantity.value,
    remark: undefined
  }
  formRef.value?.resetFields()
  dialogVisible.value = true
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    await RdOrderFulfillmentApi.allocate(formData.value as RdOrderFulfillmentAllocateReqVO)
    message.success('库存匹配成功')
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
