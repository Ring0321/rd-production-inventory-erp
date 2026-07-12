<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams">
      <el-form-item label="入库单号" prop="orderNo">
        <el-input
          v-model="queryParams.orderNo"
          clearable
          class="!w-220px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-160px" placeholder="全部">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          v-hasPermi="['rd:inbound-order:create']"
          @click="openCreate"
        >
          <Icon icon="ep:plus" class="mr-5px" />创建入库单
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="displayList" stripe show-overflow-tooltip>
      <el-table-column label="入库单号" prop="orderNo" min-width="200" fixed="left" />
      <el-table-column label="业务日期" prop="bizDate" width="110" />
      <el-table-column label="来源" prop="sourceType" width="150" />
      <el-table-column label="生产记录ID" prop="productionOutputId" width="120" align="center" />
      <el-table-column label="包数" prop="totalPackages" width="90" align="right" />
      <el-table-column label="总重量" prop="totalWeight" width="110" align="right" />
      <el-table-column label="状态" prop="status" width="100" align="center">
        <template #default="scope"
          ><el-tag :type="statusMap[scope.row.status]?.type || 'info'">{{
            statusMap[scope.row.status]?.label || scope.row.status
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column label="提交人" prop="submitterName" width="100" />
      <el-table-column label="复核人" prop="reviewerName" width="100" />
      <el-table-column label="过账人" prop="posterName" width="100" />
      <el-table-column label="撤销原因" prop="reverseReason" min-width="150" />
      <el-table-column label="操作" min-width="250" fixed="right" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-hasPermi="['rd:inbound-order:query']"
            @click="openDetail(scope.row.id)"
            >查看</el-button
          >
          <el-button
            link
            type="primary"
            :disabled="scope.row.status !== 'DRAFT'"
            v-hasPermi="['rd:inbound-order:submit']"
            @click="handleAction(scope.row, 'submit')"
            >提交</el-button
          >
          <el-button
            link
            type="warning"
            :disabled="scope.row.status !== 'SUBMITTED'"
            v-hasPermi="['rd:inbound-order:verify']"
            @click="handleAction(scope.row, 'verify')"
            >复核</el-button
          >
          <el-button
            link
            type="success"
            :disabled="scope.row.status !== 'VERIFIED'"
            v-hasPermi="['rd:inbound-order:post']"
            @click="handleAction(scope.row, 'post')"
            >过账</el-button
          >
          <el-button
            link
            type="danger"
            :disabled="scope.row.status !== 'POSTED'"
            v-hasPermi="['rd:inbound-order:reverse']"
            @click="handleReverse(scope.row)"
            >撤销</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="pageParams.pageSize"
      v-model:page="pageParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog v-model="createVisible" title="创建生产入库单" width="980px">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
      <el-row :gutter="20">
        <el-col :span="8"
          ><el-form-item label="业务日期" prop="bizDate"
            ><el-date-picker
              v-model="createForm.bizDate"
              type="date"
              value-format="YYYY-MM-DD"
              class="!w-100%" /></el-form-item
        ></el-col>
        <el-col :span="16">
          <el-form-item label="生产记录" prop="productionOutputId">
            <el-select
              v-model="createForm.productionOutputId"
              filterable
              class="!w-100%"
              @change="handleProductionChange"
            >
              <el-option
                v-for="item in productionOptions"
                :key="item.id"
                :label="item.label"
                :value="item.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="质检合格包码" prop="selectedPackageIds">
        <el-select
          v-model="selectedPackageIds"
          multiple
          filterable
          collapse-tags
          collapse-tags-tooltip
          class="!w-100%"
          :disabled="!createForm.productionOutputId"
          @change="syncLines"
        >
          <el-option
            v-for="item in selectablePackages"
            :key="item.id"
            :label="`${item.packageCode} | ${item.productModel} | ${item.packageWeight}`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="入库明细">
        <el-table :data="createForm.lines" border>
          <el-table-column label="包码" min-width="180"
            ><template #default="scope">{{
              packageMap.get(scope.row.packageId)?.packageCode
            }}</template></el-table-column
          >
          <el-table-column label="客户/型号" min-width="220"
            ><template #default="scope"
              >{{ packageMap.get(scope.row.packageId)?.customerName }} /
              {{ packageMap.get(scope.row.packageId)?.productModel }}</template
            ></el-table-column
          >
          <el-table-column label="重量" width="100" align="right" prop="weight" />
          <el-table-column label="入库货位" min-width="230">
            <template #default="scope">
              <el-select
                v-model="scope.row.locationId"
                filterable
                class="!w-100%"
                @change="(id: number) => handleLocationChange(scope.row, id)"
              >
                <el-option
                  v-for="location in locationOptions"
                  :key="location.id"
                  :label="`${location.locationCode} | ${location.warehouseName}${location.areaName ? ` / ${location.areaName}` : ''}`"
                  :value="location.id"
                />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </el-form-item>
      <el-form-item label="备注" prop="remark"
        ><el-input v-model="createForm.remark" type="textarea" :rows="3"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="createLoading" @click="submitCreate">创建</el-button>
      <el-button @click="createVisible = false">取消</el-button>
    </template>
  </Dialog>

  <Dialog v-model="detailVisible" title="入库单详情" width="980px">
    <el-descriptions v-if="detail" :column="2" border>
      <el-descriptions-item label="入库单号" :span="2">{{ detail.orderNo }}</el-descriptions-item>
      <el-descriptions-item label="业务日期">{{ detail.bizDate }}</el-descriptions-item>
      <el-descriptions-item label="状态"
        ><el-tag :type="statusMap[detail.status]?.type || 'info'">{{
          statusMap[detail.status]?.label
        }}</el-tag></el-descriptions-item
      >
      <el-descriptions-item label="生产记录ID">{{
        detail.productionOutputId
      }}</el-descriptions-item>
      <el-descriptions-item label="包数/总重量"
        >{{ detail.totalPackages }} / {{ detail.totalWeight }}</el-descriptions-item
      >
      <el-descriptions-item label="提交人">{{ detail.submitterName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="复核人">{{ detail.reviewerName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="过账人">{{ detail.posterName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="撤销人">{{ detail.reverserName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="撤销原因" :span="2">{{
        detail.reverseReason || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-table v-if="detail?.lines?.length" :data="detail.lines" border class="mt-16px">
      <el-table-column label="包ID" prop="packageId" width="100" align="center" />
      <el-table-column label="货位" prop="locationCode" min-width="130" />
      <el-table-column label="数量（包）" prop="quantity" width="110" align="right" />
      <el-table-column label="重量（kg）" prop="weight" width="120" align="right" />
      <el-table-column label="库存批次ID" prop="stockLotId" width="120" align="center" />
      <el-table-column label="库存流水ID" prop="stockTransactionId" width="120" align="center" />
      <el-table-column label="备注" prop="remark" min-width="140" />
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { formatDate } from '@/utils/formatTime'
import { RdWarehouseLocationApi, type RdWarehouseLocationVO } from '@/api/rd/masterdata/location'
import { RdPackageUnitApi, type RdPackageUnitVO } from '@/api/rd/stock/package'
import {
  RdInboundOrderApi,
  type RdInboundOrderCreateReqVO,
  type RdInboundOrderLineVO,
  type RdInboundOrderStatus,
  type RdInboundOrderVO
} from '@/api/rd/stock/inbound'

defineOptions({ name: 'RdStockInbound' })

type TagType = 'success' | 'warning' | 'info' | 'danger'
const statusOptions: Array<{ value: RdInboundOrderStatus; label: string; type: TagType }> = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'SUBMITTED', label: '待复核', type: 'warning' },
  { value: 'VERIFIED', label: '已复核', type: 'warning' },
  { value: 'POSTED', label: '已过账', type: 'success' },
  { value: 'REVERSED', label: '已撤销', type: 'danger' }
]
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<
  RdInboundOrderStatus,
  (typeof statusOptions)[number]
>
const message = useMessage()
const loading = ref(false)
const list = ref<RdInboundOrderVO[]>([])
const total = ref(0)
const pageParams = reactive({ pageNo: 1, pageSize: 10 })
const queryParams = reactive<{ orderNo?: string; status?: RdInboundOrderStatus }>({})
const queryFormRef = ref<FormInstance>()
const displayList = computed(() =>
  list.value.filter((item) => {
    const noMatched =
      !queryParams.orderNo || item.orderNo.toLowerCase().includes(queryParams.orderNo.toLowerCase())
    return noMatched && (!queryParams.status || item.status === queryParams.status)
  })
)

const createVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<RdInboundOrderCreateReqVO>({
  bizDate: formatDate(new Date(), 'YYYY-MM-DD'),
  productionOutputId: 0,
  lines: [],
  remark: ''
})
const selectedPackageIds = ref<number[]>([])
const packageOptions = ref<RdPackageUnitVO[]>([])
const locationOptions = ref<RdWarehouseLocationVO[]>([])
const packageMap = computed(() => new Map(packageOptions.value.map((item) => [item.id, item])))
const productionOptions = computed(() => {
  const values = new Map<number, string>()
  packageOptions.value.forEach((item) => {
    if (!values.has(item.productionOutputId))
      values.set(
        item.productionOutputId,
        `${item.productionDate} | ${item.customerName} | ${item.productModel} | ${item.furnaceNo}-${item.furnaceLayer}`
      )
  })
  return [...values].map(([id, label]) => ({ id, label }))
})
const selectablePackages = computed(() =>
  packageOptions.value.filter((item) => item.productionOutputId === createForm.productionOutputId)
)
const createRules = reactive<FormRules>({
  bizDate: [{ required: true, message: '业务日期不能为空', trigger: 'change' }],
  productionOutputId: [{ required: true, message: '请选择生产记录', trigger: 'change' }]
})
const detailVisible = ref(false)
const detail = ref<RdInboundOrderVO>()

const getList = async () => {
  loading.value = true
  try {
    const data = await RdInboundOrderApi.getPage(pageParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  pageParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.orderNo = undefined
  queryParams.status = undefined
  handleQuery()
}
const openCreate = async () => {
  const [packageData, locations] = await Promise.all([
    RdPackageUnitApi.getPage({ pageNo: 1, pageSize: -1 }),
    RdWarehouseLocationApi.getOptions()
  ])
  packageOptions.value = packageData.list.filter((item) => item.status === 'QC_PASSED')
  locationOptions.value = locations.filter((item) => item.enabled && !item.frozen)
  createForm.bizDate = formatDate(new Date(), 'YYYY-MM-DD')
  createForm.productionOutputId = 0
  createForm.lines = []
  createForm.remark = ''
  selectedPackageIds.value = []
  createVisible.value = true
  nextTick(() => createFormRef.value?.clearValidate())
}
const handleProductionChange = () => {
  selectedPackageIds.value = []
  createForm.lines = []
}
const syncLines = (ids: number[]) => {
  const oldLines = new Map(createForm.lines.map((line) => [line.packageId, line]))
  createForm.lines = ids.map(
    (id) =>
      oldLines.get(id) || {
        packageId: id,
        locationId: 0,
        locationCode: '',
        quantity: 1,
        weight: packageMap.value.get(id)?.packageWeight || 0
      }
  )
}
const handleLocationChange = (line: RdInboundOrderLineVO, id: number) => {
  const location = locationOptions.value.find((item) => item.id === id)
  line.locationId = id
  line.locationCode = location?.locationCode || ''
}
const submitCreate = async () => {
  await createFormRef.value?.validate()
  if (!createForm.lines.length) {
    message.warning('请至少选择一个质检合格包码')
    return
  }
  if (createForm.lines.some((line) => !line.locationId || !line.locationCode)) {
    message.warning('请为每个包码选择入库货位')
    return
  }
  createLoading.value = true
  try {
    await RdInboundOrderApi.create(createForm)
    message.success('入库单创建成功')
    createVisible.value = false
    await getList()
  } finally {
    createLoading.value = false
  }
}
const openDetail = async (id: number) => {
  detail.value = await RdInboundOrderApi.get(id)
  detailVisible.value = true
}
const actionLabels = { submit: '提交', verify: '复核', post: '过账' } as const
const handleAction = async (row: RdInboundOrderVO, action: keyof typeof actionLabels) => {
  await message.confirm(`确认${actionLabels[action]}入库单 ${row.orderNo}？`)
  await RdInboundOrderApi[action](row.id)
  message.success(`${actionLabels[action]}成功`)
  await getList()
}
const handleReverse = async (row: RdInboundOrderVO) => {
  const result = await message.prompt('请输入撤销原因（必填）', '撤销入库单')
  const reason = result.value?.trim()
  if (!reason) {
    message.warning('撤销原因不能为空')
    return
  }
  await RdInboundOrderApi.reverse({ id: row.id, reason })
  message.success('撤销成功')
  await getList()
}
onMounted(getList)
</script>
