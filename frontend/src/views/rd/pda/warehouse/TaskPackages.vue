<script setup lang="ts">
interface PackageLine {
  id: number
  packageCode: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  packageWeight: number
  customerName?: string
  plannedLocationCode?: string
  lineStatus: string
}
defineProps<{
  rows: PackageLine[]
  selected: number[]
  selectable: (id: number) => boolean
}>()
const emit = defineEmits<{ toggle: [id: number] }>()
const statuses: Record<string, string> = {
  PENDING: '待到位',
  PLANNED: '待装车',
  SCANNED: '已核验',
  CONFIRMED: '已确认',
  PUTAWAY_COMPLETED: '已入库',
  OUTBOUNDED: '已出库',
  EXCEPTION: '异常',
  CANCELLED: '已取消'
}
</script>
<template>
  <div class="package-lines" aria-label="实际任务包件">
    <article v-for="row in rows" :key="row.id" class="package-line">
      <header>
        <el-checkbox
          :model-value="selected.includes(row.id)"
          :disabled="!selectable(row.id)"
          :aria-label="`选择包件 ${row.packageCode}`"
          @change="emit('toggle', row.id)"
          ><span>{{ row.packageCode }}</span></el-checkbox
        >
        <span class="line-state">{{ statuses[row.lineStatus] || row.lineStatus }}</span>
      </header>
      <dl class="summary-grid">
        <div v-if="row.plannedLocationCode"
          ><dt>取货库位</dt><dd>{{ row.plannedLocationCode }}</dd></div
        >
        <div
          ><dt>型号</dt><dd>{{ row.productModel }}</dd></div
        >
        <div
          ><dt>炉号 / 炉层</dt><dd>{{ row.furnaceNo }} / {{ row.furnaceLayer }}</dd></div
        >
        <div
          ><dt>净重</dt><dd>{{ row.packageWeight }} kg</dd></div
        >
        <div v-if="row.customerName" class="wide"
          ><dt>客户</dt><dd>{{ row.customerName }}</dd></div
        >
      </dl>
    </article>
  </div>
</template>
