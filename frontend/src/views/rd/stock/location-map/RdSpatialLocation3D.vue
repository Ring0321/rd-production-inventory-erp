<template>
  <div class="warehouse-3d" aria-label="立体库位示意图" :aria-busy="loading">
    <div ref="host" class="scene-host"></div>
    <div class="scene-labels">
      <button
        v-for="label in labels.filter((item) => item.visible)"
        :key="label.id"
        type="button"
        class="scene-bin-label"
        :class="{ selected: selectedId === label.id, target: highlightedIds.includes(label.id) }"
        :style="{ left: `${label.x}px`, top: `${label.y}px`, width: `${label.width}px` }"
        :data-location-id="label.id"
        :aria-label="`查看库位 ${lookup(label.id)?.code}`"
        :title="`${lookup(label.id)?.code} · ${lookup(label.id)?.state.label} · ${lookup(label.id)?.summary} · ${lookup(label.id)?.reserved}`"
        @click="$emit('inspect', label.id)"
      >
        <strong>{{ lookup(label.id)?.code }}</strong>
        <span v-if="!label.compact">{{ lookup(label.id)?.state.label }}</span>
        <span v-if="!label.compact">{{ lookup(label.id)?.summary }}</span>
      </button>
    </div>
    <div v-if="loading" class="scene-loading"
      ><el-icon class="is-loading"><Icon icon="ep:loading" /></el-icon
    ></div>
  </div>
</template>
<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount, onActivated, onDeactivated } from 'vue'
import type { SpatialNode } from './spatialLayout'
import { buildWarehouse3D } from './warehouse3DModel'
import type { WarehouseScene, ProjectedBin } from './warehouseScene'

const props = withDefaults(
  defineProps<{
    nodes: SpatialNode[]
    selectedId?: number
    highlightedIds?: number[]
    matchingIds?: number[]
  }>(),
  { highlightedIds: () => [] }
)
const emit = defineEmits<{ inspect: [id: number]; unavailable: [] }>()
const model = computed(() => buildWarehouse3D(props.nodes))
const lookup = (id: number) => model.value.bins.find((bin) => bin.id === id)
const host = ref<HTMLElement>()
const labels = ref<ProjectedBin[]>([])
const loading = ref(false)
let scene: WarehouseScene | undefined
let alive = true
let active = true
let sequence = 0
const stop = () => {
  sequence++
  scene?.dispose()
  scene = undefined
  labels.value = []
  loading.value = false
}
const sync = () => {
  scene?.setModel(
    model.value.bins,
    props.selectedId,
    props.highlightedIds,
    false,
    props.matchingIds
  )
}
const start = async () => {
  if (!alive || !active || !host.value || scene || loading.value) return
  const version = ++sequence
  loading.value = true
  try {
    const { createWarehouseScene } = await import('./warehouseScene')
    if (!alive || !active || version !== sequence || !host.value) return
    scene = createWarehouseScene(host.value, {
      select: (id) => {
        if (alive && active && version === sequence && lookup(id)) emit('inspect', id)
      },
      frame: (values) => {
        if (alive && active && version === sequence) labels.value = values
      },
      unavailable: () => {
        if (alive && active && version === sequence) {
          stop()
          emit('unavailable')
        }
      }
    })
    sync()
  } catch {
    if (alive && active && version === sequence) {
      stop()
      emit('unavailable')
    }
  } finally {
    if (version === sequence) loading.value = false
  }
}
watch(
  [
    model,
    () => props.selectedId,
    () => props.highlightedIds.join(','),
    () => props.matchingIds?.join(',')
  ],
  sync
)
onMounted(start)
onActivated(() => {
  active = true
  void start()
})
onDeactivated(() => {
  active = false
  stop()
})
onBeforeUnmount(() => {
  alive = false
  stop()
})
defineExpose({
  fit: () => scene?.fit(),
  focus: (ids: number[]) => scene?.fit(ids),
  zoom: (factor: number) => scene?.zoom(factor),
  iso: () => scene?.fit(undefined, 'iso'),
  top: () => scene?.fit(undefined, 'top')
})
</script>
<style scoped lang="scss">
.warehouse-3d {
  position: relative;
  width: 100%;
  height: 520px;
  overflow: hidden;
  background: #f2f5f5;
}

.scene-host,
.scene-labels {
  position: absolute;
  inset: 0;
}

.scene-host :deep(canvas) {
  display: block;
  width: 100%;
  height: 100%;
  outline-offset: -3px;
}

.scene-labels {
  pointer-events: none;
}

.scene-bin-label {
  position: absolute;
  display: grid;
  width: 148px;
  padding: 4px 6px;
  font-family: inherit;
  font-size: 11px;
  line-height: 15px;
  color: #263d30;
  text-align: center;
  pointer-events: auto;
  cursor: pointer;
  background: rgb(255 255 255 / 92%);
  border: 1px solid #cad5d5;
  border-radius: 3px;
  transform: translate(-50%, -10%);
  gap: 2px;
}

.scene-bin-label strong {
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scene-bin-label span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.scene-bin-label.target {
  color: #155fa3;
}

.scene-bin-label.selected,
.scene-bin-label:focus-visible {
  outline: 2px solid #216fba;
  outline-offset: 1px;
}

.scene-loading {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  font-size: 24px;
  background: #f8faf9;
}

@media (width <= 760px) {
  .warehouse-3d {
    height: 380px;
  }
}
</style>
