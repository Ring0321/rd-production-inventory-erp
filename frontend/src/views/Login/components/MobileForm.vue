<template>
  <el-form
    v-show="getShow"
    ref="formRef"
    :model="form"
    :rules="rules"
    class="login-form"
    label-position="top"
    size="large"
  >
    <LoginFormTitle />
    <p class="form-hint">使用已绑定的手机号登录</p>
    <el-form-item prop="mobile">
      <el-input v-model="form.mobile" placeholder="手机号" :prefix-icon="iconCellphone" />
    </el-form-item>
    <el-form-item prop="code">
      <el-input v-model="form.code" placeholder="短信验证码" :prefix-icon="iconCheck">
        <template #append>
          <el-button :disabled="countdown > 0" @click="sendCode">
            {{ countdown > 0 ? `${countdown} 秒` : '获取验证码' }}
          </el-button>
        </template>
      </el-input>
    </el-form-item>
    <el-button :loading="loading" class="full-button" type="primary" @click="submit">登录</el-button>
    <el-button class="full-button back-button" @click="handleBackLogin">返回账号登录</el-button>
  </el-form>
</template>

<script lang="ts" setup>
import { useIcon } from '@/hooks/web/useIcon'
import { setTenantId, setToken } from '@/utils/auth'
import { sendSmsCode, smsLogin } from '@/api/login'
import LoginFormTitle from './LoginFormTitle.vue'
import { LoginStateEnum, useFormValid, useLoginState } from './useLogin'

defineOptions({ name: 'MobileForm' })

const router = useRouter()
const route = useRoute()
const message = useMessage()
const formRef = ref()
const loading = ref(false)
const countdown = ref(0)
const iconCellphone = useIcon({ icon: 'ep:cellphone' })
const iconCheck = useIcon({ icon: 'ep:circle-check' })
const { validForm } = useFormValid(formRef)
const { handleBackLogin, getLoginState } = useLoginState()
const getShow = computed(() => unref(getLoginState) === LoginStateEnum.MOBILE)
const form = reactive({ mobile: '', code: '' })
const rules = { mobile: [required], code: [required] }

const sendCode = async () => {
  if (!form.mobile) return message.warning('请先输入手机号')
  await sendSmsCode({ mobile: form.mobile, scene: 21 })
  message.success('验证码已发送')
  countdown.value = 60
  const timer = window.setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) window.clearInterval(timer)
  }, 1000)
}

const submit = async () => {
  if (!(await validForm())) return
  loading.value = true
  try {
    setTenantId(1)
    setToken(await smsLogin(form))
    await router.push((route.query.redirect as string) || '/')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.form-hint {
  margin: -4px 0 24px;
  text-align: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.full-button {
  width: 100%;
}

.back-button {
  margin: 12px 0 0;
}
</style>
