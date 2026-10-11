import assert from 'node:assert/strict'
import { test } from 'node:test'
import { evaluate } from '../workspace/testHelpers.mjs'

const module = evaluate('../warehouse/batchPending.ts')
const snapshot = (kind = 'putaway') => ({
  kind,
  ownerId: 7,
  tenant: '1',
  uncertain: false,
  request:
    kind === 'putaway'
      ? { id: 3, packageCode: 'PKG-1', version: 2, idempotencyKey: 'REQ-1', taskLineIds: [11, 12] }
      : {
          taskId: 4,
          version: 2,
          representativePackageCode: 'PKG-1',
          idempotencyKey: 'REQ-2',
          taskLineIds: [21, 22]
        }
})

test('putaway and loading snapshots keep the exact request and a distinct namespace', () => {
  for (const kind of ['putaway', 'loading']) {
    const value = snapshot(kind)
    const captured = module.captureBatchPending(value)
    assert.equal(JSON.stringify(captured), JSON.stringify(value))
    value.request.taskLineIds.pop()
    assert.equal(captured.request.taskLineIds.length, 2)
    assert.equal(module.batchPendingKey(kind, 7, '1'), `rd-warehouse-pending:1:7:${kind}`)
  }
})

test('another account, tenant or operation cannot consume the original snapshot', () => {
  const raw = JSON.stringify(snapshot())
  for (const [kind, userId, tenant] of [
    ['putaway', 8, '1'],
    ['putaway', 7, '2'],
    ['loading', 7, '1']
  ])
    assert.throws(() => module.parseBatchPending(raw, kind, userId, tenant))
})

test('malformed requests cannot become retry writes', () => {
  for (const change of [
    (value) => {
      value.request.taskLineIds = []
    },
    (value) => {
      value.request.taskLineIds = [11, 11]
    },
    (value) => {
      value.request.taskLineIds = [0]
    },
    (value) => {
      value.request.version = -1
    },
    (value) => {
      value.request.id = undefined
    },
    (value) => {
      value.request.idempotencyKey = ''
    },
    (value) => {
      value.uncertain = undefined
    }
  ]) {
    const value = snapshot()
    change(value)
    assert.throws(() => module.parseBatchPending(JSON.stringify(value), 'putaway', 7, '1'))
  }
})

test('uncertainty is retained across a reload and bad JSON is not silently discarded', () => {
  const value = snapshot('loading')
  value.uncertain = true
  assert.equal(module.parseBatchPending(JSON.stringify(value), 'loading', 7, '1').uncertain, true)
  assert.throws(() => module.parseBatchPending('{', 'loading', 7, '1'))
})
