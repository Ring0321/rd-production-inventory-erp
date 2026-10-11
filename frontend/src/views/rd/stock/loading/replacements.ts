import type { LoadingPackage, LoadingReplacement, LoadingTaskLine } from '@/api/rd/loading'
import { extractPackageCode } from '@/utils/rdDigitalLink'

export interface LoadingReplacementRow {
  key: number
  taskLineId?: number
  newPackageCode: string
}

export interface LoadingReplacementPreview {
  original: LoadingTaskLine
  replacement: LoadingPackage
}

const codeKey = (value: string) => value.trim().toUpperCase()

export const buildLoadingReplacements = (
  rows: LoadingReplacementRow[],
  lines: LoadingTaskLine[]
): LoadingReplacement[] => {
  if (!rows.length || rows.length > 100) throw new Error('每次换货须选择 1 至 100 包')
  const ids = new Set<number>()
  const codes = new Set<string>()
  return rows.map((row, index) => {
    const line = lines.find((item) => item.id === row.taskLineId)
    if (
      !line ||
      !Number.isSafeInteger(line.id) ||
      line.id <= 0 ||
      !['PLANNED', 'EXCEPTION'].includes(line.lineStatus)
    )
      throw new Error(`第 ${index + 1} 行原包已核验或不可替换，请重新核定`)
    if (ids.has(line.id)) throw new Error('原任务包不能重复选择')
    ids.add(line.id)
    const code = extractPackageCode(row.newPackageCode)
    if (!code || code.length > 128 || /[\s\\/?#:\u0000-\u001f\u007f]/u.test(code))
      throw new Error(`第 ${index + 1} 行请填写有效替换包号或二维码`)
    if (codes.has(codeKey(code))) throw new Error('替换包不能重复使用')
    codes.add(codeKey(code))
    if (lines.some((item) => codeKey(item.packageCode) === codeKey(code)))
      throw new Error('替换包已在当前装车任务中，请重新选择')
    return { taskLineId: line.id, newPackageCode: code }
  })
}

export const inspectLoadingReplacements = async (
  entries: LoadingReplacement[],
  lines: LoadingTaskLine[],
  lookup: (params: {
    pageNo: number
    pageSize: number
    customerName?: string
    productModel: string
    furnaceNo: string
    furnaceLayer: string
    keyword: string
    packageCode: string
  }) => Promise<{ list: LoadingPackage[] }>,
  assertCurrent: () => void
): Promise<LoadingReplacementPreview[]> => {
  const result: LoadingReplacementPreview[] = []
  for (const entry of entries) {
    assertCurrent()
    const original = lines.find((item) => item.id === entry.taskLineId)!
    const page = await lookup({
      pageNo: 1,
      pageSize: 100,
      customerName: original.customerName,
      productModel: original.productModel,
      furnaceNo: original.furnaceNo,
      furnaceLayer: original.furnaceLayer,
      keyword: entry.newPackageCode,
      packageCode: entry.newPackageCode
    })
    assertCurrent()
    const matches = page?.list?.filter(
      (item) => codeKey(item.packageCode) === codeKey(entry.newPackageCode)
    )
    const replacement = matches?.length === 1 ? matches[0] : undefined
    if (
      !replacement ||
      replacement.status !== 'IN_STOCK' ||
      !replacement.locationId ||
      !replacement.locationCode ||
      !replacement.stockLotId ||
      !Number.isFinite(replacement.packageWeight) ||
      replacement.packageWeight <= 0 ||
      replacement.customerName !== original.customerName ||
      replacement.productModel !== original.productModel ||
      replacement.furnaceNo !== original.furnaceNo ||
      replacement.furnaceLayer !== original.furnaceLayer
    )
      throw new Error(`替换包 ${entry.newPackageCode} 不在可选库存中或货物资料不匹配`)
    result.push({ original: { ...original }, replacement: { ...replacement } })
  }
  return result
}

export const replacementRequest = (preview: LoadingReplacementPreview[]): LoadingReplacement[] =>
  preview.map((item) => ({
    taskLineId: item.original.id,
    newPackageCode: item.replacement.packageCode
  }))

export const verifyLoadingReplacements = (
  lines: LoadingTaskLine[],
  entries: LoadingReplacement[]
) =>
  entries.every((entry) =>
    lines.some(
      (line) =>
        line.id === entry.taskLineId &&
        line.packageCode === entry.newPackageCode &&
        ['PLANNED', 'SCANNED', 'OUTBOUNDED'].includes(line.lineStatus)
    )
  )
