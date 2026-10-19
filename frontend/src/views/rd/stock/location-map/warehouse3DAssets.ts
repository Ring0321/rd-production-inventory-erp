import * as THREE from 'three'
import { RoundedBoxGeometry } from 'three/examples/jsm/geometries/RoundedBoxGeometry.js'
import type { Warehouse3DBin } from './warehouse3DModel'

const wovenMaterial = () => {
  const canvas = document.createElement('canvas')
  canvas.width = canvas.height = 64
  const context = canvas.getContext('2d')!
  context.fillStyle = '#f2f3f0'
  context.fillRect(0, 0, 64, 64)
  context.strokeStyle = '#dfe3de'
  context.lineWidth = 1
  for (let position = 0; position < 64; position += 4) {
    context.beginPath()
    context.moveTo(position, 0)
    context.lineTo(position, 64)
    context.moveTo(0, position)
    context.lineTo(64, position)
    context.stroke()
  }
  const texture = new THREE.CanvasTexture(canvas)
  texture.colorSpace = THREE.SRGBColorSpace
  texture.wrapS = texture.wrapT = THREE.RepeatWrapping
  texture.repeat.set(2, 2)
  return new THREE.MeshStandardMaterial({ map: texture, roughness: 1, color: '#f6f7f5' })
}

export const createTonBagSymbol = () => {
  const group = new THREE.Group()
  const body = new THREE.Mesh(new RoundedBoxGeometry(1.2, 1.15, 1.08, 3, 0.2), wovenMaterial())
  body.position.y = 0.69
  body.castShadow = true
  body.receiveShadow = true
  group.add(body)
  const strap = new THREE.MeshStandardMaterial({ color: '#82949a', roughness: 1 })
  for (const x of [-0.39, 0.39]) {
    for (const z of [-0.32, 0.32]) {
      const loop = new THREE.Mesh(new THREE.TorusGeometry(0.15, 0.025, 4, 12, Math.PI), strap)
      loop.position.set(x, 1.25, z)
      loop.castShadow = true
      group.add(loop)
    }
  }
  const neck = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.25, 0.15, 10), strap)
  neck.position.y = 1.35
  group.add(neck)
  const seams = new THREE.MeshStandardMaterial({ color: '#d4ddd8', roughness: 1 })
  for (const x of [-0.47, 0.47]) {
    const seam = new THREE.Mesh(new THREE.BoxGeometry(0.025, 0.75, 0.025), seams)
    seam.position.set(x, 0.68, 0.53)
    group.add(seam)
  }
  const tag = new THREE.Mesh(
    new THREE.BoxGeometry(0.24, 0.19, 0.012),
    new THREE.MeshStandardMaterial({ color: '#f9faf8', roughness: 1 })
  )
  tag.position.set(0.22, 0.78, 0.55)
  group.add(tag)
  return group
}

const ringGeometry = (width: number, depth: number) => {
  const shape = new THREE.Shape()
  shape.moveTo(-width / 2 - 0.08, -depth / 2 - 0.08)
  shape.lineTo(width / 2 + 0.08, -depth / 2 - 0.08)
  shape.lineTo(width / 2 + 0.08, depth / 2 + 0.08)
  shape.lineTo(-width / 2 - 0.08, depth / 2 + 0.08)
  shape.closePath()
  const hole = new THREE.Path()
  hole.moveTo(-width / 2 + 0.04, -depth / 2 + 0.04)
  hole.lineTo(-width / 2 + 0.04, depth / 2 - 0.04)
  hole.lineTo(width / 2 - 0.04, depth / 2 - 0.04)
  hole.lineTo(width / 2 - 0.04, -depth / 2 + 0.04)
  hole.closePath()
  shape.holes.push(hole)
  return new THREE.ExtrudeGeometry(shape, { depth: 0.04, bevelEnabled: false })
}

export const createBinSymbol = (
  bin: Warehouse3DBin,
  selected: boolean,
  highlighted: boolean,
  muted = false
) => {
  const group = new THREE.Group()
  const elevation = bin.center.y || 0
  group.position.set(bin.center.x, elevation, bin.center.z)
  group.userData.locationId = bin.id
  if (elevation > 0) {
    const steel = new THREE.MeshStandardMaterial({
      color: '#526d7c',
      roughness: 0.65,
      metalness: 0.3
    })
    const rack = new THREE.Group()
    rack.name = 'registered-tier-support'
    for (const x of [-bin.size.x / 2 + 0.08, bin.size.x / 2 - 0.08]) {
      for (const z of [-bin.size.z / 2 + 0.08, bin.size.z / 2 - 0.08]) {
        const post = new THREE.Mesh(new THREE.BoxGeometry(0.1, elevation + 0.2, 0.1), steel)
        post.position.set(x, -elevation / 2 + 0.1, z)
        post.castShadow = true
        rack.add(post)
      }
    }
    for (const z of [-bin.size.z / 2, bin.size.z / 2]) {
      const beam = new THREE.Mesh(new THREE.BoxGeometry(bin.size.x, 0.16, 0.12), steel)
      beam.position.set(0, -0.08, z)
      rack.add(beam)
    }
    group.add(rack)
  }
  const pad = new THREE.Mesh(
    new THREE.BoxGeometry(bin.size.x, 0.14, bin.size.z),
    new THREE.MeshStandardMaterial({ color: bin.state.fill, roughness: 0.95 })
  )
  pad.position.y = 0.07
  pad.receiveShadow = true
  group.add(pad)
  const border = new THREE.Mesh(
    ringGeometry(bin.size.x, bin.size.z),
    new THREE.MeshBasicMaterial({
      color: selected ? '#17231d' : highlighted ? '#216fba' : bin.state.stroke
    })
  )
  border.rotation.x = -Math.PI / 2
  border.position.y = 0.15
  group.add(border)
  if (bin.glyph === 'OCCUPIED') {
    const bag = createTonBagSymbol()
    bag.position.z = -0.25
    group.add(bag)
  } else if (bin.glyph === 'UNKNOWN') {
    const unknown = new THREE.Mesh(
      new THREE.OctahedronGeometry(0.22),
      new THREE.MeshStandardMaterial({ color: '#9097a2', roughness: 0.8 })
    )
    unknown.position.y = 0.5
    group.add(unknown)
  }
  if (muted) {
    group.traverse((child) => {
      if (!(child instanceof THREE.Mesh)) return
      const materials = Array.isArray(child.material) ? child.material : [child.material]
      for (const material of materials) {
        material.transparent = true
        material.opacity = 0.16
        material.depthWrite = false
      }
      child.castShadow = false
    })
  }
  return group
}

export const disposeSceneObjects = (object: THREE.Object3D) => {
  const geometries = new Set<THREE.BufferGeometry>()
  const materials = new Set<THREE.Material>()
  const textures = new Set<THREE.Texture>()
  object.traverse((child) => {
    const renderable = child as THREE.Mesh
    if (renderable.geometry) geometries.add(renderable.geometry)
    if (renderable.material) {
      for (const material of Array.isArray(renderable.material)
        ? renderable.material
        : [renderable.material]) {
        materials.add(material)
        for (const value of Object.values(material))
          if (value instanceof THREE.Texture) textures.add(value)
      }
    }
  })
  for (const texture of textures) texture.dispose()
  for (const material of materials) material.dispose()
  for (const geometry of geometries) geometry.dispose()
}
