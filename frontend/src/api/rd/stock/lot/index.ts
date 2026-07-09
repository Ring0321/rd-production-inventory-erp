import request from '@/config/axios'

export interface RdStockLotVO {
  id: number
  locationCode?: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  openingQty: number
  transferInQty: number
  transferOutQty: number
  inboundQty: number
  outboundQty: number
  returnQty: number
  warehouseQty: number
  packageWeight: number
  stockWeight: number
  status: string
}

export const RdStockLotApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/stock-lot/page', params })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/stock-lot/export-excel', params })
  }
}
