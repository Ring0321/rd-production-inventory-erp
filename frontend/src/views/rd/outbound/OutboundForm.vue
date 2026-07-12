<template>
  <Dialog :title="dialogTitle" v-model="dialogVisible" width="min(760px, calc(100vw - 24px))">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="110px"
      v-loading="loading"
    >
      <el-row :gutter="16">
        <el-col :xs="24" :md="12">
          <el-form-item label="出库单号" prop="outboundNo">
            <el-input v-model="formData.outboundNo" />
          </el-form-item>
        </el-col>
        <el-col :xs="24" :md="12">
          <el-form-item label="出库日期" prop="outboundDate">
            <el-date-picker
              v-model="formData.outboundDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-100%"
            />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="销售订单" prop="orderId">
        <el-select v-model="formData.orderId" filterable class="!w-100%" @change="onOrderChange">
          <el-option
            v-for="item in orders"
            :key="item.id"
            :value="item.id"
            :label="`${item.orderNo} / ${item.customerName} / ${item.productModel}`"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="分配批次" prop="fulfillmentId">
        <el-select
          v-model="formData.fulfillmentId"
          filterable
          class="!w-100%"
          :disabled="!formData.orderId"
        >
          <el-option
            v-for="item in fulfillments"
            :key="item.id"
            :value="item.id"
            :label="`${item.locationCode || '-'} / ${item.furnaceNo}-${item.furnaceLayer} / 待出 ${remaining(item)}`"
          />
        </el-select>
      </el-form-item>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="出库数量和重量由实际扫描包码自动累计。"
        class="mb-16px"
      />
      <el-form-item label="备注">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="loading" @click="submit">确定</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import { RdSaleOrderApi, type RdSaleOrderVO } from '@/api/rd/order/sale'
import { RdOrderFulfillmentApi, type RdOrderFulfillmentVO } from '@/api/rd/order/fulfillment'
import { RdOutboundOrderApi, type RdOutboundOrderSaveReqVO } from '@/api/rd/outbound'

defineOptions({ name: 'RdOutboundOrderForm' })
const message = useMessage()
const dialogVisible = ref(false)
const dialogTitle = ref('新建扫码出库单')
const loading = ref(false)
const formRef = ref()
const formType = ref<'create' | 'update'>('create')
const orders = ref<RdSaleOrderVO[]>([])
const fulfillments = ref<RdOrderFulfillmentVO[]>([])
const formData = ref<Partial<RdOutboundOrderSaveReqVO>>({})
const formRules = {
  outboundNo: [{ required: true, message: '请输入出库单号', trigger: 'blur' }],
  outboundDate: [{ required: true, message: '请选择出库日期', trigger: 'change' }],
  orderId: [{ required: true, message: '请选择销售订单', trigger: 'change' }],
  fulfillmentId: [{ required: true, message: '请选择库存分配批次', trigger: 'change' }]
}

const reset = () => {
  formData.value = {
    outboundNo: `OUT-${formatDate(new Date(), 'YYYYMMDDHHmmss')}`,
    outboundDate: formatDate(new Date(), 'YYYY-MM-DD')
  }
  fulfillments.value = []
}
const loadOrders = async () => {
  const data = await RdSaleOrderApi.getPage({ pageNo: 1, pageSize: 200 })
  orders.value = data.list.filter(
    (item) => !['CLOSED', 'CANCELLED'].includes(item.status) && Number(item.allocatedQty) > 0
  )
}
const loadFulfillments = async () => {
  if (!formData.value.orderId) return
  const data = await RdOrderFulfillmentApi.getPage({
    pageNo: 1,
    pageSize: 200,
    orderId: formData.value.orderId
  })
  fulfillments.value = data.list.filter(
    (item) => remaining(item) > 0 || item.id === formData.value.fulfillmentId
  )
}
const remaining = (item: RdOrderFulfillmentVO) =>
  Math.max(0, Number(item.allocatedQty) - Number(item.outboundQty || 0))
const onOrderChange = async () => {
  formData.value.fulfillmentId = undefined
  await loadFulfillments()
}
const open = async (type: 'create' | 'update', id?: number, orderId?: number) => {
  reset()
  formType.value = type
  dialogTitle.value = type === 'create' ? '新建扫码出库单' : '编辑扫码出库单'
  dialogVisible.value = true
  loading.value = true
  try {
    await loadOrders()
    if (id) {
      formData.value = await RdOutboundOrderApi.get(id)
      await loadFulfillments()
    } else if (orderId) {
      formData.value.orderId = orderId
      await loadFulfillments()
    }
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
const emit = defineEmits(['success'])
const submit = async () => {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = formData.value as RdOutboundOrderSaveReqVO
    if (formType.value === 'create') await RdOutboundOrderApi.create(data)
    else await RdOutboundOrderApi.update(data)
    message.success('出库单已保存')
    dialogVisible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>
