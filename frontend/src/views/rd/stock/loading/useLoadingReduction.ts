import {
  computed,
  onActivated,
  onBeforeUnmount,
  onDeactivated,
  onMounted,
  ref,
  shallowRef,
  watch
} from 'vue'
import type { LoadingTaskDetail } from '@/api/rd/loading'
import {
  RdLoadingReductionApi as api,
  type LoadingReduction,
  type LoadingReductionCreate,
  type LoadingReductionQuery,
  type LoadingReductionReview
} from '@/api/rd/loading/reduction'
import { useWarehouseSession } from '../../pda/warehouse/useWarehouseSession'
import { useUserStore } from '@/store/modules/user'
import { onAuthContextChange } from '@/utils/auth'
import { generateUUID } from '@/utils'

const positiveId = (value: unknown): value is number =>
  typeof value === 'number' && Number.isSafeInteger(value) && value > 0
const validVersion = (value: unknown): value is number =>
  typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
const validText = (value: unknown): value is string =>
  typeof value === 'string' && !!value.trim() && value.trim().length <= 1000
const validIds = (ids: unknown): ids is number[] =>
  Array.isArray(ids) &&
  ids.length >= 1 &&
  ids.length <= 100 &&
  ids.every(positiveId) &&
  new Set(ids).size === ids.length

export const reductionStatusLabel = (record: LoadingReduction) => {
  if (record.effectiveStatus === 'STALE') return '已失效'
  return { PENDING: '待商务核定', APPROVED: '已核定待变更', REJECTED: '商务退回' }[record.status]
}

export const formatReductionWeight = (value: number | string | null | undefined) => {
  if (typeof value === 'number')
    return Number.isFinite(value)
      ? value.toLocaleString('en-US', { useGrouping: false, maximumFractionDigits: 6 })
      : '-'
  if (typeof value !== 'string' || !/^-?\d+(?:\.\d{1,6})?$/.test(value.trim())) return '-'
  return value
    .trim()
    .replace(/(\.\d*?)0+$/, '$1')
    .replace(/\.$/, '')
}

export const sumReductionWeights = (values: Array<number | string>) => {
  let total = 0n
  for (const value of values) {
    const text = formatReductionWeight(value)
    if (text === '-') return '-'
    const [integer, fraction = ''] = text.replace(/^-/, '').split('.')
    const micros = BigInt(integer) * 1000000n + BigInt(fraction.padEnd(6, '0'))
    total += text.startsWith('-') ? -micros : micros
  }
  const absolute = total < 0n ? -total : total
  const fraction = String(absolute % 1000000n)
    .padStart(6, '0')
    .replace(/0+$/, '')
  return `${total < 0n ? '-' : ''}${absolute / 1000000n}${fraction ? `.${fraction}` : ''}`
}

export const reductionCandidates = (detail?: LoadingTaskDetail) =>
  detail?.lines.filter(
    (line) => ['PLANNED', 'EXCEPTION'].includes(line.lineStatus) && !line.scanTime
  ) || []

export const buildReductionCreate = (
  detail: LoadingTaskDetail | undefined,
  ids: number[],
  reason: string,
  idempotencyKey: string
): LoadingReductionCreate => {
  if (!detail || !positiveId(detail.task.id) || !validVersion(detail.task.version))
    throw new Error('任务版本缺失，请重新读取原装车任务')
  if (detail.task.status !== 'EXCEPTION_PENDING') throw new Error('仅异常待处理任务可申请少装')
  if (!validIds(ids)) throw new Error('请选择 1 至 100 个不同的未核验包')
  const active = detail.lines.filter((line) => line.lineStatus !== 'CANCELLED')
  if (
    active.length !== detail.task.plannedPackageCount ||
    new Set(active.map((line) => line.id)).size !== active.length
  )
    throw new Error('有效计划包数不一致，请重新读取原装车任务')
  if (active.length - ids.length < 1) throw new Error('少装后必须至少保留 1 包')
  const eligible = new Set(reductionCandidates(detail).map((line) => line.id))
  if (ids.some((id) => !eligible.has(id))) throw new Error('所选包已核验、已取消或不在当前任务中')
  if (!validText(reason)) throw new Error('请填写真实少装原因，最多 1000 字')
  if (!validText(idempotencyKey)) throw new Error('原请求标识缺失')
  return {
    taskId: detail.task.id,
    taskVersion: detail.task.version,
    taskLineIds: [...ids],
    reason: reason.trim(),
    idempotencyKey
  }
}

interface PendingScope {
  ownerId: number
  tenant: string
  taskId: number
  pickupSlipId: number
}
export type LoadingReductionPending = PendingScope &
  (
    | { kind: 'create'; request: LoadingReductionCreate }
    | { kind: 'review'; request: LoadingReductionReview }
  )

export const parseReductionPending = (raw: string, ownerId: number, tenant: string) => {
  const value = JSON.parse(raw) as LoadingReductionPending
  if (
    !value ||
    value.ownerId !== ownerId ||
    value.tenant !== tenant ||
    !positiveId(value.taskId) ||
    !positiveId(value.pickupSlipId) ||
    !value.request
  )
    throw new Error('原少装请求身份或关联单据不一致')
  if (value.kind === 'create') {
    const request = value.request
    if (
      request.taskId !== value.taskId ||
      !validVersion(request.taskVersion) ||
      !validIds(request.taskLineIds) ||
      !validText(request.reason) ||
      !validText(request.idempotencyKey)
    )
      throw new Error('原少装申请不完整')
  } else if (value.kind === 'review') {
    const request = value.request
    if (
      !positiveId(request.id) ||
      !validVersion(request.version) ||
      !['APPROVED', 'REJECTED'].includes(request.decision) ||
      !validText(request.reviewRemark)
    )
      throw new Error('原少装核定不完整')
  } else throw new Error('原少装请求类型不一致')
  return value
}

export const findReductionConclusion = (
  records: LoadingReduction[],
  pending: LoadingReductionPending
) =>
  records.find((record) => {
    if (
      record.taskId !== pending.taskId ||
      record.pickupSlipId !== pending.pickupSlipId ||
      !positiveId(record.id) ||
      !validVersion(record.version)
    )
      return false
    if (pending.kind === 'review')
      return (
        record.id === pending.request.id &&
        record.version > pending.request.version &&
        ['APPROVED', 'REJECTED'].includes(record.status)
      )
    const request = pending.request
    const removed = record.lines?.filter((line) => line.removed).map((line) => line.id) || []
    return (
      record.idempotencyKey === request.idempotencyKey &&
      record.proposerId === pending.ownerId &&
      record.taskVersion === request.taskVersion &&
      record.reason === request.reason &&
      removed.length === request.taskLineIds.length &&
      new Set(removed).size === removed.length &&
      removed.every((id) => request.taskLineIds.includes(id)) &&
      ['PENDING', 'APPROVED', 'REJECTED'].includes(record.status)
    )
  })

interface ReductionSources {
  taskDetail: () => LoadingTaskDetail | undefined
  pickupSlipId: () => number | undefined
  active: () => boolean
  sessionCurrent?: () => boolean
}

export const useLoadingReduction = (sources: ReductionSources) => {
  const session = useWarehouseSession()
  const user = useUserStore()
  const records = shallowRef<LoadingReduction[]>([])
  const selected = ref<number[]>([])
  const reason = ref('')
  const reviewRemarks = ref<Record<number, string>>({})
  const pending = shallowRef<LoadingReductionPending>()
  const reading = ref(false)
  const writing = ref(false)
  const loaded = ref(false)
  const error = ref('')
  const storageError = ref('')
  const feedback = ref('')
  const pageActive = ref(true)
  const pageVisible = ref(true)
  let alive = true
  let epoch = 0
  let readSequence = 0
  const storageKey = `rd-loading-reduction-pending:${session.tenant}:${session.ownerId}`
  const isDriver = () => user.getRoles.includes('rd_forklift_driver')
  const current = () =>
    alive &&
    !isDriver() &&
    pageActive.value &&
    pageVisible.value &&
    sources.active() &&
    positiveId(session.ownerId) &&
    session.current() &&
    (sources.sessionCurrent?.() ?? true)
  const query = (): LoadingReductionQuery | undefined => {
    const taskId = sources.taskDetail()?.task.id
    const slipId = sources.pickupSlipId()
    if (positiveId(taskId) && slipId === undefined) return { taskId }
    if (positiveId(slipId) && taskId === undefined) return { pickupSlipId: slipId }
    return undefined
  }
  const queryAllowed = () =>
    current() &&
    !isDriver() &&
    !!query() &&
    (session.allowed('rd:loading-task:query') || session.allowed('rd:pickup-slip:query'))
  const canQuery = computed(queryAllowed)
  const candidates = computed(() => reductionCandidates(sources.taskDetail()))
  const blockedByRecord = computed(() =>
    records.value.some(
      (record) =>
        record.taskId === sources.taskDetail()?.task.id &&
        record.effectiveStatus !== 'STALE' &&
        ['PENDING', 'APPROVED'].includes(record.status)
    )
  )
  const locked = computed(
    () =>
      !current() ||
      !canQuery.value ||
      !loaded.value ||
      reading.value ||
      writing.value ||
      !!pending.value ||
      !!storageError.value
  )
  const showCreate = computed(
    () =>
      current() &&
      !isDriver() &&
      session.allowed('rd:loading-task:dispatch') &&
      user.getRoles.some((role) => ['rd_warehouse', 'super_admin'].includes(role)) &&
      sources.taskDetail()?.task.status === 'EXCEPTION_PENDING'
  )
  const canCreate = computed(() => showCreate.value && !locked.value && !blockedByRecord.value)
  const canReview = (
    record: LoadingReduction,
    decision: LoadingReductionReview['decision'] = 'APPROVED'
  ) =>
    current() &&
    !isDriver() &&
    queryAllowed() &&
    !locked.value &&
    sources.taskDetail() === undefined &&
    record.pickupSlipId === sources.pickupSlipId() &&
    session.allowed('rd:pickup-slip:approve-reduction') &&
    user.getRoles.some((role) => ['rd_sales', 'super_admin'].includes(role)) &&
    positiveId(record.proposerId) &&
    record.proposerId !== session.ownerId &&
    record.status === 'PENDING' &&
    (record.effectiveStatus === 'PENDING' ||
      (decision === 'REJECTED' && record.effectiveStatus === 'STALE')) &&
    validVersion(record.version)
  const preview = computed(() => {
    const lines =
      sources.taskDetail()?.lines.filter((line) => line.lineStatus !== 'CANCELLED') || []
    const removed = lines.filter((line) => selected.value.includes(line.id))
    const weight = (items: typeof lines) =>
      sumReductionWeights(items.map((line) => line.packageWeight))
    return {
      originalPackageCount: lines.length,
      reducedPackageCount: removed.length,
      remainingPackageCount: lines.length - removed.length,
      originalWeight: weight(lines),
      reducedWeight: weight(removed),
      remainingWeight: weight(lines.filter((line) => !selected.value.includes(line.id)))
    }
  })
  const clearDrafts = () => {
    selected.value = []
    reason.value = ''
    reviewRemarks.value = {}
  }
  const restore = () => {
    if (!current()) return
    try {
      const raw = localStorage.getItem(storageKey)
      if (raw) {
        const stored = parseReductionPending(raw, session.ownerId, session.tenant)
        if (pending.value && JSON.stringify(stored) !== JSON.stringify(pending.value))
          throw new Error('本机已有另一份待核少装请求，不能覆盖')
        pending.value = stored
      }
      storageError.value = ''
    } catch {
      storageError.value = '原少装待核记录无法读取，暂不能新写；请保留原账号和租户核对'
    }
  }
  const persist = (original: LoadingReductionPending) => {
    if (!current()) throw new Error('登录身份或页面已变化，请用原账号和租户核对原请求')
    restore()
    if (pending.value || storageError.value) throw new Error('请先查询核对原少装请求')
    try {
      const raw = JSON.stringify(original)
      localStorage.setItem(storageKey, raw)
      if (localStorage.getItem(storageKey) !== raw) throw new Error('待核记录保存未确认')
      pending.value = original
    } catch {
      storageError.value = '原少装请求保存失败，尚未发送，暂不能新写'
      throw new Error(storageError.value)
    }
  }
  const clearPending = (original: LoadingReductionPending) => {
    const raw = localStorage.getItem(storageKey)
    if (
      raw &&
      JSON.stringify(parseReductionPending(raw, session.ownerId, session.tenant)) !==
        JSON.stringify(original)
    )
      throw new Error('本机待核少装请求已变化，不能清除另一份请求')
    localStorage.removeItem(storageKey)
    pending.value = undefined
    storageError.value = ''
  }
  const resolvePending = (list: LoadingReduction[]) => {
    if (!pending.value) return
    const conclusion = findReductionConclusion(list, pending.value)
    if (!conclusion) {
      feedback.value = '少装写入结果待核；未查到原请求不代表未写入'
      return
    }
    clearPending(pending.value)
    clearDrafts()
    feedback.value = `原少装请求已核对：${conclusion.taskNo} · ${reductionStatusLabel(conclusion)}`
  }
  const refresh = async () => {
    if (!current()) {
      invalidate()
      return
    }
    if (!queryAllowed() || writing.value) return
    restore()
    const params = query()!
    const ticket = ++readSequence
    const lifetime = epoch
    const generation = session.generation()
    const applicable = () =>
      current() &&
      ticket === readSequence &&
      lifetime === epoch &&
      generation === session.generation() &&
      queryAllowed()
    reading.value = true
    error.value = ''
    loaded.value = false
    try {
      const list = await api.list(params)
      if (!applicable()) return
      if (
        !Array.isArray(list) ||
        list.length > 100 ||
        list.some((record) =>
          'taskId' in params
            ? record.taskId !== params.taskId
            : record.pickupSlipId !== params.pickupSlipId
        )
      )
        throw new Error('少装清单返回范围不一致，请重新查询')
      records.value = list
      loaded.value = true
      const original = pending.value
      if (
        original &&
        !('taskId' in params
          ? original.taskId === params.taskId
          : original.pickupSlipId === params.pickupSlipId)
      ) {
        const originals = await api.list({ taskId: original.taskId })
        if (!applicable()) return
        if (!Array.isArray(originals) || originals.length > 100)
          throw new Error('原少装请求尚未核实')
        resolvePending(originals)
      } else resolvePending(list)
      if (pending.value && applicable()) {
        const original = pending.value
        const exact = await api.get(
          original.kind === 'create'
            ? { taskId: original.taskId, idempotencyKey: original.request.idempotencyKey }
            : { id: original.request.id }
        )
        if (!applicable()) return
        const conclusion = exact ? findReductionConclusion([exact], original) : undefined
        if (
          conclusion &&
          ('taskId' in params
            ? conclusion.taskId === params.taskId
            : conclusion.pickupSlipId === params.pickupSlipId)
        ) {
          records.value = [
            conclusion,
            ...records.value.filter((record) => record.id !== conclusion.id)
          ].slice(0, 100)
        }
        resolvePending(exact ? [exact] : [])
      }
    } catch (cause) {
      if (applicable()) {
        loaded.value = false
        error.value = cause instanceof Error ? cause.message : '少装清单读取失败，请重新查询'
      }
    } finally {
      if (ticket === readSequence) reading.value = false
    }
  }
  const send = async (original: LoadingReductionPending) => {
    if (
      !queryAllowed() ||
      !session.allowed(
        original.kind === 'create' ? 'rd:loading-task:dispatch' : 'rd:pickup-slip:approve-reduction'
      )
    )
      return
    const lifetime = epoch
    const generation = session.generation()
    const applicable = () => current() && lifetime === epoch && generation === session.generation()
    let sent = false
    writing.value = true
    error.value = ''
    feedback.value = ''
    try {
      persist(original)
      if (!applicable()) return
      sent = true
      const result =
        original.kind === 'create'
          ? await api.create(original.request)
          : await api.review(original.request)
      if (!applicable()) return
      if (!result || !findReductionConclusion([result], original))
        throw new Error('原少装写入返回未确认')
      clearPending(original)
      clearDrafts()
      records.value = [result, ...records.value.filter((record) => record.id !== result.id)].slice(
        0,
        100
      )
      feedback.value = reductionStatusLabel(result)
    } catch (cause) {
      if (!applicable()) return
      if (sent && cause && typeof cause === 'object' && 'businessCode' in cause) {
        try {
          clearPending(original)
        } catch {
          storageError.value = '原少装请求清除失败，请查询核对'
        }
        loaded.value = false
        clearDrafts()
        error.value = cause instanceof Error ? cause.message : '少装办理被拒绝，请刷新清单'
      } else if (sent) {
        feedback.value = '少装写入结果待核；未查到原请求不代表未写入'
      } else error.value = cause instanceof Error ? cause.message : '少装请求尚未发送'
    } finally {
      writing.value = false
    }
  }
  const create = async () => {
    if (!current()) {
      invalidate()
      return
    }
    if (!canCreate.value || !queryAllowed() || !session.allowed('rd:loading-task:dispatch')) return
    try {
      const request = buildReductionCreate(
        sources.taskDetail(),
        selected.value,
        reason.value,
        generateUUID()
      )
      const pickupSlipId = sources.taskDetail()!.task.pickupSlipId
      if (!positiveId(pickupSlipId)) throw new Error('原提货单关联缺失，请重新读取任务')
      await send({
        kind: 'create',
        ownerId: session.ownerId,
        tenant: session.tenant,
        taskId: request.taskId,
        pickupSlipId,
        request
      })
    } catch (cause) {
      if (current()) error.value = cause instanceof Error ? cause.message : '请核对少装清单'
    }
  }
  const review = async (record: LoadingReduction, decision: LoadingReductionReview['decision']) => {
    if (!canReview(record, decision)) return
    const original = records.value.find(
      (item) => item.id === record.id && item.version === record.version
    )
    if (!original || !canReview(original, decision)) return
    const remark = reviewRemarks.value[record.id]?.trim()
    if (!validText(remark) || !['APPROVED', 'REJECTED'].includes(decision)) {
      error.value = '请填写核定说明，最多 1000 字'
      return
    }
    await send({
      kind: 'review',
      ownerId: session.ownerId,
      tenant: session.tenant,
      taskId: original.taskId,
      pickupSlipId: original.pickupSlipId,
      request: { id: original.id, version: original.version, decision, reviewRemark: remark }
    })
  }
  const toggle = (id: number) => {
    if (!current() || !canCreate.value || !candidates.value.some((line) => line.id === id)) return
    if (selected.value.includes(id)) selected.value = selected.value.filter((value) => value !== id)
    else if (selected.value.length < Math.min(100, preview.value.originalPackageCount - 1))
      selected.value = [...selected.value, id]
    else error.value = '每次最多 100 包，且必须保留至少 1 包'
  }
  const invalidate = () => {
    ++epoch
    ++readSequence
    reading.value = false
    loaded.value = false
    records.value = []
    clearDrafts()
    error.value = ''
    feedback.value = ''
    if (!current()) {
      pending.value = undefined
      storageError.value = ''
    } else {
      restore()
      void refresh()
    }
  }
  watch(
    () =>
      JSON.stringify([
        current(),
        sources.taskDetail(),
        sources.pickupSlipId(),
        user.getUser.id,
        user.getRoles,
        [...user.getPermissions].sort()
      ]),
    invalidate,
    { immediate: true, flush: 'sync' }
  )
  const stopAuth = onAuthContextChange(invalidate)
  const visibilityChanged = () => {
    pageVisible.value = document.visibilityState !== 'hidden'
  }
  const storageChanged = () => {
    invalidate()
  }
  onMounted(() => {
    visibilityChanged()
    document.addEventListener('visibilitychange', visibilityChanged)
    window.addEventListener('storage', storageChanged)
  })
  onDeactivated(() => {
    pageActive.value = false
  })
  onActivated(() => {
    pageActive.value = true
  })
  onBeforeUnmount(() => {
    alive = false
    stopAuth()
    document.removeEventListener('visibilitychange', visibilityChanged)
    window.removeEventListener('storage', storageChanged)
    invalidate()
  })
  return {
    records,
    selected,
    reason,
    reviewRemarks,
    pending,
    reading,
    writing,
    error,
    storageError,
    feedback,
    candidates,
    preview,
    canQuery,
    showCreate,
    canCreate,
    canReview,
    blockedByRecord,
    locked,
    create,
    review,
    refresh,
    toggle
  }
}
