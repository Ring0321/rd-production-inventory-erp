import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import * as three from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'

const { disposeSceneObjects, createBinSymbol } = evaluateSource(
  readFileSync(new URL('./warehouse3DAssets.ts', import.meta.url), 'utf8'),
  {
    three,
    'three/examples/jsm/geometries/RoundedBoxGeometry.js': { RoundedBoxGeometry }
  }
)

test('upper tier has independent identity and supports extending to the ground', () => {
  const bin = {
    id: 42,
    center: { x: 1, y: 2.8, z: 2 },
    size: { x: 3.8, z: 2.3 },
    glyph: 'EMPTY',
    state: { fill: '#fff', stroke: '#444' }
  }
  const symbol = createBinSymbol(bin, false, false)
  assert.equal(symbol.position.y, 2.8)
  assert.equal(symbol.userData.locationId, 42)
  const support = symbol.getObjectByName('registered-tier-support')
  assert.ok(support)
  symbol.updateMatrixWorld(true)
  const bounds = new three.Box3().setFromObject(support)
  assert.ok(Math.abs(bounds.min.y) < 0.01)
  assert.ok(bounds.max.y >= 2.8)
  disposeSceneObjects(symbol)
})

test('owned 3D cleanup disposes shared geometry, material arrays and texture only once', () => {
  const group = new three.Group()
  const geometry = new three.BoxGeometry(1, 1, 1)
  const texture = new three.Texture()
  const first = new three.MeshStandardMaterial({ map: texture })
  const second = new three.MeshBasicMaterial({ map: texture })
  const counts = { geometry: 0, first: 0, second: 0, texture: 0 }
  geometry.addEventListener('dispose', () => counts.geometry++)
  first.addEventListener('dispose', () => counts.first++)
  second.addEventListener('dispose', () => counts.second++)
  texture.addEventListener('dispose', () => counts.texture++)
  group.add(new three.Mesh(geometry, [first, second]))
  group.add(new three.Mesh(geometry, first))
  disposeSceneObjects(group)
  assert.deepEqual(counts, { geometry: 1, first: 1, second: 1, texture: 1 })
})
