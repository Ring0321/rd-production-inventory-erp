import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { evaluateSource, plain, deferred } from '../../trace/package/testHelpers.mjs'

const read = (name) => readFileSync(new URL(name, import.meta.url), 'utf8')
const schema = evaluateSource(read('../layout/visualSchema.ts'))
const labels = evaluateSource(read('./labelModel.ts'), { '../layout/visualSchema': schema })
const exports = evaluateSource(read('./auditedExport.ts'), { vue })
const pkg = () => ({
  id: 7,
  packageCode: 'PK-0007',
  qrCode: '原始二维码-0007',
  customerName: '合成客户',
  productModel: '测试型号',
  furnaceNo: 'F-1',
  furnaceLayer: '中层',
  packageWeight: 1000,
  productionDate: [2026, 10, 15],
  version: 2,
  status: 'CREATED'
})

test('label exact logical schema has mandatory unique code/QR, no actual values or raw pdf', () => {
  const value = labels.defaultLabel()
  assert.deepEqual(plain(labels.parseLabel(value)), plain(value))
  for (const key of ['packageCode', 'qrCode']) {
    assert.throws(() =>
      labels.parseLabel({ ...value, fields: value.fields.filter((f) => f.key !== key) })
    )
    assert.throws(() =>
      labels.parseLabel({
        ...value,
        fields: [...value.fields, value.fields.find((f) => f.key === key)]
      })
    )
  }
  for (const extra of [{ basePdf: 'data:pdf' }, { script: 'evil()' }, { customerName: 'secret' }])
    assert.throws(() => labels.parseLabel({ ...value, ...extra }))
  for (const patch of [
    { key: 'unknown' },
    { x: -1 },
    { width: Infinity },
    { fontSize: 0 },
    { url: 'https://evil' }
  ]) {
    assert.throws(() =>
      labels.parseLabel({
        ...value,
        fields: [{ ...value.fields[0], ...patch }, ...value.fields.slice(1)]
      })
    )
  }
})

test('pdfme schemas are blank white with black text and local font; immutable live QR/code', () => {
  const logical = labels.defaultLabel()
  const template = labels.toPdfmeTemplate(logical)
  assert.equal(typeof template.basePdf, 'object')
  assert.equal(template.schemas.length, 1)
  for (const item of template.schemas[0]) {
    assert.equal(item.type, item.name === 'qrCode' ? 'qrcode' : 'text')
    if (item.type === 'text') {
      assert.equal(item.fontName, 'NotoSansSC')
      assert.equal(item.fontColor, '#000000')
      assert.equal(item.overflow, 'visible')
      assert.equal(item.textFormat, 'plain')
      assert.equal(item.dynamicFontSize.fit, 'vertical')
    }
  }
  const inputs = labels.packageInputs(pkg(), '本地品牌')
  assert.equal(inputs.packageCode, pkg().packageCode)
  assert.equal(inputs.qrCode, pkg().qrCode)
  assert.equal(inputs.productionDate, '2026-10-15')
  assert.ok(!JSON.stringify(logical).includes(pkg().customerName))
})

test('label editor matches server page font and minimum square QR limits', () => {
  for (const patch of [{ widthMm: 49 }, { widthMm: 211 }, { heightMm: 29 }, { heightMm: 298 }])
    assert.throws(() => labels.parseLabel({ ...labels.defaultLabel(), ...patch }))
  for (const patch of [{ width: 19 }, { height: 25 }, { fontSize: 37 }]) {
    const value = labels.defaultLabel()
    Object.assign(
      value.fields.find((f) => f.key === 'qrCode'),
      patch
    )
    assert.throws(() => labels.parseLabel(value))
  }
})

test('Designer adapter accepts geometry only, reconstructs styles and cannot persist injected fields', () => {
  const logical = labels.defaultLabel()
  const template = labels.toPdfmeTemplate(logical)
  template.schemas[0][0].position.x = 5
  template.schemas[0][0].fontColor = '#ff0000'
  template.schemas[0][0].content = 'overridden'
  template.schemas[0][0].fontName = 'https://evil'
  const accepted = labels.fromPdfmeTemplate(template, logical)
  assert.equal(accepted.fields[0].x, 5)
  assert.equal(labels.toPdfmeTemplate(accepted).schemas[0][0].fontColor, '#000000')
  assert.ok(!JSON.stringify(accepted).includes('overridden'))
  template.schemas[0].push({ name: 'image', type: 'image' })
  assert.throws(() => labels.fromPdfmeTemplate(template, logical))
  assert.throws(() => labels.fromPdfmeTemplate({ ...template, basePdf: 'remote.pdf' }, logical))
})

const setup = (extra = {}) => {
  const calls = []
  let current = true
  const controller = exports.createAuditedExport({
    current: () => current,
    canPrint: () => true,
    getPackage: async () => {
      calls.push('get')
      return pkg()
    },
    generate: async () => {
      calls.push('generate')
      return new Uint8Array([37, 80, 68, 70, 45, 49])
    },
    audit: async (mode, data) => {
      calls.push({ mode, data })
      return true
    },
    download: () => {
      calls.push('download')
    },
    ...extra
  })
  return {
    controller,
    calls,
    revoke: () => {
      current = false
      controller.clear()
    }
  }
}
const request = () => ({
  mode: 'print',
  item: pkg(),
  template: labels.defaultLabel(),
  publishedVersion: 1,
  reason: ''
})

test('export prepares valid PDF first then exactly one original audit then download', async () => {
  const { controller, calls } = setup()
  await controller.run(request())
  assert.deepEqual(plain(calls), [
    'get',
    'generate',
    { mode: 'print', data: { packageId: 7 } },
    'download'
  ])
  assert.equal(controller.state.phase, 'EXPORTED')
  await assert.rejects(() => controller.run(request()))
  assert.equal(calls.filter((v) => typeof v === 'object').length, 1)
  controller.downloadAgain()
  assert.equal(calls.filter((v) => v === 'download').length, 2)
})

test('invalid PDF or stale package never reaches audit', async () => {
  for (const options of [
    { generate: async () => new Uint8Array([1, 2]) },
    { getPackage: async () => ({ ...pkg(), version: 99 }) },
    { getPackage: async () => ({ ...pkg(), qrCode: 'changed' }) }
  ]) {
    const { controller, calls } = setup(options)
    await controller.run(request())
    assert.equal(calls.filter((v) => typeof v === 'object').length, 0)
    assert.equal(controller.state.phase, 'FAILED')
  }
})

test('reprint needs nonempty reason, original permission and non-CREATED/non-VOIDED status', async () => {
  const ctx = setup()
  await assert.rejects(() => ctx.controller.run({ ...request(), mode: 'reprint' }))
  const denied = setup({ canPrint: () => false })
  await assert.rejects(() => denied.controller.run(request()))
  const reprinted = { ...pkg(), status: 'LABELED' }
  const valid = setup({ getPackage: async () => reprinted })
  await valid.controller.run({ ...request(), item: reprinted, mode: 'reprint', reason: '标签磨损' })
  assert.deepEqual(plain(valid.calls.find((v) => typeof v === 'object')), {
    mode: 'reprint',
    data: { packageId: 7, reprintReason: '标签磨损' }
  })
})

test('unknown audit outcome is latched across clear, no retry and no unaudited download', async () => {
  const { controller, calls } = setup({
    audit: async () => {
      calls.push('audit')
      throw new Error('network lost')
    }
  })
  await controller.run(request())
  assert.equal(controller.state.phase, 'UNKNOWN')
  assert.ok(!calls.includes('download'))
  controller.clear()
  await assert.rejects(() => controller.run(request()))
  assert.equal(calls.filter((v) => v === 'audit').length, 1)
})

test('permission revocation during generate or audit drops sensitive PDF and late download', async () => {
  for (const stage of ['generate', 'audit']) {
    const pending = deferred()
    const ctx = setup({ [stage]: () => pending.promise })
    const work = ctx.controller.run(request())
    await new Promise((resolve) => setTimeout(resolve, 0))
    ctx.revoke()
    pending.resolve(stage === 'generate' ? new Uint8Array([37, 80, 68, 70, 45, 49]) : true)
    await work
    assert.ok(!ctx.calls.includes('download'))
    assert.equal(ctx.controller.state.packageId, null)
    assert.throws(() => ctx.controller.downloadAgain())
  }
})

test('audited package latch updates computed controls, and permission still gates same-PDF download', async () => {
  let allowed = true
  const ctx = setup({ canPrint: () => allowed })
  const blocked = vue.computed(() => ctx.controller.blocked(7))
  assert.equal(blocked.value, false)
  await ctx.controller.run(request())
  assert.equal(blocked.value, true)
  allowed = false
  assert.throws(() => ctx.controller.downloadAgain())
})
