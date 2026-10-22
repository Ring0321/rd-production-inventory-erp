import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource } from '../../views/rd/trace/package/testHelpers.mjs'

const source = (file) => readFileSync(new URL(file, import.meta.url), 'utf8')
const model = evaluateSource(source('./assistantModel.ts'))
const knowledge = evaluateSource(source('./assistantKnowledge.ts'), { './assistantModel': model })
const workQueries = evaluateSource(source('./assistantWorkQueries.ts'), {
  './assistantModel': model
})
const subject = evaluateSource(source('./assistantQueries.ts'), {
  './assistantModel': model,
  './assistantKnowledge': knowledge,
  './assistantWorkQueries': workQueries
})
const pages = model.collectAssistantPages([
  { path: '/warehouse-space', meta: { title: '仓储空间总览' } },
  { path: '/rd/dashboard', meta: { title: '库存看板' } },
  { path: '/rd/stock/putaway', meta: { title: '入库上架' } },
  { path: '/rd/stock/loading', meta: { title: '出库装车' } }
])
const context = (extra = {}) => ({
  pages,
  currentPath: '/warehouse-space',
  warehouseCode: '',
  allowed: () => true,
  valid: () => true,
  ...extra
})
const readers = (extra = {}) => ({
  inventory: () => assert.fail('unexpected inventory read'),
  locations: () => assert.fail('unexpected locations read'),
  trace: () => assert.fail('unexpected trace read'),
  packageContext: () => assert.fail('unexpected AI context read'),
  workQuery: () => assert.fail('unexpected work query'),
  ...extra
})

test('local navigation and workflows never issue data or model requests', async () => {
  const response = await subject.answerAssistantQuestion('入库怎么操作', context(), readers())
  assert.equal(response.kind, 'LOCAL')
  assert.match(response.paragraphs.join(''), /仓管/)
  assert.match(response.paragraphs.join(''), /确认/)
  assert.ok(response.links.some((link) => link.path === '/rd/stock/putaway'))
  const driver = await subject.answerAssistantQuestion('叉车司机需要扫码吗', context(), readers())
  assert.match(driver.paragraphs.join(''), /不.*扫码/)
})

test('denied data queries perform zero reads and disclose no values', async () => {
  for (const question of ['查库存', '有多少个库位', '查LD20261015A0014']) {
    const reply = await subject.answerAssistantQuestion(
      question,
      context({ allowed: () => false }),
      readers()
    )
    assert.equal(reply.kind, 'NOTICE')
    assert.match(reply.paragraphs.join(''), /权限/)
    assert.equal(reply.facts, undefined)
  }
})

test('inventory answer uses server summary and its scope, not synthetic constants or active warehouse assumptions', async () => {
  let calls = 0
  const reply = await subject.answerAssistantQuestion(
    '查库存',
    context({ warehouseCode: 'W1' }),
    readers({
      inventory: async () => {
        calls++
        return {
          asOfDate: '2026-10-20',
          stock: { warehouseQty: 17, lockedQty: 3, availableQty: 14, stockWeight: 16320 }
        }
      }
    })
  )
  assert.equal(calls, 1)
  assert.equal(reply.kind, 'ERP')
  assert.equal(reply.facts.find((item) => item.label === '在库包数').value, '17 包')
  assert.match(reply.paragraphs.join(''), /不是当前仓库/)
  assert.match(reply.source, /2026-10-20/)
})

test('missing or invalid summary fields remain unknown and are never zero-filled', async () => {
  const reply = await subject.answerAssistantQuestion(
    '查库存',
    context(),
    readers({
      inventory: async () => ({
        stock: { warehouseQty: 0, lockedQty: null, availableQty: -3, stockWeight: '100' }
      })
    })
  )
  assert.equal(reply.facts[0].value, '0 包')
  assert.ok(reply.facts.slice(1).every((field) => field.value === '待核'))
})

test('warehouse counts only the selected registered warehouse and keeps levels separate from furnace layers', async () => {
  const reply = await subject.answerAssistantQuestion(
    '仓库有多少库位',
    context({ warehouseCode: 'W1' }),
    readers({
      locations: async () => [
        { id: 1, warehouseCode: 'W1', levelCode: '01', occupiedPackages: 8, occupiedWeight: 8000 },
        { id: 2, warehouseCode: 'W1', levelCode: '02', occupiedPackages: 4, occupiedWeight: 4000 },
        {
          id: 3,
          warehouseCode: 'W2',
          levelCode: '02',
          occupiedPackages: 100,
          occupiedWeight: 100000
        }
      ]
    })
  )
  assert.equal(reply.facts.find((item) => item.label === '登记库位').value, '2 个')
  assert.equal(reply.facts.find((item) => item.label === '在库包数').value, '12 包')
  assert.match(reply.paragraphs.join(''), /01 层：1/)
  assert.match(reply.paragraphs.join(''), /02 层：1/)
})

test('exact package identity and internal projection must agree before any facts are rendered', async () => {
  for (const trace of [
    null,
    { packageCode: 'LD20261015A9999', internalView: true },
    { packageCode: 'LD20261015A0014', internalView: false }
  ]) {
    const reply = await subject.answerAssistantQuestion(
      '查LD20261015A0014',
      context(),
      readers({ trace: async () => trace })
    )
    assert.equal(reply.kind, 'NOTICE')
    assert.equal(reply.facts, undefined)
  }
})

test('package status and local diagnosis remain read-only and link to the same exact bag', async () => {
  const code = 'LD20261015A0014'
  const reply = await subject.answerAssistantQuestion(
    '查' + code,
    context(),
    readers({
      trace: async () => ({
        packageCode: code,
        internalView: true,
        status: 'IN_STOCK',
        statusName: '在库',
        packageWeight: 1000,
        locationCode: 'A-01',
        qualityStatus: 'PASSED'
      }),
      packageContext: async () => ({
        packageCode: code,
        source: { state: 'UNKNOWN' },
        diagnosis: { state: 'UNKNOWN', findings: [] },
        providerState: 'DISABLED'
      })
    })
  )
  assert.equal(reply.kind, 'ERP')
  assert.ok(reply.facts.some((field) => field.value === 'A-01'))
  assert.match(reply.paragraphs.join(''), /待核/)
  assert.ok(!reply.paragraphs.join('').includes('可直接出库'))
  assert.equal(reply.links[0].path, '/pkg/' + code)
})

test('session invalidation during a read rejects the late answer', async () => {
  let valid = true
  await assert.rejects(
    subject.answerAssistantQuestion(
      '查库存',
      context({ valid: () => valid }),
      readers({
        inventory: async () => {
          valid = false
          return { stock: { warehouseQty: 55 } }
        }
      })
    ),
    /ASSISTANT_CONTEXT_CHANGED/
  )
})

test('destructive or out-of-range requests return a clear boundary without making calls', async () => {
  assert.equal(
    (await subject.answerAssistantQuestion('把库存改成20包', context(), readers())).kind,
    'NOTICE'
  )
  assert.equal(
    (await subject.answerAssistantQuestion('上个月出库多少', context(), readers())).kind,
    'NOTICE'
  )
})
