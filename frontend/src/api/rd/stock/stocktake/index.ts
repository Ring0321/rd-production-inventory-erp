import request from '@/config/axios'

export type RdStocktakeStatus =
  | 'DRAFT'
  | 'FROZEN'
  | 'SCANNING'
  | 'COUNTING'
  | 'SUBMITTED'
  | 'APPROVED'
  | 'REVIEWED'
  | 'ADJUSTED'
  | 'CLOSED'
  | 'CANCELLED'
  | 'CANCELED'

export interface RdStocktakeOrderVO {
  id: number
  stocktakeNo: string
  stocktakeDate: string
  locationId: number
  locationCode: string
  status: string
  version: number
  snapshotTime?: string
  bookPackageQty: number
  countedPackageQty: number
  shortageQty: number
  surplusQty: number
  bookWeight: number
  countedWeight: number
  submitterName?: string
  submitTime?: string
  reviewerName?: string
  reviewTime?: string
  adjusterName?: string
  adjustTime?: string
  closerName?: string
  closeTime?: string
  remark?: string
}

export interface RdStocktakeLineVO {
  id: number
  orderId: number
  packageId: number
  packageCode: string
  stockLotId?: number
  customerName?: string
  productModel?: string
  furnaceNo?: string
  furnaceLayer?: string
  packageWeight: number
  bookQty: number
  countedQty: number
  differenceQty: number
  result?: string
  scannerName?: string
  scanTime?: string
}

export interface RdStocktakeDetailVO {
  order: RdStocktakeOrderVO
  lines: RdStocktakeLineVO[]
}

export interface RdStocktakeCreateReqVO {
  locationId?: number
  stocktakeDate: string
  remark?: string
}

export interface RdStocktakeActionReqVO {
  id: number
  version: number
}

export interface RdStocktakeScanReqVO {
  id: number
  packageCode: string
  deviceCode?: string
}

export interface RdStocktakePageReqVO extends PageParam {
  stocktakeNo?: string
  status?: string
  locationCode?: string
}

export const RdStocktakeApi = {
  getPage: (params: RdStocktakePageReqVO) =>
    request.get<PageResult<RdStocktakeOrderVO[]>>({ url: '/rd/stocktake/page', params }),
  getDetail: (id: number) =>
    request.get<RdStocktakeDetailVO>({ url: '/rd/stocktake/detail', params: { id } }),
  getDifference: (id: number) =>
    request.get<RdStocktakeDetailVO | RdStocktakeLineVO[]>({
      url: '/rd/stocktake/difference',
      params: { id }
    }),
  create: (data: RdStocktakeCreateReqVO) =>
    request.post<number>({ url: '/rd/stocktake/create', data }),
  freeze: (data: RdStocktakeActionReqVO) =>
    request.post<boolean>({ url: '/rd/stocktake/freeze', data }),
  scan: (data: RdStocktakeScanReqVO) =>
    request.post<boolean>({ url: '/rd/stocktake/scan', data }),
  submit: (data: RdStocktakeActionReqVO) =>
    request.post<boolean>({ url: '/rd/stocktake/submit', data }),
  approve: (data: RdStocktakeActionReqVO) =>
    request.post<boolean>({ url: '/rd/stocktake/approve', data }),
  adjust: (data: RdStocktakeActionReqVO) =>
    request.post<boolean>({ url: '/rd/stocktake/adjust', data }),
  close: (data: RdStocktakeActionReqVO) =>
    request.post<boolean>({ url: '/rd/stocktake/close', data }),
  cancel: (data: RdStocktakeActionReqVO) =>
    request.post<boolean>({ url: '/rd/stocktake/cancel', data })
}
