export type ScanKind = 'putaway' | 'loading'
export type ScanState = 'LOCAL' | 'VERIFIED' | 'CONFLICT' | 'PROCESSED'

export interface ScanScope {
  ownerId: number
  tenant: string
  kind: ScanKind
  taskId: number
}
export interface ScanMember {
  lineId: number
  packageCode: string
  packageWeight?: number
  eligible: boolean
}
export interface ScanSnapshot {
  kind: ScanKind
  taskId: number
  taskNo: string
  version: number
  status: string
  lines: ScanMember[]
}
export interface ScanDraft extends ScanScope {
  id: string
  packageCode: string
  observedVersion: number
  seenAt: number
  state: ScanState
  reason?: string
}
export const validScope = (scope: ScanScope) =>
  Number.isSafeInteger(scope.ownerId) &&
  scope.ownerId > 0 &&
  !!scope.tenant &&
  scope.tenant.length <= 64 &&
  ['putaway', 'loading'].includes(scope.kind) &&
  Number.isSafeInteger(scope.taskId) &&
  scope.taskId > 0

export const scanScopeKey = (scope: ScanScope) => {
  if (!validScope(scope)) throw new Error('扫码身份或任务无效')
  return JSON.stringify([scope.ownerId, scope.tenant, scope.kind, scope.taskId])
}
export const parseScanCode = (raw: unknown): string | undefined => {
  if (typeof raw !== 'string' || raw.length > 1024) return
  let code = raw.trim()
  if (/^https?:\/\//i.test(code)) {
    try {
      const url = new URL(code)
      if (url.username || url.password || url.search || url.hash || /[\\\u0000-\u001f]/u.test(code))
        return
      const path = url.pathname.match(/^\/(?:[A-Za-z0-9_-]+\/)*pkg\/([^/]+)$/)
      if (!path) return
      code = decodeURIComponent(path[1])
    } catch {
      return
    }
  }
  return /^[A-Za-z0-9][A-Za-z0-9_.-]{0,127}$/.test(code) ? code : undefined
}
export const snapshotValid = (snapshot: ScanSnapshot) =>
  Number.isSafeInteger(snapshot.taskId) &&
  snapshot.taskId > 0 &&
  Number.isSafeInteger(snapshot.version) &&
  snapshot.version >= 0 &&
  ['putaway', 'loading'].includes(snapshot.kind) &&
  snapshot.lines.length <= 500 &&
  snapshot.lines.every(
    (line) =>
      Number.isSafeInteger(line.lineId) &&
      line.lineId > 0 &&
      parseScanCode(line.packageCode) === line.packageCode
  ) &&
  new Set(snapshot.lines.map((line) => line.lineId)).size === snapshot.lines.length &&
  new Set(snapshot.lines.map((line) => line.packageCode)).size === snapshot.lines.length

export const taskOpen = (snapshot: ScanSnapshot) =>
  (snapshot.kind === 'loading' ? ['ASSIGNED', 'LOADING'] : ['ASSIGNED', 'MOVING']).includes(
    snapshot.status
  )

export const classifyScan = (raw: unknown, snapshot: ScanSnapshot, collected: string[]) => {
  const code = parseScanCode(raw)
  if (!code || !snapshotValid(snapshot)) return { code, kind: 'INVALID', label: '无法核对包码' }
  const line = snapshot.lines.find((item) => item.packageCode === code)
  if (!line) return { code, kind: 'WRONG', label: '非本任务包件' }
  if (!taskOpen(snapshot) || !line.eligible)
    return { code, kind: 'UNAVAILABLE', label: '已处理或状态不可用' }
  if (collected.includes(code)) return { code, kind: 'DUPLICATE', label: '重复识别' }
  return { code, kind: 'MATCH', label: '本任务候选', lineId: line.lineId }
}

export const reconcileDraft = (draft: ScanDraft, snapshot: ScanSnapshot) => {
  if (!snapshotValid(snapshot) || draft.kind !== snapshot.kind || draft.taskId !== snapshot.taskId)
    throw new Error('服务器任务身份不一致')
  if (draft.observedVersion !== snapshot.version)
    return { state: 'CONFLICT' as const, reason: '任务版本已变化，请核对现场后重新采集' }
  const line = snapshot.lines.find((member) => member.packageCode === draft.packageCode)
  if (!line) return { state: 'CONFLICT' as const, reason: '不在当前任务清单' }
  if (!line.eligible) return { state: 'PROCESSED' as const, reason: '服务器记录已处理或不可选择' }
  if (!taskOpen(snapshot)) return { state: 'CONFLICT' as const, reason: '当前任务不可核验' }
  if (!Number.isFinite(line.packageWeight) || (line.packageWeight ?? -1) < 0)
    return { state: 'CONFLICT' as const, reason: '包件登记重量待核' }
  return { state: 'VERIFIED' as const, reason: '本次服务器核对通过，仍需仓管确认' }
}

export const candidateWeight = (codes: string[], snapshot: ScanSnapshot) => {
  const members = [...new Set(codes)].map((code) =>
    snapshot.lines.find((member) => member.packageCode === code)
  )
  if (members.some((line) => !line || !Number.isFinite(line.packageWeight))) return undefined
  const amount = members.reduce((sum, line) => sum + line!.packageWeight!, 0)
  return Number.isFinite(amount) && amount >= 0 ? Number(amount.toFixed(6)) : undefined
}
