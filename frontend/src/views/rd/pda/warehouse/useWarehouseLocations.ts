import { computed, reactive, ref, shallowRef } from 'vue'
import { RdPutawayTaskApi, type RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'
import type { WarehouseSession } from './useWarehouseSession'

const labels: Record<string, string> = {
  EMPTY: '空闲',
  AVAILABLE: '可用',
  PARTIAL: '部分占用',
  OCCUPIED: '已占用',
  FULL: '已满',
  RESERVED: '已预占',
  FROZEN: '冻结',
  DISABLED: '停用'
}

export const locationLabel = (row: RdPutawayLocationMapVO) =>
  !row.enabled ? '停用' : row.frozen ? '冻结' : labels[row.displayStatus] || row.displayStatus

export const amountText = (value: number | undefined, unit: string, absent = '未设置') =>
  value === undefined || value === null ? absent : `${value.toLocaleString('zh-CN')} ${unit}`

export const useWarehouseLocations = (session: WarehouseSession) => {
  const canQuery = computed(() => session.allowed('rd:putaway-task:query'))
  const rows = shallowRef<RdPutawayLocationMapVO[]>([])
  const loading = ref(false)
  const error = ref('')
  const readTime = ref('')
  const filter = reactive({ warehouse: '', keyword: '', state: '' })
  let revision = 0
  const warehouses = computed(() =>
    [...new Map(rows.value.map((row) => [row.warehouseCode, row.warehouseName])).entries()].map(
      ([code, name]) => ({ code, name })
    )
  )
  const filtered = computed(() => {
    const term = filter.keyword.trim().toLowerCase()
    return rows.value.filter(
      (row) =>
        (!filter.warehouse || row.warehouseCode === filter.warehouse) &&
        (!filter.state || locationLabel(row) === filter.state) &&
        (!term ||
          [row.locationCode, row.locationName, row.areaCode, row.areaName].some((value) =>
            value?.toLowerCase().includes(term)
          ))
    )
  })
  const load = async (canApply = session.current) => {
    if (!session.allowed('rd:putaway-task:query')) return
    const request = ++revision
    loading.value = true
    error.value = ''
    try {
      const data = await RdPutawayTaskApi.getLocationMap()
      if (!session.current() || !canApply() || request !== revision) return false
      if (!Array.isArray(data)) throw new Error('库位数据格式异常')
      rows.value = data
      readTime.value = new Date().toLocaleTimeString('zh-CN')
      return true
    } catch {
      if (session.current() && request === revision)
        error.value = '库位状态读取失败，当前数据未刷新'
      return false
    } finally {
      if (request === revision) loading.value = false
    }
  }
  return { canQuery, rows, loading, error, readTime, filter, warehouses, filtered, load }
}
