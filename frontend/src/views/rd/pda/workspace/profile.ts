import { extractPackageCode } from '@/utils/rdDigitalLink'

export type WorkspaceProfileId =
  | 'production'
  | 'driver'
  | 'quality'
  | 'warehouse'
  | 'sales'
  | 'query'
export type TaskQuery = 'production' | 'putaway' | 'loading'
export type RouteAvailable = (name: string) => boolean
export type WorkspacePermissions = ReadonlySet<string>

export interface WorkspaceEntry {
  id: string
  label: string
  routeName: string
  required: string[]
  kind: 'pda' | 'web' | 'query'
}

export const profileLabels: Record<WorkspaceProfileId, string> = {
  production: '生产',
  driver: '叉车',
  quality: '质检',
  warehouse: '仓管',
  sales: '销售查询',
  query: '查询'
}

const roleProfiles: Record<string, WorkspaceProfileId> = {
  rd_production: 'production',
  rd_quality: 'quality',
  rd_warehouse: 'warehouse',
  rd_warehouse_operator: 'warehouse',
  rd_forklift_driver: 'driver',
  rd_sales: 'sales'
}

const roleLabels: Record<string, string> = {
  rd_production: '生产',
  rd_quality: '质检',
  rd_warehouse: '仓管',
  rd_warehouse_operator: '库房操作',
  rd_forklift_driver: '叉车司机',
  rd_sales: '销售',
  rd_audit: '审核',
  rd_readonly: '只读',
  admin: '管理',
  super_admin: '管理'
}

export const getRoleLabel = (roles: readonly string[]) =>
  [
    ...new Set(
      roles.map((role) => (Object.hasOwn(roleLabels, role) ? roleLabels[role] : '自定义岗位'))
    )
  ].join('、') || '未配置岗位'

export const hasWorkspacePermission = (permissions: WorkspacePermissions, permission: string) =>
  permissions.has('*:*:*') || permissions.has(permission)

const hasAny = (permissions: WorkspacePermissions, values: readonly string[]) =>
  values.some((permission) => hasWorkspacePermission(permissions, permission))

const entry = (
  id: string,
  label: string,
  routeName: string,
  required: string[],
  kind: WorkspaceEntry['kind'] = 'query'
): WorkspaceEntry => ({ id, label, routeName, required, kind })

const queryEntries = [
  entry('task', '生产任务查询', 'RdProductionTask', ['rd:production-task:query']),
  entry('record', '产量记录查询', 'RdProductionOutput', ['rd:production-output:query']),
  entry('quality', '检验单查询', 'RdQualityInspection', ['rd:quality-inspection:query']),
  entry('location', '仓库货位查询', 'RdWarehouseLocation', ['rd:warehouse-location:query']),
  entry('sale', '销售订单查询', 'RdSaleOrder', ['rd:sale-order:query']),
  entry('slip', '提货单', 'RdPickupSlip', ['rd:pickup-slip:query']),
  entry('goods', '包码库存', 'RdPackageUnit', ['rd:package-unit:query']),
  entry('stock', '库存台账', 'RdStockLot', ['rd:stock-lot:query']),
  entry('transaction', '库存流水', 'RdStockTransaction', ['rd:stock-transaction:query']),
  entry('matrix', '库存动态明细', 'RdStockMatrix', ['rd:stock-matrix:query']),
  entry('statistics', '每日出入库统计', 'RdDailyStockSummary', ['rd:daily-stock-summary:query']),
  entry('dashboard', '库存看板', 'RdInventoryDashboard', ['rd:dashboard:query']),
  entry('product', '产品资料', 'RdProductMaster', ['rd:product-master:query'])
]

const entries: Record<WorkspaceProfileId, WorkspaceEntry[]> = {
  production: [
    entry(
      'production',
      '生产录入',
      'PdaProduction',
      ['rd:production-task:query', 'rd:production-output:create'],
      'pda'
    ),
    entry('task', '生产任务', 'RdProductionTask', ['rd:production-task:query'], 'web'),
    entry('record', '产量记录', 'RdProductionOutput', ['rd:production-output:query'], 'web'),
    entry('labels', '包码与标签', 'RdPackageUnit', ['rd:package-unit:query'], 'web')
  ],
  driver: [
    entry('putaway', '本人上架任务', 'PdaDriverPutaway', ['rd:putaway-task:execute'], 'pda'),
    entry('loading', '本人装车任务', 'PdaDriverLoading', ['rd:loading-task:execute'], 'pda')
  ],
  quality: [
    entry('quality', '质量检验', 'RdQualityInspection', ['rd:quality-inspection:query'], 'web'),
    entry('goods', '包码库存', 'RdPackageUnit', ['rd:package-unit:query'])
  ],
  warehouse: [
    entry('putaway', '入库上架', 'RdPutawayWorkbench', ['rd:putaway-task:query'], 'web'),
    entry('loading', '出库装车', 'RdLoadingDispatch', ['rd:loading-task:query'], 'web'),
    entry('location', '仓库货位', 'RdWarehouseLocation', ['rd:warehouse-location:query'], 'web'),
    entry('inbound', '入库单', 'RdInboundOrder', ['rd:inbound-order:query'], 'web'),
    entry('outbound', '出库单', 'RdOutboundOrder', ['rd:outbound-order:query'], 'web'),
    entry('goods', '包码库存', 'RdPackageUnit', ['rd:package-unit:query']),
    entry('stock', '库存台账', 'RdStockLot', ['rd:stock-lot:query'])
  ],
  sales: queryEntries,
  query: queryEntries
}

export const getProfileEntries = (
  profile: WorkspaceProfileId | undefined,
  permissions: WorkspacePermissions,
  hasRoute: RouteAvailable
) =>
  (profile ? entries[profile] : []).filter(
    (item) =>
      hasRoute(item.routeName) &&
      item.required.every((grant) => hasWorkspacePermission(permissions, grant))
  )

export const canLookupPackage = (permissions: WorkspacePermissions, hasRoute: RouteAvailable) =>
  hasRoute('PackageTrace') &&
  hasAny(permissions, [
    'rd:package-unit:query',
    'rd:putaway-task:execute',
    'rd:loading-task:execute',
    'rd:pickup-slip:query'
  ])

const operationalCapabilities: Record<Exclude<WorkspaceProfileId, 'query'>, string[]> = {
  production: [
    'rd:production-output:create',
    'rd:production-task:create',
    'rd:production-task:update',
    'rd:production-output:confirm',
    'rd:label-print:print'
  ],
  driver: ['rd:putaway-task:execute', 'rd:loading-task:execute'],
  quality: [
    'rd:quality-inspection:create',
    'rd:quality-inspection:submit',
    'rd:quality-inspection:approve'
  ],
  warehouse: [
    'rd:putaway-task:create',
    'rd:putaway-task:assign',
    'rd:putaway-task:confirm',
    'rd:loading-task:dispatch',
    'rd:inbound-order:create',
    'rd:outbound-order:create'
  ],
  sales: ['rd:pickup-slip:create', 'rd:sale-order:create']
}

export const getWorkspaceProfiles = (
  roles: readonly string[],
  permissions: WorkspacePermissions,
  hasRoute: RouteAvailable
): Array<{ id: WorkspaceProfileId; label: string }> => {
  const queryAvailable =
    getProfileEntries('query', permissions, hasRoute).length > 0 ||
    canLookupPackage(permissions, hasRoute)
  const query: WorkspaceProfileId[] = queryAvailable ? ['query'] : []
  let candidates: WorkspaceProfileId[]
  if (roles.some((role) => role === 'rd_audit' || role === 'rd_readonly')) {
    candidates = query
  } else {
    const admin = roles.some((role) => role === 'super_admin' || role === 'admin')
    const known = [
      ...new Set(
        roles.filter((role) => Object.hasOwn(roleProfiles, role)).map((role) => roleProfiles[role])
      )
    ]
    const capable = (
      Object.keys(operationalCapabilities) as Array<Exclude<WorkspaceProfileId, 'query'>>
    ).filter((id) => hasAny(permissions, operationalCapabilities[id]))
    const operational = (admin || !known.length ? capable : known).filter(
      (id) => getProfileEntries(id, permissions, hasRoute).length > 0
    )
    candidates = admin
      ? queryAvailable || operational.length
        ? ['query', ...operational]
        : []
      : known.length
        ? operational
        : operational.length
          ? operational
          : query
  }
  return candidates.map((id) => ({ id, label: profileLabels[id] }))
}

export const getTaskQueries = (
  profile: WorkspaceProfileId | undefined,
  permissions: WorkspacePermissions,
  hasRoute: RouteAvailable
): TaskQuery[] => {
  if (
    profile === 'production' &&
    hasWorkspacePermission(permissions, 'rd:production-task:query') &&
    (hasRoute('RdProductionTask') || hasRoute('PdaProduction'))
  )
    return ['production']
  if (profile !== 'driver') return []
  const queries: TaskQuery[] = []
  if (
    hasWorkspacePermission(permissions, 'rd:putaway-task:execute') &&
    hasRoute('PdaDriverPutaway')
  )
    queries.push('putaway')
  if (
    hasWorkspacePermission(permissions, 'rd:loading-task:execute') &&
    hasRoute('PdaDriverLoading')
  )
    queries.push('loading')
  return queries
}

export const getPackageTraceTarget = (input: string) => {
  const packageCode = extractPackageCode(input)
  if (
    !packageCode ||
    packageCode.length > 128 ||
    /[\s\\/?#:\u0000-\u001f\u007f]/u.test(packageCode)
  )
    return undefined
  return { name: 'PackageTrace', params: { packageCode }, query: { workMode: 'pda' } }
}
