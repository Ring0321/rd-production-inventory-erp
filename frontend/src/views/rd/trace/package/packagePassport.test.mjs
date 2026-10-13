import { test } from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { webcrypto, createHash } from 'node:crypto'
import { evaluateSource } from './testHelpers.mjs'
const source = readFileSync(
  new URL('../../../../api/rd/passport/index.ts', import.meta.url),
  'utf8'
).replaceAll('import.meta.env', 'environment')
const calls = []
const module = evaluateSource(
  source,
  {
    '@/utils/auth': { getTenantId: () => 1, getVisitTenantId: () => undefined }
  },
  {
    crypto: webcrypto,
    TextEncoder,
    URL,
    environment: {},
    fetch: async (url, options) => {
      calls.push({ url, options })
      return {
        ok: true,
        headers: { get: () => 'application/json' },
        text: async () => JSON.stringify(make())
      }
    }
  }
)
const document = () => ({
  '@context': { '@vocab': 'urn:duoxu:public-package:v1:' },
  '@type': 'PackagePassport',
  '@id': 'https://old.example/pkg/P1',
  profile: 'DUOXU_PUBLIC_PACKAGE_V1',
  packageCode: 'P1',
  product: {
    name: null,
    model: 'MODEL',
    furnaceNo: 'F1',
    furnaceLayer: '上层',
    weightKg: '697.000000',
    productionDate: '2026-10-13',
    furnaceOutDate: null,
    shift: null
  },
  workflow: { status: 'QC_PENDING', qualityState: 'SUBMITTED', confirmedQuality: null },
  events: [{ stage: 'QUALITY', eventTime: '2026-10-13T09:00', timeBasis: 'ERP_LOCAL_DATETIME' }]
})
const make = (d = document()) => {
  const canonicalJson = JSON.stringify(d)
  return {
    schema: 'DUOXU_PUBLIC_PACKAGE_V1',
    algorithm: 'SHA-256',
    generatedAt: '2026-10-13T03:00:00Z',
    canonicalJson,
    digest: createHash('sha256').update(canonicalJson, 'utf8').digest('hex')
  }
}
test('actual UTF8 bytes verify, original identity stays unchanged, nulls remain unknown', async () => {
  const result = await module.verifyPassport(make(), 'P1')
  assert.equal(result.document['@id'], 'https://old.example/pkg/P1')
  assert.equal(result.document.product.weightKg, '697.000000')
  assert.equal(result.document.workflow.confirmedQuality, null)
})
test('modified bytes, wrong identity, private fields, external contexts and wrong schema are rejected', async () => {
  const altered = make()
  altered.canonicalJson = altered.canonicalJson.replace('697.000000', '1000')
  await assert.rejects(module.verifyPassport(altered, 'P1'), /摘要/)
  assert.throws(() => module.validatePassport(make(), 'P2'), /身份/)
  const d = document()
  d.customerName = 'PRIVATE'
  assert.throws(() => module.validatePassport(make(d)), /范围/)
  const context = document()
  context['@context'] = 'https://attacker.example/context'
  assert.throws(() => module.validatePassport(make(context)), /范围/)
  assert.throws(() => module.validatePassport({ ...make(), schema: 'OTHER' }), /版本/)
})
test('local digest can be recomputed by anyone and therefore must not be treated as a trusted source or signature', async () => {
  const original = make()
  const d = document()
  d.product.weightKg = '1000'
  const changed = make(d)
  await module.verifyPassport(changed, 'P1')
  assert.notEqual(original.digest, changed.digest)
})
test('pending inspection cannot become a confirmed pass, and oversized content is rejected', () => {
  const d = document()
  d.workflow.confirmedQuality = 'PASSED'
  assert.throws(() => module.validatePassport(make(d)), /护照内容未通过公开字段校验/)
  const oversized = { ...make(), canonicalJson: ' '.repeat(module.PASSPORT_MAX_BYTES + 1) }
  assert.throws(() => module.validatePassport(oversized), /护照结构或版本无效/)
})
test('unavailable WebCrypto fails explicitly instead of claiming integrity verification', async () => {
  const unavailable = evaluateSource(
    source,
    {
      '@/utils/auth': { getTenantId: () => 1, getVisitTenantId: () => undefined }
    },
    { crypto: {}, TextEncoder, URL, environment: {} }
  )
  await assert.rejects(unavailable.verifyPassport(make(), 'P1'), /无法|不支持|安全/)
})
test('public API is fixed to own endpoint, carries no token/cookie or local file body, and bypasses caches', async () => {
  await module.getPublicPassport('P1')
  assert.equal(calls[0].url, '/admin-api/rd/package-link/P1/passport')
  assert.equal(calls[0].options.credentials, 'omit')
  assert.equal(calls[0].options.cache, 'no-store')
  assert.deepEqual(Object.keys(calls[0].options.headers).sort(), ['Accept', 'tenant-id'])
  assert.equal(calls[0].options.body, undefined)
})
