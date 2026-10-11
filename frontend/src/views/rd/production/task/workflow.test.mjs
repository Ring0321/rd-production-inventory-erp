import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import * as vue from 'vue'
import { parse, compileScript } from 'vue/compiler-sfc'
import { bindProductionDraft, restoreProductionDraft } from './draftRequest.ts'

const taskFixture = () => ({
  id: 12,
  taskNo: 'SC-TEST',
  productionDate: '2026-10-11',
  customerName: 'Customer A',
  shiftCode: 'A',
  status: 'ACTIVE',
  version: 0
})
const outputFixture = () => ({
  id: 50,
  productionTaskId: 12,
  productionDate: '2026-10-11',
  customerName: 'Customer A',
  productModel: 'Model A',
  furnaceNo: 'F-1',
  furnaceLayer: 'L-1',
  outputQty: 2,
  packageWeight: 875.125,
  outputWeight: 1750.25,
  inboundQty: 0,
  status: 'DRAFT',
  version: 0
})
const flush = async () => {
  await vue.nextTick()
  await new Promise((resolve) => setImmediate(resolve))
}

// Execute the real setup/composable with isolated API and browser-storage boundaries.
const evaluate = (source, mocks, globals = {}) => {
  const module = { exports: {} }
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  })
  runInNewContext(outputText, {
    module,
    exports: module.exports,
    require: (name) => {
      assert.ok(name in mocks, `Unexpected dependency: ${name}`)
      return mocks[name]
    },
    crypto: globalThis.crypto,
    ...globals
  })
  return module.exports
}

test('task API uses the accepted verbs, versioned action body, and direct page result', async () => {
  const calls = []
  const page = { list: [taskFixture()], total: 1 }
  const request = {
    get: async (options) => {
      calls.push({ verb: 'GET', ...options })
      return options.url.endsWith('/page') ? page : taskFixture()
    },
    post: async (options) => {
      calls.push({ verb: 'POST', ...options })
      return 12
    },
    put: async (options) => {
      calls.push({ verb: 'PUT', ...options })
      return true
    }
  }
  const { RdProductionTaskApi: api } = evaluate(
    readFileSync(new URL('../../../../api/rd/production/task/index.ts', import.meta.url), 'utf8'),
    { '@/config/axios': { __esModule: true, default: request } }
  )
  assert.equal(await api.getPage({ pageNo: 1, pageSize: 10 }), page)
  await api.get(12)
  await api.create({ customerName: 'Customer A', productionDate: '2026-10-11', shiftCode: 'A' })
  await api.update({
    id: 12,
    version: 0,
    customerName: 'Customer A',
    productionDate: '2026-10-11',
    shiftCode: 'B'
  })
  await api.close({ id: 12, version: 1 })
  await api.cancel({ id: 13, version: 0 })
  assert.equal(calls[1].params.id, 12)
  assert.equal(calls[2].verb, 'POST')
  for (const index of [3, 4, 5]) assert.equal(calls[index].verb, 'PUT')
  assert.equal(calls[4].url, '/rd/production-task/close')
  assert.equal(calls[4].data.id, 12)
  assert.equal(calls[4].data.version, 1)
  assert.equal(calls[5].url, '/rd/production-task/cancel')
})

const outputHarness = (permissions) => {
  const scope = vue.effectScope()
  const task = vue.ref(taskFixture())
  const output = outputFixture()
  const packages = []
  const calls = { page: [], confirm: [], generate: [], routes: [] }
  const backend = { getPage: undefined, freshOutput: output }
  const api = {
    getPage: async (params) => {
      calls.page.push(params)
      return backend.getPage ? backend.getPage(params) : { list: [output], total: 23 }
    },
    get: async () => backend.freshOutput,
    confirm: async (data) => {
      calls.confirm.push(data)
      output.status = 'CONFIRMED'
      output.version++
    }
  }
  const exports = evaluate(readFileSync(new URL('./useTaskOutputs.ts', import.meta.url), 'utf8'), {
    vue,
    'vue-router': { useRouter: () => ({ push: (route) => calls.routes.push(route) }) },
    '@/directives/permission/hasPermi': {
      hasPermission: (rights) => rights.some((right) => permissions.includes(right))
    },
    '@/hooks/web/useMessage': {
      useMessage: () => ({ confirm: async () => true, success: () => {} })
    },
    '@/api/rd/production/task': { RdProductionTaskApi: { get: async () => task.value } },
    '@/api/rd/production/output': { RdProductionOutputApi: api },
    '@/api/rd/stock/package': {
      RdPackageUnitApi: {
        getTaskPackages: async () => packages,
        generate: async (data) => {
          calls.generate.push(data)
          packages.push({ id: 80, productionOutputId: data.productionOutputId })
          return packages
        }
      }
    }
  })
  const state = scope.run(() => exports.useTaskOutputs(task))
  return { state, scope, task, output, calls, backend }
}

test('linked records use direct PageResult and the exact task filter on every page', async (t) => {
  const h = outputHarness(['rd:production-output:query'])
  t.after(() => h.scope.stop())
  await flush()
  assert.equal(h.calls.page[0].productionTaskId, 12)
  assert.equal(h.state.total.value, 23)
  assert.equal(h.state.outputs.value[0].id, 50)
  h.state.page.value = 2
  await h.state.loadOutputs()
  assert.equal(h.calls.page.at(-1).pageNo, 2)
  assert.equal(h.calls.page.at(-1).productionTaskId, 12)
})

test('confirm is explicitly gated by output:confirm and sends the current ID/version', async (t) => {
  const h = outputHarness(['rd:production-output:query', 'rd:production-output:confirm'])
  t.after(() => h.scope.stop())
  await flush()
  await h.state.runAction(h.output, 'confirm')
  assert.equal(h.calls.confirm.length, 1)
  assert.equal(h.calls.confirm[0].id, 50)
  assert.equal(h.calls.confirm[0].version, 0)
  assert.equal(h.calls.generate.length, 0)
  assert.equal(h.calls.routes.length, 0)
})

test('query-only users cannot confirm, generate, or launch a first-print action', async (t) => {
  const h = outputHarness(['rd:production-output:query', 'rd:package-unit:query'])
  t.after(() => h.scope.stop())
  await flush()
  assert.equal(h.state.canConfirm.value, false)
  assert.equal(h.state.canGenerate.value, false)
  await h.state.runAction(h.output, 'confirm')
  await h.state.runAction(h.output, 'generate')
  h.state.openLabels('first')
  assert.equal(h.calls.confirm.length, 0)
  assert.equal(h.calls.generate.length, 0)
  assert.equal(h.calls.routes.length, 0)
  h.task.value.status = 'CLOSED'
  h.state.openLabels('history')
  assert.equal(h.calls.routes[0].query.productionTaskId, '12')
  assert.equal('labelAction' in h.calls.routes[0].query, false)
})

test('stale records are refreshed without silently confirming a changed version', async (t) => {
  const h = outputHarness(['rd:production-output:query', 'rd:production-output:confirm'])
  t.after(() => h.scope.stop())
  await flush()
  h.backend.freshOutput = { ...h.output, version: 1 }
  await h.state.runAction(h.output, 'confirm')
  assert.equal(h.calls.confirm.length, 0)
  assert.ok(h.state.actionError.value)
})

test('generation uses the actual integer count and is blocked for closed tasks', async (t) => {
  const h = outputHarness(['rd:package-unit:generate', 'rd:package-unit:query'])
  t.after(() => h.scope.stop())
  h.output.status = 'CONFIRMED'
  await flush()
  await h.state.runAction(h.output, 'generate')
  assert.equal(h.calls.generate[0].productionOutputId, 50)
  assert.equal(h.calls.generate[0].packageCount, 2)
  assert.equal(h.state.packageCount(50), 1)
  h.task.value.status = 'CLOSED'
  await h.state.runAction(h.output, 'generate')
  assert.equal(h.calls.generate.length, 1)
})

test('first labels route with taskPrint, while reprint/history keep the ordinary task filter', async (t) => {
  const h = outputHarness([
    'rd:package-unit:query',
    'rd:label-print:print',
    'rd:label-print:reprint'
  ])
  t.after(() => h.scope.stop())
  h.state.openLabels('first')
  h.state.openLabels('reprint')
  assert.equal(h.calls.routes[0].name, 'RdPackageUnit')
  assert.equal(h.calls.routes[0].query.productionTaskId, '12')
  assert.equal(h.calls.routes[0].query.labelAction, 'taskPrint')
  assert.equal(h.calls.routes[1].query.productionTaskId, '12')
  assert.equal('labelAction' in h.calls.routes[1].query, false)
})

test('late responses from a previously selected task do not replace the current records', async (t) => {
  const h = outputHarness(['rd:production-output:query'])
  t.after(() => h.scope.stop())
  await flush()
  const responses = []
  h.backend.getPage = () => new Promise((resolve) => responses.push(resolve))
  const oldLoad = h.state.loadOutputs()
  h.task.value = { ...taskFixture(), id: 13 }
  await flush()
  responses[1]({ list: [{ ...outputFixture(), id: 51, productionTaskId: 13 }], total: 1 })
  await flush()
  responses[0]({ list: [outputFixture()], total: 23 })
  await oldLoad
  assert.equal(h.state.outputs.value[0].productionTaskId, 13)
  assert.equal(h.state.total.value, 1)
})

const pdaHarness = (storage = new Map()) => {
  const scope = vue.effectScope()
  const mounted = []
  const user = { user: { id: 7 } }
  const permissions = vue.reactive(['rd:production-task:query', 'rd:production-output:create'])
  const calls = { create: [], get: [], confirm: [], generate: [], inbound: [], refresh: 0 }
  const backend = {
    createError: undefined,
    getError: undefined,
    waitForCreate: undefined,
    freshTask: taskFixture(),
    savedOutput: outputFixture()
  }
  const sessionStorage = {
    getItem: (key) => storage.get(key) ?? null,
    setItem: (key, value) => storage.set(key, value),
    removeItem: (key) => storage.delete(key)
  }
  const { descriptor } = parse(
    readFileSync(new URL('../../pda/production/index.vue', import.meta.url), 'utf8')
  )
  const script = compileScript(descriptor, { id: 'pda-workflow-test' })
  const component = evaluate(
    script.content,
    {
      vue: { ...vue, onMounted: (callback) => mounted.push(callback), onUnmounted: () => {} },
      'vue-router': { useRoute: () => ({ query: {} }), onBeforeRouteLeave: () => {} },
      '@/hooks/web/useMessage': {
        useMessage: () => ({
          confirm: async () => true,
          success: () => {},
          error: () => {},
          warning: () => {}
        })
      },
      '@/directives/permission/hasPermi': {
        hasPermission: (rights) => rights.some((right) => permissions.includes(right))
      },
      '@/store/modules/user': { useUserStore: () => user },
      '@/utils/auth': { getTenantId: () => '1' },
      '@/api/rd/masterdata/customer-product': {
        RdCustomerProductApi: { getModelOptions: async () => ['Model A'] }
      },
      '@/api/rd/production/task': { RdProductionTaskApi: { get: async () => backend.freshTask } },
      '@/api/rd/production/output': {
        RdProductionOutputApi: {
          create: async (payload) => {
            calls.create.push(payload)
            if (backend.createError) throw backend.createError
            return backend.waitForCreate ? await backend.waitForCreate : 50
          },
          get: async (id) => {
            calls.get.push(id)
            if (backend.getError) throw backend.getError
            return backend.savedOutput
          },
          confirm: (data) => calls.confirm.push(data),
          inbound: (data) => calls.inbound.push(data)
        }
      },
      '@/views/rd/production/task/draftRequest': { bindProductionDraft, restoreProductionDraft },
      '@/utils': { generateUUID: () => crypto.randomUUID() },
      '@/views/rd/production/task/useTaskOutputs': {
        outputStatusText: () => '',
        useTaskOutputs: () => ({
          ...Object.fromEntries(['outputs', 'packages'].map((key) => [key, vue.ref([])])),
          ...Object.fromEntries(['total', 'page', 'pageSize'].map((key) => [key, vue.ref(1)])),
          ...Object.fromEntries(
            [
              'loading',
              'packagesLoaded',
              'packageLoading',
              'canRead',
              'canConfirm',
              'canGenerate',
              'canReadLabels',
              'canPrint',
              'canReprint'
            ].map((key) => [key, vue.ref(false)])
          ),
          ...Object.fromEntries(
            ['error', 'actionError', 'packageError'].map((key) => [key, vue.ref('')])
          ),
          actingId: vue.ref(),
          packageCount: () => undefined,
          loadOutputs: async () => {},
          loadPackages: async () => {},
          refresh: async () => calls.refresh++,
          runAction: (data) => calls.generate.push(data),
          openLabels: () => {}
        })
      }
    },
    { sessionStorage, window: { addEventListener: () => {}, removeEventListener: () => {} } }
  ).default
  const state = scope.run(() => component.setup({}, { expose: () => {} }))
  state.selectedId.value = 12
  state.task.value = taskFixture()
  state.formRef.value = { validate: async () => true }
  Object.assign(state.form, {
    productModel: 'Model A',
    furnaceNo: 'F-1',
    furnaceLayer: 'L-1',
    outputQty: 2,
    packageWeight: 875.125
  })
  return { state, calls, backend, user, permissions, mounted, storage, scope }
}

test('PDA network-error retries send the same frozen key/payload even after form changes', async (t) => {
  const h = pdaHarness()
  t.after(() => h.scope.stop())
  h.backend.createError = 'network-error'
  await h.state.submitDraft()
  const first = h.calls.create[0]
  assert.ok(h.state.pending.value)
  assert.ok(Object.isFrozen(first))
  assert.equal(h.storage.size, 1)
  h.state.form.packageWeight = 1000
  h.state.task.value = { ...taskFixture(), status: 'CLOSED' }
  h.backend.createError = undefined
  await h.state.submitDraft()
  assert.equal(h.calls.create[1], first)
  assert.equal(h.calls.create[1].packageWeight, 875.125)
  assert.equal(h.state.pending.value, undefined)
  assert.equal(h.storage.size, 0)
  assert.equal(h.state.view.value, 'records')
  assert.equal(h.calls.confirm.length + h.calls.generate.length + h.calls.inbound.length, 0)
})

test('PDA retains an acknowledged request when the follow-up get fails', async (t) => {
  const h = pdaHarness()
  t.after(() => h.scope.stop())
  h.backend.getError = 'network-error'
  await h.state.submitDraft()
  const first = h.calls.create[0]
  assert.ok(h.state.pending.value)
  h.backend.getError = undefined
  await h.state.submitDraft()
  assert.equal(h.calls.create[1], first)
  assert.equal(h.state.pending.value, undefined)
})

test('PDA restores the exact original request after a same-user session reload', async (t) => {
  const original = pdaHarness()
  t.after(() => original.scope.stop())
  original.backend.createError = 'network-error'
  await original.state.submitDraft()
  const restored = pdaHarness(original.storage)
  t.after(() => restored.scope.stop())
  restored.mounted.forEach((callback) => callback())
  await flush()
  assert.equal(
    restored.state.pending.value.payload.clientRequestId,
    original.calls.create[0].clientRequestId
  )
  await restored.state.submitDraft()
  assert.equal(restored.calls.create[0].clientRequestId, original.calls.create[0].clientRequestId)
  assert.equal(restored.calls.create[0].packageWeight, 875.125)
})

test("PDA identity changes cannot consume another operator's pending request", async (t) => {
  const h = pdaHarness()
  t.after(() => h.scope.stop())
  h.backend.createError = 'network-error'
  await h.state.submitDraft()
  h.user.user.id = 8
  await h.state.submitDraft()
  assert.equal(h.calls.create.length, 1)
  assert.ok(h.state.pending.value)
  assert.equal(h.storage.size, 1)
})

test('PDA blocks concurrent submit clicks without creating another row or key', async (t) => {
  const h = pdaHarness()
  t.after(() => h.scope.stop())
  let resolve
  h.backend.waitForCreate = new Promise((done) => (resolve = done))
  const first = h.state.submitDraft()
  await flush()
  await h.state.submitDraft()
  assert.equal(h.calls.create.length, 1)
  resolve(50)
  await first
  assert.equal(h.state.pending.value, undefined)
})

test('PDA read-only users and closed tasks cannot create new drafts', async (t) => {
  const h = pdaHarness()
  t.after(() => h.scope.stop())
  h.state.task.value.status = 'CLOSED'
  await h.state.submitDraft()
  assert.equal(h.calls.create.length, 0)
  h.state.task.value.status = 'ACTIVE'
  h.permissions.splice(h.permissions.indexOf('rd:production-output:create'), 1)
  await h.state.submitDraft()
  assert.equal(h.calls.create.length, 0)
})

test('PDA clears a stale customer model and requires review before binding an updated task', async (t) => {
  const h = pdaHarness()
  t.after(() => h.scope.stop())
  h.backend.freshTask = { ...taskFixture(), customerName: 'Customer B', shiftCode: 'B', version: 1 }
  await h.state.submitDraft()
  assert.equal(h.calls.create.length, 0)
  assert.equal(h.state.pending.value, undefined)
  assert.equal(h.state.form.productModel, '')
  h.state.form.productModel = 'Model B'
  await h.state.submitDraft()
  assert.equal(h.calls.create[0].customerName, 'Customer B')
  assert.equal(h.calls.create[0].shiftCode, 'B')
})

test('PDA keeps the original binding if the returned record belongs to another task', async (t) => {
  const h = pdaHarness()
  t.after(() => h.scope.stop())
  h.backend.savedOutput = { ...outputFixture(), productionTaskId: 13 }
  await h.state.submitDraft()
  assert.ok(h.state.pending.value)
  assert.equal(h.storage.size, 1)
  assert.equal(h.state.pending.value.payload.productionTaskId, 12)
})

const taskFormHarness = () => {
  const scope = vue.effectScope()
  const calls = { create: [], update: [], page: [] }
  const permissions = vue.reactive([
    'rd:production-task:create',
    'rd:production-task:update',
    'rd:production-output:query'
  ])
  const backend = { waitForCreate: undefined, outputs: [] }
  const { descriptor } = parse(readFileSync(new URL('./TaskForm.vue', import.meta.url), 'utf8'))
  const component = evaluate(compileScript(descriptor, { id: 'task-form-test' }).content, {
    vue,
    '@/directives/permission/hasPermi': {
      hasPermission: (rights) => rights.some((right) => permissions.includes(right))
    },
    '@/hooks/web/useMessage': { useMessage: () => ({ success: () => {}, error: () => {} }) },
    '@/api/rd/masterdata/customer-product': {
      RdCustomerProductApi: { getCustomerOptions: async () => [] }
    },
    '@/api/rd/production/output': {
      RdProductionOutputApi: {
        getPage: async (params) => {
          calls.page.push(params)
          return { list: backend.outputs, total: backend.outputs.length }
        }
      }
    },
    '@/api/rd/production/task': {
      RdProductionTaskApi: {
        get: async () => ({ ...taskFixture(), version: 3 }),
        create: async (data) => {
          calls.create.push(data)
          return backend.waitForCreate ? await backend.waitForCreate : 12
        },
        update: async (data) => calls.update.push(data)
      }
    }
  }).default
  const state = scope.run(() => component.setup({}, { emit: () => {}, expose: () => {} }))
  return { state, calls, backend, permissions, scope }
}

test('task save blocks duplicate clicks before asynchronous form validation completes', async (t) => {
  const h = taskFormHarness()
  t.after(() => h.scope.stop())
  await h.state.open()
  h.state.formRef.value = { validate: async () => true }
  let resolve
  h.backend.waitForCreate = new Promise((done) => (resolve = done))
  const first = h.state.save()
  const second = h.state.save()
  await flush()
  resolve(12)
  await Promise.all([first, second])
  assert.equal(h.calls.create.length, 1)
})

test('task editing sends only the accepted fields and the freshly read ID/version', async (t) => {
  const h = taskFormHarness()
  t.after(() => h.scope.stop())
  await h.state.open(12)
  h.state.formRef.value = { validate: async () => true }
  h.state.form.shiftCode = 'B'
  await h.state.save()
  assert.equal(h.calls.page[0].productionTaskId, 12)
  assert.equal(h.calls.page[0].pageSize, -1)
  assert.equal(h.calls.update[0].id, 12)
  assert.equal(h.calls.update[0].version, 3)
  assert.equal(h.calls.update[0].shiftCode, 'B')
  assert.equal('status' in h.calls.update[0], false)
})

test('tasks with effective output and read-only users cannot enter a mutable form', async (t) => {
  const h = taskFormHarness()
  t.after(() => h.scope.stop())
  h.backend.outputs = [outputFixture()]
  await h.state.open(12)
  assert.equal(h.state.editable.value, false)
  h.permissions.splice(0)
  await h.state.open()
  await h.state.save()
  assert.equal(h.calls.create.length + h.calls.update.length, 0)
})
