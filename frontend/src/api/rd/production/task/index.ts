import request from '@/config/axios'

export type RdProductionTaskStatus = 'ACTIVE' | 'CLOSED' | 'CANCELLED'

export interface RdProductionTaskVO {
  id: number
  taskNo: string
  productionDate: string
  customerName: string
  shiftCode: string
  status: RdProductionTaskStatus
  version: number
  managerId?: number
  managerName?: string
  closedTime?: string
  cancelledTime?: string
  remark?: string
  createTime?: string
}

export interface RdProductionTaskSaveReqVO {
  customerName: string
  productionDate: string
  shiftCode: string
  remark?: string
}

export interface RdProductionTaskActionReqVO {
  id: number
  version: number
}

export interface RdProductionTaskPageReqVO extends PageParam {
  taskNo?: string
  customerName?: string
  status?: RdProductionTaskStatus
}

export const RdProductionTaskApi = {
  getPage: (params: RdProductionTaskPageReqVO) =>
    request.get<PageResult<RdProductionTaskVO[]>>({ url: '/rd/production-task/page', params }),
  get: (id: number) =>
    request.get<RdProductionTaskVO>({ url: '/rd/production-task/get', params: { id } }),
  create: (data: RdProductionTaskSaveReqVO) =>
    request.post<number>({ url: '/rd/production-task/create', data }),
  update: (data: RdProductionTaskSaveReqVO & RdProductionTaskActionReqVO) =>
    request.put<boolean>({ url: '/rd/production-task/update', data }),
  close: (data: RdProductionTaskActionReqVO) =>
    request.put<boolean>({ url: '/rd/production-task/close', data }),
  cancel: (data: RdProductionTaskActionReqVO) =>
    request.put<boolean>({ url: '/rd/production-task/cancel', data })
}
