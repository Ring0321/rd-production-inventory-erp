<template>
  <Dialog title="扫码出库" v-model="visible" width="min(980px, calc(100vw - 24px))">
    <div v-loading="loading">
      <el-descriptions v-if="detail" :column="4" border class="mb-16px">
        <el-descriptions-item label="出库单">{{ detail.order.outboundNo }}</el-descriptions-item>
        <el-descriptions-item label="销售订单">{{
          detail.order.orderNo || detail.order.orderId
        }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.order.customerName }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ detail.order.productModel }}</el-descriptions-item>
      </el-descriptions>
      <el-input
        ref="scanInputRef"
        v-model="packageCode"
        size="large"
        clearable
        placeholder="请扫描二维码或输入包码后按回车"
        @keyup.enter="scan"
      >
        <template #prefix><Icon icon="ep:full-screen" /></template>
        <template #append
          ><el-button type="primary" :loading="scanning" @click="scan">录入</el-button></template
        >
      </el-input>
      <el-alert
        v-if="feedback"
        class="my-12px"
        :type="feedbackType"
        :closable="false"
        show-icon
        :title="feedback"
      />
      <div class="summary-bar">
        <span
          >已扫描 <b>{{ detail?.lines.length || 0 }}</b> 包</span
        >
        <span
          >总重量 <b>{{ totalWeight }}</b> kg</span
        >
      </div>
      <el-table :data="detail?.lines || []" max-height="420">
        <el-table-column type="index" label="#" width="55" />
        <el-table-column prop="packageCode" label="包码" min-width="155" />
        <el-table-column prop="locationCode" label="货位" width="100" />
        <el-table-column prop="furnaceNo" label="炉号" width="120" />
        <el-table-column prop="furnaceLayer" label="炉层" width="100" />
        <el-table-column prop="packageWeight" label="重量(kg)" width="110" align="right" />
        <el-table-column prop="scannerName" label="扫描人" width="110" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="scope">
            <el-button link type="danger" @click="remove(scope.row.packageId)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <template #footer>
      <el-button type="primary" :disabled="!detail?.lines.length" @click="submitOrder"
        >提交复核</el-button
      >
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { RdOutboundOrderApi, type RdOutboundOrderDetailVO } from '@/api/rd/outbound'

defineOptions({ name: 'RdOutboundScanDialog' })
const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const scanning = ref(false)
const packageCode = ref('')
const feedback = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const detail = ref<RdOutboundOrderDetailVO>()
const scanInputRef = ref()
const totalWeight = computed(() =>
  (detail.value?.lines || []).reduce((sum, item) => sum + Number(item.packageWeight), 0).toFixed(3)
)
const focus = () => nextTick(() => scanInputRef.value?.focus())
const load = async () => {
  if (!detail.value?.order.id) return
  detail.value = await RdOutboundOrderApi.getDetail(detail.value.order.id)
}
const open = async (id: number) => {
  visible.value = true
  loading.value = true
  try {
    detail.value = await RdOutboundOrderApi.getDetail(id)
    feedback.value = ''
  } finally {
    loading.value = false
    focus()
  }
}
defineExpose({ open })
const beep = (success: boolean) => {
  const AudioContextClass = window.AudioContext || (window as any).webkitAudioContext
  if (!AudioContextClass) return
  const context = new AudioContextClass()
  const oscillator = context.createOscillator()
  oscillator.frequency.value = success ? 880 : 220
  oscillator.connect(context.destination)
  oscillator.start()
  oscillator.stop(context.currentTime + (success ? 0.08 : 0.18))
}
const scan = async () => {
  const code = packageCode.value.trim()
  if (!code || !detail.value) return
  scanning.value = true
  try {
    await RdOutboundOrderApi.scan({
      id: detail.value.order.id,
      packageCode: code,
      deviceCode: 'WEB-SCANNER'
    })
    feedbackType.value = 'success'
    feedback.value = `包码 ${code} 扫描成功`
    beep(true)
    packageCode.value = ''
    await load()
  } catch (error: any) {
    feedbackType.value = 'error'
    feedback.value = error?.msg || error?.message || '扫描失败，请核对包码和库存状态'
    beep(false)
  } finally {
    scanning.value = false
    focus()
  }
}
const remove = async (packageId: number) => {
  if (!detail.value) return
  await RdOutboundOrderApi.removeScan(detail.value.order.id, packageId)
  await load()
  focus()
}
const emit = defineEmits(['success'])
const submitOrder = async () => {
  if (!detail.value) return
  await message.confirm(`确认提交 ${detail.value.lines.length} 包进行出库复核吗？`)
  await RdOutboundOrderApi.submit(detail.value.order.id, detail.value.order.version)
  message.success('出库单已提交复核')
  visible.value = false
  emit('success')
}
</script>

<style scoped>
.summary-bar {
  display: flex;
  gap: 32px;
  padding: 12px 4px;
  color: var(--el-text-color-regular);
}
.summary-bar b {
  color: var(--el-color-primary);
  font-size: 18px;
}
</style>
