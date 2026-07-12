<template>
  <Dialog v-model="visible" title="差异查看" width="min(1080px, calc(100vw - 24px))">
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="3" border class="mb-16px">
        <el-descriptions-item label="盘点单号">{{ detail.order.stocktakeNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="盘点货位">{{ detail.order.locationCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="stocktakeStatusMap[detail.order.status]?.type || 'info'">
            {{ stocktakeStatusMap[detail.order.status]?.label || detail.order.status || '-' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="短少包数">{{ formatMetric(detail.order.shortageQty) }}</el-descriptions-item>
        <el-descriptions-item label="盘盈包数">{{ formatMetric(detail.order.surplusQty) }}</el-descriptions-item>
        <el-descriptions-item label="实扫包数">
          {{ formatMetric(detail.order.countedPackageQty) }} / {{ formatMetric(detail.order.bookPackageQty) }}
        </el-descriptions-item>
      </el-descriptions>

      <el-table v-if="differenceLines.length" :data="differenceLines" max-height="520" stripe show-overflow-tooltip>
        <el-table-column prop="packageCode" label="包码" min-width="150" fixed="left" />
        <el-table-column prop="customerName" label="客户" min-width="120" />
        <el-table-column prop="productModel" label="型号" min-width="120" />
        <el-table-column prop="furnaceNo" label="炉号" width="100" />
        <el-table-column prop="furnaceLayer" label="炉层" width="90" />
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
      <div v-else class="empty-state">当前盘点单暂无账实差异。</div>
    </div>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { RdStocktakeApi, type RdStocktakeDetailVO } from '@/api/rd/stock/stocktake'
import {
  formatMetric,
  getDifferenceClass,
  normalizeStocktakeDetail,
  stocktakeResultMap,
  stocktakeStatusMap
} from './helpers'

defineOptions({ name: 'RdStocktakeDifferenceDialog' })

const visible = ref(false)
const loading = ref(false)
const detail = ref<RdStocktakeDetailVO>()
const differenceLines = computed(() =>
  (detail.value?.lines || []).filter(
    (line) => Number(line.differenceQty || 0) !== 0 || !['MATCH', '', undefined].includes(line.result as any)
  )
)

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  try {
    detail.value = normalizeStocktakeDetail(await RdStocktakeApi.getDifference(id))
  } finally {
    loading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.empty-state {
  padding: 32px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}

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
