<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="72px"
    >
      <el-form-item label="文件名" prop="fileName">
        <el-input
          v-model="queryParams.fileName"
          placeholder="输入文件名"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="基准日期" prop="bizDate">
        <el-date-picker
          v-model="queryParams.bizDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="全部" clearable class="!w-160px">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          @click="openImportForm"
          v-hasPermi="['rd:excel-import:create']"
        >
          <Icon icon="ep:upload-filled" class="mr-5px" />上传预检
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="任务号" prop="id" width="90" align="center" />
      <el-table-column label="文件名" prop="fileName" min-width="220" />
      <el-table-column label="工作表" prop="sheetName" min-width="140" />
      <el-table-column label="基准日期" prop="bizDate" width="110" align="center">
        <template #default="scope">{{ formatBizDate(scope.row.bizDate) }}</template>
      </el-table-column>
      <el-table-column label="状态" prop="status" width="100" align="center">
        <template #default="scope">
          <el-tag :type="statusMap[scope.row.status]?.type || 'info'">
            {{ statusMap[scope.row.status]?.label || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="业务行" prop="totalRows" width="80" align="right" />
      <el-table-column label="可导入" prop="validRows" width="80" align="right" />
      <el-table-column label="错误" prop="failedRows" width="70" align="right">
        <template #default="scope">
          <el-link
            v-if="scope.row.failedRows > 0"
            type="danger"
            :underline="false"
            @click="openDetail(scope.row.id, 'errors')"
          >
            {{ scope.row.failedRows }}
          </el-link>
          <span v-else>0</span>
        </template>
      </el-table-column>
      <el-table-column label="零库存" prop="skippedRows" width="80" align="right" />
      <el-table-column label="已导入" prop="importedRows" width="80" align="right" />
      <el-table-column label="创建时间" prop="createTime" width="170" :formatter="dateFormatter" />
      <el-table-column label="操作" width="250" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" @click="openDetail(scope.row.id)">
            <Icon icon="ep:view" class="mr-3px" />详情
          </el-button>
          <el-button
            link
            type="success"
            :disabled="!canConfirm(scope.row)"
            @click="handleConfirm(scope.row)"
            v-hasPermi="['rd:excel-import:confirm']"
          >
            <Icon icon="ep:circle-check" class="mr-3px" />确认
          </el-button>
          <el-button
            v-if="scope.row.failedRows > 0"
            link
            type="warning"
            @click="handleExportErrors(scope.row.id)"
            v-hasPermi="['rd:excel-import:export']"
          >
            <Icon icon="ep:download" class="mr-3px" />错误
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getPage"
    />
  </ContentWrap>

  <ImportForm ref="importFormRef" @success="handlePrecheckSuccess" />
  <ImportDetailDrawer ref="detailDrawerRef" @export-errors="handleExportErrors" />
</template>

<script setup lang="ts">
import download from '@/utils/download'
import { dateFormatter } from '@/utils/formatTime'
import { RdExcelImportApi } from '@/api/rd/stock/excel-import'
import type { RdExcelImportTaskVO } from '@/api/rd/stock/excel-import'
import ImportForm from './ImportForm.vue'
import ImportDetailDrawer from './ImportDetailDrawer.vue'

defineOptions({ name: 'RdStockImport' })

type StatusTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const message = useMessage()
const loading = ref(false)
const exportLoading = ref(false)
const list = ref<RdExcelImportTaskVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const importFormRef = ref()
const detailDrawerRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  fileName: undefined as string | undefined,
  bizDate: undefined as string[] | undefined,
  status: undefined as string | undefined
})
const statusOptions: Array<{ label: string; value: string; type: StatusTagType }> = [
  { label: '已预检', value: 'PRECHECKED', type: 'warning' },
  { label: '导入中', value: 'CONFIRMING', type: 'primary' },
  { label: '已完成', value: 'SUCCESS', type: 'success' },
  { label: '失败', value: 'FAILED', type: 'danger' }
]
const statusMap = statusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: StatusTagType }>
)

const getPage = async () => {
  loading.value = true
  try {
    const data = await RdExcelImportApi.getPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getPage()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}

const openImportForm = () => {
  importFormRef.value?.open()
}

const openDetail = (id: number, tab = 'rows') => {
  detailDrawerRef.value?.open(id, tab)
}

const handlePrecheckSuccess = async (taskId: number) => {
  await getPage()
  openDetail(taskId)
}

const canConfirm = (task: RdExcelImportTaskVO) => {
  return task.status === 'PRECHECKED' && task.failedRows === 0 && task.validRows > 0
}

const formatBizDate = (value: string | number[]) => {
  if (typeof value === 'string') return value
  return value
    .slice(0, 3)
    .map((part, index) => (index === 0 ? String(part) : String(part).padStart(2, '0')))
    .join('-')
}

const handleConfirm = async (task: RdExcelImportTaskVO) => {
  await message.confirm(`确认把任务 #${task.id} 的 ${task.validRows} 条正库存写入库存台账吗？`)
  loading.value = true
  try {
    const count = await RdExcelImportApi.confirm(task.id)
    message.success(`已导入 ${count} 条库存批次`)
    await getPage()
  } finally {
    loading.value = false
  }
}

const handleExportErrors = async (taskId: number) => {
  if (exportLoading.value) return
  exportLoading.value = true
  try {
    const data = await RdExcelImportApi.exportErrors(taskId)
    download.excel(data, `库存导入错误-${taskId}.xls`)
  } finally {
    exportLoading.value = false
  }
}

onMounted(getPage)
</script>
