<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElButton, ElMessageBox, ElTooltip } from 'element-plus'
import { ArrowLeft, Monitor, SwitchButton } from '@element-plus/icons-vue'
import { productBrand } from '@/config/brand'
import { useUserStore } from '@/store/modules/user'
import { useTagsViewStore } from '@/store/modules/tagsView'
import { getRoleLabel } from './profile'

defineOptions({ name: 'PdaWorkspaceLayout' })

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const tagsViewStore = useTagsViewStore()
const roleLabel = computed(() => getRoleLabel(userStore.getRoles))
const atHome = computed(
  () => route.name === 'PdaWorkspace' || route.path.replace(/\/$/, '') === '/pda'
)
const content = ref<HTMLElement>()
const logoutPending = ref(false)
const logoutError = ref('')

const goHome = () => router.push({ name: 'PdaWorkspace' })
const goToErp = () => router.push({ name: 'Index' })
const goBack = () => {
  const back = router.options.history.state.back
  if (typeof back === 'string' && /^\/pda(?:[/?#]|$)/u.test(back)) router.back()
  else void goHome()
}

const logout = async () => {
  if (logoutPending.value) return
  logoutPending.value = true
  logoutError.value = ''
  const redirect = route.fullPath
  try {
    await ElMessageBox.confirm('确认退出登录？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await userStore.loginOut()
    tagsViewStore.delAllViews()
    await router.replace({ path: '/login', query: { redirect } })
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') logoutError.value = '退出失败，请重试'
  } finally {
    logoutPending.value = false
  }
}

watch(
  () => route.fullPath,
  () => {
    if (content.value) content.value.scrollTop = 0
  },
  { flush: 'post' }
)
</script>

<template>
  <div class="pda-shell">
    <header class="pda-header">
      <div class="header-row">
        <div class="company">
          <img :src="productBrand.logoUrl" :alt="productBrand.name" width="40" height="40" />
          <div class="company-name"
            ><strong>{{ productBrand.shortName }}</strong
            ><span>岗位 PDA</span></div
          >
        </div>
        <nav class="shell-actions" aria-label="工作台导航">
          <ElTooltip content="返回">
            <ElButton :icon="ArrowLeft" :disabled="atHome" aria-label="返回" @click="goBack" />
          </ElTooltip>
          <ElTooltip content="返回电脑端 ERP">
            <ElButton
              class="erp-entry"
              :icon="Monitor"
              aria-label="返回 ERP 工作台"
              @click="goToErp"
            >
              ERP
            </ElButton>
          </ElTooltip>
          <ElTooltip content="退出登录">
            <ElButton
              :icon="SwitchButton"
              :loading="logoutPending"
              :disabled="!userStore.getIsSetUser"
              aria-label="退出登录"
              @click="logout"
            />
          </ElTooltip>
        </nav>
      </div>
      <div class="user-row"
        ><span>{{ userStore.getUser.nickname || '未设置姓名' }}</span
        ><span>{{ roleLabel }}</span></div
      >
      <div v-if="logoutError" class="logout-error" role="alert">{{ logoutError }}</div>
    </header>
    <main ref="content" class="pda-content" aria-label="岗位工作内容" tabindex="-1">
      <div class="content-inner"
        ><RouterView :key="route.name === 'PdaWarehouse' ? route.fullPath : undefined"
      /></div>
    </main>
  </div>
</template>

<style scoped>
.pda-shell {
  display: grid;
  width: 100%;
  height: 100vh;
  height: 100dvh;
  min-width: 0;
  overflow: hidden;
  font-size: 14px;
  line-height: 1.5;
  letter-spacing: 0;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
  grid-template-rows: auto minmax(0, 1fr);
}

.pda-header {
  min-width: 0;
  padding: calc(8px + env(safe-area-inset-top)) 12px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.header-row {
  display: flex;
  min-width: 0;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.company {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
}

.company img {
  width: 40px;
  height: 32px;
  object-fit: contain;
  flex: none;
}

.company-name {
  display: flex;
  min-width: 0;
  overflow-wrap: anywhere;
  flex-direction: column;
}

.company-name strong {
  font-size: 14px;
  font-weight: 600;
}

.company-name > span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.shell-actions {
  display: flex;
  flex: none;
  gap: 4px;
}

.shell-actions :deep(.el-button) {
  width: 40px;
  height: 40px;
  padding: 0;
  margin-left: 0;
}

.shell-actions :deep(.erp-entry) {
  width: auto;
  min-width: 72px;
  padding: 0 10px;
}

.user-row {
  display: flex;
  min-width: 0;
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.user-row > span {
  min-width: 0;
  overflow-wrap: anywhere;
  flex: 1;
}

.user-row > span:last-child {
  text-align: right;
}

.logout-error {
  margin-top: 8px;
  color: var(--el-color-danger);
}

.pda-content {
  min-width: 0;
  min-height: 0;
  padding: 16px 12px calc(24px + env(safe-area-inset-bottom));
  overflow: auto;
  overscroll-behavior: contain;
}

.content-inner {
  width: 100%;
  max-width: 720px;
  min-width: 0;
  margin: 0 auto;
}
</style>
