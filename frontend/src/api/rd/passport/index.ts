import { getTenantId, getVisitTenantId } from '@/utils/auth'

export const PASSPORT_SCHEMA = 'DUOXU_PUBLIC_PACKAGE_V1'
export const PASSPORT_MAX_BYTES = 1048576
export interface PackagePassport {
  schema: string
  algorithm: string
  digest: string
  generatedAt: string
  canonicalJson: string
}
export interface PublicPackageDocument {
  '@context': { '@vocab': string }
  '@type': string
  '@id': string
  profile: string
  packageCode: string
  product: {
    name: string | null
    model: string | null
    furnaceNo: string | null
    furnaceLayer: string | null
    weightKg: string | null
    productionDate: string | null
    furnaceOutDate: string | null
    shift: string | null
  }
  workflow: { status: string; qualityState: string; confirmedQuality: 'PASSED' | 'REJECTED' | null }
  events: Array<{ stage: string; eventTime: string; timeBasis: string }>
}

const keysAre = (value: unknown, keys: string[]) =>
  !!value &&
  typeof value === 'object' &&
  !Array.isArray(value) &&
  Object.keys(value).length === keys.length &&
  Object.keys(value).every((key) => keys.includes(key))
const nullableText = (value: unknown) =>
  value === null || (typeof value === 'string' && value.length <= 2000)
const stages = new Set([
  'CREATED',
  'LABELED',
  'QC_PENDING',
  'QC_PASSED',
  'QC_REJECTED',
  'INBOUND_PENDING',
  'IN_STOCK',
  'STOCKTAKE_MISSING',
  'OUTBOUND_PENDING',
  'OUTBOUNDED',
  'VOIDED',
  'UNKNOWN'
])
const qualities = new Set([
  'DRAFT',
  'SUBMITTED',
  'CORRECTED',
  'RESUBMITTED',
  'PASSED',
  'REJECTED',
  'UNKNOWN'
])
export const validatePassport = (
  value: unknown,
  expectedCode?: string
): { passport: PackagePassport; document: PublicPackageDocument } => {
  const p = value as PackagePassport
  if (
    !keysAre(p, ['schema', 'algorithm', 'digest', 'generatedAt', 'canonicalJson']) ||
    p.schema !== PASSPORT_SCHEMA ||
    p.algorithm !== 'SHA-256' ||
    !/^[a-f0-9]{64}$/.test(p.digest) ||
    typeof p.canonicalJson !== 'string' ||
    new TextEncoder().encode(p.canonicalJson).length > PASSPORT_MAX_BYTES ||
    typeof p.generatedAt !== 'string' ||
    !Number.isFinite(Date.parse(p.generatedAt))
  )
    throw new Error('护照结构或版本无效')
  const d = JSON.parse(p.canonicalJson) as PublicPackageDocument
  if (
    !keysAre(d, [
      '@context',
      '@type',
      '@id',
      'profile',
      'packageCode',
      'product',
      'workflow',
      'events'
    ]) ||
    !keysAre(d['@context'], ['@vocab']) ||
    d['@context']['@vocab'] !== 'urn:duoxu:public-package:v1:' ||
    d['@type'] !== 'PackagePassport' ||
    d.profile !== PASSPORT_SCHEMA ||
    typeof d.packageCode !== 'string' ||
    !/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(d.packageCode) ||
    (expectedCode && d.packageCode !== expectedCode)
  )
    throw new Error('护照身份或公开范围无效')
  let url: URL
  try {
    url = new URL(d['@id'])
  } catch {
    throw new Error('护照链接无效')
  }
  if (
    !['http:', 'https:'].includes(url.protocol) ||
    url.username ||
    url.password ||
    url.search ||
    url.hash ||
    !decodeURIComponent(url.pathname).endsWith(`/pkg/${d.packageCode}`)
  )
    throw new Error('护照链接与包码不匹配')
  if (
    !keysAre(d.product, [
      'name',
      'model',
      'furnaceNo',
      'furnaceLayer',
      'weightKg',
      'productionDate',
      'furnaceOutDate',
      'shift'
    ]) ||
    !Object.values(d.product).every(nullableText) ||
    (d.product.weightKg !== null && !/^-?\d+(?:\.\d+)?$/.test(d.product.weightKg)) ||
    !keysAre(d.workflow, ['status', 'qualityState', 'confirmedQuality']) ||
    !stages.has(d.workflow.status) ||
    !qualities.has(d.workflow.qualityState) ||
    (d.workflow.confirmedQuality !== null &&
      (!['PASSED', 'REJECTED'].includes(d.workflow.confirmedQuality) ||
        d.workflow.confirmedQuality !== d.workflow.qualityState)) ||
    !Array.isArray(d.events) ||
    d.events.length > 5000 ||
    !d.events.every(
      (event) =>
        keysAre(event, ['stage', 'eventTime', 'timeBasis']) &&
        ['PRODUCTION', 'LABEL', 'QUALITY', 'STOCK'].includes(event.stage) &&
        typeof event.eventTime === 'string' &&
        event.eventTime.length <= 40 &&
        event.timeBasis === 'ERP_LOCAL_DATETIME'
    )
  )
    throw new Error('护照内容未通过公开字段校验')
  return { passport: p, document: d }
}

export const verifyPassport = async (value: unknown, expectedCode?: string) => {
  const parsed = validatePassport(value, expectedCode)
  if (!globalThis.crypto?.subtle)
    throw new Error('当前环境无法校验摘要，请使用HTTPS或受支持的本机浏览器')
  const bytes = await crypto.subtle.digest(
    'SHA-256',
    new TextEncoder().encode(parsed.passport.canonicalJson)
  )
  const digest = [...new Uint8Array(bytes)].map((v) => v.toString(16).padStart(2, '0')).join('')
  if (digest !== parsed.passport.digest) throw new Error('文件内容摘要不匹配')
  return parsed
}

export const getPublicPassport = async (code: string, signal?: AbortSignal) => {
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(code)) throw new Error('包码无效')
  const tenant = getVisitTenantId() ?? getTenantId()
  const base = `${import.meta.env.VITE_BASE_URL || ''}${import.meta.env.VITE_API_URL || '/admin-api'}`
  const res = await fetch(`${base}/rd/package-link/${encodeURIComponent(code)}/passport`, {
    headers: { Accept: 'application/json', ...(tenant ? { 'tenant-id': String(tenant) } : {}) },
    credentials: 'omit',
    cache: 'no-store',
    signal
  })
  if (!res.ok || !res.headers.get('content-type')?.includes('application/json'))
    throw new Error('公开护照读取失败')
  const text = await res.text()
  if (new TextEncoder().encode(text).length > PASSPORT_MAX_BYTES * 2)
    throw new Error('公开护照文件过大')
  return validatePassport(JSON.parse(text), code)
}
