import request from '@/config/axios'

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
  getPage: (params: PageParam) =>
    request.get<PageResult<RdQualityInspectionVO[]>>({
      url: '/rd/quality-inspection/page',
      params
    }),
  get: (id: number) =>
    request.get<RdQualityInspectionVO>({ url: '/rd/quality-inspection/get', params: { id } }),
  create: (data: RdQualityInspectionCreateReqVO) =>
    request.post<number>({ url: '/rd/quality-inspection/create', data }),
  submit: (id: number) =>
    request.post<boolean>({ url: '/rd/quality-inspection/submit', params: { id } }),
  approve: (id: number) =>
    request.post<boolean>({ url: '/rd/quality-inspection/approve', params: { id } }),
  reject: (data: RdQualityReviewReqVO) =>
    request.post<boolean>({ url: '/rd/quality-inspection/reject', data })
}
