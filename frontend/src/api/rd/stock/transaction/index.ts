import request from '@/config/axios'

export interface RdStockTransactionVO {
  id: number
  bizDate: string
  transactionType: string
  stockLotId: number
  locationId?: number
  locationCode?: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  quantity: number
  packageWeight: number
  weight: number
  beforeQty: number
  afterQty: number
  sourceType?: string
  sourceNo?: string
  sourceLineNo?: string
  relatedOrderId?: number
  operatorId?: number
  operatorName?: string
  remark?: string
  createTime?: string
}

export const RdStockTransactionApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/stock-transaction/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/rd/stock-transaction/get?id=' + id })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/stock-transaction/export-excel', params })
  }
}
