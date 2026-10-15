import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { getAccessToken, getAuthContextRevision, onAuthContextChange } from '@/utils/auth'
import {
  getOcrStatus,
  recognizeDocument,
  type OcrResult,
  type OcrStatus
} from '@/api/rd/documentocr'
import {
  allowedScope,
  createOcrSession,
  draftFromResult,
  fileKind,
  previewDimensions
} from './session.mjs'

export const useLocalDocumentReader = () => {
  const user = useUserStore()
  const session = createOcrSession()
  const input = ref<HTMLInputElement>()
  const file = ref<File>()
  const preview = ref('')
  const kind = ref('')
  const result = ref<OcrResult>()
  const status = ref<OcrStatus>()
  const busy = ref(false)
  const error = ref('')
  const draft = reactive(draftFromResult())
  let disposed = false
  let statusAbort: AbortController | undefined
  let exportUrl = ''
  const scope = () =>
    JSON.stringify([user.getUser.id, [...user.getRoles].sort(), [...user.getPermissions].sort()])
  const allowed = computed(() =>
    allowedScope({
      userId: user.getUser.id,
      roles: user.getRoles,
      permissions: [...user.getPermissions]
    })
  )
  const clear = () => {
    session.clear()
    statusAbort?.abort()
    status.value = undefined
    if (preview.value) URL.revokeObjectURL(preview.value)
    if (exportUrl) URL.revokeObjectURL(exportUrl)
    preview.value = ''
    exportUrl = ''
    kind.value = ''
    file.value = undefined
    result.value = undefined
    busy.value = false
    error.value = ''
    Object.assign(draft, draftFromResult())
    if (input.value) input.value.value = ''
  }
  const refreshStatus = async () => {
    if (!allowed.value || !getAccessToken() || disposed) return
    statusAbort?.abort()
    const controller = new AbortController()
    statusAbort = controller
    const revision = getAuthContextRevision()
    const capturedScope = scope()
    try {
      const value = await getOcrStatus(controller.signal)
      if (
        !disposed &&
        !controller.signal.aborted &&
        allowed.value &&
        revision === getAuthContextRevision() &&
        capturedScope === scope()
      ) {
        status.value = value
      }
    } catch {
      if (
        !controller.signal.aborted &&
        !disposed &&
        revision === getAuthContextRevision() &&
        capturedScope === scope()
      ) {
        status.value = { available: false, state: 'UNAVAILABLE', maxBytes: 10485760, maxPages: 3 }
      }
    }
  }
  const selectFile = async (event: Event) => {
    const selected = (event.target as HTMLInputElement).files?.[0]
    clear()
    if (!selected || !allowed.value || !getAccessToken()) return
    const ticket = session.begin()
    const revision = getAuthContextRevision()
    const capturedScope = scope()
    try {
      if (selected.size < 1 || selected.size > 10485760) throw new Error('文件须在 10 MiB 以内')
      const header = new Uint8Array(await selected.slice(0, 262144).arrayBuffer())
      const mime = fileKind(header)
      if (
        !session.current(ticket) ||
        revision !== getAuthContextRevision() ||
        capturedScope !== scope()
      )
        return
      if (!mime) throw new Error('仅接受 PNG、JPEG、PDF 文件')
      if (mime !== 'application/pdf') previewDimensions(header, mime)
      file.value = selected
      kind.value = mime
      if (mime !== 'application/pdf')
        preview.value = URL.createObjectURL(new Blob([selected], { type: mime }))
      void refreshStatus()
    } catch (failure) {
      if (
        session.current(ticket) &&
        revision === getAuthContextRevision() &&
        capturedScope === scope()
      ) {
        error.value = failure instanceof Error ? failure.message : '文件无法读取'
      }
    }
  }
  const recognize = async () => {
    if (
      !file.value ||
      !allowed.value ||
      !getAccessToken() ||
      busy.value ||
      !status.value?.available
    )
      return
    const ticket = session.begin()
    const revision = getAuthContextRevision()
    const capturedScope = scope()
    const current = () =>
      session.current(ticket) &&
      allowed.value &&
      !!getAccessToken() &&
      revision === getAuthContextRevision() &&
      capturedScope === scope()
    busy.value = true
    error.value = ''
    result.value = undefined
    Object.assign(draft, draftFromResult())
    try {
      const value = await recognizeDocument(file.value, ticket.signal)
      if (!current()) return
      result.value = value
      Object.assign(draft, draftFromResult(value))
    } catch {
      if (current()) error.value = '识别未完成：文件无效、服务不可用、繁忙或超时。'
    } finally {
      if (current()) busy.value = false
    }
  }
  const exportDraft = () => {
    if (!allowed.value || !getAccessToken() || !result.value || disposed) return
    if (exportUrl) URL.revokeObjectURL(exportUrl)
    const payload = {
      schemaVersion: 1,
      kind: 'LOCAL_OCR_DRAFT',
      officialDocumentCreated: false,
      fields: Object.fromEntries(
        Object.entries(draft).map(([key, value]) => [
          key,
          { value: value || null, state: value ? 'DRAFT' : 'UNKNOWN' }
        ])
      ),
      recognition: result.value
    }
    exportUrl = URL.createObjectURL(
      new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json' })
    )
    const link = document.createElement('a')
    link.href = exportUrl
    link.download = 'local-document-draft.json'
    link.click()
  }
  const stopAuth = onAuthContextChange(clear)
  watch(scope, clear, { flush: 'sync' })
  onBeforeUnmount(() => {
    disposed = true
    stopAuth()
    clear()
    session.dispose()
  })
  void refreshStatus()
  return {
    input,
    file,
    preview,
    kind,
    result,
    status,
    busy,
    error,
    draft,
    allowed,
    clear,
    selectFile,
    recognize,
    refreshStatus,
    exportDraft
  }
}
