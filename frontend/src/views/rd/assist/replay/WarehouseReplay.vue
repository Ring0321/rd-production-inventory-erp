<template>
  <section class="warehouse-replay" aria-label="仓库时间回放">
    <el-result v-if="!allowed || !valid" icon="warning" title="当前身份需要重新核对权限">
      <template #extra><el-button @click="reload">重新载入并核对权限</el-button></template>
    </el-result>
    <template v-else>
      <div class="replay-query">
        <el-select
          v-model="selectedTask"
          filterable
          remote
          :remote-method="search"
          :loading="searching"
          aria-label="回放生产任务"
          placeholder="按客户查询生产任务"
          @change="load"
        >
          <el-option
            v-for="task in choices"
            :key="task.id"
            :value="task.id"
            :label="`${task.taskNo} · ${task.customerName}`"
          />
        </el-select>
        <el-tooltip content="刷新流水"
          ><el-button
            circle
            aria-label="刷新回放流水"
            :loading="busy"
            :disabled="!selectedTask"
            @click="load"
            ><Icon icon="lucide:rotate-ccw" /></el-button
        ></el-tooltip>
        <el-button
          v-if="view"
          text
          @click="router.push({ path: '/business-flow', query: { taskId: view.taskId } })"
          ><Icon icon="lucide:workflow" class="mr-5px" />原业务全景</el-button
        >
      </div>
      <el-alert v-if="error" :title="error" type="warning" :closable="false" />
      <el-skeleton v-if="busy" :rows="6" animated />
      <template v-else-if="view">
        <header class="replay-identity"
          ><div
            ><strong>{{ view.taskNo }}</strong
            ><span>{{ view.customerName }}</span></div
          ><span>库存流水登记时间 · 北京时间</span></header
        >
        <div class="replay-stats">
          <dl
            ><dt>任务包件</dt><dd>{{ view.members.length }}</dd></dl
          >
          <dl
            ><dt>在库证据</dt><dd>{{ snapshot.inStockCount }} <small>包</small></dd></dl
          >
          <dl
            ><dt>离库证据</dt><dd>{{ snapshot.outCount }} <small>包</small></dd></dl
          >
          <dl
            ><dt>无可确认库存证据</dt><dd>{{ snapshot.unknownCount }} <small>包</small></dd></dl
          >
          <dl
            ><dt>在库登记净重</dt
            ><dd>{{
              snapshot.weightKg === null ? '待核' : `${format(snapshot.weightKg / 1000)} t`
            }}</dd></dl
          >
        </div>
        <div class="replay-controls">
          <el-tooltip :content="playing ? '暂停' : '逐条回放'"
            ><el-button
              circle
              :disabled="!timed.length"
              :aria-label="playing ? '暂停回放' : '逐条回放流水'"
              @click="togglePlay"
              ><Icon :icon="playing ? 'lucide:pause' : 'lucide:play'" /></el-button
          ></el-tooltip>
          <el-slider
            v-model="cursor"
            :min="0"
            :max="Math.max(timed.length - 1, 1)"
            :disabled="timed.length < 2"
            :format-tooltip="cursorTime"
            aria-label="回放时间"
            @change="stopPlay"
          />
          <time>{{ cursorTime(cursor) }}</time>
        </div>
        <div ref="timelineHost" class="replay-timeline" aria-label="库存登记时间轴"></div>
        <p v-if="undated" class="data-note">{{ undated }} 条流水缺少登记时间，涉及包件保持待核。</p>
        <div class="replay-body">
          <section class="replay-space" aria-label="任务历史库位示意">
            <h2>此时任务包件的库位证据</h2>
            <RdSpatialLocation3D
              v-if="nodes.length && !threeUnavailable"
              :nodes="nodes"
              :selected-id="highlighted"
              :highlighted-ids="highlighted ? [highlighted] : []"
              @inspect="inspectLocation"
              @unavailable="threeUnavailable = true"
            />
            <el-empty
              v-else
              :description="
                threeUnavailable
                  ? '立体视图不可用，请查看右侧库位记录'
                  : '此时没有可关联的在库库位证据'
              "
            />
            <div class="data-note"
              >仅当前任务的流水证据，非全仓库存；沿用当前行列示意，不还原历史厂房布局。</div
            >
          </section>
          <section class="replay-details" aria-label="流水原始证据">
            <h2>流水证据</h2>
            <template v-if="focused">
              <dl class="evidence-grid">
                <dt>包号</dt
                ><dd
                  ><el-button
                    link
                    type="primary"
                    @click="
                      router.push({
                        path: `/pkg/${encodeURIComponent(focused.packageCode)}`,
                        query: { internal: '1' }
                      })
                    "
                    >{{ focused.packageCode }}</el-button
                  ></dd
                >
                <dt>动作</dt><dd>{{ movementText(focused.transactionType) }}</dd> <dt>登记时间</dt
                ><dd>{{ timeText(recordedTime(focused.eventTime)) }}</dd> <dt>业务日期</dt
                ><dd>{{ focused.bizDate || '未登记' }}</dd> <dt>当时库位</dt
                ><dd>{{ focused.locationCode || '未登记' }}</dd> <dt>净重</dt
                ><dd>{{
                  focused.packageWeight == null ? '待核' : `${format(focused.packageWeight)} kg`
                }}</dd>
                <dt>原单据</dt><dd>{{ focused.sourceNo || '未登记' }}</dd> <dt>流水编号</dt
                ><dd>{{ focused.id }}</dd>
              </dl>
              <el-button
                v-if="focused.locationId && canMap"
                @click="
                  router.push({
                    path: '/warehouse-space',
                    query: { locationId: focused.locationId }
                  })
                "
                ><Icon icon="lucide:map-pin" class="mr-5px" />查看当前登记库位</el-button
              >
            </template>
            <el-empty v-else description="没有带时间的库存流水" />
          </section>
        </div>
        <div class="replay-records">
          <h2>在库证据包件 · {{ snapshot.positions.length }} 包</h2>
          <el-table :data="snapshot.positions" max-height="320" empty-text="此时没有在库证据">
            <el-table-column prop="packageCode" label="包号" min-width="180" />
            <el-table-column prop="locationCode" label="流水登记库位" min-width="140"
              ><template #default="{ row }">{{
                row.locationCode || '未知'
              }}</template></el-table-column
            >
            <el-table-column label="净重 / kg" width="130"
              ><template #default="{ row }">{{
                row.weightKg === null ? '待核' : format(row.weightKg)
              }}</template></el-table-column
            >
          </el-table>
        </div>
      </template>
      <el-empty v-else-if="!busy" description="请选择生产任务" />
    </template>
  </section>
</template>
<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { getAccessToken, getAuthContextRevision, onAuthContextChange } from '@/utils/auth'
import { RdProductionTaskApi, type RdProductionTaskVO } from '@/api/rd/production/task'
import { RdPutawayTaskApi, type RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'
import { getWarehouseReplay, type ReplayView } from '@/api/rd/assist/replay'
import { buildSpatialLayout } from '../../stock/location-map/spatialLayout'
import RdSpatialLocation3D from '../../stock/location-map/RdSpatialLocation3D.vue'
import { canViewWarehouseSpace } from '../../stock/location-map/warehouseSpaceAccess'
import {
  canReplay,
  recordedTime,
  replaySnapshot,
  validateReplayEvents,
  escapeTimelineText,
  movementText
} from './replayFacts'
import type { Timeline } from 'vis-timeline/standalone'
import 'vis-timeline/styles/vis-timeline-graph2d.min.css'

const user = useUserStore(),
  router = useRouter(),
  route = useRoute()
const owner = user.getUser.id,
  revision = getAuthContextRevision()
const valid = ref(true),
  allowed = computed(() => canReplay(user.getRoles, user.getPermissions))
const canMap = computed(() => canViewWarehouseSpace(user.getRoles, user.getPermissions))
const selectedTask = ref<number>(),
  choices = ref<RdProductionTaskVO[]>([]),
  view = ref<ReplayView>()
const locations = ref<RdPutawayLocationMapVO[]>([]),
  busy = ref(false),
  searching = ref(false),
  error = ref('')
const cursor = ref(0),
  selectedEvent = ref<number>(),
  highlighted = ref<number>(),
  playing = ref(false),
  threeUnavailable = ref(false)
const timelineHost = ref<HTMLElement>()
let alive = true,
  sequence = 0,
  searchSequence = 0,
  timer: ReturnType<typeof setInterval> | undefined,
  timeline: Timeline | undefined
const current = () =>
  alive &&
  valid.value &&
  allowed.value &&
  !!getAccessToken() &&
  owner === user.getUser.id &&
  revision === getAuthContextRevision()
const stopPlay = () => {
  if (timer) clearInterval(timer)
  timer = undefined
  playing.value = false
}
const disposeTimeline = () => {
  timeline?.destroy()
  timeline = undefined
}
const clear = () => {
  sequence++
  searchSequence++
  stopPlay()
  disposeTimeline()
  view.value = undefined
  locations.value = []
  choices.value = []
  selectedEvent.value = undefined
  highlighted.value = undefined
  busy.value = false
  searching.value = false
}
const invalidate = () => {
  valid.value = false
  clear()
}
const stopAuth = onAuthContextChange(invalidate)
const stopScope = watch(
  [
    () => user.getUser.id,
    () => [...user.getPermissions].sort().join('\0'),
    () => user.getRoles.join('\0')
  ],
  invalidate,
  { flush: 'sync' }
)
const ordered = computed(() =>
  view.value ? validateReplayEvents(view.value.members, view.value.events) : []
)
const timed = computed(() => ordered.value.filter((e) => recordedTime(e.eventTime) !== undefined))
const undated = computed(() => ordered.value.length - timed.value.length)
const at = computed(() => recordedTime(timed.value[cursor.value]?.eventTime) ?? 0)
const snapshot = computed(() =>
  replaySnapshot(view.value?.members || [], view.value?.events || [], at.value)
)
const focused = computed(
  () => ordered.value.find((e) => e.id === selectedEvent.value) || timed.value[cursor.value]
)
const format = (n: number) => n.toLocaleString('zh-CN', { maximumFractionDigits: 6 })
const timeText = (n: number | undefined) =>
  n === undefined
    ? '时间待核'
    : new Date(n).toLocaleString('zh-CN', { hour12: false, timeZone: 'Asia/Shanghai' })
const cursorTime = (index: number) => timeText(recordedTime(timed.value[index]?.eventTime))
const nodes = computed(() => {
  const counts = new Map<number, { count: number; weight: number; unknown: boolean }>()
  for (const p of snapshot.value.positions) {
    if (!p.locationId) continue
    const s = counts.get(p.locationId) || { count: 0, weight: 0, unknown: false }
    s.count++
    if (p.weightKg === null) s.unknown = true
    else s.weight += p.weightKg
    counts.set(p.locationId, s)
  }
  const rows = locations.value
    .filter((l) => counts.has(l.id))
    .map((l) => {
      const s = counts.get(l.id)!
      return {
        ...l,
        occupiedPackages: s.count,
        occupiedWeight: s.unknown ? NaN : s.weight,
        reservedPackages: NaN,
        reservedWeight: NaN,
        capacityPackages: undefined,
        capacityWeight: undefined,
        displayStatus: 'PARTIAL',
        enabled: true,
        frozen: false,
        locationStatus: undefined
      }
    })
  return buildSpatialLayout(rows, 'schematic').groups.flatMap((g, i) =>
    g.nodes.map((n) => ({ ...n, box: { ...n.box, x: n.box.x + i * 780 } }))
  )
})
const drawTimeline = async () => {
  disposeTimeline()
  await nextTick()
  if (!current() || !timelineHost.value || !timed.value.length) return
  const attempt = sequence
  try {
    const { Timeline: TimelineClass } = await import('vis-timeline/standalone')
    if (!current() || attempt !== sequence || !timelineHost.value) return
    timeline = new TimelineClass(
      timelineHost.value,
      timed.value.map((e) => ({
        id: e.id,
        start: new Date(recordedTime(e.eventTime)!),
        content: escapeTimelineText(`${movementText(e.transactionType)} · ${e.packageCode}`),
        className: e.quantity === 1 ? 'replay-in' : 'replay-out'
      })),
      {
        height: '210px',
        editable: false,
        selectable: true,
        multiselect: false,
        stack: true,
        showCurrentTime: false,
        locale: 'zh-cn',
        zoomKey: 'ctrlKey',
        horizontalScroll: true
      }
    )
    timeline.on('select', ({ items }: { items: number[] }) => {
      if (!current() || !items.length) return
      const i = timed.value.findIndex((e) => e.id === items[0])
      if (i >= 0) {
        stopPlay()
        cursor.value = i
        selectedEvent.value = items[0]
      }
    })
    timeline.fit({ animation: false })
  } catch {
    if (current()) error.value = '时间轴暂不可用，可继续查看流水证据和包件清单'
  }
}
watch(cursor, () => {
  selectedEvent.value = undefined
  highlighted.value = timed.value[cursor.value]?.locationId
  timeline?.setSelection(timed.value[cursor.value] ? [timed.value[cursor.value].id] : [])
})
const togglePlay = () => {
  if (playing.value) return stopPlay()
  if (!current() || !timed.value.length) return
  if (cursor.value >= timed.value.length - 1) cursor.value = 0
  playing.value = true
  timer = setInterval(() => {
    if (!current() || cursor.value >= timed.value.length - 1) return stopPlay()
    cursor.value++
  }, 1400)
}
const inspectLocation = (id: number) => {
  highlighted.value = id
  const e = [...timed.value]
    .reverse()
    .find((e) => e.locationId === id && (recordedTime(e.eventTime) || 0) <= at.value)
  selectedEvent.value = e?.id
}
const search = async (customerName = '') => {
  if (!current()) return
  const attempt = ++searchSequence
  searching.value = true
  try {
    const data = await RdProductionTaskApi.getPage({ pageNo: 1, pageSize: 50, customerName })
    if (current() && attempt === searchSequence) choices.value = data.list
  } catch {
    if (current() && attempt === searchSequence) error.value = '生产任务查询失败'
  } finally {
    if (attempt === searchSequence) searching.value = false
  }
}
const load = async () => {
  if (!current() || !selectedTask.value) return
  stopPlay()
  disposeTimeline()
  const id = selectedTask.value,
    attempt = ++sequence
  view.value = undefined
  locations.value = []
  error.value = ''
  busy.value = true
  threeUnavailable.value = false
  try {
    const value = await getWarehouseReplay(id)
    if (!current() || attempt !== sequence) return
    if (value.taskId !== id || value.complete !== true || value.timeBasis !== 'LEDGER_CREATE_TIME')
      throw new Error('历史范围未完整取得')
    validateReplayEvents(value.members, value.events)
    if (canMap.value) {
      const map = await RdPutawayTaskApi.getLocationMap()
      if (!current() || attempt !== sequence) return
      locations.value = map
    }
    if (!current() || attempt !== sequence) return
    view.value = value
    cursor.value = Math.max(
      value.events.filter((e) => recordedTime(e.eventTime) !== undefined).length - 1,
      0
    )
    selectedEvent.value = undefined
    busy.value = false
    await drawTimeline()
  } catch (cause) {
    if (current() && attempt === sequence)
      error.value = cause instanceof Error ? cause.message : '流水读取失败，请刷新核对'
  } finally {
    if (attempt === sequence) busy.value = false
  }
}
const reload = async () => {
  await user.setUserInfoAction()
  window.location.reload()
}
onMounted(async () => {
  await search()
  if (!current()) return
  const query = route.query.taskId
  const id = typeof query === 'string' && /^[1-9]\d*$/.test(query) ? Number(query) : undefined
  selectedTask.value = Number.isSafeInteger(id)
    ? id
    : choices.value.find((t) => t.remark?.includes('DUOXU-DEMO-FLOW-V1'))?.id
  if (selectedTask.value) await load()
})
onBeforeUnmount(() => {
  alive = false
  clear()
  stopAuth()
  stopScope()
})
</script>
<style scoped lang="scss">
.warehouse-replay {
  min-width: 0;
  letter-spacing: 0;
}

.replay-query {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding-bottom: 16px;
}

.replay-query :deep(.el-select) {
  width: 440px;
  max-width: 100%;
}

.replay-identity {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  flex-wrap: wrap;
}

.replay-identity strong {
  font-size: 16px;
}

.replay-identity span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.replay-identity div {
  display: grid;
  gap: 4px;
}

.replay-stats {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  border-block: 1px solid var(--el-border-color-lighter);
}

.replay-stats dl {
  padding: 14px 8px;
  margin: 0;
}

.replay-stats dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.replay-stats dd {
  margin: 7px 0 0;
  font-size: 21px;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.replay-stats small {
  font-size: 12px;
  font-weight: 400;
}

.replay-controls {
  display: grid;
  grid-template-columns: 34px minmax(80px, 1fr) auto;
  align-items: center;
  gap: 16px;
  padding-block: 14px;
}

.replay-controls time {
  font-size: 12px;
}

.replay-timeline {
  width: 100%;
  height: 210px;
}

.replay-timeline :deep(.vis-item) {
  font-size: 11px;
  border-radius: 3px;
}

.replay-timeline :deep(.replay-in) {
  color: #195c44;
  background: #e8f5ee;
  border-color: #72a78a;
}

.replay-timeline :deep(.replay-out) {
  color: #835b1b;
  background: #fff3d8;
  border-color: #c3a05a;
}

.replay-body {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(240px, 1fr);
  gap: 20px;
  padding-block: 20px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.replay-body h2,
.replay-records h2 {
  margin: 0 0 12px;
  font-size: 15px;
}

.replay-space {
  --rd-space-height: 360px;
  --rd-space-mobile-height: 300px;

  min-width: 0;
}

.replay-details {
  min-width: 0;
  padding-left: 20px;
  border-left: 1px solid var(--el-border-color-lighter);
}

.evidence-grid {
  display: grid;
  grid-template-columns: 74px minmax(0, 1fr);
  gap: 14px 10px;
  margin-bottom: 20px;
  font-size: 12px;
}

.evidence-grid dt {
  color: var(--el-text-color-secondary);
}

.evidence-grid dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.evidence-grid :deep(.el-button) {
  height: auto;
  text-align: left;
  white-space: normal;
}

.data-note {
  margin: 10px 0 0;
  font-size: 11px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.replay-records {
  padding-top: 20px;
}

@media (width <= 760px) {
  .replay-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .replay-stats dd {
    font-size: 19px;
  }

  .replay-controls {
    grid-template-columns: 34px minmax(0, 1fr);
    gap: 10px;
  }

  .replay-controls time {
    grid-column: 1 / -1;
    text-align: right;
  }

  .replay-body {
    grid-template-columns: minmax(0, 1fr);
  }

  .replay-details {
    padding-top: 16px;
    padding-left: 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }
}
</style>
