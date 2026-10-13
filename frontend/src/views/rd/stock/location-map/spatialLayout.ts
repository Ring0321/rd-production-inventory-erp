import type { RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'

export type LayoutMode = 'recorded' | 'schematic'
export interface SpatialNode {
  location: RdPutawayLocationMapVO
  box: { x: number; y: number; width: number; height: number }
  positionBasis: 'RECORDED_ROW_COLUMN' | 'SCHEMATIC_ORDER'
}
export interface SpatialGroup {
  key: string
  warehouseCode: string
  areaCode: string
  levelCode: string
  label: string
  rows: string[]
  columns: string[]
  nodes: SpatialNode[]
}
export interface UnplacedLocation {
  location: RdPutawayLocationMapVO
  reason: 'IDENTITY_INVALID' | 'POSITION_MISSING' | 'POSITION_CONFLICT'
}
const text = (value: unknown) => (typeof value === 'string' ? value.trim() : '')
export const spatialGroupKey = (location: RdPutawayLocationMapVO) =>
  JSON.stringify([text(location.warehouseCode), text(location.areaCode), text(location.levelCode)])
const order = (a: string, b: string) => a.localeCompare(b, 'zh-CN', { numeric: true })
const hasIdentity = (location: RdPutawayLocationMapVO) =>
  Number.isSafeInteger(location.id) &&
  location.id > 0 &&
  !!text(location.locationCode) &&
  !!text(location.warehouseCode)

export const isLocationIdentityValid = (locations: RdPutawayLocationMapVO[], id: number) => {
  const matches = locations.filter((location) => location.id === id)
  return matches.length === 1 && hasIdentity(matches[0])
}

export const buildSpatialLayout = (
  locations: RdPutawayLocationMapVO[],
  mode: LayoutMode = 'recorded'
) => {
  const unplaced: UnplacedLocation[] = []
  const records = new Map<string, RdPutawayLocationMapVO[]>()
  const ids = new Map<number, number>()
  for (const location of locations) ids.set(location.id, (ids.get(location.id) || 0) + 1)
  for (const location of locations) {
    if (!hasIdentity(location) || ids.get(location.id) !== 1) {
      unplaced.push({ location, reason: 'IDENTITY_INVALID' })
      continue
    }
    const key = spatialGroupKey(location)
    records.set(key, [...(records.get(key) || []), location])
  }
  const groups: SpatialGroup[] = []
  for (const [key, items] of records) {
    items.sort((a, b) => order(a.locationCode, b.locationCode) || a.id - b.id)
    const [warehouseCode, areaCode, levelCode] = JSON.parse(key) as string[]
    const first = items[0]
    const group: SpatialGroup = {
      key,
      warehouseCode,
      areaCode,
      levelCode,
      label: [
        first.warehouseName || warehouseCode,
        first.areaName || areaCode || '未登记区域',
        levelCode || '未登记层级'
      ].join(' / '),
      rows: [],
      columns: [],
      nodes: []
    }
    const cells = new Map<string, number>()
    for (const item of items) {
      const cell = JSON.stringify([text(item.rowCode), text(item.columnCode)])
      cells.set(cell, (cells.get(cell) || 0) + 1)
    }
    group.rows =
      mode === 'recorded'
        ? [...new Set(items.map((i) => text(i.rowCode)).filter(Boolean))].sort(order)
        : []
    group.columns =
      mode === 'recorded'
        ? [...new Set(items.map((i) => text(i.columnCode)).filter(Boolean))].sort(order)
        : []
    items.forEach((location, index) => {
      if (mode === 'recorded' && (!text(location.rowCode) || !text(location.columnCode))) {
        unplaced.push({ location, reason: 'POSITION_MISSING' })
        return
      }
      const cell = JSON.stringify([text(location.rowCode), text(location.columnCode)])
      if (mode === 'recorded' && cells.get(cell) !== 1) {
        unplaced.push({ location, reason: 'POSITION_CONFLICT' })
        return
      }
      const column =
        mode === 'recorded' ? group.columns.indexOf(text(location.columnCode)) : index % 4
      const row =
        mode === 'recorded' ? group.rows.indexOf(text(location.rowCode)) : Math.floor(index / 4)
      group.nodes.push({
        location,
        box: { x: column * 176, y: -row * 120, width: 152, height: 92 },
        positionBasis: mode === 'recorded' ? 'RECORDED_ROW_COLUMN' : 'SCHEMATIC_ORDER'
      })
    })
    groups.push(group)
  }
  groups.sort((a, b) => order(a.key, b.key))
  return { groups, unplaced }
}

export const uniqueLocationIdsForCodes = (locations: RdPutawayLocationMapVO[], codes: string[]) => {
  const ids = new Set<number>()
  for (const code of new Set(codes.map(text).filter(Boolean))) {
    const matches = locations.filter(
      (l) => text(l.locationCode) === code && Number.isSafeInteger(l.id) && l.id > 0
    )
    if (matches.length === 1 && isLocationIdentityValid(locations, matches[0].id))
      ids.add(matches[0].id)
  }
  return [...ids]
}

export const metricText = (value: unknown, scale = 1) => {
  if (
    value === null ||
    value === undefined ||
    typeof value === 'boolean' ||
    (typeof value === 'string' && !value.trim())
  )
    return '待核'
  const number = Number(value)
  if (!Number.isFinite(number) || number < 0 || !Number.isFinite(scale) || scale <= 0) return '待核'
  return (number / scale).toLocaleString('zh-CN', { maximumFractionDigits: 6 })
}

export const capacityText = (value: unknown, unit: string, scale = 1) => {
  if (value === null || value === undefined) return '未设上限'
  const metric = metricText(value, scale)
  return metric === '待核' ? metric : `${metric} ${unit}`
}
