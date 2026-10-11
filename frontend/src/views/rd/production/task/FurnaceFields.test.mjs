import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import * as vue from 'vue'
import { parse, compileScript } from 'vue/compiler-sfc'

const pending = () => {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
const flush = () => new Promise((resolve) => setImmediate(resolve))
const harness = (allowed = true) => {
  const props = vue.reactive({
    customerName: 'A',
    productModel: 'M1',
    furnaceNo: 'ACTUAL-NEW',
    furnaceLayer: '旧层（X）'
  })
  const calls = { furnaces: [], layers: [], emits: [] }
  const { descriptor } = parse(
    readFileSync(new URL('./FurnaceFields.vue', import.meta.url), 'utf8')
  )
  const compiled = compileScript(descriptor, { id: 'furnace-choice-test' })
  const source = ts.transpileModule(compiled.content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  const module = { exports: {} }
  const apis = Object.fromEntries(
    ['furnaces', 'layers'].map((type) => [
      type,
      (params) => {
        const request = pending()
        calls[type].push({ params, ...request })
        return request.promise
      }
    ])
  )
  runInNewContext(source, {
    module,
    exports: module.exports,
    require: (name) => {
      if (name === 'vue') return { ...vue, onBeforeUnmount: () => {} }
      if (name === '@/directives/permission/hasPermi') return { hasPermission: () => allowed }
      if (name === '@/api/rd/production/choices') return { RdProductionChoiceApi: apis }
      throw new Error(`Unexpected dependency ${name}`)
    }
  })
  const scope = vue.effectScope()
  const state = scope.run(() =>
    module.exports.default.setup(props, {
      expose: () => {},
      emit: (...args) => calls.emits.push(args)
    })
  )
  return { props, calls, state, scope }
}

test('uses real customer/model filters without changing actual entered identifiers', async (t) => {
  const h = harness()
  t.after(() => h.scope.stop())
  assert.equal(h.calls.furnaces[0].params.customerName, 'A')
  assert.equal(h.calls.furnaces[0].params.productModel, 'M1')
  h.calls.furnaces[0].resolve(['1-13#28'])
  h.calls.layers[0].resolve([{ value: '下层', label: '下层 (X)', source: 'DICTIONARY' }])
  await flush()
  assert.equal(h.state.selectedFurnaces.value[0], 'ACTUAL-NEW')
  assert.equal(h.state.layerGroups.value[0].options[0].value, '旧层（X）')
  assert.deepEqual(h.calls.emits, [])
})

test('late results from the old customer/model do not contaminate the new options', async (t) => {
  const h = harness()
  t.after(() => h.scope.stop())
  h.props.customerName = 'B'
  h.props.productModel = 'M2'
  await vue.nextTick()
  h.calls.furnaces[1].resolve(['B-NEW'])
  h.calls.layers[1].resolve([{ value: 'B-LAYER', label: 'B-LAYER', source: 'HISTORY' }])
  await flush()
  h.calls.furnaces[0].resolve(['A-OLD'])
  h.calls.layers[0].reject(new Error('old request failed'))
  await flush()
  assert.equal(h.state.furnaces.value[0], 'B-NEW')
  assert.equal(h.state.layers.value[0].value, 'B-LAYER')
  assert.equal(h.state.layerError.value, false)
})

test('failed reads expose retry state without resetting entered values', async (t) => {
  const h = harness()
  t.after(() => h.scope.stop())
  h.calls.furnaces[0].reject(new Error('offline'))
  h.calls.layers[0].resolve([])
  await flush()
  assert.equal(h.state.furnaceError.value, true)
  const retry = h.state.loadFurnaces('actual')
  h.calls.furnaces[1].resolve(['ACTUAL'])
  await retry
  assert.equal(h.state.furnaceError.value, false)
  assert.equal(h.props.furnaceNo, 'ACTUAL-NEW')
})

test('roles without production-query capability do not request protected candidates', (t) => {
  const h = harness(false)
  t.after(() => h.scope.stop())
  assert.equal(h.calls.furnaces.length, 0)
  assert.equal(h.calls.layers.length, 0)
})
