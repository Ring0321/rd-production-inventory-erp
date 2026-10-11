<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useWarehouseSession } from './useWarehouseSession'
import { useWarehouseLocations } from './useWarehouseLocations'
import LocationPanel from './LocationPanel.vue'
import PutawayPanel from './PutawayPanel.vue'
import LoadingPanel from './LoadingPanel.vue'
import { useStockRefresh } from '../../stock/useStockRefresh'

defineOptions({ name: 'PdaWarehouse' })
const session = useWarehouseSession()
const route = useRoute()
const active = ref(
  ['putaway', 'loading'].includes(String(route.query.section))
    ? String(route.query.section)
    : 'locations'
)
const map = useWarehouseLocations(session)
const canQuery = computed(
  () => session.allowed('rd:putaway-task:query') || session.allowed('rd:loading-task:query')
)
onMounted(map.load)
useStockRefresh(
  () => map.canQuery.value,
  (canApply) => map.load(canApply),
  () => !map.loading.value
)
</script>
<template>
  <div class="warehouse-pda">
    <h1>仓管工作台</h1>
    <el-result v-if="!canQuery" icon="warning" title="无仓管查询权限" />
    <el-tabs v-else v-model="active">
      <el-tab-pane label="库位" name="locations"
        ><LocationPanel :session="session" :map="map"
      /></el-tab-pane>
      <el-tab-pane label="上架" name="putaway"
        ><PutawayPanel :session="session" :map="map"
      /></el-tab-pane>
      <el-tab-pane label="装车" name="loading"
        ><LoadingPanel :session="session" :map="map"
      /></el-tab-pane>
    </el-tabs>
  </div>
</template>
<style lang="scss">
.warehouse-pda {
  min-width: 0;

  h1 {
    margin: 4px 0 16px;
    font-size: 20px;
  }

  h2 {
    margin: 0;
    font-size: 16px;
    overflow-wrap: anywhere;
  }

  .section-tools {
    display: flex;
    margin: 8px 0 16px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .location-filters,
  .summary-grid {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px;
  }

  .wide {
    grid-column: 1 / -1;
  }

  .summary-grid {
    margin: 16px 0 0;
  }

  .summary-grid > div {
    min-width: 0;
  }

  dt {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  dd {
    margin: 4px 0 0;
    overflow-wrap: anywhere;
  }

  .location-rows {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(min(100%, 300px), 1fr));
    gap: 16px;
  }

  .location-row {
    min-width: 0;
    padding: 16px 0;
    border-top: 1px solid var(--el-border-color-light);
  }

  .location-row header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }

  .location-row p {
    margin: 8px 0;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    overflow-wrap: anywhere;
  }

  .loading-state {
    padding: 20px 0;
    color: var(--el-text-color-secondary);
  }

  .lookup {
    margin: 20px 0;
  }

  .lookup > label {
    display: block;
    margin-bottom: 8px;
  }

  .lookup-controls {
    display: grid;
    grid-template-columns: minmax(0, 1fr) 44px;
    gap: 8px;
  }

  .task-header,
  .action-bar,
  .pager {
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 12px;
    margin: 20px 0;
  }

  .package-line,
  .task-row,
  .exception-band article {
    padding: 16px 0;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .package-line header {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
    align-items: start;
  }

  .package-line .el-checkbox {
    height: auto;
    align-items: start;
  }

  .package-line .el-checkbox__label {
    white-space: normal;
    overflow-wrap: anywhere;
  }

  .line-state {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .primary-action {
    width: 100%;
    margin: 20px 0;
  }

  .pending-band,
  .exception-band {
    padding: 16px 0;
    border-bottom: 1px solid var(--el-border-color-light);
  }

  .pending-band p,
  .exception-band p,
  .task-row p {
    overflow-wrap: anywhere;
  }

  .handling-form {
    margin-top: 20px;
  }

  .handling-form .el-form-item {
    margin: 16px 0;
  }

  .list-filter {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) 44px;
    gap: 8px;
    margin-top: 20px;
  }

  .task-row > span {
    display: block;
    margin-top: 8px;
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .el-form-item {
    min-width: 0;
    margin-bottom: 0;
  }

  .el-select {
    width: 100%;
  }

  .el-button {
    height: auto;
    min-height: 40px;
    white-space: normal;
  }

  .el-tabs__content,
  .el-tab-pane {
    min-width: 0;
  }
}
</style>
