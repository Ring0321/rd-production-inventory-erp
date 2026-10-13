import { test } from 'node:test'
import assert from 'node:assert/strict'
import { resolvePackageRequest as resolve, isPublicPackageQuery } from './RdPackageLinkNegotiation.mjs'
const u = (path) => new URL(path, 'http://localhost:9000')
test('stable URL has human default and four typed representations', () => {
  assert.deepEqual(resolve(u('/pkg/P1')), { code: 'P1', resource: 'web' })
  for (const [accept, resource] of [['application/json', 'passport'], ['application/ld+json', 'document'], ['application/linkset+json', 'links'], ['text/html', 'web']])
    assert.deepEqual(resolve(u('/pkg/P1'), accept), { code: 'P1', resource })
  assert.deepEqual(resolve(u('/pkg/P1?resource=passport'), 'text/html'), { code: 'P1', resource: 'passport' })
})
test('public transport accepts only canonical workflow selectors, never credentials or encoded parameter names', () => {
  assert.equal(isPublicPackageQuery(u('/pkg/P1?resource=web&workMode=pda&internal=1')), true)
  for (const query of ['token=fixture', '%74oken=fixture', 'resource=web&tok%65n=fixture', 'next=https://attacker.test']) {
    assert.equal(isPublicPackageQuery(u('/pkg/P1?' + query)), false)
    assert.deepEqual(resolve(u('/pkg/P1?' + query)), { error: 400 })
  }
})
test('weights and explicit exclusions beat wildcards; unknown MIME and unsafe selectors do not reflect', () => {
  assert.deepEqual(resolve(u('/pkg/P1'), 'text/html;q=0.5,application/json;q=0.9'), { code: 'P1', resource: 'passport' })
  assert.deepEqual(resolve(u('/pkg/P1'), 'application/json;q=0,*/*;q=0.8'), { code: 'P1', resource: 'web' })
  assert.deepEqual(resolve(u('/pkg/P1'), 'image/png'), { error: 406 })
  assert.deepEqual(resolve(u('/pkg/P1?resource=https://attacker.test')), { error: 400 })
  assert.deepEqual(resolve(u('/pkg/P1?resource=links&resource=passport')), { error: 400 })
  assert.equal(resolve(u('/pda/warehouse')), undefined)
  assert.deepEqual(resolve(u('/pkg/%')), { error: 400 })
})
