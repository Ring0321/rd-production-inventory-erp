import { computed, onBeforeUnmount, onMounted, reactive, ref, shallowRef } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { hasPermission } from '@/directives/permission/hasPermi'
import { useUserStore } from '@/store/modules/user'
import { getTenantId, getVisitTenantId } from '@/utils/auth'
import { generateUUID } from '@/utils'
import { extractPackageCode } from '@/utils/rdDigitalLink'
import {
  RdQualityInspectionApi as api,
  type RdQualityInspectionVO,
  type RdQualityInspectionItemVO,
  type RdQualityScopeVO,
  type RdQualityScopePackageVO
} from '@/api/rd/quality/inspection'
import {
  confirmedCreateRejection,
  makePending,
  parsePending,
  pendingKey,
  type QualityPending
} from './draft'

export const useQualityWork = () => {
  const route = useRoute()
  const user = useUserStore()
  const ownerId = user.getUser.id
  const tenant = String(getVisitTenantId() ?? getTenantId() ?? '')
  let alive = true
  const ownsContext = () =>
    alive &&
    user.getUser.id === ownerId &&
    String(getVisitTenantId() ?? getTenantId() ?? '') === tenant
  const requireContext = () => {
    if (!ownsContext())
      throw new Error('登录身份或页面已变化，原请求保留，请用原账号和租户重新核定')
  }
  const canQuery = computed(() => hasPermission(['rd:quality-inspection:query']))
  const canCreate = computed(() => hasPermission(['rd:quality-inspection:create']))
  const canSubmit = computed(() => hasPermission(['rd:quality-inspection:submit']))
  const canApprove = computed(() => hasPermission(['rd:quality-inspection:approve']))
  const active = ref('register')
  const input = ref('')
  const scope = shallowRef<RdQualityScopeVO>()
  const scopeLoading = ref(false)
  const scopeError = ref('')
  const scopePage = ref(1)
  const visibleScopeRows = computed(() =>
    !scope.value
      ? []
      : scope.value.packages.list.some((bag) => bag.id === scope.value?.representative.id)
        ? scope.value.packages.list
        : [scope.value.representative, ...scope.value.packages.list]
  )
  const known = ref(new Map<number, RdQualityScopePackageVO>())
  const selected = ref<number[]>([])
  const samples = ref<number[]>([])
  const items = ref<RdQualityInspectionItemVO[]>([])
  const remark = ref('')
  const pending = ref<QualityPending>()
  const writing = ref(false)
  const selectingBatch = ref(false)
  const actionError = ref('')
  const storageError = ref('')
  const selectedRows = computed(() =>
    samples.value
      .map((id) => known.value.get(id))
      .filter((row): row is RdQualityScopePackageVO => !!row)
  )
  const locked = computed(
    () =>
      !ownsContext() ||
      writing.value ||
      selectingBatch.value ||
      !!pending.value ||
      !!storageError.value
  )
  let revision = 0
  let detailRevision = 0
  let listRevision = 0

  const detail = shallowRef<RdQualityInspectionVO>()
  const detailLoading = ref(false)
  const detailError = ref('')
  const rows = ref<RdQualityInspectionVO[]>([])
  const listLoading = ref(false)
  const listError = ref('')
  const total = ref<number>()
  const query = reactive({ pageNo: 1, pageSize: 10, status: 'SUBMITTED', inspectionNo: '' })
  const newItem = (): RdQualityInspectionItemVO => ({
    samplePackageId: samples.value[0],
    itemCode: '',
    itemName: ''
  })
  const key = () => pendingKey(ownerId, tenant)
  const persist = () => {
    if (!pending.value) return
    try {
      localStorage.setItem(key(), JSON.stringify(pending.value))
      storageError.value = ''
    } catch {
      storageError.value = '本机无法保存待核请求，本次不继续发送'
      throw new Error(storageError.value)
    }
  }

  const loadScope = async (pageNo = 1, reset = true) => {
    if (!ownsContext() || !canQuery.value || locked.value) return
    const code = extractPackageCode(
      reset ? input.value : scope.value?.representative.packageCode || ''
    )
    if (!code || /[\s\\/?#:\u0000-\u001f]/u.test(code) || code.length > 128) {
      scopeError.value = '包号或二维码链接无效'
      return
    }
    const request = ++revision
    scopeLoading.value = true
    scopeError.value = ''
    if (reset) {
      scope.value = undefined
      known.value.clear()
      selected.value = []
      samples.value = []
      items.value = []
      remark.value = ''
    }
    try {
      const value = await api.scope({ packageCode: code, pageNo, pageSize: 20 })
      if (!ownsContext() || request !== revision || pending.value) return
      scope.value = value
      scopePage.value = pageNo
      for (const bag of value.packages.list) known.value.set(bag.id, bag)
      known.value.set(value.representative.id, value.representative)
      if (reset && value.representative.eligible && canCreate.value) {
        selected.value = [value.representative.id]
        samples.value = [value.representative.id]
        items.value = [newItem()]
      }
      if (reset && canCreate.value && !items.value.length) items.value = [newItem()]
    } catch {
      if (alive && request === revision) scopeError.value = '货物检验范围读取失败，请重试'
    } finally {
      if (alive && request === revision) scopeLoading.value = false
    }
  }
  const selectEligibleBatch = async () => {
    if (!ownsContext() || !scope.value || locked.value || scopeLoading.value || !canCreate.value)
      return
    const code = scope.value.representative.packageCode
    const outputId = scope.value.productionOutputId
    const request = ++revision
    selectingBatch.value = true
    actionError.value = ''
    try {
      const candidates = new Map<number, RdQualityScopePackageVO>()
      let total: number | undefined
      for (let pageNo = 1; ; pageNo++) {
        const value = await api.scope({ packageCode: code, pageNo, pageSize: 100 })
        if (!ownsContext() || request !== revision) return
        if (
          value.representative.packageCode !== code ||
          value.productionOutputId !== outputId ||
          !Number.isSafeInteger(value.packages.total) ||
          value.packages.total < 1 ||
          (total !== undefined && value.packages.total !== total)
        )
          throw new Error('批次范围已变化，请重新读取后选择')
        total = value.packages.total
        if (!value.packages.list.length) throw new Error('批次范围读取不完整，请重试')
        for (const bag of value.packages.list) {
          if (candidates.has(bag.id)) throw new Error('批次范围读取不一致，请重试')
          candidates.set(bag.id, bag)
        }
        if ([...candidates.values()].filter((bag) => bag.eligible).length > 200)
          throw new Error('可检货物超过 200 包，请分次选择检验范围')
        if (pageNo * 100 >= total) break
      }
      if (candidates.size !== total) throw new Error('批次范围读取不完整，请重试')
      const ids = [...candidates.values()].filter((bag) => bag.eligible).map((bag) => bag.id)
      if (!ids.length) throw new Error('当前批次没有可登记检验的货物')
      for (const bag of candidates.values()) known.value.set(bag.id, bag)
      selected.value = ids
      samples.value = samples.value.filter((id) => ids.includes(id))
    } catch (error) {
      if (alive && request === revision)
        actionError.value = error instanceof Error ? error.message : '批次范围读取失败，请重试'
    } finally {
      if (alive && request === revision) selectingBatch.value = false
    }
  }
  const toggleCoverage = (id: number, checked: boolean) => {
    if (locked.value || !canCreate.value || !known.value.get(id)?.eligible) return
    if (checked) selected.value = [...new Set([...selected.value, id])]
    else {
      selected.value = selected.value.filter((value) => value !== id)
      samples.value = samples.value.filter((value) => value !== id)
    }
  }
  const toggleSample = (id: number, checked: boolean) => {
    if (locked.value || !canCreate.value || !selected.value.includes(id)) return
    samples.value = checked
      ? [...new Set([...samples.value, id])]
      : samples.value.filter((value) => value !== id)
    const first = items.value[0]
    if (
      checked &&
      first &&
      first.samplePackageId == null &&
      !first.itemCode &&
      !first.itemName &&
      first.measuredValue == null
    )
      first.samplePackageId = id
  }
  const addItem = () => {
    if (!locked.value) items.value.push(newItem())
  }
  const removeItem = (index: number) => {
    if (!locked.value && items.value.length > 1) items.value.splice(index, 1)
  }

  const loadList = async (pageNo = query.pageNo) => {
    if (!ownsContext() || !canQuery.value) return
    const request = ++listRevision
    query.pageNo = pageNo
    listLoading.value = true
    listError.value = ''
    rows.value = []
    total.value = undefined
    try {
      const value = await api.getPage({
        ...query,
        status: query.status || undefined,
        inspectionNo: query.inspectionNo.trim() || undefined
      })
      if (ownsContext() && request === listRevision) {
        rows.value = value.list
        total.value = value.total
      }
    } catch {
      if (alive && request === listRevision) listError.value = '检验单列表读取失败'
    } finally {
      if (alive && request === listRevision) listLoading.value = false
    }
  }
  const openInspection = async (id: number) => {
    if (!ownsContext() || !canQuery.value || writing.value) return
    const request = ++detailRevision
    active.value = 'records'
    detail.value = undefined
    detailLoading.value = true
    detailError.value = ''
    try {
      const value = await api.get(id)
      if (ownsContext() && request === detailRevision) detail.value = value
    } catch {
      if (alive && request === detailRevision) detailError.value = '检验详情读取失败'
    } finally {
      if (alive && request === detailRevision) detailLoading.value = false
    }
  }
  const save = async () => {
    if (
      !ownsContext() ||
      writing.value ||
      selectingBatch.value ||
      scopeLoading.value ||
      !canCreate.value ||
      !canSubmit.value
    )
      return
    actionError.value = ''
    writing.value = true
    try {
      if (!pending.value) {
        if (storageError.value || !scope.value) throw new Error('待核请求或货物范围未就绪')
        if (selected.value.some((id) => !known.value.get(id)?.eligible))
          throw new Error('检验范围包含当前不能登记的包件')
        pending.value = makePending(
          {
            packageId: selected.value[0],
            packageIds: [...selected.value],
            samplePackageIds: [...samples.value],
            items: items.value.map((item) => ({
              samplePackageId: item.samplePackageId,
              itemCode: item.itemCode.trim(),
              itemName: item.itemName.trim(),
              standardMin: item.standardMin ?? undefined,
              standardMax: item.standardMax ?? undefined,
              measuredValue: item.measuredValue ?? undefined,
              unit: item.unit,
              remark: item.remark
            })),
            remark: remark.value,
            clientRequestId: generateUUID()
          },
          ownerId,
          tenant
        )
      }
      persist()
      const id = await api.createBatch(pending.value.request)
      if (!Number.isSafeInteger(id) || id <= 0) throw new Error('服务端未返回有效检验单编号')
      pending.value.inspectionId = id
      persist()
      requireContext()
      await api.submit(id)
      requireContext()
      const verified = await api.get(id)
      requireContext()
      if (
        verified.clientRequestId !== pending.value.request.clientRequestId ||
        !['SUBMITTED', 'RESUBMITTED', 'PASSED', 'REJECTED'].includes(verified.status)
      )
        throw new Error('提交结果尚未核定')
      localStorage.removeItem(key())
      pending.value = undefined
      storageError.value = ''
      detail.value = verified
      active.value = 'records'
      scope.value = undefined
      await loadList(1)
    } catch (error) {
      if (confirmedCreateRejection(error, pending.value?.inspectionId)) {
        try {
          localStorage.removeItem(key())
          pending.value = undefined
          storageError.value = ''
        } catch {
          storageError.value = '本机待核请求清理失败，保留原请求'
        }
        actionError.value = `服务器已拒绝本次登记：${(error as Error).message}`
      } else
        actionError.value = error instanceof Error ? error.message : '提交结果待核，请重试原请求'
    } finally {
      writing.value = false
    }
  }
  const canReview = computed(
    () =>
      ownsContext() &&
      !!detail.value &&
      canApprove.value &&
      ['SUBMITTED', 'RESUBMITTED'].includes(detail.value.status) &&
      detail.value.submitterId !== user.getUser.id
  )
  const canSubmitDetail = computed(
    () =>
      ownsContext() &&
      !!detail.value &&
      canSubmit.value &&
      ['DRAFT', 'CORRECTED'].includes(detail.value.status)
  )
  const qualified = computed(
    () =>
      !!detail.value?.items?.length && detail.value.items.every((item) => item.qualified === true)
  )
  const act = async (action: 'submit' | 'approve' | 'reject') => {
    const current = detail.value
    if (
      !current ||
      !ownsContext() ||
      writing.value ||
      pending.value ||
      (action === 'submit' ? !canSubmitDetail.value : !canReview.value)
    )
      return
    actionError.value = ''
    writing.value = true
    try {
      if (action === 'reject') {
        const result = await ElMessageBox.prompt('不通过原因', '质检复核', {
          inputValidator: (value) => !!value?.trim() || '原因不能为空'
        })
        requireContext()
        if (!canReview.value) throw new Error('当前已不能复核，请重新读取检验单')
        await api.reject({ id: current.id, reason: result.value.trim() })
      } else {
        if (action === 'approve' && !qualified.value) throw new Error('有检测项未合格，不能通过')
        await ElMessageBox.confirm(
          `${action === 'approve' ? '确认通过' : '确认提交'} ${current.inspectionNo}？`,
          '质检确认'
        )
        requireContext()
        if (action === 'submit' ? !canSubmitDetail.value : !canReview.value)
          throw new Error('当前操作权限或检验状态已变化，请重新读取')
        await api[action](current.id)
      }
      requireContext()
      const verified = await api.get(current.id)
      requireContext()
      detail.value = verified
      await loadList()
    } catch (error) {
      if (error !== 'cancel' && error !== 'close')
        actionError.value =
          error instanceof Error ? error.message : '操作未确认，请读取最新记录后重试'
    } finally {
      writing.value = false
    }
  }
  onMounted(() => {
    if (!ownsContext() || !canQuery.value) return
    try {
      const raw = localStorage.getItem(key())
      if (raw) pending.value = parsePending(raw, ownerId, tenant)
    } catch {
      storageError.value = '待核请求读取失败，暂不能新建检验单'
    }
    const id = Number(route.query.inspectionId)
    if (Number.isSafeInteger(id) && id > 0) void openInspection(id)
    else if (typeof route.query.packageCode === 'string' && !pending.value) {
      input.value = route.query.packageCode
      void loadScope()
    }
    void loadList()
  })
  onBeforeUnmount(() => {
    alive = false
    revision++
    detailRevision++
    listRevision++
  })
  return {
    canQuery,
    canCreate,
    canSubmit,
    active,
    input,
    scope,
    scopeLoading,
    scopeError,
    scopePage,
    visibleScopeRows,
    selected,
    samples,
    items,
    remark,
    pending,
    writing,
    selectingBatch,
    actionError,
    storageError,
    selectedRows,
    locked,
    detail,
    detailLoading,
    detailError,
    rows,
    listLoading,
    listError,
    total,
    query,
    loadScope,
    selectEligibleBatch,
    toggleCoverage,
    toggleSample,
    addItem,
    removeItem,
    loadList,
    openInspection,
    save,
    act,
    canReview,
    canSubmitDetail,
    qualified
  }
}
