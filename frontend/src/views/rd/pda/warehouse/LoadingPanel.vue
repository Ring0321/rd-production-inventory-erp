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
import { useLoadingWork } from './useLoadingWork'
import TaskPackages from './TaskPackages.vue'
import LoadingExceptions from './LoadingExceptions.vue'

const props = defineProps<{
  session: WarehouseSession
  map: ReturnType<typeof useWarehouseLocations>
}>()
const route = useRoute()
const {
  canQuery,
  canDispatch,
  pending,
  storageError,
  input,
  detail,
  selected,
  selectedWeight,
  replacementRows,
  replacementPreview,
  replacementChecking,
  replacementError,
  inspectReplacements,
  replacementOptions,
  resolutionAction,
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
  reconcile,
  act
} = useLoadingWork(props.session, props.map.load)
const labels: Record<string, string> = {
  ASSIGNED: '已派单',
  LOADING: '装车核验中',
  PENDING_REVIEW: '待出库确认',
  OUTBOUNDED: '已出库',
  EXCEPTION_PENDING: '异常待处理',
  CANCELLED: '已取消'
}
onMounted(async () => {
  await loadList()
  if (!props.session.current()) return
  if (pending.value) return
  if (route.query.section !== 'loading') return
  const id = Number(route.query.taskId)
  if (Number.isSafeInteger(id) && id > 0) await open(id)
  if (!props.session.current()) return
  if (typeof route.query.packageCode === 'string' && !pending.value && canDispatch.value) {
    input.value = route.query.packageCode
    await locate()
  }
})
</script>
<template>
  <section class="loading-panel" aria-label="仓管出库办理">
    <el-result v-if="!canQuery" icon="warning" title="无装车查询权限" />
    <template v-else>
      <el-alert
        v-if="error || storageError"
        :title="storageError || error"
        type="warning"
        :closable="false"
        role="alert"
      />
      <el-alert v-if="feedback" :title="feedback" type="success" :closable="false" role="status" />
      <section v-if="pending" class="pending-band" aria-label="待核装车请求"
        ><h2>装车核验结果待核</h2
        ><p
          >任务 {{ pending.kind === 'loading' ? pending.request.taskId : '' }} ·
          {{ pending.request.taskLineIds.length }} 包</p
        ><el-button v-if="canDispatch" type="primary" :loading="writing" @click="confirmBatch"
          ><el-icon><RefreshRight /></el-icon>重试原装车核验</el-button
        ><el-button
          v-if="canDispatch"
          :loading="writing"
          :disabled="writing || reading"
          @click="reconcile"
          ><el-icon><Check /></el-icon>核定原装车请求</el-button
        ><el-button v-if="canQuery" :disabled="writing || reading" @click="read()"
          ><el-icon><Search /></el-icon>查看当前装车任务</el-button
        ></section
      >
      <form v-if="canDispatch" class="lookup" @submit.prevent="locate"
        ><label for="loading-package">包号 / 二维码</label
        ><div class="lookup-controls"
          ><el-input
            id="loading-package"
            v-model="input"
            :disabled="locked"
            clearable
            placeholder="扫描吨袋二维码" /><el-tooltip content="定位装车任务"
            ><el-button
              native-type="submit"
              type="primary"
              :loading="reading"
              :disabled="locked"
              aria-label="定位装车任务"
              ><el-icon><Search /></el-icon></el-button></el-tooltip></div
      ></form>
      <template v-if="detail">
        <header class="task-header"
          ><h2>{{ detail.task.taskNo }}</h2
          ><el-tooltip content="重新读取装车任务"
            ><el-button
              :disabled="writing"
              :loading="reading"
              aria-label="重新读取装车任务"
              @click="read()"
              ><el-icon><RefreshRight /></el-icon></el-button></el-tooltip
        ></header>
        <dl class="summary-grid">
          <div
            ><dt>状态</dt><dd>{{ labels[detail.task.status] || detail.task.status }}</dd></div
          >
          <div
            ><dt>装车进度</dt
            ><dd
              >{{ detail.task.scannedPackageCount }} / {{ detail.task.plannedPackageCount }} 包</dd
            ></div
          >
          <div class="wide"
            ><dt>客户</dt><dd>{{ detail.task.customerName }}</dd></div
          >
          <div class="wide"
            ><dt>提货单</dt><dd>{{ detail.task.pickupSlipNo }}</dd></div
          >
          <div
            ><dt>车牌</dt><dd>{{ detail.task.vehiclePlate || '未登记' }}</dd></div
          >
          <div
            ><dt>叉车司机</dt><dd>{{ detail.task.forkliftDriverName || '未登记' }}</dd></div
          >
          <div
            ><dt>已核验净重</dt><dd>{{ detail.task.scannedWeight }} kg</dd></div
          >
          <div
            ><dt>计划净重</dt><dd>{{ detail.task.plannedWeight }} kg</dd></div
          >
        </dl>
        <div
          v-if="canDispatch && ['ASSIGNED', 'LOADING'].includes(detail.task.status)"
          class="action-bar"
          ><el-button :disabled="locked" @click="selectAll"
            ><el-icon><CircleCheck /></el-icon>选择待装车包</el-button
          ><span>本次 {{ selected.length }} 包 / {{ selectedWeight }} kg</span></div
        >
        <TaskPackages
          :rows="detail.lines"
          :selected="selected"
          :selectable="selectable"
          @toggle="toggle"
        />
        <el-button
          v-if="canDispatch && ['ASSIGNED', 'LOADING'].includes(detail.task.status)"
          type="primary"
          class="primary-action"
          :disabled="locked || !selected.length"
          :loading="writing"
          @click="confirmBatch"
          ><el-icon><Check /></el-icon>核验所选已装车包</el-button
        >
        <el-button
          v-if="canDispatch && detail.task.status === 'PENDING_REVIEW'"
          type="success"
          class="primary-action"
          :disabled="locked"
          :loading="writing"
          @click="act('outbound')"
          ><el-icon><Check /></el-icon>确认最终出库</el-button
        >
        <LoadingExceptions
          :exceptions="detail.exceptions"
          :lines="replacementOptions"
          :editable="canDispatch && detail.task.status === 'EXCEPTION_PENDING'"
          :disabled="locked"
          :action="resolutionAction"
          :reason="handlingResult"
          :replacement-rows="replacementRows"
          :replacement-preview="replacementPreview"
          :replacement-checking="replacementChecking"
          :replacement-error="replacementError"
          @action="resolutionAction = $event"
          @reason="handlingResult = $event"
          @replacement-rows="replacementRows = $event"
          @inspect-replacements="inspectReplacements"
          @resolve="act('resolve')"
        />
        <el-button :disabled="writing || !!pending" @click="reset"
          ><el-icon><ArrowLeft /></el-icon>返回装车任务列表</el-button
        >
      </template>
      <section v-else class="task-list" aria-label="装车任务列表">
        <form class="list-filter" @submit.prevent="loadList(1)"
          ><el-input
            v-model="query.taskNo"
            aria-label="装车任务号"
            placeholder="任务号"
            clearable /><el-select
            v-model="query.status"
            aria-label="装车任务状态"
            placeholder="全部状态"
            clearable
            ><el-option
              v-for="(label, value) in labels"
              :key="value"
              :label="label"
              :value="value" /></el-select
          ><el-tooltip content="查询装车任务"
            ><el-button native-type="submit" aria-label="查询装车任务"
              ><el-icon><Search /></el-icon></el-button></el-tooltip
        ></form>
        <el-empty v-if="!rows.length && !error" description="暂无装车任务" />
        <article v-for="task in rows" :key="task.id" class="task-row"
          ><el-button link type="primary" :disabled="writing || !!pending" @click="open(task.id)">{{
            task.taskNo
          }}</el-button
          ><span>{{ labels[task.status] || task.status }}</span
          ><p>{{ task.customerName }} · {{ task.vehiclePlate || '车牌未登记' }}</p
          ><p
            >{{ task.scannedPackageCount }} / {{ task.plannedPackageCount }} 包 ·
            {{ task.scannedWeight }} / {{ task.plannedWeight }} kg</p
          ></article
        >
        <nav v-if="total > query.pageSize" class="pager" aria-label="装车任务分页"
          ><el-button
            :disabled="writing || query.pageNo <= 1"
            aria-label="上一页装车任务"
            @click="loadList(query.pageNo - 1)"
            ><el-icon><ArrowLeft /></el-icon></el-button
          ><span>{{ query.pageNo }} / {{ Math.ceil(total / query.pageSize) }}</span
          ><el-button
            :disabled="writing || query.pageNo * query.pageSize >= total"
            aria-label="下一页装车任务"
            @click="loadList(query.pageNo + 1)"
            ><el-icon><ArrowRight /></el-icon></el-button
        ></nav>
      </section>
    </template>
  </section>
</template>
