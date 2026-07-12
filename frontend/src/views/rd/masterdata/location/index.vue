<template>
  <ContentWrap>
    <el-form ref="queryFormRef" :inline="true" :model="queryParams">
      <el-form-item label="关键字" prop="keyword">
        <el-input
          v-model="queryParams.keyword"
          class="!w-240px"
          clearable
          placeholder="仓库、区域或货位编码"
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
          v-hasPermi="['rd:warehouse-location:create']"
          @click="openForm()"
        >
          <Icon icon="ep:plus" class="mr-5px" />新增
        </el-button>
      </el-form-item>
    </el-form>
  </ContentWrap>

  <ContentWrap>
    <el-table v-loading="loading" :data="list" stripe show-overflow-tooltip>
      <el-table-column label="仓库编码" prop="warehouseCode" width="120" />
      <el-table-column label="仓库名称" prop="warehouseName" min-width="130" />
      <el-table-column label="区域编码" prop="areaCode" width="110" />
      <el-table-column label="区域名称" prop="areaName" min-width="120" />
      <el-table-column label="货位编码" prop="locationCode" width="130" />
      <el-table-column label="货位名称" prop="locationName" min-width="130" />
      <el-table-column label="容量（包）" prop="capacityPackages" width="110" align="right" />
      <el-table-column label="允许类别" prop="allowedCategory" min-width="120" />
      <el-table-column label="冻结" prop="frozen" width="80" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.frozen ? 'danger' : 'success'">
            {{ scope.row.frozen ? '冻结' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" prop="enabled" width="80" align="center">
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
            v-hasPermi="['rd:warehouse-location:update']"
            @click="openForm(scope.row)"
            >编辑</el-button
          >
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
    :title="formData.id ? '编辑仓库货位' : '新增仓库货位'"
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
        <el-col :span="12"
          ><el-form-item label="仓库编码" prop="warehouseCode"
            ><el-input v-model="formData.warehouseCode" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="仓库名称" prop="warehouseName"
            ><el-input v-model="formData.warehouseName" /></el-form-item
        ></el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12"
          ><el-form-item label="区域编码" prop="areaCode"
            ><el-input v-model="formData.areaCode" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="区域名称" prop="areaName"
            ><el-input v-model="formData.areaName" /></el-form-item
        ></el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12"
          ><el-form-item label="货位编码" prop="locationCode"
            ><el-input v-model="formData.locationCode" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="货位名称" prop="locationName"
            ><el-input v-model="formData.locationName" /></el-form-item
        ></el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="容量（包）" prop="capacityPackages">
            <el-input-number
              v-model="formData.capacityPackages"
              :min="0"
              :precision="0"
              class="!w-100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12"
          ><el-form-item label="允许类别" prop="allowedCategory"
            ><el-input v-model="formData.allowedCategory" /></el-form-item
        ></el-col>
      </el-row>
      <el-row :gutter="20">
        <el-col :span="12"
          ><el-form-item label="冻结" prop="frozen"
            ><el-switch v-model="formData.frozen" /></el-form-item
        ></el-col>
        <el-col :span="12"
          ><el-form-item label="启用" prop="enabled"
            ><el-switch v-model="formData.enabled" /></el-form-item
        ></el-col>
      </el-row>
      <el-form-item label="备注" prop="remark"
        ><el-input v-model="formData.remark" type="textarea" :rows="3"
      /></el-form-item>
    </el-form>
    <template #footer>
      <el-button type="primary" :loading="formLoading" @click="submitForm">确定</el-button>
      <el-button @click="dialogVisible = false">取消</el-button>
    </template>
  </Dialog>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import {
  RdWarehouseLocationApi,
  type RdWarehouseLocationSaveReqVO,
  type RdWarehouseLocationVO
} from '@/api/rd/masterdata/location'

defineOptions({ name: 'RdWarehouseLocation' })

const message = useMessage()
const loading = ref(false)
const formLoading = ref(false)
const list = ref<RdWarehouseLocationVO[]>([])
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
const createDefaultForm = (): RdWarehouseLocationSaveReqVO => ({
  warehouseCode: '',
  warehouseName: '',
  areaCode: '',
  areaName: '',
  locationCode: '',
  locationName: '',
  capacityPackages: 0,
  allowedCategory: '',
  frozen: false,
  enabled: true,
  remark: ''
})
const formData = ref<RdWarehouseLocationSaveReqVO>(createDefaultForm())
const formRules = reactive<FormRules>({
  warehouseCode: [{ required: true, message: '仓库编码不能为空', trigger: 'blur' }],
  warehouseName: [{ required: true, message: '仓库名称不能为空', trigger: 'blur' }],
  locationCode: [{ required: true, message: '货位编码不能为空', trigger: 'blur' }]
})

const getList = async () => {
  loading.value = true
  try {
    const data = await RdWarehouseLocationApi.getPage(queryParams)
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
const openForm = (row?: RdWarehouseLocationVO) => {
  formData.value = row ? { ...row } : createDefaultForm()
  dialogVisible.value = true
  nextTick(() => formRef.value?.clearValidate())
}
const submitForm = async () => {
  await formRef.value?.validate()
  formLoading.value = true
  try {
    if (formData.value.id) await RdWarehouseLocationApi.update(formData.value)
    else await RdWarehouseLocationApi.create(formData.value)
    message.success('保存成功')
    dialogVisible.value = false
    await getList()
  } finally {
    formLoading.value = false
  }
}
onMounted(getList)
</script>
