import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { test } from 'node:test'
import ts from 'typescript'
import { parse } from 'vue/compiler-sfc'
import { ref } from 'vue'

const source = parse(readFileSync(new URL('./index.vue', import.meta.url), 'utf8')).descriptor
  .scriptSetup.content
const parsed = ts.createSourceFile('loading.ts', source, ts.ScriptTarget.Latest, true)
const actual = (name, context) => {
  const statement = parsed.statements.find(
    (node) =>
      ts.isVariableStatement(node) &&
      node.declarationList.declarations.some((value) => value.name.getText(parsed) === name)
  )
  assert.ok(statement)
  const code = ts.createPrinter().printNode(ts.EmitHint.Unspecified, statement, parsed)
  const vm = { ...context, action: undefined, Error }
  runInNewContext(
    `${ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText}\naction=${name};`,
    vm
  )
  return vm.action
}
const detail = (id = 1) => ({
  task: { id, taskNo: `T-${id}`, status: 'PENDING_REVIEW', version: 3, scannedPackageCount: 2 },
  representativePackageCode: 'PKG-1'
})
const setup = (dispatch = true) => {
  const calls = []
  let current = true
  const context = {
    batchScanForm: { packageCode: 'PKG-1', taskId: 1 },
    batchScanResp: ref(detail()),
    route: { fullPath: '/rd/stock/loading?taskId=1' },
    batchScanLoading: ref(false),
    batchScanConfirming: ref(false),
    outboundConfirming: ref(false),
    batchSelectedLineIds: ref([]),
    batchPreviewRevision: 0,
    warehouseSession: {
      generation: () => 0,
      current: () => current,
      allowed: () => current && dispatch,
      assertCurrent: () => {
        if (!current) throw new Error('Expired')
      }
    },
    hasPermission: () => dispatch,
    message: { confirm: async () => true, success: () => {}, warning: () => {}, error: () => {} },
    RdLoadingApi: {
      previewBatchScan: async (data) => {
        calls.push(['preview', data])
        return detail()
      },
      confirmOutbound: async (data) => {
        calls.push(['outbound', data])
        return { taskId: data.taskId, message: 'Verified' }
      }
    },
    applyDefaultBatchSelection: async () => {},
    loadTasks: async () => {}
  }
  return {
    context,
    calls,
    expire: () => {
      current = false
    }
  }
}

test('Web query-only Enter preview cannot invoke the dispatch endpoint', async () => {
  const h = setup(false)
  await actual('previewBatchScan', h.context)()
  assert.equal(h.calls.length, 0)
})

test('Web final dialog cannot post another same-version task loaded during confirmation', async () => {
  const h = setup()
  let release, signal
  const shown = new Promise((resolve) => {
    signal = resolve
  })
  h.context.message.confirm = () =>
    new Promise((resolve) => {
      release = resolve
      signal()
    })
  const submitting = actual('confirmOutbound', h.context)()
  await shown
  h.context.batchScanResp.value = detail(2)
  release()
  await submitting
  assert.equal(
    h.calls.some(([name]) => name === 'outbound'),
    false
  )
})

test('Web final confirmation belongs to the original login and is not reusable after context expiry', async () => {
  const h = setup()
  let release, signal
  const shown = new Promise((resolve) => {
    signal = resolve
  })
  h.context.message.confirm = () =>
    new Promise((resolve) => {
      release = resolve
      signal()
    })
  const submitting = actual('confirmOutbound', h.context)()
  await shown
  h.expire()
  release()
  await submitting
  assert.equal(
    h.calls.some(([name]) => name === 'outbound'),
    false
  )
})
