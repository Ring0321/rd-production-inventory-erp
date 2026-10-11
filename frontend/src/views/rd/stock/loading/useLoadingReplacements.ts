import { ref, shallowRef, watch, type Ref } from 'vue'
import { RdLoadingApi, type LoadingTaskDetail } from '@/api/rd/loading'
import type { WarehouseSession } from '../../pda/warehouse/useWarehouseSession'
import {
  buildLoadingReplacements,
  inspectLoadingReplacements,
  type LoadingReplacementRow,
  type LoadingReplacementPreview
} from './replacements'

export const useLoadingReplacements = (
  session: WarehouseSession,
  detail: Ref<LoadingTaskDetail | undefined>
) => {
  const rows = ref<LoadingReplacementRow[]>([])
  const preview = shallowRef<LoadingReplacementPreview[]>([])
  const checking = ref(false)
  const error = ref('')
  let generation = 0
  let sequence = 0
  const reset = (lineId?: number) => {
    ++generation
    rows.value = lineId ? [{ key: ++sequence, taskLineId: lineId, newPackageCode: '' }] : []
    preview.value = []
    error.value = ''
  }
  watch(
    () => [
      rows.value.map((row) => [row.key, row.taskLineId, row.newPackageCode]),
      detail.value?.task.id,
      detail.value?.task.version
    ],
    () => {
      ++generation
      preview.value = []
      error.value = ''
    },
    { deep: true, flush: 'sync' }
  )
  const check = async () => {
    if (checking.value || !session.allowed('rd:loading-task:dispatch')) return
    const request = ++generation
    const lifetime = session.generation()
    checking.value = true
    error.value = ''
    preview.value = []
    try {
      if (!detail.value) throw new Error('请先读取原装车任务')
      const lines = detail.value.lines.map((line) => ({ ...line }))
      const entries = buildLoadingReplacements(rows.value, lines)
      const assertCurrent = () => {
        session.assertCurrent()
        if (
          request !== generation ||
          session.generation() !== lifetime ||
          !session.allowed('rd:loading-task:dispatch')
        )
          throw new Error('换货清单或任务已变化，请重新核对')
      }
      const result = await inspectLoadingReplacements(
        entries,
        lines,
        RdLoadingApi.getAvailablePackagePage,
        assertCurrent
      )
      assertCurrent()
      preview.value = result
    } catch (cause) {
      if (session.current() && request === generation)
        error.value = cause instanceof Error ? cause.message : '替换货查询失败，请重新核对'
    } finally {
      checking.value = false
    }
  }
  return { rows, preview, checking, error, reset, check }
}
