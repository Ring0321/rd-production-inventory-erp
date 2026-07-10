<template>
  <el-drawer
    v-model="drawerVisible"
    :title="drawerTitle"
    size="720px"
    class="sale-order-detail-drawer"
  >
    <div v-loading="loading">
      <el-descriptions v-if="saleOrder" :column="2" border>
        <el-descriptions-item label="订单号">{{ saleOrder.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="订单日期">{{ saleOrder.orderDate }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ saleOrder.customerName }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ saleOrder.productModel }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[saleOrder.status]?.type || 'info'">
            {{ statusMap[saleOrder.status]?.label || saleOrder.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ saleOrder.createTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">数量与差额</el-divider>

      <el-descriptions v-if="saleOrder" :column="2" border>
        <el-descriptions-item label="订单量">{{ saleOrder.requiredQty }}</el-descriptions-item>
        <el-descriptions-item label="实际产额">{{ saleOrder.actualOutputQty }}</el-descriptions-item>
        <el-descriptions-item label="调入量">{{ saleOrder.transferInQty }}</el-descriptions-item>
        <el-descriptions-item label="调出量">{{ saleOrder.transferOutQty }}</el-descriptions-item>
        <el-descriptions-item label="已分配量">{{ saleOrder.allocatedQty }}</el-descriptions-item>
        <el-descriptions-item label="满足数量">{{ saleOrder.fulfilledQty }}</el-descriptions-item>
        <el-descriptions-item label="差额">
          <span :class="differenceClass(saleOrder.differenceQty)">{{ saleOrder.differenceQty }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="调拨后差额">
          <span :class="differenceClass(saleOrder.adjustedDifferenceQty)">
            {{ saleOrder.adjustedDifferenceQty }}
          </span>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">其他信息</el-divider>

      <el-descriptions v-if="saleOrder" :column="1" border>
        <el-descriptions-item label="关闭时间">{{ saleOrder.closeTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="关闭人">{{ saleOrder.closeBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ saleOrder.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { RdSaleOrderApi } from '@/api/rd/order/sale'
import type { RdSaleOrderVO } from '@/api/rd/order/sale'

defineOptions({ name: 'RdSaleOrderDetailDrawer' })

const drawerVisible = ref(false)
const loading = ref(false)
const saleOrder = ref<RdSaleOrderVO>()
const drawerTitle = computed(() => {
  return saleOrder.value ? `销售订单 - ${saleOrder.value.orderNo}` : '销售订单详情'
})
const statusOptions = [
  { label: '待满足', value: 'OPEN', type: 'info' },
  { label: '部分满足', value: 'PARTIAL', type: 'warning' },
  { label: '已满足', value: 'FULFILLED', type: 'success' },
  { label: '已关闭', value: 'CLOSED', type: 'info' },
  { label: '已取消', value: 'CANCELLED', type: 'danger' }
]
const statusMap = statusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: string }>
)

const differenceClass = (value: number) => {
  const difference = Number(value)
  if (difference > 0) return 'difference-shortage'
  if (difference < 0) return 'difference-over'
  return 'difference-met'
}

const open = async (id: number) => {
  drawerVisible.value = true
  loading.value = true
  try {
    saleOrder.value = await RdSaleOrderApi.get(id)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>

<style scoped>
:global(.sale-order-detail-drawer) {
  max-width: 92%;
}

.difference-shortage {
  color: var(--el-color-danger);
  font-weight: 600;
}

.difference-over {
  color: var(--el-color-warning);
  font-weight: 600;
}

.difference-met {
  color: var(--el-color-success);
  font-weight: 600;
}
</style>
