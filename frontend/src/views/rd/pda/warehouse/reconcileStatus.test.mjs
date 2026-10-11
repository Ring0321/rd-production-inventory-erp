import assert from 'node:assert/strict'
import { test } from 'node:test'
import { evaluate } from './testHelpers.mjs'
const module = evaluate('./reconcileStatus.ts')
const original = {
  kind: 'putaway',
  ownerId: 7,
  tenant: '1',
  uncertain: true,
  request: { id: 3, version: 2, packageCode: 'PKG-1', idempotencyKey: 'REQ-1', taskLineIds: [11] }
}
const status = {
  taskId: 3,
  requestVersion: 2,
  currentVersion: 3,
  taskStatus: 'ASSIGNED',
  state: 'APPLIED',
  recordId: 8,
  message: 'Test'
}
test('matching committed or irrevocably stale replies can be distinguished from unknown', () => {
  for (const state of ['APPLIED', 'NOT_APPLIED_STALE', 'UNKNOWN'])
    assert.equal(module.verifyReconcileStatus({ ...status, state }, original), state)
})
test('wrong identity of task or original version cannot authorize clearing pending data', () => {
  for (const change of [
    { taskId: 4 },
    { requestVersion: 3 },
    { currentVersion: -1 },
    { state: 'OTHER' },
    { recordId: undefined }
  ])
    assert.throws(() => module.verifyReconcileStatus({ ...status, ...change }, original))
})
test('absence at the same version never proves the old request cannot execute', () => {
  assert.throws(() =>
    module.verifyReconcileStatus(
      { ...status, state: 'NOT_APPLIED_STALE', currentVersion: 2 },
      original
    )
  )
  assert.equal(
    module.verifyReconcileStatus({ ...status, state: 'UNKNOWN', currentVersion: 2 }, original),
    'UNKNOWN'
  )
})

test('a future original version is not irrevocably stale', () => {
  assert.throws(() =>
    module.verifyReconcileStatus(
      { ...status, state: 'NOT_APPLIED_STALE', currentVersion: 1 },
      original
    )
  )
  assert.equal(
    module.verifyReconcileStatus({ ...status, state: 'UNKNOWN', currentVersion: 1 }, original),
    'UNKNOWN'
  )
})
