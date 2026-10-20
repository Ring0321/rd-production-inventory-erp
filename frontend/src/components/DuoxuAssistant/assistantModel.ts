export interface AssistantMenu {
  path: string
  meta?: { title?: unknown; hidden?: boolean }
  children?: AssistantMenu[]
}

export interface AssistantLink {
  path: string
  title: string
  breadcrumb?: string
  packageCode?: string
}

export interface AssistantPage extends AssistantLink {
  breadcrumb: string
  aliases: string[]
}

export interface AssistantReply {
  title: string
  paragraphs: string[]
  links: AssistantLink[]
  kind: 'LOCAL' | 'ERP' | 'NOTICE'
  facts?: Array<{ label: string; value: string }>
  source?: string
  checkedAt?: string
}

export interface AssistantMessage {
  id: number
  question: string
  reply?: AssistantReply
}

const internalPath = (path: string) =>
  /^\/[A-Za-z0-9/_-]*$/.test(path) &&
  !path.includes('//') &&
  !/^\/(?:redirect|login|social-login|403|404|500)(?:\/|$)/.test(path)
const bagPattern = /^LD\d{8}[A-Z]\d{4,12}$/
const normalize = (value: string) => value.normalize('NFKC').toLowerCase().replace(/\s/g, '')
const pageAliases = (title: string, path: string) => {
  const aliases = [title]
  const groups: Array<[RegExp, string[]]> = [
    [/产品/, ['产品', '产品信息', '型号资料', '产品主数据', '品种']],
    [/库存看板/, ['库存总览', '库存汇总', '库存统计', '库存看板']],
    [
      /仓储空间|warehouse-space/,
      ['3D', '三维', '立体库位', '货位图', '库位图', '仓库地图', '二层仓库']
    ],
    [/仓库货位|货位资料/, ['货位', '库位维护', '库位资料', '货位登记']],
    [/入库上架/, ['入库', '上架', '仓管入库']],
    [/出库装车/, ['装车', '出库', '发货', '仓管出库']],
    [/生产任务/, ['生产计划', '生产任务', '客户生产']],
    [/质量检验/, ['质检', '检验', '抽检']],
    [/包码|包件|吨袋/, ['包码', '吨袋', '二维码', '货物追溯']],
    [/提货单/, ['提货', '提货单', '商务发货']],
    [/库存台账/, ['台账', '库存明细']],
    [/作业智助/, ['配吨', 'OCR', '单据识别', '标签模板', '布局编辑', '时间回放']],
    [/业务全景/, ['全流程', '业务流程', '流程展示']]
  ]
  for (const [pattern, words] of groups) if (pattern.test(title + path)) aliases.push(...words)
  return aliases
}

// The input is the current account's visible menu, not router.getRoutes().
export const collectAssistantPages = (routes: readonly AssistantMenu[]): AssistantPage[] => {
  const pages = new Map<string, AssistantPage>()
  const visit = (nodes: readonly AssistantMenu[], parent = '', trail: string[] = [], depth = 0) => {
    if (depth > 8 || pages.size >= 300) return
    for (const node of nodes) {
      if (node.meta?.hidden || typeof node.path !== 'string') continue
      const path = node.path.startsWith('/')
        ? node.path
        : `${parent}/${node.path}`.replace(/\/$/, '')
      if (!internalPath(path)) continue
      const title = typeof node.meta?.title === 'string' ? node.meta.title.trim().slice(0, 80) : ''
      const breadcrumb = title && trail.at(-1) !== title ? [...trail, title] : trail
      if (node.children?.length)
        visit(node.children, path === '/' ? '' : path, breadcrumb, depth + 1)
      else if (title)
        pages.set(path, {
          path,
          title,
          breadcrumb: breadcrumb.join(' / '),
          aliases: pageAliases(title, path)
        })
    }
  }
  visit(routes)
  return [...pages.values()]
}

export const searchAssistantPages = (question: string, pages: readonly AssistantPage[]) => {
  const query = normalize(question).replace(
    /帮我|请问|我想|我要|找一下|查一下|看一下|在哪里|在哪儿|在哪|打开|进入|跳转|入口|页面|一下|有没有|找|看|的/g,
    ''
  )
  if (!query || query.length > 200) return []
  return pages
    .map((page) => ({
      page,
      score: Math.max(
        0,
        ...page.aliases.map((alias) => {
          const term = normalize(alias)
          return term === query
            ? 60
            : term.includes(query)
              ? 40
              : term.length >= 2 && query.includes(term)
                ? 12 + Math.min(term.length, 12)
                : 0
        })
      )
    }))
    .filter((item) => item.score > 0)
    .sort((a, b) => b.score - a.score || a.page.title.localeCompare(b.page.title, 'zh-CN'))
    .slice(0, 6)
    .map((item) => item.page)
}

export type AssistantIntent = {
  kind:
    | 'NAVIGATE'
    | 'INVENTORY'
    | 'MOVEMENT'
    | 'LOCATIONS'
    | 'PACKAGE'
    | 'KNOWLEDGE'
    | 'CURRENT_PAGE'
    | 'GREETING'
    | 'READ_ONLY'
    | 'CLARIFY'
  packageCode?: string
}

export const interpretAssistantQuestion = (question: string): AssistantIntent => {
  const text = question.normalize('NFKC').trim()
  if (!text || text.length > 200) return { kind: 'CLARIFY' }
  if (/https?:\/\/|忽略.*权限|绕过|SQL|密钥|密码|system\s*prompt/i.test(text))
    return { kind: 'READ_ONLY' }
  if (
    /删除|扣库存|改成|设为|帮我.*(?:更新|修改|放行|确认|提交|过账)|自动.*(?:出库|入库|放行)/.test(
      text
    )
  )
    return { kind: 'READ_ONLY' }
  const packages = [...new Set(text.toUpperCase().match(/\bLD\d{8}[A-Z]\d{4,12}\b/g) || [])]
  if (packages.length > 1) return { kind: 'CLARIFY' }
  if (packages.length === 1) return { kind: 'PACKAGE', packageCode: packages[0] }
  if (/包码.*[<>{}]|昨天|上周|上个月|去年|\d{4}[-/年]\d/.test(text)) return { kind: 'CLARIFY' }
  if (/(?:这个|当前|本)(?:页|页面)/.test(text)) return { kind: 'CURRENT_PAGE' }
  if (/^(?:你好|您好|嗨|hello|hi)[，, !！\s]*(?:小垛)?$/i.test(text)) return { kind: 'GREETING' }
  if (/打开|找|在哪|入口|页面|进入|跳转/.test(text)) return { kind: 'NAVIGATE' }
  if (/今天|今日|本日/.test(text) && /出库|入库|出入库/.test(text)) return { kind: 'MOVEMENT' }
  if (/库位|货位|仓库/.test(text) && /多少|数量|统计|占用/.test(text)) return { kind: 'LOCATIONS' }
  if (/库存|在库/.test(text) && /多少|汇总|总量|重量|包数|统计|查/.test(text))
    return { kind: 'INVENTORY' }
  if (
    /怎么|如何|谁|为什么|区别|流程|步骤|需要|能不能|可以|批次扫码|一包一码|弱网|离线|GLM|DeepSeek|大模型/i.test(
      text
    )
  )
    return { kind: 'KNOWLEDGE' }
  return { kind: 'NAVIGATE' }
}

export const canOpenAssistantLink = (
  link: AssistantLink,
  pages: readonly AssistantPage[],
  packageAllowed: boolean
) =>
  link.packageCode !== undefined
    ? packageAllowed &&
      bagPattern.test(link.packageCode) &&
      link.path === `/pkg/${link.packageCode}`
    : internalPath(link.path) && pages.some((page) => page.path === link.path)
