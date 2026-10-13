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
        <el-radio-group v-model="mode" size="small" aria-label="库位视图">
          <el-radio-button value="schematic">自建示意</el-radio-button>
          <el-radio-button value="recorded">已登记行列</el-radio-button>
          <el-radio-button value="list">清单</el-radio-button>
        </el-radio-group>
        <el-select
          v-if="mode !== 'list' && layout.groups.length"
          v-model="groupKey"
          class="group-select"
          aria-label="仓库区域与库位层级"
        >
          <el-option
            v-for="option in layout.groups"
            :key="option.key"
            :label="option.label"
            :value="option.key"
          />
        </el-select>
        <div v-if="mode !== 'list'" class="map-controls">
          <el-tooltip content="放大"
            ><el-button circle aria-label="放大库位图" @click="map?.zoomIn(0.5)"
              ><Icon icon="lucide:plus" /></el-button
          ></el-tooltip>
          <el-tooltip content="缩小"
            ><el-button circle aria-label="缩小库位图" @click="map?.zoomOut(0.5)"
              ><Icon icon="lucide:minus" /></el-button
          ></el-tooltip>
          <el-tooltip content="适配库区"
            ><el-button circle aria-label="适配库区" @click="fit"
              ><Icon icon="lucide:scan" /></el-button
          ></el-tooltip>
          <el-tooltip content="定位任务库位"
            ><el-button
              circle
              :disabled="!resolvedHighlights.length"
              aria-label="定位任务库位"
              @click="focusTargets"
              ><Icon icon="lucide:locate-fixed" /></el-button
          ></el-tooltip>
        </div>
      </header>
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
        v-if="unresolvedTargets"
        :title="`${unresolvedTargets} 个任务库位未能唯一对应，仍以原任务明细为准`"
        type="warning"
        :closable="false"
      />
      <div class="map-workspace" :class="{ 'has-inspector': !!activeLocation }">
        <div class="map-main">
          <div
            v-if="mode !== 'list'"
            ref="mapHost"
            class="map-surface"
            aria-label="二维库位示意图"
          ></div>
          <div v-if="mode !== 'list' && !group?.nodes.length" class="map-empty"
            ><el-empty description="该范围暂无可布置库位" :image-size="70"
          /></div>
          <div v-if="mode === 'list'" class="bin-list">
            <button
              v-for="location in locations"
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
            <el-empty v-if="!locations.length" description="暂无库位" :image-size="70" />
          </div>
        </div>
        <aside
          v-if="activeLocation"
          class="map-inspector"
          :aria-label="`库位资料 ${activeLocation.locationCode}`"
        >
          <div class="inspector-heading"
            ><h3>{{ activeLocation.locationCode }}</h3
            ><el-button circle aria-label="关闭库位资料" @click="activeId = undefined"
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
            :key="activeLocation.id"
            :location-id="activeLocation.id"
            @task="$emit('task', $event)"
          />
        </aside>
      </div>
      <details v-if="mode === 'recorded' && layout.unplaced.length" class="unplaced-list"
        ><summary>位置待核库位（{{ layout.unplaced.length }}）</summary
        ><button
          v-for="(entry, index) in layout.unplaced"
          :key="index"
          type="button"
          @click="inspect(entry.location.id)"
          >{{ entry.location.locationCode }} ·
          {{
            entry.reason === 'POSITION_CONFLICT'
              ? '行列重复'
              : entry.reason === 'IDENTITY_INVALID'
                ? '身份待核'
                : '行列未登记'
          }}</button
        ></details
      >
      <footer class="map-note"
        ><span>{{
          mode === 'recorded'
            ? '已登记行列示意，非实测比例或自动定位'
            : '自建示意：图上排布不是现场位置，不写回ERP行列'
        }}</span
        ><a :href="`${base}licenses/leaflet-LICENSE.txt`" target="_blank" rel="noopener noreferrer"
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
  type SpatialNode
} from './spatialLayout'
import LocationDetailsPanel from './LocationDetailsPanel.vue'
const props = withDefaults(
  defineProps<{
    locations: RdPutawayLocationMapVO[]
    selectedId?: number
    highlightedIds?: number[]
    highlightedCodes?: string[]
    canSelect?: boolean
    isSelectable?: (location: RdPutawayLocationMapVO) => boolean
  }>(),
  { highlightedIds: () => [], highlightedCodes: () => [], canSelect: false }
)
const emit = defineEmits<{ select: [location: RdPutawayLocationMapVO]; task: [id: number] }>()
const base = import.meta.env.BASE_URL
const user = useUserStoreWithOut()
const ownerId = user.getUser.id
const mode = ref<'schematic' | 'recorded' | 'list'>('schematic')
const groupKey = ref('')
const activeId = ref<number>()
const contextValid = ref(!!getAccessToken())
const ownerRevision = getAuthContextRevision()
const mapHost = ref<HTMLElement>()
const mapError = ref('')
const layout = computed(() =>
  buildSpatialLayout(props.locations, mode.value === 'recorded' ? 'recorded' : 'schematic')
)
const group = computed(() => layout.value.groups.find((g) => g.key === groupKey.value))
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
const states = [
  { code: 'EMPTY', label: '空闲', fill: '#f8faf9', stroke: '#81958c', tag: 'info' as const },
  {
    code: 'PARTIAL',
    label: '部分占用',
    fill: '#e2f1e8',
    stroke: '#3f8f65',
    tag: 'success' as const
  },
  { code: 'FULL', label: '已满', fill: '#fff2cf', stroke: '#b88b2c', tag: 'warning' as const },
  {
    code: 'BLOCKED',
    label: '冻结 / 不可用',
    fill: '#fce7e7',
    stroke: '#b85050',
    tag: 'danger' as const
  },
  { code: 'UNKNOWN', label: '状态待核', fill: '#f1f2f4', stroke: '#9097a2', tag: 'info' as const }
]
const status = (location: RdPutawayLocationMapVO) =>
  states.find(
    (s) =>
      s.code ===
      (!location.enabled ||
      location.frozen ||
      ['FROZEN', 'DISABLED', 'MAINTENANCE', 'RESTRICTED'].includes(location.displayStatus)
        ? 'BLOCKED'
        : location.displayStatus)
  ) || states[4]
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
const isMapMode = () => mode.value !== 'list'
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
  if (valid() && isLocationIdentityValid(props.locations, id)) {
    activeId.value = id
  }
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
  if (map && bounds) map.fitBounds(bounds, { padding: [20, 20], maxZoom: 0.5, animate: false })
}
const focusTargets = () => {
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
      L.rectangle(nodeBounds(node), {
        color: chosen ? '#111827' : highlighted ? '#216fba' : look.stroke,
        weight: chosen || highlighted ? 3 : 1.5,
        fillColor: look.fill,
        fillOpacity: 1,
        dashArray: highlighted ? '6 3' : undefined
      })
        .on('click', () => inspect(node.location.id))
        .addTo(layer!)
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
  activeId.value = undefined
  destroy()
  await paint()
  fit()
})
watch(
  () => props.locations,
  async () => {
    activeId.value = undefined
    await paint()
    fit()
  },
  { deep: true }
)
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
const invalidateContext = () => {
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
  alive = false
  destroy()
  stopAuth()
  stopScope()
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

  .map-surface {
    height: 320px;
  }

  .group-select {
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
