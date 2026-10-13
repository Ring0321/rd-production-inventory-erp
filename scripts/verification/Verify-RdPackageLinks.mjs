import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { createHash } from 'node:crypto'
import http from 'node:http'
import { createRequire } from 'node:module'
import { root, baseUrl, api, prepareFixture } from './RdQrLocalFixture.mjs'
const { fixture, tokens, accounts } = await prepareFixture()
const code = fixture.loadingPackages[0].packageCode
const memberUrl = `/rd/package-unit/trace?productionOutputId=${fixture.loadingOutputId}`
const before = await api(memberUrl, tokens.admin)
const original = before.find((p) => p.packageCode === code).qrCode
const identityUrl = `${baseUrl}/pkg/${code}`
const reads = []
const get = async (url, headers = {}) => {
  const response = await fetch(url, { headers, cache: 'no-store' })
  const text = await response.text()
  reads.push({ status: response.status, type: response.headers.get('content-type') })
  return { response, text }
}
const html = await get(identityUrl, { Accept: 'text/html' })
assert.ok(html.text.includes('垛序 ERP'))
let snapshot
let snapshotTag
for (const [type, resource] of [['application/json','passport'], ['application/ld+json','document'], ['application/linkset+json','links']]) {
  const result = await get(identityUrl, { Accept: type })
  assert.equal(result.response.status, 200)
  assert.ok(result.response.headers.get('content-type').includes(type))
  const body = JSON.parse(result.text)
  if (resource === 'passport') {
    snapshot = body
    snapshotTag = result.response.headers.get('etag')
    assert.equal(body.schema, 'DUOXU_PUBLIC_PACKAGE_V1')
    assert.equal(createHash('sha256').update(body.canonicalJson,'utf8').digest('hex'), body.digest)
    const d = JSON.parse(body.canonicalJson)
    assert.equal(d['@id'], original)
    assert.equal(d.packageCode, code)
    for (const forbidden of ['customerName','locationCode','productionOutputId','qualityInspectionId','operatorName','remark','detail']) assert.ok(!body.canonicalJson.includes(`"${forbidden}"`))
    const again = await get(identityUrl, { Accept: type, 'If-None-Match': result.response.headers.get('etag') })
    assert.equal(again.response.status, 304); assert.equal(again.text, '')
  } else if (resource === 'document') {
    assert.equal(body['@id'], original)
    assert.equal((await get(identityUrl, { Accept: type, 'If-None-Match': snapshotTag })).response.status, 200)
  }
  else { assert.equal(body.linkset[0].anchor, original); assert.equal(body.linkset[0].alternate.length, 2) }
  const explicit = await get(identityUrl + '?resource=' + resource, { Accept: 'text/html' })
  assert.equal(explicit.response.status, 200)
}
assert.equal((await get(identityUrl+'?resource=private')).response.status, 400)
assert.equal((await get(identityUrl, { Accept:'image/png' })).response.status, 406)
assert.equal((await get(identityUrl+'?resource=web&resource=passport')).response.status, 400)
assert.equal((await get(identityUrl+'?resource=web&%72esource=passport')).response.status, 400)
const alias = await get(`${baseUrl}/admin-api/rd/package-link/${code}%20/passport`, { 'tenant-id':'999999', 'visit-tenant-id':'999999', Authorization:'Bearer invalid-fixture' })
assert.equal(alias.response.status, 200)
assert.equal(JSON.parse(alias.text).digest, snapshot.digest)
const absoluteForm = await new Promise((resolve, reject) => {
  const local = new URL(baseUrl)
  const request = http.request({ hostname: local.hostname, port: local.port, path:`http://127.0.0.1:1/admin-api/rd/package-link/${code}/passport`, headers:{Accept:'application/json'} }, (response) => {
    let text=''; response.setEncoding('utf8'); response.on('data',(chunk)=>{text+=chunk}); response.on('end',()=>resolve({status:response.statusCode,text}))
  }); request.on('error',reject); request.end()
})
assert.equal(absoluteForm.status,200,'absolute request targets must not change the configured backend')
assert.equal(JSON.parse(absoluteForm.text).digest,snapshot.digest)
reads.push({status:absoluteForm.status,type:'application/json',configuredBackendOnly:true})
for (const tokenName of ['token', '%74oken', 'tok%65n']) {
  assert.equal((await get(identityUrl+`?resource=passport&${tokenName}=${encodeURIComponent(tokens.admin)}`, { 'visit-tenant-id':'999999' })).response.status, 400)
}
const head = await fetch(identityUrl, { method:'HEAD', headers:{Accept:'application/linkset+json'} })
assert.equal(head.status,200); assert.equal(await head.text(),'')
assert.deepEqual(await api(memberUrl,tokens.admin),before)

const requireFrontend=createRequire(path.join(root,'frontend/package.json'))
const {chromium}=process.env.RD_BROWSER_MODULES ? requireFrontend(path.join(process.env.RD_BROWSER_MODULES,'playwright')) : requireFrontend('playwright')
const browser=await chromium.launch({headless:true,channel:process.env.RD_BROWSER_CHANNEL})
const out=path.join(root,'temp/package-link-verification'); await fs.mkdir(out,{recursive:true})
const errors=[], screens=[]
try {
  for (const role of ['public','warehouse']) {
    const context=await browser.newContext({viewport:{width:1440,height:1000},acceptDownloads:true})
    const page=await context.newPage(); page.on('pageerror',(e)=>errors.push(e.message))
    let writes=0; page.on('request',(req)=>{ if(req.method()!=='GET'&&req.method()!=='HEAD'&& !req.url().includes('/system/auth/') && !req.url().includes('/system/tenant/')) writes++ })
    if(role!=='public') {
      await page.goto(`${baseUrl}/login`,{waitUntil:'networkidle'})
      await page.getByPlaceholder('账号',{exact:true}).fill(accounts[role].username)
      await page.getByPlaceholder('密码',{exact:true}).fill(accounts[role].password)
      await page.getByRole('button',{name:'登录',exact:true}).click(); await page.waitForURL((u)=>!u.pathname.includes('/login'))
    }
    await page.goto(identityUrl,{waitUntil:'networkidle'})
    await page.getByRole('heading',{name:'二维码资源与公开护照',exact:true}).waitFor()
    await page.getByRole('button',{name:'下载公开护照',exact:true}).waitFor()
    const downloadPromise=page.waitForEvent('download')
    await page.getByRole('button',{name:'下载公开护照',exact:true}).click()
    const download=await downloadPromise
    const saved=path.join(out,`${role}-passport.json`); await download.saveAs(saved)
    const exported=JSON.parse(await fs.readFile(saved,'utf8'))
    assert.equal(exported.digest,snapshot.digest)
    await page.getByLabel('选择本机护照文件',{exact:true}).setInputFiles(saved)
    await page.getByText('文件内容摘要一致，尚未核对服务器当前记录；这不是来源签名或防伪结论',{exact:true}).waitFor()
    await page.getByRole('button',{name:'核对服务器当前记录',exact:true}).click()
    await page.getByText('文件公开内容与服务器当前记录一致；仍不证明实物真伪或签名',{exact:true}).waitFor()
    for(const width of [1440,390,320]) {
      await page.setViewportSize({width,height:width<500?844:1000})
      await page.locator('.passport-section').scrollIntoViewIfNeeded()
      const fit=await page.locator('.passport-section').evaluate((el)=>({scroll:el.scrollWidth,client:el.clientWidth}))
      assert.ok(fit.scroll<=fit.client+1)
      const name=`${role}-${width}.png`; await page.screenshot({path:path.join(out,name),animations:'disabled'}); screens.push(name)
      await page.locator('.passport-section').screenshot({path:path.join(out,`${role}-${width}-panel.png`),animations:'disabled'})
    }
    const modified={...exported,canonicalJson:exported.canonicalJson.replace('"PackagePassport"','"WRONG_PROFILE_TYPE"')}
    await page.getByLabel('选择本机护照文件',{exact:true}).setInputFiles({name:'modified.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(modified))})
    await page.getByText('护照身份或公开范围无效',{exact:true}).waitFor()
    const rehashed={...exported}; const d=JSON.parse(rehashed.canonicalJson); d.product.weightKg='999.123'
    rehashed.canonicalJson=JSON.stringify(d); rehashed.digest=createHash('sha256').update(rehashed.canonicalJson).digest('hex')
    await page.getByLabel('选择本机护照文件',{exact:true}).setInputFiles({name:'rehashed.json',mimeType:'application/json',buffer:Buffer.from(JSON.stringify(rehashed))})
    await page.getByText('文件内容摘要一致，尚未核对服务器当前记录；这不是来源签名或防伪结论',{exact:true}).waitFor()
    await page.getByRole('button',{name:'核对服务器当前记录',exact:true}).click()
    await page.getByText('文件与服务器当前公开记录不同，可能记录已更新或文件内容变化，请核对来源',{exact:true}).waitFor()
    await page.route('**/admin-api/rd/package-link/**/passport', (route) => route.abort('connectionfailed'))
    await page.getByRole('button',{name:'核对服务器当前记录',exact:true}).click()
    await page.getByText('服务器未核实，仅保留本机文件摘要结果，不作一致性结论',{exact:true}).waitFor()
    await page.unroute('**/admin-api/rd/package-link/**/passport')
    await page.getByRole('button',{name:'刷新公开护照',exact:true}).click()
    await page.getByRole('button',{name:'下载公开护照',exact:true}).waitFor()
    assert.equal(await page.getByRole('button',{name:'核对服务器当前记录',exact:true}).isDisabled(), true)
    assert.equal(writes,0,'resource usage must not upload a file or write business data')
    await context.close()
  }
  assert.deepEqual(errors,[])
  assert.deepEqual(await api(memberUrl,tokens.admin),before)
  await fs.writeFile(path.join(out,'result.json'),JSON.stringify({marker:'本地链接/公开护照验证，非真实GS1或签名/现场认证',code,httpChecks:reads.length,screens,errors,unchangedPackageRecords:true},null,2))
  console.log(JSON.stringify({httpChecks:reads.length,screens:screens.length,pageErrors:errors.length,unchangedPackageRecords:true}))
} finally { await browser.close() }
