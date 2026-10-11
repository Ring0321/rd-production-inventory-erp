import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { randomUUID } from 'node:crypto'
import { root, baseUrl, api, request, prepareFixture } from './RdQrLocalFixture.mjs'

// Genuine local ERP calls only. No enabled provider, API key or paid model test.
const { fixture, tokens } = await prepareFixture()
const code = fixture.loadingPackages[0].packageCode
const memberUrl = `/rd/package-unit/trace?productionOutputId=${fixture.loadingOutputId}`
const before = await api(memberUrl, tokens.admin)
const contextUrl = `/rd/ai/package/context?packageCode=${encodeURIComponent(code)}`
const checks = []
for (const role of ['production', 'quality', 'warehouse', 'admin']) {
  const c = await api(contextUrl, tokens[role])
  assert.equal(c.providerState, 'DISABLED', 'This verification must not run with live AI enabled')
  assert.equal(c.packageCode, code)
  assert.equal(c.source.state, 'KNOWN')
  assert.equal(c.source.packageCount, before.length)
  assert.equal(c.source.recordedWeight, before.reduce((sum, p) => sum + Number(p.packageWeight), 0))
  const counts = before.reduce((sum, p) => ({ ...sum, [p.status]: (sum[p.status] || 0) + 1 }), {})
  assert.deepEqual(c.source.counts, counts)
  assert.ok(!('apiKey' in c) && !('model' in c))
  assert.ok(c.notices.some((n) => n.key === 'READ_ONLY_AI'))
  const answer = await api('/rd/ai/package/ask', tokens[role], 'POST', { packageCode: code, question: 'STATUS', requestId: randomUUID() })
  assert.equal(answer.state, 'DISABLED')
  assert.deepEqual(answer.noteKeys, [])
  assert.equal(answer.totalTokens, null)
  checks.push({ role, permitted: true, sourcePackages: c.source.packageCount, providerState: c.providerState })
}
for (const role of ['driver', 'sales', 'otherDriver']) {
  assert.notEqual((await request(contextUrl, tokens[role])).code, 0)
  assert.notEqual((await request('/rd/ai/package/ask', tokens[role], 'POST', { packageCode: code, question: 'STATUS', requestId: randomUUID() })).code, 0)
  checks.push({ role, permitted: false })
}
assert.equal((await request(contextUrl)).code, 401)
assert.notEqual((await request('/rd/ai/package/ask', tokens.admin, 'POST', { packageCode: code, question: 'SHIP_NOW', requestId: randomUUID() })).code, 0)
assert.notEqual((await request('/rd/ai/package/ask', tokens.admin, 'POST', { packageCode: code, question: 'STATUS', requestId: 'invalid' })).code, 0)
assert.notEqual((await request('/rd/ai/package/context?packageCode=https://external.example/any', tokens.admin)).code, 0)
const receipt = await api(`/rd/ai/package/result?packageCode=${code}&question=STATUS&requestId=${randomUUID()}`, tokens.admin)
assert.equal(receipt.state, 'RECEIPT_MISSING')
assert.deepEqual(receipt.noteKeys, [])
const crossTenant = await fetch(`${baseUrl}/admin-api${contextUrl}`, { headers: { 'tenant-id': '2', Authorization: `Bearer ${tokens.admin}` }, signal: AbortSignal.timeout(20000) }).then((r) => r.json())
assert.notEqual(crossTenant.code, 0)
assert.deepEqual(await api(memberUrl, tokens.admin), before, 'AI preparation must not update package business records')
const out = path.join(root, 'temp/ai-package-verification')
await fs.mkdir(out, { recursive: true })
await fs.writeFile(path.join(out, 'api-results.json'), JSON.stringify({ marker: 'AI本地真实ERP验证，GLM真实调用关闭，非现场数据', code, checks, anonymousDenied: true, invalidInputsDenied: true, crossTenantDenied: true, unchangedPackageRecords: true }, null, 2))
console.log(JSON.stringify({ localApiChecks: checks.length + 6, unchangedPackageRecords: true, liveGlm: false }))
