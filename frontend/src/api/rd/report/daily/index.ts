import request from '@/config/axios'

export interface RdDailyStockSummaryVO {
  id: number
  bizDate: string
  stockLotId: number
  locationCode: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  productionQty: number
  inboundQty: number
  outboundQty: number
  transferInQty: number
  transferOutQty: number
  returnQty: number
  netChangeQty: number
  warehouseQty: number
  stockWeight: number
}

export interface RdDailyStockTrendVO {
  bizDate: string
  productionQty: number
  inboundQty: number
  outboundQty: number
  transferInQty: number
  transferOutQty: number
  returnQty: number
  netChangeQty: number
}

export const RdDailyStockSummaryApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/daily-stock-summary/page', params })
  },
  getTrend: async (params: any) => {
    return await request.get({ url: '/rd/daily-stock-summary/trend', params })
  },
  rebuild: async (startDate: string, endDate: string) => {
    return await request.post({
      url: '/rd/daily-stock-summary/rebuild',
      params: { startDate, endDate }
    })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/daily-stock-summary/export-excel', params })
  }
}
