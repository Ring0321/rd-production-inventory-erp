import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { evaluateSource, deferred } from '../../trace/package/testHelpers.mjs'
const source = readFileSync(new URL('./useWarehouseSpace.ts', import.meta.url), 'utf8')
const access = evaluateSource(readFileSync(new URL('./warehouseSpaceAccess.ts', import.meta.url), 'utf8'))
const setup = (read, permission = true) => {
  const hooks = {}
  let authCallback, revision = 0, allowed = permission
  const user = vue.reactive({ getUser: { id: 1 }, getRoles: [], getPermissions: new Set(permission ? ['rd:putaway-task:query'] : []) })
  let run
  const { useWarehouseSpace } = evaluateSource(source, {
    vue: { ...vue, ...Object.fromEntries(['onMounted', 'onActivated', 'onDeactivated', 'onBeforeUnmount'].map(name => [name, callback => { hooks[name] = callback }])) },
    '@/store/modules/user': { useUserStoreWithOut: () => user },
    '@/utils/auth': { getAccessToken: () => true, getAuthContextRevision: () => revision, onAuthContextChange: cb => { authCallback = cb; return () => {} } },
    '@/directives/permission/hasPermi': { hasPermission: () => allowed },
    './warehouseSpaceAccess': { canViewWarehouseSpace: (roles, permissions) => allowed && access.canViewWarehouseSpace(roles, permissions) },
    '@/api/rd/stock/putaway': { RdPutawayTaskApi: { getLocationMap: read } },
    '../useStockRefresh': { useStockRefresh: (canRun, load) => { run = async () => canRun() ? load(() => canRun()) : false; return { error: vue.ref(''), run } } }
  })
  const state = useWarehouseSpace()
  return { state, hooks, user, run: () => run(), invalidate: () => { revision++; authCallback() }, revoke: () => { allowed = false; user.getPermissions = new Set() } }
}

test('same snapshot preserves scene identity but increments details revision on every accepted read', async () => {
  const s = setup(async () => [{ id: 1, warehouseCode: 'W1', locationCode: 'L1' }])
  await s.run()
  const first = s.state.locations.value
  await s.run()
  assert.equal(s.state.locations.value, first)
  assert.equal(s.state.snapshotVersion.value, 2)
  assert.ok(s.state.updatedAt.value)
})
test('read without warehouse permission performs no request', async () => {
  let calls = 0
  const s = setup(async () => { calls++; return [] }, false)
  await s.run()
  assert.equal(calls, 0)
  assert.equal(s.state.updatedAt.value, undefined)
})
test('auth switch rejects a late snapshot and permanently clears previous owner data', async () => {
  const pending = deferred()
  const s = setup(() => pending.promise)
  const request = s.run()
  s.invalidate()
  pending.resolve([{ id: 1 }])
  await request
  assert.equal(s.state.locations.value.length, 0)
  assert.equal(s.state.updatedAt.value, undefined)
  assert.equal(s.state.contextValid.value, false)
  assert.equal(s.state.busy.value, false)
})
test('permission revocation removes data immediately and never applies a late response', async () => {
  const pending = deferred()
  const s = setup(() => pending.promise)
  const request = s.run()
  s.revoke()
  pending.resolve([{ id: 1 }])
  await request
  assert.equal(s.state.locations.value.length, 0)
  assert.equal(s.state.contextValid.value, false)
})
test('deactivated page releases its snapshot and rejects delayed reads', async () => {
  const pending = deferred()
  const s = setup(() => pending.promise)
  const request = s.run()
  s.hooks.onDeactivated()
  pending.resolve([{ id: 1 }])
  await request
  assert.equal(s.state.locations.value.length, 0)
  assert.equal(s.state.updatedAt.value, undefined)
})
test('invalid payload and request failure cannot advertise a successful refresh', async () => {
  const s = setup(async () => ({ list: [] }))
  await assert.rejects(s.run())
  assert.equal(s.state.updatedAt.value, undefined)
  assert.equal(s.state.busy.value, false)
})
