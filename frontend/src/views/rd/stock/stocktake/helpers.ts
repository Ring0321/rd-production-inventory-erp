import type {
  RdStocktakeDetailVO,
  RdStocktakeLineVO,
  RdStocktakeOrderVO,
  RdStocktakeStatus
} from '@/api/rd/stock/stocktake'

export type StocktakeTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

export const stocktakeStatusOptions: Array<{
  value: RdStocktakeStatus
  label: string
  type: StocktakeTagType
}> = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'FROZEN', label: '已冻结', type: 'warning' },
  { value: 'SCANNING', label: '扫码中', type: 'primary' },
  { value: 'COUNTING', label: '实盘中', type: 'primary' },
  { value: 'SUBMITTED', label: '待复核', type: 'warning' },
  { value: 'APPROVED', label: '已复核', type: 'success' },
  { value: 'REVIEWED', label: '已复核', type: 'success' },
  { value: 'ADJUSTED', label: '已调整', type: 'success' },
  { value: 'CLOSED', label: '已关闭', type: 'info' },
  { value: 'CANCELLED', label: '已取消', type: 'danger' },
  { value: 'CANCELED', label: '已取消', type: 'danger' }
]

export const stocktakeStatusMap = Object.fromEntries(
  stocktakeStatusOptions.map((item) => [item.value, item])
) as Record<string, (typeof stocktakeStatusOptions)[number]>

export const stocktakeResultMap: Record<
  string,
  { label: string; type: StocktakeTagType; tone: 'match' | 'shortage' | 'surplus' | 'pending' }
> = {
  MATCH: { label: '一致', type: 'success', tone: 'match' },
  SHORTAGE: { label: '短少', type: 'danger', tone: 'shortage' },
  SURPLUS: { label: '盘盈', type: 'warning', tone: 'surplus' },
  UNSCANNED: { label: '未盘', type: 'info', tone: 'pending' },
  PENDING: { label: '待盘', type: 'info', tone: 'pending' }
}

export const formatMetric = (value: unknown, digits = 0) => {
  const number = Number(value ?? 0)
  if (!Number.isFinite(number)) return digits === 0 ? '0' : (0).toFixed(digits)
  if (digits === 0 && Number.isInteger(number)) return `${number}`
  return number.toFixed(digits)
}

export const emptyStocktakeOrder = (): RdStocktakeOrderVO => ({
  id: 0,
  stocktakeNo: '',
  stocktakeDate: '',
  locationId: 0,
  locationCode: '',
  status: 'DRAFT',
  version: 0,
  snapshotTime: '',
  bookPackageQty: 0,
  countedPackageQty: 0,
  shortageQty: 0,
  surplusQty: 0,
  bookWeight: 0,
  countedWeight: 0,
  remark: ''
})

export const normalizeStocktakeDetail = (
  value: RdStocktakeDetailVO | RdStocktakeLineVO[] | undefined,
  fallbackOrder?: Partial<RdStocktakeOrderVO>
): RdStocktakeDetailVO => {
  if (Array.isArray(value)) {
    return {
      order: { ...emptyStocktakeOrder(), ...fallbackOrder },
      lines: value
    }
  }
  return {
    order: { ...emptyStocktakeOrder(), ...(value?.order || {}), ...fallbackOrder },
    lines: value?.lines || []
  }
}

export const isShortageLine = (line: Pick<RdStocktakeLineVO, 'result' | 'differenceQty'>) =>
  line.result === 'SHORTAGE' || Number(line.differenceQty || 0) < 0

export const isSurplusLine = (line: Pick<RdStocktakeLineVO, 'result' | 'differenceQty'>) =>
  line.result === 'SURPLUS' || Number(line.differenceQty || 0) > 0

export const getDifferenceClass = (line: Pick<RdStocktakeLineVO, 'result' | 'differenceQty'>) => {
  if (isShortageLine(line)) return 'difference-shortage'
  if (isSurplusLine(line)) return 'difference-surplus'
  return 'difference-match'
}

const matches = (status: string, candidates: string[]) => candidates.includes(status)

export const canFreeze = (status: string) => matches(status, ['DRAFT'])
export const canScan = (status: string) => matches(status, ['FROZEN', 'SCANNING', 'COUNTING'])
export const canSubmit = (status: string) => matches(status, ['FROZEN', 'SCANNING', 'COUNTING'])
export const canApprove = (status: string) => matches(status, ['SUBMITTED'])
export const canAdjust = (status: string) => matches(status, ['APPROVED', 'REVIEWED'])
export const canClose = (status: string) => matches(status, ['ADJUSTED'])
export const canCancel = (status: string) =>
  !matches(status, ['ADJUSTED', 'CLOSED', 'CANCELLED', 'CANCELED'])
