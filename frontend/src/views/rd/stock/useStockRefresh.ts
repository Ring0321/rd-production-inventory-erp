import { onActivated, onBeforeUnmount, onDeactivated, onMounted, ref } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { getTenantId, getVisitTenantId } from '@/utils/auth'

export const useStockRefresh = (
  allowed: () => boolean,
  refresh: (canApply: () => boolean) => Promise<boolean | void>,
  ready: () => boolean = () => true
) => {
  const user = useUserStore()
  const ownerId = user.getUser.id
  const tenant = String(getVisitTenantId() ?? getTenantId() ?? '')
  const error = ref('')
  let alive = true
  let active = true
  let running = false
  let revision = 0
  let timer: ReturnType<typeof setInterval> | undefined
  const current = () =>
    alive &&
    active &&
    user.getUser.id === ownerId &&
    String(getVisitTenantId() ?? getTenantId() ?? '') === tenant
  const run = async () => {
    if (!current() || !allowed() || !ready() || running || document.visibilityState !== 'visible')
      return
    const request = ++revision
    running = true
    const canApply = () =>
      current() && allowed() && request === revision && document.visibilityState === 'visible'
    try {
      const applied = await refresh(canApply)
      if (canApply() && applied !== false) error.value = ''
    } catch {
      if (canApply()) error.value = '数据更新失败，当前数据未刷新'
    } finally {
      running = false
    }
  }
  const stop = () => {
    if (timer !== undefined) clearInterval(timer)
    timer = undefined
    revision++
  }
  const start = () => {
    if (timer === undefined) timer = setInterval(run, 5000)
  }
  const visible = () => {
    if (document.visibilityState === 'visible') void run()
  }
  onMounted(() => {
    start()
    document.addEventListener('visibilitychange', visible)
  })
  onActivated(() => {
    active = true
    start()
    void run()
  })
  onDeactivated(() => {
    active = false
    stop()
  })
  onBeforeUnmount(() => {
    alive = false
    stop()
    document.removeEventListener('visibilitychange', visible)
  })
  return { error, current, run }
}
