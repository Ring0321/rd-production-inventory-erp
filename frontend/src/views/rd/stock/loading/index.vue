<template>
  <ContentWrap>
    <div class="heading">
      <h2>出库装车</h2>
      <el-button :loading="refreshing" @click="refresh"
        ><Icon icon="ep:refresh" class="mr-5px" />刷新</el-button
      >
    </div>
    <el-tabs v-model="tab">
      <el-tab-pane label="选货派单" name="dispatch" />
      <el-tab-pane label="装车任务" name="tasks" />
      <el-tab-pane label="扫码复核" name="batchScan" />
    </el-tabs>
  </ContentWrap>

  <template v-if="tab === 'dispatch'">
    <ContentWrap title="提货单">
      <el-form :inline="true">
        <el-form-item label="提货单">
          <el-select
            v-model="slipId"
            filterable
            remote
            clearable
            :remote-method="searchSlips"
            class="!w-320px"
            placeholder="按提货单号搜索"
            @change="selectSlip"
          >
            <el-option
              v-for="slip in slips"
              :key="slip.id"
              :value="slip.id"
              :label="`${slip.slipNo} · ${slip.customerName}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="叉车司机">
          <el-select
            v-model="driverId"
            filterable
            clearable
            class="!w-190px"
            placeholder="选择司机"
          >
            <el-option
              v-for="driver in drivers"
              :key="driver.id"
              :value="driver.id"
              :label="driver.nickname || driver.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :disabled="!slipDetail || !driverId || !selected.length"
            :loading="dispatching"
            v-hasPermi="['rd:loading-task:dispatch']"
            @click="dispatch"
          >
            <Icon icon="ep:promotion" class="mr-5px" />派发装车任务
          </el-button>
        </el-form-item>
      </el-form>
      <el-descriptions v-if="slipDetail" :column="4" border>
        <el-descriptions-item label="客户">{{ slipDetail.slip.customerName }}</el-descriptions-item>
        <el-descriptions-item label="车牌号">{{
          slipDetail.slip.vehiclePlate || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="运输司机">{{
          slipDetail.slip.transportDriverName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="计划包数 / kg"
          >{{ slipDetail.slip.requestedPackageCount ?? '-' }} /
          {{ slipDetail.slip.requestedWeight ?? '-' }}</el-descriptions-item
        >
      </el-descriptions>
      <el-table v-if="slipDetail" :data="slipDetail.lines" class="mt-12px" size="small">
        <el-table-column prop="lineNo" label="行" width="55" />
        <el-table-column prop="saleOrderId" label="订单 ID" width="100" />
        <el-table-column prop="productModel" label="型号" min-width="120" />
        <el-table-column prop="furnaceNo" label="炉号" min-width="120" />
        <el-table-column prop="furnaceLayer" label="炉层" width="100" />
        <el-table-column prop="requestedPackageCount" label="包数" width="80" />
        <el-table-column prop="requestedWeight" label="重量 kg" width="100" />
      </el-table>
    </ContentWrap>

    <ContentWrap title="在库可选货物">
      <el-form :inline="true" :model="packageQuery">
        <el-form-item label="提货明细">
          <el-select
            v-model="filterLineId"
            clearable
            class="!w-220px"
            placeholder="全部明细"
            @change="applyLineFilter"
          >
            <el-option
              v-for="line in slipDetail?.lines || []"
              :key="line.id"
              :value="line.id"
              :label="lineLabel(line)"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="型号"
          ><el-input
            v-model="packageQuery.productModel"
            clearable
            class="!w-130px"
            @keyup.enter="loadPackages"
        /></el-form-item>
        <el-form-item label="炉号"
          ><el-input
            v-model="packageQuery.furnaceNo"
            clearable
            class="!w-130px"
            @keyup.enter="loadPackages"
        /></el-form-item>
        <el-form-item label="炉层"
          ><el-input
            v-model="packageQuery.furnaceLayer"
            clearable
            class="!w-120px"
            @keyup.enter="loadPackages"
        /></el-form-item>
        <el-form-item label="库位">
          <el-select
            v-model="packageQuery.locationId"
            filterable
            clearable
            class="!w-180px"
            placeholder="全部库位"
          >
            <el-option
              v-for="location in locations"
              :key="location.id"
              :value="location.id"
              :label="`${location.locationCode} · ${location.occupiedPackages} 包`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="包码/库位"
          ><el-input
            v-model="packageQuery.keyword"
            clearable
            class="!w-150px"
            @keyup.enter="loadPackages"
        /></el-form-item>
        <el-form-item
          ><el-button @click="loadPackages"
            ><Icon icon="ep:search" class="mr-5px" />查询</el-button
          ></el-form-item
        >
      </el-form>
      <el-table
        v-loading="packageLoading"
        :data="packages"
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column prop="packageCode" label="包码" min-width="170" fixed="left" />
        <el-table-column prop="locationCode" label="库位" width="115" />
        <el-table-column prop="customerName" label="客户" min-width="160" />
        <el-table-column prop="productModel" label="型号" width="120" />
        <el-table-column prop="furnaceNo" label="炉号" width="120" />
        <el-table-column prop="furnaceLayer" label="炉层" width="90" />
        <el-table-column prop="packageWeight" label="重量 kg" width="105" align="right" />
      </el-table>
      <div class="table-footer">
        <el-button :disabled="!currentSelection.length || !slipDetail" @click="addPackages"
          ><Icon icon="ep:plus" class="mr-5px" />加入已选</el-button
        >
        <Pagination
          v-model:page="packageQuery.pageNo"
          v-model:limit="packageQuery.pageSize"
          :total="packageTotal"
          @pagination="loadPackages"
        />
      </div>
    </ContentWrap>

    <ContentWrap title="已选装车货物">
      <div class="selected-summary"
        >{{ selected.length }} 包 · {{ selectedWeight }} kg · {{ selectedLocations }} 个库位</div
      >
      <el-table :data="selected" max-height="340">
        <el-table-column prop="pkg.packageCode" label="包码" min-width="175" />
        <el-table-column prop="pkg.locationCode" label="库位" width="110" />
        <el-table-column prop="pkg.productModel" label="型号" width="115" />
        <el-table-column prop="pkg.furnaceLayer" label="炉层" width="95" />
        <el-table-column prop="pkg.packageWeight" label="重量 kg" width="105" />
        <el-table-column label="对应提货明细" min-width="230">
          <template #default="scope">
            <el-select
              v-model="scope.row.pickupSlipLineId"
              class="!w-full"
              placeholder="请选择"
              clearable
            >
              <el-option
                v-for="line in matchingLines(scope.row.pkg)"
                :key="line.id"
                :value="line.id"
                :label="lineLabel(line)"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="65"
          ><template #default="scope"
            ><el-button link type="danger" @click="removePackage(scope.row.pkg.id)"
              ><Icon icon="ep:delete" /></el-button></template
        ></el-table-column>
      </el-table>
    </ContentWrap>
  </template>

  <ContentWrap v-else-if="tab === 'tasks'">
    <el-form :inline="true" :model="taskQuery">
      <el-form-item label="任务号"
        ><el-input v-model="taskQuery.taskNo" clearable @keyup.enter="loadTasks"
      /></el-form-item>
      <el-form-item label="提货单号"
        ><el-input v-model="taskQuery.pickupSlipNo" clearable @keyup.enter="loadTasks"
      /></el-form-item>
      <el-form-item label="状态">
        <el-select v-model="taskQuery.status" clearable class="!w-150px">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item
        ><el-button @click="loadTasks"
          ><Icon icon="ep:search" class="mr-5px" />查询</el-button
        ></el-form-item
      >
    </el-form>
    <el-table v-loading="taskLoading" :data="tasks">
      <el-table-column prop="taskNo" label="任务号" min-width="190" />
      <el-table-column prop="pickupSlipNo" label="提货单号" min-width="150" />
      <el-table-column prop="customerName" label="客户" min-width="160" />
      <el-table-column prop="vehiclePlate" label="车牌号" width="110" />
      <el-table-column prop="forkliftDriverName" label="叉车司机" width="100" />
      <el-table-column label="状态" width="100"
        ><template #default="scope">{{ statusLabel(scope.row.status) }}</template></el-table-column
      >
      <el-table-column label="进度" width="170">
        <template #default="scope"
          ><el-progress :percentage="progress(scope.row)" :stroke-width="8"
        /></template>
      </el-table-column>
      <el-table-column label="操作" width="90"
        ><template #default="scope"
          ><el-button link type="primary" @click="openTask(scope.row.id)">详情</el-button></template
        ></el-table-column
      >
    </el-table>
    <Pagination
      v-model:page="taskQuery.pageNo"
      v-model:limit="taskQuery.pageSize"
      :total="taskTotal"
      @pagination="loadTasks"
    />
  </ContentWrap>

  <template v-else>
    <ContentWrap title="库管代表码扫码复核">
      <div class="scan-banner">
        <div>
          <div class="scan-title">扫一包，带出整车任务</div>
          <div class="scan-desc">
            系统按包码识别所属装车任务，库管核对客户、库位、包件后批量确认；每包二维码和扫码明细仍独立保留。
          </div>
        </div>
        <el-tag type="success">一包一码 + 批次代表扫码</el-tag>
      </div>
      <el-form :inline="true" :model="batchScanForm" @submit.prevent="previewBatchScan">
        <el-form-item label="包码/二维码链接">
          <el-input
            v-model="batchScanForm.packageCode"
            clearable
            class="!w-420px"
            placeholder="扫描任意一包，支持 LD... 或 https://id.tjldsic.com/pkg/LD..."
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="batchScanLoading"
            :disabled="!batchScanForm.packageCode"
            v-hasPermi="['rd:loading-task:dispatch']"
            @click="previewBatchScan"
          >
            <Icon icon="ep:scan" class="mr-5px" />识别任务
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert
        v-if="batchScanResp"
        class="mb-12px"
        :type="batchScanResp.success ? 'success' : 'warning'"
        :closable="false"
        :title="
          batchScanResp.task.status === 'OUTBOUNDED'
            ? '该任务已完成出库，逐包明细只读，不能重复确认或过账'
            : batchScanResp.message
        "
      />
      <template v-if="batchScanResp">
        <el-descriptions :column="4" border>
          <el-descriptions-item label="任务号">{{
            batchScanResp.task.taskNo
          }}</el-descriptions-item>
          <el-descriptions-item label="提货单">{{
            batchScanResp.task.pickupSlipNo
          }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{
            batchScanResp.task.customerName
          }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{
            statusLabel(batchScanResp.task.status)
          }}</el-descriptions-item>
          <el-descriptions-item label="车牌号">{{
            batchScanResp.task.vehiclePlate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="叉车司机">{{
            batchScanResp.task.forkliftDriverName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="已扫 / 计划"
            >{{ batchScanResp.task.scannedPackageCount }} /
            {{ batchScanResp.task.plannedPackageCount }}</el-descriptions-item
          >
          <el-descriptions-item label="代表包码">{{
            batchScanResp.representativePackageCode
          }}</el-descriptions-item>
        </el-descriptions>
        <div class="scan-actions">
          <span>
            已选择 {{ batchSelectedLineIds.length }} 包，未确认 {{ pendingScanLines.length }} 包
          </span>
          <el-button
            type="success"
            :loading="batchScanConfirming"
            :disabled="
              !batchSelectedLineIds.length ||
              !['ASSIGNED', 'LOADING'].includes(batchScanResp.task.status)
            "
            v-hasPermi="['rd:loading-task:dispatch']"
            @click="confirmBatchScan"
          >
            <Icon icon="ep:circle-check" class="mr-5px" />确认已装包件
          </el-button>
          <el-button
            type="danger"
            :loading="outboundConfirming"
            :disabled="batchScanResp.task.status !== 'PENDING_REVIEW'"
            v-hasPermi="['rd:loading-task:dispatch']"
            @click="confirmOutbound"
          >
            <Icon icon="ep:finished" class="mr-5px" />确认出库过账
          </el-button>
        </div>
        <el-table
          ref="batchLineTableRef"
          :data="batchScanResp.lines"
          row-key="id"
          max-height="430"
          @selection-change="onBatchLineSelection"
        >
          <el-table-column type="selection" width="48" :selectable="batchLineSelectable" />
          <el-table-column prop="plannedLocationCode" label="库位" width="110" />
          <el-table-column prop="packageCode" label="包码" min-width="170" />
          <el-table-column prop="customerName" label="客户" min-width="150" />
          <el-table-column prop="productModel" label="型号" width="110" />
          <el-table-column prop="furnaceNo" label="炉号" width="120" />
          <el-table-column prop="furnaceLayer" label="炉层" width="90" />
          <el-table-column prop="packageWeight" label="重量 kg" width="100" align="right" />
          <el-table-column label="状态" width="105">
            <template #default="scope">{{ lineStatusLabel(scope.row.lineStatus) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </ContentWrap>
  </template>

  <Dialog v-model="taskVisible" title="装车任务详情" width="960px">
    <template v-if="taskDetail">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="任务号">{{ taskDetail.task.taskNo }}</el-descriptions-item>
        <el-descriptions-item label="提货单号">{{
          taskDetail.task.pickupSlipNo
        }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{
          statusLabel(taskDetail.task.status)
        }}</el-descriptions-item>
        <el-descriptions-item label="车牌号">{{
          taskDetail.task.vehiclePlate || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="叉车司机">{{
          taskDetail.task.forkliftDriverName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="已扫 / 计划"
          >{{ taskDetail.task.scannedPackageCount }} /
          {{ taskDetail.task.plannedPackageCount }}</el-descriptions-item
        >
      </el-descriptions>
      <el-table :data="taskDetail.lines" class="mt-16px" max-height="390">
        <el-table-column prop="lineNo" label="序号" width="65" />
        <el-table-column prop="plannedLocationCode" label="库位" width="115" />
        <el-table-column prop="packageCode" label="包码" min-width="160" />
        <el-table-column prop="productModel" label="型号" width="110" />
        <el-table-column prop="furnaceNo" label="炉号" width="115" />
        <el-table-column prop="furnaceLayer" label="炉层" width="90" />
        <el-table-column prop="packageWeight" label="重量 kg" width="100" />
        <el-table-column prop="lineStatus" label="状态" width="105" />
      </el-table>
      <el-alert
        v-if="openTaskExceptions.length"
        class="mt-16px"
        type="warning"
        :closable="false"
        show-icon
        :title="`该装车任务有 ${openTaskExceptions.length} 条待处理异常`"
      />
      <el-table
        v-if="taskDetail.exceptions?.length"
        :data="taskDetail.exceptions"
        class="mt-12px"
        max-height="240"
      >
        <el-table-column prop="exceptionNo" label="异常号" min-width="160" />
        <el-table-column label="原因" width="130">
          <template #default="scope">{{ exceptionTypeLabel(scope.row.exceptionType) }}</template>
        </el-table-column>
        <el-table-column prop="packageCode" label="包码" min-width="160" />
        <el-table-column prop="observedLocationCode" label="现场库位" width="110" />
        <el-table-column prop="description" label="异常说明" min-width="220" />
        <el-table-column label="状态" width="100">
          <template #default="scope">{{
            scope.row.exceptionStatus === 'OPEN' ? '待处理' : '已处理'
          }}</template>
        </el-table-column>
        <el-table-column prop="handlingResult" label="处理说明" min-width="220" />
      </el-table>
      <div v-if="taskDetail.task.status === 'EXCEPTION_PENDING'" class="exception-actions">
        <el-button type="warning" plain @click="openResolveExceptionDialog('REPLACE_PACKAGE')">
          替换包后继续
        </el-button>
        <el-button type="primary" plain @click="openResolveExceptionDialog('RETURN_TO_DRIVER')">
          退回司机继续执行
        </el-button>
        <el-button type="danger" plain @click="openResolveExceptionDialog('CANCEL')">
          取消任务并释放占用
        </el-button>
      </div>
    </template>
  </Dialog>

  <Dialog v-model="exceptionResolveVisible" title="处理装车异常" width="560px">
    <el-form label-width="96px">
      <el-form-item label="处理动作">
        <el-radio-group v-model="exceptionResolveForm.resolutionAction">
          <el-radio-button label="REPLACE_PACKAGE">替换包后继续</el-radio-button>
          <el-radio-button label="RETURN_TO_DRIVER">退回司机继续执行</el-radio-button>
          <el-radio-button label="CANCEL">取消任务</el-radio-button>
        </el-radio-group>
      </el-form-item>
      <template v-if="exceptionResolveForm.resolutionAction === 'REPLACE_PACKAGE'">
        <el-form-item label="原任务包" required>
          <el-select
            v-model="exceptionResolveForm.taskLineId"
            filterable
            class="!w-full"
            placeholder="选择需要替换的原计划包"
          >
            <el-option
              v-for="line in replaceableTaskLines"
              :key="line.id"
              :value="line.id"
              :label="`${line.packageCode} · ${line.plannedLocationCode} · ${line.productModel} ${line.furnaceLayer || ''}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="替换包码" required>
          <el-input
            v-model="exceptionResolveForm.newPackageCode"
            clearable
            placeholder="扫描或填写新包二维码链接/包码"
          />
        </el-form-item>
      </template>
      <el-form-item label="处理说明" required>
        <el-input
          v-model="exceptionResolveForm.handlingResult"
          type="textarea"
          :rows="4"
          maxlength="1000"
          show-word-limit
          placeholder="填写核实结果，例如已通知司机继续执行、现场货位不符需取消后重新派单等"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="exceptionResolveVisible = false">取消</el-button>
      <el-button type="primary" :loading="exceptionResolving" @click="resolveLoadingException">
        确认处理
      </el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { useWarehouseSession } from '../../pda/warehouse/useWarehouseSession'
import { useRoute } from 'vue-router'
import { hasPermission } from '@/directives/permission/hasPermi'
import {
  RdLoadingApi,
  type LoadingBatchScanResp,
  type LoadingException,
  type LoadingPackage,
  type LoadingTask,
  type LoadingTaskDetail,
  type LoadingTaskLine,
  type LoadingDriverOption,
  type PickupSlip,
  type PickupSlipLine
} from '@/api/rd/loading'
import { RdPutawayTaskApi, type RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'

defineOptions({ name: 'RdLoadingDispatch' })
const route = useRoute()
const warehouseSession = useWarehouseSession()
let batchPreviewRevision = 0
const message = useMessage()
const tab = ref('dispatch')
const refreshing = ref(false)
const slipId = ref<number>()
const slips = ref<PickupSlip[]>([])
const slipDetail = ref<{ slip: PickupSlip; lines: PickupSlipLine[] }>()
const driverId = ref<number>()
const drivers = ref<LoadingDriverOption[]>([])
const locations = ref<RdPutawayLocationMapVO[]>([])
const filterLineId = ref<number>()
const packageQuery = reactive({
  pageNo: 1,
  pageSize: 100,
  customerName: '',
  saleOrderId: undefined as number | undefined,
  productModel: '',
  furnaceNo: '',
  furnaceLayer: '',
  locationId: undefined as number | undefined,
  keyword: ''
})
const packages = ref<LoadingPackage[]>([])
const packageTotal = ref(0)
const packageLoading = ref(false)
const currentSelection = ref<LoadingPackage[]>([])
const selected = ref<Array<{ pkg: LoadingPackage; pickupSlipLineId?: number }>>([])
const dispatching = ref(false)
const tasks = ref<LoadingTask[]>([])
const taskQuery = reactive({ pageNo: 1, pageSize: 20, taskNo: '', pickupSlipNo: '', status: '' })
const taskTotal = ref(0)
const taskLoading = ref(false)
const taskVisible = ref(false)
const taskDetail = ref<LoadingTaskDetail>()
const exceptionResolveVisible = ref(false)
const exceptionResolving = ref(false)
const exceptionResolveForm = reactive({
  resolutionAction: 'RETURN_TO_DRIVER',
  handlingResult: '',
  taskLineId: undefined as number | undefined,
  newPackageCode: ''
})
const batchScanForm = reactive({
  taskId: undefined as number | undefined,
  packageCode: ''
})
const batchScanLoading = ref(false)
const batchScanConfirming = ref(false)
const outboundConfirming = ref(false)
const batchScanResp = ref<LoadingBatchScanResp>()
const batchSelectedLineIds = ref<number[]>([])
const batchLineTableRef = ref()
const statusOptions = [
  { value: 'ASSIGNED', label: '已派单' },
  { value: 'LOADING', label: '装车中' },
  { value: 'PENDING_REVIEW', label: '待复核' },
  { value: 'EXCEPTION_PENDING', label: '异常待处理' },
  { value: 'OUTBOUNDED', label: '已出库' },
  { value: 'CANCELLED', label: '已取消' }
]
const statusLabel = (status: string) =>
  statusOptions.find((item) => item.value === status)?.label || status
const lineStatusOptions: Record<string, string> = {
  PLANNED: '待确认',
  SCANNED: '已扫码',
  LOADED: '已装车',
  OUTBOUNDED: '已出库',
  EXCEPTION: '异常',
  CANCELLED: '已取消'
}
const lineStatusLabel = (status: string) => lineStatusOptions[status] || status
const exceptionTypeOptions: Record<string, string> = {
  MISSING_PACKAGE: '现场缺货',
  LOCATION_MISMATCH: '库位不符',
  WRONG_PACKAGE: '错货',
  LABEL_DAMAGED: '标签破损',
  VEHICLE_CHANGED: '车辆变更',
  OTHER: '其他'
}
const exceptionTypeLabel = (type: string) => exceptionTypeOptions[type] || type
const progress = (task: LoadingTask) =>
  task.plannedPackageCount
    ? Math.round((task.scannedPackageCount / task.plannedPackageCount) * 100)
    : 0
const selectedWeight = computed(() =>
  selected.value.reduce((sum, row) => sum + Number(row.pkg.packageWeight), 0).toFixed(3)
)
const selectedLocations = computed(
  () => new Set(selected.value.map((row) => row.pkg.locationId)).size
)
const lineLabel = (line: PickupSlipLine) =>
  `${line.lineNo}. ${line.productModel} ${line.furnaceLayer || ''} ${line.requestedPackageCount ?? '-'}包`
const matchingLines = (pkg: LoadingPackage) =>
  (slipDetail.value?.lines || []).filter(
    (line) =>
      line.productModel === pkg.productModel &&
      (!line.furnaceNo || line.furnaceNo === pkg.furnaceNo) &&
      (!line.furnaceLayer || line.furnaceLayer === pkg.furnaceLayer)
  )
const pendingScanLines = computed(
  () => batchScanResp.value?.lines.filter((line) => line.lineStatus === 'PLANNED') || []
)
const openTaskExceptions = computed<LoadingException[]>(
  () => taskDetail.value?.exceptions?.filter((item) => item.exceptionStatus === 'OPEN') || []
)
const replaceableTaskLines = computed(
  () =>
    taskDetail.value?.lines.filter((line) => ['PLANNED', 'EXCEPTION'].includes(line.lineStatus)) ||
    []
)
const batchLineSelectable = (row: LoadingTaskLine) => row.lineStatus === 'PLANNED'
const createIdempotencyKey = () =>
  globalThis.crypto?.randomUUID?.() ||
  `loading-batch-${Date.now()}-${Math.random().toString(16).slice(2)}`

const searchSlips = async (term: string) => {
  const page = await RdLoadingApi.getPickupSlipPage({
    pageNo: 1,
    pageSize: 100,
    slipNo: term || undefined,
    status: 'ISSUED'
  })
  slips.value = page.list
}
const selectSlip = async () => {
  selected.value = []
  slipDetail.value = slipId.value ? await RdLoadingApi.getPickupSlip(slipId.value) : undefined
  packageQuery.customerName = slipDetail.value?.slip.customerName || ''
  filterLineId.value = undefined
  applyLineFilter()
}
const applyLineFilter = () => {
  const line = slipDetail.value?.lines.find((item) => item.id === filterLineId.value)
  packageQuery.saleOrderId = line?.saleOrderId
  packageQuery.productModel = line?.productModel || ''
  packageQuery.furnaceNo = line?.furnaceNo || ''
  packageQuery.furnaceLayer = line?.furnaceLayer || ''
  packageQuery.pageNo = 1
  loadPackages()
}
const loadPackages = async () => {
  if (!slipDetail.value) {
    packages.value = []
    packageTotal.value = 0
    return
  }
  packageLoading.value = true
  try {
    const page = await RdLoadingApi.getAvailablePackagePage(packageQuery)
    packages.value = page.list
    packageTotal.value = page.total
  } finally {
    packageLoading.value = false
  }
}
const onSelectionChange = (rows: LoadingPackage[]) => {
  currentSelection.value = rows
}
const addPackages = () => {
  for (const pkg of currentSelection.value) {
    if (selected.value.some((row) => row.pkg.id === pkg.id)) continue
    const matches = matchingLines(pkg)
    selected.value.push({ pkg, pickupSlipLineId: matches.length === 1 ? matches[0].id : undefined })
  }
}
const removePackage = (id: number) => {
  selected.value = selected.value.filter((row) => row.pkg.id !== id)
}
const dispatch = async () => {
  if (!slipId.value || !driverId.value || !selected.value.length) return
  if (selected.value.some((row) => !row.pickupSlipLineId)) {
    message.warning('请为每包选择对应的提货明细')
    return
  }
  dispatching.value = true
  try {
    await RdLoadingApi.createTask({
      pickupSlipId: slipId.value,
      forkliftDriverId: driverId.value,
      selections: selected.value.map((row) => ({
        packageId: row.pkg.id,
        pickupSlipLineId: row.pickupSlipLineId!
      }))
    })
    message.success('装车任务已派给叉车司机')
    selected.value = []
    await loadPackages()
    tab.value = 'tasks'
    await loadTasks()
  } finally {
    dispatching.value = false
  }
}
const loadTasks = async () => {
  taskLoading.value = true
  try {
    const page = await RdLoadingApi.getTaskPage(taskQuery)
    tasks.value = page.list
    taskTotal.value = page.total
  } finally {
    taskLoading.value = false
  }
}
const openTask = async (id: number) => {
  taskDetail.value = await RdLoadingApi.getTask(id)
  taskVisible.value = true
}
const openResolveExceptionDialog = (
  resolutionAction: 'REPLACE_PACKAGE' | 'RETURN_TO_DRIVER' | 'CANCEL'
) => {
  exceptionResolveForm.resolutionAction = resolutionAction
  const firstOpenException = openTaskExceptions.value.find((item) => item.taskLineId)
  exceptionResolveForm.taskLineId =
    resolutionAction === 'REPLACE_PACKAGE'
      ? firstOpenException?.taskLineId || replaceableTaskLines.value[0]?.id
      : undefined
  exceptionResolveForm.newPackageCode = ''
  exceptionResolveForm.handlingResult =
    resolutionAction === 'REPLACE_PACKAGE'
      ? '仓管员已核实异常，使用同客户同型号替换包，退回司机继续执行。'
      : resolutionAction === 'RETURN_TO_DRIVER'
        ? '仓管员已核实现场异常，退回司机按任务继续搬运。'
        : '仓管员已核实现场异常，取消本次装车任务并释放未出库包件占用。'
  exceptionResolveVisible.value = true
}
const resolveLoadingException = async () => {
  if (!taskDetail.value) return
  const handlingResult = exceptionResolveForm.handlingResult.trim()
  if (!handlingResult) {
    message.warning('请填写处理说明')
    return
  }
  if (exceptionResolveForm.resolutionAction === 'REPLACE_PACKAGE') {
    if (!exceptionResolveForm.taskLineId || !exceptionResolveForm.newPackageCode.trim()) {
      message.warning('请选择原任务包并扫描替换包码')
      return
    }
  }
  exceptionResolving.value = true
  const taskId = taskDetail.value.task.id
  try {
    await RdLoadingApi.resolveException({
      taskId,
      version: taskDetail.value.task.version || 0,
      resolutionAction: exceptionResolveForm.resolutionAction,
      handlingResult,
      replacements:
        exceptionResolveForm.resolutionAction === 'REPLACE_PACKAGE'
          ? [
              {
                taskLineId: exceptionResolveForm.taskLineId!,
                newPackageCode: exceptionResolveForm.newPackageCode.trim()
              }
            ]
          : undefined
    })
    message.success('装车异常已处理')
    exceptionResolveVisible.value = false
    await openTask(taskId)
    await loadTasks()
    if (batchScanResp.value?.task.id === taskId && batchScanForm.packageCode) {
      await previewBatchScan()
    }
  } finally {
    exceptionResolving.value = false
  }
}
const onBatchLineSelection = (rows: LoadingTaskLine[]) => {
  batchSelectedLineIds.value = rows.map((row) => row.id)
}
const applyDefaultBatchSelection = async () => {
  await nextTick()
  batchSelectedLineIds.value = []
  batchLineTableRef.value?.clearSelection?.()
  for (const line of pendingScanLines.value) {
    batchLineTableRef.value?.toggleRowSelection?.(line, true)
  }
}
const previewBatchScan = async () => {
  if (
    !warehouseSession.allowed('rd:loading-task:dispatch') ||
    !batchScanForm.packageCode ||
    batchScanConfirming.value ||
    outboundConfirming.value
  )
    return
  const requestGeneration = ++batchPreviewRevision
  batchScanLoading.value = true
  const packageCode = batchScanForm.packageCode.trim()
  const scannedCode = packageCode.split(/[?#]/)[0].replace(/\/+$/, '').split('/').pop()
  const previousTaskId =
    scannedCode?.toUpperCase() === batchScanResp.value?.representativePackageCode?.toUpperCase()
      ? batchScanResp.value?.task.id
      : undefined
  try {
    const result = await RdLoadingApi.previewBatchScan({
      taskId: batchScanForm.taskId ?? previousTaskId,
      packageCode,
      deviceCode: 'WEB_KEEPER'
    })
    if (
      !warehouseSession.current() ||
      requestGeneration !== batchPreviewRevision ||
      outboundConfirming.value
    )
      return
    batchScanResp.value = result
    await applyDefaultBatchSelection()
  } catch {
    if (
      warehouseSession.current() &&
      requestGeneration === batchPreviewRevision &&
      !outboundConfirming.value
    ) {
      batchScanResp.value = undefined
      batchSelectedLineIds.value = []
    }
  } finally {
    if (requestGeneration === batchPreviewRevision) batchScanLoading.value = false
  }
}
const confirmBatchScan = async () => {
  if (
    !warehouseSession.allowed('rd:loading-task:dispatch') ||
    batchScanLoading.value ||
    batchScanConfirming.value ||
    outboundConfirming.value ||
    !batchScanResp.value ||
    !batchSelectedLineIds.value.length
  )
    return
  const version = batchScanResp.value.task.version
  if (typeof version !== 'number' || !Number.isSafeInteger(version))
    return message.warning('任务版本缺失，请重新读取任务')
  batchScanConfirming.value = true
  try {
    batchScanResp.value = await RdLoadingApi.confirmBatchScan({
      taskId: batchScanResp.value.task.id,
      version,
      representativePackageCode:
        batchScanResp.value.representativePackageCode || batchScanForm.packageCode.trim(),
      idempotencyKey: createIdempotencyKey(),
      taskLineIds: batchSelectedLineIds.value,
      deviceCode: 'WEB_KEEPER',
      deviceTime: new Date().toISOString()
    })
    message.success(batchScanResp.value.message || '装车扫码已确认')
    await applyDefaultBatchSelection()
    await loadTasks()
  } finally {
    batchScanConfirming.value = false
  }
}
const confirmOutbound = async () => {
  if (
    !warehouseSession.allowed('rd:loading-task:dispatch') ||
    batchScanLoading.value ||
    batchScanConfirming.value ||
    outboundConfirming.value ||
    !batchScanResp.value ||
    batchScanResp.value.task.status !== 'PENDING_REVIEW'
  )
    return
  const version = batchScanResp.value.task.version
  if (typeof version !== 'number' || !Number.isSafeInteger(version))
    return message.warning('任务版本缺失，请重新读取任务')
  const target = { taskId: batchScanResp.value.task.id, version }
  const taskNo = batchScanResp.value.task.taskNo
  const packageCount = batchScanResp.value.task.scannedPackageCount
  const representative =
    batchScanResp.value.representativePackageCode || batchScanForm.packageCode.trim()
  const generation = batchPreviewRevision
  const sessionGeneration = warehouseSession.generation()
  const routeContext = route.fullPath
  outboundConfirming.value = true
  try {
    await message.confirm(
      `确认将装车任务 ${taskNo} 的 ${packageCount} 包正式出库过账吗？过账后将扣减库存并释放库位。`
    )
    warehouseSession.assertCurrent()
    if (
      !warehouseSession.allowed('rd:loading-task:dispatch') ||
      warehouseSession.generation() !== sessionGeneration ||
      generation !== batchPreviewRevision ||
      route.fullPath !== routeContext ||
      batchScanResp.value?.task.id !== target.taskId ||
      batchScanResp.value?.task.version !== target.version ||
      batchScanResp.value?.task.status !== 'PENDING_REVIEW'
    )
      throw new Error('任务或页面已变化，请重新核定出库对象')
    const result = await RdLoadingApi.confirmOutbound(target)
    warehouseSession.assertCurrent()
    if (result?.taskId !== target.taskId) throw new Error('出库返回的任务不一致，请核对原任务')
    const verified = await RdLoadingApi.previewBatchScan({
      taskId: target.taskId,
      packageCode: representative,
      deviceCode: 'WEB_KEEPER'
    })
    warehouseSession.assertCurrent()
    if (verified.task.id !== target.taskId || verified.task.status !== 'OUTBOUNDED')
      throw new Error('实际出库状态待核，请重新读取')
    batchScanResp.value = verified
    message.success(result.message || '装车任务已确认出库')
    await applyDefaultBatchSelection()
    await loadTasks()
  } catch (cause) {
    if (cause !== 'cancel' && cause !== 'close')
      message.warning(cause instanceof Error ? cause.message : '出库结果待核，请重新读取任务')
  } finally {
    outboundConfirming.value = false
  }
}
const refresh = async () => {
  refreshing.value = true
  try {
    if (tab.value === 'dispatch')
      await Promise.all([searchSlips(''), loadPackages(), loadLocations()])
    else if (tab.value === 'tasks') await loadTasks()
    else if (batchScanForm.packageCode) await previewBatchScan()
  } finally {
    refreshing.value = false
  }
}
const loadLocations = async () => {
  locations.value = await RdPutawayTaskApi.getLocationMap()
}
const getRouteString = (key: string) => {
  const value = route.query[key]
  return Array.isArray(value) ? value[0] || '' : String(value || '')
}
const applyRouteScanQuery = async () => {
  const packageCode = getRouteString('packageCode')
  const locationCode = getRouteString('locationCode')
  const taskId = Number(getRouteString('taskId'))
  batchScanForm.taskId = Number.isFinite(taskId) && taskId > 0 ? taskId : undefined
  if (
    batchScanForm.taskId &&
    (getRouteString('mode') === 'view' || !hasPermission(['rd:loading-task:dispatch']))
  ) {
    tab.value = 'tasks'
    await openTask(batchScanForm.taskId)
    return
  }
  if (!packageCode && !locationCode) return
  tab.value = packageCode ? 'batchScan' : 'dispatch'
  if (packageCode) {
    packageQuery.keyword = packageCode
    batchScanForm.packageCode = packageCode
    packageQuery.pageNo = 1
    previewBatchScan()
  }
  if (locationCode) {
    const matched = locations.value.find((item) => item.locationCode === locationCode)
    if (matched) packageQuery.locationId = matched.id
  }
}
onMounted(async () => {
  await Promise.all([
    hasPermission(['rd:pickup-slip:query']) ? searchSlips('') : Promise.resolve(),
    hasPermission(['rd:putaway-task:query']) ? loadLocations() : Promise.resolve(),
    hasPermission(['rd:loading-task:dispatch'])
      ? RdLoadingApi.getDriverOptions().then((data) => {
          drivers.value = data
        })
      : Promise.resolve(),
    loadTasks()
  ])
  await applyRouteScanQuery()
})

watch(
  () => [route.query.packageCode, route.query.locationCode, route.query.taskId],
  () => applyRouteScanQuery()
)
</script>

<style scoped>
.heading,
.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.heading h2 {
  margin: 0;
  font-size: 18px;
}

.selected-summary {
  margin-bottom: 12px;
  font-weight: 600;
}

.scan-banner,
.scan-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.scan-banner {
  padding: 14px 16px;
  margin-bottom: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
}

.scan-title {
  font-weight: 700;
}

.scan-desc {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
}

.scan-actions {
  margin: 14px 0;
  font-weight: 600;
}

.exception-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 14px;
}

@media (width <= 720px) {
  .heading,
  .table-footer,
  .scan-banner,
  .scan-actions,
  .exception-actions {
    flex-wrap: wrap;
  }
}
</style>
