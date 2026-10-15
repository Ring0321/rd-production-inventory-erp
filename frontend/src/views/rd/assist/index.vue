<template>
  <main class="operations-assist" aria-label="作业智助">
    <header class="assist-heading"
      ><div><Icon icon="ep:operation" :size="22" /><h1>作业智助</h1></div
      ><el-button text @click="router.push('/warehouse-space')"
        ><Icon icon="ep:office-building" class="mr-5px" />仓储空间</el-button
      ></header
    >
    <el-result v-if="!valid || !tools.length" icon="warning" title="当前身份需要重新核对权限"
      ><template #extra
        ><el-button @click="reload">重新载入并核对权限</el-button></template
      ></el-result
    >
    <template v-else>
      <el-tabs v-model="tab" class="assist-tabs" @tab-change="changeTab">
        <el-tab-pane v-for="tool in tools" :key="tool.key" :name="tool.key"
          ><template #label
            ><span class="tool-label"><Icon :icon="tool.icon" />{{ tool.name }}</span></template
          ></el-tab-pane
        >
      </el-tabs>
      <Suspense
        ><component :is="toolComponent" :key="tab" /><template #fallback
          ><el-skeleton :rows="6" animated /></template
      ></Suspense>
    </template>
  </main>
</template>
<script setup lang="ts">
import { ref, computed, defineAsyncComponent, watch, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { onAuthContextChange } from '@/utils/auth'
import { availableAssistTools } from './assistAccess'

defineOptions({ name: 'RdOperationsAssist' })
const user = useUserStore(),
  route = useRoute(),
  router = useRouter(),
  valid = ref(true)
const tools = computed(() => availableAssistTools(user.getRoles, user.getPermissions))
const requested = typeof route.query.tool === 'string' ? route.query.tool : ''
const tab = ref(
  tools.value.find((t) => t.key === requested)?.key || tools.value[0]?.key || 'weight'
)
const components = {
  weight: defineAsyncComponent(() => import('./weight/WeightAssistant.vue')),
  replay: defineAsyncComponent(() => import('./replay/WarehouseReplay.vue')),
  ocr: defineAsyncComponent(() => import('./ocr/LocalDocumentReader.vue')),
  layout: defineAsyncComponent(() => import('./layout/WarehouseLayoutEditor.vue')),
  templates: defineAsyncComponent(() => import('./templates/LabelTemplateStudio.vue'))
}
const toolComponent = computed(() => components[tab.value])
const stop = onAuthContextChange(() => {
  valid.value = false
})
const stopScope = watch(
  [
    () => user.getUser.id,
    () => [...user.getPermissions].sort().join('\0'),
    () => user.getRoles.join('\0')
  ],
  () => {
    valid.value = false
  },
  { flush: 'sync' }
)
const changeTab = () =>
  void router.replace({ path: '/operations-assist', query: { ...route.query, tool: tab.value } })
const reload = async () => {
  await user.setUserInfoAction()
  window.location.reload()
}
onBeforeUnmount(() => {
  stop()
  stopScope()
})
</script>
<style scoped lang="scss">
.operations-assist {
  min-width: 0;
  padding: 18px 20px;
  letter-spacing: 0;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

.assist-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.assist-heading > div,
.tool-label {
  display: flex;
  align-items: center;
  gap: 8px;
}

.assist-heading > div > .el-icon {
  color: var(--el-color-primary);
}

.assist-heading h1 {
  margin: 0;
  font-size: 20px;
  line-height: 30px;
}

.assist-tabs {
  margin-bottom: 18px;
}

.tool-label {
  font-size: 13px;
}

@media (width <= 760px) {
  .operations-assist {
    padding: 12px;
  }

  .assist-heading h1 {
    font-size: 18px;
  }
}
</style>
