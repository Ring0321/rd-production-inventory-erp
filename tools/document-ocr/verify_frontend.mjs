// Synthetic auth/API UI harness; no existing app session, business backend, or credentials.
import assert from 'node:assert/strict'
import { createRequire } from 'node:module'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const base = path.dirname(fileURLToPath(import.meta.url))
const repo = path.resolve(base, '../..')
const deps = process.argv[2]
if (!deps) throw new Error('Pass an existing frontend package directory for read-only dependency reuse')
const require = createRequire(path.resolve(deps, 'package.json'))
const load = (name) => import(pathToFileURL(require.resolve(name)).href)
const { createServer } = await load('vite')
const vue = (await load('@vitejs/plugin-vue')).default
const ts = require('typescript')
const browserRequire = createRequire('C:/Users/Ring/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/package.json')
const { chromium } = browserRequire('playwright')
const fixture = path.join(base, 'browser-fixture')
const aliases = [
  { find: '@/utils/auth', replacement: path.join(fixture, 'auth.ts') },
  { find: '@/store/modules/user', replacement: path.join(fixture, 'user.ts') },
  { find: '@/config/axios', replacement: path.join(fixture, 'axios.ts') },
  { find: '@ocr-component', replacement: path.join(repo, 'frontend/src/views/rd/assist/ocr/LocalDocumentReader.vue') },
  { find: '@', replacement: path.join(repo, 'frontend/src') },
  { find: /^vue$/, replacement: path.join(deps, 'node_modules/vue/dist/vue.runtime.esm-bundler.js') },
  { find: 'element-plus', replacement: path.join(deps, 'node_modules/element-plus') }
]
const compiler = ts.createProgram([
  path.join(repo, 'frontend/src/views/rd/assist/ocr/useLocalDocumentReader.ts'),
  path.join(repo, 'frontend/src/api/rd/documentocr/index.ts')
], { noEmit: true, strict: true, skipLibCheck: true, target: ts.ScriptTarget.ES2022,
  module: ts.ModuleKind.ESNext, moduleResolution: ts.ModuleResolutionKind.Bundler,
  baseUrl: path.join(repo, 'frontend'), ignoreDeprecations: '6.0',
  paths: {
    '@/utils/auth': [path.join(fixture, 'auth.ts')], '@/store/modules/user': [path.join(fixture, 'user.ts')],
    '@/config/axios': [path.join(fixture, 'axios.ts')], '@/*': ['src/*'],
    vue: [path.join(deps, 'node_modules/vue')]
  } })
const diagnostics = ts.getPreEmitDiagnostics(compiler)
assert.equal(diagnostics.length, 0, diagnostics.map((d) => ts.flattenDiagnosticMessageText(d.messageText, '\n')).join('\n'))

const fields = Object.fromEntries(['customer', 'order', 'model', 'bagCount', 'weightKg'].map((key, index) => [key, {
  state: 'CANDIDATE', candidates: [{ value: ['合成测试厂', '000042', 'SiC-90', '12', '1250'][index], sourceLineIds: [`p1-l${index + 1}`],
    evidence: ['客户：合成测试厂', '订单号：000042', '型号：SiC-90', '袋数：12袋', '净重：1250 kg'][index] }]
}]))
let result = { schemaVersion: 1, engine: 'PaddleOCR', model: 'PP-OCRv5_mobile', device: 'cpu', pages: 1,
  candidates: fields, rawLines: Object.values(fields).map((field, index) => ({ id: `p1-l${index + 1}`, page: 1, text: field.candidates[0].evidence })) }
const captured = path.join(base, '.runtime/real-inference.json')
if (fs.existsSync(captured)) result = JSON.parse(fs.readFileSync(captured, 'utf-8')).result
let recognizeCalls = 0
let delay = 0
const server = await createServer({ configFile: false, root: fixture, cacheDir: path.join(base, '.runtime/vite-cache'),
  resolve: { alias: aliases }, optimizeDeps: { noDiscovery: true, include: ['element-plus', 'vue'] },
  server: { host: '127.0.0.1', port: 0, strictPort: false, fs: { allow: [repo, deps] } },
  plugins: [vue(), { name: 'ocr-synthetic-api', configureServer(server) {
    server.middlewares.use((request, response, next) => {
      if (!request.url?.startsWith('/rd/document-ocr/')) return next()
      const reply = (data) => { response.setHeader('Content-Type', 'application/json'); response.end(JSON.stringify({ code: 0, data })) }
      if (request.url === '/rd/document-ocr/status') return reply({ available: true, state: 'LOCAL_FILES_READY', maxBytes: 10485760, maxPages: 3 })
      assert.equal(request.url, '/rd/document-ocr/recognize')
      assert.equal(request.method, 'POST')
      assert.match(request.headers['content-type'], /^multipart\/form-data; boundary=/)
      let bytes = 0
      request.on('data', (chunk) => { bytes += chunk.length; assert.ok(bytes <= 11 * 1024 * 1024) })
      request.on('end', () => { recognizeCalls++; setTimeout(() => reply(result), delay) })
    })
  } }]
})
let browser
try {
  await server.listen()
  const port = server.httpServer.address().port
  browser = await chromium.launch({ headless: true, channel: 'msedge' })
  const page = await browser.newPage({ viewport: { width: 1440, height: 1000 } })
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  page.on('request', (request) => assert.ok(!request.url().includes(':48110'), 'Browser must never call OCR service'))
  await page.addInitScript(() => {
    const urls = new Set()
    const create = URL.createObjectURL.bind(URL), revoke = URL.revokeObjectURL.bind(URL)
    URL.createObjectURL = (blob) => { const url = create(blob); urls.add(url); return url }
    URL.revokeObjectURL = (url) => { urls.delete(url); revoke(url) }
    window.activeOcrObjectUrls = urls
  })
  await page.goto(`http://127.0.0.1:${port}`)
  await page.getByText('本地模型就绪', { exact: true }).waitFor()
  const png = fs.readFileSync(path.join(base, '.runtime/synthetic-pickup.png'))
  const input = page.getByLabel('选择本地单据')
  await input.setInputFiles({ name: 'synthetic-pickup.png', mimeType: 'image/png', buffer: png })
  await page.getByRole('button', { name: '识别', exact: true }).click()
  await page.getByLabel('订单 / 提货单号', { exact: true }).waitFor()
  await page.waitForFunction(() => document.querySelector('input[aria-label="订单 / 提货单号"]')?.value === '000042')
  await page.getByLabel('订单 / 提货单号', { exact: true }).fill('000043')
  const download = page.waitForEvent('download')
  await page.getByRole('button', { name: '导出草稿 JSON' }).click()
  const downloaded = await download
  const exported = JSON.parse(fs.readFileSync(await downloaded.path(), 'utf-8'))
  assert.equal(exported.fields.order.value, '000043')
  assert.equal(exported.officialDocumentCreated, false)
  await page.screenshot({ path: path.join(base, '.runtime/ui-desktop.png'), fullPage: true })
  await page.setViewportSize({ width: 390, height: 844 })
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'Mobile horizontal overflow')
  await page.screenshot({ path: path.join(base, '.runtime/ui-mobile.png'), fullPage: true })
  await page.evaluate(() => window.ocrHarness.changeAuth())
  assert.equal(await page.evaluate(() => window.activeOcrObjectUrls.size), 0)
  assert.equal(await page.getByLabel('订单 / 提货单号', { exact: true }).inputValue(), '')
  await input.setInputFiles({ name: 'bad.png', mimeType: 'image/png', buffer: Buffer.from('<svg onload="x"/>') })
  await page.getByRole('alert').filter({ hasText: '仅接受 PNG' }).waitFor()
  assert.equal(recognizeCalls, 1)
  await input.setInputFiles({ name: 'synthetic-pickup.png', mimeType: 'image/png', buffer: png })
  delay = 250
  const pendingRequest = page.waitForRequest((request) => request.url().endsWith('/recognize'))
  await page.getByRole('button', { name: '识别', exact: true }).click()
  await pendingRequest
  await page.evaluate(() => window.ocrHarness.changeAuth())
  await new Promise((resolve) => setTimeout(resolve, 500))
  assert.equal(await page.getByLabel('订单 / 提货单号', { exact: true }).inputValue(), '')
  assert.equal(await page.evaluate(() => window.activeOcrObjectUrls.size), 0)
  assert.deepEqual(errors, [])
  fs.writeFileSync(path.join(base, '.runtime/ui-verification.json'), JSON.stringify({ typeCheck: true, syntheticBackend: true,
    payload: fs.existsSync(captured) ? 'captured real Paddle synthetic result' : 'fixed synthetic result',
    desktop: '1440x1000', mobile: '390x844', upload: true, draftExport: true, authClear: true, lateResponseIgnored: true,
    objectUrlsRevoked: true, browserDirectServiceRequests: 0, pageErrors: errors }, null, 2))
  console.log('PASS scoped TypeScript + synthetic UI upload/export/auth/object-URL checks; not integrated backend evidence')
} finally { await browser?.close(); await server.close() }
