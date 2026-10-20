import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc'
import { renderToString } from 'vue/server-renderer'
import {
  answer,
  context,
  evaluate,
  evaluateSource,
  flush,
  memoryStorage,
  plain,
  setup
} from './testHelpers.mjs'

const panelComponent = (h, realWork = false) => {
  const source = readFileSync(new URL('./PackageAiPanel.vue', import.meta.url), 'utf8')
  const { descriptor, errors } = parse(source)
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'package-ai-focused-test', inlineTemplate: true })
  return evaluateSource(script.content, {
    vue,
    './usePackageAi': realWork ? h.module : { ...h.module, usePackageAi: () => h.work }
  }).default
}
const registerControls = (app) => {
  for (const [name, tag] of Object.entries({
    ElButton: 'button',
    ElSelect: 'select',
    ElInput: 'input',
    ElOption: 'option',
    ElTag: 'span',
    ElTooltip: 'div',
    ElAlert: 'p',
    ElSkeleton: 'div',
    RouterLink: 'a',
    Icon: 'span'
  })) {
    app.component(name, {
      inheritAttrs: false,
      setup:
        (_, { attrs, slots }) =>
        () =>
          vue.h(tag, attrs, slots.default?.() || attrs.title || '')
    })
  }
}
const renderPanel = async (h) => {
  const app = vue.createSSRApp(panelComponent(h), h.props)
  registerControls(app)
  return renderToString(app)
}

test('the real API adapter uses only the supplied context and guided ask contracts', async () => {
  const calls = []
  const module = evaluate('../../../../api/rd/ai/index.ts', {
    '@/config/axios': {
      __esModule: true,
      default: {
        get: async (value) => {
          calls.push(plain(value))
          return context()
        },
        post: async (value) => {
          calls.push(plain(value))
          return answer(value.data)
        }
      }
    }
  })
  const request = {
    packageCode: 'P1',
    question: 'STATUS',
    requestId: '00000000-0000-4000-8000-000000000001'
  }
  await module.RdPackageAiApi.getContext('P1')
  await module.RdPackageAiApi.ask(request)
  await module.RdPackageAiApi.getResult(request)
  assert.deepEqual(calls, [
    { url: '/rd/ai/package/context', params: { packageCode: 'P1' } },
    { url: '/rd/ai/package/ask', data: request },
    { url: '/rd/ai/package/result', params: request }
  ])
})

test('mount, facts refresh and activation read ERP facts but never automatically ask', async () => {
  const h = setup()
  await flush()
  assert.equal(h.reads.length, 1)
  assert.equal(h.work.canAsk.value, true)
  assert.equal(h.asks.length, 0)
  h.props.factsRevision++
  await flush()
  h.deactivate()
  h.activate()
  await flush()
  assert.equal(h.asks.length, 0)
  assert.equal(h.work.answer.value, undefined)
  h.dispose()
})

test('anonymous and unpermitted sales/driver pages do not call either endpoint', async () => {
  for (const identity of [
    { authenticated: false },
    { id: 0, cachedId: 0 },
    { roles: ['sales'], permissions: new Set() },
    { roles: ['driver'], permissions: new Set(['rd:loading-task:execute']) }
  ]) {
    const h = setup({ identity })
    await flush()
    await h.work.ask()
    await h.work.reconcile()
    assert.equal(h.work.canQuery.value, false)
    assert.equal(h.reads.length, 0)
    assert.equal(h.asks.length, 0)
    h.dispose()
  }
})

test('each provider gate shows its local state and prevents billable requests', async () => {
  for (const providerState of [
    'DISABLED',
    'UNCONFIGURED',
    'COST_NOT_APPROVED',
    'LIMIT_NOT_SET',
    'BUDGET_NOT_SET',
    'PRICE_REVIEW_REQUIRED'
  ]) {
    const h = setup({ contextExtra: { providerState } })
    await flush()
    assert.equal(h.work.context.value.providerState, providerState)
    assert.equal(h.work.canAsk.value, false)
    assert.ok(h.work.providerLabel.value)
    await h.work.ask()
    assert.equal(h.asks.length, 0)
    h.dispose()
  }
})

test('unknown source metrics stay unknown and real recorded zero stays zero', async () => {
  const h = setup({
    contextExtra: {
      source: { state: 'UNKNOWN', packageCount: null, recordedWeight: null, counts: {} }
    }
  })
  await flush()
  assert.deepEqual(plain(h.work.sourceFacts.value), {
    packageCount: '未知',
    recordedWeight: '未知',
    counts: '未知'
  })
  h.props.factsRevision++
  await flush()
  h.dispose()
  const z = setup({
    contextExtra: {
      source: { state: 'KNOWN', packageCount: 0, recordedWeight: 0, counts: {} }
    }
  })
  await flush()
  assert.equal(z.work.sourceFacts.value.packageCount, '0 包')
  assert.equal(z.work.sourceFacts.value.recordedWeight, '0 kg')
  assert.notEqual(z.work.sourceFacts.value.counts, '未知')
  z.dispose()
})

test('only the current context request can populate facts after package/route/refresh changes', async () => {
  for (const change of [
    (h) => {
      h.props.packageCode = 'P2'
    },
    (h) => {
      h.props.factsRevision++
    },
    (h) => {
      h.route.fullPath = '/pkg/P1?workMode=pda'
    }
  ]) {
    const h = setup({ deferContext: true })
    const old = h.reads[0]
    change(h)
    const latest = h.reads.at(-1)
    assert.notEqual(latest, old)
    latest.resolve(context(h.props.packageCode, { factsAt: 'new facts' }))
    await flush()
    old.resolve(context('P1', { factsAt: 'old facts' }))
    await flush()
    assert.equal(h.work.context.value.factsAt, 'new facts')
    assert.equal(h.work.contextLoading.value, false)
    h.dispose()
  }
})

test('failed and malformed context reads remove facts and never enable asks', async () => {
  for (const fail of [
    (read) => read.reject(new Error('offline')),
    (read) => read.resolve(context('WRONG')),
    (read) => read.resolve(context('P1', { providerState: 'UNEXPECTED' })),
    (read) =>
      read.resolve(
        context('P1', {
          source: { state: 'KNOWN', packageCount: -1, recordedWeight: 0, counts: {} }
        })
      )
  ]) {
    const h = setup({ deferContext: true })
    fail(h.reads[0])
    await flush()
    assert.equal(h.work.context.value, undefined)
    assert.ok(h.work.contextError.value)
    assert.equal(h.work.canAsk.value, false)
    assert.equal(h.work.contextLoading.value, false)
    h.dispose()
  }
})

test('captured identity, tenant, permission and role changes clear visible data and reject old replies', async () => {
  for (const change of [
    (h) => {
      h.identity.id = 8
    },
    (h) => {
      h.identity.cachedId = 8
    },
    (h) => {
      h.identity.authenticated = false
    },
    (h) => {
      h.identity.visit = 3
    },
    (h) => {
      h.identity.visit = undefined
    },
    (h) => {
      h.identity.permissions.clear()
    },
    (h) => {
      h.identity.roles = ['sales']
    },
    (h) => {
      h.window.dispatch('storage', { key: 'user' })
    }
  ]) {
    const h = setup()
    await flush()
    const asking = h.work.ask()
    await flush()
    const original = h.asks[0]
    const saved = [...h.storage.values.values()][0]
    change(h)
    assert.equal(h.work.context.value, undefined)
    assert.equal(h.work.answer.value, undefined)
    assert.equal(h.work.asking.value, false)
    original.resolve(answer(original.value))
    await asking
    assert.equal(h.work.answer.value, undefined)
    assert.equal(h.work.canAsk.value, false)
    assert.equal(h.work.canReconcile.value, false)
    assert.equal([...h.storage.values.values()][0], saved)
    h.dispose()
  }
})

test('late asks after facts, route or package changes stay unresolved for explicit same-ID recovery', async () => {
  for (const change of [
    (h) => {
      h.props.factsRevision++
    },
    (h) => {
      h.route.fullPath = '/pkg/P1?workMode=pda'
    },
    (h) => {
      h.props.packageCode = 'P2'
    }
  ]) {
    const h = setup()
    await flush()
    const asking = h.work.ask()
    await flush()
    const original = h.asks[0]
    change(h)
    await flush()
    original.resolve(answer(original.value))
    await asking
    assert.equal(h.work.answer.value, undefined)
    const saved = JSON.parse([...h.storage.values.values()][0])
    assert.equal(saved.state, 'UNKNOWN')
    assert.equal(saved.request.requestId, original.value.requestId)
    assert.equal(h.work.asking.value, false)
    h.dispose()
  }
})

test('deactivation generations reject replies even after reactivation; teardown removes spinners/listeners', async () => {
  for (const end of ['deactivate', 'dispose']) {
    const h = setup()
    await flush()
    const asking = h.work.ask()
    await flush()
    const original = h.asks[0]
    h[end]()
    assert.equal(h.work.context.value, undefined)
    assert.equal(h.work.asking.value, false)
    assert.equal(h.work.contextLoading.value, false)
    if (end === 'deactivate') {
      h.activate()
      await flush()
      assert.equal(h.work.canReconcile.value, true)
    }
    original.resolve(answer(original.value))
    await asking
    assert.equal(h.work.answer.value, undefined)
    assert.equal(JSON.parse([...h.storage.values.values()][0]).state, 'UNKNOWN')
    h.dispose()
    assert.ok([...h.window.listeners.values()].every((values) => values.size === 0))
    assert.ok([...h.document.listeners.values()].every((values) => values.size === 0))
    assert.equal(h.timers.size, 0)
  }
})

test('unknown transport results lock new asks, survive reload and reconcile the immutable request only', async () => {
  const storage = memoryStorage()
  const h = setup({ storage })
  await flush()
  h.work.question.value = 'SOURCE_PROGRESS'
  const asking = h.work.ask()
  await flush()
  const original = plain(h.asks[0].value)
  await h.work.ask()
  assert.equal(h.asks.length, 1)
  h.asks[0].reject(new Error('timeout'))
  await asking
  assert.equal(h.work.canAsk.value, false)
  assert.equal(h.work.canNewQuestion.value, false)
  h.dispose()
  const recovered = setup({ storage })
  await flush()
  assert.equal(recovered.work.question.value, 'SOURCE_PROGRESS')
  assert.equal(recovered.asks.length, 0)
  recovered.work.question.value = 'NEXT_STEP'
  await recovered.work.ask()
  await recovered.work.newQuestion()
  assert.equal(recovered.asks.length, 0)
  const checking = recovered.work.reconcile()
  assert.deepEqual(recovered.reconciles[0].value, original)
  recovered.reconciles[0].resolve(answer(original))
  await checking
  assert.equal(recovered.work.answer.value.state, 'SUCCESS')
  assert.equal(recovered.work.canNewQuestion.value, true)
  assert.equal(recovered.work.canAsk.value, false)
  await recovered.work.newQuestion()
  assert.equal(storage.values.size, 0)
  assert.equal(recovered.work.canAsk.value, true)
  assert.equal(recovered.asks.length, 0)
  recovered.dispose()
})

test('PENDING, unfamiliar states and wrong answer identities never unlock repeat billing', async () => {
  for (const extra of [
    { state: 'PENDING', noteKeys: [], totalTokens: null, answeredAt: null },
    { state: 'UNKNOWN_FUTURE_STATE', noteKeys: [] },
    { requestId: '00000000-0000-4000-8000-999999999999' },
    { context: context('WRONG') }
  ]) {
    const h = setup()
    await flush()
    const asking = h.work.ask()
    await flush()
    h.asks[0].resolve(answer(h.asks[0].value, extra))
    await asking
    assert.equal(h.work.answer.value, undefined)
    assert.equal(h.work.canAsk.value, false)
    assert.equal(h.work.canNewQuestion.value, false)
    assert.equal(h.work.canReconcile.value, true)
    await h.work.newQuestion()
    assert.equal(h.storage.values.size, 1)
    h.dispose()
  }
})

test('known FAILED and INVALID_OUTPUT require an explicit new question and never display AI success', async () => {
  for (const state of [
    'FAILED',
    'INVALID_OUTPUT',
    'COST_NOT_APPROVED',
    'LIMIT_REACHED',
    'BUDGET_REACHED',
    'BUDGET_UNAVAILABLE',
    'PRICE_REVIEW_REQUIRED',
    'BUDGET_NOT_SET'
  ]) {
    const h = setup()
    await flush()
    const asking = h.work.ask()
    await flush()
    h.asks[0].resolve(answer(h.asks[0].value, { state, noteKeys: [] }))
    await asking
    assert.equal(h.work.answer.value, undefined)
    assert.equal(h.work.canAsk.value, false)
    assert.equal(h.work.canNewQuestion.value, true)
    await h.work.ask()
    assert.equal(h.asks.length, 1)
    await h.work.newQuestion()
    assert.equal(h.work.canAsk.value, true)
    assert.equal(h.storage.values.size, 0)
    h.dispose()
  }
})

test('successful notes are resolved only by keys in the answer server context, not messages or old notices', async () => {
  const h = setup()
  await flush()
  const asking = h.work.ask()
  await flush()
  const response = answer(h.asks[0].value, {
    message: 'invented quantity 999999 and https://untrusted.invalid',
    context: context('P1', {
      notices: [
        { key: 'STATUS_RECORDED', text: '服务器核实的当前状态。' },
        { key: 'SOURCE_RECORDED', text: '服务器核实的来源统计。' }
      ]
    })
  })
  h.asks[0].resolve(response)
  await asking
  assert.deepEqual(plain(h.work.notes.value), ['服务器核实的来源统计。', '服务器核实的当前状态。'])
  h.props.factsRevision++
  await flush()
  assert.equal(h.work.answer.value, undefined)
  assert.deepEqual(plain(h.work.notes.value), [])
  h.dispose()
})

test('receipt-only lookup while disabled can recover success without another POST', async () => {
  const h = setup()
  await flush()
  const asking = h.work.ask()
  await flush()
  const original = h.asks[0].value
  h.asks[0].reject(new Error('offline'))
  await asking
  h.props.factsRevision++
  await flush()
  const checking = h.work.reconcile()
  h.reconciles[0].resolve(
    answer(original, { context: context('P1', { providerState: 'DISABLED' }) })
  )
  await checking
  assert.equal(h.work.answer.value.state, 'SUCCESS')
  assert.equal(h.work.context.value.providerState, 'DISABLED')
  assert.equal(h.asks.length, 1)
  assert.equal(h.reconciles.length, 1)
  h.dispose()
})

test('expired read-only receipt cannot unlock a second paid request', async () => {
  const h = setup()
  await flush()
  const asking = h.work.ask()
  await flush()
  const original = h.asks[0].value
  h.asks[0].reject(new Error('offline'))
  await asking
  const checking = h.work.reconcile()
  h.reconciles[0].resolve(
    answer(original, {
      state: 'RECEIPT_MISSING',
      noteKeys: [],
      totalTokens: null,
      answeredAt: null
    })
  )
  await checking
  assert.equal(h.work.canAsk.value, false)
  assert.equal(h.work.canNewQuestion.value, false)
  assert.match(h.work.resultMessage.value, /过期或缺失/)
  await h.work.ask()
  assert.equal(h.asks.length, 1)
  h.dispose()
})

test('saved successful receipt remains explicitly retrievable after refresh and disabled reentry', async () => {
  const storage = memoryStorage()
  const h = setup({ storage })
  await flush()
  const asking = h.work.ask()
  await flush()
  const original = h.asks[0].value
  h.asks[0].resolve(answer(original))
  await asking
  h.dispose()
  const next = setup({ storage, contextExtra: { providerState: 'DISABLED' } })
  await flush()
  assert.equal(next.work.canReconcile.value, true)
  const lookup = next.work.reconcile()
  next.reconciles[0].resolve(
    answer(original, { context: context('P1', { providerState: 'DISABLED' }) })
  )
  await lookup
  assert.equal(next.asks.length, 0)
  assert.equal(next.work.answer.value.state, 'SUCCESS')
  next.dispose()
})

test('real cache-backed auth setters synchronously record tenant switch-back without changing token values', () => {
  const values = new Map()
  const auth = evaluate('../../../../utils/auth.ts', {
    '@/hooks/web/useCache': {
      CACHE_KEY: { USER: 'user', TenantId: 'tenantId', VisitTenantId: 'visitTenantId' },
      useCache: () => ({
        wsCache: {
          get: (key) => values.get(key),
          set: (key, value) => values.set(key, value),
          delete: (key) => values.delete(key)
        }
      })
    }
  })
  let changes = 0
  const stop = auth.onAuthContextChange(() => changes++)
  const revision = auth.getAuthContextRevision()
  auth.setVisitTenantId(2)
  auth.setVisitTenantId(1)
  assert.equal(auth.getVisitTenantId(), 1)
  assert.equal(auth.getAuthContextRevision(), revision + 2)
  assert.equal(changes, 2)
  auth.setToken({ accessToken: 'LOCAL_FIXTURE_ACCESS', refreshToken: 'LOCAL_FIXTURE_REFRESH' })
  assert.equal(auth.getAccessToken(), 'LOCAL_FIXTURE_ACCESS')
  stop()
})

test('nonreactive tenant switch-back between polls invalidates both late reads and late answers', async () => {
  const h = setup({ nonReactiveAuth: true })
  await flush()
  const asking = h.work.ask()
  await flush()
  const original = h.asks[0].value
  h.changeCache({ visit: 3 })
  h.changeCache({ visit: 2 })
  h.asks[0].resolve(answer(original))
  await asking
  assert.equal(h.work.answer.value, undefined)
  assert.equal(h.work.context.value, undefined)
  assert.equal(h.work.canQuery.value, false)
  assert.equal(h.storage.values.size, 1)
  h.dispose()
})

test('two tabs with shared persistent storage reserve one original request and never POST twice', async () => {
  const storage = memoryStorage()
  const first = setup({ storage })
  const second = setup({ storage })
  await flush()
  const a = first.work.ask()
  const b = second.work.ask()
  await flush()
  assert.equal(first.asks.length + second.asks.length, 1)
  const owner = first.asks.length ? first : second
  owner.asks[0].reject(new Error('timeout'))
  await Promise.all([a, b])
  const original = plain(owner.asks[0].value)
  first.dispose()
  second.dispose()
  const reopened = setup({ storage })
  await flush()
  assert.equal(reopened.work.canAsk.value, false)
  assert.equal(reopened.work.canReconcile.value, true)
  const lookup = reopened.work.reconcile()
  assert.deepEqual(reopened.reconciles[0].value, original)
  reopened.reconciles[0].resolve(answer(original))
  await lookup
  assert.equal(reopened.asks.length, 0)
  reopened.dispose()
})

test('same-scope user-cache refresh from another tab does not hide the panel, but transient permission changes invalidate it', async () => {
  const h = setup()
  await flush()
  const scope = { user: { id: 7 }, roles: ['warehouse'], permissions: ['rd:package-unit:query'] }
  h.window.dispatch('storage', {
    key: 'user',
    oldValue: JSON.stringify({ c: 1, v: scope }),
    newValue: JSON.stringify({ c: 2, v: scope })
  })
  assert.equal(h.work.canQuery.value, true)
  h.window.dispatch('storage', {
    key: 'user',
    oldValue: JSON.stringify({ v: scope }),
    newValue: JSON.stringify({ v: { ...scope, permissions: [] } })
  })
  assert.equal(h.work.canQuery.value, false)
  h.dispose()
})

test('unsupported Web Locks prevents paid requests but retains ERP queries', async () => {
  const h = setup({ noWebLocks: true })
  await flush()
  assert.ok(h.work.context.value)
  assert.equal(h.work.canAsk.value, false)
  assert.match(h.work.storageError.value, /跨标签页/)
  await h.work.ask()
  assert.equal(h.asks.length, 0)
  h.dispose()
})

test('untrusted or duplicate explanation keys fail closed without synthesizing a terminal acknowledgement', async () => {
  for (const extra of [
    { noteKeys: ['NON_EXISTENT'] },
    { noteKeys: ['STATUS_RECORDED', 'STATUS_RECORDED'] },
    { noteKeys: [] },
    {
      context: context('P1', {
        notices: [
          { key: 'STATUS_RECORDED', text: 'one' },
          { key: 'STATUS_RECORDED', text: 'two' }
        ]
      })
    }
  ]) {
    const h = setup()
    await flush()
    const asking = h.work.ask()
    await flush()
    h.asks[0].resolve(answer(h.asks[0].value, extra))
    await asking
    assert.equal(h.work.answer.value, undefined)
    assert.deepEqual(plain(h.work.notes.value), [])
    assert.equal(h.work.canNewQuestion.value, false)
    assert.equal(JSON.parse([...h.storage.values.values()][0]).state, 'UNKNOWN')
    h.dispose()
  }
})

test('server denial removes facts and disables operations but keeps the original unknown identity', async () => {
  for (const denial of [{ response: { status: 403 } }, 'error']) {
    const h = setup()
    await flush()
    const asking = h.work.ask()
    await flush()
    const stored = [...h.storage.values.values()][0]
    h.asks[0].reject(denial)
    await asking
    assert.equal(h.work.context.value, undefined)
    assert.equal(h.work.canAsk.value, false)
    assert.equal(h.work.canReconcile.value, false)
    assert.equal([...h.storage.values.values()][0], stored)
    h.dispose()
  }
})

test('storage denial/corruption and another stored original cannot be overwritten or cleared', async () => {
  for (const corrupt of [false, true]) {
    const storage = memoryStorage()
    const h = setup({ storage })
    await flush()
    const key = h.module.packageAiPendingKey(7, '2', 'P1')
    if (corrupt) storage.setItem(key, 'broken-json')
    else
      storage.setItem = () => {
        throw new Error('storage unavailable')
      }
    await h.work.ask()
    assert.equal(h.asks.length, 0)
    assert.ok(h.work.storageError.value)
    assert.equal(h.work.canAsk.value, false)
    assert.equal(h.work.canNewQuestion.value, false)
    h.dispose()
  }
})

test('saved requests contain no tokens/facts and cannot recover under a different user/tenant/package', async () => {
  const storage = memoryStorage()
  const h = setup({ storage })
  await flush()
  const asking = h.work.ask()
  await flush()
  const saved = JSON.parse([...storage.values.values()][0])
  assert.deepEqual(Object.keys(saved).sort(), ['ownerId', 'request', 'state', 'tenant', 'version'])
  assert.deepEqual(Object.keys(saved.request).sort(), ['packageCode', 'question', 'requestId'])
  h.dispose()
  h.asks[0].reject(new Error('offline'))
  await asking
  for (const options of [
    { identity: { id: 8, cachedId: 8 } },
    { identity: { visit: 3 } },
    { props: { packageCode: 'P2' } }
  ]) {
    const other = setup({ storage, ...options })
    await flush()
    assert.equal(other.work.pending.value, undefined)
    assert.equal(other.asks.length, 0)
    assert.equal(storage.values.size, 1)
    other.dispose()
  }
})

test('a conflicting stored request cannot be overwritten by a delayed answer or a new ask', async () => {
  const h = setup()
  await flush()
  const asking = h.work.ask()
  await flush()
  const key = [...h.storage.values.keys()][0]
  const original = h.asks[0].value
  const different = JSON.parse(h.storage.getItem(key))
  different.request.requestId = '00000000-0000-4000-8000-999999999999'
  const raw = JSON.stringify(different)
  h.storage.setItem(key, raw)
  h.asks[0].resolve(answer(original))
  await asking
  assert.ok(h.work.storageError.value)
  assert.equal(h.work.answer.value, undefined)
  await h.work.ask()
  await h.work.reconcile()
  await h.work.newQuestion()
  assert.equal(h.asks.length, 1)
  assert.equal(h.storage.getItem(key), raw)
  h.dispose()
})

test('a new-question confirmation captures its original, route, refresh and lifecycle generation', async () => {
  for (const change of [
    (h) => {
      h.props.factsRevision++
    },
    (h) => {
      h.route.fullPath = '/pkg/P1?workMode=pda'
    },
    (h) => {
      h.props.packageCode = 'P2'
    },
    (h) => {
      h.deactivate()
      h.activate()
    },
    (h) => {
      h.identity.visit = 3
    },
    (h) => {
      h.dispose()
    },
    (h) => {
      const key = [...h.storage.values.keys()][0]
      const value = JSON.parse(h.storage.getItem(key))
      value.request.requestId = '00000000-0000-4000-8000-999999999999'
      h.storage.setItem(key, JSON.stringify(value))
    }
  ]) {
    const h = setup({ deferConfirmation: true })
    await flush()
    const asking = h.work.ask()
    await flush()
    h.asks[0].resolve(answer(h.asks[0].value))
    await asking
    const clearing = h.work.newQuestion()
    assert.equal(h.confirmations.length, 1)
    change(h)
    h.confirmations[0].resolve()
    await clearing
    assert.equal(h.storage.values.size, 1)
    assert.equal(h.work.confirming.value, false)
    assert.equal(h.asks.length, 1)
    h.dispose()
  }
})

test('a stored PENDING request reloads without automatic retry and only posts the saved question/id', async () => {
  const storage = memoryStorage()
  const h = setup({ storage })
  await flush()
  h.work.question.value = 'NEXT_STEP'
  const asking = h.work.ask()
  await flush()
  const original = h.asks[0].value
  h.asks[0].resolve(
    answer(original, {
      state: 'PENDING',
      noteKeys: [],
      totalTokens: null,
      answeredAt: null
    })
  )
  await asking
  h.dispose()
  const recovered = setup({ storage })
  await flush()
  assert.equal(recovered.work.pending.value.state, 'PENDING')
  assert.equal(recovered.asks.length, 0)
  assert.equal(recovered.work.canNewQuestion.value, false)
  const checking = recovered.work.reconcile()
  assert.deepEqual(recovered.reconciles[0].value, original)
  recovered.reconciles[0].resolve(answer(original))
  await checking
  recovered.dispose()
})

test('arbitrary questions cannot create a request and a successful read-only answer performs no other action', async () => {
  const h = setup()
  await flush()
  h.work.question.value = 'FREE_CHAT'
  await h.work.ask()
  assert.equal(h.asks.length, 0)
  assert.equal(h.storage.values.size, 0)
  h.work.question.value = 'NEXT_STEP'
  const asking = h.work.ask()
  await flush()
  h.asks[0].resolve(answer(h.asks[0].value))
  await asking
  assert.equal(h.asks.length, 1)
  assert.equal(h.reads.length, 1)
  h.dispose()
})

test('stale context reads cannot return after deactivation/reactivation, destroy or an identity switch back', async () => {
  for (const change of [
    (h) => {
      h.deactivate()
      h.activate()
    },
    (h) => {
      h.dispose()
    },
    (h) => {
      h.identity.cachedId = 8
      h.identity.cachedId = 7
    }
  ]) {
    const h = setup({ deferContext: true })
    const old = h.reads[0]
    change(h)
    if (h.reads.length > 1) h.reads.at(-1).resolve(context('P1', { factsAt: 'new generation' }))
    await flush()
    old.resolve(context('P1', { factsAt: 'old generation' }))
    await flush()
    assert.notEqual(h.work.context.value?.factsAt, 'old generation')
    assert.equal(h.work.contextLoading.value, false)
    h.dispose()
  }
})

test('the actual panel renders ERP facts/provider gates without an AI answer or an automatic POST', async () => {
  const h = setup({ contextExtra: { providerState: 'COST_NOT_APPROVED' } })
  await flush()
  const html = await renderPanel(h)
  assert.ok(html.includes('ERP登记事实'))
  assert.ok(html.includes('录入包数'))
  assert.ok(html.includes('登记总重'))
  assert.ok(html.includes('事实时间'))
  assert.ok(html.includes('费用未确认'))
  assert.ok(html.includes('disabled'))
  assert.ok(!html.includes('AI整理要点'))
  assert.equal(h.asks.length, 0)
  h.dispose()
})

test('the actual panel renders AI notes only after validated SUCCESS, never server free prose', async () => {
  const h = setup({ deferConfirmation: true })
  await flush()
  const asking = h.work.ask()
  await flush()
  h.asks[0].resolve(answer(h.asks[0].value, { message: 'UNTRUSTED_FREE_PROSE_999999' }))
  await asking
  const html = await renderPanel(h)
  assert.ok(html.includes('AI整理要点'))
  assert.ok(html.includes('来源统计仅为登记事实'))
  assert.ok(!html.includes('UNTRUSTED_FREE_PROSE_999999'))
  const clearing = h.work.newQuestion()
  const confirmingHtml = await renderPanel(h)
  assert.ok(confirmingHtml.includes('新问题'))
  assert.match(confirmingHtml, /<button[^>]*disabled[^>]*>[\s\S]*?核对原请求/)
  h.confirmations[0].reject('cancel')
  await clearing
  h.props.factsRevision++
  await flush()
  assert.ok(!(await renderPanel(h)).includes('AI整理要点'))
  h.dispose()
})

test('DeepSeek metadata displays its ordinary model and budget without creating a paid request', async () => {
  const h = setup({
    contextExtra: {
      provider: {
        name: 'DeepSeek',
        model: 'deepseek-flash',
        monthlyBudgetCny: 20,
        reservationPerCallCny: 0.1,
        priceValidUntil: '2026-11-19'
      }
    }
  })
  await flush()
  const html = await renderPanel(h)
  assert.match(html, /DeepSeek/)
  assert.match(html, /普通对话/)
  assert.match(html, /deepseek-flash/)
  assert.match(html, /ERP 月预算 20 元/)
  assert.match(html, /失败不退回预留/)
  assert.equal(h.asks.length, 0)
  assert.equal(h.work.canAsk.value, true)
  h.dispose()
})

test('the trace page embedding never mounts/calls AI for public views, missing permission or a mismatched package', async () => {
  const source = readFileSync(new URL('./index.vue', import.meta.url), 'utf8')
  const { descriptor, errors } = parse(source)
  assert.deepEqual(errors, [])
  const find = (node) => {
    if (node.tag === 'PackageAiPanel') return node
    for (const child of node.children || []) {
      const match = find(child)
      if (match) return match
    }
  }
  const panel = find(descriptor.template.ast)
  assert.ok(panel, 'The panel must remain embedded in the existing trace page')
  const template = compileTemplate({
    source: panel.loc.source,
    filename: 'index.vue',
    id: 'trace-ai-gate'
  })
  assert.deepEqual(template.errors, [])
  const { render } = evaluateSource(template.code, { vue })
  for (const [trace, identity, expectedReads] of [
    [{ internalView: false, packageCode: 'P1' }, {}, 0],
    [{ internalView: true, packageCode: 'P1' }, { permissions: new Set() }, 0],
    [{ internalView: true, packageCode: 'P1' }, { authenticated: false }, 0],
    [{ internalView: true, packageCode: 'P2' }, {}, 0],
    [{ internalView: true, packageCode: 'P1' }, {}, 1]
  ]) {
    const h = setup({ accessOnly: true, identity })
    const app = vue.createSSRApp({
      render,
      setup: () => ({
        trace,
        canViewPackageAi: h.work.permitted,
        packageCode: 'P1',
        aiFactsRevision: 1
      })
    })
    app.component('PackageAiPanel', panelComponent(h, true))
    registerControls(app)
    await renderToString(app)
    await flush()
    assert.equal(h.reads.length, expectedReads)
    assert.equal(h.asks.length, 0)
    h.dispose()
  }
})

test('nonreactive same-tab auth changes are rechecked without requests and the timer is removed on destroy', async () => {
  const h = setup()
  await flush()
  // A plain replacement stops Vue tracking, like the real cache-backed getter.
  const backing = vue.toRaw(h.identity)
  backing.visit = 3
  h.tickAccess()
  assert.equal(h.work.context.value, undefined)
  assert.equal(h.work.canQuery.value, false)
  assert.equal(h.reads.length, 1)
  assert.equal(h.asks.length, 0)
  h.dispose()
  assert.equal(h.timers.size, 0)
})
