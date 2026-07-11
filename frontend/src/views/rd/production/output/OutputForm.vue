<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="100px"
      v-loading="formLoading"
    >
      <el-form-item label="产出日期" prop="productionDate">
        <el-date-picker
          v-model="formData.productionDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择产出日期"
          class="!w-1/1"
        />
      </el-form-item>
      <el-form-item label="货位" prop="locationCode">
        <el-input v-model="formData.locationCode" placeholder="请输入货位" />
      </el-form-item>
      <RdCustomerProductFields
        v-model:customer-name="formData.customerName"
        v-model:product-model="formData.productModel"
      />
      <el-form-item label="生产炉号" prop="furnaceNo">
        <el-input v-model="formData.furnaceNo" placeholder="请输入生产炉号" />
      </el-form-item>
      <el-form-item label="所在炉层" prop="furnaceLayer">
        <el-input v-model="formData.furnaceLayer" placeholder="请输入所在炉层" />
      </el-form-item>
      <el-form-item label="实际产额" prop="outputQty">
        <el-input-number
          v-model="formData.outputQty"
          :min="0.000001"
          :precision="6"
          class="!w-1/1"
          placeholder="请输入实际产额"
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
      <el-form-item label="来源表格" prop="sourceSheet">
        <el-input v-model="formData.sourceSheet" placeholder="请输入来源表格或来源单号" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" placeholder="请输入备注" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="submitForm" type="primary" :disabled="formLoading">确 定</el-button>
      <el-button @click="dialogVisible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { RdProductionOutputApi, RdProductionOutputVO } from '@/api/rd/production/output'
import RdCustomerProductFields from '@/components/RdCustomerProductFields/index.vue'

defineOptions({ name: 'RdProductionOutputForm' })

const message = useMessage()

const dialogVisible = ref(false)
const dialogTitle = ref('')
const formLoading = ref(false)
const formType = ref('')
const formData = ref<Partial<RdProductionOutputVO>>({})
const formRules = reactive({
  productionDate: [{ required: true, message: '产出日期不能为空', trigger: 'change' }],
  customerName: [{ required: true, message: '客户不能为空', trigger: 'blur' }],
  productModel: [{ required: true, message: '型号不能为空', trigger: 'blur' }],
  furnaceNo: [{ required: true, message: '生产炉号不能为空', trigger: 'blur' }],
  furnaceLayer: [{ required: true, message: '所在炉层不能为空', trigger: 'blur' }],
  outputQty: [{ required: true, message: '实际产额不能为空', trigger: 'blur' }],
  packageWeight: [{ required: true, message: '单包重量不能为空', trigger: 'blur' }]
})
const formRef = ref()

const open = async (type: string, id?: number) => {
  dialogVisible.value = true
  dialogTitle.value = type === 'create' ? '新增生产炉层产量' : '编辑生产炉层产量'
  formType.value = type
  resetForm()
  if (id) {
    formLoading.value = true
    try {
      formData.value = await RdProductionOutputApi.get(id)
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
    const data = {
      ...formData.value,
      status: formData.value.status || 'DRAFT'
    }
    if (formType.value === 'create') {
      await RdProductionOutputApi.create(data)
      message.success('新增成功')
    } else {
      await RdProductionOutputApi.update(data)
      message.success('修改成功')
    }
    dialogVisible.value = false
    emit('success')
  } finally {
    formLoading.value = false
  }
}

const resetForm = () => {
  formData.value = {
    id: undefined,
    productionDate: undefined,
    locationId: undefined,
    locationCode: undefined,
    customerName: undefined,
    productModel: undefined,
    furnaceNo: undefined,
    furnaceLayer: undefined,
    outputQty: undefined,
    packageWeight: undefined,
    inboundQty: 0,
    status: 'DRAFT',
    sourceSheet: undefined,
    remark: undefined
  }
  formRef.value?.resetFields()
}
</script>
