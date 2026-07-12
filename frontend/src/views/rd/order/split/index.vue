<template>
  <ContentWrap>
    <el-tabs v-model="activeTab" class="order-split-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="拆分执行" name="execution" />
      <el-tab-pane label="规则配置" name="rule" v-if="hasRuleAccess" />
    </el-tabs>

    <el-form
      v-if="activeTab === 'execution'"
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="拆分日期" prop="splitDate">
        <el-date-picker
          v-model="queryParams.splitDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="拆分单号" prop="splitNo">
        <el-input
          v-model="queryParams.splitNo"
          clearable
          placeholder="请输入拆分单号"
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="订单号" prop="saleOrderNo">
        <el-input
          v-model="queryParams.saleOrderNo"
          clearable
          placeholder="请输入订单号"
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <RdCustomerProductFields
        v-model:customer-name="queryParams.customerName"
        v-model:product-model="queryParams.productModel"
        query-mode
      />
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" clearable placeholder="请选择状态" class="!w-240px">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button
          type="primary"
          plain
          @click="createDialogRef?.open()"
          v-hasPermi="['rd:order-split:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 创建拆分
        </el-button>
      </el-form-item>
    </el-form>

    <el-form
      v-else
      ref="ruleQueryFormRef"
      :model="ruleQuery"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="规则编码" prop="ruleCode">
        <el-input
          v-model="ruleQuery.ruleCode"
          clearable
          placeholder="请输入规则编码"
          class="!w-240px"
          @keyup.enter="handleRuleQuery"
        />
      </el-form-item>
      <el-form-item label="规则名称" prop="ruleName">
        <el-input
          v-model="ruleQuery.ruleName"
          clearable
          placeholder="请输入规则名称"
          class="!w-240px"
          @keyup.enter="handleRuleQuery"
        />
      </el-form-item>
      <RdCustomerProductFields
        v-model:customer-name="ruleQuery.customerName"
        v-model:product-model="ruleQuery.productModel"
        query-mode
      />
      <el-form-item label="是否启用" prop="enabled">
        <el-select v-model="ruleQuery.enabled" clearable placeholder="请选择" class="!w-240px">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleRuleQuery"
          ><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button
        >
        <el-button @click="resetRuleQuery"
          ><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button
        >
        <el-button
          type="primary"
          plain
          @click="ruleDialogRef?.open('create')"
          v-hasPermi="['rd:order-split:rule-manage']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增规则
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap v-if="activeTab === 'execution'">
    <div class="table-scroll">
      <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
        <el-table-column label="拆分单号" prop="splitNo" min-width="150" />
        <el-table-column label="日期" prop="splitDate" width="110" />
        <el-table-column label="销售订单" prop="saleOrderNo" min-width="145" />
        <el-table-column label="客户" prop="customerName" min-width="140" />
        <el-table-column label="型号" prop="productModel" min-width="120" />
        <el-table-column label="投炉量" prop="furnaceLoadQty" align="right" width="100" />
        <el-table-column label="成品率" align="right" width="90"
          ><template #default="scope">{{ scope.row.yieldRate }}%</template></el-table-column
        >
        <el-table-column label="预期产出" prop="expectedOutputQty" align="right" width="110" />
        <el-table-column label="最终计划" prop="finalPlannedOutputQty" align="right" width="110" />
        <el-table-column label="绑定实绩" prop="actualBoundOutputQty" align="right" width="110">
          <template #default="scope">{{ scope.row.actualBoundOutputQty ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="计划偏差" prop="varianceQty" align="right" width="105">
          <template #default="scope"
            ><span :class="varianceClass(scope.row.varianceQty)">{{
              scope.row.varianceQty
            }}</span></template
          >
        </el-table-column>
        <el-table-column label="状态" prop="status" align="center" width="90">
          <template #default="scope"
            ><el-tag :type="statusMap[scope.row.status]?.type || 'info'">{{
              statusMap[scope.row.status]?.label || scope.row.status
            }}</el-tag></template
          >
        </el-table-column>
        <el-table-column label="操作" fixed="right" width="330" align="center">
          <template #default="scope">
            <el-button
              link
              type="primary"
              @click="detailDrawerRef?.open(scope.row.id)"
              v-hasPermi="['rd:order-split:query']"
              >详情</el-button
            >
            <el-button
              link
              type="primary"
              :disabled="scope.row.status !== 'DRAFT'"
              @click="adjustDialogRef?.open(scope.row.id)"
              v-hasPermi="['rd:order-split:adjust']"
              >调整</el-button
            >
            <el-button
              link
              type="success"
              :disabled="scope.row.status !== 'DRAFT'"
              @click="handleConfirm(scope.row)"
              v-hasPermi="['rd:order-split:confirm']"
              >确认</el-button
            >
            <el-button
              link
              type="primary"
              :disabled="scope.row.status !== 'CONFIRMED'"
              @click="detailDrawerRef?.open(scope.row.id)"
              v-hasPermi="['rd:order-split:bind']"
              >绑定实绩</el-button
            >
            <el-button
              link
              type="danger"
              :disabled="
                scope.row.status === 'CANCELLED' || Number(scope.row.actualBoundOutputQty) !== 0
              "
              @click="handleCancel(scope.row)"
              v-hasPermi="['rd:order-split:cancel']"
              >取消</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <ContentWrap v-else>
    <div class="table-scroll">
      <el-table
        v-loading="ruleLoading"
        :data="ruleList"
        :stripe="true"
        :show-overflow-tooltip="true"
      >
        <el-table-column label="规则编码" prop="ruleCode" min-width="140" />
        <el-table-column label="规则名称" prop="ruleName" min-width="160" />
        <el-table-column label="客户" prop="customerName" min-width="140" />
        <el-table-column label="型号" prop="productModel" min-width="120" />
        <el-table-column
          label="默认投炉量"
          prop="defaultFurnaceLoadQty"
          align="right"
          width="120"
        />
        <el-table-column label="成品率" align="right" width="100"
          ><template #default="scope">{{ scope.row.yieldRate }}%</template></el-table-column
        >
        <el-table-column label="启用" prop="enabled" align="center" width="80"
          ><template #default="scope"
            ><el-tag :type="scope.row.enabled ? 'success' : 'info'">{{
              scope.row.enabled ? '是' : '否'
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column label="操作" fixed="right" width="90" align="center">
          <template #default="scope"
            ><el-button
              link
              type="primary"
              @click="ruleDialogRef?.open('update', scope.row.id)"
              v-hasPermi="['rd:order-split:rule-manage']"
              >编辑</el-button
            ></template
          >
        </el-table-column>
      </el-table>
    </div>
    <Pagination
      :total="ruleTotal"
      v-model:page="ruleQuery.pageNo"
      v-model:limit="ruleQuery.pageSize"
      @pagination="getRuleList"
    />
  </ContentWrap>

  <CreateDialog ref="createDialogRef" @success="getList" />
  <AdjustDialog ref="adjustDialogRef" @success="getList" />
  <RuleDialog ref="ruleDialogRef" @success="getRuleList" />
  <DetailDrawer ref="detailDrawerRef" @changed="getList" />
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from '@/hooks/web/useMessage'
import { checkPermi } from '@/utils/permission'
import { RdOrderSplitApi } from '@/api/rd/order/split'
import type { RdOrderSplitResultVO, RdOrderSplitRuleVO } from '@/api/rd/order/split'
import RdCustomerProductFields from '@/components/RdCustomerProductFields/index.vue'
import CreateDialog from './CreateDialog.vue'
import AdjustDialog from './AdjustDialog.vue'
import RuleDialog from './RuleDialog.vue'
import DetailDrawer from './DetailDrawer.vue'

defineOptions({ name: 'RdOrderSplit' })

const route = useRoute()
const message = useMessage()
const activeTab = ref('execution')
const loading = ref(false)
const list = ref<RdOrderSplitResultVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const createDialogRef = ref<InstanceType<typeof CreateDialog>>()
const adjustDialogRef = ref<InstanceType<typeof AdjustDialog>>()
const ruleDialogRef = ref<InstanceType<typeof RuleDialog>>()
const detailDrawerRef = ref<InstanceType<typeof DetailDrawer>>()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  splitDate: [] as string[],
  splitNo: undefined as string | undefined,
  saleOrderNo: undefined as string | undefined,
  customerName: undefined as string | undefined,
  productModel: undefined as string | undefined,
  status: undefined as string | undefined
})
const statusOptions = [
  { label: '草稿', value: 'DRAFT', type: 'info' as const },
  { label: '已确认', value: 'CONFIRMED', type: 'success' as const },
  { label: '已取消', value: 'CANCELLED', type: 'danger' as const }
]
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<
  string,
  (typeof statusOptions)[number]
>
const hasRuleAccess = computed(() =>
  checkPermi(['rd:order-split:rule-query', 'rd:order-split:rule-manage'])
)

const ruleLoading = ref(false)
const ruleList = ref<RdOrderSplitRuleVO[]>([])
const ruleTotal = ref(0)
const ruleQueryFormRef = ref()
const ruleQuery = reactive({
  pageNo: 1,
  pageSize: 10,
  ruleCode: undefined as string | undefined,
  ruleName: undefined as string | undefined,
  customerName: undefined as string | undefined,
  productModel: undefined as string | undefined,
  enabled: undefined as boolean | undefined
})

const getList = async () => {
  loading.value = true
  try {
    const data = await RdOrderSplitApi.getPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const getRuleList = async () => {
  ruleLoading.value = true
  try {
    const data = await RdOrderSplitApi.getRulePage(ruleQuery)
    ruleList.value = data.list
    ruleTotal.value = data.total
  } finally {
    ruleLoading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}
const handleRuleQuery = () => {
  ruleQuery.pageNo = 1
  getRuleList()
}
const resetRuleQuery = () => {
  ruleQueryFormRef.value?.resetFields()
  handleRuleQuery()
}
const handleTabChange = (name: string | number) => {
  if (name === 'rule' && ruleList.value.length === 0) getRuleList()
}
const varianceClass = (value: number) =>
  Number(value) === 0 ? 'variance-zero' : 'variance-nonzero'

const handleConfirm = async (row: RdOrderSplitResultVO) => {
  await message.confirm(
    '确认该拆分计划吗？确认只锁定计划，不产生库存，也不计入销售订单实际产额；生产实绩需另行绑定。'
  )
  await RdOrderSplitApi.confirm({ id: row.id, version: row.version })
  message.success('拆分计划已确认')
  await getList()
}
const handleCancel = async (row: RdOrderSplitResultVO) => {
  const { value } = await message.prompt('请输入取消原因（必填）', '取消拆分计划')
  await RdOrderSplitApi.cancel({ id: row.id, version: row.version, reason: value })
  message.success('拆分计划已取消')
  await getList()
}

onMounted(async () => {
  await getList()
  const orderId = Number(route.query.orderId || 0)
  if (orderId > 0) createDialogRef.value?.open(orderId)
})
</script>

<style scoped>
.order-split-tabs {
  margin-top: -8px;
}
.table-scroll {
  max-width: 100%;
  overflow-x: auto;
}
.table-scroll :deep(.el-table) {
  min-width: 1080px;
}
.variance-zero {
  color: var(--el-color-success);
  font-weight: 600;
}
.variance-nonzero {
  color: var(--el-color-warning);
  font-weight: 600;
}
</style>
