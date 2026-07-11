<template>
  <el-form-item label="客户" :prop="customerProp">
    <el-select
      :model-value="customerName"
      filterable
      remote
      reserve-keyword
      clearable
      :allow-create="allowCreate"
      default-first-option
      :remote-method="searchCustomers"
      :loading="customerLoading"
      :disabled="disabled"
      placeholder="请选择或输入客户"
      class="!w-240px"
      @change="handleCustomerChange"
      @visible-change="handleCustomerVisible"
    >
      <el-option v-for="item in customerOptions" :key="item" :label="item" :value="item" />
    </el-select>
  </el-form-item>
  <el-form-item label="型号" :prop="modelProp">
    <el-select
      :model-value="productModel"
      filterable
      remote
      reserve-keyword
      clearable
      :allow-create="allowCreate"
      default-first-option
      :remote-method="searchModels"
      :loading="modelLoading"
      :disabled="disabled || (!queryMode && !customerName)"
      placeholder="请选择或输入型号"
      class="!w-240px"
      @change="(value) => emit('update:productModel', value || undefined)"
      @visible-change="handleModelVisible"
    >
      <el-option v-for="item in modelOptions" :key="item" :label="item" :value="item" />
    </el-select>
  </el-form-item>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { RdCustomerProductApi } from '@/api/rd/masterdata/customer-product'

const props = withDefaults(
  defineProps<{
    customerName?: string
    productModel?: string
    customerProp?: string
    modelProp?: string
    allowCreate?: boolean
    queryMode?: boolean
    disabled?: boolean
  }>(),
  {
    customerProp: 'customerName',
    modelProp: 'productModel',
    allowCreate: true,
    queryMode: false,
    disabled: false
  }
)

const emit = defineEmits<{
  (event: 'update:customerName', value?: string): void
  (event: 'update:productModel', value?: string): void
}>()

const customerOptions = ref<string[]>([])
const modelOptions = ref<string[]>([])
const customerLoading = ref(false)
const modelLoading = ref(false)

const mergeSelected = (options: string[], selected?: string) =>
  selected && !options.includes(selected) ? [selected, ...options] : options

const searchCustomers = async (keyword = '') => {
  customerLoading.value = true
  try {
    customerOptions.value = mergeSelected(
      await RdCustomerProductApi.getCustomerOptions({ keyword, limit: 100 }),
      props.customerName
    )
  } finally {
    customerLoading.value = false
  }
}

const searchModels = async (keyword = '') => {
  modelLoading.value = true
  try {
    modelOptions.value = mergeSelected(
      await RdCustomerProductApi.getModelOptions({
        customerName: props.customerName || undefined,
        keyword,
        limit: 100
      }),
      props.productModel
    )
  } finally {
    modelLoading.value = false
  }
}

const handleCustomerChange = async (value?: string) => {
  emit('update:customerName', value || undefined)
  emit('update:productModel', undefined)
  modelOptions.value = []
  if (value || props.queryMode) await searchModels()
}

const handleCustomerVisible = (visible: boolean) => {
  if (visible && customerOptions.value.length === 0) searchCustomers()
}

const handleModelVisible = (visible: boolean) => {
  if (visible && modelOptions.value.length === 0) searchModels()
}

watch(
  () => props.customerName,
  (customer, previous) => {
    if (customer !== previous && (customer || props.queryMode)) searchModels()
  }
)

onMounted(async () => {
  await searchCustomers()
  if (props.customerName || props.queryMode) await searchModels()
})
</script>
