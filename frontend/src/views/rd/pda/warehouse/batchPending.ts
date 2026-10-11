import type { RdPutawayBatchConfirmReqVO } from '@/api/rd/stock/putaway'
import type { RdLoadingApi } from '@/api/rd/loading'

export type LoadingBatchRequest = Parameters<typeof RdLoadingApi.confirmBatchScan>[0]
export type WarehouseBatchPending = {
  ownerId: number
  tenant: string
  uncertain: boolean
} & (
  | { kind: 'putaway'; request: RdPutawayBatchConfirmReqVO }
  | { kind: 'loading'; request: LoadingBatchRequest }
)

export const batchPendingKey = (kind: string, ownerId: number, tenant: string) =>
  `rd-warehouse-pending:${tenant}:${ownerId}:${kind}`

const positiveId = (value: unknown) => Number.isSafeInteger(value) && Number(value) > 0
const text = (value: unknown) => typeof value === 'string' && !!value.trim()
export const parseBatchPending = (
  raw: string,
  kind: 'putaway' | 'loading',
  ownerId: number,
  tenant: string
): WarehouseBatchPending => {
  if (raw.length > 1_000_000) throw new Error('待核请求过大')
  const value = JSON.parse(raw)
  const req = value?.request
  if (
    value?.kind !== kind ||
    value?.ownerId !== ownerId ||
    value?.tenant !== tenant ||
    typeof value?.uncertain !== 'boolean' ||
    !req ||
    !positiveId(kind === 'putaway' ? req.id : req.taskId) ||
    !text(req.idempotencyKey) ||
    req.idempotencyKey.length > 128 ||
    !text(kind === 'putaway' ? req.packageCode : req.representativePackageCode) ||
    !Array.isArray(req.taskLineIds) ||
    !req.taskLineIds.length ||
    req.taskLineIds.length > 500 ||
    !req.taskLineIds.every(positiveId) ||
    new Set(req.taskLineIds).size !== req.taskLineIds.length ||
    !Number.isSafeInteger(req.version) ||
    req.version < 0
  )
    throw new Error('待核请求与原账号、租户或业务不一致')
  return value
}

export const captureBatchPending = (value: WarehouseBatchPending) =>
  parseBatchPending(JSON.stringify(value), value.kind, value.ownerId, value.tenant)
