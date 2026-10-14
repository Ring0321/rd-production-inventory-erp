import { ref, watch, onMounted, onActivated, onDeactivated, onBeforeUnmount } from 'vue'
import { useUserStoreWithOut } from '@/store/modules/user'
import { getAccessToken, getAuthContextRevision, onAuthContextChange } from '@/utils/auth'
import { canViewWarehouseSpace } from './warehouseSpaceAccess'
import { RdPutawayTaskApi, type RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'
import { useStockRefresh } from '../useStockRefresh'

export const useWarehouseSpace = () => {
  const user = useUserStoreWithOut()
  const ownerId = user.getUser.id
  const ownerRevision = getAuthContextRevision()
  const locations = ref<RdPutawayLocationMapVO[]>([])
  const busy = ref(false)
  const updatedAt = ref<number>()
  const snapshotVersion = ref(0)
  const contextValid = ref(true)
  let active = true
  let alive = true
  let sequence = 0
  let signature = ''
  const current = () =>
    alive &&
    active &&
    contextValid.value &&
    !!getAccessToken() &&
    getAuthContextRevision() === ownerRevision &&
    user.getUser.id === ownerId &&
    canViewWarehouseSpace(user.getRoles, user.getPermissions)
  const clear = () => {
    sequence++
    locations.value = []
    signature = ''
    updatedAt.value = undefined
    snapshotVersion.value++
    busy.value = false
  }
  const load = async (canApply: () => boolean) => {
    if (!current()) return false
    const request = ++sequence
    busy.value = true
    try {
      const data = await RdPutawayTaskApi.getLocationMap()
      if (!current() || !canApply() || request !== sequence) return false
      if (!Array.isArray(data)) throw new Error('库位数据格式不正确')
      const nextSignature = JSON.stringify(data)
      if (nextSignature !== signature) {
        locations.value = data
        signature = nextSignature
      }
      snapshotVersion.value++
      updatedAt.value = Date.now()
      return true
    } finally {
      if (request === sequence) busy.value = false
    }
  }
  const sync = useStockRefresh(current, load, () => !busy.value)
  const invalidate = () => {
    contextValid.value = false
    clear()
  }
  const stopAuth = onAuthContextChange(invalidate)
  const stopScope = watch(
    [
      () => user.getUser.id,
      () => [...user.getPermissions].sort().join('\0'),
      () => user.getRoles.join('\0')
    ],
    invalidate,
    { flush: 'sync' }
  )
  onMounted(() => void sync.run())
  onDeactivated(() => {
    active = false
    clear()
  })
  onActivated(() => {
    active = true
    void sync.run()
  })
  onBeforeUnmount(() => {
    alive = false
    clear()
    stopAuth()
    stopScope()
  })
  return {
    locations,
    busy,
    updatedAt,
    snapshotVersion,
    contextValid,
    error: sync.error,
    refresh: sync.run
  }
}
