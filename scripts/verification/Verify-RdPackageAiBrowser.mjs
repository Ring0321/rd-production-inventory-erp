import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { root, baseUrl, api, prepareFixture } from './RdQrLocalFixture.mjs'

const requireFrontend = createRequire(path.join(root, 'frontend/package.json'))
const { chromium } = process.env.RD_BROWSER_MODULES
  ? requireFrontend(path.join(process.env.RD_BROWSER_MODULES, 'playwright'))
  : requireFrontend('playwright')
const { fixture, tokens, accounts } = await prepareFixture()
const code = fixture.loadingPackages[0].packageCode
const localFacts = await api(`/rd/ai/package/context?packageCode=${code}`, tokens.warehouse)
assert.equal(localFacts.providerState, 'DISABLED', 'Never run this check against enabled live GLM')
const output = path.join(root, 'temp/ai-package-verification/browser')
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true, channel: process.env.RD_BROWSER_CHANNEL })
const errors = []
const results = []
const signedPage = async (role, width = 1440) => {
  const context = await browser.newContext({ viewport: { width, height: width < 500 ? 844 : 1050 } })
  const page = await context.newPage()
  page.on('pageerror', (error) => errors.push({ role, message: error.message }))
  if (role !== 'public') {
    await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
    await page.getByPlaceholder('账号', { exact: true }).fill(accounts[role].username)
    await page.getByPlaceholder('密码', { exact: true }).fill(accounts[role].password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL((url) => !url.pathname.includes('/login'))
  }
  return { page, context }
}
try {
  for (const role of ['warehouse', 'production', 'quality', 'driver', 'sales', 'public']) {
    const { page, context } = await signedPage(role)
    let aiReads = 0, aiPosts = 0
    page.on('request', (r) => {
      if (r.url().includes('/rd/ai/package/context')) aiReads++
      if (r.url().includes('/rd/ai/package/ask')) aiPosts++
    })
    await page.goto(`${baseUrl}/pkg/${code}`, { waitUntil: 'networkidle' })
    await page.getByRole('heading', { name: code, exact: true }).waitFor()
    const allowed = ['warehouse', 'production', 'quality'].includes(role)
    assert.equal(await page.locator('.package-ai-section').count(), allowed ? 1 : 0)
    if (allowed) {
      await page.getByText('ERP登记事实', { exact: true }).waitFor()
      assert.ok(aiReads >= 1)
      assert.equal(await page.getByRole('button', { name: '请求整理', exact: true }).isDisabled(), true)
      assert.equal(await page.getByText('AI整理要点', { exact: true }).count(), 0)
      for (const width of role === 'warehouse' ? [1440, 390, 320] : [1440]) {
        await page.setViewportSize({ width, height: width < 500 ? 844 : 1050 })
        const panel = page.locator('.package-ai-section')
        await panel.scrollIntoViewIfNeeded()
        const fit = await panel.evaluate((el) => ({ scroll: el.scrollWidth, client: el.clientWidth, left: el.getBoundingClientRect().left, right: el.getBoundingClientRect().right, viewport: window.innerWidth }))
        assert.ok(fit.scroll <= fit.client + 1 && fit.left >= 0 && fit.right <= fit.viewport + 1)
        await page.screenshot({ path: path.join(output, `${role}-${width}-real-disabled.png`), fullPage: true, animations: 'disabled' })
      }
      await page.getByRole('button', { name: '刷新ERP登记事实' }).click()
    } else assert.equal(aiReads, 0)
    assert.equal(aiPosts, 0)
    results.push({ role, actualErp: true, allowed, autoModelRequests: aiPosts })
    await context.close()
  }

  // Browser-only contract fixtures. These are NOT GLM answers or real paid calls.
  const handle = await signedPage('warehouse', 390)
  const context = handle.context
  let page = handle.page
  const mockFacts = { ...localFacts, providerState: 'READY' }
  await context.route('**/admin-api/rd/ai/package/context**', (route) => route.fulfill({ json: { code: 0, data: mockFacts } }))
  let created = 0, reconciled = 0, original
  await context.route('**/admin-api/rd/ai/package/ask', async (route) => {
    created++
    original = route.request().postDataJSON()
    await route.abort('failed')
  })
  await context.route('**/admin-api/rd/ai/package/result**', async (route) => {
    reconciled++
    const url = new URL(route.request().url())
    assert.equal(url.searchParams.get('requestId'), original.requestId)
    assert.equal(url.searchParams.get('packageCode'), code)
    await route.fulfill({ json: { code: 0, data: { requestId: original.requestId, state: 'SUCCESS', message: 'LOCAL_PROTOCOL_MOCK_ONLY', context: { ...localFacts, providerState: 'DISABLED' }, noteKeys: ['READ_ONLY_AI'], totalTokens: 120, answeredAt: new Date().toISOString() } } })
  })
  await page.goto(`${baseUrl}/pkg/${code}`, { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: '请求整理', exact: true }).click()
  await page.getByRole('button', { name: '核对原请求', exact: true }).waitFor()
  assert.equal(await page.getByRole('button', { name: '请求整理', exact: true }).isDisabled(), true)
  await page.close()
  page = await context.newPage()
  page.on('pageerror', (error) => errors.push({ role: 'reopened', message: error.message }))
  await page.goto(`${baseUrl}/pkg/${code}`, { waitUntil: 'networkidle' })
  assert.equal(created, 1)
  await page.getByRole('button', { name: '核对原请求', exact: true }).click()
  await page.getByText('AI整理要点', { exact: true }).waitFor()
  assert.equal(created, 1)
  assert.equal(reconciled, 1)
  assert.equal(await page.getByText('LOCAL_PROTOCOL_MOCK_ONLY', { exact: true }).count(), 0)
  await page.locator('.package-ai-section').scrollIntoViewIfNeeded()
  await page.screenshot({ path: path.join(output, 'warehouse-390-MOCK-receipt-only.png'), fullPage: true, animations: 'disabled' })
  await page.reload({ waitUntil: 'networkidle' })
  assert.equal(await page.getByText('AI整理要点', { exact: true }).count(), 0)
  await page.getByRole('button', { name: '核对原请求', exact: true }).click()
  await page.getByText('AI整理要点', { exact: true }).waitFor()
  assert.equal(created, 1)
  assert.equal(reconciled, 2)
  results.push({ role: 'warehouse', browserProtocolMock: true, persistentAcrossClosedTab: true, successReceiptReplay: true, originalPostCount: created, readOnlyReceiptCount: reconciled, realGlm: false })
  await context.close()

  const race = await signedPage('warehouse', 390)
  let racedPosts = 0
  await race.context.route('**/admin-api/rd/ai/package/context**', (r) => r.fulfill({ json: { code: 0, data: mockFacts } }))
  await race.context.route('**/admin-api/rd/ai/package/ask', async (r) => { racedPosts++; await r.abort('failed') })
  await race.page.goto(`${baseUrl}/pkg/${code}`, { waitUntil: 'networkidle' })
  const other = await race.context.newPage()
  other.on('pageerror', (error) => errors.push({ role: 'second-tab', message: error.message }))
  await other.goto(`${baseUrl}/pkg/${code}`, { waitUntil: 'networkidle' })
  await Promise.all([race.page.getByRole('button', { name: '请求整理', exact: true }).dispatchEvent('click'), other.getByRole('button', { name: '请求整理', exact: true }).dispatchEvent('click')])
  await race.page.getByRole('button', { name: '核对原请求', exact: true }).waitFor()
  await other.getByRole('button', { name: '核对原请求', exact: true }).waitFor()
  assert.equal(racedPosts, 1)
  assert.equal(await other.getByRole('button', { name: '请求整理', exact: true }).isDisabled(), true)
  results.push({ browserProtocolMock: true, nativeWebLocksRace: true, paidRequestPostCount: racedPosts, realGlm: false })
  await race.context.close()
  assert.deepEqual(errors, [])
  await fs.writeFile(path.join(output, 'results.json'), JSON.stringify({ marker: '本地ERP页面和浏览器协议模拟，非GLM/现场验收', code, results, pageErrors: errors }, null, 2))
  console.log(JSON.stringify({ browserCases: results.length, screenshots: 6, liveGlm: false, pageErrors: errors.length }))
} finally { await browser.close() }
