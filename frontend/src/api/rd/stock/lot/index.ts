import request from '@/config/axios'

export interface RdStockLotVO {
  id: number
  locationId?: number
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
  lockedQty: number
  availableQty: number
  packageWeight: number
  stockWeight: number
  lastBizDate?: string
  status: string
  remark?: string
  createTime?: string
}

export interface RdStockOpeningCreateReqVO {
  bizDate: string
  locationId?: number
  locationCode: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  quantity: number
  packageWeight: number
  remark?: string
}

export interface RdStockAdjustmentCreateReqVO {
  stockLotId: number
  bizDate: string
  adjustmentType: 'ADJUST_IN' | 'ADJUST_OUT'
  quantity: number
  remark: string
}

export const RdStockLotApi = {
  getPage: async (params: any) => {
    return await request.get({ url: '/rd/stock-lot/page', params })
  },
  get: async (id: number) => {
    return await request.get({ url: '/rd/stock-lot/get?id=' + id })
  },
  createOpening: async (data: RdStockOpeningCreateReqVO) => {
    return await request.post({ url: '/rd/stock-lot/opening/create', data })
  },
  createAdjustment: async (data: RdStockAdjustmentCreateReqVO) => {
    return await request.post({ url: '/rd/stock-lot/adjustment/create', data })
  },
  exportExcel: async (params: any) => {
    return await request.download({ url: '/rd/stock-lot/export-excel', params })
  }
}
