<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="出库日期" prop="outboundDate">
        <el-date-picker
          v-model="queryParams.outboundDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="出库单号" prop="outboundNo">
        <el-input
          v-model="queryParams.outboundNo"
          placeholder="请输入出库单号"
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
          v-hasPermi="['rd:outbound-order:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
        <el-button
          type="success"
          plain
          :loading="exportLoading"
          @click="handleExport"
          v-hasPermi="['rd:outbound-order:export']"
        >
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="出库单号" prop="outboundNo" min-width="165" />
      <el-table-column label="日期" prop="outboundDate" align="center" width="110" />
      <el-table-column label="销售订单" prop="orderNo" min-width="150" />
      <el-table-column label="客户" prop="customerName" min-width="140" />
      <el-table-column label="型号" prop="productModel" min-width="120" />
      <el-table-column label="货位" prop="locationCode" align="center" width="90" />
      <el-table-column label="生产炉号" prop="furnaceNo" min-width="120" />
      <el-table-column label="炉层" prop="furnaceLayer" align="center" width="90" />
      <el-table-column label="出库数量" prop="quantity" align="right" width="105" />
      <el-table-column label="出库重量" prop="weight" align="right" width="105" />
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
            v-hasPermi="['rd:outbound-order:query']"
          >
            详情
          </el-button>
          <el-button
            link
            type="primary"
            :disabled="scope.row.status !== 'DRAFT'"
            @click="openForm('update', scope.row.id)"
            v-hasPermi="['rd:outbound-order:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="success"
            :disabled="scope.row.status !== 'DRAFT'"
            @click="handleConfirm(scope.row)"
            v-hasPermi="['rd:outbound-order:confirm']"
          >
            确认
          </el-button>
          <el-button
            link
            type="danger"
            :disabled="scope.row.status === 'CANCELLED'"
            @click="handleCancel(scope.row)"
            v-hasPermi="['rd:outbound-order:cancel']"
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

  <OutboundForm ref="formRef" @success="getList" />
  <OutboundDetailDrawer ref="detailDrawerRef" />
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import download from '@/utils/download'
import { useMessage } from '@/hooks/web/useMessage'
import { RdOutboundOrderApi } from '@/api/rd/outbound'
import type { RdOutboundOrderVO } from '@/api/rd/outbound'
import OutboundForm from './OutboundForm.vue'
import OutboundDetailDrawer from './OutboundDetailDrawer.vue'

defineOptions({ name: 'RdOutboundOrder' })

const route = useRoute()
const message = useMessage()
const loading = ref(true)
const exportLoading = ref(false)
const list = ref<RdOutboundOrderVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const formRef = ref()
const detailDrawerRef = ref()
type StatusTagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  outboundDate: [] as string[],
  outboundNo: undefined as string | undefined,
  status: undefined as string | undefined
})
const statusOptions: Array<{ label: string; value: string; type: StatusTagType }> = [
  { label: '草稿', value: 'DRAFT', type: 'info' },
  { label: '已确认', value: 'CONFIRMED', type: 'success' },
  { label: '已撤销', value: 'CANCELLED', type: 'danger' }
]
const statusMap = statusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: StatusTagType }>
)

const getList = async () => {
  loading.value = true
  try {
    const data = await RdOutboundOrderApi.getPage(queryParams)
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

const openForm = (type: 'create' | 'update', id?: number, orderId?: number) => {
  formRef.value?.open(type, id, orderId)
}

const openDetail = (id: number) => {
  detailDrawerRef.value?.open(id)
}

const handleConfirm = async (outboundOrder: RdOutboundOrderVO) => {
  await message.confirm(
    `确认出库单 ${outboundOrder.outboundNo} 吗？确认后将扣减库存和锁定数量，并实时更新订单状态。`
  )
  await RdOutboundOrderApi.confirm(outboundOrder.id)
  message.success('出库单确认成功')
  await getList()
}

const handleCancel = async (outboundOrder: RdOutboundOrderVO) => {
  const content =
    outboundOrder.status === 'CONFIRMED'
      ? '该出库单已确认。撤销后系统将恢复库存和订单分配数量，并生成反向库存流水。确认继续吗？'
      : '确认撤销该出库草稿吗？'
  await message.confirm(content)
  await RdOutboundOrderApi.cancel(outboundOrder.id)
  message.success('出库单已撤销')
  await getList()
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await RdOutboundOrderApi.exportExcel(queryParams)
    download.excel(data, '出库上线.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

onMounted(async () => {
  await getList()
  const orderId = Number(route.query.orderId || 0)
  if (orderId > 0) {
    openForm('create', undefined, orderId)
  }
})
</script>
