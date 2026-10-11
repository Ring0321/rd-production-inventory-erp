import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createTaskFeed } from './taskFeed.ts'

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}

test('unauthorized feeds never request data', async () => {
  let calls = 0
  const feed = createTaskFeed(
    () => false,
    async () => {
      calls++
      return { list: [], total: 0 }
    }
  )
  await feed.load()
  assert.equal(calls, 0)
  assert.equal(feed.state.status, 'unauthorized')
  assert.deepEqual(feed.state.rows, [])
  assert.equal(feed.state.total, undefined)
})

test('standard PageResult yields actual rows, total and an independent empty state', async () => {
  let params
  const feed = createTaskFeed(
    () => true,
    async (page) => {
      params = page
      return { list: [{ id: 42 }], total: 19 }
    }
  )
  await feed.load(2)
  assert.deepEqual(params, { pageNo: 2, pageSize: 5 })
  assert.equal(feed.state.status, 'ready')
  assert.equal(feed.state.total, 19)
  assert.equal(feed.state.rows[0].id, 42)
  assert.equal(feed.state.pageNo, 2)

  const empty = createTaskFeed(
    () => true,
    async () => ({ list: [], total: 0 })
  )
  await empty.load()
  assert.equal(empty.state.status, 'empty')
  assert.equal(empty.state.total, 0)
})

test('load failures clear stale results and retry recovers', async () => {
  let failing = false
  const feed = createTaskFeed(
    () => true,
    async () => {
      if (failing) throw new Error('request failed')
      return { list: [{ id: 1 }], total: 1 }
    }
  )
  await feed.load()
  failing = true
  await feed.load()
  assert.equal(feed.state.status, 'error')
  assert.deepEqual(feed.state.rows, [])
  assert.equal(feed.state.total, undefined)
  failing = false
  await feed.load()
  assert.equal(feed.state.status, 'ready')
})

test('profile reset immediately clears data and ignores a late response', async () => {
  const pending = deferred()
  const feed = createTaskFeed(
    () => true,
    () => pending.promise
  )
  const loading = feed.load()
  assert.equal(feed.state.status, 'loading')
  feed.reset()
  assert.equal(feed.state.status, 'unauthorized')
  pending.resolve({ list: [{ id: 1 }], total: 1 })
  await loading
  assert.deepEqual(feed.state.rows, [])
  assert.equal(feed.state.total, undefined)
})

test('the latest request wins, including late errors from an earlier page', async () => {
  const first = deferred()
  const second = deferred()
  const feed = createTaskFeed(
    () => true,
    (params) => (params.pageNo === 1 ? first.promise : second.promise)
  )
  const old = feed.load(1)
  const fresh = feed.load(2)
  second.resolve({ list: [{ id: 2 }], total: 6 })
  await fresh
  first.reject(new Error('late error'))
  await old
  assert.equal(feed.state.status, 'ready')
  assert.equal(feed.state.rows[0].id, 2)
})

test('permission revocation prevents an in-flight response from rendering', async () => {
  let allowed = true
  const pending = deferred()
  const feed = createTaskFeed(
    () => allowed,
    () => pending.promise
  )
  const loading = feed.load()
  allowed = false
  feed.reset()
  pending.resolve({ list: [{ id: 1 }], total: 1 })
  await loading
  assert.equal(feed.state.status, 'unauthorized')
  assert.deepEqual(feed.state.rows, [])
})

test('non-PageResult and invalid totals are errors, never fabricated counters', async () => {
  for (const data of [null, [], { list: [] }, { list: [], total: -1 }, { list: [], total: NaN }]) {
    const feed = createTaskFeed(
      () => true,
      async () => data
    )
    await feed.load()
    assert.equal(feed.state.status, 'error')
    assert.equal(feed.state.total, undefined)
  }
})
