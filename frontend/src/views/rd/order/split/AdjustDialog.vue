<template>
  <Dialog title="手工调整拆分计划" v-model="visible" width="min(900px, calc(100vw - 24px))">
    <el-descriptions v-if="result" :column="3" border class="mb-16px">
      <el-descriptions-item label="拆分单号">{{ result.splitNo }}</el-descriptions-item>
      <el-descriptions-item label="预期产出">{{ result.expectedOutputQty }}</el-descriptions-item>
      <el-descriptions-item label="当前计划">{{
        result.finalPlannedOutputQty
      }}</el-descriptions-item>
    </el-descriptions>
    <div class="table-scroll" v-loading="loading">
      <el-table :data="lines" border>
        <el-table-column label="炉层" prop="furnaceLayer" min-width="110" />
        <el-table-column label="计算基数" prop="calculatedBaseQty" align="right" min-width="120" />
        <el-table-column label="调整数量" min-width="180">
          <template #default="scope">
            <el-input-number
              v-model="scope.row.manualAdjustmentQty"
              :precision="6"
              class="!w-1/1"
            />
          </template>
        </el-table-column>
        <el-table-column label="调整后计划" align="right" min-width="130">
          <template #default="scope">
            <span :class="{ invalid: finalQty(scope.row) < 0 }">{{
              finalQty(scope.row).toFixed(6)
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="调整原因" min-width="240">
          <template #default="scope">
            <el-input
              v-model="scope.row.adjustmentReason"
              maxlength="255"
              placeholder="有调整时必填"
            />
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button type="primary" :loading="loading" @click="submit">保 存</el-button>
      <el-button @click="visible = false">取 消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { RdOrderSplitApi } from '@/api/rd/order/split'
import type { RdOrderSplitResultLineVO, RdOrderSplitResultVO } from '@/api/rd/order/split'

defineOptions({ name: 'RdOrderSplitAdjustDialog' })

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const result = ref<RdOrderSplitResultVO>()
const lines = ref<RdOrderSplitResultLineVO[]>([])

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  try {
    const data: RdOrderSplitResultVO = await RdOrderSplitApi.getDetail(id)
    result.value = data
    lines.value = (data.lines || []).map((line) => ({ ...line }))
  } finally {
    loading.value = false
  }
}
defineExpose({ open })

const finalQty = (line: RdOrderSplitResultLineVO) =>
  Number(line.calculatedBaseQty || 0) + Number(line.manualAdjustmentQty || 0)

const emit = defineEmits(['success'])
const submit = async () => {
  if (!result.value) return
  if (lines.value.some((line) => finalQty(line) < 0)) {
    message.warning('调整后计划数量不能小于 0')
    return
  }
  if (
    lines.value.some(
      (line) => Number(line.manualAdjustmentQty) !== 0 && !line.adjustmentReason?.trim()
    )
  ) {
    message.warning('存在手工调整时必须填写调整原因')
    return
  }
  loading.value = true
  try {
    await RdOrderSplitApi.adjust({
      id: result.value.id,
      version: result.value.version,
      lines: lines.value.map((line) => ({
        lineId: line.id,
        manualAdjustmentQty: Number(line.manualAdjustmentQty || 0),
        adjustmentReason: line.adjustmentReason
      }))
    })
    message.success('拆分计划调整成功')
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
  min-width: 720px;
}
.invalid {
  color: var(--el-color-danger);
  font-weight: 600;
}
</style>
