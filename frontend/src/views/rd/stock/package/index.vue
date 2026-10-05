<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams">
      <el-form-item label="包码" prop="packageCode">
        <el-input
          v-model="queryParams.packageCode"
          class="!w-220px"
          clearable
          placeholder="请输入包码"
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
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="displayList" stripe show-overflow-tooltip>
      <el-table-column label="包码" prop="packageCode" width="180" fixed="left" />
      <el-table-column label="生产记录ID" prop="productionOutputId" width="120" align="center" />
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
            @click="printLabel(scope.row, false)"
            >打印</el-button
          >
          <el-button
            link
            type="warning"
            :disabled="scope.row.status !== 'LABELED'"
            v-hasPermi="['rd:label-print:reprint']"
            @click="printLabel(scope.row, true)"
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

  <Dialog v-model="previewVisible" title="货品标识卡预览" width="620px">
    <div v-if="previewPackage" class="goods-card">
      <div class="goods-card__title">天净隆鼎货品标识卡</div>
      <div class="goods-card__body">
        <div class="goods-card__fields">
          <div
            ><span>客户名称</span><strong>{{ previewPackage.customerName }}</strong></div
          >
          <div
            ><span>产品名称</span
            ><strong>{{ previewPackage.productName || previewPackage.productModel }}</strong></div
          >
          <div
            ><span>产品型号</span><strong>{{ previewPackage.productModel }}</strong></div
          >
          <div
            ><span>生产日期</span><strong>{{ previewPackage.productionDate }}</strong></div
          >
          <div
            ><span>出炉日期</span><strong>{{ previewPackage.furnaceOutDate || '-' }}</strong></div
          >
          <div
            ><span>班次</span><strong>{{ previewPackage.shiftCode || '-' }}</strong></div
          >
          <div
            ><span>炉号/炉层</span
            ><strong
              >{{ previewPackage.furnaceNo }} / {{ previewPackage.furnaceLayer }}</strong
            ></div
          >
          <div
            ><span>单包重量</span><strong>{{ previewPackage.packageWeight }}</strong></div
          >
          <div
            ><span>包码</span><strong>{{ previewPackage.packageCode }}</strong></div
          >
        </div>
        <img v-if="qrDataUrl" :src="qrDataUrl" alt="包码二维码" class="goods-card__qr" />
      </div>
    </div>
    <template #footer>
      <el-button
        type="primary"
        :disabled="previewPackage?.status !== 'CREATED'"
        v-hasPermi="['rd:label-print:print']"
        @click="previewPackage && printLabel(previewPackage, false)"
        ><Icon icon="ep:printer" class="mr-5px" />打印</el-button
      >
      <el-button
        type="warning"
        :disabled="previewPackage?.status !== 'LABELED'"
        v-hasPermi="['rd:label-print:reprint']"
        @click="previewPackage && printLabel(previewPackage, true)"
        ><Icon icon="ep:printer" class="mr-5px" />补打</el-button
      >
      <el-button @click="previewVisible = false">关闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import QRCode from 'qrcode'
import { useRoute } from 'vue-router'
import download from '@/utils/download'
import { getPackageDigitalLink } from '@/utils/rdDigitalLink'
import { RdProductionOutputApi, type RdProductionOutputVO } from '@/api/rd/production/output'
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
const total = ref(0)
const traceMode = ref(false)
const queryFormRef = ref<FormInstance>()
const queryParams = reactive<{ packageCode?: string; productionOutputId?: number }>({})
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
const qrDataUrl = ref('')

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
  const packageCode = queryParams.packageCode?.trim()
  if (!packageCode && !queryParams.productionOutputId) {
    pageParams.pageNo = 1
    await getList()
    return
  }
  loading.value = true
  try {
    const packages = await RdPackageUnitApi.trace({
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
  await handleQuery()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.packageCode = undefined
  queryParams.productionOutputId = undefined
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
  qrDataUrl.value = await QRCode.toDataURL(getPackageDigitalLink(row.packageCode, row.qrCode), {
    width: 220,
    margin: 1,
    errorCorrectionLevel: 'M'
  })
  previewVisible.value = true
}
const escapeHtml = (value: unknown) =>
  String(value ?? '-').replace(
    /[&<>'"]/g,
    (char) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char] || char
  )
const buildPrintHtml = (item: RdPackageUnitVO, qrUrl: string) =>
  `<!doctype html><html><head><meta charset="utf-8"><title>${escapeHtml(item.packageCode)}</title><style>@page{size:100mm 76mm;margin:4mm}*{box-sizing:border-box}body{margin:0;font-family:Arial,"Microsoft YaHei",sans-serif;color:#111}.card{border:2px solid #111;width:92mm;height:68mm;padding:3mm}.title{text-align:center;font-size:18px;font-weight:700;border-bottom:1px solid #111;padding-bottom:2mm}.body{display:grid;grid-template-columns:1fr 28mm;gap:2mm;padding-top:2mm}.row{display:grid;grid-template-columns:22mm 1fr;border-bottom:1px solid #aaa;font-size:10px;line-height:4.8mm}.row span{color:#555}.row strong{overflow-wrap:anywhere}.qr{width:27mm;height:27mm;margin-top:8mm}.code{text-align:center;font-size:9px;overflow-wrap:anywhere}</style></head><body><div class="card"><div class="title">天净隆鼎货品标识卡</div><div class="body"><div><div class="row"><span>单据日期</span><strong>${escapeHtml(item.productionDate)}</strong></div><div class="row"><span>来源单据</span><strong>SC${escapeHtml(item.productionOutputId)}</strong></div><div class="row"><span>序列号</span><strong>${escapeHtml(item.packageCode)}</strong></div><div class="row"><span>客户名称</span><strong>${escapeHtml(item.customerName)}</strong></div><div class="row"><span>产品名称</span><strong>${escapeHtml(item.productName || item.productModel)}</strong></div><div class="row"><span>产品型号</span><strong>${escapeHtml(item.productModel)}</strong></div><div class="row"><span>出炉日期</span><strong>${escapeHtml(item.furnaceOutDate)}</strong></div><div class="row"><span>生产班别</span><strong>${escapeHtml(item.shiftCode)}</strong></div><div class="row"><span>炉号/炉层</span><strong>${escapeHtml(item.furnaceNo)} / ${escapeHtml(item.furnaceLayer)}</strong></div><div class="row"><span>单包重量</span><strong>${escapeHtml(item.packageWeight)} kg</strong></div></div><div><img class="qr" src="${qrUrl}"><div class="code">${escapeHtml(item.packageCode)}</div></div></div></div><script>window.onload=()=>{window.print()}<\/script></body></html>`
const printLabel = async (item: RdPackageUnitVO, reprint: boolean) => {
  let reason: string | undefined
  if (reprint) {
    const result = await message.prompt('请输入标签补打原因（必填）', '标签补打')
    reason = result.value?.trim()
    if (!reason) {
      message.warning('补打原因不能为空')
      return
    }
  }
  const printWindow = window.open('', '_blank', 'width=760,height=680')
  if (!printWindow) {
    message.warning('浏览器已阻止打印窗口，请允许弹出窗口后重试')
    return
  }
  try {
    const qrUrl = await QRCode.toDataURL(getPackageDigitalLink(item.packageCode, item.qrCode), {
      width: 360,
      margin: 1,
      errorCorrectionLevel: 'M'
    })
    printWindow.document.open()
    printWindow.document.write(buildPrintHtml(item, qrUrl))
    printWindow.document.close()
    await message.confirm('打印窗口已打开。请确认标签已经成功打印，再登记本次打印记录。')
    if (reprint) await RdPackageUnitApi.reprint({ packageId: item.id, reprintReason: reason })
    else await RdPackageUnitApi.print({ packageId: item.id })
    message.success(reprint ? '补打记录已登记' : '打印记录已登记')
    previewVisible.value = false
    await handleQuery()
  } catch (error) {
    printWindow.close()
    throw error
  }
}

onMounted(() => {
  if (route.query.packageCode || route.query.productionOutputId) return applyRouteQuery()
  return getList()
})

watch(
  () => [route.query.packageCode, route.query.productionOutputId],
  () => applyRouteQuery()
)
</script>

<style scoped>
.goods-card {
  padding: 16px;
  border: 2px solid var(--el-text-color-primary);
}

.goods-card__title {
  padding-bottom: 10px;
  font-size: 22px;
  font-weight: 700;
  text-align: center;
  border-bottom: 1px solid var(--el-border-color-darker);
}

.goods-card__body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 16px;
  padding-top: 12px;
}

.goods-card__fields > div {
  display: grid;
  line-height: 32px;
  border-bottom: 1px solid var(--el-border-color);
  grid-template-columns: 92px minmax(0, 1fr);
}

.goods-card__fields span {
  color: var(--el-text-color-secondary);
}

.goods-card__fields strong {
  overflow-wrap: anywhere;
}

.goods-card__qr {
  width: 180px;
  height: 180px;
  align-self: center;
}

@media (width <= 640px) {
  .goods-card__body {
    grid-template-columns: 1fr;
  }

  .goods-card__qr {
    justify-self: center;
  }
}
</style>
