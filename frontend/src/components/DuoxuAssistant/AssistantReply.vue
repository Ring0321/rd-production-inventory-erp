<script setup lang="ts">
import { Icon } from '@/components/Icon'
import type { AssistantLink, AssistantReply } from './assistantModel'

defineProps<{ reply: AssistantReply }>()
defineEmits<{ navigate: [link: AssistantLink] }>()
const time = (value: string) =>
  new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date(value))
</script>

<template>
  <div class="xiaoduo-answer">
    <div class="answer-heading"
      ><Icon icon="lucide:bot" :size="18" /><strong>{{ reply.title }}</strong></div
    >
    <p v-for="(paragraph, index) in reply.paragraphs" :key="index">{{ paragraph }}</p>
    <dl v-if="reply.facts?.length" class="answer-facts">
      <div v-for="fact in reply.facts" :key="fact.label"
        ><dt>{{ fact.label }}</dt
        ><dd>{{ fact.value }}</dd></div
      >
    </dl>
    <div v-if="reply.links.length" class="answer-links">
      <button
        v-for="link in reply.links"
        :key="link.path"
        type="button"
        :aria-label="`打开 ${link.title}`"
        @click="$emit('navigate', link)"
      >
        <div
          ><strong>{{ link.title }}</strong
          ><span v-if="link.breadcrumb && link.breadcrumb !== link.title">{{
            link.breadcrumb
          }}</span></div
        >
        <Icon icon="ep:right" :size="16" />
      </button>
    </div>
    <footer v-if="reply.source || reply.checkedAt" class="answer-source">
      <span>{{ reply.source }}</span
      ><time v-if="reply.checkedAt" :datetime="reply.checkedAt"
        >{{ time(reply.checkedAt) }} 查询</time
      >
    </footer>
  </div>
</template>

<style scoped lang="scss">
.xiaoduo-answer {
  min-width: 0;
  font-size: 13px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}

.answer-heading {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  color: var(--el-text-color-primary);

  .el-icon {
    margin-top: 3px;
    color: var(--el-color-primary);
    flex-shrink: 0;
  }

  strong {
    font-size: 14px;
  }
}

p {
  margin: 10px 0;
  white-space: pre-wrap;
}

.answer-facts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 16px;
  padding: 12px 0;
  margin: 12px 0;
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);

  dt {
    font-size: 12px;
    color: var(--el-text-color-regular);
  }

  dd {
    margin: 2px 0 0;
    font-size: 15px;
    font-weight: 600;
  }
}

.answer-links button {
  display: flex;
  width: 100%;
  min-height: 46px;
  padding: 10px 4px;
  font: inherit;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: center;
  justify-content: space-between;
  gap: 12px;

  div {
    min-width: 0;
  }

  strong {
    display: block;
    font-weight: 500;
    color: var(--el-color-primary);
  }

  span {
    display: block;
    margin-top: 2px;
    font-size: 11px;
    color: var(--el-text-color-regular);
  }

  .el-icon {
    flex-shrink: 0;
    color: var(--el-color-primary);
  }

  &:hover {
    background: var(--el-color-primary-light-9);
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: -2px;
  }
}

.answer-source {
  display: flex;
  margin-top: 12px;
  font-size: 11px;
  line-height: 1.5;
  color: var(--el-text-color-regular);
  flex-wrap: wrap;
  gap: 4px 10px;
}
</style>
