import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(path.join(root, 'frontend/package.json'))
const { chromium } = require(path.join(process.argv[2], 'playwright'))
const origin = 'https://101.42.182.117'
const code = 'LD20261015A0014'
const output = path.join(root, 'temp/deepseek-cloud')
const checkpoint = path.join(output, 'original-request.json')
await fs.mkdir(output, { recursive: true })
let saved
try { saved = JSON.parse(await fs.readFile(checkpoint, 'utf8')) } catch (error) {
  if (error.code !== 'ENOENT') throw error
}
if (saved) assert.equal(saved.request.packageCode, code)
const recoveryMode = Boolean(saved)
const browser = await chromium.launch({ channel: 'msedge', headless: true })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
const errors = [], external = [], forbidden = [], requests = [], screens = []
let allowOnePaidRequest = false
await context.route('**/*', async route => {
  const request = route.request(), url = new URL(request.url())
  if (!['data:', 'blob:'].includes(url.protocol) && url.origin !== origin) {
    external.push(url.origin); return route.abort()
  }
  if (url.pathname.startsWith('/admin-api/rd/') && request.method() !== 'GET') {
    if (url.pathname !== '/admin-api/rd/ai/package/ask' || !allowOnePaidRequest) {
      forbidden.push(url.pathname); return route.abort()
    }
    allowOnePaidRequest = false
    const value = request.postDataJSON()
    assert.deepEqual(Object.keys(value).sort(), ['packageCode', 'question', 'requestId'])
    assert.equal(value.packageCode, code)
    assert.equal(value.question, 'RISK_REVIEW')
    assert.match(value.requestId, /^[0-9a-f-]{36}$/i)
    requests.push(value)
    saved = { ownerId: 235, tenant: '1', request: value, state: 'UNKNOWN', version: 1 }
    // A rerun recovers this original request by GET, never creates another paid call.
    await fs.writeFile(checkpoint, JSON.stringify(saved, null, 2))
  }
  await route.continue()
})
const page = await context.newPage()
page.on('pageerror', error => errors.push(error.message))
const section = () => page.locator('.package-ai-section')
const receive = async promise => {
  const response = await promise
  assert.equal(response.status(), 200)
  const body = await response.json()
  assert.equal(body.code, 0)
  await fs.writeFile(path.join(output, 'last-answer.json'), JSON.stringify({
    state: body.data.state, noteKeys: body.data.noteKeys, totalTokens: body.data.totalTokens,
    answeredAt: body.data.answeredAt, requestId: body.data.requestId,
    provider: body.data.context?.provider
  }, null, 2))
  assert.equal(body.data.state, 'SUCCESS', 'Real model did not return a verified answer; do not automatically retry')
  assert.equal(body.data.requestId, saved.request.requestId)
  assert.ok(body.data.noteKeys.length >= 1 && body.data.noteKeys.length <= 5)
  assert.ok(body.data.noteKeys.every(key => body.data.context.notices.some(note => note.key === key)))
  return body.data
}
const reconcile = async () => {
  const response = page.waitForResponse(r => new URL(r.url()).pathname === '/admin-api/rd/ai/package/result', { timeout: 60000 })
  await section().getByRole('button', { name: '核对原请求', exact: true }).click()
  return receive(response)
}
try {
  await page.goto(origin + '/login', { waitUntil: 'networkidle' })
  await page.getByRole('button', { name: '一键进入演示', exact: true }).click()
  await page.waitForURL(url => url.pathname !== '/login', { timeout: 60000 })
  if (saved) await page.evaluate(value => {
    localStorage.setItem(`rd-package-ai:v1:1:235:${value.request.packageCode}`, JSON.stringify(value))
  }, saved)
  await page.goto(origin + `/pkg/${code}?internal=1`, { waitUntil: 'networkidle', timeout: 60000 })
  await section().getByText('ERP 月预算 20 元', { exact: true }).waitFor()
  let answer
  if (saved) answer = await reconcile()
  else {
    await section().getByRole('textbox', { name: '现场问句', exact: true }).fill('这批能发吗')
    await section().getByRole('button', { name: '识别问题', exact: true }).click()
    assert.equal(requests.length, 0)
    const response = page.waitForResponse(r => new URL(r.url()).pathname === '/admin-api/rd/ai/package/ask', { timeout: 60000 })
    allowOnePaidRequest = true
    await section().getByRole('button', { name: '请求整理', exact: true }).click()
    answer = await receive(response)
  }
  await section().getByRole('heading', { name: 'AI整理要点', exact: true }).waitFor()
  await section().screenshot({ path: path.join(output, '01-deepseek-desktop.png') })
  screens.push('01-deepseek-desktop.png')
  const replay = await reconcile()
  assert.deepEqual(replay.noteKeys, answer.noteKeys)
  assert.equal(replay.answeredAt, answer.answeredAt)
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: width === 390 ? 844 : 760 })
    await section().locator('.package-ai-heading').scrollIntoViewIfNeeded()
    assert.ok(await section().evaluate(element => element.scrollWidth <= element.clientWidth + 1))
    await page.screenshot({ path: path.join(output, `02-deepseek-mobile-${width}.png`) })
    screens.push(`02-deepseek-mobile-${width}.png`)
    await section().locator('.package-ai-answer').scrollIntoViewIfNeeded()
    const bounds = await section().locator('.package-ai-answer').boundingBox()
    assert.ok(bounds.y >= 0 && bounds.y + bounds.height <= page.viewportSize().height + 1)
    await page.screenshot({ path: path.join(output, `03-deepseek-answer-mobile-${width}.png`) })
    screens.push(`03-deepseek-answer-mobile-${width}.png`)
  }
  await page.reload({ waitUntil: 'networkidle' })
  const recovered = await reconcile()
  assert.equal(recovered.requestId, answer.requestId)
  assert.equal(recovered.answeredAt, answer.answeredAt)
  assert.equal(requests.length, recoveryMode ? 0 : 1)
  assert.deepEqual(errors, []); assert.deepEqual(external, []); assert.deepEqual(forbidden, [])
  const result = { result: 'PASS', checkedAt: new Date().toISOString(), model: 'deepseek-flash',
    provider: 'DeepSeek', monthlyBudgetCny: 20, modelPostCountThisRun: requests.length,
    originalRequestId: answer.requestId, realAnswer: true, totalTokens: answer.totalTokens,
    sameRequestReplayAndReload: true, rawFieldQuestionNotPosted: true,
    screens, pageErrors: errors, externalRequests: external, businessWrites: forbidden }
  await fs.writeFile(path.join(output, 'result.json'), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result))
} catch (error) {
  await page.screenshot({ path: path.join(output, 'failure.png') }).catch(() => {})
  throw error
} finally { await context.close(); await browser.close() }
