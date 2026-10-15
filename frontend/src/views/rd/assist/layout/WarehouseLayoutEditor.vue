<template>
  <section class="warehouse-editor" aria-label="仓库布局编辑器">
    <header class="heading"
      ><h2>仓库布局</h2
      ><el-tag
        v-if="state.document"
        :disable-transitions="true"
        :type="state.dirty ? 'warning' : 'info'"
        >{{ state.dirty ? '未保存' : `草稿 v${state.document.version}` }} / 发布 v{{
          state.document.publishedVersion || 0
        }}</el-tag
      ></header
    >
    <el-alert
      v-if="!access.valid.value"
      title="身份或权限已变化，数据已清空。请重新进入"
      type="warning"
      :closable="false"
    />
    <el-alert
      v-else-if="!access.canRead.value"
      title="无库位查询权限"
      type="warning"
      :closable="false"
    />
    <template v-else>
      <div class="toolbar">
        <el-select
          :model-value="scope"
          aria-label="仓库"
          placeholder="选择仓库"
          :disabled="locationsBusy || state.busy"
          @change="switchWarehouse"
        >
          <el-option
            v-for="warehouse in warehouses"
            :key="warehouse.code"
            :label="`${warehouse.name} (${warehouse.code})`"
            :value="warehouse.code"
          />
        </el-select>
        <el-tooltip content="重新加载服务端版本" :trigger-keys="[]"
          ><el-button
            :icon="Refresh"
            aria-label="重新加载"
            :loading="locationsBusy || state.busy"
            @click="reload"
        /></el-tooltip>
        <el-button :icon="DocumentChecked" :disabled="!editable || state.conflict" @click="save"
          >保存草稿</el-button
        >
        <el-button
          type="primary"
          :icon="Upload"
          :disabled="!editable || state.dirty || !state.document?.draft || state.conflict"
          @click="publish"
          >发布</el-button
        >
      </div>
      <el-alert
        v-if="error || state.error"
        :title="error || state.error"
        type="warning"
        :closable="false"
      />
      <div v-if="state.content" class="editor-grid">
        <div class="workspace">
          <div class="tools">
            <el-select
              v-model="newLocationId"
              aria-label="待添加的原库位"
              placeholder="选择原库位"
              :disabled="!editable"
              filterable
            >
              <el-option
                v-for="row in availableLocations"
                :key="row.id"
                :label="locationLabel(row)"
                :value="row.id"
              />
            </el-select>
            <el-tooltip content="添加绑定库位" :trigger-keys="[]"
              ><el-button
                :icon="Box"
                aria-label="添加库位"
                :disabled="!editable || !newLocationId"
                @click="add('LOCATION')"
            /></el-tooltip>
            <el-tooltip content="添加区域" :trigger-keys="[]"
              ><el-button
                :icon="Grid"
                aria-label="添加区域"
                :disabled="!editable"
                @click="add('ZONE')"
            /></el-tooltip>
            <el-tooltip content="添加通道" :trigger-keys="[]"
              ><el-button
                :icon="Rank"
                aria-label="添加通道"
                :disabled="!editable"
                @click="add('AISLE')"
            /></el-tooltip>
            <el-tooltip content="撤销" :trigger-keys="[]"
              ><el-button
                :icon="Back"
                aria-label="撤销"
                :disabled="!editable || !canUndo"
                @click="undo"
            /></el-tooltip>
            <el-tooltip content="重置为已加载版本" :trigger-keys="[]"
              ><el-button
                :icon="RefreshLeft"
                aria-label="重置布局"
                :disabled="!editable"
                @click="reset"
            /></el-tooltip>
          </div>
          <KonvaLayoutSurface
            :content="state.content"
            :selected-id="selectedId"
            :editable="editable"
            @select="selectedId = $event"
            @move="move"
          />
          <div class="object-list" aria-label="布局对象列表">
            <el-button
              v-for="item in state.content.elements"
              :key="item.id"
              :type="selectedId === item.id ? 'primary' : 'default'"
              plain
              @click="selectedId = item.id"
              >{{ names[item.kind] }} · {{ item.label || item.id }}</el-button
            >
          </div>
        </div>
        <aside class="inspector" aria-label="对象属性">
          <el-form label-position="top" :disabled="!editable">
            <div class="number-grid">
              <el-form-item label="画布宽"
                ><el-input-number
                  :model-value="state.content.width"
                  :min="64"
                  :max="4096"
                  aria-label="画布宽"
                  @change="dimension('width', $event)"
              /></el-form-item>
              <el-form-item label="画布高"
                ><el-input-number
                  :model-value="state.content.height"
                  :min="64"
                  :max="4096"
                  aria-label="画布高"
                  @change="dimension('height', $event)"
              /></el-form-item>
            </div>
            <template v-if="selected">
              <h3>{{ names[selected.kind] }}</h3>
              <el-form-item label="名称"
                ><el-input
                  :model-value="selected.label"
                  maxlength="80"
                  aria-label="对象名称"
                  @change="patch({ label: $event })"
              /></el-form-item>
              <el-form-item v-if="selected.kind === 'LOCATION'" label="原库位绑定">
                <el-select
                  :model-value="selected.locationId"
                  aria-label="原库位绑定"
                  @change="patch({ locationId: $event })"
                >
                  <el-option
                    v-for="row in bindingOptions"
                    :key="row.id"
                    :label="locationLabel(row)"
                    :value="row.id"
                  />
                </el-select>
              </el-form-item>
              <div class="number-grid">
                <el-form-item v-for="key in coordinates" :key="key" :label="coordinateNames[key]">
                  <el-input-number
                    :model-value="selected[key]"
                    :min="key === 'x' || key === 'y' ? 0 : 1"
                    :max="4096"
                    :precision="1"
                    :step="10"
                    :aria-label="coordinateNames[key]"
                    @change="patch({ [key]: $event })"
                  />
                </el-form-item>
              </div>
              <el-tooltip content="移除展示对象" :trigger-keys="[]"
                ><el-button
                  :icon="Delete"
                  aria-label="移除对象"
                  type="danger"
                  plain
                  @click="remove"
              /></el-tooltip>
            </template>
          </el-form>
        </aside>
      </div>
      <el-empty v-else-if="!locationsBusy && !state.busy" description="暂无可显示布局" />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ElAlert,
  ElButton,
  ElEmpty,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElMessage,
  ElOption,
  ElSelect,
  ElTag,
  ElTooltip
} from 'element-plus'
import {
  Back,
  Box,
  Delete,
  DocumentChecked,
  Grid,
  Rank,
  Refresh,
  RefreshLeft,
  Upload
} from '@element-plus/icons-vue'
import { RdWarehouseLocationApi } from '@/api/rd/masterdata/location'
import { RdPutawayTaskApi } from '@/api/rd/stock/putaway'
import * as visualApi from '@/api/rd/assist/visualDocument'
import { createDocumentSession } from './documentSession'
import { useVisualAccess } from './useVisualAccess'
import { createEditorDialogs } from './editorDialogs'
import { loadLayoutLocations, selectLayoutWarehouse, type LayoutLocation } from './locationLoading'
import {
  createHistory,
  emptyLayout,
  parseLayout,
  warehouseOptions,
  type LayoutContent,
  type LayoutElement
} from './layoutModel'

const KonvaLayoutSurface = defineAsyncComponent(() => import('./KonvaLayoutSurface.vue'))
const access = useVisualAccess('LAYOUT')
const dialogs = createEditorDialogs()
const route = useRoute()
let requestedScope: unknown = route.query.warehouseCode
const scope = ref('')
const locations = ref<LayoutLocation[]>([])
const locationsBusy = ref(false)
const error = ref('')
const selectedId = ref('')
const newLocationId = ref<number>()
const canUndo = ref(false)
let history: ReturnType<typeof createHistory<LayoutContent>> | null = null
let reads = 0
const doc = createDocumentSession<LayoutContent>({
  kind: 'LAYOUT',
  current: () => access.current() && access.canRead.value,
  canEdit: () => access.canEdit.value,
  api: visualApi,
  parse: (value) => parseLayout(value, scope.value, locations.value),
  empty: () => emptyLayout(scope.value)
})
const state = doc.state
const editable = computed(
  () => access.canEdit.value && !state.busy && !!state.document && !locationsBusy.value
)
const warehouses = computed(() => warehouseOptions(locations.value))
const localLocations = computed(() =>
  locations.value.filter((row) => row.warehouseCode === scope.value)
)
const availableLocations = computed(() =>
  localLocations.value.filter(
    (row) => !state.content?.elements.some((item) => item.locationId === row.id)
  )
)
const selected = computed(() =>
  state.content?.elements.find((item) => item.id === selectedId.value)
)
const bindingOptions = computed(() =>
  localLocations.value.filter(
    (row) =>
      row.id === selected.value?.locationId ||
      !state.content?.elements.some((item) => item.locationId === row.id)
  )
)
const names = { LOCATION: '库位', AISLE: '通道', ZONE: '区域' }
const coordinates = ['x', 'y', 'width', 'height'] as const
const coordinateNames = { x: '横坐标', y: '纵坐标', width: '宽度', height: '高度' }
const locationLabel = (row: LayoutLocation) =>
  [row.locationCode, row.enabled === false ? '停用' : '', row.frozen ? '冻结' : '']
    .filter(Boolean)
    .join(' · ')
watch(
  () => state.document,
  () => {
    history = state.content ? createHistory(state.content) : null
    selectedId.value = ''
    canUndo.value = false
  }
)
watch(availableLocations, (rows) => {
  if (!rows.some((row) => row.id === newLocationId.value)) newLocationId.value = rows[0]?.id
})
access.onClear(() => {
  dialogs.close()
  reads++
  doc.clear()
  locations.value = []
  scope.value = ''
  selectedId.value = ''
  newLocationId.value = undefined
  error.value = ''
  history = null
  canUndo.value = false
  locationsBusy.value = false
})
const discard = async () => {
  if (!state.dirty) return true
  try {
    await dialogs.confirm('未保存的布局将被放弃，继续？', '重新加载', { type: 'warning' })
    return access.current()
  } catch {
    return false
  }
}
const switchWarehouse = async (code: string) => {
  if (
    locationsBusy.value ||
    state.busy ||
    !access.current() ||
    !access.canRead.value ||
    !warehouses.value.some((row) => row.code === code)
  )
    return
  const revision = reads
  if (!(await discard()) || !access.current() || reads !== revision) return
  reads++
  requestedScope = undefined
  scope.value = code
  error.value = ''
  await doc.load(code)
}
const reload = async () => {
  if (!(await discard()) || !access.canRead.value || !access.current()) return
  const revision = ++reads
  locationsBusy.value = true
  error.value = ''
  const oldScope = scope.value
  const requested = requestedScope
  doc.clear()
  locations.value = []
  scope.value = ''
  try {
    const rows = await loadLayoutLocations({
      current: () => reads === revision && access.current(),
      allowed: access.allowed,
      getPage: RdWarehouseLocationApi.getPage,
      getLocationMap: RdPutawayTaskApi.getLocationMap
    })
    if (reads !== revision || !access.current()) return
    locations.value = rows
    scope.value = selectLayoutWarehouse(warehouses.value, requested, oldScope)
    requestedScope = undefined
    if (scope.value) await doc.load(scope.value)
  } catch (cause) {
    if (reads === revision && access.current()) {
      if (requestedScope === undefined && oldScope) requestedScope = oldScope
      scope.value = ''
      error.value = cause instanceof Error ? cause.message : '原库位查询失败，请重新加载'
    }
  } finally {
    if (reads === revision && access.current()) locationsBusy.value = false
  }
}
watch(
  () => route.query.warehouseCode,
  (code) => {
    requestedScope = code
    void reload()
  }
)
const change = (value: LayoutContent) => {
  try {
    doc.change(value)
    history?.change(state.content!)
    canUndo.value = !!history?.canUndo()
    error.value = ''
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '布局无效'
  }
}
const patchItem = (id: string, patch: Partial<LayoutElement>) => {
  if (!state.content || !editable.value) return
  change({
    ...state.content,
    elements: state.content.elements.map((item) => (item.id === id ? { ...item, ...patch } : item))
  })
}
const patch = (value: Partial<LayoutElement>) => patchItem(selectedId.value, value)
const move = (id: string, position: { x: number; y: number }) => patchItem(id, position)
const dimension = (key: 'width' | 'height', value?: number) => {
  if (state.content && value != null) change({ ...state.content, [key]: value })
}
const add = (kind: LayoutElement['kind']) => {
  if (!state.content || !editable.value) return
  const row = localLocations.value.find((item) => item.id === newLocationId.value)
  if (kind === 'LOCATION' && !row) return
  const id = crypto.randomUUID()
  const item: LayoutElement = {
    id,
    kind,
    x: 0,
    y: 0,
    width: Math.min(kind === 'ZONE' ? 400 : 120, state.content.width),
    height: Math.min(kind === 'AISLE' ? 240 : 90, state.content.height),
    label: kind === 'LOCATION' ? row!.locationCode : names[kind],
    ...(kind === 'LOCATION' ? { locationId: row!.id } : {})
  }
  change({ ...state.content, elements: [...state.content.elements, item] })
  selectedId.value = id
}
const remove = () => {
  if (state.content)
    change({
      ...state.content,
      elements: state.content.elements.filter((item) => item.id !== selectedId.value)
    })
  selectedId.value = ''
}
const undo = () => {
  if (history && editable.value) {
    doc.change(history.undo())
    canUndo.value = history.canUndo()
  }
}
const reset = async () => {
  if (history && editable.value && (await discard())) {
    doc.change(history.reset())
    canUndo.value = history.canUndo()
  }
}
const save = async () => {
  await doc.save()
  if (!state.conflict && access.current()) ElMessage.success('展示配置草稿已保存')
}
const publish = async () => {
  try {
    await dialogs.confirm('发布当前已保存的仓库展示配置？', '发布布局')
    if (!access.current()) return
    await doc.publish()
    if (!state.conflict && access.current()) ElMessage.success('展示配置已发布')
  } catch {
    /* cancelled or invalidated */
  }
}
onMounted(reload)
</script>

<style scoped>
.warehouse-editor {
  min-width: 0;
  color: var(--el-text-color-primary);
}

.heading,
.toolbar,
.tools {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  margin-bottom: 12px;
}

.heading h2 {
  margin: 0;
  font-size: 20px;
}

.toolbar > .el-select {
  width: 260px;
  max-width: 100%;
}

.toolbar .el-button,
.tools .el-button,
.object-list .el-button {
  margin: 0;
}

.editor-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  margin-top: 12px;
}

.workspace,
.inspector {
  min-width: 0;
}

.tools > .el-select {
  width: 180px;
  max-width: 100%;
}

.inspector {
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color);
}

.inspector h3 {
  margin: 4px 0 12px;
  font-size: 16px;
}

.number-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.number-grid .el-input-number,
.inspector .el-select {
  width: 100%;
}

.object-list {
  display: flex;
  max-height: 170px;
  padding-top: 12px;
  overflow: auto;
  flex-wrap: wrap;
  gap: 8px;
}

.object-list .el-button {
  height: auto;
  max-width: 100%;
  min-height: 32px;
  white-space: normal;
  overflow-wrap: anywhere;
}

.heading :deep(.el-tag) {
  height: auto;
  min-height: 24px;
  white-space: normal;
}

@media (width <= 900px) {
  .editor-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .inspector {
    padding: 12px 0 0;
    border-top: 1px solid var(--el-border-color);
    border-left: 0;
  }
}

@media (width <= 400px) {
  .toolbar > .el-select {
    width: 100%;
  }
}
</style>
