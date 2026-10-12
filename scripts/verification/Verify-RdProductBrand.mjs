import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { root, baseUrl, api, prepareFixture } from './RdQrLocalFixture.mjs'

const requireFrontend = createRequire(path.join(root, 'frontend/package.json'))
const { chromium } = process.env.RD_BROWSER_MODULES
  ? requireFrontend(path.join(process.env.RD_BROWSER_MODULES, 'playwright')) : requireFrontend('playwright')
const { fixture, tokens, accounts } = await prepareFixture()
const code = fixture.loadingPackages[0].packageCode
const sourceUrl = `/rd/package-unit/trace?productionOutputId=${fixture.loadingOutputId}`
const before = await api(sourceUrl, tokens.admin)
const bag = before.find((p) => p.packageCode === code)
assert.ok(bag?.qrCode)
for (const internal of [false, true]) {
  const detail = await api(`/rd/package-unit/${internal ? 'trace-detail' : 'public-trace'}/${code}`, internal ? tokens.warehouse : undefined)
  assert.equal(detail.digitalLink, bag.qrCode)
}
const html = await fetch(`${baseUrl}/login`).then((r) => r.text())
assert.ok(html.includes('<title>垛序 ERP</title>'))
assert.ok(html.includes('/duoxu-mark.png'))
assert.ok(!/天净|隆鼎|__PRODUCT_/.test(html))
assert.equal((await fetch(`${baseUrl}/duoxu-mark.png`)).status, 200)
const output = path.join(root, 'temp/product-brand-verification')
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({ headless: true, channel: process.env.RD_BROWSER_CHANNEL })
const errors = [], screens = [], cases = []
try {
  const publicContext = await browser.newContext({ viewport: { width: 1440, height: 960 } })
  const publicPage = await publicContext.newPage()
  publicPage.on('pageerror', (e) => errors.push(e.message))
  for (const width of [1440, 390, 320]) {
    await publicPage.setViewportSize({ width, height: width < 500 ? 844 : 960 })
    await publicPage.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
    await publicPage.getByRole('heading', { name: '垛序 ERP', exact: true }).waitFor()
    assert.equal(await publicPage.getByRole('button', { name: '登录', exact: true }).isVisible(), true)
    assert.ok(await publicPage.locator('img[src*="duoxu-mark.png"]').evaluate((img) => img.complete && img.naturalWidth > 0))
    assert.ok(await publicPage.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1))
    const file = `login-${width}.png`
    await publicPage.screenshot({ path: path.join(output, file), fullPage: true, animations: 'disabled' })
    screens.push(file)
  }
  await publicPage.goto(`${baseUrl}/pkg/${code}`, { waitUntil: 'networkidle' })
  assert.ok(await publicPage.locator('.brand').innerText().then((s) => s.includes('垛序 ERP')))
  assert.equal(await publicPage.locator('.package-ai-section').count(), 0)
  await publicPage.screenshot({ path: path.join(output, 'public-trace-320.png'), fullPage: true, animations: 'disabled' })
  screens.push('public-trace-320.png')
  await publicContext.close()

  for (const role of ['warehouse', 'production', 'driver']) {
    const context = await browser.newContext({ viewport: { width: 1440, height: 1050 } })
    const page = await context.newPage()
    page.on('pageerror', (e) => errors.push(e.message))
    await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
    await page.getByPlaceholder('账号', { exact: true }).fill(accounts[role].username)
    await page.getByPlaceholder('密码', { exact: true }).fill(accounts[role].password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL((u) => !u.pathname.includes('/login'))
    const title = await page.title()
    assert.ok(title.includes('垛序 ERP'))
    if (role === 'warehouse') {
      await page.goto(`${baseUrl}/rd/dashboard`, { waitUntil: 'networkidle' })
      await page.getByRole('heading', { name: '垛序 · 生产仓储看板', exact: true }).waitFor()
      await page.screenshot({ path: path.join(output, 'warehouse-web-1440.png'), fullPage: true, animations: 'disabled' })
      screens.push('warehouse-web-1440.png')
    }
    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 844 })
      await page.goto(`${baseUrl}/pda`, { waitUntil: 'networkidle' })
      await page.locator('.pda-header .company-name strong').waitFor()
      assert.equal(await page.locator('.pda-header .company-name strong').innerText(), '垛序')
      assert.ok(await page.locator('.pda-header img').evaluate((img) => img.complete && img.naturalWidth > 0))
      assert.ok(await page.locator('.pda-header').evaluate((el) => el.scrollWidth <= el.clientWidth + 1))
      const file = `${role}-pda-${width}.png`
      await page.screenshot({ path: path.join(output, file), fullPage: true, animations: 'disabled' })
      screens.push(file)
    }
    await page.goto(`${baseUrl}/pkg/${code}`, { waitUntil: 'networkidle' })
    assert.ok(await page.locator('.brand').innerText().then((s) => s.includes('垛序 ERP')))
    const aiEntryCount = await page.locator('.package-ai-section').count()
    assert.equal(aiEntryCount, role === 'driver' ? 0 : 1)
    cases.push({ role, login: true, newWebBrand: true, newPdaBrand: true, aiEntryCount })
    await context.close()
  }
  const legacyContext = await browser.newContext({ viewport: { width: 390, height: 844 } })
  const legacyPage = await legacyContext.newPage()
  legacyPage.on('pageerror', (e) => errors.push(e.message))
  await legacyPage.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
  await legacyPage.getByPlaceholder('账号', { exact: true }).fill(accounts.warehouse.username)
  await legacyPage.getByPlaceholder('密码', { exact: true }).fill(accounts.warehouse.password)
  await legacyPage.getByRole('button', { name: '登录', exact: true }).click()
  await legacyPage.waitForURL((u) => !u.pathname.includes('/login'))
  let authoritativeReads = 0
  legacyPage.on('request', (r) => { if (r.url().includes(`/rd/package-unit/public-trace/${code}`)) authoritativeReads++ })
  await legacyPage.route('**/admin-api/rd/package-unit/trace?**', async (r) => {
    const response = await r.fetch()
    const body = await response.json()
    assert.equal(body.code, 0)
    body.data = body.data.map((p) => p.packageCode === code ? { ...p, qrCode: '' } : p)
    await r.fulfill({ response, json: body })
  })
  // Only the browser DTO simulates a legacy blank QR; the real backend record is not changed.
  await legacyPage.goto(`${baseUrl}/rd/stock/package?packageCode=${code}&labelAction=preview`, { waitUntil: 'networkidle' })
  const preview = legacyPage.getByRole('dialog', { name: '货品标识卡预览' })
  await preview.waitFor()
  const image = preview.frameLocator('iframe').locator('.qr-column img')
  await image.waitFor()
  const QRCode = requireFrontend('qrcode')
  const expectedQr = QRCode.create(bag.qrCode, { errorCorrectionLevel: 'M' })
  const actualModules = await image.evaluate(async (img, size) => {
    await img.decode()
    const canvas = document.createElement('canvas')
    canvas.width = img.naturalWidth; canvas.height = img.naturalHeight
    const context = canvas.getContext('2d')
    context.drawImage(img, 0, 0)
    const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data
    const values = []
    for (let y = 0; y < size; y++) for (let x = 0; x < size; x++) {
      const px = Math.floor((x + 4.5) * canvas.width / (size + 8))
      const py = Math.floor((y + 4.5) * canvas.height / (size + 8))
      values.push(pixels[(py * canvas.width + px) * 4] < 128 ? 1 : 0)
    }
    return values
  }, expectedQr.modules.size)
  assert.deepEqual(actualModules, Array.from(expectedQr.modules.data))
  assert.equal(authoritativeReads, 1)
  assert.equal(await preview.frameLocator('iframe').locator('h1').innerText(), '垛序 · 货品标识卡')
  await legacyPage.screenshot({ path: path.join(output, 'legacy-blank-qr-390-DTO-simulation.png'), fullPage: true, animations: 'disabled' })
  screens.push('legacy-blank-qr-390-DTO-simulation.png')
  cases.push({ case: 'legacyBlankQrDtoSimulation', realBackendTrace: true, authoritativeReads, oldQrPixelsMatch: true })
  await legacyContext.close()
  assert.deepEqual(await api(sourceUrl, tokens.admin), before)
  assert.deepEqual(errors, [])
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify({ marker: '垛序品牌本地验证，非现场/商标/公网验收', oldPackageCode: code, oldStoredLinkPreserved: true, unchangedPackageRecords: true, cases, screens, pageErrors: errors }, null, 2))
  console.log(JSON.stringify({ result: 'PASS', screens: screens.length, roles: cases.length, oldStoredLinkPreserved: true, unchangedPackageRecords: true, pageErrors: errors.length }))
} finally { await browser.close() }
