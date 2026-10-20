import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync, readdirSync } from 'node:fs'
import { createRequire } from 'node:module'
import { parse, compileTemplate } from 'vue/compiler-sfc'

const read = (path) => readFileSync(new URL(path, import.meta.url), 'utf8')

test('formatted assistant templates compile with the installed Vue compiler', () => {
  for (const filename of ['DuoxuAssistant.vue', 'AssistantReply.vue']) {
    const parsed = parse(read('./' + filename), { filename })
    assert.equal(parsed.errors.length, 0, String(parsed.errors))
    const compiled = compileTemplate({
      source: parsed.descriptor.template.content,
      filename,
      id: 'xiaoduo-test'
    })
    assert.equal(compiled.errors.length, 0, String(compiled.errors))
  }
})

test('assistant uses the locked original local Lucide icon and keeps source/license metadata', () => {
  const require = createRequire(import.meta.url)
  const original = require('@iconify/json/json/lucide.json')
  const subset = JSON.parse(read('../../plugins/svgIcon/xiaoduo-icons.json'))
  assert.equal(subset.icons.bot.body, original.icons.bot.body)
  assert.equal(subset.info.license.spdx, original.info.license.spdx)
  assert.match(read('../../plugins/svgIcon/index.ts'), /addCollection\(xiaoduoIcons\)/)
})

test('every literal Lucide icon used by ERP pages is locally registered with its original geometry', () => {
  const require = createRequire(import.meta.url)
  const original = require('@iconify/json/json/lucide.json')
  const subset = JSON.parse(read('../../plugins/svgIcon/xiaoduo-icons.json'))
  const scan = (directory) => {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
      const target = new URL(entry.name + (entry.isDirectory() ? '/' : ''), directory)
      if (entry.isDirectory()) scan(target)
      else if (/\.(vue|ts|tsx)$/.test(entry.name)) {
        for (const match of readFileSync(target, 'utf8').matchAll(/lucide:([a-z0-9-]+)/g)) {
          const name = match[1]
          assert.deepEqual(
            subset.icons[name] || subset.aliases?.[name],
            original.icons[name] || original.aliases?.[name]
          )
          assert.ok(
            subset.icons[name] || subset.aliases?.[name],
            `${name} must not request an external icon API`
          )
        }
      }
    }
  }
  scan(new URL('../../', import.meta.url))
  assert.match(read('../../../public/licenses/lucide-LICENSE.txt'), /ISC License/)
})

test('global placement is limited to ERP layout and keeps user content as plain text', () => {
  assert.match(read('../../layout/Layout.vue'), /<DuoxuAssistant\s*\/>/)
  assert.ok(!read('../../views/rd/pda/workspace/layout.vue').includes('DuoxuAssistant'))
  assert.ok(!read('../../views/rd/trace/package/index.vue').includes('DuoxuAssistant'))
  const ui = read('./DuoxuAssistant.vue') + read('./AssistantReply.vue')
  assert.ok(!/v-html|innerHTML|eval\(/.test(ui))
  assert.match(ui, /role="dialog"/)
  assert.match(ui, /role="log"/)
  assert.match(ui, /isComposing/)
  assert.match(ui, /maxlength="200"/)
  assert.match(ui, /本地知识 · ERP 实查/)
})

test('the ERP landing page does not mount an unauthorized inventory dashboard behind the assistant', () => {
  const home = read('../../views/Home/Index.vue')
  assert.match(home, /<RdInventoryDashboard v-if="canViewDashboard"/)
  assert.match(home, /!userStore\.getRoles\.includes\('rd_forklift_driver'\)/)
  assert.match(home, /checkPermi\(\['rd:dashboard:query'\]\)/)
})

test('assistant network adapter exposes only existing GET readers, never model ask or business mutations', () => {
  const adapter = read('./useDuoxuAssistant.ts')
  assert.match(adapter, /RdPackageAiApi\.getContext/)
  assert.ok(
    !/RdPackageAiApi\.(?:ask|getResult)|request\.post|request\.put|localStorage|sessionStorage|setItem/.test(
      adapter
    )
  )
  assert.match(adapter, /onAuthContextChange/)
  assert.match(adapter, /removeEventListener/)
})
