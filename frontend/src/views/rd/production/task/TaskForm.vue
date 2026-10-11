<template>
  <el-dialog
    v-model="visible"
    :title="target ? '编辑生产任务' : '新建生产任务'"
    width="min(560px, calc(100vw - 24px))"
    :close-on-click-modal="false"
    :close-on-press-escape="!saving"
    :before-close="closeForm"
    destroy-on-close
  >
    <el-skeleton v-if="loading" :rows="4" animated />
    <template v-else>
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        :disabled="saving || !editable"
        label-position="top"
        class="task-form"
        @submit.prevent="save"
      >
        <el-form-item label="客户" prop="customerName">
          <el-select
            v-model="form.customerName"
            filterable
            remote
            allow-create
            default-first-option
            :remote-method="loadCustomers"
            :loading="customerLoading"
            placeholder="选择或输入客户"
            @visible-change="(open) => open && loadCustomers()"
          >
            <el-option v-for="name in customers" :key="name" :label="name" :value="name" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务生产日期" prop="productionDate">
          <el-date-picker v-model="form.productionDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="班次" prop="shiftCode">
          <el-input v-model="form.shiftCode" maxlength="32" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </template>
    <template #footer>
      <el-button :disabled="saving" @click="closeForm()">取消</el-button>
      <el-button v-if="editable" type="primary" :loading="saving" :disabled="loading" @click="save">
        <Icon icon="lucide:save" class="mr-5px" />保存
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { nextTick, reactive, ref } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useMessage } from '@/hooks/web/useMessage'
import { RdCustomerProductApi } from '@/api/rd/masterdata/customer-product'
import { RdProductionOutputApi, type RdProductionOutputVO } from '@/api/rd/production/output'
import {
  RdProductionTaskApi,
  type RdProductionTaskVO,
  type RdProductionTaskSaveReqVO
} from '@/api/rd/production/task'

defineOptions({ name: 'RdProductionTaskForm' })
const emit = defineEmits<{ saved: [id: number] }>()
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const saving = ref(false)
const editable = ref(false)
const error = ref('')
const target = ref<RdProductionTaskVO>()
const formRef = ref<FormInstance>()
const form = reactive<RdProductionTaskSaveReqVO>({
  customerName: '',
  productionDate: '',
  shiftCode: '',
  remark: ''
})
const rules: FormRules = {
  customerName: [
    { required: true, whitespace: true, message: '请填写客户', trigger: 'change' },
    { max: 128, message: '客户名称不能超过 128 字', trigger: 'change' }
  ],
  productionDate: [{ required: true, message: '请选择业务生产日期', trigger: 'change' }],
  shiftCode: [{ required: true, whitespace: true, message: '请填写班次', trigger: 'blur' }]
}
const customers = ref<string[]>([])
const customerLoading = ref(false)
let customerRequest = 0
let openRequest = 0

const loadCustomers = async (keyword = '') => {
  const request = ++customerRequest
  customerLoading.value = true
  try {
    const options = await RdCustomerProductApi.getCustomerOptions({ keyword, limit: 100 })
    if (request === customerRequest) customers.value = options
  } catch {
    if (request === customerRequest) message.error('客户选项加载失败')
  } finally {
    if (request === customerRequest) customerLoading.value = false
  }
}

const open = async (id?: number) => {
  if (
    saving.value ||
    !hasPermission([id ? 'rd:production-task:update' : 'rd:production-task:create'])
  )
    return
  const request = ++openRequest
  target.value = undefined
  error.value = ''
  editable.value = false
  loading.value = true
  visible.value = true
  Object.assign(form, { customerName: '', productionDate: '', shiftCode: '', remark: '' })
  try {
    if (id) {
      const task = await RdProductionTaskApi.get(id)
      if (request !== openRequest) return
      target.value = task
      Object.assign(form, {
        customerName: task.customerName,
        productionDate: task.productionDate,
        shiftCode: task.shiftCode,
        remark: task.remark || ''
      })
      if (task.status !== 'ACTIVE') {
        error.value = '任务已停止登记，不能修改。'
        return
      }
      if (hasPermission(['rd:production-output:query'])) {
        const data: PageResult<RdProductionOutputVO[]> = await RdProductionOutputApi.getPage({
          pageNo: 1,
          pageSize: -1,
          productionTaskId: id
        })
        if (request !== openRequest) return
        if (data.list.some((output) => output.status !== 'CANCELLED')) {
          error.value = '任务已有有效产出，不能修改。'
          return
        }
      }
    }
    editable.value = true
    await nextTick()
    formRef.value?.clearValidate()
  } catch {
    if (request === openRequest) error.value = '任务信息加载失败，请关闭后重试。'
  } finally {
    if (request === openRequest) loading.value = false
  }
}

const closeForm = (done?: () => void) => {
  if (saving.value) return
  openRequest++
  visible.value = false
  done?.()
}

const save = async () => {
  if (
    !editable.value ||
    saving.value ||
    !hasPermission([target.value ? 'rd:production-task:update' : 'rd:production-task:create'])
  ) {
    return
  }
  saving.value = true
  error.value = ''
  try {
    if (!(await formRef.value?.validate().catch(() => false))) return
    const data = {
      customerName: form.customerName.trim(),
      productionDate: form.productionDate,
      shiftCode: form.shiftCode.trim(),
      remark: form.remark?.trim() || undefined
    }
    let id: number
    if (target.value) {
      await RdProductionTaskApi.update({
        ...data,
        id: target.value.id,
        version: target.value.version
      })
      id = target.value.id
    } else {
      id = await RdProductionTaskApi.create(data)
    }
    message.success(target.value ? '任务已更新' : '任务已创建')
    visible.value = false
    emit('saved', id)
  } catch {
    error.value = '保存未完成，请核对任务列表与服务器返回结果。'
  } finally {
    saving.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.task-form {
  margin-top: 16px;
}

.task-form :deep(.el-select),
.task-form :deep(.el-date-editor) {
  width: 100%;
}
</style>
