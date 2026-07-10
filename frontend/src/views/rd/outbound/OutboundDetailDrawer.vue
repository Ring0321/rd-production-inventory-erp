<template>
  <el-drawer
    v-model="drawerVisible"
    :title="drawerTitle"
    size="760px"
    class="outbound-detail-drawer"
  >
    <div v-loading="loading">
      <el-descriptions v-if="outboundOrder" :column="2" border>
        <el-descriptions-item label="出库单号">{{ outboundOrder.outboundNo }}</el-descriptions-item>
        <el-descriptions-item label="出库日期">{{ outboundOrder.outboundDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[outboundOrder.status]?.type || 'info'">
            {{ statusMap[outboundOrder.status]?.label || outboundOrder.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ outboundOrder.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">订单与库存批次</el-divider>

      <el-descriptions v-if="outboundOrder" :column="2" border>
        <el-descriptions-item label="销售订单">{{ outboundOrder.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ outboundOrder.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ outboundOrder.productModel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="货位">{{ outboundOrder.locationCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生产炉号">{{ outboundOrder.furnaceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所在炉层">{{ outboundOrder.furnaceLayer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分配记录 ID">{{ outboundOrder.fulfillmentId }}</el-descriptions-item>
        <el-descriptions-item label="库存批次 ID">{{ outboundOrder.stockLotId }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">出库数量</el-divider>

      <el-descriptions v-if="outboundOrder" :column="3" border>
        <el-descriptions-item label="出库数量">{{ outboundOrder.quantity }}</el-descriptions-item>
        <el-descriptions-item label="单包重量">{{ outboundOrder.packageWeight }}</el-descriptions-item>
        <el-descriptions-item label="出库重量">{{ outboundOrder.weight }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">备注</el-divider>
      <div class="remark-block">{{ outboundOrder?.remark || '-' }}</div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RdOutboundOrderApi } from '@/api/rd/outbound'
import type { RdOutboundOrderVO } from '@/api/rd/outbound'

defineOptions({ name: 'RdOutboundOrderDetailDrawer' })

const drawerVisible = ref(false)
const loading = ref(false)
const outboundOrder = ref<RdOutboundOrderVO>()
type StatusTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const drawerTitle = computed(() => {
  return outboundOrder.value ? `出库单 - ${outboundOrder.value.outboundNo}` : '出库单详情'
})
const statusOptions: Array<{ label: string; value: string; type: StatusTagType }> = [
  { label: '草稿', value: 'DRAFT', type: 'info' },
  { label: '已确认', value: 'CONFIRMED', type: 'success' },
  { label: '已撤销', value: 'CANCELLED', type: 'danger' }
]
const statusMap = statusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: StatusTagType }>
)

const open = async (id: number) => {
  drawerVisible.value = true
  loading.value = true
  try {
    outboundOrder.value = await RdOutboundOrderApi.get(id)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
:global(.outbound-detail-drawer) {
  max-width: 94%;
}

.remark-block {
  min-height: 72px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-regular);
  line-height: 1.6;
  white-space: pre-wrap;
}
</style>
