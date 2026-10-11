<template>
  <section aria-label="检验范围">
    <div class="section-title"
      ><h2>检验范围</h2
      ><span>已选 {{ selectedIds.length }} 包 · 抽样 {{ sampleIds.length }} 包</span></div
    >
    <ul class="coverage-list">
      <li v-for="bag in rows" :key="bag.id">
        <div class="bag-heading">
          <el-checkbox
            :model-value="selectedIds.includes(bag.id)"
            :disabled="disabled || !bag.eligible"
            :aria-label="`覆盖 ${bag.packageCode}`"
            @change="emit('coverage', bag.id, Boolean($event))"
            >{{ bag.packageCode }}</el-checkbox
          >
          <span>{{ bag.packageWeight }} kg</span>
        </div>
        <div class="bag-state">
          <span>{{ statuses[bag.status] || bag.status }}</span>
          <el-checkbox
            :model-value="sampleIds.includes(bag.id)"
            :disabled="disabled || !selectedIds.includes(bag.id)"
            :aria-label="`抽样 ${bag.packageCode}`"
            @change="emit('sample', bag.id, Boolean($event))"
            >抽样</el-checkbox
          >
          <el-button
            v-if="bag.activeInspectionId"
            link
            type="primary"
            :disabled="disabled"
            @click="emit('inspection', bag.activeInspectionId)"
            ><el-icon><Document /></el-icon>{{ bag.activeInspectionNo || '检验单' }}</el-button
          >
        </div>
      </li>
    </ul>
  </section>
</template>
<script setup lang="ts">
import { Document } from '@element-plus/icons-vue'
import type { RdQualityScopePackageVO } from '@/api/rd/quality/inspection'
defineProps<{
  rows: RdQualityScopePackageVO[]
  selectedIds: number[]
  sampleIds: number[]
  disabled: boolean
}>()
const emit = defineEmits<{
  coverage: [id: number, value: boolean]
  sample: [id: number, value: boolean]
  inspection: [id: number]
}>()
const statuses: Record<string, string> = {
  LABELED: '待检验',
  QC_PENDING: '待复核',
  QC_REJECTED: '未通过',
  QC_PASSED: '已通过',
  INBOUND_PENDING: '待入库',
  IN_STOCK: '在库',
  OUTBOUNDED: '已出库',
  CREATED: '未贴标',
  VOIDED: '已作废'
}
</script>
<style scoped>
.section-title,
.bag-heading,
.bag-state {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

h2 {
  margin: 0;
  font-size: 15px;
}

.section-title > span,
.bag-state > span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.coverage-list {
  padding: 0;
  margin: 12px 0 0;
  list-style: none;
}

.coverage-list li {
  padding: 10px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.bag-heading :deep(.el-checkbox) {
  height: auto;
  max-width: 100%;
  min-width: 0;
  min-height: 36px;
  margin-right: 0;
}

.bag-heading :deep(.el-checkbox__label) {
  white-space: normal;
  overflow-wrap: anywhere;
}

.bag-state :deep(.el-button) {
  height: auto;
  max-width: 100%;
  white-space: normal;
  overflow-wrap: anywhere;
}
</style>
