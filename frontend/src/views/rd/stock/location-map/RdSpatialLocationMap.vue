<template>
  <section class="spatial-location" aria-label="库位空间视图">
    <el-alert
      v-if="!contextValid"
      title="登录上下文已变化，请重新进入库位页面"
      type="warning"
      :closable="false"
    />
    <template v-else>
      <header class="map-toolbar">
        <el-radio-group
          v-if="!showcase || queryExpanded"
          v-model="mode"
          size="small"
          aria-label="库位视图"
          @change="cancelLookup"
        >
          <el-radio-button value="schematic">自建示意</el-radio-button>
          <el-radio-button value="recorded">已登记行列</el-radio-button>
          <el-radio-button value="list">清单</el-radio-button>
        </el-radio-group>
        <template v-if="dimension === '3d' && mode !== 'list' && tierOptions.length > 1">
          <el-radio-group
            v-if="overview && groupKey === OVERVIEW_KEY && !configuredLayout"
            v-model="tierView"
            size="small"
            aria-label="层级展示方式"
            @change="cancelLookup"
          >
            <el-radio-button value="stacked">上下叠层</el-radio-button>
            <el-radio-button value="spread">分层平铺</el-radio-button>
          </el-radio-group>
          <el-select
            v-model="activeTier"
            class="tier-select"
            aria-label="查看库位层级"
            :empty-values="[null, undefined]"
            @change="cancelLookup"
          >
            <el-option label="全部层级" value="" />
            <el-option
              v-for="tier in tierOptions"
              :key="tier.value"
              :label="tier.label"
              :value="tier.value"
            />
          </el-select>
        </template>
        <el-radio-group
          v-if="mode !== 'list'"
          v-model="dimension"
          size="small"
          aria-label="空间维度"
          @change="cancelLookup"
        >
          <el-radio-button value="2d">平面</el-radio-button>
          <el-radio-button value="3d">立体</el-radio-button>
        </el-radio-group>
        <el-select
          v-if="mode !== 'list' && layout.groups.length && (!showcase || queryExpanded)"
          v-model="groupKey"
          class="group-select"
          aria-label="仓库区域与库位层级"
          @change="cancelLookup"
        >
          <el-option v-if="overview" label="全仓总览" :value="OVERVIEW_KEY" />
          <el-option
            v-for="option in layout.groups"
            :key="option.key"
            :label="option.label"
            :value="option.key"
          />
        </el-select>
        <form
          v-if="canLookupPackage && (!showcase || queryExpanded)"
          class="package-search"
          @submit.prevent="packageLookup.lookup()"
        >
          <el-input
            v-model="packageQuery"
            placeholder="包码 / 二维码内容"
            aria-label="包码或二维码内容"
            clearable
          />
          <el-tooltip content="定位登记库位">
            <el-button native-type="submit" :loading="lookupBusy" circle aria-label="定位货物库位"
              ><Icon icon="lucide:locate-fixed"
            /></el-button>
          </el-tooltip>
        </form>
        <div v-if="mode !== 'list'" class="map-controls">
          <el-tooltip v-if="!showcase || queryExpanded" content="放大"
            ><el-button circle aria-label="放大库位图" @click="zoom(true)"
              ><Icon icon="lucide:plus" /></el-button
          ></el-tooltip>
          <el-tooltip v-if="!showcase || queryExpanded" content="缩小"
            ><el-button circle aria-label="缩小库位图" @click="zoom(false)"
              ><Icon icon="lucide:minus" /></el-button
          ></el-tooltip>
          <el-tooltip v-if="dimension === '3d'" content="复位立体视角">
            <el-button circle aria-label="复位立体视角" @click="threeView?.iso()"
              ><Icon icon="lucide:rotate-ccw"
            /></el-button>
          </el-tooltip>
          <el-tooltip v-if="dimension === '3d'" content="俯视库位">
            <el-button circle aria-label="俯视库位" @click="threeView?.top()"
              ><Icon icon="lucide:panel-top"
            /></el-button>
          </el-tooltip>
          <el-tooltip content="适配库区"
            ><el-button circle aria-label="适配库区" @click="fit"
              ><Icon icon="lucide:scan" /></el-button
          ></el-tooltip>
          <el-tooltip v-if="!showcase || resolvedHighlights.length" content="定位任务库位"
            ><el-button
              circle
              :disabled="!resolvedHighlights.length"
              aria-label="定位任务库位"
              @click="focusTargets"
              ><Icon icon="lucide:locate-fixed" /></el-button
          ></el-tooltip>
        </div>
        <el-tooltip v-if="showcase" :content="queryExpanded ? '收起筛选与查货' : '筛选与查货'">
          <el-button
            circle
            aria-label="筛选与查货"
            :aria-expanded="queryExpanded"
            @click="toggleQuery"
            ><Icon icon="ep:filter"
          /></el-button>
        </el-tooltip>
      </header>
      <div v-if="!showcase || queryExpanded" class="map-querybar">
        <el-select
          :model-value="activeId"
          filterable
          clearable
          class="location-search"
          placeholder="查找库位"
          aria-label="查找并定位库位"
          @change="selectLocation"
          @clear="closeInspector"
        >
          <el-option
            v-for="location in validLocations"
            :key="location.id"
            :label="`${location.locationCode} · ${location.warehouseName}`"
            :value="location.id"
          />
        </el-select>
        <el-radio-group
          v-model="filter"
          size="small"
          aria-label="库位状态筛选"
          class="map-filters"
          @change="cancelLookup"
        >
          <el-radio-button value="ALL">全部</el-radio-button>
          <el-radio-button value="EMPTY">空闲</el-radio-button>
          <el-radio-button value="OCCUPIED">占用</el-radio-button>
          <el-radio-button value="RESERVED">预留</el-radio-button>
          <el-radio-button value="BLOCKED">不可用</el-radio-button>
          <el-radio-button value="UNKNOWN">待核</el-radio-button>
        </el-radio-group>
      </div>
      <dl class="map-summary" aria-label="当前范围库位统计">
        <div
          ><dt>库位</dt><dd>{{ summary.total }}</dd></div
        >
        <div
          ><dt>匹配</dt><dd>{{ matchingLocations.length }}</dd></div
        >
        <div
          ><dt>空闲</dt><dd>{{ summary.empty }}</dd></div
        >
        <div
          ><dt>预留库位</dt><dd>{{ summary.reserved }}</dd></div
        >
        <div
          ><dt>在库包数</dt><dd>{{ metricText(summary.packages) }}</dd></div
        >
        <div
          ><dt>登记吨数</dt><dd>{{ metricText(summary.weight, 1000) }}</dd></div
        >
      </dl>
      <el-alert
        v-if="lookupFeedback"
        :title="lookupFeedback"
        type="info"
        :closable="false"
        class="lookup-feedback"
      />
      <div class="map-legend">
        <span v-for="state in states" :key="state.code"
          ><i :style="{ background: state.fill, borderColor: state.stroke }"></i
          >{{ state.label }}</span
        >
        <span><i class="target-key"></i>任务 / 已选计划位</span>
        <span>预留数量另列</span>
      </div>
      <el-alert v-if="mapError" :title="mapError" type="warning" :closable="false" />
      <el-alert
        v-if="threeUnavailable"
        title="当前设备立体视图不可用，已切回平面"
        type="warning"
        :closable="false"
      />
      <el-alert
        v-if="unresolvedTargets"
        :title="`${unresolvedTargets} 个任务库位未能唯一对应，仍以原任务明细为准`"
        type="warning"
        :closable="false"
      />
      <div
        class="map-workspace"
        :class="{ 'has-inspector': !!activeLocation, 'is-3d': dimension === '3d' }"
      >
        <div class="map-main">
          <div
            v-if="mode !== 'list' && dimension === '2d'"
            ref="mapHost"
            class="map-surface"
            aria-label="二维库位示意图"
          ></div>
          <RdSpatialLocation3D
            v-if="mode !== 'list' && dimension === '3d'"
            ref="threeView"
            :nodes="threeNodes"
            :selected-id="activeId"
            :highlighted-ids="resolvedHighlights"
            :matching-ids="matchingIds"
            @inspect="inspect"
            @unavailable="fallbackToPlane"
          />
          <div v-if="mode !== 'list' && !group?.nodes.length" class="map-empty"
            ><el-empty description="该范围暂无可布置库位" :image-size="70"
          /></div>
          <div v-if="mode === 'list'" class="bin-list">
            <button
              v-for="location in matchingLocations"
              :key="location.id"
              type="button"
              :class="{
                focused: activeId === location.id,
                target: resolvedHighlights.includes(location.id)
              }"
              @click="inspect(location.id)"
            >
              <strong>{{ location.locationCode }}</strong
              ><span>{{ status(location).label }}</span>
              <span
                >在库 {{ metricText(location.occupiedPackages) }} · 预留
                {{ metricText(location.reservedPackages) }} 包</span
              >
            </button>
            <el-empty
              v-if="!matchingLocations.length"
              description="当前条件无匹配库位"
              :image-size="70"
            />
          </div>
          <div
            v-if="mode !== 'list' && group?.nodes.length && !matchingLocations.length"
            class="map-empty filter-empty"
          >
            <span>当前条件无匹配库位</span>
          </div>
        </div>
        <aside
          v-if="activeLocation"
          class="map-inspector"
          :aria-label="`库位资料 ${activeLocation.locationCode}`"
        >
          <div class="inspector-heading"
            ><h3>{{ activeLocation.locationCode }}</h3
            ><el-button circle aria-label="关闭库位资料" @click="closeInspector"
              ><Icon icon="lucide:x" /></el-button
          ></div>
          <p
            >{{ activeLocation.warehouseName }} /
            {{ activeLocation.areaName || activeLocation.areaCode || '未登记区域' }}</p
          >
          <el-tag :type="status(activeLocation).tag">{{ status(activeLocation).label }}</el-tag>
          <dl class="bin-metrics">
            <div
              ><dt>在库</dt
              ><dd
                >{{ metricText(activeLocation.occupiedPackages) }} 包 /
                {{ metricText(activeLocation.occupiedWeight, 1000) }} 吨</dd
              ></div
            >
            <div
              ><dt>预留</dt
              ><dd
                >{{ metricText(activeLocation.reservedPackages) }} 包 /
                {{ metricText(activeLocation.reservedWeight, 1000) }} 吨</dd
              ></div
            >
            <div
              ><dt>可用容量</dt
              ><dd
                >{{ capacityText(activeLocation.availablePackages, '包') }} /
                {{ capacityText(activeLocation.availableWeight, '吨', 1000) }}</dd
              ></div
            >
            <div
              ><dt>登记行列层</dt
              ><dd
                >{{ activeLocation.rowCode || '未登记' }} /
                {{ activeLocation.columnCode || '未登记' }} /
                {{ activeLocation.levelCode || '未登记' }}</dd
              ></div
            >
          </dl>
          <el-button
            v-if="canSelect && hasPermission(['rd:putaway-task:create'])"
            type="primary"
            :disabled="!isSelectable?.(activeLocation)"
            @click="choosePlan"
            ><Icon icon="lucide:map-pin" class="mr-6px" />设为计划库位</el-button
          >
          <LocationDetailsPanel
            :key="`${activeLocation.id}:${snapshotVersion || 0}`"
            :location-id="activeLocation.id"
            @task="$emit('task', $event)"
          />
        </aside>
      </div>
      <details v-if="mode !== 'list' && scopedUnplaced.length" class="unplaced-list"
        ><summary
          >{{
            configuredLayout && mode === 'schematic' ? '未配置发布位置库位' : '位置待核库位'
          }}（{{ scopedUnplaced.length }}）</summary
        ><button
          v-for="(entry, index) in scopedUnplaced"
          :key="index"
          type="button"
          :disabled="!matchingIds.includes(entry.location.id)"
          @click="inspect(entry.location.id)"
          >{{ entry.location.locationCode }} ·
          {{
            entry.reason === 'POSITION_CONFLICT'
              ? '行列重复'
              : entry.reason === 'IDENTITY_INVALID'
                ? '身份待核'
                : configuredLayout && mode === 'schematic'
                  ? '未配置发布位置'
                  : '行列未登记'
          }}</button
        ></details
      >
      <footer class="map-note"
        ><span>{{
          configuredLayout && mode === 'schematic'
            ? '已发布布局示意，非实测位置或比例，不写回ERP行列'
            : overview && groupKey === OVERVIEW_KEY
              ? '全仓自建示意，库区间距非实测，不写回库位'
              : mode === 'recorded'
                ? '已登记行列示意，非实测比例或自动定位'
                : '自建示意：图上排布不是现场位置，不写回ERP行列'
        }}</span
        ><span v-if="dimension === '3d' && mode !== 'list'">吨袋为占用图示，非实物数量或堆高</span>
        <a
          v-if="dimension === '3d' && mode !== 'list'"
          :href="`${base}licenses/three-LICENSE.txt`"
          target="_blank"
          rel="noopener noreferrer"
          >Three.js 0.186.1 · MIT</a
        >
        <a
          v-if="dimension === '3d' && mode !== 'list'"
          :href="`${base}licenses/smart-warehouse-LICENSE.txt`"
          target="_blank"
          rel="noopener noreferrer"
          >视觉参考 · MIT</a
        >
        <a
          v-else
          :href="`${base}licenses/leaflet-LICENSE.txt`"
          target="_blank"
          rel="noopener noreferrer"
          >Leaflet 1.9.4 · BSD-2-Clause</a
        ></footer
      >
    </template>
  </section>
</template>
<script setup lang="ts">
import {
  ref,
  computed,
  watch,
  nextTick,
  onMounted,
  onBeforeUnmount,
  onActivated,
  onDeactivated
} from 'vue'
import type * as Leaflet from 'leaflet'
import 'leaflet/dist/leaflet.css'
import type { RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useUserStoreWithOut } from '@/store/modules/user'
import { getAccessToken, getAuthContextRevision, onAuthContextChange } from '@/utils/auth'
import {
  buildSpatialLayout,
  uniqueLocationIdsForCodes,
  metricText,
  capacityText,
  isLocationIdentityValid,
  spatialGroupKey,
  type SpatialNode
} from './spatialLayout'
import LocationDetailsPanel from './LocationDetailsPanel.vue'
import RdSpatialLocation3D from './RdSpatialLocation3D.vue'
import { LOCATION_STATES, locationLook as status } from './locationLook'
import { matchesLocationFilter, summarizeLocations, type LocationFilter } from './warehouseMapQuery'
import { useMapPackageLookup } from './useMapPackageLookup'
import { OVERVIEW_KEY } from './warehouseOverview'
import { buildTieredWarehouseOverview, warehouseTierOptions } from './warehouseTiers'
import {
  applyPublishedLayout,
  buildLayoutOverview,
  type PublishedWarehouseLayout
} from './publishedLayout'
const props = withDefaults(
  defineProps<{
    locations: RdPutawayLocationMapVO[]
    selectedId?: number
    highlightedIds?: number[]
    highlightedCodes?: string[]
    canSelect?: boolean
    isSelectable?: (location: RdPutawayLocationMapVO) => boolean
    overview?: boolean
    showcase?: boolean
    initialDimension?: '2d' | '3d'
    snapshotVersion?: number
    retainSelectionOnRefresh?: boolean
    configuredLayout?: PublishedWarehouseLayout
  }>(),
  { highlightedIds: () => [], highlightedCodes: () => [], canSelect: false }
)
const emit = defineEmits<{
  select: [location: RdPutawayLocationMapVO]
  task: [id: number]
  scope: [key: string]
}>()
const base = import.meta.env.BASE_URL
const user = useUserStoreWithOut()
const ownerId = user.getUser.id
const mode = ref<'schematic' | 'recorded' | 'list'>('schematic')
const dimension = ref<'2d' | '3d'>(props.initialDimension || '2d')
const threeUnavailable = ref(false)
const threeView = ref<InstanceType<typeof RdSpatialLocation3D>>()
const groupKey = ref(props.overview ? OVERVIEW_KEY : '')
const filter = ref<LocationFilter>('ALL')
const tierView = ref<'stacked' | 'spread'>('stacked')
const activeTier = ref('')
const queryExpanded = ref(!props.showcase)
const activeId = ref<number>()
const contextValid = ref(!!getAccessToken())
const ownerRevision = getAuthContextRevision()
const mapHost = ref<HTMLElement>()
const mapError = ref('')
const layout = computed(() => {
  const generated = buildSpatialLayout(
    props.locations,
    mode.value === 'recorded' ? 'recorded' : 'schematic'
  )
  return props.configuredLayout && mode.value === 'schematic'
    ? applyPublishedLayout(generated, props.configuredLayout)
    : generated
})
const group = computed(() =>
  props.overview && groupKey.value === OVERVIEW_KEY
    ? buildLayoutOverview(
        layout.value.groups,
        mode.value === 'schematic' ? props.configuredLayout : undefined
      )
    : layout.value.groups.find((g) => g.key === groupKey.value)
)
const validLocations = computed(() =>
  props.locations.filter((l) => isLocationIdentityValid(props.locations, l.id))
)
const tierOptions = computed(() => warehouseTierOptions(validLocations.value))
const threeNodes = computed(() => {
  const nodes =
    props.overview &&
    groupKey.value === OVERVIEW_KEY &&
    tierView.value === 'stacked' &&
    !props.configuredLayout
      ? buildTieredWarehouseOverview(layout.value.groups).nodes
      : group.value?.nodes || []
  return nodes.filter(
    (node) => !activeTier.value || node.location.levelCode?.trim() === activeTier.value
  )
})
const rangeLocations = computed(() =>
  (mode.value === 'list' || (props.overview && groupKey.value === OVERVIEW_KEY)
    ? validLocations.value
    : validLocations.value.filter((l) => !!group.value && spatialGroupKey(l) === group.value.key)
  ).filter(
    (location) =>
      dimension.value !== '3d' ||
      mode.value === 'list' ||
      !activeTier.value ||
      location.levelCode?.trim() === activeTier.value
  )
)
const matchingLocations = computed(() =>
  rangeLocations.value.filter((l) => matchesLocationFilter(l, filter.value))
)
const matchingIds = computed(() => matchingLocations.value.map((l) => l.id))
const summary = computed(() => summarizeLocations(rangeLocations.value))
const scopedUnplaced = computed(() =>
  layout.value.unplaced.filter(
    ({ location: l }) =>
      !group.value || groupKey.value === OVERVIEW_KEY || spatialGroupKey(l) === group.value.key
  )
)
const activeLocation = computed(() =>
  activeId.value && isLocationIdentityValid(props.locations, activeId.value)
    ? props.locations.find((l) => l.id === activeId.value)
    : undefined
)
const resolvedHighlights = computed(() => [
  ...new Set([
    ...props.highlightedIds,
    ...uniqueLocationIdsForCodes(props.locations, props.highlightedCodes),
    ...(props.selectedId ? [props.selectedId] : [])
  ])
])
const unresolvedTargets = computed(
  () =>
    [...new Set(props.highlightedCodes.filter(Boolean))].filter(
      (code) => props.locations.filter((l) => l.locationCode === code).length !== 1
    ).length +
    props.highlightedIds.filter((id) => props.locations.filter((l) => l.id === id).length !== 1)
      .length
)
const states = LOCATION_STATES
let L: typeof Leaflet | undefined
let map: Leaflet.Map | undefined
let layer: Leaflet.LayerGroup | undefined
let bounds: Leaflet.LatLngBounds | undefined
let observer: ResizeObserver | undefined
let alive = true
let active = true
let revision = 0
const markers: Array<{ marker: Leaflet.Marker; width: number; height: number }> = []
const valid = () =>
  alive &&
  active &&
  contextValid.value &&
  getAuthContextRevision() === ownerRevision &&
  user.getUser.id === ownerId &&
  hasPermission(['rd:putaway-task:query'])
const isMapMode = () => mode.value !== 'list' && dimension.value === '2d'
const destroy = () => {
  revision++
  observer?.disconnect()
  observer = undefined
  map?.remove()
  map = undefined
  layer = undefined
  bounds = undefined
  markers.length = 0
}
const inspect = (id: number) => {
  if (valid() && matchingIds.value.includes(id) && isLocationIdentityValid(props.locations, id)) {
    cancelLookup()
    activeId.value = id
  }
}
let focusSequence = 0
const focusLocation = async (id: number, guard: () => boolean = valid) => {
  const version = ++focusSequence
  if (!valid() || !guard() || !isLocationIdentityValid(props.locations, id)) return
  filter.value = 'ALL'
  activeTier.value = ''
  if (!layout.value.groups.some((g) => g.nodes.some((n) => n.location.id === id)))
    mode.value = 'schematic'
  await nextTick()
  if (!valid() || !guard() || version !== focusSequence) return
  const pending = layout.value.unplaced.find((entry) => entry.location.id === id)
  const target = layout.value.groups.find(
    (g) =>
      g.nodes.some((n) => n.location.id === id) ||
      (pending && g.key === spatialGroupKey(pending.location))
  )
  if (!target) return
  groupKey.value = target.key
  await nextTick()
  if (!valid() || !guard() || version !== focusSequence) return
  activeId.value = id
  await nextTick()
  if (!valid() || !guard() || version !== focusSequence) return
  if (dimension.value === '3d') threeView.value?.focus([id])
  else if (map && L) {
    const node = group.value?.nodes.find((n) => n.location.id === id)
    if (node)
      map.fitBounds(L.latLngBounds(nodeBounds(node)), {
        padding: [30, 30],
        maxZoom: 0.5,
        animate: false
      })
  }
}
const canLookupPackage = computed(
  () => valid() && hasPermission(['rd:package-unit:query']) && hasPermission(['rd:stock-lot:query'])
)
const packageLookup = useMapPackageLookup({
  allowed: () => canLookupPackage.value,
  locations: () => props.locations,
  locate: focusLocation
})
const { raw: packageQuery, busy: lookupBusy, feedback: lookupFeedback } = packageLookup
const cancelLookup = () => {
  focusSequence++
  packageLookup.clear()
}
const toggleQuery = () => {
  cancelLookup()
  queryExpanded.value = !queryExpanded.value
}
const selectLocation = (id: number) => {
  cancelLookup()
  void focusLocation(id)
}
const closeInspector = () => {
  cancelLookup()
  activeId.value = undefined
}
const choosePlan = () => {
  if (
    valid() &&
    props.canSelect &&
    activeLocation.value &&
    isLocationIdentityValid(props.locations, activeLocation.value.id) &&
    hasPermission(['rd:putaway-task:create']) &&
    props.isSelectable?.(activeLocation.value)
  )
    emit('select', activeLocation.value)
}
const fit = () => {
  if (dimension.value === '3d') {
    threeView.value?.fit()
    return
  }
  if (map && bounds) map.fitBounds(bounds, { padding: [20, 20], maxZoom: 0.5, animate: false })
}
const zoom = (inward: boolean) => {
  if (dimension.value === '3d') threeView.value?.zoom(inward ? 0.8 : 1.25)
  else if (inward) map?.zoomIn(0.5)
  else map?.zoomOut(0.5)
}
const fallbackToPlane = () => {
  threeUnavailable.value = true
  dimension.value = '2d'
}
const focusTargets = () => {
  if (dimension.value === '3d') {
    threeView.value?.focus(resolvedHighlights.value)
    return
  }
  if (!map || !L) return
  const nodes =
    group.value?.nodes.filter((n) => resolvedHighlights.value.includes(n.location.id)) || []
  if (nodes.length)
    map.fitBounds(L.latLngBounds(nodes.flatMap((n) => nodeBounds(n))), {
      padding: [30, 30],
      maxZoom: 0.5,
      animate: false
    })
}
const nodeBounds = (node: SpatialNode): Leaflet.LatLngTuple[] => [
  [node.box.y - node.box.height, node.box.x],
  [node.box.y, node.box.x + node.box.width]
]
const labels = () => {
  const scale = map ? 2 ** map.getZoom() : 1
  for (const item of markers) {
    const el = item.marker.getElement()
    if (!el) continue
    const width = Math.max(0, Math.min(146, item.width * scale - 6))
    const height = Math.max(0, Math.min(84, item.height * scale - 6))
    el.style.width = `${width}px`
    el.style.height = `${height}px`
    el.style.marginLeft = `${-width / 2}px`
    el.style.marginTop = `${-height / 2}px`
    el.style.visibility = width >= 44 && height >= 28 ? 'visible' : 'hidden'
    for (const detail of el.querySelectorAll('span')) detail.hidden = height < 64
  }
}
const paint = async () => {
  await nextTick()
  if (!valid() || !isMapMode() || !mapHost.value) return
  const version = ++revision
  try {
    if (!L) L = await import('leaflet')
    if (!valid() || version !== revision || !mapHost.value || !isMapMode()) return
    if (!map) {
      map = L.map(mapHost.value, {
        crs: L.CRS.Simple,
        center: [0, 0],
        zoom: 0,
        minZoom: -5,
        maxZoom: 2,
        zoomSnap: 0.25,
        zoomControl: false,
        attributionControl: false,
        zoomAnimation: false,
        keyboard: true
      })
      layer = L.layerGroup().addTo(map)
      map.on('zoomend', labels)
      let previousWidth = 0
      let previousHeight = 0
      observer = new ResizeObserver(([entry]) => {
        const { width, height } = entry.contentRect
        if (!map || !valid() || width <= 0 || height <= 0) return
        map.invalidateSize({ pan: false })
        if (width !== previousWidth || height !== previousHeight) {
          previousWidth = width
          previousHeight = height
          fit()
        }
      })
      observer.observe(mapHost.value)
    }
    layer?.clearLayers()
    markers.length = 0
    const nodes = group.value?.nodes || []
    bounds = nodes.length ? L.latLngBounds(nodes.flatMap((n) => nodeBounds(n))) : undefined
    for (const node of nodes) {
      const highlighted = resolvedHighlights.value.includes(node.location.id)
      const chosen = activeId.value === node.location.id
      const look = status(node.location)
      const matching = matchingIds.value.includes(node.location.id)
      L.rectangle(nodeBounds(node), {
        color: chosen ? '#111827' : highlighted ? '#216fba' : look.stroke,
        weight: chosen || highlighted ? 3 : 1.5,
        fillColor: look.fill,
        fillOpacity: matching ? 1 : 0.12,
        opacity: matching ? 1 : 0.2,
        dashArray: highlighted ? '6 3' : undefined
      })
        .on('click', () => {
          if (matching) inspect(node.location.id)
        })
        .addTo(layer!)
      if (!matching) continue
      const button = document.createElement('button')
      button.type = 'button'
      button.className = 'spatial-bin-label'
      button.dataset.locationId = String(node.location.id)
      button.setAttribute('aria-label', `查看库位 ${node.location.locationCode}`)
      button.title = `${node.location.locationCode} · ${look.label}`
      const code = document.createElement('strong')
      code.textContent = node.location.locationCode
      const state = document.createElement('span')
      state.textContent = look.label
      const count = document.createElement('span')
      count.textContent = `在库 ${metricText(node.location.occupiedPackages)} · 预留 ${metricText(node.location.reservedPackages)}`
      button.append(code, state, count)
      const marker = L.marker([node.box.y - node.box.height / 2, node.box.x + node.box.width / 2], {
        icon: L.divIcon({
          html: button,
          className: 'spatial-bin-icon',
          iconSize: [146, 84],
          iconAnchor: [73, 42]
        }),
        keyboard: false
      })
        .on('click', () => inspect(node.location.id))
        .addTo(layer!)
      markers.push({ marker, width: node.box.width, height: node.box.height })
    }
    labels()
    mapError.value = ''
  } catch {
    if (valid()) mapError.value = '库位平面图暂不可用，可切换清单查看'
  }
}
watch(
  () => layout.value.groups,
  (groups) => {
    if (props.overview && groupKey.value === OVERVIEW_KEY) return
    if (!groups.some((g) => g.key === groupKey.value))
      groupKey.value =
        groups.find((g) => g.nodes.some((n) => resolvedHighlights.value.includes(n.location.id)))
          ?.key ||
        groups[0]?.key ||
        ''
  },
  { immediate: true }
)
watch([groupKey, mode], async () => {
  activeTier.value = ''
  if (props.overview) emit('scope', groupKey.value)
  activeId.value = undefined
  destroy()
  await paint()
  fit()
})
watch(dimension, async () => {
  if (dimension.value === '3d') threeUnavailable.value = false
  destroy()
  await paint()
  fit()
})
watch(
  [() => props.locations, () => props.configuredLayout],
  async () => {
    if (!props.retainSelectionOnRefresh || !matchingIds.value.includes(activeId.value || 0))
      activeId.value = undefined
    await paint()
    if (!props.retainSelectionOnRefresh) fit()
  },
  { deep: true }
)
watch(() => props.snapshotVersion, cancelLookup, { flush: 'sync' })
watch(
  () => resolvedHighlights.value.join(','),
  () => {
    if (
      resolvedHighlights.value.length &&
      !group.value?.nodes.some((n) => resolvedHighlights.value.includes(n.location.id))
    ) {
      const target = layout.value.groups.find((g) =>
        g.nodes.some((n) => resolvedHighlights.value.includes(n.location.id))
      )
      if (target) groupKey.value = target.key
    }
    void paint()
  }
)
watch(activeId, () => {
  void paint()
})
watch(filter, () => {
  activeId.value = undefined
  void paint()
})
watch(activeTier, () => {
  activeId.value = undefined
})
watch(tierOptions, (options) => {
  if (activeTier.value && !options.some((option) => option.value === activeTier.value))
    activeTier.value = ''
})
const invalidateContext = () => {
  cancelLookup()
  contextValid.value = false
  activeId.value = undefined
  destroy()
}
const stopAuth = onAuthContextChange(invalidateContext)
const stopScope = watch(
  [() => user.getUser.id, () => [...user.getPermissions].sort().join('\0')],
  invalidateContext,
  { flush: 'sync' }
)
onMounted(async () => {
  await paint()
  fit()
})
onDeactivated(() => {
  cancelLookup()
  active = false
  activeId.value = undefined
  destroy()
})
onActivated(async () => {
  active = true
  if (valid()) {
    await paint()
    fit()
  }
})
onBeforeUnmount(() => {
  cancelLookup()
  alive = false
  destroy()
  stopAuth()
  stopScope()
})
defineExpose({
  focusLocation: async (id: number) => {
    if (valid() && isLocationIdentityValid(props.locations, id)) await focusLocation(id)
  },
  showGroup: async (key: string) => {
    if (
      !valid() ||
      (!layout.value.groups.some((g) => g.key === key) && !(props.overview && key === OVERVIEW_KEY))
    )
      return
    cancelLookup()
    activeId.value = undefined
    groupKey.value = key
    await nextTick()
    fit()
  }
})
</script>
<style scoped lang="scss">
.spatial-location {
  min-width: 0;
  letter-spacing: 0;
}

.map-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.group-select {
  width: 300px;
  max-width: 100%;
}

.tier-select {
  width: 150px;
  max-width: 100%;
}

.map-controls {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.map-controls :deep(.el-button),
.inspector-heading :deep(.el-button) {
  margin-left: 0;
}

.map-legend {
  display: flex;
  margin: 0 0 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  flex-wrap: wrap;
  gap: 8px 16px;
}

.map-querybar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.location-search {
  width: 250px;
  max-width: 100%;
}

.package-search {
  display: flex;
  width: 260px;
  max-width: 100%;
  gap: 6px;
}

.package-search :deep(.el-button) {
  flex-shrink: 0;
  margin-left: 0;
}

.lookup-feedback {
  margin-bottom: 10px;
}

.map-filters {
  flex-wrap: wrap;
}

.map-summary {
  display: flex;
  padding: 10px 0;
  margin: 0 0 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-wrap: wrap;
  gap: 12px 28px;
}

.map-summary div {
  min-width: 48px;
}

.map-summary dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.map-summary dd {
  margin: 4px 0 0;
  font-size: 16px;
  font-weight: 600;
}

.filter-empty {
  align-items: end;
  padding-bottom: 20px;
  font-size: 12px;
}

.map-legend span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.map-legend i {
  display: inline-block;
  width: 12px;
  height: 12px;
  border: 1px solid;
}

.target-key {
  background: #eef5fd;
  border: 2px dashed #216fba !important;
}

.map-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  border-top: 1px solid var(--el-border-color-light);
}

.map-workspace.has-inspector {
  grid-template-columns: minmax(0, 1fr) 300px;
}

.map-main {
  position: relative;
  min-width: 0;
}

.map-surface {
  z-index: 0;
  width: 100%;
  height: 420px;
  background: #fff;
  isolation: isolate;
}

.map-empty {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  pointer-events: none;
}

.map-inspector {
  max-height: 420px;
  min-width: 0;
  padding: 16px;
  overflow: auto;
  border-left: 1px solid var(--el-border-color-light);
}

.is-3d .map-inspector {
  max-height: 520px;
}

.inspector-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.inspector-heading h3 {
  margin: 4px 0 0;
  font-size: 16px;
  overflow-wrap: anywhere;
}

.map-inspector p {
  margin: 6px 0 10px;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.bin-metrics {
  margin: 12px 0;
  font-size: 12px;
}

.bin-metrics div {
  padding: 7px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.bin-metrics dt {
  color: var(--el-text-color-secondary);
}

.bin-metrics dd {
  margin: 3px 0 0;
  line-height: 18px;
  overflow-wrap: anywhere;
}

.bin-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 8px;
  padding: 12px 0;
}

.bin-list button {
  display: grid;
  gap: 6px;
  padding: 12px;
  text-align: left;
  cursor: pointer;
  background: #fff;
  border: 1px solid #d4dadf;
  border-radius: 4px;
  overflow-wrap: anywhere;
}

.bin-list button.focused {
  border-color: #111827;
}

.bin-list button.target {
  outline: 2px dashed #216fba;
}

.map-note {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 6px 16px;
  padding: 10px 0 0;
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
}

.map-note a {
  color: var(--el-color-primary);
}

.unplaced-list {
  padding-top: 12px;
  font-size: 12px;
}

.unplaced-list button {
  display: inline-block;
  padding: 7px;
  margin: 6px 6px 0 0;
  cursor: pointer;
  background: #fff;
  border: 1px solid #d4dadf;
  border-radius: 4px;
}

:deep(.spatial-bin-icon) {
  background: transparent;
  border: 0;
}

:deep(.spatial-bin-label) {
  display: grid;
  align-content: center;
  gap: 4px;
  width: 100%;
  height: 100%;
  padding: 4px;
  font-family: inherit;
  font-size: 12px;
  line-height: 16px;
  color: #24342b;
  text-align: center;
  cursor: pointer;
  background: transparent;
  border: 0;
}

:deep(.spatial-bin-label strong) {
  overflow: hidden;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.spatial-bin-label:focus-visible) {
  outline: 2px solid #216fba;
  outline-offset: -4px;
}

@media (width <= 760px) {
  .map-workspace.has-inspector {
    grid-template-columns: minmax(0, 1fr);
  }

  .map-inspector {
    max-height: none;
    border-top: 1px solid var(--el-border-color-light);
    border-left: 0;
  }

  .is-3d .map-inspector {
    max-height: none;
  }

  .map-surface {
    height: 320px;
  }

  .group-select {
    width: 100%;
  }

  .location-search {
    width: 100%;
  }

  .package-search {
    width: 100%;
  }

  .map-toolbar :deep(.el-radio-group) {
    flex-wrap: wrap;
  }

  .map-controls {
    margin-left: 0;
  }
}
</style>
