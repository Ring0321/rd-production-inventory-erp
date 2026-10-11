import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import * as vue from 'vue'

const plain = (value) => JSON.parse(JSON.stringify(value))
const evaluate = (source, mocks, globals = {}) => {
  const module = { exports: {} }
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  runInNewContext(output, {
    module,
    exports: module.exports,
    require: (name) => {
      assert.ok(name in mocks, `Unexpected dependency ${name}`)
      return mocks[name]
    },
    ...globals
  })
  return module.exports
}
const draft = evaluate(readFileSync(new URL('./draft.ts', import.meta.url), 'utf8'), {})
const bag = (id, code = `PKG-${id}`) => ({
  id,
  packageCode: code,
  productionOutputId: 12,
  customerName: 'Test',
  productModel: 'A',
  furnaceNo: 'F-1',
  furnaceLayer: '上层',
  packageWeight: 900,
  status: 'LABELED',
  eligible: true
})
const scope = (id = 1) => ({
  representative: bag(id),
  productionOutputId: 12,
  packages: { list: [bag(id), bag(id + 1)], total: 2 }
})
const setup = (overrides = {}, grants = ['*:*:*']) => {
  const calls = []
  const data = new Map()
  const mounts = []
  const storage = {
    getItem: (key) => data.get(key) ?? null,
    setItem: (key, value) => data.set(key, value),
    removeItem: (key) => data.delete(key)
  }
  const api = {
    scope: async (params) => {
      calls.push(['scope', plain(params)])
      return scope()
    },
    getPage: async (params) => {
      calls.push(['page', plain(params)])
      return { list: [], total: 0 }
    },
    createBatch: async (payload) => {
      calls.push(['create', plain(payload)])
      return 9
    },
    submit: async (id) => {
      calls.push(['submit', id])
      return true
    },
    approve: async (id) => {
      calls.push(['approve', id])
      return true
    },
    reject: async (payload) => {
      calls.push(['reject', plain(payload)])
      return true
    },
    get: async () => ({
      id: 9,
      inspectionNo: 'QC-TEST',
      status: 'SUBMITTED',
      clientRequestId: 'REQ-1',
      submitterId: 5,
      items: [{ qualified: true }]
    }),
    ...overrides
  }
  const module = evaluate(
    readFileSync(new URL('./useQualityWork.ts', import.meta.url), 'utf8'),
    {
      vue: { ...vue, onMounted: (fn) => mounts.push(fn), onBeforeUnmount: () => {} },
      'vue-router': { useRoute: () => ({ query: {} }) },
      'element-plus': {
        ElMessageBox: { confirm: async () => true, prompt: async () => ({ value: '  不合格  ' }) }
      },
      '@/directives/permission/hasPermi': {
        hasPermission: (permissions) =>
          grants.includes('*:*:*') || permissions.some((permission) => grants.includes(permission))
      },
      '@/store/modules/user': { useUserStore: () => ({ getUser: { id: 5 } }) },
      '@/utils/auth': { getTenantId: () => 1, getVisitTenantId: () => undefined },
      '@/utils': { generateUUID: () => 'REQ-1' },
      '@/utils/rdDigitalLink': {
        extractPackageCode: (value) => value.trim().split('/pkg/').at(-1)
      },
      '@/api/rd/quality/inspection': { RdQualityInspectionApi: api },
      './draft': draft
    },
    { localStorage: storage }
  )
  const work = module.useQualityWork()
  const fill = async () => {
    work.input.value = 'https://id.tjldsic.com/pkg/PKG-1'
    await work.loadScope()
    work.items.value[0].itemCode = 'SIC'
    work.items.value[0].itemName = '碳化硅'
    work.items.value[0].measuredValue = 98
  }
  return { work, calls, data, api, storage, mount: () => mounts.forEach((fn) => fn()), fill }
}
test('scan only locates source and selects the actual representative, never all source packages', async () => {
  const { work, calls, fill } = setup()
  await fill()
  assert.deepEqual(plain(work.selected.value), [1])
  assert.deepEqual(plain(work.samples.value), [1])
  assert.equal(
    calls.some(([name]) => ['create', 'submit'].includes(name)),
    false
  )
  work.toggleCoverage(2, true)
  assert.deepEqual(plain(work.selected.value), [1, 2])
  assert.deepEqual(plain(work.samples.value), [1])
})
test('page navigation stays pinned to resolved source even if lookup input changes', async () => {
  const { work, calls, fill } = setup()
  await fill()
  work.input.value = 'OTHER'
  await work.loadScope(2, false)
  assert.equal(calls.filter(([name]) => name === 'scope').at(-1)[1].packageCode, 'PKG-1')
  assert.deepEqual(plain(work.selected.value), [1])
})
test('late response from another source cannot replace current scope or selection', async () => {
  let release
  const { work } = setup({
    scope: (params) =>
      params.packageCode === 'FIRST'
        ? new Promise((resolve) => {
            release = resolve
          })
        : Promise.resolve(scope(10))
  })
  work.input.value = 'FIRST'
  const first = work.loadScope()
  work.input.value = 'SECOND'
  await work.loadScope()
  release(scope(1))
  await first
  assert.equal(work.scope.value.representative.id, 10)
  assert.deepEqual(plain(work.selected.value), [10])
})
test('lost create response preserves exact payload and retry identity despite changed form values', async () => {
  let attempts = 0
  const payloads = []
  const context = setup({
    createBatch: async (payload) => {
      payloads.push(plain(payload))
      if (++attempts === 1) throw new Error('Network Error')
      return 9
    }
  })
  await context.fill()
  context.work.toggleCoverage(2, true)
  await context.work.save()
  assert.ok(context.work.pending.value)
  assert.equal(context.work.locked.value, true)
  context.work.items.value[0].measuredValue = 1
  context.work.selected.value = [1]
  await context.work.save()
  assert.deepEqual(payloads[0], payloads[1])
  assert.deepEqual(payloads[1].packageIds, [1, 2])
  assert.equal(payloads[1].items[0].measuredValue, 98)
  assert.equal(context.work.pending.value, undefined)
  assert.equal(context.data.size, 0)
})
test('lost submit response retains acknowledged inspection and retries the original request', async () => {
  let submits = 0
  const { work, fill, data } = setup({
    submit: async () => {
      if (++submits === 1) throw new Error('timeout')
      return true
    }
  })
  await fill()
  await work.save()
  assert.equal(work.pending.value.inspectionId, 9)
  assert.equal(data.size, 1)
  await work.save()
  assert.equal(work.pending.value, undefined)
  assert.equal(submits, 2)
})
test('failed local persistence prevents the first business request', async () => {
  const { work, fill, storage, calls } = setup()
  await fill()
  storage.setItem = () => {
    throw new Error('quota')
  }
  await work.save()
  assert.equal(
    calls.some(([name]) => name === 'create'),
    false
  )
  assert.ok(work.pending.value)
  assert.ok(work.storageError.value)
})
test('known create rejection releases only the uncreated request; unknown failure remains pending', async () => {
  for (const [error, remains] of [
    [{ businessCode: 10090001, message: '范围已被占用' }, false],
    [new Error('Network Error'), true]
  ]) {
    const { work, fill } = setup({
      createBatch: async () => {
        throw error
      }
    })
    await fill()
    await work.save()
    assert.equal(!!work.pending.value, remains)
    assert.equal(work.items.value[0].measuredValue, 98)
  }
})
test('self-review is hidden and refuses to call an approval even with all permissions', async () => {
  const { work, calls } = setup()
  await work.openInspection(9)
  assert.equal(work.canReview.value, false)
  await work.act('approve')
  assert.equal(
    calls.some(([name]) => name === 'approve'),
    false
  )
})
test('another reviewer uses actual detail and trims non-passing reason', async () => {
  const { work, calls } = setup({
    get: async () => ({
      id: 9,
      inspectionNo: 'QC-TEST',
      status: 'SUBMITTED',
      submitterId: 7,
      items: [{ qualified: false }]
    })
  })
  await work.openInspection(9)
  assert.equal(work.canReview.value, true)
  await work.act('approve')
  assert.equal(
    calls.some(([name]) => name === 'approve'),
    false
  )
  await work.act('reject')
  assert.deepEqual(calls.find(([name]) => name === 'reject')[1], { id: 9, reason: '不合格' })
})
test('no query permission produces no requests and cannot create or review', async () => {
  const { work, calls, mount } = setup({}, [])
  mount()
  work.input.value = 'PKG-1'
  await work.loadScope()
  await work.save()
  await work.openInspection(9)
  assert.equal(calls.length, 0)
  assert.equal(work.canReview.value, false)
})
test('scanning a processed source bag can still select actual remaining eligible bags', async () => {
  const source = scope()
  source.representative.eligible = false
  source.representative.status = 'QC_PASSED'
  source.packages.list[0] = source.representative
  const { work, calls } = setup({ scope: async () => source })
  work.input.value = 'PKG-1'
  await work.loadScope()
  assert.deepEqual(plain(work.selected.value), [])
  work.toggleCoverage(2, true)
  work.toggleSample(2, true)
  work.items.value[0].itemCode = 'TEST'
  work.items.value[0].itemName = 'result'
  work.items.value[0].measuredValue = 98
  await work.save()
  const created = calls.find(([name]) => name === 'create')[1]
  assert.equal(created.packageId, 2)
  assert.deepEqual(created.packageIds, [2])
  assert.deepEqual(created.samplePackageIds, [2])
})

test('explicit whole-batch selection reads every page but includes only eligible bags', async () => {
  const all = Array.from({ length: 121 }, (_, index) => ({
    ...bag(index + 1),
    eligible: index % 3 !== 2
  }))
  const calls = []
  const { work } = setup({
    scope: async (params) => {
      calls.push(plain(params))
      return {
        representative: all[0],
        productionOutputId: 12,
        packages: {
          list: all.slice((params.pageNo - 1) * params.pageSize, params.pageNo * params.pageSize),
          total: all.length
        }
      }
    }
  })
  work.input.value = 'PKG-1'
  await work.loadScope()
  await work.selectEligibleBatch()
  assert.deepEqual(
    plain(work.selected.value),
    all.filter((row) => row.eligible).map((row) => row.id)
  )
  assert.deepEqual(plain(work.samples.value), [1])
  assert.deepEqual(
    calls.slice(1).map((params) => [params.pageNo, params.pageSize]),
    [
      [1, 100],
      [2, 100]
    ]
  )
  assert.equal(work.selectingBatch.value, false)
})

test('oversized batch is never silently truncated and preserves the original selection', async () => {
  const all = Array.from({ length: 201 }, (_, index) => bag(index + 1))
  const { work } = setup({
    scope: async (params) => ({
      representative: all[0],
      productionOutputId: 12,
      packages: {
        list: all.slice((params.pageNo - 1) * params.pageSize, params.pageNo * params.pageSize),
        total: all.length
      }
    })
  })
  work.input.value = 'PKG-1'
  await work.loadScope()
  work.toggleCoverage(2, true)
  await work.selectEligibleBatch()
  assert.deepEqual(plain(work.selected.value), [1, 2])
  assert.match(work.actionError.value, /200/)
  assert.equal(work.locked.value, false)
})

test('failed or inconsistent batch pagination cannot replace the selected coverage', async () => {
  for (const fail of ['network', 'changed']) {
    const all = Array.from({ length: 101 }, (_, index) => bag(index + 1))
    const { work } = setup({
      scope: async (params) => {
        if (params.pageNo === 2 && fail === 'network') throw new Error('Network Error')
        return {
          representative: all[0],
          productionOutputId: 12,
          packages: {
            list: all.slice((params.pageNo - 1) * params.pageSize, params.pageNo * params.pageSize),
            total: params.pageNo === 2 && fail === 'changed' ? 102 : 101
          }
        }
      }
    })
    work.input.value = 'PKG-1'
    await work.loadScope()
    work.toggleCoverage(2, true)
    await work.selectEligibleBatch()
    assert.deepEqual(plain(work.selected.value), [1, 2])
    assert.ok(work.actionError.value)
    assert.equal(work.locked.value, false)
  }
})
