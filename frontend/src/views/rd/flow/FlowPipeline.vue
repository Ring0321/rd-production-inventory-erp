<script setup lang="ts">
import { computed } from 'vue'
import { flowSteps, type summarizeFlow } from './flowFacts'

const props = defineProps<{ summary: ReturnType<typeof summarizeFlow>; selected: string }>()
defineEmits<{ select: [key: string] }>()
const active = computed(() => flowSteps.find((step) => step.key === props.selected) || flowSteps[0])
const metric = (key: string) => {
  if (key === 'PRODUCTION') return `${props.summary.outputCount} 条生产记录`
  if (key === 'LABEL') return `${props.summary.count} 个独立包码`
  if (key === 'TRACE') return `${props.summary.count} 包独立记录`
  if (key === 'OUTBOUND')
    return `${props.summary.states.find((state) => state.key === 'OUTBOUND')?.count || 0} 包已出库`
  if (key === 'LOADING' && !props.summary.workKnown) return '作业状态待核'
  const step = flowSteps.find((item) => item.key === key)!
  const count = props.summary.states
    .filter((state) => (step.states as readonly string[]).includes(state.key))
    .reduce((sum, state) => sum + state.count, 0)
  return `${count} 包当前停留`
}
</script>

<template>
  <section class="flow-pipeline" aria-label="生产仓储全流程">
    <nav class="pipeline-track" aria-label="业务阶段">
      <button
        v-for="(step, index) in flowSteps"
        :key="step.key"
        type="button"
        :aria-pressed="selected === step.key"
        :aria-label="`查看${step.title}流程`"
        @click="$emit('select', step.key)"
      >
        <span class="step-number">{{ String(index + 1).padStart(2, '0') }}</span>
        <Icon :icon="step.icon" :size="23" />
        <strong>{{ step.title }}</strong>
        <small>{{ metric(step.key) }}</small>
        <Icon v-if="index < flowSteps.length - 1" icon="ep:arrow-right" class="step-arrow" />
      </button>
    </nav>
    <div class="stage-handoff" aria-label="当前阶段岗位职责">
      <div
        ><span>责任岗位</span><strong>{{ active.role }}</strong></div
      >
      <div
        ><span>办理内容</span><p>{{ active.work }}</p></div
      >
      <div
        ><span>形成记录</span><p>{{ active.result }}</p></div
      >
    </div>
  </section>
</template>

<style scoped>
.pipeline-track {
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  border: 1px solid var(--el-border-color);
}

.pipeline-track button {
  position: relative;
  display: flex;
  min-width: 0;
  min-height: 142px;
  padding: 15px 8px 12px;
  color: var(--el-text-color-primary);
  text-align: center;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  align-items: center;
  flex-direction: column;
  gap: 8px;
}

.pipeline-track button:last-child {
  border-right: 0;
}

.pipeline-track button[aria-pressed='true'] {
  background: #eff8f5;
  box-shadow: inset 0 -3px #087e72;
}

.pipeline-track button:focus-visible {
  outline: 2px solid #087e72;
  outline-offset: -2px;
}

.pipeline-track button > .el-icon {
  color: #137b70;
}

.step-number {
  align-self: flex-start;
  font-size: 11px;
  color: var(--el-text-color-secondary);
  font-variant-numeric: tabular-nums;
}

.pipeline-track strong {
  font-size: 14px;
  line-height: 20px;
}

.pipeline-track small {
  font-size: 12px;
  line-height: 18px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
}

.step-arrow {
  position: absolute;
  top: 52px;
  right: -9px;
  z-index: 1;
  background: var(--el-bg-color);
}

.stage-handoff {
  display: grid;
  grid-template-columns: 1.2fr 2fr 1.3fr;
  gap: 20px;
  padding: 18px 0 4px;
}

.stage-handoff span {
  display: block;
  margin-bottom: 7px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stage-handoff strong,
.stage-handoff p {
  margin: 0;
  font-size: 13px;
  line-height: 22px;
  overflow-wrap: anywhere;
}

@media (width <= 1100px) {
  .pipeline-track {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }

  .pipeline-track button:nth-child(4) {
    border-right: 0;
  }

  .pipeline-track button:nth-child(-n + 4) {
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .pipeline-track button:nth-child(4) .step-arrow {
    display: none;
  }
}

@media (width <= 600px) {
  .pipeline-track {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .pipeline-track button {
    min-height: 128px;
  }

  .pipeline-track button:nth-child(even) {
    border-right: 0;
  }

  .pipeline-track button:nth-child(-n + 6) {
    border-bottom: 1px solid var(--el-border-color-lighter);
  }

  .pipeline-track button:nth-child(even) .step-arrow {
    display: none;
  }

  .stage-handoff {
    grid-template-columns: 1fr;
    gap: 13px;
  }

  .stage-handoff span {
    margin-bottom: 3px;
  }
}
</style>
