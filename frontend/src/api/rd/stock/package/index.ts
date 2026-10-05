import request from '@/config/axios'

export type RdPackageStatus =
  | 'CREATED'
  | 'LABELED'
  | 'QC_PENDING'
  | 'QC_PASSED'
  | 'QC_REJECTED'
  | 'INBOUND_PENDING'
  | 'IN_STOCK'
  | 'OUTBOUND_PENDING'
  | 'OUTBOUNDED'
  | 'VOIDED'

export interface RdPackageUnitVO {
  id: number
  packageCode: string
  qrCode: string
  productionOutputId: number
  productionDate: string
  furnaceOutDate?: string
  shiftCode?: string
  customerName: string
  productName?: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  packageWeight: number
  locationId?: number
  locationCode?: string
  stockLotId?: number
  status: RdPackageStatus
  version: number
  remark?: string
  createTime?: string
}

export interface RdPackageGenerateReqVO {
  productionOutputId: number
  packageCount: number
}

export interface RdPackagePrintReqVO {
  packageId: number
  reprintReason?: string
}

export type RdPackageUnitPageReqVO = PageParam & {
  packageCode?: string
  productionOutputId?: number
}

export interface RdPackageTraceEventVO {
  stage: 'PRODUCTION' | 'QUALITY' | 'STOCK' | string
  title: string
  eventTime: string
  detail?: string
}

export interface RdPackageTraceVO {
  packageCode: string
  digitalLink: string
  status: RdPackageStatus
  statusName: string
  productionDate?: string
  furnaceOutDate?: string
  shiftCode?: string
  productName?: string
  productModel?: string
  furnaceNo?: string
  furnaceLayer?: string
  packageWeight?: number
  qualityStatus?: string
  qualityConclusion?: string
  internalView: boolean
  customerName?: string
  locationCode?: string
  productionOutputId?: number
  events: RdPackageTraceEventVO[]
}

export const RdPackageUnitApi = {
  getPage: (params: RdPackageUnitPageReqVO) =>
    request.get<PageResult<RdPackageUnitVO[]>>({ url: '/rd/package-unit/page', params }),
  get: (id: number) =>
    request.get<RdPackageUnitVO>({ url: '/rd/package-unit/get', params: { id } }),
  trace: (params: number | { packageCode?: string; productionOutputId?: number }) =>
    request.get<RdPackageUnitVO[]>({
      url: '/rd/package-unit/trace',
      params: typeof params === 'number' ? { productionOutputId: params } : params
    }),
  getPublicTrace: (packageCode: string) =>
    request.get<RdPackageTraceVO | null>({
      url: `/rd/package-unit/public-trace/${encodeURIComponent(packageCode)}`,
      headers: { isToken: false }
    }),
  getTraceDetail: (packageCode: string) =>
    request.get<RdPackageTraceVO | null>({
      url: `/rd/package-unit/trace-detail/${encodeURIComponent(packageCode)}`
    }),
  generate: (data: RdPackageGenerateReqVO) =>
    request.post<RdPackageUnitVO[]>({ url: '/rd/package-unit/generate', data }),
  print: (data: RdPackagePrintReqVO) =>
    request.post<boolean>({ url: '/rd/package-unit/print', data }),
  reprint: (data: RdPackagePrintReqVO) =>
    request.post<boolean>({ url: '/rd/package-unit/reprint', data }),
  exportExcel: (params: { packageCode?: string; productionOutputId?: number }) =>
    request.download({ url: '/rd/package-unit/export-excel', params })
}
