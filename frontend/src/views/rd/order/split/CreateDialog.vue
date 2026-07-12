<template>
  <Dialog title="创建订单拆分" v-model="visible" width="min(760px, calc(100vw - 24px))">
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="112px" v-loading="loading">
      <el-form-item label="拆分日期" prop="splitDate">
        <el-date-picker
          v-model="formData.splitDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="请选择拆分日期"
          class="!w-1/1"
        />
      </el-form-item>
      <el-form-item label="销售订单" prop="saleOrderId">
        <el-select
          v-model="formData.saleOrderId"
          filterable
          class="!w-1/1"
          placeholder="请选择未关闭销售订单"
          @change="handleOrderChange"
        >
          <el-option
            v-for="order in orderOptions"
            :key="order.id"
            :label="formatOrder(order)"
            :value="order.id"
          />
        </el-select>
      </el-form-item>
      <el-descriptions v-if="selectedOrder" :column="3" border class="mb-18px">
        <el-descriptions-item label="客户">{{ selectedOrder.customerName }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ selectedOrder.productModel }}</el-descriptions-item>
        <el-descriptions-item label="订单差额">{{
          selectedOrder.adjustedDifferenceQty
        }}</el-descriptions-item>
      </el-descriptions>
      <el-form-item label="拆分规则" prop="ruleId">
        <el-select
          v-model="formData.ruleId"
          filterable
          class="!w-1/1"
          placeholder="请选择匹配且已启用的规则"
          :disabled="!selectedOrder"
          @change="handleRuleChange"
        >
          <el-option
            v-for="rule in ruleOptions"
            :key="rule.id"
            :label="`${rule.ruleCode} / ${rule.ruleName}`"
            :value="rule.id"
          />
        </el-select>
      </el-form-item>
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-form-item label="投炉量" prop="furnaceLoadQty">
            <el-input-number
              v-model="formData.furnaceLoadQty"
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
      <el-alert type="info" :closable="false" show-icon>
        创建后仅生成拆分计划，不改变库存或销售订单实际产额；生产实绩需在确认计划后另行绑定。
      </el-alert>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="loading" @click="submit">创 建</el-button>
      <el-button @click="visible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { formatDate } from '@/utils/formatTime'
import { RdSaleOrderApi } from '@/api/rd/order/sale'
import type { RdSaleOrderVO } from '@/api/rd/order/sale'
import { RdOrderSplitApi } from '@/api/rd/order/split'
import type { RdOrderSplitCreateReqVO, RdOrderSplitRuleVO } from '@/api/rd/order/split'

defineOptions({ name: 'RdOrderSplitCreateDialog' })

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const formRef = ref()
const orderOptions = ref<RdSaleOrderVO[]>([])
const ruleOptions = ref<RdOrderSplitRuleVO[]>([])
const formData = ref<Partial<RdOrderSplitCreateReqVO>>({})
const selectedOrder = computed(() =>
  orderOptions.value.find((item) => item.id === formData.value.saleOrderId)
)
const rules = reactive({
  splitDate: [{ required: true, message: '拆分日期不能为空', trigger: 'change' }],
  saleOrderId: [{ required: true, message: '销售订单不能为空', trigger: 'change' }],
  ruleId: [{ required: true, message: '拆分规则不能为空', trigger: 'change' }],
  furnaceLoadQty: [{ required: true, message: '投炉量不能为空', trigger: 'blur' }],
  yieldRate: [{ required: true, message: '成品率不能为空', trigger: 'blur' }]
})

const loadOrders = async (orderId?: number) => {
  const data = await RdSaleOrderApi.getPage({ pageNo: 1, pageSize: 200 })
  orderOptions.value = data.list.filter(
    (item: RdSaleOrderVO) => !['CLOSED', 'CANCELLED'].includes(item.status)
  )
  if (orderId && !orderOptions.value.some((item) => item.id === orderId)) {
    const order = await RdSaleOrderApi.get(orderId)
    if (!['CLOSED', 'CANCELLED'].includes(order.status)) orderOptions.value.unshift(order)
  }
}

const loadRules = async () => {
  const order = selectedOrder.value
  ruleOptions.value = []
  if (!order) return
  const data = await RdOrderSplitApi.getRulePage({
    pageNo: 1,
    pageSize: 200,
    customerName: order.customerName,
    productModel: order.productModel,
    enabled: true
  })
  ruleOptions.value = data.list
}

const open = async (orderId?: number) => {
  formData.value = { splitDate: formatDate(new Date(), 'YYYY-MM-DD'), saleOrderId: orderId }
  ruleOptions.value = []
  visible.value = true
  loading.value = true
  try {
    await loadOrders(orderId)
    if (formData.value.saleOrderId) await loadRules()
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

const handleOrderChange = async () => {
  formData.value.ruleId = undefined
  formData.value.furnaceLoadQty = undefined
  formData.value.yieldRate = undefined
  await loadRules()
}

const handleRuleChange = () => {
  const rule = ruleOptions.value.find((item) => item.id === formData.value.ruleId)
  formData.value.furnaceLoadQty = rule?.defaultFurnaceLoadQty
  formData.value.yieldRate = rule?.yieldRate
}

const formatOrder = (order: RdSaleOrderVO) =>
  `${order.orderNo} / ${order.customerName} / ${order.productModel}`
const emit = defineEmits(['success'])
const submit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    await RdOrderSplitApi.create(formData.value as RdOrderSplitCreateReqVO)
    message.success('订单拆分计划创建成功')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>
