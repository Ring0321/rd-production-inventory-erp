import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluate, profileModule } from './testHelpers.mjs'

const deferred = () => {
  let resolve
  const promise = new Promise((yes) => {
    resolve = yes
  })
  return { promise, resolve }
}
const drain = async () => {
  await new Promise((resolve) => setImmediate(resolve))
  await vue.nextTick()
}
const taskFeed = evaluate('./taskFeed.ts', { vue })
const summaries = evaluate('./taskSummary.ts', { './profile': profileModule })

const createHarness = (initialProfile, grants, response) => {
  const calls = []
  const request = (api) => async (params) => {
    calls.push({ api, params })
    return response ? response(api, params) : { list: [], total: 0 }
  }
  const imports = {
    vue,
    './profile': profileModule,
    './taskFeed': taskFeed,
    '@/api/rd/production/task': { RdProductionTaskApi: { getPage: request('production') } },
    '@/api/rd/stock/putaway': { RdPutawayTaskApi: { getMyPage: request('my-putaway') } },
    '@/api/rd/loading': { RdLoadingApi: { getMyTaskPage: request('my-loading') } }
  }
  const controller = evaluate('./useWorkspaceTasks.ts', imports)
  const scope = vue.effectScope()
  const profile = vue.ref(initialProfile)
  const permissions = vue.ref(new Set(grants))
  const tasks = scope.run(() => controller.useWorkspaceTasks(profile, permissions, () => true))
  return { calls, profile, permissions, tasks, scope, controller }
}

test('production requests only the real ACTIVE production PageResult', async () => {
  const h = createHarness('production', ['*:*:*'], async () => ({ list: [{ id: 9 }], total: 37 }))
  await drain()
  assert.equal(h.calls.length, 1)
  assert.equal(h.calls[0].api, 'production')
  assert.equal(h.calls[0].params.status, 'ACTIVE')
  assert.equal(h.calls[0].params.pageSize, 5)
  assert.equal(h.tasks.production.state.total, 37)
  assert.equal(h.tasks.putaway.state.status, 'unauthorized')
  h.scope.stop()
})

test('driver requests only the two own-task APIs, even with wildcard permissions', async () => {
  const h = createHarness('driver', ['*:*:*'])
  await drain()
  assert.deepEqual(h.calls.map((call) => call.api).sort(), ['my-loading', 'my-putaway'])
  assert.equal(h.calls.find((call) => call.api === 'my-loading').params.activeOnly, true)
  assert.equal(h.tasks.putaway.state.status, 'empty')
  assert.equal(h.tasks.loading.state.status, 'empty')
  assert.equal(h.tasks.production.state.status, 'unauthorized')
  h.scope.stop()
})

test('query and unauthorized profiles never request task APIs', async () => {
  for (const [id, grants] of [
    ['query', ['*:*:*']],
    ['driver', []],
    [undefined, ['*:*:*']]
  ]) {
    const h = createHarness(id, grants)
    await drain()
    assert.equal(h.calls.length, 0)
    h.scope.stop()
  }
})

test('profile switch clears all feeds synchronously and discards the old driver response', async () => {
  const pending = deferred()
  const h = createHarness('driver', ['rd:loading-task:execute'], () => pending.promise)
  assert.equal(h.tasks.loading.state.status, 'loading')
  h.profile.value = 'query'
  assert.equal(h.tasks.loading.state.status, 'unauthorized')
  pending.resolve({ list: [{ id: 7 }], total: 1 })
  await drain()
  assert.deepEqual(Array.from(h.tasks.loading.state.rows), [])
  assert.equal(h.tasks.loading.state.total, undefined)
  assert.equal(h.calls.length, 1)
  h.scope.stop()
})

test('permission changes clear data and unmount invalidates pending requests', async () => {
  const h = createHarness('driver', ['rd:putaway-task:execute'], async () => ({
    list: [{ id: 2 }],
    total: 1
  }))
  await drain()
  assert.equal(h.tasks.putaway.state.rows.length, 1)
  h.permissions.value = new Set()
  assert.equal(h.tasks.putaway.state.status, 'unauthorized')
  assert.equal(h.tasks.putaway.state.rows.length, 0)
  h.scope.stop()

  const pending = deferred()
  const stopped = createHarness('production', ['rd:production-task:query'], () => pending.promise)
  stopped.scope.stop()
  pending.resolve({ list: [{ id: 3 }], total: 1 })
  await drain()
  assert.equal(stopped.tasks.production.state.rows.length, 0)
})

test('driver summaries retain real targets, progress and quantities with canonical taskId links', () => {
  const putaway = summaries.summarizePutawayTask({
    id: 17,
    taskNo: 'PA-17',
    status: 'PENDING_CONFIRMATION',
    plannedLocationCode: 'A-02',
    packageCount: 8,
    scannedPackageCount: 3,
    exceptionPackageCount: 1,
    totalWeight: 7600.125
  })
  assert.equal(putaway.target.value, 'A-02')
  assert.equal(putaway.progress.done, 3)
  assert.equal(putaway.progress.total, 8)
  assert.equal(putaway.details.find((item) => item.label === '重量').value, '7,600.125 kg')
  assert.equal(putaway.to.name, 'PdaDriverPutaway')
  assert.equal(putaway.to.query.taskId, '17')

  const loading = summaries.summarizeLoadingTask({
    id: 18,
    taskNo: 'LD-18',
    status: 'LOADING',
    vehiclePlate: 'TEST-PLATE',
    customerName: 'Customer A',
    pickupSlipNo: 'SLIP-18',
    plannedPackageCount: 12,
    scannedPackageCount: 4,
    plannedWeight: 12000,
    scannedWeight: 4000
  })
  assert.equal(loading.target.value, 'TEST-PLATE')
  assert.equal(loading.progress.done, 4)
  assert.equal(loading.progress.total, 12)
  assert.equal(loading.to.name, 'PdaDriverLoading')
  assert.equal(loading.to.query.taskId, '18')
})

test('production summaries use task context and never manufacture production quantities', () => {
  const task = {
    id: 20,
    taskNo: 'SC-20',
    status: 'ACTIVE',
    customerName: 'Customer B',
    productionDate: '2026-10-11',
    shiftCode: 'A'
  }
  const readonly = summaries.summarizeProductionTask(
    task,
    new Set(['rd:production-task:query']),
    () => true
  )
  assert.equal(readonly.to.name, 'RdProductionTask')
  assert.equal(readonly.to.query.productionTaskId, '20')
  assert.equal(readonly.progress, undefined)
  assert.ok(!readonly.details.some((item) => /重量|包数/.test(item.label)))
  const writable = summaries.summarizeProductionTask(task, new Set(['*:*:*']), () => true)
  assert.equal(writable.to.name, 'PdaProduction')
})

test('missing task fields stay missing instead of becoming zero quantities', () => {
  const task = summaries.summarizeLoadingTask({ id: 1, taskNo: 'LD-1', status: 'UNKNOWN' })
  assert.equal(task.target.value, '未记录')
  assert.equal(task.status, 'UNKNOWN')
  assert.equal(task.progress, undefined)
  assert.ok(task.details.every((item) => item.value === '未记录'))
  const malformed = summaries.summarizeLoadingTask({
    id: 2,
    taskNo: 'LD-2',
    status: 'constructor',
    plannedWeight: ''
  })
  assert.equal(malformed.status, 'constructor')
  assert.equal(malformed.details.find((item) => item.label === '计划重量').value, '未记录')
})

test('home lookup and readonly profile selection use the actual Vue script', async () => {
  const h = createHarness('query', [])
  h.scope.stop()
  const pushes = []
  const user = vue.reactive({
    getIsSetUser: true,
    getRoles: ['rd_readonly'],
    getPermissions: new Set(['*:*:*'])
  })
  const imports = {
    vue,
    'vue-router': {
      useRoute: () => vue.reactive({ name: 'PdaWorkspace' }),
      useRouter: () => ({
        hasRoute: () => true,
        push: async (target) => {
          pushes.push(target)
        }
      })
    },
    'element-plus': { ElButton: {}, ElInput: {}, ElOption: {}, ElSelect: {}, ElTooltip: {} },
    '@element-plus/icons-vue': { RefreshRight: {}, Search: {} },
    '@/store/modules/user': { useUserStore: () => user },
    './EntryLinks.vue': { default: {} },
    './TaskGroup.vue': { default: {} },
    './profile': profileModule,
    './useWorkspaceTasks': h.controller,
    './taskSummary': summaries
  }
  const component = evaluate('./index.vue', imports).default
  const scope = vue.effectScope()
  const bindings = scope.run(() => component.setup({}, { expose: () => {} }))
  assert.equal(bindings.profile.value, 'query')
  assert.equal(bindings.profiles.value.length, 1)
  bindings.profile.value = 'production'
  assert.equal(bindings.profile.value, 'query')
  bindings.packageInput.value = 'https://untrusted.example/pkg/BAG-90?next=external'
  await bindings.lookupPackage()
  assert.equal(pushes[0].name, 'PackageTrace')
  assert.equal(pushes[0].params.packageCode, 'BAG-90')
  assert.equal(pushes[0].query.workMode, 'pda')
  assert.equal(h.calls.length, 0)
  bindings.packageInput.value = 'https://untrusted.example/redirect'
  await bindings.lookupPackage()
  assert.equal(pushes.length, 1)
  assert.ok(bindings.lookupError.value)
  scope.stop()
})

const shellHarness = (confirm = async () => true, loginOut = async () => true) => {
  const calls = []
  const route = vue.reactive({
    name: 'PdaDriverLoading',
    path: '/pda/driver-loading',
    fullPath: '/pda/driver-loading?taskId=18'
  })
  const history = { state: { back: null } }
  const router = {
    options: { history },
    push: async (target) => {
      calls.push(['push', target])
    },
    back: () => {
      calls.push(['back'])
    },
    replace: async (target) => {
      calls.push(['replace', target])
    }
  }
  const user = vue.reactive({
    getRoles: ['rd_forklift_driver'],
    getIsSetUser: true,
    getUser: { nickname: 'Test Operator' },
    loginOut: async () => {
      await loginOut()
      calls.push(['logout'])
    }
  })
  const imports = {
    vue,
    'vue-router': { RouterView: {}, useRoute: () => route, useRouter: () => router },
    'element-plus': { ElButton: {}, ElTooltip: {}, ElMessageBox: { confirm } },
    '@element-plus/icons-vue': { ArrowLeft: {}, Monitor: {}, SwitchButton: {} },
    '@/config/brand': {
      productBrand: { name: '垛序 ERP', shortName: '垛序', logoUrl: '/duoxu-mark.png' }
    },
    '@/store/modules/user': { useUserStore: () => user },
    '@/store/modules/tagsView': {
      useTagsViewStore: () => ({
        delAllViews: () => {
          calls.push(['clear-tags'])
        }
      })
    },
    './profile': profileModule
  }
  const scope = vue.effectScope()
  const component = evaluate('./layout.vue', imports).default
  const bindings = scope.run(() => component.setup({}, { expose: () => {} }))
  return { calls, route, history, user, bindings, scope }
}

test('shell back uses PDA history only, otherwise returns to the canonical home', async () => {
  const h = shellHarness()
  h.history.state.back = 'https://untrusted.example'
  h.bindings.goBack()
  assert.equal(h.calls[0][0], 'push')
  assert.equal(h.calls[0][1].name, 'PdaWorkspace')
  h.history.state.back = '/pda/production?productionTaskId=9'
  h.bindings.goBack()
  assert.equal(h.calls[1][0], 'back')
  h.route.path = '/pda'
  h.route.name = 'PdaWorkspace'
  assert.equal(h.bindings.atHome.value, true)
  h.scope.stop()
})

test('return to ERP is available at PDA home and child pages without signing out or changing roles', async () => {
  for (const [name, path] of [
    ['PdaWorkspace', '/pda'],
    ['PdaDriverLoading', '/pda/driver-loading']
  ]) {
    const h = shellHarness()
    h.route.name = name
    h.route.path = path
    const roles = [...h.user.getRoles]
    await h.bindings.goToErp()
    assert.equal(h.calls.length, 1)
    assert.equal(h.calls[0][0], 'push')
    assert.equal(h.calls[0][1].name, 'Index')
    assert.equal(h.user.getIsSetUser, true)
    assert.deepEqual([...h.user.getRoles], roles)
    h.scope.stop()
  }
})

test('successful logout uses the existing stores and preserves the exact PDA return target', async () => {
  const h = shellHarness()
  await h.bindings.logout()
  assert.deepEqual(
    h.calls.map((call) => call[0]),
    ['logout', 'clear-tags', 'replace']
  )
  const target = h.calls[2][1]
  assert.equal(target.path, '/login')
  assert.equal(target.query.redirect, '/pda/driver-loading?taskId=18')
  assert.equal(h.bindings.logoutPending.value, false)
  h.scope.stop()
})

test('cancelled or failed logout never navigates away and remains retryable', async () => {
  const cancelled = shellHarness(async () => {
    throw 'cancel'
  })
  await cancelled.bindings.logout()
  assert.equal(cancelled.calls.length, 0)
  assert.equal(cancelled.bindings.logoutError.value, '')
  cancelled.scope.stop()
  let fail = true
  const h = shellHarness(undefined, async () => {
    if (fail) throw new Error('offline')
  })
  await h.bindings.logout()
  assert.equal(h.calls.length, 0)
  assert.ok(h.bindings.logoutError.value)
  assert.equal(h.bindings.logoutPending.value, false)
  fail = false
  await h.bindings.logout()
  assert.equal(h.calls[2][0], 'replace')
  h.scope.stop()
})
