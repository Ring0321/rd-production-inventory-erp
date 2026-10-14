import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import { test } from 'node:test'
import ts from 'typescript'
import { parse } from 'vue/compiler-sfc'
import { ref } from 'vue'
import { evaluate, plain } from '../../pda/warehouse/testHelpers.mjs'

const replacementHelpers = evaluate(
  '../../stock/loading/replacements.ts',
  {
    '@/utils/rdDigitalLink': { extractPackageCode: (value) => value.trim().split('/pkg/').at(-1) }
  },
  { Error }
)

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
  let lifetime = 0
  const context = {
    batchScanForm: { packageCode: 'PKG-1', taskId: 1 },
    batchScanResp: ref(detail()),
    route: { fullPath: '/rd/stock/loading?taskId=1' },
    batchScanLoading: ref(false),
    batchScanConfirming: ref(false),
    batchGuard: { locked: ref(false) },
    outboundConfirming: ref(false),
    batchSelectedLineIds: ref([]),
    batchPreviewRevision: 0,
    warehouseSession: {
      generation: () => lifetime,
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
    advanceLifecycle: () => {
      lifetime++
    },
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
test('Web pending guard blocks ordinary preview and final outbound without dropping the original request', async () => {
  const h = setup()
  h.context.batchGuard.locked.value = true
  await actual('previewBatchScan', h.context)()
  await actual('confirmOutbound', h.context)()
  assert.equal(h.calls.length, 0)
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

const resolutionSetup = () => {
  const h = setup()
  let current = {
    task: { id: 1, taskNo: 'LOAD-1', version: 4, status: 'EXCEPTION_PENDING' },
    lines: [21, 22].map((id) => ({
      id,
      packageCode: `OLD-${id}`,
      lineStatus: 'PLANNED',
      customerName: '验证客户',
      productModel: 'A',
      furnaceNo: '1-01',
      furnaceLayer: '上层',
      packageWeight: 1000
    })),
    exceptions: [{ id: 8, exceptionStatus: 'OPEN' }]
  }
  Object.assign(h.context, replacementHelpers, {
    taskDetail: ref(plain(current)),
    taskVisible: ref(true),
    exceptionResolveVisible: ref(true),
    resolutionTarget: ref({ taskId: 1, version: 4 }),
    resolutionNeedsCheck: ref(false),
    exceptionResolving: ref(false),
    replacementChecking: ref(false),
    replacementPreview: ref([]),
    replacementRows: ref([
      { key: 1, taskLineId: 21, newPackageCode: 'https://id.tjldsic.com/pkg/NEW-1' },
      { key: 2, taskLineId: 22, newPackageCode: 'NEW-2' }
    ]),
    exceptionResolveForm: { resolutionAction: 'REPLACE_PACKAGE', handlingResult: '原核定说明' },
    ElMessageBox: { confirm: async () => true }
  })
  Object.assign(h.context.RdLoadingApi, {
    getTask: async () => plain(current),
    getAvailablePackagePage: async (query) => ({
      list: [
        {
          id: 101,
          packageCode: query.keyword,
          customerName: query.customerName,
          productModel: query.productModel,
          furnaceNo: query.furnaceNo,
          furnaceLayer: query.furnaceLayer,
          packageWeight: 1000,
          stockLotId: 1,
          locationId: 1,
          locationCode: 'A-01',
          status: 'IN_STOCK'
        }
      ]
    }),
    resolveException: async (body) => {
      h.calls.push(['resolve', plain(body)])
      current.task.version++
      current.task.status = 'ASSIGNED'
      current.exceptions[0].exceptionStatus = 'RESOLVED'
      for (const entry of body.replacements)
        current.lines.find((line) => line.id === entry.taskLineId).packageCode =
          entry.newPackageCode
      return true
    }
  })
  h.context.message.success = () => h.calls.push(['success'])
  h.context.message.warning = (value) => h.calls.push(['warning', value])
  return {
    ...h,
    setCurrent: (value) => {
      current = value
    }
  }
}

test('leaving and returning to the cached Web page cannot revive an old replacement confirmation', async () => {
  const h = resolutionSetup()
  h.context.ElMessageBox.confirm = async () => {
    h.advanceLifecycle()
    return true
  }
  await actual('resolveLoadingException', h.context)()
  assert.equal(
    h.calls.some(([name]) => name === 'resolve'),
    false
  )
  assert.equal(
    h.calls.some(([name]) => name === 'success'),
    false
  )
})

test('Web sends all replacement pairs once and verifies every actual member', async () => {
  const h = resolutionSetup()
  await actual('resolveLoadingException', h.context)()
  assert.equal(h.calls.filter(([name]) => name === 'resolve').length, 1)
  assert.equal(h.calls.find(([name]) => name === 'resolve')[1].replacements.length, 2)
  assert.equal(h.context.taskDetail.value.lines[1].packageCode, 'NEW-2')
  assert.equal(
    h.calls.some(([name]) => name === 'success'),
    true
  )
})

test('Web cannot submit a bulk plan for a newly selected same-version task', async () => {
  const h = resolutionSetup()
  h.context.ElMessageBox.confirm = async () => {
    h.context.taskDetail.value.task.id = 2
    return true
  }
  await actual('resolveLoadingException', h.context)()
  assert.equal(
    h.calls.some(([name]) => name === 'resolve'),
    false
  )
})

test('Web confirmation keeps the original replacement array and handling reason', async () => {
  const h = resolutionSetup()
  h.context.ElMessageBox.confirm = async () => {
    h.context.replacementRows.value[1].newPackageCode = 'LATER'
    h.context.exceptionResolveForm.handlingResult = '迟到说明'
    return true
  }
  await actual('resolveLoadingException', h.context)()
  const sent = h.calls.find(([name]) => name === 'resolve')[1]
  assert.equal(sent.replacements[1].newPackageCode, 'NEW-2')
  assert.equal(sent.handlingResult, '原核定说明')
})

test('Web aborted dialog or expired identity never sends a replacement command', async () => {
  for (const mode of ['cancel', 'expired']) {
    const h = resolutionSetup()
    h.context.ElMessageBox.confirm = async () => {
      if (mode === 'cancel') throw 'cancel'
      h.expire()
      return true
    }
    await actual('resolveLoadingException', h.context)()
    assert.equal(
      h.calls.some(([name]) => name === 'resolve'),
      false
    )
  }
})

test('Web false or unverified replies remain awaiting checks and never automatically retry', async () => {
  for (const reply of [false, true]) {
    const h = resolutionSetup()
    h.context.RdLoadingApi.resolveException = async (body) => {
      h.calls.push(['resolve', body])
      return reply
    }
    const action = actual('resolveLoadingException', h.context)
    await action()
    await action()
    assert.equal(h.calls.filter(([name]) => name === 'resolve').length, 1)
    assert.equal(h.context.resolutionNeedsCheck.value, true)
    assert.equal(
      h.calls.some(([name]) => name === 'success'),
      false
    )
  }
})
