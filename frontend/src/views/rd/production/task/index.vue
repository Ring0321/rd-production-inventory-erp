<template>
  <main class="production-task-page">
    <h1>生产任务</h1>
    <el-result v-if="!canQuery" icon="warning" title="无生产任务查询权限" />
    <template v-else>
      <ContentWrap>
        <el-form :model="query" inline class="task-query" @submit.prevent="search">
          <el-form-item label="任务号">
            <el-input v-model="query.taskNo" clearable @keyup.enter="search" />
          </el-form-item>
          <el-form-item label="客户">
            <el-input v-model="query.customerName" clearable @keyup.enter="search" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.status" clearable>
              <el-option
                v-for="item in statuses"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button @click="search"><Icon icon="lucide:search" class="mr-5px" />查询</el-button>
            <el-button @click="reset"
              ><Icon icon="lucide:rotate-ccw" class="mr-5px" />重置</el-button
            >
            <el-button
              v-if="hasPermission(['rd:production-task:create'])"
              type="primary"
              @click="formRef?.open()"
            >
              <Icon icon="lucide:plus" class="mr-5px" />新建任务
            </el-button>
          </el-form-item>
        </el-form>
      </ContentWrap>
      <ContentWrap>
        <el-alert v-if="listError" :title="listError" type="error" :closable="false" show-icon />
        <el-table
          v-loading="loading"
          :data="list"
          stripe
          show-overflow-tooltip
          empty-text="暂无生产任务"
        >
          <el-table-column label="任务号" prop="taskNo" min-width="190" />
          <el-table-column label="客户" prop="customerName" min-width="160" />
          <el-table-column label="业务生产日期" prop="productionDate" width="130" />
          <el-table-column label="班次" prop="shiftCode" width="100" />
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="taskStatus(row.status).type">{{
                taskStatus(row.status).label
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="班长" prop="managerName" min-width="100" />
          <el-table-column label="备注" prop="remark" min-width="180" />
          <el-table-column label="操作" fixed="right" :width="hasTaskMutation ? 270 : 80">
            <template #default="{ row }">
              <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
              <template v-if="row.status === 'ACTIVE'">
                <el-button
                  v-if="hasPermission(['rd:production-task:update'])"
                  link
                  type="primary"
                  @click="formRef?.open(row.id)"
                  >编辑</el-button
                >
                <el-button
                  v-if="hasPermission(['rd:production-task:close'])"
                  link
                  type="warning"
                  :disabled="actionBusy"
                  @click="changeStatus(row, 'close')"
                  >结束登记</el-button
                >
                <el-button
                  v-if="hasPermission(['rd:production-task:cancel'])"
                  link
                  type="danger"
                  :disabled="actionBusy"
                  @click="changeStatus(row, 'cancel')"
                  >作废</el-button
                >
              </template>
            </template>
          </el-table-column>
        </el-table>
        <Pagination
          v-model:page="query.pageNo"
          v-model:limit="query.pageSize"
          :total="total"
          @pagination="loadList"
        />
      </ContentWrap>
    </template>

    <el-drawer
      v-model="detailVisible"
      title="生产任务详情"
      size="min(880px, 100vw)"
      @closed="clearDetail"
    >
      <el-skeleton v-if="detailLoading" :rows="6" animated />
      <div v-else-if="detailError" class="detail-error" role="alert">
        <el-alert :title="detailError" type="error" :closable="false" show-icon />
        <el-button @click="reloadDetail"
          ><Icon icon="lucide:refresh-cw" class="mr-5px" />重试</el-button
        >
      </div>
      <template v-else-if="task">
        <header class="detail-header">
          <h2>{{ task.taskNo }}</h2>
          <el-tag :type="taskStatus(task.status).type">{{ taskStatus(task.status).label }}</el-tag>
          <el-tooltip content="刷新任务详情">
            <el-button
              circle
              aria-label="刷新任务详情"
              :disabled="actionBusy || actingId !== undefined"
              @click="reloadDetail"
            >
              <Icon icon="lucide:refresh-cw" />
            </el-button>
          </el-tooltip>
        </header>
        <el-descriptions :column="2" border class="task-description">
          <el-descriptions-item label="客户">{{ task.customerName }}</el-descriptions-item>
          <el-descriptions-item label="业务生产日期">{{
            task.productionDate
          }}</el-descriptions-item>
          <el-descriptions-item label="班次">{{ task.shiftCode }}</el-descriptions-item>
          <el-descriptions-item label="班长">{{ task.managerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{
            timeText(task.createTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{
            timeText(task.closedTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="作废时间">{{
            timeText(task.cancelledTime)
          }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ task.version }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{
            task.remark || '-'
          }}</el-descriptions-item>
        </el-descriptions>
        <div class="detail-actions">
          <template v-if="task.status === 'ACTIVE'">
            <el-button
              v-if="hasPermission(['rd:production-task:update'])"
              @click="formRef?.open(task.id)"
              ><Icon icon="lucide:pencil" class="mr-5px" />编辑任务</el-button
            >
            <el-button
              v-if="hasPermission(['rd:production-output:create'])"
              type="primary"
              @click="openPda"
              ><Icon icon="lucide:clipboard-plus" class="mr-5px" />生产录入</el-button
            >
            <el-button
              v-if="hasPermission(['rd:production-task:close'])"
              :disabled="actionBusy || actingId !== undefined"
              @click="changeStatus(task, 'close')"
              >结束登记</el-button
            >
            <el-button
              v-if="hasPermission(['rd:production-task:cancel'])"
              type="danger"
              plain
              :disabled="actionBusy || actingId !== undefined"
              @click="changeStatus(task, 'cancel')"
              >作废任务</el-button
            >
          </template>
        </div>
        <section class="output-section" aria-labelledby="task-output-title">
          <h2 id="task-output-title"
            >生产记录 <small>{{ outputTotal }} 条</small></h2
          >
          <el-alert v-if="!canRead" title="无生产记录查询权限" type="info" :closable="false" />
          <template v-else>
            <el-alert
              v-if="outputError || outputActionError"
              :title="outputError || outputActionError"
              type="error"
              :closable="false"
              show-icon
            />
            <el-button v-if="outputError" @click="loadOutputs">重试</el-button>
            <el-table
              v-loading="outputLoading"
              :data="outputs"
              show-overflow-tooltip
              empty-text="该任务暂无生产记录"
            >
              <el-table-column label="型号" prop="productModel" min-width="120" />
              <el-table-column label="炉号 / 炉层" min-width="145">
                <template #default="{ row }">{{ row.furnaceNo }} / {{ row.furnaceLayer }}</template>
              </el-table-column>
              <el-table-column label="包数" prop="outputQty" width="70" align="right" />
              <el-table-column label="实称单包 kg" prop="packageWeight" width="120" align="right" />
              <el-table-column label="出炉日期" prop="furnaceOutDate" width="110" />
              <el-table-column label="录入人" prop="operatorName" min-width="90" />
              <el-table-column label="状态" width="100">
                <template #default="{ row }">{{ outputStatusText(row.status) }}</template>
              </el-table-column>
              <el-table-column label="包码数" width="80" align="right">
                <template #default="{ row }">{{ packageCount(row.id) ?? '-' }}</template>
              </el-table-column>
              <el-table-column
                v-if="canConfirm || canGenerate"
                label="操作"
                fixed="right"
                width="180"
              >
                <template #default="{ row }">
                  <el-button
                    v-if="canConfirm"
                    link
                    type="primary"
                    :disabled="
                      task.status !== 'ACTIVE' || row.status !== 'DRAFT' || actingId !== undefined
                    "
                    @click="runAction(row, 'confirm')"
                    >确认产出</el-button
                  >
                  <el-button
                    v-if="canGenerate"
                    link
                    type="warning"
                    :disabled="
                      task.status !== 'ACTIVE' ||
                      row.status !== 'CONFIRMED' ||
                      actingId !== undefined ||
                      (packageCount(row.id) ?? 0) > 0
                    "
                    @click="runAction(row, 'generate')"
                    >生成包码</el-button
                  >
                </template>
              </el-table-column>
            </el-table>
            <Pagination
              v-model:page="outputPage"
              v-model:limit="outputPageSize"
              :total="outputTotal"
              @pagination="loadOutputs"
            />
          </template>
        </section>
        <section class="label-section" aria-labelledby="task-label-title">
          <h2 id="task-label-title">任务标签</h2>
          <p v-if="packagesLoaded" class="package-summary">已生成 {{ packages.length }} 个包码</p>
          <el-alert v-if="packageError" :title="packageError" type="error" :closable="false" />
          <div class="detail-actions">
            <el-button v-if="packageError" :loading="packageLoading" @click="loadPackages"
              >重试</el-button
            >
            <el-button v-if="canReadLabels" @click="openLabels('history')"
              ><Icon icon="lucide:history" class="mr-5px" />包码与标签历史</el-button
            >
            <el-button v-if="canPrint" type="primary" plain @click="openLabels('first')"
              ><Icon icon="lucide:printer" class="mr-5px" />首次标签</el-button
            >
            <el-button v-if="canReprint" @click="openLabels('reprint')"
              ><Icon icon="lucide:copy" class="mr-5px" />补打标签</el-button
            >
          </div>
        </section>
      </template>
    </el-drawer>
    <TaskForm ref="formRef" @saved="onSaved" />
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { hasPermission } from '@/directives/permission/hasPermi'
import { formatDate } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import {
  RdProductionTaskApi,
  type RdProductionTaskVO,
  type RdProductionTaskPageReqVO,
  type RdProductionTaskStatus
} from '@/api/rd/production/task'
import TaskForm from './TaskForm.vue'
import { useTaskOutputs, outputStatusText } from './useTaskOutputs'

defineOptions({ name: 'RdProductionTask' })
const route = useRoute()
const router = useRouter()
const message = useMessage()
const canQuery = computed(() => hasPermission(['rd:production-task:query']))
const hasTaskMutation = computed(() =>
  hasPermission([
    'rd:production-task:update',
    'rd:production-task:close',
    'rd:production-task:cancel'
  ])
)
const statuses: Array<{
  value: RdProductionTaskStatus
  label: string
  type: 'success' | 'info' | 'danger'
}> = [
  { value: 'ACTIVE', label: '登记中', type: 'success' },
  { value: 'CLOSED', label: '已结束登记', type: 'info' },
  { value: 'CANCELLED', label: '已作废', type: 'danger' }
]
const taskStatus = (status: string) =>
  statuses.find((item) => item.value === status) || { label: status, type: 'info' as const }
const timeText = (value?: string) => (value ? formatDate(value) : '-')
const query = reactive<RdProductionTaskPageReqVO & { pageNo: number; pageSize: number }>({
  pageNo: 1,
  pageSize: 10,
  taskNo: '',
  customerName: '',
  status: undefined
})
const list = ref<RdProductionTaskVO[]>([])
const total = ref(0)
const loading = ref(false)
const listError = ref('')
const actionBusy = ref(false)
const formRef = ref<InstanceType<typeof TaskForm>>()
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailError = ref('')
const selectedId = ref<number>()
const task = ref<RdProductionTaskVO>()
let listRequest = 0
let detailRequest = 0
const {
  outputs,
  total: outputTotal,
  page: outputPage,
  pageSize: outputPageSize,
  loading: outputLoading,
  error: outputError,
  actionError: outputActionError,
  actingId,
  packages,
  packagesLoaded,
  packageLoading,
  packageError,
  packageCount,
  canRead,
  canConfirm,
  canGenerate,
  canReadLabels,
  canPrint,
  canReprint,
  loadOutputs,
  loadPackages,
  refresh: refreshOutputs,
  runAction,
  openLabels
} = useTaskOutputs(task)

const loadList = async () => {
  if (!canQuery.value) return
  const request = ++listRequest
  loading.value = true
  listError.value = ''
  try {
    const data = await RdProductionTaskApi.getPage({
      ...query,
      taskNo: query.taskNo?.trim() || undefined,
      customerName: query.customerName?.trim() || undefined
    })
    if (request !== listRequest) return
    list.value = data.list
    total.value = data.total
  } catch {
    if (request === listRequest) {
      list.value = []
      total.value = 0
      listError.value = '任务列表加载失败，请重新查询。'
    }
  } finally {
    if (request === listRequest) loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  void loadList()
}
const reset = () => {
  Object.assign(query, { taskNo: '', customerName: '', status: undefined })
  search()
}
const reloadDetail = async () => {
  const id = selectedId.value
  if (!id || !canQuery.value) return
  const request = ++detailRequest
  detailLoading.value = true
  detailError.value = ''
  try {
    const data = await RdProductionTaskApi.get(id)
    if (request !== detailRequest) return
    const sameTask = task.value?.id === id
    task.value = data
    if (sameTask) await refreshOutputs()
  } catch {
    if (request === detailRequest) {
      task.value = undefined
      detailError.value = '任务详情加载失败，请重试。'
    }
  } finally {
    if (request === detailRequest) detailLoading.value = false
  }
}
const openDetail = (id: number) => {
  if (!canQuery.value) return
  if (selectedId.value !== id) task.value = undefined
  selectedId.value = id
  detailVisible.value = true
  void reloadDetail()
}
const clearDetail = () => {
  detailRequest++
  selectedId.value = undefined
  task.value = undefined
}
const onSaved = async (id: number) => {
  await loadList()
  if (selectedId.value === id) await reloadDetail()
}
const changeStatus = async (row: RdProductionTaskVO, action: 'close' | 'cancel') => {
  if (
    actionBusy.value ||
    row.status !== 'ACTIVE' ||
    !hasPermission([`rd:production-task:${action}`])
  )
    return
  actionBusy.value = true
  try {
    await message.confirm(
      action === 'close'
        ? `结束任务 ${row.taskNo} 的生产登记？有效产出须已确认并生成全部实际包码。结束登记不等于质检或入库完成。`
        : `作废任务 ${row.taskNo}？仅无有效产出的任务可作废，历史记录保留。`
    )
    await RdProductionTaskApi[action]({ id: row.id, version: row.version })
    message.success(action === 'close' ? '已结束生产登记' : '任务已作废')
    await loadList()
    if (selectedId.value === row.id) await reloadDetail()
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close') {
      message.error('操作未完成，请刷新核对任务状态。')
      await loadList()
      if (selectedId.value === row.id) await reloadDetail()
    }
  } finally {
    actionBusy.value = false
  }
}
const openPda = () => {
  if (!task.value || !hasPermission(['rd:production-output:create'])) return
  void router.push({
    name: 'RdProductionEntry',
    query: { productionTaskId: String(task.value.id) }
  })
}

watch(
  () => route.query.productionTaskId,
  (value) => {
    const id = Number(value)
    if (value && Number.isSafeInteger(id) && id > 0) openDetail(id)
  },
  { immediate: true }
)
onMounted(loadList)
</script>

<style scoped>
.production-task-page h1 {
  margin: 0 0 16px;
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.task-query :deep(.el-input),
.task-query :deep(.el-select) {
  width: 200px;
}

.detail-header,
.detail-actions {
  display: flex;
  margin-bottom: 16px;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

.detail-header h2 {
  min-width: 0;
  margin: 0;
  font-size: 18px;
  overflow-wrap: anywhere;
}

.detail-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.task-description {
  margin-bottom: 16px;
  overflow-wrap: anywhere;
}

.output-section,
.label-section {
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}

.output-section h2,
.label-section h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

.output-section small,
.package-summary {
  font-size: 13px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

.detail-error {
  display: grid;
  gap: 12px;
  justify-items: start;
}

@media (width <= 600px) {
  .task-query :deep(.el-form-item),
  .task-query :deep(.el-input),
  .task-query :deep(.el-select) {
    width: 100%;
    margin-right: 0;
  }
}
</style>
