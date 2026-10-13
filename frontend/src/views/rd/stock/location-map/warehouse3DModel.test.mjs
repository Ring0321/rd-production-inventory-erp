import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'

const spatial = evaluateSource(readFileSync(new URL('./spatialLayout.ts', import.meta.url), 'utf8'))
const { buildWarehouse3D } = evaluateSource(readFileSync(new URL('./warehouse3DModel.ts', import.meta.url), 'utf8'), { './spatialLayout': spatial, './locationLook': evaluateSource(readFileSync(new URL('./locationLook.ts', import.meta.url), 'utf8')) })
const node = (id, extra = {}) => ({ location: { id, warehouseCode: 'W1', locationCode: `A-${id}`, enabled: true, frozen: false, displayStatus: 'PARTIAL', occupiedPackages: 7, occupiedWeight: 7000, reservedPackages: 0, ...extra }, box: { x: (id-1)*176, y: 0, width: 152, height: 92 }, positionBasis: 'SCHEMATIC_ORDER' })

test('3D uses same ERP IDs and schematic plane, with unchanged input and no invented physical levels', () => {
  const input = [node(1), { ...node(2), box: { x: 176, y: -120, width: 152, height: 92 } }]
  const before = JSON.stringify(input)
  const model = buildWarehouse3D(input)
  assert.equal(model.bins.length, 2)
  assert.deepEqual(Array.from(model.bins, b => b.id), [1,2])
  assert.ok(model.bins[1].center.z > model.bins[0].center.z)
  assert.equal(JSON.stringify(input), before)
  assert.equal(model.bins[0].height, model.bins[1].height)
})
test('the single bag glyph means occupancy, never fabricated bag count or physical stack height', () => {
  const bins = buildWarehouse3D([node(1), node(2, { occupiedPackages: 999 }), node(3, { occupiedPackages: 0, occupiedWeight: 0, reservedPackages: 12, displayStatus: 'EMPTY' }), node(4, { occupiedPackages: undefined, occupiedWeight: undefined })]).bins
  assert.equal(bins[0].glyph, 'OCCUPIED')
  assert.equal(bins[1].glyph, 'OCCUPIED')
  assert.equal(bins[0].height, bins[1].height)
  assert.equal(bins[2].glyph, 'EMPTY')
  assert.equal(bins[3].glyph, 'UNKNOWN')
  assert.match(bins[2].summary, /在库 0/)
  assert.match(bins[3].summary, /待核/)
})
test('frozen/invalid state and identity remain distinct; invalid or duplicate drawing IDs cannot bind', () => {
  const model = buildWarehouse3D([node(1, { frozen: true }), node(2, { warehouseCode: '' }), node(3), node(3), { ...node(4), box: { x: Infinity, y: 0, width: 152, height: 92 } }])
  assert.equal(model.bins.length, 1)
  assert.equal(model.bins[0].state.code, 'BLOCKED')
})
test('empty 3D model has no invented stock bins and bounds are safe', () => {
  const model = buildWarehouse3D([])
  assert.equal(model.bins.length, 0)
  assert.equal(model.bounds, undefined)
})
