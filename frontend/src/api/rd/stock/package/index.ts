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

export const RdPackageUnitApi = {
  getPage: (params: PageParam) =>
    request.get<PageResult<RdPackageUnitVO[]>>({ url: '/rd/package-unit/page', params }),
  get: (id: number) =>
    request.get<RdPackageUnitVO>({ url: '/rd/package-unit/get', params: { id } }),
  trace: (productionOutputId: number) =>
    request.get<RdPackageUnitVO[]>({
      url: '/rd/package-unit/trace',
      params: { productionOutputId }
    }),
  generate: (data: RdPackageGenerateReqVO) =>
    request.post<RdPackageUnitVO[]>({ url: '/rd/package-unit/generate', data }),
  print: (data: RdPackagePrintReqVO) =>
    request.post<boolean>({ url: '/rd/package-unit/print', data }),
  reprint: (data: RdPackagePrintReqVO) =>
    request.post<boolean>({ url: '/rd/package-unit/reprint', data })
}
