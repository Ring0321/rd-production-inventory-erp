import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluateSource, deferred, plain } from '../../trace/package/testHelpers.mjs'

const setup = () => {
  const source = readFileSync(new URL('./RdSpatialLocation3D.vue', import.meta.url), 'utf8')
    .match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
    .replace("await import('./warehouseScene')", 'await testLoadScene()')
  const loader = deferred(),
    hooks = {},
    fits = []
  let expose,
    created = 0,
    synced = 0
  const model = evaluateSource(
    source + '\nexport const testHost = host',
    {
      vue: {
        ...vue,
        watch: (_, callback) => {
          hooks.sync = callback
        },
        onMounted: (fn) => {
          hooks.mount = fn
        },
        onActivated: (fn) => {
          hooks.activate = fn
        },
        onDeactivated: (fn) => {
          hooks.deactivate = fn
        },
        onBeforeUnmount: (fn) => {
          hooks.unmount = fn
        }
      },
      './warehouse3DModel': { buildWarehouse3D: () => ({ bins: [{ id: 7 }, { id: 8 }] }) }
    },
    {
      defineProps: () => ({ nodes: [], highlightedIds: [], matchingIds: [] }),
      withDefaults: (props) => props,
      defineEmits: () => () => {},
      testLoadScene: () => loader.promise,
      defineExpose: (value) => {
        expose = value
      }
    }
  )
  model.testHost.value = {}
  return {
    expose,
    hooks,
    fits,
    created: () => created,
    synced: () => synced,
    ready: () =>
      loader.resolve({
        createWarehouseScene: () => {
          created++
          return {
            setModel: () => {
              synced++
            },
            fit: (ids) => fits.push(plain(ids || [])),
            dispose: () => {}
          }
        }
      })
  }
}
test('a location requested before the async 3D chunk loads is focused once after scene and model initialization', async () => {
  const h = setup()
  const starting = h.hooks.mount()
  h.expose.focus([7])
  assert.equal(h.created(), 0)
  h.ready()
  await starting
  assert.deepEqual(h.fits, [[7]])
  assert.equal(h.synced(), 1)
  h.hooks.sync()
  assert.equal(h.fits.length, 1, 'normal refresh must not reset a user-controlled camera')
})
test('invalid IDs do not focus and deactivation rejects a late scene import and its old queued request', async () => {
  const h = setup()
  const starting = h.hooks.mount()
  h.expose.focus([99])
  h.expose.focus([7])
  h.hooks.deactivate()
  h.ready()
  await starting
  assert.equal(h.created(), 0)
  assert.equal(h.fits.length, 0)
})
