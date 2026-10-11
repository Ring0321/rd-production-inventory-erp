<script setup lang="ts">
import { onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  RefreshRight,
  Search,
  Check,
  CircleCheck,
  ArrowLeft,
  ArrowRight
} from '@element-plus/icons-vue'
import type { WarehouseSession } from './useWarehouseSession'
import type { useWarehouseLocations } from './useWarehouseLocations'
import { usePutawayWork } from './usePutawayWork'
import TaskPackages from './TaskPackages.vue'

const props = defineProps<{
  session: WarehouseSession
  map: ReturnType<typeof useWarehouseLocations>
}>()
const route = useRoute()
const {
  canQuery,
  canConfirm,
  canAssign,
  pending,
  storageError,
  input,
  detail,
  selected,
  selectedWeight,
  finalLocationId,
  handlingResult,
  rows,
  total,
  query,
  reading,
  writing,
  error,
  feedback,
  locked,
  selectable,
  toggle,
  selectAll,
  loadList,
  open,
  locate,
  read,
  reset,
  confirmBatch,
  act
} = usePutawayWork(props.session, props.map.load, (id) =>
  props.map.rows.value.find((row) => row.id === id)
)
const labels: Record<string, string> = {
  ASSIGNED: '已派单',
  MOVING: '到位核验中',
  PENDING_CONFIRMATION: '待入库确认',
  PUTAWAY_COMPLETED: '已入库',
  EXCEPTION_PENDING: '异常待处理',
  CANCELLED: '已取消'
}
onMounted(async () => {
  await loadList()
  if (!props.session.current()) return
  if (pending.value) return
  if (route.query.section !== 'putaway') return
  const id = Number(route.query.taskId)
  if (Number.isSafeInteger(id) && id > 0) await open(id)
  if (!props.session.current()) return
  if (typeof route.query.packageCode === 'string' && !pending.value) {
    input.value = route.query.packageCode
    await locate()
  }
})
</script>
<template>
  <section class="putaway-panel" aria-label="仓管入库办理">
    <el-result v-if="!canQuery" icon="warning" title="无上架查询权限" />
    <template v-else>
      <el-alert
        v-if="error || storageError"
        :title="storageError || error"
        type="warning"
        :closable="false"
        role="alert"
      />
      <el-alert v-if="feedback" :title="feedback" type="success" :closable="false" role="status" />
      <section v-if="pending" class="pending-band" aria-label="待核上架请求">
        <h2>到位核验结果待核</h2
        ><p
          >任务 {{ pending.kind === 'putaway' ? pending.request.id : '' }} ·
          {{ pending.request.taskLineIds.length }} 包</p
        >
        <el-button v-if="canConfirm" type="primary" :loading="writing" @click="confirmBatch"
          ><el-icon><RefreshRight /></el-icon>重试原上架核验</el-button
        >
      </section>
      <form class="lookup" @submit.prevent="locate">
        <label for="putaway-package">包号 / 二维码</label>
        <div class="lookup-controls"
          ><el-input
            id="putaway-package"
            v-model="input"
            :disabled="locked"
            clearable
            placeholder="扫描吨袋二维码" /><el-tooltip content="定位上架任务"
            ><el-button
              native-type="submit"
              type="primary"
              :loading="reading"
              :disabled="locked"
              aria-label="定位上架任务"
              ><el-icon><Search /></el-icon></el-button></el-tooltip
        ></div>
      </form>
      <template v-if="detail">
        <header class="task-header"
          ><h2>{{ detail.task.taskNo }}</h2
          ><el-tooltip content="重新读取上架任务"
            ><el-button
              :disabled="writing"
              :loading="reading"
              aria-label="重新读取上架任务"
              @click="read()"
              ><el-icon><RefreshRight /></el-icon></el-button></el-tooltip
        ></header>
        <dl class="summary-grid">
          <div
            ><dt>状态</dt><dd>{{ labels[detail.task.status] || detail.task.status }}</dd></div
          >
          <div
            ><dt>到位进度</dt
            ><dd>{{ detail.task.scannedPackageCount }} / {{ detail.task.packageCount }} 包</dd></div
          >
          <div
            ><dt>计划库位</dt><dd>{{ detail.task.plannedLocationCode }}</dd></div
          >
          <div
            ><dt>实际库位</dt><dd>{{ detail.task.finalLocationCode || '待确认' }}</dd></div
          >
          <div
            ><dt>叉车司机</dt><dd>{{ detail.task.forkliftDriverName }}</dd></div
          >
          <div
            ><dt>总净重</dt><dd>{{ detail.task.totalWeight }} kg</dd></div
          >
        </dl>
        <div
          v-if="canConfirm && ['ASSIGNED', 'MOVING'].includes(detail.task.status)"
          class="action-bar"
        >
          <el-button :disabled="locked" @click="selectAll"
            ><el-icon><CircleCheck /></el-icon>选择待到位包</el-button
          >
          <span>本次 {{ selected.length }} 包 / {{ selectedWeight }} kg</span>
        </div>
        <TaskPackages
          :rows="detail.lines"
          :selected="selected"
          :selectable="selectable"
          @toggle="toggle"
        />
        <el-button
          v-if="canConfirm && ['ASSIGNED', 'MOVING'].includes(detail.task.status)"
          type="primary"
          class="primary-action"
          :disabled="locked || !selected.length"
          :loading="writing"
          @click="confirmBatch"
          ><el-icon><Check /></el-icon>核验所选已到位包</el-button
        >
        <section v-if="detail.exceptions.length" class="exception-band" aria-label="上架异常">
          <h2>现场异常</h2
          ><article v-for="item in detail.exceptions" :key="item.id"
            ><strong>{{ item.exceptionNo }}</strong
            ><p>{{ item.description }}</p
            ><p
              >{{ item.reporterName }} ·
              {{ item.exceptionStatus === 'OPEN' ? '待处理' : item.exceptionStatus }}</p
            ><p v-if="item.handlingResult">{{ item.handlingResult }}</p></article
          >
        </section>
        <el-form
          v-if="
            (canConfirm && detail.task.status === 'PENDING_CONFIRMATION') ||
            (canAssign && detail.task.status === 'EXCEPTION_PENDING')
          "
          label-position="top"
          class="handling-form"
        >
          <el-form-item label="实际 / 调整库位"
            ><el-select
              v-model="finalLocationId"
              :disabled="locked || props.map.loading.value"
              filterable
              placeholder="选择库位"
              ><el-option
                v-for="row in props.map.rows.value"
                :key="row.id"
                :value="row.id"
                :label="`${row.locationCode} · ${row.warehouseName}`"
                :disabled="!row.enabled || row.frozen" /></el-select
          ></el-form-item>
          <el-form-item v-if="detail.task.status === 'EXCEPTION_PENDING'" label="异常处理说明"
            ><el-input
              v-model="handlingResult"
              :disabled="locked"
              type="textarea"
              maxlength="1000"
              :rows="3"
          /></el-form-item>
          <el-button
            v-if="detail.task.status === 'PENDING_CONFIRMATION'"
            type="success"
            class="primary-action"
            :disabled="locked || !finalLocationId"
            :loading="writing"
            @click="act('confirm')"
            ><el-icon><Check /></el-icon>确认最终入库</el-button
          >
          <el-button
            v-else
            type="warning"
            class="primary-action"
            :disabled="locked || !finalLocationId || !handlingResult.trim()"
            :loading="writing"
            @click="act('reassign')"
            ><el-icon><Check /></el-icon>处理异常并改派</el-button
          >
        </el-form>
        <el-button :disabled="writing || !!pending" @click="reset"
          ><el-icon><ArrowLeft /></el-icon>返回上架任务列表</el-button
        >
      </template>
      <section v-else class="task-list" aria-label="上架任务列表">
        <form class="list-filter" @submit.prevent="loadList(1)"
          ><el-input
            v-model="query.taskNo"
            aria-label="上架任务号"
            placeholder="任务号"
            clearable /><el-select
            v-model="query.status"
            aria-label="上架任务状态"
            placeholder="全部状态"
            clearable
            ><el-option
              v-for="(label, value) in labels"
              :key="value"
              :label="label"
              :value="value" /></el-select
          ><el-tooltip content="查询上架任务"
            ><el-button native-type="submit" aria-label="查询上架任务"
              ><el-icon><Search /></el-icon></el-button></el-tooltip
        ></form>
        <el-empty v-if="!rows.length && !error" description="暂无上架任务" />
        <article v-for="task in rows" :key="task.id" class="task-row"
          ><el-button link type="primary" :disabled="writing || !!pending" @click="open(task.id)">{{
            task.taskNo
          }}</el-button
          ><span>{{ labels[task.status] || task.status }}</span
          ><p
            >{{ task.plannedLocationCode }} · {{ task.scannedPackageCount }} /
            {{ task.packageCount }} 包 · {{ task.forkliftDriverName }}</p
          ></article
        >
        <nav v-if="total > query.pageSize" class="pager" aria-label="上架任务分页"
          ><el-button
            :disabled="writing || query.pageNo <= 1"
            aria-label="上一页上架任务"
            @click="loadList(query.pageNo - 1)"
            ><el-icon><ArrowLeft /></el-icon></el-button
          ><span>{{ query.pageNo }} / {{ Math.ceil(total / query.pageSize) }}</span
          ><el-button
            :disabled="writing || query.pageNo * query.pageSize >= total"
            aria-label="下一页上架任务"
            @click="loadList(query.pageNo + 1)"
            ><el-icon><ArrowRight /></el-icon></el-button
        ></nav>
      </section>
    </template>
  </section>
</template>
