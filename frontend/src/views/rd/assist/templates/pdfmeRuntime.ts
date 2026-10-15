import type { Font } from '@pdfme/common'
import type { RdPackageUnitVO } from '@/api/rd/stock/package'
import { productBrand } from '@/config/brand'
import { packageInputs, toPdfmeTemplate, type LabelContent } from './labelModel'

export const LOCAL_FONT_PATH = '/fonts/assist/NotoSansSC-Regular.ttf'
let fontPromise: Promise<Font> | undefined
export const loadLocalFont = (): Promise<Font> => {
  if (!fontPromise) {
    fontPromise = (async () => {
      const response = await fetch(LOCAL_FONT_PATH, {
        credentials: 'same-origin',
        redirect: 'error',
        signal: AbortSignal.timeout(20000)
      })
      if (!response.ok) throw new Error('本地 NotoSansSC 字体不可用')
      const data = await response.arrayBuffer()
      const header = new Uint8Array(data, 0, Math.min(4, data.byteLength))
      if (
        data.byteLength < 1024 ||
        data.byteLength > 32 * 1024 * 1024 ||
        !(
          (header[0] === 0 && header[1] === 1 && header[2] === 0 && header[3] === 0) ||
          String.fromCharCode(...header) === 'OTTO'
        )
      )
        throw new Error('本地字体文件无效')
      return { NotoSansSC: { data, fallback: true, subset: true } }
    })().catch((error) => {
      fontPromise = undefined
      throw error
    })
  }
  return fontPromise
}

export const loadPdfmeRuntime = async () => {
  const ui = await import('@pdfme/ui')
  const schemas = await import('@pdfme/schemas')
  const font = await loadLocalFont()
  return { ...ui, font, plugins: { text: schemas.text, qrcode: schemas.barcodes.qrcode } }
}

export const generateLabelPdf = async (content: LabelContent, item: RdPackageUnitVO) => {
  const { generate } = await import('@pdfme/generator')
  const schemas = await import('@pdfme/schemas')
  const font = await loadLocalFont()
  return generate({
    template: toPdfmeTemplate(content),
    inputs: [packageInputs(item, productBrand.labelTitle)],
    options: { font },
    plugins: { text: schemas.text, qrcode: schemas.barcodes.qrcode }
  })
}

export const downloadLabelPdf = (bytes: Uint8Array, item: RdPackageUnitVO) => {
  const url = URL.createObjectURL(
    new Blob([new Uint8Array(bytes).buffer], { type: 'application/pdf' })
  )
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `label-${item.packageCode.replace(/[^a-zA-Z0-9_-]/g, '_').slice(0, 80)}.pdf`
  anchor.click()
  window.setTimeout(() => URL.revokeObjectURL(url), 10000)
}
