import type { WarehouseBatchRequestStatus } from '@/api/rd/stock/batch-status'
import type { WarehouseBatchPending } from './batchPending'

export const verifyReconcileStatus = (
  value: WarehouseBatchRequestStatus,
  original: WarehouseBatchPending
) => {
  const id = original.kind === 'putaway' ? original.request.id : original.request.taskId
  if (
    !value ||
    value.taskId !== id ||
    value.requestVersion !== original.request.version ||
    !Number.isSafeInteger(value.currentVersion) ||
    value.currentVersion < 0 ||
    !['APPLIED', 'NOT_APPLIED_STALE', 'UNKNOWN'].includes(value.state) ||
    (value.state === 'APPLIED' &&
      (!Number.isSafeInteger(value.recordId) || Number(value.recordId) <= 0)) ||
    (value.state === 'NOT_APPLIED_STALE' && value.currentVersion <= original.request.version)
  )
    throw new Error('原请求核定结果不一致，请保留待核记录')
  return value.state
}
