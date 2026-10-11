import request from '@/config/axios'
import type { WarehouseBatchRequestStatus } from '../batch-status'

export type RdPutawayTaskStatus =
  | 'PENDING_PUTAWAY'
  | 'ASSIGNED'
  | 'MOVING'
  | 'PENDING_CONFIRMATION'
  | 'PUTAWAY_COMPLETED'
  | 'EXCEPTION_PENDING'
  | 'CANCELLED'

export interface RdPendingPutawayPackageVO {
  id: number
  packageCode: string
  productionDate: string
  shiftCode?: string
  customerName: string
  productName?: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  packageWeight: number
  status: string
}

export interface RdPutawayLocationMapVO {
  id: number
  warehouseCode: string
  warehouseName: string
  areaCode?: string
  areaName?: string
  rowCode?: string
  columnCode?: string
  levelCode?: string
  locationCode: string
  locationName?: string
  capacityPackages?: number
  capacityWeight?: number
  occupiedPackages: number
  occupiedWeight: number
  reservedPackages: number
  reservedWeight: number
  availablePackages?: number
  availableWeight?: number
  usagePercent?: number
  frozen: boolean
  enabled: boolean
  locationStatus?: string
  displayStatus: string
}

export interface RdPutawayDriverOptionVO {
  id: number
  nickname: string
  username: string
}

export interface RdPutawayTaskVO {
  id: number
  taskNo: string
  status: RdPutawayTaskStatus
  plannedLocationId: number
  plannedLocationCode: string
  finalLocationId?: number
  finalLocationCode?: string
  packageCount: number
  scannedPackageCount: number
  exceptionPackageCount: number
  totalWeight: number
  warehouseKeeperId?: number
  warehouseKeeperName?: string
  forkliftDriverId: number
  forkliftDriverName: string
  assignedTime?: string
  startTime?: string
  scanCompleteTime?: string
  confirmTime?: string
  version: number
  remark?: string
  createTime?: string
}

export interface RdPutawayTaskLineVO {
  id: number
  lineNo: number
  packageId: number
  packageCode: string
  lineStatus: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  packageWeight: number
  scanCount: number
}

export interface RdPutawayExceptionVO {
  id: number
  exceptionNo: string
  packageCode?: string
  exceptionType: string
  exceptionStatus: string
  description: string
  reporterName?: string
  reportedTime: string
  handlingResult?: string
  handlerName?: string
  handleTime?: string
}

export interface RdPutawayTaskDetailVO {
  task: RdPutawayTaskVO
  lines: RdPutawayTaskLineVO[]
  exceptions: RdPutawayExceptionVO[]
}

export interface RdPendingPutawayPackagePageReqVO extends PageParam {
  keyword?: string
  customerName?: string
  productModel?: string
  furnaceNo?: string
  furnaceLayer?: string
}

export interface RdPutawayTaskPageReqVO extends PageParam {
  taskNo?: string
  status?: RdPutawayTaskStatus
  plannedLocationId?: number
  forkliftDriverId?: number
}

export interface RdPutawayTaskCreateReqVO {
  plannedLocationId: number
  forkliftDriverId: number
  packageIds: number[]
  remark?: string
}

export interface RdPutawayTaskReassignReqVO {
  id: number
  plannedLocationId: number
  forkliftDriverId: number
  version: number
  handlingResult: string
}

export interface RdPutawayScanReqVO {
  id: number
  packageCode: string
  idempotencyKey: string
  deviceCode?: string
  deviceTime?: string
}

export interface RdPutawayScanRespVO {
  recordId: number
  taskId: number
  packageCode: string
  scanResult: 'SUCCESS' | 'DUPLICATE' | 'MISMATCH' | 'STATUS_INVALID'
  success: boolean
  replayed: boolean
  message: string
  taskStatus: RdPutawayTaskStatus
  scannedPackageCount: number
  packageCount: number
  scanTime: string
}

export interface RdPutawayBatchScanRespVO extends RdPutawayTaskDetailVO {
  representativePackageCode: string
  recordId?: number
  replayed: boolean
  confirmedCount: number
}

export interface RdPutawayBatchConfirmReqVO extends RdPutawayScanReqVO {
  version: number
  taskLineIds: number[]
}

export type RdPutawayExceptionType =
  | 'LOCATION_CAPACITY_INSUFFICIENT'
  | 'LOCATION_OCCUPIED'
  | 'PACKAGE_MISMATCH'
  | 'LABEL_DAMAGED'
  | 'PACKAGE_MISSING'
  | 'OTHER'

export interface RdPutawayExceptionCreateReqVO {
  id: number
  exceptionType: RdPutawayExceptionType
  packageCode?: string
  description: string
  attachmentRefs?: string
}

export interface RdPutawayTaskConfirmReqVO {
  id: number
  finalLocationId: number
  version: number
}

export const RdPutawayTaskApi = {
  getPendingPackagePage: (params: RdPendingPutawayPackagePageReqVO) =>
    request.get<PageResult<RdPendingPutawayPackageVO[]>>({
      url: '/rd/putaway-task/pending-package-page',
      params
    }),
  getLocationMap: (params?: { warehouseCode?: string; areaCode?: string }) =>
    request.get<RdPutawayLocationMapVO[]>({ url: '/rd/putaway-task/location-map', params }),
  getDriverOptions: () =>
    request.get<RdPutawayDriverOptionVO[]>({ url: '/rd/putaway-task/driver-options' }),
  create: (data: RdPutawayTaskCreateReqVO) =>
    request.post<number>({ url: '/rd/putaway-task/create', data }),
  reassign: (data: RdPutawayTaskReassignReqVO) =>
    request.put<boolean>({ url: '/rd/putaway-task/reassign', data }),
  scan: (data: RdPutawayScanReqVO) =>
    request.post<RdPutawayScanRespVO>({ url: '/rd/putaway-task/scan', data }),
  previewBatchScan: (data: { id?: number; packageCode: string }) =>
    request.post<RdPutawayBatchScanRespVO>({ url: '/rd/putaway-task/batch-scan/preview', data }),
  confirmBatchScan: (data: RdPutawayBatchConfirmReqVO) =>
    request.post<RdPutawayBatchScanRespVO>({ url: '/rd/putaway-task/batch-scan/confirm', data }),
  getBatchStatus: (data: RdPutawayBatchConfirmReqVO) =>
    request.post<WarehouseBatchRequestStatus>({ url: '/rd/putaway-task/batch-scan/status', data }),
  reportException: (data: RdPutawayExceptionCreateReqVO) =>
    request.post<number>({ url: '/rd/putaway-task/exception', data }),
  confirm: (data: RdPutawayTaskConfirmReqVO) =>
    request.put<number>({ url: '/rd/putaway-task/confirm', data }),
  getPage: (params: RdPutawayTaskPageReqVO) =>
    request.get<PageResult<RdPutawayTaskVO[]>>({ url: '/rd/putaway-task/page', params }),
  get: (id: number) =>
    request.get<RdPutawayTaskDetailVO>({ url: '/rd/putaway-task/get', params: { id } }),
  getMyPage: (params: PageParam) =>
    request.get<PageResult<RdPutawayTaskVO[]>>({ url: '/rd/putaway-task/my-page', params }),
  getMy: (id: number) =>
    request.get<RdPutawayTaskDetailVO>({ url: '/rd/putaway-task/my-get', params: { id } })
}
