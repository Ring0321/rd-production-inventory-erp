import type { RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'
import { locationLook } from './locationLook'
import { isLocationIdentityValid } from './spatialLayout'

export type LocationFilter = 'ALL' | 'EMPTY' | 'OCCUPIED' | 'RESERVED' | 'BLOCKED' | 'UNKNOWN'
const knownAmount = (value: unknown) =>
  typeof value === 'number' && Number.isFinite(value) && value >= 0

export const matchesLocationFilter = (location: RdPutawayLocationMapVO, filter: LocationFilter) => {
  const state = locationLook(location).code
  if (filter === 'ALL') return true
  if (filter === 'OCCUPIED') return state === 'PARTIAL' || state === 'FULL'
  if (filter === 'RESERVED')
    return (
      (knownAmount(location.reservedPackages) && location.reservedPackages > 0) ||
      (knownAmount(location.reservedWeight) && location.reservedWeight > 0)
    )
  return state === filter
}

export const summarizeLocations = (locations: RdPutawayLocationMapVO[]) => {
  const valid = locations.filter((location) => isLocationIdentityValid(locations, location.id))
  const count = (filter: LocationFilter) =>
    valid.filter((l) => matchesLocationFilter(l, filter)).length
  const total = (field: 'occupiedPackages' | 'occupiedWeight') => {
    if (!valid.every((l) => knownAmount(l[field]))) return undefined
    const sum = valid.reduce((value, l) => value + l[field], 0)
    return Number.isFinite(sum) ? sum : undefined
  }
  return {
    total: valid.length,
    empty: count('EMPTY'),
    occupied: count('OCCUPIED'),
    reserved: count('RESERVED'),
    blocked: count('BLOCKED'),
    unknown: count('UNKNOWN'),
    packages: total('occupiedPackages'),
    weight: total('occupiedWeight')
  }
}
