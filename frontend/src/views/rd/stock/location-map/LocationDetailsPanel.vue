<template>
  <div class="bin-details" v-loading="details.busy.value">
    <div class="details-heading">
      <h4>库位库存</h4>
      <el-tooltip content="重新读取库位资料"
        ><el-button circle aria-label="刷新库位资料" @click="reload"
          ><Icon icon="lucide:refresh-cw" /></el-button
      ></el-tooltip>
    </div>
    <el-alert
      v-if="details.stockError.value"
      :title="details.stockError.value"
      type="warning"
      :closable="false"
    />
    <template v-else-if="details.stock.value">
      <el-empty
        v-if="!details.stock.value.total"
        description="暂无已关联该库位的台账"
        :image-size="48"
      />
      <ul v-else class="stock-list">
        <li v-for="lot in details.stock.value.list" :key="lot.id">
          <strong>{{ lot.productModel }} · {{ lot.furnaceLayer }}</strong>
          <span>{{ lot.customerName }}</span>
          <span>炉号 {{ lot.furnaceNo }}</span>
          <div class="lot-metrics"
            ><span>在库 {{ metricText(lot.warehouseQty) }} 包</span
            ><span>{{ metricText(lot.stockWeight, 1000) }} 吨</span></div
          >
          <span>已锁定 {{ metricText(lot.lockedQty) }} 包</span>
        </li>
      </ul>
      <el-pagination
        v-if="details.stock.value.total > 8"
        v-model:current-page="stockPage"
        :page-size="8"
        :total="details.stock.value.total"
        layout="prev, pager, next"
        small
        @current-change="reload"
      />
    </template>
    <div class="details-heading"><h4>关联上架任务</h4></div>
    <el-alert
      v-if="details.taskError.value"
      :title="details.taskError.value"
      type="warning"
      :closable="false"
    />
    <template v-else-if="details.tasks.value">
      <el-empty
        v-if="!details.tasks.value.total"
        description="暂无计划在该库位的上架任务"
        :image-size="48"
      />
      <ul v-else class="task-list">
        <li v-for="task in details.tasks.value.list" :key="task.id">
          <el-button link type="primary" @click="$emit('task', task.id)">{{
            task.taskNo
          }}</el-button>
          <span>{{ task.packageCount }} 包 · {{ metricText(task.totalWeight, 1000) }} 吨</span>
          <span>{{ task.forkliftDriverName }}</span>
        </li>
      </ul>
      <el-pagination
        v-if="details.tasks.value.total > 6"
        v-model:current-page="taskPage"
        :page-size="6"
        :total="details.tasks.value.total"
        layout="prev, pager, next"
        small
        @current-change="reload"
      />
    </template>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue'
import { useLocationDetails } from './useLocationDetails'
import { metricText } from './spatialLayout'
const props = defineProps<{ locationId: number }>()
defineEmits<{ task: [id: number] }>()
const details = useLocationDetails()
const stockPage = ref(1)
const taskPage = ref(1)
const reload = () => details.load(props.locationId, stockPage.value, taskPage.value)
watch(
  () => props.locationId,
  () => {
    stockPage.value = 1
    taskPage.value = 1
    void reload()
  },
  { immediate: true }
)
</script>
<style scoped lang="scss">
.bin-details {
  min-width: 0;
}

.details-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 18px 0 8px;
}

.details-heading h4 {
  margin: 0;
  font-size: 13px;
}

.stock-list,
.task-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.stock-list li,
.task-list li {
  display: grid;
  padding: 10px 0;
  font-size: 12px;
  line-height: 18px;
  border-bottom: 1px solid var(--el-border-color-light);
  gap: 4px;
  overflow-wrap: anywhere;
}

.stock-list strong {
  font-size: 13px;
}

.lot-metrics {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
}

.bin-details :deep(.el-pagination) {
  justify-content: center;
  margin-top: 8px;
}

.task-list :deep(.el-button) {
  height: auto;
  text-align: left;
  white-space: normal;
  justify-content: flex-start;
}
</style>
