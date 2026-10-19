import { isLocationIdentityValid, metricText, type SpatialNode } from './spatialLayout'
import { locationLook } from './locationLook'

export interface Warehouse3DBin {
  id: number
  code: string
  center: { x: number; y?: number; z: number }
  size: { x: number; z: number }
  height: number
  glyph: 'EMPTY' | 'OCCUPIED' | 'UNKNOWN'
  state: ReturnType<typeof locationLook>
  summary: string
  reserved: string
}

export const buildWarehouse3D = (nodes: SpatialNode[]) => {
  const locations = nodes.map((node) => node.location)
  const bins: Warehouse3DBin[] = nodes
    .filter(
      (node) =>
        isLocationIdentityValid(locations, node.location.id) &&
        [node.box.x, node.box.y, node.box.width, node.box.height].every(Number.isFinite) &&
        node.box.width > 0 &&
        node.box.height > 0
    )
    .map((node) => {
      const location = node.location
      const count = location.occupiedPackages
      return {
        id: location.id,
        code: location.locationCode,
        center: {
          x: (node.box.x + node.box.width / 2) / 40,
          y:
            Number.isSafeInteger(node.displayTier) &&
            node.displayTier! >= 0 &&
            node.displayTier! < 8
              ? node.displayTier! * 2.8
              : 0,
          z: (-node.box.y + node.box.height / 2) / 40
        },
        size: { x: node.box.width / 40, z: node.box.height / 40 },
        height: 1.65,
        glyph:
          typeof count === 'number' && Number.isSafeInteger(count) && count >= 0
            ? count > 0
              ? 'OCCUPIED'
              : 'EMPTY'
            : 'UNKNOWN',
        state: locationLook(location),
        summary: `在库 ${metricText(count)} 包 · ${metricText(location.occupiedWeight, 1000)} 吨`,
        reserved: `预留 ${metricText(location.reservedPackages)} 包`
      }
    })
  const bounds = bins.length
    ? {
        minX: Math.min(...bins.map((bin) => bin.center.x - bin.size.x / 2)),
        maxX: Math.max(...bins.map((bin) => bin.center.x + bin.size.x / 2)),
        minZ: Math.min(...bins.map((bin) => bin.center.z - bin.size.z / 2)),
        maxZ: Math.max(...bins.map((bin) => bin.center.z + bin.size.z / 2))
      }
    : undefined
  return { bins, bounds }
}
