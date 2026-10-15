import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { homedir } from 'node:os'
import { fileURLToPath } from 'node:url'
import { createRequire } from 'node:module'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const folder = path.join(root, 'temp/reuse-verification/browser')
const facts = JSON.parse(await fs.readFile(path.join(folder, 'result.json'), 'utf8'))
const require = createRequire(path.join(root, 'frontend/package.json'))
const bundled = createRequire(path.join(process.env.RD_BROWSER_MODULES || path.join(homedir(), '.cache/codex-runtimes/codex-primary-runtime/dependencies/node'), 'package.json'))
const sharp = bundled('sharp')
const decoder = require('zxing-wasm/reader')
decoder.setZXingModuleOverrides({ wasmBinary: await fs.readFile(path.join(root, 'frontend/dist-prod/assets/zxing_reader-DUHi_9Vq.wasm')) })
const { data, info } = await sharp(path.join(folder, 'actual-bag-label-render.png')).ensureAlpha().raw().toBuffer({resolveWithObject:true})
const results = await decoder.readBarcodesFromImageData({ data: new Uint8ClampedArray(data), width: info.width, height: info.height }, { tryHarder: true })
assert.equal(results.length, 1)
assert.equal(results[0].text, facts.originalQrCode)
assert.equal(results[0].format, 'QRCode')
console.log(JSON.stringify({decoder:'local ZXing-WASM 1.1.3',decodedOriginalQr:results[0].text,externalRequests:0}))
