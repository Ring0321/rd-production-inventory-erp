import { computed, onBeforeUnmount, onDeactivated, ref, watch } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { hasPermission } from '@/directives/permission/hasPermi'
import {
  getAccessToken,
  getAuthContextRevision,
  getCachedAuthScope,
  getCurrentUserId,
  getTenantId,
  getVisitTenantId,
  onAuthContextChange
} from '@/utils/auth'

export const useVisualAccess = (kind: 'LAYOUT' | 'LABEL') => {
  const user = useUserStore()
  const valid = ref(true)
  const cleanups = new Set<() => void>()
  const signature = () =>
    JSON.stringify([
      !!getAccessToken(),
      getAuthContextRevision(),
      getCurrentUserId(),
      getTenantId(),
      getVisitTenantId(),
      user.getUser.id,
      [...user.getRoles].sort(),
      [...user.getPermissions].sort(),
      getCachedAuthScope()
    ])
  const captured = signature()
  const invalidate = () => {
    if (!valid.value) return
    valid.value = false
    cleanups.forEach((clear) => clear())
  }
  const current = () => {
    if (
      !valid.value ||
      !getAccessToken() ||
      !user.getUser.id ||
      user.getUser.id !== getCurrentUserId() ||
      signature() !== captured
    ) {
      invalidate()
      return false
    }
    return true
  }
  const allowed = (permission: string) => current() && hasPermission([permission])
  const canRead = computed(() =>
    kind === 'LABEL'
      ? allowed('rd:package-unit:query')
      : allowed('rd:warehouse-location:query') || allowed('rd:putaway-task:query')
  )
  const canEdit = computed(
    () =>
      canRead.value &&
      (kind === 'LAYOUT'
        ? allowed('rd:warehouse-location:update')
        : user.getRoles.includes('super_admin'))
  )
  const canPrint = (mode: 'print' | 'reprint') => canRead.value && allowed(`rd:label-print:${mode}`)
  const stopWatch = watch(
    signature,
    () => {
      if (signature() !== captured) invalidate()
    },
    { flush: 'sync' }
  )
  const stopAuth = onAuthContextChange(invalidate)
  const check = () => {
    current()
  }
  const timer = window.setInterval(check, 1000)
  window.addEventListener('focus', check)
  window.addEventListener('storage', check)
  onDeactivated(invalidate)
  onBeforeUnmount(() => {
    invalidate()
    stopWatch()
    stopAuth()
    window.clearInterval(timer)
    window.removeEventListener('focus', check)
    window.removeEventListener('storage', check)
    cleanups.clear()
  })
  return {
    valid,
    current,
    allowed,
    canRead,
    canEdit,
    canPrint,
    onClear: (clear: () => void) => {
      cleanups.add(clear)
    }
  }
}
