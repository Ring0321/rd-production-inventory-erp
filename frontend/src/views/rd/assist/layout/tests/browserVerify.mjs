import assert from 'node:assert/strict'
import { mkdirSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import { homedir } from 'node:os'
import { resolve, join } from 'node:path'

const bundled = createRequire(
  join(homedir(), '.cache/codex-runtimes/codex-primary-runtime/dependencies/node/package.json')
)
const { chromium } = bundled('playwright')
const sharp = bundled('sharp')
const artifacts = resolve('src/views/rd/assist/layout/tests/artifacts')
const origin = process.argv[2] || 'http://127.0.0.1:5187'
mkdirSync(artifacts, { recursive: true })
const browser = await chromium.launch({
  headless: true,
  executablePath:
    process.env.REUSE_CHROME || 'C:/Program Files/Google/Chrome/Application/chrome.exe'
})
const page = await browser.newPage({
  viewport: { width: 1440, height: 1000 },
  acceptDownloads: true
})
const errors = []
const external = []
const results = []
page.on('pageerror', (error) => errors.push(error.stack))
page.on('console', (message) => {
  if (['error', 'warning'].includes(message.type())) errors.push(message.text())
})
page.on('request', (request) => {
  if (!request.url().startsWith(origin) && !/^(blob:|data:)/.test(request.url()))
    external.push(request.url())
})
const click = async (name) => {
  await page.mouse.move(1, 1)
  await page.getByRole('button', { name, exact: true }).click({ position: { x: 8, y: 8 } })
}
const select = async (name, option) => {
  await page.getByRole('combobox', { name, exact: true }).press('ArrowDown')
  await page.getByRole('option', { name: option }).click()
}
const confirm = () => page.locator('.el-message-box__btns .el-button--primary').click()
const audit = () => page.evaluate(() => structuredClone(window.__reuseEditorFixture.audit))
const overflow = () => page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)
const previewPixels = async () => {
  for (let attempt = 0; attempt < 20; attempt++) {
    const screenshot = await page.locator('.pdfme-viewport').screenshot({ animations: 'disabled' })
    const { data, info } = await sharp(screenshot)
      .removeAlpha()
      .raw()
      .toBuffer({ resolveWithObject: true })
    let black = 0,
      white = 0
    for (let index = 0; index < data.length; index += info.channels) {
      if (data[index] < 50 && data[index + 1] < 50 && data[index + 2] < 50) black++
      if (data[index] > 250 && data[index + 1] > 250 && data[index + 2] > 250) white++
    }
    if (black > 100 && white > 1000) return { black, white }
    await page.waitForTimeout(100)
  }
  throw new Error('Preview pixels are blank after resize')
}
try {
  await page.goto(`${origin}/src/views/rd/assist/layout/tests/index.html`)
  await page.locator('canvas').first().waitFor()
  await page.waitForFunction(() => {
    const canvas = document.querySelector('canvas')
    if (!canvas || canvas.width < 100) return false
    const data = canvas.getContext('2d').getImageData(0, 0, canvas.width, canvas.height).data
    let count = 0
    for (let index = 0; index < data.length; index += 4)
      if (data[index + 3] && data[index] < 245) count++
    return count > 1000
  })
  const canvas = page.locator('canvas').first()
  const pixels = await canvas.evaluate((c) => {
    const data = c.getContext('2d').getImageData(0, 0, c.width, c.height).data
    let count = 0
    for (let i = 0; i < data.length; i += 4) if (data[i + 3] > 0 && data[i] < 245) count++
    return count
  })
  assert.ok(pixels > 1000)
  const box = await canvas.boundingBox()
  const scale = Math.min(box.width / 1200, box.height / 800)
  const offset = (box.width - 1200 * scale) / 2
  const x = box.x + offset + 200 * scale
  const y = box.y + 200 * scale
  await page.mouse.move(x, y)
  await page.mouse.down()
  await page.mouse.move(x + 60, y + 30, { steps: 10 })
  await page.mouse.up()
  await click('库位 · A-01')
  const moved = Number(
    await page.getByRole('spinbutton', { name: '横坐标', exact: true }).inputValue()
  )
  assert.ok(moved > 130, `Drag failed: ${moved}`)
  await page.getByRole('button', { name: '撤销', exact: true }).focus()
  await page.keyboard.press('Enter')
  await page.waitForFunction(
    () => Number(document.querySelector('input[aria-label="横坐标"]').value) === 130
  )
  assert.equal(
    Number(await page.getByRole('spinbutton', { name: '横坐标', exact: true }).inputValue()),
    130
  )
  await click('添加区域')
  await click('保存草稿')
  await page.getByText('草稿 v2 / 发布 v1', { exact: true }).waitFor()
  await click('发布')
  await confirm()
  await page.getByText('草稿 v3 / 发布 v3', { exact: true }).waitFor()
  await click('重新加载')
  await page.getByText('草稿 v3 / 发布 v3', { exact: true }).waitFor()
  await click('Conflict fixture')
  await click('添加通道')
  await click('保存草稿')
  await page.getByText(/版本冲突或提交结果未确认/).waitFor()
  assert.equal(await page.getByRole('button', { name: '保存草稿', exact: true }).isDisabled(), true)
  await click('Conflict fixture')
  results.push({
    scenario: 'Konva real drag, undo, add, save, publish, reload, conflict',
    passed: true,
    pixels
  })
  // Reload the synthetic fixture to discard the deliberately conflicting local draft.
  await page.reload()
  await page.locator('canvas').first().waitFor()
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.screenshot({ path: join(artifacts, `layout-${width}.png`), fullPage: true })
    assert.equal(await overflow(), false, `layout overflow ${width}`)
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await click('Label fixture')
  await select('预览包件', /SYNTH-0007/)
  await page.getByText('合成客户甲', { exact: true }).waitFor({ timeout: 60000 })
  assert.equal((await audit()).length, 0)
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 1000 })
    await page.waitForFunction(() => {
      const frame = document.querySelector('.pdfme-viewport')?.getBoundingClientRect()
      return (
        frame &&
        [...document.querySelectorAll('.pdfme-host *')]
          .filter((node) => node.textContent === '合成客户甲')
          .map((node) => node.getBoundingClientRect())
          .some(
            (text) =>
              text.height > 0 && text.width > 0 && text.x >= frame.x && text.right <= frame.right
          )
      )
    })
    const pixels = await previewPixels()
    results.push({ scenario: `Complete nonblank label at ${width}px`, passed: true, pixels })
    await page.screenshot({ path: join(artifacts, `label-${width}.png`), fullPage: true })
    assert.equal(await overflow(), false, `label overflow ${width}`)
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  const first = page.waitForEvent('download', { timeout: 60000 })
  await click('登记首次打印并导出 PDF')
  await confirm()
  const firstDownload = await first
  await firstDownload.saveAs(join(artifacts, 'first-print.pdf'))
  assert.deepEqual(await audit(), [{ mode: 'print', data: { packageId: 7 } }])
  const again = page.waitForEvent('download')
  await click('下载同一份 PDF')
  await again
  assert.equal((await audit()).length, 1)
  results.push({
    scenario:
      'Chinese/local QR preview; first print audit once; same PDF download without another audit',
    passed: true
  })
  await select('预览包件', /SYNTH-0008/)
  await page.getByText('合成客户乙', { exact: true }).waitFor()
  const reprint = page.waitForEvent('download', { timeout: 60000 })
  await click('登记补打并导出 PDF')
  await page.locator('.el-message-box textarea').fill('合成测试：标签磨损')
  await confirm()
  const reprintDownload = await reprint
  await reprintDownload.saveAs(join(artifacts, 'reprint.pdf'))
  assert.deepEqual((await audit())[1], {
    mode: 'reprint',
    data: { packageId: 8, reprintReason: '合成测试：标签磨损' }
  })
  await page.getByText('草稿预览', { exact: true }).click()
  await page.getByText('布局设计', { exact: true }).click()
  await page.locator('.designing .pdfme-host').waitFor()
  await page.locator('.designing').getByText('包件编号', { exact: true }).first().waitFor()
  const nativeField = page.locator('.designing').getByText('包件编号', { exact: true }).first()
  await nativeField.click({ position: { x: 4, y: 4 } })
  const beforeDrag = await nativeField.boundingBox()
  await page.mouse.move(beforeDrag.x + 10, beforeDrag.y + 10)
  await page.mouse.down()
  await page.mouse.move(beforeDrag.x + 50, beforeDrag.y + 40, { steps: 8 })
  await page.mouse.up()
  await page.getByText('未保存 / 发布 v1', { exact: true }).waitFor()
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 1000 })
    await previewPixels()
    await page.screenshot({ path: join(artifacts, `designer-${width}.png`), fullPage: true })
    assert.equal(await overflow(), false, `Designer document overflow ${width}`)
  }
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.screenshot({ path: join(artifacts, 'designer-1440.png'), fullPage: true })
  await page.getByRole('spinbutton', { name: '字段横坐标 (mm)', exact: true }).fill('6')
  await page.getByRole('spinbutton', { name: '字段横坐标 (mm)', exact: true }).press('Tab')
  await click('保存草稿')
  await page.getByText('草稿 v2 / 发布 v1', { exact: true }).waitFor()
  results.push({
    scenario: 'Reprint reason + distinct original audit; actual Designer geometry and logical save',
    passed: true
  })
  await click('Revoke fixture')
  await page.getByText(/身份或权限已变化，包件与预览已清空/).waitFor()
  assert.equal(await page.locator('.pdfme-host').count(), 0)
  assert.equal((await page.locator('body').innerText()).includes('合成客户甲'), false)
  await page.reload()
  await click('Label fixture')
  await select('预览包件', /SYNTH-0008/)
  await page.getByText('合成客户乙', { exact: true }).waitFor({ timeout: 60000 })
  await click('登记补打并导出 PDF')
  await page.locator('.el-message-box textarea').fill('AUTH-CLEAR-SENSITIVE-REASON')
  await page.evaluate(() => window.__reuseEditorFixture.revoke())
  await page.locator('.el-message-box').waitFor({ state: 'hidden' })
  assert.equal((await audit()).length, 0)
  assert.equal((await page.locator('body').innerText()).includes('SYNTH-0008'), false)
  results.push({
    scenario: 'Auth revision closes owned reprint-reason dialog without auditing',
    passed: true
  })
  await page.reload()
  await click('Label fixture')
  await select('预览包件', /SYNTH-0007/)
  await page.getByText('合成客户甲', { exact: true }).waitFor({ timeout: 60000 })
  await click('Unknown audit fixture')
  await click('登记首次打印并导出 PDF')
  await confirm()
  await page.getByText(/打印登记结果未知/).waitFor()
  assert.equal((await audit()).length, 1)
  assert.equal(
    await page.getByRole('button', { name: '登记首次打印并导出 PDF', exact: true }).isDisabled(),
    true
  )
  results.push({ scenario: 'Auth disposal + synthetic ambiguous audit blocks retry', passed: true })
  assert.deepEqual(external, [])
  assert.deepEqual(errors, [])
  writeFileSync(
    join(artifacts, 'browser-results.json'),
    JSON.stringify({ results, errors, external }, null, 2)
  )
  console.log(JSON.stringify({ results, errors, external }, null, 2))
} catch (error) {
  await page.screenshot({ path: join(artifacts, 'failure.png'), fullPage: true })
  console.error(error)
  console.error(
    JSON.stringify({ body: await page.locator('body').innerText(), errors, external }, null, 2)
  )
  process.exitCode = 1
} finally {
  await browser.close()
}
