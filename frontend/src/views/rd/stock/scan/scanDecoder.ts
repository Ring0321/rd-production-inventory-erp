import wasmUrl from 'zxing-wasm/reader/zxing_reader.wasm?url'
import type { DetectedBarcode } from 'barcode-detector/pure'

export const localZXingOptions = {
  locateFile: (path: string, prefix: string) =>
    path.endsWith('.wasm') ? wasmUrl : `${prefix}${path}`
}

export const decodeLocalImage = async (image: ImageBitmap): Promise<DetectedBarcode[]> => {
  if (image.width * image.height > 16_000_000)
    throw new Error('图片超过 1600 万像素，请选择标签近照')
  const { BarcodeDetector, setZXingModuleOverrides } = await import('barcode-detector/pure')
  setZXingModuleOverrides(localZXingOptions)
  return new BarcodeDetector({ formats: ['qr_code'] }).detect(image)
}
