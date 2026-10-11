import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { createRequire } from 'node:module'
import { test } from 'node:test'
const require = createRequire(new URL('../../../../../package.json', import.meta.url))
const ts = require('typescript')
const source = await readFile(new URL('./draft.ts', import.meta.url), 'utf8')
const js = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.ESNext }
}).outputText
const { validateQualityRequest, makePending, parsePending, pendingKey, confirmedCreateRejection } =
  await import(`data:text/javascript;base64,${Buffer.from(js).toString('base64')}`)
const valid = () => ({
  packageId: 2,
  packageIds: [2, 3],
  samplePackageIds: [2],
  clientRequestId: 'original-request',
  items: [
    { samplePackageId: 2, itemCode: 'SIC', itemName: '碳化硅', measuredValue: 98, standardMin: 97 }
  ],
  remark: '原请求'
})
test('explicit coverage and actual sample are validated without extending to a whole task', () => {
  const value = valid()
  assert.equal(validateQualityRequest(value), undefined)
  assert.deepEqual(value.packageIds, [2, 3])
})
for (const [name, change] of [
  [
    'duplicate members',
    (r) => {
      r.packageIds.push(2)
    }
  ],
  [
    'representative outside coverage',
    (r) => {
      r.packageIds = [3]
    }
  ],
  [
    'sample outside coverage',
    (r) => {
      r.samplePackageIds = [4]
    }
  ],
  [
    'empty sampling',
    (r) => {
      r.samplePackageIds = []
    }
  ],
  [
    'sample without results',
    (r) => {
      r.samplePackageIds.push(3)
    }
  ],
  [
    'result of non-sampled bag',
    (r) => {
      r.items[0].samplePackageId = 3
    }
  ],
  [
    'empty result',
    (r) => {
      r.items[0].measuredValue = undefined
    }
  ],
  [
    'nonfinite result',
    (r) => {
      r.items[0].measuredValue = Infinity
    }
  ],
  [
    'inverted standards',
    (r) => {
      r.items[0].standardMax = 96
    }
  ],
  [
    'blank item name',
    (r) => {
      r.items[0].itemName = ' '
    }
  ],
  [
    'more than 200 bags',
    (r) => {
      r.packageIds = Array.from({ length: 201 }, (_, i) => i + 1)
    }
  ]
]) {
  test(`rejects ${name}`, () => {
    const value = valid()
    change(value)
    assert.ok(validateQualityRequest(value))
  })
}
test('pending request is a detached snapshot and restores only for its owner and tenant', () => {
  const input = valid()
  const saved = makePending(input, 5, '1')
  input.items[0].measuredValue = 1
  input.packageIds.push(8)
  assert.equal(saved.request.items[0].measuredValue, 98)
  assert.deepEqual(saved.request.packageIds, [2, 3])
  assert.equal(
    parsePending(JSON.stringify(saved), 5, '1').request.clientRequestId,
    'original-request'
  )
  assert.throws(() => parsePending(JSON.stringify(saved), 6, '1'))
  assert.throws(() => parsePending(JSON.stringify(saved), 5, '2'))
  assert.notEqual(pendingKey(5, '1'), pendingKey(5, '2'))
})
test('corrupt pending snapshots fail closed and cannot silently release a request', () => {
  assert.throws(() => parsePending('bad-json', 5, '1'))
  const saved = makePending(valid(), 5, '1')
  saved.request.samplePackageIds = []
  assert.throws(() => parsePending(JSON.stringify(saved), 5, '1'))
})
test('only a confirmed business rejection before draft creation allows editing', () => {
  assert.equal(confirmedCreateRejection({ businessCode: 10090001 }), true)
  assert.equal(confirmedCreateRejection({ businessCode: 403 }), true)
  for (const error of [
    new Error('Network Error'),
    'error',
    { businessCode: 500 },
    { businessCode: 401 },
    { businessCode: 901 }
  ])
    assert.equal(confirmedCreateRejection(error), false)
  assert.equal(confirmedCreateRejection({ businessCode: 10090001 }, 7), false)
})
