import request from '@/config/axios'

export interface RdProductionOutputVO {
  id: number
  productionDate: string
  locationId?: number
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
  sourceSheet?: string
  remark?: string
  createTime?: string
}

export const RdProductionOutputApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/production-output/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/rd/production-output/get?id=' + id })
  },
  create: async (data: Partial<RdProductionOutputVO>) => {
    return await request.post({ url: '/rd/production-output/create', data })
  },
  update: async (data: Partial<RdProductionOutputVO>) => {
    return await request.put({ url: '/rd/production-output/update', data })
  },
  cancel: async (id: number) => {
    return await request.put({ url: '/rd/production-output/cancel', params: { id } })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/production-output/export-excel', params })
  }
}
