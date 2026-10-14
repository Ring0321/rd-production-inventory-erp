import assert from 'node:assert/strict'
import { test } from 'node:test'
import 'fake-indexeddb/auto'
import Dexie from 'dexie'
import { evaluate, plain } from '../../pda/warehouse/testHelpers.mjs'

const model = evaluate(new URL('./scanCandidates.ts', import.meta.url), {}, { URL })
const m = evaluate(
  new URL('./pendingScanBox.ts', import.meta.url),
  {
    dexie: Dexie,
    './scanCandidates': model
  },
  { Date }
)
const scope = { ownerId: 5, tenant: '1', kind: 'loading', taskId: 7 }
const factory = () => m.createPendingScanBox('qr-s3-test-' + Date.now() + '-' + Math.random())

test('actual IndexedDB persists a draft across close/reopen, without storing identity details or tokens', async () => {
  const name = 'qr-s3-reopen-' + Date.now()
  let box = m.createPendingScanBox(name)
  await box.collect(scope, 3, 'LD-1', () => true)
  box.close()
  box = m.createPendingScanBox(name)
  const [row] = await box.list(scope)
  assert.equal(row.packageCode, 'LD-1')
  assert.equal(row.state, 'LOCAL')
  assert.deepEqual(
    Object.keys(plain(row)).sort(),
    [
      'id',
      'kind',
      'observedVersion',
      'ownerId',
      'packageCode',
      'seenAt',
      'state',
      'taskId',
      'tenant'
    ].sort()
  )
  box.close()
})
test('user, tenant, task and operation scopes cannot mix records', async () => {
  const box = factory()
  await box.collect(scope, 3, 'LD-1', () => true)
  for (const other of [
    { ...scope, ownerId: 6 },
    { ...scope, tenant: '2' },
    { ...scope, taskId: 8 },
    { ...scope, kind: 'putaway' }
  ])
    assert.equal((await box.list(other)).length, 0)
  assert.equal((await box.listOwner(5, '1')).length, 1)
  assert.equal((await box.listOwner(6, '1')).length, 0)
  box.close()
})
test('runtime scope extras never enter IndexedDB and unexpected stored fields fail closed', async () => {
  const name = 'qr-s3-whitelist-' + Date.now() + '-' + Math.random()
  const box = m.createPendingScanBox(name)
  await box.collect(
    { ...scope, accessToken: 'not-a-token', customerName: 'not-a-customer' },
    3,
    'LD-1',
    () => true
  )
  const [row] = await box.list(scope)
  assert.equal(row.accessToken, undefined)
  assert.equal(row.customerName, undefined)
  const db = new Dexie(name)
  db.version(1).stores({
    candidates: 'id,[ownerId+tenant],[ownerId+tenant+kind+taskId],seenAt'
  })
  await db.table('candidates').put({ ...plain(row), customerName: 'unexpected' })
  await assert.rejects(box.list(scope), /损坏/)
  await assert.rejects(
    box.mark(scope, [{ packageCode: 'LD-1', state: 'VERIFIED', reason: 'ok' }], () => true),
    /损坏/
  )
  assert.equal((await db.table('candidates').get(row.id)).state, 'LOCAL')
  db.close()
  box.close()
})
test('duplicate and concurrent collection preserve the original observed version and one row', async () => {
  const box = factory()
  await Promise.all([
    box.collect(scope, 3, 'LD-1', () => true),
    box.collect(scope, 3, 'LD-1', () => true)
  ])
  await box.collect(scope, 4, 'LD-1', () => true)
  const rows = await box.list(scope)
  assert.equal(rows.length, 1)
  assert.equal(rows[0].observedVersion, 3)
  box.close()
})
test('marking conflict never rebases the source version and removal is scoped and explicit', async () => {
  const box = factory()
  await box.collect(scope, 3, 'LD-1', () => true)
  await box.mark(
    scope,
    [{ packageCode: 'LD-1', state: 'CONFLICT', reason: '版本变化' }],
    () => true
  )
  const [row] = await box.list(scope)
  assert.equal(row.state, 'CONFLICT')
  assert.equal(row.observedVersion, 3)
  await box.remove({ ...scope, taskId: 8 }, 'LD-1', () => true)
  assert.equal((await box.list(scope)).length, 1)
  await box.remove(scope, 'LD-1', () => true)
  assert.equal((await box.list(scope)).length, 0)
  box.close()
})
test('stale identity refuses local writes and missing records make a whole mark transaction roll back', async () => {
  const box = factory()
  await assert.rejects(
    box.collect(scope, 3, 'LD-1', () => false),
    /失效/
  )
  await box.collect(scope, 3, 'LD-1', () => true)
  await assert.rejects(
    box.mark(
      scope,
      [
        { packageCode: 'LD-1', state: 'VERIFIED', reason: 'ok' },
        { packageCode: 'LD-2', state: 'VERIFIED', reason: 'ok' }
      ],
      () => true
    ),
    /移除/
  )
  assert.equal((await box.list(scope))[0].state, 'LOCAL')
  await assert.rejects(
    box.remove(scope, 'LD-1', () => false),
    /变化/
  )
  box.close()
})
