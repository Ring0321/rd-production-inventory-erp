import { computed, ref, watch, type Ref } from 'vue'
import { useRouter } from 'vue-router'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useMessage } from '@/hooks/web/useMessage'
import { RdProductionTaskApi, type RdProductionTaskVO } from '@/api/rd/production/task'
import { RdProductionOutputApi, type RdProductionOutputVO } from '@/api/rd/production/output'
import { RdPackageUnitApi, type RdPackageUnitVO } from '@/api/rd/stock/package'

export const outputStatusText = (status: string) =>
  ({ DRAFT: '草稿', CONFIRMED: '已确认', INBOUNDED: '已入库', CANCELLED: '已作废' })[status] ||
  status

export const useTaskOutputs = (task: Ref<RdProductionTaskVO | undefined>) => {
  const message = useMessage()
  const router = useRouter()
  const outputs = ref<RdProductionOutputVO[]>([])
  const total = ref(0)
  const page = ref(1)
  const pageSize = ref(10)
  const loading = ref(false)
  const error = ref('')
  const actionError = ref('')
  const actingId = ref<number>()
  const packages = ref<RdPackageUnitVO[]>([])
  const packageLoading = ref(false)
  const packageError = ref('')
  const packagesLoaded = ref(false)
  let outputRequest = 0
  let packageRequest = 0

  const canRead = computed(() => hasPermission(['rd:production-output:query']))
  const canConfirm = computed(() => hasPermission(['rd:production-output:confirm']))
  const canGenerate = computed(() => hasPermission(['rd:package-unit:generate']))
  const canReadLabels = computed(() => hasPermission(['rd:package-unit:query']))
  const canPrint = computed(() => canReadLabels.value && hasPermission(['rd:label-print:print']))
  const canReprint = computed(
    () => canReadLabels.value && hasPermission(['rd:label-print:reprint'])
  )

  const loadOutputs = async () => {
    const id = task.value?.id
    const requestId = ++outputRequest
    outputs.value = []
    error.value = ''
    loading.value = false
    if (!id || !canRead.value) return
    loading.value = true
    try {
      const data: PageResult<RdProductionOutputVO[]> = await RdProductionOutputApi.getPage({
        pageNo: page.value,
        pageSize: pageSize.value,
        productionTaskId: id
      })
      if (requestId !== outputRequest || task.value?.id !== id) return
      outputs.value = data.list
      total.value = data.total
    } catch {
      if (requestId === outputRequest) error.value = '生产记录加载失败，请重试。'
    } finally {
      if (requestId === outputRequest) loading.value = false
    }
  }

  const loadPackages = async () => {
    const id = task.value?.id
    const requestId = ++packageRequest
    packageError.value = ''
    packagesLoaded.value = false
    packageLoading.value = false
    if (!id || !canReadLabels.value) return
    packageLoading.value = true
    try {
      const data = await RdPackageUnitApi.getTaskPackages(id)
      if (requestId !== packageRequest || task.value?.id !== id) return
      packages.value = data
      packagesLoaded.value = true
    } catch {
      if (requestId === packageRequest) packageError.value = '任务包码加载失败，请重试。'
    } finally {
      if (requestId === packageRequest) packageLoading.value = false
    }
  }

  const packageCount = (outputId: number): number | undefined =>
    packagesLoaded.value
      ? packages.value.filter((item) => item.productionOutputId === outputId).length
      : undefined

  const refresh = async () => {
    await Promise.all([loadOutputs(), loadPackages()])
  }

  const runAction = async (row: RdProductionOutputVO, action: 'confirm' | 'generate') => {
    const id = task.value?.id
    if (
      !id ||
      actingId.value !== undefined ||
      task.value?.status !== 'ACTIVE' ||
      (action === 'generate' && (packageCount(row.id) ?? 0) > 0) ||
      (action === 'confirm' ? !canConfirm.value : !canGenerate.value)
    ) {
      return
    }
    actingId.value = row.id
    actionError.value = ''
    try {
      const freshTask = await RdProductionTaskApi.get(id)
      const fresh: RdProductionOutputVO = await RdProductionOutputApi.get(row.id)
      if (task.value?.id !== id) return
      task.value = freshTask
      if (
        freshTask.status !== 'ACTIVE' ||
        fresh.productionTaskId !== id ||
        fresh.version !== row.version ||
        fresh.status !== (action === 'confirm' ? 'DRAFT' : 'CONFIRMED')
      ) {
        actionError.value = '任务或产出已变更，请刷新后核对。'
        await refresh()
        return
      }
      await message.confirm(
        action === 'confirm'
          ? `确认炉号 ${fresh.furnaceNo}、炉层 ${fresh.furnaceLayer} 的 ${fresh.outputQty} 包产出，实称单包 ${fresh.packageWeight} kg？`
          : `为炉号 ${fresh.furnaceNo}、炉层 ${fresh.furnaceLayer} 生成 ${fresh.outputQty} 个包码？单包 ${fresh.packageWeight} kg。`
      )
      if (task.value?.id !== id) return
      if (action === 'confirm') {
        await RdProductionOutputApi.confirm({ id: fresh.id, version: fresh.version })
        message.success('产出已确认')
      } else {
        const created = await RdPackageUnitApi.generate({
          productionOutputId: fresh.id,
          packageCount: fresh.outputQty
        })
        message.success(`已生成 ${created.length} 个包码`)
      }
      await refresh()
    } catch (cause) {
      if (cause !== 'cancel' && cause !== 'close') {
        actionError.value = '操作未完成，请刷新记录核对结果后再重试。'
        await refresh()
      }
    } finally {
      actingId.value = undefined
    }
  }

  const openLabels = (action: 'first' | 'reprint' | 'history') => {
    const id = task.value?.id
    if (
      !id ||
      !canReadLabels.value ||
      (action === 'first' && !canPrint.value) ||
      (action === 'reprint' && !canReprint.value)
    ) {
      return
    }
    return router.push({
      name: 'RdPackageUnit',
      query: {
        productionTaskId: String(id),
        ...(action === 'first' ? { labelAction: 'taskPrint' } : {})
      }
    })
  }

  watch(
    () => task.value?.id,
    () => {
      page.value = 1
      total.value = 0
      packages.value = []
      actionError.value = ''
      void refresh()
    },
    { immediate: true }
  )

  return {
    outputs,
    total,
    page,
    pageSize,
    loading,
    error,
    actionError,
    actingId,
    packageLoading,
    packageError,
    packagesLoaded,
    packages,
    packageCount,
    canRead,
    canConfirm,
    canGenerate,
    canReadLabels,
    canPrint,
    canReprint,
    loadOutputs,
    loadPackages,
    refresh,
    runAction,
    openLabels
  }
}
