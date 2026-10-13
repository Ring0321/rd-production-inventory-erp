import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import * as three from 'three'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'

const { createWarehouseShell } = evaluateSource(readFileSync(new URL('./warehouseShell.ts', import.meta.url), 'utf8'), { three })

test('warehouse shell is local non-pickable geometry enclosing the original location bounds', () => {
  const box = new three.Box3(new three.Vector3(-2, 0, -1), new three.Vector3(7, 1.65, 4))
  const group = createWarehouseShell(box)
  const bounds = new three.Box3().setFromObject(group)
  assert.ok(bounds.min.x < box.min.x && bounds.max.x > box.max.x)
  assert.ok(bounds.min.z < box.min.z && bounds.max.z > box.max.z)
  assert.ok(group.getObjectByName('warehouse-floor'))
  assert.ok(group.getObjectByName('warehouse-frame'))
  assert.ok(group.getObjectByName('warehouse-markings'))
  group.traverse(child => {
    assert.equal(child.userData.locationId, undefined)
    assert.equal(child.type === 'Sprite', false)
  })
  assert.deepEqual(box.min.toArray(), [-2, 0, -1])
  assert.deepEqual(box.max.toArray(), [7, 1.65, 4])
})

test('shell remains bounded for large layouts and contains no invented package meshes', () => {
  const group = createWarehouseShell(new three.Box3(new three.Vector3(0, 0, 0), new three.Vector3(50, 1.65, 70)))
  let meshes = 0
  group.traverse(child => { if (child.isMesh) meshes++ })
  assert.ok(meshes < 120)
  assert.ok(new three.Box3().setFromObject(group).max.y < 5)
})
