<script setup lang="ts">
import { ArrowLeft, ArrowRight, RefreshRight } from '@element-plus/icons-vue'
import { ElButton, ElTooltip } from 'element-plus'
import { RouterLink } from 'vue-router'
import type { TaskFeedState } from './taskFeed'
import type { WorkspaceTaskSummary } from './taskSummary'

defineProps<{
  title: string
  groupId: string
  state: Omit<TaskFeedState<unknown>, 'rows'>
  tasks: WorkspaceTaskSummary[]
}>()
defineEmits<{ retry: []; page: [pageNo: number] }>()
</script>

<template>
  <section class="task-group" :aria-labelledby="groupId" :aria-busy="state.status === 'loading'">
    <header class="group-heading">
      <h2 :id="groupId">{{ title }}</h2>
      <span v-if="state.total !== undefined" class="task-total">共 {{ state.total }} 项</span>
    </header>
    <div v-if="state.status === 'loading'" class="feed-state" role="status">
      <span>任务加载中</span>
      <div class="skeleton"></div>
      <div class="skeleton"></div>
    </div>
    <div v-else-if="state.status === 'error'" class="feed-state" role="alert">
      <span>任务加载失败</span>
      <ElButton :icon="RefreshRight" @click="$emit('retry')">重试</ElButton>
    </div>
    <div v-else-if="state.status === 'unauthorized'" class="feed-state" role="status">
      无任务查询权限或入口不可用
    </div>
    <div v-else-if="state.status === 'empty'" class="feed-state" role="status">暂无任务</div>
    <ul v-else class="task-list">
      <li v-for="task in tasks" :key="task.id" class="task-row">
        <div class="task-heading">
          <h3>
            <RouterLink v-if="task.to" :to="task.to" class="task-link">{{
              task.taskNo
            }}</RouterLink>
            <span v-else>{{ task.taskNo }}</span>
          </h3>
          <span class="task-status">{{ task.status }}</span>
        </div>
        <dl class="task-target">
          <dt>{{ task.target.label }}</dt
          ><dd>{{ task.target.value }}</dd>
        </dl>
        <dl class="task-details">
          <div v-for="detail in task.details" :key="detail.label">
            <dt>{{ detail.label }}</dt
            ><dd>{{ detail.value }}</dd>
          </div>
        </dl>
        <div v-if="task.progress" class="task-progress">
          <span>核验进度</span>
          <strong>{{ task.progress.done }} / {{ task.progress.total }} 包</strong>
          <progress
            v-if="task.progress.total > 0"
            :value="task.progress.done"
            :max="task.progress.total"
            :aria-label="`${task.taskNo} 核验进度`"
          ></progress>
        </div>
      </li>
    </ul>
    <nav
      v-if="state.total !== undefined && state.total > state.pageSize"
      class="feed-pages"
      :aria-label="`${title}分页`"
    >
      <ElTooltip content="上一页">
        <ElButton
          :icon="ArrowLeft"
          :disabled="state.pageNo <= 1"
          aria-label="上一页"
          @click="$emit('page', state.pageNo - 1)"
        />
      </ElTooltip>
      <span>{{ state.pageNo }} / {{ Math.ceil(state.total / state.pageSize) }}</span>
      <ElTooltip content="下一页">
        <ElButton
          :icon="ArrowRight"
          :disabled="state.pageNo * state.pageSize >= state.total"
          aria-label="下一页"
          @click="$emit('page', state.pageNo + 1)"
        />
      </ElTooltip>
    </nav>
  </section>
</template>

<style scoped>
.task-group {
  min-width: 0;
}

.group-heading,
.task-heading {
  display: flex;
  min-width: 0;
  align-items: flex-start;
  gap: 8px;
}

h2,
h3 {
  min-width: 0;
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
  flex: 1;
}

.task-total,
.task-status {
  max-width: 112px;
  font-size: 12px;
  line-height: 22px;
  color: var(--el-text-color-secondary);
  overflow-wrap: anywhere;
  flex: none;
}

.task-status {
  padding: 0 4px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.task-list {
  padding: 0;
  margin: 8px 0 0;
  list-style: none;
  border-top: 1px solid var(--el-border-color-lighter);
}

.task-row {
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.task-link {
  display: inline-flex;
  min-height: 40px;
  color: var(--el-color-primary);
  text-decoration: none;
  align-items: center;
}

.task-link:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}

dl {
  margin: 0;
}

dt {
  font-weight: 400;
  color: var(--el-text-color-secondary);
}

dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.task-target {
  display: grid;
  margin-bottom: 8px;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 8px;
}

.task-details {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 12px;
}

.task-details > div {
  min-width: 0;
}

.task-progress {
  display: grid;
  margin-top: 12px;
  font-size: 12px;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 8px;
}

.task-progress strong {
  font-weight: 500;
}

progress {
  width: 100%;
  height: 4px;
  accent-color: var(--el-color-success);
  grid-column: 1 / -1;
}

.feed-state {
  display: flex;
  min-height: 112px;
  padding: 16px 0;
  color: var(--el-text-color-secondary);
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-direction: column;
  align-items: flex-start;
  justify-content: center;
  gap: 12px;
}

.skeleton {
  width: 100%;
  height: 12px;
  background: var(--el-fill-color);
}

.feed-pages {
  display: flex;
  min-height: 48px;
  margin-top: 8px;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.feed-pages :deep(.el-button) {
  width: 40px;
  height: 40px;
  padding: 0;
  margin-left: 0;
}
</style>
