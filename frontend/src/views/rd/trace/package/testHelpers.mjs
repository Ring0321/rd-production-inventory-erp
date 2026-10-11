import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import * as vue from 'vue'

export const plain = (value) => JSON.parse(JSON.stringify(value))
export const evaluate = (file, imports = {}, globals = {}) => {
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
  return evaluateSource(source, imports, globals)
}
export const evaluateSource = (source, imports = {}, globals = {}) => {
  const module = { exports: {} }
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

export const deferred = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

export const memoryStorage = () => {
  const values = new Map()
  return {
    values,
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key)
  }
}

export const context = (code = 'P1', extra = {}) => ({
  packageCode: code,
  status: 'LABELED',
  qualityStage: 'SUBMITTED',
  source: { state: 'KNOWN', packageCount: 3, recordedWeight: 1500, counts: { LABELED: 3 } },
  factsAt: '2026-10-12T10:00:00+08:00',
  providerState: 'READY',
  notices: [
    { key: 'STATUS_RECORDED', text: '该包已登记贴码。' },
    { key: 'SOURCE_RECORDED', text: '来源统计仅为登记事实，不代表可用库存。' }
  ],
  ...extra
})

export const answer = (request, extra = {}) => ({
  requestId: request.requestId,
  state: 'SUCCESS',
  message: 'server status message, not model prose',
  context: context(request.packageCode),
  noteKeys: ['SOURCE_RECORDED', 'STATUS_RECORDED'],
  totalTokens: 10,
  answeredAt: '2026-10-12T10:00:01+08:00',
  ...extra
})

const eventTarget = () => {
  const listeners = new Map()
  return {
    listeners,
    addEventListener(type, callback) {
      if (!listeners.has(type)) listeners.set(type, new Set())
      listeners.get(type).add(callback)
    },
    removeEventListener(type, callback) {
      listeners.get(type)?.delete(callback)
    },
    dispatch(type, event = {}) {
      for (const callback of listeners.get(type) || []) callback(event)
    }
  }
}

export const flush = async () => {
  await vue.nextTick()
  await vue.nextTick()
}

export const setup = (options = {}) => {
  const identity = vue.reactive({
    id: 7,
    cachedId: 7,
    authenticated: true,
    tenant: 1,
    visit: 2,
    roles: ['warehouse'],
    permissions: new Set(['rd:package-unit:query']),
    ...options.identity
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
    }
  }
  const callbacks = { onActivated: [], onDeactivated: [], onBeforeUnmount: [], onUnmounted: [] }
  const lifecycle = Object.fromEntries(
    Object.keys(callbacks).map((name) => [name, (callback) => callbacks[name].push(callback)])
  )
  let authRevision = 0
  const authListeners = new Set()
  const authCache = { ...identity }
  const readCache = () => (options.nonReactiveAuth ? authCache : identity)
  const auth = {
    getTenantId: () => readCache().tenant,
    getVisitTenantId: () => readCache().visit,
    getCurrentUserId: () => readCache().cachedId,
    getAccessToken: () => readCache().authenticated,
    getAuthContextRevision: () => authRevision,
    getCachedAuthScope: (data) =>
      data
        ? {
            userId: data.user.id,
            roles: [...data.roles].sort(),
            permissions: [...data.permissions].sort()
          }
        : {
            userId: readCache().cachedId,
            roles: [...readCache().roles].sort(),
            permissions: [...readCache().permissions].sort()
          },
    onAuthContextChange: (callback) => {
      authListeners.add(callback)
      return () => authListeners.delete(callback)
    }
  }
  const hasPermission = (values) =>
    identity.permissions.has('*:*:*') || values.some((value) => identity.permissions.has(value))
  const sessionModule = evaluate('../../pda/warehouse/useWarehouseSession.ts', {
    vue: { ...vue, ...lifecycle },
    '@/store/modules/user': { useUserStore: () => user },
    '@/utils/auth': auth,
    '@/directives/permission/hasPermi': { hasPermission }
  })
  const storage = options.storage || memoryStorage()
  const window = eventTarget()
  const tails = new Map()
  if (!storage.lockManager)
    storage.lockManager = {
      request(name, _, callback) {
        const work = (tails.get(name) || Promise.resolve()).then(callback)
        tails.set(
          name,
          work.then(
            () => {},
            () => {}
          )
        )
        return work
      }
    }
  window.navigator = { locks: options.noWebLocks ? undefined : storage.lockManager }
  const timers = new Map()
  let timerId = 0
  window.setInterval = (callback) => {
    timers.set(++timerId, callback)
    return timerId
  }
  window.clearInterval = (id) => timers.delete(id)
  const document = { ...eventTarget(), visibilityState: 'visible' }
  const reads = []
  const asks = []
  const reconciles = []
  const confirmations = []
  let uuid = 0
  const api = {
    getContext(code) {
      const request = { code, ...deferred() }
      reads.push(request)
      if (!options.deferContext) request.resolve(context(code, options.contextExtra))
      return request.promise
    },
    ask(value) {
      const request = { value: plain(value), ...deferred() }
      asks.push(request)
      return request.promise
    },
    getResult(value) {
      const request = { value: plain(value), ...deferred() }
      reconciles.push(request)
      return request.promise
    }
  }
  const module = evaluate(
    './usePackageAi.ts',
    {
      vue: { ...vue, ...lifecycle },
      'vue-router': { useRoute: () => route },
      'element-plus': {
        ElMessageBox: {
          confirm: () => {
            const confirmation = deferred()
            confirmations.push(confirmation)
            if (!options.deferConfirmation) confirmation.resolve()
            return confirmation.promise
          }
        }
      },
      '@/api/rd/ai': { RdPackageAiApi: api },
      '@/utils': {
        generateUUID: () => `00000000-0000-4000-8000-${String(++uuid).padStart(12, '0')}`
      },
      '@/utils/auth': auth,
      '@/store/modules/user': { useUserStore: () => user },
      '@/hooks/web/useCache': {
        CACHE_KEY: {
          USER: 'user',
          TenantId: 'tenantId',
          VisitTenantId: 'visitTenantId',
          ROLE_ROUTERS: 'roleRouters'
        }
      },
      '../../pda/warehouse/useWarehouseSession': sessionModule
    },
    { window, document, localStorage: storage }
  )
  const props = vue.reactive({ packageCode: 'P1', factsRevision: 1, ...options.props })
  const route = vue.reactive({ fullPath: '/pkg/P1' })
  const scope = vue.effectScope()
  const work = scope.run(() =>
    options.accessOnly ? module.usePackageAiAccess() : module.usePackageAi(props)
  )
  return {
    work,
    module,
    props,
    route,
    identity,
    storage,
    window,
    document,
    reads,
    asks,
    reconciles,
    confirmations,
    timers,
    tickAccess: () => [...timers.values()].forEach((callback) => callback()),
    changeCache: (patch) => {
      Object.assign(authCache, patch)
      authRevision++
      authListeners.forEach((callback) => callback())
    },
    deactivate: () => callbacks.onDeactivated.forEach((callback) => callback()),
    activate: () => callbacks.onActivated.forEach((callback) => callback()),
    dispose() {
      callbacks.onBeforeUnmount.forEach((callback) => callback())
      callbacks.onUnmounted.forEach((callback) => callback())
      scope.stop()
    }
  }
}
