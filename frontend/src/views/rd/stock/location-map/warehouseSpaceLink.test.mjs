import assert from 'node:assert/strict'
import { test } from 'node:test'
import { evaluate, plain } from '../../pda/warehouse/testHelpers.mjs'
const m = evaluate(new URL('./warehouseSpaceLink.ts', import.meta.url), {
  './spatialLayout': {
    isLocationIdentityValid: (rows, id) =>
      rows.filter((row) => row.id === id).length === 1 &&
      rows.find((row) => row.id === id)?.valid !== false
  }
})
test('graph space link uses exact registered IDs, not a location code or inferred coordinate', () => {
  const locations = [
    { id: 7, warehouseCode: 'W1', locationCode: 'A-7' },
    { id: 8, warehouseCode: 'W2', locationCode: 'A-7' }
  ]
  assert.deepEqual(plain(m.resolveWarehouseSpaceLink(locations, '8')), {
    state: 'FOUND',
    id: 8,
    warehouseCode: 'W2'
  })
  assert.equal(m.resolveWarehouseSpaceLink(locations, 'A-7').state, 'INVALID')
})
test('unknown, duplicate, unsafe or malicious link identity cannot focus another bin', () => {
  assert.equal(m.resolveWarehouseSpaceLink([], undefined).state, 'NONE')
  for (const value of ['0', '-1', '1.1', '9007199254740993', ['7'], '7;alert(1)'])
    assert.equal(m.resolveWarehouseSpaceLink([], value).state, 'INVALID')
  assert.equal(m.resolveWarehouseSpaceLink([], '7').state, 'MISSING')
  assert.equal(m.resolveWarehouseSpaceLink([{ id: 7 }, { id: 7 }], '7').state, 'MISSING')
})
