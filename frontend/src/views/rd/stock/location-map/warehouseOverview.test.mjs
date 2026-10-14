import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource, plain } from '../../trace/package/testHelpers.mjs'
const layout = evaluateSource(readFileSync(new URL('./spatialLayout.ts', import.meta.url), 'utf8'))
const { buildWarehouseOverview, warehouseScopes } = evaluateSource(readFileSync(new URL('./warehouseOverview.ts', import.meta.url), 'utf8'), { './spatialLayout': layout })
const location = (id, extra = {}) => ({ id, warehouseCode: 'W1', warehouseName: '仓库1', locationCode: `L${id}`, areaCode: 'A1', levelCode: '1', ...extra })

test('overview separates every actual area with nonoverlapping stable boxes and leaves input untouched', () => {
  const input = [location(1), location(2), location(3, { areaCode: 'A2' }), location(4, { levelCode: '2' })]
  const original = JSON.stringify(input)
  const groups = layout.buildSpatialLayout(input, 'schematic').groups
  const result = buildWarehouseOverview(groups)
  assert.equal(result.nodes.length, 4)
  assert.deepEqual(plain(result.nodes.map(n => n.location.id).sort()), [1, 2, 3, 4])
  for (const [i, a] of result.nodes.entries()) for (const b of result.nodes.slice(i + 1)) {
    assert.ok(a.box.x + a.box.width <= b.box.x || b.box.x + b.box.width <= a.box.x || a.box.y - a.box.height >= b.box.y || b.box.y - b.box.height >= a.box.y)
  }
  assert.deepEqual(plain(buildWarehouseOverview(groups)), plain(result))
  assert.equal(JSON.stringify(input), original)
})

test('warehouse scope normalizes metadata but excludes duplicate and invalid identity globally', () => {
  const scopes = warehouseScopes([location(1, { warehouseCode: ' W1 ' }), location(2, { warehouseCode: 'W2' }), location(3), location(3, { warehouseCode: 'W2' }), location(0)])
  assert.deepEqual(plain(scopes.map(s => ({ code: s.code, ids: s.locations.map(l => l.id) }))), [{ code: 'W1', ids: [1] }, { code: 'W2', ids: [2] }])
})

test('pending recorded cells remain unplotted and empty overview does not invent bins', () => {
  const recorded = layout.buildSpatialLayout([location(1), location(2, { rowCode: 'R1', columnCode: 'C1' })], 'recorded')
  assert.deepEqual(plain(buildWarehouseOverview(recorded.groups).nodes.map(n => n.location.id)), [2])
  assert.equal(recorded.unplaced.length, 1)
  assert.equal(buildWarehouseOverview([]).nodes.length, 0)
})
