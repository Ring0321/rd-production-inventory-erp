import { copy, exactObject, finiteNumber, plainText, rect } from './visualSchema'

export interface LocationBinding {
  id: number
  warehouseCode: string
  warehouseName?: string
  locationCode: string
}
export interface LayoutElement {
  id: string
  kind: 'LOCATION' | 'AISLE' | 'ZONE'
  locationId?: number
  x: number
  y: number
  width: number
  height: number
  label: string
}
export interface LayoutContent {
  schemaVersion: 1
  warehouseCode: string
  width: number
  height: number
  elements: LayoutElement[]
}

export const emptyLayout = (warehouseCode: string): LayoutContent => ({
  schemaVersion: 1,
  warehouseCode,
  width: 1200,
  height: 800,
  elements: []
})

export const warehouseOptions = (locations: readonly LocationBinding[]) =>
  [
    ...new Map(
      locations.map((row) => [
        row.warehouseCode,
        { code: row.warehouseCode, name: row.warehouseName || row.warehouseCode }
      ])
    ).values()
  ].sort((a, b) => a.code.localeCompare(b.code))

export const parseLayout = (
  input: unknown,
  warehouseCode: string,
  locations: readonly LocationBinding[]
): LayoutContent => {
  const value = exactObject(input, [
    'schemaVersion',
    'warehouseCode',
    'width',
    'height',
    'elements'
  ])
  if (value.schemaVersion !== 1 || value.warehouseCode !== warehouseCode)
    throw new Error('布局仓库或 schema 版本不匹配')
  plainText(warehouseCode, 64)
  const width = finiteNumber(value.width, 64, 4096)
  const height = finiteNumber(value.height, 64, 4096)
  if (!Array.isArray(value.elements) || value.elements.length > 1000)
    throw new Error('布局对象数量无效')
  const ids = new Set<string>()
  const bindings = new Set<number>()
  const elements = value.elements.map((raw): LayoutElement => {
    const item = exactObject(raw, [
      'id',
      'kind',
      'locationId',
      'x',
      'y',
      'width',
      'height',
      'label'
    ])
    const id = plainText(item.id, 64)
    if (!/^[A-Za-z0-9_-]+$/.test(id))
      throw new Error('布局对象 ID 仅允许字母、数字、下划线和连字符')
    if (ids.has(id)) throw new Error('布局对象 ID 重复')
    ids.add(id)
    if (!['LOCATION', 'AISLE', 'ZONE'].includes(String(item.kind))) throw new Error('未知对象类型')
    const kind = item.kind as LayoutElement['kind']
    const element: LayoutElement = {
      id,
      kind,
      ...rect(item, width, height),
      label: plainText(item.label, 80)
    }
    if (kind === 'LOCATION') {
      const locationId = finiteNumber(item.locationId, 1, Number.MAX_SAFE_INTEGER)
      if (
        !Number.isSafeInteger(locationId) ||
        bindings.has(locationId) ||
        !locations.some((row) => row.id === locationId && row.warehouseCode === warehouseCode)
      ) {
        throw new Error('库位必须绑定本仓库现有且不重复的库位 ID')
      }
      bindings.add(locationId)
      element.locationId = locationId
    } else if (Object.hasOwn(item, 'locationId')) throw new Error('区域与通道不可绑定库位')
    return element
  })
  return { schemaVersion: 1, warehouseCode, width, height, elements }
}

export const boundPosition = (
  position: { x: number; y: number },
  item: LayoutElement,
  layout: LayoutContent
) => ({
  x: Math.max(
    0,
    Math.min(layout.width - item.width, Number.isFinite(position.x) ? position.x : item.x)
  ),
  y: Math.max(
    0,
    Math.min(layout.height - item.height, Number.isFinite(position.y) ? position.y : item.y)
  )
})

export const createHistory = <T>(initial: T) => {
  const baseline = copy(initial)
  let current = copy(initial)
  const stack: T[] = []
  return {
    value: () => copy(current),
    canUndo: () => stack.length > 0,
    change(next: T) {
      if (JSON.stringify(next) === JSON.stringify(current)) return copy(current)
      stack.push(copy(current))
      if (stack.length > 50) stack.shift()
      current = copy(next)
      return copy(current)
    },
    undo() {
      current = stack.pop() ?? current
      return copy(current)
    },
    reset() {
      stack.push(copy(current))
      current = copy(baseline)
      return copy(current)
    }
  }
}
