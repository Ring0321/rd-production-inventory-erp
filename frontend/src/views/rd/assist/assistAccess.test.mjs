import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { evaluateSource, plain } from '../trace/package/testHelpers.mjs'
const access = evaluateSource(readFileSync(new URL('./assistAccess.ts', import.meta.url), 'utf8'))
test('five tools are distinct and unrestricted driver grants cannot expose assistance', () => {
  assert.equal(access.assistTools.length, 5)
  assert.equal(access.canViewAssist(['rd_forklift_driver'], new Set(['*:*:*'])), false)
  assert.equal(access.canViewAssist([], new Set()), false)
  assert.equal(access.availableAssistTools(['super_admin'], new Set(['*:*:*'])).length, 5)
})
test('partial permissions expose only their original domain tool', () => {
  const tools = access.availableAssistTools([], new Set(['rd:package-unit:query']))
  assert.deepEqual(plain(tools.map((t) => t.key)), ['templates'])
  assert.equal(access.availableAssistTools([], new Set(['rd:stock-transaction:query'])).length, 0)
  assert.deepEqual(
    plain(
      access.availableAssistTools([], new Set(['rd:quality-inspection:create'])).map((t) => t.key)
    ),
    ['ocr']
  )
})
