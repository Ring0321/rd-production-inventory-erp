import type { LocationBinding } from './layoutModel'

export interface LayoutLocation extends LocationBinding {
  enabled?: boolean
  frozen?: boolean
}

const PAGE_SIZE = 100
export const MAX_LAYOUT_LOCATION_PAGES = 100

export const loadLayoutLocations = async (options: {
  current: () => boolean
  allowed: (permission: string) => boolean
  getPage: (params: {
    pageNo: number
    pageSize: number
  }) => Promise<{ list: LayoutLocation[]; total: number }>
  getLocationMap: () => Promise<LayoutLocation[]>
}): Promise<LayoutLocation[]> => {
  const check = (permission: string) => {
    if (!options.current() || !options.allowed(permission))
      throw new Error('原库位查询权限或请求已失效，请重新进入')
  }
  const rows: LayoutLocation[] = []
  const ids = new Set<number>()
  const codes = new Set<string>()
  const append = (list: LayoutLocation[]) => {
    for (const row of list) {
      if (
        !row ||
        !Number.isSafeInteger(row.id) ||
        row.id <= 0 ||
        typeof row.warehouseCode !== 'string' ||
        !row.warehouseCode.trim() ||
        typeof row.locationCode !== 'string' ||
        !row.locationCode.trim()
      )
        throw new Error('原库位身份无效，请重新加载核对')
      const code = JSON.stringify([row.warehouseCode, row.locationCode])
      if (ids.has(row.id) || codes.has(code)) throw new Error('原库位分页身份重复，请重新加载核对')
      ids.add(row.id)
      codes.add(code)
      rows.push({
        id: row.id,
        warehouseCode: row.warehouseCode,
        warehouseName: row.warehouseName,
        locationCode: row.locationCode,
        enabled: row.enabled,
        frozen: row.frozen
      })
    }
  }
  if (!options.current()) throw new Error('原库位查询请求已失效，请重新进入')
  if (!options.allowed('rd:warehouse-location:query')) {
    check('rd:putaway-task:query')
    const list = await options.getLocationMap()
    check('rd:putaway-task:query')
    if (!Array.isArray(list) || list.length > PAGE_SIZE * MAX_LAYOUT_LOCATION_PAGES)
      throw new Error('原库位查询数量无效或超出安全上限')
    append(list)
    return rows
  }
  let total: number | undefined
  for (let pageNo = 1; pageNo <= MAX_LAYOUT_LOCATION_PAGES; pageNo++) {
    check('rd:warehouse-location:query')
    // No enabled/frozen filters: existing published bindings must remain repairable.
    const page = await options.getPage({ pageNo, pageSize: PAGE_SIZE })
    check('rd:warehouse-location:query')
    if (
      !page ||
      !Number.isSafeInteger(page.total) ||
      page.total < 0 ||
      page.total > PAGE_SIZE * MAX_LAYOUT_LOCATION_PAGES ||
      !Array.isArray(page.list)
    )
      throw new Error('原库位分页总数无效或超出安全上限')
    total ??= page.total
    if (page.total !== total || page.list.length !== Math.min(PAGE_SIZE, total - rows.length))
      throw new Error('原库位分页不完整或总数已变化，请重新加载核对')
    append(page.list)
    if (rows.length === total) return rows
  }
  throw new Error('原库位查询超出安全页数，请核对数据范围')
}

export const selectLayoutWarehouse = (
  warehouses: readonly { code: string }[],
  requested: unknown,
  previous = ''
) => {
  if (requested !== undefined) {
    if (typeof requested !== 'string' || !warehouses.some((row) => row.code === requested))
      throw new Error('指定仓库不存在或不在当前可查询范围，请选择仓库')
    return requested
  }
  if (previous) {
    if (!warehouses.some((row) => row.code === previous))
      throw new Error('当前仓库已不在可查询范围，请重新选择仓库')
    return previous
  }
  return warehouses[0]?.code || ''
}
