import type { AssistantLink, AssistantPage, AssistantReply } from './assistantModel'

export type WorkQueryKind = 'Q03' | 'Q04' | 'Q05'
const definitions = {
  Q03: {
    title: '待检包件',
    count: 'DISTINCT_PACKAGE_COUNT',
    source: ['PACKAGE_UNIT'],
    metrics: [
      ['DISTINCT_PACKAGE_COUNT', '待检包件', 'PACKAGE', '包'],
      ['REGISTERED_WEIGHT_KG', '登记重量', 'KG', 'kg']
    ]
  },
  Q04: {
    title: '质检待复核',
    count: 'BUSINESS_OBJECT_COUNT',
    source: ['QUALITY_INSPECTION', 'QUALITY_INSPECTION_MEMBER'],
    metrics: [
      ['BUSINESS_OBJECT_COUNT', '待复核质检单', 'BUSINESS_OBJECT', '单'],
      ['DISTINCT_PACKAGE_COUNT', '实际覆盖包件', 'PACKAGE', '包']
    ]
  },
  Q05: {
    title: '合格待入库包件',
    count: 'DISTINCT_PACKAGE_COUNT',
    source: ['PACKAGE_UNIT'],
    metrics: [
      ['DISTINCT_PACKAGE_COUNT', '待入库合计', 'PACKAGE', '包'],
      ['QC_PASSED_PACKAGE_COUNT', '合格待办理', 'PACKAGE', '包'],
      ['INBOUND_PENDING_PACKAGE_COUNT', '入库办理中', 'PACKAGE', '包'],
      ['REGISTERED_WEIGHT_KG', '登记重量', 'KG', 'kg']
    ]
  }
} as const

const record = (value: unknown): Record<string, unknown> => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw Error('INVALID_WORK_QUERY')
  return value as Record<string, unknown>
}
const requireFact: (condition: unknown) => asserts condition = (condition) => {
  if (!condition) throw Error('INVALID_WORK_QUERY_FACTS')
}
const integer = (value: unknown): string => {
  if (typeof value === 'number' && Number.isSafeInteger(value) && value >= 0) return String(value)
  requireFact(typeof value === 'string' && /^(?:0|[1-9]\d{0,18})$/.test(value))
  return value
}
const id = (value: unknown): string => {
  requireFact(typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value))
  return value
}
const label = (value: unknown, max = 80): string => {
  requireFact(typeof value === 'string' && value.trim().length > 0 && value.length <= max)
  return value
}
const date = (value: unknown): value is string =>
  typeof value === 'string' && /^\d{4}-\d\d-\d\dT/.test(value) && Number.isFinite(Date.parse(value))
const decimal = (value: unknown): string => {
  requireFact(typeof value === 'string' && /^(?:0|[1-9]\d{0,29})(?:\.\d{1,6})?$/.test(value))
  return value
}
const stateName = (status: unknown) =>
  (
    ({ QC_PENDING: '待检', QC_PASSED: '合格待入库', INBOUND_PENDING: '入库办理中' }) as Record<
      string,
      string
    >
  )[String(status)]

export const answerWorkQuery = (
  queryType: WorkQueryKind,
  raw: unknown,
  pages: readonly AssistantPage[],
  packageAllowed: boolean
): AssistantReply => {
  const data = record(raw),
    request = record(data.request),
    spec = definitions[queryType]
  requireFact(
    request.queryType === queryType && request.warehouseCode == null && request.alertId == null
  )
  requireFact(
    request.pageNo === 1 &&
      request.pageSize === 20 &&
      data.scope === 'TENANT_AUTHORIZED' &&
      data.businessDate == null
  )
  requireFact(['KNOWN', 'PARTIAL', 'UNAVAILABLE'].includes(String(data.quality)))
  const metrics = record(data.metrics)
  requireFact(Object.keys(metrics).length === spec.metrics.length)
  const facts = spec.metrics.map(([key, title, unit, suffix]) => {
    const metric = record(metrics[key])
    requireFact(
      metric.key === key &&
        metric.unit === unit &&
        ['KNOWN', 'PARTIAL', 'UNAVAILABLE'].includes(String(metric.quality))
    )
    if (metric.quality !== 'KNOWN') label(metric.reason, 500)
    if (metric.quality === 'UNAVAILABLE') requireFact(metric.value === null)
    if (metric.quality === 'KNOWN') requireFact(metric.value !== null && metric.value !== undefined)
    const value =
      metric.value == null ? null : unit === 'KG' ? decimal(metric.value) : integer(metric.value)
    if (data.quality === 'KNOWN') requireFact(metric.quality === 'KNOWN')
    if (data.quality === 'UNAVAILABLE') requireFact(metric.quality === 'UNAVAILABLE')
    return {
      label: title,
      value:
        value === null
          ? '待核'
          : `${value} ${suffix}${metric.quality === 'PARTIAL' ? '（部分）' : ''}`
    }
  })
  requireFact(
    Array.isArray(data.items) &&
      data.items.length <= 20 &&
      Array.isArray(data.sources) &&
      Array.isArray(data.notices) &&
      data.notices.length <= 20
  )
  const notices = data.notices.map((value) => label(value, 500))
  if (data.quality === 'UNAVAILABLE') {
    requireFact(
      data.items.length === 0 &&
        data.total === null &&
        data.asOf === null &&
        data.sources.length === 0 &&
        notices.length > 0
    )
    return {
      kind: 'NOTICE',
      title: '本次来源不可用',
      paragraphs: ['未取得可核对的原业务数据，不将空结果视为零。'],
      links: []
    }
  }
  requireFact(date(data.asOf))
  const sourceTypes = data.sources.map((source) => label(record(source).type))
  requireFact(
    sourceTypes.length > 0 &&
      new Set(sourceTypes).size === sourceTypes.length &&
      sourceTypes.every((type) => (spec.source as readonly string[]).includes(type))
  )
  if (data.quality === 'KNOWN') {
    requireFact(sourceTypes.length === spec.source.length)
    const total = integer(data.total)
    requireFact(BigInt(total) === BigInt(integer(record(metrics[spec.count]).value)))
    requireFact(data.items.length === Number(BigInt(total) > 20n ? 20n : BigInt(total)))
  } else requireFact(data.total === null && notices.length > 0)
  if (queryType === 'Q05')
    requireFact(
      BigInt(integer(record(metrics.DISTINCT_PACKAGE_COUNT).value)) ===
        BigInt(integer(record(metrics.QC_PASSED_PACKAGE_COUNT).value)) +
          BigInt(integer(record(metrics.INBOUND_PENDING_PACKAGE_COUNT).value))
    )

  const rows = data.items.map(record),
    ids = rows.map((row) => id(row.sourceId))
  requireFact(new Set(ids).size === ids.length)
  const paragraphs = [
    '统计范围：当前账号授权的全部仓库，不是当前三维图选中的仓库或库区。',
    ...notices
  ]
  const links: AssistantLink[] = []
  for (const [index, row] of rows.entries()) {
    if (queryType === 'Q04') {
      requireFact(
        row.sourceType === 'QUALITY_INSPECTION' &&
          row.status === 'SUBMITTED' &&
          (row.submittedAt === null || date(row.submittedAt))
      )
      requireFact(
        ['KNOWN', 'UNAVAILABLE'].includes(String(row.coverageQuality)) &&
          Array.isArray(row.members) &&
          row.members.length <= 8
      )
      const no = row.inspectionNo == null ? '单号待核' : label(row.inspectionNo)
      let coverage = '实际覆盖范围待核'
      if (row.coverageQuality === 'KNOWN') {
        const count = integer(row.coveredPackageCount),
          sampled = integer(row.sampledPackageCount)
        requireFact(BigInt(count) > 0n && BigInt(sampled) <= BigInt(count))
        requireFact(row.members.length === Number(BigInt(count) > 8n ? 8n : BigInt(count)))
        coverage = `实际覆盖 ${count} 包，抽检 ${sampled} 包`
      } else requireFact(row.coveredPackageCount === null && row.sampledPackageCount === null)
      const memberIds = row.members.map((member) => {
        const item = record(member)
        label(item.packageCode, 32)
        requireFact(typeof item.sampled === 'boolean')
        return id(item.packageId)
      })
      requireFact(new Set(memberIds).size === memberIds.length)
      if (index < 5)
        paragraphs.push(
          `${no}：已提交待复核；${coverage}。${row.submittedAt === null ? '提交时间待核。' : ''}`
        )
      if (
        index < 3 &&
        pages.some((page) => page.path === '/rd/quality/inspection') &&
        BigInt(id(row.sourceId)) <= BigInt(Number.MAX_SAFE_INTEGER)
      )
        links.push({
          path: '/rd/quality/inspection',
          inspectionId: id(row.sourceId),
          title: `查看质检单 ${no}`
        })
    } else {
      requireFact(
        row.sourceType === 'PACKAGE_UNIT' &&
          (queryType === 'Q03'
            ? row.status === 'QC_PENDING'
            : ['QC_PASSED', 'INBOUND_PENDING'].includes(String(row.status)))
      )
      const code = label(row.packageCode, 32)
      const weight =
        row.registeredWeightKg == null ? '重量待核' : `${decimal(row.registeredWeightKg)} kg`
      if (index < 5) paragraphs.push(`${code}：${stateName(row.status)}，${weight}。`)
      if (index < 3 && packageAllowed && /^LD\d{8}[A-Z]\d{4,12}$/.test(code))
        links.push({ path: `/pkg/${code}`, packageCode: code, title: `查看包件 ${code}` })
    }
  }
  if (
    rows.length > 5 ||
    (data.quality === 'KNOWN' && BigInt(integer(data.total)) > BigInt(rows.length))
  )
    paragraphs.push('上方指标为授权范围聚合，下方仅列前 5 条；其余明细见原业务列表。')
  const listPath = queryType === 'Q04' ? '/rd/quality/inspection' : '/rd/stock/package'
  if (pages.some((page) => page.path === listPath))
    links.push({ path: listPath, title: queryType === 'Q04' ? '质量检验列表' : '包码库存列表' })
  return {
    kind: 'ERP',
    title: `${spec.title}${data.quality === 'PARTIAL' ? '（部分结果）' : ''}`,
    paragraphs,
    facts,
    links,
    source:
      queryType === 'Q04'
        ? 'ERP 质检单与实际覆盖成员 · 一致读取快照'
        : 'ERP 包件登记 · 一致读取快照',
    checkedAt: data.asOf
  }
}
