<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
      <el-form-item label="日期" prop="productionDate">
        <el-date-picker
          v-model="queryParams.productionDate"
          type="daterange"
          value-format="YYYY-MM-DD"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="货位" prop="locationCode">
        <el-input
          v-model="queryParams.locationCode"
          placeholder="请输入货位"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="客户" prop="customerName">
        <el-input
          v-model="queryParams.customerName"
          placeholder="请输入客户"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="型号" prop="productModel">
        <el-input
          v-model="queryParams.productModel"
          placeholder="请输入型号"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="炉号" prop="furnaceNo">
        <el-input
          v-model="queryParams.furnaceNo"
          placeholder="请输入生产炉号"
          clearable
          @keyup.enter="handleQuery"
          class="!w-240px"
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
          v-hasPermi="['rd:production-output:create']"
        >
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
        <el-button
          type="success"
          plain
          @click="handleExport"
          :loading="exportLoading"
          v-hasPermi="['rd:production-output:export']"
        >
          <Icon icon="ep:download" class="mr-5px" /> 导出
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" :stripe="true" :show-overflow-tooltip="true">
      <el-table-column label="日期" align="center" prop="productionDate" width="110" />
      <el-table-column label="货位" align="center" prop="locationCode" width="100" />
      <el-table-column label="客户" align="center" prop="customerName" min-width="150" />
      <el-table-column label="型号" align="center" prop="productModel" min-width="140" />
      <el-table-column label="生产炉号" align="center" prop="furnaceNo" width="130" />
      <el-table-column label="所在炉层" align="center" prop="furnaceLayer" width="100" />
      <el-table-column label="实际产额" align="right" prop="outputQty" width="110" />
      <el-table-column label="入库数量" align="right" prop="inboundQty" width="110" />
      <el-table-column label="单包重量" align="right" prop="packageWeight" width="110" />
      <el-table-column label="产出重量" align="right" prop="outputWeight" width="110" />
      <el-table-column label="状态" align="center" prop="status" width="110">
        <template #default="scope">
          <el-tag :type="statusMap[scope.row.status]?.type || 'info'">
            {{ statusMap[scope.row.status]?.label || scope.row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        label="创建时间"
        align="center"
        prop="createTime"
        :formatter="dateFormatter"
        width="180"
      />
      <el-table-column label="操作" align="center" fixed="right" width="230">
        <template #default="scope">
          <el-button
            link
            type="primary"
            @click="openForm('update', scope.row.id)"
            :disabled="scope.row.status === 'INBOUNDED' || scope.row.status === 'CANCELLED'"
            v-hasPermi="['rd:production-output:update']"
          >
            编辑
          </el-button>
          <el-button
            link
            type="success"
            @click="handleInbound(scope.row)"
            :disabled="
              scope.row.status === 'INBOUNDED' ||
              scope.row.status === 'CANCELLED' ||
              !scope.row.locationCode
            "
            v-hasPermi="['rd:production-output:inbound']"
          >
            转入库
          </el-button>
          <el-button
            link
            type="danger"
            @click="handleCancel(scope.row.id)"
            :disabled="scope.row.status === 'INBOUNDED' || scope.row.status === 'CANCELLED'"
            v-hasPermi="['rd:production-output:cancel']"
          >
            作废
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

  <OutputForm ref="formRef" @success="getList" />
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import download from '@/utils/download'
import { dateFormatter } from '@/utils/formatTime'
import { useMessage } from '@/hooks/web/useMessage'
import OutputForm from './OutputForm.vue'
import { RdProductionOutputApi } from '@/api/rd/production/output'
import type { RdProductionOutputVO } from '@/api/rd/production/output'

defineOptions({ name: 'RdProductionOutput' })

const message = useMessage()
const loading = ref(true)
const list = ref<RdProductionOutputVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const exportLoading = ref(false)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  productionDate: [] as string[],
  locationCode: undefined as string | undefined,
  customerName: undefined as string | undefined,
  productModel: undefined as string | undefined,
  furnaceNo: undefined as string | undefined,
  status: undefined as string | undefined
})
const statusOptions: Array<{ label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }> = [
  { label: '草稿', value: 'DRAFT', type: 'info' },
  { label: '已确认', value: 'CONFIRMED', type: 'warning' },
  { label: '已入库', value: 'INBOUNDED', type: 'success' },
  { label: '已作废', value: 'CANCELLED', type: 'danger' }
]
const statusMap = statusOptions.reduce(
  (map, item) => ({ ...map, [item.value]: item }),
  {} as Record<string, { label: string; value: string; type: 'success' | 'warning' | 'info' | 'danger' }>
)

const getList = async () => {
  loading.value = true
  try {
    const data = await RdProductionOutputApi.getPage(queryParams)
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

const formRef = ref()
const openForm = (type: string, id?: number) => {
  formRef.value.open(type, id)
}

const handleCancel = async (id: number) => {
  try {
    await message.confirm('确认作废该生产炉层产量吗？作废后不会删除历史记录。')
    await RdProductionOutputApi.cancel(id)
    message.success('作废成功')
    await getList()
  } catch {}
}

const handleInbound = async (row: RdProductionOutputVO) => {
  try {
    await message.confirm('确认将该生产炉层产量转入库存吗？入库后会生成库存批次和入库流水。')
    await RdProductionOutputApi.inbound(row.id)
    message.success('入库成功')
    await getList()
  } catch {}
}

const handleExport = async () => {
  try {
    await message.exportConfirm()
    exportLoading.value = true
    const data = await RdProductionOutputApi.exportExcel(queryParams)
    download.excel(data, '生产炉层产量.xls')
  } catch {
  } finally {
    exportLoading.value = false
  }
}

onMounted(() => {
  getList()
})
</script>
