import http from 'node:http'
import https from 'node:https'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// Loopback-only verification preview; business writes are intentionally unavailable.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../frontend/dist-prod')
const origin = 'https://101.42.182.117'
const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.woff2': 'font/woff2', '.wasm': 'application/wasm' }
const server = http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://127.0.0.1:9001')
    if (url.pathname.startsWith('/admin-api/') || url.pathname === '/demo-login.json') {
      const login = request.method === 'POST' && ['/admin-api/system/auth/login', '/admin-api/system/auth/logout', '/admin-api/system/auth/refresh-token', '/admin-api/system/captcha/get', '/admin-api/system/captcha/check'].includes(url.pathname)
      if (!['GET', 'HEAD'].includes(request.method) && !login) { response.writeHead(405); response.end(); return }
      const headers = { ...request.headers, host: '101.42.182.117' }
      delete headers.origin
      delete headers.referer
      const proxy = https.request(origin + url.pathname + url.search, { method: request.method, headers, timeout: 30000 }, (remote) => {
        response.writeHead(remote.statusCode, { ...remote.headers, 'cache-control': 'no-store' })
        remote.pipe(response)
      })
      proxy.on('timeout', () => proxy.destroy())
      proxy.on('error', () => { if (!response.headersSent) response.writeHead(502); response.end('ERP preview upstream unavailable') })
      request.pipe(proxy)
      return
    }
    if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405); response.end(); return }
    const pathname = decodeURIComponent(url.pathname)
    let target = path.resolve(root, '.' + pathname)
    if (!target.startsWith(root + path.sep) && target !== root) { response.writeHead(404); response.end(); return }
    let stat = await fs.stat(target).catch(() => undefined)
    if (!stat?.isFile()) {
      if (path.extname(pathname)) { response.writeHead(404); response.end(); return }
      target = path.join(root, 'index.html')
    }
    response.writeHead(200, { 'content-type': types[path.extname(target)] || 'application/octet-stream', 'cache-control': 'no-store' })
    response.end(request.method === 'HEAD' ? undefined : await fs.readFile(target))
  } catch { if (!response.headersSent) response.writeHead(500); response.end('Preview failed') }
})
server.listen(9001, '127.0.0.1', () => console.log('ERP assistant verification preview: http://127.0.0.1:9001'))
