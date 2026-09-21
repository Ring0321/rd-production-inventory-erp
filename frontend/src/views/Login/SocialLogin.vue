<template>
  <main class="social-callback" aria-live="polite">
    <img src="@/assets/imgs/logo.png" alt="天净隆鼎" />
    <h1>正在验证企业账号</h1>
    <p>请稍候，验证完成后将自动进入 ERP 工作台。</p>
    <el-progress :percentage="100" :indeterminate="true" :duration="2" />
  </main>
</template>

<script lang="ts" setup>
import * as LoginApi from '@/api/login'
import * as authUtil from '@/utils/auth'

defineOptions({ name: 'SocialLogin' })

const route = useRoute()
const router = useRouter()
const message = useMessage()

const decodedParams = () => {
  const url = new URL(decodeURIComponent(location.href))
  return {
    type: url.searchParams.get('type') || '',
    redirect: url.searchParams.get('redirect') || '/'
  }
}

onMounted(async () => {
  authUtil.setTenantId(1)
  const { type, redirect } = decodedParams()
  try {
    const token = await LoginApi.socialLogin(
      type,
      String(route.query.code || ''),
      String(route.query.state || '')
    )
    authUtil.setToken(token)
    await router.replace(redirect)
  } catch {
    message.error('社交账号验证失败，请使用企业账号登录')
    await router.replace('/login')
  }
})
</script>

<style scoped>
.social-callback {
  display: flex;
  width: min(420px, calc(100% - 32px));
  min-height: 100%;
  margin: auto;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  text-align: center;
}

.social-callback img {
  width: 150px;
  height: 90px;
  object-fit: contain;
}

.social-callback h1 {
  margin: 22px 0 8px;
  font-size: 21px;
  letter-spacing: 0;
}

.social-callback p {
  margin: 0 0 24px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.social-callback :deep(.el-progress) {
  width: 100%;
}
</style>
