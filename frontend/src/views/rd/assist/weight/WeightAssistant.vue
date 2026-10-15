<template>
  <section class="weight-assistant" aria-label="配吨建议">
    <el-alert v-if="!allowed" title="当前身份无配吨权限" type="warning" :closable="false" />
    <template v-else>
      <el-form inline @submit.prevent="search">
        <el-form-item label="提货单号">
          <el-input v-model="keyword" clearable maxlength="64" aria-label="提货单号" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="loading" @click="search"
            ><Icon icon="ep:search" class="mr-1" />查询</el-button
          >
        </el-form-item>
      </el-form>
      <el-table
        :data="slips"
        v-loading="loading"
        row-key="id"
        max-height="260"
        highlight-current-row
        @row-click="choose"
      >
        <el-table-column prop="slipNo" label="提货单" min-width="160" />
        <el-table-column prop="customerName" label="客户" min-width="150" />
        <el-table-column prop="requestedWeight" label="申请重量 (kg)" min-width="140" />
        <el-table-column label="选择" width="84">
          <template #default="scope"
            ><el-radio
              :model-value="slipId"
              :value="scope.row.id"
              :label="scope.row.id"
              :aria-label="`选择 ${scope.row.slipNo}`"
              @click.stop
              @change="selectSlip(scope.row.id)"
              ><span class="sr-only">选择</span></el-radio
            ></template
          >
        </el-table-column>
      </el-table>
      <el-pagination
        v-if="total > 20"
        v-model:current-page="page"
        :page-size="20"
        :total="total"
        layout="prev, pager, next"
        @current-change="loadSlips"
      />
      <div v-loading="detailLoading" class="weight-selection">
        <template v-if="detail">
          <div class="weight-heading">
            <h3>{{ detail.slip.slipNo }} · {{ detail.slip.customerName }}</h3>
            <el-button v-if="canOpenOriginal" @click="openOriginal"
              ><Icon icon="ep:view" class="mr-1" />查看原任务</el-button
            >
          </div>
          <el-form class="weight-form" label-position="top" @submit.prevent="suggest">
            <el-form-item label="提货行 / 型号">
              <el-select v-model="lineId" placeholder="选择原提货行" aria-label="提货行">
                <el-option
                  v-for="item in detail.lines"
                  :key="item.id"
                  :value="item.id"
                  :label="`${item.lineNo ?? item.id} · ${item.productModel}${item.furnaceNo ? ` · ${item.furnaceNo}` : ''}`"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="目标净重 (kg)">
              <el-input-number
                v-model="target"
                :min="0.001"
                :max="line?.requestedWeight || 1000000"
                :step="1"
                :disabled="!line?.requestedWeight"
                :controls="true"
                aria-label="目标净重 kg"
              />
            </el-form-item>
            <el-form-item label="容差 (kg)">
              <el-input-number
                v-model="tolerance"
                :min="0"
                :max="line?.requestedWeight || 0"
                :step="0.001"
                aria-label="容差 kg"
              />
            </el-form-item>
            <el-form-item class="weight-submit">
              <el-button type="primary" :disabled="!canSuggest" :loading="solving" @click="suggest"
                ><Icon icon="ep:operation" class="mr-1" />计算建议</el-button
              >
            </el-form-item>
          </el-form>
          <dl v-if="line" class="weight-facts">
            <div
              ><dt>本行授权重量</dt><dd>{{ line.requestedWeight ?? '未登记' }} kg</dd></div
            >
            <div
              ><dt>本行申请包数上限</dt><dd>{{ line.requestedPackageCount ?? '未登记' }}</dd></div
            >
          </dl>
          <el-alert
            v-if="!line?.requestedWeight"
            title="本行未登记授权重量，不能计算配吨"
            type="warning"
            :closable="false"
          />
        </template>
        <el-empty v-else :image-size="64" description="未选择提货单" />
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="weight-error" />
      <section v-if="result" class="weight-result" aria-live="polite">
        <el-alert
          :title="weightStatusLabel(result.solverStatus)"
          :type="result.withinTolerance ? 'success' : 'warning'"
          :description="result.message"
          :closable="false"
        />
        <dl class="weight-facts">
          <div
            ><dt>建议总净重</dt><dd>{{ kg(result.totalWeightKg) }}</dd></div
          >
          <div
            ><dt>总重减目标</dt><dd>{{ kg(result.deviationKg) }}</dd></div
          >
          <div
            ><dt>{{ result.withinTolerance ? '建议短缺净重' : '库存最小短缺' }}</dt
            ><dd>{{ kg(result.shortfallKg) }}</dd></div
          >
          <div
            ><dt>可用候选包数</dt><dd>{{ result.candidateCount ?? '未确定' }}</dd></div
          >
          <div
            ><dt>已扫描包数</dt
            ><dd>{{ result.scannedCount }}{{ result.candidateScanComplete ? '' : '+' }}</dd></div
          >
          <div
            ><dt>求解预算</dt
            ><dd>{{ result.solverTimeLimitMs }} ms / {{ result.candidateLimit }} 包</dd></div
          >
          <div
            ><dt>事实读取时间</dt><dd>{{ result.factsAt }}</dd></div
          >
        </dl>
        <el-table
          v-if="result.bags.length"
          :data="result.bags"
          row-key="packageId"
          max-height="480"
        >
          <el-table-column prop="packageCode" label="原包码" min-width="180" />
          <el-table-column prop="packageWeightKg" label="实际净重 (kg)" min-width="140" />
          <el-table-column prop="locationCode" label="实际库位" min-width="130" />
          <el-table-column prop="packageId" label="包件 ID" min-width="100" />
        </el-table>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { Icon } from '@/components/Icon'
import { useWeightAssistant, weightStatusLabel } from './useWeightAssistant'

defineOptions({ name: 'WeightAssistant' })
const {
  allowed,
  slips,
  detail,
  result,
  slipId,
  lineId,
  line,
  target,
  tolerance,
  keyword,
  page,
  total,
  loading,
  detailLoading,
  solving,
  error,
  canSuggest,
  canOpenOriginal,
  loadSlips,
  selectSlip,
  suggest,
  openOriginal
} = useWeightAssistant()
const search = () => {
  page.value = 1
  void loadSlips()
}
const choose = (row: { id: number }) => {
  void selectSlip(row.id)
}
const kg = (value: number | null) => (value == null ? '未确定' : `${value.toFixed(3)} kg`)
onMounted(loadSlips)
</script>

<style scoped>
.weight-assistant {
  min-width: 0;
}

.weight-selection,
.weight-result {
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.weight-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.weight-heading h3 {
  min-width: 0;
  margin: 0;
  font-size: 16px;
  overflow-wrap: anywhere;
}

.weight-form {
  display: grid;
  grid-template-columns: minmax(160px, 2fr) repeat(2, minmax(140px, 1fr)) auto;
  gap: 12px;
  align-items: end;
}

.weight-form .el-input-number,
.weight-form .el-select {
  width: 100%;
}

.weight-facts {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 28px;
  margin: 16px 0;
}

.weight-facts div {
  max-width: 100%;
  min-width: 140px;
}

.weight-facts dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.weight-facts dd {
  margin: 4px 0 0;
  font-size: 14px;
  overflow-wrap: anywhere;
}

.weight-error {
  margin-top: 12px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

:deep(.el-pagination) {
  max-width: 100%;
  margin-top: 12px;
}

@media (width <= 760px) {
  .weight-form {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .weight-form .el-form-item:first-child {
    grid-column: 1 / -1;
  }

  .weight-heading {
    flex-wrap: wrap;
  }
}

@media (width <= 390px) {
  .weight-form {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
