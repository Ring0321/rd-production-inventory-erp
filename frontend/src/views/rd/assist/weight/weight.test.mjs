import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { runInNewContext } from 'node:vm'
import { test } from 'node:test'

// A separate checkout may reuse installed dependencies without changing its lockfile.
const require = createRequire(
  process.env.RD_WEIGHT_TEST_PACKAGE || new URL('../../../../../package.json', import.meta.url)
)
const ts = require('typescript')
const vue = require('vue')
const evaluate = (file, imports, globals = {}) => {
  const module = { exports: {} }
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  })
  runInNewContext(outputText, {
    module,
    exports: module.exports,
    require: (name) => {
      assert.ok(name in imports, `Unexpected dependency ${name}`)
      return imports[name]
    },
    ...globals
  })
  return module.exports
}
const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const plain = (value) => JSON.parse(JSON.stringify(value))
const setup = () => {
  const identity = vue.reactive({
    id: 7,
    tenant: 1,
    token: true,
    roles: ['rd_warehouse'],
    permissions: [
      'rd:pickup-slip:query',
      'rd:package-unit:query',
      'rd:loading-task:dispatch',
      'rd:loading-task:query'
    ]
  })
  let revision = 0
  const callbacks = new Set()
  const hooks = {}
  const events = new Map()
  const timers = new Set()
  const window = {
    addEventListener: (name, fn) => events.set(name, fn),
    removeEventListener: (name) => events.delete(name),
    setInterval: (fn) => {
      timers.add(fn)
      return fn
    },
    clearInterval: (fn) => timers.delete(fn)
  }
  const document = { ...window }
  const calls = { page: [], detail: [], suggest: [], tasks: [] }
  const api = Object.fromEntries(
    Object.keys(calls).map((name) => [
      name,
      (body) => {
        const work = { body: plain(body), ...deferred() }
        calls[name].push(work)
        return work.promise
      }
    ])
  )
  const auth = {
    getAccessToken: () => identity.token,
    getCurrentUserId: () => identity.id,
    getTenantId: () => identity.tenant,
    getVisitTenantId: () => undefined,
    getAuthContextRevision: () => revision,
    getCachedAuthScope: () => ({
      userId: identity.id,
      roles: [...identity.roles].sort(),
      permissions: [...identity.permissions].sort()
    }),
    onAuthContextChange: (fn) => {
      callbacks.add(fn)
      return () => callbacks.delete(fn)
    }
  }
  const route = vue.reactive({ fullPath: '/operations-assist' })
  const navigations = []
  const module = evaluate(
    './useWeightAssistant.ts',
    {
      vue: {
        ...vue,
        ...Object.fromEntries(
          ['onActivated', 'onDeactivated', 'onBeforeUnmount'].map((key) => [
            key,
            (fn) => {
              hooks[key] = fn
            }
          ])
        )
      },
      'vue-router': {
        useRoute: () => route,
        useRouter: () => ({ push: (value) => navigations.push(plain(value)) })
      },
      '@/api/rd/weight': { RdWeightApi: api },
      '@/utils/auth': auth,
      '@/store/modules/user': {
        useUserStore: () => ({
          getUser: identity,
          get getRoles() {
            return identity.roles
          },
          get getPermissions() {
            return new Set(identity.permissions)
          }
        })
      }
    },
    { window, document }
  )
  const assistant = module.useWeightAssistant()
  return {
    assistant,
    calls,
    identity,
    hooks,
    events,
    timers,
    route,
    navigations,
    authChanged: () => {
      revision++
      callbacks.forEach((fn) => fn())
    }
  }
}
const slip = { id: 1, slipNo: 'S1', status: 'ISSUED', customerName: 'CUSTOMER' }
const detail = {
  slip,
  lines: [
    { id: 11, slipId: 1, productModel: 'M1', requestedWeight: 1000, requestedPackageCount: 2 }
  ]
}
const result = {
  pickupSlipId: 1,
  pickupSlipLineId: 11,
  solverStatus: 'OPTIMAL',
  withinTolerance: true,
  totalWeightKg: 1000,
  targetWeightKg: 1000,
  toleranceKg: 0,
  deviationKg: 0,
  shortfallKg: 0,
  weightScale: 3,
  candidateCount: 2,
  candidateScanComplete: true,
  bags: [
    {
      packageId: 1,
      packageCode: 'PK1',
      packageWeightKg: 500,
      locationId: 20,
      locationCode: 'A-01',
      stockLotId: 10
    },
    {
      packageId: 2,
      packageCode: 'PK2',
      packageWeightKg: 500,
      locationId: 20,
      locationCode: 'A-01',
      stockLotId: 10
    }
  ]
}
const choose = async (env, value = detail) => {
  const work = env.assistant.selectSlip(1)
  env.calls.detail.at(-1).resolve(value)
  await work
}

test('requires all permissions and rejects driver even with wildcard legacy grants', () => {
  for (const missing of [
    'rd:pickup-slip:query',
    'rd:package-unit:query',
    'rd:loading-task:dispatch'
  ]) {
    const env = setup()
    env.identity.permissions = env.identity.permissions.filter((p) => p !== missing)
    assert.equal(env.assistant.refreshAccess(), false)
    env.assistant.loadSlips()
    assert.equal(env.calls.page.length, 0)
  }
  const env = setup()
  env.identity.roles = ['rd_forklift_driver']
  env.identity.permissions = ['*:*:*']
  assert.equal(env.assistant.refreshAccess(), false)
})

test('mixed model requires an explicit original line and sends no client candidates or weights', async () => {
  const env = setup()
  await choose(env, {
    slip,
    lines: [...detail.lines, { ...detail.lines[0], id: 12, productModel: 'M2' }]
  })
  assert.equal(env.assistant.lineId.value, undefined)
  assert.equal(env.assistant.canSuggest.value, false)
  env.assistant.lineId.value = 11
  env.assistant.target.value = 1000
  env.assistant.tolerance.value = 0
  const work = env.assistant.suggest()
  assert.deepEqual(env.calls.suggest[0].body, {
    pickupSlipId: 1,
    pickupSlipLineId: 11,
    targetWeightKg: 1000,
    toleranceKg: 0
  })
  env.calls.suggest[0].resolve(result)
  await work
  assert.equal(env.assistant.result.value.bags.length, 2)
})

test('target plus tolerance cannot exceed line authorization and excess precision is rejected', async () => {
  const env = setup()
  await choose(env)
  env.assistant.target.value = 999
  env.assistant.tolerance.value = 2
  assert.equal(env.assistant.canSuggest.value, false)
  env.assistant.target.value = 999.0001
  env.assistant.tolerance.value = 0
  assert.equal(env.assistant.canSuggest.value, false)
})

test('auth revision clears private data immediately and drops late success', async () => {
  const env = setup()
  await choose(env)
  const work = env.assistant.suggest()
  env.authChanged()
  assert.equal(env.assistant.detail.value, undefined)
  assert.equal(env.assistant.slips.value.length, 0)
  env.calls.suggest[0].resolve(result)
  await work
  assert.equal(env.assistant.result.value, undefined)
  assert.equal(env.assistant.allowed.value, false)
})

test('cross-tab token replacement clears facts even when owner and grants stay the same', async () => {
  const env = setup()
  await choose(env)
  const work = env.assistant.suggest()
  env.events.get('storage')({ key: 'ACCESS_TOKEN' })
  assert.equal(env.assistant.detail.value, undefined)
  env.calls.suggest[0].resolve(result)
  await work
  assert.equal(env.assistant.result.value, undefined)
  assert.equal(env.assistant.allowed.value, false)
})

test('owner tenant permission route and lifecycle changes discard late results', async () => {
  for (const mutate of [
    (e) => {
      e.identity.id = 9
    },
    (e) => {
      e.identity.tenant = 2
    },
    (e) => {
      e.identity.permissions = []
    },
    (e) => {
      e.route.fullPath = '/other'
    },
    (e) => e.hooks.onDeactivated(),
    (e) => e.hooks.onBeforeUnmount()
  ]) {
    const env = setup()
    await choose(env)
    const work = env.assistant.suggest()
    mutate(env)
    env.calls.suggest[0].resolve(result)
    await work
    assert.equal(env.assistant.result.value, undefined)
  }
})

test('changed selection or input invalidates a computed plan', async () => {
  const env = setup()
  await choose(env)
  const work = env.assistant.suggest()
  env.assistant.target.value = 900
  env.calls.suggest[0].resolve(result)
  await work
  assert.equal(env.assistant.result.value, undefined)
})

test('slow pickup detail cannot replace a newer selection', async () => {
  const env = setup()
  const old = env.assistant.selectSlip(1)
  const next = env.assistant.selectSlip(2)
  env.calls.detail[1].resolve({
    slip: { ...slip, id: 2 },
    lines: [{ ...detail.lines[0], id: 21, slipId: 2 }]
  })
  await next
  env.calls.detail[0].resolve(detail)
  await old
  assert.equal(env.assistant.detail.value.slip.id, 2)
  assert.equal(env.assistant.lineId.value, 21)
})

test('malformed or out-of-tolerance solver response cannot become a plan', async () => {
  for (const change of [
    { pickupSlipLineId: 99 },
    { totalWeightKg: 999 },
    { bags: [result.bags[0], result.bags[0]] },
    { withinTolerance: false },
    { solverStatus: 'UNKNOWN', bags: result.bags }
  ]) {
    const env = setup()
    await choose(env)
    const work = env.assistant.suggest()
    env.calls.suggest[0].resolve({ ...result, ...change })
    await work
    assert.equal(env.assistant.result.value, undefined)
    assert.ok(env.assistant.error.value)
  }
})

test('late original task lookup never navigates after identity changes', async () => {
  const env = setup()
  await choose(env)
  const work = env.assistant.openOriginal()
  env.authChanged()
  env.calls.tasks[0].resolve({ list: [{ id: 81, pickupSlipId: 1, status: 'ASSIGNED' }], total: 1 })
  await work
  assert.equal(env.navigations.length, 0)
})

test('403 clears all facts and stops subsequent requests', async () => {
  const env = setup()
  await choose(env)
  const work = env.assistant.suggest()
  env.calls.suggest[0].reject({ response: { status: 403 } })
  await work
  assert.equal(env.assistant.detail.value, undefined)
  assert.equal(env.assistant.allowed.value, false)
  env.assistant.loadSlips()
  assert.equal(env.calls.page.length, 0)
})

test('non-solution status is retained without claiming a plan, navigation is always mode=view', async () => {
  const env = setup()
  await choose(env)
  const work = env.assistant.suggest()
  env.calls.suggest[0].resolve({
    ...result,
    solverStatus: 'UNKNOWN',
    withinTolerance: false,
    bags: [],
    totalWeightKg: null
  })
  await work
  assert.equal(env.assistant.result.value.solverStatus, 'UNKNOWN')
  const navigation = env.assistant.openOriginal()
  env.calls.tasks[0].resolve({ list: [{ id: 81, pickupSlipId: 1, status: 'ASSIGNED' }], total: 1 })
  await navigation
  assert.deepEqual(env.navigations[0], {
    name: 'RdLoadingDispatch',
    query: { taskId: '81', mode: 'view' }
  })
})

test('default embeddable Vue component compiles without router or package changes', () => {
  const { parse, compileScript, compileTemplate } = require('vue/compiler-sfc')
  const source = readFileSync(new URL('./WeightAssistant.vue', import.meta.url), 'utf8')
  const { descriptor, errors } = parse(source)
  assert.equal(errors.length, 0)
  assert.ok(compileScript(descriptor, { id: 'weight-assistant' }).content)
  assert.equal(
    compileTemplate({
      source: descriptor.template.content,
      filename: 'WeightAssistant.vue',
      id: 'weight-assistant'
    }).errors.length,
    0
  )
  assert.doesNotMatch(source, /v-html|createTask|confirmOutbound|GLM/)
})
