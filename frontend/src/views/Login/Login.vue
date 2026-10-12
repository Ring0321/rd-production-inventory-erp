<template>
  <main class="login-page">
    <section class="login-stage">
      <div class="system-context">
        <div class="brand">
          <img :src="productBrand.logoUrl" :alt="productBrand.name" />
        </div>
        <h1>{{ productBrand.name }}</h1>
        <p>{{ productBrand.subtitle }}</p>
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
import { productBrand } from '@/config/brand'
import { ThemeSwitch } from '@/layout/components/ThemeSwitch'
import { LocaleDropdown } from '@/layout/components/LocaleDropdown'
import { LoginForm, MobileForm, ForgetPasswordForm } from './components'

defineOptions({ name: 'Login' })
</script>

<style lang="scss" scoped>
.login-page {
  display: flex;
  min-height: 100%;
  padding: 36px;
  color: #202a30;
  background: #f5f7f8;
  border-top: 4px solid #087e72;
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
  width: 96px;
  height: 96px;
  object-fit: contain;
}

.login-stage {
  display: grid;
  width: min(1040px, 100%);
  align-items: center;
  gap: 72px;
  grid-template-columns: minmax(0, 1fr) minmax(380px, 460px);
}

.system-context {
  max-width: 460px;
  justify-self: center;
}

.system-context h1 {
  margin: 22px 0 12px;
  overflow-wrap: anywhere;
  font-size: 34px;
  line-height: 1.22;
  letter-spacing: 0;
}

.system-context p {
  max-width: 460px;
  margin: 0;
  font-size: 16px;
  line-height: 1.75;
  color: #606b72;
}

.login-panel {
  position: relative;
  padding: 32px 34px;
  color: #27313f;
  background: #fff;
  border: 1px solid #d9e0e4;
  border-radius: 8px;
  box-shadow: 0 12px 36px rgb(32 42 48 / 6%);
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
    width: 64px;
    height: 64px;
  }

  .system-context h1 {
    margin: 20px 0 10px;
    font-size: 26px;
    text-align: center;
  }

  .system-context p {
    font-size: 14px;
    text-align: center;
  }

  .login-panel {
    padding: 28px 22px;
  }
}
</style>
