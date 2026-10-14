import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'
const { canViewWarehouseSpace } = evaluateSource(readFileSync(new URL('./warehouseSpaceAccess.ts', import.meta.url), 'utf8'))
test('warehouse showcase allows existing query-only and administrator access, not execute-only or mixed driver roles', () => {
  assert.equal(canViewWarehouseSpace([], new Set(['rd:putaway-task:query'])), true)
  assert.equal(canViewWarehouseSpace(['super_admin'], new Set(['*:*:*'])), true)
  assert.equal(canViewWarehouseSpace([], new Set()), false)
  assert.equal(canViewWarehouseSpace(['rd_forklift_driver'], new Set(['rd:putaway-task:execute'])), false)
  assert.equal(canViewWarehouseSpace(['super_admin', 'rd_forklift_driver'], new Set(['*:*:*'])), false)
})
test('entry, authenticated route, menu projection and snapshot use the shared access predicate', () => {
  for (const file of ['../../../Home/Index.vue', '../../../../permission.ts', '../../../../store/modules/permission.ts', './useWarehouseSpace.ts', './RdWarehouseSpace.vue'])
    assert.match(readFileSync(new URL(file, import.meta.url), 'utf8'), /canViewWarehouseSpace/)
  const reset = readFileSync(new URL('../../../../router/index.ts', import.meta.url), 'utf8')
  assert.match(reset, /'WarehouseSpaceRoot'/)
  assert.match(reset, /'RdWarehouseSpace'/)
})

test('role landing icons used during permission checks are bundled from the existing local Lucide dataset', () => {
  const local = JSON.parse(readFileSync(new URL('../../../../plugins/svgIcon/warehouse-icons.json', import.meta.url), 'utf8'))
  const official = JSON.parse(readFileSync(new URL('../../../../../node_modules/@iconify/json/json/lucide.json', import.meta.url), 'utf8'))
  for (const icon of ['forklift', 'truck', 'clipboard-list', 'package-plus']) assert.deepEqual(local.icons[icon], official.icons[icon])
})
