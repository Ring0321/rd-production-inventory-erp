import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(path.join(root, 'frontend/package.json'))
const { chromium } = require(path.join(process.argv[2], 'playwright'))
const origin = process.argv[3] || 'http://127.0.0.1:9001'
assert.ok(['http://127.0.0.1:9001', 'https://101.42.182.117'].includes(origin))
const output = path.join(root, 'temp/xiaoduo-assistant-' + (origin.startsWith('https:') ? 'cloud' : 'local'))
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--use-angle=swiftshader', '--enable-unsafe-swiftshader'] })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const errors = [], external = [], writes = [], reads = [], screens = []
let holdInventory = false, releaseInventory, inventoryStarted
await context.route('**/*', async (route) => {
  const request = route.request(), url = new URL(request.url())
  if (!['data:', 'blob:'].includes(url.protocol) && url.origin !== origin) { external.push(url.origin); return route.abort() }
  if (url.pathname.startsWith('/admin-api/rd/')) {
    if (request.method() !== 'GET') writes.push({ path: url.pathname, method: request.method() })
    else reads.push(url.pathname)
  }
  if (holdInventory && url.pathname === '/admin-api/rd/dashboard/overview') {
    holdInventory = false
    const response = await route.fetch()
    const gate = new Promise((resolve) => { releaseInventory = resolve })
    inventoryStarted?.()
    await gate
    return route.fulfill({ response })
  }
  await route.continue()
})
const page = await context.newPage()
page.on('pageerror', (error) => errors.push(error.message))
const panel = () => page.getByRole('dialog', { name: '智能小垛', exact: true })
const editor = () => panel().getByRole('textbox', { name: '向智能小垛提问', exact: true })
const shot = async (name) => { await page.mouse.move(1000, 90); await page.screenshot({ path: path.join(output, name + '.png') }); screens.push(name + '.png') }
const open = async () => { await page.getByRole('button', { name: '打开智能小垛', exact: true }).click(); await panel().waitFor() }
const ask = async (question) => {
  const count = await panel().locator('.xiaoduo-turn').count()
  await editor().fill(question)
  await panel().getByRole('button', { name: '发送给小垛', exact: true }).click()
  await page.waitForFunction((count) => document.querySelectorAll('.xiaoduo-turn').length > count && Boolean(document.querySelector('.xiaoduo-turn:last-child .xiaoduo-answer')), count)
}
const fitPanel = async () => {
  const rect = await panel().boundingBox(), viewport = page.viewportSize()
  assert.ok(rect.x >= 0 && rect.y >= 0 && rect.x + rect.width <= viewport.width + 1 && rect.y + rect.height <= viewport.height + 1)
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1))
}

try {
  await page.goto(origin + '/login', { waitUntil: 'networkidle', timeout: 60000 })
  assert.equal(await page.getByRole('button', { name: '打开智能小垛', exact: true }).count(), 0)
  await page.getByRole('button', { name: '一键进入演示', exact: true }).click()
  await page.waitForURL((url) => url.pathname !== '/login', { timeout: 60000 })
  await page.goto(origin + '/warehouse-space?warehouseCode=DEMO-SPACE-V2', { waitUntil: 'networkidle', timeout: 60000 })
  const launcher = page.getByRole('button', { name: '打开智能小垛', exact: true })
  await launcher.waitFor()
  const launcherRect = await launcher.boundingBox()
  assert.ok(launcherRect.x <= 20 && launcherRect.y >= 900)
  await open()
  await fitPanel()
  assert.ok((await panel().locator('svg.iconify path').count()) > 0)
  await page.waitForFunction(() => {
    const logo = document.querySelector('.xiaoduo-welcome img')
    return logo instanceof HTMLImageElement && logo.complete && logo.naturalWidth > 0
  })
  await shot('01-welcome-desktop')
  await panel().screenshot({ path: path.join(output, '00-assistant-window.png') })
  screens.push('00-assistant-window.png')
  await ask('产品资料在哪里')
  await panel().getByRole('button', { name: '打开 产品资料', exact: true }).waitFor()
  await shot('02-find-product')
  await panel().getByRole('button', { name: '打开 产品资料', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/rd/masterdata/product')
  assert.equal(await panel().count(), 0)
  await open()
  assert.equal(await panel().locator('.xiaoduo-turn').count(), 1)
  await ask('这个页面怎么用')
  await panel().getByText('产品资料怎么维护', { exact: true }).waitFor()
  await shot('03-current-page-help')
  const beforeInventory = reads.filter((value) => value.endsWith('/dashboard/overview')).length
  await ask('查库存')
  await panel().getByText('在库包数', { exact: true }).waitFor()
  assert.equal(reads.filter((value) => value.endsWith('/dashboard/overview')).length, beforeInventory + 1)
  await shot('04-live-inventory')
  await page.goto(origin + '/warehouse-space?warehouseCode=DEMO-SPACE-V2', { waitUntil: 'networkidle' })
  await open()
  await ask('仓库有多少库位')
  await panel().getByText('96 个', { exact: true }).waitFor()
  await panel().getByText('02 层：48 个库位。', { exact: true }).waitFor()
  await shot('05-live-double-tier-counts')
  await ask('查 LD20261015A0014 在哪里')
  await panel().getByRole('button', { name: '打开 打开这包货物的追溯资料', exact: true }).waitFor()
  await shot('06-exact-package')
  await ask('叉车司机需要扫码吗')
  await panel().getByText('叉车司机与仓管的职责', { exact: true }).waitFor()
  await shot('07-keeper-driver-boundary')
  await ask('帮我把库存改成200包')
  await panel().getByText('小垛只做查询与导引', { exact: true }).waitFor()
  let started
  const startedPromise = new Promise((resolve) => { started = resolve })
  inventoryStarted = started
  holdInventory = true
  await editor().fill('查库存')
  await panel().getByRole('button', { name: '发送给小垛', exact: true }).click()
  await startedPromise
  await panel().getByRole('button', { name: '取消小垛查询', exact: true }).click()
  releaseInventory()
  await panel().getByText('已取消本次查询', { exact: true }).waitFor()
  await shot('08-cancel-read')
  await panel().getByRole('button', { name: '清空小垛对话', exact: true }).click()
  assert.equal(await panel().locator('.xiaoduo-turn').count(), 0)
  await panel().getByRole('button', { name: '展开小垛窗口', exact: true }).click()
  await fitPanel()
  await shot('09-expanded-window')
  await panel().getByRole('button', { name: '还原小垛窗口', exact: true }).click()
  await editor().fill('入库怎么操作')
  await editor().press('Enter')
  await panel().getByText('入库上架怎么做', { exact: true }).waitFor()
  await editor().fill('第一行')
  await editor().press('Shift+Enter')
  assert.equal(await editor().inputValue(), '第一行\n')
  await editor().press('Escape')
  await launcher.waitFor()
  assert.ok(await launcher.evaluate((element) => element === document.activeElement))
  await open()
  for (const viewport of [{ width: 390, height: 844 }, { width: 320, height: 760 }]) {
    await page.setViewportSize(viewport)
    await fitPanel()
    await shot(`10-mobile-${viewport.width}`)
    await editor().fill('出库怎么操作')
    await editor().press('Enter')
    await panel().getByText('提货到装车出库怎么做', { exact: true }).last().waitFor()
    await fitPanel()
    await shot(`11-mobile-answer-${viewport.width}`)
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto(origin + '/pda', { waitUntil: 'networkidle' })
  assert.equal(await page.getByRole('button', { name: '打开智能小垛', exact: true }).count(), 0)
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.getByRole('button', { name: '退出', exact: true }).click()
  await page.waitForURL((url) => url.pathname === '/login')
  assert.equal(await page.getByRole('button', { name: '打开智能小垛', exact: true }).count(), 0)
  await page.getByRole('button', { name: '一键进入演示', exact: true }).click()
  await page.waitForURL((url) => url.pathname !== '/login')
  await page.goto(origin + '/warehouse-space?warehouseCode=DEMO-SPACE-V2', { waitUntil: 'networkidle' })
  await open()
  assert.equal(await panel().locator('.xiaoduo-turn').count(), 0)
  await shot('12-clean-relogin')
  assert.equal(errors.length, 0, errors.join('\n'))
  assert.equal(external.length, 0, external.join('\n'))
  assert.equal(writes.length, 0, JSON.stringify(writes))
  const result = { result: 'PASS', checkedAt: new Date().toISOString(), origin, screens, pageErrors: errors, externalRequests: external, businessOrModelWrites: writes, pageReadCounts: Object.fromEntries([...new Set(reads)].map((key) => [key, reads.filter((value) => value === key).length])), desktopAndMobile: [1440, 390, 320], pageNavigationAndContext: true, realWarehouseAndPackageQueries: true, cancelAndKeyboard: true, logoutClearsConversation: true }
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result))
} catch (error) {
  releaseInventory?.()
  await page.screenshot({ path: path.join(output, 'failure.png') }).catch(() => {})
  await fs.writeFile(path.join(output, 'failure.json'), JSON.stringify({ message: error.message, pageErrors: errors, externalRequests: external, writes }, null, 2))
  throw error
} finally {
  await browser.close()
}
