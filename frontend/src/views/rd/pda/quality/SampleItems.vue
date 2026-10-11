<template>
  <section aria-label="抽检结果">
    <div class="section-title"
      ><h2>抽检结果</h2
      ><el-tooltip content="添加检测项目"
        ><el-button :disabled="disabled" aria-label="添加检测项目" @click="emit('add')"
          ><el-icon><Plus /></el-icon></el-button></el-tooltip
    ></div>
    <div v-for="(item, index) in items" :key="index" class="sample-item">
      <div class="item-heading"
        ><strong>检测项 {{ index + 1 }}</strong
        ><el-tooltip content="删除检测项目"
          ><el-button
            :disabled="disabled || items.length <= 1"
            aria-label="删除检测项目"
            @click="emit('remove', index)"
            ><el-icon><Delete /></el-icon></el-button></el-tooltip
      ></div>
      <el-form label-position="top" :disabled="disabled">
        <el-form-item label="抽样包">
          <el-select
            v-model="item.samplePackageId"
            :aria-label="`检测项${index + 1}抽样包`"
            placeholder="选择实际抽样包"
          >
            <el-option
              v-for="bag in samples"
              :key="bag.id"
              :value="bag.id"
              :label="bag.packageCode"
            />
          </el-select>
        </el-form-item>
        <div class="fields-grid">
          <el-form-item label="项目编码"
            ><el-input
              v-model="item.itemCode"
              :aria-label="`检测项${index + 1}项目编码`"
              maxlength="64"
          /></el-form-item>
          <el-form-item label="项目名称"
            ><el-input
              v-model="item.itemName"
              :aria-label="`检测项${index + 1}项目名称`"
              maxlength="128"
          /></el-form-item>
          <el-form-item label="标准下限"
            ><el-input-number
              v-model="item.standardMin"
              :controls="false"
              :aria-label="`检测项${index + 1}标准下限`"
          /></el-form-item>
          <el-form-item label="标准上限"
            ><el-input-number
              v-model="item.standardMax"
              :controls="false"
              :aria-label="`检测项${index + 1}标准上限`"
          /></el-form-item>
          <el-form-item label="实测值"
            ><el-input-number
              v-model="item.measuredValue"
              :controls="false"
              :aria-label="`检测项${index + 1}实测值`"
          /></el-form-item>
          <el-form-item label="单位"
            ><el-input v-model="item.unit" :aria-label="`检测项${index + 1}单位`" maxlength="32"
          /></el-form-item>
        </div>
        <el-form-item label="检测备注"
          ><el-input
            v-model="item.remark"
            type="textarea"
            :aria-label="`检测项${index + 1}备注`"
            maxlength="512"
        /></el-form-item>
      </el-form>
    </div>
  </section>
</template>
<script setup lang="ts">
import { Plus, Delete } from '@element-plus/icons-vue'
import type { RdQualityInspectionItemVO } from '@/api/rd/quality/inspection'
import type { RdPackageUnitVO } from '@/api/rd/stock/package'
defineProps<{ items: RdQualityInspectionItemVO[]; samples: RdPackageUnitVO[]; disabled: boolean }>()
const emit = defineEmits<{ add: []; remove: [index: number] }>()
</script>
<style scoped>
h2 {
  margin: 0;
  font-size: 15px;
}

.section-title,
.item-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.sample-item {
  padding-top: 12px;
  margin-top: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.item-heading {
  margin-bottom: 12px;
}

.fields-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

:deep(.el-input-number),
:deep(.el-select) {
  width: 100%;
  min-width: 0;
}

:deep(.el-button) {
  width: 40px;
  height: 40px;
  padding: 0;
}

@media (width <= 380px) {
  .fields-grid {
    grid-template-columns: 1fr;
  }
}
</style>
