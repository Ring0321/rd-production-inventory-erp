import type { RdQualityBatchCreateReqVO } from '@/api/rd/quality/inspection'

export interface QualityPending {
  version: 1
  userId: number
  tenantId: string
  request: RdQualityBatchCreateReqVO
  inspectionId?: number
}

const validId = (value: unknown): value is number =>
  Number.isSafeInteger(value) && Number(value) > 0
const uniqueIds = (values: unknown): values is number[] =>
  Array.isArray(values) &&
  values.length > 0 &&
  values.every(validId) &&
  new Set(values).size === values.length
const finite = (value: unknown) => typeof value === 'number' && Number.isFinite(value)
const filled = (value: unknown) => typeof value === 'string' && value.trim().length > 0

export const validateQualityRequest = (request: RdQualityBatchCreateReqVO): string | undefined => {
  if (
    !request ||
    !validId(request.packageId) ||
    !uniqueIds(request.packageIds) ||
    request.packageIds.length > 200
  )
    return '检验范围须为 1 至 200 个不同包件'
  if (!request.packageIds.includes(request.packageId)) return '代表包须在本次检验范围内'
  if (
    !uniqueIds(request.samplePackageIds) ||
    request.samplePackageIds.some((id) => !request.packageIds.includes(id))
  )
    return '抽样包须为检验范围的非空子集'
  if (!filled(request.clientRequestId) || request.clientRequestId.length > 128)
    return '请求编号无效'
  if (!Array.isArray(request.items) || !request.items.length) return '须录入检测项目'
  for (const item of request.items) {
    if (
      !item ||
      !validId(item.samplePackageId) ||
      !request.samplePackageIds.includes(item.samplePackageId)
    )
      return '检测项目须关联本次实际抽样包'
    if (!filled(item.itemCode) || !filled(item.itemName)) return '检测项目编码和名称不能为空'
    if (!finite(item.measuredValue)) return '实测值须为有效数值'
    if (
      (item.standardMin !== undefined && !finite(item.standardMin)) ||
      (item.standardMax !== undefined && !finite(item.standardMax))
    )
      return '检测标准须为有效数值'
    if (
      item.standardMin !== undefined &&
      item.standardMax !== undefined &&
      item.standardMin > item.standardMax
    )
      return '检测标准下限不能高于上限'
  }
  if (
    request.samplePackageIds.some(
      (id) => !request.items.some((item) => item.samplePackageId === id)
    )
  )
    return '每个抽样包须至少有一项实测记录'
}

export const pendingKey = (userId: number, tenantId: string) => {
  if (!validId(userId) || !filled(tenantId)) throw new Error('登录身份未就绪')
  return `rd-quality-pending:${encodeURIComponent(tenantId)}:${userId}`
}

export const makePending = (
  request: RdQualityBatchCreateReqVO,
  userId: number,
  tenantId: string
): QualityPending => {
  pendingKey(userId, tenantId)
  const error = validateQualityRequest(request)
  if (error) throw new Error(error)
  return { version: 1, userId, tenantId, request: JSON.parse(JSON.stringify(request)) }
}

export const parsePending = (raw: string, userId: number, tenantId: string): QualityPending => {
  const value = JSON.parse(raw) as QualityPending
  if (
    !value ||
    value.version !== 1 ||
    value.userId !== userId ||
    value.tenantId !== tenantId ||
    validateQualityRequest(value.request) ||
    (value.inspectionId !== undefined && !validId(value.inspectionId))
  )
    throw new Error('待核请求不完整或不属于当前登录身份')
  return value
}

export const confirmedCreateRejection = (error: unknown, inspectionId?: number) => {
  const code =
    error && typeof error === 'object'
      ? (error as { businessCode?: number }).businessCode
      : undefined
  return (
    inspectionId === undefined &&
    typeof code === 'number' &&
    Number.isInteger(code) &&
    code > 0 &&
    ![401, 500, 901].includes(code)
  )
}
