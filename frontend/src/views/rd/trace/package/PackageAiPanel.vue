<template>
  <section
    class="package-ai-section"
    aria-labelledby="package-ai-heading"
    :aria-busy="contextLoading"
  >
    <header class="package-ai-heading">
      <div class="package-ai-title">
        <Icon icon="lucide:message-square-text" />
        <h2 id="package-ai-heading">包码问答</h2>
        <el-tag size="small" :type="context?.providerState === 'READY' ? 'success' : 'info'">
          {{ canQuery ? providerLabel : '无查询权限' }}
        </el-tag>
      </div>
      <el-tooltip content="刷新ERP登记事实" placement="top">
        <el-button
          circle
          :loading="contextLoading"
          :disabled="!canQuery || asking || confirming"
          aria-label="刷新ERP登记事实"
          @click="loadContext"
        >
          <Icon icon="lucide:refresh-cw" />
        </el-button>
      </el-tooltip>
    </header>

    <el-skeleton v-if="contextLoading" :rows="3" animated />
    <el-alert
      v-else-if="contextError || !canQuery"
      :title="contextError || '无包码查询权限，已停止读取'"
      type="warning"
      :closable="false"
    />
    <div v-else-if="context" class="package-ai-facts">
      <h3>ERP登记事实</h3>
      <dl class="package-ai-fact-grid">
        <div
          ><dt>包码状态</dt><dd>{{ packageAiStatusLabel(context.status) }}</dd></div
        >
        <div
          ><dt>质检阶段</dt><dd>{{ qualityLabel }}</dd></div
        >
        <div
          ><dt>录入包数</dt><dd>{{ sourceFacts.packageCount }}</dd></div
        >
        <div
          ><dt>登记总重</dt><dd>{{ sourceFacts.recordedWeight }}</dd></div
        >
        <div
          ><dt>状态分布</dt><dd>{{ sourceFacts.counts }}</dd></div
        >
      </dl>
      <p class="package-ai-meta">
        <span>数据来源：ERP生产来源登记</span>
        <span>来源{{ context.source.state === 'KNOWN' ? '已关联' : '未知' }}</span>
        <span>事实时间：{{ context.factsAt }}</span>
      </p>
      <p class="package-ai-boundary">来源登记值不代表可用库存或订单履约量。</p>
      <p v-if="context.provider?.name === 'DeepSeek'" class="package-ai-meta">
        <span>DeepSeek · 普通对话 · {{ context.provider.model }}</span>
        <span>ERP 月预算 {{ context.provider.monthlyBudgetCny }} 元</span>
        <span>每次预留 {{ context.provider.reservationPerCallCny }} 元，失败不退回预留</span>
      </p>
      <ul v-if="baseNotices.length" class="package-ai-notices">
        <li v-for="notice in baseNotices" :key="notice.key">{{ notice.text }}</li>
      </ul>
    </div>
    <p v-else class="package-ai-meta">尚未读取ERP登记事实</p>

    <div v-if="context?.diagnosis" class="package-ai-diagnostics">
      <div class="package-ai-title">
        <Icon icon="lucide:clipboard-check" />
        <h3>核对清单</h3>
        <el-tag size="small" type="info">ERP规则</el-tag>
      </div>
      <p class="package-ai-meta">
        同一生产产出记录 ·
        {{
          context.diagnosis.inspectedPackages === null
            ? '来源未确认'
            : `${context.diagnosis.inspectedPackages} 包已检查`
        }}
      </p>
      <ul v-if="context.diagnosis.findings.length" class="package-ai-diagnostic-list">
        <li v-for="finding in context.diagnosis.findings" :key="finding.key">
          <div class="package-ai-title">
            <el-tag
              size="small"
              :type="
                finding.severity === 'HIGH'
                  ? 'danger'
                  : finding.severity === 'REVIEW'
                    ? 'warning'
                    : 'info'
              "
            >
              {{ severityLabels[finding.severity] }}
            </el-tag>
            <strong>{{ finding.title }}</strong>
          </div>
          <p>{{ finding.detail }}</p>
          <dl class="package-ai-diagnostic-meta">
            <div
              ><dt>核对岗位</dt><dd>{{ ownerLabels[finding.owner] }}</dd></div
            >
            <div
              ><dt>影响包数</dt
              ><dd>{{
                finding.affectedCount === null ? '未知' : `${finding.affectedCount} 包`
              }}</dd></div
            >
            <div
              ><dt>依据</dt><dd>{{ finding.evidence }}</dd></div
            >
          </dl>
          <div v-if="finding.packageCodes.length" class="package-ai-samples">
            <span>样例包件</span>
            <router-link
              v-for="code in finding.packageCodes"
              :key="code"
              :to="`/pkg/${encodeURIComponent(code)}?internal=1`"
              >{{ code }}</router-link
            >
          </div>
        </li>
      </ul>
      <p v-else-if="context.diagnosis.state === 'KNOWN'" class="package-ai-meta"
        >当前规则检查未发现核对项，不代表已经满足质检放行或发货条件。</p
      >
      <p v-else class="package-ai-meta">来源未确认，诊断保持未知。</p>
    </div>

    <div v-if="canQuery && context" class="package-ai-field-question">
      <label for="package-ai-field-question">现场问句</label>
      <el-input
        id="package-ai-field-question"
        v-model="fieldQuestion"
        :maxlength="120"
        placeholder="这批能发吗 / 下一步谁处理 / 交接摘要"
        :disabled="questionLocked"
        aria-label="现场问句"
        @keyup.enter="selectFieldQuestion"
      />
      <el-button :disabled="questionLocked || !fieldQuestion.trim()" @click="selectFieldQuestion">
        <Icon icon="lucide:list-filter" class="mr-6px" />
        识别问题
      </el-button>
      <p v-if="fieldQuestionMessage" class="package-ai-meta" role="status">{{
        fieldQuestionMessage
      }}</p>
    </div>

    <div class="package-ai-controls">
      <label for="package-ai-question">问题</label>
      <el-select
        id="package-ai-question"
        v-model="question"
        aria-label="包码问题"
        class="package-ai-select"
        :disabled="
          !canQuery || contextLoading || asking || confirming || !!pending || !!storageError
        "
      >
        <el-option
          v-for="option in packageAiQuestions"
          :key="option.value"
          :value="option.value"
          :label="option.label"
        />
      </el-select>
      <el-button type="primary" :loading="asking" :disabled="!canAsk" @click="ask">
        <Icon icon="lucide:send" class="mr-6px" />
        请求整理
      </el-button>
      <el-button
        v-if="pending && (!requestTerminal || pending.state === 'SUCCESS')"
        :disabled="!canReconcile"
        @click="reconcile"
      >
        <Icon icon="lucide:search-check" class="mr-6px" />
        核对原请求
      </el-button>
      <el-button
        v-if="requestTerminal"
        :loading="confirming"
        :disabled="!canNewQuestion"
        @click="newQuestion"
      >
        <Icon icon="lucide:plus" class="mr-6px" />
        新问题
      </el-button>
    </div>

    <el-alert
      v-if="storageError || requestError"
      class="package-ai-request-error"
      :title="storageError || requestError"
      type="warning"
      :closable="false"
    />
    <p v-if="pending" class="package-ai-meta" role="status">
      <span>{{ resultMessage }}</span>
      <span>原请求：{{ pending.request.requestId }}</span>
    </p>
    <div v-if="answer" class="package-ai-answer" aria-live="polite">
      <h3>AI整理要点</h3>
      <ol
        ><li v-for="(note, index) in notes" :key="answer.noteKeys[index]">{{ note }}</li></ol
      >
      <p class="package-ai-meta">
        <span>整理时间：{{ answer.answeredAt }}</span>
        <span v-if="answer.totalTokens !== null">用量：{{ answer.totalTokens }} tokens</span>
      </p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  interpretPackageAiQuestion,
  packageAiQuestions,
  packageAiStatusLabel,
  usePackageAi
} from './usePackageAi'

const props = defineProps<{ packageCode: string; factsRevision: string | number }>()
const {
  canQuery,
  canAsk,
  canReconcile,
  canNewQuestion,
  requestTerminal,
  context,
  answer,
  pending,
  question,
  contextLoading,
  asking,
  confirming,
  contextError,
  requestError,
  storageError,
  providerLabel,
  sourceFacts,
  notes,
  resultMessage,
  loadContext,
  ask,
  reconcile,
  newQuestion
} = usePackageAi(props)
const ownerLabels = { PRODUCTION: '生产登记岗', QUALITY: '质检岗', WAREHOUSE: '仓管员' }
const baseNotices = computed(
  () => context.value?.notices.filter((notice) => !notice.key.startsWith('DIAG_')) || []
)
const severityLabels = { HIGH: '优先核对', REVIEW: '待核对', INFO: '提示' }
const fieldQuestion = ref('')
const fieldQuestionMessage = ref('')
const questionLocked = computed(
  () =>
    !canQuery.value ||
    contextLoading.value ||
    asking.value ||
    confirming.value ||
    !!pending.value ||
    !!storageError.value
)
const selectFieldQuestion = () => {
  if (questionLocked.value) return
  const result = interpretPackageAiQuestion(fieldQuestion.value)
  if (result.state === 'MATCHED') {
    question.value = result.question
    fieldQuestionMessage.value = `已选择：${packageAiQuestions.find((option) => option.value === result.question)?.label}`
  } else {
    fieldQuestionMessage.value =
      result.state === 'AMBIGUOUS'
        ? '包含多个问题，请在下方选择本次核对项。'
        : '该问句未识别。请选受控问题；配吨和业务办理请使用原任务。'
  }
}
watch(
  () => [props.packageCode, canQuery.value],
  () => {
    fieldQuestion.value = ''
    fieldQuestionMessage.value = ''
  }
)
const qualityLabel = computed(() => {
  const labels: Record<string, string> = {
    DRAFT: '草稿（未提交）',
    SUBMITTED: '已提交，待审核',
    CORRECTED: '已更正（未提交）',
    RESUBMITTED: '已重提，待审核',
    REVIEWED: '已审核',
    PASS: '合格',
    PASSED: '合格',
    REJECT: '不合格',
    REJECTED: '不合格',
    UNKNOWN: '未知'
  }
  const stage = context.value?.qualityStage || ''
  return labels[stage] || stage || '未记录'
})
</script>

<style scoped lang="scss">
.package-ai-section {
  width: 100%;
  padding: 24px 32px;
  letter-spacing: 0;
  border-bottom: 1px solid var(--el-border-color-light);
  box-sizing: border-box;
}

.package-ai-heading,
.package-ai-title,
.package-ai-controls,
.package-ai-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  align-items: center;
}

.package-ai-heading {
  justify-content: space-between;
  margin-bottom: 16px;
}

.package-ai-title {
  min-width: 0;
  color: var(--el-text-color-primary);
}

.package-ai-title h2 {
  margin: 0;
  font-size: 18px;
  line-height: 26px;
}

.package-ai-facts h3,
.package-ai-answer h3 {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

.package-ai-fact-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px 16px;
  margin: 0;
}

.package-ai-fact-grid > div,
.package-ai-meta > span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.package-ai-fact-grid dt,
.package-ai-meta,
.package-ai-boundary {
  font-size: 12px;
  line-height: 20px;
  color: var(--el-text-color-secondary);
}

.package-ai-fact-grid dd {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
  font-size: 14px;
  line-height: 22px;
  color: var(--el-text-color-primary);
}

.package-ai-meta {
  margin: 12px 0 0;
}

.package-ai-boundary {
  margin: 4px 0 0;
}

.package-ai-notices,
.package-ai-answer ol {
  padding-left: 20px;
  margin: 8px 0 0;
  overflow-wrap: anywhere;
  font-size: 13px;
  line-height: 22px;
  color: var(--el-text-color-regular);
}

.package-ai-controls {
  margin-top: 16px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.package-ai-select {
  width: 200px;
  max-width: 100%;
}

.package-ai-controls :deep(.el-button) {
  min-height: 32px;
  margin-left: 0;
}

.package-ai-request-error,
.package-ai-answer {
  margin-top: 12px;
}

.package-ai-answer {
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.package-ai-diagnostics {
  padding-top: 16px;
  margin-top: 20px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.package-ai-diagnostics h3 {
  margin: 0;
  font-size: 14px;
}

.package-ai-diagnostic-list {
  padding: 0;
  margin: 8px 0 0;
  font-size: 13px;
  list-style: none;
}

.package-ai-diagnostic-list > li {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.package-ai-diagnostic-list p {
  margin: 8px 0;
  overflow-wrap: anywhere;
  line-height: 22px;
}

.package-ai-diagnostic-meta {
  display: grid;
  grid-template-columns: 140px 100px minmax(0, 1fr);
  gap: 8px;
  margin: 0;
}

.package-ai-diagnostic-meta dt,
.package-ai-samples > span {
  color: var(--el-text-color-secondary);
}

.package-ai-diagnostic-meta dd {
  margin: 4px 0 0;
  overflow-wrap: anywhere;
  line-height: 20px;
}

.package-ai-samples {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 8px;
  overflow-wrap: anywhere;
}

.package-ai-samples a {
  max-width: 100%;
  color: var(--el-color-primary);
}

.package-ai-field-question {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  margin-top: 16px;
  font-size: 13px;
}

.package-ai-field-question label,
.package-ai-field-question p {
  grid-column: 1 / -1;
}

@media (width <= 760px) {
  .package-ai-section {
    padding: 24px 18px;
  }

  .package-ai-fact-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .package-ai-fact-grid > div:last-child {
    grid-column: 1 / -1;
  }

  .package-ai-diagnostic-meta {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .package-ai-diagnostic-meta > div:last-child {
    grid-column: 1 / -1;
  }
}

@media (width <= 420px) {
  .package-ai-select {
    flex: 1;
    min-width: 180px;
  }
}
</style>
