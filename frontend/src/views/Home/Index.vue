<template>
  <div class="erp-workbench">
    <RdWarehouseSpace v-if="canViewSpace" embedded />
    <ContentWrap>
      <div class="workbench-heading">
        <div>
          <h1>ERP 业务工作台</h1>
          <p>生产、库存与销售订单的实时业务入口</p>
        </div>
        <span class="operator">{{ userStore.getUser.nickname }}</span>
      </div>
      <nav class="quick-actions" aria-label="ERP 快捷入口">
        <button
          v-for="item in visibleShortcuts"
          :key="item.path"
          type="button"
          @click="router.push(item.path)"
        >
          <span class="action-icon"><Icon :icon="item.icon" :size="19" /></span>
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
          <Icon icon="ep:arrow-right" class="arrow" />
        </button>
      </nav>
    </ContentWrap>

    <RdInventoryDashboard />
  </div>
</template>

<script lang="ts" setup>
import { checkPermi } from '@/utils/permission'
import { useUserStore } from '@/store/modules/user'
import RdInventoryDashboard from '@/views/rd/dashboard/index.vue'
import RdWarehouseSpace from '@/views/rd/stock/location-map/RdWarehouseSpace.vue'
import { canViewWarehouseSpace } from '@/views/rd/stock/location-map/warehouseSpaceAccess'

defineOptions({ name: 'Index' })

const router = useRouter()
const userStore = useUserStore()
const canViewSpace = computed(() =>
  canViewWarehouseSpace(userStore.getRoles, userStore.getPermissions)
)
const shortcuts = [
  {
    label: '仓储空间',
    description: '全仓库存与库区',
    icon: 'ep:office-building',
    path: '/warehouse-space',
    permission: 'rd:putaway-task:query'
  },
  {
    label: '生产录入',
    description: '登记炉号与炉层产额',
    icon: 'ep:edit-pen',
    path: '/rd/production/output',
    permission: 'rd:production-output:query'
  },
  {
    label: '库存调整',
    description: '查看台账并处理差异',
    icon: 'ep:box',
    path: '/rd/stock/lot',
    permission: 'rd:stock-lot:query'
  },
  {
    label: '销售订单',
    description: '跟踪应入、产额与差额',
    icon: 'ep:tickets',
    path: '/rd/order/sale',
    permission: 'rd:sale-order:query'
  },
  {
    label: '调拨管理',
    description: '跨订单调拨可用库存',
    icon: 'ep:switch',
    path: '/rd/transfer/list',
    permission: 'rd:transfer-order:query'
  },
  {
    label: '出库上线',
    description: '确认每日订单出库',
    icon: 'ep:upload',
    path: '/rd/stock/outbound',
    permission: 'rd:outbound-order:query'
  },
  {
    label: '日报报表',
    description: '核对每日业务汇总',
    icon: 'ep:data-analysis',
    path: '/rd/report/daily',
    permission: 'rd:daily-summary:query'
  }
]

const visibleShortcuts = computed(() =>
  shortcuts.filter((item) =>
    item.path === '/warehouse-space' ? canViewSpace.value : checkPermi([item.permission])
  )
)
</script>

<style scoped>
.erp-workbench > .warehouse-space {
  margin-bottom: 16px;
}

.workbench-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.workbench-heading h1 {
  margin: 0;
  font-size: 21px;
  line-height: 30px;
  letter-spacing: 0;
}

.workbench-heading p {
  margin: 3px 0 0;
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.operator {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.quick-actions {
  display: grid;
  border-top: 1px solid var(--el-border-color-lighter);
  border-left: 1px solid var(--el-border-color-lighter);
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.quick-actions button {
  display: grid;
  min-width: 0;
  min-height: 74px;
  padding: 12px 14px;
  color: var(--el-text-color-primary);
  text-align: left;
  cursor: pointer;
  background: var(--el-bg-color);
  border: 0;
  border-right: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
  grid-template-columns: 36px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 10px;
}

.quick-actions button:hover,
.quick-actions button:focus-visible {
  background: var(--el-fill-color-light);
  outline: none;
}

.action-icon {
  display: flex;
  width: 36px;
  height: 36px;
  color: #13795b;
  background: #e8f5ef;
  border-radius: 6px;
  align-items: center;
  justify-content: center;
}

.quick-actions strong,
.quick-actions small {
  display: block;
  overflow: hidden;
  letter-spacing: 0;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.quick-actions strong {
  margin-bottom: 3px;
  font-size: 14px;
}

.quick-actions small,
.arrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

@media (width <= 900px) {
  .quick-actions {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (width <= 560px) {
  .workbench-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .quick-actions {
    grid-template-columns: 1fr;
  }
}
</style>
