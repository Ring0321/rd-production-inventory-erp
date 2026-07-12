<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams">
      <el-form-item label="关键字" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          class="!w-240px"
          clearable
          placeholder="产品编码、名称、客户或型号"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="启用状态" prop="enabled">
        <el-select v-model="queryParams.enabled" class="!w-160px" clearable placeholder="全部">
          <el-option label="启用" :value="true" />
          <el-option label="停用" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button @click="handleQuery"><Icon icon="ep:search" class="mr-5px" />查询</el-button>
        <el-button @click="resetQuery"><Icon icon="ep:refresh" class="mr-5px" />重置</el-button>
        <el-button
          type="primary"
          plain
          v-hasPermi="['rd:product-master:create']"
          @click="openForm()"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe show-overflow-tooltip>
      <el-table-column label="产品编码" prop="productCode" min-width="130" />
      <el-table-column label="产品名称" prop="productName" min-width="140" />
      <el-table-column label="客户" prop="customerName" min-width="140" />
      <el-table-column label="型号" prop="productModel" min-width="140" />
      <el-table-column label="类别" prop="category" width="110" />
      <el-table-column label="单位" prop="unit" width="80" align="center" />
      <el-table-column label="默认单包重量" prop="defaultPackageWeight" width="130" align="right" />
      <el-table-column label="质量标准" prop="qualityStandard" min-width="150" />
      <el-table-column label="状态" prop="enabled" width="90" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.enabled ? 'success' : 'info'">
            {{ scope.row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            v-hasPermi="['rd:product-master:update']"
            @click="openForm(scope.row)"
          >
            编辑
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <Pagination
      v-model:limit="queryParams.pageSize"
      v-model:page="queryParams.pageNo"
      :total="total"
      @pagination="getList"
    />
  </ContentWrap>

  <Dialog
    v-model="dialogVisible"
    :title="formData.id ? '编辑产品资料' : '新增产品资料'"
    width="720px"
  >
    <el-form
      ref="formRef"
      v-loading="formLoading"
      :model="formData"
      :rules="formRules"
      label-width="110px"
    >
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="产品编码" prop="productCode">
            <el-input v-model="formData.productCode" placeholder="请输入产品编码" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="产品名称" prop="productName">
            <el-input v-model="formData.productName" placeholder="请输入产品名称" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <RdCustomerProductFields
            v-model:customer-name="formData.customerName"
            v-model:product-model="formData.productModel"
            customer-prop="customerName"
            model-prop="productModel"
          />
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="产品类别" prop="category">
            <el-input v-model="formData.category" placeholder="如：成品、退货品" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="计量单位" prop="unit">
            <el-input v-model="formData.unit" placeholder="如：包" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="单包重量" prop="defaultPackageWeight">
            <el-input-number
              v-model="formData.defaultPackageWeight"
              :min="0.001"
              :precision="3"
              :step="0.1"
              class="!w-100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="启用" prop="enabled">
            <el-switch v-model="formData.enabled" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="质量标准" prop="qualityStandard">
        <el-input v-model="formData.qualityStandard" placeholder="请输入质量标准" />
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="formData.remark" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">确定</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import RdCustomerProductFields from '@/components/RdCustomerProductFields/index.vue'
import {
  RdProductMasterApi,
  type RdProductMasterSaveReqVO,
  type RdProductMasterVO
} from '@/api/rd/masterdata/product'

defineOptions({ name: 'RdProductMaster' })

const message = useMessage()
const loading = ref(false)
const formLoading = ref(false)
const list = ref<RdProductMasterVO[]>([])
const total = ref(0)
const queryFormRef = ref<FormInstance>()
const formRef = ref<FormInstance>()
const dialogVisible = ref(false)
const queryParams = reactive({
  pageNo: 1,
  pageSize: 10,
  keyword: undefined,
  enabled: undefined
} as {
  pageNo: number
  pageSize: number
  keyword?: string
  enabled?: boolean
})

const createDefaultForm = (): RdProductMasterSaveReqVO => ({
  productCode: '',
  productName: '',
  customerName: '',
  productModel: '',
  category: '',
  unit: '包',
  defaultPackageWeight: 0,
  qualityStandard: '',
  enabled: true,
  remark: ''
})
const formData = ref<RdProductMasterSaveReqVO>(createDefaultForm())
const formRules = reactive<FormRules>({
  productCode: [{ required: true, message: '产品编码不能为空', trigger: 'blur' }],
  productName: [{ required: true, message: '产品名称不能为空', trigger: 'blur' }],
  customerName: [{ required: true, message: '客户不能为空', trigger: 'change' }],
  productModel: [{ required: true, message: '型号不能为空', trigger: 'change' }],
  defaultPackageWeight: [{ required: true, message: '单包重量不能为空', trigger: 'change' }]
})

const getList = async () => {
  loading.value = true
  try {
    const data = await RdProductMasterApi.getPage(queryParams)
    list.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}
const handleQuery = () => {
  queryParams.pageNo = 1
  getList()
}
const resetQuery = () => {
  queryFormRef.value?.resetFields()
  handleQuery()
}
const openForm = (row?: RdProductMasterVO) => {
  formData.value = row ? { ...row } : createDefaultForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  await formRef.value?.validate()
  formLoading.value = true
  try {
    if (formData.value.id) await RdProductMasterApi.update(formData.value)
    else await RdProductMasterApi.create(formData.value)
    message.success('保存成功')
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}

onMounted(getList)
</script>
