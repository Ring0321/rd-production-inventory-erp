import { computed, reactive, ref, shallowRef } from 'vue'
import { ElMessageBox } from 'element-plus'
import { generateUUID } from '@/utils'
import { extractPackageCode } from '@/utils/rdDigitalLink'
import {
  RdLoadingApi as api,
  type LoadingTask,
  type LoadingTaskDetail,
  type LoadingBatchScanResp
} from '@/api/rd/loading'
import type { WarehouseSession } from './useWarehouseSession'
import { useBatchPending } from './useBatchPending'

export const useLoadingWork = (session: WarehouseSession, changed: () => Promise<unknown>) => {
  const canQuery = computed(() => session.allowed('rd:loading-task:query'))
  const canDispatch = computed(() => session.allowed('rd:loading-task:dispatch'))
  const store = useBatchPending(session, 'loading')
  const { pending, storageError } = store
  const input = ref('')
  const detail = shallowRef<LoadingTaskDetail>()
  const targetId = ref<number>()
  const selected = ref<number[]>([])
  const replacementLineId = ref<number>()
  const replacementCode = ref('')
  const resolutionAction = ref('RETURN_TO_DRIVER')
  const handlingResult = ref('')
  const rows = shallowRef<LoadingTask[]>([])
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
    () => detail.value?.lines.filter((line) => line.lineStatus === 'PLANNED') || []
  )
  const selectedWeight = computed(
    () =>
      detail.value?.lines
        .filter((line) => selected.value.includes(line.id))
        .reduce((sum, line) => sum + line.packageWeight, 0) || 0
  )
  const replacementOptions = computed(
    () =>
      detail.value?.lines.filter((line) => ['PLANNED', 'EXCEPTION'].includes(line.lineStatus)) || []
  )
  const selectable = (id: number) =>
    canDispatch.value &&
    !locked.value &&
    ['ASSIGNED', 'LOADING'].includes(detail.value?.task.status || '') &&
    eligible.value.some((line) => line.id === id)
  const toggle = (id: number) => {
    if (!selectable(id)) return
    selected.value = selected.value.includes(id)
      ? selected.value.filter((value) => value !== id)
      : [...selected.value, id]
  }
  const selectAll = () => {
    if (eligible.value.length > 500) {
      error.value = '本次核验最多 500 包，请核对所选范围'
      return
    }
    if (eligible.value.some((line) => selectable(line.id)))
      selected.value = eligible.value.map((line) => line.id)
  }
  const apply = (value: LoadingTaskDetail | LoadingBatchScanResp) => {
    detail.value = {
      task: value.task,
      lines: value.lines,
      exceptions: 'exceptions' in value ? value.exceptions : []
    }
    targetId.value = value.task.id
    selected.value = []
    replacementLineId.value = undefined
    replacementCode.value = ''
    needsCheck.value = false
  }
  const loadList = async (pageNo = query.pageNo) => {
    if (!session.allowed('rd:loading-task:query')) return
    const request = ++listRevision
    query.pageNo = pageNo
    try {
      const page = await api.getTaskPage({ ...query, status: query.status || undefined })
      if (session.current() && request === listRevision) {
        rows.value = page.list
        total.value = page.total
      }
    } catch {
      if (session.current() && request === listRevision) error.value = '装车任务列表读取失败'
    }
  }
  const read = async (id?: number, locate = false) => {
    if (
      !session.allowed('rd:loading-task:query') ||
      writing.value ||
      (locate && !session.allowed('rd:loading-task:dispatch'))
    )
      return
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
        ? await api.previewBatchScan({
            taskId: id ?? targetId.value,
            packageCode: code,
            deviceCode: 'WAREHOUSE-PDA-WEB'
          })
        : await api.getTask(id ?? targetId.value!)
      if (!session.current() || request !== revision) return
      apply(value)
      if (!locate) input.value = value.lines[0]?.packageCode || ''
      else {
        const full = await api.getTask(value.task.id)
        if (!session.current() || request !== revision) return
        apply(full)
      }
    } catch {
      if (session.current() && request === revision) {
        needsCheck.value = true
        error.value = '装车任务读取失败，请重新核对'
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
    if (!session.allowed('rd:loading-task:dispatch') || writing.value || reading.value) return
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
        const version = task.version
        const ids = [...selected.value]
        const code = extractPackageCode(input.value)
        if (typeof version !== 'number' || !Number.isSafeInteger(version))
          throw new Error('任务版本缺失，请重新读取')
        if (!detail.value!.lines.some((line) => line.packageCode === code))
          throw new Error('代表包不在当前任务中')
        await ElMessageBox.confirm(
          `确认任务 ${task.taskNo} 的所选 ${ids.length} 包已实际装车？`,
          '装车核验'
        )
        session.assertCurrent()
        if (!session.allowed('rd:loading-task:dispatch')) throw new Error('当前无装车核验权限')
        store.capture({
          kind: 'loading',
          ownerId: session.ownerId,
          tenant: session.tenant,
          uncertain: false,
          request: {
            taskId: task.id,
            version,
            representativePackageCode: code,
            taskLineIds: ids,
            idempotencyKey: generateUUID(),
            deviceCode: 'WAREHOUSE-PDA-WEB'
          }
        })
      }
      if (pending.value?.kind !== 'loading') throw new Error('原请求业务不一致')
      store.persist()
      session.assertCurrent()
      const result = await api.confirmBatchScan(pending.value.request)
      session.assertCurrent()
      if (result.task.id !== pending.value.request.taskId || !result.success)
        throw new Error('核验返回不一致或未成功，请核对原请求')
      apply(result)
      store.clear()
      feedback.value = result.replayed
        ? '原请求已核验，未重复计数'
        : `已核验 ${result.confirmedCount} 包`
      const full = await api.getTask(result.task.id)
      session.assertCurrent()
      apply(full)
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
        } else if (feedback.value) needsCheck.value = true
        error.value =
          cause instanceof Error ? cause.message : '装车核验结果待核，请重试原请求或读取任务状态'
      }
    } finally {
      writing.value = false
    }
  }
  const act = async (action: 'outbound' | 'resolve') => {
    if (!session.allowed('rd:loading-task:dispatch') || locked.value || !detail.value) return
    const id = detail.value.task.id
    const expectedVersion = detail.value.task.version
    const reason = handlingResult.value.trim()
    const resolution = resolutionAction.value
    const lineId = replacementLineId.value
    const code = extractPackageCode(replacementCode.value)
    if (
      action === 'resolve' &&
      (!reason || !['RETURN_TO_DRIVER', 'REPLACE_PACKAGE', 'CANCEL'].includes(resolution))
    ) {
      error.value = '请选择处理方式并填写说明'
      return
    }
    if (
      action === 'resolve' &&
      resolution === 'REPLACE_PACKAGE' &&
      (!lineId || !code || code.length > 128 || /[\s\\/?#:\u0000-\u001f\u007f]/u.test(code))
    ) {
      error.value = '请选择原包并填写有效替换包码'
      return
    }
    writing.value = true
    error.value = ''
    feedback.value = ''
    let sent = false
    try {
      const current = await api.getTask(id)
      session.assertCurrent()
      if (typeof current.task.version !== 'number' || !Number.isSafeInteger(current.task.version))
        throw new Error('任务版本缺失，请重新读取')
      if (current.task.version !== expectedVersion) {
        apply(current)
        throw new Error('任务版本已变化，请重新核定本次办理')
      }
      detail.value = current
      if (action === 'outbound' && current.task.status !== 'PENDING_REVIEW')
        throw new Error('任务尚未全部核验或已处理，请核对当前状态')
      if (
        action === 'resolve' &&
        (current.task.status !== 'EXCEPTION_PENDING' ||
          !current.exceptions.some((item) => item.exceptionStatus === 'OPEN'))
      )
        throw new Error('暂无待处理的装车异常，请核对当前状态')
      if (
        action === 'resolve' &&
        resolution === 'REPLACE_PACKAGE' &&
        !current.lines.some(
          (line) => line.id === lineId && ['PLANNED', 'EXCEPTION'].includes(line.lineStatus)
        )
      )
        throw new Error('原包已核验或已变更，不能替换')
      await ElMessageBox.confirm(
        action === 'outbound'
          ? `确认任务 ${current.task.taskNo} 的 ${current.task.scannedPackageCount} 包 / ${current.task.scannedWeight} kg 正式出库？`
          : `${resolution === 'CANCEL' ? '取消任务并释放货物占用' : resolution === 'REPLACE_PACKAGE' ? '替换所选包并退回司机继续执行' : '退回司机继续执行'}，确认处理任务 ${current.task.taskNo} 的全部待处理异常？`,
        action === 'outbound' ? '最终出库' : '处理装车异常'
      )
      session.assertCurrent()
      if (!session.allowed('rd:loading-task:dispatch')) throw new Error('当前无装车办理权限')
      sent = true
      if (action === 'outbound') {
        const result = await api.confirmOutbound({ taskId: id, version: current.task.version })
        if (
          result?.taskId !== id ||
          result.outboundPackageCount !== current.task.scannedPackageCount ||
          result.outboundWeight !== current.task.scannedWeight ||
          !result.outboundOrderIds?.length
        )
          throw new Error('出库返回未确认，请核对原任务')
      } else {
        const acknowledged = await api.resolveException({
          taskId: id,
          version: current.task.version,
          resolutionAction: resolution,
          handlingResult: reason,
          replacements:
            resolution === 'REPLACE_PACKAGE'
              ? [{ taskLineId: lineId!, newPackageCode: code }]
              : undefined
        })
        if (acknowledged !== true) throw new Error('异常处理未确认，请核对原任务')
      }
      session.assertCurrent()
      const verified = await api.getTask(id)
      session.assertCurrent()
      const openIds = current.exceptions
        .filter((item) => item.exceptionStatus === 'OPEN')
        .map((item) => item.id)
      if (
        verified.task.id !== id ||
        (action === 'outbound'
          ? verified.task.status !== 'OUTBOUNDED'
          : verified.exceptions.some(
              (item) => openIds.includes(item.id) && item.exceptionStatus === 'OPEN'
            ) ||
            (resolution === 'CANCEL'
              ? verified.task.status !== 'CANCELLED'
              : verified.task.status === 'EXCEPTION_PENDING') ||
            (resolution === 'REPLACE_PACKAGE' &&
              !verified.lines.some((line) => line.id === lineId && line.packageCode === code)))
      )
        throw new Error('任务返回的实际办理结果未确认，请重新核对')
      apply(verified)
      feedback.value = action === 'outbound' ? '出库已确认' : '装车异常已处理'
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
  if (pending.value?.kind === 'loading') {
    targetId.value = pending.value.request.taskId
    input.value = pending.value.request.representativePackageCode
  }
  return {
    canQuery,
    canDispatch,
    pending,
    storageError,
    input,
    detail,
    selected,
    selectedWeight,
    replacementLineId,
    replacementCode,
    replacementOptions,
    resolutionAction,
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
