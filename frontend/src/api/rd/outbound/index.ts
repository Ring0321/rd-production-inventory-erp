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
  remark?: string
  createTime?: string
}

export interface RdOutboundOrderSaveReqVO {
  id?: number
  outboundNo: string
  outboundDate: string
  orderId: number
  fulfillmentId: number
  quantity: number
  remark?: string
}

export const RdOutboundOrderApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/outbound-order/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/rd/outbound-order/get?id=' + id })
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
  cancel: async (id: number) => {
    return await request.put({ url: '/rd/outbound-order/cancel', params: { id } })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/outbound-order/export-excel', params })
  }
}
