<script setup lang="ts">
import {
  RefreshRight,
  Search,
  Document,
  ArrowLeft,
  ArrowRight,
  Position,
  Check,
  Close,
  CircleCheck
} from '@element-plus/icons-vue'
import CoverageSelector from './CoverageSelector.vue'
import SampleItems from './SampleItems.vue'
import InspectionDetail from './InspectionDetail.vue'
import { useQualityWork } from './useQualityWork'
defineOptions({ name: 'PdaQuality' })
const {
  canQuery,
  canCreate,
  canSubmit,
  active,
  input,
  scope,
  scopeLoading,
  scopeError,
  scopePage,
  visibleScopeRows,
  selected,
  samples,
  items,
  remark,
  pending,
  writing,
  selectingBatch,
  actionError,
  storageError,
  selectedRows,
  locked,
  detail,
  detailLoading,
  detailError,
  rows,
  listLoading,
  listError,
  total,
  query,
  loadScope,
  selectEligibleBatch,
  toggleCoverage,
  toggleSample,
  addItem,
  removeItem,
  loadList,
  openInspection,
  save,
  act,
  canReview,
  canSubmitDetail,
  qualified
} = useQualityWork()
const statuses: Record<string, string> = {
  DRAFT: '草稿',
  SUBMITTED: '待复核',
  PASSED: '已通过',
  REJECTED: '未通过',
  CORRECTED: '已修正',
  RESUBMITTED: '待复核'
}
</script>
<template>
  <div class="quality-pda">
    <h1>质量检验</h1>
    <el-result v-if="!canQuery" icon="warning" title="无质检查询权限" />
    <template v-else>
      <el-alert v-if="storageError" :title="storageError" type="error" :closable="false" />
      <el-alert
        v-if="actionError"
        :title="actionError"
        type="warning"
        :closable="false"
        role="alert"
      />
      <section v-if="pending" class="pending-band" aria-label="待核质检请求">
        <h2>登记结果待核</h2>
        <dl class="summary-grid"
          ><div
            ><dt>代表包 ID</dt><dd>{{ pending.request.packageId }}</dd></div
          ><div
            ><dt>覆盖 / 抽样</dt
            ><dd
              >{{ pending.request.packageIds.length }} /
              {{ pending.request.samplePackageIds.length }} 包</dd
            ></div
          ><div
            ><dt>检验单 ID</dt><dd>{{ pending.inspectionId || '待返回' }}</dd></div
          ><div
            ><dt>请求编号</dt><dd>{{ pending.request.clientRequestId }}</dd></div
          ></dl
        >
        <el-button v-if="canCreate && canSubmit" type="primary" :loading="writing" @click="save"
          ><el-icon><RefreshRight /></el-icon>重试原提交</el-button
        >
      </section>
      <el-tabs v-model="active">
        <el-tab-pane label="检验登记" name="register">
          <form class="lookup" @submit.prevent="loadScope()">
            <label for="quality-package">包号 / 二维码</label>
            <div class="lookup-controls"
              ><el-input
                id="quality-package"
                v-model="input"
                :disabled="locked"
                placeholder="扫描吨袋二维码"
                clearable /><el-tooltip content="读取检验范围"
                ><el-button
                  native-type="submit"
                  type="primary"
                  :disabled="locked"
                  :loading="scopeLoading"
                  aria-label="读取检验范围"
                  ><el-icon><Search /></el-icon></el-button></el-tooltip
            ></div>
          </form>
          <div v-if="scopeError" class="error-band" role="alert"
            ><span>{{ scopeError }}</span
            ><el-button :disabled="locked" @click="loadScope()"
              ><el-icon><RefreshRight /></el-icon>重试</el-button
            ></div
          >
          <div v-if="scopeLoading" class="loading-state" role="status">货物范围读取中</div>
          <template v-if="scope">
            <dl class="summary-grid"
              ><div class="wide"
                ><dt>当前包</dt><dd>{{ scope.representative.packageCode }}</dd></div
              ><div
                ><dt>客户</dt><dd>{{ scope.representative.customerName }}</dd></div
              ><div
                ><dt>型号</dt><dd>{{ scope.representative.productModel }}</dd></div
              ><div
                ><dt>炉号</dt><dd>{{ scope.representative.furnaceNo }}</dd></div
              ><div
                ><dt>炉层</dt><dd>{{ scope.representative.furnaceLayer }}</dd></div
              ></dl
            >
            <el-button
              v-if="scope.representative.activeInspectionId"
              link
              type="primary"
              :disabled="writing"
              @click="openInspection(scope.representative.activeInspectionId)"
              ><el-icon><Document /></el-icon
              >{{ scope.representative.activeInspectionNo || '当前检验单' }}</el-button
            >
            <el-button
              v-if="canCreate"
              :disabled="locked || scopeLoading"
              :loading="selectingBatch"
              @click="selectEligibleBatch"
              ><el-icon><CircleCheck /></el-icon>选择可检整批</el-button
            >
            <CoverageSelector
              :rows="visibleScopeRows"
              :selected-ids="selected"
              :sample-ids="samples"
              :disabled="locked || !canCreate"
              @coverage="toggleCoverage"
              @sample="toggleSample"
              @inspection="openInspection"
            />
            <nav v-if="scope.packages.total > 20" class="pager" aria-label="检验范围分页"
              ><el-button
                :disabled="locked || scopeLoading || scopePage <= 1"
                aria-label="上一页范围"
                @click="loadScope(scopePage - 1, false)"
                ><el-icon><ArrowLeft /></el-icon></el-button
              ><span>{{ scopePage }} / {{ Math.ceil(scope.packages.total / 20) }}</span
              ><el-button
                :disabled="locked || scopeLoading || scopePage * 20 >= scope.packages.total"
                aria-label="下一页范围"
                @click="loadScope(scopePage + 1, false)"
                ><el-icon><ArrowRight /></el-icon></el-button
            ></nav>
            <template v-if="canCreate">
              <SampleItems
                :items="items"
                :samples="selectedRows"
                :disabled="locked"
                @add="addItem"
                @remove="removeItem"
              />
              <el-form label-position="top"
                ><el-form-item label="检验备注"
                  ><el-input
                    v-model="remark"
                    :disabled="locked"
                    type="textarea"
                    maxlength="512"
                    :rows="3" /></el-form-item
              ></el-form>
              <el-button
                v-if="canSubmit"
                type="primary"
                class="submit-button"
                :disabled="locked || scopeLoading"
                :loading="writing"
                @click="save"
                ><el-icon><Position /></el-icon>保存并提交</el-button
              >
            </template>
          </template>
          <div v-else-if="!scopeLoading && !scopeError && !pending" class="empty-state"
            >尚未读取货物</div
          >
        </el-tab-pane>
        <el-tab-pane label="检验记录" name="records">
          <form class="record-filters" @submit.prevent="loadList(1)">
            <el-input
              v-model="query.inspectionNo"
              placeholder="检验单号"
              aria-label="检验单号"
              :disabled="writing"
              clearable
            />
            <el-select
              v-model="query.status"
              aria-label="检验状态"
              :disabled="writing"
              @change="loadList(1)"
              ><el-option label="全部状态" value="" /><el-option
                v-for="(label, value) in statuses"
                :key="value"
                :label="label"
                :value="value"
            /></el-select>
            <el-tooltip content="查询检验单"
              ><el-button
                native-type="submit"
                :disabled="writing"
                :loading="listLoading"
                aria-label="查询检验单"
                ><el-icon><Search /></el-icon></el-button
            ></el-tooltip>
          </form>
          <div v-if="listLoading" class="loading-state" role="status">检验单读取中</div>
          <div v-else-if="listError" class="error-band" role="alert"
            ><span>{{ listError }}</span
            ><el-button @click="loadList()"
              ><el-icon><RefreshRight /></el-icon>重试</el-button
            ></div
          >
          <div v-else-if="!rows.length" class="empty-state">暂无检验单</div>
          <ul v-else class="record-list"
            ><li v-for="row in rows" :key="row.id"
              ><el-button link type="primary" :disabled="writing" @click="openInspection(row.id)">{{
                row.inspectionNo
              }}</el-button
              ><span>{{ statuses[row.status] || row.status }}</span></li
            ></ul
          >
          <nav
            v-if="total !== undefined && total > query.pageSize"
            class="pager"
            aria-label="检验记录分页"
            ><el-button
              :disabled="writing || listLoading || query.pageNo <= 1"
              aria-label="上一页记录"
              @click="loadList(query.pageNo - 1)"
              ><el-icon><ArrowLeft /></el-icon></el-button
            ><span>{{ query.pageNo }} / {{ Math.ceil(total / query.pageSize) }}</span
            ><el-button
              :disabled="writing || listLoading || query.pageNo * query.pageSize >= total"
              aria-label="下一页记录"
              @click="loadList(query.pageNo + 1)"
              ><el-icon><ArrowRight /></el-icon></el-button
          ></nav>
          <div v-if="detailLoading" class="loading-state" role="status">检验详情读取中</div>
          <div v-else-if="detailError" class="error-band" role="alert">{{ detailError }}</div>
          <template v-else-if="detail">
            <InspectionDetail :inspection="detail" />
            <div class="review-actions">
              <el-button
                v-if="canSubmitDetail"
                type="primary"
                :disabled="!!pending"
                :loading="writing"
                @click="act('submit')"
                ><el-icon><Position /></el-icon>提交检验单</el-button
              >
              <el-button
                v-if="canReview"
                type="success"
                :disabled="!qualified || !!pending"
                :loading="writing"
                @click="act('approve')"
                ><el-icon><Check /></el-icon>复核通过</el-button
              >
              <el-button
                v-if="canReview"
                type="danger"
                :disabled="!!pending"
                :loading="writing"
                @click="act('reject')"
                ><el-icon><Close /></el-icon>复核不通过</el-button
              >
            </div>
          </template>
        </el-tab-pane>
      </el-tabs>
    </template>
  </div>
</template>
<style scoped>
.quality-pda {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 16px;
  font-size: 14px;
  line-height: 1.5;
  letter-spacing: 0;
}

h1 {
  margin: 0;
  font-size: 18px;
}

h2 {
  margin: 0 0 12px;
  font-size: 15px;
}

.lookup {
  margin: 0 0 16px;
}

.lookup label {
  display: block;
  margin-bottom: 8px;
}

.lookup-controls,
.record-filters,
.pager,
.review-actions,
.error-band {
  display: flex;
  align-items: center;
  gap: 8px;
}

.lookup-controls :deep(.el-input) {
  min-width: 0;
  flex: 1;
}

.lookup-controls :deep(.el-button),
.record-filters :deep(.el-button),
.pager :deep(.el-button) {
  width: 40px;
  height: 40px;
  padding: 0;
  margin-left: 0;
  flex: none;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}

.summary-grid dt {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.summary-grid dd {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
}

.wide {
  grid-column: 1 / -1;
}

.pending-band {
  padding: 16px 0;
  border-top: 1px solid var(--el-color-warning);
  border-bottom: 1px solid var(--el-color-warning);
}

.error-band {
  padding: 12px 0;
  color: var(--el-color-danger);
  flex-wrap: wrap;
  overflow-wrap: anywhere;
}

.loading-state,
.empty-state {
  padding: 16px 0;
  color: var(--el-text-color-secondary);
}

.record-filters {
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.record-filters :deep(.el-input) {
  min-width: 140px;
  flex: 1;
}

.record-filters :deep(.el-select) {
  width: 124px;
}

.record-list {
  padding: 0;
  margin: 0 0 20px;
  list-style: none;
}

.record-list li {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  gap: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.record-list :deep(.el-button) {
  height: auto;
  min-width: 0;
  text-align: left;
  white-space: normal;
  overflow-wrap: anywhere;
}

.record-list li > span {
  flex: none;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.pager {
  margin: 12px 0 20px;
  justify-content: center;
}

.review-actions {
  margin-top: 20px;
  flex-wrap: wrap;
}

.review-actions :deep(.el-button) {
  min-height: 44px;
  margin-left: 0;
}

.submit-button {
  width: 100%;
  min-height: 44px;
}

:deep(.el-alert__content) {
  min-width: 0;
}

:deep(.el-alert__title) {
  overflow-wrap: anywhere;
}

@media (width <= 380px) {
  .summary-grid {
    grid-template-columns: 1fr;
  }
}
</style>
