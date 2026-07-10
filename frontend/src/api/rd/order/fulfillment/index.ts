import request from '@/config/axios'

export interface RdOrderStockCandidateVO {
  id: number
  locationCode?: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  warehouseQty: number
  lockedQty: number
  availableQty: number
  packageWeight: number
  stockWeight: number
  lastBizDate?: string
  status: string
  sameCustomer: boolean
}

export interface RdOrderFulfillmentVO {
  id: number
  orderId: number
  stockLotId: number
  locationCode?: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  matchedQty: number
  allocatedQty: number
  transferQty: number
  outboundQty: number
  sourceType: string
  status: string
  remark?: string
  createTime?: string
}

export interface RdOrderFulfillmentAllocateReqVO {
  orderId: number
  stockLotId: number
  quantity: number
  remark?: string
}

export const RdOrderFulfillmentApi = {
  getCandidatePage: async (params: any) => {
    return await request.get({ url: '/rd/order-fulfillment/candidate-page', params })
  },
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/order-fulfillment/page', params })
  },
  allocate: async (data: RdOrderFulfillmentAllocateReqVO) => {
    return await request.post({ url: '/rd/order-fulfillment/allocate', data })
  },
  autoAllocate: async (orderId: number) => {
    return await request.post({ url: '/rd/order-fulfillment/auto-allocate', params: { orderId } })
  },
  cancel: async (id: number) => {
    return await request.put({ url: '/rd/order-fulfillment/cancel', params: { id } })
  }
}
