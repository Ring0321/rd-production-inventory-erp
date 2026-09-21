<template>
  <main class="login-page">
    <header class="brand-bar">
      <div class="brand">
        <img src="@/assets/imgs/logo.png" alt="天净隆鼎" />
        <div>
          <strong>{{ appStore.getTitle }}</strong>
          <span>生产 · 库存 · 订单</span>
        </div>
      </div>
      <div class="header-tools">
        <ThemeSwitch />
        <LocaleDropdown />
      </div>
    </header>

    <section class="login-stage">
      <div class="system-context" aria-hidden="true">
        <div class="context-mark"><Icon icon="ep:office-building" :size="28" /></div>
        <h1>生产库存协同管理</h1>
        <p>炉号炉层追溯、库房数量管控与销售订单履约。</p>
        <div class="context-grid">
          <span><Icon icon="ep:box" /> 库存台账</span>
          <span><Icon icon="ep:tickets" /> 订单差额</span>
          <span><Icon icon="ep:switch" /> 调拨出库</span>
        </div>
      </div>

      <div class="login-panel">
        <LoginForm />
        <MobileForm />
        <ForgetPasswordForm />
      </div>
    </section>

    <footer>瑞鼎生产库存订单ERP</footer>
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
  display: grid;
  min-height: 100%;
  color: #27313f;
  background: #f4f6f8;
  grid-template-rows: 72px 1fr 44px;
}

.brand-bar {
  display: flex;
  padding: 0 clamp(20px, 4vw, 64px);
  background: #fff;
  border-bottom: 1px solid #dfe4ea;
  align-items: center;
  justify-content: space-between;
}

.brand {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 12px;
}

.brand img {
  width: 112px;
  height: 48px;
  object-fit: contain;
}

.brand strong,
.brand span {
  display: block;
  letter-spacing: 0;
}

.brand strong {
  font-size: 19px;
}

.brand span {
  margin-top: 2px;
  font-size: 12px;
  color: #7b8491;
}

.header-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}

.login-stage {
  display: grid;
  width: min(1040px, calc(100% - 40px));
  margin: auto;
  gap: clamp(40px, 8vw, 110px);
  grid-template-columns: minmax(0, 1fr) minmax(340px, 420px);
}

.system-context {
  align-self: center;
}

.context-mark {
  display: flex;
  width: 52px;
  height: 52px;
  color: #13795b;
  background: #e9f5f0;
  border: 1px solid #b9d9cc;
  border-radius: 6px;
  align-items: center;
  justify-content: center;
}

.system-context h1 {
  margin: 22px 0 10px;
  font-size: 30px;
  line-height: 1.3;
  letter-spacing: 0;
}

.system-context p {
  max-width: 480px;
  margin: 0;
  line-height: 1.8;
  color: #697382;
}

.context-grid {
  display: flex;
  margin-top: 28px;
  font-size: 13px;
  color: #465261;
  flex-wrap: wrap;
  gap: 10px 24px;
}

.context-grid span {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.login-panel {
  padding: 32px 34px;
  background: #fff;
  border: 1px solid #dce2e8;
  border-radius: 8px;
  box-shadow: 0 14px 34px rgb(38 50 64 / 8%);
  align-self: center;
}

footer {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #8a929d;
}

@media (width <= 760px) {
  .login-page {
    grid-template-rows: 64px 1fr 38px;
  }

  .brand span,
  .system-context {
    display: none;
  }

  .brand img {
    width: 88px;
    height: 42px;
  }

  .brand strong {
    font-size: 14px;
  }

  .login-stage {
    width: min(420px, calc(100% - 24px));
    grid-template-columns: 1fr;
  }

  .login-panel {
    padding: 26px 20px;
  }
}
</style>
