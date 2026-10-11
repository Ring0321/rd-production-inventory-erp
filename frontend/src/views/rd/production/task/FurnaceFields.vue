<template>
  <div class="furnace-fields">
    <el-form-item label="炉号" prop="furnaceNo">
      <el-select
        :model-value="furnaceNo"
        filterable
        remote
        allow-create
        default-first-option
        clearable
        :remote-method="loadFurnaces"
        :loading="furnaceLoading"
        :disabled="disabled"
        placeholder="选择或输入实际炉号"
        @visible-change="(open) => open && loadFurnaces()"
        @change="(value) => emit('update:furnaceNo', value || '')"
      >
        <el-option v-for="value in selectedFurnaces" :key="value" :value="value" :label="value" />
      </el-select>
      <div v-if="furnaceError" class="choice-error" role="alert">
        <span>炉号候选读取失败</span
        ><el-button link type="primary" :disabled="disabled" @click="loadFurnaces()"
          >重试</el-button
        >
      </div>
    </el-form-item>
    <el-form-item label="炉层" prop="furnaceLayer">
      <el-select
        :model-value="furnaceLayer"
        filterable
        remote
        allow-create
        default-first-option
        clearable
        :remote-method="loadLayers"
        :loading="layerLoading"
        :disabled="disabled"
        placeholder="选择或输入实际炉层"
        @visible-change="(open) => open && loadLayers()"
        @change="(value) => emit('update:furnaceLayer', value || '')"
      >
        <el-option-group v-for="group in layerGroups" :key="group.label" :label="group.label">
          <el-option
            v-for="option in group.options"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </el-option-group>
      </el-select>
      <div v-if="layerError" class="choice-error" role="alert">
        <span>炉层候选读取失败</span
        ><el-button link type="primary" :disabled="disabled" @click="loadLayers()">重试</el-button>
      </div>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { hasPermission } from '@/directives/permission/hasPermi'
import { RdProductionChoiceApi, type ProductionLayerChoice } from '@/api/rd/production/choices'

const props = defineProps<{
  customerName?: string
  productModel?: string
  furnaceNo?: string
  furnaceLayer?: string
  disabled?: boolean
}>()
const emit = defineEmits<{
  (event: 'update:furnaceNo', value: string): void
  (event: 'update:furnaceLayer', value: string): void
}>()
const furnaces = ref<string[]>([])
const layers = ref<ProductionLayerChoice[]>([])
const furnaceLoading = ref(false)
const layerLoading = ref(false)
const furnaceError = ref(false)
const layerError = ref(false)
let furnaceRequest = 0
let layerRequest = 0
let active = true
const params = (keyword: string) => ({
  customerName: props.customerName,
  productModel: props.productModel,
  keyword,
  limit: 100
})
const selectedFurnaces = computed(() =>
  props.furnaceNo && !furnaces.value.includes(props.furnaceNo)
    ? [props.furnaceNo, ...furnaces.value]
    : furnaces.value
)
const layerGroups = computed(() => {
  const groups = [
    { label: '有效字典', options: layers.value.filter((item) => item.source === 'DICTIONARY') },
    { label: '已登记炉层', options: layers.value.filter((item) => item.source === 'HISTORY') }
  ]
  if (props.furnaceLayer && !layers.value.some((item) => item.value === props.furnaceLayer))
    groups.unshift({
      label: '当前值',
      options: [{ value: props.furnaceLayer, label: props.furnaceLayer, source: 'HISTORY' }]
    })
  return groups.filter((group) => group.options.length)
})
const loadFurnaces = async (keyword = '') => {
  const requestId = ++furnaceRequest
  if (!hasPermission(['rd:production-output:query'])) return
  furnaceLoading.value = true
  furnaceError.value = false
  try {
    const values = await RdProductionChoiceApi.furnaces(params(keyword))
    if (active && requestId === furnaceRequest) furnaces.value = values
  } catch {
    if (active && requestId === furnaceRequest) furnaceError.value = true
  } finally {
    if (active && requestId === furnaceRequest) furnaceLoading.value = false
  }
}
const loadLayers = async (keyword = '') => {
  const requestId = ++layerRequest
  if (!hasPermission(['rd:production-output:query'])) return
  layerLoading.value = true
  layerError.value = false
  try {
    const values = await RdProductionChoiceApi.layers(params(keyword))
    if (active && requestId === layerRequest) layers.value = values
  } catch {
    if (active && requestId === layerRequest) layerError.value = true
  } finally {
    if (active && requestId === layerRequest) layerLoading.value = false
  }
}
watch(
  () => [props.customerName, props.productModel],
  () => {
    furnaceRequest++
    layerRequest++
    furnaces.value = []
    layers.value = []
    void loadFurnaces()
    void loadLayers()
  },
  { immediate: true }
)
onBeforeUnmount(() => {
  active = false
  furnaceRequest++
  layerRequest++
})
</script>

<style scoped lang="scss">
.furnace-fields {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  min-width: 0;
}

.furnace-fields :deep(.el-select) {
  width: 100%;
  min-width: 0;
}

.choice-error {
  display: flex;
  font-size: 12px;
  color: #b42318;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

@media (width <= 380px) {
  .furnace-fields {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
