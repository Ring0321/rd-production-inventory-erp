import request from '@/config/axios'

export interface RdOutboundOrderVO {
  id: number
  outboundNo: string
  outboundDate: string
  orderId: number
  orderNo?: string
  fulfillmentId: number
  stockLotId: number
  customerName?: string
  productModel?: string
  locationCode?: string
  furnaceNo?: string
  furnaceLayer?: string
  quantity: number
  packageWeight: number
  weight: number
  status: string
  version?: number
  submitterName?: string
  submitTime?: string
  reviewerName?: string
  reviewTime?: string
  posterName?: string
  postTime?: string
  reverserName?: string
  reverseTime?: string
  reverseReason?: string
  remark?: string
  createTime?: string
}

export interface RdOutboundOrderSaveReqVO {
  id?: number
  outboundNo: string
  outboundDate: string
  orderId: number
  fulfillmentId: number
  quantity?: number
  remark?: string
}

export interface RdOutboundOrderLineVO {
  id: number
  orderId: number
  packageId: number
  packageCode: string
  stockLotId: number
  locationCode?: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  packageWeight: number
  scannerName?: string
  createTime?: string
}

export interface RdOutboundOrderDetailVO {
  order: RdOutboundOrderVO
  lines: RdOutboundOrderLineVO[]
}

export const RdOutboundOrderApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/outbound-order/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/rd/outbound-order/get?id=' + id })
  },
  getDetail: async (id: number): Promise<RdOutboundOrderDetailVO> => {
    return await request.get({ url: '/rd/outbound-order/detail', params: { id } })
  },
  create: async (data: RdOutboundOrderSaveReqVO) => {
    return await request.post({ url: '/rd/outbound-order/create', data })
  },
  update: async (data: RdOutboundOrderSaveReqVO) => {
    return await request.put({ url: '/rd/outbound-order/update', data })
  },
  confirm: async (id: number) => {
    return await request.put({ url: '/rd/outbound-order/confirm', params: { id } })
  },
  scan: async (data: { id: number; packageCode: string; deviceCode?: string }) => {
    return await request.post({ url: '/rd/outbound-order/scan', data })
  },
  removeScan: async (id: number, packageId: number) => {
    return await request.put({ url: '/rd/outbound-order/remove-scan', params: { id, packageId } })
  },
  submit: async (id: number, version?: number) => {
    return await request.post({ url: '/rd/outbound-order/submit', data: { id, version } })
  },
  approve: async (id: number, version?: number) => {
    return await request.post({ url: '/rd/outbound-order/approve', data: { id, version } })
  },
  post: async (id: number, version?: number) => {
    return await request.post({ url: '/rd/outbound-order/post', data: { id, version } })
  },
  reverse: async (id: number, reason: string, version?: number) => {
    return await request.post({ url: '/rd/outbound-order/reverse', data: { id, reason, version } })
  },
  cancel: async (id: number) => {
    return await request.put({ url: '/rd/outbound-order/cancel', params: { id } })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/outbound-order/export-excel', params })
  }
}
