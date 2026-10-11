import assert from 'node:assert/strict'
import { test } from 'node:test'
import { bindProductionDraft, restoreProductionDraft } from './draftRequest.ts'

const task = {
  id: 12,
  taskNo: 'SC-TEST',
  customerName: 'Customer A',
  productionDate: '2026-10-11',
  shiftCode: 'A',
  status: 'ACTIVE'
}
const fields = {
  productModel: 'Model A',
  furnaceNo: 'F-1',
  furnaceLayer: 'L-1',
  outputQty: 2,
  packageWeight: 875.125,
  furnaceOutDate: '',
  remark: 'Actual weight'
}
const owner = { operatorId: 7, tenantId: '1' }

test('binds a crypto UUID to an immutable draft and inherited task fields', () => {
  const input = { ...fields }
  const draft = bindProductionDraft(task, input, owner)
  assert.match(draft.payload.clientRequestId, /^[\da-f]{8}(-[\da-f]{4}){3}-[\da-f]{12}$/i)
  assert.equal(draft.payload.productionTaskId, task.id)
  assert.equal(draft.payload.customerName, task.customerName)
  assert.equal(draft.payload.productionDate, task.productionDate)
  assert.equal(draft.payload.shiftCode, task.shiftCode)
  assert.equal(draft.payload.status, 'DRAFT')
  assert.equal(draft.payload.furnaceOutDate, undefined)
  assert.equal('locationId' in draft.payload, false)
  assert.equal('operatorId' in draft.payload, false)
  input.packageWeight = 1000
  assert.equal(draft.payload.packageWeight, 875.125)
  assert.ok(Object.isFrozen(draft))
  assert.ok(Object.isFrozen(draft.payload))
  assert.throws(() => (draft.payload.productionTaskId = 99), TypeError)
})

test('session restoration preserves the exact retry request ID and payload', () => {
  const draft = bindProductionDraft(task, fields, owner)
  const restored = restoreProductionDraft(JSON.stringify(draft), owner)
  assert.deepEqual(restored, draft)
  assert.ok(Object.isFrozen(restored.payload))
})

test('restoration rejects another operator or tenant', () => {
  const serialized = JSON.stringify(bindProductionDraft(task, fields, owner))
  assert.throws(() => restoreProductionDraft(serialized, { ...owner, operatorId: 8 }))
  assert.throws(() => restoreProductionDraft(serialized, { ...owner, tenantId: '2' }))
})

test('does not bind new drafts to closed or cancelled tasks', () => {
  for (const status of ['CLOSED', 'CANCELLED']) {
    assert.throws(() => bindProductionDraft({ ...task, status }, fields, owner))
  }
})

test('requires positive integer bag counts and actual positive bag weights', () => {
  for (const outputQty of [0, -1, 1.5, NaN, Infinity]) {
    assert.throws(() => bindProductionDraft(task, { ...fields, outputQty }, owner))
  }
  for (const packageWeight of [0, -1, NaN, Infinity]) {
    assert.throws(() => bindProductionDraft(task, { ...fields, packageWeight }, owner))
  }
})

test('rejects damaged or expanded cached payloads without silently creating a new key', () => {
  const draft = bindProductionDraft(task, fields, owner)
  assert.throws(() => restoreProductionDraft('{', owner))
  assert.throws(() => restoreProductionDraft(JSON.stringify({ ...draft, payload: {} }), owner))
  assert.throws(() =>
    restoreProductionDraft(
      JSON.stringify({ ...draft, payload: { ...draft.payload, locationId: 3 } }),
      owner
    )
  )
})
