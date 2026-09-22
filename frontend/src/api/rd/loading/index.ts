import request from '@/config/axios'

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
  warehouseKeeperName?: string
  forkliftDriverName?: string
  status: string
  plannedPackageCount: number
  scannedPackageCount: number
  plannedWeight: number
  scannedWeight: number
  assignedTime?: string
}

export interface LoadingTaskLine {
  id: number
  lineNo: number
  pickupSlipLineId: number
  packageId: number
  packageCode: string
  saleOrderId?: number
  plannedLocationCode: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  packageWeight: number
  lineStatus: string
}

export interface LoadingDriverOption {
  id: number
  nickname: string
  username: string
}

export const RdLoadingApi = {
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
  getTaskPage: (params: PageParam & { taskNo?: string; pickupSlipNo?: string; status?: string }) =>
    request.get<PageResult<LoadingTask[]>>({ url: '/rd/loading-task/page', params }),
  getTask: (id: number) =>
    request.get<{ task: LoadingTask; lines: LoadingTaskLine[] }>({
      url: '/rd/loading-task/get',
      params: { id }
    }),
  getMyTaskPage: (params: PageParam) =>
    request.get<PageResult<LoadingTask[]>>({ url: '/rd/loading-task/my-page', params }),
  getMyTask: (id: number) =>
    request.get<{ task: LoadingTask; lines: LoadingTaskLine[] }>({
      url: '/rd/loading-task/my-get',
      params: { id }
    })
}
