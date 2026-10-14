import type { RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'
import { isLocationIdentityValid, type SpatialGroup, type SpatialNode } from './spatialLayout'

export const OVERVIEW_KEY = '__warehouse_overview__'
const text = (value: unknown) => (typeof value === 'string' ? value.trim() : '')

export const warehouseScopes = (locations: RdPutawayLocationMapVO[]) => {
  const groups = new Map<
    string,
    { code: string; name: string; locations: RdPutawayLocationMapVO[] }
  >()
  for (const location of locations) {
    if (!isLocationIdentityValid(locations, location.id)) continue
    const code = text(location.warehouseCode)
    if (!groups.has(code))
      groups.set(code, { code, name: text(location.warehouseName) || code, locations: [] })
    groups.get(code)!.locations.push(location)
  }
  return [...groups.values()].sort((a, b) =>
    a.code.localeCompare(b.code, 'zh-CN', { numeric: true })
  )
}

// Separate areas on a schematic floor; offsets never become ERP row/column coordinates.
export const buildWarehouseOverview = (groups: SpatialGroup[]): SpatialGroup => {
  const sections = groups
    .filter((group) => group.nodes.length)
    .map((group) => {
      const minX = Math.min(...group.nodes.map((n) => n.box.x))
      const maxY = Math.max(...group.nodes.map((n) => n.box.y))
      const width = Math.max(...group.nodes.map((n) => n.box.x + n.box.width)) - minX
      const height = maxY - Math.min(...group.nodes.map((n) => n.box.y - n.box.height))
      return { group, minX, maxY, width, height }
    })
  const gap = 112
  const area = sections.reduce((sum, s) => sum + (s.width + gap) * (s.height + gap), 0)
  const rowWidth = Math.max(0, ...sections.map((s) => s.width), Math.sqrt(area * 1.6))
  let x = 0,
    y = 0,
    rowHeight = 0
  const nodes: SpatialNode[] = []
  for (const section of sections) {
    if (x > 0 && x + section.width > rowWidth) {
      x = 0
      y += rowHeight + gap
      rowHeight = 0
    }
    nodes.push(
      ...section.group.nodes.map((node) => ({
        ...node,
        box: { ...node.box, x: node.box.x - section.minX + x, y: node.box.y - section.maxY - y }
      }))
    )
    x += section.width + gap
    rowHeight = Math.max(rowHeight, section.height)
  }
  return {
    key: OVERVIEW_KEY,
    warehouseCode: '',
    areaCode: '',
    levelCode: '',
    label: '全仓总览',
    rows: [],
    columns: [],
    nodes
  }
}
