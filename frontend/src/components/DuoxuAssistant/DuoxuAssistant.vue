<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { Icon } from '@/components/Icon'
import { productBrand } from '@/config/brand'
import AssistantReply from './AssistantReply.vue'
import { useDuoxuAssistant } from './useDuoxuAssistant'
import type { AssistantLink } from './assistantModel'

const {
  open,
  input,
  busy,
  messages,
  available,
  currentPage,
  quickQuestions,
  ask,
  cancel,
  clear,
  openLink
} = useDuoxuAssistant()
const expanded = ref(false)
const launcher = ref<HTMLButtonElement>()
const editor = ref<HTMLTextAreaElement>()
const feed = ref<HTMLElement>()
const focusEditor = async () => {
  await nextTick()
  editor.value?.focus()
}
const clearConversation = () => {
  clear()
  void focusEditor()
}
const close = async () => {
  cancel()
  open.value = false
  await nextTick()
  launcher.value?.focus()
}
const toggle = () => {
  if (open.value) void close()
  else {
    open.value = true
    void focusEditor()
  }
}
const submit = async (question?: string) => {
  await ask(question)
  await focusEditor()
}
const keydown = (event: KeyboardEvent) => {
  if (event.key === 'Enter' && !event.shiftKey && !event.isComposing && event.keyCode !== 229) {
    event.preventDefault()
    void submit()
  }
}
const navigate = (link: AssistantLink) => {
  if (openLink(link)) void close()
}
watch(
  () => [messages.value.length, messages.value.at(-1)?.reply?.title, busy.value],
  async () => {
    await nextTick()
    if (feed.value) feed.value.scrollTop = feed.value.scrollHeight
  },
  { flush: 'post' }
)
</script>

<template>
  <Teleport to="body">
    <div v-if="available" class="xiaoduo-root">
      <section
        v-if="open"
        id="xiaoduo-panel"
        class="xiaoduo-panel"
        :class="{ 'is-expanded': expanded }"
        role="dialog"
        aria-label="智能小垛"
        :aria-modal="false"
        @keydown.esc.stop="close"
      >
        <header class="xiaoduo-header">
          <span class="header-avatar"><Icon icon="lucide:bot" :size="25" /></span>
          <div class="header-title"
            ><h2>智能小垛</h2><span>{{ productBrand.shortName }} · 站内助手</span></div
          >
          <div class="header-actions">
            <el-tooltip content="清空对话"
              ><button
                type="button"
                aria-label="清空小垛对话"
                :disabled="!messages.length && !input"
                @click="clearConversation"
                ><Icon icon="ep:delete" :size="17" /></button
            ></el-tooltip>
            <el-tooltip :content="expanded ? '还原窗口' : '展开窗口'"
              ><button
                type="button"
                :aria-label="expanded ? '还原小垛窗口' : '展开小垛窗口'"
                @click="expanded = !expanded"
                ><Icon
                  :icon="expanded ? 'ep:copy-document' : 'ep:full-screen'"
                  :size="17" /></button
            ></el-tooltip>
            <el-tooltip content="收起小垛"
              ><button type="button" aria-label="关闭智能小垛" @click="close"
                ><Icon icon="ep:close" :size="19" /></button
            ></el-tooltip>
          </div>
        </header>
        <div class="xiaoduo-context"
          ><Icon icon="ep:location" :size="14" /><span :title="currentPage">{{ currentPage }}</span
          ><small>本地解析</small></div
        >
        <div
          ref="feed"
          class="xiaoduo-feed"
          role="log"
          aria-label="小垛对话记录"
          aria-live="polite"
          aria-relevant="additions text"
        >
          <div v-if="!messages.length" class="xiaoduo-welcome">
            <img :src="productBrand.logoUrl" alt="" width="48" height="48" />
            <h3>你好，我是小垛</h3><p>今天想查什么？</p>
            <div class="quick-questions"
              ><button
                v-for="item in quickQuestions"
                :key="item.question"
                type="button"
                @click="submit(item.question)"
                ><Icon :icon="item.icon" :size="17" /><span>{{ item.label }}</span
                ><Icon icon="ep:arrow-right" :size="12" /></button
            ></div>
          </div>
          <article v-for="message in messages" :key="message.id" class="xiaoduo-turn">
            <div class="user-question"
              ><span>{{ message.question }}</span></div
            >
            <AssistantReply v-if="message.reply" :reply="message.reply" @navigate="navigate" />
            <div v-else class="xiaoduo-pending" role="status"
              ><Icon icon="ep:loading" :size="17" /><span>正在查询…</span></div
            >
          </article>
        </div>
        <form class="xiaoduo-composer" @submit.prevent="submit()">
          <div class="composer-input">
            <textarea
              ref="editor"
              v-model="input"
              rows="2"
              maxlength="200"
              aria-label="向智能小垛提问"
              placeholder="找页面、查包码、问流程"
              @keydown="keydown"
            ></textarea
            ><el-tooltip :content="busy ? '取消查询' : '发送'"
              ><button
                v-if="busy"
                type="button"
                class="send-button"
                aria-label="取消小垛查询"
                @click="cancel"
                ><Icon icon="ep:circle-close" :size="21" /></button
              ><button
                v-else
                type="submit"
                class="send-button"
                aria-label="发送给小垛"
                :disabled="!input.trim()"
                ><Icon icon="ep:top" :size="22" /></button></el-tooltip
          ></div>
          <div class="composer-status"
            ><span>本地知识 · ERP 实查</span><span>{{ input.length }}/200</span></div
          >
        </form>
      </section>
      <button
        ref="launcher"
        type="button"
        class="xiaoduo-launcher"
        :aria-expanded="open"
        aria-controls="xiaoduo-panel"
        :aria-label="open ? '收起智能小垛' : '打开智能小垛'"
        @click="toggle"
        ><span class="launcher-avatar"><Icon icon="lucide:bot" :size="26" /></span
        ><strong>智能小垛</strong><span class="launcher-ai">AI</span></button
      >
    </div>
  </Teleport>
</template>

<style scoped lang="scss" src="./assistant.scss"></style>
