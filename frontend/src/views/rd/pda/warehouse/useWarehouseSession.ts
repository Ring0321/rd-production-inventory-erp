import { onActivated, onBeforeUnmount, onDeactivated } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { getAccessToken, getCurrentUserId, getTenantId, getVisitTenantId } from '@/utils/auth'
import { hasPermission } from '@/directives/permission/hasPermi'

export const useWarehouseSession = () => {
  const user = useUserStore()
  const ownerId = user.getUser.id
  const tenant = String(getVisitTenantId() ?? getTenantId() ?? '')
  let alive = true
  let active = true
  let revision = 0
  const current = () =>
    alive &&
    active &&
    !!getAccessToken() &&
    getCurrentUserId() === ownerId &&
    user.getUser.id === ownerId &&
    String(getVisitTenantId() ?? getTenantId() ?? '') === tenant
  const allowed = (permission: string) => current() && hasPermission([permission])
  const assertCurrent = () => {
    if (!current()) throw new Error('登录身份或页面已变化，请用原账号和租户核对原请求')
  }
  onBeforeUnmount(() => {
    alive = false
    revision++
  })
  onDeactivated(() => {
    active = false
    revision++
  })
  onActivated(() => {
    active = true
  })
  return { ownerId, tenant, current, allowed, assertCurrent, generation: () => revision }
}

export type WarehouseSession = ReturnType<typeof useWarehouseSession>
