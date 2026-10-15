import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource, plain } from '../../trace/package/testHelpers.mjs'

const f = evaluateSource(readFileSync(new URL('./replayFacts.ts', import.meta.url), 'utf8'))
const bag = (id) => ({
  id,
  packageCode: `P${id}`,
  status: 'IN_STOCK',
  packageWeight: 1000,
  locationId: 999
})
const event = (id, packageId, time, quantity, locationId = 10) => ({
  id,
  packageId,
  packageCode: `P${packageId}`,
  eventTime: time,
  quantity,
  locationId,
  locationCode: `A-${locationId}`,
  packageWeight: 800,
  transactionType: quantity > 0 ? 'INBOUND' : 'OUTBOUND'
})
test('history follows exact member and recorded event time, never current location', () => {
  const e = [event(2, 1, 2000, -1), event(1, 1, 1000, 1)]
  assert.equal(f.replaySnapshot([bag(1), bag(2)], e, 1500).positions[0].locationId, 10)
  assert.equal(f.replaySnapshot([bag(1), bag(2)], e, 2500).inStockCount, 0)
  assert.equal(f.replaySnapshot([bag(1), bag(2)], e, 2500).outCount, 1)
  assert.equal(f.replaySnapshot([bag(1), bag(2)], e, 2500).unknownCount, 1)
  assert.throws(() => f.validateReplayEvents([bag(1)], [event(1, 2, 1000, 1)]))
})
test('missing time, non-unit change and unrecognized transaction cannot imply stock', () => {
  for (const patch of [{ eventTime: null }, { quantity: 2 }, { transactionType: 'NEW_KIND' }]) {
    const s = f.replaySnapshot([bag(1)], [{ ...event(1, 1, 1000, 1), ...patch }], 5000)
    assert.equal(s.inStockCount, 0)
    assert.equal(s.unknownCount, 1)
  }
})
test('two timestamps tie by immutable transaction ID, tail weight comes from historical ledger', () => {
  const s = f.replaySnapshot([bag(1)], [event(2, 1, 1000, -1), event(1, 1, 1000, 1)], 1000)
  assert.equal(s.outCount, 1)
  const inbound = f.replaySnapshot([bag(1)], [event(1, 1, 1000, 1)], 1000)
  assert.equal(inbound.weightKg, 800)
  assert.equal(inbound.positions[0].locationId, 10)
})
test('missing historical weight remains unknown and negative weight never counted as zero', () => {
  const s = f.replaySnapshot([bag(1)], [{ ...event(1, 1, 1000, 1), packageWeight: null }], 1000)
  assert.equal(s.weightKg, null)
  assert.equal(s.unknownWeightCount, 1)
})
test('invalid identity or duplicate ledger IDs fails instead of showing partial totals', () => {
  assert.throws(() =>
    f.validateReplayEvents([bag(1)], [event(1, 1, 1000, 1), event(1, 1, 2000, -1)])
  )
  assert.throws(() => f.validateReplayEvents([bag(1), bag(1)], []))
  assert.throws(() =>
    f.validateReplayEvents([bag(1)], [{ ...event(1, 1, 1000, 1), packageCode: 'P2' }])
  )
})
test('timeline textual labels are escaped before third-party HTML rendering', () => {
  assert.equal(
    f.escapeTimelineText('<img src=x onerror=alert(1)>'),
    '&lt;img src=x onerror=alert(1)&gt;'
  )
  assert.equal(f.recordedTime('2026-10-15T10:00:00+08:00'), Date.parse('2026-10-15T02:00:00Z'))
  assert.equal(f.recordedTime('2026-10-15'), undefined)
  assert.equal(f.recordedTime(null), undefined)
})
test('staged permissions forbid driver and do not combine unrelated partial grants', () => {
  assert.equal(f.canReplay(['rd_forklift_driver'], new Set(['*:*:*'])), false)
  assert.equal(f.canReplay([], new Set(['rd:package-unit:query'])), false)
  assert.equal(f.canReplay([], new Set(['*:*:*'])), true)
})
