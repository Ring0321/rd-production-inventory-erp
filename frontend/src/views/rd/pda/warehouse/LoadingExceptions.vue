<script setup lang="ts">
import { Check } from '@element-plus/icons-vue'
import type { LoadingException, LoadingTaskLine } from '@/api/rd/loading'
defineProps<{
  exceptions: LoadingException[]
  lines: LoadingTaskLine[]
  editable: boolean
  disabled: boolean
  action: string
  reason: string
  replacementId?: number
  replacementCode: string
}>()
const emit = defineEmits<{
  action: [value: string]
  reason: [value: string]
  replacementId: [value?: number]
  replacementCode: [value: string]
  resolve: []
}>()
</script>
<template>
  <section v-if="exceptions.length" class="exception-band" aria-label="装车异常">
    <h2>现场异常</h2>
    <article v-for="item in exceptions" :key="item.id"
      ><strong>{{ item.exceptionNo }}</strong
      ><p v-if="item.packageCode">{{ item.packageCode }}</p
      ><p>{{ item.description }}</p
      ><p
        >{{ item.reporterName }} ·
        {{
          item.exceptionStatus === 'OPEN'
            ? '待处理'
            : item.exceptionStatus === 'RESOLVED'
              ? '已处理'
              : item.exceptionStatus
        }}</p
      ><p v-if="item.observedLocationCode">现场记录：{{ item.observedLocationCode }}</p
      ><p v-if="item.handlingResult">{{ item.handlerName }} · {{ item.handlingResult }}</p></article
    >
    <el-form v-if="editable" label-position="top" class="handling-form">
      <el-form-item label="处理方式"
        ><el-select
          :model-value="action"
          :disabled="disabled"
          aria-label="装车异常处理方式"
          @update:model-value="emit('action', String($event))"
          ><el-option label="退回司机继续执行" value="RETURN_TO_DRIVER" /><el-option
            label="替换包后继续"
            value="REPLACE_PACKAGE" /><el-option label="取消任务" value="CANCEL" /></el-select
      ></el-form-item>
      <template v-if="action === 'REPLACE_PACKAGE'">
        <el-form-item label="原任务包"
          ><el-select
            :model-value="replacementId"
            :disabled="disabled"
            placeholder="选择待替换包"
            aria-label="原任务包"
            @update:model-value="emit('replacementId', Number($event) || undefined)"
            ><el-option
              v-for="line in lines"
              :key="line.id"
              :value="line.id"
              :label="`${line.packageCode} · ${line.plannedLocationCode}`" /></el-select
        ></el-form-item>
        <el-form-item label="替换包号 / 二维码"
          ><el-input
            :model-value="replacementCode"
            :disabled="disabled"
            aria-label="替换包号或二维码"
            @update:model-value="emit('replacementCode', String($event))"
        /></el-form-item>
      </template>
      <el-form-item label="异常处理说明"
        ><el-input
          :model-value="reason"
          :disabled="disabled"
          type="textarea"
          maxlength="1000"
          :rows="3"
          aria-label="装车异常处理说明"
          @update:model-value="emit('reason', String($event))"
      /></el-form-item>
      <el-button
        type="warning"
        class="primary-action"
        :disabled="disabled || !reason.trim()"
        @click="emit('resolve')"
        ><el-icon><Check /></el-icon>确认处理装车异常</el-button
      >
    </el-form>
  </section>
</template>
