<template>
  <Dialog title="绑定生产实绩" v-model="visible" width="min(860px, calc(100vw - 24px))">
    <el-alert type="warning" :closable="false" show-icon class="mb-16px">
      绑定会把该生产实绩计入销售订单实际产额；同一生产实绩只能绑定一次。
    </el-alert>
    <div class="table-scroll">
      <el-table
        v-loading="loading"
        :data="options"
        highlight-current-row
        @current-change="selected = $event"
      >
        <el-table-column label="生产日期" prop="productionDate" width="110" />
        <el-table-column label="炉号" prop="furnaceNo" min-width="120" />
        <el-table-column label="炉层" prop="furnaceLayer" width="90" />
        <el-table-column label="客户" prop="customerName" min-width="140" />
        <el-table-column label="型号" prop="productModel" min-width="120" />
        <el-table-column label="实际产额" prop="outputQty" align="right" width="110" />
        <el-table-column label="状态" prop="status" align="center" width="100">
          <template #default="scope">{{
            statusMap[scope.row.status] || scope.row.status
          }}</template>
        </el-table-column>
      </el-table>
    </div>
    <el-empty
      v-if="!loading && options.length === 0"
      description="没有符合客户、型号、炉层和状态条件的生产实绩"
      :image-size="72"
    />
    <template #footer>
      <el-button type="primary" :disabled="!selected" :loading="loading" @click="submit"
        >确认绑定</el-button
      >
      <el-button @click="visible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { RdOrderSplitApi } from '@/api/rd/order/split'
import type {
  RdOrderSplitProductionOptionVO,
  RdOrderSplitResultLineVO,
  RdOrderSplitResultVO
} from '@/api/rd/order/split'

defineOptions({ name: 'RdOrderSplitBindDialog' })

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const line = ref<RdOrderSplitResultLineVO>()
const result = ref<RdOrderSplitResultVO>()
const options = ref<RdOrderSplitProductionOptionVO[]>([])
const selected = ref<RdOrderSplitProductionOptionVO>()
const statusMap: Record<string, string> = { CONFIRMED: '已确认', INBOUNDED: '已入库' }

const open = async (resultLine: RdOrderSplitResultLineVO, splitResult: RdOrderSplitResultVO) => {
  line.value = resultLine
  result.value = splitResult
  selected.value = undefined
  options.value = []
  visible.value = true
  loading.value = true
  try {
    options.value = await RdOrderSplitApi.getProductionOptions(resultLine.id)
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

const emit = defineEmits(['success'])
const submit = async () => {
  if (!line.value || !result.value || !selected.value) return
  await message.confirm(
    `确认将生产炉号 ${selected.value.furnaceNo}、产额 ${selected.value.outputQty} 绑定到炉层 ${line.value.furnaceLayer} 吗？`
  )
  loading.value = true
  try {
    await RdOrderSplitApi.bindOutput({
      id: result.value.id,
      version: result.value.version,
      resultLineId: line.value.id,
      productionOutputId: selected.value.id
    })
    message.success('生产实绩绑定成功')
    visible.value = false
    emit('success')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.table-scroll {
  max-width: 100%;
  overflow-x: auto;
}
.table-scroll :deep(.el-table) {
  min-width: 760px;
}
</style>
