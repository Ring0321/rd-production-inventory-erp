import type { RdPackageUnitApi } from '@/api/rd/stock/package'

export const queryLabelPackages = async (
  api: Pick<typeof RdPackageUnitApi, 'trace' | 'getPage'>,
  keyword: string,
  pageNo: number
) => {
  const code = keyword.trim()
  if (!code) return api.getPage({ pageNo, pageSize: 20 })
  const list = await api.trace({ packageCode: code })
  if (!Array.isArray(list) || list.length > 1 || list.some((row) => row.packageCode !== code))
    throw new Error('原包件查询范围不一致')
  return { list, total: list.length }
}
