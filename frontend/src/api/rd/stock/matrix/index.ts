import request from '@/config/axios'

export interface RdStockMatrixDailyVO {
  bizDate: string
  inboundQty: number
  outboundQty: number
  transferQty: number
  returnQty: number
}

export interface RdStockMatrixRowVO {
  stockLotId?: number
  locationCode?: string
  customerName?: string
  productModel?: string
  furnaceNo?: string
  furnaceLayer?: string
  openingQty: number
  transferQty: number
  inboundQty: number
  outboundQty: number
  returnQty: number
  warehouseQty: number
  packageWeight: number
  stockWeight: number
  dailyMovements: RdStockMatrixDailyVO[]
}

export interface RdStockMatrixPageVO {
  dates: string[]
  list: RdStockMatrixRowVO[]
  total: number
  totals: RdStockMatrixRowVO
}

export const RdStockMatrixApi = {
  getPage: async (params: Record<string, any>) =>
    await request.get<RdStockMatrixPageVO>({ url: '/rd/stock-matrix/page', params })
}
