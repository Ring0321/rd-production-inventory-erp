<script lang="ts" setup>
import { computed, onMounted, ref, unref, watch } from 'vue'
import { useAppStore } from '@/store/modules/app'
import { useDesign } from '@/hooks/web/useDesign'
import { getLayoutRenderMode, isHeaderNavLayout } from '@/utils/layout'
import { productBrand } from '@/config/brand'

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
        :src="productBrand.logoUrl"
        :alt="productBrand.name"
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
        <span :title="productBrand.name">{{ productBrand.name }}</span>
        <span class="rd-logo-subtitle">{{ productBrand.subtitle }}</span>
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
  padding: 2px;
  background: #f8fcfb;
  border-radius: 4px;
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
  overflow-wrap: anywhere;
}

.rd-logo-title > span {
  max-width: 100%;
}

.rd-logo-subtitle {
  font-size: 11px;
  font-weight: 400;
}
</style>
