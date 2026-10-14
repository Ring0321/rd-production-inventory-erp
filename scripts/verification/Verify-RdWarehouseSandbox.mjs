import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { root, baseUrl, api, prepareFixture } from './RdQrLocalFixture.mjs'

const { tokens, accounts } = await prepareFixture()
const before = await api('/rd/putaway-task/location-map', tokens.warehouse)
const stockBefore = await api('/rd/stock-lot/page?pageNo=1&pageSize=100', tokens.warehouse)
const units = (await api('/rd/package-unit/page?pageNo=1&pageSize=100', tokens.warehouse)).list
let unit
for (const candidate of units.filter(u => ['IN_STOCK', 'OUTBOUND_PENDING'].includes(u.status) && u.stockLotId && before.some(l => l.id === u.locationId))) {
  const lot = await api(`/rd/stock-lot/get?id=${candidate.stockLotId}`, tokens.warehouse)
  if (lot.locationId === candidate.locationId && lot.warehouseQty > 0) { unit = candidate; break }
}
assert.ok(unit, 'existing local in-stock package fixture with exact inventory location')
const location = before.find(l => l.id === unit.locationId)
const requireFrontend = createRequire(path.join(root, 'frontend/package.json'))
const { chromium } = process.env.RD_BROWSER_MODULES ? requireFrontend(path.join(process.env.RD_BROWSER_MODULES, 'playwright')) : requireFrontend('playwright')
const browser = await chromium.launch({ headless: true, channel: process.env.RD_BROWSER_CHANNEL, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] })
const output = path.join(root, 'temp/warehouse-sandbox-verification')
await fs.mkdir(output, { recursive: true })
const errors = [], external = [], screens = [], pixels = []
let writes = 0
const context = await browser.newContext({ viewport: { width: 1440, height: 1200 } })
const page = await context.newPage()
page.on('pageerror', e => errors.push(e.message))
page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
page.on('request', request => {
  if (new URL(request.url()).origin !== new URL(baseUrl).origin) external.push(request.url())
  if (!['GET', 'HEAD'].includes(request.method()) && !request.url().includes('/system/auth/') && !request.url().includes('/system/tenant/')) writes++
})
await context.route('**/*', route => new URL(route.request().url()).origin === new URL(baseUrl).origin ? route.continue() : route.abort())
const region = page.getByRole('region', { name: '库位空间视图', exact: true })
const shot = async name => {
  await page.mouse.move(0, 0)
  await page.waitForFunction(() => ![...document.querySelectorAll('.el-popper.is-dark')].some(el => getComputedStyle(el).visibility !== 'hidden' && el.getBoundingClientRect().width > 0))
  await region.screenshot({ path: path.join(output, name), animations: 'disabled' })
  screens.push(name)
}
const search = async raw => {
  await region.getByRole('textbox', { name: '包码或二维码内容', exact: true }).fill(raw)
  await region.getByRole('button', { name: '定位货物库位', exact: true }).click()
}
try {
  await page.goto(baseUrl + '/login', { waitUntil: 'networkidle' })
  await page.getByPlaceholder('账号', { exact: true }).fill(accounts.warehouse.username)
  await page.getByPlaceholder('密码', { exact: true }).fill(accounts.warehouse.password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(u => !u.pathname.includes('/login'))
  await page.goto(baseUrl + '/rd/stock/putaway', { waitUntil: 'networkidle' })
  await region.getByText('立体', { exact: true }).click()
  await region.locator('canvas[data-ready="true"]').waitFor()
  await region.locator('.scene-bin-label').first().waitFor()
  const firstLabelId = await region.locator('.scene-bin-label').first().getAttribute('data-location-id')
  const first = before.find(l => l.id === Number(firstLabelId))
  assert.ok(first)
  const scope = before.filter(l => l.warehouseCode === first.warehouseCode && (l.areaCode || '') === (first.areaCode || '') && (l.levelCode || '') === (first.levelCode || ''))
  const totals = region.locator('.map-summary')
  const metric = label => totals.locator('div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd')
  assert.equal((await metric('库位').innerText()).trim(), String(scope.length))
  assert.equal((await metric('在库包数').innerText()).trim(), scope.reduce((n, l) => n + l.occupiedPackages, 0).toLocaleString('zh-CN'))
  assert.equal((await metric('登记吨数').innerText()).trim(), (scope.reduce((n, l) => n + l.occupiedWeight, 0) / 1000).toLocaleString('zh-CN', { maximumFractionDigits: 6 }))
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: width < 500 ? 1000 : 1200 })
    await region.getByRole('button', { name: '适配库区', exact: true }).click()
    const frame = await region.locator('canvas').evaluate(el => {
      const gl = el.getContext('webgl2'), data = new Uint8Array(el.width * el.height * 4)
      gl.readPixels(0, 0, el.width, el.height, gl.RGBA, gl.UNSIGNED_BYTE, data)
      let contrast = 0
      for (let i = 0; i < data.length; i += 4) if (Math.min(data[i], data[i + 1], data[i + 2]) < 190) contrast++
      return { width: el.width, height: el.height, contrast }
    })
    assert.ok(frame.contrast > 1000, 'actual nonblank warehouse shell and bag pixels')
    await page.waitForFunction(() => { const el = document.querySelector('.spatial-location'); return el.scrollWidth <= el.clientWidth + 1 })
    pixels.push({ viewport: width, ...frame })
    await shot(`warehouse-day-${width}.png`)
  }
  await page.setViewportSize({ width: 1440, height: 1200 })
  const filters = region.getByRole('radiogroup', { name: '库位状态筛选' })
  await filters.getByText('待核', { exact: true }).click()
  const unknown = scope.filter(l => !['EMPTY', 'PARTIAL', 'FULL', 'BLOCKED'].includes(l.displayStatus) && l.enabled && !l.frozen)
  assert.equal((await metric('匹配').innerText()).trim(), String(unknown.length))
  if (!unknown.length) {
    assert.equal(await region.locator('.scene-bin-label').count(), 0)
    await region.getByText('当前条件无匹配库位', { exact: true }).waitFor()
  }
  await shot('warehouse-filter.png')
  await filters.getByText('全部', { exact: true }).click()
  // Manual refresh does not restart polling. Use the next natural response as the close-action window.
  const readyMap = await page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/rd/putaway-task/location-map'))
  await readyMap.finished()
  await page.waitForFunction(() => ![...document.querySelectorAll('.el-loading-mask')].some(el => getComputedStyle(el).display !== 'none' && getComputedStyle(el).visibility !== 'hidden' && el.getBoundingClientRect().width > 0))
  // The legacy workbench invalidates inspectors on polling; test closing independently of triple-read lookup.
  const closeBin = region.locator('.scene-bin-label').first()
  const closeId = Number(await closeBin.getAttribute('data-location-id'))
  const closeLocation = before.find(l => l.id === closeId)
  assert.ok(closeLocation, 'fresh rendered bin retains registered identity')
  await closeBin.click()
  await region.getByRole('complementary', { name: `库位资料 ${closeLocation.locationCode}`, exact: true }).waitFor()
  await region.getByRole('button', { name: '关闭库位资料', exact: true }).click({ timeout: 8000 })
  assert.equal(await region.getByRole('complementary').count(), 0, 'reachable close control removes the location inspector')
  await search(unit.packageCode)
  await region.getByText(`已定位登记库位 ${location.locationCode}，以仓管确认记录为准`, { exact: true }).waitFor()
  await region.getByRole('complementary', { name: `库位资料 ${location.locationCode}`, exact: true }).waitFor()
  await search(`https://legacy.example/pkg/${encodeURIComponent(unit.packageCode)}?internal=1`)
  await region.getByText(`已定位登记库位 ${location.locationCode}，以仓管确认记录为准`, { exact: true }).waitFor()
  await region.getByRole('complementary', { name: `库位资料 ${location.locationCode}`, exact: true }).waitFor()
  await shot('warehouse-package-locate.png')
  await filters.getByText('待核', { exact: true }).click()
  await filters.getByText('全部', { exact: true }).click()
  const tracePath = '**/admin-api/rd/package-unit/trace?*'
  await page.route(tracePath, async route => {
    const response = await route.fetch()
    const body = await response.json()
    body.data = body.data.map(u => ({ ...u, status: 'OUTBOUNDED' }))
    await route.fulfill({ response, json: body })
  })
  await search(unit.packageCode)
  await region.getByText('该包不处于已入库或待出库状态，不能定位当前在库货物', { exact: true }).waitFor()
  assert.equal(await region.getByRole('complementary').count(), 0)
  await shot('warehouse-outbound-rejected.png')
  await page.unroute(tracePath)
  let release, observed
  const started = new Promise(resolve => { observed = resolve })
  const paused = new Promise(resolve => { release = resolve })
  await page.route(tracePath, async route => { observed(); await paused; await route.continue() })
  await region.getByRole('textbox', { name: '包码或二维码内容', exact: true }).fill(unit.packageCode)
  await region.getByRole('button', { name: '定位货物库位', exact: true }).click()
  await started
  await filters.getByText('待核', { exact: true }).click()
  const lateResponse = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/rd/package-unit/trace'))
  release()
  await lateResponse
  assert.equal(await region.getByRole('complementary').count(), 0, 'manual filter cancels late package positioning')
  await page.unroute(tracePath)
  await filters.getByText('全部', { exact: true }).click()
  await region.getByText('平面', { exact: true }).click()
  await region.locator('.leaflet-overlay-pane path').first().waitFor()
  await shot('warehouse-plane-regression.png')
  await search(unit.packageCode)
  await region.getByRole('complementary', { name: `库位资料 ${location.locationCode}`, exact: true }).waitFor()
  const refreshed = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/rd/putaway-task/location-map'))
  await page.getByRole('button', { name: '刷新', exact: true }).first().click()
  await refreshed
  await page.waitForFunction(() => !document.querySelector('.spatial-location .map-inspector'))
  assert.equal(await region.getByRole('complementary').count(), 0, 'real map refresh still clears obsolete inspection and lookup')
  assert.deepEqual(await api('/rd/putaway-task/location-map', tokens.warehouse), before)
  assert.deepEqual(await api('/rd/stock-lot/page?pageNo=1&pageSize=100', tokens.warehouse), stockBefore)
  assert.equal(writes, 0)
  assert.deepEqual(external, [])
  assert.deepEqual(errors, [])
  const report = { scope: 'local software WebGL warehouse visuals and exact registered inventory location, not factory geometry or physical PDA acceptance', pixels, screens, locationId: location.id, currentPackageLookup: true, outboundFieldsRejected: true, lateFilterCancelled: true, refreshInvalidation: true, statisticsFromERP: true, stockUnchanged: true, writes, external, errors }
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify(report))
} catch (error) {
  await page.screenshot({ path: path.join(output, 'failure-page.png'), fullPage: true })
  await fs.writeFile(path.join(output, 'failure.json'), JSON.stringify({ error: error.message, url: page.url(), region: await region.innerText().catch(() => 'region unavailable'), inspectorCount: await region.getByRole('complementary').count(), errors, writes, external }, null, 2))
  throw error
} finally { await browser.close() }
