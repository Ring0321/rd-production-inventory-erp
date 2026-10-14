import type { LoadingTaskDetail, LoadingBatchScanResp } from '@/api/rd/loading'
import type { RdPutawayTaskDetailVO } from '@/api/rd/stock/putaway'
import { snapshotValid, taskOpen, type ScanSnapshot } from './scanCandidates'

export const loadingSnapshot = (value: LoadingTaskDetail | LoadingBatchScanResp): ScanSnapshot => ({
  kind: 'loading',
  taskId: value.task.id,
  taskNo: value.task.taskNo,
  version: value.task.version ?? -1,
  status: value.task.status,
  lines: value.lines.map((line) => ({
    lineId: line.id,
    packageCode: line.packageCode,
    packageWeight: line.packageWeight,
    eligible: line.lineStatus === 'PLANNED'
  }))
})
export const putawaySnapshot = (value: RdPutawayTaskDetailVO): ScanSnapshot => ({
  kind: 'putaway',
  taskId: value.task.id,
  taskNo: value.task.taskNo,
  version: value.task.version,
  status: value.task.status,
  lines: value.lines.map((line) => ({
    lineId: line.id,
    packageCode: line.packageCode,
    packageWeight: line.packageWeight,
    eligible: line.lineStatus === 'PENDING' && line.scanCount === 0
  }))
})
export interface ScanSelection {
  taskId: number
  version: number
  packageCodes: string[]
  lineIds: number[]
}
export const verifyScanSelection = (snapshot: ScanSnapshot, value: ScanSelection) => {
  if (
    !snapshotValid(snapshot) ||
    !taskOpen(snapshot) ||
    value.taskId !== snapshot.taskId ||
    value.version !== snapshot.version ||
    !value.lineIds.length ||
    value.lineIds.length > 500 ||
    value.lineIds.length !== value.packageCodes.length ||
    new Set(value.lineIds).size !== value.lineIds.length ||
    new Set(value.packageCodes).size !== value.packageCodes.length
  )
    return false
  return value.lineIds.every((id, index) =>
    snapshot.lines.some(
      (line) =>
        line.lineId === id && line.packageCode === value.packageCodes[index] && line.eligible
    )
  )
}
