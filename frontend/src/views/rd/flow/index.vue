<script setup lang="ts">
import { computed, ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { formatDate } from '@/utils/formatTime'
import FlowPipeline from './FlowPipeline.vue'
import FlowPackageDetail from './FlowPackageDetail.vue'
import { useBusinessFlow } from './useBusinessFlow'
import {
  DEMO_CUSTOMER,
  isDemoTask,
  flowSteps,
  packageState,
  sumPackages,
  weightText,
  packageWeightText,
  summaryWeightText,
  validTaskId,
  packageLocation,
  businessFlowTarget
} from './flowFacts'
import type { RdPackageUnitVO } from '@/api/rd/stock/package'

defineOptions({ name: 'RdBusinessFlow' })
const route = useRoute()
const router = useRouter()
const flow = useBusinessFlow()
const {
  allowed,
  contextValid,
  task,
  packages,
  choices,
  total,
  trace,
  context,
  workByCode,
  workError,
  selectedCode,
  busy,
  searching,
  detailBusy,
  error,
  searchError,
  detailError,
  updatedAt,
  factsRevision,
  summary,
  searchTasks,
  loadTask,
  selectPackage,
  permitted
} = flow
const customer = ref(DEMO_CUSTOMER)
const taskPage = ref(1)
const selectedStep = ref('PRODUCTION')
const stateFilter = ref('ALL')
const keyword = ref('')
const layer = ref('')
const packagePage = ref(1)
const stateOf = (bag: RdPackageUnitVO) =>
  packageState(bag.status, workByCode.value[bag.packageCode])
const linkError = ref('')
const demo = computed(() => task.value && isDemoTask(task.value))
const taskChoices = computed(() =>
  task.value && !choices.value.some((row) => row.id === task.value?.id)
    ? [task.value, ...choices.value]
    : choices.value
)
const activeStep = computed(
  () => flowSteps.find((step) => step.key === selectedStep.value) || flowSteps[0]
)
const layers = computed(() => [
  ...new Set(packages.value.map((bag) => bag.furnaceLayer).filter(Boolean))
])
const filtered = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return packages.value.filter(
    (bag) =>
      (stateFilter.value === 'ALL' || stateOf(bag).key === stateFilter.value) &&
      (!layer.value || bag.furnaceLayer === layer.value) &&
      (!query ||
        [bag.packageCode, bag.productModel, bag.furnaceNo, bag.locationCode].some((value) =>
          value?.toLowerCase().includes(query)
        ))
  )
})
const filteredTotal = computed(() => sumPackages(filtered.value))
const visiblePackages = computed(() =>
  filtered.value.slice((packagePage.value - 1) * 8, packagePage.value * 8)
)
const selectedBag = computed(() =>
  filtered.value.find((bag) => bag.packageCode === selectedCode.value)
)
const currentTotal = (keys: readonly string[]) =>
  sumPackages(packages.value.filter((bag) => keys.includes(stateOf(bag).key)))
const metrics = computed(() => [
  {
    label: '登记包件',
    value: summary.value.count,
    unit: '包',
    detail: `${summary.value.outputCount} 条来源生产记录`
  },
  {
    label: summary.value.unknownWeight ? '已知登记净重' : '登记总净重',
    value:
      summary.value.unknownWeight === summary.value.count && summary.value.count > 0
        ? '待核'
        : weightText(summary.value.weightKg),
    unit: 'kg',
    detail: summary.value.unknownWeight
      ? `${summary.value.unknownWeight} 包净重待核，仅列已知量`
      : '按每包登记净重累计'
  },
  {
    label: '在库及待出库',
    value: currentTotal(['STOCK', 'LOADING', 'LOADING_EXCEPTION', 'MISSING']).count,
    unit: '包',
    detail: summaryWeightText(currentTotal(['STOCK', 'LOADING', 'LOADING_EXCEPTION', 'MISSING']))
  },
  {
    label: '已出库',
    value: currentTotal(['OUTBOUND']).count,
    unit: '包',
    detail: summaryWeightText(currentTotal(['OUTBOUND']))
  },
  {
    label: '不合格 / 异常',
    value: currentTotal(['REJECTED', 'LOADING_EXCEPTION', 'MISSING', 'UNKNOWN']).count,
    unit: '包',
    detail: '不计为合格可用库存'
  }
])
watch([stateFilter, keyword, layer, packages], () => {
  packagePage.value = 1
})
watch(filtered, (rows) => {
  if (rows.length && !rows.some((bag) => bag.packageCode === selectedCode.value))
    void selectPackage(rows[0].packageCode)
})
const search = async () => {
  taskPage.value = 1
  await searchTasks(customer.value)
}
const openDemo = async () => {
  customer.value = DEMO_CUSTOMER
  taskPage.value = 1
  const records = await searchTasks(customer.value)
  const record = records.find(isDemoTask)
  if (record) await chooseTask(record.id)
}
const chooseTask = async (id: number) => {
  linkError.value = ''
  stateFilter.value = 'ALL'
  keyword.value = ''
  layer.value = ''
  await loadTask(id)
  if (task.value?.id === id)
    await router.replace({ name: 'RdBusinessFlow', query: { taskId: String(id) } })
}
const selectRow = (bag: RdPackageUnitVO) => {
  void selectPackage(bag.packageCode)
}
const stageTarget = computed(() =>
  task.value
    ? businessFlowTarget(
        activeStep.value.key,
        task.value.id,
        selectedBag.value,
        trace.value,
        context.value
      )
    : undefined
)
const canOpenStage = computed(() => {
  const name = stageTarget.value?.name
  return !!name && router.hasRoute(name) && permitted(activeStep.value.permission)
})
const openStage = () => {
  if (canOpenStage.value && stageTarget.value) void router.push(stageTarget.value)
}
const reloadIdentity = () => window.location.reload()
onMounted(async () => {
  if (!allowed.value || !contextValid.value) return
  const queryId = route.query.taskId
  if (queryId !== undefined) {
    const id = validTaskId(queryId)
    if (!id) {
      linkError.value = '生产任务链接标识无效，未自动展示其他任务'
      return
    }
    await loadTask(id)
    if (task.value) {
      customer.value = task.value.customerName
      await searchTasks(customer.value)
    }
    return
  }
  await openDemo()
})
</script>

<template>
  <main class="business-flow" aria-label="业务全景">
    <el-result
      v-if="!allowed || !contextValid"
      icon="warning"
      title="当前岗位无全流程查看权限，或登录身份已变化"
    >
      <template #extra
        ><el-button v-if="!contextValid" type="primary" @click="reloadIdentity"
          ><Icon icon="lucide:refresh-cw" />重新载入并核对权限</el-button
        ></template
      >
    </el-result>
    <template v-else>
      <header class="flow-heading">
        <div
          ><h1><Icon icon="lucide:workflow" :size="25" />业务全景</h1
          ><span>生产 · 抽检 · 仓储 · 发运 · 追溯</span></div
        >
        <div class="heading-actions">
          <time v-if="updatedAt">{{ formatDate(updatedAt, 'HH:mm:ss') }} 读取</time>
          <el-button :loading="searching" @click="openDemo"
            ><Icon icon="lucide:boxes" />演示业务</el-button
          >
          <el-tooltip content="刷新当前任务"
            ><el-button
              circle
              :loading="busy"
              :disabled="!task"
              aria-label="刷新业务全景"
              @click="task && loadTask(task.id)"
              ><Icon icon="lucide:refresh-cw" /></el-button
          ></el-tooltip>
        </div>
      </header>
      <section class="task-toolbar" aria-label="选择生产任务">
        <el-input
          v-model="customer"
          clearable
          placeholder="按客户查询任务"
          aria-label="查询客户"
          @keyup.enter="search"
        />
        <el-tooltip content="查询生产任务"
          ><el-button circle :loading="searching" aria-label="查询生产任务" @click="search"
            ><Icon icon="lucide:list-filter" /></el-button
        ></el-tooltip>
        <el-select
          :model-value="task?.id"
          aria-label="选择生产任务"
          placeholder="选择生产任务"
          :loading="searching"
          @update:model-value="chooseTask"
        >
          <el-option
            v-for="item in taskChoices"
            :key="item.id"
            :value="item.id"
            :label="`${item.taskNo} · ${item.customerName}`"
          />
        </el-select>
        <el-pagination
          v-if="total > 20"
          v-model:current-page="taskPage"
          small
          :page-size="20"
          :total="total"
          layout="prev, next, total"
          @current-change="searchTasks(customer, taskPage)"
        />
        <span v-else-if="!searching" class="query-count">查到 {{ total }} 个任务</span>
      </section>
      <el-alert
        v-if="linkError || error || searchError"
        :title="linkError || error || searchError"
        type="warning"
        :closable="false"
      />
      <el-skeleton v-if="busy && !task" :rows="8" animated />
      <el-empty
        v-else-if="!task"
        description="请选择生产任务；本地演示业务尚未登记时不会生成虚构数据"
      />
      <template v-else>
        <el-alert
          v-if="demo"
          class="demo-notice"
          title="本地演示业务 · 非现场实货、非实测检验、非真实交易"
          type="warning"
          :closable="false"
        />
        <section class="task-identity" aria-label="当前生产任务">
          <div
            ><strong>{{ task.customerName }}</strong
            ><span>{{ task.taskNo }}</span></div
          >
          <dl
            ><div
              ><dt>生产日期</dt><dd>{{ task.productionDate }}</dd></div
            ><div
              ><dt>班次</dt><dd>{{ task.shiftCode }}</dd></div
            ><div
              ><dt>任务状态</dt
              ><dd>{{
                task.status === 'ACTIVE'
                  ? '进行中'
                  : task.status === 'CLOSED'
                    ? '已关闭'
                    : task.status === 'CANCELLED'
                      ? '已取消'
                      : task.status
              }}</dd></div
            ></dl
          >
        </section>
        <section class="flow-metrics" aria-label="任务登记汇总">
          <dl v-for="item in metrics" :key="item.label"
            ><dt>{{ item.label }}</dt
            ><dd
              >{{ item.value }} <small>{{ item.unit }}</small></dd
            ><p>{{ item.detail }}</p></dl
          >
        </section>
        <el-alert v-if="workError" :title="workError" type="warning" :closable="false" />
        <FlowPipeline :summary="summary" :selected="selectedStep" @select="selectedStep = $event" />
        <nav class="stage-actions" aria-label="当前阶段业务入口"
          ><el-button v-if="canOpenStage" link type="primary" @click="openStage"
            >进入{{ activeStep.title }}<Icon icon="lucide:arrow-up-right" /></el-button
          ><span v-else>{{
            activeStep.key === 'TRACE' ? '下方按包件查看流转证据' : '当前岗位无此环节办理页权限'
          }}</span></nav
        >
        <section class="flow-goods" aria-label="任务包件明细">
          <header
            ><h2>包件状态与货位</h2
            ><span
              >{{ filteredTotal.count }} 包 · {{ summaryWeightText(filteredTotal) }}</span
            ></header
          >
          <nav class="state-distribution" aria-label="当前状态分布">
            <button type="button" :aria-pressed="stateFilter === 'ALL'" @click="stateFilter = 'ALL'"
              >全部 <strong>{{ summary.count }}</strong></button
            >
            <button
              v-for="item in summary.states.filter((s) => s.count > 0)"
              :key="item.key"
              type="button"
              :aria-pressed="stateFilter === item.key"
              :data-tone="item.tone"
              @click="stateFilter = item.key"
              >{{ item.name }} <strong>{{ item.count }}</strong></button
            >
          </nav>
          <div class="goods-filters">
            <el-input
              v-model="keyword"
              clearable
              placeholder="包号 / 型号 / 炉号 / 货位"
              aria-label="筛选包件"
            />
            <el-select v-model="layer" clearable placeholder="全部炉层" aria-label="筛选炉层"
              ><el-option v-for="item in layers" :key="item" :value="item" :label="item"
            /></el-select>
          </div>
          <el-table
            class="desktop-packages"
            :data="visiblePackages"
            row-key="packageCode"
            :current-row-key="selectedCode"
            highlight-current-row
            @row-click="selectRow"
          >
            <el-table-column label="包码" min-width="190"
              ><template #default="scope"
                ><el-button link type="primary" @click.stop="selectRow(scope.row)">{{
                  scope.row.packageCode
                }}</el-button></template
              ></el-table-column
            >
            <el-table-column label="型号 / 规格" prop="productModel" min-width="145" />
            <el-table-column label="炉号 / 炉层" min-width="160"
              ><template #default="scope"
                >{{ scope.row.furnaceNo }} / {{ scope.row.furnaceLayer }}</template
              ></el-table-column
            >
            <el-table-column label="登记净重 / kg" width="115" align="right"
              ><template #default="scope">{{
                packageWeightText(scope.row.packageWeight)
              }}</template></el-table-column
            >
            <el-table-column label="库位记录" min-width="140"
              ><template #default="scope">{{
                packageLocation(scope.row).caption
              }}</template></el-table-column
            >
            <el-table-column label="状态" width="110"
              ><template #default="scope"
                ><el-tag :type="stateOf(scope.row).tone" size="small">{{
                  stateOf(scope.row).name
                }}</el-tag></template
              ></el-table-column
            >
          </el-table>
          <div class="mobile-packages">
            <button
              v-for="bag in visiblePackages"
              :key="bag.id"
              type="button"
              :aria-pressed="selectedCode === bag.packageCode"
              @click="selectRow(bag)"
              ><strong>{{ bag.packageCode }}</strong
              ><el-tag size="small" :type="stateOf(bag).tone">{{ stateOf(bag).name }}</el-tag
              ><span
                >{{ bag.productModel }} · {{ bag.furnaceLayer }} ·
                {{ packageWeightText(bag.packageWeight) }} kg</span
              ><small>{{ bag.furnaceNo }} / {{ packageLocation(bag).caption }}</small></button
            >
          </div>
          <el-empty v-if="!filtered.length" description="当前筛选无包件" :image-size="60" />
          <el-pagination
            v-if="filtered.length > 8"
            v-model:current-page="packagePage"
            small
            :total="filtered.length"
            :page-size="8"
            layout="prev, pager, next"
            class="package-pagination"
          />
        </section>
        <FlowPackageDetail
          v-if="selectedBag"
          :key="selectedBag.packageCode"
          :bag="selectedBag"
          :trace="trace"
          :context="context"
          :busy="detailBusy"
          :error="detailError"
          :facts-revision="factsRevision"
          :permitted="permitted"
        />
      </template>
    </template>
  </main>
</template>

<style scoped>
.business-flow {
  min-width: 0;
  padding: 22px;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

.flow-heading {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  padding-bottom: 20px;
}

.flow-heading h1 {
  display: flex;
  margin: 0 0 6px;
  font-size: 22px;
  line-height: 30px;
  align-items: center;
  gap: 10px;
}

.flow-heading > div > span,
time,
.query-count {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.heading-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.heading-actions .el-button + .el-button {
  margin-left: 0;
}

.task-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding: 16px 0;
  border-top: 1px solid var(--el-border-color);
}

.task-toolbar > .el-input {
  width: 210px;
}

.task-toolbar > .el-select {
  width: 390px;
  max-width: 100%;
}

.demo-notice {
  margin: 8px 0;
}

.task-identity {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  align-items: center;
  margin: 20px 0;
}

.task-identity strong {
  display: block;
  margin-bottom: 5px;
  font-size: 17px;
  line-height: 26px;
  overflow-wrap: anywhere;
}

.task-identity span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.task-identity dl {
  display: flex;
  gap: 24px;
  margin: 0;
}

.task-identity dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.task-identity dd {
  margin: 7px 0 0;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.flow-metrics {
  display: grid;
  margin-bottom: 22px;
  border-top: 1px solid var(--el-border-color);
  border-bottom: 1px solid var(--el-border-color);
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.flow-metrics dl {
  min-width: 0;
  padding: 0 16px;
  margin: 16px 0;
  border-right: 1px solid var(--el-border-color-lighter);
}

.flow-metrics dl:first-child {
  padding-left: 0;
}

.flow-metrics dl:last-child {
  border-right: 0;
}

.flow-metrics dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.flow-metrics dd {
  margin: 9px 0;
  font-size: 25px;
  font-weight: 600;
  line-height: 32px;
  overflow-wrap: anywhere;
  font-variant-numeric: tabular-nums;
}

.flow-metrics small {
  font-size: 12px;
  font-weight: 400;
}

.flow-metrics p {
  margin: 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.stage-actions {
  display: flex;
  min-height: 40px;
  padding: 5px 0;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  align-items: center;
}

.flow-goods {
  padding-top: 20px;
  margin: 22px 0;
  border-top: 1px solid var(--el-border-color);
}

.flow-goods > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.flow-goods h2 {
  margin: 0;
  font-size: 17px;
}

.flow-goods > header > span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.state-distribution {
  display: flex;
  gap: 0;
  flex-wrap: wrap;
  margin-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.state-distribution button {
  min-height: 40px;
  padding: 11px 12px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  background: none;
  border: 0;
  border-bottom: 2px solid transparent;
}

.state-distribution strong {
  margin-left: 3px;
}

.state-distribution button[aria-pressed='true'] {
  color: #087e72;
  border-bottom-color: #087e72;
}

.state-distribution button[data-tone='danger'] {
  color: #b33f3f;
}

.state-distribution button:focus-visible {
  outline: 2px solid #087e72;
  outline-offset: -2px;
}

.goods-filters {
  display: grid;
  grid-template-columns: minmax(160px, 360px) 160px;
  gap: 12px;
  margin-bottom: 14px;
}

.mobile-packages {
  display: none;
}

.package-pagination {
  margin: 16px 0;
  justify-content: flex-end;
}

@media (width <= 1100px) {
  .flow-metrics {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .flow-metrics dl {
    padding: 0 12px;
  }
}

@media (width <= 600px) {
  .business-flow {
    padding: 14px;
  }

  .flow-heading h1 {
    font-size: 20px;
  }

  .task-toolbar > .el-input {
    width: calc(100% - 46px);
  }

  .task-toolbar > .el-select {
    width: 100%;
  }

  .task-identity {
    align-items: flex-start;
    flex-direction: column;
    gap: 14px;
  }

  .task-identity dl {
    gap: 18px;
    flex-wrap: wrap;
  }

  .flow-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .flow-metrics dl:nth-child(even) {
    border-right: 0;
  }

  .flow-metrics dl:first-child {
    padding-left: 12px;
  }

  .flow-metrics dd {
    font-size: 23px;
  }

  .goods-filters {
    grid-template-columns: 1fr;
  }

  .desktop-packages {
    display: none;
  }

  .mobile-packages {
    display: grid;
    gap: 0;
  }

  .mobile-packages button {
    display: grid;
    min-width: 0;
    padding: 14px 10px;
    color: var(--el-text-color-primary);
    text-align: left;
    cursor: pointer;
    background: none;
    border: 0;
    border-bottom: 1px solid var(--el-border-color);
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 8px;
  }

  .mobile-packages button[aria-pressed='true'] {
    background: #eff8f5;
    box-shadow: inset 3px 0 #087e72;
  }

  .mobile-packages strong {
    font-size: 13px;
    line-height: 20px;
    overflow-wrap: anywhere;
  }

  .mobile-packages span:not(.el-tag),
  .mobile-packages small {
    grid-column: 1 / -1;
    font-size: 12px;
    line-height: 20px;
    overflow-wrap: anywhere;
  }

  .mobile-packages small {
    color: var(--el-text-color-secondary);
  }

  .mobile-packages :deep(.el-tag span) {
    white-space: nowrap;
  }
}
</style>
