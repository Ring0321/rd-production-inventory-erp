import type { PackageRelations, PackageRelationNode } from '@/api/rd/stock/package/relations'

export const relationKinds: Record<string, string> = {
  PRODUCTION_TASK: '生产任务',
  PRODUCTION_OUTPUT: '生产批次',
  PACKAGE: '包件',
  QUALITY_INSPECTION: '质检记录',
  POSITION: '登记库位',
  PUTAWAY_TASK: '上架任务',
  LOADING_TASK: '装车任务',
  OUTBOUND_ORDER: '出库单',
  STOCK_TRANSACTION: '库存流水',
  LOCATION: '登记库位',
  LOCATION_SNAPSHOT: '历史库位快照',
  TASK_EVENT: '任务历史',
  OUTBOUND_LINE: '出库明细'
}
export const validateRelations = (data: PackageRelations, code: string) => {
  if (
    !data ||
    data.rootPackageCode !== code ||
    data.scope !== 'ROOT_GROUPS' ||
    !Array.isArray(data.nodes) ||
    !Array.isArray(data.edges) ||
    !Array.isArray(data.sections) ||
    data.nodes.length > 500 ||
    data.edges.length > 1000 ||
    data.sections.length > 20
  )
    throw new Error('关系资料范围或包件身份无效')
  const ids = new Set<string>()
  for (const node of data.nodes) {
    if (
      typeof node.nodeKey !== 'string' ||
      node.nodeKey.length > 256 ||
      !/^[A-Z_]+:/.test(node.nodeKey) ||
      !(node.kind in relationKinds) ||
      typeof node.label !== 'string' ||
      node.label.length > 500 ||
      ids.has(node.nodeKey)
    )
      throw new Error('关系节点身份无效')
    ids.add(node.nodeKey)
  }
  const root = data.nodes.find((node) => node.nodeKey === data.rootNodeKey)
  if (!root || root.kind !== 'PACKAGE' || root.packageCode !== code)
    throw new Error('关系根包件不一致')
  const edges = new Set<string>()
  for (const edge of data.edges) {
    if (
      typeof edge.edgeKey !== 'string' ||
      edges.has(edge.edgeKey) ||
      !ids.has(edge.fromNodeKey) ||
      !ids.has(edge.toNodeKey) ||
      typeof edge.evidenceType !== 'string' ||
      !edge.evidenceType ||
      typeof edge.label !== 'string'
    )
      throw new Error('关系证据或连接无效')
    edges.add(edge.edgeKey)
  }
  for (const section of data.sections) {
    if (
      !['COMPLETE', 'REDACTED', 'LIMITED', 'INCOMPLETE'].includes(section.state) ||
      !Number.isSafeInteger(section.returnedCount) ||
      section.returnedCount < 0 ||
      (section.state === 'REDACTED' && section.returnedCount !== 0)
    )
      throw new Error('关系范围说明无效')
  }
  return data
}
export const relationView = (
  data: PackageRelations,
  mode: 'ROOT' | 'RELATED' | 'QUALITY',
  qualityKey?: string
) => {
  if (mode === 'QUALITY') {
    const inspection = data.nodes.find(
      (node) => node.nodeKey === qualityKey && node.kind === 'QUALITY_INSPECTION'
    )
    if (!inspection) return { nodes: [], edges: [] }
    const members = qualityAffectedPackages(data, inspection)
    const keys = new Set(members.map((node) => node.nodeKey))
    return {
      nodes: [inspection, ...members],
      edges: data.edges.filter(
        (edge) =>
          edge.fromNodeKey === qualityKey &&
          keys.has(edge.toNodeKey) &&
          edge.relation === 'QUALITY_COVERAGE' &&
          edge.evidenceType === 'QUALITY_INSPECTION_MEMBER'
      )
    }
  }
  const detailKinds = new Set([
    'TASK_EVENT',
    'STOCK_TRANSACTION',
    'OUTBOUND_LINE',
    'LOCATION_SNAPSHOT'
  ])
  const nodes = data.nodes.filter(
    (node) =>
      mode === 'RELATED' ||
      (!detailKinds.has(node.kind) &&
        (node.kind !== 'PACKAGE' || node.nodeKey === data.rootNodeKey))
  )
  const keys = new Set(nodes.map((node) => node.nodeKey))
  return {
    nodes,
    edges: data.edges.filter((edge) => keys.has(edge.fromNodeKey) && keys.has(edge.toNodeKey))
  }
}
export const qualityAffectedPackages = (data: PackageRelations, node: PackageRelationNode) => {
  if (node.kind !== 'QUALITY_INSPECTION') return []
  const keys = new Set(
    data.edges
      .filter(
        (edge) =>
          edge.fromNodeKey === node.nodeKey &&
          edge.relation === 'QUALITY_COVERAGE' &&
          edge.evidenceType === 'QUALITY_INSPECTION_MEMBER'
      )
      .map((edge) => edge.toNodeKey)
  )
  return data.nodes.filter((member) => member.kind === 'PACKAGE' && keys.has(member.nodeKey))
}
export const qualitySampleLabel = (
  data: PackageRelations,
  inspectionKey: string,
  packageKey: string
) => {
  const flags = data.edges
    .filter(
      (edge) =>
        edge.fromNodeKey === inspectionKey &&
        edge.toNodeKey === packageKey &&
        edge.relation === 'QUALITY_COVERAGE' &&
        edge.evidenceType === 'QUALITY_INSPECTION_MEMBER'
    )
    .map((edge) => edge.sampled)
  if (flags.length && flags.every((flag) => flag === true)) return '抽检样包'
  if (flags.length && flags.every((flag) => flag === false)) return '覆盖包（非样包）'
  return '样包标识待核'
}
export const relationPalette = (node: PackageRelationNode, root: string) => {
  if (node.nodeKey === root) return { fill: '#e7f8ee', stroke: '#138360' }
  if (node.kind === 'QUALITY_INSPECTION')
    return { fill: '#fff6dc', stroke: node.status === 'REJECTED' ? '#be3f4b' : '#b58224' }
  if (node.kind.startsWith('PRODUCTION')) return { fill: '#edf4ff', stroke: '#779acc' }
  if (node.kind === 'LOADING_TASK') return { fill: '#fff0f3', stroke: '#bc7186' }
  if (node.kind === 'OUTBOUND_ORDER') return { fill: '#e8f8fb', stroke: '#6096a4' }
  return { fill: '#f2f4f6', stroke: '#94a2ad' }
}

export const relationNodeLabel = (node: PackageRelationNode) => {
  const prefixes: Record<string, string> = {
    PRODUCTION_OUTPUT: '批次记录',
    STOCK_TRANSACTION: '流水',
    OUTBOUND_LINE: '明细'
  }
  return prefixes[node.kind] && node.referenceId
    ? `${prefixes[node.kind]} ${node.referenceId}`
    : node.label
}
export const relationLabels: Record<string, string> = {
  PRODUCTION_SOURCE: '来源批次',
  PRODUCTION_TASK_SOURCE: '生产任务',
  OUTPUT_TASK_SOURCE: '任务来源',
  OUTPUT_MEMBER: '实际生产包',
  QUALITY_COVERAGE: '实际覆盖',
  LEGACY_REPRESENTATIVE: '旧版代表记录',
  PUTAWAY_MEMBER: '上架成员',
  LOADING_MEMBER: '装车成员',
  HISTORICAL_PUTAWAY_MEMBER: '历史上架成员',
  HISTORICAL_LOADING_MEMBER: '历史装车成员',
  RECORDED_EVENT: '实际历史记录',
  HISTORICAL_TASK_EVENT: '任务历史',
  HISTORICAL_RELATED_PACKAGE: '历史关联包',
  OUTBOUND_PACKAGE: '出库包件',
  OUTBOUND_ORDER: '所属出库单',
  LOADING_OUTBOUND_LINE: '出库明细',
  LOADING_TRANSACTION: '装车关联流水',
  PUTAWAY_TRANSACTION: '入库关联流水',
  OUTBOUND_TRANSACTION: '出库关联流水',
  RECORDED_TRANSACTION: '库存流水',
  CURRENT_POSITION: '当前登记库位',
  LAST_RECORDED_POSITION: '最近记录库位',
  PLANNED_POSITION: '计划位置',
  RECORDED_FINAL_POSITION: '确认登记位置',
  EVENT_RECORDED_POSITION: '事件位置记录',
  HISTORICAL_PREVIOUS_POSITION: '历史位置',
  TRANSACTION_RECORDED_POSITION: '流水位置记录'
}
