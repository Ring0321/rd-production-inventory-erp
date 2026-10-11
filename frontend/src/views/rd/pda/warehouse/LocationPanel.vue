<script setup lang="ts">
import { RefreshRight } from '@element-plus/icons-vue'
import type { WarehouseSession } from './useWarehouseSession'
import { useWarehouseLocations, locationLabel, amountText } from './useWarehouseLocations'

const props = defineProps<{
  session: WarehouseSession
  map: ReturnType<typeof useWarehouseLocations>
}>()
const { canQuery, loading, error, readTime, filter, warehouses, filtered, load } = props.map
</script>
<template>
  <section class="location-panel" aria-label="库位状态">
    <el-result v-if="!canQuery" icon="warning" title="无库位查询权限" />
    <template v-else>
      <div class="section-tools">
        <span v-if="readTime">读取于 {{ readTime }}</span>
        <el-tooltip content="刷新库位状态"
          ><el-button :loading="loading" aria-label="刷新库位状态" @click="load()"
            ><el-icon><RefreshRight /></el-icon></el-button
        ></el-tooltip>
      </div>
      <el-form label-position="top" class="location-filters">
        <el-form-item label="仓库"
          ><el-select v-model="filter.warehouse" clearable placeholder="全部仓库"
            ><el-option
              v-for="warehouse in warehouses"
              :key="warehouse.code"
              :value="warehouse.code"
              :label="warehouse.name" /></el-select
        ></el-form-item>
        <el-form-item label="状态"
          ><el-select v-model="filter.state" clearable placeholder="全部状态"
            ><el-option
              v-for="label in [
                '空闲',
                '可用',
                '部分占用',
                '已占用',
                '已满',
                '已预占',
                '冻结',
                '停用'
              ]"
              :key="label"
              :value="label"
              :label="label" /></el-select
        ></el-form-item>
        <el-form-item label="库位 / 库区" class="wide"
          ><el-input v-model="filter.keyword" clearable placeholder="库位号或库区"
        /></el-form-item>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" role="alert" />
      <div v-if="loading" class="loading-state" role="status">库位读取中</div>
      <el-empty v-else-if="!error && !filtered.length" description="暂无匹配库位" />
      <div class="location-rows">
        <article v-for="row in filtered" :key="row.id" class="location-row">
          <header
            ><h2>{{ row.locationCode }}</h2
            ><el-tag
              :type="
                !row.enabled || row.frozen ? 'danger' : row.occupiedPackages ? 'warning' : 'success'
              "
              >{{ locationLabel(row) }}</el-tag
            ></header
          >
          <p>{{
            [row.warehouseName, row.areaName, row.locationName].filter(Boolean).join(' / ')
          }}</p>
          <dl class="summary-grid">
            <div
              ><dt>在库</dt><dd>{{ amountText(row.occupiedPackages, '包', '未知') }}</dd></div
            >
            <div
              ><dt>在库重量</dt><dd>{{ amountText(row.occupiedWeight, 'kg', '未知') }}</dd></div
            >
            <div
              ><dt>预占</dt><dd>{{ amountText(row.reservedPackages, '包', '未知') }}</dd></div
            >
            <div
              ><dt>预占重量</dt><dd>{{ amountText(row.reservedWeight, 'kg', '未知') }}</dd></div
            >
            <div
              ><dt>容量</dt><dd>{{ amountText(row.capacityPackages, '包') }}</dd></div
            >
            <div
              ><dt>重量容量</dt><dd>{{ amountText(row.capacityWeight, 'kg') }}</dd></div
            >
            <div
              ><dt>可用数量</dt><dd>{{ amountText(row.availablePackages, '包', '未知') }}</dd></div
            >
            <div
              ><dt>可用重量</dt><dd>{{ amountText(row.availableWeight, 'kg', '未知') }}</dd></div
            >
          </dl>
        </article>
      </div>
    </template>
  </section>
</template>
