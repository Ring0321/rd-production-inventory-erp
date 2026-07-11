<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="96px">
    <el-form-item :label="t('profile.user.nickname')" prop="nickname">
      <el-input v-model="form.nickname" />
    </el-form-item>
    <el-form-item :label="t('profile.user.mobile')" prop="mobile">
      <el-input v-model="form.mobile" />
    </el-form-item>
    <el-form-item :label="t('profile.user.email')" prop="email">
      <el-input v-model="form.email" />
    </el-form-item>
    <el-form-item :label="t('profile.user.sex')" prop="sex">
      <el-radio-group v-model="form.sex">
        <el-radio :value="1">{{ t('profile.user.man') }}</el-radio>
        <el-radio :value="2">{{ t('profile.user.woman') }}</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="submit">{{ t('common.save') }}</el-button>
      <el-button @click="init">{{ t('common.reset') }}</el-button>
    </el-form-item>
  </el-form>
</template>

<script lang="ts" setup>
import type { FormInstance, FormRules } from 'element-plus'
import {
  getUserProfile,
  updateUserProfile,
  type UserProfileUpdateReqVO
} from '@/api/system/user/profile'
import { useUserStore } from '@/store/modules/user'

defineOptions({ name: 'BasicInfo' })

const { t } = useI18n()
const message = useMessage()
const userStore = useUserStore()
const emit = defineEmits<{ (e: 'success'): void }>()
const formRef = ref<FormInstance>()
const form = reactive<UserProfileUpdateReqVO>({ nickname: '', mobile: '', email: '', sex: 0 })
const rules = reactive<FormRules>({
  nickname: [{ required: true, message: t('profile.rules.nickname'), trigger: 'blur' }],
  email: [
    { required: true, message: t('profile.rules.mail'), trigger: 'blur' },
    { type: 'email', message: t('profile.rules.truemail'), trigger: ['blur', 'change'] }
  ],
  mobile: [
    { required: true, message: t('profile.rules.phone'), trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: t('profile.rules.truephone'), trigger: 'blur' }
  ]
})

const init = async () => {
  Object.assign(form, await getUserProfile())
  return form
}

const submit = async () => {
  if (!(await formRef.value?.validate())) return
  await updateUserProfile(form)
  message.success(t('common.updateSuccess'))
  await userStore.setUserNicknameAction(form.nickname || '')
  emit('success')
}

onMounted(init)
</script>
