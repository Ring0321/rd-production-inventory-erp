import assert from 'node:assert/strict'
import fs from 'node:fs'
import { test } from 'node:test'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const original = require('@iconify/json/json/lucide.json')
const icons = JSON.parse(
  fs.readFileSync(new URL('./qr-workbench-icons.json', import.meta.url), 'utf8')
)

test('QR workbench icons are registered locally and retain the original Lucide shapes and license', () => {
  const setup = fs.readFileSync(new URL('./index.ts', import.meta.url), 'utf8')
  assert.ok(setup.includes("import qrWorkbenchIcons from './qr-workbench-icons.json'"))
  assert.ok(setup.includes('addCollection(qrWorkbenchIcons)'))
  assert.equal(icons.prefix, 'lucide')
  assert.deepEqual(icons.info.license, original.info.license)
  for (const [name, shape] of Object.entries(icons.icons))
    assert.deepEqual(shape, original.icons[name])
})

test('all scanner and relationship button icons are included before any offline UI opens', () => {
  const required = new Set()
  for (const source of [
    '../../views/rd/stock/scan/ScanWorkbench.vue',
    '../../views/rd/stock/scan/PendingScanBoxOverview.vue',
    '../../views/rd/trace/package/PackageRelationsPanel.vue'
  ]) {
    const text = fs.readFileSync(new URL(source, import.meta.url), 'utf8')
    for (const match of text.matchAll(/lucide:([a-z0-9-]+)/g)) required.add(match[1])
  }
  for (const name of required) assert.ok(icons.icons[name], `Missing local icon ${name}`)
})
