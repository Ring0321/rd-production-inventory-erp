<script lang="ts" setup>
import { computed, onMounted, ref, unref, watch } from 'vue'
import { useAppStore } from '@/store/modules/app'
import { useDesign } from '@/hooks/web/useDesign'
import { getLayoutRenderMode, isHeaderNavLayout } from '@/utils/layout'
import brandLogo from '@/assets/imgs/logo.png'

defineOptions({ name: 'Logo' })

const { getPrefixCls } = useDesign()

const prefixCls = getPrefixCls('logo')

const appStore = useAppStore()

const show = ref(true)

const layout = computed(() => appStore.getLayout)

const collapse = computed(() => appStore.getCollapse)

onMounted(() => {
  if (unref(collapse)) show.value = false
})

watch(
  () => collapse.value,
  (collapse: boolean) => {
    if (
      getLayoutRenderMode(unref(layout)) === 'topLeft' ||
      getLayoutRenderMode(unref(layout)) === 'cutMenu'
    ) {
      show.value = true
      return
    }
    if (!collapse) {
      setTimeout(() => {
        show.value = !collapse
      }, 400)
    } else {
      show.value = !collapse
    }
  }
)

watch(
  () => layout.value,
  (layout) => {
    const renderMode = getLayoutRenderMode(layout)
    if (renderMode === 'top' || renderMode === 'cutMenu') {
      show.value = true
    } else {
      if (unref(collapse)) {
        show.value = false
      } else {
        show.value = true
      }
    }
  }
)
</script>

<template>
  <div>
    <router-link
      :class="[
        prefixCls,
        getLayoutRenderMode(layout) !== 'classic' ? `${prefixCls}__Top` : '',
        'flex !h-[var(--logo-height)] items-center cursor-pointer pl-8px relative decoration-none overflow-hidden'
      ]"
      to="/"
    >
      <img
        :src="brandLogo"
        alt="天净隆鼎"
        :class="['rd-logo-image', { 'rd-logo-image--expanded': show }]"
      />
      <div
        v-if="show"
        :class="[
          'rd-logo-title ml-8px font-700',
          {
            'text-[var(--logo-title-text-color)]': getLayoutRenderMode(layout) === 'classic',
            'text-[var(--top-header-text-color)]':
              getLayoutRenderMode(layout) === 'topLeft' ||
              isHeaderNavLayout(layout) ||
              getLayoutRenderMode(layout) === 'cutMenu'
          }
        ]"
      >
        <span>天净隆鼎生产货物</span>
        <span>二维码管理系统</span>
      </div>
    </router-link>
  </div>
</template>

<style scoped>
.rd-logo-image {
  width: 48px;
  height: calc(var(--logo-height) - 12px);
  flex: none;
  object-fit: contain;
  transition: width 0.2s ease;
}

.rd-logo-image--expanded {
  width: 64px;
}

.rd-logo-title {
  display: flex;
  min-width: 0;
  flex-direction: column;
  font-size: 13px;
  line-height: 1.3;
  white-space: nowrap;
}
</style>
