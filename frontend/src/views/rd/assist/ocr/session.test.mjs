import test from 'node:test'
import assert from 'node:assert/strict'
import {
  createOcrSession,
  draftFromResult,
  allowedScope,
  fileKind,
  previewDimensions
} from './session.mjs'

test('new requests, auth clear and dispose all invalidate late inference', () => {
  const session = createOcrSession()
  const first = session.begin()
  const second = session.begin()
  assert.equal(first.signal.aborted, true)
  assert.equal(session.current(first), false)
  assert.equal(session.current(second), true)
  session.clear()
  assert.equal(session.current(second), false)
  assert.equal(second.signal.aborted, true)
  const third = session.begin()
  session.dispose()
  assert.equal(session.current(third), false)
})

test('draft keeps leading zero IDs, unknown and competing fields empty', () => {
  const result = {
    candidates: {
      order: { candidates: [{ value: '00000000000000000001' }] },
      customer: { candidates: [] },
      model: { candidates: [{ value: 'A' }, { value: 'B' }] }
    }
  }
  assert.deepEqual(draftFromResult(result), {
    customer: '',
    order: '00000000000000000001',
    model: '',
    bagCount: '',
    weightKg: ''
  })
})

test('driver cannot use even wildcard create access', () => {
  assert.equal(
    allowedScope({ userId: 1, roles: ['rd_forklift_driver'], permissions: ['*:*:*'] }),
    false
  )
  assert.equal(allowedScope({ userId: 1, roles: [], permissions: ['rd:pickup-slip:create'] }), true)
  assert.equal(
    allowedScope({ userId: 1, roles: [], permissions: ['rd:quality-inspection:create'] }),
    true
  )
  assert.equal(allowedScope({ userId: 1, roles: [], permissions: ['rd:pickup-slip:query'] }), false)
})

test('preview accepts only inspected local PNG JPEG PDF magic', () => {
  assert.equal(fileKind(Uint8Array.from([137, 80, 78, 71, 13, 10, 26, 10])), 'image/png')
  assert.equal(fileKind(Uint8Array.from([255, 216, 255])), 'image/jpeg')
  assert.equal(fileKind(new TextEncoder().encode('%PDF-1.7')), 'application/pdf')
  assert.equal(fileKind(new TextEncoder().encode('<svg onload="x"/>')), null)
})

test('preview dimensions are checked before creating a browser object URL', () => {
  const bytes = new Uint8Array(24)
  bytes.set([137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 13, 73, 72, 68, 82])
  const view = new DataView(bytes.buffer)
  view.setUint32(16, 1600)
  view.setUint32(20, 1000)
  assert.deepEqual(previewDimensions(bytes, 'image/png'), { width: 1600, height: 1000 })
  view.setUint32(16, 5001)
  assert.throws(() => previewDimensions(bytes, 'image/png'))
  assert.throws(() => previewDimensions(Uint8Array.from([255, 216, 255, 217]), 'image/jpeg'))
})
