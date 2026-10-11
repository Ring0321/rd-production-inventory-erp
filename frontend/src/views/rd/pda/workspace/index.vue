<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElInput, ElOption, ElSelect, ElTooltip } from 'element-plus'
import { RefreshRight, Search } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import EntryLinks from './EntryLinks.vue'
import TaskGroup from './TaskGroup.vue'
import {
  canLookupPackage,
  getPackageTraceTarget,
  getProfileEntries,
  getWorkspaceProfiles,
  profileLabels,
  type WorkspaceProfileId
} from './profile'
import { useWorkspaceTasks } from './useWorkspaceTasks'
import { summarizeLoadingTask, summarizeProductionTask, summarizePutawayTask } from './taskSummary'

defineOptions({ name: 'PdaWorkspace' })

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const noPermissions = new Set<string>()
const permissions = computed(() =>
  userStore.getIsSetUser ? userStore.getPermissions : noPermissions
)
const hasRoute = (name: string) => {
  void route.name
  return router.hasRoute(name)
}
const profiles = computed(() =>
  getWorkspaceProfiles(userStore.getRoles, permissions.value, hasRoute)
)
const selectedProfile = ref<WorkspaceProfileId>()
const profile = computed({
  get: () => profiles.value.find((item) => item.id === selectedProfile.value)?.id,
  set: (value: WorkspaceProfileId | undefined) => {
    if (profiles.value.some((item) => item.id === value)) selectedProfile.value = value
  }
})

watch(
  () => profiles.value.map((item) => item.id).join('|'),
  () => {
    if (!profiles.value.some((item) => item.id === selectedProfile.value))
      selectedProfile.value = profiles.value[0]?.id
  },
  { immediate: true, flush: 'sync' }
)

const entries = computed(() => getProfileEntries(profile.value, permissions.value, hasRoute))
const lookupAllowed = computed(
  () => profile.value !== 'driver' && canLookupPackage(permissions.value, hasRoute)
)
const packageInput = ref('')
const lookupError = ref('')
watch(
  profile,
  () => {
    packageInput.value = ''
    lookupError.value = ''
  },
  { flush: 'sync' }
)

const lookupPackage = async () => {
  lookupError.value = ''
  if (!lookupAllowed.value) {
    lookupError.value = '无包码查询权限或入口不可用'
    return
  }
  const target = getPackageTraceTarget(packageInput.value)
  if (!target) {
    lookupError.value = '包号或二维码链接无效'
    return
  }
  try {
    await router.push(target)
  } catch {
    lookupError.value = '页面打开失败，请重试'
  }
}

const tasks = useWorkspaceTasks(profile, permissions, hasRoute)
const productionRows = computed(() =>
  tasks.production.state.rows.map((task) =>
    summarizeProductionTask(task, permissions.value, hasRoute)
  )
)
const putawayRows = computed(() => tasks.putaway.state.rows.map(summarizePutawayTask))
const loadingRows = computed(() => tasks.loading.state.rows.map(summarizeLoadingTask))
const refreshing = computed(() =>
  [tasks.production, tasks.putaway, tasks.loading].some((feed) => feed.state.status === 'loading')
)
</script>

<template>
  <div class="workspace-home">
    <header class="home-heading">
      <h1>{{ profile ? `${profileLabels[profile]}工作台` : '岗位工作台' }}</h1>
      <ElSelect
        v-if="profiles.length > 1"
        v-model="profile"
        class="profile-select"
        aria-label="岗位视图"
      >
        <ElOption v-for="item in profiles" :key="item.id" :value="item.id" :label="item.label" />
      </ElSelect>
      <ElTooltip v-if="tasks.queries.value.length" content="刷新任务">
        <ElButton
          class="refresh-button"
          :icon="RefreshRight"
          :disabled="refreshing"
          aria-label="刷新任务"
          @click="tasks.refresh()"
        />
      </ElTooltip>
    </header>

    <div v-if="!userStore.getIsSetUser" class="access-state" role="status">登录信息加载中</div>
    <div v-else-if="!profiles.length" class="access-state" role="status">无可用岗位权限或入口</div>
    <div v-else-if="!entries.length && !lookupAllowed" class="access-state" role="status"
      >当前岗位无可用入口</div
    >

    <form v-if="lookupAllowed" class="package-lookup" @submit.prevent="lookupPackage">
      <label for="workspace-package-code">包码查询</label>
      <div class="lookup-controls">
        <ElInput
          id="workspace-package-code"
          v-model="packageInput"
          clearable
          placeholder="包号或二维码链接"
          autocomplete="off"
          :aria-invalid="!!lookupError"
          :aria-describedby="lookupError ? 'workspace-lookup-error' : undefined"
          @input="lookupError = ''"
        />
        <ElTooltip content="查询包码">
          <ElButton
            class="lookup-button"
            native-type="submit"
            type="primary"
            :icon="Search"
            aria-label="查询包码"
          />
        </ElTooltip>
      </div>
      <span v-if="lookupError" id="workspace-lookup-error" class="lookup-error" role="alert">{{
        lookupError
      }}</span>
    </form>

    <TaskGroup
      v-if="profile === 'production'"
      group-id="workspace-production"
      title="登记中的生产任务"
      :state="tasks.production.state"
      :tasks="productionRows"
      @retry="tasks.production.load()"
      @page="tasks.production.load($event)"
    />
    <template v-if="profile === 'driver'">
      <TaskGroup
        group-id="workspace-putaway"
        title="本人入库上架任务"
        :state="tasks.putaway.state"
        :tasks="putawayRows"
        @retry="tasks.putaway.load()"
        @page="tasks.putaway.load($event)"
      />
      <TaskGroup
        group-id="workspace-loading"
        title="本人出库装车任务"
        :state="tasks.loading.state"
        :tasks="loadingRows"
        @retry="tasks.loading.load()"
        @page="tasks.loading.load($event)"
      />
    </template>
    <EntryLinks :entries="entries" />
  </div>
</template>

<style scoped>
.workspace-home {
  display: flex;
  min-width: 0;
  font-size: 14px;
  line-height: 1.5;
  flex-direction: column;
  gap: 24px;
}

.home-heading {
  display: flex;
  min-width: 0;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}

h1 {
  min-width: 0;
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  line-height: 1.5;
  overflow-wrap: anywhere;
  flex: 1;
}

.profile-select {
  width: 108px;
  flex: none;
}

.profile-select :deep(.el-select__wrapper) {
  min-height: 40px;
}

.refresh-button,
.lookup-button {
  width: 40px;
  height: 40px;
  padding: 0;
  margin-left: 0;
  flex: none;
}

.package-lookup {
  min-width: 0;
}

.package-lookup label {
  display: block;
  margin-bottom: 8px;
  font-weight: 600;
}

.lookup-controls {
  display: flex;
  min-width: 0;
  gap: 8px;
}

.lookup-controls :deep(.el-input) {
  height: 40px;
  min-width: 0;
  flex: 1;
}

.lookup-error {
  display: block;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-color-danger);
  overflow-wrap: anywhere;
}

.access-state {
  padding: 16px 0;
  color: var(--el-text-color-secondary);
}
</style>
