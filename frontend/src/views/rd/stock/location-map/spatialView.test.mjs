import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'

const model = evaluateSource(readFileSync(new URL('./spatialLayout.ts', import.meta.url), 'utf8'))
const component = readFileSync(new URL('./RdSpatialLocationMap.vue', import.meta.url), 'utf8')
const source = component.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
  .replace('import.meta.env.BASE_URL', "'/'") + '\nexport { groupKey, activeId, inspect, choosePlan, layout };'
const setup = (locations, extra = {}) => {
  const props = vue.reactive({ locations, highlightedIds: [], highlightedCodes: [], canSelect: true, isSelectable: () => true, ...extra })
  const events = []
  const user = vue.reactive({ getUser: { id: 1 }, getPermissions: new Set(['rd:putaway-task:create', 'rd:putaway-task:query']) })
  const hooks = { onMounted: () => {}, onActivated: () => {}, onDeactivated: () => {}, onBeforeUnmount: () => {} }
  const imports = {
    vue: { ...vue, ...hooks },
    'leaflet/dist/leaflet.css': {},
    '@/directives/permission/hasPermi': { hasPermission: () => true },
    '@/utils/auth': { getAccessToken: () => true, getAuthContextRevision: () => 0, onAuthContextChange: () => () => {} },
    '@/store/modules/user': { useUserStoreWithOut: () => user },
    './spatialLayout': model,
    './locationLook': evaluateSource(readFileSync(new URL('./locationLook.ts', import.meta.url), 'utf8')),
    './LocationDetailsPanel.vue': {}
  }
  const globals = { defineProps: () => props, withDefaults: (p) => p, defineEmits: () => (...e) => events.push(e) }
  return { ...evaluateSource(source + '\nexport { contextValid, dimension, fallbackToPlane, threeUnavailable };', imports, globals), props, events, user }
}
const location = (id, warehouseCode = 'W1') => ({ id, warehouseCode, warehouseName: warehouseCode, locationCode: `L-${id}`, rowCode: 'R1', columnCode: `C${id}`, enabled: true, frozen: false, displayStatus: 'EMPTY' })

test('inspecting a manually selected group does not jump back to a task highlight', async () => {
  const s = setup([location(1), location(2, 'W2')], { highlightedIds: [1] })
  const second = s.layout.value.groups.find(g => g.warehouseCode === 'W2').key
  s.groupKey.value = second
  await vue.nextTick()
  s.inspect(2)
  await vue.nextTick()
  await vue.nextTick()
  assert.equal(s.groupKey.value, second)
  assert.equal(s.activeId.value, 2)
})
test('identity-invalid fallback cannot emit a planned location', () => {
  const s = setup([location(1, '')])
  s.inspect(1)
  s.choosePlan()
  assert.equal(s.events.length, 0)
})
test('permission changes invalidate the visible map context immediately', () => {
  const s = setup([location(1)])
  s.inspect(1)
  s.user.getPermissions = new Set()
  assert.equal(s.contextValid.value, false)
  assert.equal(s.activeId.value, undefined)
  s.choosePlan()
  assert.equal(s.events.length, 0)
})
test('3D unavailable fallback preserves inspection identity and never emits a plan change', () => {
  const s = setup([location(1)])
  s.inspect(1)
  s.dimension.value = '3d'
  s.fallbackToPlane()
  assert.equal(s.dimension.value, '2d')
  assert.equal(s.threeUnavailable.value, true)
  assert.equal(s.activeId.value, 1)
  assert.equal(s.events.length, 0)
})
