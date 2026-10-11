import request from '@/config/axios'
import type { WarehouseBatchRequestStatus } from '../stock/batch-status'

export interface PickupSlipLine {
  id: number
  slipId: number
  lineNo: number
  saleOrderId?: number
  productName?: string
  productModel: string
  furnaceNo?: string
  furnaceLayer?: string
  requestedPackageCount?: number
  requestedWeight?: number
  remark?: string
}

export interface PickupSlip {
  id: number
  slipNo: string
  status: string
  customerName: string
  pickupDate?: string
  vehiclePlate?: string
  transportDriverName?: string
  transportDriverPhone?: string
  requestedPackageCount?: number
  requestedWeight?: number
  sourceAttachmentRef?: string
  issuerName?: string
  issuedTime?: string
  remark?: string
}

export interface PickupSlipCreate {
  slipNo: string
  customerName: string
  pickupDate?: string
  vehiclePlate?: string
  transportDriverName?: string
  transportDriverPhone?: string
  requestedPackageCount?: number
  requestedWeight?: number
  sourceAttachmentRef?: string
  remark?: string
  lines: Array<Omit<PickupSlipLine, 'id' | 'slipId'>>
}

export interface LoadingPackage {
  id: number
  packageCode: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  packageWeight: number
  locationId: number
  locationCode: string
  stockLotId: number
  status: string
}

export interface LoadingTask {
  id: number
  taskNo: string
  pickupSlipId: number
  pickupSlipNo: string
  customerName: string
  vehiclePlate?: string
  transportDriverName?: string
  transportDriverPhone?: string
  warehouseKeeperName?: string
  forkliftDriverName?: string
  status: string
  plannedPackageCount: number
  scannedPackageCount: number
  plannedWeight: number
  scannedWeight: number
  planVersion?: number
  version?: number
  assignedTime?: string
  startTime?: string
  scanCompleteTime?: string
  confirmTime?: string
  remark?: string
}

export interface LoadingTaskLine {
  id: number
  lineNo: number
  pickupSlipLineId: number
  packageId: number
  packageCode: string
  saleOrderId?: number
  fulfillmentId?: number
  stockLotId?: number
  plannedLocationCode: string
  customerName?: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  packageWeight: number
  lineStatus: string
  planVersion?: number
  scanTime?: string
  remark?: string
}

export interface LoadingException {
  id: number
  exceptionNo: string
  taskId: number
  taskLineId?: number
  packageId?: number
  packageCode?: string
  planVersion?: number
  exceptionType: string
  exceptionStatus: string
  description?: string
  attachmentRefs?: string
  observedLocationCode?: string
  reporterName?: string
  reportedTime?: string
  handlerName?: string
  handleTime?: string
  handlingResult?: string
  resolutionAction?: string
  resolvedLocationCode?: string
  version?: number
}

export interface LoadingTaskDetail {
  task: LoadingTask
  lines: LoadingTaskLine[]
  exceptions: LoadingException[]
}

export interface LoadingReplacement {
  taskLineId: number
  newPackageCode: string
}

export interface LoadingDriverOption {
  id: number
  nickname: string
  username: string
}

export interface LoadingBatchScanResp {
  recordId?: number
  task: LoadingTask
  lines: LoadingTaskLine[]
  matchedTaskLineId?: number
  representativePackageCode: string
  scanResult: string
  success: boolean
  replayed: boolean
  confirmedCount: number
  remainingCount: number
  confirmedWeight: number
  message: string
}

export interface LoadingConfirmOutboundResp {
  taskId: number
  taskNo: string
  outboundOrderIds: number[]
  outboundPackageCount: number
  outboundWeight: number
  message: string
}

export interface LoadingBatchConfirmReq {
  taskId: number
  version: number
  representativePackageCode: string
  idempotencyKey: string
  taskLineIds: number[]
  deviceCode?: string
  deviceTime?: string
}

export const RdLoadingApi = {
  getBatchStatus: (data: LoadingBatchConfirmReq) =>
    request.post<WarehouseBatchRequestStatus>({ url: '/rd/loading-task/batch-scan/status', data }),
  createPickupSlip: (data: PickupSlipCreate) =>
    request.post<number>({ url: '/rd/loading-task/pickup-slip/create', data }),
  getPickupSlipPage: (
    params: PageParam & { slipNo?: string; customerName?: string; status?: string }
  ) => request.get<PageResult<PickupSlip[]>>({ url: '/rd/loading-task/pickup-slip/page', params }),
  getPickupSlip: (id: number) =>
    request.get<{ slip: PickupSlip; lines: PickupSlipLine[] }>({
      url: '/rd/loading-task/pickup-slip/get',
      params: { id }
    }),
  getAvailablePackagePage: (
    params: PageParam & {
      customerName?: string
      saleOrderId?: number
      productModel?: string
      furnaceNo?: string
      furnaceLayer?: string
      locationId?: number
      keyword?: string
      packageCode?: string
    }
  ) =>
    request.get<PageResult<LoadingPackage[]>>({
      url: '/rd/loading-task/available-package-page',
      params
    }),
  getDriverOptions: () =>
    request.get<LoadingDriverOption[]>({ url: '/rd/loading-task/driver-options' }),
  createTask: (data: {
    pickupSlipId: number
    forkliftDriverId: number
    selections: Array<{ pickupSlipLineId: number; packageId: number }>
    remark?: string
  }) => request.post<number>({ url: '/rd/loading-task/create', data }),
  previewBatchScan: (data: { taskId?: number; packageCode: string; deviceCode?: string }) =>
    request.post<LoadingBatchScanResp>({ url: '/rd/loading-task/batch-scan/preview', data }),
  confirmBatchScan: (data: LoadingBatchConfirmReq) =>
    request.post<LoadingBatchScanResp>({ url: '/rd/loading-task/batch-scan/confirm', data }),
  confirmOutbound: (data: { taskId: number; version: number; remark?: string }) =>
    request.post<LoadingConfirmOutboundResp>({ url: '/rd/loading-task/confirm-outbound', data }),
  reportException: (data: {
    taskId: number
    taskLineId?: number
    packageCode?: string
    exceptionType: string
    description: string
    observedLocationCode?: string
    attachmentRefs?: string
  }) => request.post<number>({ url: '/rd/loading-task/exception', data }),
  resolveException: (data: {
    taskId: number
    version: number
    resolutionAction: string
    handlingResult: string
    resolvedLocationId?: number
    resolvedLocationCode?: string
    replacements?: LoadingReplacement[]
  }) => request.put<boolean>({ url: '/rd/loading-task/exception/resolve', data }),
  getTaskPage: (params: PageParam & { taskNo?: string; pickupSlipNo?: string; status?: string }) =>
    request.get<PageResult<LoadingTask[]>>({ url: '/rd/loading-task/page', params }),
  getTask: (id: number) =>
    request.get<LoadingTaskDetail>({
      url: '/rd/loading-task/get',
      params: { id }
    }),
  getMyTaskPage: (params: PageParam & { activeOnly?: boolean }) =>
    request.get<PageResult<LoadingTask[]>>({ url: '/rd/loading-task/my-page', params }),
  getMyTask: (id: number) =>
    request.get<LoadingTaskDetail>({
      url: '/rd/loading-task/my-get',
      params: { id }
    })
}
