<template>
  <Dialog v-model="visible" title="新建库存盘点单" width="620px">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="盘点货位" prop="locationId">
        <el-select
          v-model="formData.locationId"
          filterable
          class="!w-100%"
          placeholder="请选择盘点货位"
        >
          <el-option
            v-for="item in locationOptions"
            :key="item.id"
            :label="`${item.locationCode} | ${item.warehouseName}${item.areaName ? ` / ${item.areaName}` : ''}`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="盘点日期" prop="stocktakeDate">
        <el-date-picker
          v-model="formData.stocktakeDate"
          type="date"
          value-format="YYYY-MM-DD"
          class="!w-100%"
        />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="4" maxlength="255" show-word-limit />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="submitting" @click="submit">创建盘点单</el-button>
      <el-button @click="visible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { nextTick, reactive, ref } from 'vue'
import { formatDate } from '@/utils/formatTime'
import { RdWarehouseLocationApi, type RdWarehouseLocationVO } from '@/api/rd/masterdata/location'
import { RdStocktakeApi, type RdStocktakeCreateReqVO } from '@/api/rd/stock/stocktake'

defineOptions({ name: 'RdStocktakeCreateDialog' })

const emit = defineEmits<{
  success: []
}>()

const message = useMessage()
const visible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const locationOptions = ref<RdWarehouseLocationVO[]>([])
const formData = reactive<RdStocktakeCreateReqVO>({
  locationId: undefined,
  stocktakeDate: formatDate(new Date(), 'YYYY-MM-DD'),
  remark: ''
})
const rules = reactive<FormRules>({
  locationId: [{ required: true, message: '请选择盘点货位', trigger: 'change' }],
  stocktakeDate: [{ required: true, message: '请选择盘点日期', trigger: 'change' }]
})

const open = async () => {
  const locations = await RdWarehouseLocationApi.getOptions()
  locationOptions.value = locations.filter((item) => item.enabled && !item.frozen)
  formData.locationId = undefined
  formData.stocktakeDate = formatDate(new Date(), 'YYYY-MM-DD')
  formData.remark = ''
  visible.value = true
  nextTick(() => formRef.value?.clearValidate())
}

const submit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await RdStocktakeApi.create(formData)
    message.success('盘点单已创建')
    visible.value = false
    emit('success')
  } finally {
    submitting.value = false
  }
}

defineExpose({ open })
</script>
