<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :model="query" inline>
      <el-form-item label="出库单号" prop="outboundNo"
        ><el-input v-model="query.outboundNo" clearable
      /></el-form-item>
      <el-form-item label="状态" prop="status">
        <el-select v-model="query.status" clearable class="!w-150px">
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search"><Icon icon="ep:search" />查询</el-button>
        <el-button @click="reset"><Icon icon="ep:refresh" />重置</el-button>
        <el-button
          type="primary"
          plain
          @click="formRef?.open('create')"
          v-hasPermi="['rd:outbound-order:create']"
        >
          <Icon icon="ep:plus" />新建出库单
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>
  <ContentWrap>
    <el-table v-loading="loading" :data="list">
      <el-table-column prop="outboundNo" label="出库单号" min-width="155" fixed="left" />
      <el-table-column prop="outboundDate" label="日期" width="110" />
      <el-table-column prop="orderNo" label="销售订单" min-width="140" />
      <el-table-column prop="customerName" label="客户" min-width="140" />
      <el-table-column prop="productModel" label="型号" min-width="120" />
      <el-table-column prop="locationCode" label="货位" width="90" />
      <el-table-column prop="furnaceNo" label="炉号" width="115" />
      <el-table-column prop="furnaceLayer" label="炉层" width="95" />
      <el-table-column prop="quantity" label="包数" width="80" align="right" />
      <el-table-column prop="weight" label="重量(kg)" width="115" align="right" />
      <el-table-column label="状态" width="100">
        <template #default="scope"
          ><el-tag :type="statusMap[scope.row.status]?.type || 'info'">{{
            statusMap[scope.row.status]?.label || scope.row.status
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column label="操作" width="310" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="detailRef?.open(scope.row.id)">详情</el-button>
          <el-button
            v-if="['DRAFT', 'SCANNING'].includes(scope.row.status)"
            link
            type="primary"
            @click="scanRef?.open(scope.row.id)"
            v-hasPermi="['rd:outbound-scan:scan']"
            >扫码</el-button
          >
          <el-button
            v-if="scope.row.status === 'DRAFT'"
            link
            type="primary"
            @click="formRef?.open('update', scope.row.id)"
            v-hasPermi="['rd:outbound-order:update']"
            >编辑</el-button
          >
          <el-button
            v-if="scope.row.status === 'SUBMITTED'"
            link
            type="success"
            @click="approve(scope.row)"
            v-hasPermi="['rd:outbound-scan:approve']"
            >审核</el-button
          >
          <el-button
            v-if="scope.row.status === 'APPROVED'"
            link
            type="success"
            @click="post(scope.row)"
            v-hasPermi="['rd:outbound-scan:post']"
            >过账</el-button
          >
          <el-button
            v-if="scope.row.status === 'POSTED'"
            link
            type="danger"
            @click="reverse(scope.row)"
            v-hasPermi="['rd:outbound-scan:reverse']"
            >撤销</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      :total="total"
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      @pagination="load"
    />
  </ContentWrap>
  <OutboundForm ref="formRef" @success="load" />
  <OutboundScanDialog ref="scanRef" @success="load" />
  <OutboundDetailDrawer ref="detailRef" />
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useMessage } from '@/hooks/web/useMessage'
import { RdOutboundOrderApi, type RdOutboundOrderVO } from '@/api/rd/outbound'
import OutboundForm from './OutboundForm.vue'
import OutboundScanDialog from './OutboundScanDialog.vue'
import OutboundDetailDrawer from './OutboundDetailDrawer.vue'

defineOptions({ name: 'RdOutboundOrder' })
const route = useRoute()
const message = useMessage()
const loading = ref(false)
const list = ref<RdOutboundOrderVO[]>([])
const total = ref(0)
const formRef = ref()
const scanRef = ref()
const detailRef = ref()
const queryFormRef = ref()
const query = reactive({
  pageNo: 1,
  pageSize: 10,
  outboundNo: undefined as string | undefined,
  status: undefined as string | undefined
})
const statusOptions = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'SCANNING', label: '扫码中', type: 'primary' },
  { value: 'SUBMITTED', label: '待审核', type: 'warning' },
  { value: 'APPROVED', label: '已审核', type: 'success' },
  { value: 'POSTED', label: '已过账', type: 'success' },
  { value: 'REVERSED', label: '已撤销', type: 'danger' }
] as const
const statusMap = Object.fromEntries(statusOptions.map((item) => [item.value, item])) as Record<
  string,
  any
>
const load = async () => {
  loading.value = true
  try {
    const data = await RdOutboundOrderApi.getPage(query)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const search = () => {
  query.pageNo = 1
  load()
}
const reset = () => {
  queryFormRef.value?.resetFields()
  search()
}
const approve = async (row: RdOutboundOrderVO) => {
  await message.confirm(`确认审核通过出库单 ${row.outboundNo} 吗？`)
  await RdOutboundOrderApi.approve(row.id, row.version)
  message.success('审核通过')
  await load()
}
const post = async (row: RdOutboundOrderVO) => {
  await message.confirm(`确认过账 ${row.quantity} 包吗？过账后将正式扣减库存。`)
  await RdOutboundOrderApi.post(row.id, row.version)
  message.success('出库过账完成')
  await load()
}
const reverse = async (row: RdOutboundOrderVO) => {
  const { value } = await ElMessageBox.prompt('请输入撤销原因', `撤销 ${row.outboundNo}`, {
    inputValidator: (v) => !!v?.trim() || '撤销原因不能为空'
  })
  await RdOutboundOrderApi.reverse(row.id, value, row.version)
  message.success('已生成反向库存流水')
  await load()
}
onMounted(async () => {
  await load()
  const orderId = Number(route.query.orderId || 0)
  if (orderId) formRef.value?.open('create', undefined, orderId)
})
</script>
