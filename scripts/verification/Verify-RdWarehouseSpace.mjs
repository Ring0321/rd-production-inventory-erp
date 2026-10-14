import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { root, baseUrl, api, prepareFixture } from './RdQrLocalFixture.mjs'

const { tokens, accounts } = await prepareFixture()
const before = await api('/rd/putaway-task/location-map', tokens.warehouse)
const stockBefore = await api('/rd/stock-lot/page?pageNo=1&pageSize=100', tokens.warehouse)
const valid = before.filter(l => Number.isSafeInteger(l.id) && l.id > 0 && typeof l.warehouseCode === 'string' && l.warehouseCode.trim() && typeof l.locationCode === 'string' && l.locationCode.trim() && before.filter(other => other.id === l.id).length === 1)
const units = (await api('/rd/package-unit/page?pageNo=1&pageSize=100', tokens.warehouse)).list
let unit
for (const candidate of units.filter(u => ['IN_STOCK', 'OUTBOUND_PENDING'].includes(u.status) && u.stockLotId && valid.some(l => l.id === u.locationId))) {
  const lot = await api(`/rd/stock-lot/get?id=${candidate.stockLotId}`, tokens.warehouse)
  if (lot.locationId === candidate.locationId && lot.warehouseQty > 0) { unit = candidate; break }
}
assert.ok(unit, 'existing in-stock fixture, no new inventory for showcase')
const location = valid.find(l => l.id === unit.locationId)
const requireFrontend = createRequire(path.join(root, 'frontend/package.json'))
const { chromium } = process.env.RD_BROWSER_MODULES ? requireFrontend(path.join(process.env.RD_BROWSER_MODULES, 'playwright')) : requireFrontend('playwright')
const browser = await chromium.launch({ headless: true, channel: process.env.RD_BROWSER_CHANNEL, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] })
const output = path.join(root, 'temp/warehouse-space-verification')
await fs.mkdir(output, { recursive: true })
const errors = [], external = [], screens = [], pixels = [], denied = []
let writes = 0, stockReads = 0
const observe = page => {
  page.on('pageerror', e => errors.push(e.message))
  page.on('console', message => { if (message.type() === 'error') errors.push(message.text()) })
  page.on('request', request => {
    if (new URL(request.url()).origin !== new URL(baseUrl).origin) external.push(request.url())
    if (!['GET', 'HEAD'].includes(request.method()) && !request.url().includes('/system/auth/') && !request.url().includes('/system/tenant/')) writes++
    if (new URL(request.url()).pathname.endsWith('/rd/stock-lot/page')) stockReads++
  })
}
const isolated = async (width = 1440) => {
  const context = await browser.newContext({ viewport: { width, height: width < 500 ? 1000 : 1100 } })
  await context.route('**/*', route => new URL(route.request().url()).origin === new URL(baseUrl).origin ? route.continue() : route.abort())
  const page = await context.newPage()
  observe(page)
  return { context, page }
}
const login = async (page, account) => {
  await page.goto(baseUrl + '/login', { waitUntil: 'networkidle' })
  await page.getByPlaceholder('账号', { exact: true }).fill(account.username)
  await page.getByPlaceholder('密码', { exact: true }).fill(account.password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(u => !u.pathname.includes('/login'))
}
const { context, page } = await isolated()
const space = page.getByRole('region', { name: '仓储空间总览', exact: true })
const map = space.getByRole('region', { name: '库位空间视图', exact: true })
const metric = label => map.locator('.map-summary div').filter({ has: page.getByText(label, { exact: true }) }).locator('dd')
const shot = async name => {
  await page.mouse.move(0, 0)
  await page.waitForFunction(() => ![...document.querySelectorAll('.el-popper.is-dark')].some(el => getComputedStyle(el).visibility !== 'hidden' && el.getBoundingClientRect().width > 0))
  await space.screenshot({ path: path.join(output, name), animations: 'disabled' })
  screens.push(name)
}
const frame = async () => map.locator('canvas[data-ready="true"]').evaluate(el => {
  const gl = el.getContext('webgl2'), data = new Uint8Array(el.width * el.height * 4)
  gl.readPixels(0, 0, el.width, el.height, gl.RGBA, gl.UNSIGNED_BYTE, data)
  let contrast = 0, checksum = 0, minX = el.width, minY = el.height, maxX = 0, maxY = 0
  for (let i = 0; i < data.length; i += 4) {
    if (Math.min(data[i], data[i + 1], data[i + 2]) < 190) {
      contrast++
      const pixel = i / 4, x = pixel % el.width, y = Math.floor(pixel / el.width)
      minX = Math.min(minX, x); maxX = Math.max(maxX, x)
      minY = Math.min(minY, y); maxY = Math.max(maxY, y)
    }
    checksum = (checksum + data[i] * (i % 31 + 1) + data[i + 1] + data[i + 2]) >>> 0
  }
  return { width: el.width, height: el.height, contrast, checksum, ink: { minX, minY, maxX, maxY } }
})
try {
  await login(page, accounts.warehouse)
  await page.goto(baseUrl + '/index', { waitUntil: 'networkidle' })
  await map.locator('canvas[data-ready="true"]').waitFor()
  assert.equal(await map.locator('.leaflet-container').count(), 0, 'home defaults to actual 3D')
  assert.equal((await metric('库位').innerText()).trim(), String(valid.length))
  const headingBox = await space.getByRole('heading', { name: '仓储空间总览', exact: true }).boundingBox()
  assert.ok(headingBox.y < 230, 'showcase is the first workbench section')
  await shot('warehouse-home-1440.png')
  await page.screenshot({ path: path.join(output, 'warehouse-workbench-full.png'), animations: 'disabled' })
  screens.push('warehouse-workbench-full.png')
  await space.getByRole('button', { name: '打开仓储空间', exact: true }).click()
  await page.waitForURL(u => u.pathname === '/warehouse-space')
  await map.locator('canvas[data-ready="true"]').waitFor()
  assert.ok(await page.getByRole('menuitem', { name: '仓储空间总览', exact: true }).count(), 'dedicated permission-filtered menu')
  assert.equal((await metric('在库包数').innerText()).trim(), valid.reduce((n, l) => n + l.occupiedPackages, 0).toLocaleString('zh-CN'))
  assert.equal((await metric('登记吨数').innerText()).trim(), (valid.reduce((n, l) => n + l.occupiedWeight, 0) / 1000).toLocaleString('zh-CN', { maximumFractionDigits: 6 }))
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: width < 500 ? 1000 : 1100 })
    await map.getByRole('button', { name: '适配库区', exact: true }).click()
    const image = await frame()
    assert.ok(image.contrast > 1000, 'actual nonblank WebGL warehouse pixels')
    assert.ok(image.ink.minX > 0 && image.ink.minY > 0 && image.ink.maxX < image.width - 1 && image.ink.maxY < image.height - 1, 'framed warehouse pixels remain inside canvas')
    await page.waitForFunction(() => { const el = document.querySelector('.warehouse-space'); return el.scrollWidth <= el.clientWidth + 1 })
    pixels.push({ viewport: width, ...image })
    await shot(`warehouse-space-${width}.png`)
    if (width < 500) {
      await map.locator('canvas').scrollIntoViewIfNeeded()
      await map.locator('canvas').screenshot({ path: path.join(output, `warehouse-canvas-${width}.png`) })
      screens.push(`warehouse-canvas-${width}.png`)
    }
  }
  await page.setViewportSize({ width: 1440, height: 1100 })
  await space.getByRole('button', { name: '全屏展示仓储空间', exact: true }).click()
  await page.waitForFunction(() => document.fullscreenElement?.classList.contains('warehouse-space'))
  assert.ok((await frame()).contrast > 1000)
  await shot('warehouse-space-fullscreen.png')
  await space.getByRole('button', { name: '退出全屏展示', exact: true }).click()
  await page.waitForFunction(() => !document.fullscreenElement)
  const canvasBox = await map.locator('canvas').boundingBox()
  const initial = await frame()
  await page.mouse.move(canvasBox.x + canvasBox.width / 2, canvasBox.y + canvasBox.height / 2)
  await page.mouse.down()
  await page.mouse.move(canvasBox.x + canvasBox.width / 2 + 90, canvasBox.y + canvasBox.height / 2 + 20, { steps: 6 })
  await page.mouse.up()
  assert.notEqual((await frame()).checksum, initial.checksum, 'real orbit interaction changes canvas pixels')
  await map.getByRole('button', { name: '复位立体视角', exact: true }).click()
  const areaButton = space.getByRole('navigation', { name: '库区导航', exact: true }).getByRole('button').nth(1)
  await areaButton.click()
  const areaSize = Number((await areaButton.locator('span').innerText()).match(/\d+/)[0])
  assert.equal((await metric('库位').innerText()).trim(), String(areaSize))
  await shot('warehouse-space-area.png')
  await space.getByRole('navigation', { name: '库区导航', exact: true }).getByRole('button').first().click()
  assert.equal((await metric('库位').innerText()).trim(), String(valid.length))
  const warehouseSelector = space.locator('.warehouse-selector .el-select__wrapper')
  await warehouseSelector.click()
  await page.getByRole('option', { name: location.warehouseName, exact: true }).click()
  const scoped = valid.filter(l => l.warehouseCode.trim() === location.warehouseCode.trim())
  assert.equal((await metric('库位').innerText()).trim(), String(scoped.length))
  assert.equal((await space.getByRole('navigation', { name: '库区导航', exact: true }).getByRole('button').first().locator('span').innerText()).trim(), `${scoped.length} 库位`)
  await shot('warehouse-space-warehouse.png')
  await map.getByRole('button', { name: '筛选与查货', exact: true }).click()
  await map.getByRole('textbox', { name: '包码或二维码内容', exact: true }).fill(`https://legacy.invalid/pkg/${encodeURIComponent(unit.packageCode)}?internal=1`)
  await map.getByRole('button', { name: '定位货物库位', exact: true }).click()
  const inspector = map.getByRole('complementary', { name: `库位资料 ${location.locationCode}`, exact: true })
  await inspector.waitFor()
  await shot('warehouse-space-package.png')
  const readsBefore = stockReads
  const cameraBefore = await frame()
  const stockResponse = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rd/stock-lot/page') && new URL(r.url()).searchParams.get('locationId') === String(location.id))
  await space.getByRole('button', { name: '刷新仓储空间', exact: true }).click()
  await stockResponse
  await inspector.waitFor()
  assert.ok(stockReads > readsBefore, 'same overview snapshot re-reads selected exact location details')
  assert.equal((await frame()).checksum, cameraBefore.checksum, 'same snapshot refresh preserves focused scene/camera')
  const pollResponse = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rd/putaway-task/location-map'))
  const pollDetail = page.waitForResponse(r => new URL(r.url()).pathname.endsWith('/rd/stock-lot/page') && new URL(r.url()).searchParams.get('locationId') === String(location.id))
  await pollResponse
  await pollDetail
  await inspector.waitFor()
  assert.equal((await frame()).checksum, cameraBefore.checksum, 'actual five-second polling retains focus while re-reading details')
  assert.equal(await map.getByRole('button', { name: '设为计划库位', exact: true }).count(), 0, 'showcase does not expose stock/planning writes')
  await map.getByRole('button', { name: '关闭库位资料', exact: true }).click()
  assert.equal(await map.getByRole('complementary').count(), 0)
  await map.getByRole('radiogroup', { name: '库位状态筛选' }).getByText('预留', { exact: true }).click()
  const reserved = scoped.filter(l => l.reservedPackages > 0 || l.reservedWeight > 0)
  // The exact package lookup selected one area; return to the whole selected warehouse first.
  await space.getByRole('navigation', { name: '库区导航', exact: true }).getByRole('button').first().click()
  assert.equal((await metric('匹配').innerText()).trim(), String(reserved.length))
  await shot('warehouse-space-filter.png')
  await map.getByRole('radiogroup', { name: '库位状态筛选' }).getByText('全部', { exact: true }).click()
  await page.reload({ waitUntil: 'networkidle' })
  await map.locator('canvas[data-ready="true"]').waitFor()
  for (const role of ['driver', 'production']) {
    const deniedBrowser = await isolated()
    let reads = 0
    deniedBrowser.page.on('request', request => { if (new URL(request.url()).pathname.endsWith('/rd/putaway-task/location-map')) reads++ })
    await login(deniedBrowser.page, accounts[role])
    await deniedBrowser.page.goto(baseUrl + '/index', { waitUntil: 'networkidle' })
    assert.equal(await deniedBrowser.page.getByRole('region', { name: '仓储空间总览', exact: true }).count(), 0)
    assert.equal(await deniedBrowser.page.getByRole('menuitem', { name: '仓储空间总览', exact: true }).count(), 0)
    await deniedBrowser.page.goto(baseUrl + '/warehouse-space', { waitUntil: 'networkidle' })
    assert.equal(new URL(deniedBrowser.page.url()).pathname, '/403')
    assert.equal(reads, 0)
    denied.push({ role, mapReads: reads, redirect: '/403' })
    await deniedBrowser.context.close()
  }
  const anonymous = await isolated()
  await anonymous.page.goto(baseUrl + '/warehouse-space', { waitUntil: 'networkidle' })
  assert.equal(new URL(anonymous.page.url()).pathname, '/login')
  assert.equal(new URL(anonymous.page.url()).searchParams.get('redirect'), '/warehouse-space')
  await anonymous.context.close()
  const after = await api('/rd/putaway-task/location-map', tokens.warehouse)
  const stockAfter = await api('/rd/stock-lot/page?pageNo=1&pageSize=100', tokens.warehouse)
  assert.deepEqual(after, before)
  assert.deepEqual(stockAfter, stockBefore)
  assert.equal(writes, 0)
  assert.deepEqual(external, [])
  assert.deepEqual(errors, [])
  const result = { state: 'PASS', localOnly: true, locations: valid.length, default3D: true, primaryHome: true, fullScreen: true, realOrbit: true, refreshedDetails: true, actualPollingDetails: true, cameraPreserved: true, denied, anonymousRedirect: true, screens, pixels, writes, external, errors, stockUnchanged: true }
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
} catch (error) {
  await page.screenshot({ path: path.join(output, 'failure-page.png'), fullPage: true }).catch(() => {})
  await fs.writeFile(path.join(output, 'failure.json'), JSON.stringify({ state: 'FAIL', message: String(error), screens, pixels, errors, external, writes }, null, 2))
  throw error
} finally {
  await context.close()
  await browser.close()
}
