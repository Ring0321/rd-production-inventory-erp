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
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择日期"
          class="!w-240px"
        />
      </el-form-item>
      <el-form-item label="货位" prop="locationCode">
        <el-input v-model="queryParams.locationCode" placeholder="请输入货位" clearable class="!w-240px" />
      </el-form-item>
      <el-form-item label="客户" prop="customerName">
        <el-input v-model="queryParams.customerName" placeholder="请输入客户" clearable class="!w-240px" />
      </el-form-item>
      <el-form-item label="型号" prop="productModel">
        <el-input v-model="queryParams.productModel" placeholder="请输入型号" clearable class="!w-240px" />
      </el-form-item>
      <el-form-item label="炉号" prop="furnaceNo">
        <el-input v-model="queryParams.furnaceNo" placeholder="请输入生产炉号" clearable class="!w-240px" />
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" /> 搜索</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" /> 重置</el-button>
        <el-button type="primary" plain>
          <Icon icon="ep:plus" class="mr-5px" /> 新增
        </el-button>
        <el-button type="success" plain>
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
      <el-table-column label="实际产额" align="center" prop="outputQty" width="110" />
      <el-table-column label="入库数量" align="center" prop="inboundQty" width="110" />
      <el-table-column label="单包重量" align="center" prop="packageWeight" width="110" />
      <el-table-column label="产出重量" align="center" prop="outputWeight" width="110" />
      <el-table-column label="状态" align="center" prop="status" width="110" />
      <el-table-column label="操作" align="center" fixed="right" width="160">
        <template #default>
          <el-button link type="primary">查看</el-button>
          <el-button link type="primary">编辑</el-button>
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
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import type { RdProductionOutputVO } from '@/api/rd/production/output'

defineOptions({ name: 'RdProductionOutput' })

const loading = ref(false)
const list = ref<RdProductionOutputVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  productionDate: undefined,
  locationCode: undefined,
  customerName: undefined,
  productModel: undefined,
  furnaceNo: undefined
})

const getList = async () => {
  loading.value = false
  list.value = []
  total.value = 0
}

const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}

const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}
</script>
