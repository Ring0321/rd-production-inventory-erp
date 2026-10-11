import { onActivated, onBeforeUnmount, onDeactivated, ref, shallowRef } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { getAccessToken, getCurrentUserId, getTenantId, getVisitTenantId } from '@/utils/auth'
import { hasPermission } from '@/directives/permission/hasPermi'

interface DriverTask {
  id: number
  status: string
}

export const useDriverTasks = <TTask extends DriverTask, TDetail extends { task: TTask }>(
  permission: string,
  page: (params: PageParam) => Promise<PageResult<TTask[]>>,
  read: (id: number) => Promise<TDetail>
) => {
  const user = useUserStore()
  const ownerId = user.getUser.id
  const tenant = String(getVisitTenantId() ?? getTenantId() ?? '')
  let alive = true
  let active = true
  let revision = 0
  let detailRevision = 0
  const current = () =>
    alive &&
    active &&
    !!getAccessToken() &&
    getCurrentUserId() === ownerId &&
    user.getUser.id === ownerId &&
    String(getVisitTenantId() ?? getTenantId() ?? '') === tenant
  const allowed = () => current() && hasPermission([permission])
  const assertCurrent = () => {
    if (!allowed()) throw new Error('账号、租户或页面已变化，请重新打开本人任务')
  }
  const taskList = shallowRef<TTask[]>([])
  const taskTotal = ref(0)
  const taskPageNo = ref(0)
  const taskLoading = ref(false)
  const taskLoadingMore = ref(false)
  const detailLoading = ref(false)
  const taskDetail = shallowRef<TDetail>()
  const targetId = ref<number>()
  const readError = ref('')
  const loadTasks = async (reset = true) => {
    if (!allowed() || taskLoading.value || taskLoadingMore.value) return
    const expected = revision
    const nextPage = reset ? 1 : taskPageNo.value + 1
    if (reset) taskLoading.value = true
    else taskLoadingMore.value = true
    readError.value = ''
    try {
      const result = await page({ pageNo: nextPage, pageSize: 20 })
      if (!allowed() || expected !== revision) return
      if (!Array.isArray(result?.list) || !Number.isSafeInteger(result.total) || result.total < 0)
        throw new Error('任务列表返回不完整，请重试')
      taskList.value = reset ? result.list : [...taskList.value, ...result.list]
      taskTotal.value = result.total
      taskPageNo.value = nextPage
    } catch (cause) {
      if (current() && expected === revision)
        readError.value = cause instanceof Error ? cause.message : '本人任务读取失败，请重试'
    } finally {
      taskLoading.value = false
      taskLoadingMore.value = false
    }
  }
  const openTask = async (id: number) => {
    if (!allowed() || !Number.isSafeInteger(id) || id <= 0) return
    const expected = ++detailRevision
    const sessionRevision = revision
    detailLoading.value = true
    targetId.value = id
    taskDetail.value = undefined
    readError.value = ''
    try {
      const value = await read(id)
      if (!allowed() || expected !== detailRevision || sessionRevision !== revision) return
      if (!value?.task || value.task.id !== id) throw new Error('任务返回不一致，请重新读取')
      taskDetail.value = value
    } catch (cause) {
      if (current() && expected === detailRevision && sessionRevision === revision)
        readError.value = cause instanceof Error ? cause.message : '本人任务不可读取，请重试'
    } finally {
      if (expected === detailRevision) detailLoading.value = false
    }
  }
  const backToList = async () => {
    if (!allowed()) return
    detailRevision++
    taskDetail.value = undefined
    detailLoading.value = false
    targetId.value = undefined
    await loadTasks(true)
  }
  const refreshTaskDetail = async () => {
    if (taskDetail.value) await openTask(taskDetail.value.task.id)
  }
  const invalidate = () => {
    revision++
    detailRevision++
    detailLoading.value = false
  }
  onDeactivated(() => {
    active = false
    invalidate()
  })
  onActivated(() => {
    active = true
  })
  onBeforeUnmount(() => {
    alive = false
    invalidate()
  })
  return {
    taskList,
    taskTotal,
    taskPageNo,
    taskLoading,
    taskLoadingMore,
    detailLoading,
    taskDetail,
    targetId,
    readError,
    loadTasks,
    loadMore: () => loadTasks(false),
    openTask,
    backToList,
    refreshTaskDetail,
    retryDetail: () => targetId.value && openTask(targetId.value),
    current,
    allowed,
    assertCurrent,
    generation: () => revision
  }
}
