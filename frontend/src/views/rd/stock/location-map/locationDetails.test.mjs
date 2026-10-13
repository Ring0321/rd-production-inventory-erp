import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { evaluateSource, deferred } from '../../trace/package/testHelpers.mjs'

const source = readFileSync(new URL('./useLocationDetails.ts', import.meta.url), 'utf8')
const setup = (stock, tasks = async () => ({ list: [], total: 0 })) => {
  const callbacks = { onDeactivated: [], onActivated: [], onBeforeUnmount: [] }
  let revision = 0
  let listener
  let permission = true
  const user = vue.reactive({ getUser: { id: 1 }, getPermissions: new Set(['rd:stock-lot:query', 'rd:putaway-task:query']) })
  const imports = {
    vue: {
      ...vue,
      ...Object.fromEntries(Object.keys(callbacks).map((k) => [k, (cb) => callbacks[k].push(cb)]))
    },
    '@/utils/auth': {
      getAccessToken: () => true,
      getAuthContextRevision: () => revision,
      onAuthContextChange: (cb) => {
        listener = cb
        return () => {
          listener = undefined
        }
      }
    },
    '@/directives/permission/hasPermi': { hasPermission: () => permission },
    '@/store/modules/user': { useUserStoreWithOut: () => user },
    '@/api/rd/stock/lot': { RdStockLotApi: { getPage: stock } },
    '@/api/rd/stock/putaway': { RdPutawayTaskApi: { getPage: tasks } }
  }
  const hook = evaluateSource(source, imports).useLocationDetails()
  return {
    hook,
    callbacks,
    losePermission: () => {
      permission = false
      user.getPermissions = new Set()
    },
    restorePermission: () => {
      permission = true
      user.getPermissions = new Set(['rd:stock-lot:query', 'rd:putaway-task:query'])
    },
    switchStoreIdentity: () => { user.getUser.id = 2 },
    switchIdentity: () => {
      revision++
      listener?.()
    },
    hasListener: () => !!listener
  }
}
test('stock and tasks query exact location identity and stay read-only', async () => {
  const calls = []
  const s = setup(
    async (p) => {
      calls.push(p)
      return { list: [{ id: 1, locationId: 7 }], total: 1 }
    },
    async (p) => {
      calls.push(p)
      return { list: [{ id: 8, plannedLocationId: 7 }], total: 1 }
    }
  )
  await s.hook.load(7)
  assert.equal(calls.length, 2)
  assert.equal(calls[0].locationId, 7)
  assert.equal(calls[1].plannedLocationId, 7)
  assert.equal(s.hook.stock.value.list.length, 1)
})
test('late details cannot overwrite another selected bin', async () => {
  const old = deferred()
  const s = setup((p) =>
    p.locationId === 1
      ? old.promise
      : Promise.resolve({ list: [{ id: 2, locationId: 2 }], total: 1 })
  )
  const reading = s.hook.load(1)
  await s.hook.load(2)
  old.resolve({ list: [{ id: 1, locationId: 1 }], total: 1 })
  await reading
  assert.equal(s.hook.stock.value.list[0].locationId, 2)
})
test('auth epoch changes clear data and reject even a late response under restored permissions', async () => {
  const pending = deferred()
  const s = setup(() => pending.promise)
  const reading = s.hook.load(1)
  s.switchIdentity()
  pending.resolve({ list: [{ id: 1, locationId: 1 }], total: 1 })
  await reading
  assert.equal(s.hook.stock.value, undefined)
  assert.equal(s.hook.busy.value, false)
})
test('wrong bin response fails closed, preserving no misleading inventory table', async () => {
  const s = setup(async () => ({ list: [{ id: 1, locationId: 999 }], total: 1 }))
  await s.hook.load(1)
  assert.equal(s.hook.stock.value, undefined)
  assert.ok(s.hook.stockError.value)
})
test('permission denial and page lifecycle prevent requests and clear existing detail', async () => {
  let count = 0
  const s = setup(async () => {
    count++
    return { list: [], total: 0 }
  })
  s.losePermission()
  await s.hook.load(1)
  assert.equal(count, 0)
  for (const cb of s.callbacks.onBeforeUnmount) cb()
  assert.equal(s.hasListener(), false)
})
test('permission revocation synchronously clears visible rows and cannot revive a revoked late read', async () => {
  const pending = deferred()
  let next = false
  const s = setup(() => next ? pending.promise : Promise.resolve({ list: [{ id: 1, locationId: 1 }], total: 1 }), async () => ({ list: [{ id: 2, plannedLocationId: 1 }], total: 1 }))
  await s.hook.load(1)
  s.losePermission()
  assert.equal(s.hook.stock.value, undefined)
  assert.equal(s.hook.tasks.value, undefined)
  s.restorePermission()
  next = true
  const reading = s.hook.load(1)
  s.losePermission()
  s.restorePermission()
  pending.resolve({ list: [{ id: 1, locationId: 1 }], total: 1 })
  await reading
  assert.equal(s.hook.stock.value, undefined)
})
test('store account changes invalidate visible rows even without a token epoch event', async () => {
  const s = setup(async () => ({ list: [{ id: 1, locationId: 1 }], total: 1 }))
  await s.hook.load(1)
  s.switchStoreIdentity()
  assert.equal(s.hook.stock.value, undefined)
  await s.hook.load(1)
  assert.equal(s.hook.stock.value, undefined)
})
