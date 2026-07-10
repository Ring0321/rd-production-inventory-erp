import request from '@/config/axios'

export interface RdTransferOrderVO {
  id: number
  transferNo: string
  transferDate: string
  fromOrderId?: number
  fromOrderNo?: string
  toOrderId: number
  toOrderNo?: string
  fromCustomerName?: string
  toCustomerName?: string
  productModel?: string
  fromStockLotId: number
  toStockLotId?: number
  fromLocationCode?: string
  toLocationCode?: string
  furnaceNo?: string
  furnaceLayer?: string
  quantity: number
  packageWeight: number
  weight: number
  status: string
  remark?: string
  createTime?: string
}

export interface RdTransferOrderSaveReqVO {
  id?: number
  transferNo: string
  transferDate: string
  fromOrderId?: number
  toOrderId: number
  fromStockLotId: number
  quantity: number
  remark?: string
}

export const RdTransferOrderApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/transfer-order/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/rd/transfer-order/get?id=' + id })
  },
  create: async (data: RdTransferOrderSaveReqVO) => {
    return await request.post({ url: '/rd/transfer-order/create', data })
  },
  update: async (data: RdTransferOrderSaveReqVO) => {
    return await request.put({ url: '/rd/transfer-order/update', data })
  },
  confirm: async (id: number) => {
    return await request.put({ url: '/rd/transfer-order/confirm', params: { id } })
  },
  cancel: async (id: number) => {
    return await request.put({ url: '/rd/transfer-order/cancel', params: { id } })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/transfer-order/export-excel', params })
  }
}
