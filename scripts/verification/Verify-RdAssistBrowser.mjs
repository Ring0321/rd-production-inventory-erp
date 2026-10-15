import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createRequire } from 'node:module'
import { createHash } from 'node:crypto'
import { spawn } from 'node:child_process'
import { root, baseUrl, api, login } from './RdQrLocalFixture.mjs'
import { collectUtf8Process } from './RdUtf8ProcessText.mjs'

assert.equal(baseUrl, 'http://127.0.0.1:9000')
const settings = Object.fromEntries((await fs.readFile(path.join(root, 'temp/local-runtime-patent/.env.local'), 'utf8')).split(/\r?\n/).filter(l => l && !l.startsWith('#') && l.includes('=')).map(l => [l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]))
const admin = { username: 'admin', password: settings.RD_ADMIN_INITIAL_PASSWORD }
const token = await login(admin)
const fixture = JSON.parse(await fs.readFile(path.join(root, 'temp/qr-e4-fixture.json'), 'utf8'))
const contracts = JSON.parse(await fs.readFile(path.join(root, 'temp/reuse-verification/contracts-result.json'), 'utf8'))
const output = path.join(root, 'temp/reuse-verification/browser')
await fs.mkdir(output, { recursive: true })
const allPackages = async () => {
  const rows = []
  for (let pageNo = 1; pageNo <= 100; pageNo++) {
    const data = await api(`/rd/package-unit/page?pageNo=${pageNo}&pageSize=100`, token)
    rows.push(...data.list)
    if (rows.length >= data.total) { assert.equal(rows.length, data.total); return rows.sort((a,b) => a.id-b.id) }
  }
  throw new Error('Incomplete packages')
}
const fingerprint = async () => createHash('sha256').update(JSON.stringify({ packages: await allPackages(), locations: await api('/rd/putaway-task/location-map', token) })).digest('hex')
const before = await fingerprint()
const taskBags = await api('/rd/package-unit/task-packages?productionTaskId=90', token)
const bag = taskBags.find(b => b.status === 'IN_STOCK' && b.stockLotId === 161)
assert.ok(bag, 'Use existing synthetic already-labelled demo only')
const auditCount = async () => {
  const child = spawn(process.env.RD_MYSQL_BIN || 'mysql', ['--host=127.0.0.1','--port=3310','--user=root','--batch','--skip-column-names','--default-character-set=utf8mb4','rd_erp'], { env:{...process.env,MYSQL_PWD:settings.RD_DB_PASSWORD}, windowsHide:true })
  const capture = collectUtf8Process(child)
  child.stdin.end(`SELECT COUNT(*) FROM rd_label_print_log WHERE package_id=${bag.id} AND tenant_id=1 AND reprint_reason='本地开源复用集成验证，不发货';`)
  const result = await capture; assert.equal(result.code,0)
  const count = Number(result.stdout.trim()); assert.ok(Number.isSafeInteger(count)); return count
}
const initialAuditCount = await auditCount()
const require = createRequire(path.join(process.env.RD_BROWSER_MODULES || 'C:/Users/Ring/.cache/codex-runtimes/codex-primary-runtime/dependencies/node', 'package.json'))
const { chromium } = require('playwright')
const sharp = require('sharp')
const browser = await chromium.launch({ channel: 'msedge', headless: true, args: ['--use-angle=swiftshader','--enable-unsafe-swiftshader'] })
const errors = [], external = [], mutations = [], checks = [], screenshots = [], contexts = []
const skipLayout = process.argv.includes('--skip-layout')
const makePage = async (width = 1440) => {
  const context = await browser.newContext({ viewport: { width, height: width < 600 ? 1000 : 1080 }, acceptDownloads: true })
  contexts.push(context)
  await context.route('**/*', route => {
    const url = route.request().url()
    return /^(blob:|data:)/.test(url) || new URL(url).origin === baseUrl ? route.continue() : route.abort()
  })
  const page = await context.newPage()
  page.setDefaultTimeout(30000)
  page.on('pageerror', e => errors.push(e.message))
  page.on('console', m => { if (m.type() === 'error') errors.push(m.text()) })
  page.on('request', r => {
    if (!/^(blob:|data:)/.test(r.url()) && new URL(r.url()).origin !== baseUrl) external.push(new URL(r.url()).host)
    if (r.url().includes('/admin-api/rd/') && !['GET','HEAD'].includes(r.method())) mutations.push({ path: new URL(r.url()).pathname, method: r.method() })
  })
  return page
}
const logIn = async (page, account) => {
  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
  await page.getByPlaceholder('账号', { exact: true }).fill(account.username)
  await page.getByPlaceholder('密码', { exact: true }).fill(account.password)
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.waitForURL(u => !u.pathname.includes('/login'))
}
const shot = async (page, name) => {
  await page.mouse.move(0, 0)
  await page.screenshot({ path: path.join(output, `${name}.png`), animations: 'disabled', fullPage: true })
  screenshots.push(`${name}.png`)
  assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1), false, `${name}: document overflow`)
}
const select = async (page, name, option) => {
  await page.getByRole('combobox', { name, exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: option }).click()
}
const pixels = async locator => {
  const {data, info} = await sharp(await locator.screenshot()).removeAlpha().raw().toBuffer({resolveWithObject:true})
  let dark = 0, colored = 0
  for (let i=0; i<data.length; i+=info.channels) {
    if (data[i]<80 && data[i+1]<80 && data[i+2]<80) dark++
    if (Math.max(data[i],data[i+1],data[i+2])-Math.min(data[i],data[i+1],data[i+2])>15) colored++
  }
  assert.ok(dark > 100 || colored > 1000, 'Nonblank actual canvas pixels')
  return { dark, colored }
}
let page
try {
  page = await makePage()
  await logIn(page, admin)
  await page.goto(`${baseUrl}/operations-assist?tool=replay&taskId=90`)
  await page.locator('.vis-timeline').waitFor()
  await page.locator('.replay-space canvas').waitFor()
  await page.waitForTimeout(900)
  checks.push({ replayCanvas: await pixels(page.locator('.replay-space canvas')) })
  assert.match(await page.locator('.replay-stats').innerText(), /30/)
  const time = await page.locator('.replay-controls time').innerText()
  await page.getByRole('button', { name: '逐条回放流水', exact: true }).click()
  await page.waitForTimeout(1600)
  await page.getByRole('button', { name: '暂停回放', exact: true }).click()
  assert.notEqual(await page.locator('.replay-controls time').innerText(), time)
  await shot(page, 'replay-desktop')
  checks.push('actual persisted task timeline, playback and linked nonblank Three.js')

  if (!skipLayout) {
  await page.goto(`${baseUrl}/operations-assist?tool=layout&warehouseCode=${encodeURIComponent(contracts.layoutWarehouseCode)}`)
  const map = await api('/rd/putaway-task/location-map', token)
  const warehouse = map.find(l => l.warehouseCode === contracts.layoutWarehouseCode)
  await page.getByText(`${warehouse.warehouseName} (${warehouse.warehouseCode})`, {exact:true}).first().waitFor()
  await page.locator('.konvajs-content canvas').first().waitFor()
  await page.waitForTimeout(300)
  checks.push({ layoutCanvas: await pixels(page.locator('.konvajs-content canvas').first()) })
  await page.getByRole('button', { name: /区域 · 本地演示库区/ }).click()
  const x = page.getByRole('spinbutton', { name: '横坐标', exact: true })
  const oldX = Number(await x.inputValue())
  await x.fill(String(oldX + 10)); await x.press('Tab')
  await page.getByRole('button', { name: '保存草稿', exact: true }).click()
  await page.getByRole('button', { name: '发布', exact: true }).click()
  await page.locator('.el-message-box__btns .el-button--primary').click()
  await page.waitForTimeout(400)
  const stored = await api(`/rd/visual-document/get?kind=LAYOUT&scopeId=${encodeURIComponent(warehouse.warehouseCode)}`, token)
  assert.equal(stored.published.elements.find(e => e.id === 'reuse-verification-zone').x, oldX+10)
  await shot(page, 'layout-desktop')
  checks.push('actual layout editor saves and publishes versioned geometry only')
  }

  await page.goto(`${baseUrl}/operations-assist?tool=weight`)
  await page.getByRole('textbox', { name: '提货单号', exact: true }).fill('DEMO-REUSE-WEIGHT-20261015')
  await page.getByRole('button', { name: '查询', exact: true }).click()
  await page.getByRole('row').filter({hasText:'DEMO-REUSE-WEIGHT-20261015'}).click()
  await page.getByRole('spinbutton', {name:'目标净重 kg',exact:true}).fill('2000')
  await page.getByRole('spinbutton', {name:'目标净重 kg',exact:true}).press('Tab')
  const solve = page.waitForResponse(r => r.url().endsWith('/rd/weight-assistant/suggest'))
  await page.getByRole('button', {name:'计算建议',exact:true}).click()
  const suggestion = (await (await solve).json()).data
  assert.equal(suggestion.solverStatus, 'OPTIMAL'); assert.equal(suggestion.totalWeightKg, 2000)
  assert.equal(suggestion.bags.length, 2)
  assert.ok(suggestion.bags.every(b => taskBags.some(p => p.id===b.packageId && p.packageWeight===b.packageWeightKg)))
  await shot(page, 'weight-desktop')
  checks.push({ actualSolver: suggestion.solverStatus, selectedBags:suggestion.bags.length, weightKg:suggestion.totalWeightKg })

  await page.goto(`${baseUrl}/operations-assist?tool=ocr`)
  await page.getByText('本地模型就绪', {exact:true}).waitFor()
  await page.locator('input[type=file]').setInputFiles(path.join(root,'tools/document-ocr/.runtime/synthetic-pickup.png'))
  const recognize = page.waitForResponse(r => r.url().endsWith('/rd/document-ocr/recognize'), {timeout:85000})
  await page.getByRole('button', {name:'识别',exact:true}).click()
  const ocr = (await (await recognize).json()).data
  assert.equal(ocr.engine,'PaddleOCR'); assert.ok(ocr.rawLines.length>=5)
  assert.ok(ocr.candidates.order.candidates.some(c=>c.value==='000042'))
  await page.getByRole('textbox',{name:'客户',exact:true}).fill('本地人工确认草稿')
  const downloadOcr = page.waitForEvent('download')
  await page.getByRole('button',{name:'导出草稿 JSON',exact:true}).click()
  const draftFile = path.join(output,'ocr-draft.json'); await (await downloadOcr).saveAs(draftFile)
  const draft = JSON.parse(await fs.readFile(draftFile,'utf8'))
  assert.equal(draft.officialDocumentCreated,false)
  await shot(page,'ocr-desktop')
  checks.push('actual authenticated multipart -> Java proxy -> local Paddle CPU -> editable nonofficial draft')

  await page.goto(`${baseUrl}/operations-assist?tool=templates`)
  await page.getByRole('textbox',{name:'查询包件编号',exact:true}).fill(bag.packageCode)
  const originalLookup = page.waitForResponse(r=>r.url().includes('/rd/package-unit/trace?'))
  await page.getByRole('button',{name:'查询原包件',exact:true}).click()
  await originalLookup
  await page.waitForTimeout(150)
  await select(page,'预览包件',`${bag.packageCode} · ${bag.productModel}`)
  await page.locator('.pdfme-viewport').getByText(bag.packageCode,{exact:true}).waitFor()
  await page.getByText('草稿预览',{exact:true}).click()
  await page.getByRole('button',{name:'保存草稿',exact:true}).click()
  await page.getByRole('button',{name:'发布',exact:true}).click()
  await page.locator('.el-message-box__btns .el-button--primary').click()
  await page.getByText('已发布',{exact:true}).click()
  await page.getByRole('button',{name:'刷新包件预览',exact:true}).click()
  await page.locator('.pdfme-viewport').getByText(bag.packageCode,{exact:true}).waitFor()
  await page.waitForTimeout(400)
  checks.push({ labelPixels: await pixels(page.locator('.pdfme-viewport')) })
  const auditsBefore = mutations.filter(m=>m.path==='/admin-api/rd/package-unit/reprint').length
  await page.getByRole('button',{name:'登记补打并导出 PDF',exact:true}).click()
  await page.locator('.el-message-box textarea').fill('本地开源复用集成验证，不发货')
  const downloadPdf = page.waitForEvent('download',{timeout:60000})
  await page.locator('.el-message-box__btns .el-button--primary').click()
  await (await downloadPdf).saveAs(path.join(output,'actual-bag-label.pdf'))
  const same = page.waitForEvent('download')
  await page.getByRole('button',{name:'下载同一份 PDF',exact:true}).click()
  await (await same).saveAs(path.join(output,'actual-bag-label-again.pdf'))
  assert.equal(mutations.filter(m=>m.path==='/admin-api/rd/package-unit/reprint').length-auditsBefore,1)
  assert.equal(await auditCount(),initialAuditCount+1,'exactly one persisted original print audit')
  assert.deepEqual(await fs.readFile(path.join(output,'actual-bag-label.pdf')),await fs.readFile(path.join(output,'actual-bag-label-again.pdf')))
  await shot(page,'templates-desktop')
  checks.push('actual published template, unchanged QR, one original reprint audit and identical same-file redownload')

  for (const width of [390,320]) {
    await page.setViewportSize({width,height:1000})
    for (const tool of ['replay','layout','weight','ocr','templates'].filter(t=>!skipLayout || t!=='layout')) {
      await page.goto(`${baseUrl}/operations-assist?tool=${tool}&taskId=90${tool==='layout'?`&warehouseCode=${encodeURIComponent(contracts.layoutWarehouseCode)}`:''}`)
      await page.locator('.operations-assist').waitFor()
      await page.waitForTimeout(tool==='replay'?1400:700)
      if (tool==='layout') {
        await page.getByRole('button',{name:/区域 · 本地演示库区/}).waitFor()
        checks.push({width,layoutCanvas:await pixels(page.locator('.konvajs-content canvas').first())})
      }
      if (tool==='templates') {
        await page.getByRole('textbox',{name:'查询包件编号',exact:true}).fill(bag.packageCode)
        const lookup=page.waitForResponse(r=>r.url().includes('/rd/package-unit/trace?'))
        await page.getByRole('button',{name:'查询原包件',exact:true}).click(); await lookup
        await page.waitForTimeout(100)
        await select(page,'预览包件',`${bag.packageCode} · ${bag.productModel}`)
        await page.locator('.pdfme-viewport').getByText(bag.packageCode,{exact:true}).waitFor()
        checks.push({width,labelPixels:await pixels(page.locator('.pdfme-viewport'))})
      }
      await shot(page,`${tool}-${width}`)
      if(tool==='replay') checks.push({width,replayCanvas:await pixels(page.locator('.replay-space canvas'))})
    }
  }
  await page.setViewportSize({width:1440,height:1080})
  await page.goto(`${baseUrl}/warehouse-space?warehouseCode=${encodeURIComponent(contracts.layoutWarehouseCode)}&layout=published`)
  await page.locator('.warehouse-3d canvas').first().waitFor()
  await page.waitForTimeout(700)
  checks.push({published3DCanvas:await pixels(page.locator('.warehouse-3d canvas').first())})
  await shot(page,'published-warehouse-3d')
  const driver = await makePage(390); await logIn(driver,fixture.accounts.driver)
  await driver.goto(`${baseUrl}/operations-assist?tool=layout`)
  await driver.waitForTimeout(600)
  assert.equal(await driver.locator('.warehouse-editor,.label-studio,.weight-assistant,.warehouse-replay,.document-reader').count(),0)
  checks.push('driver cannot open assist tool data')
  assert.equal(await fingerprint(),before,'all original package and location fields preserved')
  const allowed = new Set(['/admin-api/rd/visual-document/save','/admin-api/rd/visual-document/publish','/admin-api/rd/weight-assistant/suggest','/admin-api/rd/document-ocr/recognize','/admin-api/rd/package-unit/reprint'])
  assert.deepEqual(mutations.filter(m=>!allowed.has(m.path)),[],'no stock/task/document mutation API')
  assert.deepEqual(external,[],'no external business, font, icon or OCR requests')
  assert.deepEqual(errors,[])
  const result = skipLayout ? 'PARTIAL' : 'PASS'
  await fs.writeFile(path.join(output,skipLayout?'partial-result.json':'result.json'),JSON.stringify({result,checks,screenshots,mutations,externalRequests:external.length,browserErrors:errors,unchangedBusinessFingerprint:before,packageId:bag.id,packageCode:bag.packageCode,originalQrCode:bag.qrCode},null,2))
  console.log(JSON.stringify({result,checkCount:checks.length,screenshots,externalRequests:external.length,browserErrors:errors.length}))
} catch(error) {
  if(page) { await page.screenshot({path:path.join(output,'failure.png'),fullPage:true}).catch(()=>{}); await fs.writeFile(path.join(output,'failure-visible.txt'),await page.locator('body').innerText().catch(()=>'')) }
  await fs.writeFile(path.join(output,'failure.json'),JSON.stringify({error:String(error),errors,external,mutations,checks},null,2))
  throw error
} finally {
  for(const c of contexts) await c.close()
  await browser.close()
}
