import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import os from 'node:os'
import net from 'node:net'
import { spawn, execFile } from 'node:child_process'
import { promisify } from 'node:util'
import { createHash } from 'node:crypto'
import { root, api, prepareFixture } from './RdQrLocalFixture.mjs'

const binary = process.env.RD_NGINX_BIN
assert.ok(binary, 'Set RD_NGINX_BIN to an official Nginx 1.27 executable')
const execute = promisify(execFile)
const version = await execute(binary, ['-v'], { windowsHide: true })
assert.match(version.stderr, /nginx\/1\.27\./)
const port = 9006
const probe = net.createServer()
await new Promise((resolve, reject) => { probe.once('error', reject); probe.listen(port, '127.0.0.1', resolve) })
await new Promise((resolve) => probe.close(resolve))
const { fixture, tokens } = await prepareFixture()
const code = fixture.loadingPackages[0].packageCode
const source = `/rd/package-unit/trace?productionOutputId=${fixture.loadingOutputId}`
const before = await api(source, tokens.admin)
const prefix = await fs.mkdtemp(path.join(os.tmpdir(), 'rd-package-nginx-'))
await fs.mkdir(path.join(prefix, 'logs'))
await fs.mkdir(path.join(prefix, 'conf'))
await fs.mkdir(path.join(prefix, 'temp'))
await fs.cp(path.join(root, 'frontend/dist-prod'), path.join(prefix, 'html'), { recursive: true })
await fs.copyFile(path.join(path.dirname(binary), 'conf/mime.types'), path.join(prefix, 'conf/mime.types'))
const template = await fs.readFile(path.join(root, 'deploy/trial/nginx.conf'), 'utf8')
const server = template.replaceAll('${RD_PUBLIC_TENANT_ID}', '1')
  .replace('listen 80;', `listen 127.0.0.1:${port};`)
  .replaceAll('http://backend:48080', 'http://127.0.0.1:48080')
  .replace('root /usr/share/nginx/html;', 'root html;')
assert.ok(!server.includes('${'), 'Only the trusted tenant placeholder may be substituted')
const configuration = `worker_processes 1;\npid logs/nginx.pid;\nerror_log logs/error.log;\nevents { worker_connections 256; }\nhttp { include mime.types; ${server} }\n`
const config = path.join(prefix, 'conf/verification.conf')
await fs.writeFile(config, configuration)
const args = ['-p', prefix.replaceAll('\\', '/') + '/', '-c', config.replaceAll('\\', '/')]
await execute(binary, [...args, '-t'], { windowsHide: true })
const child = spawn(binary, args, { windowsHide: true, stdio: ['ignore', 'ignore', 'pipe'] })
child.on('error', (e) => console.error(e.message))
const base = `http://127.0.0.1:${port}`
const identity = `${base}/pkg/${code}`
const results = []
const get = async (url, headers = {}, method = 'GET') => {
  const response = await fetch(url, { headers, method, redirect: 'manual', signal: AbortSignal.timeout(20000) })
  const text = await response.text()
  results.push({ status: response.status, type: response.headers.get('content-type') })
  return { response, text }
}
try {
  for (let attempt = 0; ; attempt++) {
    try { await fetch(base, { signal: AbortSignal.timeout(1000) }); break } catch (e) {
      if (attempt >= 30) throw e
      await new Promise((r) => setTimeout(r, 200))
    }
  }
  for (const suffix of ['', '?resource=web', '?workMode=pda&internal=1', '?resource=web&workMode=pda']) {
    const html = await get(identity + suffix, { Accept: 'text/html' })
    assert.equal(html.response.status, 200)
    assert.ok(html.response.headers.get('content-type').includes('text/html'))
    assert.ok(html.text.includes('垛序 ERP'))
    assert.equal(html.response.headers.get('location'), null)
  }
  const snapshot = await get(identity, { Accept: 'application/json' })
  assert.equal(snapshot.response.status, 200, 'anonymous request requires the deployment tenant binding')
  const passport = JSON.parse(snapshot.text)
  assert.equal(createHash('sha256').update(passport.canonicalJson, 'utf8').digest('hex'), passport.digest)
  assert.equal(JSON.parse(passport.canonicalJson)['@id'], before.find((p) => p.packageCode === code).qrCode)
  assert.equal((await get(identity, { Accept: 'application/json', 'If-None-Match': snapshot.response.headers.get('etag') })).response.status, 304)
  const document = await get(identity, { Accept: 'application/ld+json', 'If-None-Match': snapshot.response.headers.get('etag') })
  assert.equal(document.response.status, 200, 'different representations must not share an ETag')
  assert.notEqual(document.response.headers.get('etag'), snapshot.response.headers.get('etag'))
  const directory = await get(identity + '?resource=links', { Accept: 'text/html' })
  assert.equal(directory.response.status, 200)
  assert.ok(directory.response.headers.get('content-type').includes('application/linkset+json'))
  const forgedTenant = await get(`${base}/admin-api/rd/package-link/${code}/passport`, { 'tenant-id': '999999', Authorization: 'Bearer invalid-fixture', Cookie: 'ignored=fixture' })
  assert.equal(forgedTenant.response.status, 200)
  assert.equal(JSON.parse(forgedTenant.text).digest, passport.digest)
  const alias = await get(`${base}/admin-api/rd/package-link/${code}%20/passport`, { 'tenant-id': '999999', 'visit-tenant-id': '999999', Authorization: 'Bearer invalid-fixture' })
  assert.equal(alias.response.status, 200, 'the entire public prefix, including normalized aliases, must use fixed tenancy')
  assert.equal(JSON.parse(alias.text).digest, passport.digest)
  for (const tokenName of ['token', '%74oken', 'tok%65n']) {
    assert.equal((await get(identity + `?resource=passport&${tokenName}=${encodeURIComponent(tokens.admin)}`, { 'visit-tenant-id': '999999' })).response.status, 400, 'query authentication must be rejected before security interceptors')
  }
  for (const suffix of ['?resource=web&resource=passport', '?resource=web&%72esource=passport', '?%72esource=web&resource=document', '?resource=private']) {
    assert.equal((await get(identity + suffix, { Accept: 'text/html' })).response.status, 400)
  }
  assert.equal((await get(identity, { Accept: 'image/png' })).response.status, 406)
  const head = await get(identity, { Accept: 'application/json' }, 'HEAD')
  assert.equal(head.response.status, 200); assert.equal(head.text, '')
  const prefixed = await get(`${base}/factory_1/site-2/pkg/${code}?resource=passport`)
  assert.equal(prefixed.response.status, 200)
  assert.equal(JSON.parse(prefixed.text).digest, passport.digest)
  assert.equal((await get(base + '/_rd-package-web')).response.status, 404)
  assert.equal((await get(`${base}/admin-api/rd/package-link/NO_SUCH_PACKAGE/passport`)).response.status, 404)
  const privateApi = await get(base + '/admin-api/rd/package-unit/page?pageNo=1&pageSize=1', { 'tenant-id': '1' })
  assert.notEqual(JSON.parse(privateApi.text).code, 0, 'public gateway must not grant private API access')
  assert.deepEqual(await api(source, tokens.admin), before)
  const output = path.join(root, 'temp/package-link-verification')
  await fs.mkdir(output, { recursive: true })
  const report = { runtime: version.stderr.trim(), scope: 'Official native Nginx, same template; not Docker/HTTPS/field acceptance', httpChecks: results.length, unchangedPackageRecords: true, results }
  await fs.writeFile(path.join(output, 'nginx-result.json'), JSON.stringify(report, null, 2))
  console.log(JSON.stringify({ runtime: report.runtime, httpChecks: results.length, unchangedPackageRecords: true }))
} finally {
  await execute(binary, [...args, '-s', 'quit'], { windowsHide: true })
  await new Promise((resolve) => {
    if (child.exitCode !== null) return resolve()
    child.once('exit', resolve)
  })
}
