import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluate, plain } from './testHelpers.mjs'

const initial = (status = 'ASSIGNED', version = 1) => ({
  task: {
    id: 4,
    taskNo: 'LOAD-4',
    pickupSlipNo: 'SLIP-4',
    customerName: '验证客户',
    status,
    version,
    plannedPackageCount: 2,
    scannedPackageCount: 0,
    scannedWeight: 0
  },
  lines: [21, 22].map((id) => ({
    id,
    packageCode: `PKG-${id}`,
    packageWeight: 1000,
    plannedLocationCode: `A-${id}`,
    lineStatus: 'PLANNED'
  })),
  exceptions:
    status === 'EXCEPTION_PENDING'
      ? [{ id: 8, exceptionStatus: 'OPEN', description: '验证异常' }]
      : []
})
const setup = (overrides = {}, grants = ['*:*:*'], saved) => {
  const calls = []
  const data = new Map(saved || [])
  const identity = vue.reactive({ current: true })
  let current = initial()
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
    getTask: async (id) => {
      calls.push(['get', id])
      return plain(current)
    },
    getTaskPage: async (params) => {
      calls.push(['page', plain(params)])
      return { list: [], total: 31 }
    },
    previewBatchScan: async (params) => {
      calls.push(['preview', plain(params)])
      return { ...plain(current), representativePackageCode: params.packageCode, success: true }
    },
    confirmBatchScan: async (params) => {
      calls.push(['batch', plain(params)])
      current.task.version++
      for (const line of current.lines)
        if (params.taskLineIds.includes(line.id)) line.lineStatus = 'SCANNED'
      current.task.scannedPackageCount = current.lines.filter(
        (line) => line.lineStatus === 'SCANNED'
      ).length
      current.task.scannedWeight = current.task.scannedPackageCount * 1000
      current.task.status = current.task.scannedPackageCount === 2 ? 'PENDING_REVIEW' : 'LOADING'
      return {
        ...plain(current),
        success: true,
        replayed: false,
        confirmedCount: params.taskLineIds.length
      }
    },
    confirmOutbound: async (params) => {
      calls.push(['outbound', plain(params)])
      current.task.status = 'OUTBOUNDED'
      current.task.version++
      return {
        taskId: 4,
        outboundPackageCount: current.task.scannedPackageCount,
        outboundWeight: current.task.scannedWeight,
        outboundOrderIds: [9]
      }
    },
    resolveException: async (params) => {
      calls.push(['resolve', plain(params)])
      current.task.status = params.resolutionAction === 'CANCEL' ? 'CANCELLED' : 'ASSIGNED'
      if (params.resolutionAction === 'REPLACE_PACKAGE')
        current.lines.find((line) => line.id === params.replacements[0].taskLineId).packageCode =
          params.replacements[0].newPackageCode
      current.task.version++
      current.exceptions = []
      return true
    },
    ...overrides
  }
  const module = evaluate('./useLoadingWork.ts', {
    vue,
    'element-plus': { ElMessageBox: { confirm: async () => true } },
    '@/utils': { generateUUID: () => 'REQ-2' },
    '@/utils/rdDigitalLink': { extractPackageCode: (value) => value.trim().split('/pkg/').at(-1) },
    '@/api/rd/loading': { RdLoadingApi: api },
    './useBatchPending': pendingModule
  })
  return {
    work: module.useLoadingWork(session, async () => {
      calls.push(['map-refresh'])
    }),
    api,
    session,
    calls,
    identity,
    data,
    setCurrent: (value) => {
      current = value
    }
  }
}

test('query-only loading users read tasks but never call dispatch preview or commands', async () => {
  const h = setup({}, ['rd:loading-task:query'])
  await h.work.open(4)
  await h.work.locate()
  h.work.selectAll()
  await h.work.confirmBatch()
  await h.work.act('outbound')
  await h.work.act('resolve')
  assert.deepEqual(
    h.calls.map(([name]) => name),
    ['get']
  )
})

test('loading selection remains explicit, with the original server version and physical subset', async () => {
  const h = setup()
  await h.work.open(4)
  assert.equal(h.work.selected.value.length, 0)
  h.work.toggle(22)
  await h.work.confirmBatch()
  const sent = h.calls.find(([name]) => name === 'batch')[1]
  assert.deepEqual(sent.taskLineIds, [22])
  assert.equal(sent.version, 1)
  assert.equal(h.work.detail.value.lines[0].lineStatus, 'PLANNED')
  assert.equal(h.work.detail.value.lines[1].lineStatus, 'SCANNED')
  assert.equal(
    h.calls.some(([name]) => name === 'outbound'),
    false
  )
  assert.equal(h.work.pending.value, undefined)
})

test('lost loading response restores the original version, key and selection, not the current task version', async () => {
  const requests = []
  const h = setup({
    confirmBatchScan: async (params) => {
      requests.push(plain(params))
      throw new Error('Lost response')
    }
  })
  await h.work.open(4)
  h.work.toggle(21)
  await h.work.confirmBatch()
  const resumed = setup(
    {
      confirmBatchScan: async (params) => {
        requests.push(plain(params))
        return { ...initial('LOADING', 9), success: true, replayed: true, confirmedCount: 0 }
      }
    },
    ['*:*:*'],
    [...h.data]
  )
  resumed.setCurrent(initial('LOADING', 9))
  await resumed.work.confirmBatch()
  assert.deepEqual(requests[0], requests[1])
  assert.equal(requests[1].version, 1)
  assert.equal(resumed.work.pending.value, undefined)
})

test('an unversioned loading detail cannot become a batch request', async () => {
  const h = setup()
  const value = initial()
  delete value.task.version
  h.setCurrent(value)
  await h.work.open(4)
  h.work.toggle(21)
  await h.work.confirmBatch()
  assert.equal(
    h.calls.some(([name]) => name === 'batch'),
    false
  )
  assert.equal(h.work.pending.value, undefined)
})

test('identity change after response keeps the original loading snapshot and prevents follow-up reads', async () => {
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
  await h.work.open(4)
  h.work.toggle(21)
  const sending = h.work.confirmBatch()
  await started
  const readCount = h.calls.filter(([name]) => name === 'get').length
  h.identity.current = false
  release({ ...initial('LOADING', 2), success: true, replayed: false, confirmedCount: 1 })
  await sending
  assert.ok(h.work.pending.value)
  assert.deepEqual([...h.data.keys()], ['rd-warehouse-pending:1:7:loading'])
  assert.equal(h.calls.filter(([name]) => name === 'get').length, readCount)
})

test('version changed since viewing does not silently confirm final outbound', async () => {
  const h = setup()
  h.setCurrent(initial('PENDING_REVIEW', 3))
  await h.work.open(4)
  h.setCurrent(initial('PENDING_REVIEW', 4))
  await h.work.act('outbound')
  assert.equal(
    h.calls.some(([name]) => name === 'outbound'),
    false
  )
  assert.match(h.work.error.value, /版本/)
})

test('final outbound is a separate explicit step and refreshes authoritative map and task data', async () => {
  const h = setup()
  await h.work.open(4)
  h.work.selectAll()
  await h.work.confirmBatch()
  assert.equal(h.work.detail.value.task.status, 'PENDING_REVIEW')
  assert.equal(
    h.calls.some(([name]) => name === 'outbound'),
    false
  )
  await h.work.act('outbound')
  assert.equal(h.work.detail.value.task.status, 'OUTBOUNDED')
  assert.equal(h.calls.filter(([name]) => name === 'outbound').length, 1)
  assert.equal(h.calls.filter(([name]) => name === 'map-refresh').length, 2)
  await h.work.act('outbound')
  assert.equal(h.calls.filter(([name]) => name === 'outbound').length, 1)
})

test('return, replace and cancel preserve distinct commands and require an actual open exception', async () => {
  for (const action of ['RETURN_TO_DRIVER', 'REPLACE_PACKAGE', 'CANCEL']) {
    const h = setup()
    h.setCurrent(initial('EXCEPTION_PENDING', 4))
    await h.work.open(4)
    h.work.resolutionAction.value = action
    h.work.handlingResult.value = '现场已核定'
    if (action === 'REPLACE_PACKAGE') {
      h.work.replacementLineId.value = 21
      h.work.replacementCode.value = 'https://id.tjldsic.com/pkg/PKG-23'
    }
    await h.work.act('resolve')
    const sent = h.calls.find(([name]) => name === 'resolve')[1]
    assert.equal(sent.version, 4)
    assert.equal(sent.resolutionAction, action)
    if (action === 'REPLACE_PACKAGE')
      assert.deepEqual(sent.replacements, [{ taskLineId: 21, newPackageCode: 'PKG-23' }])
    else assert.equal(sent.replacements, undefined)
    assert.equal(
      h.calls.some(([name]) => name === 'outbound'),
      false
    )
  }
})

test('an already confirmed package cannot be selected for exception replacement', async () => {
  const h = setup()
  const value = initial('EXCEPTION_PENDING', 4)
  value.lines[0].lineStatus = 'SCANNED'
  h.setCurrent(value)
  await h.work.open(4)
  h.work.resolutionAction.value = 'REPLACE_PACKAGE'
  h.work.handlingResult.value = '验证'
  h.work.replacementLineId.value = 21
  h.work.replacementCode.value = 'PKG-23'
  await h.work.act('resolve')
  assert.equal(
    h.calls.some(([name]) => name === 'resolve'),
    false
  )
})

test('wrong-task acknowledgement cannot clear the original request', async () => {
  const h = setup({
    confirmBatchScan: async () => ({
      ...initial(),
      task: { ...initial().task, id: 99 },
      success: true
    })
  })
  await h.work.open(4)
  h.work.toggle(21)
  await h.work.confirmBatch()
  assert.ok(h.work.pending.value)
  assert.equal(h.work.pending.value.request.taskId, 4)
})

test('cached loading permission cannot issue reads in a non-reactively expired context', async () => {
  const h = setup()
  await h.work.open(4)
  const before = h.calls.length
  h.session.current = () => false
  h.session.allowed = () => false
  await h.work.loadList()
  await h.work.open(4)
  await h.work.locate()
  assert.equal(h.calls.length, before)
})

test('a false loading resolution acknowledgement never emits successful feedback', async () => {
  const h = setup({ resolveException: async () => false })
  h.setCurrent(initial('EXCEPTION_PENDING', 4))
  await h.work.open(4)
  h.work.handlingResult.value = '验证'
  await h.work.act('resolve')
  assert.equal(h.work.feedback.value, '')
  assert.equal(h.work.needsCheck.value, true)
})

test('a resolution true reply with original exceptions still open remains pending verification', async () => {
  const h = setup({ resolveException: async () => true })
  h.setCurrent(initial('EXCEPTION_PENDING', 4))
  await h.work.open(4)
  h.work.handlingResult.value = '验证'
  await h.work.act('resolve')
  assert.equal(h.work.feedback.value, '')
  assert.equal(h.work.needsCheck.value, true)
})
