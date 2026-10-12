export const getPackageDigitalLink = (packageCode: string, storedQrCode?: string) => {
  if (!packageCode.trim()) throw new Error('Package identity is required before printing')
  if (storedQrCode && /^https?:\/\//i.test(storedQrCode.trim())) {
    return storedQrCode.trim()
  }
  throw new Error('Package QR identity is missing; resolve it from ERP before printing')
}

export const resolvePackageDigitalLink = async (
  packageCode: string,
  storedQrCode: string | undefined,
  readTrace: (code: string) => Promise<{ packageCode: string; digitalLink?: string } | null>
) => {
  if (storedQrCode && /^https?:\/\//i.test(storedQrCode.trim())) {
    return getPackageDigitalLink(packageCode, storedQrCode)
  }
  const trace = await readTrace(packageCode)
  if (!trace || trace.packageCode !== packageCode)
    throw new Error('Package identity could not be verified')
  return getPackageDigitalLink(packageCode, trace.digitalLink)
}

export const extractPackageCode = (value: string) => {
  const normalized = value.trim()
  if (!/^https?:\/\//i.test(normalized)) return normalized
  try {
    const url = new URL(normalized)
    const marker = '/pkg/'
    const markerIndex = url.pathname.toLowerCase().lastIndexOf(marker)
    if (markerIndex >= 0) {
      return decodeURIComponent(url.pathname.slice(markerIndex + marker.length)).trim()
    }
  } catch {
    return normalized
  }
  return normalized
}
