import * as THREE from 'three'

export const createWarehouseShell = (bounds: THREE.Box3) => {
  const group = new THREE.Group()
  const minX = bounds.min.x - 1.1
  const maxX = bounds.max.x + 1.1
  const minZ = bounds.min.z - 1.1
  const maxZ = bounds.max.z + 2.6
  const width = maxX - minX
  const depth = maxZ - minZ
  const centerX = (minX + maxX) / 2
  const centerZ = (minZ + maxZ) / 2
  const frameHeight = Math.max(2.6, bounds.max.y + 0.8)
  const concrete = new THREE.MeshStandardMaterial({ color: '#e5e9e8', roughness: 0.95 })
  const edge = new THREE.MeshStandardMaterial({ color: '#bbc5c5', roughness: 0.9 })
  const steel = new THREE.MeshStandardMaterial({
    color: '#778d94',
    roughness: 0.55,
    metalness: 0.45
  })
  const wall = new THREE.MeshStandardMaterial({ color: '#dbe3e4', roughness: 0.9 })
  const yellow = new THREE.MeshStandardMaterial({ color: '#d5b35d', roughness: 0.9 })
  const box = (
    parent: THREE.Group,
    size: number[],
    position: number[],
    material: THREE.Material
  ) => {
    const mesh = new THREE.Mesh(
      new THREE.BoxGeometry(...(size as [number, number, number])),
      material
    )
    mesh.position.set(...(position as [number, number, number]))
    mesh.castShadow = true
    mesh.receiveShadow = true
    parent.add(mesh)
    return mesh
  }
  const floor = box(group, [width, 0.22, depth], [centerX, -0.15, centerZ], concrete)
  floor.name = 'warehouse-floor'
  floor.castShadow = false
  box(group, [width + 0.12, 0.16, depth + 0.12], [centerX, -0.34, centerZ], edge)
  const frame = new THREE.Group()
  frame.name = 'warehouse-frame'
  group.add(frame)
  box(frame, [width, 1.25, 0.12], [centerX, 0.62, minZ], wall)
  box(frame, [0.12, 1.25, depth], [minX, 0.62, centerZ], wall)
  box(frame, [width, 0.18, 0.2], [centerX, frameHeight - 0.05, minZ], steel)
  box(frame, [0.2, 0.18, depth], [minX, frameHeight - 0.05, centerZ], steel)
  const rearPosts = Math.min(10, Math.max(2, Math.ceil(width / 4)))
  const sidePosts = Math.min(10, Math.max(2, Math.ceil(depth / 4)))
  for (let index = 0; index <= rearPosts; index++) {
    const x = minX + (index / rearPosts) * width
    box(frame, [0.15, frameHeight, 0.15], [x, frameHeight / 2, minZ], steel)
    box(frame, [0.3, 0.42, 0.3], [x, 0.21, minZ], yellow)
  }
  for (let index = 1; index <= sidePosts; index++) {
    const z = minZ + (index / sidePosts) * depth
    box(frame, [0.15, frameHeight, 0.15], [minX, frameHeight / 2, z], steel)
    box(frame, [0.3, 0.42, 0.3], [minX, 0.21, z], yellow)
  }
  const markings = new THREE.Group()
  markings.name = 'warehouse-markings'
  group.add(markings)
  const laneZ = bounds.max.z + 1.0
  box(markings, [width - 0.6, 0.015, 0.055], [centerX, -0.02, laneZ], yellow)
  box(markings, [width - 0.6, 0.015, 0.055], [centerX, -0.02, maxZ - 0.35], yellow)
  const divisions = Math.min(24, Math.max(3, Math.ceil(width / 1.1)))
  for (let index = 0; index < divisions; index++) {
    const x = minX + 0.45 + (index / divisions) * (width - 0.9)
    const mark = box(markings, [0.035, 0.015, 0.3], [x, -0.02, laneZ - 0.23], yellow)
    mark.rotation.y = -Math.PI / 4
  }
  return group
}
