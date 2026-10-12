import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { root, baseUrl, api, request, prepareFixture } from './RdQrLocalFixture.mjs'

const requireFrontend = createRequire(path.join(root, 'frontend/package.json'))
const { chromium } = process.env.RD_BROWSER_MODULES
  ? requireFrontend(path.join(process.env.RD_BROWSER_MODULES, 'playwright')) : requireFrontend('playwright')
const { tokens, accounts } = await prepareFixture()
const marker = 'AI增强本地验证（非现场数据）'
const fixtureFile = path.join(root, 'temp/ai-enhancement-fixture.json')
let fixture
try { fixture = JSON.parse(await fs.readFile(fixtureFile, 'utf8')); assert.equal(fixture.marker, marker) }
catch (error) { if (error.code !== 'ENOENT') throw error; fixture = { marker } }
const save = () => fs.writeFile(fixtureFile, JSON.stringify(fixture, null, 2))
if (!fixture.outputId) {
  fixture.outputId = await api('/rd/production-output/create', tokens.production, 'POST', {
    productionDate: '2026-10-13', customerName: marker, productModel: 'QR-E4',
    furnaceNo: `AI-B-${Date.now()}`, furnaceLayer: '上层', outputQty: 3, packageWeight: 800,
    sourceSheet: marker, remark: marker
  })
  await save()
}
const output = await api(`/rd/production-output/get?id=${fixture.outputId}`, tokens.production)
assert.equal(output.sourceSheet, marker)
if (output.status === 'DRAFT') await api('/rd/production-output/confirm', tokens.production, 'POST', { id: output.id, version: output.version })
const membersUrl = `/rd/package-unit/trace?productionOutputId=${fixture.outputId}`
let members = await api(membersUrl, tokens.production)
if (!members.length) members = await api('/rd/package-unit/generate', tokens.production, 'POST', { productionOutputId: fixture.outputId, packageCount: 3 })
const unprinted = members.filter((p) => p.status === 'CREATED').map((p) => p.id)
if (unprinted.length) await api('/rd/package-unit/print-batch', tokens.production, 'POST', { packageIds: unprinted })
if (!fixture.inspectionId) {
  fixture.inspectionId = await api('/rd/quality-inspection/create', tokens.quality, 'POST', {
    packageId: members[0].id, remark: marker,
    items: [{ itemCode: 'LOCAL_TEST', itemName: '接口验证示例（非检测值）', standardMin: 0, standardMax: 10, measuredValue: 5, unit: '示例' }]
  })
  await save()
}
const inspection = await api(`/rd/quality-inspection/get?id=${fixture.inspectionId}`, tokens.quality)
assert.equal(inspection.packageId, members[0].id)
if (inspection.status === 'DRAFT') await api(`/rd/quality-inspection/submit?id=${inspection.id}`, tokens.quality, 'POST')
assert.equal((await api(`/rd/quality-inspection/get?id=${inspection.id}`, tokens.quality)).status, 'SUBMITTED')

const before = await api(membersUrl, tokens.admin)
const code = before[0].packageCode
const contextUrl = `/rd/ai/package/context?packageCode=${code}`
const facts = await api(contextUrl, tokens.warehouse)
assert.equal(facts.providerState, 'DISABLED', 'Live GLM must remain disabled')
assert.equal(facts.diagnosis.scope, 'PRODUCTION_OUTPUT')
assert.equal(facts.diagnosis.state, 'KNOWN')
assert.equal(facts.diagnosis.inspectedPackages, 3)
const pending = facts.diagnosis.findings.find((f) => f.key === 'DIAG_QUALITY_PENDING')
assert.ok(pending)
assert.equal(pending.affectedCount, 1)
assert.equal(pending.owner, 'QUALITY')
assert.equal(pending.severity, 'REVIEW')
assert.deepEqual(pending.packageCodes, [code])
assert.ok(facts.notices.some((n) => n.key === pending.key))
for (const question of ['RISK_REVIEW', 'HANDOVER']) {
  const answer = await api('/rd/ai/package/ask', tokens.warehouse, 'POST', {
    packageCode: code, question, requestId: '00000000-0000-4000-8000-000000000001'
  })
  assert.equal(answer.state, 'DISABLED')
}
for (const role of ['driver', 'sales']) assert.notEqual((await request(contextUrl, tokens[role])).code, 0)
assert.equal((await request(contextUrl)).code, 401)
assert.deepEqual(await api(membersUrl, tokens.admin), before)

const directory = path.join(root, 'temp/ai-enhancement-verification')
await fs.mkdir(directory, { recursive: true })
const browser = await chromium.launch({ headless: true, channel: process.env.RD_BROWSER_CHANNEL })
const errors = [], screenshots = []
const signedPage = async (width) => {
  const context = await browser.newContext({ viewport: { width, height: width < 500 ? 844 : 1050 } })
  const page = await context.newPage()
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
  await page.getByPlaceholder('账号', { exact: true }).fill(accounts.warehouse.username)
  await page.getByPlaceholder('密码', { exact: true }).fill(accounts.warehouse.password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL((url) => !url.pathname.includes('/login'))
  return { page, context }
}
try {
  const real = await signedPage(1440)
  let realPosts = 0
  real.page.on('request', (r) => { if (r.url().includes('/rd/ai/package/ask')) realPosts++ })
  await real.page.goto(`${baseUrl}/pkg/${code}`, { waitUntil: 'networkidle' })
  await real.page.getByRole('heading', { name: '核对清单', exact: true }).waitFor()
  const panel = real.page.locator('.package-ai-section')
  assert.ok((await panel.innerText()).includes(pending.title))
  await real.page.getByRole('textbox', { name: '现场问句', exact: true }).fill('这批能发吗')
  await real.page.getByRole('button', { name: '识别问题', exact: true }).click()
  await real.page.getByText('已选择：风险核对', { exact: true }).waitFor()
  assert.equal(realPosts, 0)
  assert.equal(await real.page.getByRole('button', { name: '请求整理', exact: true }).isDisabled(), true)
  for (const width of [1440, 390, 320]) {
    await real.page.setViewportSize({ width, height: width < 500 ? 844 : 1050 })
    const fit = await panel.evaluate((el) => ({ scroll: el.scrollWidth, client: el.clientWidth, left: el.getBoundingClientRect().left, right: el.getBoundingClientRect().right, viewport: window.innerWidth }))
    assert.ok(fit.scroll <= fit.client + 1 && fit.left >= 0 && fit.right <= fit.viewport + 1)
    const name = `diagnosis-${width}-real-erp.png`
    await real.page.getByRole('heading', { name: '包码问答', exact: true }).scrollIntoViewIfNeeded()
    await real.page.screenshot({ path: path.join(directory, name), animations: 'disabled' })
    screenshots.push(name)
    if (width < 500) {
      await real.page.getByRole('textbox', { name: '现场问句', exact: true }).scrollIntoViewIfNeeded()
      const controls = `controls-${width}-real-erp.png`
      await real.page.screenshot({ path: path.join(directory, controls), animations: 'disabled' })
      screenshots.push(controls)
    }
  }
  await real.page.getByRole('textbox', { name: '现场问句', exact: true }).fill('帮我删除这些数据')
  await real.page.getByRole('button', { name: '识别问题', exact: true }).click()
  await real.page.getByText('该问句未识别。请选受控问题；配吨和业务办理请使用原任务。', { exact: true }).waitFor()
  assert.equal(realPosts, 0)
  await real.context.close()

  // Browser-only GLM contract response. The server stays disabled; no live provider request.
  for (const [fieldText, expected] of [['这批能发吗', 'RISK_REVIEW'], ['生成交接摘要', 'HANDOVER']]) {
    const mock = await signedPage(390)
    let posts = 0
    await mock.context.route('**/admin-api/rd/ai/package/context**', (r) => r.fulfill({ json: { code: 0, data: { ...facts, providerState: 'READY' } } }))
    await mock.context.route('**/admin-api/rd/ai/package/ask', async (r) => {
      posts++
      const body = r.request().postDataJSON()
      assert.deepEqual(Object.keys(body).sort(), ['packageCode', 'question', 'requestId'])
      assert.equal(body.question, expected)
      assert.ok(!JSON.stringify(body).includes(fieldText))
      await r.fulfill({ json: { code: 0, data: {
        requestId: body.requestId, state: 'SUCCESS', message: 'LOCAL_PROTOCOL_MOCK_ONLY', context: { ...facts, providerState: 'READY' },
        noteKeys: ['DIAG_QUALITY_PENDING', 'READ_ONLY_AI'], totalTokens: 111, answeredAt: new Date().toISOString()
      } } })
    })
    await mock.page.goto(`${baseUrl}/pkg/${code}`, { waitUntil: 'networkidle' })
    await mock.page.getByRole('textbox', { name: '现场问句', exact: true }).fill(fieldText)
    await mock.page.getByRole('button', { name: '识别问题', exact: true }).click()
    assert.equal(posts, 0)
    await mock.page.getByRole('button', { name: '请求整理', exact: true }).click()
    await mock.page.getByRole('heading', { name: 'AI整理要点', exact: true }).waitFor()
    assert.equal(posts, 1)
    assert.equal(await mock.page.getByText('LOCAL_PROTOCOL_MOCK_ONLY', { exact: true }).count(), 0)
    assert.equal(await mock.page.locator('.package-ai-answer li').count(), 2)
    const pendingKey = `rd-package-ai:v1:1:${accounts.warehouse.userId}:${encodeURIComponent(code)}`
    const metadata = await mock.page.evaluate((key) => localStorage.getItem(key), pendingKey)
    assert.ok(metadata && !metadata.includes(fieldText) && !metadata.includes('diagnosis') && !metadata.includes('packageCodes'))
    const name = `${expected}-390-MOCK-protocol-only.png`
    await mock.page.locator('.package-ai-section').screenshot({ path: path.join(directory, name), animations: 'disabled' })
    screenshots.push(name)
    await mock.context.close()
  }
  assert.deepEqual(errors, [])
  assert.deepEqual(await api(membersUrl, tokens.admin), before)
  await fs.writeFile(path.join(directory, 'result.json'), JSON.stringify({ marker, outputId: fixture.outputId, code, actualDiagnostics: true, realPosts, newIntentsProtocolMocked: 2, pageErrors: errors, screenshots, unchangedPackageRecords: true, liveGlm: false }, null, 2))
  console.log(JSON.stringify({ outputId: fixture.outputId, actualDiagnostics: true, screenshots: screenshots.length, pageErrors: errors.length, unchangedPackageRecords: true, liveGlm: false }))
} finally { await browser.close() }
