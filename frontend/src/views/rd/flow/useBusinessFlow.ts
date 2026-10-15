import { ref, computed, watch, onBeforeUnmount, onDeactivated } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { getAccessToken, getAuthContextRevision, onAuthContextChange } from '@/utils/auth'
import { RdProductionTaskApi, type RdProductionTaskVO } from '@/api/rd/production/task'
import {
  RdPackageUnitApi,
  type RdPackageUnitVO,
  type RdPackageTraceVO,
  type RdPackageWorkContextVO
} from '@/api/rd/stock/package'
import { canViewBusinessFlow, summarizeFlow, validateTaskPackages } from './flowFacts'

export const useBusinessFlow = () => {
  const user = useUserStore()
  const owner = user.getUser.id
  const revision = getAuthContextRevision()
  const contextValid = ref(true)
  const allowed = computed(() => canViewBusinessFlow(user.getRoles, user.getPermissions))
  const task = ref<RdProductionTaskVO>()
  const packages = ref<RdPackageUnitVO[]>([])
  const choices = ref<RdProductionTaskVO[]>([])
  const total = ref(0)
  const trace = ref<RdPackageTraceVO>()
  const context = ref<RdPackageWorkContextVO>()
  const workByCode = ref<Record<string, RdPackageWorkContextVO>>({})
  const workError = ref('')
  const selectedCode = ref('')
  const busy = ref(false)
  const searching = ref(false)
  const detailBusy = ref(false)
  const error = ref('')
  const searchError = ref('')
  const detailError = ref('')
  const updatedAt = ref(0)
  const factsRevision = ref(0)
  let sequence = 0
  let detailSequence = 0
  let searchSequence = 0
  let alive = true
  const current = () =>
    alive &&
    contextValid.value &&
    !!getAccessToken() &&
    getAuthContextRevision() === revision &&
    user.getUser.id === owner &&
    allowed.value
  const permitted = (key: string) =>
    current() && (user.getPermissions.has('*:*:*') || user.getPermissions.has(key))
  const clearDetail = () => {
    detailSequence++
    trace.value = undefined
    context.value = undefined
    detailBusy.value = false
    detailError.value = ''
    factsRevision.value++
  }
  const clear = () => {
    sequence++
    searchSequence++
    clearDetail()
    task.value = undefined
    packages.value = []
    workByCode.value = {}
    workError.value = ''
    choices.value = []
    total.value = 0
    updatedAt.value = 0
    selectedCode.value = ''
    busy.value = false
    searching.value = false
    error.value = ''
    searchError.value = ''
  }
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
  const searchTasks = async (customerName = '', pageNo = 1) => {
    if (!current()) return []
    const attempt = ++searchSequence
    searching.value = true
    searchError.value = ''
    choices.value = []
    total.value = 0
    try {
      const result = await RdProductionTaskApi.getPage({ pageNo, pageSize: 20, customerName })
      if (!current() || attempt !== searchSequence) return []
      if (
        !result ||
        !Array.isArray(result.list) ||
        !Number.isSafeInteger(result.total) ||
        result.total < 0
      )
        throw new Error('任务列表格式待核')
      choices.value = result.list
      total.value = result.total
      return result.list
    } catch {
      if (current() && attempt === searchSequence) searchError.value = '任务查询失败，请重试'
      return []
    } finally {
      if (attempt === searchSequence) searching.value = false
    }
  }
  const selectPackage = async (code: string) => {
    if (!current() || !packages.value.some((row) => row.packageCode === code)) return
    clearDetail()
    selectedCode.value = code
    const attempt = detailSequence
    const taskAttempt = sequence
    detailBusy.value = true
    try {
      const result = await RdPackageUnitApi.getTraceDetail(code)
      if (!current() || attempt !== detailSequence || taskAttempt !== sequence) return
      const bag = packages.value.find((row) => row.packageCode === code)
      if (
        !result ||
        !result.internalView ||
        result.packageCode !== code ||
        result.productionTaskId !== task.value?.id ||
        !bag ||
        result.status !== bag.status
      )
        throw new Error('包件范围或状态已变化，请刷新当前任务后核对')
      trace.value = result
      if (permitted('rd:putaway-task:query') && permitted('rd:loading-task:query')) {
        try {
          const work = await RdPackageUnitApi.getWorkContext(code)
          if (!current() || attempt !== detailSequence || taskAttempt !== sequence) return
          if (!work || work.packageCode !== code || !Array.isArray(work.tasks)) throw new Error()
          context.value = { packageCode: work.packageCode, tasks: work.tasks }
        } catch {
          if (current() && attempt === detailSequence && taskAttempt === sequence)
            detailError.value = '关联任务读取失败，未将未知任务作为已完成'
        }
      }
    } catch (cause) {
      if (current() && attempt === detailSequence && taskAttempt === sequence)
        detailError.value = cause instanceof Error ? cause.message : '流转证据读取失败，请重试'
    } finally {
      if (attempt === detailSequence) detailBusy.value = false
    }
  }
  const loadTask = async (id: number) => {
    if (!current() || !Number.isSafeInteger(id) || id <= 0) return
    const keepCode = task.value?.id === id ? selectedCode.value : ''
    clearDetail()
    const attempt = ++sequence
    task.value = undefined
    packages.value = []
    workByCode.value = {}
    workError.value = ''
    selectedCode.value = ''
    updatedAt.value = 0
    error.value = ''
    busy.value = true
    try {
      const record = await RdProductionTaskApi.get(id)
      if (!current() || attempt !== sequence) return
      if (!record || record.id !== id || typeof record.customerName !== 'string')
        throw new Error('生产任务身份待核')
      const rows = await RdPackageUnitApi.getTaskPackages(id)
      if (!current() || attempt !== sequence) return
      const validated = validateTaskPackages(id, rows)
      const work: Record<string, RdPackageWorkContextVO> = {}
      if (permitted('rd:putaway-task:query') && permitted('rd:loading-task:query')) {
        try {
          for (let offset = 0; offset < validated.length; offset += 4) {
            if (!current() || attempt !== sequence) return
            const entries = await Promise.all(
              validated.slice(offset, offset + 4).map(async (row) => {
                const value = await RdPackageUnitApi.getWorkContext(row.packageCode)
                if (
                  !value ||
                  value.packageCode !== row.packageCode ||
                  !Array.isArray(value.tasks) ||
                  value.tasks.some(
                    (item) =>
                      !['PUTAWAY', 'LOADING'].includes(item.type) ||
                      !Number.isSafeInteger(item.taskId) ||
                      item.taskId <= 0 ||
                      typeof item.status !== 'string'
                  )
                )
                  throw new Error('作业状态范围待核')
                return value
              })
            )
            for (const value of entries) work[value.packageCode] = value
          }
          if (!current() || attempt !== sequence) return
          workByCode.value = Object.fromEntries(
            Object.entries(work).map(([code, value]) => [
              code,
              { packageCode: value.packageCode, tasks: value.tasks }
            ])
          )
        } catch {
          if (!current() || attempt !== sequence) return
          workError.value = '作业状态未完整读取；在库状态不代表可用库存，请刷新核对'
        }
      } else {
        workError.value = '当前岗位无完整仓储作业查看权限；在库状态不代表可用库存'
      }
      if (!current() || attempt !== sequence) return
      packages.value = validated
      task.value = record
      updatedAt.value = Date.now()
      factsRevision.value++
      const code =
        rows.find((row) => row.packageCode === keepCode)?.packageCode || rows[0]?.packageCode
      if (code) await selectPackage(code)
    } catch (cause) {
      if (current() && attempt === sequence) {
        task.value = undefined
        packages.value = []
        error.value = cause instanceof Error ? cause.message : '业务数据读取失败，请刷新'
      }
    } finally {
      if (attempt === sequence) busy.value = false
    }
  }
  onDeactivated(invalidate)
  onBeforeUnmount(() => {
    alive = false
    clear()
    stopAuth()
    stopScope()
  })
  return {
    allowed,
    contextValid,
    task,
    packages,
    choices,
    total,
    trace,
    context,
    workByCode,
    workError,
    selectedCode,
    busy,
    searching,
    detailBusy,
    error,
    searchError,
    detailError,
    updatedAt,
    factsRevision,
    summary: computed(() => summarizeFlow(packages.value, workByCode.value)),
    searchTasks,
    loadTask,
    selectPackage,
    permitted
  }
}
