<template>
  <el-form
    v-show="getShow"
    ref="formLogin"
    :model="loginForm"
    :rules="loginRules"
    class="login-form"
    label-position="top"
    size="large"
  >
    <div class="login-heading">
      <LoginFormTitle />
      <span>{{ demoLogin ? '在线演示' : '一期开发环境' }}</span>
    </div>

    <section v-if="demoLogin" class="demo-access" aria-label="admin 演示账号">
      <div class="demo-heading">
        <Icon icon="ep:user-filled" />
        <strong>admin 演示账号</strong>
        <span>业务体验</span>
      </div>
      <dl class="demo-credentials">
        <dt>账号</dt><dd>{{ demoLogin.username }}</dd> <dt>密码</dt
        ><dd>{{ demoLogin.password }}</dd>
      </dl>
      <el-button
        :loading="loginLoading"
        :disabled="loginLoading || !authReady"
        class="demo-button"
        type="primary"
        plain
        @click="enterDemo"
      >
        <Icon icon="ep:right" /> 一键进入演示
      </el-button>
      <p>共享演示数据，可操作业务；系统维护权限独立保留。</p>
    </section>

    <el-form-item prop="username">
      <el-input
        v-model="loginForm.username"
        aria-label="账号"
        placeholder="账号"
        :prefix-icon="iconAvatar"
      />
    </el-form-item>
    <el-form-item prop="password">
      <el-input
        v-model="loginForm.password"
        aria-label="密码"
        placeholder="密码"
        :prefix-icon="iconLock"
        show-password
        type="password"
        @keyup.enter="getCode"
      />
    </el-form-item>

    <div class="login-options">
      <el-checkbox v-model="loginForm.rememberMe">记住账号</el-checkbox>
      <el-link
        v-if="capabilities.passwordReset"
        type="primary"
        @click="setLoginState(LoginStateEnum.RESET_PASSWORD)"
      >
        忘记密码？
      </el-link>
    </div>

    <el-button :loading="loginLoading" class="login-button" type="primary" @click="getCode">
      登录
    </el-button>

    <Verify
      v-if="capabilities.captcha"
      ref="verify"
      :captchaType="captchaType"
      :imgSize="{ width: '400px', height: '200px' }"
      mode="pop"
      @success="handleLogin"
    />

    <div v-if="capabilities.sms || capabilities.socialProviders.length" class="alternate-login">
      <el-button v-if="capabilities.sms" @click="setLoginState(LoginStateEnum.MOBILE)">
        <Icon icon="ep:cellphone" /> 手机登录
      </el-button>
      <el-tooltip
        v-for="provider in capabilities.socialProviders"
        :key="provider"
        :content="socialProviderName(provider)"
      >
        <el-button
          circle
          :aria-label="socialProviderName(provider)"
          @click="doSocialLogin(provider)"
        >
          <Icon :icon="socialProviderIcon(provider)" />
        </el-button>
      </el-tooltip>
    </div>
  </el-form>
</template>

<script lang="ts" setup>
import { ElLoading } from 'element-plus'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { useIcon } from '@/hooks/web/useIcon'
import * as authUtil from '@/utils/auth'
import { usePermissionStore } from '@/store/modules/permission'
import * as LoginApi from '@/api/login'
import type { AuthCapabilitiesVO } from '@/api/login'
import LoginFormTitle from './LoginFormTitle.vue'
import { LoginStateEnum, useFormValid, useLoginState } from './useLogin'
import { loadPublicDemoLogin } from './demoLogin'
import type { PublicDemoLogin } from './demoLogin'

defineOptions({ name: 'LoginForm' })

const iconAvatar = useIcon({ icon: 'ep:user' })
const iconLock = useIcon({ icon: 'ep:lock' })
const formLogin = ref()
const { validForm } = useFormValid(formLogin)
const { setLoginState, getLoginState } = useLoginState()
const { currentRoute, push } = useRouter()
const permissionStore = usePermissionStore()
const redirect = ref('')
const loginLoading = ref(false)
const demoLogin = ref<PublicDemoLogin | null>(null)
const authReady = ref(false)
const verify = ref()
const captchaType = ref('blockPuzzle')
const getShow = computed(() => unref(getLoginState) === LoginStateEnum.LOGIN)
const capabilities = reactive<AuthCapabilitiesVO>({
  password: true,
  captcha: false,
  sms: false,
  passwordReset: false,
  registration: false,
  socialProviders: [],
  qrProvider: null
})

const loginRules = { username: [required], password: [required] }
const loginForm = reactive({
  username: '',
  password: '',
  captchaVerification: '',
  rememberMe: true
})

const getCode = async () => {
  if (loginLoading.value) return
  if (capabilities.captcha) verify.value?.show()
  else await handleLogin({})
}

const enterDemo = async () => {
  if (!demoLogin.value || loginLoading.value) return
  loginForm.username = demoLogin.value.username
  loginForm.password = demoLogin.value.password
  await getCode()
}

const handleLogin = async (params: { captchaVerification?: string }) => {
  if (!(await validForm())) return
  loginLoading.value = true
  let loading: ReturnType<typeof ElLoading.service> | undefined
  try {
    const result = await LoginApi.login({
      username: loginForm.username,
      password: loginForm.password,
      captchaVerification: params.captchaVerification || ''
    })
    if (loginForm.rememberMe) {
      authUtil.setLoginForm({ username: loginForm.username, rememberMe: true })
    } else {
      authUtil.removeLoginForm()
    }
    authUtil.setToken(result)
    loading = ElLoading.service({ lock: true, text: '正在加载 ERP 工作台…' })
    await push({ path: redirect.value || permissionStore.addRouters[0]?.path || '/' })
  } finally {
    loginLoading.value = false
    loading?.close()
  }
}

const socialProviderName = (type: number) =>
  ({
    10: 'Gitee',
    20: '钉钉',
    30: '企业微信',
    31: '微信公众号',
    32: '微信开放平台',
    34: '微信小程序',
    40: '支付宝小程序'
  })[type] || `社交登录 ${type}`
const socialProviderIcon = (type: number) =>
  ({
    10: 'simple-icons:gitee',
    20: 'ri:dingtalk-fill',
    30: 'ri:wechat-2-fill',
    31: 'ri:wechat-fill',
    32: 'ri:wechat-fill',
    34: 'ri:wechat-fill',
    40: 'ri:alipay-fill'
  })[type] || 'ep:connection'

const doSocialLogin = async (type: number) => {
  const redirectUri = `${location.origin}/social-login?${encodeURIComponent(`type=${type}&redirect=${redirect.value || '/'}`)}`
  window.location.href = await LoginApi.socialAuthRedirect(type, encodeURIComponent(redirectUri))
}

watch(
  () => currentRoute.value,
  (route: RouteLocationNormalizedLoaded) => {
    redirect.value = (route.query.redirect as string) || ''
  },
  { immediate: true }
)

onMounted(async () => {
  authUtil.setTenantId(1)
  void loadPublicDemoLogin(import.meta.env.BASE_URL).then((config) => {
    demoLogin.value = config
  })
  const cached = authUtil.getLoginForm()
  if (cached) {
    loginForm.username = cached.username || ''
    loginForm.rememberMe = cached.rememberMe
  }
  Object.assign(capabilities, await LoginApi.getAuthCapabilities())
  authReady.value = true
})
</script>

<style lang="scss" scoped>
.login-form {
  width: 100%;
}

.login-heading {
  display: flex;
  min-height: 48px;
  padding-bottom: 18px;
  margin: -8px 78px 24px 0;
  border-bottom: 1px solid #e2e7ec;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.login-heading span {
  flex: none;
  font-size: 13px;
  color: #7b8491;
  white-space: nowrap;
}

.login-heading :deep(.login-form-title) {
  margin: 0;
  font-size: 25px;
  line-height: 1.3;
  text-align: left;
}

.login-options {
  display: flex;
  min-height: 34px;
  align-items: center;
  justify-content: space-between;
}

.login-button {
  width: 100%;
  margin-top: 10px;
}

.demo-access {
  padding-bottom: 20px;
  margin-bottom: 22px;
  border-bottom: 1px solid #e2e7ec;
}

.demo-heading {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
}

.demo-heading span {
  margin-left: auto;
  font-size: 12px;
  font-weight: 400;
  color: #607079;
}

.demo-credentials {
  display: grid;
  margin: 14px 0;
  font-size: 14px;
  line-height: 1.7;
  gap: 4px 16px;
  grid-template-columns: auto minmax(0, 1fr);
}

.demo-credentials dt {
  color: #607079;
}

.demo-credentials dd {
  margin: 0;
  overflow-wrap: anywhere;
  font-family: Consolas, monospace;
}

.demo-button {
  width: 100%;
}

.demo-access p {
  margin: 10px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: #68747b;
}

.alternate-login {
  display: flex;
  margin-top: 18px;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

@media (width <= 480px) {
  .login-heading {
    margin-right: 72px;
  }

  .login-heading span {
    display: none;
  }
}
</style>
