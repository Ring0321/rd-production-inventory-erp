import assert from 'node:assert/strict'
import { test } from 'node:test'
import { plain } from './testHelpers.mjs'

export const registerReconcileTests = ({ kind, setup, detail }) => {
  const isLoading = kind === 'loading'
  const id = isLoading ? 4 : 3
  const lineId = isLoading ? 21 : 11
  const readMethod = isLoading ? 'getTask' : 'get'
  const grant = isLoading ? 'rd:loading-task:dispatch' : 'rd:putaway-task:confirm'
  const key = `rd-warehouse-pending:1:7:${kind}`
  const original = {
    kind,
    ownerId: 7,
    tenant: '1',
    uncertain: true,
    request: {
      [isLoading ? 'taskId' : 'id']: id,
      version: 1,
      [isLoading ? 'representativePackageCode' : 'packageCode']: `PKG-${lineId}`,
      taskLineIds: [lineId],
      idempotencyKey: 'ORIGINAL-KEY'
    }
  }
  const saved = () => [[key, JSON.stringify(original)]]
  const result = (state) => ({
    taskId: id,
    requestVersion: 1,
    currentVersion: state === 'UNKNOWN' ? 1 : 2,
    taskStatus: 'ASSIGNED',
    state,
    ...(state === 'APPLIED' ? { recordId: 9 } : {})
  })
  const retained = (h) => {
    assert.deepEqual(plain(h.work.pending.value.request), original.request)
    assert.deepEqual(JSON.parse(h.data.get(key)).request, original.request)
    assert.equal(h.work.feedback.value, '')
    assert.equal(h.work.locked.value, true)
  }

  test(`${kind}: authoritative applied/stale results recover only after reading the current task`, async () => {
    for (const state of ['APPLIED', 'NOT_APPLIED_STALE']) {
      const requests = []
      let dialogs = 0
      let reads = 0
      const h = setup(
        {
          getBatchStatus: async (request) => {
            requests.push(plain(request))
            return result(state)
          },
          [readMethod]: async () => {
            reads++
            return detail(2)
          }
        },
        ['*:*:*'],
        saved(),
        async () => {
          dialogs++
        }
      )
      await h.work.reconcile()
      assert.deepEqual(requests, [original.request])
      assert.equal(reads, 1)
      assert.equal(dialogs, state === 'APPLIED' ? 0 : 1)
      assert.equal(h.work.pending.value, undefined)
      assert.equal(h.data.has(key), false)
      assert.equal(h.work.detail.value.task.version, 2)
      assert.deepEqual(plain(h.work.selected.value), [])
      assert.match(h.work.feedback.value, state === 'APPLIED' ? /已执行/ : /重新选择/)
      assert.ok(h.calls.every(([name]) => ['page', 'map-refresh'].includes(name)))
    }
  })

  test(`${kind}: unknown result preserves the exact key/version/members without a new command`, async () => {
    let reads = 0
    const h = setup(
      {
        getBatchStatus: async () => result('UNKNOWN'),
        [readMethod]: async () => {
          reads++
          return detail(1)
        }
      },
      ['*:*:*'],
      saved()
    )
    await h.work.reconcile()
    retained(h)
    assert.equal(reads, 0)
    assert.deepEqual(h.calls, [])
    assert.match(h.work.error.value, /仍未知/)
  })

  test(`${kind}: inconsistent status, stale task reads and transport failures never discard the original`, async () => {
    const good = result('APPLIED')
    const cases = [
      { status: { ...good, taskId: 99 } },
      { status: { ...good, requestVersion: 99 } },
      { status: { ...good, recordId: undefined } },
      { status: { ...result('NOT_APPLIED_STALE'), currentVersion: 1 } },
      { task: { ...detail(2), task: { ...detail(2).task, id: 99 } } },
      { task: detail(1) },
      { statusFailure: true },
      { readFailure: true }
    ]
    for (const item of cases) {
      const h = setup(
        {
          getBatchStatus: async () => {
            if (item.statusFailure) throw new Error('Status unavailable')
            return item.status || good
          },
          [readMethod]: async () => {
            if (item.readFailure) throw new Error('Task unavailable')
            return item.task || detail(2)
          }
        },
        ['*:*:*'],
        saved()
      )
      await h.work.reconcile()
      retained(h)
      assert.ok(h.work.error.value)
      assert.deepEqual(h.calls, [])
    }
  })

  test(`${kind}: cancelling the stale-request confirmation preserves the original`, async () => {
    const h = setup(
      { getBatchStatus: async () => result('NOT_APPLIED_STALE') },
      ['*:*:*'],
      saved(),
      async () => {
        throw 'cancel'
      }
    )
    await h.work.reconcile()
    retained(h)
    assert.deepEqual(h.calls, [])
  })

  test(`${kind}: changed page identity or a newer stored request survives an old status/read/dialog continuation`, async () => {
    for (const phase of ['status', 'read', 'dialog']) {
      for (const change of ['identity', 'stored', 'permission']) {
        let release
        let started
        const ready = new Promise((resolve) => {
          started = resolve
        })
        const deferred = () =>
          new Promise((resolve) => {
            release = resolve
            started()
          })
        const state = phase === 'dialog' ? 'NOT_APPLIED_STALE' : 'APPLIED'
        const h = setup(
          {
            getBatchStatus: async () => (phase === 'status' ? deferred() : result(state)),
            [readMethod]: async () => (phase === 'read' ? deferred() : detail(2))
          },
          ['*:*:*'],
          saved(),
          async () => (phase === 'dialog' ? deferred() : true)
        )
        const running = h.work.reconcile()
        await ready
        const newer = plain(original)
        newer.request.idempotencyKey = 'NEWER-KEY'
        if (change === 'identity') h.identity.current = false
        if (change === 'stored') h.data.set(key, JSON.stringify(newer))
        if (change === 'permission') h.session.allowed = (value) => value !== grant
        release(phase === 'status' ? result(state) : phase === 'read' ? detail(2) : true)
        await running
        assert.ok(h.work.pending.value)
        assert.equal(h.work.feedback.value, '')
        assert.equal(
          JSON.parse(h.data.get(key)).request.idempotencyKey,
          change === 'stored' ? 'NEWER-KEY' : 'ORIGINAL-KEY'
        )
        assert.deepEqual(h.calls, [])
      }
    }
  })

  test(`${kind}: query-only capability cannot start request reconciliation`, async () => {
    let statuses = 0
    const h = setup(
      {
        getBatchStatus: async () => {
          statuses++
          return result('APPLIED')
        }
      },
      [isLoading ? 'rd:loading-task:query' : 'rd:putaway-task:query'],
      saved()
    )
    await h.work.reconcile()
    assert.equal(statuses, 0)
    retained(h)
  })

  test(`${kind}: retrying an old page cannot overwrite a newer stored request before sending`, async () => {
    let sent = 0
    const h = setup(
      {
        confirmBatchScan: async () => {
          sent++
          throw new Error('Should not send')
        }
      },
      ['*:*:*'],
      saved()
    )
    const newer = plain(original)
    newer.request.idempotencyKey = 'NEWER-STORED'
    h.data.set(key, JSON.stringify(newer))
    await h.work.confirmBatch()
    assert.equal(sent, 0)
    assert.deepEqual(JSON.parse(h.data.get(key)).request, newer.request)
    assert.ok(h.work.pending.value)
  })

  test(`${kind}: capturing a fresh local request cannot overwrite another tab's pending request`, async () => {
    let sent = 0
    const h = setup({
      confirmBatchScan: async () => {
        sent++
        throw new Error('Should not send')
      }
    })
    await h.work.open(id)
    h.work.toggle(lineId)
    h.data.set(key, JSON.stringify(original))
    await h.work.confirmBatch()
    assert.equal(sent, 0)
    assert.deepEqual(JSON.parse(h.data.get(key)).request, original.request)
  })
}
