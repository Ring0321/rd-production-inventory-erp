<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="调拨日期" prop="transferDate">
        <el-date-picker
          v-model="queryParams.transferDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="调拨单号" prop="transferNo">
        <el-input
          v-model="queryParams.transferNo"
          placeholder="请输入调拨单号"
          clearable
          class="!w-240px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="queryParams.status" placeholder="请选择状态" clearable class="!w-240px">
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
          @click="openForm('create')"
          v-hasPermi="['rd:transfer-order:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
        <el-button
          type="success"
          plain
          :loading="exportLoading"
          @click="handleExport"
          v-hasPermi="['rd:transfer-order:export']"
        >
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="调拨单号" prop="transferNo" min-width="160" />
      <el-table-column label="日期" prop="transferDate" align="center" width="110" />
      <el-table-column label="调出客户" prop="fromCustomerName" min-width="140" />
      <el-table-column label="调入订单" prop="toOrderNo" min-width="150" />
      <el-table-column label="调入客户" prop="toCustomerName" min-width="140" />
      <el-table-column label="型号" prop="productModel" min-width="120" />
      <el-table-column label="货位" prop="fromLocationCode" align="center" width="90" />
      <el-table-column label="生产炉号" prop="furnaceNo" min-width="120" />
      <el-table-column label="炉层" prop="furnaceLayer" align="center" width="90" />
      <el-table-column label="调拨数量" prop="quantity" align="right" width="105" />
      <el-table-column label="调拨重量" prop="weight" align="right" width="105" />
      <el-table-column label="状态" prop="status" align="center" width="100">
        <template #default="scope">
          <el-tag :type="statusMap[scope.row.status]?.type || 'info'">
            {{ statusMap[scope.row.status]?.label || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" align="center" fixed="right" width="240">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openDetail(scope.row.id)"
            v-hasPermi="['rd:transfer-order:query']"
          >
            详情
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="scope.row.status !== 'DRAFT'"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['rd:transfer-order:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="success"
            :disabled="scope.row.status !== 'DRAFT'"
            @click="handleConfirm(scope.row)"
            v-hasPermi="['rd:transfer-order:confirm']"
          >
            确认
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="scope.row.status === 'CANCELLED'"
            @click="handleCancel(scope.row)"
            v-hasPermi="['rd:transfer-order:cancel']"
          >
            撤销
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="queryParams.pageNo"
      v-model:limit="queryParams.pageSize"
      @pagination="getList"
    />
  </ContentWrap>

  <TransferForm ref="formRef" @success="getList" />
  <TransferDetailDrawer ref="detailDrawerRef" />
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import download from '@/utils/download'
import { useMessage } from '@/hooks/web/useMessage'
import { RdTransferOrderApi } from '@/api/rd/transfer'
import type { RdTransferOrderVO } from '@/api/rd/transfer'
import TransferForm from './TransferForm.vue'
import TransferDetailDrawer from './TransferDetailDrawer.vue'

defineOptions({ name: 'RdTransferOrder' })

const route = useRoute()
const message = useMessage()
const loading = ref(true)
const exportLoading = ref(false)
const list = ref<RdTransferOrderVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const formRef = ref()
const detailDrawerRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  transferDate: [] as string[],
  transferNo: undefined as string | undefined,
  status: undefined as string | undefined
})
const statusOptions: Array<{ label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }> = [
  { label: '草稿', value: 'DRAFT', type: 'info' },
  { label: '已确认', value: 'CONFIRMED', type: 'success' },
  { label: '已撤销', value: 'CANCELLED', type: 'danger' }
]
const statusMap = statusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }>
)

const getList = async () => {
  loading.value = true
  try {
    const data = await RdTransferOrderApi.getPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
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

const openForm = (type: 'create' | 'update', id?: number, toOrderId?: number) => {
  formRef.value?.open(type, id, toOrderId)
}

const openDetail = (id: number) => {
  detailDrawerRef.value?.open(id)
}

const handleConfirm = async (transferOrder: RdTransferOrderVO) => {
  await message.confirm(
    `确认调拨单 ${transferOrder.transferNo} 吗？确认后将扣减来源库存并更新目标订单差额。`
  )
  await RdTransferOrderApi.confirm(transferOrder.id)
  message.success('调拨单确认成功')
  await getList()
}

const handleCancel = async (transferOrder: RdTransferOrderVO) => {
  const content =
    transferOrder.status === 'CONFIRMED'
      ? '该调拨已确认。撤销前系统会校验目标库存未被锁定或出库，并生成反向库存流水。确认继续吗？'
      : '确认撤销该调拨草稿吗？'
  await message.confirm(content)
  await RdTransferOrderApi.cancel(transferOrder.id)
  message.success('调拨单已撤销')
  await getList()
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await RdTransferOrderApi.exportExcel(queryParams)
    download.excel(data, '调拨单.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

onMounted(async () => {
  await getList()
  const toOrderId = Number(route.query.toOrderId || 0)
  if (toOrderId > 0) {
    openForm('create', undefined, toOrderId)
  }
})
</script>
