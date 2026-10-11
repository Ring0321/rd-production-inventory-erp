import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import { parse } from 'vue/compiler-sfc'
import * as vue from 'vue'
import { evaluate } from '../warehouse/testHelpers.mjs'

const actual = (kind, name, context) => {
  const source = parse(readFileSync(new URL(`../${kind}/index.vue`, import.meta.url), 'utf8'))
    .descriptor.scriptSetup.content
  const parsed = ts.createSourceFile('driver.ts', source, ts.ScriptTarget.Latest, true)
  const names = (node) =>
    ts.isIdentifier(node) ? [node.text] : node.elements.flatMap((e) => names(e.name))
  const declaration = parsed.statements.find(
    (node) =>
      ts.isVariableStatement(node) &&
      node.declarationList.declarations.some((value) => names(value.name).includes(name))
  )
  assert.ok(declaration, `Actual ${kind} page must bind ${name}`)
  const code = ts.createPrinter().printNode(ts.EmitHint.Unspecified, declaration, parsed)
  const vm = { ...context, Error }
  runInNewContext(
    `${ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText}\naction=${name};`,
    vm
  )
  return vm.action
}
const setup = () => {
  const identity = vue.reactive({ id: 7, cachedId: 7, tenant: 1 })
  const calls = []
  let fail = false
  let permit = true
  let dispose, activate, deactivate
  let detail = async (id) => ({ task: { id, status: 'ASSIGNED' }, lines: [], exceptions: [] })
  const page = async (params) => {
    calls.push(['page', params.pageNo])
    if (fail) throw new Error('Disconnected')
    return { list: [{ id: 1, status: 'ASSIGNED' }], total: 45 }
  }
  const read = async (id) => {
    calls.push(['read', id])
    return detail(id)
  }
  const module = evaluate(
    '../driver/useDriverTasks.ts',
    {
      vue: {
        ...vue,
        onActivated: (callback) => {
          activate = callback
        },
        onDeactivated: (callback) => {
          deactivate = callback
        },
        onBeforeUnmount: (callback) => {
          dispose = callback
        }
      },
      '@/store/modules/user': { useUserStore: () => ({ getUser: identity }) },
      '@/utils/auth': {
        getAccessToken: () => true,
        getCurrentUserId: () => identity.cachedId,
        getTenantId: () => identity.tenant,
        getVisitTenantId: () => undefined
      },
      '@/directives/permission/hasPermi': { hasPermission: () => permit }
    },
    { Error }
  )
  const driver = module.useDriverTasks('driver:execute', page, read)
  driver.taskPageNo.value = 1
  const api = { getMy: read, getMyTask: read, getMyPage: page, getMyTaskPage: page }
  const context = {
    ...driver,
    driver,
    RdPutawayTaskApi: api,
    RdLoadingApi: api,
    loadTasks: driver.loadTasks
  }
  return {
    driver,
    calls,
    identity,
    context,
    failPage: () => {
      fail = true
    },
    healPage: () => {
      fail = false
    },
    forbid: () => {
      permit = false
    },
    dispose: () => dispose(),
    activate: () => activate(),
    deactivate: () => deactivate(),
    setRead: (value) => {
      detail = value
    }
  }
}

test('failed driver page reports an error and a healthy retry resumes the same page', async () => {
  const h = setup()
  await h.driver.loadTasks(true)
  h.failPage()
  await h.driver.loadMore()
  assert.equal(h.driver.taskPageNo.value, 1)
  assert.equal(h.driver.taskList.value.length, 1)
  assert.match(h.driver.readError.value, /Disconnected/)
  h.healPage()
  await h.driver.loadMore()
  assert.equal(h.driver.taskPageNo.value, 2)
  assert.equal(h.driver.readError.value, '')
  assert.deepEqual(h.calls, [
    ['page', 1],
    ['page', 2],
    ['page', 2]
  ])
})

test('wrong-task data is rejected and detail retry stays bound to the requested ID', async () => {
  const h = setup()
  h.setRead(async () => ({ task: { id: 99, status: 'ASSIGNED' } }))
  await h.driver.openTask(2)
  assert.equal(h.driver.taskDetail.value, undefined)
  assert.match(h.driver.readError.value, /不一致/)
  h.setRead(async (id) => ({ task: { id, status: 'ASSIGNED' } }))
  await h.driver.retryDetail()
  assert.equal(h.driver.taskDetail.value.task.id, 2)
  assert.deepEqual(h.calls, [
    ['read', 2],
    ['read', 2]
  ])
})

test('expired account, tenant, permission and lifecycle cannot apply late driver details', async () => {
  for (const change of [
    (h) => {
      h.identity.cachedId = 8
    },
    (h) => {
      h.identity.tenant = 2
    },
    (h) => h.forbid(),
    (h) => h.dispose(),
    (h) => h.deactivate()
  ]) {
    const h = setup()
    let release
    h.setRead(
      () =>
        new Promise((resolve) => {
          release = resolve
        })
    )
    const pending = h.driver.openTask(1)
    change(h)
    release({ task: { id: 1, status: 'ASSIGNED' } })
    await pending
    await h.driver.openTask(2)
    assert.equal(h.driver.taskDetail.value, undefined)
    assert.deepEqual(h.calls, [['read', 1]])
  }
})

test('going back cancels application of an in-flight driver task detail', async () => {
  const h = setup()
  let release
  h.setRead(
    () =>
      new Promise((resolve) => {
        release = resolve
      })
  )
  const pending = h.driver.openTask(1)
  await h.driver.backToList()
  release({ task: { id: 1, status: 'ASSIGNED' } })
  await pending
  assert.equal(h.driver.taskDetail.value, undefined)
  assert.equal(h.driver.targetId.value, undefined)
  assert.equal(h.driver.detailLoading.value, false)
})

test('invalid driver IDs and forbidden capabilities never start reads', async () => {
  const h = setup()
  for (const id of [0, -1, NaN, 1.1]) await h.driver.openTask(id)
  h.forbid()
  await h.driver.openTask(1)
  await h.driver.loadTasks()
  assert.deepEqual(h.calls, [])
})

for (const kind of ['putaway', 'loading']) {
  test(`${kind} actual page cannot skip a failed next page`, async () => {
    const h = setup()
    h.failPage()
    await actual(kind, 'loadMore', h.context)().catch(() => {})
    assert.equal(h.driver.taskPageNo.value, 1)
    assert.deepEqual(h.calls, [['page', 2]])
  })
  test(`${kind} actual page cannot apply an older detail over the new task`, async () => {
    const h = setup()
    let release
    h.setRead((id) =>
      id === 1
        ? new Promise((resolve) => {
            release = resolve
          })
        : Promise.resolve({ task: { id, status: 'ASSIGNED' }, lines: [], exceptions: [] })
    )
    const open = actual(kind, 'openTask', h.context)
    const first = open(1)
    await open(2)
    release({ task: { id: 1, status: 'ASSIGNED' }, lines: [], exceptions: [] })
    await first
    assert.equal(h.driver.taskDetail.value.task.id, 2)
  })
  test(`${kind} actual page checks the live account before issuing its own-task read`, async () => {
    const h = setup()
    h.identity.cachedId = 8
    await actual(kind, 'openTask', h.context)(1)
    assert.deepEqual(h.calls, [])
  })
}
