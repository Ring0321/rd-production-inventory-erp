import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { evaluateSource, deferred } from '../../views/rd/trace/package/testHelpers.mjs'

const source = (name) => readFileSync(new URL(name, import.meta.url), 'utf8')
const model = evaluateSource(source('./assistantModel.ts'))
const knowledge = evaluateSource(source('./assistantKnowledge.ts'), { './assistantModel': model })
const workQueries = evaluateSource(source('./assistantWorkQueries.ts'), {
  './assistantModel': model
})
const query = evaluateSource(source('./assistantQueries.ts'), {
  './assistantModel': model,
  './assistantKnowledge': knowledge,
  './assistantWorkQueries': workQueries
})
const setup = (options = {}) => {
  const identity = vue.reactive({
    id: 235,
    token: true,
    tenant: 1,
    roles: ['rd_demo_operator'],
    permissions: new Set(['rd:dashboard:query', 'rd:package-unit:query', 'rd:putaway-task:query'])
  })
  const user = {
    get getUser() {
      return identity
    },
    get getRoles() {
      return identity.roles
    },
    get getPermissions() {
      return identity.permissions
    },
    get getIsSetUser() {
      return identity.token
    }
  }
  const routes = vue.ref([{ path: '/warehouse-space', meta: { title: '仓储空间总览' } }])
  const route = vue.reactive({
    path: '/warehouse-space',
    fullPath: '/warehouse-space',
    query: { warehouseCode: 'W1' },
    meta: {}
  })
  let authCallback,
    revision = 0
  const listeners = new Map(),
    hooks = {},
    reads = [],
    pushes = []
  const inventory =
    options.inventory ||
    (async () => ({
      stock: { warehouseQty: 12, lockedQty: 2, availableQty: 10, stockWeight: 12000 }
    }))
  const { useDuoxuAssistant } = evaluateSource(
    source('./useDuoxuAssistant.ts'),
    {
      vue: {
        ...vue,
        onMounted: (cb) => {
          hooks.mount = cb
        },
        onBeforeUnmount: (cb) => {
          hooks.unmount = cb
        }
      },
      'vue-router': {
        useRoute: () => route,
        useRouter: () => ({ push: (value) => pushes.push(value) })
      },
      '@/store/modules/user': { useUserStore: () => user },
      '@/store/modules/permission': {
        usePermissionStore: () => ({
          get getRouters() {
            return routes.value
          }
        })
      },
      '@/utils/auth': {
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
        onAuthContextChange: (cb) => {
          authCallback = cb
          return () => {
            authCallback = undefined
          }
        }
      },
      '@/hooks/web/useCache': {
        CACHE_KEY: { USER: 'user', TenantId: 'tenant', VisitTenantId: 'visit' }
      },
      '@/api/rd/dashboard': {
        RdInventoryDashboardApi: {
          getOverview: async (...args) => {
            reads.push(args)
            return inventory()
          }
        }
      },
      '@/api/rd/stock/putaway': {
        RdPutawayTaskApi: { getLocationMap: () => assert.fail('unexpected locations read') }
      },
      '@/api/rd/stock/package': {
        RdPackageUnitApi: { getTraceDetail: () => assert.fail('unexpected package read') }
      },
      '@/api/rd/ai': {
        RdPackageAiApi: { getContext: () => assert.fail('unexpected package context read') }
      },
      '@/api/rd/workassistant': {
        RdWorkQueryApi: { query: options.workQuery || (() => assert.fail('unexpected work query')) }
      },
      './assistantModel': model,
      './assistantQueries': query
    },
    {
      window: {
        addEventListener: (name, cb) => listeners.set(name, cb),
        removeEventListener: (name) => listeners.delete(name)
      },
      document: {
        addEventListener: (name, cb) => listeners.set(name, cb),
        removeEventListener: (name) => listeners.delete(name),
        visibilityState: 'visible'
      }
    }
  )
  const state = useDuoxuAssistant()
  hooks.mount?.()
  return {
    state,
    identity,
    reads,
    pushes,
    routes,
    route,
    hooks,
    listeners,
    invalidate: () => {
      revision++
      authCallback?.()
    }
  }
}

test('no automatic query; one explicit question uses one original read-only API and no model ask', async () => {
  const s = setup()
  assert.equal(s.reads.length, 0)
  await s.state.ask('查库存')
  assert.equal(s.reads.length, 1)
  assert.equal(s.state.messages.value[0].reply.kind, 'ERP')
  assert.equal(s.state.busy.value, false)
})

test('cancelled response cannot overwrite a later answer', async () => {
  const pending = deferred()
  const s = setup({ inventory: () => pending.promise })
  const first = s.state.ask('查库存')
  s.state.cancel()
  await s.state.ask('货位图在哪里')
  pending.resolve({ stock: { warehouseQty: 9999 } })
  await first
  assert.equal(s.state.messages.value.at(-1).reply.kind, 'LOCAL')
  assert.ok(!JSON.stringify(s.state.messages.value).includes('9999'))
})

test('clearing and auth/permission/tenant changes discard history and reject delayed facts', async () => {
  for (const change of [
    (s) => s.state.clear(),
    (s) => s.invalidate(),
    (s) => {
      s.identity.permissions.clear()
    },
    (s) => {
      s.identity.tenant = 2
    },
    (s) => {
      s.identity.id = 9
    }
  ]) {
    const pending = deferred()
    const s = setup({ inventory: () => pending.promise })
    const work = s.state.ask('查库存')
    change(s)
    pending.resolve({ stock: { warehouseQty: 777 } })
    await work
    assert.equal(s.state.messages.value.length, 0)
    assert.equal(s.state.busy.value, false)
  }
})

test('driver role cannot use wildcard grants to obtain warehouse or package facts', async () => {
  const s = setup()
  s.identity.roles = ['rd_forklift_driver']
  s.identity.permissions = new Set(['*:*:*'])
  await s.state.ask('查库存')
  assert.equal(s.reads.length, 0)
  assert.equal(s.state.messages.value[0].reply.kind, 'NOTICE')
})

test('page actions are rechecked before navigation and do not open external or revoked links', async () => {
  const s = setup()
  assert.equal(s.state.openLink({ path: 'https://other.test', title: '外链' }), false)
  assert.equal(s.state.openLink({ path: '/warehouse-space', title: '仓储空间' }), true)
  assert.equal(s.pushes.length, 1)
  s.routes.value = []
  assert.equal(s.state.openLink({ path: '/warehouse-space', title: '仓储空间' }), false)
})

test('business reads are cancelled when changing page; plain navigation preserves existing conversation', async () => {
  const pending = deferred()
  const s = setup({ inventory: () => pending.promise })
  await s.state.ask('货位图在哪里')
  const work = s.state.ask('查库存')
  s.route.fullPath = '/index'
  pending.resolve({ stock: { warehouseQty: 88 } })
  await work
  assert.equal(s.state.messages.value[0].reply.kind, 'LOCAL')
  assert.ok(!JSON.stringify(s.state.messages.value).includes('88 包'))
})

test('unmount releases listeners and clears all conversation data', async () => {
  const s = setup()
  await s.state.ask('货位图在哪里')
  s.hooks.unmount()
  assert.equal(s.state.messages.value.length, 0)
  assert.equal(s.listeners.size, 0)
  await s.state.ask('查库存')
  assert.equal(s.reads.length, 0)
})

test('cross-tab authentication storage changes immediately clear and disable the previous conversation', async () => {
  const s = setup()
  await s.state.ask('货位图在哪里')
  s.listeners.get('storage')({ key: 'ACCESS_TOKEN' })
  assert.equal(s.state.messages.value.length, 0)
  assert.equal(s.state.available.value, false)
  await s.state.ask('查库存')
  assert.equal(s.reads.length, 0)
})
