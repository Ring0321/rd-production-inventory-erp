import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluate, plain } from './testHelpers.mjs'

const response = (version = 1, status = 'ASSIGNED') => ({
  task: {
    id: 3,
    taskNo: 'PA-3',
    status,
    version,
    packageCount: 2,
    scannedPackageCount: 0,
    plannedLocationId: 5,
    plannedLocationCode: 'A-5',
    forkliftDriverId: 6
  },
  lines: [11, 12].map((id) => ({
    id,
    packageCode: `PKG-${id}`,
    packageWeight: 1000,
    scanCount: 0,
    lineStatus: 'PENDING'
  })),
  exceptions: []
})
const setup = (overrides = {}, grants = ['*:*:*'], saved) => {
  const data = new Map(saved || [])
  const calls = []
  const identity = vue.reactive({ current: true })
  const session = {
    ownerId: 7,
    tenant: '1',
    current: () => identity.current,
    allowed: (grant) => identity.current && (grants.includes('*:*:*') || grants.includes(grant)),
    assertCurrent: () => {
      if (!identity.current) throw new Error('Identity changed')
    }
  }
  const storage = {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value),
    removeItem: (key) => data.delete(key)
  }
  const pendingModule = evaluate(
    './useBatchPending.ts',
    { vue, './batchPending': evaluate('./batchPending.ts') },
    { localStorage: storage }
  )
  const api = {
    get: async (id) => {
      calls.push(['get', id])
      return response()
    },
    getPage: async (params) => {
      calls.push(['page', plain(params)])
      return { list: [], total: 22 }
    },
    previewBatchScan: async (params) => {
      calls.push(['preview', plain(params)])
      return { ...response(), representativePackageCode: params.packageCode }
    },
    confirmBatchScan: async (params) => {
      calls.push(['batch', plain(params)])
      return {
        ...response(2, 'MOVING'),
        lines: response().lines.map((line) => ({
          ...line,
          scanCount: params.taskLineIds.includes(line.id) ? 1 : 0,
          lineStatus: params.taskLineIds.includes(line.id) ? 'SCANNED' : 'PENDING'
        })),
        confirmedCount: params.taskLineIds.length,
        replayed: false
      }
    },
    confirm: async (params) => {
      calls.push(['confirm', plain(params)])
      return 8
    },
    reassign: async (params) => {
      calls.push(['reassign', plain(params)])
      return true
    },
    ...overrides
  }
  const controller = evaluate('./usePutawayWork.ts', {
    vue,
    'element-plus': { ElMessageBox: { confirm: async () => true } },
    '@/utils': { generateUUID: () => 'REQ-1' },
    '@/utils/rdDigitalLink': { extractPackageCode: (value) => value.trim().split('/pkg/').at(-1) },
    '@/api/rd/stock/putaway': { RdPutawayTaskApi: api },
    './useBatchPending': pendingModule
  })
  const work = controller.usePutawayWork(
    session,
    async () => {
      calls.push(['map-refresh'])
    },
    (id) => ({ id, enabled: true, frozen: false, locationCode: `A-${id}` })
  )
  return { work, calls, data, identity, api, session }
}

test('putaway lookup and task navigation only read and do not default to whole-task selection', async () => {
  const h = setup()
  await h.work.open(3)
  assert.equal(h.work.input.value, 'PKG-11')
  assert.equal(h.work.selected.value.length, 0)
  h.work.input.value = 'https://id.tjldsic.com/pkg/PKG-12'
  await h.work.locate()
  assert.equal(h.calls.at(-1)[0], 'preview')
  assert.equal(h.work.selected.value.length, 0)
  assert.equal(
    h.calls.some(([name]) => ['batch', 'confirm', 'reassign'].includes(name)),
    false
  )
})

test('partial physical selection sends only actual line IDs and does not post stock', async () => {
  const h = setup()
  await h.work.open(3)
  h.work.toggle(12)
  await h.work.confirmBatch()
  assert.deepEqual(h.calls.find(([name]) => name === 'batch')[1].taskLineIds, [12])
  assert.equal(h.work.detail.value.lines[0].lineStatus, 'PENDING')
  assert.equal(h.work.detail.value.lines[1].lineStatus, 'SCANNED')
  assert.equal(h.work.pending.value, undefined)
  assert.equal(
    h.calls.some(([name]) => name === 'confirm'),
    false
  )
  assert.ok(h.calls.some(([name]) => name === 'map-refresh'))
})

test('unknown response keeps the exact original request across reload', async () => {
  const requests = []
  const h = setup({
    confirmBatchScan: async (params) => {
      requests.push(plain(params))
      throw new Error('Lost response')
    }
  })
  await h.work.open(3)
  h.work.toggle(11)
  await h.work.confirmBatch()
  assert.equal(h.work.pending.value.uncertain, true)
  h.work.input.value = 'PKG-OTHER'
  h.work.selected.value = [12]
  const restored = setup(
    {
      confirmBatchScan: async (params) => {
        requests.push(plain(params))
        return { ...response(2), replayed: true, confirmedCount: 0 }
      }
    },
    ['*:*:*'],
    [...h.data]
  )
  await restored.work.confirmBatch()
  assert.deepEqual(requests[0], requests[1])
  assert.equal(restored.work.pending.value, undefined)
})

test('definite first refusal unlocks, while a later refusal cannot erase an uncertain request', async () => {
  const businessError = Object.assign(new Error('Changed version'), { businessCode: 42 })
  const h = setup({
    confirmBatchScan: async () => {
      throw businessError
    }
  })
  await h.work.open(3)
  h.work.toggle(11)
  await h.work.confirmBatch()
  assert.equal(h.work.pending.value, undefined)
  h.api.confirmBatchScan = async () => {
    throw new Error('Unknown')
  }
  await h.work.confirmBatch()
  assert.ok(h.work.pending.value)
  h.api.confirmBatchScan = async () => {
    throw businessError
  }
  await h.work.confirmBatch()
  assert.equal(h.work.pending.value.uncertain, true)
})

test('changed identity after batch response retains the original namespace and stops follow-up operations', async () => {
  let release, signal
  const started = new Promise((resolve) => {
    signal = resolve
  })
  const h = setup({
    confirmBatchScan: () =>
      new Promise((resolve) => {
        release = resolve
        signal()
      })
  })
  await h.work.open(3)
  h.work.toggle(11)
  const sending = h.work.confirmBatch()
  await started
  h.identity.current = false
  release({ ...response(2), confirmedCount: 1, replayed: false })
  await sending
  assert.deepEqual([...h.data.keys()], ['rd-warehouse-pending:1:7:putaway'])
  assert.ok(h.work.pending.value)
  assert.equal(
    h.calls.some(([name]) => name === 'map-refresh'),
    false
  )
})

test('query-only and driver capabilities cannot batch-confirm, reassign or final-post', async () => {
  for (const grants of [['rd:putaway-task:query'], ['rd:putaway-task:execute']]) {
    const h = setup({}, grants)
    await h.work.open(3)
    h.work.selectAll()
    await h.work.confirmBatch()
    await h.work.act('confirm')
    await h.work.act('reassign')
    assert.equal(
      h.calls.some(([name]) => ['batch', 'confirm', 'reassign'].includes(name)),
      false
    )
  }
})

test('changed displayed version cannot silently authorize a final confirmation with the newest version', async () => {
  let reads = 0
  const h = setup({ get: async () => response(++reads, 'PENDING_CONFIRMATION') })
  await h.work.open(3)
  h.work.finalLocationId.value = 9
  await h.work.act('confirm')
  assert.equal(
    h.calls.some(([name]) => name === 'confirm'),
    false
  )
  assert.match(h.work.error.value, /版本/)
})

test('server pagination preserves the total and no local truncation claims the full task list', async () => {
  const h = setup()
  await h.work.loadList(3)
  assert.equal(h.work.total.value, 22)
  assert.equal(h.calls[0][1].pageNo, 3)
})

test('old page response cannot recreate a resolved snapshot or overwrite a newer pending request', async () => {
  for (const newer of [false, true]) {
    let release, signal
    const started = new Promise((resolve) => {
      signal = resolve
    })
    const h = setup({
      confirmBatchScan: () =>
        new Promise((resolve) => {
          release = resolve
          signal()
        })
    })
    await h.work.open(3)
    h.work.toggle(11)
    const sending = h.work.confirmBatch()
    await started
    const key = 'rd-warehouse-pending:1:7:putaway'
    const next = plain(h.work.pending.value)
    next.request.idempotencyKey = 'REQ-NEW'
    h.identity.current = false
    if (newer) h.data.set(key, JSON.stringify(next))
    else h.data.delete(key)
    release({ ...response(2), confirmedCount: 1, replayed: false })
    await sending
    if (newer) assert.equal(JSON.parse(h.data.get(key)).request.idempotencyKey, 'REQ-NEW')
    else assert.equal(h.data.has(key), false)
  }
})

test('non-reactive expired context cannot dispatch reads after computed permission was cached', async () => {
  const h = setup()
  await h.work.open(3)
  const before = h.calls.length
  h.session.current = () => false
  h.session.allowed = () => false
  await h.work.loadList()
  await h.work.open(3)
  await h.work.locate()
  assert.equal(h.calls.length, before)
})

test('abrupt reload treats a captured but unacknowledged request as uncertain', async () => {
  const saved = [
    [
      'rd-warehouse-pending:1:7:putaway',
      JSON.stringify({
        kind: 'putaway',
        ownerId: 7,
        tenant: '1',
        uncertain: false,
        request: {
          id: 3,
          version: 1,
          packageCode: 'PKG-11',
          taskLineIds: [11],
          idempotencyKey: 'REQ-SENT'
        }
      })
    ]
  ]
  const h = setup(
    {
      confirmBatchScan: async () => {
        throw Object.assign(new Error('Version changed'), { businessCode: 42 })
      }
    },
    ['*:*:*'],
    saved
  )
  await h.work.confirmBatch()
  assert.ok(h.work.pending.value)
  assert.equal(h.work.pending.value.uncertain, true)
})

test('a false reassign acknowledgement cannot be reported as resolved', async () => {
  const h = setup({
    get: async () => ({
      ...response(2, 'EXCEPTION_PENDING'),
      exceptions: [{ id: 5, exceptionStatus: 'OPEN' }]
    }),
    reassign: async () => false
  })
  await h.work.open(3)
  h.work.handlingResult.value = '验证'
  await h.work.act('reassign')
  assert.equal(h.work.feedback.value, '')
  assert.equal(h.work.needsCheck.value, true)
})
