<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible" width="680px">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      v-loading="formLoading"
    >
      <el-form-item label="订单号" prop="orderNo">
        <el-input v-model="formData.orderNo" placeholder="请输入订单号" />
      </el-form-item>
      <el-form-item label="订单日期" prop="orderDate">
        <el-date-picker
          v-model="formData.orderDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择订单日期"
          class="!w-1/1"
        />
      </el-form-item>
      <el-form-item label="客户" prop="customerName">
        <el-input v-model="formData.customerName" placeholder="请输入客户" />
      </el-form-item>
      <el-form-item label="型号" prop="productModel">
        <el-input v-model="formData.productModel" placeholder="请输入型号" />
      </el-form-item>
      <el-form-item label="订单量" prop="requiredQty">
        <el-input-number
          v-model="formData.requiredQty"
          :min="0.000001"
          :precision="6"
          class="!w-1/1"
          placeholder="请输入订单量"
        />
      </el-form-item>
      <el-form-item label="实际产额" prop="actualOutputQty">
        <el-input-number
          v-model="formData.actualOutputQty"
          :min="0"
          :precision="6"
          class="!w-1/1"
          placeholder="请输入实际产额"
        />
      </el-form-item>
      <el-form-item label="当前差额">
        <el-input :model-value="differencePreview" disabled />
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
import { formatDate } from '@/utils/formatTime'
import { RdSaleOrderApi } from '@/api/rd/order/sale'
import type { RdSaleOrderVO } from '@/api/rd/order/sale'

defineOptions({ name: 'RdSaleOrderForm' })

const message = useMessage()
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref<'create' | 'update'>('create')
const formRef = ref()
const formData = ref<Partial<RdSaleOrderVO>>({})
const formRules = reactive({
  orderNo: [{ required: true, message: '订单号不能为空', trigger: 'blur' }],
  orderDate: [{ required: true, message: '订单日期不能为空', trigger: 'change' }],
  customerName: [{ required: true, message: '客户不能为空', trigger: 'blur' }],
  productModel: [{ required: true, message: '型号不能为空', trigger: 'blur' }],
  requiredQty: [{ required: true, message: '订单量不能为空', trigger: 'blur' }],
  actualOutputQty: [{ required: true, message: '实际产额不能为空', trigger: 'blur' }]
})
const differencePreview = computed(() => {
  const requiredQty = Number(formData.value.requiredQty || 0)
  const actualOutputQty = Number(formData.value.actualOutputQty || 0)
  return (requiredQty - actualOutputQty).toFixed(6)
})

const resetForm = () => {
  formData.value = {
    id: undefined,
    orderNo: undefined,
    orderDate: formatDate(new Date(), 'YYYY-MM-DD'),
    customerName: undefined,
    productModel: undefined,
    requiredQty: undefined,
    actualOutputQty: 0,
    remark: undefined
  }
  formRef.value?.resetFields()
}

const open = async (type: 'create' | 'update', id?: number) => {
  resetForm()
  formType.value = type
  dialogTitle.value = type === 'create' ? '新增销售订单' : '编辑销售订单'
  dialogVisible.value = true
  if (id) {
    formLoading.value = true
    try {
      formData.value = await RdSaleOrderApi.get(id)
    } finally {
      formLoading.value = false
    }
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submitForm = async () => {
  await formRef.value.validate()
  formLoading.value = true
  try {
    if (formType.value === 'create') {
      await RdSaleOrderApi.create(formData.value)
      message.success('销售订单新增成功')
    } else {
      await RdSaleOrderApi.update(formData.value)
      message.success('销售订单修改成功')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}
</script>
