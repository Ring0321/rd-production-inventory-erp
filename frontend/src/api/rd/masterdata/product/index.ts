import request from '@/config/axios'

export interface RdProductMasterVO {
  id: number
  productCode: string
  productName: string
  customerName: string
  productModel: string
  category?: string
  unit?: string
  defaultPackageWeight: number
  qualityStandard?: string
  enabled: boolean
  remark?: string
  createTime?: string
}

export type RdProductMasterSaveReqVO = Omit<RdProductMasterVO, 'id' | 'createTime'> & {
  id?: number
}

export interface RdMasterDataPageReqVO extends PageParam {
  keyword?: string
  enabled?: boolean
}

export const RdProductMasterApi = {
  getPage: (params: RdMasterDataPageReqVO) =>
    request.get<PageResult<RdProductMasterVO[]>>({ url: '/rd/product-master/page', params }),
  getOptions: (keyword?: string) =>
    request.get<RdProductMasterVO[]>({ url: '/rd/product-master/options', params: { keyword } }),
  create: (data: RdProductMasterSaveReqVO) =>
    request.post<number>({ url: '/rd/product-master/create', data }),
  update: (data: RdProductMasterSaveReqVO) =>
    request.put<boolean>({ url: '/rd/product-master/update', data })
}
