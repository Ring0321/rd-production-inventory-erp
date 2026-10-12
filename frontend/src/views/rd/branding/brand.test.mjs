import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'

const evaluate = (file, mocks = {}, env = {}, globals = {}) => {
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
  const parsed = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true)
  const transformed = ts.transform(parsed, [
    (context) => {
      const visit = (node) => {
        if (
          ts.isPropertyAccessExpression(node) &&
          node.name.text === 'env' &&
          ts.isMetaProperty(node.expression)
        ) {
          return ts.factory.createIdentifier('__env')
        }
        return ts.visitEachChild(node, visit, context)
      }
      return (node) => ts.visitNode(node, visit)
    }
  ])
  const text = ts.createPrinter().printFile(transformed.transformed[0])
  transformed.dispose()
  const output = ts.transpileModule(text, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  }).outputText
  const module = { exports: {} }
  runInNewContext(output, {
    module,
    exports: module.exports,
    __env: env,
    ...globals,
    require: (name) => {
      assert.ok(name in mocks, 'Unexpected dependency: ' + name)
      return mocks[name]
    }
  })
  return module.exports
}

test('product branding has a company-neutral default and can be changed together', () => {
  const b = evaluate('../../../config/brand.ts')
  assert.equal(b.productBrand.name, '垛序 ERP')
  assert.equal(b.productBrand.shortName, '垛序')
  assert.equal(b.productBrand.logoUrl, '/duoxu-mark.png')
  const custom = b.createProductBrand({
    VITE_APP_TITLE: 'Custom ERP',
    VITE_PRODUCT_SHORT_NAME: 'Custom',
    VITE_PRODUCT_SUBTITLE: 'Production operations',
    BASE_URL: '/erp/'
  })
  assert.equal(custom.name, 'Custom ERP')
  assert.equal(custom.logoUrl, '/erp/duoxu-mark.png')
  assert.equal(custom.labelTitle, 'Custom · 货品标识卡')
})

test('changing brand or link configuration never rewrites a stored QR identity', () => {
  for (const DEV of [true, false]) {
    const links = evaluate(
      '../../../utils/rdDigitalLink.ts',
      {},
      { DEV, VITE_RD_DIGITAL_LINK_BASE_URL: 'https://new.example/pkg' },
      { URL, window: { location: { origin: 'http://localhost:9000' } } }
    )
    const original = 'https://id.tjldsic.com/pkg/LD20261011A0001'
    assert.equal(links.getPackageDigitalLink('LD20261011A0001', original), original)
    assert.equal(links.extractPackageCode(original), 'LD20261011A0001')
  }
})

test('legacy missing QR uses backend trace even when browser and backend origins differ', async () => {
  const links = evaluate(
    '../../../utils/rdDigitalLink.ts',
    {},
    { VITE_RD_DIGITAL_LINK_BASE_URL: 'https://wrong.example/pkg' },
    { URL, window: { location: { origin: 'http://internal.example:9000' } } }
  )
  const expected = 'https://configured.example/pkg/OLD-1'
  let reads = 0
  const read = async (packageCode) => {
    reads++
    return { packageCode, digitalLink: expected }
  }
  assert.equal(await links.resolvePackageDigitalLink('OLD-1', '', read), expected)
  assert.equal(reads, 1)
  assert.equal(await links.resolvePackageDigitalLink('OLD-1', expected, read), expected)
  assert.equal(reads, 1)
  await assert.rejects(
    links.resolvePackageDigitalLink('OLD-1', '', async () => ({
      packageCode: 'WRONG',
      digitalLink: expected
    }))
  )
  await assert.rejects(links.resolvePackageDigitalLink('OLD-1', '', async () => null))
})

test('brand configuration rejects external or executable mark references', () => {
  const b = evaluate('../../../config/brand.ts')
  for (const value of [
    '//external.invalid/mark.png',
    'https://external.invalid/x',
    'javascript:alert(1)',
    '../private.png'
  ]) {
    assert.equal(b.createProductBrand({ VITE_PRODUCT_LOGO: value }).logoUrl, '/duoxu-mark.png')
  }
})

test('initial page branding uses the same configured values and safe HTML escaping', () => {
  const b = evaluate('../../../config/brand.ts')
  const brand = b.createProductBrand({
    VITE_APP_TITLE: 'Custom & ERP',
    VITE_PRODUCT_SHORT_NAME: 'Custom',
    BASE_URL: '/erp/',
    VITE_PRODUCT_LOGO: 'custom.png'
  })
  const html = b.applyProductBrandHtml(
    '<title>__PRODUCT_NAME__</title><img src="__PRODUCT_LOGO__" alt="__PRODUCT_SUBTITLE__">',
    brand
  )
  assert.ok(html.includes('Custom &amp; ERP'))
  assert.ok(html.includes('/erp/custom.png'))
  assert.ok(!html.includes('__PRODUCT_'))
  assert.throws(() => b.createProductBrand({ VITE_APP_TITLE: '<script>bad</script>' }))
})

test('printed branding changes but business names, package identity and original QR pixels are preserved', () => {
  const b = evaluate('../../../config/brand.ts')
  const labels = evaluate('../../../utils/rdPackageLabel.ts', { '@/config/brand': b })
  const item = {
    packageCode: 'LD20261011A0001',
    customerName: '真实客户保留',
    productModel: 'M1',
    furnaceNo: 'F1',
    furnaceLayer: '上层',
    packageWeight: 697,
    productionDate: '2026-10-11'
  }
  const qrDataUrl = 'data:image/png;base64,ORIGINAL_QR'
  const html = labels.buildPackageLabelHtml([{ item, qrDataUrl }], 'LABEL_100_76')
  assert.ok(html.includes('垛序 · 货品标识卡'))
  assert.ok(!html.includes('天净隆鼎'))
  assert.ok(html.includes(item.customerName))
  assert.ok(html.includes(item.packageCode))
  assert.ok(html.includes(qrDataUrl))
  const unsafe = labels.buildPackageLabelHtml(
    [{ item, qrDataUrl }],
    'A4',
    false,
    '<img src=x onerror=alert(1)>'
  )
  assert.ok(!unsafe.includes('<h1><img'))
  assert.ok(unsafe.includes('&lt;img'))
})
