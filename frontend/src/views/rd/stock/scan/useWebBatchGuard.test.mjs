import assert from 'node:assert/strict'
import { test } from 'node:test'
import { ref, shallowRef, computed } from 'vue'
import { evaluate, plain } from '../../pda/warehouse/testHelpers.mjs'

const original = (kind = 'loading') => ({
  kind,
  ownerId: 5,
  tenant: '1',
  uncertain: false,
  request: {
    ...(kind === 'loading'
      ? { taskId: 7, representativePackageCode: 'LD-1' }
      : { id: 7, packageCode: 'LD-1' }),
    version: 3,
    idempotencyKey: 'original-key',
    taskLineIds: [1, 2]
  }
})
const setup = (kind = 'loading') => {
  const storage = new Map(),
    sent = []
  let active = true,
    allowed = true,
    confirm,
    state = 'UNKNOWN',
    prompt = 0
  const localStorage = {
    getItem: (key) => storage.get(key) || null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key)
  }
  const vue = { ref, shallowRef, computed }
  const pendingModel = evaluate(new URL('../../pda/warehouse/batchPending.ts', import.meta.url))
  const store = evaluate(
    new URL('../../pda/warehouse/useBatchPending.ts', import.meta.url),
    {
      vue,
      './batchPending': pendingModel
    },
    { localStorage, Error }
  )
  const statusModel = evaluate(
    new URL('../../pda/warehouse/reconcileStatus.ts', import.meta.url),
    {},
    { Error }
  )
  const m = evaluate(
    new URL('./useWebBatchGuard.ts', import.meta.url),
    {
      vue,
      '../../pda/warehouse/useBatchPending': store,
      '../../pda/warehouse/reconcileStatus': statusModel
    },
    { Error }
  )
  const session = {
    ownerId: 5,
    tenant: '1',
    current: () => active,
    allowed: () => active && allowed,
    assertCurrent: () => {
      if (!active) throw new Error('identity changed')
    }
  }
  const reply = () => ({
    task: { id: 7, version: 4 },
    recordId: 77,
    confirmedCount: 2,
    success: true
  })
  const port = {
    confirm: async (value) => {
      sent.push(plain(value))
      return confirm ? confirm() : reply()
    },
    status: async () => ({ taskId: 7, requestVersion: 3, currentVersion: 4, state, recordId: 77 }),
    read: async () => reply(),
    confirmStale: async () => {
      prompt++
    }
  }
  const create = () => m.useWebBatchGuard(session, kind, port)
  return {
    guard: create(),
    create,
    storage,
    sent,
    setConfirm: (fn) => {
      confirm = fn
    },
    setState: (value) => {
      state = value
    },
    inactive: () => {
      active = false
    },
    deny: () => {
      allowed = false
    },
    prompts: () => prompt
  }
}
test('lost response persists the exact request and any retry reuses its original key and member IDs', async () => {
  const h = setup()
  h.setConfirm(() => {
    throw new Error('response lost')
  })
  await h.guard.submit(original())
  assert.equal(h.guard.locked.value, true)
  assert.equal(h.guard.pending.value.uncertain, true)
  h.setConfirm(() => ({
    task: { id: 7, version: 4 },
    recordId: 77,
    confirmedCount: 2,
    success: true
  }))
  await h.guard.submit({
    ...original(),
    request: { ...original().request, idempotencyKey: 'different', taskLineIds: [3] }
  })
  assert.deepEqual(h.sent[1].request, h.sent[0].request)
  assert.equal(h.guard.pending.value, undefined)
  assert.equal(h.storage.size, 0)
})
test('reload and UNKNOWN status cannot clear the original request or permit a new scan', async () => {
  const h = setup('putaway')
  h.setConfirm(() => {
    throw new Error('offline')
  })
  await h.guard.submit(original('putaway'))
  const restored = h.create()
  assert.equal(restored.pending.value.request.idempotencyKey, 'original-key')
  await restored.reconcile()
  assert.equal(restored.locked.value, true)
  assert.equal(restored.pending.value.request.version, 3)
  assert.match(restored.error.value, /仍未知/)
})
test('the existing replay API reports zero new confirmations and resolves only with the exact APPLIED record', async () => {
  const h = setup()
  h.setConfirm(() => {
    throw new Error('lost')
  })
  await h.guard.submit(original())
  h.setConfirm(() => ({
    task: { id: 7, version: 4 },
    recordId: 77,
    confirmedCount: 0,
    success: true,
    replayed: true
  }))
  assert.equal(await h.guard.submit(), undefined)
  assert.equal(h.guard.locked.value, true)
  h.setState('APPLIED')
  const result = await h.guard.submit()
  assert.ok(result?.replayed)
  assert.equal(result.confirmedCount, 0)
  assert.equal(h.guard.pending.value, undefined)
})
test('only an exact APPLIED status followed by verified current task data clears the pending barrier', async () => {
  const h = setup()
  h.setConfirm(() => {
    throw new Error('lost')
  })
  await h.guard.submit(original())
  h.setState('APPLIED')
  assert.equal((await h.guard.reconcile()).task.version, 4)
  assert.equal(h.guard.pending.value, undefined)
  assert.equal(h.sent.length, 1)
})
test('NOT_APPLIED_STALE requires explicit human confirmation and never reposts with a new version', async () => {
  const h = setup()
  h.setConfirm(() => {
    throw new Error('lost')
  })
  await h.guard.submit(original())
  h.setState('NOT_APPLIED_STALE')
  await h.guard.reconcile()
  assert.equal(h.prompts(), 1)
  assert.equal(h.sent.length, 1)
  assert.equal(h.guard.pending.value, undefined)
})
test('a mismatched response retains the original pending record', async () => {
  const h = setup()
  h.setConfirm(() => ({ task: { id: 8, version: 4 }, recordId: 77, confirmedCount: 2 }))
  await h.guard.submit(original())
  assert.equal(h.guard.pending.value.request.taskId, 7)
  assert.equal(h.guard.locked.value, true)
})
test('known rejection after an uncertain reply cannot clear the original request', async () => {
  const h = setup()
  h.setConfirm(() => {
    throw new Error('lost')
  })
  await h.guard.submit(original())
  h.setConfirm(() => {
    throw Object.assign(new Error('stale'), { businessCode: 1 })
  })
  await h.guard.submit()
  assert.equal(h.guard.pending.value.request.idempotencyKey, 'original-key')
  assert.equal(h.guard.locked.value, true)
})
test('identity change while a response is pending neither clears nor applies the old request; revoked authority sends nothing', async () => {
  const h = setup()
  let release
  h.setConfirm(
    () =>
      new Promise((resolve) => {
        release = resolve
      })
  )
  const waiting = h.guard.submit(original())
  await Promise.resolve()
  h.inactive()
  release({ task: { id: 7, version: 4 }, recordId: 77, confirmedCount: 2 })
  assert.equal(await waiting, undefined)
  assert.equal(h.storage.size, 1)
  const denied = setup()
  denied.deny()
  await denied.guard.submit(original())
  assert.equal(denied.sent.length, 0)
})
