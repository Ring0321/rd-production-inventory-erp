import request from '@/config/axios'

export interface RdProductionOutputVO {
  id: number
  productionDate: string
  locationCode?: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  outputQty: number
  packageWeight: number
  outputWeight: number
  inboundQty: number
  status: string
}

export const RdProductionOutputApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/production-output/page', params })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/production-output/export-excel', params })
  }
}
