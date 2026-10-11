<script setup lang="ts">
import { Plus, Delete, Search } from '@element-plus/icons-vue'
import type { LoadingTaskLine } from '@/api/rd/loading'
import type { LoadingReplacementRow, LoadingReplacementPreview } from './replacements'

const props = defineProps<{
  lines: LoadingTaskLine[]
  rows: LoadingReplacementRow[]
  preview: LoadingReplacementPreview[]
  disabled: boolean
  checking: boolean
  error: string
}>()
const emit = defineEmits<{
  'update:rows': [value: LoadingReplacementRow[]]
  inspect: []
}>()
const change = (key: number, field: 'taskLineId' | 'newPackageCode', value: unknown) => {
  if (props.disabled || props.checking) return
  emit(
    'update:rows',
    props.rows.map((row) =>
      row.key === key
        ? {
            ...row,
            [field]: field === 'taskLineId' ? Number(value) || undefined : String(value || '')
          }
        : row
    )
  )
}
const add = () => {
  if (props.disabled || props.checking || props.rows.length >= 100) return
  emit('update:rows', [
    ...props.rows,
    {
      key: Math.max(0, ...props.rows.map((row) => row.key)) + 1,
      newPackageCode: ''
    }
  ])
}
const remove = (key: number) => {
  if (!props.disabled && !props.checking)
    emit(
      'update:rows',
      props.rows.filter((row) => row.key !== key)
    )
}
const original = (row: LoadingReplacementRow) =>
  props.lines.find((line) => line.id === row.taskLineId)
const inspected = (row: LoadingReplacementRow) =>
  props.preview.find((item) => item.original.id === row.taskLineId)
</script>
<template>
  <section class="replacement-editor" aria-label="换货清单">
    <div class="replacement-heading">
      <strong>换货清单 · {{ rows.length }} 包</strong>
      <el-tooltip content="增加替换包" placement="top">
        <el-button
          :icon="Plus"
          aria-label="增加替换包"
          :disabled="disabled || checking || rows.length >= 100"
          @click="add"
        />
      </el-tooltip>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
    <div
      v-for="(row, index) in rows"
      :key="row.key"
      class="replacement-row"
      :aria-label="`换货第 ${index + 1} 行`"
    >
      <div class="replacement-heading">
        <strong>第 {{ index + 1 }} 包</strong>
        <el-tooltip content="移除这一行" placement="top">
          <el-button
            :icon="Delete"
            aria-label="移除替换行"
            :disabled="disabled || checking"
            @click="remove(row.key)"
          />
        </el-tooltip>
      </div>
      <el-form-item label="原任务包" required>
        <el-select
          :model-value="row.taskLineId"
          filterable
          :disabled="disabled || checking"
          :aria-label="`第 ${index + 1} 行原任务包`"
          @update:model-value="change(row.key, 'taskLineId', $event)"
        >
          <el-option
            v-for="line in lines"
            :key="line.id"
            :value="line.id"
            :disabled="rows.some((other) => other.key !== row.key && other.taskLineId === line.id)"
            :label="`${line.packageCode} · ${line.plannedLocationCode}`"
          />
        </el-select>
      </el-form-item>
      <p v-if="original(row)" class="replacement-meta"
        >{{ original(row)?.productModel }} · {{ original(row)?.furnaceNo }} ·
        {{ original(row)?.furnaceLayer }} · {{ original(row)?.packageWeight }} kg</p
      >
      <el-form-item label="替换包码" required>
        <el-input
          :model-value="row.newPackageCode"
          :disabled="disabled || checking"
          maxlength="256"
          :aria-label="`第 ${index + 1} 行替换包号或二维码`"
          @update:model-value="change(row.key, 'newPackageCode', $event)"
        />
      </el-form-item>
      <dl v-if="inspected(row)" class="replacement-facts">
        <dt>替换货</dt><dd>{{ inspected(row)?.replacement.packageCode }}</dd> <dt>客户</dt
        ><dd>{{ inspected(row)?.replacement.customerName }}</dd> <dt>型号 / 炉号 / 炉层</dt
        ><dd
          >{{ inspected(row)?.replacement.productModel }} /
          {{ inspected(row)?.replacement.furnaceNo }} /
          {{ inspected(row)?.replacement.furnaceLayer }}</dd
        >
        <dt>当前库位</dt><dd>{{ inspected(row)?.replacement.locationCode }}</dd> <dt>实际净重</dt
        ><dd>{{ inspected(row)?.replacement.packageWeight }} kg</dd>
      </dl>
    </div>
    <el-button
      :icon="Search"
      :loading="checking"
      :disabled="disabled || !rows.length"
      @click="emit('inspect')"
      >核对替换货</el-button
    >
    <p v-if="preview.length" class="replacement-total"
      >已核对 {{ preview.length }} 包 · 原重
      {{ preview.reduce((sum, item) => sum + item.original.packageWeight, 0) }} kg · 替换重
      {{ preview.reduce((sum, item) => sum + item.replacement.packageWeight, 0) }} kg</p
    >
  </section>
</template>
<style scoped>
.replacement-editor {
  width: 100%;
  min-width: 0;
}

.replacement-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.replacement-row {
  padding: 14px 0;
  border-top: 1px solid var(--el-border-color-lighter);
}

.replacement-row :deep(.el-select) {
  width: 100%;
  min-width: 0;
}

.replacement-meta,
.replacement-total {
  margin: 0 0 12px;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.replacement-facts {
  display: grid;
  grid-template-columns: minmax(80px, 30%) minmax(0, 1fr);
  gap: 6px 12px;
  margin: 0;
  font-size: 13px;
}

.replacement-facts dt {
  color: var(--el-text-color-secondary);
}

.replacement-facts dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.replacement-total {
  margin-top: 12px;
  font-weight: 600;
}
</style>
