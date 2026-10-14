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
    './warehouseOverview': evaluateSource(readFileSync(new URL('./warehouseOverview.ts', import.meta.url), 'utf8'), { './spatialLayout': model }),
    './locationLook': evaluateSource(readFileSync(new URL('./locationLook.ts', import.meta.url), 'utf8')),
    './warehouseMapQuery': evaluateSource(readFileSync(new URL('./warehouseMapQuery.ts', import.meta.url), 'utf8'), { './spatialLayout': model, './locationLook': evaluateSource(readFileSync(new URL('./locationLook.ts', import.meta.url), 'utf8')) }),
    './useMapPackageLookup': { useMapPackageLookup: () => ({ raw: vue.ref(''), busy: vue.ref(false), feedback: vue.ref(''), clear() {} }) },
    './LocationDetailsPanel.vue': {}
  }
  const globals = { defineProps: () => props, withDefaults: (p) => p, defineEmits: () => (...e) => events.push(e), defineExpose: () => {} }
  return { ...evaluateSource(source + '\nexport { contextValid, dimension, fallbackToPlane, threeUnavailable, filter, matchingIds, focusLocation, selectLocation, summary, mode, scopedUnplaced, queryExpanded, toggleQuery };', imports, globals), props, events, user }
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
test('nonmatching locations cannot be inspected or selected as plans', async () => {
  const s = setup([location(1), { ...location(2), displayStatus: 'FULL' }])
  s.filter.value = 'EMPTY'
  await vue.nextTick()
  assert.deepEqual([...s.matchingIds.value], [1])
  s.inspect(2)
  s.choosePlan()
  assert.equal(s.activeId.value, undefined)
  assert.equal(s.events.length, 0)
})
test('precise location search switches scope and cancelled focus never changes inspection', async () => {
  const s = setup([location(1), location(2, 'W2')])
  await s.focusLocation(2)
  assert.equal(s.activeId.value, 2)
  assert.equal(s.layout.value.groups.find(g => g.key === s.groupKey.value).warehouseCode, 'W2')
  s.activeId.value = undefined
  let live = true
  const pending = s.focusLocation(1, () => live)
  live = false
  await pending
  assert.equal(s.activeId.value, undefined)
  assert.equal(s.events.length, 0)
})
test('recorded scope includes identity-valid pending locations and preserves unknown totals', async () => {
  const missing = { ...location(2), rowCode: undefined, displayStatus: 'UNKNOWN', occupiedPackages: undefined, occupiedWeight: undefined }
  const s = setup([{ ...location(1), occupiedPackages: 0, occupiedWeight: 0 }, missing])
  s.mode.value = 'recorded'
  await vue.nextTick()
  assert.equal(s.summary.value.total, 2)
  assert.equal(s.summary.value.packages, undefined)
  s.inspect(2)
  assert.equal(s.activeId.value, 2)
  s.filter.value = 'UNKNOWN'
  await vue.nextTick()
  assert.deepEqual([...s.matchingIds.value], [2])
  assert.equal(s.layout.value.groups[0].nodes.length, 1, 'pending records receive no invented coordinates')
})
test('pending-list scope shares the layout normalization of legacy warehouse metadata', async () => {
  const s = setup([location(1), { ...location(2, ' W1 '), rowCode: undefined }])
  s.mode.value = 'recorded'
  await vue.nextTick()
  assert.equal(s.summary.value.total, 2)
  assert.deepEqual([...s.matchingIds.value], [1, 2])
  assert.equal(s.scopedUnplaced.value.length, 1)
  s.inspect(2)
  assert.equal(s.activeId.value, 2)
})

test('primary overview is opt-in 3D and retains all actual areas without changing IDs', async () => {
  const s = setup([location(1), location(2, 'W2')], { overview: true, initialDimension: '3d', canSelect: false })
  assert.equal(s.dimension.value, '3d')
  assert.equal(s.summary.value.total, 2)
  assert.deepEqual([...s.matchingIds.value], [1, 2])
  s.inspect(2)
  s.choosePlan()
  assert.equal(s.events.filter(e => e[0] === 'select').length, 0)
  assert.equal(setup([location(1)]).dimension.value, '2d', 'original workbench remains 2D')
})

test('overview refresh retains only a valid visible selection and rejects a removed ID', async () => {
  const s = setup([location(1), location(2)], { overview: true, retainSelectionOnRefresh: true, snapshotVersion: 1 })
  s.inspect(1)
  s.props.locations = [location(1), location(2)]
  s.props.snapshotVersion++
  await vue.nextTick()
  assert.equal(s.activeId.value, 1)
  s.props.locations = [location(2)]
  s.props.snapshotVersion++
  await vue.nextTick()
  assert.equal(s.activeId.value, undefined)
  assert.match(component, /:key="`\$\{activeLocation.id\}:\$\{snapshotVersion \|\| 0\}`"/, 'every accepted read remounts details rather than retaining stale rows')
})

test('primary showcase starts with a compact toolbar while original map keeps all query controls', () => {
  const showcase = setup([location(1)], { showcase: true })
  assert.equal(showcase.queryExpanded.value, false)
  showcase.toggleQuery()
  assert.equal(showcase.queryExpanded.value, true)
  assert.equal(setup([location(1)]).queryExpanded.value, true)
})
