import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { usePermissionStore } from '@/store/modules/permission'
import { CACHE_KEY } from '@/hooks/web/useCache'
import {
  getAccessToken,
  getCurrentUserId,
  getTenantId,
  getVisitTenantId,
  getAuthContextRevision,
  getCachedAuthScope,
  onAuthContextChange
} from '@/utils/auth'
import { RdInventoryDashboardApi } from '@/api/rd/dashboard'
import { RdPutawayTaskApi } from '@/api/rd/stock/putaway'
import { RdPackageUnitApi } from '@/api/rd/stock/package'
import { RdPackageAiApi } from '@/api/rd/ai'
import { RdWorkQueryApi } from '@/api/rd/workassistant'
import {
  canOpenAssistantLink,
  collectAssistantPages,
  type AssistantLink,
  type AssistantMessage
} from './assistantModel'
import { answerAssistantQuestion } from './assistantQueries'

export const useDuoxuAssistant = () => {
  const user = useUserStore()
  const permission = usePermissionStore()
  const route = useRoute()
  const router = useRouter()
  const open = ref(false)
  const input = ref('')
  const busy = ref(false)
  const messages = ref<AssistantMessage[]>([])
  const authSignal = ref(0)
  const foreignAuthChanged = ref(false)
  let alive = true,
    generation = 0,
    sequence = 0
  const scope = () => ({
    userId: user.getUser.id,
    roles: [...user.getRoles].sort(),
    permissions: [...user.getPermissions].sort()
  })
  const signature = () =>
    JSON.stringify([
      getAuthContextRevision(),
      getCurrentUserId(),
      Boolean(getAccessToken()),
      getVisitTenantId() ?? getTenantId(),
      scope(),
      getCachedAuthScope()
    ])
  const available = computed(() => {
    void authSignal.value
    return (
      alive &&
      !foreignAuthChanged.value &&
      user.getIsSetUser &&
      user.getUser.id > 0 &&
      !!getAccessToken() &&
      getCurrentUserId() === user.getUser.id &&
      JSON.stringify(scope()) === JSON.stringify(getCachedAuthScope())
    )
  })
  const allowed = (name: string) =>
    available.value &&
    !user.getRoles.includes('rd_forklift_driver') &&
    (user.getPermissions.has(name) || user.getPermissions.has('*:*:*'))
  const pages = computed(() =>
    available.value
      ? collectAssistantPages(permission.getRouters).filter(
          (page) => page.path !== '/warehouse-space' || allowed('rd:putaway-task:query')
        )
      : []
  )
  const currentPage = computed(
    () => pages.value.find((page) => page.path === route.path)?.title || 'ERP 工作台'
  )
  const quickQuestions = computed(() =>
    [
      ...(allowed('rd:package-unit:query')
        ? [
            { label: '查待检', question: '待检包件有多少', icon: 'ep:search' },
            { label: '待入库', question: '合格待入库的货物', icon: 'ep:download' }
          ]
        : []),
      ...(allowed('rd:quality-inspection:query')
        ? [{ label: '待复核', question: '质检待复核', icon: 'ep:checked' }]
        : []),
      ...(pages.value.some((page) => page.title.includes('产品'))
        ? [{ label: '找产品', question: '产品资料在哪里', icon: 'ep:search' }]
        : []),
      ...(allowed('rd:dashboard:query')
        ? [{ label: '查库存', question: '查库存', icon: 'ep:box' }]
        : []),
      ...(allowed('rd:putaway-task:query')
        ? [{ label: '查货位', question: '仓库有多少库位', icon: 'ep:location' }]
        : []),
      { label: '入库流程', question: '入库怎么操作', icon: 'ep:download' },
      { label: '出库流程', question: '出库怎么操作', icon: 'ep:van' },
      { label: '当前页面', question: '这个页面怎么用', icon: 'ep:help' }
    ].slice(0, 9)
  )

  const cancel = () => {
    generation++
    if (busy.value) {
      const message = messages.value.at(-1)
      if (message && !message.reply)
        message.reply = {
          kind: 'NOTICE',
          title: '已取消本次查询',
          paragraphs: ['不会接收本次迟到的查询结果，也没有修改业务数据。'],
          links: []
        }
    }
    busy.value = false
  }
  const clear = () => {
    cancel()
    messages.value = []
    input.value = ''
  }
  const resetIdentity = () => {
    clear()
    open.value = false
  }
  let lastIdentity = signature()
  const stopIdentity = watch(
    () => `${authSignal.value}:${signature()}`,
    () => {
      lastIdentity = signature()
      resetIdentity()
    },
    { flush: 'sync' }
  )
  const stopRoute = watch(
    () => route.fullPath,
    () => {
      if (busy.value) cancel()
    },
    { flush: 'sync' }
  )
  const stopAuth = onAuthContextChange(() => {
    authSignal.value++
  })
  const storage = (event: StorageEvent) => {
    if (
      event.key === null ||
      [
        'ACCESS_TOKEN',
        'REFRESH_TOKEN',
        CACHE_KEY.USER,
        CACHE_KEY.TenantId,
        CACHE_KEY.VisitTenantId
      ].includes(event.key)
    ) {
      foreignAuthChanged.value = true
      resetIdentity()
    }
  }
  const visibility = () => {
    if (document.visibilityState === 'visible' && signature() !== lastIdentity) authSignal.value++
  }
  const readers = {
    inventory: () =>
      RdInventoryDashboardApi.getOverview(
        new Intl.DateTimeFormat('sv-SE', {
          timeZone: 'Asia/Shanghai',
          year: 'numeric',
          month: '2-digit',
          day: '2-digit'
        }).format(new Date())
      ),
    locations: () => RdPutawayTaskApi.getLocationMap(),
    trace: (code: string) => RdPackageUnitApi.getTraceDetail(code),
    packageContext: (code: string) => RdPackageAiApi.getContext(code),
    workQuery: RdWorkQueryApi.query
  }
  const ask = async (value = input.value) => {
    const question = value.trim()
    if (!alive || !available.value || busy.value || !question) return
    input.value = ''
    const epoch = ++generation
    const identity = signature()
    const valid = () => alive && available.value && epoch === generation && identity === signature()
    const message: AssistantMessage = { id: ++sequence, question: question.slice(0, 200) }
    messages.value = [...messages.value.slice(-11), message]
    busy.value = true
    try {
      const reply = await answerAssistantQuestion(
        question,
        {
          pages: pages.value,
          currentPath: route.path,
          warehouseCode:
            typeof route.query.warehouseCode === 'string'
              ? route.query.warehouseCode.slice(0, 80)
              : '',
          allowed,
          valid
        },
        readers
      )
      if (valid()) messages.value.find((item) => item.id === message.id)!.reply = reply
    } catch {
      if (valid())
        messages.value.find((item) => item.id === message.id)!.reply = {
          kind: 'NOTICE',
          title: '本次查询未完成',
          paragraphs: [
            '没有取得可核对的结果，请稍后重试或到原业务页面查看。未修改库存或其他业务记录。'
          ],
          links: []
        }
    } finally {
      if (epoch === generation) busy.value = false
    }
  }
  const openLink = (link: AssistantLink) => {
    if (
      !alive ||
      !available.value ||
      !canOpenAssistantLink(link, pages.value, allowed('rd:package-unit:query'))
    )
      return false
    cancel()
    void router.push(
      link.packageCode
        ? { path: link.path, query: { internal: '1' } }
        : link.inspectionId
          ? { path: link.path, query: { inspectionId: link.inspectionId } }
          : { path: link.path }
    )
    return true
  }
  onMounted(() => {
    window.addEventListener('storage', storage)
    document.addEventListener('visibilitychange', visibility)
  })
  onBeforeUnmount(() => {
    alive = false
    clear()
    stopIdentity()
    stopRoute()
    stopAuth()
    window.removeEventListener('storage', storage)
    document.removeEventListener('visibilitychange', visibility)
  })
  return {
    open,
    input,
    busy,
    messages,
    available,
    currentPage,
    quickQuestions,
    ask,
    cancel,
    clear,
    openLink
  }
}
