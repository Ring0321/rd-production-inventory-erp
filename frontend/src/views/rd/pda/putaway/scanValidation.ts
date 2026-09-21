export interface PutawayScanLine {
  packageCode: string
  scanCount?: number
}

export type PutawayScanDecisionType = 'success' | 'duplicate' | 'mismatch' | 'empty'

export interface PutawayScanDecision {
  type: PutawayScanDecisionType
  code: string
  packageCode?: string
  message: string
}

const packageKey = (value: string) => value.trim().toUpperCase()

export const normalizePutawayPackageCode = (rawValue: string) => {
  const value = rawValue.trim()
  if (!value) return ''

  try {
    const url = new URL(value)
    const segments = url.pathname.split('/').filter(Boolean)
    const pkgIndex = segments.findIndex((segment) => segment.toLowerCase() === 'pkg')
    if (pkgIndex >= 0 && segments[pkgIndex + 1]) {
      return decodeURIComponent(segments[pkgIndex + 1]).trim()
    }
  } catch {
    const match = value.match(/(?:^|\/)pkg\/([^/?#]+)/i)
    if (match?.[1]) return decodeURIComponent(match[1]).trim()
  }

  return value
}

export const validatePutawayScan = (
  rawValue: string,
  lines: PutawayScanLine[],
  sessionScannedCodes: ReadonlySet<string>
): PutawayScanDecision => {
  const code = normalizePutawayPackageCode(rawValue)
  if (!code) {
    return { type: 'empty', code: '', message: '请扫描包码' }
  }

  const key = packageKey(code)
  const line = lines.find((item) => packageKey(item.packageCode) === key)
  if (!line) {
    return { type: 'mismatch', code, message: `包码 ${code} 不属于本任务` }
  }

  const canonicalKey = packageKey(line.packageCode)
  if (Number(line.scanCount || 0) > 0 || sessionScannedCodes.has(canonicalKey)) {
    return {
      type: 'duplicate',
      code,
      packageCode: line.packageCode,
      message: `包码 ${line.packageCode} 已扫描，请勿重复扫描`
    }
  }

  return {
    type: 'success',
    code,
    packageCode: line.packageCode,
    message: `包码 ${line.packageCode} 校验通过`
  }
}

export const toPutawayPackageKey = packageKey
