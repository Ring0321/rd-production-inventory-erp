import { computed, reactive, ref, shallowRef } from 'vue'
import { ElMessageBox } from 'element-plus'
import { generateUUID } from '@/utils'
import { extractPackageCode } from '@/utils/rdDigitalLink'
import {
  RdPutawayTaskApi as api,
  type RdPutawayTaskDetailVO,
  type RdPutawayLocationMapVO,
  type RdPutawayTaskStatus,
  type RdPutawayTaskVO
} from '@/api/rd/stock/putaway'
import type { WarehouseSession } from './useWarehouseSession'
import { useBatchPending } from './useBatchPending'

export const usePutawayWork = (
  session: WarehouseSession,
  changed: () => Promise<unknown>,
  resolveLocation: (id: number) => RdPutawayLocationMapVO | undefined
) => {
  const canQuery = computed(() => session.allowed('rd:putaway-task:query'))
  const canConfirm = computed(() => session.allowed('rd:putaway-task:confirm'))
  const canAssign = computed(() => session.allowed('rd:putaway-task:assign'))
  const store = useBatchPending(session, 'putaway')
  const { pending, storageError } = store
  const input = ref('')
  const detail = shallowRef<RdPutawayTaskDetailVO>()
  const targetId = ref<number>()
  const selected = ref<number[]>([])
  const finalLocationId = ref<number>()
  const handlingResult = ref('')
  const rows = shallowRef<RdPutawayTaskVO[]>([])
  const total = ref(0)
  const query = reactive({ pageNo: 1, pageSize: 10, taskNo: '', status: '' })
  const reading = ref(false)
  const writing = ref(false)
  const error = ref('')
  const feedback = ref('')
  const needsCheck = ref(false)
  let revision = 0
  let listRevision = 0
  const locked = computed(
    () =>
      writing.value ||
      reading.value ||
      !!pending.value ||
      !!storageError.value ||
      needsCheck.value ||
      !session.current()
  )
  const eligible = computed(
    () =>
      detail.value?.lines.filter((line) => line.lineStatus === 'PENDING' && line.scanCount === 0) ||
      []
  )
  const selectedWeight = computed(
    () =>
      detail.value?.lines
        .filter((line) => selected.value.includes(line.id))
        .reduce((sum, line) => sum + line.packageWeight, 0) || 0
  )
  const selectable = (id: number) =>
    canConfirm.value &&
    !locked.value &&
    ['ASSIGNED', 'MOVING'].includes(detail.value?.task.status || '') &&
    eligible.value.some((line) => line.id === id)
  const toggle = (id: number) => {
    if (!selectable(id)) return
    selected.value = selected.value.includes(id)
      ? selected.value.filter((value) => value !== id)
      : [...selected.value, id]
  }
  const selectAll = () => {
    if (eligible.value.some((line) => selectable(line.id)))
      selected.value = eligible.value.map((line) => line.id)
  }
  const apply = (value: RdPutawayTaskDetailVO) => {
    detail.value = value
    targetId.value = value.task.id
    finalLocationId.value = value.task.finalLocationId || value.task.plannedLocationId
    selected.value = []
    needsCheck.value = false
  }
  const loadList = async (pageNo = query.pageNo) => {
    if (!session.allowed('rd:putaway-task:query')) return
    const request = ++listRevision
    query.pageNo = pageNo
    try {
      const page = await api.getPage({
        ...query,
        status: (query.status || undefined) as RdPutawayTaskStatus | undefined
      })
      if (session.current() && request === listRevision) {
        rows.value = page.list
        total.value = page.total
      }
    } catch {
      if (session.current() && request === listRevision) error.value = '上架任务列表读取失败'
    }
  }
  const read = async (id?: number, locate = false) => {
    if (!session.allowed('rd:putaway-task:query') || writing.value) return
    const code = extractPackageCode(input.value)
    if (locate && (!code || code.length > 128 || /[\s\\/?#:\u0000-\u001f\u007f]/u.test(code))) {
      error.value = '请输入有效包号或货物二维码'
      return
    }
    const request = ++revision
    reading.value = true
    error.value = ''
    selected.value = []
    try {
      const value = locate
        ? await api.previewBatchScan({ id: id ?? targetId.value, packageCode: code })
        : await api.get(id ?? targetId.value!)
      if (!session.current() || request !== revision) return
      apply(value)
      if (!locate) input.value = value.lines[0]?.packageCode || ''
    } catch {
      if (session.current() && request === revision) {
        needsCheck.value = true
        error.value = '上架任务读取失败，请重新核对'
      }
    } finally {
      if (request === revision) reading.value = false
    }
  }
  const open = (id: number) => read(id)
  const locate = () => {
    if (!pending.value && !storageError.value) return read(undefined, true)
  }
  const reset = () => {
    if (writing.value || pending.value) return
    revision++
    detail.value = undefined
    targetId.value = undefined
    input.value = ''
    selected.value = []
    needsCheck.value = false
  }
  const confirmBatch = async () => {
    if (!session.allowed('rd:putaway-task:confirm') || writing.value || reading.value) return
    if (
      !pending.value &&
      (locked.value || !detail.value || !selected.value.length || !selected.value.every(selectable))
    )
      return
    writing.value = true
    error.value = ''
    feedback.value = ''
    try {
      if (!pending.value) {
        const task = detail.value!.task
        const ids = [...selected.value]
        const code = extractPackageCode(input.value)
        if (!detail.value!.lines.some((line) => line.packageCode === code))
          throw new Error('代表包不在当前任务中')
        await ElMessageBox.confirm(
          `确认 ${ids.length} 包已实际到位至 ${task.plannedLocationCode}？`,
          '到位核验'
        )
        session.assertCurrent()
        if (!session.allowed('rd:putaway-task:confirm')) throw new Error('当前无上架核验权限')
        store.capture({
          kind: 'putaway',
          ownerId: session.ownerId,
          tenant: session.tenant,
          uncertain: false,
          request: {
            id: task.id,
            version: task.version,
            packageCode: code,
            taskLineIds: ids,
            idempotencyKey: generateUUID(),
            deviceCode: 'WAREHOUSE-PDA-WEB'
          }
        })
      }
      if (pending.value?.kind !== 'putaway') throw new Error('原请求业务不一致')
      store.persist()
      session.assertCurrent()
      const result = await api.confirmBatchScan(pending.value.request)
      session.assertCurrent()
      if (result.task.id !== pending.value.request.id)
        throw new Error('核验返回的任务不一致，请核对原请求')
      apply(result)
      store.clear()
      feedback.value = result.replayed
        ? '原请求已核验，未重复计数'
        : `已核验 ${result.confirmedCount} 包`
      await loadList()
      if (session.current()) await changed()
    } catch (cause) {
      if (cause !== 'cancel' && cause !== 'close') {
        const known = !!(cause && typeof cause === 'object' && 'businessCode' in cause)
        if (pending.value) {
          if (known && !pending.value.uncertain && session.current()) store.clear()
          else {
            try {
              store.markUncertain()
            } catch {
              /* Original in-memory request is retained. */
            }
          }
        }
        error.value = cause instanceof Error ? cause.message : '核验结果待核，请重试原请求'
      }
    } finally {
      writing.value = false
    }
  }
  const act = async (action: 'confirm' | 'reassign') => {
    if (
      !session.current() ||
      locked.value ||
      !detail.value ||
      (action === 'confirm' ? !canConfirm.value : !canAssign.value)
    )
      return
    const id = detail.value.task.id
    const expectedVersion = detail.value.task.version
    const locationId = finalLocationId.value
    const reason = handlingResult.value.trim()
    if (!locationId || (action === 'reassign' && !reason)) {
      error.value = '请选择库位并填写异常处理说明'
      return
    }
    writing.value = true
    error.value = ''
    feedback.value = ''
    let sent = false
    try {
      const location = resolveLocation(locationId)
      if (!location?.enabled || location.frozen)
        throw new Error('库位未加载、已停用或冻结，请刷新库位后核定')
      const current = await api.get(id)
      session.assertCurrent()
      if (current.task.version !== expectedVersion) {
        apply(current)
        throw new Error('任务版本已变化，请重新核定位置和异常处理')
      }
      detail.value = current
      if (action === 'confirm' && current.task.status !== 'PENDING_CONFIRMATION')
        throw new Error('任务尚未全部核验或已处理，请核对当前状态')
      if (action === 'reassign' && current.task.status !== 'EXCEPTION_PENDING')
        throw new Error('任务已不处于异常待处理状态')
      await ElMessageBox.confirm(
        action === 'confirm'
          ? `确认任务 ${current.task.taskNo} 的 ${current.task.packageCount} 包正式入库至 ${location.locationCode}？`
          : `确认处理任务 ${current.task.taskNo} 的异常并改派至 ${location.locationCode}？`,
        action === 'confirm' ? '最终入库' : '处理上架异常'
      )
      session.assertCurrent()
      if (
        !session.allowed(
          action === 'confirm' ? 'rd:putaway-task:confirm' : 'rd:putaway-task:assign'
        )
      )
        throw new Error('当前操作权限已变化')
      sent = true
      if (action === 'confirm') {
        const orderId = await api.confirm({
          id,
          version: current.task.version,
          finalLocationId: locationId
        })
        if (!Number.isSafeInteger(orderId) || orderId <= 0)
          throw new Error('入库返回未确认，请核对任务')
      } else {
        const acknowledged = await api.reassign({
          id,
          version: current.task.version,
          plannedLocationId: locationId,
          forkliftDriverId: current.task.forkliftDriverId,
          handlingResult: reason
        })
        if (acknowledged !== true) throw new Error('异常处理未确认，请核对任务')
      }
      session.assertCurrent()
      const verified = await api.get(id)
      session.assertCurrent()
      const openIds = current.exceptions
        .filter((item) => item.exceptionStatus === 'OPEN')
        .map((item) => item.id)
      if (
        verified.task.id !== id ||
        (action === 'confirm'
          ? verified.task.status !== 'PUTAWAY_COMPLETED' ||
            verified.task.finalLocationId !== locationId
          : verified.task.plannedLocationId !== locationId ||
            verified.exceptions.some(
              (item) => openIds.includes(item.id) && item.exceptionStatus === 'OPEN'
            ))
      )
        throw new Error('任务返回的实际状态或库位未确认，请重新核对')
      apply(verified)
      feedback.value = action === 'confirm' ? '入库已确认' : '上架异常已处理'
      await loadList()
      if (session.current()) await changed()
    } catch (cause) {
      if (cause !== 'cancel' && cause !== 'close') {
        if (sent) needsCheck.value = true
        error.value = cause instanceof Error ? cause.message : '办理结果待核，请重新读取任务状态'
      }
    } finally {
      writing.value = false
    }
  }
  if (pending.value?.kind === 'putaway') {
    targetId.value = pending.value.request.id
    input.value = pending.value.request.packageCode
  }
  return {
    canQuery,
    canConfirm,
    canAssign,
    pending,
    storageError,
    input,
    detail,
    selected,
    selectedWeight,
    finalLocationId,
    handlingResult,
    rows,
    total,
    query,
    reading,
    writing,
    error,
    feedback,
    needsCheck,
    locked,
    selectable,
    toggle,
    selectAll,
    loadList,
    open,
    locate,
    read,
    reset,
    confirmBatch,
    act
  }
}
