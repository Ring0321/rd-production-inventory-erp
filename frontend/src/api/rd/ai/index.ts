import request from '@/config/axios'

export type PackageAiQuestion =
  | 'STATUS'
  | 'SOURCE_PROGRESS'
  | 'NEXT_STEP'
  | 'RISK_REVIEW'
  | 'HANDOVER'
export type PackageAiProviderState =
  | 'DISABLED'
  | 'UNCONFIGURED'
  | 'COST_NOT_APPROVED'
  | 'LIMIT_NOT_SET'
  | 'BUDGET_NOT_SET'
  | 'PRICE_REVIEW_REQUIRED'
  | 'READY'

export interface PackageAiContext {
  packageCode: string
  status: string
  qualityStage: string
  source: {
    state: 'KNOWN' | 'UNKNOWN'
    packageCount: number | null
    recordedWeight: number | null
    counts: Record<string, number>
  }
  factsAt: string
  providerState: PackageAiProviderState
  provider?: {
    name: 'DeepSeek'
    model: string
    monthlyBudgetCny: number
    reservationPerCallCny: number
    priceValidUntil: string
  } | null
  notices: Array<{ key: string; text: string }>
  diagnosis?: PackageAiDiagnosis
}

export interface PackageAiDiagnosis {
  scope: 'PRODUCTION_OUTPUT'
  state: 'KNOWN' | 'UNKNOWN'
  inspectedPackages: number | null
  findings: Array<{
    key: string
    severity: 'HIGH' | 'REVIEW' | 'INFO'
    owner: 'PRODUCTION' | 'QUALITY' | 'WAREHOUSE'
    title: string
    detail: string
    affectedCount: number | null
    packageCodes: string[]
    evidence: string
  }>
}

export interface PackageAiAskRequest {
  packageCode: string
  question: PackageAiQuestion
  requestId: string
}

export interface PackageAiAnswer {
  requestId: string
  state: string
  message: string
  context: PackageAiContext
  noteKeys: string[]
  totalTokens: number | null
  answeredAt: string | null
}

export const RdPackageAiApi = {
  getContext: (packageCode: string) =>
    request.get<PackageAiContext>({ url: '/rd/ai/package/context', params: { packageCode } }),
  ask: (data: PackageAiAskRequest) =>
    request.post<PackageAiAnswer>({ url: '/rd/ai/package/ask', data }),
  getResult: (params: PackageAiAskRequest) =>
    request.get<PackageAiAnswer>({ url: '/rd/ai/package/result', params })
}
