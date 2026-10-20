import {
  interpretAssistantQuestion,
  searchAssistantPages,
  type AssistantPage,
  type AssistantReply
} from './assistantModel'
import { answerAssistantKnowledge } from './assistantKnowledge'

export interface AssistantContext {
  pages: AssistantPage[]
  currentPath: string
  warehouseCode: string
  allowed: (permission: string) => boolean
  valid: () => boolean
}

export interface AssistantReaders {
  inventory: () => Promise<unknown>
  locations: () => Promise<unknown>
  trace: (code: string) => Promise<unknown>
  packageContext: (code: string) => Promise<unknown>
}

const record = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === 'object' && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {}
const text = (value: unknown, length = 120) =>
  typeof value === 'string' && value.trim() ? value.trim().slice(0, length) : '未记录'
const number = (value: unknown, integer = true): value is number =>
  typeof value === 'number' &&
  Number.isFinite(value) &&
  value >= 0 &&
  (!integer || Number.isSafeInteger(value))
const metric = (value: unknown, unit: string, integer = true) =>
  number(value, integer)
    ? `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 3 }).format(value)} ${unit}`
    : '待核'
const notice = (title: string, paragraph: string): AssistantReply => ({
  kind: 'NOTICE',
  title,
  paragraphs: [paragraph],
  links: []
})
const assertCurrent = (context: AssistantContext) => {
  if (!context.valid()) throw Error('ASSISTANT_CONTEXT_CHANGED')
}
const date = (value: unknown) =>
  Array.isArray(value) && value.length >= 3
    ? value
        .slice(0, 3)
        .map((part) => String(part).padStart(2, '0'))
        .join('-')
    : text(value, 20)

export const answerAssistantQuestion = async (
  question: string,
  context: AssistantContext,
  readers: AssistantReaders
): Promise<AssistantReply> => {
  assertCurrent(context)
  const intent = interpretAssistantQuestion(question)
  const permissions: Record<string, string> = {
    INVENTORY: 'rd:dashboard:query',
    MOVEMENT: 'rd:dashboard:query',
    LOCATIONS: 'rd:putaway-task:query',
    PACKAGE: 'rd:package-unit:query'
  }
  const permission = permissions[intent.kind]
  if (permission && !context.allowed(permission))
    return notice(
      '当前账号无查询权限',
      '没有读取这项业务数据的权限。请使用当前岗位的任务入口，或联系对应仓管、质检或管理员核对。'
    )
  const links = (query: string) => searchAssistantPages(query, context.pages).slice(0, 3)
  if (intent.kind === 'READ_ONLY')
    return notice(
      '小垛只做查询与导引',
      '不会执行修改、删除、放行、提交或库存过账，也不会绕过权限或接收外部查询地址。业务办理请进入原页面，由有权岗位确认。'
    )
  if (intent.kind === 'CLARIFY')
    return notice(
      '请明确本次查询范围',
      '可以查当前库存、今天出入库、登记库位，或一次核对一个完整包码。历史区间请在原报表选择日期；不会把当前汇总当作历史结果。'
    )
  if (intent.kind === 'GREETING')
    return {
      kind: 'LOCAL',
      title: '你好，我是智能小垛',
      paragraphs: ['想找哪个业务入口，或核对哪项数据？'],
      links: links('货位图'),
      source: '站内助手 · 本地解析'
    }
  if (intent.kind === 'CURRENT_PAGE') {
    const page = context.pages.find((item) => item.path === context.currentPath)
    return (
      (page && answerAssistantKnowledge(page.title, context.pages)) || {
        kind: 'LOCAL',
        title: page?.title || '当前页面',
        paragraphs: [
          '可以按页面名称查找业务入口，也可以核对库存汇总、包件状态，或询问具体作业流程。'
        ],
        links: [],
        source: '站内页面导引'
      }
    )
  }
  if (intent.kind === 'KNOWLEDGE') {
    const answer = answerAssistantKnowledge(question, context.pages)
    if (answer) return answer
  }
  if (intent.kind === 'INVENTORY' || intent.kind === 'MOVEMENT') {
    const data = record(await readers.inventory())
    assertCurrent(context)
    const movement = intent.kind === 'MOVEMENT'
    const values = record(data[movement ? 'todayMovement' : 'stock'])
    return {
      kind: 'ERP',
      title: movement ? '今天出入库汇总' : '当前库存看板汇总',
      paragraphs: ['这是库存看板的授权汇总，不是当前仓库或库区筛选结果；包码总数不等于在库数。'],
      facts: movement
        ? [
            { label: '今日入库', value: metric(values.inboundQty, '包') },
            { label: '今日出库', value: metric(values.outboundQty, '包') },
            { label: '今日生产', value: metric(values.productionQty, '包') },
            {
              label: '净变动',
              value:
                typeof values.netChangeQty === 'number' && Number.isSafeInteger(values.netChangeQty)
                  ? `${values.netChangeQty} 包`
                  : '待核'
            }
          ]
        : [
            { label: '在库包数', value: metric(values.warehouseQty, '包') },
            { label: '可用包数', value: metric(values.availableQty, '包') },
            { label: '锁定包数', value: metric(values.lockedQty, '包') },
            { label: '登记库存重量', value: metric(values.stockWeight, 'kg', false) }
          ],
      links: links('库存看板'),
      source: `ERP 库存看板 · 截至 ${date(data.asOfDate)}`,
      checkedAt: new Date().toISOString()
    }
  }
  if (intent.kind === 'LOCATIONS') {
    const data = await readers.locations()
    assertCurrent(context)
    if (!Array.isArray(data)) throw Error('INVALID_LOCATION_FACTS')
    const items = data
      .map(record)
      .filter((item) => !context.warehouseCode || item.warehouseCode === context.warehouseCode)
    if (
      items.some((item) => !number(item.id) || item.id === 0) ||
      new Set(items.map((item) => item.id)).size !== items.length
    )
      throw Error('INVALID_LOCATION_IDENTITY')
    const levels = new Map<string, number>()
    for (const item of items) {
      const level = text(item.levelCode, 30)
      levels.set(level, (levels.get(level) || 0) + 1)
    }
    const sum = (field: string) =>
      items.every((item) => number(item[field], field !== 'occupiedWeight'))
        ? items.reduce((total, item) => total + Number(item[field]), 0)
        : undefined
    return {
      kind: 'ERP',
      title: '仓储登记汇总',
      paragraphs: [
        context.warehouseCode
          ? `查询范围：仓库 ${context.warehouseCode}，包含所有登记层级。`
          : '查询范围：当前账号可读取的全部仓库。',
        ...[...levels]
          .sort(([a], [b]) => a.localeCompare(b, 'zh-CN', { numeric: true }))
          .map(
            ([level, count]) =>
              `${level === '未记录' ? '未登记层级' : level + ' 层'}：${count} 个库位。`
          )
      ],
      facts: [
        { label: '登记库位', value: metric(items.length, '个') },
        { label: '在库包数', value: metric(sum('occupiedPackages'), '包') },
        { label: '登记重量', value: metric(sum('occupiedWeight'), 'kg', false) }
      ],
      links: links('货位图'),
      source: 'ERP 仓储空间 · 登记值，非自动定位',
      checkedAt: new Date().toISOString()
    }
  }
  if (intent.kind === 'PACKAGE' && intent.packageCode) {
    const code = intent.packageCode
    const trace = record(await readers.trace(code))
    assertCurrent(context)
    if (trace.packageCode !== code || trace.internalView !== true)
      return notice(
        '未取得这包的内部记录',
        '请核对完整包码及当前账号的查询权限。未取得记录不代表现场没有这包货物。'
      )
    if (!context.allowed('rd:package-unit:query')) throw Error('ASSISTANT_CONTEXT_CHANGED')
    const facts = record(await readers.packageContext(code))
    assertCurrent(context)
    if (facts.packageCode !== code) throw Error('PACKAGE_IDENTITY_CHANGED')
    const source = record(facts.source)
    const diagnosis = record(facts.diagnosis)
    const findings = Array.isArray(diagnosis.findings)
      ? diagnosis.findings.map(record).slice(0, 2)
      : []
    const paragraphs = [
      source.state === 'KNOWN'
        ? `同一生产产出记录登记 ${metric(source.packageCount, '包')} / ${metric(source.recordedWeight, 'kg', false)}；不等于整张生产任务或整个炉次。`
        : '同源登记范围待核，不推断整批数量。',
      ...(diagnosis.state === 'KNOWN'
        ? findings.length
          ? findings.map((item) => `${text(item.title)}：${text(item.detail, 500)}`)
          : ['本地规则未列出核对项；仍须按实际检验、任务和仓管确认办理。']
        : ['本地规则检查范围待核，不作“可出库”或“无异常”结论。'])
    ]
    return {
      kind: 'ERP',
      title: code,
      paragraphs,
      facts: [
        { label: '流程状态', value: text(trace.statusName) },
        { label: '登记库位', value: text(trace.locationCode) },
        { label: '登记净重', value: metric(trace.packageWeight, 'kg', false) },
        {
          label: '质检状态',
          value:
            (
              {
                PASSED: '合格',
                REJECTED: '不合格',
                PENDING: '待检',
                SUBMITTED: '待复核'
              } as Record<string, string>
            )[String(trace.qualityStatus)] || text(trace.qualityStatus)
        }
      ],
      links: [{ path: `/pkg/${code}`, packageCode: code, title: '打开这包货物的追溯资料' }],
      source: 'ERP 包件追溯 + 扫码问货本地规则',
      checkedAt: new Date().toISOString()
    }
  }
  const found = searchAssistantPages(question, context.pages)
  return found.length
    ? {
        kind: 'LOCAL',
        title: '找到这些业务入口',
        paragraphs: [],
        links: found,
        source: '当前账号可见菜单'
      }
    : notice(
        '暂未找到匹配内容',
        '当前可见菜单中没有匹配入口，或这个问题暂不在已核定的流程知识范围。可以换一个业务名称；没有权限的页面不会出现在结果里。'
      )
}
