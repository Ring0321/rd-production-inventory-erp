export interface ReplayMember {
  id: number
  packageCode: string
  status: string
}
export interface ReplayEvent {
  id: number
  packageId: number
  packageCode: string
  transactionType: string
  eventTime: string | number | null
  bizDate?: string
  quantity: number | null
  packageWeight: number | null
  weight?: number | null
  locationId?: number
  locationCode?: string
  sourceType?: string
  sourceNo?: string
}
export const canReplay = (roles: string[], permissions: Set<string>) =>
  !roles.includes('rd_forklift_driver') &&
  ['rd:production-task:query', 'rd:package-unit:query', 'rd:stock-transaction:query'].every(
    (p) => permissions.has('*:*:*') || permissions.has(p)
  )
export const recordedTime = (value: unknown): number | undefined => {
  if (typeof value === 'number')
    return Number.isSafeInteger(value) && value >= 0 ? value : undefined
  if (
    typeof value !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$/.test(value)
  )
    return undefined
  const time = Date.parse(value)
  return Number.isFinite(time) ? time : undefined
}
export const escapeTimelineText = (text: string) =>
  text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
export const validateReplayEvents = (members: ReplayMember[], events: ReplayEvent[]) => {
  if (!Array.isArray(members) || !Array.isArray(events)) throw new Error('历史数据格式待核')
  const byId = new Map<number, ReplayMember>(),
    codes = new Set<string>(),
    ids = new Set<number>()
  for (const member of members) {
    if (
      !Number.isSafeInteger(member.id) ||
      member.id <= 0 ||
      !member.packageCode ||
      byId.has(member.id) ||
      codes.has(member.packageCode)
    )
      throw new Error('任务包件身份待核')
    byId.set(member.id, member)
    codes.add(member.packageCode)
  }
  for (const e of events) {
    if (
      !Number.isSafeInteger(e.id) ||
      e.id <= 0 ||
      ids.has(e.id) ||
      byId.get(e.packageId)?.packageCode !== e.packageCode
    )
      throw new Error('流水与任务成员不一致')
    ids.add(e.id)
  }
  return [...events].sort(
    (a, b) =>
      (recordedTime(a.eventTime) ?? Infinity) - (recordedTime(b.eventTime) ?? Infinity) ||
      a.id - b.id
  )
}
const inboundTypes = new Set([
  'OPENING',
  'PRODUCTION_IN',
  'INBOUND',
  'TRANSFER_IN',
  'RETURN_IN',
  'ADJUST_IN'
])
const outboundTypes = new Set(['OUTBOUND', 'TRANSFER_OUT', 'ADJUST_OUT'])
export const replaySnapshot = (members: ReplayMember[], events: ReplayEvent[], time: number) => {
  const checked = validateReplayEvents(members, events)
  const last = new Map<number, ReplayEvent>(),
    uncertain = new Set<number>()
  for (const e of checked) {
    const at = recordedTime(e.eventTime)
    if (at === undefined) {
      uncertain.add(e.packageId)
      continue
    }
    if (at <= time) last.set(e.packageId, e)
  }
  let outCount = 0,
    unknownCount = 0,
    unknownWeightCount = 0,
    weightMicros = 0
  const positions: Array<{
    packageId: number
    packageCode: string
    locationId?: number
    locationCode?: string
    weightKg: number | null
    lastEvent: ReplayEvent
  }> = []
  for (const member of members) {
    const e = last.get(member.id)
    if (!e || uncertain.has(member.id)) {
      unknownCount++
      continue
    }
    if (e.quantity === -1 && outboundTypes.has(e.transactionType)) {
      outCount++
      continue
    }
    if (e.quantity !== 1 || !inboundTypes.has(e.transactionType)) {
      unknownCount++
      continue
    }
    const w = e.packageWeight
    const micros = typeof w === 'number' ? Math.round(w * 1e6) : NaN
    const validWeight =
      typeof w === 'number' &&
      Number.isFinite(w) &&
      w > 0 &&
      Number.isSafeInteger(micros) &&
      Math.abs(micros / 1e6 - w) < 1e-10
    if (!validWeight || !Number.isSafeInteger(weightMicros + micros)) unknownWeightCount++
    else weightMicros += micros
    positions.push({
      packageId: member.id,
      packageCode: member.packageCode,
      locationId: e.locationId,
      locationCode: e.locationCode,
      weightKg: validWeight ? w : null,
      lastEvent: e
    })
  }
  return {
    positions,
    inStockCount: positions.length,
    outCount,
    unknownCount,
    unknownWeightCount,
    weightKg: unknownWeightCount ? null : weightMicros / 1e6
  }
}
export const movementText = (type: string) =>
  ({
    OPENING: '期初登记',
    PRODUCTION_IN: '生产入库',
    INBOUND: '入库',
    OUTBOUND: '出库',
    TRANSFER_IN: '调入',
    TRANSFER_OUT: '调出',
    RETURN_IN: '退货入库',
    ADJUST_IN: '盘盈/调增',
    ADJUST_OUT: '盘亏/调减'
  })[type] || '类型待核'
