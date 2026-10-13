import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const original = require('@iconify/json/json/lucide.json')
const subset = JSON.parse(readFileSync(new URL('./warehouse-icons.json', import.meta.url), 'utf8'))

test('warehouse map icons are an unmodified local subset of the locked icon collection', () => {
  assert.equal(subset.prefix, original.prefix)
  assert.equal(subset.width, original.width)
  assert.equal(subset.height, original.height)
  assert.deepEqual(subset.info, original.info)
  for (const [name, icon] of Object.entries(subset.icons))
    assert.deepEqual(icon, original.icons[name])
  const files = ['RdSpatialLocationMap.vue', 'LocationDetailsPanel.vue'].map((name) =>
    readFileSync(new URL(`../../views/rd/stock/location-map/${name}`, import.meta.url), 'utf8')
  )
  for (const file of files) {
    for (const [, name] of file.matchAll(/icon="lucide:([\w-]+)"/g))
      assert.ok(subset.icons[name], name)
  }
  const license = readFileSync(
    new URL('../../../public/licenses/lucide-LICENSE.txt', import.meta.url),
    'utf8'
  )
  assert.match(license, /ISC License/)
  assert.match(license, /Cole Bemis/)
  const localFa = require('@iconify/json/json/fa.json')
  for (const [file, names] of [
    ['layout/components/Screenfull/src/Screenfull.vue', ['expand', 'compress']],
    ['layout/components/LocaleDropdown/src/LocaleDropdown.vue', ['language']],
    ['layout/components/SizeDropdown/src/SizeDropdown.vue', ['text-height']],
    ['components/Dialog/src/Dialog.vue', ['expand', 'compress']],
    ['layout/components/ThemeSwitch/src/ThemeSwitch.vue', ['sun-o', 'moon-o']]
  ]) {
    const source = readFileSync(new URL(`../../${file}`, import.meta.url), 'utf8')
    for (const name of names) {
      assert.ok(localFa.icons[name])
      assert.ok(source.includes(`fa:${name}`))
    }
  }
})
