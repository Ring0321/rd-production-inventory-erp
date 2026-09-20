<template>
  <ContentWrap>
    <div class="workbench-title">
      <div>
        <h2>入库上架工作台</h2>
        <p>仓管员选择货物、计划库位和叉车司机；最终库位需由仓管确认后才进入正式库存。</p>
      </div>
      <el-button :loading="refreshing" @click="refreshAll">
        <Icon icon="ep:refresh" class="mr-5px" />刷新
      </el-button>
    </div>
    <el-tabs v-model="activeTab">
      <el-tab-pane label="库位与派单" name="dispatch" />
      <el-tab-pane label="上架任务" name="task" />
    </el-tabs>
  </ContentWrap>

  <template v-if="activeTab === 'dispatch'">
    <ContentWrap>
      <el-form :inline="true" :model="locationQuery">
        <el-form-item label="仓库">
          <el-select
            v-model="locationQuery.warehouseCode"
            class="!w-200px"
            clearable
            placeholder="全部仓库"
            @change="handleWarehouseChange"
          >
            <el-option
              v-for="item in warehouseOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="区域">
          <el-select
            v-model="locationQuery.areaCode"
            class="!w-180px"
            clearable
            placeholder="全部区域"
          >
            <el-option
              v-for="item in areaOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetLocationQuery">
            <Icon icon="ep:refresh-left" class="mr-5px" />重置
          </el-button>
        </el-form-item>
      </el-form>

      <div class="summary-strip">
        <div class="summary-item">
          <span>库位总数</span><strong>{{ filteredLocations.length }}</strong>
        </div>
        <div class="summary-item summary-empty">
          <span>空位</span><strong>{{ locationSummary.EMPTY }}</strong>
        </div>
        <div class="summary-item summary-partial">
          <span>部分占用</span><strong>{{ locationSummary.PARTIAL }}</strong>
        </div>
        <div class="summary-item summary-full">
          <span>已满</span><strong>{{ locationSummary.FULL }}</strong>
        </div>
        <div class="summary-item summary-blocked">
          <span>冻结/不可用</span><strong>{{ locationSummary.BLOCKED }}</strong>
        </div>
        <div class="summary-item">
          <span>待上架</span><strong>{{ pendingTotal }} 包</strong>
        </div>
      </div>
    </ContentWrap>

    <ContentWrap title="库位地图">
      <el-empty v-if="!locationLoading && !filteredLocations.length" description="暂无库位数据" />
      <div v-else v-loading="locationLoading" class="location-grid">
        <button
          v-for="location in filteredLocations"
          :key="location.id"
          type="button"
          class="location-tile"
          :class="[
            `status-${location.displayStatus.toLowerCase()}`,
            { selected: selectedLocationId === location.id }
          ]"
          :disabled="!isLocationSelectable(location)"
          @click="selectLocation(location)"
        >
          <div class="location-code">
            <span>{{ location.locationCode }}</span>
            <el-tag :type="locationTagType(location.displayStatus)" size="small">
              {{ locationStatusLabel(location.displayStatus) }}
            </el-tag>
          </div>
          <div class="location-meta">
            {{ location.warehouseName
            }}<template v-if="location.areaName"> / {{ location.areaName }}</template>
          </div>
          <div class="location-meta"> 排列层：{{ coordinateText(location) }} </div>
          <div class="capacity-line">
            <span>在库 {{ numberText(location.occupiedPackages) }}</span>
            <span>预留 {{ numberText(location.reservedPackages) }}</span>
          </div>
          <el-progress
            v-if="location.usagePercent !== undefined && location.usagePercent !== null"
            :percentage="Math.min(Number(location.usagePercent), 100)"
            :show-text="false"
            :stroke-width="6"
          />
          <div class="available-line">
            可用：{{ capacityText(location.availablePackages, '包') }} /
            {{ capacityText(location.availableWeight, 'kg') }}
          </div>
        </button>
      </div>
    </ContentWrap>

    <ContentWrap title="待上架货物">
      <el-form ref="pendingQueryFormRef" :inline="true" :model="pendingQuery">
        <el-form-item label="快速查找" prop="keyword">
          <el-input
            v-model="pendingQuery.keyword"
            class="!w-240px"
            clearable
            placeholder="包码、客户、型号或炉号"
            @keyup.enter="handlePendingQuery"
          />
        </el-form-item>
        <el-form-item label="炉层" prop="furnaceLayer">
          <el-input
            v-model="pendingQuery.furnaceLayer"
            class="!w-150px"
            clearable
            placeholder="上层/中层/下层"
            @keyup.enter="handlePendingQuery"
          />
        </el-form-item>
        <el-form-item>
          <el-button @click="handlePendingQuery">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
          <el-button @click="resetPendingQuery">
            <Icon icon="ep:refresh" class="mr-5px" />重置
          </el-button>
          <el-button
            type="primary"
            :disabled="!selectedPackageIds.length || !selectedLocationId"
            v-hasPermi="['rd:putaway-task:create']"
            @click="openCreateDialog"
          >
            <Icon icon="ep:promotion" class="mr-5px" />创建上架任务
          </el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="selectedPackageIds.length || selectedLocation"
        class="mb-12px"
        type="info"
        :closable="false"
        show-icon
      >
        已选 {{ selectedPackageIds.length }} 包，计划库位：{{
          selectedLocation?.locationCode || '尚未选择'
        }}
      </el-alert>

      <el-table
        ref="pendingTableRef"
        v-loading="pendingLoading"
        :data="pendingList"
        row-key="id"
        stripe
        show-overflow-tooltip
        @selection-change="handlePackageSelection"
      >
        <el-table-column type="selection" width="48" :reserve-selection="true" />
        <el-table-column label="包码" prop="packageCode" min-width="180" fixed="left" />
        <el-table-column label="生产日期" prop="productionDate" width="110" />
        <el-table-column label="客户" prop="customerName" min-width="150" />
        <el-table-column label="型号" prop="productModel" width="120" />
        <el-table-column label="炉号" prop="furnaceNo" width="130" />
        <el-table-column label="炉层" prop="furnaceLayer" width="100" />
        <el-table-column label="重量（kg）" prop="packageWeight" width="120" align="right" />
        <el-table-column label="班次" prop="shiftCode" width="90" />
      </el-table>
      <Pagination
        v-model:limit="pendingQuery.pageSize"
        v-model:page="pendingQuery.pageNo"
        :total="pendingTotal"
        @pagination="getPendingPackages"
      />
    </ContentWrap>
  </template>

  <template v-else>
    <ContentWrap>
      <el-form ref="taskQueryFormRef" :inline="true" :model="taskQuery">
        <el-form-item label="任务号" prop="taskNo">
          <el-input
            v-model="taskQuery.taskNo"
            class="!w-230px"
            clearable
            @keyup.enter="handleTaskQuery"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="taskQuery.status" class="!w-180px" clearable placeholder="全部">
            <el-option
              v-for="item in taskStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="司机" prop="forkliftDriverId">
          <el-select
            v-model="taskQuery.forkliftDriverId"
            class="!w-180px"
            clearable
            placeholder="全部司机"
          >
            <el-option
              v-for="item in driverOptions"
              :key="item.id"
              :label="driverLabel(item)"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="handleTaskQuery">
            <Icon icon="ep:search" class="mr-5px" />查询
          </el-button>
          <el-button @click="resetTaskQuery">
            <Icon icon="ep:refresh" class="mr-5px" />重置
          </el-button>
        </el-form-item>
      </el-form>
    </ContentWrap>

    <ContentWrap>
      <el-table v-loading="taskLoading" :data="taskList" stripe show-overflow-tooltip>
        <el-table-column label="任务号" prop="taskNo" min-width="230" fixed="left" />
        <el-table-column label="计划库位" prop="plannedLocationCode" width="130" />
        <el-table-column label="司机" prop="forkliftDriverName" width="120" />
        <el-table-column label="包数" prop="packageCount" width="80" align="right" />
        <el-table-column label="扫码进度" width="110" align="center">
          <template #default="scope">
            {{ scope.row.scannedPackageCount }}/{{ scope.row.packageCount }}
          </template>
        </el-table-column>
        <el-table-column label="总重量（kg）" prop="totalWeight" width="130" align="right" />
        <el-table-column label="状态" width="120" align="center">
          <template #default="scope">
            <el-tag :type="taskStatusMap[scope.row.status]?.type || 'info'">
              {{ taskStatusMap[scope.row.status]?.label || scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="异常" prop="exceptionPackageCount" width="80" align="center">
          <template #default="scope">
            <span :class="{ 'exception-count': scope.row.exceptionPackageCount > 0 }">
              {{ scope.row.exceptionPackageCount }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="派单人" prop="warehouseKeeperName" width="110" />
        <el-table-column label="派单时间" prop="assignedTime" min-width="165">
          <template #default="scope">{{ dateTimeText(scope.row.assignedTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="scope">
            <el-button link type="primary" @click="openTaskDetail(scope.row.id)">查看</el-button>
            <el-button
              v-if="['ASSIGNED', 'EXCEPTION_PENDING'].includes(scope.row.status)"
              link
              type="warning"
              v-hasPermi="['rd:putaway-task:assign']"
              @click="openReassignDialog(scope.row)"
            >
              调整派单
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <Pagination
        v-model:limit="taskQuery.pageSize"
        v-model:page="taskQuery.pageNo"
        :total="taskTotal"
        @pagination="getTasks"
      />
    </ContentWrap>
  </template>

  <Dialog v-model="createVisible" title="创建上架任务" width="720px">
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
      <el-form-item label="已选货物">
        <div class="selection-summary">
          <strong>{{ selectedPackageIds.length }} 包</strong>
          <span>合计 {{ selectedWeight.toFixed(2) }} kg</span>
        </div>
      </el-form-item>
      <el-form-item label="计划库位" prop="plannedLocationId">
        <el-select v-model="createForm.plannedLocationId" filterable class="!w-100%">
          <el-option
            v-for="item in selectableLocations"
            :key="item.id"
            :label="locationOptionLabel(item)"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="叉车司机" prop="forkliftDriverId">
        <el-select v-model="createForm.forkliftDriverId" filterable class="!w-100%">
          <el-option
            v-for="item in driverOptions"
            :key="item.id"
            :label="driverLabel(item)"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-alert
        v-if="!driverOptions.length"
        class="mb-16px"
        type="warning"
        :closable="false"
        title="尚无可派单司机，请先在系统管理中为人员分配“叉车司机”角色。"
        show-icon
      />
      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="createForm.remark"
          type="textarea"
          :rows="3"
          maxlength="512"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="createLoading" @click="submitCreate">确认派单</el-button>
      <el-button @click="createVisible = false">取消</el-button>
    </template>
  </Dialog>

  <Dialog v-model="reassignVisible" title="调整上架派单" width="680px">
    <el-form ref="reassignFormRef" :model="reassignForm" :rules="reassignRules" label-width="110px">
      <el-form-item label="任务号">
        <el-input :model-value="reassignTask?.taskNo" disabled />
      </el-form-item>
      <el-form-item label="计划库位" prop="plannedLocationId">
        <el-select v-model="reassignForm.plannedLocationId" filterable class="!w-100%">
          <el-option
            v-for="item in selectableLocations"
            :key="item.id"
            :label="locationOptionLabel(item)"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="叉车司机" prop="forkliftDriverId">
        <el-select v-model="reassignForm.forkliftDriverId" filterable class="!w-100%">
          <el-option
            v-for="item in driverOptions"
            :key="item.id"
            :label="driverLabel(item)"
            :value="item.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="调整说明" prop="handlingResult">
        <el-input
          v-model="reassignForm.handlingResult"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="例如：原库位已占满，改派至相邻空位"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="reassignLoading" @click="submitReassign"
        >保存调整</el-button
      >
      <el-button @click="reassignVisible = false">取消</el-button>
    </template>
  </Dialog>

  <Dialog v-model="detailVisible" title="上架任务详情" width="1020px">
    <el-descriptions v-if="taskDetail" :column="3" border>
      <el-descriptions-item label="任务号" :span="2">{{
        taskDetail.task.taskNo
      }}</el-descriptions-item>
      <el-descriptions-item label="状态">
        <el-tag :type="taskStatusMap[taskDetail.task.status]?.type || 'info'">
          {{ taskStatusMap[taskDetail.task.status]?.label || taskDetail.task.status }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="计划库位">{{
        taskDetail.task.plannedLocationCode
      }}</el-descriptions-item>
      <el-descriptions-item label="叉车司机">{{
        taskDetail.task.forkliftDriverName
      }}</el-descriptions-item>
      <el-descriptions-item label="派单人">{{
        taskDetail.task.warehouseKeeperName || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="包数/进度">
        {{ taskDetail.task.packageCount }} / {{ taskDetail.task.scannedPackageCount }} 已扫
      </el-descriptions-item>
      <el-descriptions-item label="总重量"
        >{{ taskDetail.task.totalWeight }} kg</el-descriptions-item
      >
      <el-descriptions-item label="派单时间">{{
        dateTimeText(taskDetail.task.assignedTime)
      }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="3">{{
        taskDetail.task.remark || '-'
      }}</el-descriptions-item>
    </el-descriptions>

    <el-tabs v-if="taskDetail" class="mt-16px">
      <el-tab-pane :label="`货物明细（${taskDetail.lines.length}）`">
        <el-table :data="taskDetail.lines" border max-height="360">
          <el-table-column label="序号" prop="lineNo" width="70" align="center" />
          <el-table-column label="包码" prop="packageCode" min-width="175" />
          <el-table-column label="客户" prop="customerName" min-width="140" />
          <el-table-column label="型号" prop="productModel" width="110" />
          <el-table-column label="炉号" prop="furnaceNo" width="125" />
          <el-table-column label="炉层" prop="furnaceLayer" width="100" />
          <el-table-column label="重量" prop="packageWeight" width="100" align="right" />
          <el-table-column label="明细状态" prop="lineStatus" width="105" />
        </el-table>
      </el-tab-pane>
      <el-tab-pane :label="`异常反馈（${taskDetail.exceptions.length}）`">
        <el-empty v-if="!taskDetail.exceptions.length" description="暂无异常反馈" />
        <el-table v-else :data="taskDetail.exceptions" border max-height="360">
          <el-table-column label="异常类型" prop="exceptionType" width="165" />
          <el-table-column label="包码" prop="packageCode" min-width="160" />
          <el-table-column label="说明" prop="description" min-width="230" />
          <el-table-column label="上报人" prop="reporterName" width="100" />
          <el-table-column label="上报时间" prop="reportedTime" width="165">
            <template #default="scope">{{ dateTimeText(scope.row.reportedTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" prop="exceptionStatus" width="100" />
          <el-table-column label="处理结果" prop="handlingResult" min-width="200" />
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { formatDate } from '@/utils/formatTime'
import {
  RdPutawayTaskApi,
  type RdPendingPutawayPackageVO,
  type RdPutawayDriverOptionVO,
  type RdPutawayLocationMapVO,
  type RdPutawayTaskDetailVO,
  type RdPutawayTaskStatus,
  type RdPutawayTaskVO
} from '@/api/rd/stock/putaway'

defineOptions({ name: 'RdPutawayWorkbench' })

type TagType = 'success' | 'warning' | 'info' | 'danger' | 'primary'
const taskStatusOptions: Array<{ value: RdPutawayTaskStatus; label: string; type: TagType }> = [
  { value: 'PENDING_PUTAWAY', label: '待上架', type: 'info' },
  { value: 'ASSIGNED', label: '已派单', type: 'primary' },
  { value: 'MOVING', label: '搬运中', type: 'warning' },
  { value: 'PENDING_CONFIRMATION', label: '待仓管确认', type: 'warning' },
  { value: 'PUTAWAY_COMPLETED', label: '已上架', type: 'success' },
  { value: 'EXCEPTION_PENDING', label: '异常待处理', type: 'danger' },
  { value: 'CANCELLED', label: '已取消', type: 'info' }
]
const taskStatusMap = Object.fromEntries(
  taskStatusOptions.map((item) => [item.value, item])
) as Record<RdPutawayTaskStatus, (typeof taskStatusOptions)[number]>

const message = useMessage()
const activeTab = ref('dispatch')
const refreshing = ref(false)

const locationLoading = ref(false)
const allLocations = ref<RdPutawayLocationMapVO[]>([])
const selectedLocationId = ref<number>()
const locationQuery = reactive<{ warehouseCode?: string; areaCode?: string }>({})
const warehouseOptions = computed(() => {
  const values = new Map<string, string>()
  allLocations.value.forEach((item) => values.set(item.warehouseCode, item.warehouseName))
  return [...values].map(([value, label]) => ({ value, label }))
})
const areaOptions = computed(() => {
  const values = new Map<string, string>()
  allLocations.value
    .filter(
      (item) => !locationQuery.warehouseCode || item.warehouseCode === locationQuery.warehouseCode
    )
    .forEach((item) => {
      if (item.areaCode) values.set(item.areaCode, item.areaName || item.areaCode)
    })
  return [...values].map(([value, label]) => ({ value, label }))
})
const filteredLocations = computed(() =>
  allLocations.value.filter(
    (item) =>
      (!locationQuery.warehouseCode || item.warehouseCode === locationQuery.warehouseCode) &&
      (!locationQuery.areaCode || item.areaCode === locationQuery.areaCode)
  )
)
const selectableLocations = computed(() => allLocations.value.filter(isLocationSelectable))
const selectedLocation = computed(() =>
  allLocations.value.find((item) => item.id === selectedLocationId.value)
)
const locationSummary = computed(() => {
  const result = { EMPTY: 0, PARTIAL: 0, FULL: 0, BLOCKED: 0 }
  filteredLocations.value.forEach((item) => {
    if (item.displayStatus === 'EMPTY') result.EMPTY += 1
    else if (item.displayStatus === 'PARTIAL') result.PARTIAL += 1
    else if (item.displayStatus === 'FULL') result.FULL += 1
    else result.BLOCKED += 1
  })
  return result
})

const pendingLoading = ref(false)
const pendingList = ref<RdPendingPutawayPackageVO[]>([])
const pendingTotal = ref(0)
const pendingTableRef = ref()
const pendingQueryFormRef = ref<FormInstance>()
const pendingQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: undefined,
  furnaceLayer: undefined
} as {
  pageNo: number
  pageSize: number
  keyword?: string
  furnaceLayer?: string
})
const selectedPackageIds = ref<number[]>([])
const selectedPackageMap = ref(new Map<number, RdPendingPutawayPackageVO>())
const selectedWeight = computed(() =>
  [...selectedPackageMap.value.values()].reduce(
    (sum, item) => sum + Number(item.packageWeight || 0),
    0
  )
)

const driverOptions = ref<RdPutawayDriverOptionVO[]>([])
const createVisible = ref(false)
const createLoading = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  plannedLocationId: undefined as number | undefined,
  forkliftDriverId: undefined as number | undefined,
  remark: ''
})
const createRules = reactive<FormRules>({
  plannedLocationId: [{ required: true, message: '请选择计划库位', trigger: 'change' }],
  forkliftDriverId: [{ required: true, message: '请选择叉车司机', trigger: 'change' }]
})

const taskLoading = ref(false)
const taskList = ref<RdPutawayTaskVO[]>([])
const taskTotal = ref(0)
const taskQueryFormRef = ref<FormInstance>()
const taskQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  taskNo: undefined,
  status: undefined,
  forkliftDriverId: undefined
} as {
  pageNo: number
  pageSize: number
  taskNo?: string
  status?: RdPutawayTaskStatus
  forkliftDriverId?: number
})

const reassignVisible = ref(false)
const reassignLoading = ref(false)
const reassignFormRef = ref<FormInstance>()
const reassignTask = ref<RdPutawayTaskVO>()
const reassignForm = reactive({
  plannedLocationId: undefined as number | undefined,
  forkliftDriverId: undefined as number | undefined,
  handlingResult: ''
})
const reassignRules = reactive<FormRules>({
  plannedLocationId: [{ required: true, message: '请选择计划库位', trigger: 'change' }],
  forkliftDriverId: [{ required: true, message: '请选择叉车司机', trigger: 'change' }],
  handlingResult: [{ required: true, message: '请填写调整说明', trigger: 'blur' }]
})

const detailVisible = ref(false)
const taskDetail = ref<RdPutawayTaskDetailVO>()

const getLocations = async () => {
  locationLoading.value = true
  try {
    allLocations.value = await RdPutawayTaskApi.getLocationMap()
  } finally {
    locationLoading.value = false
  }
}

const getPendingPackages = async () => {
  pendingLoading.value = true
  try {
    const data = await RdPutawayTaskApi.getPendingPackagePage(pendingQuery)
    pendingList.value = data.list
    pendingTotal.value = data.total
  } finally {
    pendingLoading.value = false
  }
}

const getTasks = async () => {
  taskLoading.value = true
  try {
    const data = await RdPutawayTaskApi.getPage(taskQuery)
    taskList.value = data.list
    taskTotal.value = data.total
  } finally {
    taskLoading.value = false
  }
}

const getDriverOptions = async () => {
  driverOptions.value = await RdPutawayTaskApi.getDriverOptions()
}

const refreshAll = async () => {
  refreshing.value = true
  try {
    await Promise.all([getLocations(), getPendingPackages(), getTasks(), getDriverOptions()])
  } finally {
    refreshing.value = false
  }
}

const handleWarehouseChange = () => {
  locationQuery.areaCode = undefined
}

const resetLocationQuery = () => {
  locationQuery.warehouseCode = undefined
  locationQuery.areaCode = undefined
}

const selectLocation = (location: RdPutawayLocationMapVO) => {
  selectedLocationId.value = location.id
}

const handlePendingQuery = () => {
  pendingQuery.pageNo = 1
  getPendingPackages()
}

const resetPendingQuery = () => {
  pendingQueryFormRef.value?.resetFields()
  pendingQuery.keyword = undefined
  pendingQuery.furnaceLayer = undefined
  handlePendingQuery()
}

const handlePackageSelection = (rows: RdPendingPutawayPackageVO[]) => {
  const pageIds = new Set(pendingList.value.map((item) => item.id))
  pageIds.forEach((id) => selectedPackageMap.value.delete(id))
  rows.forEach((item) => selectedPackageMap.value.set(item.id, item))
  selectedPackageIds.value = [...selectedPackageMap.value.keys()]
}

const openCreateDialog = () => {
  if (!selectedPackageIds.value.length || !selectedLocationId.value) {
    message.warning('请先选择待上架货物和计划库位')
    return
  }
  createForm.plannedLocationId = selectedLocationId.value
  createForm.forkliftDriverId = undefined
  createForm.remark = ''
  createVisible.value = true
  nextTick(() => createFormRef.value?.clearValidate())
}

const submitCreate = async () => {
  await createFormRef.value?.validate()
  if (!createForm.plannedLocationId || !createForm.forkliftDriverId) return
  createLoading.value = true
  try {
    await RdPutawayTaskApi.create({
      plannedLocationId: createForm.plannedLocationId,
      forkliftDriverId: createForm.forkliftDriverId,
      packageIds: selectedPackageIds.value,
      remark: createForm.remark
    })
    message.success('上架任务已派发，叉车司机端可立即查询')
    createVisible.value = false
    selectedPackageMap.value.clear()
    selectedPackageIds.value = []
    pendingTableRef.value?.clearSelection()
    await Promise.all([getLocations(), getPendingPackages(), getTasks()])
  } finally {
    createLoading.value = false
  }
}

const handleTaskQuery = () => {
  taskQuery.pageNo = 1
  getTasks()
}

const resetTaskQuery = () => {
  taskQueryFormRef.value?.resetFields()
  taskQuery.taskNo = undefined
  taskQuery.status = undefined
  taskQuery.forkliftDriverId = undefined
  handleTaskQuery()
}

const openReassignDialog = (task: RdPutawayTaskVO) => {
  reassignTask.value = task
  reassignForm.plannedLocationId = task.plannedLocationId
  reassignForm.forkliftDriverId = task.forkliftDriverId
  reassignForm.handlingResult = ''
  reassignVisible.value = true
  nextTick(() => reassignFormRef.value?.clearValidate())
}

const submitReassign = async () => {
  await reassignFormRef.value?.validate()
  if (!reassignTask.value || !reassignForm.plannedLocationId || !reassignForm.forkliftDriverId)
    return
  reassignLoading.value = true
  try {
    await RdPutawayTaskApi.reassign({
      id: reassignTask.value.id,
      plannedLocationId: reassignForm.plannedLocationId,
      forkliftDriverId: reassignForm.forkliftDriverId,
      version: reassignTask.value.version,
      handlingResult: reassignForm.handlingResult
    })
    message.success('派单信息已调整并同步')
    reassignVisible.value = false
    await Promise.all([getLocations(), getTasks()])
  } finally {
    reassignLoading.value = false
  }
}

const openTaskDetail = async (id: number) => {
  taskDetail.value = await RdPutawayTaskApi.get(id)
  detailVisible.value = true
}

function isLocationSelectable(location: RdPutawayLocationMapVO) {
  return (
    location.enabled && !location.frozen && ['EMPTY', 'PARTIAL'].includes(location.displayStatus)
  )
}

function locationTagType(status: string): TagType {
  if (status === 'EMPTY') return 'success'
  if (status === 'PARTIAL') return 'warning'
  if (status === 'FULL') return 'danger'
  return 'info'
}

function locationStatusLabel(status: string) {
  const labels: Record<string, string> = {
    EMPTY: '空位',
    PARTIAL: '部分占用',
    FULL: '已满',
    FROZEN: '冻结',
    DISABLED: '停用',
    MAINTENANCE: '维护中',
    RESTRICTED: '限制使用'
  }
  return labels[status] || status
}

function coordinateText(location: RdPutawayLocationMapVO) {
  return (
    [location.rowCode, location.columnCode, location.levelCode].filter(Boolean).join('/') || '-'
  )
}

function numberText(value?: number) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function capacityText(value: number | undefined, unit: string) {
  return value === undefined || value === null ? `未设上限` : `${numberText(value)} ${unit}`
}

function locationOptionLabel(location: RdPutawayLocationMapVO) {
  return `${location.locationCode} | ${location.warehouseName}${location.areaName ? ` / ${location.areaName}` : ''} | 可用 ${capacityText(location.availablePackages, '包')}`
}

function driverLabel(driver: RdPutawayDriverOptionVO) {
  return driver.nickname ? `${driver.nickname}（${driver.username}）` : driver.username
}

function dateTimeText(value?: string) {
  return value ? formatDate(new Date(value), 'YYYY-MM-DD HH:mm:ss') : '-'
}

onMounted(refreshAll)
</script>

<style scoped>
.workbench-title {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
}

.workbench-title h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 600;
  letter-spacing: 0;
}

.workbench-title p {
  margin: 0;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}

.summary-strip {
  display: grid;
  grid-template-columns: repeat(6, minmax(120px, 1fr));
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.summary-item {
  display: flex;
  min-height: 68px;
  padding: 10px 16px;
  flex-direction: column;
  justify-content: center;
  border-right: 1px solid var(--el-border-color-lighter);
}

.summary-item:last-child {
  border-right: 0;
}

.summary-item span {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.summary-item strong {
  margin-top: 4px;
  font-size: 22px;
  line-height: 1.2;
}

.summary-empty strong {
  color: #138a61;
}

.summary-partial strong {
  color: #b66c00;
}

.summary-full strong {
  color: #c53b3b;
}

.summary-blocked strong {
  color: #6b7280;
}

.location-grid {
  display: grid;
  min-height: 160px;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.location-tile {
  min-height: 150px;
  padding: 14px;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color);
  border-left-width: 4px;
  border-radius: 6px;
}

.location-tile:hover:not(:disabled),
.location-tile.selected {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 2px rgb(64 158 255 / 12%);
}

.location-tile:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.status-empty {
  border-left-color: #138a61;
}

.status-partial {
  border-left-color: #c98210;
}

.status-full {
  border-left-color: #c53b3b;
}

.status-frozen,
.status-disabled,
.status-maintenance,
.status-restricted {
  background: var(--el-fill-color-light);
  border-left-color: #6b7280;
}

.location-code,
.capacity-line,
.available-line,
.selection-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.location-code > span {
  overflow-wrap: anywhere;
  font-size: 16px;
  font-weight: 600;
}

.location-meta,
.capacity-line,
.available-line {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}

.available-line {
  display: block;
}

.exception-count {
  font-weight: 600;
  color: var(--el-color-danger);
}

.selection-summary {
  width: 100%;
  padding: 10px 12px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

@media (width <= 900px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .summary-item:nth-child(2n) {
    border-right: 0;
  }

  .workbench-title {
    flex-direction: column;
  }
}
</style>
