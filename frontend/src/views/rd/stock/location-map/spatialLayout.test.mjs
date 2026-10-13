import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'
const { buildSpatialLayout, uniqueLocationIdsForCodes, metricText, capacityText } = evaluateSource(
  readFileSync(new URL('./spatialLayout.ts', import.meta.url), 'utf8')
)
const location = (id, extra = {}) => ({
  id,
  warehouseCode: 'W1',
  warehouseName: '示意仓库',
  areaCode: 'A1',
  rowCode: 'R1',
  columnCode: 'C1',
  levelCode: 'L1',
  locationCode: `LOC-${id}`,
  occupiedPackages: 0,
  occupiedWeight: 0,
  reservedPackages: 0,
  reservedWeight: 0,
  enabled: true,
  frozen: false,
  displayStatus: 'EMPTY',
  ...extra
})
test('warehouse, area and location level are separate and axis order is natural, not physical coordinates', () => {
  const input = [
    location(3, { rowCode: 'R10', columnCode: 'C2' }),
    location(1),
    location(2, { rowCode: 'R2' }),
    location(4, { warehouseCode: 'W2' }),
    location(5, { levelCode: 'L2' })
  ]
  const before = JSON.stringify(input)
  const layout = buildSpatialLayout(input)
  assert.equal(layout.groups.length, 3)
  const first = layout.groups.find((g) => g.warehouseCode === 'W1' && g.levelCode === 'L1')
  assert.deepEqual(Array.from(first.rows), ['R1', 'R2', 'R10'])
  assert.deepEqual(Array.from(first.columns), ['C1', 'C2'])
  assert.ok(
    first.nodes.find((n) => n.location.id === 2).box.y <
      first.nodes.find((n) => n.location.id === 1).box.y
  )
  assert.ok(
    first.nodes.find((n) => n.location.id === 3).box.x >
      first.nodes.find((n) => n.location.id === 1).box.x
  )
  assert.equal(JSON.stringify(input), before)
})
test('missing row/column and ambiguous cells stay visible as unplaced, never assigned invented positions', () => {
  const layout = buildSpatialLayout([
    location(1, { rowCode: undefined }),
    location(2, { columnCode: ' ' }),
    location(3),
    location(4)
  ])
  assert.equal(layout.unplaced.length, 4)
  assert.equal(
    layout.groups.reduce((n, g) => n + g.nodes.length, 0),
    0
  )
  assert.ok(layout.unplaced.some((n) => n.reason === 'POSITION_CONFLICT'))
})
test('task location codes bind only exact unique ERP identities, including no ambiguous or substring fallback', () => {
  const input = [
    location(1, { locationCode: 'A-1' }),
    location(2, { locationCode: 'A-10', columnCode: 'C2' }),
    location(3, { locationCode: 'DUP', warehouseCode: 'W2' }),
    location(4, { locationCode: 'DUP', warehouseCode: 'W3' })
  ]
  assert.deepEqual(Array.from(uniqueLocationIdsForCodes(input, ['A-1', 'DUP', 'MISSING'])), [1])
})
test('unknown, invalid and real zero metrics stay distinguishable', () => {
  assert.equal(metricText(undefined), '待核')
  assert.equal(metricText(null), '待核')
  assert.equal(metricText(-1), '待核')
  assert.equal(metricText(Number.NaN), '待核')
  assert.equal(metricText(0), '0')
  assert.equal(metricText(1250, 1000), '1.25')
})
test('optional capacity preserves the ERP unlimited contract without making invalid values zero', () => {
  assert.equal(capacityText(undefined, '包'), '未设上限')
  assert.equal(capacityText(null, '吨', 1000), '未设上限')
  assert.equal(capacityText(0, '包'), '0 包')
  assert.equal(capacityText(1250, '吨', 1000), '1.25 吨')
  assert.equal(capacityText(-1, '包'), '待核')
})
test('duplicate or unsafe location IDs cannot produce map identity collisions', () => {
  const layout = buildSpatialLayout([
    location(1),
    location(1, { columnCode: 'C2' }),
    location(Number.MAX_SAFE_INTEGER + 1)
  ])
  assert.equal(
    layout.groups.reduce((n, g) => n + g.nodes.length, 0),
    0
  )
  assert.equal(layout.unplaced.length, 3)
})
test('user-authorized self-built layout uses real IDs but never writes invented row/column metadata', () => {
  const input = [
    location(2, { rowCode: undefined, columnCode: undefined }),
    location(1, { rowCode: undefined, columnCode: undefined })
  ]
  const before = JSON.stringify(input)
  const layout = buildSpatialLayout(input, 'schematic')
  assert.equal(layout.groups[0].nodes.length, 2)
  assert.equal(layout.groups[0].nodes[0].location.id, 1)
  assert.equal(layout.groups[0].nodes[0].positionBasis, 'SCHEMATIC_ORDER')
  assert.equal(layout.groups[0].nodes[0].location.rowCode, undefined)
  assert.equal(JSON.stringify(input), before)
})
