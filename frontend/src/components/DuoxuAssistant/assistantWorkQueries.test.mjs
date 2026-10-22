import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource } from '../../views/rd/trace/package/testHelpers.mjs'

const source = (name) => readFileSync(new URL(name, import.meta.url), 'utf8')
const model = evaluateSource(source('./assistantModel.ts'))
const subject = evaluateSource(source('./assistantWorkQueries.ts'), { './assistantModel': model })
const pages = model.collectAssistantPages([
  { path: '/rd/quality/inspection', meta: { title: '质量检验' } },
  { path: '/rd/stock/package', meta: { title: '包码库存' } }
])
const metric = (key, value, unit, quality = 'KNOWN') => ({
  key,
  value,
  unit,
  quality,
  reason: quality === 'KNOWN' ? null : '待核'
})
const fixture = () => ({
  request: { queryType: 'Q03', warehouseCode: null, alertId: null, pageNo: 1, pageSize: 20 },
  scope: 'TENANT_AUTHORIZED',
  quality: 'KNOWN',
  asOf: '2026-10-22T01:00:00Z',
  businessDate: null,
  metrics: {
    DISTINCT_PACKAGE_COUNT: metric('DISTINCT_PACKAGE_COUNT', '2', 'PACKAGE'),
    REGISTERED_WEIGHT_KG: metric('REGISTERED_WEIGHT_KG', '1800.123456', 'KG')
  },
  items: [1, 2].map((id) => ({
    sourceType: 'PACKAGE_UNIT',
    sourceId: String(id),
    packageCode: `LD20261022A000${id}`,
    status: 'QC_PENDING',
    registeredWeightKg: '900',
    sourceVersion: 4
  })),
  total: 2,
  sources: [{ type: 'PACKAGE_UNIT', revision: null }],
  notices: []
})

test('controlled intents distinguish package quality from inspection review and inbound states', () => {
  for (const [question, queryType] of [
    ['待检包件有多少', 'Q03'],
    ['哪些包还没质检', 'Q03'],
    ['质检待复核', 'Q04'],
    ['哪些质检已经提交但还没复核', 'Q04'],
    ['合格待入库的货物', 'Q05']
  ]) {
    assert.deepEqual(JSON.parse(JSON.stringify(model.interpretAssistantQuestion(question))), {
      kind: 'WORK_QUERY',
      queryType
    })
  }
  assert.equal(model.interpretAssistantQuestion('帮我把待检货全部放行').kind, 'READ_ONLY')
  assert.equal(model.interpretAssistantQuestion('上周待检包件').kind, 'CLARIFY')
  assert.equal(model.interpretAssistantQuestion('A仓库的待检包件有多少').kind, 'CLARIFY')
  assert.equal(model.interpretAssistantQuestion('质检页面在哪里').kind, 'NAVIGATE')
})

test('real authorized counts and exact decimal strings are rendered without floating point loss', () => {
  const result = subject.answerWorkQuery('Q03', fixture(), pages, true)
  assert.equal(result.kind, 'ERP')
  assert.equal(result.facts[0].value, '2 包')
  assert.equal(result.facts[1].value, '1800.123456 kg')
  assert.match(result.paragraphs.join(''), /全部仓库/)
  assert.equal(result.checkedAt, '2026-10-22T01:00:00Z')
  assert.ok(result.links.some((link) => link.packageCode === 'LD20261022A0001'))
})

test('known zero is distinct from missing source and invalid numbers', () => {
  const data = fixture()
  data.items = []
  data.total = 0
  data.metrics.DISTINCT_PACKAGE_COUNT.value = '0'
  data.metrics.REGISTERED_WEIGHT_KG.value = '0'
  assert.equal(subject.answerWorkQuery('Q03', data, pages, true).facts[0].value, '0 包')
  data.quality = 'UNAVAILABLE'
  data.asOf = null
  data.total = null
  data.sources = []
  for (const metric of Object.values(data.metrics))
    Object.assign(metric, { quality: 'UNAVAILABLE', value: null, reason: '源不可用' })
  data.notices = ['源不可用']
  const result = subject.answerWorkQuery('Q03', data, pages, true)
  assert.equal(result.kind, 'NOTICE')
  assert.equal(result.facts, undefined)
})

test('partial weight does not erase known package count or masquerade as complete', () => {
  const data = fixture()
  data.quality = 'PARTIAL'
  data.total = null
  data.metrics.REGISTERED_WEIGHT_KG = metric('REGISTERED_WEIGHT_KG', null, 'KG', 'UNAVAILABLE')
  data.notices = ['重量来源待核']
  const result = subject.answerWorkQuery('Q03', data, pages, true)
  assert.match(result.title, /部分/)
  assert.equal(result.facts[0].value, '2 包')
  assert.equal(result.facts[1].value, '待核')
})

for (const [name, mutate] of [
  [
    'foreign query',
    (data) => {
      data.request.queryType = 'Q04'
    }
  ],
  [
    'foreign warehouse',
    (data) => {
      data.request.warehouseCode = 'other'
    }
  ],
  [
    'foreign scope',
    (data) => {
      data.scope = 'OWN_TASKS'
    }
  ],
  [
    'foreign source',
    (data) => {
      data.sources = [{ type: 'LOADING_TASK' }]
    }
  ],
  [
    'missing snapshot',
    (data) => {
      data.asOf = null
    }
  ],
  [
    'page length as total',
    (data) => {
      data.total = 1
    }
  ],
  [
    'duplicate identity',
    (data) => {
      data.items[1].sourceId = '1'
    }
  ],
  [
    'wrong status',
    (data) => {
      data.items[0].status = 'IN_STOCK'
    }
  ],
  [
    'wrong unit',
    (data) => {
      data.metrics.REGISTERED_WEIGHT_KG.unit = 'TON'
    }
  ],
  [
    'missing metric',
    (data) => {
      delete data.metrics.REGISTERED_WEIGHT_KG
    }
  ],
  [
    'unsafe numeric count',
    (data) => {
      data.metrics.DISTINCT_PACKAGE_COUNT.value = 9007199254740992
    }
  ],
  [
    'negative weight',
    (data) => {
      data.metrics.REGISTERED_WEIGHT_KG.value = '-1'
    }
  ]
])
  test(`rejects ${name} before presenting facts`, () => {
    const data = fixture()
    mutate(data)
    assert.throws(() => subject.answerWorkQuery('Q03', data, pages, true))
  })

test('inspection result links only the exact visible original inspection page', () => {
  const data = fixture()
  data.request.queryType = 'Q04'
  data.total = 1
  data.metrics = {
    BUSINESS_OBJECT_COUNT: metric('BUSINESS_OBJECT_COUNT', '1', 'BUSINESS_OBJECT'),
    DISTINCT_PACKAGE_COUNT: metric('DISTINCT_PACKAGE_COUNT', '2', 'PACKAGE')
  }
  data.sources = [{ type: 'QUALITY_INSPECTION' }, { type: 'QUALITY_INSPECTION_MEMBER' }]
  data.items = [
    {
      sourceType: 'QUALITY_INSPECTION',
      sourceId: '123',
      inspectionNo: 'QI-123',
      status: 'SUBMITTED',
      submittedAt: data.asOf,
      coverageQuality: 'KNOWN',
      coveredPackageCount: '2',
      sampledPackageCount: '1',
      members: [
        { packageId: '1', packageCode: 'LD20261022A0001', sampled: true },
        { packageId: '2', packageCode: 'LD20261022A0002', sampled: false }
      ],
      membersTruncated: false
    }
  ]
  const reply = subject.answerWorkQuery('Q04', data, pages, false)
  assert.ok(
    reply.links.some(
      (link) => link.path === '/rd/quality/inspection' && link.inspectionId === '123'
    )
  )
  assert.ok(reply.links.every((link) => !link.packageCode))
  assert.equal(subject.answerWorkQuery('Q04', data, [], false).links.length, 0)
  assert.ok(model.canOpenAssistantLink(reply.links[0], pages, false))
  assert.equal(
    model.canOpenAssistantLink(
      { ...reply.links[0], inspectionId: '1&url=https://example.com' },
      pages,
      false
    ),
    false
  )
})
