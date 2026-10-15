<template>
  <div
    class="pdfme-viewport"
    :class="{ designing: editable }"
    :aria-label="editable ? '标签布局设计器' : '标签预览，未登记打印'"
  >
    <div ref="host" class="pdfme-host"></div>
    <p v-if="error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Designer, Form } from '@pdfme/ui'
import type { Template } from '@pdfme/common'
import { fromPdfmeTemplate, toPdfmeTemplate, type LabelContent } from './labelModel'
import { loadPdfmeRuntime } from './pdfmeRuntime'
import { copy } from '../layout/visualSchema'

const props = defineProps<{
  content: LabelContent
  editable: boolean
  inputs: Record<string, string>
  current: () => boolean
}>()
const emit = defineEmits<{ change: [content: LabelContent]; error: [message: string]; ready: [] }>()
const host = ref<HTMLElement>()
const error = ref('')
let instance: Designer | Form | undefined
let alive = true
let generation = 0
let updating = false
let rendered = ''
let observer: ResizeObserver | undefined
const fitScale = () => {
  // PreviewUI already fits narrow pages; an extra multiplier would shrink twice.
  if (!props.editable && (host.value?.clientWidth || 300) < 600) return 1
  return Math.max(
    0.2,
    Math.min(
      1.5,
      ((host.value?.clientWidth || 300) - 32) / (props.content.widthMm * 3.779528),
      ((host.value?.clientHeight || 420) - 72) / (props.content.heightMm * 3.779528)
    )
  )
}
const dispose = () => {
  generation++
  observer?.disconnect()
  observer = undefined
  instance?.destroy()
  instance = undefined
  host.value?.replaceChildren()
}
const update = (template: Template) => {
  if (!instance) return
  updating = true
  try {
    instance.updateTemplate(template)
  } finally {
    updating = false
  }
}
const mount = async () => {
  dispose()
  const revision = generation
  error.value = ''
  try {
    const runtime = await loadPdfmeRuntime()
    if (!alive || revision !== generation || !props.current() || !host.value) return
    const template = toPdfmeTemplate(props.content)
    rendered = JSON.stringify(template)
    const options = {
      font: runtime.font,
      lang: 'zh' as const,
      sidebarOpen: false,
      zoomLevel: fitScale()
    }
    if (props.editable) {
      const designer = new runtime.Designer({
        domContainer: host.value,
        template,
        options,
        plugins: runtime.plugins
      })
      instance = designer
      const accept = (changed: Template) => {
        if (updating || !alive || revision !== generation || !props.current()) return
        try {
          const logical = fromPdfmeTemplate(changed, props.content)
          const canonical = toPdfmeTemplate(logical)
          rendered = JSON.stringify(canonical)
          emit('change', logical)
          // Rebuild styles/content so native Designer controls cannot change allowed output.
          update(canonical)
        } catch {
          update(toPdfmeTemplate(props.content))
          emit('error', '仅接受原字段的位置、尺寸和字号，其他变更已撤销')
        }
      }
      designer.onChangeTemplate(accept)
      designer.onSaveTemplate(accept)
    } else {
      const inputs = copy(props.inputs)
      const readonlyTemplate = {
        ...template,
        schemas: template.schemas.map((page) =>
          page.map((field) => ({ ...field, readOnly: true, content: inputs[field.name] ?? '' }))
        )
      }
      // pdfme v6 structuredClone cannot accept Vue's reactive input proxies.
      instance = new runtime.Form({
        domContainer: host.value,
        template: readonlyTemplate,
        inputs: [inputs],
        options,
        plugins: runtime.plugins
      })
      emit('ready')
    }
    let size = `${host.value.clientWidth}:${host.value.clientHeight}`
    observer = new ResizeObserver(() => {
      if (!alive || revision !== generation || !props.current() || !host.value) return
      const next = `${host.value.clientWidth}:${host.value.clientHeight}`
      if (next !== size) {
        size = next
        void mount()
      }
    })
    observer.observe(host.value)
  } catch {
    if (alive && revision === generation && props.current()) {
      error.value = '标签渲染不可用，请检查本地字体及依赖'
      emit('error', error.value)
    }
  }
}
watch(() => props.editable, mount)
watch(
  () => props.content,
  (value, previous) => {
    if (!instance || !props.current()) return
    if (value.widthMm !== previous.widthMm || value.heightMm !== previous.heightMm) {
      void mount()
      return
    }
    const template = toPdfmeTemplate(props.content)
    const serialized = JSON.stringify(template)
    if (rendered !== serialized) {
      rendered = serialized
      update(template)
    }
  },
  { deep: true }
)
watch(
  () => props.inputs,
  () => {
    if (instance && !props.editable && props.current()) void mount()
  },
  { deep: true }
)
onMounted(mount)
onBeforeUnmount(() => {
  alive = false
  dispose()
})
</script>

<style scoped>
.pdfme-viewport {
  position: relative;
  height: 520px;
  overflow: auto;
  background: #f5f7f8;
  border: 1px solid var(--el-border-color);
}

.pdfme-host {
  width: 100%;
  height: 100%;
  min-width: 0;
}

.designing .pdfme-host {
  min-width: 700px;
}

.pdfme-viewport p {
  padding: 12px;
  color: var(--el-color-danger);
}

@media (width <= 600px) {
  .pdfme-viewport {
    height: 420px;
  }
}
</style>
