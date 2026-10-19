import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'

const read = (name) => readFileSync(new URL(name, import.meta.url), 'utf8')
const spatial = evaluateSource(read('./spatialLayout.ts'))
const overview = evaluateSource(read('./warehouseOverview.ts'), { './spatialLayout': spatial })
const tiers = evaluateSource(read('./warehouseTiers.ts'), { './warehouseOverview': overview })
const location = (id, levelCode, extra = {}) => ({
  id,
  warehouseCode: 'W1',
  areaCode: 'A',
  levelCode,
  rowCode: '01',
  columnCode: '01',
  locationCode: `L-${id}`,
  ...extra
})

test('registered upper and lower cells align only in display and preserve source facts', () => {
  const input = [location(1, '01'), location(2, '02')]
  const before = JSON.stringify(input)
  const groups = spatial.buildSpatialLayout(input, 'schematic').groups
  const nodes = tiers.buildTieredWarehouseOverview(groups).nodes
  assert.equal(nodes.length, 2)
  assert.equal(nodes[0].box.x, nodes[1].box.x)
  assert.equal(nodes[0].box.y, nodes[1].box.y)
  assert.deepEqual(
    Array.from(nodes, (node) => node.displayTier),
    [0, 1]
  )
  assert.equal(JSON.stringify(input), before)
  assert.equal(
    groups.flatMap((group) => group.nodes).some((node) => node.displayTier !== undefined),
    false
  )
})

test('other warehouses and areas never share one rack', () => {
  const groups = spatial.buildSpatialLayout([
    location(1, '01'),
    location(2, '02'),
    location(3, '01', { warehouseCode: 'W2' }),
    location(4, '02', { warehouseCode: 'W2' }),
    location(5, '01', { areaCode: 'B' })
  ]).groups
  const nodes = tiers.buildTieredWarehouseOverview(groups).nodes
  assert.notEqual(
    JSON.stringify(nodes.find((n) => n.location.id === 1).box),
    JSON.stringify(nodes.find((n) => n.location.id === 3).box)
  )
  assert.equal(new Set(nodes.map((n) => n.location.id)).size, 5)
})

test('missing and conflicting registration stays flat rather than inventing a tier', () => {
  for (const input of [
    [location(1, ''), location(2, '02')],
    [location(1, '01', { rowCode: '' }), location(2, '02')],
    [location(1, '01'), location(2, '01'), location(3, '02')]
  ]) {
    const result = tiers.buildTieredWarehouseOverview(spatial.buildSpatialLayout(input).groups)
    assert.ok(result.nodes.every((node) => node.displayTier === undefined))
  }
})

test('tier labels use registered level codes, never furnace layers or bag quantities', () => {
  const options = tiers.warehouseTierOptions([
    location(1, '02'),
    location(2, '01'),
    location(3, '')
  ])
  assert.deepEqual(
    Array.from(options, (option) => option.value),
    ['01', '02']
  )
  assert.equal(options[0].label, '库位 01 层')
})
