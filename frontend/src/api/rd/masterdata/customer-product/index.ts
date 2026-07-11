import request from '@/config/axios'

export const RdCustomerProductApi = {
  getCustomerOptions: async (params?: { keyword?: string; limit?: number }) =>
    await request.get<string[]>({ url: '/rd/customer-product/customer-options', params }),

  getModelOptions: async (params?: {
    customerName?: string
    keyword?: string
    limit?: number
  }) => await request.get<string[]>({ url: '/rd/customer-product/model-options', params })
}
