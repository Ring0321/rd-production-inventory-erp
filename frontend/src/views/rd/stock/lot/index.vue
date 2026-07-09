<template>
  <ContentWrap>
    <el-form
      ref="queryFormRef"
      :model="queryParams"
      :inline="true"
      class="-mb-15px"
      label-width="68px"
    >
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
      <el-form-item label="炉层" prop="furnaceLayer">
        <el-input v-model="queryParams.furnaceLayer" placeholder="请输入所在炉层" clearable class="!w-240px" />
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
      <el-table-column label="货位" align="center" prop="locationCode" width="100" />
      <el-table-column label="客户" align="center" prop="customerName" min-width="150" />
      <el-table-column label="型号" align="center" prop="productModel" min-width="140" />
      <el-table-column label="生产炉号" align="center" prop="furnaceNo" width="130" />
      <el-table-column label="所在炉层" align="center" prop="furnaceLayer" width="100" />
      <el-table-column label="期初" align="center" prop="openingQty" width="100" />
      <el-table-column label="调入" align="center" prop="transferInQty" width="100" />
      <el-table-column label="调出" align="center" prop="transferOutQty" width="100" />
      <el-table-column label="入库" align="center" prop="inboundQty" width="100" />
      <el-table-column label="出库" align="center" prop="outboundQty" width="100" />
      <el-table-column label="退货" align="center" prop="returnQty" width="100" />
      <el-table-column label="库房数量" align="center" prop="warehouseQty" width="110" />
      <el-table-column label="单包重量" align="center" prop="packageWeight" width="110" />
      <el-table-column label="库存重量" align="center" prop="stockWeight" width="110" />
      <el-table-column label="状态" align="center" prop="status" width="110" />
      <el-table-column label="操作" align="center" fixed="right" width="160">
        <template #default>
          <el-button link type="primary">流水</el-button>
          <el-button link type="primary">调拨</el-button>
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
import type { RdStockLotVO } from '@/api/rd/stock/lot'

defineOptions({ name: 'RdStockLot' })

const loading = ref(false)
const list = ref<RdStockLotVO[]>([])
const total = ref(0)
const queryFormRef = ref()
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  locationCode: undefined,
  customerName: undefined,
  productModel: undefined,
  furnaceNo: undefined,
  furnaceLayer: undefined
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
