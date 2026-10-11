import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluate } from '../workspace/testHelpers.mjs'

const createHarness = (fetch = async () => [], authorized = true) => {
  const identity = vue.ref(true)
  let calls = 0
  const session = {
    current: () => identity.value,
    allowed: (grant) => identity.value && authorized && grant === 'rd:putaway-task:query'
  }
  const api = {
    getLocationMap: async () => {
      calls++
      return fetch()
    }
  }
  const module = evaluate('../warehouse/useWarehouseLocations.ts', {
    vue,
    '@/api/rd/stock/putaway': { RdPutawayTaskApi: api }
  })
  return { work: module.useWarehouseLocations(session), module, identity, calls: () => calls }
}
const location = (id, overrides = {}) => ({
  id,
  warehouseCode: 'W1',
  warehouseName: '仓库一',
  locationCode: `A-${id}`,
  areaName: '一区',
  enabled: true,
  frozen: false,
  occupiedPackages: 0,
  occupiedWeight: 0,
  reservedPackages: 0,
  reservedWeight: 0,
  displayStatus: 'EMPTY',
  ...overrides
})

test('location reads require the existing warehouse query grant', async () => {
  const h = createHarness(undefined, false)
  await h.work.load()
  assert.equal(h.calls(), 0)
  assert.equal(h.work.rows.value.length, 0)
})

test('map retains actual capacity, occupancy and reservations without guessing unknown values', async () => {
  const rows = [
    location(1, { reservedPackages: 2, reservedWeight: 1500, displayStatus: 'RESERVED' })
  ]
  const h = createHarness(async () => rows)
  await h.work.load()
  assert.equal(h.work.rows.value[0].reservedPackages, 2)
  assert.equal(h.module.amountText(h.work.rows.value[0].capacityPackages, '包'), '未设置')
  assert.equal(h.module.amountText(undefined, 'kg', '未知'), '未知')
  assert.equal(h.module.amountText(0, '包'), '0 包')
  assert.equal(h.module.locationLabel(h.work.rows.value[0]), '已预占')
})

test('disabled and frozen states override a misleading empty display label', () => {
  const h = createHarness()
  assert.equal(h.module.locationLabel(location(1, { enabled: false })), '停用')
  assert.equal(h.module.locationLabel(location(1, { frozen: true })), '冻结')
})

test('warehouse, status and location filters compose without modifying server records', async () => {
  const h = createHarness(async () => [
    location(1),
    location(2, { occupiedPackages: 3, displayStatus: 'OCCUPIED' }),
    location(3, { warehouseCode: 'W2', warehouseName: '仓库二' })
  ])
  await h.work.load()
  assert.equal(h.work.warehouses.value.length, 2)
  h.work.filter.warehouse = 'W1'
  h.work.filter.keyword = 'a-2'
  h.work.filter.state = '已占用'
  assert.deepEqual(
    h.work.filtered.value.map((row) => row.id),
    [2]
  )
  assert.equal(h.work.rows.value.length, 3)
  assert.equal(h.calls(), 1)
})

test('failed refresh shows stale-data error instead of replacing inventory with an empty map', async () => {
  let failing = false
  const h = createHarness(async () => {
    if (failing) throw new Error('Offline')
    return [location(1)]
  })
  await h.work.load()
  const readTime = h.work.readTime.value
  failing = true
  await h.work.load()
  assert.match(h.work.error.value, /未刷新/)
  assert.equal(h.work.rows.value.length, 1)
  assert.equal(h.work.readTime.value, readTime)
})

test('identity change prevents a delayed map response from entering the new session', async () => {
  let release
  const h = createHarness(
    () =>
      new Promise((resolve) => {
        release = resolve
      })
  )
  const loading = h.work.load()
  h.identity.value = false
  release([location(1)])
  await loading
  assert.equal(h.work.rows.value.length, 0)
})

test('an older map reply cannot overwrite the latest refresh', async () => {
  const releases = []
  const h = createHarness(() => new Promise((resolve) => releases.push(resolve)))
  const first = h.work.load()
  const second = h.work.load()
  releases[1]([location(2)])
  await second
  releases[0]([location(1)])
  await first
  assert.deepEqual(
    h.work.rows.value.map((row) => row.id),
    [2]
  )
})

test('non-array map response is an error, not an empty warehouse', async () => {
  const h = createHarness(async () => ({}))
  await h.work.load()
  assert.match(h.work.error.value, /读取失败/)
})
