import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as vue from 'vue'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'
import { evaluate, plain } from '../../pda/warehouse/testHelpers.mjs'

const taskDetail = (changes = {}) => ({
  task: {
    id: 4,
    version: 3,
    planVersion: 2,
    pickupSlipId: 8,
    status: 'EXCEPTION_PENDING',
    plannedPackageCount: 3,
    ...changes
  },
  lines: [21, 22, 23].map((id) => ({
    id,
    packageId: id + 100,
    packageCode: `PKG-${id}`,
    lineStatus: id === 22 ? 'EXCEPTION' : 'PLANNED',
    packageWeight: 1000,
    plannedLocationCode: 'A-01'
  })),
  exceptions: []
})
const record = (changes = {}) => ({
  id: 10,
  taskId: 4,
  pickupSlipId: 8,
  taskNo: 'TASK-4',
  pickupSlipNo: 'SLIP-8',
  customerName: '本地验证客户',
  status: 'PENDING',
  effectiveStatus: 'PENDING',
  taskVersion: 3,
  planVersion: 2,
  slipVersion: 1,
  version: 0,
  idempotencyKey: 'key-1',
  originalPackageCount: 3,
  reducedPackageCount: 1,
  remainingPackageCount: 2,
  originalWeight: 3000,
  reducedWeight: 1000,
  remainingWeight: 2000,
  reason: '现场包件破损',
  proposerId: 7,
  proposerName: '验证仓管',
  proposedTime: '2026-10-14 10:00:00',
  lines: taskDetail().lines.map((line) => ({ ...line, removed: line.id === 21 })),
  ...changes
})
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const settle = async () => {
  for (let index = 0; index < 8; index++) await Promise.resolve()
}
const setup = ({ api = {}, storage = new Map(), pickup = false } = {}) => {
  const hooks = {}
  const events = new Map()
  const document = {
    visibilityState: 'visible',
    addEventListener: (name, fn) => events.set(name, fn),
    removeEventListener: (name) => events.delete(name)
  }
  const window = {
    addEventListener: (name, fn) => events.set(name, fn),
    removeEventListener: (name) => events.delete(name)
  }
  const identity = vue.reactive({ id: 7, tenant: '1', token: true })
  const permissions = vue.reactive(new Set(['rd:loading-task:query', 'rd:loading-task:dispatch']))
  const roles = vue.ref([pickup ? 'rd_sales' : 'rd_warehouse'])
  const props = vue.reactive({
    taskDetail: pickup ? undefined : taskDetail(),
    pickupSlipId: pickup ? 8 : undefined,
    active: true
  })
  const calls = { list: [], get: [], create: [], review: [] }
  const localStorage = {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key)
  }
  const session = {
    ownerId: 7,
    tenant: '1',
    current: () => identity.id === 7 && identity.tenant === '1' && identity.token,
    generation: () => 0,
    allowed: (permission) => session.current() && permissions.has(permission)
  }
  const module = evaluate(
    '../../stock/loading/useLoadingReduction.ts',
    {
      vue: {
        ...vue,
        ...Object.fromEntries(
          ['onMounted', 'onActivated', 'onDeactivated', 'onBeforeUnmount'].map((name) => [
            name,
            (fn) => {
              hooks[name] = fn
            }
          ])
        )
      },
      '@/api/rd/loading/reduction': {
        RdLoadingReductionApi: Object.fromEntries(
          ['list', 'get', 'create', 'review'].map((name) => [
            name,
            (input) => {
              calls[name].push(plain(input))
              return api[name]?.(input) ?? Promise.resolve(name === 'list' ? [] : undefined)
            }
          ])
        )
      },
      '../../pda/warehouse/useWarehouseSession': { useWarehouseSession: () => session },
      '@/store/modules/user': {
        useUserStore: () => ({
          getUser: identity,
          get getRoles() {
            return roles.value
          },
          getPermissions: permissions
        })
      },
      '@/utils/auth': {
        onAuthContextChange: (fn) => {
          hooks.auth = fn
          return () => {}
        }
      },
      '@/utils': { generateUUID: () => 'key-1' }
    },
    { localStorage, Error, document, window }
  )
  const scope = vue.effectScope()
  const work = scope.run(() =>
    module.useLoadingReduction({
      taskDetail: () => props.taskDetail,
      pickupSlipId: () => props.pickupSlipId,
      active: () => props.active
    })
  )
  return {
    work,
    module,
    calls,
    props,
    hooks,
    permissions,
    identity,
    storage,
    scope,
    localStorage,
    document,
    events,
    session,
    roles
  }
}
const draft = (h) => {
  h.work.selected.value = [21]
  h.work.reason.value = '现场包件破损'
}

test('create sends only the fixed contract, preserves one package and uses task version', async () => {
  const h = setup({ api: { create: async () => record() } })
  await settle()
  draft(h)
  await h.work.create()
  assert.deepEqual(h.calls.create, [
    {
      taskId: 4,
      taskVersion: 3,
      taskLineIds: [21],
      reason: '现场包件破损',
      idempotencyKey: 'key-1'
    }
  ])
  assert.equal(h.work.pending.value, undefined)
  assert.equal(h.work.records.value[0].status, 'PENDING')
  assert.equal(h.props.taskDetail.task.plannedPackageCount, 3)
  h.scope.stop()
})

test('empty, duplicate, nonmember, scanned, cancelled, over-100 and all-package reductions fail', () => {
  const h = setup()
  const build = h.module.buildReductionCreate
  for (const selected of [
    [],
    [21, 21],
    [999],
    [21, 22, 23],
    [0],
    Array.from({ length: 101 }, (_, i) => i + 1)
  ])
    assert.throws(() => build(taskDetail(), selected, '真实原因', 'key'))
  for (const lineStatus of ['SCANNED', 'LOADED', 'OUTBOUNDED', 'CANCELLED']) {
    const detail = taskDetail()
    detail.lines[0].lineStatus = lineStatus
    assert.throws(() => build(detail, [21], '真实原因', 'key'))
  }
  const scanned = taskDetail()
  scanned.lines[0].scanTime = '2026-10-14 10:00:00'
  assert.throws(() => build(scanned, [21], '真实原因', 'key'))
  for (const changes of [
    { version: undefined },
    { version: -1 },
    { status: 'LOADING' },
    { plannedPackageCount: 99 }
  ])
    assert.throws(() => build(taskDetail(changes), [21], '真实原因', 'key'))
  for (const reason of ['', '   ', 'x'.repeat(1001)])
    assert.throws(() => build(taskDetail(), [21], reason, 'key'))
  h.scope.stop()
})

test('100 eligible packages are allowed when at least one effective package remains', () => {
  const h = setup()
  const detail = taskDetail({ plannedPackageCount: 101 })
  detail.lines = Array.from({ length: 101 }, (_, index) => ({ ...detail.lines[0], id: index + 1 }))
  assert.equal(
    h.module.buildReductionCreate(
      detail,
      detail.lines.slice(0, 100).map((line) => line.id),
      '真实原因',
      'key'
    ).taskLineIds.length,
    100
  )
  h.scope.stop()
})

test('write loss is fixed pending; empty lists and another key never authorize a new write', async () => {
  let result = []
  const h = setup({
    api: {
      create: async () => {
        throw new Error('timeout')
      },
      list: async () => result
    }
  })
  await settle()
  draft(h)
  await h.work.create()
  assert.equal(h.work.pending.value.request.idempotencyKey, 'key-1')
  assert.match(h.work.feedback.value, /待核/)
  await h.work.refresh()
  result = [record({ idempotencyKey: 'other' })]
  await h.work.refresh()
  await h.work.create()
  assert.equal(h.calls.create.length, 1)
  assert.ok(h.work.pending.value)
  result = [record()]
  await h.work.refresh()
  assert.equal(h.work.pending.value, undefined)
  assert.equal(h.storage.size, 0)
  h.scope.stop()
})

test('uncertain request survives remount and can be reconciled from the original slip', async () => {
  const storage = new Map()
  const first = setup({
    storage,
    api: {
      create: async () => {
        throw new Error('offline')
      }
    }
  })
  await settle()
  draft(first)
  await first.work.create()
  first.scope.stop()
  const next = setup({ storage, pickup: true, api: { list: async () => [record()] } })
  next.permissions.add('rd:pickup-slip:query')
  await next.work.refresh()
  assert.equal(next.work.pending.value, undefined)
  assert.equal(next.calls.create.length, 0)
  assert.equal(storage.size, 0)
  next.scope.stop()
})

test('independent approval permission, original slip context and no self-review are required', async () => {
  const h = setup({ pickup: true, api: { list: async () => [record()] } })
  h.permissions.add('rd:pickup-slip:query')
  await h.work.refresh()
  assert.equal(h.work.canReview(record()), false)
  h.permissions.add('rd:pickup-slip:approve-reduction')
  await h.work.refresh()
  assert.equal(h.work.canReview(record()), false)
  assert.equal(h.work.canReview(record({ proposerId: 9 })), true)
  for (const changes of [
    { effectiveStatus: 'STALE' },
    { status: 'APPROVED' },
    { status: 'REJECTED' }
  ])
    assert.equal(h.work.canReview(record({ proposerId: 9, ...changes })), false)
  h.work.reviewRemarks.value[10] = '核对后通过'
  await h.work.review(record(), 'APPROVED')
  assert.equal(h.calls.review.length, 0)
  h.scope.stop()
})

test('approval loss reconciles only the same id and a newer final version, without resending', async () => {
  let result = [record({ proposerId: 9 })]
  const h = setup({
    pickup: true,
    api: {
      list: async () => result,
      review: async () => {
        throw new Error('timeout')
      }
    }
  })
  h.permissions.add('rd:pickup-slip:query')
  h.permissions.add('rd:pickup-slip:approve-reduction')
  await h.work.refresh()
  h.work.reviewRemarks.value[10] = '核对实物，同意申请'
  await h.work.review(result[0], 'APPROVED')
  assert.deepEqual(h.calls.review, [
    { id: 10, version: 0, decision: 'APPROVED', reviewRemark: '核对实物，同意申请' }
  ])
  for (const entries of [
    [],
    [record({ id: 11, version: 1, status: 'APPROVED' })],
    [record({ status: 'PENDING' })]
  ]) {
    result = entries
    await h.work.refresh()
    assert.ok(h.work.pending.value)
  }
  result = [
    record({
      version: 1,
      status: 'REJECTED',
      effectiveStatus: 'REJECTED',
      reviewerId: 99,
      reviewRemark: '另一商务已退回'
    })
  ]
  await h.work.refresh()
  assert.equal(h.work.pending.value, undefined)
  assert.match(h.work.feedback.value, /商务退回/)
  assert.equal(h.calls.review.length, 1)
  h.scope.stop()
})

test('task, version, membership, active and identity changes synchronously clear drafts', async () => {
  for (const change of [
    (h) => {
      h.props.taskDetail.task.id = 5
    },
    (h) => {
      h.props.taskDetail.task.version++
    },
    (h) => {
      h.props.taskDetail.lines[0].lineStatus = 'SCANNED'
    },
    (h) => {
      h.props.active = false
    },
    (h) => {
      h.identity.id = 99
    },
    (h) => {
      h.identity.token = false
    },
    (h) => {
      h.hooks.onDeactivated()
    },
    (h) => {
      h.hooks.auth()
    }
  ]) {
    const h = setup()
    await settle()
    draft(h)
    h.work.reviewRemarks.value[10] = '旧说明'
    change(h)
    assert.deepEqual(plain(h.work.selected.value), [])
    assert.equal(h.work.reason.value, '')
    assert.deepEqual(plain(h.work.reviewRemarks.value), {})
    h.scope.stop()
  }
})

test('late list and write responses cannot attach across task or deactivate/reactivate changes', async () => {
  for (const mode of ['task', 'lifecycle']) {
    const write = deferred()
    const h = setup({ api: { create: () => write.promise } })
    await settle()
    draft(h)
    const running = h.work.create()
    if (mode === 'task') h.props.taskDetail = taskDetail({ id: 5, pickupSlipId: 9 })
    else {
      h.hooks.onDeactivated()
      h.hooks.onActivated()
    }
    write.resolve(record())
    await running
    assert.equal(h.work.records.value.length, 0)
    assert.ok(h.storage.size > 0)
    h.scope.stop()
  }
  const read = deferred()
  const h = setup({ api: { list: () => read.promise } })
  h.props.active = false
  read.resolve([record()])
  await settle()
  assert.equal(h.work.records.value.length, 0)
  h.scope.stop()
})

test('storage failures, conflicting journals and missing identity fail closed before network writes', async () => {
  const h = setup()
  await settle()
  draft(h)
  h.localStorage.setItem = () => {
    throw new Error('quota')
  }
  await h.work.create()
  assert.equal(h.calls.create.length, 0)
  assert.ok(h.work.storageError.value)
  h.scope.stop()
  const invalid = setup({ storage: new Map([['rd-loading-reduction-pending:1:7', '{broken']]) })
  await settle()
  draft(invalid)
  await invalid.work.create()
  assert.equal(invalid.calls.create.length, 0)
  assert.ok(invalid.work.storageError.value)
  invalid.scope.stop()
})

test('status labels never claim R1 changed task quantity or inventory', () => {
  const h = setup()
  assert.equal(h.module.reductionStatusLabel(record()), '待商务核定')
  assert.equal(
    h.module.reductionStatusLabel(record({ status: 'APPROVED', effectiveStatus: 'APPROVED' })),
    '已核定待变更'
  )
  assert.equal(
    h.module.reductionStatusLabel(record({ status: 'REJECTED', effectiveStatus: 'REJECTED' })),
    '商务退回'
  )
  assert.equal(h.module.reductionStatusLabel(record({ effectiveStatus: 'STALE' })), '已失效')
  h.scope.stop()
})

test('API adapter preserves the three fixed HTTP contracts and exclusive query scopes', async () => {
  const requests = []
  const api = evaluate('../../../../api/rd/loading/reduction.ts', {
    '@/config/axios': {
      __esModule: true,
      default: Object.fromEntries(
        ['get', 'post', 'put'].map((method) => [
          method,
          async (input) => {
            requests.push({ method, ...plain(input) })
            return []
          }
        ])
      )
    }
  }).RdLoadingReductionApi
  await api.list({ taskId: 4 })
  await api.list({ pickupSlipId: 8 })
  await api.create({
    taskId: 4,
    taskVersion: 3,
    taskLineIds: [21],
    reason: '真实原因',
    idempotencyKey: 'key'
  })
  await api.review({ id: 10, version: 0, decision: 'REJECTED', reviewRemark: '已核实退回' })
  assert.deepEqual(
    requests.map(({ method, url }) => ({ method, url })),
    [
      { method: 'get', url: '/rd/loading-task/reduction/list' },
      { method: 'get', url: '/rd/loading-task/reduction/list' },
      { method: 'post', url: '/rd/loading-task/reduction/create' },
      { method: 'put', url: '/rd/loading-task/reduction/review' }
    ]
  )
  assert.deepEqual(
    requests.slice(0, 2).map(({ params }) => params),
    [{ taskId: 4 }, { pickupSlipId: 8 }]
  )
})

test('query-only warehouse users can read records but cannot submit or review', async () => {
  const h = setup({ api: { list: async () => [record()] } })
  h.permissions.delete('rd:loading-task:dispatch')
  await settle()
  assert.equal(h.work.records.value.length, 1)
  assert.equal(h.work.showCreate.value, false)
  draft(h)
  await h.work.create()
  await h.work.review(record({ proposerId: 9 }), 'APPROVED')
  assert.equal(h.calls.create.length + h.calls.review.length, 0)
  h.scope.stop()
})

test('live pending/approved records block another application; stale and rejected history do not', async () => {
  for (const [changes, allowed] of [
    [{}, false],
    [{ status: 'APPROVED', effectiveStatus: 'APPROVED' }, false],
    [{ effectiveStatus: 'STALE' }, true],
    [{ status: 'REJECTED', effectiveStatus: 'REJECTED' }, true]
  ]) {
    const h = setup({ api: { list: async () => [record(changes)] } })
    await settle()
    assert.equal(h.work.canCreate.value, allowed)
    h.scope.stop()
  }
})

test('snapshot summary distinguishes package count from kg and excludes cancelled old members', async () => {
  const h = setup()
  await settle()
  h.props.taskDetail.lines.push({
    ...h.props.taskDetail.lines[0],
    id: 99,
    lineStatus: 'CANCELLED',
    packageWeight: 5000
  })
  await settle()
  h.work.selected.value = [21, 22]
  assert.deepEqual(plain(h.work.preview.value), {
    originalPackageCount: 3,
    reducedPackageCount: 2,
    remainingPackageCount: 1,
    originalWeight: '3000',
    reducedWeight: '2000',
    remainingWeight: '1000'
  })
  h.scope.stop()
})

test('approval and rejection use server identity/version and require a real remark', async () => {
  for (const decision of ['APPROVED', 'REJECTED']) {
    const h = setup({
      pickup: true,
      api: {
        list: async () => [record({ proposerId: 9 })],
        review: async (input) =>
          record({
            proposerId: 9,
            version: 1,
            status: input.decision,
            effectiveStatus: input.decision,
            reviewerId: 7,
            reviewRemark: input.reviewRemark
          })
      }
    })
    h.permissions.add('rd:pickup-slip:query')
    h.permissions.add('rd:pickup-slip:approve-reduction')
    await settle()
    await h.work.review(h.work.records.value[0], decision)
    assert.equal(h.calls.review.length, 0)
    h.work.reviewRemarks.value[10] = '  已现场核对，按此结论办理  '
    await h.work.review(h.work.records.value[0], decision)
    assert.deepEqual(h.calls.review, [
      { id: 10, version: 0, decision, reviewRemark: '已现场核对，按此结论办理' }
    ])
    assert.equal(h.work.pending.value, undefined)
    assert.equal(h.work.records.value[0].status, decision)
    h.scope.stop()
  }
})

test('same key with different content, proposer or selected members cannot resolve uncertain create', async () => {
  let list = []
  const h = setup({
    api: {
      create: async () => {
        throw new Error('offline')
      },
      list: async () => list
    }
  })
  await settle()
  draft(h)
  await h.work.create()
  for (const changes of [
    { reason: '不同原因' },
    { proposerId: 9 },
    { taskVersion: 99 },
    { lines: record().lines.map((line) => ({ ...line, removed: line.id === 22 })) }
  ]) {
    list = [record(changes)]
    await h.work.refresh()
    assert.ok(h.work.pending.value)
  }
  h.scope.stop()
})

test('an unrelated page reconciles the original task by read only and ignores empty original lists', async () => {
  const h = setup({
    api: {
      create: async () => {
        throw new Error('offline')
      }
    }
  })
  await settle()
  draft(h)
  await h.work.create()
  h.props.taskDetail = taskDetail({ id: 5, pickupSlipId: 9 })
  await settle()
  assert.ok(h.work.pending.value)
  assert.ok(h.calls.list.some((input) => input.taskId === 4))
  await h.work.create()
  assert.equal(h.calls.create.length, 1)
  h.scope.stop()
})

test('known business rejection is explicit, unlike network loss, and requires a fresh list', async () => {
  const h = setup({
    api: {
      create: async () => {
        throw Object.assign(new Error('旧任务已失效'), { businessCode: 400 })
      }
    }
  })
  await settle()
  draft(h)
  await h.work.create()
  assert.equal(h.work.pending.value, undefined)
  assert.equal(h.storage.size, 0)
  assert.equal(h.work.canCreate.value, false)
  assert.match(h.work.error.value, /已失效/)
  await h.work.refresh()
  assert.equal(h.work.canCreate.value, true)
  h.scope.stop()
})

test('late acknowledgement cannot remove a newer stored journal from another page', async () => {
  const response = deferred()
  const h = setup({ api: { create: () => response.promise } })
  await settle()
  draft(h)
  const running = h.work.create()
  const key = [...h.storage.keys()][0]
  const newer = {
    ...plain(h.work.pending.value),
    request: { ...plain(h.work.pending.value.request), idempotencyKey: 'newer-key' }
  }
  h.storage.set(key, JSON.stringify(newer))
  response.resolve(record())
  await running
  assert.equal(JSON.parse(h.storage.get(key)).request.idempotencyKey, 'newer-key')
  assert.ok(h.work.pending.value)
  h.scope.stop()
})

test('missing or conflicting query scopes, oversized lists and wrong-task replies never permit writes', async () => {
  const h = setup()
  h.props.pickupSlipId = 8
  await settle()
  assert.equal(h.work.canQuery.value, false)
  const count = h.calls.list.length
  await h.work.refresh()
  assert.equal(h.calls.list.length, count)
  h.scope.stop()
  for (const result of [[record({ taskId: 99 })], Array.from({ length: 101 }, () => record())]) {
    const next = setup({ api: { list: async () => result } })
    await settle()
    assert.equal(next.work.records.value.length, 0)
    assert.equal(next.work.canCreate.value, false)
    next.scope.stop()
  }
})

test('cached permission cannot read or write after non-reactive session expiry', async () => {
  const h = setup()
  await settle()
  draft(h)
  assert.equal(h.work.canCreate.value, true)
  h.session.current = () => false
  const reads = h.calls.list.length
  await h.work.create()
  await h.work.refresh()
  assert.equal(h.calls.create.length, 0)
  assert.equal(h.calls.list.length, reads)
  assert.deepEqual(plain(h.work.selected.value), [])
  h.scope.stop()
})

test('visibility and storage events invalidate drafts and detach on unmount', async () => {
  const h = setup()
  h.hooks.onMounted()
  await settle()
  draft(h)
  h.document.visibilityState = 'hidden'
  h.events.get('visibilitychange')()
  assert.equal(h.work.reason.value, '')
  assert.equal(h.work.canQuery.value, false)
  h.document.visibilityState = 'visible'
  h.events.get('visibilitychange')()
  await settle()
  draft(h)
  h.events.get('storage')()
  assert.equal(h.work.reason.value, '')
  h.hooks.onBeforeUnmount()
  assert.equal(h.events.size, 0)
  h.scope.stop()
})

test('panel and three real entry SFCs compile without running a build or server', () => {
  for (const relative of [
    './LoadingReductionPanel.vue',
    './index.vue',
    '../../pda/warehouse/LoadingPanel.vue',
    '../../order/pickup/index.vue'
  ]) {
    const filename = new URL(relative, import.meta.url).pathname
    const source = readFileSync(new URL(relative, import.meta.url), 'utf8')
    const { descriptor, errors } = parse(source, { filename })
    assert.deepEqual(errors, [])
    const script = compileScript(descriptor, { id: 'reduction-check' })
    const template = compileTemplate({
      source: descriptor.template.content,
      filename,
      id: 'reduction-check',
      compilerOptions: { bindingMetadata: script.bindings }
    })
    assert.deepEqual(template.errors, [])
    if (relative !== './LoadingReductionPanel.vue')
      assert.ok(
        source.includes('LoadingReductionPanel'),
        `${relative} must mount the real shared panel`
      )
  }
})

test('registered decimal weights retain six-place values and exact string precision', () => {
  const h = setup()
  const format = h.module.formatReductionWeight
  for (const [value, expected] of [
    [1000.123456, '1000.123456'],
    [0.000001, '0.000001'],
    [123.0001, '123.0001'],
    ['123456789012345678.123456', '123456789012345678.123456'],
    ['1000.123456', '1000.123456'],
    ['1000.120000', '1000.12'],
    [3000, '3000'],
    [null, '-'],
    [NaN, '-']
  ])
    assert.equal(format(value), expected)
  assert.equal(h.module.sumReductionWeights([0.000001, 0.000002]), '0.000003')
  assert.equal(h.module.sumReductionWeights([0.1, 0.2]), '0.3')
  assert.equal(
    h.module.sumReductionWeights(['123456789012345678.123456', '0.000001']),
    '123456789012345678.123457'
  )
  h.scope.stop()
})

test('EXCEPTION with a historical scanTime is excluded from selection and submission', async () => {
  const h = setup()
  h.props.taskDetail.lines[1].scanTime = '2026-10-14 10:00:00'
  await settle()
  assert.equal(
    h.work.candidates.value.some((line) => line.id === 22),
    false
  )
  h.work.selected.value = [22]
  h.work.reason.value = '真实现场原因'
  await h.work.create()
  assert.equal(h.calls.create.length, 0)
  assert.match(h.work.error.value, /已核验/)
  h.scope.stop()
})

test('a stale PENDING request can be rejected but never approved or self-reviewed', async () => {
  const stale = record({ proposerId: 9, effectiveStatus: 'STALE' })
  const h = setup({
    pickup: true,
    api: {
      list: async () => [stale],
      review: async () =>
        record({
          proposerId: 9,
          version: 1,
          status: 'REJECTED',
          effectiveStatus: 'REJECTED',
          reviewRemark: '旧计划已变化，关闭申请'
        })
    }
  })
  h.permissions.add('rd:pickup-slip:query')
  h.permissions.add('rd:pickup-slip:approve-reduction')
  await settle()
  assert.equal(h.work.canReview(stale, 'APPROVED'), false)
  assert.equal(h.work.canReview(stale, 'REJECTED'), true)
  assert.equal(h.work.canReview({ ...stale, proposerId: 7 }, 'REJECTED'), false)
  h.work.reviewRemarks.value[10] = '旧计划已变化，关闭申请'
  await h.work.review(stale, 'APPROVED')
  assert.equal(h.calls.review.length, 0)
  await h.work.review(stale, 'REJECTED')
  assert.equal(h.calls.review.length, 1)
  assert.equal(h.calls.review[0].decision, 'REJECTED')
  assert.equal(h.work.pending.value, undefined)
  assert.equal(h.work.records.value[0].status, 'REJECTED')
  h.scope.stop()
})

test('forklift driver is hard excluded from query/proposal/review even with misconfigured permissions', async () => {
  for (const pickup of [false, true]) {
    const h = setup({ pickup, api: { list: async () => [record({ proposerId: 9 })] } })
    h.permissions.add('rd:pickup-slip:query')
    h.permissions.add('rd:pickup-slip:approve-reduction')
    h.permissions.add('*:*:*')
    await settle()
    h.roles.value = ['admin', 'warehouse', 'rd_forklift_driver']
    const reads = h.calls.list.length
    assert.equal(h.work.canQuery.value, false)
    assert.equal(h.work.showCreate.value, false)
    assert.equal(h.work.canReview(record({ proposerId: 9 }), 'APPROVED'), false)
    assert.equal(
      h.work.canReview(record({ proposerId: 9, effectiveStatus: 'STALE' }), 'REJECTED'),
      false
    )
    assert.equal(h.work.records.value.length, 0)
    draft(h)
    await h.work.refresh()
    await h.work.create()
    await h.work.review(record({ proposerId: 9 }), 'REJECTED')
    assert.equal(h.calls.list.length, reads)
    assert.equal(h.calls.create.length + h.calls.review.length, 0)
    h.scope.stop()
  }
})

test('only warehouse or super admin can propose despite legacy audit dispatch permission', async () => {
  for (const role of ['super_admin', 'rd_warehouse', 'rd_sales', 'rd_audit']) {
    const h = setup()
    h.roles.value = [role]
    await settle()
    assert.equal(h.work.canQuery.value, true)
    assert.equal(h.work.showCreate.value, ['super_admin', 'rd_warehouse'].includes(role))
    h.permissions.delete('rd:loading-task:dispatch')
    await settle()
    assert.equal(h.work.canQuery.value, true)
    assert.equal(h.work.showCreate.value, false)
    h.scope.stop()
  }
})

test('precise GET recovers a pending key outside the recent-list window without resending', async () => {
  const h = setup({
    api: {
      create: async () => {
        throw new Error('lost response')
      }
    }
  })
  await settle()
  draft(h)
  await h.work.create()
  assert.ok(h.work.pending.value)
  const oldWrites = h.calls.create.length
  h.scope.stop()
  let reads = 0
  const recovered = setup({
    storage: h.storage,
    api: { list: async () => (++reads === 1 ? [] : [record()]), get: async () => record() }
  })
  await settle()
  assert.equal(recovered.work.pending.value, undefined)
  assert.equal(recovered.work.records.value[0]?.id, 10)
  assert.equal(recovered.work.canCreate.value, false)
  await recovered.work.refresh()
  assert.ok(
    recovered.calls.get.some((input) => input.taskId === 4 && input.idempotencyKey === 'key-1')
  )
  assert.equal(h.calls.create.length, oldWrites)
  assert.equal(recovered.calls.create.length, 0)
  recovered.scope.stop()
})

test('legacy audit dispatch permission cannot propose or approve even when permissions are broadened', async () => {
  for (const pickup of [false, true]) {
    const h = setup({ pickup, api: { list: async () => [record({ proposerId: 9 })] } })
    h.roles.value = ['rd_audit']
    h.permissions.add('rd:pickup-slip:approve-reduction')
    await settle()
    assert.equal(h.work.canQuery.value, true)
    assert.equal(h.work.showCreate.value, false)
    assert.equal(h.work.canReview(record({ proposerId: 9 })), false)
    h.scope.stop()
  }
})
