<template>
  <main class="login-page">
    <section class="login-stage">
      <div class="system-context">
        <div class="brand">
          <img src="@/assets/imgs/logo.png" alt="天净隆鼎" />
          <strong>{{ appStore.getTitle }}</strong>
        </div>
        <h1>生产货物二维码数字化管理系统</h1>
        <p>围绕生产任务、包装标签、质检放行、入库出库和数字护照，建立一包一码的管理闭环。</p>
        <div class="flow-tags" aria-label="系统业务范围">
          <span>生产</span>
          <span>质检</span>
          <span>入库</span>
          <span>出库</span>
          <span>追溯</span>
        </div>
      </div>

      <div class="login-panel">
        <div class="panel-tools">
          <ThemeSwitch />
          <LocaleDropdown />
        </div>
        <LoginForm />
        <MobileForm />
        <ForgetPasswordForm />
      </div>
    </section>
  </main>
</template>

<script lang="ts" setup>
import { useAppStore } from '@/store/modules/app'
import { ThemeSwitch } from '@/layout/components/ThemeSwitch'
import { LocaleDropdown } from '@/layout/components/LocaleDropdown'
import { LoginForm, MobileForm, ForgetPasswordForm } from './components'

defineOptions({ name: 'Login' })
const appStore = useAppStore()
</script>

<style lang="scss" scoped>
.login-page {
  display: flex;
  min-height: 100%;
  padding: 36px;
  color: #fff;
  background: linear-gradient(118deg, #173a5a 0%, #24596a 52%, #28776f 100%);
  align-items: center;
  justify-content: center;
}

.brand {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 16px;
}

.brand img {
  width: 116px;
  height: 58px;
  object-fit: contain;
}

.brand strong {
  max-width: 390px;
  font-size: 20px;
  line-height: 1.45;
  letter-spacing: 0;
}

.login-stage {
  display: grid;
  width: min(1720px, 100%);
  align-items: center;
  gap: clamp(56px, 9vw, 180px);
  grid-template-columns: minmax(0, 1fr) minmax(380px, 460px);
}

.system-context {
  max-width: 760px;
  justify-self: center;
}

.system-context h1 {
  margin: 36px 0 18px;
  font-size: clamp(38px, 3.2vw, 56px);
  line-height: 1.22;
  letter-spacing: 0;
}

.system-context p {
  max-width: 720px;
  margin: 0;
  font-size: 18px;
  line-height: 1.75;
  color: rgb(255 255 255 / 78%);
}

.flow-tags {
  display: flex;
  margin-top: 34px;
  flex-wrap: wrap;
  gap: 12px;
}

.flow-tags span {
  min-width: 84px;
  padding: 10px 18px;
  font-size: 16px;
  text-align: center;
  background: rgb(14 42 61 / 14%);
  border: 1px solid rgb(255 255 255 / 28%);
  border-radius: 6px;
}

.login-panel {
  position: relative;
  padding: 32px 34px;
  color: #27313f;
  background: #fff;
  border: 1px solid rgb(255 255 255 / 44%);
  border-radius: 8px;
  box-shadow: 0 22px 56px rgb(7 33 45 / 22%);
}

.panel-tools {
  position: absolute;
  top: 22px;
  right: 28px;
  display: flex;
  align-items: center;
  gap: 6px;
}

@media (width <= 760px) {
  .login-page {
    padding: 18px 12px;
  }

  .login-stage {
    width: min(440px, 100%);
    gap: 24px;
    grid-template-columns: 1fr;
  }

  .system-context {
    width: 100%;
  }

  .brand {
    justify-content: center;
  }

  .brand img {
    width: 96px;
    height: 48px;
  }

  .brand strong {
    max-width: 220px;
    font-size: 15px;
  }

  .system-context h1 {
    margin: 20px 0 10px;
    font-size: 26px;
    text-align: center;
  }

  .system-context p,
  .flow-tags {
    display: none;
  }

  .login-panel {
    padding: 28px 22px;
  }
}
</style>
