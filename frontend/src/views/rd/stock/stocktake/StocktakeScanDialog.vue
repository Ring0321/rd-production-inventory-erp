<template>
  <Dialog
    v-model="visible"
    title="扫码实盘"
    width="min(390px, calc(100vw - 24px))"
    class="stocktake-scan-dialog"
  >
    <div v-loading="loading" class="scan-panel">
      <el-descriptions v-if="detail" :column="1" border size="small" class="mb-12px">
        <el-descriptions-item label="盘点单号">{{ detail.order.stocktakeNo }}</el-descriptions-item>
        <el-descriptions-item label="盘点货位">{{ detail.order.locationCode }}</el-descriptions-item>
        <el-descriptions-item label="冻结快照">{{ detail.order.snapshotTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-alert
        class="mb-12px"
        type="info"
        :closable="false"
        show-icon
        title="扫码仅记录实盘结果，不直接改库存；差异需提交、复核、调整后才会生效。"
      />

      <el-input
        ref="scanInputRef"
        v-model="packageCode"
        size="large"
        clearable
        placeholder="请扫码或输入包码后回车"
        @keyup.enter="scan"
      >
        <template #prefix><Icon icon="ep:full-screen" /></template>
        <template #append>
          <el-button type="primary" :loading="scanning" @click="scan">录入</el-button>
        </template>
      </el-input>

      <el-alert
        v-if="feedback"
        class="my-12px"
        :type="feedbackType"
        :closable="false"
        show-icon
        :title="feedback"
      />

      <div class="stat-grid">
        <div class="stat-card">
          <span>应有包数</span>
          <strong>{{ formatMetric(detail?.order.bookPackageQty) }}</strong>
        </div>
        <div class="stat-card">
          <span>实扫包数</span>
          <strong>{{ formatMetric(detail?.order.countedPackageQty) }}</strong>
        </div>
        <div class="stat-card">
          <span>短少包数</span>
          <strong class="danger">{{ formatMetric(detail?.order.shortageQty) }}</strong>
        </div>
        <div class="stat-card">
          <span>盘盈包数</span>
          <strong class="warning">{{ formatMetric(detail?.order.surplusQty) }}</strong>
        </div>
        <div class="stat-card">
          <span>应有重量</span>
          <strong>{{ formatMetric(detail?.order.bookWeight, 3) }}</strong>
        </div>
        <div class="stat-card">
          <span>实扫重量</span>
          <strong>{{ formatMetric(detail?.order.countedWeight, 3) }}</strong>
        </div>
      </div>

      <div class="line-header">
        <span>扫码明细</span>
        <span>{{ scannedLines.length }} 条</span>
      </div>
      <el-scrollbar max-height="300px">
        <div v-if="scannedLines.length" class="line-list">
          <div v-for="line in scannedLines" :key="line.id" class="line-item">
            <div class="line-main">
              <strong>{{ line.packageCode }}</strong>
              <el-tag size="small" :type="stocktakeResultMap[line.result || 'MATCH']?.type || 'info'">
                {{ stocktakeResultMap[line.result || 'MATCH']?.label || line.result || '待盘' }}
              </el-tag>
            </div>
            <div class="line-sub">
              {{ line.customerName || '-' }} / {{ line.productModel || '-' }}
            </div>
            <div class="line-meta">
              <span>应有 {{ formatMetric(line.bookQty) }}</span>
              <span>实扫 {{ formatMetric(line.countedQty) }}</span>
              <span :class="getDifferenceClass(line)">差异 {{ formatMetric(line.differenceQty) }}</span>
            </div>
            <div class="line-meta">
              <span>{{ line.furnaceNo || '-' }} / {{ line.furnaceLayer || '-' }}</span>
              <span>{{ formatMetric(line.packageWeight, 3) }} kg</span>
            </div>
            <div class="line-sub">{{ line.scannerName || '-' }} {{ line.scanTime || '' }}</div>
          </div>
        </div>
        <div v-else class="empty-state">还没有扫码记录。</div>
      </el-scrollbar>
    </div>
    <template #footer>
      <el-button
        type="primary"
        :disabled="!detail || scannedLines.length === 0"
        @click="submitOrder"
      >
        提交复核
      </el-button>
      <el-button @click="visible = false">关闭</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useMessage } from '@/hooks/web/useMessage'
import { RdStocktakeApi, type RdStocktakeDetailVO } from '@/api/rd/stock/stocktake'
import {
  formatMetric,
  getDifferenceClass,
  stocktakeResultMap
} from './helpers'

defineOptions({ name: 'RdStocktakeScanDialog' })

const emit = defineEmits<{
  success: []
}>()

const message = useMessage()
const visible = ref(false)
const loading = ref(false)
const scanning = ref(false)
const packageCode = ref('')
const feedback = ref('')
const feedbackType = ref<'success' | 'error'>('success')
const detail = ref<RdStocktakeDetailVO>()
const scanInputRef = ref()
const changed = ref(false)

const scannedLines = computed(() =>
  (detail.value?.lines || []).filter((line) => Number(line.countedQty || 0) > 0)
)

const focus = () => nextTick(() => scanInputRef.value?.focus())

watch(visible, (value) => {
  if (value) {
    focus()
  } else if (changed.value) {
    changed.value = false
    emit('success')
  }
})

const load = async () => {
  if (!detail.value?.order.id) return
  detail.value = await RdStocktakeApi.getDetail(detail.value.order.id)
}

const open = async (id: number) => {
  visible.value = true
  loading.value = true
  packageCode.value = ''
  feedback.value = ''
  changed.value = false
  try {
    detail.value = await RdStocktakeApi.getDetail(id)
  } finally {
    loading.value = false
    focus()
  }
}

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
    await RdStocktakeApi.scan({
      id: detail.value.order.id,
      packageCode: code,
      deviceCode: 'WEB-SCANNER'
    })
    feedbackType.value = 'success'
    feedback.value = `包码 ${code} 录入成功`
    changed.value = true
    beep(true)
    packageCode.value = ''
    await load()
  } catch (error: any) {
    feedbackType.value = 'error'
    feedback.value = error?.msg || error?.message || '扫码失败，请核对包码、货位冻结状态和盘点单状态。'
    beep(false)
  } finally {
    scanning.value = false
    focus()
  }
}

const submitOrder = async () => {
  if (!detail.value) return
  await message.confirm(`确认提交盘点单 ${detail.value.order.stocktakeNo} 进入双人复核吗？`)
  await RdStocktakeApi.submit({
    id: detail.value.order.id,
    version: detail.value.order.version
  })
  message.success('盘点单已提交复核')
  changed.value = false
  visible.value = false
  emit('success')
}

defineExpose({ open })
</script>

<style scoped>
.scan-panel {
  overflow-x: hidden;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin: 12px 0;
}

.stat-card {
  min-width: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 10px 12px;
  background: var(--el-fill-color-blank);
}

.stat-card span {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.stat-card strong {
  display: block;
  margin-top: 4px;
  color: var(--el-text-color-primary);
  font-size: 18px;
  line-height: 1.2;
}

.stat-card .danger {
  color: var(--el-color-danger);
}

.stat-card .warning {
  color: var(--el-color-warning);
}

.line-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 4px 0 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.line-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.line-item {
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  overflow: hidden;
}

.line-main,
.line-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  align-items: center;
}

.line-main strong,
.line-sub,
.line-meta span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.line-sub {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.line-meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.difference-shortage {
  color: var(--el-color-danger);
  font-weight: 600;
}

.difference-surplus {
  color: var(--el-color-warning);
  font-weight: 600;
}

.difference-match {
  color: var(--el-color-success);
  font-weight: 600;
}

.empty-state {
  padding: 32px 0;
  text-align: center;
  color: var(--el-text-color-secondary);
}
</style>
