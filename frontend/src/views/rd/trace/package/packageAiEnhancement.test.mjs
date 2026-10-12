import assert from 'node:assert/strict'
import { test } from 'node:test'
import { setup, flush, plain, answer } from './testHelpers.mjs'

test('local field questions select risk and handover intents without making any model request', async () => {
  const h = setup()
  await flush()
  for (const [text, question] of [
    ['这批能发吗', 'RISK_REVIEW'],
    ['有没有异常', 'RISK_REVIEW'],
    ['生成交接摘要', 'HANDOVER'],
    ['下一步谁处理', 'NEXT_STEP'],
    ['现在到哪一步了', 'STATUS'],
    ['登记了多少包', 'SOURCE_PROGRESS']
  ])
    assert.deepEqual(plain(h.module.interpretPackageAiQuestion(text)), {
      state: 'MATCHED',
      question
    })
  assert.equal(h.asks.length, 0)
  assert.equal(h.reconciles.length, 0)
  h.dispose()
})

test('ambiguous or unsupported questions cannot silently become a model call or an inventory command', async () => {
  const h = setup()
  await flush()
  const mixed = h.module.interpretPackageAiQuestion('查看当前状态和下一步谁处理')
  assert.equal(mixed.state, 'AMBIGUOUS')
  assert.deepEqual(plain(mixed.candidates), ['STATUS', 'NEXT_STEP'])
  for (const value of [
    '',
    '帮我删除这批数据',
    '直接自动放行',
    '能给客户配10吨吗',
    '能发十吨吗',
    '给我查任意SQL',
    'a'.repeat(121)
  ]) {
    assert.equal(h.module.interpretPackageAiQuestion(value).state, 'UNSUPPORTED', value)
  }
  assert.equal(h.asks.length, 0)
  h.dispose()
})

test('new guided questions keep the existing idempotency request shape and omit original text', async () => {
  for (const question of ['RISK_REVIEW', 'HANDOVER']) {
    const h = setup()
    await flush()
    h.work.question.value = question
    const asking = h.work.ask()
    await flush()
    assert.equal(h.asks.length, 1)
    assert.deepEqual(Object.keys(h.asks[0].value).sort(), ['packageCode', 'question', 'requestId'])
    assert.equal(h.asks[0].value.question, question)
    h.asks[0].resolve(answer(h.asks[0].value))
    await asking
    assert.equal(h.work.pending.value.request.question, question)
    h.dispose()
  }
})

test('malformed diagnosis is rejected and cannot contaminate the trusted ERP context', async () => {
  const diagnosis = {
    scope: 'PRODUCTION_OUTPUT',
    state: 'KNOWN',
    inspectedPackages: 3,
    findings: []
  }
  for (const broken of [
    { ...diagnosis, inspectedPackages: -1 },
    { ...diagnosis, scope: 'ALL_WAREHOUSES' },
    {
      ...diagnosis,
      findings: [
        {
          key: 'SHIP_NOW',
          severity: 'HIGH',
          owner: 'WAREHOUSE',
          title: 'x',
          detail: 'x',
          affectedCount: 1,
          packageCodes: ['P1'],
          evidence: 'x'
        }
      ]
    }
  ]) {
    const h = setup({ contextExtra: { diagnosis: broken } })
    await flush()
    assert.equal(h.work.context.value, undefined)
    assert.ok(h.work.contextError.value)
    assert.equal(h.asks.length, 0)
    h.dispose()
  }
})

test('review regressions: quantities and mutation clauses never pass local intent matching', async () => {
  const h = setup()
  await flush()
  for (const value of [
    '查当前状态并更新库位',
    '这批能发两吨吗',
    '这批能发２吨吗',
    '查看状态并设为合格'
  ])
    assert.equal(h.module.interpretPackageAiQuestion(value).state, 'UNSUPPORTED', value)
  assert.equal(h.asks.length, 0)
  h.dispose()
})

test('review regression: an unknown source cannot return an empty apparently clean diagnosis', async () => {
  const h = setup({
    contextExtra: {
      source: { state: 'UNKNOWN', packageCount: null, recordedWeight: null, counts: {} },
      diagnosis: {
        scope: 'PRODUCTION_OUTPUT',
        state: 'UNKNOWN',
        inspectedPackages: null,
        findings: []
      }
    }
  })
  await flush()
  assert.equal(h.work.context.value, undefined)
  assert.ok(h.work.contextError.value)
  h.dispose()
})

test('confirmed unknown diagnosis remains usable and null known counts cannot look clean', async () => {
  const unknown = {
    scope: 'PRODUCTION_OUTPUT',
    state: 'UNKNOWN',
    inspectedPackages: null,
    findings: [
      {
        key: 'DIAG_SOURCE_UNKNOWN',
        severity: 'REVIEW',
        owner: 'PRODUCTION',
        title: '来源待核',
        detail: '不按零处理',
        affectedCount: null,
        packageCodes: [],
        evidence: '来源关联状态'
      }
    ]
  }
  const h = setup({
    contextExtra: {
      source: { state: 'UNKNOWN', packageCount: null, recordedWeight: null, counts: {} },
      diagnosis: unknown
    }
  })
  await flush()
  assert.ok(h.work.context.value)
  assert.equal(h.work.sourceFacts.value.packageCount, '未知')
  h.dispose()
  const broken = setup({
    contextExtra: {
      source: { state: 'KNOWN', packageCount: null, recordedWeight: null, counts: {} },
      diagnosis: {
        scope: 'PRODUCTION_OUTPUT',
        state: 'KNOWN',
        inspectedPackages: null,
        findings: []
      }
    }
  })
  await flush()
  assert.equal(broken.work.context.value, undefined)
  broken.dispose()
})
