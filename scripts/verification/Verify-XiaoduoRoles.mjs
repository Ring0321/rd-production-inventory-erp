import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(path.join(root, 'frontend/package.json'))
const { chromium } = require(path.join(process.argv[2], 'playwright'))
const origin = 'https://101.42.182.117'
const access = JSON.parse((await fs.readFile(path.join(root, 'temp/cloud-demo-20261018/cloud-access.json'), 'utf8')).replace(/^\uFEFF/, ''))
assert.equal(access.origin, origin)
assert.ok(access.syntheticDemoOnly)
const output = path.join(root, 'temp/xiaoduo-assistant-roles')
await fs.mkdir(output, { recursive: true })
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const results = [], errors = [], writes = [], external = []
const redact = (value) => access.accounts.reduce((text, account) => text.replaceAll(account.password, '[redacted]'), value)
try {
  for (const role of ['driver', 'sales', 'warehouse', 'quality']) {
    const account = access.accounts.find((item) => item.role === role)
    assert.ok(account)
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
    const calls = []
    await context.route('**/*', async (route) => {
      const request = route.request(), url = new URL(request.url())
      if (!['data:', 'blob:'].includes(url.protocol) && url.origin !== origin) { external.push(url.origin); return route.abort() }
      if (url.pathname.startsWith('/admin-api/rd/')) {
        calls.push(url.pathname)
        if (request.method() !== 'GET') writes.push(url.pathname)
      }
      await route.continue()
    })
    const page = await context.newPage()
    page.on('pageerror', (error) => errors.push(redact(error.message)))
    await page.goto(origin + '/login', { waitUntil: 'networkidle' })
    await page.getByPlaceholder('账号', { exact: true }).fill(account.username)
    await page.getByPlaceholder('密码', { exact: true }).fill(account.password)
    await page.getByRole('button', { name: '登录', exact: true }).click()
    await page.waitForURL((url) => url.pathname !== '/login', { timeout: 60000 })
    await page.goto(origin + '/index', { waitUntil: 'networkidle' })
    if (role === 'driver') {
      assert.equal(await page.locator('.dashboard-page').count(), 0)
      assert.ok(!calls.some((value) => value.endsWith('/dashboard/overview')))
    }
    await page.getByRole('button', { name: '打开智能小垛', exact: true }).click()
    const panel = page.getByRole('dialog', { name: '智能小垛', exact: true })
    const questions = role === 'driver' ? ['查库存', '查LD20261015A0014'] : role === 'sales' ? ['查LD20261015A0014'] : role === 'warehouse' ? ['仓库有多少库位'] : ['查LD20261015A0014']
    const outcomes = []
    for (const question of questions) {
      const before = calls.length
      const turns = await panel.locator('.xiaoduo-turn').count()
      await panel.getByRole('textbox', { name: '向智能小垛提问', exact: true }).fill(question)
      await panel.getByRole('button', { name: '发送给小垛', exact: true }).click()
      await page.waitForFunction((count) => document.querySelectorAll('.xiaoduo-turn').length > count && document.querySelector('.xiaoduo-turn:last-child .xiaoduo-answer'), turns)
      if (['driver', 'sales'].includes(role)) {
        await panel.locator('.xiaoduo-turn').last().getByText('当前账号无查询权限', { exact: true }).waitFor()
        assert.equal(calls.length, before)
      } else {
        assert.ok((await panel.locator('.xiaoduo-turn').last().locator('.answer-facts dd').count()) > 0)
      }
      outcomes.push({ question, dataReads: calls.length - before, denied: ['driver', 'sales'].includes(role) })
    }
    await page.screenshot({ path: path.join(output, role + '.png') })
    results.push({ role, outcomes })
    await context.close()
  }
  assert.equal(writes.length, 0)
  assert.equal(external.length, 0)
  assert.equal(errors.length, 0)
  const result = { result: 'PASS', checkedAt: new Date().toISOString(), origin, roles: results, businessWrites: writes, externalRequests: external, pageErrors: errors, credentialsRecorded: false }
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result))
} catch (error) {
  const message = redact(error.message)
  await fs.writeFile(path.join(output, 'failure.json'), JSON.stringify({ message, results, writes, external, errors }, null, 2))
  throw Error(message)
} finally {
  await browser.close()
}
