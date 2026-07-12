<template>
  <el-drawer v-model="visible" title="盘点详情" size="min(1120px, 100vw)">
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="盘点单号">{{ detail.order.stocktakeNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="stocktakeStatusMap[detail.order.status]?.type || 'info'">
            {{ stocktakeStatusMap[detail.order.status]?.label || detail.order.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="盘点日期">{{ detail.order.stocktakeDate }}</el-descriptions-item>
        <el-descriptions-item label="盘点货位">{{ detail.order.locationCode }}</el-descriptions-item>
        <el-descriptions-item label="冻结快照">{{ detail.order.snapshotTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="版本">{{ detail.order.version }}</el-descriptions-item>
        <el-descriptions-item label="应有包数 / 实扫包数">
          {{ formatMetric(detail.order.bookPackageQty) }} / {{ formatMetric(detail.order.countedPackageQty) }}
        </el-descriptions-item>
        <el-descriptions-item label="短少 / 盘盈">
          {{ formatMetric(detail.order.shortageQty) }} / {{ formatMetric(detail.order.surplusQty) }}
        </el-descriptions-item>
        <el-descriptions-item label="应有重量 / 实扫重量">
          {{ formatMetric(detail.order.bookWeight, 3) }} / {{ formatMetric(detail.order.countedWeight, 3) }}
        </el-descriptions-item>
        <el-descriptions-item label="提交 / 复核">
          {{ detail.order.submitterName || '-' }} / {{ detail.order.reviewerName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="调整 / 关闭">
          {{ detail.order.adjusterName || '-' }} / {{ detail.order.closerName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.order.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">盘点明细</el-divider>
      <el-table :data="detail?.lines || []" max-height="560" stripe show-overflow-tooltip>
        <el-table-column prop="packageCode" label="包码" min-width="150" fixed="left" />
        <el-table-column prop="customerName" label="客户" min-width="120" />
        <el-table-column prop="productModel" label="型号" min-width="120" />
        <el-table-column prop="furnaceNo" label="炉号" width="100" />
        <el-table-column prop="furnaceLayer" label="炉层" width="90" />
        <el-table-column prop="packageWeight" label="单包重量(kg)" width="120" align="right">
          <template #default="scope">{{ formatMetric(scope.row.packageWeight, 3) }}</template>
        </el-table-column>
        <el-table-column prop="bookQty" label="应有" width="90" align="right">
          <template #default="scope">{{ formatMetric(scope.row.bookQty) }}</template>
        </el-table-column>
        <el-table-column prop="countedQty" label="实扫" width="90" align="right">
          <template #default="scope">{{ formatMetric(scope.row.countedQty) }}</template>
        </el-table-column>
        <el-table-column prop="differenceQty" label="差异" width="100" align="right">
          <template #default="scope">
            <span :class="getDifferenceClass(scope.row)">{{ formatMetric(scope.row.differenceQty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="90" align="center">
          <template #default="scope">
            <el-tag :type="stocktakeResultMap[scope.row.result || 'MATCH']?.type || 'info'">
              {{ stocktakeResultMap[scope.row.result || 'MATCH']?.label || scope.row.result || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="scannerName" label="扫码人" width="110" />
        <el-table-column prop="scanTime" label="扫码时间" width="170" :formatter="dateFormatter" />
      </el-table>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { RdStocktakeApi, type RdStocktakeDetailVO } from '@/api/rd/stock/stocktake'
import {
  formatMetric,
  getDifferenceClass,
  stocktakeResultMap,
  stocktakeStatusMap
} from './helpers'

defineOptions({ name: 'RdStocktakeDetailDrawer' })

const visible = ref(false)
const loading = ref(false)
const detail = ref<RdStocktakeDetailVO>()

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  try {
    detail.value = await RdStocktakeApi.getDetail(id)
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.difference-shortage {
  color: var(--el-color-danger);
  font-weight: 600;
}

.difference-surplus {
  color: var(--el-color-warning);
  font-weight: 600;
}

.difference-match {
  color: var(--el-color-success);
  font-weight: 600;
}
</style>
