<template>
  <section v-if="inspection" class="inspection-detail" aria-label="检验详情">
    <h2>{{ inspection.inspectionNo }}</h2>
    <dl class="detail-grid">
      <div
        ><dt>状态</dt><dd>{{ labels[inspection.status] || inspection.status }}</dd></div
      >
      <div
        ><dt>结论</dt
        ><dd>{{
          inspection.conclusion === 'PASSED'
            ? '合格'
            : inspection.conclusion === 'REJECTED'
              ? '不合格'
              : '待复核'
        }}</dd></div
      >
      <div
        ><dt>提交人</dt><dd>{{ inspection.submitterName || '未提交' }}</dd></div
      >
      <div
        ><dt>复核人</dt><dd>{{ inspection.reviewerName || '未复核' }}</dd></div
      >
      <div v-if="inspection.coverageCount != null"
        ><dt>覆盖范围</dt><dd>{{ inspection.coverageCount }} 包</dd></div
      >
      <div v-if="inspection.sampleCount != null"
        ><dt>实际抽样</dt><dd>{{ inspection.sampleCount }} 包</dd></div
      >
    </dl>
    <p v-if="inspection.rejectReason" class="reject-reason">{{ inspection.rejectReason }}</p>
    <p v-if="inspection.remark">{{ inspection.remark }}</p>
    <ul v-if="inspection.members?.length" class="member-list">
      <li v-for="bag in inspection.members" :key="bag.packageId"
        ><span>{{ bag.packageCode }}</span
        ><span>{{ bag.packageWeight }} kg</span
        ><strong>{{
          bag.sampled == null ? '抽样未记录' : bag.sampled ? '抽样包' : '覆盖包'
        }}</strong></li
      >
    </ul>
    <div v-for="item in inspection.items" :key="item.id" class="result-row">
      <div
        ><strong>{{ item.itemName }}</strong
        ><span :class="item.qualified ? 'passed' : 'rejected'">{{
          item.qualified ? '合格' : '不合格'
        }}</span></div
      >
      <dl class="detail-grid"
        ><div
          ><dt>抽样包</dt><dd>{{ sampleCode(item.samplePackageId) }}</dd></div
        ><div
          ><dt>实测值</dt><dd>{{ item.measuredValue ?? '未记录' }} {{ item.unit }}</dd></div
        ><div
          ><dt>下限</dt><dd>{{ item.standardMin ?? '未设定' }}</dd></div
        ><div
          ><dt>上限</dt><dd>{{ item.standardMax ?? '未设定' }}</dd></div
        ></dl
      >
      <p v-if="item.remark">{{ item.remark }}</p>
    </div>
    <ol v-if="inspection.events?.length" class="quality-audit" aria-label="质检审计">
      <li v-for="event in inspection.events" :key="event.id">
        <div
          ><strong>{{ actions[event.action] || event.action }}</strong
          ><time>{{ formatDate(event.eventTime) }}</time></div
        >
        <p
          >{{ event.actorName || `操作人 ID ${event.actorId}` }} ·
          {{ labels[event.afterStatus] || event.afterStatus }}</p
        >
        <p v-if="event.detail">{{ event.detail }}</p>
      </li>
    </ol>
  </section>
</template>
<script setup lang="ts">
import type { RdQualityInspectionVO } from '@/api/rd/quality/inspection'
import { formatDate } from '@/utils/formatTime'
const props = defineProps<{ inspection?: RdQualityInspectionVO }>()
const actions: Record<string, string> = {
  CREATE: '登记',
  SUBMIT: '提交',
  APPROVE: '复核通过',
  REJECT: '复核不通过'
}
const labels: Record<string, string> = {
  DRAFT: '草稿',
  CORRECTED: '已修正',
  SUBMITTED: '待复核',
  RESUBMITTED: '待复核',
  PASSED: '已通过',
  REJECTED: '未通过'
}
const sampleCode = (id?: number) =>
  id == null
    ? '未记录'
    : props.inspection?.members?.find((bag) => bag.packageId === id)?.packageCode || `包件 ID ${id}`
</script>
<style scoped>
h2 {
  margin: 0 0 16px;
  font-size: 16px;
  overflow-wrap: anywhere;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 0 0 16px;
}

dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

dd {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
}

p {
  margin: 12px 0;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.member-list {
  padding: 0;
  margin: 0;
  list-style: none;
}

.member-list li,
.result-row > div:first-child {
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.member-list li {
  padding: 10px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  overflow-wrap: anywhere;
}

.member-list strong {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.result-row {
  padding-top: 16px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.result-row .detail-grid {
  margin-top: 12px;
}

.passed {
  color: var(--el-color-success);
}

.rejected,
.reject-reason {
  color: var(--el-color-danger);
}

.quality-audit {
  padding: 0;
  margin: 20px 0 0;
  list-style: none;
}

.quality-audit li {
  padding: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.quality-audit li > div {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
}

.quality-audit time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.quality-audit p {
  margin: 8px 0 0;
}

@media (width <= 380px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
