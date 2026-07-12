<template>
  <el-drawer v-model="visible" :title="title" size="900px" class="order-split-detail-drawer">
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="3" border>
        <el-descriptions-item label="拆分单号">{{ detail.splitNo }}</el-descriptions-item>
        <el-descriptions-item label="拆分日期">{{ detail.splitDate }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusMap[detail.status]?.type || 'info'">{{
            statusMap[detail.status]?.label || detail.status
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="销售订单">{{ detail.saleOrderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ detail.productModel }}</el-descriptions-item>
        <el-descriptions-item label="投炉量">{{ detail.furnaceLoadQty }}</el-descriptions-item>
        <el-descriptions-item label="成品率">{{ detail.yieldRate }}%</el-descriptions-item>
        <el-descriptions-item label="预期产出">{{ detail.expectedOutputQty }}</el-descriptions-item>
        <el-descriptions-item label="最终计划">{{
          detail.finalPlannedOutputQty
        }}</el-descriptions-item>
        <el-descriptions-item label="已绑定实绩">{{
          detail.actualBoundOutputQty
        }}</el-descriptions-item>
        <el-descriptions-item label="计划偏差">{{ detail.varianceQty }}</el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">炉层计划</el-divider>
      <div class="table-scroll">
        <el-table :data="detail?.lines || []" border>
          <el-table-column label="顺序" prop="sequence" width="70" align="center" />
          <el-table-column label="炉层" prop="furnaceLayer" min-width="100" />
          <el-table-column label="规则" min-width="140">
            <template #default="scope">{{ allocationLabel(scope.row) }}</template>
          </el-table-column>
          <el-table-column
            label="计算基数"
            prop="calculatedBaseQty"
            align="right"
            min-width="110"
          />
          <el-table-column
            label="手工调整"
            prop="manualAdjustmentQty"
            align="right"
            min-width="110"
          />
          <el-table-column label="最终计划" prop="finalPlannedQty" align="right" min-width="110" />
          <el-table-column label="已绑定" prop="actualBoundQty" align="right" min-width="100" />
          <el-table-column label="调整原因" prop="adjustmentReason" min-width="150" />
          <el-table-column
            v-if="detail?.status === 'CONFIRMED'"
            label="操作"
            fixed="right"
            width="90"
            align="center"
          >
            <template #default="scope">
              <el-button
                link
                type="primary"
                @click="detail && bindDialogRef?.open(scope.row, detail)"
                v-hasPermi="['rd:order-split:bind']"
                >绑定实绩</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-divider content-position="left">生产实绩绑定</el-divider>
      <div class="table-scroll">
        <el-table :data="detail?.outputBindings || []" border empty-text="暂无绑定记录">
          <el-table-column label="生产实绩" min-width="110">
            <template #default="scope">{{
              scope.row.productionOutputNo || scope.row.furnaceNo
            }}</template>
          </el-table-column>
          <el-table-column label="炉层" prop="furnaceLayer" width="90" />
          <el-table-column label="实绩数量" prop="outputQty" align="right" width="110" />
          <el-table-column label="状态" prop="status" align="center" width="90">
            <template #default="scope">{{
              scope.row.status === 'ACTIVE' ? '有效' : '已解绑'
            }}</template>
          </el-table-column>
          <el-table-column label="绑定人/时间" min-width="180">
            <template #default="scope"
              >{{ scope.row.bindBy || '-' }} / {{ scope.row.bindTime || '-' }}</template
            >
          </el-table-column>
          <el-table-column label="解绑原因" prop="unbindReason" min-width="150" />
          <el-table-column label="操作" fixed="right" width="80" align="center">
            <template #default="scope">
              <el-button
                v-if="scope.row.status === 'ACTIVE'"
                link
                type="danger"
                @click="handleUnbind(scope.row)"
                v-hasPermi="['rd:order-split:bind']"
                >解绑</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-divider content-position="left">审核状态</el-divider>
      <el-descriptions v-if="detail" :column="2" border>
        <el-descriptions-item label="创建人/时间"
          >{{ detail.createBy || '-' }} / {{ detail.createTime || '-' }}</el-descriptions-item
        >
        <el-descriptions-item label="确认人/时间"
          >{{ detail.confirmBy || '-' }} / {{ detail.confirmTime || '-' }}</el-descriptions-item
        >
        <el-descriptions-item label="取消人/时间"
          >{{ detail.cancelBy || '-' }} / {{ detail.cancelTime || '-' }}</el-descriptions-item
        >
        <el-descriptions-item label="取消原因">{{
          detail.cancelReason || '-'
        }}</el-descriptions-item>
      </el-descriptions>
    </div>
  </el-drawer>
  <BindDialog ref="bindDialogRef" @success="handleBindingSuccess" />
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { RdOrderSplitApi } from '@/api/rd/order/split'
import type {
  RdOrderSplitOutputBindVO,
  RdOrderSplitResultLineVO,
  RdOrderSplitResultVO
} from '@/api/rd/order/split'
import BindDialog from './BindDialog.vue'

defineOptions({ name: 'RdOrderSplitDetailDrawer' })

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const detail = ref<RdOrderSplitResultVO>()
const bindDialogRef = ref<InstanceType<typeof BindDialog>>()
const title = computed(() => (detail.value ? `订单拆分 - ${detail.value.splitNo}` : '订单拆分详情'))
const statusMap: Record<string, { label: string; type: 'info' | 'success' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  CONFIRMED: { label: '已确认', type: 'success' },
  CANCELLED: { label: '已取消', type: 'danger' }
}

const loadDetail = async (id: number) => {
  loading.value = true
  try {
    detail.value = await RdOrderSplitApi.getDetail(id)
  } finally {
    loading.value = false
  }
}
const open = async (id: number) => {
  visible.value = true
  await loadDetail(id)
}
defineExpose({ open })

const allocationLabel = (line: RdOrderSplitResultLineVO) =>
  `${line.allocationType === 'FIXED_QTY' ? '固定数量' : '比例权重'} ${line.allocationValue}`

const emit = defineEmits(['changed'])
const handleBindingSuccess = async () => {
  if (!detail.value) return
  await loadDetail(detail.value.id)
  emit('changed')
}
const handleUnbind = async (binding: RdOrderSplitOutputBindVO) => {
  if (!detail.value) return
  const { value } = await message.prompt('请输入解绑原因（必填）', '解绑生产实绩')
  await RdOrderSplitApi.unbindOutput({
    id: detail.value.id,
    version: detail.value.version,
    productionOutputId: binding.productionOutputId,
    reason: value
  })
  message.success('生产实绩解绑成功')
  await handleBindingSuccess()
}
</script>

<style scoped>
:global(.order-split-detail-drawer) {
  max-width: calc(100vw - 12px);
}
.table-scroll {
  max-width: 100%;
  overflow-x: auto;
}
.table-scroll :deep(.el-table) {
  min-width: 760px;
}
</style>
