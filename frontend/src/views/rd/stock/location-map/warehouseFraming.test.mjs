import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import * as three from 'three'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'

const { warehouseFrameDistance } = evaluateSource(
  readFileSync(new URL('./warehouseFraming.ts', import.meta.url), 'utf8'),
  { three }
)
test('actual bounds are fully framed without excessive sphere padding on portrait and landscape views', () => {
  const box = new three.Box3(new three.Vector3(-1, -0.4, -1), new three.Vector3(10, 2.65, 7))
  for (const aspect of [2.2, 0.68, 0.85])
    for (const direction of [
      new three.Vector3(0.85, 1.1, 1.3),
      new three.Vector3(0.001, 1, 0.001)
    ]) {
      const distance = warehouseFrameDistance(box, direction, aspect, 36)
      const center = box.getCenter(new three.Vector3())
      const camera = new three.PerspectiveCamera(36, aspect, 0.1, 200)
      camera.position.copy(center).add(direction.clone().normalize().multiplyScalar(distance))
      camera.lookAt(center)
      camera.updateMatrixWorld()
      let extent = 0
      for (const x of [box.min.x, box.max.x])
        for (const y of [box.min.y, box.max.y])
          for (const z of [box.min.z, box.max.z]) {
            const point = new three.Vector3(x, y, z).project(camera)
            assert.ok(Math.abs(point.x) < 0.96 && Math.abs(point.y) < 0.96)
            extent = Math.max(extent, Math.abs(point.x), Math.abs(point.y))
          }
      assert.ok(extent > 0.7, 'warehouse uses a meaningful part of the viewport')
    }
})

test('dense full-warehouse footprint uses the narrow canvas while retaining safe projected margins', () => {
  const box = new three.Box3(new three.Vector3(-82, -0.4, -82), new three.Vector3(82, 2.65, 82))
  for (const aspect of [256 / 380, 326 / 380]) {
    const direction = new three.Vector3(0.85, 1.1, 1.3)
    const center = box.getCenter(new three.Vector3())
    const distance = warehouseFrameDistance(box, direction, aspect, 36)
    const camera = new three.PerspectiveCamera(36, aspect, 0.1, 2000)
    camera.position.copy(center).add(direction.normalize().multiplyScalar(distance))
    camera.lookAt(center)
    camera.updateMatrixWorld()
    let extent = 0
    for (const x of [box.min.x, box.max.x])
      for (const y of [box.min.y, box.max.y])
        for (const z of [box.min.z, box.max.z]) {
          const point = new three.Vector3(x, y, z).project(camera)
          assert.ok(Math.abs(point.x) < 0.96 && Math.abs(point.y) < 0.96)
          extent = Math.max(extent, Math.abs(point.x), Math.abs(point.y))
        }
    assert.ok(extent > 0.9, 'dense warehouse fills useful width without clipping')
  }
})
