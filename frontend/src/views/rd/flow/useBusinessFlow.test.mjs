import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluateSource, deferred, flush } from '../trace/package/testHelpers.mjs'

const facts = evaluateSource(
  readFileSync(new URL('./flowFacts.ts', import.meta.url), 'utf8'),
  {},
  { Error }
)
const source = readFileSync(new URL('./useBusinessFlow.ts', import.meta.url), 'utf8')
const bag = (id = 1, task = 7) => ({
  id,
  packageCode: `P${id}`,
  productionTaskId: task,
  productionOutputId: 9,
  status: 'IN_STOCK',
  packageWeight: 800
})
const setup = (overrides = {}) => {
  const user = vue.reactive({
    getUser: { id: 5 },
    getRoles: ['super_admin'],
    getPermissions: overrides.permissions || new Set(['*:*:*'])
  })
  let revision = 1
  let listener
  let token = true
  const unmounts = []
  const calls = []
  const imports = {
    vue: { ...vue, onBeforeUnmount: (fn) => unmounts.push(fn), onDeactivated: () => {} },
    '@/store/modules/user': { useUserStore: () => user },
    '@/utils/auth': {
      getAccessToken: () => token,
      getAuthContextRevision: () => revision,
      onAuthContextChange: (fn) => {
        listener = fn
        return () => {
          listener = undefined
        }
      }
    },
    '@/api/rd/production/task': {
      RdProductionTaskApi: {
        get: async (id) => {
          calls.push(['task', id])
          return { id, customerName: '演示客户', taskNo: 'T' }
        },
        getPage: async () => ({ list: [], total: 0 }),
        ...overrides.task
      }
    },
    '@/api/rd/stock/package': {
      RdPackageUnitApi: {
        getTaskPackages: async (id) => {
          calls.push(['members', id])
          return [bag(id, id)]
        },
        getTraceDetail: async (code) => ({
          packageCode: code,
          productionTaskId: Number(code.slice(1)),
          status: 'IN_STOCK',
          internalView: true,
          events: []
        }),
        getWorkContext: async (code) => ({ packageCode: code, tasks: [] }),
        ...overrides.packages
      }
    },
    './flowFacts': facts
  }
  const { useBusinessFlow } = evaluateSource(source, imports, { Date, Number, Set, Error })
  const state = useBusinessFlow()
  return {
    state,
    user,
    calls,
    changeAuth: () => {
      revision++
      listener?.()
    },
    removeToken: () => {
      token = false
    },
    unmount: () => unmounts.forEach((fn) => fn())
  }
}

test('loads only actual task members and matching internal trace', async () => {
  const ctx = setup()
  await ctx.state.loadTask(7)
  assert.equal(ctx.state.task.value.id, 7)
  assert.equal(ctx.state.summary.value.weightKg, 800)
  assert.equal(ctx.state.trace.value.packageCode, 'P7')
  assert.deepEqual(ctx.calls, [
    ['task', 7],
    ['members', 7]
  ])
  ctx.unmount()
})

test('late task response cannot replace the newer chosen task or read old members', async () => {
  const slow = deferred()
  const ctx = setup({
    task: {
      get: (id) => (id === 7 ? slow.promise : Promise.resolve({ id, customerName: '演示客户' }))
    }
  })
  const old = ctx.state.loadTask(7)
  await ctx.state.loadTask(8)
  slow.resolve({ id: 7, customerName: '旧客户' })
  await old
  assert.equal(ctx.state.task.value.id, 8)
  assert.equal(
    ctx.calls.some(([key, id]) => key === 'members' && id === 7),
    false
  )
  ctx.unmount()
})

test('identity transition clears immediately and ignores already-in-flight data', async () => {
  const slow = deferred()
  const ctx = setup({ packages: { getTaskPackages: () => slow.promise } })
  const load = ctx.state.loadTask(7)
  await flush()
  ctx.changeAuth()
  slow.resolve([bag(7, 7)])
  await load
  assert.equal(ctx.state.contextValid.value, false)
  assert.equal(ctx.state.packages.value.length, 0)
  assert.equal(ctx.state.task.value, undefined)
  ctx.unmount()
})

test('mixed task members fail as a whole, never producing a believable partial total', async () => {
  const ctx = setup({ packages: { getTaskPackages: async () => [bag(7, 7), bag(8, 8)] } })
  await ctx.state.loadTask(7)
  assert.equal(ctx.state.task.value, undefined)
  assert.equal(ctx.state.packages.value.length, 0)
  assert.match(ctx.state.error.value, /成员不一致/)
  ctx.unmount()
})

test('changed trace status and public projection are not presented as consistent internal evidence', async () => {
  for (const extra of [
    { status: 'OUTBOUNDED', internalView: true },
    { status: 'IN_STOCK', internalView: false }
  ]) {
    const ctx = setup({
      packages: {
        getTraceDetail: async (code) => ({ packageCode: code, productionTaskId: 7, ...extra })
      }
    })
    await ctx.state.loadTask(7)
    assert.equal(ctx.state.trace.value, undefined)
    assert.match(ctx.state.detailError.value, /已变化/)
    ctx.unmount()
  }
})

test('driver or logged-out context never issues business queries', async () => {
  const ctx = setup()
  ctx.user.getRoles = ['rd_forklift_driver']
  await ctx.state.loadTask(7)
  await ctx.state.searchTasks()
  assert.equal(ctx.calls.length, 0)
  ctx.unmount()
  const absent = setup()
  absent.removeToken()
  await absent.state.loadTask(7)
  assert.equal(absent.calls.length, 0)
  absent.unmount()
})

test('server list failure is not reported as a successful empty business dataset', async () => {
  const ctx = setup({
    task: {
      getPage: async () => {
        throw new Error('network')
      }
    }
  })
  await ctx.state.searchTasks()
  assert.match(ctx.state.searchError.value, /查询失败/)
  assert.equal(ctx.state.total.value, 0)
  ctx.unmount()
})

test('late package detail is discarded after another bag is selected', async () => {
  const slow = deferred()
  const ctx = setup({
    packages: {
      getTaskPackages: async () => [bag(7, 7), bag(8, 7)],
      getTraceDetail: (code) =>
        code === 'P8'
          ? slow.promise
          : Promise.resolve({
              packageCode: code,
              productionTaskId: 7,
              status: 'IN_STOCK',
              internalView: true,
              events: []
            })
    }
  })
  await ctx.state.loadTask(7)
  const late = ctx.state.selectPackage('P8')
  await ctx.state.selectPackage('P7')
  slow.resolve({ packageCode: 'P8', productionTaskId: 7, status: 'IN_STOCK', internalView: true })
  await late
  assert.equal(ctx.state.trace.value.packageCode, 'P7')
  ctx.unmount()
})

test('incomplete work-context reads remain unknown, never presenting all in-stock bags as available', async () => {
  const ctx = setup({
    packages: {
      getWorkContext: async () => {
        throw new Error('unavailable')
      }
    }
  })
  await ctx.state.loadTask(7)
  assert.equal(ctx.state.task.value.id, 7)
  assert.equal(ctx.state.summary.value.workKnown, false)
  assert.match(ctx.state.workError.value, /不代表可用库存/)
  ctx.unmount()
})

test('single-domain viewer cannot read or expose the other domain through selected-package details', async () => {
  let reads = 0
  const ctx = setup({
    permissions: new Set([
      'rd:production-task:query',
      'rd:package-unit:query',
      'rd:putaway-task:query'
    ]),
    packages: {
      getWorkContext: async (code) => {
        reads++
        return {
          packageCode: code,
          tasks: [{ type: 'LOADING', taskId: 40, taskNo: 'HIDDEN', status: 'EXCEPTION_PENDING' }]
        }
      }
    }
  })
  await ctx.state.loadTask(7)
  await ctx.state.selectPackage('P7')
  assert.equal(reads, 0)
  assert.equal(ctx.state.context.value, undefined)
  assert.equal(ctx.state.summary.value.workKnown, false)
  assert.match(ctx.state.workError.value, /无完整仓储作业查看权限/)
  ctx.unmount()
})
