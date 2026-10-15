import type {
  RdPackageUnitVO,
  RdPackageTraceVO,
  RdPackageWorkContextVO
} from '@/api/rd/stock/package'
import type { RdProductionTaskVO } from '@/api/rd/production/task'

export const DEMO_MARKER = 'DUOXU-DEMO-FLOW-V1'
export const DEMO_CUSTOMER = '演示客户·宁川新材'
export const canViewBusinessFlow = (roles: readonly string[], permissions: ReadonlySet<string>) =>
  !roles.includes('rd_forklift_driver') &&
  ['rd:production-task:query', 'rd:package-unit:query'].every(
    (key) => permissions.has(key) || permissions.has('*:*:*')
  )

export const isDemoTask = (task: RdProductionTaskVO) =>
  task.customerName.startsWith('演示客户') && task.remark?.includes(DEMO_MARKER) === true

export const flowStates = [
  { key: 'PREPARING', name: '待贴码', statuses: ['CREATED'], tone: 'info' },
  { key: 'QUALITY', name: '待质检', statuses: ['LABELED', 'QC_PENDING'], tone: 'warning' },
  { key: 'REJECTED', name: '检验不合格', statuses: ['QC_REJECTED'], tone: 'danger' },
  { key: 'RELEASED', name: '合格待派', statuses: ['QC_PASSED'], tone: 'success' },
  { key: 'PUTAWAY', name: '上架中', statuses: ['INBOUND_PENDING'], tone: 'warning' },
  { key: 'STOCK', name: '在库', statuses: ['IN_STOCK'], tone: 'success' },
  { key: 'LOADING', name: '待出库', statuses: ['OUTBOUND_PENDING'], tone: 'warning' },
  { key: 'LOADING_EXCEPTION', name: '装车异常', statuses: [], tone: 'danger' },
  { key: 'OUTBOUND', name: '已出库', statuses: ['OUTBOUNDED'], tone: 'info' },
  { key: 'MISSING', name: '盘点待处理', statuses: ['STOCKTAKE_MISSING'], tone: 'danger' },
  { key: 'VOIDED', name: '已作废', statuses: ['VOIDED'], tone: 'info' },
  { key: 'UNKNOWN', name: '状态待核', statuses: [], tone: 'danger' }
] as const

export const packageState = (status: string, context?: RdPackageWorkContextVO) => {
  const loading = context?.tasks.filter((task) => task.type === 'LOADING') || []
  if (['IN_STOCK', 'OUTBOUND_PENDING'].includes(status) && loading.length) {
    if (loading.some((task) => task.status === 'EXCEPTION_PENDING'))
      return flowStates.find((state) => state.key === 'LOADING_EXCEPTION')!
    if (loading.every((task) => ['ASSIGNED', 'LOADING', 'PENDING_REVIEW'].includes(task.status)))
      return flowStates.find((state) => state.key === 'LOADING')!
    return flowStates[flowStates.length - 1]
  }
  return (
    flowStates.find((state) => (state.statuses as readonly string[]).includes(status)) ||
    flowStates[flowStates.length - 1]
  )
}

export const flowSteps = [
  {
    key: 'PRODUCTION',
    title: '生产任务',
    role: '生产班长 / 生产岗位',
    icon: 'lucide:clipboard-list',
    work: '按客户下达班次任务，登记型号、炉号、炉层、产量和单包净重。',
    result: '生产任务、生产记录、包件来源',
    states: [],
    permission: 'rd:production-task:query',
    path: '/rd/production/output'
  },
  {
    key: 'LABEL',
    title: '包装贴码',
    role: '生产岗位',
    icon: 'lucide:scan-line',
    work: '生产记录确认后生成独立包码，将货品标识卡随装包工序固定在吨袋上。',
    result: '一包一码、登记净重、打印记录',
    states: ['PREPARING'],
    permission: 'rd:package-unit:query',
    path: '/rd/stock/package'
  },
  {
    key: 'QUALITY',
    title: '批次抽检',
    role: '质检员 / 质检复核',
    icon: 'lucide:clipboard-check',
    work: '明确本次覆盖包件和实际样包，登记检验值，复核后放行或转不合格处理。',
    result: '检验单、覆盖范围、样包与结论',
    states: ['QUALITY', 'REJECTED'],
    permission: 'rd:quality-inspection:query',
    path: '/rd/quality/inspection'
  },
  {
    key: 'PUTAWAY',
    title: '入库上架',
    role: '仓管员 → 叉车司机 → 仓管员',
    icon: 'lucide:package-plus',
    work: '仓管选库位、派单并核对批次成员；司机按单摆放，仓管确认实际位置和入库。',
    result: '上架任务、最终库位、入库流水',
    states: ['RELEASED', 'PUTAWAY'],
    permission: 'rd:putaway-task:query',
    path: '/rd/stock/putaway'
  },
  {
    key: 'STOCK',
    title: '库位库存',
    role: '仓管员 / 物资部',
    icon: 'lucide:warehouse',
    work: '维护实际库位、核对货物和占用，库位变更由仓管办理并同步库存台账。',
    result: '包件位置、可用与占用、库存流水',
    states: ['STOCK', 'MISSING'],
    permission: 'rd:putaway-task:query',
    path: '/warehouse-space'
  },
  {
    key: 'LOADING',
    title: '提货装车',
    role: '商务 → 仓管员 → 叉车司机',
    icon: 'lucide:truck',
    work: '商务下提货单，仓管锁定具体包件并派整车任务；司机按货位和货物资料装车。',
    result: '销售订单、提货单、整车任务',
    states: ['LOADING', 'LOADING_EXCEPTION'],
    permission: 'rd:loading-task:query',
    path: '/rd/stock/loading'
  },
  {
    key: 'OUTBOUND',
    title: '出库复核',
    role: '仓管员 / 商务核定异常',
    icon: 'lucide:package-check',
    work: '仓管核对实际装车包件和重量后确认出库；少装、错货等先核定处理再办理。',
    result: '出库明细、扣库流水、库位释放',
    states: ['OUTBOUND'],
    permission: 'rd:outbound-order:query',
    path: '/rd/stock/outbound'
  },
  {
    key: 'TRACE',
    title: '数字追溯',
    role: '授权岗位 / 外部只读视图',
    icon: 'lucide:workflow',
    work: '每包保留独立身份，按实际记录关联生产、检验、库位、装车与出库证据。',
    result: '流转时间线、真实关联图、同码查询',
    states: [],
    permission: 'rd:package-unit:query',
    path: ''
  }
] as const

export const validateTaskPackages = (taskId: number, data: unknown): RdPackageUnitVO[] => {
  if (!Number.isSafeInteger(taskId) || taskId <= 0 || !Array.isArray(data) || data.length > 10000)
    throw new Error('任务包件范围待核，未显示部分统计')
  const ids = new Set<number>()
  const codes = new Set<string>()
  for (const row of data) {
    if (
      !row ||
      row.productionTaskId !== taskId ||
      !Number.isSafeInteger(row.id) ||
      row.id <= 0 ||
      typeof row.packageCode !== 'string' ||
      !row.packageCode ||
      row.packageCode.length > 128 ||
      ids.has(row.id) ||
      codes.has(row.packageCode) ||
      !Number.isSafeInteger(row.productionOutputId) ||
      row.productionOutputId <= 0 ||
      typeof row.status !== 'string'
    )
      throw new Error('任务包件身份或成员不一致，请刷新核对')
    ids.add(row.id)
    codes.add(row.packageCode)
  }
  return data
}

export const sumPackages = (rows: readonly RdPackageUnitVO[]) => {
  let micros = 0n
  let unknownWeight = 0
  for (const row of rows) {
    const text = String(row.packageWeight ?? '')
    if (!/^\d{1,9}(\.\d{1,6})?$/.test(text) || Number(text) <= 0) {
      unknownWeight++
      continue
    }
    const [whole, decimal = ''] = text.split('.')
    micros += BigInt(whole) * 1000000n + BigInt(decimal.padEnd(6, '0'))
  }
  const safe = micros <= BigInt(Number.MAX_SAFE_INTEGER)
  return {
    count: rows.length,
    weightKg: safe ? Number(micros) / 1000000 : undefined,
    unknownWeight: safe ? unknownWeight : rows.length
  }
}

export const summarizeFlow = (
  rows: readonly RdPackageUnitVO[],
  contexts: Record<string, RdPackageWorkContextVO> = {}
) => ({
  ...sumPackages(rows),
  outputCount: new Set(rows.map((row) => row.productionOutputId)).size,
  locationCount: new Set(rows.filter((row) => row.locationId).map((row) => row.locationId)).size,
  workKnown: rows.every((row) => contexts[row.packageCode] !== undefined),
  states: flowStates.map((state) => ({
    ...state,
    ...sumPackages(
      rows.filter((row) => packageState(row.status, contexts[row.packageCode]).key === state.key)
    )
  }))
})

export const weightText = (value: unknown) =>
  typeof value !== 'number' || !Number.isFinite(value) || value < 0
    ? '待核'
    : value.toLocaleString('zh-CN', { maximumFractionDigits: 6 })

export const packageWeightText = (value: unknown) =>
  typeof value !== 'number' || value <= 0 ? '待核' : weightText(value)

export const summaryWeightText = (summary: ReturnType<typeof sumPackages>) =>
  summary.count > 0 && summary.unknownWeight === summary.count
    ? '净重待核'
    : `${summary.unknownWeight ? '已知 ' : ''}${weightText(summary.weightKg)} kg${
        summary.unknownWeight ? `（${summary.unknownWeight} 包待核）` : ''
      }`

export const validTaskId = (value: unknown) => {
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : undefined
}

export const packageLocation = (bag: RdPackageUnitVO) => {
  const historical = ['OUTBOUNDED', 'VOIDED'].includes(bag.status)
  const title = bag.status === 'OUTBOUNDED' ? '出库前库位' : historical ? '历史库位' : '登记库位'
  const code = bag.locationCode || (bag.locationId ? `库位 #${bag.locationId}` : '未登记')
  return { title, code, caption: bag.locationId && historical ? `${code}（${title}）` : code }
}

export const businessFlowTarget = (
  stage: string,
  taskId: number,
  bag?: RdPackageUnitVO,
  trace?: RdPackageTraceVO,
  context?: RdPackageWorkContextVO
) => {
  const names: Record<string, string> = {
    PRODUCTION: 'RdProductionTask',
    LABEL: 'RdPackageUnit',
    QUALITY: 'RdQualityInspection',
    PUTAWAY: 'RdPutawayWorkbench',
    STOCK: 'RdWarehouseSpace',
    LOADING: 'RdLoadingDispatch',
    OUTBOUND: 'RdOutboundOrder'
  }
  if (!names[stage] || !Number.isSafeInteger(taskId) || taskId <= 0) return undefined
  const query: Record<string, string> = {}
  if (stage === 'LABEL') query.productionTaskId = String(taskId)
  if (stage === 'PRODUCTION') query.productionTaskId = String(taskId)
  if (stage === 'STOCK' && bag?.locationId) query.locationId = String(bag.locationId)
  if (stage === 'QUALITY' && trace?.qualityInspectionId)
    query.inspectionId = String(trace.qualityInspectionId)
  if (['PUTAWAY', 'LOADING'].includes(stage)) {
    const actual = context?.tasks.find(
      (task) => task.type === stage && Number.isSafeInteger(task.taskId) && task.taskId > 0
    )
    if (actual) {
      query.taskId = String(actual.taskId)
      query.mode = 'view'
    }
  }
  return { name: names[stage], query }
}

export const qualityConclusionText = (trace?: RdPackageTraceVO) => {
  const value = trace?.qualityConclusion || trace?.qualityStatus
  const labels: Record<string, string> = {
    PASSED: '质检通过',
    REJECTED: '质检不合格',
    SUBMITTED: '已提交，待复核',
    DRAFT: '检验草稿',
    PENDING: '待质检'
  }
  return value ? labels[value] || value : '未取得结论'
}
