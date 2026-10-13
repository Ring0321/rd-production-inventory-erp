import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { evaluateSource, deferred } from '../../trace/package/testHelpers.mjs'

const layout = evaluateSource(readFileSync(new URL('./spatialLayout.ts', import.meta.url), 'utf8'))
const digital = evaluateSource(readFileSync(new URL('../../../../utils/rdDigitalLink.ts', import.meta.url), 'utf8'), {}, { URL })
const source = readFileSync(new URL('./useMapPackageLookup.ts', import.meta.url), 'utf8')
const packageRow = (extra = {}) => ({ id: 9, packageCode: 'P1', status: 'IN_STOCK', locationId: 2, stockLotId: 5, version: 3, ...extra })
const lot = (extra = {}) => ({ id: 5, locationId: 2, warehouseQty: 2, availableQty: 0, ...extra })
const setup = (trace, stock = async () => lot()) => {
  const state = vue.reactive({ allowed: true, locations: [{ id: 2, locationCode: 'L2', warehouseCode: 'W1' }] })
  const picked = []
  const reads = []
  const imported = {
    vue: { ...vue, onBeforeUnmount() {}, onDeactivated() {} },
    '@/api/rd/stock/package': { RdPackageUnitApi: { trace: async p => { reads.push(p); return trace(p) } } },
    '@/api/rd/stock/lot': { RdStockLotApi: { get: stock } },
    '@/utils/rdDigitalLink': digital,
    './spatialLayout': layout
  }
  const { useMapPackageLookup } = evaluateSource(source, imported)
  const lookup = useMapPackageLookup({ allowed: () => state.allowed, locations: () => state.locations, locate: async (id, live) => { if (live()) picked.push(id) } })
  lookup.raw.value = 'P1'
  return { lookup, picked, reads, state }
}

test('QR parsing only queries the exact local code, fully locked stock is still locatable', async () => {
  const s = setup(async () => [packageRow()])
  s.lookup.raw.value = 'https://legacy.example/pkg/P1?internal=1'
  await s.lookup.lookup()
  assert.deepEqual(s.picked, [2])
  assert.equal(s.reads.length, 2)
  assert.ok(s.reads.every(x => x.packageCode === 'P1'))
})
test('outbound, pending, missing, ambiguous and mismatched inventory never locate stale fields', async () => {
  for (const status of ['OUTBOUNDED', 'INBOUND_PENDING', 'STOCKTAKE_MISSING', 'VOIDED']) {
    const s = setup(async () => [packageRow({ status })])
    await s.lookup.lookup()
    assert.deepEqual(s.picked, [])
    assert.ok(s.lookup.feedback.value)
  }
  for (const rows of [[], [packageRow(), packageRow()], [packageRow({ packageCode: 'P11' })]]) {
    const s = setup(async () => rows)
    await s.lookup.lookup()
    assert.deepEqual(s.picked, [])
  }
  const s = setup(async () => [packageRow()], async () => lot({ locationId: 3 }))
  await s.lookup.lookup()
  assert.deepEqual(s.picked, [])
})
test('denied access and invalid scan data do not call protected APIs', async () => {
  const s = setup(async () => [packageRow()])
  s.state.allowed = false
  await s.lookup.lookup()
  assert.equal(s.reads.length, 0)
  s.state.allowed = true
  for (const raw of ['https://other.example/no-package', 'P1/child', 'a'.repeat(129), 'P1\nP2']) {
    s.lookup.raw.value = raw
    await s.lookup.lookup()
  }
  assert.equal(s.reads.length, 0)
})
test('permission changes, newer lookups and map refresh reject late responses', async () => {
  const wait = deferred()
  const s = setup(async () => wait.promise)
  const task = s.lookup.lookup()
  s.state.allowed = false
  wait.resolve([packageRow()])
  await task
  assert.deepEqual(s.picked, [])
  assert.equal(s.lookup.busy.value, false)
  const refresh = deferred()
  const r = setup(async () => refresh.promise)
  const pending = r.lookup.lookup()
  r.state.locations = []
  await vue.nextTick()
  refresh.resolve([packageRow()])
  await pending
  assert.deepEqual(r.picked, [])
})
test('a changed package version between reads is not presented as current location', async () => {
  let count = 0
  const s = setup(async () => [packageRow({ version: ++count })])
  await s.lookup.lookup()
  assert.deepEqual(s.picked, [])
  assert.match(s.lookup.feedback.value, /变化/)
})
test('a newer code query wins and the old result cannot replace its feedback or location', async () => {
  const first = deferred()
  const s = setup(async p => p.packageCode === 'P1' ? first.promise : [packageRow({ packageCode: 'P2' })])
  const old = s.lookup.lookup()
  s.lookup.raw.value = 'P2'
  await s.lookup.lookup()
  const message = s.lookup.feedback.value
  first.resolve([packageRow()])
  await old
  assert.deepEqual(s.picked, [2])
  assert.equal(s.lookup.feedback.value, message)
})
