<template>
  <el-drawer v-model="drawerVisible" :title="drawerTitle" size="900px" class="import-detail-drawer">
    <div v-loading="loading">
      <el-descriptions v-if="task" :column="4" border>
        <el-descriptions-item label="基准日期">{{ task.bizDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[task.status]?.type || 'info'">
            {{ statusMap[task.status]?.label || task.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="业务行">{{ task.totalRows }}</el-descriptions-item>
        <el-descriptions-item label="可导入">{{ task.validRows }}</el-descriptions-item>
        <el-descriptions-item label="错误">{{ task.failedRows }}</el-descriptions-item>
        <el-descriptions-item label="零库存跳过">{{ task.skippedRows }}</el-descriptions-item>
        <el-descriptions-item label="已导入">{{ task.importedRows }}</el-descriptions-item>
        <el-descriptions-item label="确认时间">{{ task.confirmTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-tabs v-model="activeTab" class="mt-16px" @tab-change="handleTabChange">
        <el-tab-pane label="行预览" name="rows">
          <div class="detail-toolbar">
            <el-segmented
              v-model="rowQuery.status"
              :options="rowStatusOptions"
              @change="handleRowStatus"
            />
          </div>
          <el-table :data="rows" :stripe="true" :show-overflow-tooltip="true" height="480">
            <el-table-column label="行号" prop="rowNo" width="70" align="center" fixed />
            <el-table-column label="货位" prop="locationCode" width="90" fixed />
            <el-table-column label="客户" prop="customerName" min-width="150" />
            <el-table-column label="型号" prop="productModel" min-width="120" />
            <el-table-column label="生产炉号" prop="furnaceNo" min-width="110" />
            <el-table-column label="所在炉层" prop="furnaceLayer" min-width="100" />
            <el-table-column label="库房数量" prop="warehouseQty" width="100" align="right" />
            <el-table-column label="单包重量" prop="packageWeight" width="100" align="right" />
            <el-table-column label="库存重量" prop="stockWeight" width="110" align="right" />
            <el-table-column label="状态" prop="status" width="90" align="center">
              <template #default="scope">
                <el-tag :type="rowStatusType(scope.row.status)" size="small">
                  {{ rowStatusLabel(scope.row.status) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="校验说明" prop="errorMessage" min-width="220" />
          </el-table>
          <Pagination
            :total="rowTotal"
            v-model:page="rowQuery.pageNo"
            v-model:limit="rowQuery.pageSize"
            @pagination="getRows"
          />
        </el-tab-pane>

        <el-tab-pane :label="`错误明细 ${task?.failedRows || 0}`" name="errors">
          <div class="detail-toolbar">
            <el-button
              type="success"
              plain
              :disabled="!task?.failedRows"
              @click="emitExportErrors"
              v-hasPermi="['rd:excel-import:export']"
            >
              <Icon icon="ep:download" class="mr-5px" />导出错误
            </el-button>
          </div>
          <el-table :data="errors" :stripe="true" :show-overflow-tooltip="true" height="480">
            <el-table-column label="Excel 行号" prop="rowNo" width="100" align="center" />
            <el-table-column label="字段" prop="fieldName" width="130" />
            <el-table-column label="原始值" prop="rawValue" min-width="180" />
            <el-table-column label="错误原因" prop="errorMessage" min-width="300" />
          </el-table>
          <Pagination
            :total="errorTotal"
            v-model:page="errorQuery.pageNo"
            v-model:limit="errorQuery.pageSize"
            @pagination="getErrors"
          />
        </el-tab-pane>
      </el-tabs>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import type {
  RdExcelImportErrorVO,
  RdExcelImportRowVO,
  RdExcelImportTaskVO
} from '@/api/rd/stock/excel-import'
import { RdExcelImportApi } from '@/api/rd/stock/excel-import'

defineOptions({ name: 'RdStockImportDetailDrawer' })

type StatusTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const drawerVisible = ref(false)
const loading = ref(false)
const task = ref<RdExcelImportTaskVO>()
const rows = ref<RdExcelImportRowVO[]>([])
const errors = ref<RdExcelImportErrorVO[]>([])
const rowTotal = ref(0)
const errorTotal = ref(0)
const activeTab = ref('rows')
const rowQuery = reactive({ pageNo: 1, pageSize: 10, taskId: 0, status: '' })
const errorQuery = reactive({ pageNo: 1, pageSize: 10, taskId: 0 })
const rowStatusOptions = [
  { label: '全部', value: '' },
  { label: '可导入', value: 'VALID' },
  { label: '错误', value: 'ERROR' },
  { label: '已跳过', value: 'SKIPPED' },
  { label: '已导入', value: 'IMPORTED' }
]
const statusMap: Record<string, { label: string; type: StatusTagType }> = {
  PRECHECKED: { label: '已预检', type: 'warning' },
  CONFIRMING: { label: '导入中', type: 'primary' },
  SUCCESS: { label: '已完成', type: 'success' },
  FAILED: { label: '失败', type: 'danger' }
}
const drawerTitle = computed(() =>
  task.value ? `导入任务 #${task.value.id} - ${task.value.fileName}` : '导入任务详情'
)
const emits = defineEmits<{
  exportErrors: [taskId: number]
}>()

const open = async (id: number, tab = 'rows') => {
  drawerVisible.value = true
  activeTab.value = tab
  rowQuery.taskId = id
  rowQuery.pageNo = 1
  rowQuery.status = ''
  errorQuery.taskId = id
  errorQuery.pageNo = 1
  loading.value = true
  try {
    task.value = await RdExcelImportApi.get(id)
    await getRows()
    if (tab === 'errors') await getErrors()
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

const getRows = async () => {
  const data = await RdExcelImportApi.getRowPage(rowQuery)
  rows.value = data.list
  rowTotal.value = data.total
}

const getErrors = async () => {
  const data = await RdExcelImportApi.getErrorPage(errorQuery)
  errors.value = data.list
  errorTotal.value = data.total
}

const handleTabChange = async (name: string | number) => {
  if (name === 'errors' && errors.value.length === 0) await getErrors()
}

const handleRowStatus = () => {
  rowQuery.pageNo = 1
  getRows()
}

const emitExportErrors = () => {
  if (task.value) emits('exportErrors', task.value.id)
}

const rowStatusLabel = (status: string) =>
  ({
    VALID: '可导入',
    ERROR: '错误',
    SKIPPED: '已跳过',
    IMPORTED: '已导入'
  })[status] || status

const rowStatusType = (status: string): StatusTagType =>
  (({
    VALID: 'success',
    ERROR: 'danger',
    SKIPPED: 'info',
    IMPORTED: 'primary'
  })[status] as StatusTagType) || 'info'
</script>

<style scoped>
:global(.import-detail-drawer) {
  max-width: 96%;
}

.detail-toolbar {
  display: flex;
  min-height: 40px;
  align-items: flex-start;
  justify-content: flex-end;
}
</style>
