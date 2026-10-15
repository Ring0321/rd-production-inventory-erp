import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import * as vue from 'vue'
import { deferred, evaluateSource, plain } from '../../trace/package/testHelpers.mjs'

const read = (file) => readFileSync(new URL(file, import.meta.url), 'utf8')
const helper = () => evaluateSource(read('./locationLoading.ts'), {}, { Error })
const location = (id, warehouseCode = 'A') => ({
  id,
  warehouseCode,
  warehouseName: warehouseCode,
  locationCode: `${warehouseCode}-${id}`,
  enabled: id !== 101,
  frozen: id === 102
})
const options = (extra = {}) => ({
  allowed: (permission) => permission === 'rd:warehouse-location:query',
  current: () => true,
  getPage: async () => ({ list: [], total: 0 }),
  getLocationMap: async () => assert.fail('unexpected map fallback'),
  ...extra
})

test('loads every original page beyond 100, retaining disabled and frozen bindings', async () => {
  const rows = Array.from({ length: 205 }, (_, index) => location(index + 1))
  const calls = []
  const result = await helper().loadLayoutLocations(
    options({
      getPage: async (params) => {
        calls.push(plain(params))
        return { list: rows.slice((params.pageNo - 1) * 100, params.pageNo * 100), total: 205 }
      }
    })
  )
  assert.equal(result.length, 205)
  assert.equal(result[100].enabled, false)
  assert.equal(result[101].frozen, true)
  assert.deepEqual(
    calls,
    [1, 2, 3].map((pageNo) => ({ pageNo, pageSize: 100 }))
  )
  const schema = evaluateSource(read('./visualSchema.ts'))
  const model = evaluateSource(read('./layoutModel.ts'), { './visualSchema': schema })
  const content = {
    ...model.emptyLayout('A'),
    elements: [101, 102].map((id) => ({
      id: `bin-${id}`,
      kind: 'LOCATION',
      locationId: id,
      x: id,
      y: 0,
      width: 50,
      height: 50,
      label: `A-${id}`
    }))
  }
  assert.deepEqual(plain(model.parseLayout(content, 'A', result)), plain(content))
})

test('rejects duplicate IDs or duplicate same-warehouse codes instead of accepting partial lists', async () => {
  const first = Array.from({ length: 100 }, (_, index) => location(index + 1))
  for (const duplicate of [location(1), { ...location(101), locationCode: 'A-1' }]) {
    await assert.rejects(
      () =>
        helper().loadLayoutLocations(
          options({
            getPage: async ({ pageNo }) => ({
              list: pageNo === 1 ? first : [duplicate],
              total: 101
            })
          })
        ),
      /重复/
    )
  }
})

test('rejects invalid, changing, truncated or over-cap totals with bounded requests', async () => {
  const h = helper()
  for (const total of [-1, 1.5, undefined, '1', h.MAX_LAYOUT_LOCATION_PAGES * 100 + 1]) {
    let calls = 0
    await assert.rejects(() =>
      h.loadLayoutLocations(
        options({
          getPage: async () => {
            calls++
            return { list: [], total }
          }
        })
      )
    )
    assert.equal(calls, 1)
  }
  for (const page of [
    { list: [location(101)], total: 102 },
    { list: [], total: 101 },
    { list: [location(101), location(102)], total: 101 }
  ]) {
    await assert.rejects(() =>
      h.loadLayoutLocations(
        options({
          getPage: async ({ pageNo }) =>
            pageNo === 1
              ? { list: Array.from({ length: 100 }, (_, i) => location(i + 1)), total: 101 }
              : page
        })
      )
    )
  }
  await assert.rejects(() =>
    h.loadLayoutLocations(
      options({
        getPage: async () => ({ list: [location(1)], total: 101 })
      })
    )
  )
})

test('exactly full final page and the safety-cap boundary do not issue a speculative extra request', async () => {
  const h = helper()
  for (const total of [0, 100, h.MAX_LAYOUT_LOCATION_PAGES * 100]) {
    let calls = 0
    const result = await h.loadLayoutLocations(
      options({
        getPage: async ({ pageNo }) => {
          calls++
          return {
            list: Array.from({ length: Math.min(100, total - (pageNo - 1) * 100) }, (_, i) =>
              location((pageNo - 1) * 100 + i + 1)
            ),
            total
          }
        }
      })
    )
    assert.equal(result.length, total)
    assert.equal(calls, Math.max(1, total / 100))
  }
})

test('never falls back after a page failure and checks the original map permission', async () => {
  await assert.rejects(
    () =>
      helper().loadLayoutLocations(
        options({
          getPage: async () => {
            throw new Error('page denied')
          }
        })
      ),
    /page denied/
  )
  let mapCalls = 0
  const fallback = options({
    allowed: (permission) => permission === 'rd:putaway-task:query',
    getPage: async () => assert.fail('unexpected page query'),
    getLocationMap: async () => {
      mapCalls++
      return [location(102)]
    }
  })
  assert.equal((await helper().loadLayoutLocations(fallback))[0].frozen, true)
  await assert.rejects(() => helper().loadLayoutLocations({ ...fallback, allowed: () => false }))
  assert.equal(mapCalls, 1)
  await assert.rejects(
    () =>
      helper().loadLayoutLocations({
        ...fallback,
        getLocationMap: async () => [location(1), location(1)]
      }),
    /重复/
  )
})

test('stale/auth-invalid pagination stops before requesting or returning another page', async () => {
  let current = true
  let calls = 0
  const pending = deferred()
  const loading = helper().loadLayoutLocations(
    options({
      current: () => current,
      getPage: async () => {
        calls++
        return pending.promise
      }
    })
  )
  current = false
  pending.resolve({ list: Array.from({ length: 100 }, (_, i) => location(i + 1)), total: 101 })
  await assert.rejects(() => loading)
  assert.equal(calls, 1)
  await assert.rejects(() =>
    helper().loadLayoutLocations(
      options({
        current: () => false,
        getPage: async () => assert.fail('invalid context queried')
      })
    )
  )
})

const setupEditor = ({ query = {}, getPage, permissions, confirm } = {}) => {
  const calls = []
  const mounted = []
  const clearers = []
  let current = true
  const access = {
    current: () => current,
    allowed: (permission) =>
      current &&
      (permissions || ['rd:warehouse-location:query', 'rd:warehouse-location:update']).includes(
        permission
      ),
    valid: vue.ref(true),
    canRead: vue.ref(true),
    canEdit: vue.ref(true),
    onClear: (fn) => clearers.push(fn)
  }
  const schema = evaluateSource(read('./visualSchema.ts'))
  const model = evaluateSource(read('./layoutModel.ts'), { './visualSchema': schema })
  const session = evaluateSource(read('./documentSession.ts'), { vue })
  const route = vue.reactive({ query })
  const source = read('./WarehouseLayoutEditor.vue').match(
    /<script setup lang="ts">([\s\S]*?)<\/script>/
  )[1]
  const effects = vue.effectScope()
  const s = effects.run(() =>
    evaluateSource(
      source +
        '\nexport { reload, switchWarehouse, locations, scope, state, error, locationsBusy };',
      {
        vue: { ...vue, onMounted: (fn) => mounted.push(fn) },
        'vue-router': { useRoute: () => route },
        'element-plus': { ElMessage: { success() {} } },
        '@element-plus/icons-vue': {},
        '@/api/rd/masterdata/location': {
          RdWarehouseLocationApi: {
            getOptions: async () => [location(1)],
            getPage: getPage || (async () => ({ list: [location(1), location(2, 'B')], total: 2 }))
          }
        },
        '@/api/rd/stock/putaway': {
          RdPutawayTaskApi: { getLocationMap: async () => [location(2, 'B')] }
        },
        '@/api/rd/assist/visualDocument': {
          getVisualDocument: async (kind, scopeId) => {
            calls.push(scopeId)
            return { kind, scopeId, version: 1, draft: null, published: null, publishedVersion: 0 }
          }
        },
        './documentSession': session,
        './useVisualAccess': { useVisualAccess: () => access },
        './editorDialogs': {
          createEditorDialogs: () => ({ confirm: confirm || (async () => {}), close() {} })
        },
        './layoutModel': model,
        './locationLoading': helper()
      },
      { Error }
    )
  )
  return {
    ...s,
    calls,
    route,
    mount: () => mounted[0](),
    revoke: () => {
      current = false
      clearers.forEach((fn) => fn())
    },
    dispose: () => effects.stop()
  }
}

test('editor honors exact requested warehouse, preserves manual switches and reload scope', async () => {
  const s = setupEditor({ query: { warehouseCode: 'B' } })
  try {
    await s.mount()
    assert.equal(s.scope.value, 'B')
    assert.deepEqual(s.calls, ['B'])
    await s.switchWarehouse('A')
    await s.reload()
    assert.equal(s.scope.value, 'A')
    assert.deepEqual(s.calls, ['B', 'A', 'A'])
    await s.switchWarehouse('unknown')
    assert.equal(s.scope.value, 'A')
    assert.deepEqual(s.calls, ['B', 'A', 'A'])
  } finally {
    s.dispose()
  }
})

test('unavailable or malformed requested scope never silently loads the first warehouse', async () => {
  for (const warehouseCode of ['missing', 'b', ' B ', '', ['B'], null]) {
    const s = setupEditor({ query: { warehouseCode } })
    try {
      await s.mount()
      assert.deepEqual(s.calls, [])
      assert.equal(s.state.document, null)
      assert.equal(s.scope.value, '')
      assert.match(s.error.value, /仓库/)
      await s.switchWarehouse('B')
      assert.equal(s.scope.value, 'B')
      assert.equal(s.error.value, '')
      await s.reload()
      assert.equal(s.scope.value, 'B')
    } finally {
      s.dispose()
    }
  }
})

test('no requested scope retains the original first-warehouse default', async () => {
  const s = setupEditor()
  try {
    await s.mount()
    assert.equal(s.scope.value, 'A')
  } finally {
    s.dispose()
  }
})

test('retry after a failed reload keeps the last exact warehouse instead of falling back to the first', async () => {
  let fail = false
  const s = setupEditor({
    query: { warehouseCode: 'B' },
    getPage: async () => {
      if (fail) throw new Error('read failed')
      return { list: [location(1), location(2, 'B')], total: 2 }
    }
  })
  try {
    await s.mount()
    fail = true
    await s.reload()
    assert.equal(s.state.document, null)
    fail = false
    await s.reload()
    assert.equal(s.scope.value, 'B')
    assert.deepEqual(s.calls, ['B', 'B'])
  } finally {
    s.dispose()
  }
})

test('a newer requested warehouse rejects the previous in-flight paginated result', async () => {
  const first = deferred()
  const second = deferred()
  let reads = 0
  const s = setupEditor({
    query: { warehouseCode: 'B' },
    getPage: () => (++reads === 1 ? first.promise : second.promise)
  })
  try {
    const old = s.mount()
    await vue.nextTick()
    s.route.query.warehouseCode = 'A'
    await vue.nextTick()
    second.resolve({ list: [location(1), location(2, 'B')], total: 2 })
    await new Promise(setImmediate)
    assert.equal(s.scope.value, 'A')
    assert.deepEqual(s.calls, ['A'])
    first.resolve({ list: [location(2, 'B')], total: 1 })
    await old
    assert.equal(s.scope.value, 'A')
    assert.equal(s.locations.value.length, 2)
    assert.deepEqual(s.calls, ['A'])
  } finally {
    s.dispose()
  }
})

test('scope confirmation cannot resume a warehouse load after auth invalidation', async () => {
  const confirmation = deferred()
  const s = setupEditor({ confirm: () => confirmation.promise })
  try {
    await s.mount()
    s.state.dirty = true
    const switching = s.switchWarehouse('B')
    s.revoke()
    confirmation.resolve()
    await switching
    assert.deepEqual(s.calls, ['A'])
    assert.equal(s.scope.value, '')
    assert.equal(s.state.document, null)
  } finally {
    s.dispose()
  }
})

test('editor clears stale location metadata on reload failure and auth invalidation', async () => {
  let fail = false
  const pending = deferred()
  const s = setupEditor({
    getPage: async () => (fail ? pending.promise : { list: [location(1)], total: 1 })
  })
  try {
    await s.mount()
    fail = true
    const loading = s.reload()
    await vue.nextTick()
    pending.reject(new Error('read failed'))
    await loading
    assert.equal(s.locations.value.length, 0)
    assert.equal(s.state.document, null)
    assert.equal(s.scope.value, '')
  } finally {
    s.dispose()
  }
  const stale = deferred()
  const second = setupEditor({ getPage: () => stale.promise })
  try {
    const loading = second.mount()
    await vue.nextTick()
    second.revoke()
    stale.resolve({ list: [location(1)], total: 1 })
    await loading
    assert.equal(second.locations.value.length, 0)
    assert.deepEqual(second.calls, [])
    assert.equal(second.locationsBusy.value, false)
  } finally {
    second.dispose()
  }
})
