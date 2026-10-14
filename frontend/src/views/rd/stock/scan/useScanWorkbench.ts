import { computed, ref, shallowRef, watch, onBeforeUnmount, onDeactivated, onActivated } from 'vue'
import { createPendingScanBox, type PendingScanBox } from './pendingScanBox'
import {
  classifyScan,
  reconcileDraft,
  scanScopeKey,
  snapshotValid,
  candidateWeight,
  type ScanDraft,
  type ScanScope,
  type ScanSnapshot
} from './scanCandidates'

export interface ScanWorkbenchContext {
  snapshot: () => ScanSnapshot | undefined
  scope: () => ScanScope
  current: () => boolean
  locked: () => boolean
  readSnapshot: () => Promise<ScanSnapshot>
}

export const useScanWorkbench = (
  context: ScanWorkbenchContext,
  box: PendingScanBox = createPendingScanBox()
) => {
  const rows = shallowRef<ScanDraft[]>([])
  const observations = shallowRef<Array<{ code?: string; kind: string; label: string }>>([])
  const selected = ref<string[]>([])
  const verified = ref<string[]>([])
  const error = ref('')
  const checking = ref(false)
  const queued = ref(0)
  const capturing = computed(() => queued.value > 0)
  let active = true
  let revision = 0
  let captureQueue: Promise<void> = Promise.resolve()
  let verifiedVersion: number | undefined
  const current = () => active && context.current()
  const ready = () => current() && !context.locked() && !!context.snapshot()
  const captureContext = () => {
    const snapshot = context.snapshot()
    const scope = { ...context.scope() }
    const generation = revision
    if (!ready() || !snapshot || !snapshotValid(snapshot)) throw new Error('请先打开有效仓管任务')
    const key = scanScopeKey(scope)
    const version = snapshot.version
    const guard = () =>
      ready() &&
      generation === revision &&
      scanScopeKey(context.scope()) === key &&
      context.snapshot()?.version === version
    return { snapshot, scope, guard }
  }
  const load = async () => {
    if (!current() || !context.snapshot()) return
    const generation = revision
    const scope = { ...context.scope() }
    const key = scanScopeKey(scope)
    try {
      const values = await box.list(scope)
      if (!current() || generation !== revision || scanScopeKey(context.scope()) !== key) return
      rows.value = values
      error.value = ''
    } catch (cause) {
      if (current() && generation === revision)
        error.value = cause instanceof Error ? cause.message : '本机待同步箱读取失败'
    }
  }
  const invalidate = () => {
    revision++
    verified.value = []
    selected.value = []
    verifiedVersion = undefined
    observations.value = []
    rows.value = []
  }
  watch(
    () => [
      context.snapshot()?.taskId,
      context.snapshot()?.version,
      context.scope().ownerId,
      context.scope().tenant,
      context.scope().kind,
      context.current()
    ],
    () => {
      invalidate()
      void load()
    }
  )
  const collect = (rawValues: string[]) => {
    if (!ready() || checking.value) return Promise.resolve()
    const captured = captureContext()
    queued.value++
    const execute = async () => {
      if (!captured.guard()) return
      try {
        const seen = (await box.list(captured.scope)).map((row) => row.packageCode)
        let changed = false
        const results: Array<{ code?: string; kind: string; label: string }> = []
        for (const raw of rawValues.slice(0, 100)) {
          if (!captured.guard()) return
          const result = classifyScan(raw, captured.snapshot, seen)
          results.push(result)
          if (!result.code || result.kind === 'INVALID' || seen.includes(result.code)) continue
          if (!changed) {
            verified.value = []
            selected.value = []
            verifiedVersion = undefined
            changed = true
          }
          await box.collect(
            captured.scope,
            captured.snapshot.version,
            result.code,
            captured.guard,
            result.kind === 'MATCH' ? 'LOCAL' : 'CONFLICT',
            result.label
          )
          seen.push(result.code)
        }
        if (captured.guard()) observations.value = results.slice(-12)
        await load()
      } catch (cause) {
        if (captured.guard())
          error.value = cause instanceof Error ? cause.message : '本机保存失败，未确认保存'
      }
    }
    const result = captureQueue.then(execute).finally(() => {
      queued.value--
    })
    captureQueue = result.catch(() => undefined)
    return result
  }
  const check = async () => {
    if (!ready() || checking.value || capturing.value) return false
    const captured = captureContext()
    checking.value = true
    verified.value = []
    selected.value = []
    verifiedVersion = undefined
    error.value = ''
    try {
      const original = await box.list(captured.scope)
      const fresh = await context.readSnapshot()
      if (!captured.guard()) return false
      if (
        !snapshotValid(fresh) ||
        fresh.taskId !== captured.scope.taskId ||
        fresh.kind !== captured.scope.kind
      )
        throw new Error('服务器任务身份不一致')
      const updates = original.map((row) => ({
        packageCode: row.packageCode,
        ...reconcileDraft(row, fresh)
      }))
      await box.mark(captured.scope, updates, captured.guard)
      if (!captured.guard()) return false
      const latest = await box.list(captured.scope)
      if (!captured.guard()) return false
      rows.value = latest
      verified.value = updates
        .filter((row) => row.state === 'VERIFIED')
        .map((row) => row.packageCode)
      verifiedVersion = fresh.version
      selected.value = [...verified.value]
      return true
    } catch (cause) {
      if (captured.guard())
        error.value = cause instanceof Error ? cause.message : '服务器核对失败，本机记录仍待核'
      return false
    } finally {
      checking.value = false
    }
  }
  const remove = async (code: string) => {
    if (!ready() || checking.value || capturing.value) return
    const captured = captureContext()
    try {
      await box.remove(captured.scope, code, captured.guard)
      verified.value = verified.value.filter((value) => value !== code)
      selected.value = selected.value.filter((value) => value !== code)
      await load()
    } catch (cause) {
      if (captured.guard()) error.value = cause instanceof Error ? cause.message : '移除失败'
    }
  }
  const canTransfer = computed(
    () =>
      ready() &&
      !checking.value &&
      !capturing.value &&
      !error.value &&
      selected.value.length > 0 &&
      verifiedVersion === context.snapshot()?.version &&
      selected.value.every((code) => verified.value.includes(code))
  )
  const transfer = async () => {
    if (!canTransfer.value) return
    const chosen = [...selected.value]
    if (!(await check())) return
    const snapshot = context.snapshot()
    if (
      !snapshot ||
      verifiedVersion !== snapshot.version ||
      !chosen.every((code) => verified.value.includes(code))
    ) {
      error.value = '任务或所选包件已变化，请重新核对'
      return
    }
    const lineIds = chosen.map(
      (code) => snapshot.lines.find((line) => line.packageCode === code)!.lineId
    )
    return { taskId: snapshot.taskId, version: snapshot.version, packageCodes: chosen, lineIds }
  }
  const weight = computed(() =>
    context.snapshot()
      ? candidateWeight(
          rows.value
            .filter((row) => row.state !== 'CONFLICT' && row.state !== 'PROCESSED')
            .map((row) => row.packageCode),
          context.snapshot()!
        )
      : undefined
  )
  onDeactivated(() => {
    active = false
    invalidate()
  })
  onActivated(() => {
    active = true
    invalidate()
    void load()
  })
  onBeforeUnmount(() => {
    active = false
    invalidate()
    box.close()
  })
  return {
    rows,
    observations,
    selected,
    verified,
    error,
    checking,
    capturing,
    canTransfer,
    weight,
    load,
    collect,
    check,
    remove,
    transfer,
    invalidate
  }
}
