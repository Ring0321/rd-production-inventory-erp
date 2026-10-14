<script setup lang="ts">
import { Check, Close, DocumentAdd, RefreshRight } from '@element-plus/icons-vue'
import type { LoadingTaskDetail } from '@/api/rd/loading'
import type { LoadingReduction } from '@/api/rd/loading/reduction'
import { formatDate } from '@/utils/formatTime'
import {
  formatReductionWeight as weight,
  reductionStatusLabel,
  useLoadingReduction
} from './useLoadingReduction'

const props = withDefaults(
  defineProps<{
    taskDetail?: LoadingTaskDetail
    pickupSlipId?: number
    active?: boolean
    sessionCurrent?: () => boolean
  }>(),
  { active: true }
)
const {
  records,
  selected,
  reason,
  reviewRemarks,
  pending,
  reading,
  writing,
  error,
  storageError,
  feedback,
  candidates,
  preview,
  canQuery,
  showCreate,
  canCreate,
  canReview,
  blockedByRecord,
  create,
  review,
  refresh,
  toggle
} = useLoadingReduction({
  taskDetail: () => props.taskDetail,
  pickupSlipId: () => props.pickupSlipId,
  active: () => props.active,
  sessionCurrent: () => props.sessionCurrent?.() ?? true
})
const statusType = (record: LoadingReduction) =>
  record.effectiveStatus === 'STALE'
    ? 'info'
    : record.status === 'APPROVED'
      ? 'success'
      : record.status === 'REJECTED'
        ? 'danger'
        : 'warning'
</script>

<template>
  <section class="reduction-panel" aria-label="少装申请与商务核定" :aria-busy="reading || writing">
    <header class="panel-heading">
      <h3>少装申请与核定</h3>
      <el-tooltip :content="pending ? '查询核对原少装请求' : '刷新少装清单'">
        <el-button
          :loading="reading"
          :disabled="!canQuery || writing"
          aria-label="刷新少装清单"
          @click="refresh"
        >
          <el-icon><RefreshRight /></el-icon>
        </el-button>
      </el-tooltip>
    </header>
    <el-alert
      v-if="storageError || error"
      :title="storageError || error"
      type="warning"
      :closable="false"
      role="alert"
    />
    <el-alert
      v-if="feedback && !pending"
      :title="feedback"
      type="info"
      :closable="false"
      role="status"
    />
    <section v-if="pending" class="pending-band" aria-label="原少装写入待核">
      <strong>少装写入结果待核</strong>
      <p
        >原任务 ID {{ pending.taskId }} ·
        {{ pending.kind === 'create' ? '申请少装' : '商务核定' }}</p
      >
      <p
        >原请求
        {{
          pending.kind === 'create'
            ? pending.request.idempotencyKey
            : `${pending.request.id} / 版本 ${pending.request.version}`
        }}</p
      >
      <p>未查到原请求不代表未写入</p>
      <el-button :disabled="!canQuery || writing" :loading="reading" @click="refresh">
        <el-icon><RefreshRight /></el-icon>查询核对原请求
      </el-button>
    </section>
    <p v-if="!canQuery" class="muted" role="status">当前页面、身份或查询权限不可用</p>
    <el-form v-if="showCreate" label-position="top" class="proposal" @submit.prevent="create">
      <h4>拟少装清单</h4>
      <el-alert
        v-if="blockedByRecord"
        title="原任务已有待核定或已核定待变更申请"
        type="warning"
        :closable="false"
      />
      <div class="candidate-list" role="group" aria-label="拟少装未核验包">
        <div v-for="line in candidates" :key="line.id" class="candidate-row">
          <el-checkbox
            :model-value="selected.includes(line.id)"
            :disabled="!canCreate"
            :aria-label="`拟少装包 ${line.packageCode}`"
            @change="toggle(line.id)"
          >
            <span class="package-code">{{ line.packageCode }}</span>
          </el-checkbox>
          <span class="candidate-meta"
            >{{ line.plannedLocationCode }} · {{ line.productModel }} · {{ line.furnaceNo }} /
            {{ line.furnaceLayer }} · {{ weight(line.packageWeight) }} kg</span
          >
        </div>
        <p v-if="!candidates.length" class="muted">暂无可申请的未核验包</p>
      </div>
      <dl class="quantity-grid">
        <div
          ><dt>原计划</dt><dd>{{ preview.originalPackageCount }} 包</dd
          ><dd>{{ weight(preview.originalWeight) }} kg</dd></div
        >
        <div
          ><dt>拟减少</dt><dd>{{ preview.reducedPackageCount }} 包</dd
          ><dd>{{ weight(preview.reducedWeight) }} kg</dd></div
        >
        <div
          ><dt>拟剩余</dt><dd>{{ preview.remainingPackageCount }} 包</dd
          ><dd>{{ weight(preview.remainingWeight) }} kg</dd></div
        >
      </dl>
      <el-form-item label="真实少装原因" required>
        <el-input
          v-model="reason"
          aria-label="真实少装原因"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          :disabled="!canCreate"
        />
      </el-form-item>
      <el-button
        native-type="submit"
        type="warning"
        :loading="writing"
        :disabled="!canCreate || !selected.length || !reason.trim()"
      >
        <el-icon><DocumentAdd /></el-icon>申请少装
      </el-button>
    </el-form>
    <p v-if="canQuery && !reading && !records.length" class="muted" role="status"
      >暂无少装申请记录</p
    >
    <article
      v-for="record in records"
      :key="record.id"
      class="reduction-record"
      :aria-label="`少装申请 ${record.id}`"
    >
      <header class="record-heading">
        <h4>{{ record.taskNo }}</h4>
        <el-tag :type="statusType(record)">{{ reductionStatusLabel(record) }}</el-tag>
      </header>
      <dl class="record-meta">
        <dt>提货单 / 客户</dt><dd>{{ record.pickupSlipNo }} / {{ record.customerName }}</dd>
        <dt>版本快照</dt
        ><dd
          >任务 {{ record.taskVersion }} · 计划 {{ record.planVersion }} · 提货单
          {{ record.slipVersion }} · 申请 {{ record.version }}</dd
        >
      </dl>
      <dl class="quantity-grid">
        <div
          ><dt>原计划</dt><dd>{{ record.originalPackageCount }} 包</dd
          ><dd>{{ weight(record.originalWeight) }} kg</dd></div
        >
        <div
          ><dt>拟减少</dt><dd>{{ record.reducedPackageCount }} 包</dd
          ><dd>{{ weight(record.reducedWeight) }} kg</dd></div
        >
        <div
          ><dt>拟剩余</dt><dd>{{ record.remainingPackageCount }} 包</dd
          ><dd>{{ weight(record.remainingWeight) }} kg</dd></div
        >
      </dl>
      <dl class="record-meta">
        <dt>少装原因</dt><dd>{{ record.reason }}</dd> <dt>提出人 / 时间</dt
        ><dd
          >{{ record.proposerName || record.proposerId }} /
          {{ formatDate(record.proposedTime) || '-' }}</dd
        >
        <dt>核定人 / 时间</dt
        ><dd
          >{{ record.reviewerName || record.reviewerId || '-' }} /
          {{ formatDate(record.reviewTime) || '-' }}</dd
        >
        <dt>核定说明</dt><dd>{{ record.reviewRemark || '-' }}</dd>
      </dl>
      <el-collapse>
        <el-collapse-item :name="record.id" :title="`计划清单快照 · ${record.lines.length} 包`">
          <div class="snapshot-scroll">
            <el-table :data="record.lines" size="small" max-height="320" row-key="id">
              <el-table-column label="拟处置" width="90"
                ><template #default="scope">{{
                  scope.row.removed ? '拟减少' : '拟保留'
                }}</template></el-table-column
              >
              <el-table-column prop="packageCode" label="包码" min-width="180" />
              <el-table-column prop="plannedLocationCode" label="原库位" width="110" />
              <el-table-column prop="productModel" label="型号" width="100" />
              <el-table-column prop="furnaceNo" label="炉号" width="110" />
              <el-table-column prop="furnaceLayer" label="炉层" width="80" />
              <el-table-column label="净重 kg" width="120" align="right"
                ><template #default="scope">{{
                  weight(scope.row.packageWeight)
                }}</template></el-table-column
              >
              <el-table-column prop="lineStatus" label="原状态" width="110" />
            </el-table>
          </div>
        </el-collapse-item>
      </el-collapse>
      <el-form
        v-if="canReview(record, 'APPROVED') || canReview(record, 'REJECTED')"
        class="review-form"
        label-position="top"
        @submit.prevent
      >
        <el-form-item label="商务核定说明" required>
          <el-input
            v-model="reviewRemarks[record.id]"
            :aria-label="`少装申请 ${record.id} 核定说明`"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
          />
        </el-form-item>
        <div class="review-actions">
          <el-button
            v-if="canReview(record, 'APPROVED')"
            type="primary"
            :disabled="!reviewRemarks[record.id]?.trim()"
            @click="review(record, 'APPROVED')"
            ><el-icon><Check /></el-icon>核定通过</el-button
          >
          <el-button
            v-if="canReview(record, 'REJECTED')"
            type="danger"
            plain
            :disabled="!reviewRemarks[record.id]?.trim()"
            @click="review(record, 'REJECTED')"
            ><el-icon><Close /></el-icon>商务退回</el-button
          >
        </div>
      </el-form>
    </article>
  </section>
</template>

<style scoped>
.reduction-panel {
  max-width: 100%;
  min-width: 0;
  padding-top: 12px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color);
  overflow-wrap: anywhere;
}

.panel-heading,
.record-heading,
.review-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

h3,
h4 {
  min-width: 0;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.proposal,
.reduction-record,
.review-form {
  min-width: 0;
  padding: 12px 0;
}

.reduction-record + .reduction-record {
  border-top: 1px solid var(--el-border-color-lighter);
}

.pending-band {
  padding: 12px;
  margin: 12px 0;
  background: var(--el-color-warning-light-9);
  border-left: 3px solid var(--el-color-warning);
}

.pending-band p {
  margin: 8px 0;
}

.candidate-list {
  max-height: 240px;
  margin-top: 8px;
  overflow: auto;
}

.candidate-row {
  display: grid;
  min-width: 0;
  padding: 8px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.candidate-row :deep(.el-checkbox) {
  width: 100%;
  height: auto;
  min-height: 28px;
  margin-right: 0;
  white-space: normal;
}

.candidate-row :deep(.el-checkbox__label) {
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
}

.candidate-meta,
.muted {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.candidate-meta {
  padding-left: 24px;
}

.quantity-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin: 12px 0;
  font-size: 13px;
}

.quantity-grid dt,
.record-meta dt {
  color: var(--el-text-color-secondary);
}

.quantity-grid dd,
.record-meta dd {
  min-width: 0;
  margin: 4px 0 0;
  white-space: pre-wrap;
}

.record-meta {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 8px;
  margin: 12px 0;
  font-size: 13px;
}

.record-meta dd {
  margin: 0;
}

.snapshot-scroll {
  width: 100%;
  min-width: 0;
  overflow: auto;
}

.record-heading :deep(.el-tag) {
  flex-shrink: 0;
  font-weight: 600;
}

.record-heading :deep(.el-tag--success) {
  --el-tag-text-color: #237144;
}

.record-heading :deep(.el-tag--warning) {
  --el-tag-text-color: #805d17;
}

.record-heading :deep(.el-tag--danger) {
  --el-tag-text-color: #a53c41;
}

.record-heading :deep(.el-tag--info) {
  --el-tag-text-color: #535b65;
}

.review-actions {
  justify-content: flex-start;
}

.review-actions :deep(.el-button) {
  margin-left: 0;
}

.reduction-panel :deep(.el-alert),
.reduction-panel :deep(.el-input),
.reduction-panel :deep(.el-textarea) {
  max-width: 100%;
  min-width: 0;
}

.reduction-panel :deep(.el-alert) {
  margin: 8px 0;
}

.reduction-panel :deep(.el-collapse-item__title) {
  min-width: 0;
  white-space: normal;
  overflow-wrap: anywhere;
}

@media (width <= 390px) {
  .record-meta {
    grid-template-columns: 80px minmax(0, 1fr);
  }

  .quantity-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .quantity-grid > div {
    display: grid;
    grid-template-columns: 64px minmax(0, 1fr);
    gap: 4px;
  }

  .quantity-grid dd {
    margin: 0;
  }

  .quantity-grid dd:last-child {
    grid-column: 2;
  }
}
</style>
