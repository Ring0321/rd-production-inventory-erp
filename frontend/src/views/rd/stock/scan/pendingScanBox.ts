import Dexie, { type Table } from 'dexie'
import {
  parseScanCode,
  scanScopeKey,
  type ScanDraft,
  type ScanScope,
  type ScanState
} from './scanCandidates'

const states: ScanState[] = ['LOCAL', 'VERIFIED', 'CONFLICT', 'PROCESSED']
const allowedFields = new Set([
  'ownerId',
  'tenant',
  'kind',
  'taskId',
  'id',
  'packageCode',
  'observedVersion',
  'seenAt',
  'state',
  'reason'
])
const identity = (scope: ScanScope, code: string) =>
  `${scanScopeKey(scope)}:${JSON.stringify(code)}`
const assertRow = (row: ScanDraft) => {
  if (
    Object.keys(row).some((key) => !allowedFields.has(key)) ||
    row.id !== identity(row, row.packageCode) ||
    parseScanCode(row.packageCode) !== row.packageCode ||
    !Number.isSafeInteger(row.observedVersion) ||
    row.observedVersion < 0 ||
    !Number.isSafeInteger(row.seenAt) ||
    row.seenAt <= 0 ||
    !states.includes(row.state) ||
    (row.reason !== undefined && (typeof row.reason !== 'string' || row.reason.length > 160))
  )
    throw new Error('本机扫码记录损坏，请保留原待核请求并重新核对')
  return row
}

export const createPendingScanBox = (name = 'duoxu-scan-candidates-v1') => {
  const db = new Dexie(name)
  db.version(1).stores({
    candidates: 'id,[ownerId+tenant],[ownerId+tenant+kind+taskId],seenAt'
  })
  const table = db.table('candidates') as Table<ScanDraft, string>
  const list = async (scope: ScanScope) => {
    scanScopeKey(scope)
    const rows = await table
      .where('[ownerId+tenant+kind+taskId]')
      .equals([scope.ownerId, scope.tenant, scope.kind, scope.taskId])
      .toArray()
    return rows.map(assertRow).sort((a, b) => a.seenAt - b.seenAt)
  }
  const listOwner = async (ownerId: number, tenant: string) => {
    scanScopeKey({ ownerId, tenant, kind: 'loading', taskId: 1 })
    const rows = await table.where('[ownerId+tenant]').equals([ownerId, tenant]).toArray()
    return rows.map(assertRow).sort((a, b) => b.seenAt - a.seenAt)
  }
  const collect = async (
    scope: ScanScope,
    version: number,
    raw: string,
    guard: () => boolean,
    state: ScanState = 'LOCAL',
    reason?: string
  ) => {
    const code = parseScanCode(raw)
    if (!code || code !== raw || !guard()) throw new Error('扫码身份或包码已失效')
    const row = assertRow({
      ownerId: scope.ownerId,
      tenant: scope.tenant,
      kind: scope.kind,
      taskId: scope.taskId,
      id: identity(scope, code),
      packageCode: code,
      observedVersion: version,
      seenAt: Date.now(),
      state,
      reason
    })
    return db.transaction('rw', table, async () => {
      if (!guard()) throw new Error('登录身份或任务已变化')
      const existing = await table.get(row.id)
      if (existing) return assertRow(existing)
      const count = await table
        .where('[ownerId+tenant+kind+taskId]')
        .equals([scope.ownerId, scope.tenant, scope.kind, scope.taskId])
        .count()
      if (count >= 500) throw new Error('本任务待同步箱已达 500 包，请先核对或清理')
      if (!guard()) throw new Error('登录身份或任务已变化')
      await table.add(row)
      return row
    })
  }
  const mark = async (
    scope: ScanScope,
    updates: Array<{ packageCode: string; state: ScanState; reason: string }>,
    guard: () => boolean
  ) =>
    db.transaction('rw', table, async () => {
      if (!guard()) throw new Error('登录身份或任务已变化')
      for (const patch of updates) {
        const key = identity(scope, patch.packageCode)
        const current = await table.get(key)
        if (!current) throw new Error('候选已由另一页面移除，请刷新待同步箱')
        assertRow(current)
        const next = assertRow({ ...current, state: patch.state, reason: patch.reason })
        if (!guard()) throw new Error('登录身份或任务已变化')
        await table.put(next)
      }
    })
  const remove = async (scope: ScanScope, code: string, guard: () => boolean) => {
    if (!guard()) throw new Error('登录身份或任务已变化')
    await table.delete(identity(scope, code))
  }
  return { list, listOwner, collect, mark, remove, close: () => db.close() }
}

export type PendingScanBox = ReturnType<typeof createPendingScanBox>
