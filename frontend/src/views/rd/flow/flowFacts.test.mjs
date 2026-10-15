import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { evaluateSource, plain } from '../trace/package/testHelpers.mjs'

const source = readFileSync(new URL('./flowFacts.ts', import.meta.url), 'utf8')
const facts = evaluateSource(source)
const bag = (id, status = 'IN_STOCK', weight = 1000) => ({
  id,
  packageCode: `P${id}`,
  productionTaskId: 7,
  productionOutputId: 10,
  status,
  packageWeight: weight
})

test('flow requires both original permissions, excludes drivers and grants no new rights', () => {
  const permissions = new Set(['rd:production-task:query', 'rd:package-unit:query'])
  assert.equal(facts.canViewBusinessFlow([], permissions), true)
  assert.equal(facts.canViewBusinessFlow([], new Set(['rd:package-unit:query'])), false)
  assert.equal(facts.canViewBusinessFlow(['super_admin'], new Set(['*:*:*'])), true)
  assert.equal(facts.canViewBusinessFlow(['rd_forklift_driver'], permissions), false)
})

test('demo identity needs both reserved remark and visibly synthetic customer', () => {
  assert.equal(facts.isDemoTask({ customerName: '真实客户', remark: facts.DEMO_MARKER }), false)
  assert.equal(facts.isDemoTask({ customerName: facts.DEMO_CUSTOMER, remark: '' }), false)
  assert.equal(
    facts.isDemoTask({ customerName: facts.DEMO_CUSTOMER, remark: facts.DEMO_MARKER }),
    true
  )
})

test('exact task membership rejects duplicates, invalid identity and foreign members rather than partial totals', () => {
  assert.equal(facts.validateTaskPackages(7, [bag(1)]).length, 1)
  for (const data of [
    [bag(1), bag(1)],
    [{ ...bag(1), productionTaskId: 8 }],
    [{ ...bag(1), id: 0 }],
    null
  ])
    assert.throws(() => facts.validateTaskPackages(7, data))
  assert.throws(() => facts.validateTaskPackages(7, [bag(1), { ...bag(2), packageCode: 'P1' }]))
  assert.throws(() => facts.validateTaskPackages(7, [{ ...bag(1), productionOutputId: undefined }]))
})

test('current states are disjoint, unknown is not shipped and rejected is not treated as released', () => {
  const rows = [
    bag(1, 'QC_REJECTED'),
    bag(2, 'INBOUND_PENDING'),
    bag(3, 'OUTBOUNDED'),
    bag(4, 'NEW')
  ]
  const summary = facts.summarizeFlow(rows)
  assert.equal(
    summary.states.reduce((n, state) => n + state.count, 0),
    4
  )
  assert.equal(summary.states.find((s) => s.key === 'OUTBOUND').count, 1)
  assert.equal(summary.states.find((s) => s.key === 'UNKNOWN').count, 1)
  assert.equal(summary.states.find((s) => s.key === 'RELEASED').count, 0)
})

test('tail weight and six decimal quantities sum without cumulative floating error; unknown is explicit', () => {
  assert.deepEqual(plain(facts.sumPackages([bag(1), bag(2, 'OUTBOUNDED', 800)])), {
    count: 2,
    weightKg: 1800,
    unknownWeight: 0
  })
  assert.equal(facts.sumPackages([bag(1, 'IN_STOCK', 0.1), bag(2, 'IN_STOCK', 0.2)]).weightKg, 0.3)
  assert.equal(facts.sumPackages([bag(1, 'IN_STOCK', 0.000001)]).weightKg, 0.000001)
  for (const weight of [null, undefined, NaN, Infinity, -1, 0, 0.0000001, 'unknown']) {
    const summary = facts.sumPackages([{ ...bag(1), packageWeight: weight }])
    assert.equal(summary.unknownWeight, 1)
  }
})

test('distinct source and location counts come only from persisted identifiers', () => {
  const result = facts.summarizeFlow([
    bag(1),
    { ...bag(2), productionOutputId: 11, locationId: 25 },
    { ...bag(3), locationId: 25 }
  ])
  assert.equal(result.outputCount, 2)
  assert.equal(result.locationCount, 1)
})

test('missing or invalid net weights remain unknown in both row and total presentation', () => {
  for (const value of [null, undefined, NaN, Infinity, -1, 0])
    assert.equal(facts.packageWeightText(value), '待核')
  assert.equal(
    facts.summaryWeightText(facts.sumPackages([{ ...bag(1), packageWeight: null }])),
    '净重待核'
  )
  assert.match(
    facts.summaryWeightText(facts.sumPackages([bag(1), { ...bag(2), packageWeight: null }])),
    /已知.*1.*包待核/
  )
})

test('all eight stages maintain responsibility and no autonomous driver posting', () => {
  assert.equal(facts.flowSteps.length, 8)
  assert.match(facts.flowSteps.find((step) => step.key === 'PUTAWAY').work, /仓管确认/)
  assert.match(facts.flowSteps.find((step) => step.key === 'LOADING').work, /整车任务/)
})

test('task deep link accepts only exact safe positive identifier', () => {
  assert.equal(facts.validTaskId('7'), 7)
  for (const id of ['07', '-7', '7x', ['7'], '', '9007199254740992', undefined])
    assert.equal(facts.validTaskId(id), undefined)
})

test('shipped and voided package positions are historical, not current inventory', () => {
  const outbound = { ...bag(1, 'OUTBOUNDED'), locationId: 3, locationCode: 'A-01' }
  assert.equal(facts.packageLocation(outbound).title, '出库前库位')
  assert.match(facts.packageLocation(outbound).caption, /出库前/)
  assert.equal(facts.packageLocation({ ...outbound, status: 'IN_STOCK' }).title, '登记库位')
})

test('stage navigation uses the persisted task and read-only view, never a scan deep link', () => {
  const target = facts.businessFlowTarget('LOADING', 7, bag(1), undefined, {
    tasks: [{ type: 'LOADING', taskId: 26 }]
  })
  assert.deepEqual(plain(target), {
    name: 'RdLoadingDispatch',
    query: { taskId: '26', mode: 'view' }
  })
  assert.deepEqual(plain(facts.businessFlowTarget('LOADING', 7, bag(1))).query, {})
  assert.equal(facts.businessFlowTarget('TRACE', 7), undefined)
  assert.equal(facts.businessFlowTarget('LOADING', 0), undefined)
  assert.deepEqual(plain(facts.businessFlowTarget('PRODUCTION', 7)), {
    name: 'RdProductionTask',
    query: { productionTaskId: '7' }
  })
})

test('known QC status is localized without turning absent data into a pass', () => {
  assert.equal(facts.qualityConclusionText(), '未取得结论')
  assert.equal(facts.qualityConclusionText({ qualityStatus: 'SUBMITTED' }), '已提交，待复核')
  assert.equal(facts.qualityConclusionText({ qualityStatus: 'PASSED' }), '质检通过')
})

test('actual active tasks distinguish loading and exceptions without rewriting the IN_STOCK register', () => {
  const bags = [bag(1), bag(2), bag(3)]
  const contexts = {
    P1: { packageCode: 'P1', tasks: [] },
    P2: { packageCode: 'P2', tasks: [{ type: 'LOADING', taskId: 40, status: 'LOADING' }] },
    P3: { packageCode: 'P3', tasks: [{ type: 'LOADING', taskId: 41, status: 'EXCEPTION_PENDING' }] }
  }
  const result = facts.summarizeFlow(bags, contexts)
  for (const key of ['STOCK', 'LOADING', 'LOADING_EXCEPTION'])
    assert.equal(result.states.find((state) => state.key === key).count, 1)
  assert.equal(result.workKnown, true)
  assert.equal(
    bags.every((row) => row.status === 'IN_STOCK'),
    true
  )
  assert.equal(facts.summarizeFlow(bags).workKnown, false)
  assert.equal(facts.packageState('OUTBOUNDED', contexts.P3).key, 'OUTBOUND')
  assert.equal(
    facts.packageState('IN_STOCK', { tasks: [{ type: 'LOADING', status: 'UNKNOWN_FUTURE' }] }).key,
    'UNKNOWN'
  )
})
