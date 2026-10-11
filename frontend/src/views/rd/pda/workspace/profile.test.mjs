import assert from 'node:assert/strict'
import { test } from 'node:test'
import { profileModule as profile } from './testHelpers.mjs'
const permissions = (...values) => new Set(values)
const allRoutes = () => true
const ids = (roles, grants, routes = allRoutes) =>
  Array.from(profile.getWorkspaceProfiles(roles, grants, routes), (item) => item.id)

test('verified operational roles stay in their own context', () => {
  const cases = [
    ['rd_production', 'rd:production-task:query', 'production'],
    ['rd_quality', 'rd:quality-inspection:query', 'quality'],
    ['rd_warehouse', 'rd:putaway-task:query', 'warehouse'],
    ['rd_warehouse_operator', 'rd:inbound-order:query', 'warehouse'],
    ['rd_forklift_driver', 'rd:putaway-task:execute', 'driver'],
    ['rd_sales', 'rd:pickup-slip:query', 'sales']
  ]
  for (const [role, grant, expected] of cases) {
    assert.deepEqual(ids([role], permissions(grant)), [expected])
  }
  assert.deepEqual(ids(['rd_production'], permissions('*:*:*')), ['production'])
  assert.deepEqual(ids(['rd_forklift_driver'], permissions('*:*:*')), ['driver'])
})

test('audit and readonly are query-only even with accidental execution grants', () => {
  for (const role of ['rd_audit', 'rd_readonly']) {
    assert.deepEqual(ids([role, 'rd_production'], permissions('*:*:*')), ['query'])
  }
})

test('admin defaults to query and can select permitted operational profiles', () => {
  for (const role of ['admin', 'super_admin']) {
    const result = ids([role], permissions('*:*:*'))
    assert.equal(result[0], 'query')
    assert.ok(result.includes('production'))
    assert.ok(result.includes('driver'))
    assert.ok(result.includes('quality'))
    assert.ok(result.includes('warehouse'))
  }
  assert.deepEqual(ids(['super_admin'], permissions('rd:stock-lot:query')), ['query'])
  assert.deepEqual(ids(['admin'], permissions()), [])
  assert.deepEqual(ids(['admin'], permissions('rd:putaway-task:query', 'rd:putaway-task:create')), [
    'query',
    'warehouse'
  ])
})

test('custom roles use capabilities, not role spelling or identities', () => {
  assert.deepEqual(ids(['custom_rd_production_admin'], permissions('rd:stock-lot:query')), [
    'query'
  ])
  assert.deepEqual(ids(['custom'], permissions('rd:loading-task:execute')), ['driver'])
  assert.deepEqual(ids([], permissions('rd:loading-task:execute')), ['driver'])
  assert.deepEqual(
    ids(['custom'], permissions('rd:quality-inspection:query', 'rd:quality-inspection:submit')),
    ['quality']
  )
  assert.deepEqual(ids(['custom'], permissions()), [])
  for (const role of ['constructor', 'toString', '__proto__']) {
    assert.deepEqual(ids([role], permissions('rd:stock-lot:query')), ['query'])
    assert.equal(profile.getRoleLabel([role]), '自定义岗位')
  }
})

test('query-only custom roles retain their permitted real query routes', () => {
  for (const [grant, routeName] of [
    ['rd:production-task:query', 'RdProductionTask'],
    ['rd:production-output:query', 'RdProductionOutput'],
    ['rd:quality-inspection:query', 'RdQualityInspection'],
    ['rd:warehouse-location:query', 'RdWarehouseLocation'],
    ['rd:sale-order:query', 'RdSaleOrder'],
    ['rd:stock-transaction:query', 'RdStockTransaction']
  ]) {
    const grants = permissions(grant)
    assert.deepEqual(ids(['custom'], grants), ['query'])
    const entries = profile.getProfileEntries('query', grants, allRoutes)
    assert.equal(entries.length, 1)
    assert.equal(entries[0].routeName, routeName)
    assert.equal(entries[0].kind, 'query')
    assert.equal(profile.getTaskQueries('query', grants, allRoutes).length, 0)
  }
  assert.deepEqual(ids(['rd_sales'], permissions('rd:sale-order:query')), ['sales'])
})

test('missing routes and grants hide entries without granting new capabilities', () => {
  assert.equal(profile.getProfileEntries('production', permissions(), allRoutes).length, 0)
  assert.equal(profile.getProfileEntries('driver', permissions('*:*:*'), () => false).length, 0)
  const entries = profile.getProfileEntries(
    'production',
    permissions('rd:production-output:create'),
    allRoutes
  )
  assert.ok(!entries.some((item) => item.routeName === 'PdaProduction'))
  const warehouse = profile.getProfileEntries('warehouse', permissions('*:*:*'), allRoutes)
  assert.ok(warehouse.some((item) => item.routeName === 'RdPutawayWorkbench'))
  assert.ok(!warehouse.some((item) => item.routeName === 'RdPutawayWorkstation'))
})

test('quality role uses only the independent PDA quality entry with existing query permission', () => {
  const grants = permissions('rd:quality-inspection:query', 'rd:package-unit:query')
  const entries = profile.getProfileEntries('quality', grants, allRoutes)
  assert.deepEqual(
    Array.from(entries, (item) => [item.routeName, item.kind]),
    [['PdaQuality', 'pda']]
  )
  assert.equal(
    profile.getProfileEntries('quality', permissions('rd:package-unit:query'), allRoutes).length,
    0
  )
})

test('query and sales never expose operational PDA or dispatch entries', () => {
  for (const id of ['query', 'sales']) {
    const entries = profile.getProfileEntries(id, permissions('*:*:*'), allRoutes)
    assert.ok(entries.length > 0)
    assert.ok(entries.every((item) => item.kind === 'query'))
    assert.ok(!entries.some((item) => item.routeName.startsWith('Pda')))
    assert.ok(!entries.some((item) => /Dispatch|Workbench/.test(item.routeName)))
  }
})

test('production task summary and driver feeds are permission and route gated', () => {
  assert.deepEqual(
    Array.from(
      profile.getTaskQueries('production', permissions('rd:production-task:query'), allRoutes)
    ),
    ['production']
  )
  assert.deepEqual(Array.from(profile.getTaskQueries('driver', permissions('*:*:*'), allRoutes)), [
    'putaway',
    'loading'
  ])
  assert.deepEqual(
    Array.from(profile.getTaskQueries('driver', permissions('rd:loading-task:execute'), allRoutes)),
    ['loading']
  )
  assert.equal(profile.getTaskQueries('query', permissions('*:*:*'), allRoutes).length, 0)
  assert.equal(profile.getTaskQueries('production', permissions(), allRoutes).length, 0)
  assert.equal(profile.getTaskQueries('driver', permissions('*:*:*'), () => false).length, 0)
})

test('bag lookup extracts legacy links but always targets the local identity route', () => {
  for (const input of [' BAG-001 ', 'https://id.tjldsic.com/pkg/BAG-001?source=old']) {
    const target = profile.getPackageTraceTarget(input)
    assert.equal(target.name, 'PackageTrace')
    assert.equal(target.params.packageCode, 'BAG-001')
    assert.equal(target.query.workMode, 'pda')
    assert.equal(target.path, undefined)
  }
  assert.equal(
    profile.getPackageTraceTarget('https://untrusted.example/pkg/%E5%8C%85-01').params.packageCode,
    '包-01'
  )
  for (const input of [
    '',
    'https://untrusted.example/redirect',
    'javascript:alert(1)',
    '/pkg/X',
    'X/Y'
  ]) {
    assert.equal(profile.getPackageTraceTarget(input), undefined)
  }
  assert.equal(profile.getPackageTraceTarget('X'.repeat(129)), undefined)
})

test('bag lookup respects the existing identity query capabilities', () => {
  assert.equal(profile.canLookupPackage(permissions(), allRoutes), false)
  assert.equal(profile.canLookupPackage(permissions('rd:loading-task:execute'), allRoutes), true)
  assert.equal(
    profile.canLookupPackage(permissions('rd:package-unit:query'), () => false),
    false
  )
})
