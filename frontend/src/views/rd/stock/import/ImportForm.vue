<template>
  <Dialog v-model="dialogVisible" title="库存 Excel 预检" width="520px">
    <el-form ref="formRef" :model="formData" :rules="formRules" label-width="96px">
      <el-form-item label="基准日期" prop="bizDate">
        <el-date-picker
          v-model="formData.bizDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择库存基准日期"
          class="!w-100%"
        />
      </el-form-item>
      <el-form-item label="工作表" prop="sheetName">
        <el-input v-model="formData.sheetName" maxlength="128" />
      </el-form-item>
      <el-form-item label="Excel 文件" required>
        <el-upload
          ref="uploadRef"
          v-model:file-list="fileList"
          action="#"
          :auto-upload="false"
          :disabled="formLoading"
          :limit="1"
          :on-exceed="handleExceed"
          accept=".xlsx"
          drag
          class="inventory-upload"
        >
          <Icon icon="ep:upload-filled" :size="28" />
          <div class="el-upload__text">拖入文件或点击选择</div>
          <template #tip>
            <div class="el-upload__tip">xlsx，最大 20 MB</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">
        <Icon icon="ep:circle-check" class="mr-5px" />预检
      </el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules, UploadUserFile } from 'element-plus'
import { formatDate } from '@/utils/formatTime'
import { RdExcelImportApi } from '@/api/rd/stock/excel-import'

defineOptions({ name: 'RdStockImportForm' })

const message = useMessage()
const dialogVisible = ref(false)
const formLoading = ref(false)
const formRef = ref<FormInstance>()
const uploadRef = ref()
const fileList = ref<UploadUserFile[]>([])
const formData = reactive({
  bizDate: '',
  sheetName: '货位统计明细表'
})
const formRules = reactive<FormRules>({
  bizDate: [{ required: true, message: '请选择库存基准日期', trigger: 'change' }],
  sheetName: [{ required: true, message: '请输入工作表名称', trigger: 'blur' }]
})

const emits = defineEmits<{
  success: [taskId: number]
}>()

const open = async () => {
  dialogVisible.value = true
  formData.bizDate = formatDate(new Date(), 'YYYY-MM-DD')
  formData.sheetName = '货位统计明细表'
  fileList.value = []
  await nextTick()
  uploadRef.value?.clearFiles()
  formRef.value?.clearValidate()
}
defineExpose({ open })

const submitForm = async () => {
  await formRef.value?.validate()
  const file = fileList.value[0]?.raw
  if (!file) {
    message.warning('请选择 xlsx 文件')
    return
  }
  if (!file.name.toLowerCase().endsWith('.xlsx') || file.size > 20 * 1024 * 1024) {
    message.error('文件必须是 20 MB 以内的 xlsx')
    return
  }
  formLoading.value = true
  try {
    const data = new FormData()
    data.append('file', file)
    data.append('bizDate', formData.bizDate)
    data.append('sheetName', formData.sheetName)
    const taskId = await RdExcelImportApi.precheck(data)
    message.success('预检完成')
    dialogVisible.value = false
    emits('success', taskId)
  } finally {
    formLoading.value = false
  }
}

const handleExceed = () => {
  message.warning('每次只能预检一个文件')
}
</script>

<style scoped>
.inventory-upload {
  width: 100%;
}

.inventory-upload :deep(.el-upload),
.inventory-upload :deep(.el-upload-dragger) {
  width: 100%;
}
</style>
