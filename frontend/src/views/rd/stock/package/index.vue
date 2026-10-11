<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams">
      <el-form-item label="包码" prop="packageCode">
        <el-input
          v-model="queryParams.packageCode"
          class="!w-220px"
          clearable
          placeholder="包号或二维码链接"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="生产记录ID" prop="productionOutputId">
        <el-input-number
          v-model="queryParams.productionOutputId"
          :min="1"
          :controls="false"
          class="!w-180px"
        />
      </el-form-item>
      <el-form-item v-if="hasPermission(['rd:production-task:query'])" label="生产任务">
        <el-select
          v-model="queryParams.productionTaskId"
          filterable
          remote
          clearable
          :remote-method="loadTasks"
          :loading="taskLoading"
          placeholder="搜索任务号"
          class="!w-260px"
          @change="handleQuery"
        >
          <el-option
            v-for="task in taskOptions"
            :key="task.id"
            :value="task.id"
            :label="`${task.taskNo} · ${task.customerName} · ${task.shiftCode}`"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          v-hasPermi="['rd:package-unit:generate']"
          @click="openGenerate"
        >
          <Icon icon="ep:plus" class="mr-5px" />生成包码
        </el-button>
        <el-button
          type="success"
          plain
          :loading="exportLoading"
          v-hasPermi="['rd:package-unit:query']"
          @click="handleExport"
        >
          <Icon icon="ep:download" class="mr-5px" />导出 Excel
        </el-button>
        <el-button
          v-hasPermi="['rd:label-print:print']"
          :disabled="!selectedPackages.length"
          @click="preparePrint(selectedPackages, false)"
          ><Icon icon="lucide:printer" class="mr-5px" />打印所选</el-button
        >
        <el-button
          v-hasPermi="['rd:label-print:reprint']"
          :disabled="!selectedPackages.length"
          @click="preparePrint(selectedPackages, true)"
          ><Icon icon="lucide:printer" class="mr-5px" />补打所选</el-button
        >
        <el-button
          v-hasPermi="['rd:label-print:print']"
          :disabled="!queryParams.productionOutputId"
          :loading="printPreparing"
          @click="prepareProductionPrint"
          ><Icon icon="lucide:layers" class="mr-5px" />整批打印</el-button
        >
        <el-button
          v-hasPermi="['rd:label-print:print']"
          :disabled="!queryParams.productionTaskId"
          :loading="printPreparing"
          @click="prepareTaskPrint"
        >
          <Icon icon="lucide:clipboard-list" class="mr-5px" />任务标签打印
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table
      v-loading="loading"
      :data="displayList"
      row-key="id"
      stripe
      show-overflow-tooltip
      @selection-change="selectedPackages = $event"
    >
      <el-table-column type="selection" width="46" />
      <el-table-column label="包码" prop="packageCode" width="180" fixed="left" />
      <el-table-column label="生产记录ID" prop="productionOutputId" width="120" align="center" />
      <el-table-column label="生产任务" prop="productionTaskNo" width="210" />
      <el-table-column label="生产日期" prop="productionDate" width="110" />
      <el-table-column label="客户" prop="customerName" min-width="130" />
      <el-table-column label="产品" prop="productName" min-width="120">
        <template #default="scope">{{ scope.row.productName || '-' }}</template>
      </el-table-column>
      <el-table-column label="型号" prop="productModel" min-width="130" />
      <el-table-column label="炉号/炉层" min-width="150">
        <template #default="scope"
          >{{ scope.row.furnaceNo }} / {{ scope.row.furnaceLayer }}</template
        >
      </el-table-column>
      <el-table-column label="单包重量" prop="packageWeight" width="110" align="right" />
      <el-table-column label="货位" prop="locationCode" width="120" />
      <el-table-column label="状态" prop="status" width="110" align="center">
        <template #default="scope">
          <el-tag :type="packageStatusMap[scope.row.status]?.type || 'info'">
            {{ packageStatusMap[scope.row.status]?.label || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="210" fixed="right" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-hasPermi="['rd:package-unit:query']"
            @click="openPreview(scope.row)"
            >标签预览</el-button
          >
          <el-button
            link
            type="success"
            :disabled="scope.row.status !== 'CREATED'"
            v-hasPermi="['rd:label-print:print']"
            @click="preparePrint([scope.row], false)"
            >打印</el-button
          >
          <el-button
            link
            type="warning"
            :disabled="!canReprint(scope.row)"
            v-hasPermi="['rd:label-print:reprint']"
            @click="preparePrint([scope.row], true)"
            >补打</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-if="!traceMode"
      v-model:limit="pageParams.pageSize"
      v-model:page="pageParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="generateVisible" title="从生产记录生成一包一码" width="620px">
    <el-form ref="generateFormRef" :model="generateForm" :rules="generateRules" label-width="110px">
      <el-form-item label="生产记录" prop="productionOutputId">
        <el-select
          v-model="generateForm.productionOutputId"
          filterable
          class="!w-100%"
          @change="handleOutputChange"
        >
          <el-option
            v-for="item in productionOptions"
            :key="item.id"
            :label="`${item.productionDate} | ${item.customerName} | ${item.productModel} | ${item.furnaceNo}-${item.furnaceLayer} | ${item.outputQty}包`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="包数" prop="packageCount">
        <el-input-number
          v-model="generateForm.packageCount"
          :min="1"
          :precision="0"
          class="!w-100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="generateLoading" @click="submitGenerate">生成</el-button>
      <el-button @click="generateVisible = false">取消</el-button>
    </template>
  </Dialog>

  <Dialog
    v-model="previewVisible"
    title="货品标识卡预览"
    width="620px"
    class="package-label-dialog"
  >
    <iframe class="label-preview" :srcdoc="previewHtml" title="单包标签预览" sandbox=""></iframe>
    <template #footer>
      <el-button
        type="primary"
        :disabled="previewPackage?.status !== 'CREATED'"
        v-hasPermi="['rd:label-print:print']"
        @click="previewPackage && preparePrint([previewPackage], false)"
        ><Icon icon="ep:printer" class="mr-5px" />打印</el-button
      >
      <el-button
        type="warning"
        :disabled="!previewPackage || !canReprint(previewPackage)"
        v-hasPermi="['rd:label-print:reprint']"
        @click="previewPackage && preparePrint([previewPackage], true)"
        ><Icon icon="ep:printer" class="mr-5px" />补打</el-button
      >
      <el-button @click="previewVisible = false">关闭</el-button>
    </template>
  </Dialog>

  <Dialog
    v-model="printVisible"
    :title="reprintMode ? '补打标签' : '打印吨袋标签'"
    width="720px"
    class="package-label-dialog"
  >
    <el-form label-width="94px">
      <el-form-item label="打印数量"
        ><strong>{{ printLabels.length }} 张</strong></el-form-item
      >
      <el-form-item label="纸张">
        <el-radio-group v-model="printPaper">
          <el-radio-button value="LABEL_100_76">100 × 76 mm</el-radio-button>
          <el-radio-button value="A4">A4</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="reprintMode" label="补打原因" required>
        <el-input
          v-model="reprintReason"
          type="textarea"
          :rows="2"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <el-table :data="printLabels.map((label) => label.item)" max-height="220" size="small">
      <el-table-column label="包号" prop="packageCode" min-width="175" />
      <el-table-column label="客户" prop="customerName" min-width="100" />
      <el-table-column label="型号" prop="productModel" min-width="80" />
      <el-table-column label="炉号 / 炉层" min-width="130">
        <template #default="scope"
          >{{ scope.row.furnaceNo }} / {{ scope.row.furnaceLayer }}</template
        >
      </el-table-column>
      <el-table-column label="kg" prop="packageWeight" width="75" align="right" />
    </el-table>
    <iframe
      class="label-preview"
      :srcdoc="printPreviewHtml"
      title="批量标签预览"
      sandbox=""
    ></iframe>
    <template #footer>
      <el-button :disabled="printRegistering" @click="printVisible = false">关闭</el-button>
      <el-button :disabled="printRegistering" @click="openPrintWindow">
        <Icon icon="lucide:printer" class="mr-5px" />打开打印窗口
      </el-button>
      <el-button
        type="primary"
        :disabled="!printOpened"
        :loading="printRegistering"
        @click="confirmPrinted"
      >
        <Icon icon="lucide:check" class="mr-5px" />确认打印成功
      </el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import QRCode from 'qrcode'
import { useRoute } from 'vue-router'
import download from '@/utils/download'
import { extractPackageCode, getPackageDigitalLink } from '@/utils/rdDigitalLink'
import {
  buildPackageLabelHtml,
  type PackageLabel,
  type PackageLabelPaper
} from '@/utils/rdPackageLabel'
import { RdProductionOutputApi, type RdProductionOutputVO } from '@/api/rd/production/output'
import { RdProductionTaskApi, type RdProductionTaskVO } from '@/api/rd/production/task'
import { hasPermission } from '@/directives/permission/hasPermi'
import {
  RdPackageUnitApi,
  type RdPackageGenerateReqVO,
  type RdPackageStatus,
  type RdPackageUnitVO
} from '@/api/rd/stock/package'

defineOptions({ name: 'RdStockPackage' })

type TagType = 'success' | 'warning' | 'info' | 'danger'
const packageStatusOptions: Array<{ value: RdPackageStatus; label: string; type: TagType }> = [
  { value: 'CREATED', label: '待贴标', type: 'info' },
  { value: 'LABELED', label: '已贴标', type: 'warning' },
  { value: 'QC_PENDING', label: '待质检', type: 'warning' },
  { value: 'QC_PASSED', label: '质检通过', type: 'success' },
  { value: 'QC_REJECTED', label: '质检驳回', type: 'danger' },
  { value: 'INBOUND_PENDING', label: '待入库', type: 'warning' },
  { value: 'IN_STOCK', label: '在库', type: 'success' },
  { value: 'STOCKTAKE_MISSING', label: '盘点异常', type: 'danger' },
  { value: 'OUTBOUND_PENDING', label: '待出库', type: 'warning' },
  { value: 'OUTBOUNDED', label: '已出库', type: 'info' },
  { value: 'VOIDED', label: '已作废', type: 'danger' }
]
const packageStatusMap = Object.fromEntries(
  packageStatusOptions.map((item) => [item.value, item])
) as Record<RdPackageStatus, (typeof packageStatusOptions)[number]>
const route = useRoute()
const message = useMessage()
const loading = ref(false)
const exportLoading = ref(false)
const list = ref<RdPackageUnitVO[]>([])
const selectedPackages = ref<RdPackageUnitVO[]>([])
const total = ref(0)
const traceMode = ref(false)
const queryFormRef = ref<FormInstance>()
const queryParams = reactive<{
  packageCode?: string
  productionOutputId?: number
  productionTaskId?: number
}>({})
const taskOptions = ref<RdProductionTaskVO[]>([])
const taskLoading = ref(false)
const loadTasks = async (taskNo = '') => {
  if (!hasPermission(['rd:production-task:query'])) return
  taskLoading.value = true
  try {
    taskOptions.value = (
      await RdProductionTaskApi.getPage({ pageNo: 1, pageSize: 100, taskNo })
    ).list
    const id = queryParams.productionTaskId
    if (id && !taskOptions.value.some((task) => task.id === id))
      taskOptions.value.unshift(await RdProductionTaskApi.get(id))
  } finally {
    taskLoading.value = false
  }
}
const pageParams = reactive({ pageNo: 1, pageSize: 10 })
const displayList = computed(() => {
  const code = queryParams.packageCode?.trim().toLowerCase()
  return code
    ? list.value.filter((item) => item.packageCode.toLowerCase().includes(code))
    : list.value
})

const generateVisible = ref(false)
const generateLoading = ref(false)
const generateFormRef = ref<FormInstance>()
const generateForm = reactive<RdPackageGenerateReqVO>({ productionOutputId: 0, packageCount: 1 })
const productionOptions = ref<RdProductionOutputVO[]>([])
const generateRules = reactive<FormRules>({
  productionOutputId: [{ required: true, message: '请选择生产记录', trigger: 'change' }],
  packageCount: [{ required: true, message: '包数不能为空', trigger: 'change' }]
})
const previewVisible = ref(false)
const previewPackage = ref<RdPackageUnitVO>()
const previewHtml = ref('')
const printVisible = ref(false)
const printPreparing = ref(false)
const printRegistering = ref(false)
const printOpened = ref(false)
const printLabels = ref<PackageLabel[]>([])
const printTaskId = ref<number>()
const printPaper = ref<PackageLabelPaper>('LABEL_100_76')
const reprintMode = ref(false)
const reprintReason = ref('')
const printPreviewHtml = computed(() =>
  buildPackageLabelHtml(printLabels.value.slice(0, 1), printPaper.value)
)
const canReprint = (item: RdPackageUnitVO) => !['CREATED', 'VOIDED'].includes(item.status)

const formatBusinessDate = (value: unknown): string => {
  if (Array.isArray(value) && value.length >= 3) {
    return `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')}`
  }
  return typeof value === 'string' ? value : ''
}
const normalizePackageDates = (item: RdPackageUnitVO): RdPackageUnitVO => ({
  ...item,
  productionDate: formatBusinessDate(item.productionDate),
  furnaceOutDate: formatBusinessDate(item.furnaceOutDate)
})

const getList = async () => {
  loading.value = true
  traceMode.value = false
  try {
    const data = await RdPackageUnitApi.getPage(pageParams)
    list.value = data.list.map(normalizePackageDates)
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = async () => {
  const packageCode = extractPackageCode(queryParams.packageCode || '')
  if (!packageCode && !queryParams.productionOutputId && !queryParams.productionTaskId) {
    pageParams.pageNo = 1
    await getList()
    return
  }
  loading.value = true
  try {
    const packages = queryParams.productionTaskId
      ? (await RdPackageUnitApi.getTaskPackages(queryParams.productionTaskId)).filter(
          (item) =>
            !queryParams.productionOutputId ||
            item.productionOutputId === queryParams.productionOutputId
        )
      : await RdPackageUnitApi.trace({
          packageCode: packageCode || undefined,
          productionOutputId: queryParams.productionOutputId
        })
    list.value = packages.map(normalizePackageDates)
    total.value = list.value.length
    traceMode.value = true
  } finally {
    loading.value = false
  }
}
const getRouteString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : String(value || '')
}
const getRouteNumber = (key: string) => {
  const value = Number(getRouteString(key))
  return Number.isFinite(value) && value > 0 ? value : undefined
}
const applyRouteQuery = async () => {
  queryParams.packageCode = getRouteString('packageCode') || undefined
  queryParams.productionOutputId = getRouteNumber('productionOutputId')
  queryParams.productionTaskId = getRouteNumber('productionTaskId')
  await loadTasks()
  await handleQuery()
  if (getRouteString('labelAction') === 'preview' && list.value.length === 1) {
    await openPreview(list.value[0])
  }
  if (
    getRouteString('labelAction') === 'taskPrint' &&
    queryParams.productionTaskId &&
    hasPermission(['rd:label-print:print'])
  )
    await prepareTaskPrint()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.packageCode = undefined
  queryParams.productionOutputId = undefined
  queryParams.productionTaskId = undefined
  pageParams.pageNo = 1
  getList()
}
const handleExport = async () => {
  exportLoading.value = true
  try {
    const data = await RdPackageUnitApi.exportExcel(queryParams)
    download.excel(data, '吨袋包码台账.xlsx')
  } finally {
    exportLoading.value = false
  }
}
const openGenerate = async () => {
  const data = await RdProductionOutputApi.getPage({ pageNo: 1, pageSize: -1 })
  productionOptions.value = data.list.filter(
    (item: RdProductionOutputVO) => item.status === 'CONFIRMED'
  )
  generateForm.productionOutputId = 0
  generateForm.packageCount = 1
  generateVisible.value = true
  nextTick(() => generateFormRef.value?.clearValidate())
}
const handleOutputChange = (id: number) => {
  const output = productionOptions.value.find((item) => item.id === id)
  if (output) generateForm.packageCount = output.outputQty
}
const submitGenerate = async () => {
  await generateFormRef.value?.validate()
  generateLoading.value = true
  try {
    const packages = await RdPackageUnitApi.generate(generateForm)
    message.success(`已生成 ${packages.length} 个包码`)
    generateVisible.value = false
    await getList()
  } finally {
    generateLoading.value = false
  }
}
const openPreview = async (row: RdPackageUnitVO) => {
  previewPackage.value = row
  previewHtml.value = buildPackageLabelHtml([await buildLabel(row)], 'LABEL_100_76')
  previewVisible.value = true
}
const buildLabel = async (item: RdPackageUnitVO): Promise<PackageLabel> => ({
  item: normalizePackageDates(item),
  qrDataUrl: await QRCode.toDataURL(getPackageDigitalLink(item.packageCode, item.qrCode), {
    width: 420,
    margin: 4,
    errorCorrectionLevel: 'M'
  })
})
const preparePrint = async (packages: RdPackageUnitVO[], reprint: boolean) => {
  if (!packages.length || packages.length > 500) {
    message.warning('每次请选择 1-500 包打印')
    return
  }
  if (packages.some((item) => (reprint ? !canReprint(item) : item.status !== 'CREATED'))) {
    message.warning(
      reprint ? '只可补打已贴标且未作废的包件' : '首次打印请选择待贴标包件，其他包件请使用补打'
    )
    return
  }
  printPreparing.value = true
  try {
    const taskId = queryParams.productionTaskId
    const outputIds = [...new Set(packages.map((item) => item.productionOutputId))]
    const freshBatches = taskId
      ? [await RdPackageUnitApi.getTaskPackages(taskId)]
      : await Promise.all(outputIds.map((id) => RdPackageUnitApi.trace(id)))
    const selectedIds = new Set(packages.map((item) => item.id))
    const freshPackages = freshBatches.flat().filter((item) => selectedIds.has(item.id))
    if (taskId !== queryParams.productionTaskId) {
      message.warning('任务选择已变化，请重新准备打印')
      return
    }
    if (freshPackages.length !== packages.length) {
      message.warning('部分包件已不存在，请刷新列表')
      await handleQuery()
      return
    }
    if (freshPackages.some((item) => (reprint ? !canReprint(item) : item.status !== 'CREATED'))) {
      message.warning('包件状态已经变化，请刷新列表')
      await handleQuery()
      return
    }
    printLabels.value = await Promise.all(freshPackages.map(buildLabel))
    printTaskId.value = taskId
    reprintMode.value = reprint
    reprintReason.value = ''
    printOpened.value = false
    previewVisible.value = false
    printVisible.value = true
  } finally {
    printPreparing.value = false
  }
}
const prepareProductionPrint = async () => {
  if (!queryParams.productionOutputId) return
  printPreparing.value = true
  try {
    const packages = await RdPackageUnitApi.trace(queryParams.productionOutputId)
    const pending = packages.filter((item) => item.status === 'CREATED')
    if (!pending.length) {
      message.warning('该生产记录没有待贴标包件')
      return
    }
    await preparePrint(pending, false)
  } finally {
    printPreparing.value = false
  }
}
const prepareTaskPrint = async () => {
  if (!queryParams.productionTaskId) return
  const packages = await RdPackageUnitApi.getTaskPackages(queryParams.productionTaskId)
  const pending = packages.filter((item) => item.status === 'CREATED')
  if (!pending.length) {
    message.warning('该任务没有待贴标包件')
    return
  }
  await preparePrint(pending, false)
}
const openPrintWindow = () => {
  if (reprintMode.value && !reprintReason.value.trim()) {
    message.warning('补打原因不能为空')
    return
  }
  const printWindow = window.open('', '_blank', 'width=760,height=680')
  if (!printWindow) {
    message.warning('浏览器已阻止打印窗口，请允许弹出窗口后重试')
    return
  }
  printWindow.document.open()
  printWindow.document.write(buildPackageLabelHtml(printLabels.value, printPaper.value, true))
  printWindow.document.close()
  printOpened.value = true
}
const confirmPrinted = async () => {
  if (!printOpened.value || printRegistering.value) return
  const reason = reprintReason.value.trim()
  if (reprintMode.value && !reason) {
    message.warning('补打原因不能为空')
    return
  }
  await message.confirm(`请核对 ${printLabels.value.length} 张标签已全部成功打印，再登记逐包记录。`)
  printRegistering.value = true
  try {
    const data = {
      productionTaskId: printTaskId.value,
      packageIds: printLabels.value.map((label) => label.item.id),
      reprintReason: reason || undefined
    }
    if (reprintMode.value) await RdPackageUnitApi.reprintBatch(data)
    else await RdPackageUnitApi.printBatch(data)
    message.success(
      `已登记 ${printLabels.value.length} 包${reprintMode.value ? '补打' : '打印'}记录`
    )
    printVisible.value = false
    printOpened.value = false
    selectedPackages.value = []
    await handleQuery()
  } finally {
    printRegistering.value = false
  }
}

onMounted(() => {
  loadTasks()
  if (route.query.packageCode || route.query.productionOutputId || route.query.productionTaskId)
    return applyRouteQuery()
  return getList()
})

watch(
  () => [route.query.packageCode, route.query.productionOutputId, route.query.productionTaskId],
  () => applyRouteQuery()
)
</script>

<style scoped>
:global(.package-label-dialog) {
  max-width: calc(100vw - 24px);
  max-height: calc(100vh - 32px);
  overflow-y: auto;
}

:global(.package-label-dialog .el-dialog__footer) {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

:global(.package-label-dialog .el-dialog__footer .el-button) {
  margin-left: 0;
}

.label-preview {
  display: block;
  width: 100%;
  height: 340px;
  margin-top: 12px;
  border: 1px solid var(--el-border-color);
}

@media (width <= 480px) {
  .label-preview {
    height: 420px;
  }
}
</style>
