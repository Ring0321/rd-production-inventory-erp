import request from '@/config/axios'
import { RdLoadingApi } from '@/api/rd/loading'

export interface WeightSuggestionRequest {
  pickupSlipId: number
  pickupSlipLineId: number
  targetWeightKg: number
  toleranceKg: number
}

export interface WeightBag {
  packageId: number
  packageCode: string
  packageWeightKg: number
  locationId: number
  locationCode: string
  stockLotId: number
}

export interface WeightSuggestion extends WeightSuggestionRequest {
  slipNo: string
  customerName: string
  productModel: string
  authorizedWeightKg: number
  authorizedPackageCount: number | null
  solverStatus: string
  message: string
  withinTolerance: boolean
  totalWeightKg: number | null
  deviationKg: number | null
  shortfallKg: number | null
  availableWeightUpperBoundKg: number | null
  objectiveBound: number | null
  candidateCount: number | null
  scannedCount: number
  candidateScanComplete: boolean
  candidateLimit: number
  solverTimeLimitMs: number
  elapsedMs: number
  factsAt: string
  weightScale: number
  bags: WeightBag[]
}

export const RdWeightApi = {
  page: RdLoadingApi.getPickupSlipPage,
  detail: RdLoadingApi.getPickupSlip,
  tasks: RdLoadingApi.getTaskPage,
  suggest: (data: WeightSuggestionRequest) =>
    request.post<WeightSuggestion>({ url: '/rd/weight-assistant/suggest', data })
}
