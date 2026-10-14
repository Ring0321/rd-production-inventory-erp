<script setup lang="ts">
import { computed, ref, shallowRef, nextTick, watch, onBeforeUnmount, onDeactivated } from 'vue'
import { useRouter } from 'vue-router'
import type { Graph as GraphType } from '@antv/g6'
import { hasPermission } from '@/directives/permission/hasPermi'
import { getAccessToken, getAuthContextRevision } from '@/utils/auth'
import { useUserStore } from '@/store/modules/user'
import { useQrAuthBoundary } from '../../stock/scan/useQrAuthBoundary'
import { getPackageRelations, type PackageRelations } from '@/api/rd/stock/package/relations'
import {
  validateRelations,
  relationView,
  relationKinds,
  relationPalette,
  qualityAffectedPackages,
  relationNodeLabel,
  relationLabels,
  qualitySampleLabel
} from './packageRelations'

const props = defineProps<{ packageCode: string; factsRevision: number }>()
const router = useRouter()
const user = useUserStore()
const ownerRevision = getAuthContextRevision()
const authBoundary = useQrAuthBoundary(() => {
  visible.value = false
  close()
  error.value = ''
  busy.value = false
})
const permitted = computed(
  () =>
    authBoundary.current() &&
    !!getAccessToken() &&
    getAuthContextRevision() === ownerRevision &&
    (!user.getRoles.includes('rd_forklift_driver') || user.getRoles.includes('super_admin')) &&
    hasPermission(['rd:package-unit:query'])
)
const visible = ref(false)
const mode = ref<'ROOT' | 'RELATED' | 'QUALITY'>('ROOT')
const qualityKey = ref('')
const data = shallowRef<PackageRelations>()
const selectedKey = ref('')
const error = ref('')
const busy = ref(false)
const frame = ref<HTMLElement>()
const rendered = ref(false)
const selectedNode = computed(() =>
  data.value?.nodes.find((node) => node.nodeKey === selectedKey.value)
)
const affected = computed(() =>
  data.value && selectedNode.value ? qualityAffectedPackages(data.value, selectedNode.value) : []
)
const viewCounts = computed(() =>
  data.value ? relationView(data.value, mode.value, qualityKey.value) : undefined
)
const limited = computed(
  () => data.value?.sections.filter((section) => section.state !== 'COMPLETE') || []
)
let graph: GraphType | undefined
let observer: ResizeObserver | undefined
let generation = 0
let paintGeneration = 0
let alive = true
const current = () => alive && visible.value && authBoundary.current() && permitted.value
const disposeGraph = () => {
  paintGeneration++
  observer?.disconnect()
  observer = undefined
  if (graph && !graph.destroyed) graph.destroy()
  graph = undefined
  rendered.value = false
}
const highlight = async () => {
  if (!graph || graph.destroyed || !data.value) return
  const view = relationView(data.value, mode.value, qualityKey.value)
  const active = new Set([selectedKey.value, ...affected.value.map((node) => node.nodeKey)])
  const states = Object.fromEntries(
    view.nodes.map((node) => [node.nodeKey, active.has(node.nodeKey) ? ['selected'] : []])
  )
  await graph.setElementState(states, false)
}
const selectNode = (key: string) => {
  if (!current() || !data.value?.nodes.some((node) => node.nodeKey === key)) return
  selectedKey.value = key
  let repaint = false
  if (selectedNode.value?.kind === 'QUALITY_INSPECTION') {
    repaint = mode.value !== 'QUALITY' || qualityKey.value !== key
    qualityKey.value = key
    mode.value = 'QUALITY'
  } else if (mode.value === 'QUALITY' && selectedNode.value?.kind !== 'PACKAGE') {
    repaint = true
    mode.value = 'ROOT'
  }
  if (!repaint)
    void highlight().catch(() => {
      if (current()) error.value = '关系高亮失败，可查看右侧资料'
    })
}
const paint = async () => {
  disposeGraph()
  if (!current() || !data.value || !frame.value) return
  const attempt = paintGeneration
  const source = data.value
  const view = relationView(source, mode.value, qualityKey.value)
  let created: GraphType | undefined
  try {
    const { Graph } = await import('@antv/g6')
    if (!current() || attempt !== paintGeneration || !frame.value) return
    created = new Graph({
      container: frame.value,
      width: frame.value.clientWidth,
      height: 420,
      animation: false,
      padding: 32,
      data: {
        nodes: view.nodes.map((node) => ({
          id: node.nodeKey,
          data: { kind: node.kind },
          style: {
            labelText: `${relationKinds[node.kind]}\n${relationNodeLabel(node)}`,
            ...relationPalette(node, source.rootNodeKey)
          }
        })),
        edges: view.edges.map((edge) => ({
          id: edge.edgeKey,
          source: edge.fromNodeKey,
          target: edge.toNodeKey,
          style: {
            labelText:
              edge.relation === 'QUALITY_COVERAGE'
                ? qualitySampleLabel(source, edge.fromNodeKey, edge.toNodeKey)
                : relationLabels[edge.relation] || edge.label,
            lineDash:
              edge.relation.includes('HISTOR') || edge.relation.includes('PLANNED')
                ? [4, 4]
                : undefined
          }
        }))
      },
      node: {
        type: 'rect',
        style: {
          size: [192, 76],
          radius: 4,
          lineWidth: 1.5,
          labelFontSize: 12,
          labelPlacement: 'center',
          labelFill: '#22313d',
          labelWordWrap: true,
          labelWordWrapWidth: 174,
          labelMaxLines: 3
        },
        state: { selected: { lineWidth: 3, stroke: '#117a65' } }
      },
      edge: {
        type: 'polyline',
        style: {
          stroke: '#a9b6c0',
          endArrow: true,
          labelFontSize: 10,
          labelFill: '#5a6974',
          labelBackground: true,
          labelBackgroundFill: '#ffffff',
          labelPadding: [2, 4]
        }
      },
      layout: { type: 'antv-dagre', rankdir: 'LR', nodesep: 22, ranksep: 68 },
      behaviors: ['drag-canvas', 'zoom-canvas', 'drag-element']
    })
    graph = created
    created.on('node:click', (event) => {
      if (
        'target' in event &&
        event.target &&
        'id' in event.target &&
        typeof event.target.id === 'string'
      )
        selectNode(event.target.id)
    })
    await created.render()
    if (!current() || attempt !== paintGeneration) {
      if (!created.destroyed) created.destroy()
      return
    }
    await created.fitView({}, false)
    if (selectedNode.value?.kind === 'QUALITY_INSPECTION' && mode.value === 'RELATED') {
      await created.zoomTo(Math.max(created.getZoom(), 0.65), false)
      await created.focusElement(
        [selectedKey.value, ...affected.value.map((node) => node.nodeKey)],
        false
      )
    }
    await highlight()
    if (!current() || attempt !== paintGeneration) return
    rendered.value = true
    observer = new ResizeObserver(() => {
      if (current() && graph && !graph.destroyed && frame.value)
        graph.resize(frame.value.clientWidth, 420)
    })
    observer.observe(frame.value)
  } catch {
    if (created && !created.destroyed) created.destroy()
    if (current() && attempt === paintGeneration) {
      graph = undefined
      error.value = '关系图初始化失败，已保留节点资料清单'
    }
  }
}
const load = async () => {
  if (!current()) return
  const attempt = ++generation
  const code = props.packageCode
  busy.value = true
  error.value = ''
  data.value = undefined
  disposeGraph()
  try {
    const value = validateRelations(await getPackageRelations(code), code)
    if (!current() || attempt !== generation || props.packageCode !== code) return
    data.value = value
    selectedKey.value = value.rootNodeKey
    await nextTick()
    await paint()
  } catch (cause) {
    if (current() && attempt === generation)
      error.value = cause instanceof Error ? cause.message : '关系资料读取失败'
  } finally {
    if (attempt === generation) busy.value = false
  }
}
const close = () => {
  generation++
  disposeGraph()
  data.value = undefined
  selectedKey.value = ''
  qualityKey.value = ''
  mode.value = 'ROOT'
}
watch([mode, qualityKey], () => {
  if (current() && data.value) void paint()
})
watch(visible, (value) => {
  if (!value) close()
})
watch(
  () => [
    props.packageCode,
    props.factsRevision,
    permitted.value,
    user.getRoles.join(','),
    [...user.getPermissions].join(',')
  ],
  () => {
    close()
    if (!permitted.value) visible.value = false
    else if (visible.value) void load()
  }
)
const zoom = (factor: number) => {
  if (graph && !graph.destroyed) void graph.zoomTo(graph.getZoom() * factor, false)
}
const fit = () => {
  if (graph && !graph.destroyed) void graph.fitView({}, false)
}
const openSelected = () => {
  const node = selectedNode.value
  if (!current() || !node) return
  if (node.kind === 'PACKAGE' && node.packageCode) {
    visible.value = false
    void router.push({
      path: `/pkg/${encodeURIComponent(node.packageCode)}`,
      query: { internal: '1' }
    })
    return
  }
  if (
    ['POSITION', 'LOCATION'].includes(node.kind) &&
    node.locationId &&
    hasPermission(['rd:putaway-task:query']) &&
    hasPermission(['rd:stock-lot:query'])
  ) {
    visible.value = false
    void router.push({ path: '/warehouse-space', query: { locationId: String(node.locationId) } })
    return
  }
  const links: Record<string, { path: string; permission: string; query: string }> = {
    LOADING_TASK: {
      path: '/rd/stock/loading',
      permission: 'rd:loading-task:query',
      query: 'taskId'
    },
    PUTAWAY_TASK: {
      path: '/rd/stock/putaway',
      permission: 'rd:putaway-task:query',
      query: 'taskId'
    },
    QUALITY_INSPECTION: {
      path: '/rd/quality/inspection',
      permission: 'rd:quality-inspection:query',
      query: 'inspectionId'
    },
    PRODUCTION_OUTPUT: {
      path: '/rd/production/output',
      permission: 'rd:production-output:query',
      query: 'productionOutputId'
    }
  }
  const link = links[node.kind]
  if (link && node.referenceId && hasPermission([link.permission])) {
    visible.value = false
    void router.push({ path: link.path, query: { [link.query]: String(node.referenceId) } })
  }
}
const canOpenSelected = computed(() => {
  const node = selectedNode.value
  if (!node) return false
  if (node.kind === 'PACKAGE') return !!node.packageCode
  if (['POSITION', 'LOCATION'].includes(node.kind))
    return (
      !!node.locationId &&
      hasPermission(['rd:putaway-task:query']) &&
      hasPermission(['rd:stock-lot:query'])
    )
  return (
    !!node.referenceId &&
    ['LOADING_TASK', 'PUTAWAY_TASK', 'QUALITY_INSPECTION', 'PRODUCTION_OUTPUT'].includes(node.kind)
  )
})
onDeactivated(() => {
  visible.value = false
  close()
})
onBeforeUnmount(() => {
  alive = false
  close()
})
</script>
<template>
  <section v-if="permitted" class="relations-entry" aria-label="货物追溯关系入口">
    <h2><Icon icon="lucide:workflow" />货物关联</h2
    ><el-button @click="visible = true"><Icon icon="lucide:git-fork" />查看追溯关系图</el-button>
  </section>
  <el-dialog
    v-model="visible"
    title="货物追溯关系"
    width="min(1420px, 98vw)"
    :close-on-click-modal="false"
    destroy-on-close
    @opened="load"
  >
    <section
      v-if="permitted"
      v-loading="busy"
      class="relations-workspace"
      aria-label="货物追溯关系图"
    >
      <el-alert v-if="error" :title="error" type="warning" :closable="false" role="alert" />
      <header class="relations-toolbar"
        ><strong>{{ packageCode }}</strong
        ><el-radio-group v-model="mode" size="small"
          ><el-radio-button value="ROOT">当前包主线</el-radio-button
          ><el-radio-button v-if="qualityKey" value="QUALITY">质检覆盖</el-radio-button
          ><el-radio-button value="RELATED">全部关联</el-radio-button></el-radio-group
        ><span v-if="viewCounts"
          >{{ viewCounts.nodes.length }} 个节点 · {{ viewCounts.edges.length }} 条证据连接</span
        ><div class="graph-tools"
          ><el-tooltip content="放大"
            ><el-button circle aria-label="放大关系图" :disabled="!rendered" @click="zoom(1.2)"
              ><Icon icon="lucide:plus" /></el-button></el-tooltip
          ><el-tooltip content="缩小"
            ><el-button circle aria-label="缩小关系图" :disabled="!rendered" @click="zoom(1 / 1.2)"
              ><Icon icon="lucide:minus" /></el-button></el-tooltip
          ><el-tooltip content="适配视图"
            ><el-button circle aria-label="适配关系图" :disabled="!rendered" @click="fit"
              ><Icon icon="lucide:scan" /></el-button></el-tooltip
          ><el-tooltip content="重新读取"
            ><el-button circle aria-label="重新读取关系" :disabled="busy" @click="load"
              ><Icon icon="lucide:refresh-cw" /></el-button></el-tooltip></div
      ></header>
      <div v-if="limited.length" class="relation-limits" role="status"
        ><span v-for="section in limited" :key="section.kind">{{
          section.reason || `${section.kind}：${section.state}`
        }}</span></div
      >
      <div class="relations-grid">
        <div ref="frame" class="relations-canvas" role="img" aria-label="真实记录关系画布"></div>
        <aside class="relation-detail" aria-label="关系节点资料">
          <el-select
            :model-value="selectedKey"
            filterable
            aria-label="选择关系节点"
            placeholder="选择节点"
            @update:model-value="selectNode"
            ><el-option
              v-for="node in data?.nodes || []"
              :key="node.nodeKey"
              :label="`${relationKinds[node.kind]} · ${node.label}`"
              :value="node.nodeKey"
          /></el-select>
          <template v-if="selectedNode"
            ><h3>{{ relationKinds[selectedNode.kind] }}</h3
            ><strong class="node-title">{{ selectedNode.label }}</strong
            ><dl
              ><div v-if="selectedNode.status"
                ><dt>记录状态</dt><dd>{{ selectedNode.status }}</dd></div
              ><div v-if="selectedNode.referenceId"
                ><dt>来源记录</dt><dd>{{ selectedNode.referenceId }}</dd></div
              ><div v-if="selectedNode.locationCode"
                ><dt>库位</dt><dd>{{ selectedNode.locationCode }}</dd></div
              ></dl
            ><p v-if="selectedNode.description">{{ selectedNode.description }}</p
            ><el-button v-if="canOpenSelected" link type="primary" @click="openSelected"
              ><Icon icon="lucide:arrow-up-right" />{{
                ['POSITION', 'LOCATION'].includes(selectedNode.kind)
                  ? '查看登记库位空间'
                  : '打开原资料'
              }}</el-button
            >
            <template v-if="selectedNode.kind === 'QUALITY_INSPECTION'"
              ><h4>已加载实际覆盖 {{ affected.length }} 包</h4
              ><ul class="affected-list"
                ><li v-for="node in affected" :key="node.nodeKey"
                  ><el-button link @click="selectNode(node.nodeKey)">{{
                    node.packageCode
                  }}</el-button
                  ><span>{{ node.status || '状态待核' }}</span
                  ><el-tag
                    v-if="data"
                    size="small"
                    :type="
                      qualitySampleLabel(data, selectedNode.nodeKey, node.nodeKey) === '抽检样包'
                        ? 'warning'
                        : 'info'
                    "
                    >{{ qualitySampleLabel(data, selectedNode.nodeKey, node.nodeKey) }}</el-tag
                  ></li
                ></ul
              ></template
            ></template
          >
          <ul v-if="data && !rendered" class="node-fallback"
            ><li v-for="node in data.nodes" :key="node.nodeKey"
              ><el-button link @click="selectNode(node.nodeKey)">{{ node.label }}</el-button></li
            ></ul
          >
        </aside>
      </div>
    </section>
  </el-dialog>
</template>
<style scoped>
.relations-entry {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 18px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.relations-entry h2 {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  font-size: 18px;
}

.relations-workspace {
  min-width: 0;
}

.relations-toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.relations-toolbar strong {
  overflow-wrap: anywhere;
}

.relations-toolbar span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.relations-toolbar :deep(.el-radio-button__inner) {
  min-width: 80px;
  padding: 7px 10px;
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
}

.graph-tools {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.graph-tools :deep(.el-button + .el-button) {
  margin-left: 0;
}

.relation-limits {
  display: flex;
  padding: 8px 0;
  font-size: 12px;
  color: #8d5a14;
  flex-wrap: wrap;
  gap: 8px;
}

.relations-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
}

.relations-canvas {
  height: 420px;
  min-width: 0;
  overflow: hidden;
  background: #f7fafb;
  border: 1px solid var(--el-border-color-lighter);
}

.relation-detail {
  max-height: 420px;
  min-width: 0;
  padding-left: 14px;
  overflow: auto;
  border-left: 1px solid var(--el-border-color-lighter);
}

.relation-detail :deep(.el-select) {
  width: 100%;
}

.relation-detail h3 {
  margin: 16px 0 8px;
  font-size: 15px;
}

.relation-detail h4 {
  margin: 14px 0 8px;
  font-size: 13px;
}

.node-title {
  display: block;
  overflow-wrap: anywhere;
  font-size: 14px;
}

.relation-detail dl {
  font-size: 12px;
}

.relation-detail dl div {
  display: flex;
  gap: 10px;
  margin: 8px 0;
}

.relation-detail dt {
  min-width: 56px;
  color: var(--el-text-color-secondary);
}

.relation-detail dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.relation-detail p {
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
}

.affected-list,
.node-fallback {
  padding: 0;
  margin: 0;
  list-style: none;
}

.affected-list li {
  display: flex;
  padding: 5px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  gap: 6px;
  flex-wrap: wrap;
  align-items: center;
}

.affected-list span {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.affected-list :deep(.el-button) {
  height: auto;
  min-height: 24px;
  word-break: break-all;
  white-space: normal;
}

@media (width<=760px) {
  .relations-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .relations-canvas {
    height: 420px;
  }

  .relation-detail {
    max-height: 320px;
    padding-top: 12px;
    padding-left: 0;
    border-top: 1px solid var(--el-border-color-lighter);
    border-left: 0;
  }

  .graph-tools {
    margin-left: 0;
  }

  .relations-entry h2 {
    font-size: 16px;
  }
}
</style>
