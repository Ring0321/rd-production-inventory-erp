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
    <p class="form-hint">通过已绑定手机号重置密码</p>
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
    <el-form-item prop="password">
      <el-input
        v-model="form.password"
        type="password"
        show-password
        placeholder="新密码（4-16 位）"
        :prefix-icon="iconLock"
      />
    </el-form-item>
    <el-button :loading="loading" class="full-button" type="primary" @click="submit">确认重置</el-button>
    <el-button class="full-button back-button" @click="handleBackLogin">返回账号登录</el-button>
  </el-form>
</template>

<script lang="ts" setup>
import { useIcon } from '@/hooks/web/useIcon'
import { sendSmsCode, smsResetPassword } from '@/api/login'
import LoginFormTitle from './LoginFormTitle.vue'
import { LoginStateEnum, useFormValid, useLoginState } from './useLogin'

defineOptions({ name: 'ForgetPasswordForm' })

const message = useMessage()
const formRef = ref()
const loading = ref(false)
const countdown = ref(0)
const iconCellphone = useIcon({ icon: 'ep:cellphone' })
const iconCheck = useIcon({ icon: 'ep:circle-check' })
const iconLock = useIcon({ icon: 'ep:lock' })
const { validForm } = useFormValid(formRef)
const { handleBackLogin, getLoginState } = useLoginState()
const getShow = computed(() => unref(getLoginState) === LoginStateEnum.RESET_PASSWORD)
const form = reactive({ mobile: '', code: '', password: '' })
const rules = {
  mobile: [required],
  code: [required],
  password: [required, { min: 4, max: 16, message: '密码长度为 4-16 位', trigger: 'blur' }]
}

const sendCode = async () => {
  if (!form.mobile) return message.warning('请先输入手机号')
  await sendSmsCode({ mobile: form.mobile, scene: 24 })
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
    await smsResetPassword(form)
    message.success('密码已重置，请使用新密码登录')
    handleBackLogin()
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
