import request from '@/config/axios'

export interface RdProductionOutputVO {
  id: number
  productionTaskId?: number
  productionTaskNo?: string
  shiftCode?: string
  furnaceOutDate?: string
  operatorId?: number
  operatorName?: string
  clientRequestId?: string
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
  version: number
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
  confirm: async (data: { id: number; version: number }) => {
    return await request.post({ url: '/rd/production-output/confirm', data })
  },
  cancel: async (data: { id: number; version: number }) => {
    return await request.put({ url: '/rd/production-output/cancel', data })
  },
  inbound: async (id: number) => {
    return await request.put({ url: '/rd/production-output/inbound', params: { id } })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/production-output/export-excel', params })
  }
}
