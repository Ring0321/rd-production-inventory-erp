<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="queryParams" inline>
      <el-form-item label="盘点单号" prop="stocktakeNo">
        <el-input
          v-model="queryParams.stocktakeNo"
          clearable
          class="!w-220px"
          placeholder="请输入盘点单号"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="货位" prop="locationCode">
        <el-input
          v-model="queryParams.locationCode"
          clearable
          class="!w-180px"
          placeholder="请输入货位编码"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable class="!w-160px" placeholder="全部">
          <el-option
            v-for="item in stocktakeStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleQuery">
          <Icon icon="ep:search" class="mr-5px" />查询
        </el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          v-hasPermi="['rd:stocktake:create']"
          @click="createDialogRef?.open()"
        >
          <Icon icon="ep:plus" class="mr-5px" />新建盘点单
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe show-overflow-tooltip>
      <el-table-column prop="stocktakeNo" label="盘点单号" min-width="180" fixed="left" />
      <el-table-column prop="stocktakeDate" label="盘点日期" width="110" />
      <el-table-column prop="locationCode" label="盘点货位" width="110" />
      <el-table-column label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="stocktakeStatusMap[scope.row.status]?.type || 'info'">
            {{ stocktakeStatusMap[scope.row.status]?.label || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="snapshotTime" label="冻结快照" width="170" :formatter="dateFormatter" />
      <el-table-column label="应有包数" width="100" align="right">
        <template #default="scope">{{ formatMetric(scope.row.bookPackageQty) }}</template>
      </el-table-column>
      <el-table-column label="实扫包数" width="100" align="right">
        <template #default="scope">{{ formatMetric(scope.row.countedPackageQty) }}</template>
      </el-table-column>
      <el-table-column label="短少" width="90" align="right">
        <template #default="scope">
          <span class="difference-shortage">{{ formatMetric(scope.row.shortageQty) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="盘盈" width="90" align="right">
        <template #default="scope">
          <span class="difference-surplus">{{ formatMetric(scope.row.surplusQty) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="应有重量(kg)" width="120" align="right">
        <template #default="scope">{{ formatMetric(scope.row.bookWeight, 3) }}</template>
      </el-table-column>
      <el-table-column label="实扫重量(kg)" width="120" align="right">
        <template #default="scope">{{ formatMetric(scope.row.countedWeight, 3) }}</template>
      </el-table-column>
      <el-table-column prop="submitterName" label="提交人" width="100" />
      <el-table-column prop="submitTime" label="提交时间" width="170" :formatter="dateFormatter" />
      <el-table-column prop="reviewerName" label="复核人" width="100" />
      <el-table-column prop="reviewTime" label="复核时间" width="170" :formatter="dateFormatter" />
      <el-table-column prop="adjusterName" label="调整人" width="100" />
      <el-table-column prop="adjustTime" label="调整时间" width="170" :formatter="dateFormatter" />
      <el-table-column prop="closerName" label="关闭人" width="100" />
      <el-table-column prop="closeTime" label="关闭时间" width="170" :formatter="dateFormatter" />
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="操作" min-width="430" fixed="right" align="center">
        <template #default="scope">
          <el-button link type="primary" @click="detailDrawerRef?.open(scope.row.id)">详情</el-button>
          <el-button link type="primary" @click="differenceDialogRef?.open(scope.row.id)">
            差异
          </el-button>
          <el-button
            link
            type="warning"
            :disabled="!canFreeze(scope.row.status)"
            v-hasPermi="['rd:stocktake:freeze']"
            @click="handleFreeze(scope.row)"
          >
            冻结
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="!canScan(scope.row.status)"
            v-hasPermi="['rd:stocktake:scan']"
            @click="scanDialogRef?.open(scope.row.id)"
          >
            扫码实盘
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="!canSubmit(scope.row.status)"
            v-hasPermi="['rd:stocktake:submit']"
            @click="handleSubmit(scope.row)"
          >
            提交
          </el-button>
          <el-button
            link
            type="success"
            :disabled="!canApprove(scope.row.status)"
            v-hasPermi="['rd:stocktake:approve']"
            @click="handleApprove(scope.row)"
          >
            双人审核
          </el-button>
          <el-button
            link
            type="success"
            :disabled="!canAdjust(scope.row.status)"
            v-hasPermi="['rd:stocktake:adjust']"
            @click="handleAdjust(scope.row)"
          >
            差异调整
          </el-button>
          <el-button
            link
            type="info"
            :disabled="!canClose(scope.row.status)"
            v-hasPermi="['rd:stocktake:close']"
            @click="handleClose(scope.row)"
          >
            关闭
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="!canCancel(scope.row.status)"
            v-hasPermi="['rd:stocktake:cancel']"
            @click="handleCancel(scope.row)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      :total="total"
      @pagination="load"
    />
  </ContentWrap>

  <StocktakeCreateDialog ref="createDialogRef" @success="load" />
  <StocktakeScanDialog ref="scanDialogRef" @success="load" />
  <StocktakeDetailDrawer ref="detailDrawerRef" />
  <StocktakeDifferenceDialog ref="differenceDialogRef" />
</template>

<script setup lang="ts">
import type { FormInstance } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { dateFormatter } from '@/utils/formatTime'
import { RdStocktakeApi, type RdStocktakeOrderVO } from '@/api/rd/stock/stocktake'
import StocktakeCreateDialog from './StocktakeCreateDialog.vue'
import StocktakeDetailDrawer from './StocktakeDetailDrawer.vue'
import StocktakeDifferenceDialog from './StocktakeDifferenceDialog.vue'
import StocktakeScanDialog from './StocktakeScanDialog.vue'
import {
  canAdjust,
  canApprove,
  canCancel,
  canClose,
  canFreeze,
  canScan,
  canSubmit,
  formatMetric,
  stocktakeStatusMap,
  stocktakeStatusOptions
} from './helpers'

defineOptions({ name: 'RdStocktake' })

const message = useMessage()
const loading = ref(false)
const total = ref(0)
const list = ref<RdStocktakeOrderVO[]>([])
const queryFormRef = ref<FormInstance>()
const createDialogRef = ref<InstanceType<typeof StocktakeCreateDialog>>()
const scanDialogRef = ref<InstanceType<typeof StocktakeScanDialog>>()
const detailDrawerRef = ref<InstanceType<typeof StocktakeDetailDrawer>>()
const differenceDialogRef = ref<InstanceType<typeof StocktakeDifferenceDialog>>()

const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  stocktakeNo: undefined as string | undefined,
  status: undefined as string | undefined,
  locationCode: undefined as string | undefined
})

const load = async () => {
  loading.value = true
  try {
    const data = await RdStocktakeApi.getPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNo = 1
  load()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  queryParams.pageNo = 1
  queryParams.pageSize = 10
  load()
}

const handleFreeze = async (row: RdStocktakeOrderVO) => {
  await message.confirm(`确认冻结货位 ${row.locationCode} 并生成盘点快照 ${row.stocktakeNo} 吗？`)
  await RdStocktakeApi.freeze({ id: row.id, version: row.version })
  message.success('盘点单已冻结')
  await load()
}

const handleSubmit = async (row: RdStocktakeOrderVO) => {
  await message.confirm(`确认提交盘点单 ${row.stocktakeNo} 进入双人复核吗？`)
  await RdStocktakeApi.submit({ id: row.id, version: row.version })
  message.success('盘点单已提交')
  await load()
}

const handleApprove = async (row: RdStocktakeOrderVO) => {
  await message.confirm(`确认完成盘点单 ${row.stocktakeNo} 的双人审核吗？`)
  await RdStocktakeApi.approve({ id: row.id, version: row.version })
  message.success('盘点单已复核')
  await load()
}

const handleAdjust = async (row: RdStocktakeOrderVO) => {
  await message.confirm(`确认按差异结果调整库存并过账盘点单 ${row.stocktakeNo} 吗？`)
  await RdStocktakeApi.adjust({ id: row.id, version: row.version })
  message.success('差异调整已完成')
  await load()
}

const handleClose = async (row: RdStocktakeOrderVO) => {
  await message.confirm(`确认关闭盘点单 ${row.stocktakeNo} 吗？关闭后本次盘点流程结束。`)
  await RdStocktakeApi.close({ id: row.id, version: row.version })
  message.success('盘点单已关闭')
  await load()
}

const handleCancel = async (row: RdStocktakeOrderVO) => {
  await message.confirm(`确认取消盘点单 ${row.stocktakeNo} 吗？`)
  await RdStocktakeApi.cancel({ id: row.id, version: row.version })
  message.success('盘点单已取消')
  await load()
}

onMounted(load)
</script>

<style scoped>
.difference-shortage {
  color: var(--el-color-danger);
  font-weight: 600;
}

.difference-surplus {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
