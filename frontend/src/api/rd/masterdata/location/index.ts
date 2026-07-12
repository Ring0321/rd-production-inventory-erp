import request from '@/config/axios'

export interface RdWarehouseLocationVO {
  id: number
  warehouseCode: string
  warehouseName: string
  areaCode?: string
  areaName?: string
  locationCode: string
  locationName?: string
  capacityPackages?: number
  allowedCategory?: string
  frozen: boolean
  enabled: boolean
  remark?: string
  createTime?: string
}

export type RdWarehouseLocationSaveReqVO = Omit<RdWarehouseLocationVO, 'id' | 'createTime'> & {
  id?: number
}

export interface RdMasterDataPageReqVO extends PageParam {
  keyword?: string
  enabled?: boolean
}

export const RdWarehouseLocationApi = {
  getPage: (params: RdMasterDataPageReqVO) =>
    request.get<PageResult<RdWarehouseLocationVO[]>>({
      url: '/rd/warehouse-location/page',
      params
    }),
  getOptions: (keyword?: string) =>
    request.get<RdWarehouseLocationVO[]>({
      url: '/rd/warehouse-location/options',
      params: { keyword }
    }),
  create: (data: RdWarehouseLocationSaveReqVO) =>
    request.post<number>({ url: '/rd/warehouse-location/create', data }),
  update: (data: RdWarehouseLocationSaveReqVO) =>
    request.put<boolean>({ url: '/rd/warehouse-location/update', data })
}
