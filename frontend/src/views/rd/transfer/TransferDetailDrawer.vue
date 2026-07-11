<template>
  <el-drawer
    v-model="drawerVisible"
    :title="drawerTitle"
    size="760px"
    class="transfer-detail-drawer"
  >
    <div v-loading="loading">
      <el-descriptions v-if="transferOrder" :column="2" border>
        <el-descriptions-item label="调拨单号">{{ transferOrder.transferNo }}</el-descriptions-item>
        <el-descriptions-item label="调拨日期">{{ transferOrder.transferDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[transferOrder.status]?.type || 'info'">
            {{ statusMap[transferOrder.status]?.label || transferOrder.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ transferOrder.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">调拨关系</el-divider>

      <el-descriptions v-if="transferOrder" :column="2" border>
        <el-descriptions-item label="调出客户">{{ transferOrder.fromCustomerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="调入客户">{{ transferOrder.toCustomerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源关联订单">{{ transferOrder.fromOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="目标销售订单">{{ transferOrder.toOrderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ transferOrder.productModel || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生产炉号">{{ transferOrder.furnaceNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所在炉层">{{ transferOrder.furnaceLayer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="货位">{{ transferOrder.fromLocationCode || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">数量与库存批次</el-divider>

      <el-descriptions v-if="transferOrder" :column="2" border>
        <el-descriptions-item label="调拨数量">{{ transferOrder.quantity }}</el-descriptions-item>
        <el-descriptions-item label="单包重量">{{ transferOrder.packageWeight }}</el-descriptions-item>
        <el-descriptions-item label="调拨重量">{{ transferOrder.weight }}</el-descriptions-item>
        <el-descriptions-item label="调出批次 ID">{{ transferOrder.fromStockLotId }}</el-descriptions-item>
        <el-descriptions-item label="调入批次 ID">{{ transferOrder.toStockLotId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="调入货位">{{ transferOrder.toLocationCode || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">调拨原因</el-divider>
      <div class="remark-block">{{ transferOrder?.remark || '-' }}</div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RdTransferOrderApi } from '@/api/rd/transfer'
import type { RdTransferOrderVO } from '@/api/rd/transfer'

defineOptions({ name: 'RdTransferOrderDetailDrawer' })

const drawerVisible = ref(false)
const loading = ref(false)
const transferOrder = ref<RdTransferOrderVO>()
const drawerTitle = computed(() => {
  return transferOrder.value ? `调拨单 - ${transferOrder.value.transferNo}` : '调拨单详情'
})
const statusOptions: Array<{ label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }> = [
  { label: '草稿', value: 'DRAFT', type: 'info' },
  { label: '已确认', value: 'CONFIRMED', type: 'success' },
  { label: '已撤销', value: 'CANCELLED', type: 'danger' }
]
const statusMap = statusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }>
)

const open = async (id: number) => {
  drawerVisible.value = true
  loading.value = true
  try {
    transferOrder.value = await RdTransferOrderApi.get(id)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
:global(.transfer-detail-drawer) {
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
