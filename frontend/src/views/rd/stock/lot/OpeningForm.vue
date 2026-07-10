<template>
  <Dialog title="新增期初库存" v-model="dialogVisible" width="680px">
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
      <el-form-item label="货位" prop="locationCode">
        <el-input v-model="formData.locationCode" placeholder="请输入货位" />
      </el-form-item>
      <el-form-item label="客户" prop="customerName">
        <el-input v-model="formData.customerName" placeholder="请输入客户" />
      </el-form-item>
      <el-form-item label="型号" prop="productModel">
        <el-input v-model="formData.productModel" placeholder="请输入型号" />
      </el-form-item>
      <el-form-item label="生产炉号" prop="furnaceNo">
        <el-input v-model="formData.furnaceNo" placeholder="请输入生产炉号" />
      </el-form-item>
      <el-form-item label="所在炉层" prop="furnaceLayer">
        <el-input v-model="formData.furnaceLayer" placeholder="请输入所在炉层" />
      </el-form-item>
      <el-form-item label="期初数量" prop="quantity">
        <el-input-number
          v-model="formData.quantity"
          :min="0.000001"
          :precision="6"
          class="!w-1/1"
          placeholder="请输入期初数量"
        />
      </el-form-item>
      <el-form-item label="单包重量" prop="packageWeight">
        <el-input-number
          v-model="formData.packageWeight"
          :min="0.000001"
          :precision="6"
          class="!w-1/1"
          placeholder="请输入单包重量"
        />
      </el-form-item>
      <el-form-item label="库存重量">
        <el-input :model-value="stockWeight" disabled />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="3" placeholder="请输入备注" />
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
import type { RdStockOpeningCreateReqVO } from '@/api/rd/stock/lot'

defineOptions({ name: 'RdStockOpeningForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref()
const formData = ref<Partial<RdStockOpeningCreateReqVO>>({})
const formRules = reactive({
  bizDate: [{ required: true, message: '业务日期不能为空', trigger: 'change' }],
  locationCode: [{ required: true, message: '货位不能为空', trigger: 'blur' }],
  customerName: [{ required: true, message: '客户不能为空', trigger: 'blur' }],
  productModel: [{ required: true, message: '型号不能为空', trigger: 'blur' }],
  furnaceNo: [{ required: true, message: '生产炉号不能为空', trigger: 'blur' }],
  furnaceLayer: [{ required: true, message: '所在炉层不能为空', trigger: 'blur' }],
  quantity: [{ required: true, message: '期初数量不能为空', trigger: 'blur' }],
  packageWeight: [{ required: true, message: '单包重量不能为空', trigger: 'blur' }]
})
const stockWeight = computed(() => {
  const quantity = Number(formData.value.quantity || 0)
  const packageWeight = Number(formData.value.packageWeight || 0)
  return (quantity * packageWeight).toFixed(6)
})

const resetForm = () => {
  formData.value = {
    bizDate: formatDate(new Date(), 'YYYY-MM-DD'),
    locationId: undefined,
    locationCode: undefined,
    customerName: undefined,
    productModel: undefined,
    furnaceNo: undefined,
    furnaceLayer: undefined,
    quantity: undefined,
    packageWeight: undefined,
    remark: undefined
  }
  formRef.value?.resetFields()
}

const open = () => {
  resetForm()
  dialogVisible.value = true
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    await RdStockLotApi.createOpening(formData.value as RdStockOpeningCreateReqVO)
    message.success('期初库存新增成功')
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
