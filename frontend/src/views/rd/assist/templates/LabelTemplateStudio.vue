<template>
  <section class="label-studio" aria-label="标签模板工作室">
    <header class="heading"
      ><h2>标签模板</h2
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
      title="身份或权限已变化，包件与预览已清空。请重新进入"
      type="warning"
      :closable="false"
    />
    <el-alert
      v-else-if="!access.canRead.value"
      title="无包件查询权限"
      type="warning"
      :closable="false"
    />
    <template v-else>
      <div class="toolbar">
        <el-radio-group
          :model-value="source"
          aria-label="模板版本"
          :disabled="state.busy || printing"
          @change="setSource"
        >
          <el-radio-button value="draft">草稿预览</el-radio-button
          ><el-radio-button value="published" :disabled="!state.document?.published"
            >已发布</el-radio-button
          >
        </el-radio-group>
        <el-tooltip content="重新加载配置" :trigger-keys="[]"
          ><el-button
            :icon="Refresh"
            aria-label="重新加载配置"
            :disabled="state.busy || printing"
            @click="reload"
        /></el-tooltip>
        <el-button
          v-if="access.canEdit.value"
          :icon="DocumentChecked"
          :disabled="!editable || state.conflict"
          @click="save"
          >保存草稿</el-button
        >
        <el-button
          v-if="access.canEdit.value"
          type="primary"
          :icon="Upload"
          :disabled="!editable || state.dirty || !state.document?.draft || state.conflict"
          @click="publish"
          >发布</el-button
        >
      </div>
      <el-alert
        v-if="error || state.error || exporter.state.error"
        :title="error || state.error || exporter.state.error"
        type="warning"
        :closable="false"
      />
      <div v-if="content" class="studio-grid">
        <div class="workspace">
          <form class="package-search" @submit.prevent="search(1)">
            <el-input
              v-model="keyword"
              aria-label="查询包件编号"
              placeholder="包件编号"
              maxlength="100"
              :disabled="printing"
              clearable
            />
            <el-tooltip content="查询原包件" :trigger-keys="[]"
              ><el-button
                native-type="submit"
                :icon="Search"
                aria-label="查询原包件"
                :loading="queryBusy"
                :disabled="printing"
            /></el-tooltip>
          </form>
          <div class="package-picker">
            <el-select
              :model-value="item?.id"
              aria-label="预览包件"
              placeholder="选择原包件"
              :disabled="queryBusy || printing"
              @change="selectPackage"
            >
              <el-option
                v-for="row in packages"
                :key="row.id"
                :value="row.id"
                :label="`${row.packageCode} · ${row.productModel}`"
              />
            </el-select>
            <el-pagination
              size="small"
              :current-page="page"
              :page-size="20"
              :pager-count="5"
              layout="prev, pager, next"
              :total="total"
              :disabled="queryBusy || printing"
              @current-change="search"
            />
          </div>
          <div class="preview-tools">
            <el-radio-group v-model="mode" aria-label="视图" :disabled="printing || state.busy">
              <el-radio-button value="preview">包件预览</el-radio-button
              ><el-radio-button v-if="editable" value="design">布局设计</el-radio-button>
            </el-radio-group>
            <el-tooltip content="刷新包件预览" :trigger-keys="[]"
              ><el-button
                :icon="View"
                aria-label="刷新包件预览"
                :loading="previewBusy"
                :disabled="!item || printing"
                @click="preview"
            /></el-tooltip>
            <el-tag v-if="snapshot" type="info"
              >{{ source === 'published' ? `发布 v${snapshot.publishedVersion}` : '草稿' }} ·
              预览，未登记打印</el-tag
            >
          </div>
          <PdfmeSurface
            v-if="mode === 'design' && editable"
            :content="content"
            :editable="true"
            :inputs="{}"
            :current="access.current"
            @change="change"
            @error="error = $event"
          />
          <PdfmeSurface
            v-else-if="snapshot"
            :key="previewRevision"
            :content="snapshot.content"
            :editable="false"
            :inputs="snapshot.inputs"
            :current="access.current"
            @ready="previewRendered = true"
            @error="onPreviewError"
          />
          <div v-else class="empty-preview"
            ><el-empty :description="previewBusy ? '正在准备本地 PDF' : '未选择预览包件'"
          /></div>
          <div v-if="item" class="export-tools">
            <el-button
              type="primary"
              :icon="Download"
              :disabled="!canExport"
              :loading="printing"
              @click="exportPdf"
              >{{
                printMode === 'print' ? '登记首次打印并导出 PDF' : '登记补打并导出 PDF'
              }}</el-button
            >
            <el-button
              v-if="exporter.state.phase === 'EXPORTED'"
              :icon="Download"
              :disabled="!access.canPrint(printMode)"
              @click="downloadAgain"
              >下载同一份 PDF</el-button
            >
            <span v-if="blocked && exporter.state.phase !== 'EXPORTED'" role="status"
              >本包已提交，请先核对原打印记录</span
            >
          </div>
        </div>
        <aside class="inspector" aria-label="标签字段属性">
          <el-form label-position="top" :disabled="!editable || printing">
            <el-form-item label="模板名称"
              ><el-input
                :model-value="content.name"
                maxlength="80"
                aria-label="模板名称"
                @change="change({ ...content!, name: $event })"
            /></el-form-item>
            <div class="number-grid">
              <el-form-item label="宽度 (mm)"
                ><el-input-number
                  :model-value="content.widthMm"
                  :min="50"
                  :max="210"
                  aria-label="标签宽度毫米"
                  @change="size('widthMm', $event)"
              /></el-form-item>
              <el-form-item label="高度 (mm)"
                ><el-input-number
                  :model-value="content.heightMm"
                  :min="30"
                  :max="297"
                  aria-label="标签高度毫米"
                  @change="size('heightMm', $event)"
              /></el-form-item>
            </div>
            <el-form-item label="字段"
              ><el-select v-model="fieldKey" aria-label="选中标签字段"
                ><el-option
                  v-for="entry in content.fields"
                  :key="entry.key"
                  :label="FIELD_NAMES[entry.key]"
                  :value="entry.key" /></el-select
            ></el-form-item>
            <template v-if="field">
              <div class="number-grid">
                <el-form-item v-for="key in coordinates" :key="key" :label="coordinateNames[key]">
                  <el-input-number
                    :model-value="field[key]"
                    :min="key === 'x' || key === 'y' ? 0 : field.key === 'qrCode' ? 20 : 1"
                    :max="field.key === 'qrCode' ? 80 : 297"
                    :precision="1"
                    :step="1"
                    :aria-label="`字段${coordinateNames[key]}`"
                    @change="patch({ [key]: $event })"
                  />
                </el-form-item>
              </div>
              <el-form-item v-if="field.key !== 'qrCode'" label="字号 (pt)"
                ><el-input-number
                  :model-value="field.fontSize"
                  :min="6"
                  :max="36"
                  aria-label="字段字号"
                  @change="patch({ fontSize: $event })"
              /></el-form-item>
              <el-tooltip content="移除可选字段" :trigger-keys="[]"
                ><el-button
                  :icon="Delete"
                  aria-label="移除字段"
                  :disabled="field.key === 'qrCode' || field.key === 'packageCode'"
                  @click="removeField"
              /></el-tooltip>
            </template>
            <div class="add-field">
              <el-select v-model="newFieldKey" placeholder="可选字段" aria-label="待添加标签字段"
                ><el-option
                  v-for="key in availableFields"
                  :key="key"
                  :label="FIELD_NAMES[key]"
                  :value="key"
              /></el-select>
              <el-tooltip content="添加字段" :trigger-keys="[]"
                ><el-button
                  :icon="Plus"
                  aria-label="添加字段"
                  :disabled="!newFieldKey"
                  @click="addField"
              /></el-tooltip>
            </div>
            <div class="history-tools">
              <el-tooltip content="撤销" :trigger-keys="[]"
                ><el-button
                  :icon="Back"
                  aria-label="撤销模板修改"
                  :disabled="!canUndo"
                  @click="undo"
              /></el-tooltip>
              <el-tooltip content="重置为已加载版本" :trigger-keys="[]"
                ><el-button :icon="RefreshLeft" aria-label="重置模板" @click="reset"
              /></el-tooltip>
            </div>
          </el-form>
        </aside>
      </div>
      <el-empty v-else-if="!state.busy" description="标签配置不可用" />
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref, watch } from 'vue'
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
  ElPagination,
  ElRadioButton,
  ElRadioGroup,
  ElSelect,
  ElTag,
  ElTooltip
} from 'element-plus'
import {
  Back,
  Delete,
  DocumentChecked,
  Download,
  Plus,
  Refresh,
  RefreshLeft,
  Search,
  Upload,
  View
} from '@element-plus/icons-vue'
import { RdPackageUnitApi, type RdPackageUnitVO } from '@/api/rd/stock/package'
import * as visualApi from '@/api/rd/assist/visualDocument'
import { productBrand } from '@/config/brand'
import { createDocumentSession } from '../layout/documentSession'
import { createHistory } from '../layout/layoutModel'
import { copy } from '../layout/visualSchema'
import { useVisualAccess } from '../layout/useVisualAccess'
import { createEditorDialogs } from '../layout/editorDialogs'
import {
  defaultLabel,
  FIELD_NAMES,
  packageInputs,
  parseLabel,
  type LabelContent,
  type LabelField,
  type LabelKey
} from './labelModel'
import { createAuditedExport } from './auditedExport'
import { downloadLabelPdf, generateLabelPdf } from './pdfmeRuntime'
import { queryLabelPackages } from './packageLookup'

const PdfmeSurface = defineAsyncComponent(() => import('./PdfmeSurface.vue'))
const access = useVisualAccess('LABEL')
const dialogs = createEditorDialogs()
const doc = createDocumentSession<LabelContent>({
  kind: 'LABEL',
  current: () => access.current() && access.canRead.value,
  canEdit: () => access.canEdit.value,
  api: visualApi,
  parse: parseLabel,
  empty: defaultLabel
})
const state = doc.state
const source = ref<'draft' | 'published'>('draft')
const mode = ref<'preview' | 'design'>('preview')
const content = computed(() =>
  source.value === 'draft'
    ? state.content
    : state.document?.published
      ? parseLabel(state.document.published)
      : null
)
const editable = computed(
  () => access.canEdit.value && source.value === 'draft' && !state.busy && !!state.document
)
const keyword = ref('')
const page = ref(1)
const total = ref(0)
const packages = ref<RdPackageUnitVO[]>([])
const item = ref<RdPackageUnitVO | null>(null)
const error = ref('')
const queryBusy = ref(false)
const previewBusy = ref(false)
const previewRendered = ref(false)
const onPreviewError = (message: string) => {
  error.value = message
  previewRendered.value = false
}
const previewRevision = ref(0)
const snapshot = ref<{
  content: LabelContent
  inputs: Record<string, string>
  publishedVersion: number
} | null>(null)
const fieldKey = ref<LabelKey>('brand')
const newFieldKey = ref<LabelKey>()
const field = computed(() => content.value?.fields.find((value) => value.key === fieldKey.value))
const availableFields = computed(() =>
  (Object.keys(FIELD_NAMES) as LabelKey[]).filter(
    (key) => !content.value?.fields.some((value) => value.key === key)
  )
)
const coordinates = ['x', 'y', 'width', 'height'] as const
const coordinateNames = {
  x: '横坐标 (mm)',
  y: '纵坐标 (mm)',
  width: '宽度 (mm)',
  height: '高度 (mm)'
}
const canUndo = ref(false)
let history: ReturnType<typeof createHistory<LabelContent>> | null = null
let queryRevision = 0
let selectionRevision = 0
let renderRevision = 0
const exporter = createAuditedExport({
  current: access.current,
  canPrint: access.canPrint,
  getPackage: RdPackageUnitApi.get,
  generate: generateLabelPdf,
  assertPublished: async (template, version) => {
    const latest = await visualApi.getVisualDocument('LABEL', 'GLOBAL')
    if (
      !access.current() ||
      latest.kind !== 'LABEL' ||
      latest.scopeId !== 'GLOBAL' ||
      latest.publishedVersion !== version ||
      JSON.stringify(parseLabel(latest.published)) !== JSON.stringify(template)
    )
      throw new Error('发布版本已变化')
  },
  audit: (printMode, data) => RdPackageUnitApi[printMode](data),
  download: (bytes, value) => {
    if (!access.current()) return
    if (access.canPrint(printMode.value)) downloadLabelPdf(bytes, value)
  }
})
const printing = computed(() => ['PREPARING', 'REGISTERING'].includes(exporter.state.phase))
const printMode = computed(() =>
  item.value?.status === 'CREATED' ? ('print' as const) : ('reprint' as const)
)
const blocked = computed(() => !!item.value && exporter.blocked(item.value.id))
const canExport = computed(
  () =>
    !!item.value &&
    !!snapshot.value &&
    previewRendered.value &&
    source.value === 'published' &&
    snapshot.value.publishedVersion > 0 &&
    item.value.status !== 'VOIDED' &&
    access.canPrint(printMode.value) &&
    !blocked.value &&
    !printing.value &&
    !previewBusy.value &&
    !state.busy
)
const clearPreview = () => {
  renderRevision++
  snapshot.value = null
  previewBusy.value = false
  previewRendered.value = false
}
watch(
  () => state.document,
  () => {
    history = state.content ? createHistory(state.content) : null
    canUndo.value = false
  }
)
watch([content, item, source], clearPreview, { flush: 'sync', deep: true })
watch(availableFields, (keys) => {
  if (!newFieldKey.value || !keys.includes(newFieldKey.value)) newFieldKey.value = keys[0]
})
access.onClear(() => {
  dialogs.close()
  queryRevision++
  selectionRevision++
  clearPreview()
  doc.clear()
  exporter.dispose()
  packages.value = []
  item.value = null
  keyword.value = ''
  total.value = 0
  page.value = 1
  error.value = ''
  queryBusy.value = false
  mode.value = 'preview'
  history = null
  canUndo.value = false
})
const discard = async () => {
  if (!state.dirty) return true
  try {
    await dialogs.confirm('未保存的模板将被放弃，继续？', '重新加载', { type: 'warning' })
    return access.current()
  } catch {
    return false
  }
}
const reload = async () => {
  if (printing.value || !(await discard()) || !access.current()) return
  clearPreview()
  mode.value = 'preview'
  await doc.load('GLOBAL')
  if (access.current()) source.value = state.document?.published ? 'published' : 'draft'
}
const setSource = (value: unknown) => {
  if (!printing.value) {
    source.value = value === 'published' ? 'published' : 'draft'
    mode.value = 'preview'
  }
}
const search = async (number = 1) => {
  if (!access.canRead.value || !access.current() || printing.value) return
  const revision = ++queryRevision
  selectionRevision++
  item.value = null
  packages.value = []
  clearPreview()
  exporter.clear()
  queryBusy.value = true
  error.value = ''
  page.value = number
  try {
    const result = await queryLabelPackages(RdPackageUnitApi, keyword.value, number)
    if (revision !== queryRevision || !access.current()) return
    packages.value = result.list
    total.value = result.total
  } catch {
    if (revision === queryRevision && access.current()) error.value = '原包件查询失败，请重新查询'
  } finally {
    if (revision === queryRevision && access.current()) queryBusy.value = false
  }
}
const selectPackage = async (id: number) => {
  if (!access.current() || printing.value) return
  const revision = ++selectionRevision
  clearPreview()
  exporter.clear()
  item.value = null
  error.value = ''
  try {
    const value = await RdPackageUnitApi.get(id)
    if (revision !== selectionRevision || !access.current()) return
    if (value.id !== id) throw new Error('原包件 ID 不匹配')
    item.value = value
    mode.value = 'preview'
    await preview()
  } catch {
    if (revision === selectionRevision && access.current())
      error.value = '包件加载或本地 PDF 准备失败，请核对本地字体后刷新预览'
  }
}
const preview = async () => {
  if (!item.value || !content.value || !access.current() || printing.value) return
  const revision = ++renderRevision
  const value = copy(item.value)
  const template = parseLabel(copy(content.value))
  const publishedVersion = source.value === 'published' ? state.document?.publishedVersion || 0 : 0
  snapshot.value = null
  previewBusy.value = true
  previewRendered.value = false
  error.value = ''
  try {
    const pdf = await generateLabelPdf(template, value)
    if (revision !== renderRevision || !access.current()) return
    if (pdf.length < 6 || pdf[0] !== 37 || pdf[1] !== 80 || pdf[2] !== 68 || pdf[3] !== 70)
      throw new Error('PDF 无效')
    snapshot.value = {
      content: template,
      inputs: packageInputs(value, productBrand.labelTitle),
      publishedVersion
    }
    previewRevision.value++
  } catch {
    if (revision === renderRevision && access.current())
      error.value = '本地 PDF 生成失败，未登记打印。请检查本地 NotoSansSC 字体及依赖'
  } finally {
    if (revision === renderRevision && access.current()) previewBusy.value = false
  }
}
const change = (value: LabelContent) => {
  if (!editable.value || printing.value) return
  try {
    doc.change(value)
    history?.change(state.content!)
    canUndo.value = !!history?.canUndo()
    error.value = ''
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '标签字段无效'
  }
}
const size = (key: 'widthMm' | 'heightMm', value?: number) => {
  if (content.value && value != null) change({ ...content.value, [key]: value })
}
const patch = (value: Partial<LabelField>) => {
  if (fieldKey.value === 'qrCode' && (value.width !== undefined || value.height !== undefined)) {
    const side = value.width ?? value.height
    value = { ...value, width: side, height: side }
  }
  if (content.value)
    change({
      ...content.value,
      fields: content.value.fields.map((entry) =>
        entry.key === fieldKey.value ? { ...entry, ...value } : entry
      )
    })
}
const removeField = () => {
  if (!content.value || ['packageCode', 'qrCode'].includes(fieldKey.value)) return
  change({
    ...content.value,
    fields: content.value.fields.filter((entry) => entry.key !== fieldKey.value)
  })
  fieldKey.value = 'packageCode'
}
const addField = () => {
  if (!content.value || !newFieldKey.value) return
  const key = newFieldKey.value
  change({
    ...content.value,
    fields: [
      ...content.value.fields,
      { key, x: 0, y: 0, width: Math.min(40, content.value.widthMm), height: 7, fontSize: 10 }
    ]
  })
  fieldKey.value = key
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
  if (!state.conflict && access.current()) ElMessage.success('标签逻辑字段草稿已保存')
}
const publish = async () => {
  try {
    await dialogs.confirm('发布当前已保存的标签模板？', '发布模板')
    if (!access.current()) return
    await doc.publish()
    if (!state.conflict && access.current()) ElMessage.success('标签模板已发布')
  } catch {
    /* cancelled or invalidated */
  }
}
const exportPdf = async () => {
  if (!canExport.value || !item.value || !snapshot.value) return
  const selected = copy(item.value)
  const selectedSnapshot = copy(snapshot.value)
  const action = printMode.value
  let reason = ''
  try {
    if (action === 'reprint') {
      const result = await dialogs.prompt('补打原因', '补打登记', {
        inputType: 'textarea',
        inputValidator: (value) =>
          (!!value?.trim() && value.trim().length <= 200) || '请输入 1 至 200 字的补打原因'
      })
      reason = result.value.trim()
    } else
      await dialogs.confirm(`登记包件 ${selected.packageCode} 的首次打印并导出 PDF？`, '打印登记')
    if (
      !canExport.value ||
      !access.current() ||
      item.value?.id !== selected.id ||
      selectedSnapshot.publishedVersion !== snapshot.value?.publishedVersion
    )
      return
    await exporter.run({
      mode: action,
      item: selected,
      template: selectedSnapshot.content,
      publishedVersion: selectedSnapshot.publishedVersion,
      reason
    })
  } catch {
    /* cancelled, permission changed, or already latched */
  }
}
const downloadAgain = () => {
  try {
    exporter.downloadAgain()
  } catch {
    error.value = '当前无已登记的 PDF 可下载'
  }
}
onMounted(async () => {
  await reload()
  if (access.current()) await search()
})
</script>

<style scoped>
.label-studio {
  min-width: 0;
  color: var(--el-text-color-primary);
}

.heading,
.toolbar,
.package-search,
.package-picker,
.preview-tools,
.export-tools,
.add-field,
.history-tools {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.heading h2 {
  margin: 0;
  font-size: 20px;
}

.studio-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 16px;
  margin-top: 12px;
}

.workspace,
.inspector {
  min-width: 0;
}

.inspector {
  padding-left: 16px;
  border-left: 1px solid var(--el-border-color);
}

.number-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.number-grid .el-input-number,
.inspector .el-input-number,
.inspector .el-select {
  width: 100%;
}

.package-search .el-input {
  flex: 1;
  min-width: 100px;
}

.package-picker > .el-select {
  flex: 1;
  min-width: 180px;
}

.add-field {
  margin-top: 12px;
  flex-wrap: nowrap;
}

.add-field > .el-select {
  min-width: 0;
}

.label-studio :deep(.el-button) {
  margin-left: 0;
}

.export-tools {
  margin: 12px 0 0;
}

.export-tools .el-button {
  height: auto;
  max-width: 100%;
  min-height: 32px;
  white-space: normal;
}

.export-tools :deep(.el-button > span) {
  overflow-wrap: anywhere;
  white-space: normal;
}

.empty-preview {
  display: grid;
  height: 520px;
  background: #f5f7f8;
  place-items: center;
}

.heading :deep(.el-tag),
.preview-tools :deep(.el-tag) {
  height: auto;
  min-height: 24px;
  white-space: normal;
}

@media (width <= 900px) {
  .studio-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .inspector {
    padding: 12px 0 0;
    border-top: 1px solid var(--el-border-color);
    border-left: 0;
  }
}

@media (width <= 600px) {
  .empty-preview {
    height: 420px;
  }

  .package-picker > .el-select {
    flex-basis: 100%;
  }
}
</style>
