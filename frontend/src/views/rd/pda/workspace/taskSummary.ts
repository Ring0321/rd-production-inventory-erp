import type { RouteLocationRaw } from 'vue-router'
import type { RdProductionTaskVO } from '@/api/rd/production/task'
import type { RdPutawayTaskVO } from '@/api/rd/stock/putaway'
import type { LoadingTask } from '@/api/rd/loading'
import type { RouteAvailable, WorkspacePermissions } from './profile'
import { hasWorkspacePermission } from './profile'

export interface WorkspaceTaskSummary {
  id: number
  taskNo: string
  status: string
  target: { label: string; value: string }
  details: Array<{ label: string; value: string }>
  progress?: { done: number; total: number }
  to?: RouteLocationRaw
}

const statuses: Record<string, string> = {
  ACTIVE: '登记中',
  CLOSED: '已结束',
  CANCELLED: '已取消',
  PENDING_PUTAWAY: '待上架',
  PENDING_ASSIGNMENT: '待派单',
  ASSIGNED: '待执行',
  MOVING: '仓管核验中',
  LOADING: '装车中',
  PENDING_CONFIRMATION: '待仓管确认',
  PENDING_REVIEW: '待仓管复核',
  PUTAWAY_COMPLETED: '已上架',
  OUTBOUNDED: '已出库',
  EXCEPTION_PENDING: '异常待处理'
}

const text = (value: string | undefined) => value || '未记录'
const statusText = (value: string) =>
  Object.hasOwn(statuses, value) ? statuses[value] : text(value)
const quantity = (value: unknown, unit: string) =>
  (typeof value === 'number' || (typeof value === 'string' && value.trim() !== '')) &&
  Number.isFinite(Number(value))
    ? `${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 6 })} ${unit}`
    : '未记录'
const progress = (done: number, total: number) =>
  Number.isFinite(done) && Number.isFinite(total) && done >= 0 && total >= 0
    ? { done, total }
    : undefined

export const summarizeProductionTask = (
  task: RdProductionTaskVO,
  permissions: WorkspacePermissions,
  hasRoute: RouteAvailable
): WorkspaceTaskSummary => {
  const canRead = hasWorkspacePermission(permissions, 'rd:production-task:query')
  const routeName =
    canRead &&
    (hasWorkspacePermission(permissions, 'rd:production-output:create') && hasRoute('PdaProduction')
      ? 'PdaProduction'
      : hasRoute('RdProductionTask')
        ? 'RdProductionTask'
        : undefined)
  return {
    id: task.id,
    taskNo: text(task.taskNo),
    status: statusText(task.status),
    target: { label: '客户', value: text(task.customerName) },
    details: [
      { label: '生产日期', value: text(task.productionDate) },
      { label: '班次', value: text(task.shiftCode) }
    ],
    to: routeName ? { name: routeName, query: { productionTaskId: String(task.id) } } : undefined
  }
}

export const summarizePutawayTask = (task: RdPutawayTaskVO): WorkspaceTaskSummary => ({
  id: task.id,
  taskNo: text(task.taskNo),
  status: statusText(task.status),
  target: { label: '计划货位', value: text(task.plannedLocationCode) },
  details: [
    { label: '包数', value: quantity(task.packageCount, '包') },
    { label: '重量', value: quantity(task.totalWeight, 'kg') },
    { label: '异常包数', value: quantity(task.exceptionPackageCount, '包') },
    ...(task.finalLocationCode ? [{ label: '确认货位', value: task.finalLocationCode }] : [])
  ],
  progress: progress(task.scannedPackageCount, task.packageCount),
  to: { name: 'PdaDriverPutaway', query: { taskId: String(task.id) } }
})

export const summarizeLoadingTask = (task: LoadingTask): WorkspaceTaskSummary => ({
  id: task.id,
  taskNo: text(task.taskNo),
  status: task.status === 'ASSIGNED' ? '待装车' : statusText(task.status),
  target: { label: '车辆', value: text(task.vehiclePlate) },
  details: [
    { label: '客户', value: text(task.customerName) },
    { label: '提货单', value: text(task.pickupSlipNo) },
    { label: '计划包数', value: quantity(task.plannedPackageCount, '包') },
    { label: '计划重量', value: quantity(task.plannedWeight, 'kg') },
    { label: '核验重量', value: quantity(task.scannedWeight, 'kg') }
  ],
  progress: progress(task.scannedPackageCount, task.plannedPackageCount),
  to: { name: 'PdaDriverLoading', query: { taskId: String(task.id) } }
})
