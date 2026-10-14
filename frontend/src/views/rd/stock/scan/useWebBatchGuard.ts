import { computed, ref } from 'vue'
import type { WarehouseBatchRequestStatus } from '@/api/rd/stock/batch-status'
import type { WarehouseSession } from '../../pda/warehouse/useWarehouseSession'
import type { WarehouseBatchPending } from '../../pda/warehouse/batchPending'
import { useBatchPending } from '../../pda/warehouse/useBatchPending'
import { verifyReconcileStatus } from '../../pda/warehouse/reconcileStatus'

interface BatchResponse {
  task: { id: number; version?: number }
  recordId?: number
  confirmedCount: number
  success?: boolean
  replayed?: boolean
}
interface BatchPort<R extends BatchResponse> {
  confirm: (original: WarehouseBatchPending) => Promise<R>
  status: (original: WarehouseBatchPending) => Promise<WarehouseBatchRequestStatus>
  read: (original: WarehouseBatchPending) => Promise<R>
  confirmStale: () => Promise<unknown>
}

export const useWebBatchGuard = <R extends BatchResponse>(
  session: WarehouseSession,
  kind: 'putaway' | 'loading',
  port: BatchPort<R>
) => {
  const store = useBatchPending(session, kind)
  const { pending, storageError } = store
  const error = ref('')
  const busy = ref(false)
  const permission = kind === 'putaway' ? 'rd:putaway-task:confirm' : 'rd:loading-task:dispatch'
  const allowed = () => session.allowed(permission)
  const id = (original: WarehouseBatchPending) =>
    original.kind === 'putaway' ? original.request.id : original.request.taskId
  const locked = computed(() => busy.value || !!pending.value || !!storageError.value || !allowed())
  const submit = async (value?: WarehouseBatchPending) => {
    if (!allowed() || busy.value || storageError.value) return
    busy.value = true
    error.value = ''
    try {
      if (!pending.value) {
        if (!value) throw new Error('请先核对所选实际包件')
        store.capture(value)
      }
      const original = pending.value!
      store.persist()
      store.assertOriginal(original)
      const result = await port.confirm(original)
      store.assertOriginal(original)
      if (
        !allowed() ||
        result.task.id !== id(original) ||
        !Number.isSafeInteger(result.recordId) ||
        Number(result.recordId) <= 0 ||
        !Number.isSafeInteger(result.task.version) ||
        Number(result.task.version) < original.request.version ||
        !Number.isSafeInteger(result.confirmedCount) ||
        result.confirmedCount < 0 ||
        (result.replayed === true
          ? result.confirmedCount > original.request.taskLineIds.length
          : result.confirmedCount !== original.request.taskLineIds.length) ||
        result.success === false
      )
        throw new Error('原核验回执身份或结果不一致，请保留待核请求')
      if (result.replayed === true) {
        const status = await port.status(original)
        store.assertOriginal(original)
        if (
          verifyReconcileStatus(status, original) !== 'APPLIED' ||
          status.recordId !== result.recordId ||
          status.currentVersion > Number(result.task.version)
        )
          throw new Error('重放回执与原请求记录未核实，请保留待核请求')
      }
      store.clear(original)
      return result
    } catch (cause) {
      if (pending.value && session.current()) {
        if (
          cause &&
          typeof cause === 'object' &&
          'businessCode' in cause &&
          !pending.value.uncertain
        )
          store.clear()
        else {
          try {
            store.markUncertain()
          } catch {
            /* The original in-memory request remains locked. */
          }
        }
      }
      error.value = cause instanceof Error ? cause.message : '核验结果待核，请重试或核定原请求'
    } finally {
      busy.value = false
    }
  }
  const reconcile = async () => {
    if (!allowed() || busy.value || !pending.value) return
    const original = pending.value
    busy.value = true
    error.value = ''
    try {
      store.assertOriginal(original)
      const status = await port.status(original)
      store.assertOriginal(original)
      const state = verifyReconcileStatus(status, original)
      if (state === 'UNKNOWN') {
        error.value = '原请求结果仍未知，请保留并重试原请求'
        return
      }
      if (state === 'NOT_APPLIED_STALE') {
        await port.confirmStale()
        store.assertOriginal(original)
      }
      const response = await port.read(original)
      store.assertOriginal(original)
      if (
        !allowed() ||
        response.task.id !== id(original) ||
        !Number.isSafeInteger(response.task.version) ||
        Number(response.task.version) < status.currentVersion
      )
        throw new Error('原任务当前版本未确认，请保留待核请求')
      store.clear(original)
      return response
    } catch (cause) {
      if (cause !== 'cancel' && cause !== 'close')
        error.value = cause instanceof Error ? cause.message : '原请求核定失败，待核记录已保留'
    } finally {
      busy.value = false
    }
  }
  return { pending, storageError, error, busy, locked, submit, reconcile }
}
