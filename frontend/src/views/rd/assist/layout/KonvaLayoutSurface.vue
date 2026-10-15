<template>
  <div ref="host" class="layout-surface" role="img" aria-label="仓库示意布局画布">
    <Stage :config="{ width: viewportWidth, height: viewportHeight }" @click="blank" @tap="blank">
      <Layer>
        <Group :config="{ x: offsetX, y: offsetY, scaleX: scale, scaleY: scale }">
          <Rect
            :config="{
              width: content.width,
              height: content.height,
              fill: '#ffffff',
              stroke: '#c7cdd4',
              strokeWidth: 1 / scale
            }"
          />
          <Group
            v-for="item in ordered"
            :key="item.id"
            :config="{
              id: item.id,
              x: item.x,
              y: item.y,
              draggable: editable,
              clipWidth: item.width,
              clipHeight: item.height
            }"
            @click="select($event, item.id)"
            @tap="select($event, item.id)"
            @dragstart="select($event, item.id)"
            @dragmove="constrain($event, item)"
            @dragend="move($event, item)"
          >
            <Rect
              :config="{
                width: item.width,
                height: item.height,
                fill: colors[item.kind],
                stroke: selectedId === item.id ? '#be4d14' : '#526577',
                strokeWidth: selectedId === item.id ? 3 : 1,
                dash: item.kind === 'AISLE' ? [8, 5] : []
              }"
            />
            <Text
              :config="{
                text: item.label || names[item.kind],
                x: 6,
                y: 6,
                width: Math.max(1, item.width - 12),
                height: Math.max(1, item.height - 12),
                fontSize: 16,
                fill: '#19252e',
                wrap: 'char',
                listening: false
              }"
            />
          </Group>
        </Group>
      </Layer>
    </Stage>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { Stage, Layer, Group as KonvaGroup, Rect, Text } from 'vue-konva'
import type { KonvaEventObject } from 'konva/lib/Node'
import { boundPosition, type LayoutContent, type LayoutElement } from './layoutModel'

const props = defineProps<{ content: LayoutContent; selectedId: string; editable: boolean }>()
// vue-konva 4.0.1 consumes listeners itself on a fragment-rendering Group.
const Group = { ...KonvaGroup, inheritAttrs: false }
const emit = defineEmits<{
  select: [id: string]
  move: [id: string, position: { x: number; y: number }]
}>()
const host = ref<HTMLElement>()
const viewportWidth = ref(1)
const viewportHeight = ref(480)
const scale = computed(() =>
  Math.max(
    0.001,
    Math.min(viewportWidth.value / props.content.width, viewportHeight.value / props.content.height)
  )
)
const offsetX = computed(() => (viewportWidth.value - props.content.width * scale.value) / 2)
const offsetY = computed(() => (viewportHeight.value - props.content.height * scale.value) / 2)
const colors = { LOCATION: '#e5f1ff', ZONE: '#e6f2e6', AISLE: '#f1f1f1' }
const names = { LOCATION: '库位', ZONE: '区域', AISLE: '通道' }
const ordered = computed(() =>
  [...props.content.elements].sort(
    (a, b) => Number(a.kind === 'LOCATION') - Number(b.kind === 'LOCATION')
  )
)
type CanvasEvent = KonvaEventObject<Event>
const select = (event: CanvasEvent, id: string) => {
  event.cancelBubble = true
  emit('select', id)
}
const blank = () => emit('select', '')
const constrain = (event: CanvasEvent, item: LayoutElement) => {
  event.target.position(boundPosition(event.target.position(), item, props.content))
}
const move = (event: CanvasEvent, item: LayoutElement) => {
  constrain(event, item)
  if (props.editable) emit('move', item.id, event.target.position())
}
let observer: ResizeObserver | undefined
onMounted(() => {
  observer = new ResizeObserver(() => {
    if (!host.value) return
    viewportWidth.value = host.value.clientWidth
    viewportHeight.value = host.value.clientHeight
  })
  if (host.value) observer.observe(host.value)
})
onBeforeUnmount(() => observer?.disconnect())
</script>

<style scoped>
.layout-surface {
  width: 100%;
  height: 480px;
  overflow: hidden;
  background: #f5f7f8;
  touch-action: none;
}

@media (width <= 600px) {
  .layout-surface {
    height: 420px;
  }
}
</style>
