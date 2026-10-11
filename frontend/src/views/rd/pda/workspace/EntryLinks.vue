<script setup lang="ts">
import { RouterLink } from 'vue-router'
import {
  ArrowRight,
  Box,
  CircleCheck,
  DataAnalysis,
  Document,
  Download,
  Grid,
  List,
  Location,
  Printer,
  Tickets,
  Upload,
  Van
} from '@element-plus/icons-vue'
import type { Component } from 'vue'
import type { WorkspaceEntry } from './profile'

defineProps<{ entries: WorkspaceEntry[] }>()

const icons: Record<string, Component> = {
  production: Document,
  task: List,
  record: Tickets,
  labels: Printer,
  putaway: Download,
  loading: Van,
  quality: CircleCheck,
  location: Location,
  inbound: Download,
  outbound: Upload,
  slip: Document,
  sale: Document,
  goods: Box,
  stock: Box,
  transaction: List,
  matrix: Grid,
  statistics: DataAnalysis,
  dashboard: DataAnalysis,
  product: Box
}
const kinds = { pda: 'PDA', web: 'Web', query: '查询' }
</script>

<template>
  <nav v-if="entries.length" class="workspace-entries" aria-label="业务入口">
    <h2>业务入口</h2>
    <ul>
      <li v-for="entry in entries" :key="entry.id">
        <RouterLink :to="{ name: entry.routeName }" class="entry-link">
          <component :is="icons[entry.id] || Document" class="entry-icon" aria-hidden="true" />
          <span class="entry-label">{{ entry.label }}</span>
          <span class="entry-kind">{{ kinds[entry.kind] }}</span>
          <ArrowRight class="entry-arrow" aria-hidden="true" />
        </RouterLink>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.workspace-entries {
  min-width: 0;
}

h2 {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
}

ul {
  padding: 0;
  margin: 0;
  list-style: none;
  border-top: 1px solid var(--el-border-color-lighter);
}

.entry-link {
  display: flex;
  min-width: 0;
  min-height: 48px;
  padding: 8px 0;
  color: var(--el-text-color-primary);
  text-decoration: none;
  border-bottom: 1px solid var(--el-border-color-lighter);
  align-items: center;
  gap: 12px;
}

.entry-label {
  min-width: 0;
  overflow-wrap: anywhere;
  flex: 1;
}

.entry-icon,
.entry-arrow {
  width: 20px;
  height: 20px;
  flex: none;
}

.entry-icon {
  color: var(--el-color-primary);
}

.entry-arrow,
.entry-kind {
  color: var(--el-text-color-secondary);
}

.entry-kind {
  font-size: 12px;
  flex: none;
}

.entry-link:hover {
  background: var(--el-fill-color-light);
}

.entry-link:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
