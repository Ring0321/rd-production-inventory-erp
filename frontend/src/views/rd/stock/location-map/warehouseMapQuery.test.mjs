import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource, plain } from '../../trace/package/testHelpers.mjs'

const look = evaluateSource(readFileSync(new URL('./locationLook.ts', import.meta.url), 'utf8'))
const layout = evaluateSource(readFileSync(new URL('./spatialLayout.ts', import.meta.url), 'utf8'))
const query = evaluateSource(readFileSync(new URL('./warehouseMapQuery.ts', import.meta.url), 'utf8'), { './locationLook': look, './spatialLayout': layout })
const location = (id, extra = {}) => ({ id, locationCode: `L${id}`, warehouseCode: 'W1', enabled: true, frozen: false, displayStatus: 'EMPTY', occupiedPackages: 0, occupiedWeight: 0, reservedPackages: 0, reservedWeight: 0, ...extra })

test('filters preserve real IDs and use effective blocked state and independent reservation', () => {
  const items = [location(1), location(2, { displayStatus: 'PARTIAL', occupiedPackages: 4, occupiedWeight: 3600 }), location(3, { reservedPackages: 2 }), location(4, { enabled: false, reservedPackages: 1 }), location(5, { displayStatus: 'MISSING' })]
  assert.deepEqual(items.filter(x => query.matchesLocationFilter(x, 'OCCUPIED')).map(x => x.id), [2])
  assert.deepEqual(items.filter(x => query.matchesLocationFilter(x, 'BLOCKED')).map(x => x.id), [4])
  assert.deepEqual(items.filter(x => query.matchesLocationFilter(x, 'RESERVED')).map(x => x.id), [3, 4])
  assert.deepEqual(items.filter(x => query.matchesLocationFilter(x, 'UNKNOWN')).map(x => x.id), [5])
  const summary = query.summarizeLocations(items)
  assert.deepEqual(plain(summary), { total: 5, empty: 2, occupied: 1, reserved: 2, blocked: 1, unknown: 1, packages: 4, weight: 3600 })
})

test('unknown metrics are never silently totaled as zero and invalid IDs are excluded', () => {
  const unknown = location(1, { occupiedPackages: undefined, occupiedWeight: undefined })
  const items = [unknown, location(2, { occupiedPackages: 2, occupiedWeight: 1600 }), location(-1, { occupiedPackages: 99 }), location(3), location(3)]
  const summary = query.summarizeLocations(items)
  assert.equal(summary.total, 2)
  assert.equal(summary.packages, undefined)
  assert.equal(summary.weight, undefined)
  assert.equal(query.summarizeLocations([]).packages, 0)
})
