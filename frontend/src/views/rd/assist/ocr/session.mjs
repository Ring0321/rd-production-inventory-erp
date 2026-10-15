export const fieldKeys = ['customer', 'order', 'model', 'bagCount', 'weightKg']

export const fileKind = (bytes) => {
  const starts = (magic) => magic.every((value, index) => bytes[index] === value)
  if (starts([137, 80, 78, 71, 13, 10, 26, 10])) return 'image/png'
  if (starts([255, 216, 255])) return 'image/jpeg'
  if (starts([37, 80, 68, 70, 45])) return 'application/pdf'
  return null
}

export const previewDimensions = (bytes, kind) => {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  let width = 0,
    height = 0
  if (kind === 'image/png' && bytes.length >= 24 && view.getUint32(12) === 0x49484452) {
    width = view.getUint32(16)
    height = view.getUint32(20)
  } else if (kind === 'image/jpeg') {
    let offset = 2
    while (offset + 4 <= bytes.length && bytes[offset] === 255) {
      const marker = bytes[offset + 1]
      if (marker === 255) {
        offset++
        continue
      }
      const length = view.getUint16(offset + 2)
      if (length < 2 || offset + length + 2 > bytes.length) break
      if (
        [192, 193, 194, 195, 197, 198, 199, 201, 202, 203, 205, 206, 207].includes(marker) &&
        length >= 7
      ) {
        height = view.getUint16(offset + 5)
        width = view.getUint16(offset + 7)
        break
      }
      offset += length + 2
    }
  }
  if (width < 1 || height < 1 || width > 5000 || height > 5000 || width * height > 12000000) {
    throw new Error('图片尺寸无效或超过上限')
  }
  return { width, height }
}

export const allowedScope = ({ userId, roles, permissions }) =>
  !!userId &&
  !roles.includes('rd_forklift_driver') &&
  ['*:*:*', 'rd:pickup-slip:create', 'rd:quality-inspection:create'].some((key) =>
    permissions.includes(key)
  )

export const draftFromResult = (result) =>
  Object.fromEntries(
    fieldKeys.map((key) => {
      const candidates = result?.candidates?.[key]?.candidates || []
      return [
        key,
        candidates.length === 1 && typeof candidates[0].value === 'string'
          ? candidates[0].value
          : ''
      ]
    })
  )

export const createOcrSession = () => {
  let generation = 0
  let disposed = false
  let active
  const clear = () => {
    generation++
    active?.abort()
    active = undefined
  }
  return {
    clear,
    begin: () => {
      clear()
      if (disposed) throw new Error('OCR session disposed')
      active = new AbortController()
      return { generation, signal: active.signal }
    },
    current: (ticket) => !disposed && ticket.generation === generation && !ticket.signal.aborted,
    dispose: () => {
      clear()
      disposed = true
    }
  }
}
