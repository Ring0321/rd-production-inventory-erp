<template>
  <Dialog :title="dialogTitle" v-model="visible" width="min(980px, calc(100vw - 24px))">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="104px" v-loading="loading">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-form-item label="规则编码" prop="ruleCode">
            <el-input v-model="formData.ruleCode" maxlength="64" placeholder="请输入规则编码" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model="formData.ruleName" maxlength="128" placeholder="请输入规则名称" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <RdCustomerProductFields
            v-model:customer-name="formData.customerName"
            v-model:product-model="formData.productModel"
            customer-prop="customerName"
            model-prop="productModel"
          />
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="是否启用" prop="enabled">
            <el-switch v-model="formData.enabled" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-form-item label="默认投炉量" prop="defaultFurnaceLoadQty">
            <el-input-number
              v-model="formData.defaultFurnaceLoadQty"
              :min="0.000001"
              :precision="6"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="成品率(%)" prop="yieldRate">
            <el-input-number
              v-model="formData.yieldRate"
              :min="0.000001"
              :max="100"
              :precision="6"
              class="!w-1/1"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <div class="line-header">
        <span>炉层分配</span>
        <el-button type="primary" plain @click="addLine">
          <Icon icon="ep:plus" class="mr-5px" /> 添加炉层
        </el-button>
      </div>
      <div class="table-scroll">
        <el-table :data="formData.lines" border>
          <el-table-column label="顺序" width="74" align="center">
            <template #default="scope">{{ scope.$index + 1 }}</template>
          </el-table-column>
          <el-table-column label="炉层" min-width="150">
            <template #default="scope">
              <el-input v-model="scope.row.furnaceLayer" maxlength="64" placeholder="请输入炉层" />
            </template>
          </el-table-column>
          <el-table-column label="分配方式" min-width="150">
            <template #default="scope">
              <el-select v-model="scope.row.allocationType" class="!w-1/1">
                <el-option label="固定数量" value="FIXED_QTY" />
                <el-option label="比例权重" value="RATIO" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="分配值" min-width="170">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.allocationValue"
                :min="0.000001"
                :precision="6"
                class="!w-1/1"
              />
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="180">
            <template #default="scope">
              <el-input v-model="scope.row.remark" maxlength="255" placeholder="可选" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right" align="center">
            <template #default="scope">
              <el-button
                link
                type="danger"
                :disabled="(formData.lines?.length || 0) === 1"
                @click="removeLine(scope.$index)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-form-item label="备注" prop="remark" class="mt-16px">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="2"
          maxlength="512"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="loading" @click="submit">确 定</el-button>
      <el-button @click="visible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { RdOrderSplitApi } from '@/api/rd/order/split'
import type { RdOrderSplitRuleLineVO, RdOrderSplitRuleVO } from '@/api/rd/order/split'
import RdCustomerProductFields from '@/components/RdCustomerProductFields/index.vue'

defineOptions({ name: 'RdOrderSplitRuleDialog' })

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const formRef = ref()
const dialogTitle = ref('')
const mode = ref<'create' | 'update'>('create')
const emptyLine = (): RdOrderSplitRuleLineVO => ({
  sequence: 1,
  furnaceLayer: '',
  allocationType: 'RATIO',
  allocationValue: 1
})
const formData = ref<Partial<RdOrderSplitRuleVO>>({ lines: [emptyLine()] })
const rules = reactive({
  ruleCode: [{ required: true, message: '规则编码不能为空', trigger: 'blur' }],
  ruleName: [{ required: true, message: '规则名称不能为空', trigger: 'blur' }],
  customerName: [{ required: true, message: '客户不能为空', trigger: 'change' }],
  productModel: [{ required: true, message: '型号不能为空', trigger: 'change' }],
  defaultFurnaceLoadQty: [{ required: true, message: '默认投炉量不能为空', trigger: 'blur' }],
  yieldRate: [{ required: true, message: '成品率不能为空', trigger: 'blur' }]
})

const reset = () => {
  formData.value = {
    ruleCode: '',
    ruleName: '',
    customerName: undefined,
    productModel: undefined,
    defaultFurnaceLoadQty: undefined,
    yieldRate: undefined,
    enabled: true,
    remark: undefined,
    lines: [emptyLine()]
  }
  formRef.value?.resetFields()
}

const open = async (type: 'create' | 'update', id?: number) => {
  reset()
  mode.value = type
  dialogTitle.value = type === 'create' ? '新增拆分规则' : '编辑拆分规则'
  visible.value = true
  if (!id) return
  loading.value = true
  try {
    formData.value = await RdOrderSplitApi.getRule(id)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

const addLine = () => {
  formData.value.lines!.push({ ...emptyLine(), sequence: formData.value.lines!.length + 1 })
}
const removeLine = (index: number) => formData.value.lines!.splice(index, 1)

const emit = defineEmits(['success'])
const submit = async () => {
  await formRef.value.validate()
  const lines = formData.value.lines || []
  if (
    !lines.length ||
    lines.some((line) => !line.furnaceLayer || Number(line.allocationValue) <= 0)
  ) {
    message.warning('请完整填写炉层、分配方式和大于 0 的分配值')
    return
  }
  const duplicate = lines.some(
    (line, index) => lines.findIndex((item) => item.furnaceLayer === line.furnaceLayer) !== index
  )
  if (duplicate) {
    message.warning('同一规则不能配置重复炉层')
    return
  }
  loading.value = true
  try {
    const data = {
      ...formData.value,
      lines: lines.map((line, index) => ({ ...line, sequence: index + 1 }))
    }
    if (mode.value === 'create') await RdOrderSplitApi.createRule(data)
    else await RdOrderSplitApi.updateRule(data)
    message.success(mode.value === 'create' ? '拆分规则新增成功' : '拆分规则修改成功')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.line-header {
  display: flex;
  min-height: 32px;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
}
.table-scroll {
  max-width: 100%;
  overflow-x: auto;
}
.table-scroll :deep(.el-table) {
  min-width: 760px;
}
</style>
