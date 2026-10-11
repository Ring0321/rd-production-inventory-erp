import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluate, plain } from '../../pda/warehouse/testHelpers.mjs'

const helpers = evaluate(
  '../../stock/loading/replacements.ts',
  {
    '@/utils/rdDigitalLink': {
      extractPackageCode: (value) => {
        const text = value.trim()
        if (!/^https?:\/\//i.test(text)) return text
        const url = new URL(text)
        return decodeURIComponent(url.pathname.split('/pkg/').at(-1)).trim()
      }
    }
  },
  { Error }
)
const lines = [21, 22].map((id) => ({
  id,
  packageCode: `OLD-${id}`,
  lineStatus: 'PLANNED',
  customerName: '验证客户',
  productModel: 'A',
  furnaceNo: '1-01',
  furnaceLayer: '上层',
  packageWeight: 1000
}))
const rows = () => [
  { key: 1, taskLineId: 21, newPackageCode: 'https://id.tjldsic.com/pkg/NEW-1' },
  { key: 2, taskLineId: 22, newPackageCode: 'NEW-2' }
]
const candidate = (code, changes = {}) => ({
  id: 101,
  packageCode: code,
  status: 'IN_STOCK',
  customerName: '验证客户',
  productModel: 'A',
  furnaceNo: '1-01',
  furnaceLayer: '上层',
  packageWeight: 1000,
  stockLotId: 1,
  locationId: 1,
  locationCode: 'A-01',
  ...changes
})

test('two rows remain one request array with canonical package identities', () => {
  assert.deepEqual(plain(helpers.buildLoadingReplacements(rows(), lines)), [
    { taskLineId: 21, newPackageCode: 'NEW-1' },
    { taskLineId: 22, newPackageCode: 'NEW-2' }
  ])
})

test('empty, oversized, repeated, current-task and inactive members are rejected', () => {
  for (const input of [
    [],
    Array.from({ length: 101 }, (_, key) => ({ ...rows()[0], key })),
    [{ ...rows()[0] }, { ...rows()[1], taskLineId: 21 }],
    [{ ...rows()[0] }, { ...rows()[1], newPackageCode: 'new-1' }],
    [{ ...rows()[0], newPackageCode: 'OLD-22' }],
    [{ ...rows()[0], newPackageCode: 'https://id.tjldsic.com/pkg/' }],
    [{ ...rows()[0], newPackageCode: 'NEW/1' }]
  ])
    assert.throws(() => helpers.buildLoadingReplacements(input, lines))
  for (const lineStatus of ['SCANNED', 'OUTBOUNDED', 'CANCELLED'])
    assert.throws(() =>
      helpers.buildLoadingReplacements(rows(), [{ ...lines[0], lineStatus }, lines[1]])
    )
})

test('real candidate metadata must match every original bag; partial preview is never returned', async () => {
  for (const changes of [
    { customerName: '其他客户' },
    { furnaceLayer: '下层' },
    { status: 'OUTBOUNDED' },
    { packageWeight: 0 },
    { stockLotId: null },
    { locationCode: '' }
  ]) {
    const read = async ({ keyword }) => ({
      list: [candidate(keyword, keyword === 'NEW-2' ? changes : {})]
    })
    await assert.rejects(() =>
      helpers.inspectLoadingReplacements(
        helpers.buildLoadingReplacements(rows(), lines),
        lines,
        read,
        () => {}
      )
    )
  }
})

test('substring or ambiguous package queries do not authorize a replacement', async () => {
  for (const result of [[candidate('NEW-11')], [candidate('NEW-1'), candidate('new-1')], []])
    await assert.rejects(() =>
      helpers.inspectLoadingReplacements(
        helpers.buildLoadingReplacements([rows()[0]], lines),
        lines,
        async () => ({ list: result }),
        () => {}
      )
    )
})

test('one bad acknowledgement cannot verify a multi-package command', () => {
  const entries = helpers.buildLoadingReplacements(rows(), lines)
  const actual = lines.map((line, index) => ({ ...line, packageCode: `NEW-${index + 1}` }))
  assert.equal(helpers.verifyLoadingReplacements(actual, entries), true)
  assert.equal(
    helpers.verifyLoadingReplacements([actual[0], { ...actual[1], packageCode: 'WRONG' }], entries),
    false
  )
  assert.equal(
    helpers.verifyLoadingReplacements(
      [actual[0], { ...actual[1], lineStatus: 'CANCELLED' }],
      entries
    ),
    false
  )
})

test('late candidate reads cannot attach to an edited draft or another task', async () => {
  let release
  const result = new Promise((resolve) => {
    release = resolve
  })
  const detail = vue.ref({ task: { id: 4, version: 3 }, lines })
  const session = {
    current: () => true,
    generation: () => 0,
    allowed: () => true,
    assertCurrent: () => {}
  }
  const module = evaluate(
    '../../stock/loading/useLoadingReplacements.ts',
    {
      vue,
      './replacements': helpers,
      '@/api/rd/loading': { RdLoadingApi: { getAvailablePackagePage: () => result } }
    },
    { Error }
  )
  for (const mode of ['draft', 'task']) {
    const draft = module.useLoadingReplacements(session, detail)
    draft.rows.value = [rows()[0]]
    const reading = draft.check()
    if (mode === 'draft') draft.rows.value[0].newPackageCode = 'LATER'
    else detail.value = { ...detail.value, task: { id: 9, version: 3 } }
    release({ list: [candidate('NEW-1')] })
    await reading
    assert.equal(draft.preview.value.length, 0)
  }
})

test('exact-package inspection cannot be truncated by one hundred substring matches', async () => {
  const entries = helpers.buildLoadingReplacements([rows()[0]], lines)
  const read = async (query) =>
    query.packageCode === 'NEW-1'
      ? { list: [candidate('NEW-1')] }
      : { list: Array.from({ length: 100 }, (_, index) => candidate(`NEW-1-${index}`)) }
  const result = await helpers.inspectLoadingReplacements(entries, lines, read, () => {})
  assert.equal(result[0].replacement.packageCode, 'NEW-1')
})

test('candidate previews cannot survive a deactivate-reactivate lifecycle', async () => {
  let release,
    lifetime = 0
  const result = new Promise((resolve) => {
    release = resolve
  })
  const detail = vue.ref({ task: { id: 4, version: 3 }, lines })
  const session = {
    current: () => true,
    generation: () => lifetime,
    allowed: () => true,
    assertCurrent: () => {}
  }
  const module = evaluate(
    '../../stock/loading/useLoadingReplacements.ts',
    {
      vue,
      './replacements': helpers,
      '@/api/rd/loading': { RdLoadingApi: { getAvailablePackagePage: () => result } }
    },
    { Error }
  )
  const draft = module.useLoadingReplacements(session, detail)
  draft.rows.value = [rows()[0]]
  const reading = draft.check()
  lifetime++
  release({ list: [candidate('NEW-1')] })
  await reading
  assert.equal(draft.preview.value.length, 0)
})
