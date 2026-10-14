import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluate, plain } from '../../pda/warehouse/testHelpers.mjs'

const model = evaluate(new URL('./scanCandidates.ts', import.meta.url), {}, { URL, Error })
const snapshot = () => ({
  kind: 'loading',
  taskId: 7,
  taskNo: 'LT-7',
  version: 3,
  status: 'ASSIGNED',
  lines: [
    { lineId: 1, packageCode: 'LD-001', packageWeight: 1000, eligible: true },
    { lineId: 2, packageCode: 'LD-002', packageWeight: 800, eligible: true }
  ]
})
const setup = (read, existing = []) => {
  const data = new Map(existing.map((row) => [row.packageCode, structuredClone(row)]))
  const hooks = {}
  const state = vue.reactive({
    scope: { ownerId: 5, tenant: '1', kind: 'loading', taskId: 7 },
    snapshot: snapshot(),
    current: true,
    locked: false
  })
  let calls = 0
  let savingFails = false
  const box = {
    list: async () => structuredClone([...data.values()]),
    collect: async (scope, version, code, guard, tag, reason) => {
      if (savingFails) throw new Error('本机配额不足')
      assert.ok(guard())
      const row = data.get(code) || {
        ...plain(scope),
        id: code,
        packageCode: code,
        observedVersion: version,
        seenAt: 1,
        state: tag,
        reason
      }
      data.set(code, row)
      return row
    },
    mark: async (scope, patches, guard) => {
      assert.ok(guard())
      for (const patch of patches) Object.assign(data.get(patch.packageCode), patch)
    },
    remove: async (scope, code, guard) => {
      assert.ok(guard())
      data.delete(code)
    },
    close: () => {}
  }
  const m = evaluate(
    new URL('./useScanWorkbench.ts', import.meta.url),
    {
      vue: {
        ...vue,
        onBeforeUnmount: (fn) => {
          hooks.unmount = fn
        },
        onDeactivated: (fn) => {
          hooks.deactivate = fn
        },
        onActivated: (fn) => {
          hooks.activate = fn
        }
      },
      './pendingScanBox': { createPendingScanBox: () => box },
      './scanCandidates': model
    },
    { Error }
  )
  const work = m.useScanWorkbench(
    {
      snapshot: () => state.snapshot,
      scope: () => state.scope,
      current: () => state.current,
      locked: () => state.locked,
      readSnapshot: async () => {
        calls++
        return read ? read() : plain(state.snapshot)
      }
    },
    box
  )
  return {
    state,
    work,
    data,
    hooks,
    calls: () => calls,
    failSaving: () => {
      savingFails = true
    }
  }
}
test('capture performs zero server calls, deduplicates, classifies wrong bags and keeps local state', async () => {
  const h = setup()
  await h.work.collect(['LD-001', 'LD-001', 'WRONG', 'LD-002'])
  assert.equal(h.calls(), 0)
  assert.equal(h.data.size, 3)
  assert.equal(h.data.get('WRONG').state, 'CONFLICT')
  assert.equal(h.data.get('LD-002').state, 'LOCAL')
  assert.equal(h.work.weight.value, 1800)
  assert.equal(h.work.canTransfer.value, false)
})
test('server recheck and explicit transfer preserve only chosen members and original task/version', async () => {
  const h = setup()
  await h.work.collect(['LD-001', 'LD-002'])
  await h.work.check()
  h.work.selected.value = ['LD-002']
  assert.equal(h.work.canTransfer.value, true)
  const value = await h.work.transfer()
  assert.deepEqual(plain(value), { taskId: 7, version: 3, packageCodes: ['LD-002'], lineIds: [2] })
  assert.equal(h.calls(), 2)
})
test('network loss keeps local candidates without manufacturing verification or transfer', async () => {
  const h = setup(async () => {
    throw new Error('network offline')
  })
  await h.work.collect(['LD-001'])
  assert.equal(await h.work.check(), false)
  assert.equal(h.data.get('LD-001').state, 'LOCAL')
  assert.equal(h.work.canTransfer.value, false)
  assert.equal(await h.work.transfer(), undefined)
})
test('unchanged repeated camera frames retain the verified chosen subset, while a new candidate invalidates it', async () => {
  const h = setup()
  await h.work.collect(['LD-001'])
  await h.work.check()
  await h.work.collect(['LD-001', 'LD-001', 'not a code'])
  assert.deepEqual(plain(h.work.selected.value), ['LD-001'])
  assert.equal(h.work.canTransfer.value, true)
  await h.work.collect(['LD-002'])
  assert.equal(h.work.verified.value.length, 0)
  assert.equal(h.work.canTransfer.value, false)
})
test('stored verified tags cannot authorize a transfer after reload without a new server check', async () => {
  const h = setup(undefined, [
    {
      ownerId: 5,
      tenant: '1',
      kind: 'loading',
      taskId: 7,
      id: 'a',
      packageCode: 'LD-001',
      observedVersion: 3,
      seenAt: 1,
      state: 'VERIFIED'
    }
  ])
  await h.work.load()
  h.work.selected.value = ['LD-001']
  assert.equal(h.work.canTransfer.value, false)
})
test('version-changed and processed candidates become conflicts, not new-version commands', async () => {
  const newer = snapshot()
  newer.version = 4
  const h = setup(() => newer)
  await h.work.collect(['LD-001'])
  await h.work.check()
  assert.equal(h.data.get('LD-001').state, 'CONFLICT')
  assert.equal(h.data.get('LD-001').observedVersion, 3)
  assert.equal(h.work.canTransfer.value, false)
})
test('late replies after task or user changes never populate the next scope', async () => {
  let release
  const h = setup(
    () =>
      new Promise((r) => {
        release = r
      })
  )
  await h.work.collect(['LD-001'])
  const wait = h.work.check()
  await Promise.resolve()
  await Promise.resolve()
  h.state.current = false
  await vue.nextTick()
  release(snapshot())
  await wait
  assert.equal(h.work.verified.value.length, 0)
  assert.equal(h.work.canTransfer.value, false)
})
test('local storage failure is visible and blocks transfer; original pending write lock blocks capture', async () => {
  const h = setup()
  h.failSaving()
  await h.work.collect(['LD-001'])
  assert.match(h.work.error.value, /配额/)
  assert.equal(h.data.size, 0)
  h.state.locked = true
  await h.work.collect(['LD-002'])
  assert.equal(h.data.size, 0)
})
test('deactivation stops processing and reactivation loads candidates but invalidates previous verification', async () => {
  const h = setup()
  await h.work.collect(['LD-001'])
  await h.work.check()
  h.hooks.deactivate()
  assert.equal(h.work.canTransfer.value, false)
  await h.work.collect(['LD-002'])
  assert.equal(h.data.size, 1)
  h.hooks.activate()
  await h.work.load()
  assert.equal(h.work.rows.value.length, 1)
  assert.equal(h.work.canTransfer.value, false)
})
