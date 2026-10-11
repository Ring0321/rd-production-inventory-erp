import request from '@/config/axios'

export interface ProductionChoiceQuery {
  customerName?: string
  productModel?: string
  keyword?: string
  limit?: number
}
export interface ProductionLayerChoice {
  value: string
  label: string
  source: 'DICTIONARY' | 'HISTORY'
}
export const RdProductionChoiceApi = {
  furnaces: (params: ProductionChoiceQuery) =>
    request.get<string[]>({ url: '/rd/production-output/furnace-options', params }),
  layers: (params: ProductionChoiceQuery) =>
    request.get<ProductionLayerChoice[]>({ url: '/rd/production-output/layer-options', params })
}
