<template>
  <main class="loading-pda">
    <header class="page-header">
      <div>
        <p class="page-kicker">叉车司机工作台</p>
        <h1>{{ taskDetail ? '装车任务详情' : '我的装车任务' }}</h1>
      </div>
      <el-button
        v-if="taskDetail"
        class="header-action"
        circle
        aria-label="返回任务列表"
        @click="backToList"
      >
        <Icon icon="lucide:arrow-left" />
      </el-button>
      <el-button
        v-else
        class="header-action"
        circle
        :loading="taskLoading"
        aria-label="刷新任务"
        @click="loadTasks(true)"
      >
        <Icon icon="lucide:refresh-cw" />
      </el-button>
    </header>

    <section v-if="!taskDetail" v-loading="taskLoading" class="task-list-section">
      <div class="list-summary">
        <span>已派装车任务</span>
        <strong>{{ taskTotal }}</strong>
        <span>项</span>
      </div>

      <el-alert
        class="role-alert"
        type="info"
        :closable="false"
        show-icon
        title="司机端只接收任务和查看搬运资料；库位调整、扫码确认、出库复核由仓管员负责。"
      />

      <el-empty
        v-if="!taskLoading && !taskList.length"
        :image-size="92"
        description="当前没有分配给你的装车任务"
      />

      <div v-else class="task-list">
        <button
          v-for="task in taskList"
          :key="task.id"
          type="button"
          class="task-card"
          @click="openTask(task.id)"
        >
          <div class="task-card__head">
            <span class="task-no">{{ task.taskNo }}</span>
            <el-tag :type="statusMeta(task.status).type" effect="light">
              {{ statusMeta(task.status).label }}
            </el-tag>
          </div>
          <div class="customer-row">
            <Icon icon="lucide:building-2" />
            <span>{{ task.customerName || '-' }}</span>
          </div>
          <div class="task-grid">
            <div>
              <span>提货单</span>
              <strong>{{ task.pickupSlipNo || '-' }}</strong>
            </div>
            <div>
              <span>车牌</span>
              <strong>{{ task.vehiclePlate || '-' }}</strong>
            </div>
            <div>
              <span>计划包数</span>
              <strong>{{ task.plannedPackageCount || 0 }} 包</strong>
            </div>
            <div>
              <span>计划重量</span>
              <strong>{{ weightText(task.plannedWeight) }} kg</strong>
            </div>
          </div>
          <div class="task-progress">
            <div>
              <span>仓管扫码进度</span>
              <b>{{ task.scannedPackageCount || 0 }} / {{ task.plannedPackageCount || 0 }}</b>
            </div>
            <el-progress :percentage="taskProgress(task)" :stroke-width="8" :show-text="false" />
          </div>
          <div class="task-card__foot">
            <span>{{ dateTimeText(task.assignedTime) }}</span>
            <Icon icon="lucide:chevron-right" />
          </div>
        </button>
      </div>

      <el-button
        v-if="taskList.length < taskTotal"
        class="load-more"
        :loading="taskLoadingMore"
        @click="loadMore"
      >
        加载更多
      </el-button>
    </section>

    <section v-else v-loading="detailLoading" class="task-detail-section">
      <div class="task-title-band">
        <div>
          <span>提货单</span>
          <strong>{{ taskDetail.task.pickupSlipNo || '-' }}</strong>
        </div>
        <el-tag :type="statusMeta(taskDetail.task.status).type" effect="light">
          {{ statusMeta(taskDetail.task.status).label }}
        </el-tag>
      </div>

      <el-alert
        class="role-alert"
        type="info"
        :closable="false"
        show-icon
        title="按下方库位和货物资料搬运；发现货物不符、库位找不到、现场无法装车时，反馈给仓管员调整任务。"
      />

      <el-alert
        v-if="openExceptions.length"
        class="role-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="`已上报 ${openExceptions.length} 条异常，等待仓管员处理`"
        :description="openExceptions[0]?.description || '请等待仓管端退回任务或重新派发。'"
      />

      <section class="info-band" aria-label="装车任务信息">
        <div>
          <span>客户</span>
          <strong>{{ taskDetail.task.customerName || '-' }}</strong>
        </div>
        <div>
          <span>车牌</span>
          <strong>{{ taskDetail.task.vehiclePlate || '-' }}</strong>
        </div>
        <div>
          <span>运输司机</span>
          <strong>{{ taskDetail.task.transportDriverName || '-' }}</strong>
        </div>
        <div>
          <span>仓管员</span>
          <strong>{{ taskDetail.task.warehouseKeeperName || '-' }}</strong>
        </div>
      </section>

      <section class="progress-band" aria-labelledby="progress-heading">
        <div class="band-heading">
          <h2 id="progress-heading">任务进度</h2>
          <el-button circle aria-label="刷新任务详情" @click="refreshTaskDetail">
            <Icon icon="lucide:refresh-cw" />
          </el-button>
        </div>
        <div class="progress-counts">
          <div>
            <span>计划包数</span>
            <strong>{{ taskDetail.task.plannedPackageCount || 0 }}</strong>
          </div>
          <div>
            <span>仓管已扫</span>
            <strong>{{ taskDetail.task.scannedPackageCount || 0 }}</strong>
          </div>
          <div>
            <span>剩余</span>
            <strong>{{ remainingCount }}</strong>
          </div>
        </div>
        <el-progress :percentage="detailProgress" :stroke-width="12" />
        <div class="progress-caption">
          <span>{{ weightText(taskDetail.task.plannedWeight) }} kg</span>
          <span>最终出库以仓管复核为准</span>
        </div>
      </section>

      <section class="locations-band" aria-labelledby="locations-heading">
        <div class="band-heading">
          <h2 id="locations-heading">库位与货物资料</h2>
          <span>{{ locationGroups.length }} 个库位</span>
        </div>
        <div v-for="group in locationGroups" :key="group.key" class="location-card">
          <div class="location-head">
            <div>
              <span>目标库位</span>
              <strong>{{ group.locationCode || '-' }}</strong>
            </div>
            <div>
              <strong>{{ group.packageCount }}</strong>
              <span>包 / {{ weightText(group.totalWeight) }} kg</span>
            </div>
          </div>
          <div class="goods-tags">
            <span v-for="item in group.summary" :key="item">{{ item }}</span>
          </div>
          <div class="package-list">
            <div
              v-for="line in group.lines"
              :key="line.id"
              class="package-row"
              :class="{ 'is-highlight': isHighlighted(line.packageCode) }"
            >
              <div>
                <strong>{{ line.packageCode }}</strong>
                <span
                  >{{ line.productModel }} / {{ line.furnaceNo || '-' }} /
                  {{ line.furnaceLayer || '-' }}</span
                >
              </div>
              <div>
                <span>{{ weightText(line.packageWeight) }} kg</span>
                <el-tag :type="lineTagType(line)" size="small" effect="plain">
                  {{ lineStatusText(line) }}
                </el-tag>
              </div>
            </div>
          </div>
        </div>
      </section>

      <section class="actions-band" aria-label="现场操作">
        <el-button type="primary" plain @click="refreshTaskDetail">
          <Icon icon="lucide:refresh-cw" />
          刷新任务
        </el-button>
        <el-button type="warning" plain @click="openExceptionDialog">
          <Icon icon="lucide:triangle-alert" />
          异常上报
        </el-button>
      </section>
    </section>

    <Dialog v-model="exceptionDialogVisible" title="装车异常上报" width="520px">
      <el-form label-width="96px">
        <el-form-item label="异常原因" required>
          <el-select v-model="exceptionForm.exceptionType" class="!w-full">
            <el-option
              v-for="item in exceptionTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关联包件">
          <el-select v-model="exceptionForm.taskLineId" clearable filterable class="!w-full">
            <el-option
              v-for="line in taskDetail?.lines || []"
              :key="line.id"
              :label="`${line.packageCode} / ${line.plannedLocationCode || '-'}`"
              :value="line.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="现场库位">
          <el-input
            v-model="exceptionForm.observedLocationCode"
            clearable
            placeholder="现场看到的库位，可不填"
          />
        </el-form-item>
        <el-form-item label="异常说明" required>
          <el-input
            v-model="exceptionForm.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="说明现场情况，例如库位放不下、找不到该包、包码破损、货物与任务不一致等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exceptionDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="exceptionSubmitting" @click="submitException">
          提交异常
        </el-button>
      </template>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { RdPackageUnitApi } from '@/api/rd/stock/package'
import { useAppStore } from '@/store/modules/app'
import {
  RdLoadingApi,
  type LoadingTask,
  type LoadingTaskDetail,
  type LoadingTaskLine
} from '@/api/rd/loading'

defineOptions({ name: 'RdDriverLoading' })

const route = useRoute()
const appStore = useAppStore()
const message = useMessage()
const collapseBeforeMobile = ref(appStore.getCollapse)
const collapsedForMobile = ref(false)

type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'

const statusMap: Record<string, { label: string; type: TagType }> = {
  PENDING_ASSIGNMENT: { label: '待派单', type: 'info' },
  ASSIGNED: { label: '待装车', type: 'primary' },
  LOADING: { label: '装车中', type: 'warning' },
  PENDING_REVIEW: { label: '待仓管复核', type: 'warning' },
  EXCEPTION_PENDING: { label: '异常处理中', type: 'danger' },
  OUTBOUNDED: { label: '已出库', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const lineStatusMap: Record<string, { label: string; type: TagType }> = {
  PLANNED: { label: '待搬运', type: 'info' },
  SCANNED: { label: '已扫码', type: 'primary' },
  LOADED: { label: '已装车', type: 'success' },
  OUTBOUNDED: { label: '已出库', type: 'success' },
  EXCEPTION: { label: '异常', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const taskList = ref<LoadingTask[]>([])
const taskTotal = ref(0)
const taskPageNo = ref(1)
const taskLoading = ref(false)
const taskLoadingMore = ref(false)
const detailLoading = ref(false)
const taskDetail = ref<LoadingTaskDetail>()
const highlightedPackageCode = ref('')
const routePackageHandled = ref(false)
const exceptionDialogVisible = ref(false)
const exceptionSubmitting = ref(false)
const exceptionForm = reactive({
  taskLineId: undefined as number | undefined,
  exceptionType: 'LOCATION_MISMATCH',
  observedLocationCode: '',
  description: ''
})

const exceptionTypeOptions = [
  { value: 'MISSING_PACKAGE', label: '现场缺货' },
  { value: 'LOCATION_MISMATCH', label: '库位不符或找不到' },
  { value: 'WRONG_PACKAGE', label: '货物与任务不一致' },
  { value: 'LABEL_DAMAGED', label: '标签/二维码破损' },
  { value: 'VEHICLE_CHANGED', label: '车辆或装车要求变化' },
  { value: 'OTHER', label: '其他异常' }
]

const openExceptions = computed(
  () => taskDetail.value?.exceptions?.filter((item) => item.exceptionStatus === 'OPEN') || []
)

const remainingCount = computed(() =>
  Math.max(
    0,
    Number(taskDetail.value?.task.plannedPackageCount || 0) -
      Number(taskDetail.value?.task.scannedPackageCount || 0)
  )
)

const detailProgress = computed(() => (taskDetail.value ? taskProgress(taskDetail.value.task) : 0))

const locationGroups = computed(() => {
  const groups = new Map<
    string,
    {
      key: string
      locationCode: string
      packageCount: number
      totalWeight: number
      summary: string[]
      lines: LoadingTaskLine[]
    }
  >()
  for (const line of taskDetail.value?.lines || []) {
    const key = line.plannedLocationCode || 'UNKNOWN'
    const group = groups.get(key) || {
      key,
      locationCode: line.plannedLocationCode,
      packageCount: 0,
      totalWeight: 0,
      summary: [],
      lines: []
    }
    const summaryItem = [line.productModel, line.furnaceNo, line.furnaceLayer]
      .filter(Boolean)
      .join(' / ')
    if (summaryItem && !group.summary.includes(summaryItem)) group.summary.push(summaryItem)
    group.packageCount += 1
    group.totalWeight += Number(line.packageWeight || 0)
    group.lines.push(line)
    groups.set(key, group)
  }
  return [...groups.values()].sort((a, b) => a.locationCode.localeCompare(b.locationCode))
})

const statusMeta = (status: string) =>
  statusMap[status] || { label: status, type: 'info' as TagType }

const taskProgress = (task: LoadingTask) => {
  const total = Number(task.plannedPackageCount || 0)
  return total ? Math.round((Number(task.scannedPackageCount || 0) / total) * 100) : 0
}

const lineStatusText = (line: LoadingTaskLine) =>
  lineStatusMap[line.lineStatus]?.label || line.lineStatus || '待搬运'

const lineTagType = (line: LoadingTaskLine) =>
  lineStatusMap[line.lineStatus]?.type || ('info' as TagType)

const weightText = (value?: number) => Number(value || 0).toFixed(2)

const dateTimeText = (value?: string) => {
  if (!value) return '未记录派单时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

const getRouteString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : String(value || '')
}

const normalizePackageCode = (value: string) => {
  const raw = value.trim()
  if (!raw) return ''
  try {
    const url = new URL(raw)
    const parts = url.pathname.split('/').filter(Boolean)
    const pkgIndex = parts.findIndex((part) => part.toLowerCase() === 'pkg')
    return (pkgIndex >= 0 ? parts[pkgIndex + 1] : parts.at(-1) || raw).trim().toUpperCase()
  } catch {
    return raw.trim().toUpperCase()
  }
}

const isHighlighted = (packageCode: string) =>
  Boolean(highlightedPackageCode.value) &&
  normalizePackageCode(packageCode) === highlightedPackageCode.value

const loadTasks = async (reset: boolean) => {
  if (reset) {
    taskLoading.value = true
    taskPageNo.value = 1
  } else {
    taskLoadingMore.value = true
  }
  try {
    const result = await RdLoadingApi.getMyTaskPage({ pageNo: taskPageNo.value, pageSize: 20 })
    taskList.value = reset ? result.list : [...taskList.value, ...result.list]
    taskTotal.value = result.total
  } finally {
    taskLoading.value = false
    taskLoadingMore.value = false
  }
}

const loadMore = async () => {
  taskPageNo.value += 1
  await loadTasks(false)
}

const openTask = async (id: number) => {
  detailLoading.value = true
  taskDetail.value = undefined
  try {
    taskDetail.value = await RdLoadingApi.getMyTask(id)
  } finally {
    detailLoading.value = false
  }
}

const refreshTaskDetail = async () => {
  if (!taskDetail.value) return
  await openTask(taskDetail.value.task.id)
}

const backToList = async () => {
  taskDetail.value = undefined
  highlightedPackageCode.value = ''
  routePackageHandled.value = false
  await loadTasks(true)
}

const openExceptionDialog = () => {
  const matchedLine = taskDetail.value?.lines.find(
    (line) => highlightedPackageCode.value && isHighlighted(line.packageCode)
  )
  exceptionForm.taskLineId = matchedLine?.id
  exceptionForm.exceptionType = 'LOCATION_MISMATCH'
  exceptionForm.observedLocationCode = matchedLine?.plannedLocationCode || ''
  exceptionForm.description = ''
  exceptionDialogVisible.value = true
}

const submitException = async () => {
  if (!taskDetail.value) return
  const description = exceptionForm.description.trim()
  if (!description) {
    message.warning('请填写异常说明')
    return
  }
  const line = taskDetail.value.lines.find((item) => item.id === exceptionForm.taskLineId)
  exceptionSubmitting.value = true
  try {
    await RdLoadingApi.reportException({
      taskId: taskDetail.value.task.id,
      taskLineId: line?.id,
      packageCode: line?.packageCode,
      exceptionType: exceptionForm.exceptionType,
      observedLocationCode: exceptionForm.observedLocationCode || undefined,
      description
    })
    message.success('异常已提交给仓管员处理')
    exceptionDialogVisible.value = false
    await refreshTaskDetail()
  } finally {
    exceptionSubmitting.value = false
  }
}

const openTaskByPackageQuery = async () => {
  const code = normalizePackageCode(getRouteString('packageCode'))
  const requestedId = Number(getRouteString('taskId'))
  if ((!code && !requestedId) || routePackageHandled.value) return
  routePackageHandled.value = true
  highlightedPackageCode.value = code
  const context =
    !Number.isSafeInteger(requestedId) || requestedId <= 0
      ? await RdPackageUnitApi.getWorkContext(code)
      : null
  const taskId =
    Number.isSafeInteger(requestedId) && requestedId > 0
      ? requestedId
      : context?.tasks.find((task) => task.type === 'LOADING' && task.myTask)?.taskId
  if (!taskId) {
    taskDetail.value = undefined
    message.info('该包没有分配给本人的有效装车任务')
    return
  }
  await openTask(taskId)
  if (
    code &&
    !taskDetail.value?.lines.some((line) => normalizePackageCode(line.packageCode) === code)
  ) {
    taskDetail.value = undefined
    message.warning('该包不属于当前装车任务，请重新查询')
  }
}

watch(
  () => appStore.getMobile,
  (mobile) => {
    if (mobile) {
      if (!collapsedForMobile.value) collapseBeforeMobile.value = appStore.getCollapse
      appStore.setCollapse(true)
      collapsedForMobile.value = true
      return
    }
    if (collapsedForMobile.value) {
      appStore.setCollapse(collapseBeforeMobile.value)
      collapsedForMobile.value = false
    }
  },
  { immediate: true }
)

watch(
  () => [route.query.packageCode, route.query.taskId],
  () => {
    routePackageHandled.value = false
    openTaskByPackageQuery()
  }
)

onMounted(async () => {
  await loadTasks(true)
  await openTaskByPackageQuery()
})
</script>

<style scoped lang="scss">
.loading-pda {
  width: min(100%, 760px);
  min-height: calc(100vh - var(--top-tool-height) - var(--tags-view-height) - 40px);
  margin: 0 auto;
  color: var(--el-text-color-primary);
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 76px;
  padding: 8px 0 16px;
  border-bottom: 1px solid var(--el-border-color-light);

  h1 {
    margin: 2px 0 0;
    font-size: 24px;
    line-height: 1.25;
    letter-spacing: 0;
  }
}

.page-kicker {
  margin: 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.header-action {
  width: 42px;
  height: 42px;
  flex: 0 0 42px;
}

.task-list-section,
.task-detail-section {
  min-height: 360px;
}

.list-summary {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 18px 0 12px;
  color: var(--el-text-color-secondary);

  strong {
    font-size: 26px;
    color: var(--el-color-primary);
  }
}

.role-alert {
  margin-bottom: 12px;
}

.task-list {
  display: grid;
  gap: 12px;
}

.task-card {
  width: 100%;
  min-height: 220px;
  padding: 18px;
  overflow: hidden;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease;

  &:hover,
  &:focus-visible {
    border-color: var(--el-color-primary);
    outline: none;
    box-shadow: 0 4px 14px rgb(31 45 61 / 8%);
  }
}

.task-card__head,
.task-card__foot,
.customer-row,
.task-progress > div,
.task-title-band,
.band-heading,
.progress-caption,
.actions-band {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.task-no {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.customer-row {
  justify-content: flex-start;
  min-height: 42px;
  margin: 10px 0;
  color: var(--el-text-color-regular);
  gap: 8px;

  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.task-grid,
.info-band,
.progress-counts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;

  div {
    display: grid;
    gap: 3px;
    min-width: 0;
    padding: 10px 12px;
    background: var(--el-fill-color-lighter);
    border-radius: 6px;
  }

  span {
    overflow: hidden;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  strong {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.task-progress {
  margin-top: 14px;

  span {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  b {
    font-size: 15px;
  }

  :deep(.el-progress) {
    margin-top: 8px;
  }
}

.task-card__foot {
  margin-top: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  gap: 10px;
}

.load-more {
  width: 100%;
  height: 44px;
  margin-top: 14px;
}

.task-title-band {
  min-height: 72px;
  gap: 12px;

  > div {
    display: grid;
    gap: 4px;
    min-width: 0;
  }

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  strong {
    overflow: hidden;
    font-size: 22px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.info-band,
.progress-band,
.locations-band,
.actions-band {
  padding: 18px 0;
  border-bottom: 1px solid var(--el-border-color-light);
}

.band-heading {
  min-height: 34px;
  margin-bottom: 12px;

  h2 {
    margin: 0;
    font-size: 17px;
    line-height: 1.35;
  }

  > span {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
}

.progress-counts {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  margin-bottom: 14px;

  div {
    text-align: center;
  }

  strong {
    font-size: 25px;
  }
}

.progress-caption {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.location-card {
  padding: 14px;
  margin-top: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.location-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;

  > div {
    display: grid;
    gap: 3px;
  }

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  strong {
    font-size: 26px;
    color: var(--el-color-primary);
  }

  > div:last-child {
    text-align: right;
  }
}

.goods-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;

  span {
    min-height: 24px;
    padding: 3px 8px;
    font-size: 12px;
    color: var(--el-text-color-regular);
    background: var(--el-fill-color-light);
    border-radius: 4px;
  }
}

.package-list {
  margin-top: 10px;
}

.package-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  min-height: 58px;
  padding: 8px 0;
  border-top: 1px solid var(--el-border-color-lighter);

  > div {
    display: grid;
    gap: 3px;
    min-width: 0;
  }

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  > div:last-child {
    justify-items: end;
  }

  &.is-highlight {
    padding-inline: 8px;
    background: var(--el-color-primary-light-9);
    border-radius: 6px;
  }
}

.actions-band {
  gap: 10px;

  .el-button {
    flex: 1;
    min-height: 42px;
  }
}

@media (width <= 768px) {
  .loading-pda {
    min-height: calc(100vh - var(--top-tool-height) - 20px);
  }

  .page-header {
    min-height: 68px;
    padding-top: 0;

    h1 {
      font-size: 21px;
    }
  }

  .task-card {
    min-height: 210px;
    padding: 14px;
  }

  .task-grid,
  .info-band {
    grid-template-columns: 1fr;
  }

  .progress-counts {
    grid-template-columns: repeat(3, minmax(0, 1fr));

    div {
      padding-inline: 8px;
    }

    strong {
      font-size: 22px;
    }
  }

  .package-row {
    grid-template-columns: minmax(0, 1fr);

    > div:last-child {
      grid-template-columns: 1fr auto;
      place-items: center start;
    }
  }
}
</style>
