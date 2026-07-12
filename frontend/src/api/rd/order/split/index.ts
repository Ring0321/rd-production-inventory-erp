import request from '@/config/axios'

export type OrderSplitStatus = 'DRAFT' | 'CONFIRMED' | 'CANCELLED'
export type AllocationType = 'FIXED_QTY' | 'RATIO'
export type OutputBindStatus = 'ACTIVE' | 'CANCELLED'

export interface RdOrderSplitRuleLineVO {
  id?: number
  ruleId?: number
  sequence: number
  furnaceLayer: string
  allocationType: AllocationType
  allocationValue: number
  remark?: string
}

export interface RdOrderSplitRuleVO {
  id: number
  ruleCode: string
  ruleName: string
  customerName: string
  productModel: string
  defaultFurnaceLoadQty: number
  yieldRate: number
  enabled: boolean
  version: number
  remark?: string
  lines: RdOrderSplitRuleLineVO[]
  createTime?: string
}

export interface RdOrderSplitResultLineVO {
  id: number
  resultId: number
  sequence: number
  furnaceLayer: string
  allocationType: AllocationType
  allocationValue: number
  calculatedBaseQty: number
  manualAdjustmentQty: number
  finalPlannedQty: number
  actualBoundQty: number
  adjustmentReason?: string
}

export interface RdOrderSplitOutputBindVO {
  id: number
  resultId: number
  resultLineId: number
  productionOutputId: number
  productionOutputNo?: string
  productionDate?: string
  furnaceNo?: string
  furnaceLayer?: string
  outputQty: number
  status: OutputBindStatus
  bindBy?: string
  bindTime?: string
  unbindBy?: string
  unbindTime?: string
  unbindReason?: string
}

export interface RdOrderSplitResultVO {
  id: number
  splitNo: string
  splitDate: string
  saleOrderId: number
  saleOrderNo: string
  ruleId: number
  ruleName: string
  customerName: string
  productModel: string
  furnaceLoadQty: number
  yieldRate: number
  expectedOutputQty: number
  finalPlannedOutputQty: number
  actualBoundOutputQty: number
  varianceQty: number
  status: OrderSplitStatus
  version: number
  lines?: RdOrderSplitResultLineVO[]
  outputBindings?: RdOrderSplitOutputBindVO[]
  confirmBy?: string
  confirmTime?: string
  cancelBy?: string
  cancelTime?: string
  cancelReason?: string
  createBy?: string
  createTime?: string
}

export interface RdOrderSplitProductionOptionVO {
  id: number
  productionDate: string
  customerName: string
  productModel: string
  furnaceNo: string
  furnaceLayer: string
  outputQty: number
  status: string
}

export interface RdOrderSplitCreateReqVO {
  splitDate: string
  saleOrderId: number
  ruleId: number
  furnaceLoadQty: number
  yieldRate: number
}

export interface RdOrderSplitAdjustReqVO {
  id: number
  version: number
  lines: Array<{
    lineId: number
    manualAdjustmentQty: number
    adjustmentReason?: string
  }>
}

export interface RdOrderSplitActionReqVO {
  id: number
  version: number
  reason?: string
}

export interface RdOrderSplitBindReqVO {
  id: number
  version: number
  resultLineId: number
  productionOutputId: number
}

export interface RdOrderSplitUnbindReqVO {
  id: number
  version: number
  productionOutputId: number
  reason: string
}

const normalizeRule = (source: any): RdOrderSplitRuleVO => {
  const rule = source.rule || source
  return {
    ...rule,
    yieldRate: rule.yieldRate ?? rule.yieldRatePercent,
    lines: (source.lines || rule.lines || []).map((line: any) => ({
      ...line,
      sequence: line.sequence ?? line.sequenceNo
    }))
  }
}

const normalizeResult = (source: any): RdOrderSplitResultVO => {
  const result = source.result || source
  const lines = (source.lines || result.lines || []).map((line: any) => ({
    ...line,
    sequence: line.sequence ?? line.sequenceNo,
    allocationType: line.allocationType ?? line.allocationTypeSnapshot,
    allocationValue: line.allocationValue ?? line.allocationValueSnapshot
  }))
  const outputBindings = (source.bindings || result.outputBindings || []).map((binding: any) => ({
    ...binding,
    furnaceLayer:
      binding.furnaceLayer ??
      lines.find((line: RdOrderSplitResultLineVO) => line.id === binding.resultLineId)
        ?.furnaceLayer,
    outputQty: binding.outputQty ?? binding.outputQtySnapshot,
    bindBy: binding.bindBy ?? binding.binderName,
    unbindBy: binding.unbindBy ?? binding.unbinderName
  }))
  return {
    ...result,
    ruleName: result.ruleName ?? result.ruleNameSnapshot,
    customerName: result.customerName ?? result.customerNameSnapshot,
    productModel: result.productModel ?? result.productModelSnapshot,
    yieldRate: result.yieldRate ?? result.yieldRatePercent,
    actualBoundOutputQty:
      result.actualBoundOutputQty ??
      (lines.length > 0
        ? lines.reduce(
            (sum: number, line: RdOrderSplitResultLineVO) => sum + Number(line.actualBoundQty || 0),
            0
          )
        : undefined),
    confirmBy: result.confirmBy ?? result.confirmerName,
    cancelBy: result.cancelBy ?? result.cancellerName,
    lines,
    outputBindings
  }
}

const toRuleRequest = (data: Partial<RdOrderSplitRuleVO>) => ({
  id: data.id,
  version: data.version,
  ruleCode: data.ruleCode,
  ruleName: data.ruleName,
  customerName: data.customerName,
  productModel: data.productModel,
  defaultFurnaceLoadQty: data.defaultFurnaceLoadQty,
  yieldRatePercent: data.yieldRate,
  enabled: data.enabled,
  remark: data.remark,
  lines: data.lines?.map((line) => ({
    sequenceNo: line.sequence,
    furnaceLayer: line.furnaceLayer,
    allocationType: line.allocationType,
    allocationValue: line.allocationValue,
    remark: line.remark
  }))
})

export const RdOrderSplitApi = {
  getRulePage: async (params: any) => {
    const data = await request.get({ url: '/rd/order-split/rule/page', params })
    return { ...data, list: data.list.map(normalizeRule) }
  },
  getRule: async (id: number) => {
    const data = await request.get({ url: '/rd/order-split/rule/get', params: { id } })
    return normalizeRule(data)
  },
  createRule: async (data: Partial<RdOrderSplitRuleVO>) =>
    await request.post({ url: '/rd/order-split/rule/create', data: toRuleRequest(data) }),
  updateRule: async (data: Partial<RdOrderSplitRuleVO>) =>
    await request.put({ url: '/rd/order-split/rule/update', data: toRuleRequest(data) }),
  getPage: async (params: any) => {
    const data = await request.get({ url: '/rd/order-split/page', params })
    return { ...data, list: data.list.map(normalizeResult) }
  },
  getDetail: async (id: number) => {
    const data = await request.get({ url: '/rd/order-split/detail', params: { id } })
    return normalizeResult(data)
  },
  create: async (data: RdOrderSplitCreateReqVO) =>
    await request.post({
      url: '/rd/order-split/create',
      data: { ...data, yieldRatePercent: data.yieldRate, yieldRate: undefined }
    }),
  adjust: async (data: RdOrderSplitAdjustReqVO) =>
    await request.put({ url: '/rd/order-split/adjust', data }),
  confirm: async (data: RdOrderSplitActionReqVO) =>
    await request.post({ url: '/rd/order-split/confirm', data }),
  cancel: async (data: RdOrderSplitActionReqVO) =>
    await request.post({ url: '/rd/order-split/cancel', data }),
  getProductionOptions: async (resultLineId: number) =>
    await request.get({ url: '/rd/order-split/production-options', params: { resultLineId } }),
  bindOutput: async (data: RdOrderSplitBindReqVO) =>
    await request.post({ url: '/rd/order-split/bind-output', data }),
  unbindOutput: async (data: RdOrderSplitUnbindReqVO) =>
    await request.post({ url: '/rd/order-split/unbind-output', data })
}
