export interface WarehouseBatchRequestStatus {
  taskId: number
  requestVersion: number
  currentVersion: number
  taskStatus: string
  state: 'APPLIED' | 'NOT_APPLIED_STALE' | 'UNKNOWN'
  recordId?: number
  message: string
}
