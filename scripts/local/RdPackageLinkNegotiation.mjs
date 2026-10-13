const types = new Map([
  ['text/html', 'web'], ['application/linkset+json', 'links'],
  ['application/json', 'passport'], ['application/ld+json', 'document']
])

export const isPublicPackageQuery = (url) =>
  /^((resource|workMode|internal)=[A-Za-z0-9_-]*(&(resource|workMode|internal)=[A-Za-z0-9_-]*)*)?$/.test(url.search.slice(1))

export const resolvePackageRequest = (url, accept = '') => {
  let pathname
  try { pathname = decodeURIComponent(url.pathname) } catch { return { error: 400 } }
  const match = /^\/(?:[A-Za-z0-9_-]+\/)*pkg\/([A-Za-z0-9][A-Za-z0-9_-]{0,63})$/.exec(pathname)
  if (!match) return undefined
  if (!isPublicPackageQuery(url)) return { error: 400 }
  const code = match[1]
  const requested = url.searchParams.getAll('resource')
  if (requested.length > 1 || (requested.length && !['web', 'passport', 'document', 'links'].includes(requested[0]))) return { error: 400 }
  if (requested.length) return { code, resource: requested[0] }
  if (!accept.trim()) return { code, resource: 'web' }
  const ranges = accept.toLowerCase().split(',').map((range) => {
    const [type, ...parameters] = range.trim().split(';').map((s) => s.trim())
    const q = parameters.find((p) => p.startsWith('q='))
    const quality = q ? Number(q.slice(2)) : 1
    return { type, quality: Number.isFinite(quality) && quality >= 0 && quality <= 1 ? quality : 0 }
  })
  const matches = [...types].map(([type, resource], order) => {
    const found = ranges.filter((r) => r.type === type || r.type === type.split('/')[0] + '/*' || r.type === '*/*')
      .sort((a, b) => (b.type === type ? 2 : b.type === '*/*' ? 0 : 1) - (a.type === type ? 2 : a.type === '*/*' ? 0 : 1))
    return { resource, quality: found[0]?.quality || 0, order }
  }).filter((m) => m.quality > 0).sort((a, b) => b.quality - a.quality || a.order - b.order)
  return matches.length ? { code, resource: matches[0].resource } : { error: 406 }
}
