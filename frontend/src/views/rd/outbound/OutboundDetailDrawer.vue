<template>
  <el-drawer v-model="visible" title="出库单详情" size="min(760px, 100vw)">
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="出库单号">{{ detail.order.outboundNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail.order.status }}</el-descriptions-item>
        <el-descriptions-item label="出库日期">{{
          detail.order.outboundDate
        }}</el-descriptions-item>
        <el-descriptions-item label="销售订单">{{
          detail.order.orderNo || detail.order.orderId
        }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.order.customerName }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ detail.order.productModel }}</el-descriptions-item>
        <el-descriptions-item label="包数">{{ detail.order.quantity }}</el-descriptions-item>
        <el-descriptions-item label="重量">{{ detail.order.weight }} kg</el-descriptions-item>
        <el-descriptions-item label="提交人">{{
          detail.order.submitterName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="审核人">{{
          detail.order.reviewerName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="过账人">{{
          detail.order.posterName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="撤销原因">{{
          detail.order.reverseReason || '-'
        }}</el-descriptions-item>
      </el-descriptions>
      <el-divider content-position="left">扫描包码明细</el-divider>
      <el-table :data="detail?.lines || []" max-height="500">
        <el-table-column prop="packageCode" label="包码" min-width="150" />
        <el-table-column prop="locationCode" label="货位" width="90" />
        <el-table-column prop="furnaceNo" label="炉号" width="120" />
        <el-table-column prop="furnaceLayer" label="炉层" width="100" />
        <el-table-column prop="packageWeight" label="重量(kg)" width="110" align="right" />
        <el-table-column prop="scannerName" label="扫描人" width="110" />
      </el-table>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { RdOutboundOrderApi, type RdOutboundOrderDetailVO } from '@/api/rd/outbound'
defineOptions({ name: 'RdOutboundDetailDrawer' })
const visible = ref(false)
const loading = ref(false)
const detail = ref<RdOutboundOrderDetailVO>()
const open = async (id: number) => {
  visible.value = true
  loading.value = true
  try {
    detail.value = await RdOutboundOrderApi.getDetail(id)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })
</script>
