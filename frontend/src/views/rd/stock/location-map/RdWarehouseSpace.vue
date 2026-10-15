<template>
  <section ref="spaceHost" class="warehouse-space" aria-label="仓储空间总览">
    <el-result v-if="!allowed || !contextValid" icon="warning" title="当前岗位无仓储空间查看权限" />
    <template v-else>
      <header class="space-heading">
        <div class="space-title">
          <Icon icon="ep:office-building" :size="24" />
          <div
            ><h1>仓储空间总览</h1><span>{{ productBrand.name }} · 登记库存</span></div
          >
        </div>
        <div class="space-actions">
          <time v-if="updatedAt">{{ timeText }} 更新</time>
          <el-tooltip v-if="embedded" content="打开仓储空间">
            <el-button circle aria-label="打开仓储空间" @click="router.push('/warehouse-space')"
              ><Icon icon="ep:top-right"
            /></el-button>
          </el-tooltip>
          <el-tooltip content="刷新库存">
            <el-button circle :loading="busy" aria-label="刷新仓储空间" @click="refresh"
              ><Icon icon="lucide:rotate-ccw"
            /></el-button>
          </el-tooltip>
          <el-tooltip :content="fullscreen ? '退出全屏' : '全屏展示'">
            <el-button
              circle
              :aria-label="fullscreen ? '退出全屏展示' : '全屏展示仓储空间'"
              @click="toggleFullscreen"
              ><Icon icon="ep:full-screen"
            /></el-button>
          </el-tooltip>
        </div>
      </header>
      <div class="space-navigation">
        <el-select
          v-model="warehouseCode"
          aria-label="总览仓库"
          class="warehouse-selector"
          :empty-values="[null, undefined]"
          @change="scope = OVERVIEW_KEY"
        >
          <el-option label="全部仓库" value="" />
          <el-option
            v-for="warehouse in warehouses"
            :key="warehouse.code"
            :label="warehouse.name"
            :value="warehouse.code"
          />
        </el-select>
        <div v-if="canReadLayout" class="published-layout-controls">
          <el-switch
            v-model="usePublished"
            :disabled="!warehouseCode"
            active-text="发布布局"
            aria-label="使用发布布局"
          />
          <span v-if="layoutVersion">v{{ layoutVersion }}</span>
          <el-button
            v-if="hasPermission(['rd:warehouse-location:update'])"
            text
            @click="
              router.push({ path: '/operations-assist', query: { tool: 'layout', warehouseCode } })
            "
            ><Icon icon="ep:edit" class="mr-5px" />编辑布局</el-button
          >
        </div>
        <nav class="business-links" aria-label="仓储业务入口">
          <el-button
            v-if="hasPermission(['rd:putaway-task:query'])"
            text
            @click="router.push('/rd/stock/putaway')"
            ><Icon icon="ep:download" class="mr-5px" />入库上架</el-button
          >
          <el-button
            v-if="hasPermission(['rd:loading-task:query'])"
            text
            @click="router.push('/rd/stock/loading')"
            ><Icon icon="ep:van" class="mr-5px" />出库装车</el-button
          >
        </nav>
      </div>
      <nav v-if="groups.length" class="area-navigation" aria-label="库区导航">
        <button
          type="button"
          :aria-pressed="scope === OVERVIEW_KEY"
          @click="showGroup(OVERVIEW_KEY)"
          ><strong>全仓</strong><span>{{ visibleLocations.length }} 库位</span></button
        >
        <button
          v-for="group in groups"
          :key="group.key"
          type="button"
          :aria-pressed="scope === group.key"
          :title="group.label"
          @click="showGroup(group.key)"
        >
          <strong>{{ areaLabel(group) }}</strong
          ><span>{{ group.nodes.length }} 库位</span>
        </button>
      </nav>
      <el-alert v-if="error" :title="error + '，请刷新后核对'" type="warning" :closable="false" />
      <el-alert v-if="fullscreenError" :title="fullscreenError" type="warning" :closable="false" />
      <el-alert v-if="layoutError" :title="layoutError" type="warning" :closable="false" />
      <el-alert
        v-if="invalidCount"
        :title="`${invalidCount} 条库位身份待核，未计入总览`"
        type="warning"
        :closable="false"
      />
      <el-skeleton v-if="!updatedAt && busy" :rows="6" animated />
      <el-alert
        v-if="updatedAt && ['INVALID', 'MISSING'].includes(linkedLocation.state)"
        title="链接中的库位标识或当前登记状态待核，未自动聚焦其他库位"
        type="warning"
        :closable="false"
      />
      <RdSpatialLocationMap
        v-else-if="updatedAt"
        :key="warehouseCode"
        ref="mapView"
        :locations="visibleLocations"
        :snapshot-version="snapshotVersion"
        :selected-id="linkedLocation.state === 'FOUND' ? linkedLocation.id : undefined"
        :configured-layout="configuredLayout"
        overview
        showcase
        retain-selection-on-refresh
        initial-dimension="3d"
        @scope="scope = $event"
        @task="openTask"
      />
      <el-empty v-else-if="!busy" description="尚未读取库位数据" />
    </template>
  </section>
</template>
<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { productBrand } from '@/config/brand'
import { hasPermission } from '@/directives/permission/hasPermi'
import RdSpatialLocationMap from './RdSpatialLocationMap.vue'
import { useWarehouseSpace } from './useWarehouseSpace'
import { buildSpatialLayout, isLocationIdentityValid, type SpatialGroup } from './spatialLayout'
import { warehouseScopes, OVERVIEW_KEY } from './warehouseOverview'
import { useUserStore } from '@/store/modules/user'
import { canViewWarehouseSpace } from './warehouseSpaceAccess'
import { resolveWarehouseSpaceLink } from './warehouseSpaceLink'
import { getVisualDocument } from '@/api/rd/assist/visualDocument'
import { applyPublishedLayout, type PublishedWarehouseLayout } from './publishedLayout'

defineProps<{ embedded?: boolean }>()
defineOptions({ name: 'RdWarehouseSpace' })
const router = useRouter()
const route = useRoute()
const user = useUserStore()
const allowed = computed(() => canViewWarehouseSpace(user.getRoles, user.getPermissions))
const { locations, busy, updatedAt, snapshotVersion, contextValid, error, refresh } =
  useWarehouseSpace()
const spaceHost = ref<HTMLElement>()
const mapView = ref<InstanceType<typeof RdSpatialLocationMap>>()
const warehouseCode = ref(
  typeof route.query.warehouseCode === 'string' ? route.query.warehouseCode : ''
)
const usePublished = ref(route.query.layout === 'published')
const configuredLayout = ref<PublishedWarehouseLayout>()
const layoutVersion = ref(0)
const layoutError = ref('')
const canReadLayout = computed(
  () => allowed.value && hasPermission(['rd:warehouse-location:query'])
)
let layoutSequence = 0
watch([warehouseCode, usePublished, updatedAt, contextValid], async () => {
  const sequence = ++layoutSequence
  if (
    !usePublished.value ||
    !canReadLayout.value ||
    !contextValid.value ||
    configuredLayout.value?.warehouseCode !== warehouseCode.value
  ) {
    configuredLayout.value = undefined
    layoutVersion.value = 0
  }
  layoutError.value = ''
  if (
    !usePublished.value ||
    !warehouseCode.value ||
    !canReadLayout.value ||
    !contextValid.value ||
    !updatedAt.value
  )
    return
  const code = warehouseCode.value
  try {
    const value = await getVisualDocument('LAYOUT', code)
    if (
      sequence !== layoutSequence ||
      !contextValid.value ||
      !canReadLayout.value ||
      warehouseCode.value !== code
    )
      return
    if (!value.published || !value.publishedVersion) {
      configuredLayout.value = undefined
      layoutVersion.value = 0
      layoutError.value = '此仓库尚无发布布局，当前显示自建示意'
      return
    }
    const doc = value.published as PublishedWarehouseLayout
    if (value.scopeId !== code || doc.warehouseCode !== code)
      throw new Error('发布布局仓库范围待核')
    applyPublishedLayout(
      buildSpatialLayout(
        locations.value.filter((l) => l.warehouseCode === code),
        'schematic'
      ),
      doc
    )
    configuredLayout.value = doc
    layoutVersion.value = value.publishedVersion
  } catch {
    if (sequence === layoutSequence && contextValid.value) {
      configuredLayout.value = undefined
      layoutVersion.value = 0
      layoutError.value = '发布布局未取得，当前显示自建示意'
    }
  }
})
const scope = ref(OVERVIEW_KEY)
const validLocations = computed(() =>
  locations.value.filter((l) => isLocationIdentityValid(locations.value, l.id))
)
const warehouses = computed(() => warehouseScopes(locations.value))
const visibleLocations = computed(() =>
  validLocations.value.filter(
    (l) => !warehouseCode.value || l.warehouseCode.trim() === warehouseCode.value
  )
)
const invalidCount = computed(() => locations.value.length - validLocations.value.length)
const linkedLocation = computed(() =>
  resolveWarehouseSpaceLink(locations.value, route.query.locationId)
)
let linkedKey = ''
watch([mapView, () => route.query.locationId, updatedAt], async () => {
  if (!allowed.value || !contextValid.value || !updatedAt.value) return
  const linked = linkedLocation.value
  if (linked.state !== 'FOUND') {
    linkedKey = ''
    return
  }
  const key = String(route.query.locationId)
  if (linkedKey === key || !mapView.value) return
  linkedKey = key
  warehouseCode.value = linked.warehouseCode
  await nextTick()
  if (allowed.value && contextValid.value && linkedKey === key)
    await mapView.value?.focusLocation(linked.id)
})
const groups = computed(() => buildSpatialLayout(visibleLocations.value, 'schematic').groups)
const timeText = computed(() =>
  updatedAt.value ? new Date(updatedAt.value).toLocaleTimeString('zh-CN', { hour12: false }) : ''
)
const areaLabel = (group: SpatialGroup) =>
  [
    !warehouseCode.value && group.warehouseCode,
    group.areaCode || '未登记区域',
    group.levelCode || '未登记层级'
  ]
    .filter(Boolean)
    .join(' / ')
const showGroup = (key: string) => void mapView.value?.showGroup(key)
const openTask = (id: number) => {
  if (allowed.value && Number.isSafeInteger(id) && id > 0)
    void router.push({ path: '/rd/stock/putaway', query: { taskId: id } })
}
watch(warehouses, (values) => {
  if (warehouseCode.value && !values.some((w) => w.code === warehouseCode.value))
    warehouseCode.value = ''
})
const fullscreen = ref(false)
const fullscreenError = ref('')
const fullscreenChange = () => {
  fullscreen.value = document.fullscreenElement === spaceHost.value
}
const toggleFullscreen = async () => {
  fullscreenError.value = ''
  try {
    if (document.fullscreenElement === spaceHost.value) await document.exitFullscreen()
    else if (spaceHost.value?.requestFullscreen) await spaceHost.value.requestFullscreen()
    else fullscreenError.value = '当前浏览器不支持全屏展示'
  } catch {
    fullscreenError.value = '全屏未能打开，可继续在当前页面查看'
  }
}
onMounted(() => document.addEventListener('fullscreenchange', fullscreenChange))
onBeforeUnmount(() => document.removeEventListener('fullscreenchange', fullscreenChange))
onBeforeUnmount(() => {
  layoutSequence++
  configuredLayout.value = undefined
})
</script>
<style scoped lang="scss">
.warehouse-space {
  --rd-space-height: 520px;

  min-width: 0;
  padding: 18px;
  letter-spacing: 0;
  color: var(--el-text-color-primary);
  background: var(--el-bg-color);
}

.space-heading,
.space-title,
.space-actions,
.space-navigation,
.business-links {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.published-layout-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.space-heading {
  justify-content: space-between;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.space-title > .el-icon {
  color: var(--el-color-primary);
}

.space-title h1 {
  margin: 0;
  font-size: 20px;
  line-height: 28px;
}

.space-title span,
.space-actions time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.space-actions {
  flex-wrap: wrap;
  gap: 6px;
}

.space-actions :deep(.el-button),
.business-links :deep(.el-button) {
  margin-left: 0;
}

.space-actions time {
  margin-right: 8px;
}

.space-navigation {
  justify-content: space-between;
  flex-wrap: wrap;
  margin-bottom: 12px;
}

.warehouse-selector {
  width: 260px;
  max-width: 100%;
}

.business-links {
  flex-wrap: wrap;
  gap: 2px;
}

.area-navigation {
  display: flex;
  max-height: 76px;
  margin-bottom: 14px;
  overflow: auto;
  border-bottom: 1px solid var(--el-border-color-lighter);
  flex-wrap: wrap;
}

.area-navigation button {
  display: flex;
  min-width: 0;
  min-height: 38px;
  padding: 8px 12px;
  font-family: inherit;
  color: var(--el-text-color-primary);
  cursor: pointer;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  align-items: center;
  gap: 8px;
}

.area-navigation button[aria-pressed='true'] {
  background: var(--el-color-primary-light-9);
  border-bottom-color: var(--el-color-primary);
}

.area-navigation button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}

.area-navigation strong {
  overflow-wrap: anywhere;
  font-size: 12px;
  font-weight: 500;
}

.area-navigation span {
  flex-shrink: 0;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.warehouse-space:fullscreen {
  --rd-space-height: clamp(380px, calc(100dvh - 340px), 1100px);
  --rd-space-mobile-height: 380px;

  width: 100%;
  height: 100%;
  overflow: auto;
  box-sizing: border-box;
}

.warehouse-space :deep(.map-surface) {
  height: var(--rd-space-height);
}

@media (width <= 760px) {
  .warehouse-space {
    padding: 12px;
  }

  .space-heading {
    gap: 8px;
  }

  .space-title h1 {
    font-size: 18px;
  }

  .space-actions time {
    display: none;
  }

  .area-navigation {
    max-height: 44px;
    flex-wrap: nowrap;
  }

  .area-navigation button {
    padding: 8px;
    flex-shrink: 0;
    white-space: nowrap;
  }

  .warehouse-space :deep(.map-surface) {
    height: 380px;
  }
}
</style>
