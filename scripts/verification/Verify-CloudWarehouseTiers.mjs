import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { createHash } from 'node:crypto'
import { fileURLToPath } from 'node:url'

// User explicitly authorized a separate local Playwright browser for this cloud demo.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const requireFrontend = createRequire(path.join(root, 'frontend/package.json'))
const modules = process.argv[2] || process.env.RD_BROWSER_MODULES
const { chromium } = modules ? requireFrontend(path.join(modules, 'playwright')) : requireFrontend('playwright')
const origin = 'https://101.42.182.117'
const output = path.join(root, 'temp/cloud-warehouse-tiers-verification')
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({
  channel: 'msedge',
  headless: true,
  args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader']
})
const errors = [], external = [], screens = [], observations = []
let businessWrites = 0
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
await context.route('**/*', async (route) => {
  const request = route.request()
  const url = new URL(request.url())
  if (!['data:', 'blob:'].includes(url.protocol) && url.origin !== origin) {
    external.push(url.origin)
    return route.abort()
  }
  if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method()) && url.pathname.startsWith('/admin-api/rd/')) businessWrites++
  await route.continue()
})
const page = await context.newPage()
page.on('pageerror', (error) => errors.push(error.message))
const region = () => page.getByRole('region', { name: '库位空间视图', exact: true })
const canvas = () => region().locator('canvas[data-ready="true"]')
const screenshot = async (name) => {
  const filename = name + '.png'
  await page.screenshot({ path: path.join(output, filename), fullPage: true })
  screens.push(filename)
}
const statistics = () => region().getByLabel('当前范围库位统计').evaluate((element) =>
  Object.fromEntries([...element.children].map((row) => [row.querySelector('dt').textContent, row.querySelector('dd').textContent])))
const pixelCheck = async () => {
  const result = await canvas().evaluate((element) => {
    const gl = element.getContext('webgl2')
    if (!gl || gl.isContextLost()) throw Error('No active warehouse WebGL2 context')
    const pixels = new Uint8Array(element.width * element.height * 4)
    gl.readPixels(0, 0, element.width, element.height, gl.RGBA, gl.UNSIGNED_BYTE, pixels)
    const colors = new Set()
    let changed = 0, total = 0
    for (let index = 0; index < pixels.length; index += 4 * 13) {
      const r = pixels[index], g = pixels[index + 1], b = pixels[index + 2]
      colors.add((r << 16) + (g << 8) + b)
      if (Math.abs(r - 242) + Math.abs(g - 245) + Math.abs(b - 245) > 40) changed++
      total++
    }
    return { width: element.width, height: element.height, colors: colors.size, nonBackgroundFraction: changed / total }
  })
  assert.ok(result.colors > 64 && result.nonBackgroundFraction > 0.04, JSON.stringify(result))
  return result
}
const chooseTier = async (label) => {
  await region().locator('.tier-select .el-select__wrapper').click()
  await page.getByRole('option', { name: label, exact: true }).click()
  await page.locator('.el-select__popper:visible').waitFor({ state: 'hidden' })
  await canvas().waitFor()
}
const assertNoOverflow = async () => {
  const layout = await page.evaluate(() => ({
    width: window.innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
    outside: [...document.querySelectorAll('body *')].map((element) => {
      const rect = element.getBoundingClientRect()
      return { tag: element.tagName, class: typeof element.className === 'string' ? element.className : '', left: rect.left, right: rect.right }
    }).filter((rect) => rect.right > window.innerWidth + 1 && rect.left >= 0).slice(0, 18)
  }))
  assert.ok(layout.scrollWidth <= layout.width + 1, JSON.stringify(layout))
}

try {
  await page.goto(origin + '/login', { waitUntil: 'networkidle', timeout: 60000 })
  await page.getByRole('button', { name: '一键进入演示', exact: true }).click()
  await page.waitForURL((url) => url.pathname !== '/login', { timeout: 60000 })
  await page.goto(origin + '/warehouse-space?warehouseCode=DEMO-SPACE-V2', { waitUntil: 'networkidle', timeout: 60000 })
  await canvas().waitFor({ timeout: 60000 })
  await region().locator('.scene-bin-label').first().waitFor()
  assert.equal((await statistics())['库位'], '96')
  const desktop = await pixelCheck()
  await screenshot('01-desktop-double-tier')
  const beforeOrbit = createHash('sha256').update(await canvas().screenshot()).digest('hex')
  const rect = await canvas().boundingBox()
  await page.mouse.move(rect.x + rect.width * 0.7, rect.y + rect.height * 0.55)
  await page.mouse.down()
  await page.mouse.move(rect.x + rect.width * 0.58, rect.y + rect.height * 0.6, { steps: 12 })
  await page.mouse.up()
  const afterOrbit = createHash('sha256').update(await canvas().screenshot()).digest('hex')
  assert.notEqual(beforeOrbit, afterOrbit)
  await screenshot('02-desktop-orbit')
  await chooseTier('库位 02 层')
  assert.equal((await statistics())['库位'], '48')
  const upper = await pixelCheck()
  const codes = await region().locator('.scene-bin-label').evaluateAll((items) => items.map((item) => item.textContent.trim()))
  assert.ok(codes.length && codes.every((code) => code.includes('-02')))
  await screenshot('03-upper-tier')
  await region().locator('.scene-bin-label').first().click()
  await region().getByText('库位库存', { exact: true }).waitFor()
  await screenshot('04-upper-tier-details')
  await chooseTier('库位 01 层')
  assert.equal((await statistics())['库位'], '48')
  await screenshot('05-lower-tier')
  await chooseTier('全部层级')
  await region().getByText('分层平铺', { exact: true }).click()
  assert.equal((await statistics())['库位'], '96')
  await screenshot('06-spread-tiers')
  await region().getByText('平面', { exact: true }).click()
  await region().locator('.leaflet-container').waitFor()
  await screenshot('07-two-dimensional')
  await region().getByText('立体', { exact: true }).click()
  await canvas().waitFor()
  await region().getByText('上下叠层', { exact: true }).click()
  await page.reload({ waitUntil: 'networkidle' })
  await canvas().waitFor()
  assert.equal((await statistics())['库位'], '96')
  await page.getByRole('button', { name: '全屏展示仓储空间', exact: true }).click()
  await page.waitForFunction(() => Boolean(document.fullscreenElement))
  await screenshot('12-fullscreen-double-tier')
  await page.getByRole('button', { name: '退出全屏展示', exact: true }).click()
  await page.waitForFunction(() => !document.fullscreenElement)
  observations.push({ viewport: '1440x1000', desktop, upper, orbitChangedPixels: true })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.getByRole('button', { name: '适配库区', exact: true }).click()
  const mobile = await pixelCheck()
  await assertNoOverflow()
  await canvas().scrollIntoViewIfNeeded()
  await screenshot('08-mobile-double-tier')
  await chooseTier('库位 02 层')
  assert.equal((await statistics())['库位'], '48')
  await screenshot('09-mobile-upper-tier')
  observations.push({ viewport: '390x844', mobile, noHorizontalOverflow: true })
  await page.setViewportSize({ width: 320, height: 760 })
  await page.getByRole('button', { name: '适配库区', exact: true }).click()
  const narrow = await pixelCheck()
  await assertNoOverflow()
  await canvas().scrollIntoViewIfNeeded()
  await screenshot('10-narrow-upper-tier')
  observations.push({ viewport: '320x760', narrow, noHorizontalOverflow: true })
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(origin + '/rd/masterdata/product', { waitUntil: 'networkidle' })
  await page.getByText('DEMO2-P001', { exact: true }).waitFor()
  assert.ok((await page.locator('.el-table__body tr').count()) >= 10)
  await screenshot('11-product-master')
  await page.goto(origin + '/pda', { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: '返回 ERP 工作台', exact: true }).click()
  await page.waitForURL((url) => !url.pathname.startsWith('/pda') && url.pathname !== '/login')
  await screenshot('13-pda-return-erp')
  await page.goto(origin + '/pda', { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/login')
  await page.reload({ waitUntil: 'networkidle' })
  await page.getByRole('button', { name: '一键进入演示', exact: true }).waitFor()
  await screenshot('14-logged-out-login')
  await page.goto(origin + '/warehouse-space?warehouseCode=DEMO-SPACE-V2', { waitUntil: 'networkidle' })
  assert.equal(new URL(page.url()).pathname, '/login')
  await page.getByRole('button', { name: '一键进入演示', exact: true }).click()
  await canvas().waitFor({ timeout: 60000 })
  assert.equal((await statistics())['库位'], '96')
  await screenshot('15-relogin-double-tier')
  assert.equal(errors.length, 0, errors.join('\n'))
  assert.equal(external.length, 0, external.join('\n'))
  assert.equal(businessWrites, 0)
  const result = { result: 'PASS', origin, observations, screens, reloadVerified: true, fullscreenVerified: true, pdaReturnErpVerified: true, logoutAndReloginVerified: true, loggedOutMapRedirectedToLogin: true, pageErrors: errors, externalRequests: external, businessWrites }
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result))
} catch (error) {
  await screenshot('failure').catch(() => {})
  await fs.writeFile(path.join(output, 'failure.json'), JSON.stringify({ message: error.message, pageErrors: errors, externalRequests: external, businessWrites }, null, 2))
  throw error
} finally {
  await browser.close()
}
