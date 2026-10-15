import { computed, onActivated, onBeforeUnmount, onDeactivated, ref, shallowRef, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import {
  getAccessToken,
  getAuthContextRevision,
  getCachedAuthScope,
  getCurrentUserId,
  getTenantId,
  getVisitTenantId,
  onAuthContextChange
} from '@/utils/auth'
import { RdWeightApi, type WeightSuggestion } from '@/api/rd/weight'
import type { PickupSlip, PickupSlipLine } from '@/api/rd/loading'

const permissions = ['rd:pickup-slip:query', 'rd:package-unit:query', 'rd:loading-task:dispatch']
const solutionStates = new Set(['OPTIMAL', 'FEASIBLE'])
const states = new Set([
  ...solutionStates,
  'INFEASIBLE',
  'UNKNOWN',
  'MODEL_INVALID',
  'UNAVAILABLE',
  'BUSY',
  'BUDGET_EXCEEDED'
])
const id = (value: unknown): value is number => Number.isSafeInteger(value) && Number(value) > 0
const text = (value: unknown): value is string =>
  typeof value === 'string' && value.trim().length > 0
// Convert the decimal representation, never silently round an extra precision digit.
const grams = (value: unknown): number | undefined => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 1000000) return
  const match = /^(\d+)(?:\.(\d{1,3}))?$/.exec(String(value))
  if (!match) return
  return Number(match[1]) * 1000 + Number((match[2] || '').padEnd(3, '0'))
}

export const weightStatusLabel = (state: string) =>
  ({
    OPTIMAL: '已证明最优',
    FEASIBLE: '可行方案，未证明最优',
    INFEASIBLE: '容差内无可行组合',
    UNKNOWN: '预算内未找到方案，未证明无解',
    MODEL_INVALID: '求解模型无效',
    UNAVAILABLE: '求解器不可用',
    BUSY: '求解队列繁忙',
    BUDGET_EXCEEDED: '候选读取预算已用尽'
  })[state] || '未取得求解结果'

type Detail = { slip: PickupSlip; lines: PickupSlipLine[] }

export const useWeightAssistant = () => {
  const user = useUserStore()
  const route = useRoute()
  const router = useRouter()
  const allowed = ref(false)
  const slips = shallowRef<PickupSlip[]>([])
  const detail = shallowRef<Detail>()
  const result = shallowRef<WeightSuggestion>()
  const slipId = ref<number>()
  const lineId = ref<number>()
  const target = ref<number | undefined>()
  const tolerance = ref<number>(0)
  const keyword = ref('')
  const page = ref(1)
  const total = ref(0)
  const loading = ref(false)
  const detailLoading = ref(false)
  const solving = ref(false)
  const error = ref('')
  let alive = true
  let active = true
  let revoked = false
  let epoch = 0
  let listSequence = 0
  let detailSequence = 0
  let solveSequence = 0
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
  const owner = signature()
  const clear = () => {
    epoch++
    listSequence++
    detailSequence++
    solveSequence++
    slips.value = []
    total.value = 0
    detail.value = undefined
    result.value = undefined
    slipId.value = undefined
    lineId.value = undefined
    target.value = undefined
    tolerance.value = 0
    keyword.value = ''
    page.value = 1
    loading.value = detailLoading.value = solving.value = false
    error.value = ''
  }
  const invalidate = () => {
    revoked = true
    allowed.value = false
    clear()
  }
  const refreshAccess = () => {
    if (signature() !== owner) invalidate()
    const grants = user.getPermissions
    allowed.value =
      alive &&
      active &&
      !revoked &&
      user.getUser.id > 0 &&
      !!getAccessToken() &&
      getCurrentUserId() === user.getUser.id &&
      !user.getRoles.includes('rd_forklift_driver') &&
      permissions.every((permission) => grants.has('*:*:*') || grants.has(permission))
    return allowed.value
  }
  const stamp = () => ({ epoch, route: route.fullPath })
  const current = (captured: ReturnType<typeof stamp>) =>
    refreshAccess() && captured.epoch === epoch && captured.route === route.fullPath
  const fail = (cause: unknown) => {
    const value = cause as
      | { response?: { status?: number }; code?: number; businessCode?: number }
      | undefined
    if (
      cause === 'error' ||
      [401, 403].some(
        (code) =>
          value?.response?.status === code || value?.code === code || value?.businessCode === code
      )
    ) {
      invalidate()
      error.value = '当前身份无配吨权限'
    } else error.value = '读取或计算失败，请重新查询原提货单'
  }
  const line = computed(() => detail.value?.lines.find((item) => item.id === lineId.value))
  const canSuggest = computed(() => {
    const maximum = grams(line.value?.requestedWeight)
    const requested = grams(target.value)
    const margin = grams(tolerance.value)
    return (
      allowed.value &&
      !detailLoading.value &&
      !solving.value &&
      detail.value?.slip.status === 'ISSUED' &&
      id(slipId.value) &&
      id(lineId.value) &&
      maximum !== undefined &&
      maximum > 0 &&
      requested !== undefined &&
      requested > 0 &&
      margin !== undefined &&
      margin < requested &&
      requested + margin <= maximum
    )
  })
  const loadSlips = async () => {
    if (!refreshAccess()) return
    const captured = stamp()
    const sequence = ++listSequence
    loading.value = true
    error.value = ''
    slips.value = []
    try {
      const value = await RdWeightApi.page({
        pageNo: page.value,
        pageSize: 20,
        status: 'ISSUED',
        slipNo: keyword.value.trim() || undefined
      })
      if (!current(captured) || sequence !== listSequence) return
      if (
        !Array.isArray(value?.list) ||
        !Number.isSafeInteger(value.total) ||
        value.total < 0 ||
        !value.list.every(
          (item) =>
            id(item.id) && item.status === 'ISSUED' && text(item.slipNo) && text(item.customerName)
        )
      )
        throw new Error('Invalid slips')
      slips.value = value.list
      total.value = value.total
    } catch (cause) {
      if (current(captured) && sequence === listSequence) fail(cause)
    } finally {
      if (captured.epoch === epoch && sequence === listSequence) loading.value = false
    }
  }
  const selectSlip = async (selected: number) => {
    if (!refreshAccess() || !id(selected)) return
    const captured = stamp()
    const sequence = ++detailSequence
    slipId.value = selected
    detail.value = undefined
    lineId.value = undefined
    result.value = undefined
    detailLoading.value = true
    error.value = ''
    try {
      const value = await RdWeightApi.detail(selected)
      if (!current(captured) || sequence !== detailSequence || slipId.value !== selected) return
      if (
        value?.slip?.id !== selected ||
        value.slip.status !== 'ISSUED' ||
        !text(value.slip.customerName) ||
        !Array.isArray(value.lines) ||
        !value.lines.length ||
        !value.lines.every(
          (item) => id(item.id) && item.slipId === selected && text(item.productModel)
        ) ||
        new Set(value.lines.map((item) => item.id)).size !== value.lines.length
      )
        throw new Error('Invalid slip lines')
      detail.value = value
      if (value.lines.length === 1) lineId.value = value.lines[0].id
    } catch (cause) {
      if (current(captured) && sequence === detailSequence) fail(cause)
    } finally {
      if (captured.epoch === epoch && sequence === detailSequence) detailLoading.value = false
    }
  }
  const validResult = (
    value: WeightSuggestion,
    request: {
      pickupSlipId: number
      pickupSlipLineId: number
      targetWeightKg: number
      toleranceKg: number
    }
  ) => {
    if (
      !value ||
      !states.has(value.solverStatus) ||
      value.pickupSlipId !== request.pickupSlipId ||
      value.pickupSlipLineId !== request.pickupSlipLineId ||
      grams(value.targetWeightKg) !== grams(request.targetWeightKg) ||
      grams(value.toleranceKg) !== grams(request.toleranceKg) ||
      value.weightScale !== 3 ||
      !Array.isArray(value.bags) ||
      value.bags.length > 500
    )
      return false
    if (!solutionStates.has(value.solverStatus))
      return !value.withinTolerance && value.bags.length === 0 && value.totalWeightKg === null
    const rows = value.bags
    if (
      !value.withinTolerance ||
      !rows.length ||
      !rows.every(
        (bag) =>
          id(bag.packageId) &&
          text(bag.packageCode) &&
          id(bag.locationId) &&
          id(bag.stockLotId) &&
          text(bag.locationCode) &&
          (grams(bag.packageWeightKg) ?? 0) > 0
      ) ||
      new Set(rows.map((bag) => bag.packageId)).size !== rows.length ||
      new Set(rows.map((bag) => bag.packageCode)).size !== rows.length ||
      (line.value?.requestedPackageCount != null && rows.length > line.value.requestedPackageCount)
    )
      return false
    const weight = rows.reduce((sum, bag) => sum + grams(bag.packageWeightKg)!, 0)
    return (
      grams(value.totalWeightKg) === weight &&
      Math.abs(weight - grams(request.targetWeightKg)!) <= grams(request.toleranceKg)!
    )
  }
  const suggest = async () => {
    if (!refreshAccess() || !canSuggest.value) return
    const captured = stamp()
    const sequence = ++solveSequence
    const request = {
      pickupSlipId: slipId.value!,
      pickupSlipLineId: lineId.value!,
      targetWeightKg: target.value!,
      toleranceKg: tolerance.value
    }
    result.value = undefined
    error.value = ''
    solving.value = true
    try {
      const value = await RdWeightApi.suggest(request)
      if (!current(captured) || sequence !== solveSequence) return
      if (!validResult(value, request)) throw new Error('Invalid suggestion')
      result.value = value
    } catch (cause) {
      if (current(captured) && sequence === solveSequence) fail(cause)
    } finally {
      if (captured.epoch === epoch && sequence === solveSequence) solving.value = false
    }
  }
  const canOpenOriginal = computed(
    () =>
      allowed.value &&
      !!detail.value &&
      (user.getPermissions.has('*:*:*') || user.getPermissions.has('rd:loading-task:query'))
  )
  const openOriginal = async () => {
    if (!refreshAccess() || !canOpenOriginal.value || !detail.value) return
    const captured = stamp()
    const selected = slipId.value
    error.value = ''
    try {
      const value = await RdWeightApi.tasks({
        pageNo: 1,
        pageSize: 20,
        pickupSlipNo: detail.value.slip.slipNo
      })
      if (!current(captured) || selected !== slipId.value) return
      const tasks =
        value?.list?.filter(
          (item) => item.pickupSlipId === selected && item.status !== 'CANCELLED'
        ) || []
      if (tasks.length !== 1 || !id(tasks[0].id)) {
        error.value = '未找到唯一的原整车任务，请在原装车任务列表核对'
        return
      }
      void router.push({
        name: 'RdLoadingDispatch',
        query: { taskId: String(tasks[0].id), mode: 'view' }
      })
    } catch (cause) {
      if (current(captured) && selected === slipId.value) fail(cause)
    }
  }
  const stopOwner = watch(signature, refreshAccess, { immediate: true, flush: 'sync' })
  const stopRoute = watch(() => route.fullPath, clear, { flush: 'sync' })
  const stopLine = watch(
    lineId,
    () => {
      target.value = grams(line.value?.requestedWeight)
        ? Number(line.value?.requestedWeight)
        : undefined
      tolerance.value = 0
    },
    { flush: 'sync' }
  )
  const stopInput = watch(
    () => [lineId.value, target.value, tolerance.value],
    () => {
      solveSequence++
      result.value = undefined
      solving.value = false
    },
    { flush: 'sync' }
  )
  const stopAuth = onAuthContextChange(invalidate)
  const storageChanged = (event: StorageEvent) => {
    if (event.key === null || event.key === 'ACCESS_TOKEN' || event.key === 'REFRESH_TOKEN')
      invalidate()
    else if (!refreshAccess()) clear()
  }
  const timer = window.setInterval(refreshAccess, 1000)
  window.addEventListener('storage', storageChanged)
  window.addEventListener('focus', refreshAccess)
  document.addEventListener('visibilitychange', refreshAccess)
  onDeactivated(() => {
    active = false
    allowed.value = false
    clear()
  })
  onActivated(() => {
    const resume = !active
    active = true
    if (refreshAccess() && resume) void loadSlips()
  })
  onBeforeUnmount(() => {
    alive = false
    invalidate()
    stopOwner()
    stopRoute()
    stopLine()
    stopInput()
    stopAuth()
    window.clearInterval(timer)
    window.removeEventListener('storage', storageChanged)
    window.removeEventListener('focus', refreshAccess)
    document.removeEventListener('visibilitychange', refreshAccess)
  })
  return {
    allowed,
    slips,
    detail,
    result,
    slipId,
    lineId,
    line,
    target,
    tolerance,
    keyword,
    page,
    total,
    loading,
    detailLoading,
    solving,
    error,
    canSuggest,
    canOpenOriginal,
    refreshAccess,
    loadSlips,
    selectSlip,
    suggest,
    openOriginal
  }
}
