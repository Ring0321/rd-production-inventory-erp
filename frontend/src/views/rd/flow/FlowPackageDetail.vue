<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import QRCode from 'qrcode'
import type {
  RdPackageUnitVO,
  RdPackageTraceVO,
  RdPackageWorkContextVO
} from '@/api/rd/stock/package'
import { extractPackageCode } from '@/utils/rdDigitalLink'
import { formatDate } from '@/utils/formatTime'
import PackageRelationsPanel from '../trace/package/PackageRelationsPanel.vue'
import {
  packageState,
  packageWeightText,
  packageLocation,
  qualityConclusionText
} from './flowFacts'

const props = defineProps<{
  bag: RdPackageUnitVO
  trace?: RdPackageTraceVO
  context?: RdPackageWorkContextVO
  busy: boolean
  error: string
  factsRevision: number
  permitted: (permission: string) => boolean
}>()
const router = useRouter()
const qr = ref('')
const qrError = ref(false)
let generation = 0
const state = computed(() => packageState(props.bag.status, props.context))
watch(
  () => props.trace,
  async (trace) => {
    const attempt = ++generation
    qr.value = ''
    qrError.value = false
    if (!trace || !props.permitted('rd:package-unit:query')) return
    try {
      const link = trace.digitalLink
      if (!/^https?:\/\//i.test(link) || extractPackageCode(link) !== trace.packageCode)
        throw new Error()
      const image = await QRCode.toDataURL(link, {
        width: 256,
        margin: 4,
        errorCorrectionLevel: 'M'
      })
      if (attempt === generation && props.permitted('rd:package-unit:query')) qr.value = image
    } catch {
      if (attempt === generation) qrError.value = true
    }
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  generation++
})
const openTrace = () => {
  if (props.permitted('rd:package-unit:query'))
    void router.push({
      path: `/pkg/${encodeURIComponent(props.bag.packageCode)}`,
      query: { internal: '1' }
    })
}
const openLocation = () => {
  if (
    props.bag.locationId &&
    props.permitted('rd:putaway-task:query') &&
    props.permitted('rd:stock-lot:query')
  )
    void router.push({
      path: '/warehouse-space',
      query: { locationId: String(props.bag.locationId) }
    })
}
const openQuality = () => {
  if (props.trace?.qualityInspectionId && props.permitted('rd:quality-inspection:query'))
    void router.push({
      path: '/rd/quality/inspection',
      query: {
        inspectionId: String(props.trace.qualityInspectionId),
        packageCode: props.bag.packageCode
      }
    })
}
const openTask = (task: NonNullable<RdPackageWorkContextVO['tasks']>[number]) => {
  if (
    !['PUTAWAY', 'LOADING'].includes(task.type) ||
    !Number.isSafeInteger(task.taskId) ||
    task.taskId <= 0
  )
    return
  const kind = task.type === 'PUTAWAY' ? 'putaway' : 'loading'
  if (props.permitted(`rd:${kind}-task:query`))
    void router.push({
      path: `/rd/stock/${kind}`,
      query: { taskId: String(task.taskId), mode: 'view' }
    })
}
const taskState: Record<string, string> = {
  ASSIGNED: '已派单',
  IN_PROGRESS: '执行中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  EXCEPTION: '异常待处理',
  EXCEPTION_PENDING: '异常待处理',
  LOADING: '装车中',
  PENDING_REVIEW: '待出库复核',
  PENDING_CONFIRMATION: '待仓管确认',
  READY: '待执行',
  PENDING: '待确认'
}
const displayTime = (value: unknown) => {
  if (Array.isArray(value) && value.length >= 3) {
    const [y, m, d, h = 0, min = 0, s = 0] = value
    return formatDate(new Date(y, m - 1, d, h, min, s))
  }
  return value ? formatDate(value as string) : '时间未记录'
}
</script>

<template>
  <section class="package-evidence" aria-label="选中包件流转证据" v-loading="busy">
    <header class="package-heading">
      <div
        ><span>当前包件</span><h2>{{ bag.packageCode }}</h2></div
      >
      <el-tag :type="state.tone">{{ state.name }}</el-tag>
      <el-button @click="openTrace"><Icon icon="lucide:arrow-up-right" />完整追溯</el-button>
    </header>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <div class="package-facts">
      <div class="bag-identity">
        <img
          v-if="qr"
          :src="qr"
          width="128"
          height="128"
          :alt="`${bag.packageCode} 原包件二维码`"
        />
        <div v-else class="qr-placeholder">{{ qrError ? '二维码内容待核' : '二维码读取中' }}</div>
        <strong>{{ packageWeightText(bag.packageWeight) }} kg</strong>
      </div>
      <dl class="facts-grid">
        <div
          ><dt>包件登记状态</dt><dd>{{ packageState(bag.status).name }}</dd></div
        >
        <div
          ><dt>型号 / 规格</dt><dd>{{ bag.productModel || '未登记' }}</dd></div
        >
        <div
          ><dt>炉号 / 炉层</dt
          ><dd>{{ bag.furnaceNo || '未登记' }} / {{ bag.furnaceLayer || '未登记' }}</dd></div
        >
        <div
          ><dt>生产批次</dt><dd>记录 #{{ bag.productionOutputId }}</dd></div
        >
        <div
          ><dt>{{ packageLocation(bag).title }}</dt
          ><dd
            ><el-button
              v-if="
                bag.locationId &&
                permitted('rd:putaway-task:query') &&
                permitted('rd:stock-lot:query')
              "
              link
              type="primary"
              @click="openLocation"
              ><Icon icon="lucide:map-pin" />{{ packageLocation(bag).code }}</el-button
            ><span v-else>{{ packageLocation(bag).code }}</span></dd
          ></div
        >
        <div
          ><dt>登记检验结论</dt><dd>{{ qualityConclusionText(trace) }}</dd></div
        >
        <div
          ><dt>检验覆盖 / 实际抽样</dt
          ><dd
            >{{ trace?.qualityCoverageCount ?? '未记录' }} /
            {{ trace?.qualitySampleCount ?? '未记录' }} 包</dd
          ></div
        >
        <div v-if="trace?.qualitySampled !== undefined"
          ><dt>本包检验角色</dt
          ><dd>{{ trace.qualitySampled ? '实际抽样包' : '检验覆盖包（未抽样）' }}</dd></div
        >
        <div v-if="trace?.qualityInspectionId"
          ><dt>质检原单</dt
          ><dd
            ><el-button
              v-if="permitted('rd:quality-inspection:query')"
              link
              type="primary"
              @click="openQuality"
              >检验单 #{{ trace.qualityInspectionId
              }}<Icon icon="lucide:arrow-up-right" /></el-button
            ><span v-else>当前岗位不可查看</span></dd
          ></div
        >
      </dl>
    </div>
    <div v-if="context?.tasks.length" class="task-evidence" aria-label="包件关联作业任务">
      <div v-for="item in context.tasks" :key="`${item.type}-${item.taskId}`">
        <span>{{ item.type === 'PUTAWAY' ? '上架任务' : '整车装车任务' }}</span>
        <el-button
          link
          type="primary"
          :disabled="
            !permitted(item.type === 'PUTAWAY' ? 'rd:putaway-task:query' : 'rd:loading-task:query')
          "
          @click="openTask(item)"
          >{{ item.taskNo }}<Icon icon="lucide:arrow-up-right"
        /></el-button>
        <el-tag :type="item.status === 'EXCEPTION' ? 'danger' : 'info'" size="small">{{
          taskState[item.status] || item.status
        }}</el-tag>
      </div>
    </div>
    <div v-if="trace" class="timeline-section">
      <h3
        ><Icon icon="lucide:history" />实际流转记录 <small>{{ trace.events.length }} 条</small></h3
      >
      <ol v-if="trace.events.length" class="event-timeline">
        <li v-for="(event, index) in trace.events" :key="index">
          <time>{{ displayTime(event.eventTime) }}</time
          ><div
            ><strong>{{ event.title }}</strong
            ><p v-if="event.detail">{{ event.detail }}</p></div
          >
        </li>
      </ol>
      <el-empty v-else description="未登记流转事件" :image-size="60" />
      <PackageRelationsPanel
        :key="bag.packageCode"
        :package-code="bag.packageCode"
        :facts-revision="factsRevision"
      />
    </div>
  </section>
</template>

<style scoped>
.package-evidence {
  padding-top: 22px;
  border-top: 1px solid var(--el-border-color);
}

.package-heading {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
}

.package-heading > div {
  flex: 1;
  min-width: 170px;
}

.package-heading span,
dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

h2 {
  margin: 4px 0 0;
  font-size: 18px;
  line-height: 26px;
  overflow-wrap: anywhere;
}

.package-facts {
  display: grid;
  grid-template-columns: 148px minmax(0, 1fr);
  gap: 22px;
  padding: 16px 0;
}

.bag-identity {
  text-align: center;
}

.bag-identity img,
.qr-placeholder {
  display: block;
  width: 128px;
  height: 128px;
  margin: 0 auto;
}

.qr-placeholder {
  display: flex;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  border: 1px solid var(--el-border-color);
  align-items: center;
  justify-content: center;
}

.bag-identity strong {
  display: block;
  margin-top: 8px;
  font-size: 18px;
}

.facts-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  margin: 0;
  align-content: start;
}

dd {
  min-height: 22px;
  margin: 7px 0 0;
  font-size: 13px;
  overflow-wrap: anywhere;
}

dd :deep(.el-button) {
  height: auto;
  line-height: 22px;
  white-space: normal;
}

.task-evidence {
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.task-evidence > div {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  padding: 5px 0;
  font-size: 13px;
}

h3 {
  display: flex;
  margin: 18px 0;
  font-size: 15px;
  gap: 8px;
  align-items: center;
}

h3 small {
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.event-timeline {
  padding: 0;
  margin: 0;
  list-style: none;
}

.event-timeline li {
  position: relative;
  display: grid;
  padding: 0 0 18px 22px;
  margin-left: 5px;
  border-left: 1px solid #b8cec7;
  grid-template-columns: 160px minmax(0, 1fr);
  gap: 16px;
}

.event-timeline li::before {
  position: absolute;
  top: 3px;
  left: -5px;
  width: 8px;
  height: 8px;
  background: white;
  border: 2px solid #147d70;
  border-radius: 50%;
  content: '';
}

.event-timeline li:last-child {
  border-left-color: transparent;
}

time {
  font-size: 12px;
  line-height: 22px;
  color: var(--el-text-color-secondary);
}

.event-timeline strong {
  font-size: 13px;
  line-height: 22px;
}

.event-timeline p {
  margin: 3px 0 0;
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

@media (width <= 900px) {
  .facts-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 600px) {
  .package-facts {
    grid-template-columns: 1fr;
    gap: 18px;
  }

  .facts-grid {
    gap: 16px 12px;
  }

  .event-timeline li {
    grid-template-columns: 1fr;
    gap: 3px;
  }

  .task-evidence .el-button {
    height: auto;
    max-width: 100%;
    line-height: 22px;
    white-space: normal;
    overflow-wrap: anywhere;
  }
}
</style>
