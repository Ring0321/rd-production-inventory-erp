import type { RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'
import { isLocationIdentityValid } from './spatialLayout'

export const resolveWarehouseSpaceLink = (locations: RdPutawayLocationMapVO[], value: unknown) => {
  if (value === undefined) return { state: 'NONE' as const }
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return { state: 'INVALID' as const }
  const id = Number(value)
  if (!Number.isSafeInteger(id)) return { state: 'INVALID' as const }
  const location = locations.find((row) => row.id === id)
  if (!location || !isLocationIdentityValid(locations, id)) return { state: 'MISSING' as const }
  return { state: 'FOUND' as const, id, warehouseCode: location.warehouseCode }
}
