import request from '@/config/axios'

export type RdInboundOrderStatus = 'DRAFT' | 'SUBMITTED' | 'VERIFIED' | 'POSTED' | 'REVERSED'

export interface RdInboundOrderLineVO {
  id?: number
  orderId?: number
  packageId: number
  locationId: number
  locationCode: string
  quantity: number
  weight: number
  stockLotId?: number
  stockTransactionId?: number
  remark?: string
}

export interface RdInboundOrderVO {
  id: number
  orderNo: string
  bizDate: string
  sourceType: string
  productionOutputId: number
  totalPackages: number
  totalWeight: number
  status: RdInboundOrderStatus
  submitterId?: number
  submitterName?: string
  submitTime?: string
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string
  posterId?: number
  posterName?: string
  postTime?: string
  reverserId?: number
  reverserName?: string
  reverseTime?: string
  reverseReason?: string
  version: number
  remark?: string
  createTime?: string
}

export interface RdInboundOrderCreateReqVO {
  bizDate: string
  productionOutputId: number
  lines: RdInboundOrderLineVO[]
  remark?: string
}

export interface RdInboundReverseReqVO {
  id: number
  reason: string
}

export const RdInboundOrderApi = {
  getPage: (params: PageParam) =>
    request.get<PageResult<RdInboundOrderVO[]>>({ url: '/rd/inbound-order/page', params }),
  get: (id: number) =>
    request.get<RdInboundOrderVO>({ url: '/rd/inbound-order/get', params: { id } }),
  create: (data: RdInboundOrderCreateReqVO) =>
    request.post<number>({ url: '/rd/inbound-order/create', data }),
  submit: (id: number) =>
    request.post<boolean>({ url: '/rd/inbound-order/submit', params: { id } }),
  verify: (id: number) =>
    request.post<boolean>({ url: '/rd/inbound-order/verify', params: { id } }),
  post: (id: number) => request.post<boolean>({ url: '/rd/inbound-order/post', params: { id } }),
  reverse: (data: RdInboundReverseReqVO) =>
    request.post<boolean>({ url: '/rd/inbound-order/reverse', data })
}
