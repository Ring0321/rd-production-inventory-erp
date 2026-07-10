<template>
  <Dialog title="库存调整" v-model="dialogVisible" width="680px">
    <el-descriptions v-if="currentLot" :column="2" border class="mb-16px">
      <el-descriptions-item label="货位">{{ currentLot.locationCode || '-' }}</el-descriptions-item>
      <el-descriptions-item label="客户">{{ currentLot.customerName }}</el-descriptions-item>
      <el-descriptions-item label="型号">{{ currentLot.productModel }}</el-descriptions-item>
      <el-descriptions-item label="生产炉号">{{ currentLot.furnaceNo }}</el-descriptions-item>
      <el-descriptions-item label="所在炉层">{{ currentLot.furnaceLayer }}</el-descriptions-item>
      <el-descriptions-item label="当前库房数量">{{ currentLot.warehouseQty }}</el-descriptions-item>
    </el-descriptions>

    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      v-loading="formLoading"
    >
      <el-form-item label="业务日期" prop="bizDate">
        <el-date-picker
          v-model="formData.bizDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择业务日期"
          class="!w-1/1"
        />
      </el-form-item>
      <el-form-item label="调整类型" prop="adjustmentType">
        <el-segmented v-model="formData.adjustmentType" :options="adjustmentTypeOptions" block />
      </el-form-item>
      <el-form-item label="调整数量" prop="quantity">
        <el-input-number
          v-model="formData.quantity"
          :min="0.000001"
          :max="maxQuantity"
          :precision="6"
          class="!w-1/1"
          placeholder="请输入调整数量"
        />
      </el-form-item>
      <el-form-item label="调整后数量">
        <el-input :model-value="previewQuantity" disabled />
      </el-form-item>
      <el-form-item label="调整原因" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
          placeholder="请输入调整原因"
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
import { RdStockLotApi } from '@/api/rd/stock/lot'
import type { RdStockAdjustmentCreateReqVO, RdStockLotVO } from '@/api/rd/stock/lot'

defineOptions({ name: 'RdStockAdjustmentForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const currentLot = ref<RdStockLotVO>()
const formData = ref<Partial<RdStockAdjustmentCreateReqVO>>({})
const adjustmentTypeOptions = [
  { label: '盘盈调增', value: 'ADJUST_IN' },
  { label: '盘亏调减', value: 'ADJUST_OUT' }
]
const formRules = reactive({
  bizDate: [{ required: true, message: '业务日期不能为空', trigger: 'change' }],
  adjustmentType: [{ required: true, message: '调整类型不能为空', trigger: 'change' }],
  quantity: [{ required: true, message: '调整数量不能为空', trigger: 'blur' }],
  remark: [{ required: true, message: '调整原因不能为空', trigger: 'blur' }]
})
const currentQuantity = computed(() => Number(currentLot.value?.warehouseQty || 0))
const maxQuantity = computed(() => {
  return formData.value.adjustmentType === 'ADJUST_OUT' ? currentQuantity.value : undefined
})
const previewQuantity = computed(() => {
  const quantity = Number(formData.value.quantity || 0)
  const value =
    formData.value.adjustmentType === 'ADJUST_OUT'
      ? currentQuantity.value - quantity
      : currentQuantity.value + quantity
  return value.toFixed(6)
})

const resetForm = (stockLot: RdStockLotVO) => {
  formData.value = {
    stockLotId: stockLot.id,
    bizDate: formatDate(new Date(), 'YYYY-MM-DD'),
    adjustmentType: 'ADJUST_IN',
    quantity: undefined,
    remark: undefined
  }
  formRef.value?.resetFields()
}

const open = (stockLot: RdStockLotVO) => {
  currentLot.value = stockLot
  resetForm(stockLot)
  dialogVisible.value = true
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  await formRef.value.validate()
  const quantity = Number(formData.value.quantity || 0)
  if (formData.value.adjustmentType === 'ADJUST_OUT' && quantity > currentQuantity.value) {
    message.error('调减数量不能超过当前库房数量')
    return
  }
  formLoading.value = true
  try {
    await RdStockLotApi.createAdjustment(formData.value as RdStockAdjustmentCreateReqVO)
    message.success('库存调整成功')
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
