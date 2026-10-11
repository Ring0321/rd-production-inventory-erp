<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams">
      <el-form-item label="检验单号" prop="inspectionNo">
        <el-input
          v-model="queryParams.inspectionNo"
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
          v-hasPermi="['rd:quality-inspection:create']"
          @click="openCreate"
        >
          <Icon icon="ep:plus" class="mr-5px" />创建质检
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="displayList" stripe show-overflow-tooltip>
      <el-table-column label="检验单号" prop="inspectionNo" min-width="190" fixed="left" />
      <el-table-column label="包ID" prop="packageId" width="100" align="center" />
      <el-table-column label="状态" prop="status" width="110" align="center">
        <template #default="scope"
          ><el-tag :type="statusMap[scope.row.status]?.type || 'info'">{{
            statusMap[scope.row.status]?.label || scope.row.status
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column label="结论" prop="conclusion" width="110" align="center" />
      <el-table-column label="提交人" prop="submitterName" width="110" />
      <el-table-column label="提交时间" prop="submitTime" width="170" :formatter="dateFormatter" />
      <el-table-column label="审核人" prop="reviewerName" width="110" />
      <el-table-column label="审核时间" prop="reviewTime" width="170" :formatter="dateFormatter" />
      <el-table-column label="驳回原因" prop="rejectReason" min-width="160" />
      <el-table-column label="备注" prop="remark" min-width="140" />
      <el-table-column label="操作" min-width="220" fixed="right" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-hasPermi="['rd:quality-inspection:query']"
            @click="openDetail(scope.row.id)"
            >查看</el-button
          >
          <el-button
            link
            type="primary"
            :disabled="!['DRAFT', 'CORRECTED'].includes(scope.row.status)"
            v-hasPermi="['rd:quality-inspection:submit']"
            @click="handleSubmit(scope.row)"
            >提交</el-button
          >
          <el-button
            link
            type="success"
            :disabled="!['SUBMITTED', 'RESUBMITTED'].includes(scope.row.status)"
            v-hasPermi="['rd:quality-inspection:approve']"
            @click="handleApprove(scope.row)"
            >通过</el-button
          >
          <el-button
            link
            type="danger"
            :disabled="!['SUBMITTED', 'RESUBMITTED'].includes(scope.row.status)"
            v-hasPermi="['rd:quality-inspection:approve']"
            @click="handleReject(scope.row)"
            >驳回</el-button
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

  <Dialog v-model="createVisible" title="创建质量检验" width="920px">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
      <el-form-item label="待检包码" prop="packageId">
        <el-select v-model="createForm.packageId" filterable class="!w-100%">
          <el-option
            v-for="item in packageOptions"
            :key="item.id"
            :label="`${item.packageCode} | ${item.customerName} | ${item.productModel} | ${item.furnaceNo}-${item.furnaceLayer}`"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="检验项目">
        <el-table :data="createForm.items" border>
          <el-table-column label="项目编码" min-width="120"
            ><template #default="scope"><el-input v-model="scope.row.itemCode" /></template
          ></el-table-column>
          <el-table-column label="项目名称" min-width="130"
            ><template #default="scope"><el-input v-model="scope.row.itemName" /></template
          ></el-table-column>
          <el-table-column label="标准下限" width="120"
            ><template #default="scope"
              ><el-input-number
                v-model="scope.row.standardMin"
                :controls="false"
                class="!w-100%" /></template
          ></el-table-column>
          <el-table-column label="标准上限" width="120"
            ><template #default="scope"
              ><el-input-number
                v-model="scope.row.standardMax"
                :controls="false"
                class="!w-100%" /></template
          ></el-table-column>
          <el-table-column label="实测值" width="120"
            ><template #default="scope"
              ><el-input-number
                v-model="scope.row.measuredValue"
                :controls="false"
                class="!w-100%" /></template
          ></el-table-column>
          <el-table-column label="单位" width="90"
            ><template #default="scope"><el-input v-model="scope.row.unit" /></template
          ></el-table-column>
          <el-table-column label="合格" width="80" align="center"
            ><template #default="scope"><el-checkbox v-model="scope.row.qualified" /></template
          ></el-table-column>
          <el-table-column label="操作" width="70" align="center"
            ><template #default="scope"
              ><el-button
                link
                type="danger"
                :disabled="createForm.items.length === 1"
                @click="removeItem(scope.$index)"
                ><Icon icon="ep:delete" /></el-button></template
          ></el-table-column>
        </el-table>
        <el-button class="mt-10px" plain @click="addItem"
          ><Icon icon="ep:plus" class="mr-5px" />添加项目</el-button
        >
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

  <Dialog v-model="detailVisible" title="质检详情" width="920px">
    <InspectionDetail v-if="detail?.members?.length" :inspection="detail" />
    <el-descriptions v-if="detail && !detail.members?.length" :column="2" border>
      <el-descriptions-item label="检验单号">{{ detail.inspectionNo }}</el-descriptions-item>
      <el-descriptions-item label="包ID">{{ detail.packageId }}</el-descriptions-item>
      <el-descriptions-item label="状态"
        ><el-tag :type="statusMap[detail.status]?.type || 'info'">{{
          statusMap[detail.status]?.label
        }}</el-tag></el-descriptions-item
      >
      <el-descriptions-item label="结论">{{ detail.conclusion || '-' }}</el-descriptions-item>
      <el-descriptions-item label="提交人">{{ detail.submitterName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="审核人">{{ detail.reviewerName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="驳回原因" :span="2">{{
        detail.rejectReason || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
    </el-descriptions>
    <el-table
      v-if="detail?.items?.length && !detail.members?.length"
      :data="detail.items"
      border
      class="mt-16px"
    >
      <el-table-column label="检测项目" prop="itemName" min-width="140" />
      <el-table-column label="项目编码" prop="itemCode" width="110" />
      <el-table-column label="下限" prop="standardMin" width="90" align="right" />
      <el-table-column label="实测值" prop="measuredValue" width="100" align="right" />
      <el-table-column label="上限" prop="standardMax" width="90" align="right" />
      <el-table-column label="单位" prop="unit" width="80" />
      <el-table-column label="判定" width="90" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.qualified ? 'success' : 'danger'">
            {{ scope.row.qualified ? '合格' : '不合格' }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
  </Dialog>
</template>

<script setup lang="ts">
import InspectionDetail from '@/views/rd/pda/quality/InspectionDetail.vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useRoute } from 'vue-router'
import { hasPermission } from '@/directives/permission/hasPermi'
import { dateFormatter } from '@/utils/formatTime'
import { RdPackageUnitApi, type RdPackageUnitVO } from '@/api/rd/stock/package'
import {
  RdQualityInspectionApi,
  type RdQualityInspectionCreateReqVO,
  type RdQualityInspectionItemVO,
  type RdQualityInspectionStatus,
  type RdQualityInspectionVO
} from '@/api/rd/quality/inspection'

defineOptions({ name: 'RdQualityInspection' })

type TagType = 'success' | 'warning' | 'info' | 'danger'
const route = useRoute()
const statusOptions: Array<{ value: RdQualityInspectionStatus; label: string; type: TagType }> = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'SUBMITTED', label: '待审核', type: 'warning' },
  { value: 'PASSED', label: '已通过', type: 'success' },
  { value: 'REJECTED', label: '已驳回', type: 'danger' },
  { value: 'CORRECTED', label: '已修正', type: 'warning' },
  { value: 'RESUBMITTED', label: '已重提', type: 'warning' }
]
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<
  RdQualityInspectionStatus,
  (typeof statusOptions)[number]
>
const message = useMessage()
const loading = ref(false)
const list = ref<RdQualityInspectionVO[]>([])
const total = ref(0)
const pageParams = reactive({ pageNo: 1, pageSize: 10 })
const queryParams = reactive<{ inspectionNo?: string; status?: RdQualityInspectionStatus }>({})
const queryFormRef = ref<FormInstance>()
const displayList = computed(() => list.value)

const newItem = (): RdQualityInspectionItemVO => ({
  itemCode: '',
  itemName: '',
  standardMin: undefined,
  standardMax: undefined,
  measuredValue: undefined,
  unit: '',
  qualified: true,
  remark: ''
})
const createVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive<RdQualityInspectionCreateReqVO>({
  packageId: 0,
  items: [newItem()],
  remark: ''
})
const packageOptions = ref<RdPackageUnitVO[]>([])
const createRules = reactive<FormRules>({
  packageId: [{ required: true, message: '请选择待检包码', trigger: 'change' }]
})
const detailVisible = ref(false)
const detail = ref<RdQualityInspectionVO>()
const inspectablePackageStatuses = ['LABELED', 'QC_REJECTED']

const getList = async () => {
  loading.value = true
  try {
    const data = await RdQualityInspectionApi.getPage({ ...pageParams, ...queryParams })
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
  queryParams.inspectionNo = undefined
  queryParams.status = undefined
  handleQuery()
}
const resetCreateForm = () => {
  createForm.packageId = 0
  createForm.items = [newItem()]
  createForm.remark = ''
}
const loadInspectablePackages = async (
  params: { packageCode?: string; productionOutputId?: number } = {}
) => {
  const hasTraceQuery = Boolean(params.packageCode || params.productionOutputId)
  const list = hasTraceQuery
    ? await RdPackageUnitApi.trace(params)
    : (await RdPackageUnitApi.getPage({ pageNo: 1, pageSize: -1 })).list
  packageOptions.value = list.filter((item) => inspectablePackageStatuses.includes(item.status))
  return packageOptions.value
}
const openCreateDialog = async (packageId?: number) => {
  await loadInspectablePackages()
  resetCreateForm()
  if (packageId) createForm.packageId = packageId
  createVisible.value = true
  nextTick(() => createFormRef.value?.clearValidate())
}
const openCreate = () => openCreateDialog()
const getRouteString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : String(value || '')
}
const getRouteNumber = (key: string) => {
  const value = Number(getRouteString(key))
  return Number.isFinite(value) && value > 0 ? value : undefined
}
const openCreateFromRoute = async () => {
  const inspectionId = getRouteNumber('inspectionId')
  if (inspectionId && hasPermission(['rd:quality-inspection:query'])) {
    await openDetail(inspectionId)
    return
  }
  const packageCode = getRouteString('packageCode')
  const productionOutputId = getRouteNumber('productionOutputId')
  if (!packageCode && !productionOutputId) return
  if (!hasPermission(['rd:quality-inspection:create'])) return
  const options = await loadInspectablePackages({
    packageCode: packageCode || undefined,
    productionOutputId
  })
  const matched = options.find(
    (item) =>
      (!packageCode || item.packageCode === packageCode) &&
      (!productionOutputId || item.productionOutputId === productionOutputId)
  )
  if (!matched) {
    message.info('扫码包件当前不适合新建质检单，请在列表中查看现有质检记录')
    return
  }
  resetCreateForm()
  createForm.packageId = matched.id
  createVisible.value = true
  nextTick(() => createFormRef.value?.clearValidate())
}
const addItem = () => createForm.items.push(newItem())
const removeItem = (index: number) => createForm.items.splice(index, 1)
const submitCreate = async () => {
  await createFormRef.value?.validate()
  if (createForm.items.some((item) => !item.itemCode.trim() || !item.itemName.trim())) {
    message.warning('检验项目编码和名称不能为空')
    return
  }
  createLoading.value = true
  try {
    await RdQualityInspectionApi.create(createForm)
    message.success('质检单创建成功')
    createVisible.value = false
    await getList()
  } finally {
    createLoading.value = false
  }
}
const openDetail = async (id: number) => {
  detail.value = await RdQualityInspectionApi.get(id)
  detailVisible.value = true
}
const handleSubmit = async (row: RdQualityInspectionVO) => {
  await message.confirm(`确认提交质检单 ${row.inspectionNo}？`)
  await RdQualityInspectionApi.submit(row.id)
  message.success('提交成功')
  await getList()
}
const handleApprove = async (row: RdQualityInspectionVO) => {
  await message.confirm(`确认通过质检单 ${row.inspectionNo}？`)
  await RdQualityInspectionApi.approve(row.id)
  message.success('审核通过')
  await getList()
}
const handleReject = async (row: RdQualityInspectionVO) => {
  const result = await message.prompt('请输入驳回原因（必填）', '质检驳回')
  const reason = result.value?.trim()
  if (!reason) {
    message.warning('驳回原因不能为空')
    return
  }
  await RdQualityInspectionApi.reject({ id: row.id, reason })
  message.success('已驳回')
  await getList()
}
onMounted(async () => {
  await getList()
  await openCreateFromRoute()
})

watch(
  () => [route.query.packageCode, route.query.productionOutputId, route.query.inspectionId],
  () => openCreateFromRoute()
)
</script>
