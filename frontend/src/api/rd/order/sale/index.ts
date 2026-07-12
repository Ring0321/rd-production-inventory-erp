import request from '@/config/axios'

export interface RdSaleOrderVO {
  id: number
  orderNo: string
  orderDate: string
  customerName: string
  productModel: string
  requiredQty: number
  manualOutputQty: number
  splitOutputQty: number
  actualOutputQty: number
  transferInQty: number
  transferOutQty: number
  allocatedQty: number
  differenceQty: number
  adjustedDifferenceQty: number
  fulfilledQty: number
  status: string
  closeTime?: string
  closeBy?: string
  remark?: string
  createTime?: string
}

export const RdSaleOrderApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/sale-order/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/rd/sale-order/get?id=' + id })
  },
  create: async (data: Partial<RdSaleOrderVO>) => {
    return await request.post({ url: '/rd/sale-order/create', data })
  },
  update: async (data: Partial<RdSaleOrderVO>) => {
    return await request.put({ url: '/rd/sale-order/update', data })
  },
  cancel: async (id: number) => {
    return await request.put({ url: '/rd/sale-order/cancel', params: { id } })
  },
  recalculate: async (id: number) => {
    return await request.put({ url: '/rd/sale-order/recalculate', params: { id } })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/sale-order/export-excel', params })
  }
}
