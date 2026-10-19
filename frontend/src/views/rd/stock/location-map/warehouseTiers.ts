import type { RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'
import type { SpatialGroup, SpatialNode } from './spatialLayout'
import { buildWarehouseOverview } from './warehouseOverview'

const text = (value: unknown) => (typeof value === 'string' ? value.trim() : '')
const order = (a: string, b: string) => a.localeCompare(b, 'zh-CN', { numeric: true })

export const warehouseTierOptions = (locations: RdPutawayLocationMapVO[]) =>
  [...new Set(locations.map((location) => text(location.levelCode)).filter(Boolean))]
    .sort(order)
    .map((value) => ({ value, label: `库位 ${value} 层` }))

// Registered cells align in this display mode only; spacing is not measured elevation.
export const buildTieredWarehouseOverview = (groups: SpatialGroup[]): SpatialGroup => {
  const areas = new Map<string, SpatialGroup[]>()
  for (const group of groups) {
    const key = JSON.stringify([group.warehouseCode, group.areaCode])
    areas.set(key, [...(areas.get(key) || []), group])
  }
  const sections: SpatialGroup[] = []
  for (const area of areas.values()) {
    const levels = [...new Set(area.map((group) => text(group.levelCode)))].sort(order)
    const cells = new Set<string>()
    const nodes = area.flatMap((group) => group.nodes)
    const validCells = nodes.every((node) => {
      const row = text(node.location.rowCode)
      const column = text(node.location.columnCode)
      const cell = JSON.stringify([text(node.location.levelCode), row, column])
      if (!row || !column || cells.has(cell)) return false
      cells.add(cell)
      return true
    })
    if (
      levels.length < 2 ||
      levels.length > 8 ||
      levels.includes('') ||
      area.some((group) => !group.nodes.length) ||
      !validCells
    ) {
      sections.push(...area)
      continue
    }
    const rows = [...new Set(nodes.map((node) => text(node.location.rowCode)))].sort(order)
    const columns = [...new Set(nodes.map((node) => text(node.location.columnCode)))].sort(order)
    const layered: SpatialNode[] = nodes.map((node) => ({
      ...node,
      displayTier: levels.indexOf(text(node.location.levelCode)),
      box: {
        x: columns.indexOf(text(node.location.columnCode)) * 176,
        y: -rows.indexOf(text(node.location.rowCode)) * 120,
        width: 152,
        height: 92
      }
    }))
    sections.push({ ...area[0], nodes: layered, rows, columns, levelCode: '' })
  }
  return { ...buildWarehouseOverview(sections), label: '全仓登记层级示意' }
}
