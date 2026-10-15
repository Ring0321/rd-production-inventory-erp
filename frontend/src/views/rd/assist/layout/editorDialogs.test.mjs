import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { evaluateSource, deferred } from '../../trace/package/testHelpers.mjs'

const source = readFileSync(new URL('./editorDialogs.ts', import.meta.url), 'utf8')
test('auth disposal closes owned prompt and blocks later dialogs without leaking reason', async () => {
  const pending = deferred()
  let closed = 0
  let reason = 'synthetic sensitive reason'
  const api = {
    prompt: () => pending.promise,
    confirm: () => pending.promise,
    close: () => {
      closed++
      reason = ''
      pending.reject('close')
    }
  }
  const { createEditorDialogs } = evaluateSource(source, { 'element-plus': { ElMessageBox: api } })
  const dialogs = createEditorDialogs()
  const work = dialogs.prompt('reason')
  const rejection = assert.rejects(work)
  dialogs.close()
  await rejection
  assert.equal(closed, 1)
  assert.equal(reason, '')
  await assert.rejects(() => dialogs.confirm('next'))
})

test('disposing idle editor never closes an unrelated global message box', async () => {
  let closed = 0
  const api = {
    confirm: async () => 'confirm',
    prompt: async () => ({ value: 'test' }),
    close: () => closed++
  }
  const { createEditorDialogs } = evaluateSource(source, { 'element-plus': { ElMessageBox: api } })
  const dialogs = createEditorDialogs()
  await dialogs.confirm('owned and resolved')
  dialogs.close()
  assert.equal(closed, 0)
})
