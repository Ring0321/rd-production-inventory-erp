import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { evaluateSource, plain, deferred } from '../../trace/package/testHelpers.mjs'

const read = (name) => readFileSync(new URL(name, import.meta.url), 'utf8')
const schema = evaluateSource(read('./visualSchema.ts'))
const layout = evaluateSource(read('./layoutModel.ts'), { './visualSchema': schema })
const docs = evaluateSource(read('./documentSession.ts'), { vue })
const locations = [
  { id: 11, warehouseCode: 'WH-A', warehouseName: 'A', locationCode: 'A-01' },
  { id: 12, warehouseCode: 'WH-A', warehouseName: 'A', locationCode: 'A-02' },
  { id: 21, warehouseCode: 'WH-B', warehouseName: 'B', locationCode: 'B-01' }
]
const content = () => ({
  ...layout.emptyLayout('WH-A'),
  elements: [
    {
      id: 'bin-11',
      kind: 'LOCATION',
      locationId: 11,
      x: 0,
      y: 0,
      width: 100,
      height: 60,
      label: 'A-01'
    }
  ]
})

test('layout binds original same-warehouse numeric IDs without mutating original rows', () => {
  const before = JSON.stringify(locations)
  assert.deepEqual(plain(layout.parseLayout(content(), 'WH-A', locations)), content())
  assert.equal(JSON.stringify(locations), before)
  assert.deepEqual(
    plain(layout.warehouseOptions(locations)).map((v) => v.code),
    ['WH-A', 'WH-B']
  )
})

test('layout rejects duplicate, missing, foreign bindings and binding on zones', () => {
  for (const id of [21, 99, '11', NaN]) {
    const value = content()
    value.elements[0].locationId = id
    assert.throws(() => layout.parseLayout(value, 'WH-A', locations))
  }
  const value = content()
  value.elements.push({ ...value.elements[0], id: 'second' })
  assert.throws(() => layout.parseLayout(value, 'WH-A', locations))
  value.elements = [{ ...value.elements[0], kind: 'ZONE' }]
  assert.throws(() => layout.parseLayout(value, 'WH-A', locations))
  assert.throws(() => layout.parseLayout(content(), 'WH-B', locations))
})

test('layout rejects non-finite, negative, oversized and injected content', () => {
  for (const patch of [
    { x: -1 },
    { y: Infinity },
    { width: 0 },
    { x: 1199 },
    { onClick: 'evil' },
    { label: '<b>X</b>' },
    { label: 'https://remote.test' }
  ]) {
    const value = content()
    Object.assign(value.elements[0], patch)
    assert.throws(() => layout.parseLayout(value, 'WH-A', locations))
  }
  assert.throws(() => layout.parseLayout({ ...content(), warehouseId: 1 }, 'WH-A', locations))
})

test('layout editor matches server limits before save', () => {
  for (const patch of [{ width: 63 }, { height: 4097 }])
    assert.throws(() => layout.parseLayout({ ...content(), ...patch }, 'WH-A', locations))
  for (const patch of [{ id: 'bad:id' }, { label: '' }, { width: 0.5 }]) {
    const value = content()
    Object.assign(value.elements[0], patch)
    assert.throws(() => layout.parseLayout(value, 'WH-A', locations))
  }
})

test('layout history undo/reset owns copies and bounded movement', () => {
  const initial = content()
  const history = layout.createHistory(initial)
  const next = content()
  next.elements[0].x = 200
  history.change(next)
  next.elements[0].x = 300
  assert.equal(history.value().elements[0].x, 200)
  assert.equal(history.undo().elements[0].x, 0)
  history.change(next)
  assert.equal(history.reset().elements[0].x, 0)
  assert.deepEqual(plain(layout.boundPosition({ x: -1, y: 999 }, initial.elements[0], initial)), {
    x: 0,
    y: 740
  })
})

const setup = (extra = {}) => {
  let current = true
  const calls = []
  const record = (version = 0) => ({
    kind: 'LAYOUT',
    scopeId: 'WH-A',
    version,
    draft: content(),
    published: null,
    publishedVersion: 0,
    updatedAt: ''
  })
  const api = {
    getVisualDocument: async () => record(),
    saveVisualDocument: async (arg) => {
      calls.push(arg)
      return record(arg.expectedVersion + 1)
    },
    publishVisualDocument: async (arg) => {
      calls.push(arg)
      return {
        ...record(arg.expectedVersion + 1),
        published: content(),
        publishedVersion: arg.expectedVersion + 1
      }
    },
    ...extra
  }
  const session = docs.createDocumentSession({
    kind: 'LAYOUT',
    current: () => current,
    canEdit: () => current,
    api,
    parse: (v) => layout.parseLayout(v, 'WH-A', locations),
    empty: () => layout.emptyLayout('WH-A')
  })
  return {
    session,
    calls,
    record,
    revoke: () => {
      current = false
      session.clear()
    }
  }
}

test('document save/publish use exact string scope and optimistic version; never publish dirty', async () => {
  const { session, calls } = setup()
  await session.load('WH-A')
  const changed = content()
  changed.elements[0].x = 10
  session.change(changed)
  await assert.rejects(() => session.publish())
  await session.save()
  await session.publish()
  assert.equal(calls[0].scopeId, 'WH-A')
  assert.equal(calls[0].expectedVersion, 0)
  assert.equal(calls[1].expectedVersion, 1)
  assert.deepEqual(Object.keys(calls[0]).sort(), ['content', 'expectedVersion', 'kind', 'scopeId'])
})

test('version conflict keeps local draft and blocks save/publish until explicit reload', async () => {
  const { session } = setup({
    saveVisualDocument: async () => {
      throw { response: { status: 409 } }
    }
  })
  await session.load('WH-A')
  const changed = content()
  changed.elements[0].x = 33
  session.change(changed)
  await session.save()
  assert.equal(session.state.conflict, true)
  assert.equal(session.state.content.elements[0].x, 33)
  await assert.rejects(() => session.publish())
})

test('late read/write after auth clear cannot restore data', async () => {
  const pending = deferred()
  const ctx = setup({ getVisualDocument: () => pending.promise })
  const loading = ctx.session.load('WH-A')
  ctx.revoke()
  pending.resolve(ctx.record())
  await loading
  assert.equal(ctx.session.state.content, null)
  assert.equal(ctx.session.state.document, null)
  const write = deferred()
  const ctx2 = setup({ saveVisualDocument: () => write.promise })
  await ctx2.session.load('WH-A')
  const saving = ctx2.session.save()
  ctx2.revoke()
  write.resolve(ctx2.record(1))
  await saving
  assert.equal(ctx2.session.state.document, null)
})

test('reload refuses foreign document scope and stale scope requests', async () => {
  const pending = deferred()
  const ctx = setup({
    getVisualDocument: (kind, scope) =>
      scope === 'WH-A' ? pending.promise : Promise.resolve({ ...ctx.record(), scopeId: 'WH-B' })
  })
  const old = ctx.session.load('WH-A')
  await ctx.session.load('WH-B')
  pending.resolve(ctx.record())
  await old
  assert.equal(ctx.session.state.scopeId, 'WH-B')
  const wrong = setup({ getVisualDocument: async () => ({ ...ctx.record(), scopeId: 'WH-X' }) })
  await wrong.session.load('WH-A')
  assert.equal(wrong.session.state.document, null)
})
