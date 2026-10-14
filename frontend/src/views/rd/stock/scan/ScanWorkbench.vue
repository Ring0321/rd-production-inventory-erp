<script setup lang="ts">
import {
  computed,
  ref,
  shallowRef,
  watch,
  onBeforeUnmount,
  onDeactivated,
  type Component
} from 'vue'
import type { DetectedBarcode } from 'barcode-detector/pure'
import { useUserStore } from '@/store/modules/user'
import {
  getCurrentUserId,
  getTenantId,
  getVisitTenantId,
  getAuthContextRevision
} from '@/utils/auth'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useScanWorkbench } from './useScanWorkbench'
import { classifyScan, type ScanSnapshot, type ScanScope } from './scanCandidates'
import { decodeLocalImage, localZXingOptions } from './scanDecoder'
import { useQrAuthBoundary } from './useQrAuthBoundary'

const props = defineProps<{
  snapshot: ScanSnapshot
  scope: ScanScope
  disabled: boolean
  readSnapshot: () => Promise<ScanSnapshot>
  isCurrent?: () => boolean
}>()
const emit = defineEmits<{
  apply: [value: { taskId: number; version: number; packageCodes: string[]; lineIds: number[] }]
}>()
const user = useUserStore()
const visible = ref(false)
const camera = ref(false)
const cameraLoading = ref(false)
const decoding = ref(false)
const input = ref('')
const cameraError = ref('')
const fileInput = ref<HTMLInputElement>()
const preview = ref<HTMLCanvasElement>()
const streamComponent = shallowRef<Component>()
let alive = true
const ownerRevision = getAuthContextRevision()
const authBoundary = useQrAuthBoundary(() => {
  stopCamera()
  visible.value = false
  work.invalidate()
  cameraError.value = ''
})
let epoch = 0
let trackingSnapshot: ScanSnapshot = props.snapshot
let trackingCodes: string[] = []
const current = () =>
  alive &&
  authBoundary.current() &&
  getAuthContextRevision() === ownerRevision &&
  (props.isCurrent?.() ?? true) &&
  user.getUser.id === props.scope.ownerId &&
  getCurrentUserId() === props.scope.ownerId &&
  String(getVisitTenantId() ?? getTenantId() ?? '') === props.scope.tenant
const permitted = computed(
  () =>
    current() &&
    user.getRoles.some((role) =>
      ['rd_warehouse', 'rd_warehouse_operator', 'super_admin'].includes(role)
    ) &&
    hasPermission([
      props.snapshot.kind === 'loading' ? 'rd:loading-task:dispatch' : 'rd:putaway-task:confirm'
    ])
)
const work = useScanWorkbench({
  snapshot: () => props.snapshot,
  scope: () => props.scope,
  current: () => current() && permitted.value,
  locked: () => props.disabled,
  readSnapshot: () => props.readSnapshot()
})
const { rows, observations, selected, verified, error, checking, capturing, weight, canTransfer } =
  work
const blocked = computed(
  () => props.disabled || checking.value || capturing.value || decoding.value || !permitted.value
)
const counts = computed(() => ({
  local: rows.value.filter(
    (row) =>
      !verified.value.includes(row.packageCode) && !['CONFLICT', 'PROCESSED'].includes(row.state)
  ).length,
  verified: verified.value.length,
  conflict: rows.value.filter((row) => ['CONFLICT', 'PROCESSED'].includes(row.state)).length
}))
const colors: Record<string, string> = {
  MATCH: '#16875d',
  DUPLICATE: '#ac7716',
  WRONG: '#c53f48',
  UNAVAILABLE: '#c53f48',
  INVALID: '#69737d'
}
const draw = (codes: DetectedBarcode[], ctx: CanvasRenderingContext2D) => {
  for (const code of codes.slice(0, 100)) {
    const result = classifyScan(code.rawValue, trackingSnapshot, trackingCodes)
    const points = code.cornerPoints
    if (points.length !== 4) continue
    ctx.strokeStyle = colors[result.kind] || '#69737d'
    ctx.lineWidth = 3
    ctx.beginPath()
    ctx.moveTo(points[0].x, points[0].y)
    for (const point of points.slice(1)) ctx.lineTo(point.x, point.y)
    ctx.closePath()
    ctx.stroke()
  }
}
watch(
  () => props.snapshot,
  (snapshot) => {
    trackingSnapshot = { ...snapshot, lines: snapshot.lines.map((line) => ({ ...line })) }
  },
  { immediate: true }
)
watch(rows, (value) => {
  trackingCodes = value.map((row) => row.packageCode)
})
const stopCamera = () => {
  camera.value = false
  epoch++
}
watch(
  () => [
    props.scope.ownerId,
    props.scope.tenant,
    props.scope.taskId,
    props.snapshot.version,
    permitted.value
  ],
  () => {
    stopCamera()
    visible.value = false
    cameraError.value = ''
    work.invalidate()
  }
)
watch(
  () => props.disabled,
  (value) => {
    if (value) stopCamera()
  }
)
watch(visible, (value) => {
  if (!value) {
    stopCamera()
    window.dispatchEvent(new CustomEvent('duoxu-scan-drafts-changed'))
  }
})
const show = async () => {
  if (!permitted.value || props.disabled) return
  visible.value = true
  cameraError.value = ''
  work.invalidate()
  await work.load()
}
const startCamera = async () => {
  if (blocked.value || !visible.value) return
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    cameraError.value = '当前入口不支持相机权限，可选择本机标签图片'
    return
  }
  const generation = ++epoch
  cameraLoading.value = true
  cameraError.value = ''
  try {
    const module = await import('vue-qrcode-reader')
    module.setZXingModuleOverrides(localZXingOptions)
    if (generation !== epoch || !visible.value || blocked.value) return
    streamComponent.value = module.QrcodeStream
    camera.value = true
  } catch {
    if (generation === epoch) cameraError.value = '相机模块加载失败，请重试或选择图片'
  } finally {
    cameraLoading.value = false
  }
}
const onDetect = (codes: DetectedBarcode[]) => {
  if (!visible.value || !camera.value || !permitted.value || props.disabled) return
  void work.collect(codes.map((code) => code.rawValue))
}
const cameraFailed = (cause: unknown) => {
  cameraError.value =
    cause instanceof Error && cause.name === 'NotAllowedError'
      ? '相机权限未授予'
      : '相机不可用，请检查设备或选择图片'
  stopCamera()
}
const scanFiles = async (event: Event) => {
  const field = event.target as HTMLInputElement
  const files = Array.from(field.files || []).slice(0, 10)
  field.value = ''
  if (blocked.value || !visible.value) return
  stopCamera()
  const generation = epoch
  decoding.value = true
  cameraError.value = ''
  try {
    for (const file of files) {
      if (
        !['image/png', 'image/jpeg', 'image/webp'].includes(file.type) ||
        file.size > 12 * 1024 * 1024
      )
        throw new Error('请选择不超过 12 MB 的 PNG、JPEG 或 WebP 标签照片')
      const bitmap = await createImageBitmap(file)
      try {
        const result = await decodeLocalImage(bitmap)
        if (generation !== epoch || !visible.value || !permitted.value || props.disabled) return
        if (preview.value) {
          preview.value.width = bitmap.width
          preview.value.height = bitmap.height
          const ctx = preview.value.getContext('2d')
          if (ctx) {
            ctx.drawImage(bitmap, 0, 0)
            draw(result, ctx)
          }
        }
        await work.collect(result.map((code) => code.rawValue))
        if (!result.length) cameraError.value = '未识别到清晰二维码，本机清单未增加'
      } finally {
        bitmap.close()
      }
    }
  } catch (cause) {
    if (generation === epoch)
      cameraError.value = cause instanceof Error ? cause.message : '图片解码失败，清单未确认'
  } finally {
    decoding.value = false
  }
}
const addInput = async () => {
  if (!blocked.value) {
    await work.collect(input.value.split(/\r?\n/).filter(Boolean))
    input.value = ''
  }
}
const useSelection = async () => {
  stopCamera()
  const value = await work.transfer()
  if (value && current() && !props.disabled) {
    emit('apply', value)
    visible.value = false
  }
}
const checkServer = () => {
  stopCamera()
  return work.check()
}
const label = (code: string, state: string) =>
  verified.value.includes(code)
    ? '本次服务器核对通过'
    : {
        LOCAL: '本机已采集 · 待核',
        VERIFIED: '上次核对通过 · 待重核',
        CONFLICT: '存在冲突',
        PROCESSED: '已处理 / 不可选择'
      }[state] || '待核'
onDeactivated(() => {
  stopCamera()
  visible.value = false
})
onBeforeUnmount(() => {
  alive = false
  stopCamera()
})
</script>
<template>
  <el-button v-if="permitted" :disabled="disabled" @click="show"
    ><Icon icon="lucide:scan-line" />连续扫码 / 待同步箱</el-button
  >
  <el-dialog
    v-model="visible"
    title="扫码工作区"
    top="5vh"
    width="min(1080px, 96vw)"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <section v-if="permitted" class="scan-workbench" aria-label="连续扫码工作区">
      <header class="scan-summary"
        ><strong>{{ snapshot.taskNo }}</strong
        ><span>版本 {{ snapshot.version }}</span
        ><span>本机待核 {{ counts.local }} 包</span><span>本次核对 {{ counts.verified }} 包</span
        ><span>冲突 / 已处理 {{ counts.conflict }} 包</span
        ><span>候选登记净重 {{ weight === undefined ? '待核' : `${weight} kg` }}</span></header
      >
      <el-alert
        v-if="error || cameraError"
        :title="error || cameraError"
        type="warning"
        :closable="false"
        role="alert"
      />
      <div class="scan-toolbar">
        <el-button
          :disabled="blocked"
          :loading="cameraLoading"
          @click="camera ? stopCamera() : startCamera()"
          ><Icon :icon="camera ? 'lucide:camera-off' : 'lucide:camera'" />{{
            camera ? '停止相机' : '开启相机'
          }}</el-button
        >
        <el-button :disabled="blocked" :loading="decoding" @click="fileInput?.click()"
          ><Icon icon="lucide:image-up" />识别标签图片</el-button
        >
        <input
          ref="fileInput"
          type="file"
          accept="image/png,image/jpeg,image/webp"
          multiple
          hidden
          aria-label="本机标签图片"
          @change="scanFiles"
        />
        <el-button :disabled="blocked || !rows.length" :loading="checking" @click="checkServer"
          ><Icon icon="lucide:refresh-cw" />重新核对服务器</el-button
        >
      </div>
      <div class="scan-grid">
        <section class="capture-area" aria-label="标签识别画面">
          <div class="scan-media">
            <component
              :is="streamComponent"
              v-if="camera && streamComponent"
              :formats="['qr_code']"
              :constraints="{ facingMode: 'environment' }"
              :track="draw"
              :paused="disabled || checking"
              @detect="onDetect"
              @error="cameraFailed"
            />
            <canvas
              v-show="!camera"
              ref="preview"
              width="640"
              height="480"
              aria-label="标签图片识别框"
            ></canvas>
          </div>
          <div class="scan-legend"
            ><span class="match">任务候选</span><span class="duplicate">重复</span
            ><span class="wrong">错包 / 不可用</span></div
          >
          <form class="scan-input" @submit.prevent="addInput"
            ><el-input
              v-model="input"
              aria-label="采集包号"
              placeholder="包号 / 二维码"
              :disabled="blocked" /><el-tooltip content="采集到本机"
              ><el-button native-type="submit" :disabled="blocked || !input" aria-label="采集到本机"
                ><Icon icon="lucide:plus" /></el-button></el-tooltip
          ></form>
          <ul class="observations" aria-label="最近识别结果"
            ><li v-for="(item, index) in observations" :key="index" :data-kind="item.kind"
              ><span>{{ item.code || '无效内容' }}</span
              ><strong>{{ item.label }}</strong></li
            ></ul
          >
        </section>
        <section class="candidate-area" aria-label="本任务待同步箱">
          <h3>本机候选记录</h3>
          <el-empty v-if="!rows.length" description="暂无本机候选" :image-size="72" />
          <el-checkbox-group v-model="selected" class="candidate-list">
            <article
              v-for="row in rows"
              :key="row.id"
              class="candidate-row"
              :data-state="row.state"
            >
              <el-checkbox
                :value="row.packageCode"
                :disabled="blocked || !verified.includes(row.packageCode)"
                :aria-label="`选择 ${row.packageCode}`"
              />
              <div
                ><strong>{{ row.packageCode }}</strong
                ><span>{{ label(row.packageCode, row.state) }}</span
                ><small
                  >采集版本 {{ row.observedVersion }} ·
                  {{ new Date(row.seenAt).toLocaleString('zh-CN', { hour12: false }) }}</small
                ><small v-if="row.reason">{{ row.reason }}</small></div
              >
              <el-tooltip content="移除本机候选"
                ><el-button
                  :disabled="blocked"
                  circle
                  aria-label="移除本机候选"
                  @click="work.remove(row.packageCode)"
                  ><Icon icon="lucide:trash-2" /></el-button
              ></el-tooltip>
            </article>
          </el-checkbox-group>
        </section>
      </div>
      <footer class="scan-footer"
        ><span>本机采集不计入入库或出库完成</span
        ><el-button
          type="primary"
          :disabled="!canTransfer || decoding"
          :loading="checking"
          @click="useSelection"
          ><Icon icon="lucide:check" />带回所选核验清单</el-button
        ></footer
      >
    </section>
  </el-dialog>
</template>
<style scoped>
.scan-workbench {
  max-height: calc(85dvh - 80px);
  min-width: 0;
  padding-right: 4px;
  overflow-y: auto;
  color: var(--el-text-color-primary);
}

.scan-summary {
  display: flex;
  padding-bottom: 12px;
  font-size: 13px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  gap: 12px;
  flex-wrap: wrap;
}

.scan-summary strong {
  overflow-wrap: anywhere;
}

.scan-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}

.scan-toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}

.scan-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 20px;
}

.capture-area,
.candidate-area {
  min-width: 0;
}

.candidate-area h3 {
  margin: 0 0 10px;
  font-size: 15px;
}

.scan-media {
  width: 100%;
  aspect-ratio: 4 / 3;
  overflow: hidden;
  background: #eef2f4;
  border-radius: 4px;
}

.scan-media canvas {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.scan-media :deep(video) {
  object-fit: contain;
}

.scan-legend {
  display: flex;
  gap: 14px;
  margin: 10px 0;
  font-size: 12px;
  flex-wrap: wrap;
}

.match {
  color: #16875d;
}

.duplicate {
  color: #93620a;
}

.wrong {
  color: #b5323e;
}

.scan-input {
  display: flex;
  gap: 8px;
}

.observations {
  padding: 0;
  margin: 10px 0;
  list-style: none;
}

.observations li {
  display: flex;
  padding: 6px 0;
  font-size: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  justify-content: space-between;
  gap: 8px;
}

.observations li span {
  overflow-wrap: anywhere;
}

.observations strong {
  white-space: nowrap;
}

.candidate-list {
  max-height: 480px;
  overflow: auto;
}

.candidate-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 32px;
  gap: 8px;
  align-items: start;
  padding: 10px 0;
  line-height: 1.5;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.candidate-row strong,
.candidate-row span,
.candidate-row small {
  display: block;
  overflow-wrap: anywhere;
}

.candidate-row strong {
  font-size: 13px;
}

.candidate-row span {
  margin: 4px 0;
  font-size: 12px;
}

.candidate-row small {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.candidate-row[data-state='CONFLICT'] span {
  color: #b5323e;
}

.scan-footer {
  position: sticky;
  bottom: 0;
  z-index: 1;
  display: flex;
  padding-top: 14px;
  padding-bottom: 4px;
  margin-top: 14px;
  background: var(--el-bg-color);
  border-top: 1px solid var(--el-border-color-lighter);
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
}

.scan-footer span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (width <= 760px) {
  .scan-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 14px;
  }

  .candidate-list {
    max-height: 300px;
  }

  .scan-summary {
    gap: 8px;
  }

  .scan-toolbar :deep(.el-button) {
    height: auto;
    max-width: 100%;
    min-height: 32px;
    white-space: normal;
  }

  .scan-footer :deep(.el-button) {
    width: 100%;
  }
}
</style>
