<template>
  <main class="putaway-pda">
    <header class="page-header">
      <div>
        <p class="page-kicker">叉车司机工作台</p>
        <h1>{{ taskDetail ? '执行上架任务' : '我的上架任务' }}</h1>
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
        <span>待执行</span>
        <strong>{{ taskTotal }}</strong>
        <span>项</span>
      </div>

      <el-empty
        v-if="!taskLoading && !taskList.length"
        :image-size="92"
        description="当前没有分配给你的上架任务"
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
          <div class="location-row">
            <Icon icon="lucide:map-pin" />
            <span>目标库位</span>
            <strong>{{ task.plannedLocationCode || '-' }}</strong>
          </div>
          <div class="task-progress">
            <div>
              <span>包数进度</span>
              <b>{{ task.scannedPackageCount || 0 }} / {{ task.packageCount || 0 }}</b>
            </div>
            <el-progress :percentage="taskProgress(task)" :stroke-width="8" :show-text="false" />
          </div>
          <div class="task-card__foot">
            <span>{{ weightText(task.totalWeight) }} kg</span>
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
      <div class="destination-band">
        <div class="destination-label">
          <Icon icon="lucide:map-pin" />
          <span>目标库位</span>
        </div>
        <strong>{{ taskDetail.task.plannedLocationCode || '-' }}</strong>
        <span class="destination-note">库位由仓管员确定</span>
      </div>

      <div class="task-meta-band">
        <div>
          <span>任务号</span>
          <strong>{{ taskDetail.task.taskNo }}</strong>
        </div>
        <el-tag :type="statusMeta(taskDetail.task.status).type" effect="light">
          {{ statusMeta(taskDetail.task.status).label }}
        </el-tag>
        <el-button
          class="refresh-detail"
          circle
          aria-label="刷新任务详情"
          @click="refreshTaskDetail"
        >
          <Icon icon="lucide:refresh-cw" />
        </el-button>
      </div>

      <div class="progress-band">
        <div class="progress-counts">
          <div>
            <span>已校验</span>
            <strong>{{ validatedCount }}</strong>
          </div>
          <div>
            <span>未校验</span>
            <strong>{{ remainingCount }}</strong>
          </div>
          <div>
            <span>总包数</span>
            <strong>{{ taskDetail.task.packageCount || 0 }}</strong>
          </div>
        </div>
        <el-progress :percentage="detailProgress" :stroke-width="12" />
        <div class="progress-caption">
          <span>系统已记录 {{ confirmedCount }} 包</span>
          <span>仓管核验进度</span>
        </div>
      </div>

      <section class="goods-band" aria-labelledby="goods-heading">
        <h2 id="goods-heading">货物信息</h2>
        <div v-for="group in goodsGroups" :key="group.key" class="goods-row">
          <div class="goods-primary">
            <strong>{{ group.productModel || '未填型号' }}</strong>
            <span>{{ group.customerName || '未填客户' }}</span>
          </div>
          <div class="goods-secondary">
            <span>炉号 {{ group.furnaceNo || '-' }}</span>
            <span>炉层 {{ group.furnaceLayer || '-' }}</span>
          </div>
          <div class="goods-count">
            <strong>{{ group.packageCount }}</strong>
            <span>包 / {{ weightText(group.totalWeight) }} kg</span>
          </div>
        </div>
      </section>

      <el-button type="warning" plain :disabled="!exceptionEnabled" @click="openExceptionDialog">
        <Icon icon="lucide:triangle-alert" class="mr-6px" />异常反馈
      </el-button>

      <el-collapse class="package-collapse">
        <el-collapse-item :title="`任务包码 ${taskDetail.lines.length} 包`" name="packages">
          <div v-for="line in taskDetail.lines" :key="line.id" class="package-row">
            <span>{{ line.packageCode }}</span>
            <span>{{ line.productModel }} / {{ line.furnaceNo }} / {{ line.furnaceLayer }}</span>
            <el-tag :type="packageTagType(line)" size="small" effect="plain">
              {{ packageStatusText(line) }}
            </el-tag>
          </div>
        </el-collapse-item>
      </el-collapse>

      <el-alert
        v-if="taskDetail.exceptions.length"
        class="exception-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="`该任务有 ${taskDetail.exceptions.length} 条异常记录，请按仓管员处理结果执行`"
      />
    </section>

    <Dialog v-model="exceptionVisible" title="上报上架异常" width="min(520px, calc(100vw - 24px))">
      <el-form
        ref="exceptionFormRef"
        :model="exceptionForm"
        :rules="exceptionRules"
        label-position="top"
      >
        <el-form-item label="异常原因" prop="exceptionType">
          <el-select v-model="exceptionForm.exceptionType" class="!w-100%" placeholder="请选择">
            <el-option
              v-for="item in exceptionOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="相关包码" prop="packageCode">
          <el-select
            v-model="exceptionForm.packageCode"
            clearable
            filterable
            placeholder="整任务异常可不选包件"
            class="!w-100%"
          >
            <el-option
              v-for="line in taskDetail?.lines || []"
              :key="line.id"
              :value="line.packageCode"
              :label="`${line.packageCode} · ${line.productModel} / ${line.furnaceLayer}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="现场说明" prop="description">
          <el-input
            v-model="exceptionForm.description"
            type="textarea"
            :rows="4"
            maxlength="1000"
            show-word-limit
            placeholder="说明现场情况，仓管员处理后会重新同步任务"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button type="warning" :loading="exceptionSubmitting" @click="submitException">
          提交异常
        </el-button>
        <el-button @click="exceptionVisible = false">取消</el-button>
      </template>
    </Dialog>
  </main>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { RdPackageUnitApi } from '@/api/rd/stock/package'
import { extractPackageCode } from '@/utils/rdDigitalLink'
import { useAppStore } from '@/store/modules/app'
import {
  RdPutawayTaskApi,
  type RdPutawayExceptionType,
  type RdPutawayTaskDetailVO,
  type RdPutawayTaskLineVO,
  type RdPutawayTaskStatus,
  type RdPutawayTaskVO
} from '@/api/rd/stock/putaway'

defineOptions({ name: 'RdDriverPutaway' })

const route = useRoute()
const appStore = useAppStore()
const message = useMessage()
const collapseBeforeMobile = ref(appStore.getCollapse)
const collapsedForMobile = ref(false)

type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'
const statusMap: Record<RdPutawayTaskStatus, { label: string; type: TagType }> = {
  PENDING_PUTAWAY: { label: '待上架', type: 'info' },
  ASSIGNED: { label: '待执行', type: 'primary' },
  MOVING: { label: '仓管核验中', type: 'warning' },
  PENDING_CONFIRMATION: { label: '待仓管确认', type: 'warning' },
  PUTAWAY_COMPLETED: { label: '已上架', type: 'success' },
  EXCEPTION_PENDING: { label: '异常待处理', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const taskList = ref<RdPutawayTaskVO[]>([])
const taskTotal = ref(0)
const taskPageNo = ref(1)
const taskLoading = ref(false)
const taskLoadingMore = ref(false)
const detailLoading = ref(false)
const taskDetail = ref<RdPutawayTaskDetailVO>()
const exceptionVisible = ref(false)
const exceptionSubmitting = ref(false)
const exceptionFormRef = ref<FormInstance>()
const exceptionForm = reactive<{
  exceptionType?: RdPutawayExceptionType
  packageCode: string
  description: string
}>({
  exceptionType: undefined,
  packageCode: '',
  description: ''
})
const exceptionRules = reactive<FormRules>({
  exceptionType: [{ required: true, message: '请选择异常原因', trigger: 'change' }],
  description: [{ required: true, message: '请填写现场说明', trigger: 'blur' }]
})
const exceptionOptions: Array<{ value: RdPutawayExceptionType; label: string }> = [
  { value: 'LOCATION_CAPACITY_INSUFFICIENT', label: '库位放不下' },
  { value: 'LOCATION_OCCUPIED', label: '库位被占' },
  { value: 'PACKAGE_MISMATCH', label: '货物不符' },
  { value: 'LABEL_DAMAGED', label: '标签损坏' },
  { value: 'PACKAGE_MISSING', label: '货物缺失' },
  { value: 'OTHER', label: '其他' }
]

const confirmedCount = computed(() => {
  if (!taskDetail.value) return 0
  const countFromLines = taskDetail.value.lines.filter(
    (line) => Number(line.scanCount || 0) > 0
  ).length
  return Math.max(Number(taskDetail.value.task.scannedPackageCount || 0), countFromLines)
})

const validatedCount = computed(() => confirmedCount.value)

const exceptionEnabled = computed(() =>
  taskDetail.value ? ['ASSIGNED', 'MOVING'].includes(taskDetail.value.task.status) : false
)

const remainingCount = computed(() =>
  Math.max(0, Number(taskDetail.value?.task.packageCount || 0) - validatedCount.value)
)

const detailProgress = computed(() => {
  const total = Number(taskDetail.value?.task.packageCount || 0)
  return total ? Math.round((validatedCount.value / total) * 100) : 0
})

const goodsGroups = computed(() => {
  const groups = new Map<
    string,
    {
      key: string
      customerName: string
      productModel: string
      furnaceNo: string
      furnaceLayer: string
      packageCount: number
      totalWeight: number
    }
  >()
  for (const line of taskDetail.value?.lines || []) {
    const key = [line.customerName, line.productModel, line.furnaceNo, line.furnaceLayer].join('|')
    const group = groups.get(key) || {
      key,
      customerName: line.customerName,
      productModel: line.productModel,
      furnaceNo: line.furnaceNo,
      furnaceLayer: line.furnaceLayer,
      packageCount: 0,
      totalWeight: 0
    }
    group.packageCount += 1
    group.totalWeight += Number(line.packageWeight || 0)
    groups.set(key, group)
  }
  return [...groups.values()]
})

const statusMeta = (status: RdPutawayTaskStatus) =>
  statusMap[status] || { label: status, type: 'info' as TagType }

const taskProgress = (task: RdPutawayTaskVO) => {
  const total = Number(task.packageCount || 0)
  return total ? Math.round((Number(task.scannedPackageCount || 0) / total) * 100) : 0
}

const weightText = (value?: number) => Number(value || 0).toFixed(2)

const dateTimeText = (value?: string) => {
  if (!value) return '未记录派单时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false })
}

const loadTasks = async (reset: boolean) => {
  if (reset) {
    taskLoading.value = true
    taskPageNo.value = 1
  } else {
    taskLoadingMore.value = true
  }
  try {
    const result = await RdPutawayTaskApi.getMyPage({ pageNo: taskPageNo.value, pageSize: 20 })
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
    taskDetail.value = await RdPutawayTaskApi.getMy(id)
  } finally {
    detailLoading.value = false
  }
}

const refreshTaskDetail = async () => {
  if (!taskDetail.value) return
  detailLoading.value = true
  try {
    taskDetail.value = await RdPutawayTaskApi.getMy(taskDetail.value.task.id)
  } finally {
    detailLoading.value = false
  }
}

const backToList = async () => {
  taskDetail.value = undefined
  await loadTasks(true)
}

const getRouteString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : String(value || '')
}

const openExceptionDialog = () => {
  if (!taskDetail.value || !exceptionEnabled.value) return
  exceptionForm.exceptionType = undefined
  exceptionForm.packageCode = extractPackageCode(getRouteString('packageCode'))
  exceptionForm.description = ''
  exceptionVisible.value = true
  nextTick(() => exceptionFormRef.value?.clearValidate())
}

const submitException = async () => {
  await exceptionFormRef.value?.validate()
  if (!taskDetail.value || !exceptionForm.exceptionType) return
  exceptionSubmitting.value = true
  try {
    await RdPutawayTaskApi.reportException({
      id: taskDetail.value.task.id,
      exceptionType: exceptionForm.exceptionType,
      packageCode: exceptionForm.packageCode.trim() || undefined,
      description: exceptionForm.description.trim()
    })
    message.success('异常已同步给仓管员，任务暂停执行')
    exceptionVisible.value = false
    await refreshTaskDetail()
  } finally {
    exceptionSubmitting.value = false
  }
}

const packageTagType = (line: RdPutawayTaskLineVO): TagType => {
  if (Number(line.scanCount || 0) > 0) return 'success'
  if (line.lineStatus === 'EXCEPTION') return 'danger'
  return 'info'
}

const packageStatusText = (line: RdPutawayTaskLineVO) => {
  if (line.lineStatus === 'CONFIRMED') return '已入库'
  if (Number(line.scanCount || 0) > 0) return '仓管已核验'
  if (line.lineStatus === 'EXCEPTION') return '异常待处理'
  return '待仓管核验'
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

const applyRouteContext = async () => {
  const code = extractPackageCode(getRouteString('packageCode'))
  const requestedId = Number(getRouteString('taskId'))
  if (!code && !requestedId) return
  const context =
    Number.isSafeInteger(requestedId) && requestedId > 0
      ? null
      : await RdPackageUnitApi.getWorkContext(code)
  const id =
    Number.isSafeInteger(requestedId) && requestedId > 0
      ? requestedId
      : context?.tasks.find((task) => task.type === 'PUTAWAY' && task.myTask)?.taskId
  if (!id) {
    taskDetail.value = undefined
    message.info('该包没有分配给本人的上架任务')
    return
  }
  await openTask(id)
  if (
    code &&
    !taskDetail.value?.lines.some((line) => line.packageCode.toUpperCase() === code.toUpperCase())
  ) {
    taskDetail.value = undefined
    message.warning('该包不属于当前上架任务')
  }
}
onMounted(async () => {
  await loadTasks(true)
  await applyRouteContext()
})
watch(() => [route.query.packageCode, route.query.taskId], applyRouteContext)
</script>

<style scoped lang="scss">
.putaway-pda {
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

.header-action,
.refresh-detail {
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

.task-list {
  display: grid;
  gap: 12px;
}

.task-card {
  width: 100%;
  min-height: 190px;
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
.location-row,
.task-progress > div,
.task-meta-band,
.scan-heading-row,
.progress-caption {
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

.location-row {
  min-height: 54px;
  padding: 0 14px;
  margin: 14px 0;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-light);
  border-left: 4px solid var(--el-color-primary);
  justify-content: flex-start;
  gap: 8px;

  strong {
    margin-left: auto;
    font-size: 24px;
    color: var(--el-color-primary);
  }
}

.task-progress {
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

  span:nth-child(2) {
    margin-left: auto;
  }
}

.load-more {
  width: 100%;
  height: 44px;
  margin-top: 14px;
}

.destination-band {
  padding: 22px 0;
  text-align: center;
  border-bottom: 1px solid var(--el-border-color-light);

  > strong {
    display: block;
    margin: 6px 0 2px;
    font-size: 40px;
    line-height: 1.15;
    color: var(--el-color-primary);
  }
}

.destination-label {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
}

.destination-note {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.task-meta-band {
  gap: 12px;
  min-height: 66px;
  border-bottom: 1px solid var(--el-border-color-light);

  > div:first-child {
    display: grid;
    min-width: 0;

    span {
      font-size: 12px;
      color: var(--el-text-color-secondary);
    }

    strong {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

.progress-band,
.goods-band,
.scan-band,
.recent-band {
  padding: 18px 0;
  border-bottom: 1px solid var(--el-border-color-light);

  h2 {
    margin: 0 0 12px;
    font-size: 17px;
    line-height: 1.35;
  }
}

.progress-counts {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 14px;

  div {
    display: grid;
    gap: 3px;
    text-align: center;
  }

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
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

.goods-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  min-height: 68px;
  padding: 10px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.goods-primary,
.goods-secondary,
.goods-count {
  display: grid;
  gap: 4px;

  span {
    overflow: hidden;
    font-size: 12px;
    color: var(--el-text-color-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.goods-count {
  text-align: right;

  strong {
    font-size: 22px;
    color: var(--el-color-primary);
  }
}

.scan-heading-row {
  align-items: flex-start;

  > div:last-child {
    width: 26px;
    height: 26px;
    color: var(--el-color-primary);
  }
}

.mode-chip {
  display: inline-flex;
  min-height: 24px;
  padding: 0 8px;
  font-size: 12px;
  color: var(--el-color-success-dark-2);
  background: var(--el-color-success-light-9);
  border: 1px solid var(--el-color-success-light-5);
  border-radius: 4px;
  align-items: center;
}

.scan-actions {
  display: flex;
  min-height: 46px;
  margin-top: 10px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  > span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.scan-input {
  margin-top: 14px;

  :deep(.el-input__wrapper) {
    min-height: 52px;
  }

  :deep(.el-input-group__append) {
    padding: 0;

    .el-button {
      min-width: 80px;
      min-height: 52px;
      margin: 0;
      border-radius: 0;
    }
  }
}

.scan-feedback {
  display: flex;
  min-height: 70px;
  padding: 12px 14px;
  margin-top: 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  align-items: center;
  gap: 12px;

  > svg,
  > div:first-child {
    width: 26px;
    height: 26px;
    flex: 0 0 26px;
  }

  > div:last-child {
    display: grid;
    gap: 4px;
    min-width: 0;
  }

  strong {
    overflow-wrap: anywhere;
  }

  span {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &.is-success {
    color: var(--el-color-success-dark-2);
    background: var(--el-color-success-light-9);
    border-color: var(--el-color-success-light-5);
  }

  &.is-duplicate {
    color: var(--el-color-warning-dark-2);
    background: var(--el-color-warning-light-9);
    border-color: var(--el-color-warning-light-5);
  }

  &.is-mismatch,
  &.is-empty {
    color: var(--el-color-danger-dark-2);
    background: var(--el-color-danger-light-9);
    border-color: var(--el-color-danger-light-5);
  }

  &.is-status {
    color: var(--el-color-warning-dark-2);
    background: var(--el-color-warning-light-9);
    border-color: var(--el-color-warning-light-5);
  }
}

.recent-row,
.package-row {
  display: grid;
  align-items: center;
  min-height: 52px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.recent-row {
  grid-template-columns: 24px minmax(0, 1fr) auto;
  gap: 10px;

  > div:nth-child(2) {
    display: grid;
    gap: 2px;
    min-width: 0;
  }

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span,
  time {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.package-collapse {
  margin-top: 10px;
}

.package-row {
  grid-template-columns: minmax(100px, 1fr) minmax(130px, 1.4fr) auto;
  gap: 8px;
  font-size: 13px;

  > span:nth-child(2) {
    overflow: hidden;
    color: var(--el-text-color-secondary);
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.exception-alert {
  margin-top: 14px;
}

@media (width <= 768px) {
  .putaway-pda {
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
    min-height: 182px;
    padding: 14px;
  }

  .destination-band > strong {
    font-size: 34px;
  }

  .goods-row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .goods-secondary {
    grid-column: 1 / -1;
    grid-row: 2;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .goods-count {
    grid-column: 2;
    grid-row: 1;
  }

  .scan-input :deep(.el-input-group__append .el-button) {
    min-width: 66px;
  }

  .package-row {
    grid-template-columns: minmax(0, 1fr) auto;
    padding: 8px 0;

    > span:nth-child(2) {
      grid-column: 1 / -1;
      grid-row: 2;
    }
  }
}
</style>
