import * as THREE from 'three'

export const warehouseFrameDistance = (
  box: THREE.Box3,
  direction: THREE.Vector3,
  aspect: number,
  fov: number
) => {
  const center = box.getCenter(new THREE.Vector3())
  const rotation = new THREE.Matrix4().lookAt(
    direction,
    new THREE.Vector3(),
    new THREE.Vector3(0, 1, 0)
  )
  const inverse = new THREE.Quaternion().setFromRotationMatrix(rotation).invert()
  const vertical = Math.tan(THREE.MathUtils.degToRad(fov) / 2)
  const horizontal = vertical * aspect
  let distance = 1
  for (const x of [box.min.x, box.max.x])
    for (const y of [box.min.y, box.max.y])
      for (const z of [box.min.z, box.max.z]) {
        const corner = new THREE.Vector3(x, y, z).sub(center).applyQuaternion(inverse)
        distance = Math.max(
          distance,
          corner.z + Math.abs(corner.x) / horizontal,
          corner.z + Math.abs(corner.y) / vertical
        )
      }
  return distance * 1.12
}
