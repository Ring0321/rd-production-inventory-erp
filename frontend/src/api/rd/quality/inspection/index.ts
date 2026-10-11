import request from '@/config/axios'
import type { RdPackageUnitVO } from '@/api/rd/stock/package'

export type RdQualityInspectionStatus =
  | 'DRAFT'
  | 'SUBMITTED'
  | 'PASSED'
  | 'REJECTED'
  | 'CORRECTED'
  | 'RESUBMITTED'

export interface RdQualityInspectionItemVO {
  id?: number
  inspectionId?: number
  samplePackageId?: number
  itemCode: string
  itemName: string
  standardMin?: number
  standardMax?: number
  measuredValue?: number
  unit?: string
  qualified?: boolean
  remark?: string
}

export interface RdQualityInspectionVO {
  id: number
  inspectionNo: string
  packageId: number
  status: RdQualityInspectionStatus
  conclusion?: string
  rejectReason?: string
  submitterId?: number
  submitterName?: string
  submitTime?: string
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string
  version: number
  remark?: string
  createTime?: string
  items?: RdQualityInspectionItemVO[]
  productionOutputId?: number
  clientRequestId?: string
  registrarId?: number
  registrarName?: string
  members?: RdQualityMemberVO[]
  coverageCount?: number
  sampleCount?: number
  events?: RdQualityEventVO[]
}

export interface RdQualityEventVO {
  id: number
  action: string
  actorId: number
  actorName?: string
  beforeStatus?: string
  afterStatus: string
  eventTime: string | number
  coverageCount: number
  sampleCount?: number
  detail?: string
}

export interface RdQualityMemberVO {
  packageId: number
  packageCode: string
  packageWeight: number
  sampled?: boolean
}

export interface RdQualityScopePackageVO extends RdPackageUnitVO {
  eligible: boolean
  activeInspectionId?: number
  activeInspectionNo?: string
}

export interface RdQualityScopeVO {
  representative: RdQualityScopePackageVO
  productionOutputId?: number
  packages: { list: RdQualityScopePackageVO[]; total: number }
}

export interface RdQualityBatchCreateReqVO extends RdQualityInspectionCreateReqVO {
  packageIds: number[]
  samplePackageIds: number[]
  clientRequestId: string
}

export interface RdQualityInspectionCreateReqVO {
  packageId: number
  items: RdQualityInspectionItemVO[]
  remark?: string
}

export interface RdQualityReviewReqVO {
  id: number
  reason?: string
}

export const RdQualityInspectionApi = {
  getPage: (params: PageParam & { status?: string; inspectionNo?: string }) =>
    request.get<PageResult<RdQualityInspectionVO[]>>({
      url: '/rd/quality-inspection/page',
      params
    }),
  get: (id: number) =>
    request.get<RdQualityInspectionVO>({ url: '/rd/quality-inspection/get', params: { id } }),
  create: (data: RdQualityInspectionCreateReqVO) =>
    request.post<number>({ url: '/rd/quality-inspection/create', data }),
  scope: (params: PageParam & { packageCode: string }) =>
    request.get<RdQualityScopeVO>({ url: '/rd/quality-inspection/scope', params }),
  createBatch: (data: RdQualityBatchCreateReqVO) =>
    request.post<number>({ url: '/rd/quality-inspection/create-batch', data }),
  submit: (id: number) =>
    request.post<boolean>({ url: '/rd/quality-inspection/submit', params: { id } }),
  approve: (id: number) =>
    request.post<boolean>({ url: '/rd/quality-inspection/approve', params: { id } }),
  reject: (data: RdQualityReviewReqVO) =>
    request.post<boolean>({ url: '/rd/quality-inspection/reject', data })
}
