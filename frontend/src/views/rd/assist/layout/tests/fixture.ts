import { reactive } from 'vue'
import { defaultLabel } from '../../templates/labelModel'
import type { VisualDocumentVO } from './visualDocument.stub'

const clone = <T>(value: T): T => JSON.parse(JSON.stringify(value))
const user = reactive({ id: 1, roles: ['super_admin'], permissions: new Set(['*:*:*']) })
let revision = 0
const listeners = new Set<() => void>()
export const fixture = {
  conflict: false,
  unknown: false,
  audit: [] as unknown[],
  revoke: () => {
    user.roles = ['reader']
    user.permissions = new Set()
    revision++
    listeners.forEach((fn) => fn())
  },
  resetAuth: () => {
    user.roles = ['super_admin']
    user.permissions = new Set(['*:*:*'])
    revision++
    listeners.forEach((fn) => fn())
  }
}
export const useUserStore = () => ({
  get getUser() {
    return user
  },
  get getRoles() {
    return user.roles
  },
  get getPermissions() {
    return user.permissions
  }
})
export const hasPermission = (values: string[]) =>
  user.permissions.has('*:*:*') || values.some((key) => user.permissions.has(key))
export const getAccessToken = () => true
export const getCurrentUserId = () => 1
export const getAuthContextRevision = () => revision
export const getTenantId = () => 1
export const getVisitTenantId = () => null
export const getCachedAuthScope = () => ({
  userId: user.id,
  roles: user.roles,
  permissions: [...user.permissions]
})
export const onAuthContextChange = (fn: () => void) => {
  listeners.add(fn)
  return () => listeners.delete(fn)
}
const rows = [
  { id: 11, warehouseCode: 'WH-A', warehouseName: '合成一库', locationCode: 'A-01' },
  { id: 12, warehouseCode: 'WH-A', warehouseName: '合成一库', locationCode: 'A-02' },
  { id: 21, warehouseCode: 'WH-B', warehouseName: '合成二库', locationCode: 'B-01' }
]
export const RdWarehouseLocationApi = { getOptions: async () => clone(rows) }
export const RdPutawayTaskApi = { getLocationMap: async () => clone(rows) }
const documents = new Map<string, VisualDocumentVO>()
export const getVisualDocument = async (kind: 'LAYOUT' | 'LABEL', scopeId: string) => {
  const key = `${kind}:${scopeId}`
  if (!documents.has(key)) {
    const content =
      kind === 'LABEL'
        ? defaultLabel()
        : {
            schemaVersion: 1,
            warehouseCode: scopeId,
            width: 1200,
            height: 800,
            elements: [
              {
                id: 'zone',
                kind: 'ZONE',
                x: 70,
                y: 50,
                width: 800,
                height: 550,
                label: '合成区域'
              },
              {
                id: 'aisle',
                kind: 'AISLE',
                x: 90,
                y: 400,
                width: 750,
                height: 100,
                label: '合成通道'
              },
              {
                id: 'bin-1',
                kind: 'LOCATION',
                locationId: scopeId === 'WH-A' ? 11 : 21,
                x: 130,
                y: 150,
                width: 180,
                height: 110,
                label: scopeId === 'WH-A' ? 'A-01' : 'B-01'
              }
            ]
          }
    documents.set(key, {
      kind,
      scopeId,
      version: 1,
      draft: content,
      published: clone(content),
      publishedVersion: 1,
      updatedAt: '2026-10-15T12:00:00Z'
    })
  }
  return clone(documents.get(key)!)
}
export const saveVisualDocument = async (arg: {
  kind: 'LAYOUT' | 'LABEL'
  scopeId: string
  expectedVersion: number
  content: unknown
}) => {
  const record = await getVisualDocument(arg.kind, arg.scopeId)
  if (fixture.conflict || record.version !== arg.expectedVersion)
    throw { response: { status: 409 } }
  record.version++
  record.draft = clone(arg.content)
  documents.set(`${arg.kind}:${arg.scopeId}`, record)
  return clone(record)
}
export const publishVisualDocument = async (arg: {
  kind: 'LAYOUT' | 'LABEL'
  scopeId: string
  expectedVersion: number
}) => {
  const record = await getVisualDocument(arg.kind, arg.scopeId)
  if (fixture.conflict || record.version !== arg.expectedVersion)
    throw { response: { status: 409 } }
  record.version++
  record.published = clone(record.draft)
  record.publishedVersion = record.version
  documents.set(`${arg.kind}:${arg.scopeId}`, record)
  return clone(record)
}
const packages = [
  {
    id: 7,
    packageCode: 'SYNTH-0007',
    qrCode: 'LOCAL-ORIGINAL-QR-SYNTH-0007',
    customerName: '合成客户甲',
    productModel: '测试型号甲',
    furnaceNo: 'F-01',
    furnaceLayer: '中层',
    packageWeight: 1000,
    productionDate: '2026-10-15',
    version: 1,
    status: 'CREATED',
    productionOutputId: 1
  },
  {
    id: 8,
    packageCode: 'SYNTH-0008',
    qrCode: 'LOCAL-ORIGINAL-QR-SYNTH-0008',
    customerName: '合成客户乙',
    productModel: '测试型号乙',
    furnaceNo: 'F-02',
    furnaceLayer: '上层',
    packageWeight: 500,
    productionDate: '2026-10-14',
    version: 2,
    status: 'LABELED',
    productionOutputId: 2
  }
]
const audit = async (mode: string, data: { packageId: number; reprintReason?: string }) => {
  fixture.audit.push({ mode, data: clone(data) })
  const item = packages.find((row) => row.id === data.packageId)!
  item.status = 'LABELED'
  item.version++
  if (fixture.unknown) throw new Error('synthetic network loss after audit')
  return true
}
export const RdPackageUnitApi = {
  getPage: async (params: { packageCode?: string }) => ({
    list: clone(
      packages.filter((row) => !params.packageCode || row.packageCode.includes(params.packageCode))
    ),
    total: packages.length
  }),
  get: async (id: number) => clone(packages.find((row) => row.id === id)!),
  print: (data: { packageId: number }) => audit('print', data),
  reprint: (data: { packageId: number; reprintReason?: string }) => audit('reprint', data)
}
