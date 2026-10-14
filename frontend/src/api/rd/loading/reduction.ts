import request from '@/config/axios'
import type { LoadingTaskLine } from './index'

export type LoadingReductionStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
export interface LoadingReduction {
  id: number
  taskId: number
  pickupSlipId: number
  taskNo: string
  pickupSlipNo: string
  customerName: string
  status: LoadingReductionStatus
  effectiveStatus: LoadingReductionStatus | 'STALE'
  taskVersion: number
  planVersion: number
  slipVersion: number
  version: number
  idempotencyKey: string
  originalPackageCount: number
  reducedPackageCount: number
  remainingPackageCount: number
  originalWeight: number | string
  reducedWeight: number | string
  remainingWeight: number | string
  reason: string
  proposerId: number
  proposerName: string
  proposedTime: string | number
  reviewerId?: number
  reviewerName?: string
  reviewTime?: string | number
  reviewRemark?: string
  lines: Array<
    Omit<LoadingTaskLine, 'packageWeight'> & { packageWeight: number | string; removed: boolean }
  >
}

export type LoadingReductionQuery =
  | { taskId: number; pickupSlipId?: never }
  | { pickupSlipId: number; taskId?: never }

export interface LoadingReductionCreate {
  taskId: number
  taskVersion: number
  taskLineIds: number[]
  reason: string
  idempotencyKey: string
}

export interface LoadingReductionReview {
  id: number
  version: number
  decision: 'APPROVED' | 'REJECTED'
  reviewRemark: string
}

export const RdLoadingReductionApi = {
  get: (params: { id: number } | { taskId: number; idempotencyKey: string }) =>
    request.get<LoadingReduction | null>({ url: '/rd/loading-task/reduction/get', params }),
  list: (params: LoadingReductionQuery) =>
    request.get<LoadingReduction[]>({ url: '/rd/loading-task/reduction/list', params }),
  create: (data: LoadingReductionCreate) =>
    request.post<LoadingReduction>({ url: '/rd/loading-task/reduction/create', data }),
  review: (data: LoadingReductionReview) =>
    request.put<LoadingReduction>({ url: '/rd/loading-task/reduction/review', data })
}
