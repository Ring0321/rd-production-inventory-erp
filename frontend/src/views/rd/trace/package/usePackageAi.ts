import { computed, onActivated, onBeforeUnmount, onDeactivated, ref, shallowRef, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import {
  RdPackageAiApi,
  type PackageAiAnswer,
  type PackageAiAskRequest,
  type PackageAiContext,
  type PackageAiDiagnosis,
  type PackageAiQuestion
} from '@/api/rd/ai'
import { generateUUID } from '@/utils'
import {
  getAccessToken,
  getCurrentUserId,
  getTenantId,
  getVisitTenantId,
  getAuthContextRevision,
  getCachedAuthScope,
  onAuthContextChange
} from '@/utils/auth'
import { useUserStore } from '@/store/modules/user'
import { CACHE_KEY } from '@/hooks/web/useCache'
import { useWarehouseSession } from '../../pda/warehouse/useWarehouseSession'

const permission = 'rd:package-unit:query'
export const packageAiQuestions: Array<{ value: PackageAiQuestion; label: string }> = [
  { value: 'STATUS', label: '当前状态' },
  { value: 'SOURCE_PROGRESS', label: '来源登记进度' },
  { value: 'NEXT_STEP', label: '下一步办理提示' },
  { value: 'RISK_REVIEW', label: '风险核对' },
  { value: 'HANDOVER', label: '交接摘要' }
]

type FieldQuestion =
  | { state: 'MATCHED'; question: PackageAiQuestion }
  | { state: 'AMBIGUOUS'; candidates: PackageAiQuestion[] }
  | { state: 'UNSUPPORTED' }

export const interpretPackageAiQuestion = (value: string): FieldQuestion => {
  const input = value.normalize('NFKC').trim()
  if (
    !input ||
    input.length > 120 ||
    /删除|修改|更改|改成|更新|设为|设置|调整|转移|过账|作废|放行|自动|提交|扣库存|调账|SQL|密钥|密码|https?:\/\//i.test(
      input
    ) ||
    /[0-9零一二两三四五六七八九十百千万点壹贰叁肆伍陆柒捌玖拾佰仟]+\s*(?:吨|t\b|kg\b|千克|公斤|包)/i.test(
      input
    )
  )
    return { state: 'UNSUPPORTED' }
  const patterns: Array<[PackageAiQuestion, RegExp]> = [
    ['STATUS', /状态|到哪|哪一步|什么阶段/],
    ['SOURCE_PROGRESS', /包数|重量|多重|多少包|多少吨|多少公斤|进度/],
    ['NEXT_STEP', /下一步|接下来|谁处理|谁负责|谁确认|找谁|哪个部门/],
    [
      'RISK_REVIEW',
      /(?:能|可以|可不可以|能不能|是否能).*?(?:发|出库|入库)|风险|异常|有没有问题|有问题吗|注意|检查/
    ],
    ['HANDOVER', /交接|交班|摘要/]
  ]
  const candidates = patterns
    .filter(([, pattern]) => pattern.test(input))
    .map(([question]) => question)
  if (candidates.length === 1) return { state: 'MATCHED', question: candidates[0] }
  return candidates.length ? { state: 'AMBIGUOUS', candidates } : { state: 'UNSUPPORTED' }
}
const providerLabels = {
  DISABLED: '未启用',
  UNCONFIGURED: '未配置',
  COST_NOT_APPROVED: '费用未确认',
  LIMIT_NOT_SET: '额度未设置',
  BUDGET_NOT_SET: '预算未设置',
  PRICE_REVIEW_REQUIRED: '价格待复核',
  READY: '可请求'
}
const terminalStates = new Set([
  'SUCCESS',
  'FAILED',
  'INVALID_OUTPUT',
  'DISABLED',
  'UNCONFIGURED',
  'COST_NOT_APPROVED',
  'LIMIT_NOT_SET',
  'LIMIT_REACHED',
  'BUDGET_NOT_SET',
  'PRICE_REVIEW_REQUIRED',
  'BUDGET_REACHED',
  'BUDGET_UNAVAILABLE'
])
const unresolvedStates = new Set([
  'PENDING',
  'UNKNOWN',
  'STALE',
  'RECEIPT_MISSING',
  'GUARD_UNAVAILABLE'
])
const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i
const validQuestion = (value: unknown): value is PackageAiQuestion =>
  packageAiQuestions.some((item) => item.value === value)
const text = (value: unknown): value is string => typeof value === 'string' && !!value.trim()
const count = (value: unknown) => Number.isSafeInteger(value) && Number(value) >= 0
const weight = (value: unknown) => typeof value === 'number' && Number.isFinite(value) && value >= 0
const diagnosticKeys = new Set([
  'DIAG_VOIDED',
  'DIAG_QUALITY_REJECTED',
  'DIAG_QUALITY_PENDING',
  'DIAG_STOCKTAKE_MISSING',
  'DIAG_UNKNOWN_STATUS',
  'DIAG_INVALID_WEIGHT',
  'DIAG_MISSING_LOCATION',
  'DIAG_SPLIT_LOCATION',
  'DIAG_SOURCE_UNKNOWN'
])
const validDiagnosis = (
  value: PackageAiDiagnosis | undefined,
  source: PackageAiContext['source']
) => {
  if (value === undefined || value === null) return true
  if (
    value.state === 'KNOWN' &&
    (!count(value.inspectedPackages) || Number(value.inspectedPackages) < 1)
  )
    return false
  if (
    value.state === 'UNKNOWN' &&
    (!Array.isArray(value.findings) ||
      value.findings.length !== 1 ||
      value.findings[0]?.key !== 'DIAG_SOURCE_UNKNOWN')
  )
    return false
  if (
    value.scope !== 'PRODUCTION_OUTPUT' ||
    value.state !== source.state ||
    !Array.isArray(value.findings) ||
    value.findings.length > diagnosticKeys.size
  )
    return false
  if (
    value.state === 'KNOWN'
      ? value.inspectedPackages !== source.packageCount
      : value.inspectedPackages !== null
  )
    return false
  return (
    new Set(value.findings.map((finding) => finding.key)).size === value.findings.length &&
    value.findings.every(
      (finding) =>
        diagnosticKeys.has(finding.key) &&
        ['HIGH', 'REVIEW', 'INFO'].includes(finding.severity) &&
        ['PRODUCTION', 'QUALITY', 'WAREHOUSE'].includes(finding.owner) &&
        text(finding.title) &&
        finding.title.length <= 100 &&
        text(finding.detail) &&
        finding.detail.length <= 1000 &&
        text(finding.evidence) &&
        finding.evidence.length <= 256 &&
        Array.isArray(finding.packageCodes) &&
        finding.packageCodes.length <= 5 &&
        new Set(finding.packageCodes).size === finding.packageCodes.length &&
        finding.packageCodes.every(
          (code) => typeof code === 'string' && /^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(code)
        ) &&
        (value.state === 'UNKNOWN'
          ? finding.key === 'DIAG_SOURCE_UNKNOWN' &&
            finding.affectedCount === null &&
            finding.packageCodes.length === 0
          : finding.key !== 'DIAG_SOURCE_UNKNOWN' &&
            count(finding.affectedCount) &&
            Number(finding.affectedCount) > 0 &&
            Number(finding.affectedCount) <= Number(value.inspectedPackages))
    )
  )
}

export const packageAiStatusLabel = (status: string) => {
  const labels: Record<string, string> = {
    CREATED: '已建包',
    LABELED: '已贴码',
    QC_PENDING: '待质检',
    QC_PASSED: '质检合格',
    QC_REJECTED: '质检不合格',
    INBOUND_PENDING: '待入库',
    IN_STOCK: '在库',
    STOCKTAKE_MISSING: '盘点缺失',
    OUTBOUND_PENDING: '待出库',
    OUTBOUNDED: '已出库',
    VOIDED: '已作废'
  }
  return labels[status] || status || '未记录'
}

export const usePackageAiAccess = () => {
  const session = useWarehouseSession()
  const user = useUserStore()
  const permitted = ref(false)
  const epoch = ref(0)
  let alive = true
  let active = true
  let invalidated = false
  const signature = () =>
    JSON.stringify([
      user.getUser.id,
      getCurrentUserId(),
      Boolean(getAccessToken()),
      String(getVisitTenantId() ?? getTenantId() ?? ''),
      [...user.getRoles].sort(),
      [...user.getPermissions].sort(),
      getAuthContextRevision(),
      getCachedAuthScope()
    ])
  const ownerSignature = signature()
  const invalidate = () => {
    permitted.value = false
    if (!invalidated) epoch.value++
    invalidated = true
  }
  const refreshAccess = () => {
    if (!alive || !active || invalidated) return false
    if (signature() !== ownerSignature || !session.current() || session.ownerId <= 0) invalidate()
    permitted.value = !invalidated && session.allowed(permission)
    return permitted.value
  }
  const storageChanged = (event: StorageEvent) => {
    const keys: string[] = [
      'ACCESS_TOKEN',
      'REFRESH_TOKEN',
      CACHE_KEY.TenantId,
      CACHE_KEY.VisitTenantId
    ]
    if (event.key === null || keys.includes(event.key)) invalidate()
    else if (event.key === CACHE_KEY.USER) {
      try {
        // The installed WebStorageCache envelope stores its structured value in v.
        const before = JSON.parse(event.oldValue || '').v
        const after = JSON.parse(event.newValue || '').v
        if (
          !before ||
          !after ||
          JSON.stringify(getCachedAuthScope(before)) !== JSON.stringify(getCachedAuthScope(after))
        )
          invalidate()
        else refreshAccess()
      } catch {
        invalidate()
      }
    } else refreshAccess()
  }
  const stop = watch(signature, refreshAccess, { immediate: true, flush: 'sync' })
  const stopAuth = onAuthContextChange(invalidate)
  // Auth/tenant cache getters are not reactive for same-tab changes.
  const accessTimer = window.setInterval(refreshAccess, 1000)
  window.addEventListener('storage', storageChanged)
  window.addEventListener('focus', refreshAccess)
  document.addEventListener('visibilitychange', refreshAccess)
  onDeactivated(() => {
    active = false
    permitted.value = false
    epoch.value++
  })
  onActivated(() => {
    active = true
    epoch.value++
    refreshAccess()
  })
  onBeforeUnmount(() => {
    alive = false
    permitted.value = false
    epoch.value++
    stop()
    stopAuth()
    window.clearInterval(accessTimer)
    window.removeEventListener('storage', storageChanged)
    window.removeEventListener('focus', refreshAccess)
    document.removeEventListener('visibilitychange', refreshAccess)
  })
  return { session, permitted, epoch, refreshAccess }
}

type Pending = Readonly<{
  version: 1
  ownerId: number
  tenant: string
  request: Readonly<PackageAiAskRequest>
  state: string
}>

export const packageAiPendingKey = (ownerId: number, tenant: string, packageCode: string) =>
  `rd-package-ai:v1:${encodeURIComponent(tenant)}:${ownerId}:${encodeURIComponent(packageCode)}`

const parsePending = (
  raw: string,
  ownerId: number,
  tenant: string,
  packageCode: string
): Pending => {
  if (raw.length > 4096) throw new Error('Invalid original request')
  const value = JSON.parse(raw)
  const request = value?.request
  if (
    value?.version !== 1 ||
    value.ownerId !== ownerId ||
    value.tenant !== tenant ||
    !text(value.state) ||
    request?.packageCode !== packageCode ||
    !validQuestion(request?.question) ||
    !text(request?.requestId) ||
    !uuidPattern.test(request.requestId)
  )
    throw new Error('Original request identity mismatch')
  return Object.freeze({
    version: 1,
    ownerId,
    tenant,
    request: Object.freeze({
      packageCode,
      question: request.question,
      requestId: request.requestId
    }),
    state:
      terminalStates.has(value.state) || unresolvedStates.has(value.state) ? value.state : 'UNKNOWN'
  })
}

const validContext = (
  value: PackageAiContext | undefined,
  code: string
): value is PackageAiContext => {
  const source = value?.source
  return (
    !!value &&
    value.packageCode === code &&
    typeof value.status === 'string' &&
    typeof value.qualityStage === 'string' &&
    text(value.factsAt) &&
    Object.hasOwn(providerLabels, value.providerState) &&
    !!source &&
    ['KNOWN', 'UNKNOWN'].includes(source.state) &&
    (source.packageCount === null || count(source.packageCount)) &&
    (source.recordedWeight === null || weight(source.recordedWeight)) &&
    !!source.counts &&
    typeof source.counts === 'object' &&
    !Array.isArray(source.counts) &&
    Object.values(source.counts).every(count) &&
    Array.isArray(value.notices) &&
    value.notices.every((notice) => text(notice?.key) && text(notice?.text)) &&
    new Set(value.notices.map((notice) => notice.key)).size === value.notices.length &&
    validDiagnosis(value.diagnosis, source)
  )
}

const validAnswer = (
  value: PackageAiAnswer | undefined,
  original: Pending
): value is PackageAiAnswer =>
  !!value &&
  value.requestId === original.request.requestId &&
  text(value.state) &&
  typeof value.message === 'string' &&
  validContext(value.context, original.request.packageCode) &&
  Array.isArray(value.noteKeys) &&
  value.noteKeys.length <= 5 &&
  value.noteKeys.every(text) &&
  new Set(value.noteKeys).size === value.noteKeys.length &&
  value.noteKeys.every((key) => value.context.notices.some((notice) => notice.key === key)) &&
  (value.totalTokens === null || count(value.totalTokens)) &&
  (value.answeredAt === null || text(value.answeredAt)) &&
  (value.state !== 'SUCCESS' || (value.noteKeys.length > 0 && text(value.answeredAt)))

const denied = (cause: unknown) => {
  const error = cause as {
    response?: { status?: number }
    code?: number
    businessCode?: number
  } | null
  // The shared client masks some business denials as 'error'; fail closed for that case too.
  return (
    cause === 'error' ||
    [401, 403].some(
      (code) =>
        error?.response?.status === code || error?.code === code || error?.businessCode === code
    )
  )
}

export const usePackageAi = (props: { packageCode: string; factsRevision: string | number }) => {
  const access = usePackageAiAccess()
  const { session } = access
  const route = useRoute()
  const context = shallowRef<PackageAiContext>()
  const answer = shallowRef<PackageAiAnswer>()
  const pending = shallowRef<Pending>()
  const question = ref<PackageAiQuestion>('STATUS')
  const contextLoading = ref(false)
  const asking = ref(false)
  const confirming = ref(false)
  const contextError = ref('')
  const requestError = ref('')
  const storageError = ref('')
  const serverDenied = ref(false)
  let revision = 0
  let contextSequence = 0
  let askSequence = 0
  const code = () => props.packageCode.trim()
  const key = () => packageAiPendingKey(session.ownerId, session.tenant, code())
  const lockManager = window.navigator?.locks
  const stamp = () => ({
    revision,
    generation: session.generation(),
    epoch: access.epoch.value,
    code: code(),
    route: route.fullPath,
    factsRevision: props.factsRevision,
    authRevision: getAuthContextRevision()
  })
  const current = (captured: ReturnType<typeof stamp>) =>
    access.refreshAccess() &&
    !serverDenied.value &&
    captured.revision === revision &&
    captured.generation === session.generation() &&
    captured.epoch === access.epoch.value &&
    captured.code === code() &&
    captured.route === route.fullPath &&
    captured.factsRevision === props.factsRevision &&
    captured.authRevision === getAuthContextRevision()
  const canQuery = computed(() => access.permitted.value && !serverDenied.value)
  const busy = computed(() => contextLoading.value || asking.value || confirming.value)
  const canAsk = computed(
    () =>
      canQuery.value &&
      !busy.value &&
      !storageError.value &&
      !pending.value &&
      !!lockManager &&
      context.value?.providerState === 'READY' &&
      validQuestion(question.value)
  )
  const canReconcile = computed(
    () =>
      canQuery.value &&
      !busy.value &&
      !storageError.value &&
      !!pending.value &&
      (pending.value.state === 'SUCCESS' || !terminalStates.has(pending.value.state))
  )
  const requestTerminal = computed(() => !!pending.value && terminalStates.has(pending.value.state))
  const canNewQuestion = computed(
    () =>
      canQuery.value &&
      !busy.value &&
      !storageError.value &&
      !!pending.value &&
      requestTerminal.value
  )
  const providerLabel = computed(() =>
    context.value
      ? providerLabels[context.value.providerState]
      : contextLoading.value
        ? '读取中'
        : '未读取'
  )
  const sourceFacts = computed(() => {
    const source = context.value?.source
    if (!source || source.state === 'UNKNOWN')
      return { packageCount: '未知', recordedWeight: '未知', counts: '未知' }
    return {
      packageCount: source.packageCount === null ? '未知' : `${source.packageCount} 包`,
      recordedWeight: source.recordedWeight === null ? '未知' : `${source.recordedWeight} kg`,
      counts:
        Object.entries(source.counts)
          .map(([state, amount]) => `${packageAiStatusLabel(state)} ${amount} 包`)
          .join('、') || '未登记'
    }
  })
  const notes = computed(() => {
    if (!answer.value) return []
    const notices = new Map(answer.value.context.notices.map((notice) => [notice.key, notice.text]))
    return answer.value.noteKeys.map((key) => notices.get(key)!)
  })
  const resultMessage = computed(() => {
    if (!pending.value) return ''
    if (asking.value) return '原请求处理中'
    const labels: Record<string, string> = {
      SUCCESS: '原请求已成功',
      FAILED: '原请求失败',
      INVALID_OUTPUT: '原请求输出未通过校验',
      DISABLED: '未启用',
      UNCONFIGURED: '未配置',
      COST_NOT_APPROVED: '费用未确认',
      LIMIT_NOT_SET: '额度未设置',
      LIMIT_REACHED: '调用次数已达上限',
      BUDGET_NOT_SET: '预算未设置',
      PRICE_REVIEW_REQUIRED: '价格待复核',
      BUDGET_REACHED: '月度预算已达上限',
      BUDGET_UNAVAILABLE: '预算保护不可用',
      STALE: '原请求依据已变化，已停止新请求，请核对原业务记录',
      RECEIPT_MISSING: '原请求回执已过期或缺失，已停止新请求，请联系系统负责人',
      GUARD_UNAVAILABLE: '调用保护不可用，原请求仍待核'
    }
    return labels[pending.value.state] || '原请求结果待核，已停止新请求'
  })
  const assertStored = (original: Pending) => {
    const raw = localStorage.getItem(key())
    if (
      !raw ||
      JSON.stringify(parsePending(raw, session.ownerId, session.tenant, code())) !==
        JSON.stringify(original)
    )
      throw new Error('Original stored request changed')
  }
  const storageFailed = () => {
    storageError.value = '原请求记录无法保存或核对，已停止新请求'
  }
  const restore = () => {
    try {
      const raw = localStorage.getItem(key())
      if (raw !== null) {
        pending.value = parsePending(raw, session.ownerId, session.tenant, code())
        question.value = pending.value.request.question
      }
    } catch {
      storageFailed()
    }
  }
  const loadContext = async () => {
    if (!access.refreshAccess() || serverDenied.value || !code() || asking.value) return
    const captured = stamp()
    const sequence = ++contextSequence
    context.value = undefined
    answer.value = undefined
    contextError.value = ''
    contextLoading.value = true
    try {
      const value = await RdPackageAiApi.getContext(captured.code)
      if (!current(captured) || sequence !== contextSequence) return
      if (!validContext(value, captured.code)) throw new Error('Invalid ERP context')
      context.value = value
    } catch (cause) {
      if (!current(captured) || sequence !== contextSequence) return
      contextError.value = denied(cause) ? '无包码查询权限，已停止读取' : 'ERP登记事实读取失败'
      if (denied(cause)) serverDenied.value = true
    } finally {
      if (captured.revision === revision && sequence === contextSequence)
        contextLoading.value = false
    }
  }
  const resetView = () => {
    revision++
    contextSequence++
    askSequence++
    context.value = undefined
    answer.value = undefined
    pending.value = undefined
    contextLoading.value = false
    asking.value = false
    confirming.value = false
    serverDenied.value = false
    contextError.value = ''
    requestError.value = ''
    storageError.value = ''
    question.value = 'STATUS'
    if (!access.permitted.value || !code()) return
    restore()
    if (!lockManager)
      storageError.value =
        '当前浏览器缺少跨标签页请求保护，未开放模型调用；请使用支持Web Locks的HTTPS浏览器'
    void loadContext()
  }
  const sharedRequestChanged = (event: StorageEvent) => {
    if (event.key === key() || event.key === null) resetView()
  }
  window.addEventListener('storage', sharedRequestChanged)
  onBeforeUnmount(() => window.removeEventListener('storage', sharedRequestChanged))
  watch(
    () => [
      props.packageCode,
      props.factsRevision,
      route.fullPath,
      access.epoch.value,
      access.permitted.value
    ],
    resetView,
    { immediate: true, flush: 'sync' }
  )
  const send = async (original: Pending, receiptOnly = false) => {
    const captured = stamp()
    const sequence = ++askSequence
    asking.value = true
    answer.value = undefined
    requestError.value = ''
    try {
      try {
        assertStored(original)
      } catch {
        storageFailed()
        return
      }
      if (!current(captured)) return
      const value = receiptOnly
        ? await RdPackageAiApi.getResult(original.request)
        : await RdPackageAiApi.ask(original.request)
      if (!current(captured) || sequence !== askSequence) return
      if (!validAnswer(value, original)) throw new Error('Unverified original result')
      const state =
        terminalStates.has(value.state) || unresolvedStates.has(value.state)
          ? value.state
          : 'UNKNOWN'
      const next = parsePending(
        JSON.stringify({ ...original, state }),
        session.ownerId,
        session.tenant,
        code()
      )
      try {
        assertStored(original)
        localStorage.setItem(key(), JSON.stringify(next))
      } catch {
        storageFailed()
        return
      }
      pending.value = next
      context.value = value.context
      if (state === 'SUCCESS') answer.value = value
    } catch (cause) {
      if (!current(captured) || sequence !== askSequence) return
      if (denied(cause)) {
        serverDenied.value = true
        context.value = undefined
        contextError.value = '请求权限未核实，已停止读取；原请求仍保留'
      }
      requestError.value = '原请求结果未核实，请保留并核对原请求'
    } finally {
      if (captured.revision === revision && sequence === askSequence) asking.value = false
    }
  }
  const ask = async () => {
    if (!access.refreshAccess() || !canAsk.value) return
    let original: Pending | undefined
    const captured = stamp()
    confirming.value = true
    try {
      await lockManager!.request(key(), { mode: 'exclusive' }, () => {
        if (!current(captured)) return
        const stored = localStorage.getItem(key())
        if (stored !== null) {
          pending.value = parsePending(stored, session.ownerId, session.tenant, code())
          question.value = pending.value.request.question
          return
        }
        original = parsePending(
          JSON.stringify({
            version: 1,
            ownerId: session.ownerId,
            tenant: session.tenant,
            state: 'UNKNOWN',
            request: { packageCode: code(), question: question.value, requestId: generateUUID() }
          }),
          session.ownerId,
          session.tenant,
          code()
        )
        localStorage.setItem(key(), JSON.stringify(original))
        pending.value = original
      })
    } catch {
      storageFailed()
      return
    } finally {
      if (captured.revision === revision) confirming.value = false
    }
    if (original && current(captured)) await send(original)
  }
  const reconcile = async () => {
    if (access.refreshAccess() && canReconcile.value && pending.value)
      await send(pending.value, true)
  }
  const newQuestion = async () => {
    if (!access.refreshAccess() || !canNewQuestion.value || !pending.value) return
    const original = pending.value
    const captured = stamp()
    confirming.value = true
    try {
      await ElMessageBox.confirm('原请求已结束。开始新问题可能产生新的费用，确认继续？', '新问题', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'warning'
      })
      if (!current(captured) || pending.value !== original) return
      try {
        assertStored(original)
        localStorage.removeItem(key())
      } catch {
        storageFailed()
        return
      }
      pending.value = undefined
      answer.value = undefined
      requestError.value = ''
    } catch (cause) {
      if (cause !== 'cancel' && cause !== 'close' && current(captured)) storageFailed()
    } finally {
      if (captured.revision === revision) confirming.value = false
    }
  }
  return {
    canQuery,
    canAsk,
    canReconcile,
    canNewQuestion,
    requestTerminal,
    context,
    answer,
    pending,
    question,
    contextLoading,
    asking,
    confirming,
    contextError,
    requestError,
    storageError,
    providerLabel,
    sourceFacts,
    notes,
    resultMessage,
    loadContext,
    ask,
    reconcile,
    newQuestion
  }
}
