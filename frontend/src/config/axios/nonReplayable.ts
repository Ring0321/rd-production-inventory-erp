const nonReplayablePaths = new Set([
  '/rd/package-unit/print',
  '/rd/package-unit/reprint',
  '/rd/package-unit/print-batch',
  '/rd/package-unit/reprint-batch',
  '/rd/visual-document/save',
  '/rd/visual-document/publish',
  '/rd/document-ocr/recognize'
])

export const isNonReplayableOperation = (method?: string, url?: string) => {
  if (!method || ['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase()) || !url) return false
  try {
    const path = new URL(url, 'http://127.0.0.1').pathname.replace(/^\/admin-api(?=\/)/, '')
    return nonReplayablePaths.has(path)
  } catch {
    return false
  }
}
