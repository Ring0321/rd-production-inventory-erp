const DEFAULT_DIGITAL_LINK_BASE_URL = 'https://id.tjldsic.com/pkg'

const normalizeBaseUrl = (value: string) => value.trim().replace(/\/+$/, '')

export const getPackageDigitalLink = (packageCode: string, storedQrCode?: string) => {
  const configuredBaseUrl = import.meta.env.VITE_RD_DIGITAL_LINK_BASE_URL?.trim()
  if (configuredBaseUrl) {
    return `${normalizeBaseUrl(configuredBaseUrl)}/${encodeURIComponent(packageCode)}`
  }
  if (import.meta.env.DEV && typeof window !== 'undefined') {
    return `${window.location.origin}/pkg/${encodeURIComponent(packageCode)}`
  }
  if (storedQrCode && /^https?:\/\//i.test(storedQrCode.trim())) {
    return storedQrCode.trim()
  }
  return `${DEFAULT_DIGITAL_LINK_BASE_URL}/${encodeURIComponent(packageCode)}`
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
