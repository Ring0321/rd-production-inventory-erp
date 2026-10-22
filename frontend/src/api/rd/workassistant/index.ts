import request from '@/config/axios'

export const RdWorkQueryApi = {
  query: (queryType: 'Q03' | 'Q04' | 'Q05'): Promise<unknown> =>
    request.get({ url: '/rd/work-assistant/query', params: { queryType, pageNo: 1, pageSize: 20 } })
}
