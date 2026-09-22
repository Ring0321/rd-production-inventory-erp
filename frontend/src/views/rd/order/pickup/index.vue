<template>
  <ContentWrap>
    <div class="page-heading">
      <h2>提货单</h2>
      <el-button type="primary" v-hasPermi="['rd:pickup-slip:create']" @click="openCreate">
        <Icon icon="ep:plus" class="mr-5px" />录入提货单
      </el-button>
    </div>
    <el-form :inline="true" :model="query">
      <el-form-item label="提货单号">
        <el-input v-model="query.slipNo" clearable @keyup.enter="loadPage" />
      </el-form-item>
      <el-form-item label="客户">
        <el-input v-model="query.customerName" clearable @keyup.enter="loadPage" />
      </el-form-item>
      <el-form-item>
        <el-button @click="loadPage"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
      </el-form-item>
    </el-form>
    <el-table v-loading="loading" :data="rows" show-overflow-tooltip>
      <el-table-column prop="slipNo" label="提货单号" min-width="160" />
      <el-table-column prop="customerName" label="客户" min-width="180" />
      <el-table-column prop="pickupDate" label="提货日期" width="120" />
      <el-table-column prop="vehiclePlate" label="车牌号" width="120" />
      <el-table-column prop="requestedPackageCount" label="包数" width="90" align="right" />
      <el-table-column prop="requestedWeight" label="重量 kg" width="110" align="right" />
      <el-table-column prop="status" label="状态" width="100" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="scope">
          <el-button link type="primary" @click="showDetail(scope.row.id)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:page="query.pageNo"
      v-model:limit="query.pageSize"
      :total="total"
      @pagination="loadPage"
    />
  </ContentWrap>

  <Dialog v-model="createVisible" title="录入提货单" width="1100px">
    <el-form label-width="110px" :model="form">
      <el-row :gutter="16">
        <el-col :xs="24" :md="12"
          ><el-form-item label="提货单号" required><el-input v-model="form.slipNo" /></el-form-item
        ></el-col>
        <el-col :xs="24" :md="12"
          ><el-form-item label="客户" required
            ><el-input v-model="form.customerName" /></el-form-item
        ></el-col>
        <el-col :xs="24" :md="8"
          ><el-form-item label="提货日期"
            ><el-date-picker
              v-model="form.pickupDate"
              type="date"
              value-format="YYYY-MM-DD" /></el-form-item
        ></el-col>
        <el-col :xs="24" :md="8"
          ><el-form-item label="车牌号"><el-input v-model="form.vehiclePlate" /></el-form-item
        ></el-col>
        <el-col :xs="24" :md="8"
          ><el-form-item label="运输司机"
            ><el-input v-model="form.transportDriverName" /></el-form-item
        ></el-col>
        <el-col :xs="24" :md="8"
          ><el-form-item label="司机电话"
            ><el-input v-model="form.transportDriverPhone" /></el-form-item
        ></el-col>
        <el-col :xs="24" :md="8"
          ><el-form-item label="总包数"
            ><el-input-number
              v-model="form.requestedPackageCount"
              :min="1"
              :controls="false" /></el-form-item
        ></el-col>
        <el-col :xs="24" :md="8"
          ><el-form-item label="总重量 kg"
            ><el-input-number
              v-model="form.requestedWeight"
              :min="0.001"
              :precision="3"
              :controls="false" /></el-form-item
        ></el-col>
        <el-col :xs="24"
          ><el-form-item label="原单附件引用"
            ><el-input v-model="form.sourceAttachmentRef" /></el-form-item
        ></el-col>
      </el-row>
      <div class="line-heading">
        <strong>货物明细</strong>
        <el-button @click="addLine"><Icon icon="ep:plus" class="mr-5px" />添加明细</el-button>
      </div>
      <el-table :data="form.lines" max-height="350" show-overflow-tooltip>
        <el-table-column prop="lineNo" label="序号" width="60" />
        <el-table-column label="订单" min-width="170">
          <template #default="scope">
            <el-select
              v-model="scope.row.saleOrderId"
              clearable
              filterable
              remote
              :remote-method="searchOrders"
              @change="applyOrder(scope.row)"
            >
              <el-option
                v-for="order in orders"
                :key="order.id"
                :value="order.id"
                :label="order.orderNo"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="型号" min-width="130"
          ><template #default="scope"><el-input v-model="scope.row.productModel" /></template
        ></el-table-column>
        <el-table-column label="炉号" min-width="125"
          ><template #default="scope"><el-input v-model="scope.row.furnaceNo" /></template
        ></el-table-column>
        <el-table-column label="炉层" min-width="110"
          ><template #default="scope"><el-input v-model="scope.row.furnaceLayer" /></template
        ></el-table-column>
        <el-table-column label="包数" width="115"
          ><template #default="scope"
            ><el-input-number
              v-model="scope.row.requestedPackageCount"
              :min="1"
              :controls="false"
              class="!w-95px" /></template
        ></el-table-column>
        <el-table-column label="重量 kg" width="135"
          ><template #default="scope"
            ><el-input-number
              v-model="scope.row.requestedWeight"
              :min="0.001"
              :precision="3"
              :controls="false"
              class="!w-115px" /></template
        ></el-table-column>
        <el-table-column label="操作" width="68"
          ><template #default="scope"
            ><el-button
              link
              type="danger"
              :disabled="form.lines.length === 1"
              @click="removeLine(scope.$index)"
              ><Icon icon="ep:delete" /></el-button></template
        ></el-table-column>
      </el-table>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">签发</el-button>
    </template>
  </Dialog>

  <Dialog v-model="detailVisible" title="提货单详情" width="940px">
    <template v-if="detail">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="提货单号">{{ detail.slip.slipNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.slip.customerName }}</el-descriptions-item>
        <el-descriptions-item label="提货日期">{{
          detail.slip.pickupDate || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="车牌号">{{
          detail.slip.vehiclePlate || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="包数">{{
          detail.slip.requestedPackageCount ?? '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="重量 kg">{{
          detail.slip.requestedWeight ?? '-'
        }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="detail.lines" class="mt-16px">
        <el-table-column prop="lineNo" label="序号" width="70" />
        <el-table-column prop="saleOrderId" label="订单 ID" width="100" />
        <el-table-column prop="productModel" label="型号" min-width="120" />
        <el-table-column prop="furnaceNo" label="炉号" min-width="120" />
        <el-table-column prop="furnaceLayer" label="炉层" width="100" />
        <el-table-column prop="requestedPackageCount" label="包数" width="90" />
        <el-table-column prop="requestedWeight" label="重量 kg" width="110" />
      </el-table>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { RdSaleOrderApi, type RdSaleOrderVO } from '@/api/rd/order/sale'
import {
  RdLoadingApi,
  type PickupSlip,
  type PickupSlipCreate,
  type PickupSlipLine
} from '@/api/rd/loading'

defineOptions({ name: 'RdPickupSlip' })
const message = useMessage()
const loading = ref(false)
const submitting = ref(false)
const createVisible = ref(false)
const detailVisible = ref(false)
const rows = ref<PickupSlip[]>([])
const total = ref(0)
const query = reactive({ pageNo: 1, pageSize: 20, slipNo: '', customerName: '' })
const orders = ref<RdSaleOrderVO[]>([])
const detail = ref<{ slip: PickupSlip; lines: PickupSlipLine[] }>()
const blankForm = (): PickupSlipCreate => ({
  slipNo: '',
  customerName: '',
  vehiclePlate: '',
  transportDriverName: '',
  transportDriverPhone: '',
  lines: [{ lineNo: 1, productModel: '' }]
})
const form = ref<PickupSlipCreate>(blankForm())

const loadPage = async () => {
  loading.value = true
  try {
    const page = await RdLoadingApi.getPickupSlipPage(query)
    rows.value = page.list
    total.value = page.total
  } finally {
    loading.value = false
  }
}
const searchOrders = async (keyword: string) => {
  const page = await RdSaleOrderApi.getPage({
    pageNo: 1,
    pageSize: 50,
    orderNo: keyword || undefined,
    customerName: form.value.customerName || undefined
  })
  orders.value = page.list || []
}
const applyOrder = (line: PickupSlipCreate['lines'][number]) => {
  const order = orders.value.find((item) => item.id === line.saleOrderId)
  if (order) {
    line.productModel = order.productModel
    if (!form.value.customerName) form.value.customerName = order.customerName
  }
}
const openCreate = () => {
  form.value = blankForm()
  createVisible.value = true
  searchOrders('')
}
const addLine = () =>
  form.value.lines.push({ lineNo: form.value.lines.length + 1, productModel: '' })
const removeLine = (index: number) => {
  form.value.lines.splice(index, 1)
  form.value.lines.forEach((line, i) => {
    line.lineNo = i + 1
  })
}
const submit = async () => {
  if (
    !form.value.slipNo.trim() ||
    !form.value.customerName.trim() ||
    form.value.lines.some(
      (line) => !line.productModel.trim() || (!line.requestedPackageCount && !line.requestedWeight)
    )
  ) {
    message.warning('请填写提货单号、客户以及每行的型号和数量')
    return
  }
  submitting.value = true
  try {
    await RdLoadingApi.createPickupSlip(form.value)
    message.success('提货单已签发')
    createVisible.value = false
    await loadPage()
  } finally {
    submitting.value = false
  }
}
const showDetail = async (id: number) => {
  detail.value = await RdLoadingApi.getPickupSlip(id)
  detailVisible.value = true
}
onMounted(loadPage)
</script>

<style scoped>
.page-heading,
.line-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}
.page-heading h2 {
  font-size: 18px;
  margin: 0;
}
</style>
