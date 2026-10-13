import { ref, watch, onActivated, onDeactivated, onBeforeUnmount } from 'vue'
import { useUserStoreWithOut } from '@/store/modules/user'
import { getAccessToken, getAuthContextRevision, onAuthContextChange } from '@/utils/auth'
import { hasPermission } from '@/directives/permission/hasPermi'
import { RdStockLotApi, type RdStockLotVO } from '@/api/rd/stock/lot'
import { RdPutawayTaskApi, type RdPutawayTaskVO } from '@/api/rd/stock/putaway'

export const useLocationDetails = () => {
  const user = useUserStoreWithOut()
  const ownerId = user.getUser.id
  const stock = ref<{ list: RdStockLotVO[]; total: number }>()
  const tasks = ref<{ list: RdPutawayTaskVO[]; total: number }>()
  const stockError = ref('')
  const taskError = ref('')
  const busy = ref(false)
  let alive = true
  let active = true
  let sequence = 0
  const ownerRevision = getAuthContextRevision()
  const current = (version: number) =>
    alive &&
    active &&
    sequence === version &&
    user.getUser.id === ownerId &&
    getAuthContextRevision() === ownerRevision &&
    !!getAccessToken()
  const allowed = (permission: string, version: number) =>
    current(version) && hasPermission([permission])
  const clear = () => {
    sequence++
    stock.value = undefined
    tasks.value = undefined
    stockError.value = ''
    taskError.value = ''
    busy.value = false
  }
  const stop = onAuthContextChange(clear)
  const stopScope = watch(
    [() => user.getUser.id, () => [...user.getPermissions].sort().join('\0')],
    clear,
    { flush: 'sync' }
  )
  const load = async (locationId: number, stockPage = 1, taskPage = 1) => {
    clear()
    if (!Number.isSafeInteger(locationId) || locationId <= 0) return
    const version = sequence
    if (!current(version)) return
    busy.value = true
    const readStock = async () => {
      if (!allowed('rd:stock-lot:query', version)) {
        stockError.value = '当前岗位无库存台账查看权限'
        return
      }
      try {
        const page = await RdStockLotApi.getPage({ locationId, pageNo: stockPage, pageSize: 8 })
        if (
          !Array.isArray(page.list) ||
          !Number.isSafeInteger(page.total) ||
          page.total < 0 ||
          page.list.some((row: RdStockLotVO) => row.locationId !== locationId)
        )
          throw new Error('位置范围不一致')
        if (allowed('rd:stock-lot:query', version)) stock.value = page
      } catch {
        if (current(version)) stockError.value = '该库位库存读取失败，请重新核对'
      }
    }
    const readTasks = async () => {
      if (!allowed('rd:putaway-task:query', version)) {
        taskError.value = '当前岗位无上架任务查看权限'
        return
      }
      try {
        const page = await RdPutawayTaskApi.getPage({
          plannedLocationId: locationId,
          pageNo: taskPage,
          pageSize: 6
        })
        if (
          !Array.isArray(page.list) ||
          !Number.isSafeInteger(page.total) ||
          page.total < 0 ||
          page.list.some((row) => row.plannedLocationId !== locationId)
        )
          throw new Error('任务位置范围不一致')
        if (allowed('rd:putaway-task:query', version)) tasks.value = page
      } catch {
        if (current(version)) taskError.value = '该库位任务读取失败，请重新核对'
      }
    }
    await Promise.all([readStock(), readTasks()])
    if (current(version)) busy.value = false
  }
  onDeactivated(() => {
    active = false
    clear()
  })
  onActivated(() => {
    active = true
  })
  onBeforeUnmount(() => {
    alive = false
    clear()
    stop()
    stopScope()
  })
  return { stock, tasks, stockError, taskError, busy, load, clear }
}
