import type { SpatialGroup, UnplacedLocation } from './spatialLayout'
import { buildWarehouseOverview, OVERVIEW_KEY } from './warehouseOverview'
export interface PublishedWarehouseLayout {
  schemaVersion: number
  warehouseCode: string
  width: number
  height: number
  elements: Array<{
    id: string
    kind: 'LOCATION' | 'AISLE' | 'ZONE'
    locationId?: number
    x: number
    y: number
    width: number
    height: number
    label: string
  }>
}

export const buildLayoutOverview = (
  groups: SpatialGroup[],
  configuredLayout?: PublishedWarehouseLayout
): SpatialGroup =>
  configuredLayout
    ? {
        key: OVERVIEW_KEY,
        warehouseCode: configuredLayout.warehouseCode,
        areaCode: '',
        levelCode: '',
        label: '全仓发布布局示意',
        rows: [],
        columns: [],
        // Published areas/levels share one canvas; never normalize or pack them separately.
        nodes: groups.flatMap((group) => group.nodes)
      }
    : buildWarehouseOverview(groups)

export const applyPublishedLayout = (
  base: { groups: SpatialGroup[]; unplaced: UnplacedLocation[] },
  doc: PublishedWarehouseLayout
) => {
  if (
    doc.schemaVersion !== 1 ||
    !doc.warehouseCode ||
    !Array.isArray(doc.elements) ||
    ![doc.width, doc.height].every((n) => Number.isFinite(n) && n >= 64 && n <= 4096)
  )
    throw new Error('发布布局结构待核')
  const byId = new Map<number, PublishedWarehouseLayout['elements'][number]>()
  for (const e of doc.elements) {
    if (
      ![e.x, e.y, e.width, e.height].every(Number.isFinite) ||
      e.x < 0 ||
      e.y < 0 ||
      e.width <= 0 ||
      e.height <= 0 ||
      e.x + e.width > doc.width ||
      e.y + e.height > doc.height
    )
      throw new Error('发布布局坐标待核')
    if (e.kind === 'LOCATION') {
      if (
        !Number.isSafeInteger(e.locationId) ||
        (e.locationId || 0) <= 0 ||
        byId.has(e.locationId!)
      )
        throw new Error('发布库位身份重复或无效')
      byId.set(e.locationId!, e)
    }
  }
  const unplaced = [...base.unplaced]
  const groups = base.groups.map((group) =>
    group.warehouseCode !== doc.warehouseCode
      ? group
      : {
          ...group,
          nodes: group.nodes.flatMap((node) => {
            const e = byId.get(node.location.id)
            if (!e) {
              unplaced.push({ location: node.location, reason: 'POSITION_MISSING' })
              return []
            }
            return [
              {
                ...node,
                box: { x: e.x, y: -e.y, width: e.width, height: e.height },
                positionBasis: 'SCHEMATIC_ORDER' as const
              }
            ]
          })
        }
  )
  return { groups, unplaced }
}
