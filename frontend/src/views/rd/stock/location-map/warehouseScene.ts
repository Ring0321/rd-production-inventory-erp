import * as THREE from 'three'
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js'
import { createBinSymbol, disposeSceneObjects } from './warehouse3DAssets'
import type { Warehouse3DBin } from './warehouse3DModel'
import { createSceneTap } from './sceneTap'

export interface ProjectedBin {
  id: number
  x: number
  y: number
  visible: boolean
  compact: boolean
}

export const createWarehouseScene = (
  host: HTMLElement,
  callbacks: {
    select: (id: number) => void
    frame: (labels: ProjectedBin[]) => void
    unavailable: () => void
  }
) => {
  const owned: {
    renderer?: THREE.WebGLRenderer
    canvas?: HTMLCanvasElement
    scene?: THREE.Scene
    controls?: OrbitControls
    light?: THREE.DirectionalLight
    observer?: ResizeObserver
  } = {}
  const listeners: Array<() => void> = []
  let disposed = false
  const dispose = () => {
    if (disposed) return
    disposed = true
    owned.observer?.disconnect()
    for (const remove of listeners) remove()
    owned.controls?.dispose()
    if (owned.scene) disposeSceneObjects(owned.scene)
    owned.light?.shadow.dispose()
    owned.renderer?.dispose()
    if (owned.renderer && !owned.renderer.getContext().isContextLost())
      owned.renderer.forceContextLoss()
    owned.canvas?.remove()
  }
  try {
    const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true })
    owned.renderer = renderer
    const canvas = renderer.domElement
    owned.canvas = canvas
    canvas.setAttribute('aria-label', '立体库位示意画布')
    canvas.tabIndex = 0
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.5))
    renderer.setClearColor('#f8faf9')
    renderer.shadowMap.enabled = true
    renderer.shadowMap.type = THREE.PCFSoftShadowMap
    host.appendChild(canvas)
    const scene = new THREE.Scene()
    owned.scene = scene
    const camera = new THREE.PerspectiveCamera(36, 1, 0.1, 200)
    camera.position.set(8, 10, 12)
    const controls = new OrbitControls(camera)
    owned.controls = controls
    controls.connect(canvas)
    controls.enableDamping = false
    controls.maxPolarAngle = Math.PI / 2 - 0.08
    controls.minDistance = 2
    controls.maxDistance = 100
    controls.listenToKeyEvents(canvas)
    scene.add(new THREE.HemisphereLight('#ffffff', '#aebcb2', 2.6))
    const light = new THREE.DirectionalLight('#ffffff', 3)
    owned.light = light
    light.position.set(-8, 16, 10)
    light.castShadow = true
    light.shadow.mapSize.set(1024, 1024)
    light.shadow.camera.left = light.shadow.camera.bottom = -30
    light.shadow.camera.right = light.shadow.camera.top = 30
    light.shadow.bias = -0.001
    scene.add(light)
    let objects = new THREE.Group()
    scene.add(objects)
    let bins: Warehouse3DBin[] = []
    let width = 0
    let height = 0
    const tap = createSceneTap()
    const raycaster = new THREE.Raycaster()
    const render = () => {
      if (disposed || width <= 0 || height <= 0) return
      renderer.render(scene, camera)
      canvas.dataset.ready = 'true'
      const projections = bins.map((bin) => {
        const anchor = new THREE.Vector3(
          bin.center.x,
          0.2,
          bin.center.z + bin.size.z / 2 - 0.1
        ).project(camera)
        const left = new THREE.Vector3(bin.center.x - bin.size.x / 2, 0.2, bin.center.z).project(
          camera
        )
        const right = new THREE.Vector3(bin.center.x + bin.size.x / 2, 0.2, bin.center.z).project(
          camera
        )
        const screenWidth = Math.hypot(
          ((right.x - left.x) * width) / 2,
          ((right.y - left.y) * height) / 2
        )
        return {
          id: bin.id,
          x: ((anchor.x + 1) * width) / 2,
          y: ((1 - anchor.y) * height) / 2,
          visible:
            anchor.z >= -1 &&
            anchor.z <= 1 &&
            Math.abs(anchor.x) < 0.94 &&
            Math.abs(anchor.y) < 0.94 &&
            screenWidth >= 60,
          compact: screenWidth < 155
        }
      })
      const visible: ProjectedBin[] = []
      for (const projection of projections) {
        if (
          projection.visible &&
          visible.some(
            (other) =>
              Math.abs(other.x - projection.x) < 115 && Math.abs(other.y - projection.y) < 44
          )
        )
          projection.visible = false
        if (projection.visible) visible.push(projection)
      }
      callbacks.frame(projections)
    }
    const boxFor = (targetBins: Warehouse3DBin[]) => {
      const box = new THREE.Box3()
      for (const bin of targetBins) {
        box.expandByPoint(
          new THREE.Vector3(
            bin.center.x - bin.size.x / 2 - 0.4,
            0,
            bin.center.z - bin.size.z / 2 - 0.4
          )
        )
        box.expandByPoint(
          new THREE.Vector3(
            bin.center.x + bin.size.x / 2 + 0.4,
            bin.height,
            bin.center.z + bin.size.z / 2 + 0.4
          )
        )
      }
      return box
    }
    const fit = (ids?: number[], view?: 'iso' | 'top') => {
      if (disposed || !bins.length || width <= 0 || height <= 0) return
      const chosen = ids?.length ? bins.filter((bin) => ids.includes(bin.id)) : bins
      if (!chosen.length) return
      const sphere = boxFor(chosen).getBoundingSphere(new THREE.Sphere())
      const direction =
        view === 'iso'
          ? new THREE.Vector3(0.85, 1.1, 1.3)
          : view === 'top'
            ? new THREE.Vector3(0.001, 1, 0.001)
            : camera.position.clone().sub(controls.target)
      if (!direction.lengthSq()) direction.set(0.85, 1.1, 1.3)
      const angle = THREE.MathUtils.degToRad(camera.fov)
      const horizontalAngle = 2 * Math.atan(Math.tan(angle / 2) * camera.aspect)
      const distance = (sphere.radius / Math.sin(Math.min(angle, horizontalAngle) / 2)) * 1.18
      camera.position.copy(sphere.center).add(direction.normalize().multiplyScalar(distance))
      camera.near = 0.1
      camera.far = Math.max(200, distance + sphere.radius * 6)
      camera.updateProjectionMatrix()
      controls.target.copy(sphere.center)
      controls.minDistance = Math.max(1, sphere.radius * 0.8)
      controls.maxDistance = Math.max(50, distance * 5)
      controls.update()
      render()
    }
    const resize = () => {
      if (disposed) return
      const nextWidth = host.clientWidth
      const nextHeight = host.clientHeight
      if (!nextWidth || !nextHeight || (nextWidth === width && nextHeight === height)) return
      width = nextWidth
      height = nextHeight
      renderer.setSize(width, height, false)
      camera.aspect = width / height
      camera.updateProjectionMatrix()
      fit()
      render()
    }
    const select = (event: PointerEvent) => {
      if (disposed || !tap.end(event)) return
      const rect = canvas.getBoundingClientRect()
      const position = new THREE.Vector2(
        ((event.clientX - rect.left) / rect.width) * 2 - 1,
        (-(event.clientY - rect.top) / rect.height) * 2 + 1
      )
      raycaster.setFromCamera(position, camera)
      const hit = raycaster.intersectObjects(objects.children, true)[0]
      let object: THREE.Object3D | null | undefined = hit?.object
      while (object && !object.userData.locationId) object = object.parent
      const id = object?.userData.locationId
      if (Number.isSafeInteger(id) && bins.some((bin) => bin.id === id)) callbacks.select(id)
    }
    const start = (event: PointerEvent) => {
      tap.start(event)
    }
    const move = (event: PointerEvent) => {
      tap.move(event)
    }
    const cancel = (event: PointerEvent) => {
      tap.cancel(event)
    }
    const lost = (event: Event) => {
      event.preventDefault()
      if (!disposed) callbacks.unavailable()
    }
    canvas.addEventListener('pointerdown', start)
    listeners.push(() => canvas.removeEventListener('pointerdown', start))
    canvas.addEventListener('pointermove', move)
    listeners.push(() => canvas.removeEventListener('pointermove', move))
    canvas.addEventListener('pointerup', select)
    listeners.push(() => canvas.removeEventListener('pointerup', select))
    canvas.addEventListener('pointercancel', cancel)
    listeners.push(() => canvas.removeEventListener('pointercancel', cancel))
    canvas.addEventListener('webglcontextlost', lost)
    listeners.push(() => canvas.removeEventListener('webglcontextlost', lost))
    controls.addEventListener('change', render)
    listeners.push(() => controls.removeEventListener('change', render))
    const observer = new ResizeObserver(resize)
    owned.observer = observer
    observer.observe(host)
    resize()
    return {
      setModel(
        next: Warehouse3DBin[],
        selectedId?: number,
        highlightedIds: number[] = [],
        reframe = false
      ) {
        if (disposed) return
        const shapeKey = (values: Warehouse3DBin[]) =>
          JSON.stringify(
            values.map((bin) => [
              bin.id,
              bin.center.x,
              bin.center.z,
              bin.size.x,
              bin.size.z,
              bin.height
            ])
          )
        const shapeChanged = shapeKey(next) !== shapeKey(bins)
        bins = next
        disposeSceneObjects(objects)
        scene.remove(objects)
        objects = new THREE.Group()
        scene.add(objects)
        for (const bin of bins)
          objects.add(createBinSymbol(bin, bin.id === selectedId, highlightedIds.includes(bin.id)))
        if (bins.length) {
          const bounds = boxFor(bins)
          const size = bounds.getSize(new THREE.Vector3())
          const center = bounds.getCenter(new THREE.Vector3())
          const floor = new THREE.Mesh(
            new THREE.PlaneGeometry(size.x + 2, size.z + 2),
            new THREE.MeshStandardMaterial({ color: '#f0f3f1', roughness: 1 })
          )
          floor.rotation.x = -Math.PI / 2
          floor.position.set(center.x, -0.02, center.z)
          floor.receiveShadow = true
          objects.add(floor)
          const grid = new THREE.GridHelper(Math.max(size.x, size.z) + 2, 12, '#d0d9d3', '#e0e7e2')
          grid.position.set(center.x, -0.005, center.z)
          objects.add(grid)
        }
        if (shapeChanged || reframe) fit(undefined, 'iso')
        render()
      },
      fit,
      zoom(factor: number) {
        if (disposed) return
        if (factor < 1) controls.dollyIn(factor)
        else controls.dollyOut(1 / factor)
        render()
      },
      dispose
    }
  } catch (error) {
    dispose()
    throw error
  }
}

export type WarehouseScene = ReturnType<typeof createWarehouseScene>
