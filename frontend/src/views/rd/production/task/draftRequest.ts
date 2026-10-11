import type { RdProductionTaskVO } from '@/api/rd/production/task'

export interface ProductionDraftFields {
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  outputQty?: number
  packageWeight?: number
  furnaceOutDate?: string
  remark?: string
}

export interface DraftOwner {
  operatorId: number
  tenantId: string
}

export interface ProductionDraftPayload extends ProductionDraftFields {
  productionTaskId: number
  productionDate: string
  customerName: string
  shiftCode: string
  outputQty: number
  packageWeight: number
  clientRequestId: string
  status: 'DRAFT'
}

export interface BoundProductionDraft extends DraftOwner {
  taskNo: string
  payload: Readonly<ProductionDraftPayload>
}

const payloadKeys = new Set([
  'productionTaskId',
  'productionDate',
  'customerName',
  'shiftCode',
  'productModel',
  'furnaceNo',
  'furnaceLayer',
  'outputQty',
  'packageWeight',
  'furnaceOutDate',
  'remark',
  'clientRequestId',
  'status'
])
const isText = (value: unknown): value is string =>
  typeof value === 'string' && value.trim().length > 0
const isDate = (value: unknown): value is string =>
  typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)

const validatePayload = (payload: ProductionDraftPayload) => {
  if (
    !payload ||
    Object.keys(payload).some((key) => !payloadKeys.has(key)) ||
    !Number.isSafeInteger(payload.productionTaskId) ||
    payload.productionTaskId <= 0 ||
    !isDate(payload.productionDate) ||
    !['customerName', 'shiftCode', 'productModel', 'furnaceNo', 'furnaceLayer'].every((key) =>
      isText(payload[key as keyof ProductionDraftPayload])
    ) ||
    !Number.isSafeInteger(payload.outputQty) ||
    payload.outputQty <= 0 ||
    !Number.isFinite(payload.packageWeight) ||
    payload.packageWeight < 0.000001 ||
    (payload.furnaceOutDate !== undefined && !isDate(payload.furnaceOutDate)) ||
    (payload.remark !== undefined && typeof payload.remark !== 'string') ||
    !isText(payload.clientRequestId) ||
    !/^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i.test(payload.clientRequestId) ||
    payload.status !== 'DRAFT'
  ) {
    throw new Error('待提交内容不完整，包数须为正整数，单包重量须为实际正数。')
  }
}

const freezeDraft = (draft: BoundProductionDraft): Readonly<BoundProductionDraft> => {
  validatePayload(draft.payload)
  if (
    !Number.isSafeInteger(draft.operatorId) ||
    draft.operatorId <= 0 ||
    typeof draft.tenantId !== 'string' ||
    !isText(draft.taskNo)
  ) {
    throw new Error('无法绑定当前录入身份或任务。')
  }
  return Object.freeze({
    ...draft,
    payload: Object.freeze({
      ...draft.payload,
      furnaceOutDate: draft.payload.furnaceOutDate,
      remark: draft.payload.remark
    })
  })
}

export const bindProductionDraft = (
  task: Pick<
    RdProductionTaskVO,
    'id' | 'taskNo' | 'customerName' | 'productionDate' | 'shiftCode' | 'status'
  >,
  fields: ProductionDraftFields,
  owner: DraftOwner,
  requestId: string = crypto.randomUUID()
): Readonly<BoundProductionDraft> => {
  if (task.status !== 'ACTIVE') throw new Error('该任务已停止生产登记。')
  return freezeDraft({
    ...owner,
    taskNo: task.taskNo,
    payload: {
      productionTaskId: task.id,
      productionDate: task.productionDate,
      customerName: task.customerName,
      shiftCode: task.shiftCode,
      productModel: fields.productModel.trim(),
      furnaceNo: fields.furnaceNo.trim(),
      furnaceLayer: fields.furnaceLayer.trim(),
      outputQty: fields.outputQty as number,
      packageWeight: fields.packageWeight as number,
      furnaceOutDate: fields.furnaceOutDate || undefined,
      remark: fields.remark?.trim() || undefined,
      clientRequestId: requestId,
      status: 'DRAFT'
    }
  })
}

export const restoreProductionDraft = (
  serialized: string,
  owner: DraftOwner
): Readonly<BoundProductionDraft> => {
  const draft: BoundProductionDraft = JSON.parse(serialized)
  if (
    !draft ||
    draft.operatorId !== owner.operatorId ||
    draft.tenantId !== owner.tenantId ||
    typeof draft.tenantId !== 'string'
  ) {
    throw new Error('待重试记录与当前录入身份不一致。')
  }
  return freezeDraft(draft)
}
