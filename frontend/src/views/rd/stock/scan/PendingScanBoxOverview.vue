<script setup lang="ts">
import { computed, ref, onMounted, onBeforeUnmount } from 'vue'
import { useUserStore } from '@/store/modules/user'
import type { WarehouseSession } from '../../pda/warehouse/useWarehouseSession'
import { createPendingScanBox } from './pendingScanBox'
import type { ScanDraft } from './scanCandidates'
import { useQrAuthBoundary } from './useQrAuthBoundary'

const props = defineProps<{ session: WarehouseSession }>()
const user = useUserStore()
const box = createPendingScanBox()
const rows = ref<ScanDraft[]>([])
const error = ref('')
const busy = ref(false)
let sequence = 0
const authBoundary = useQrAuthBoundary(() => {
  sequence++
  rows.value = []
  error.value = ''
  busy.value = false
})
const permitted = computed(
  () =>
    authBoundary.current() &&
    props.session.current() &&
    user.getRoles.some((role) =>
      ['rd_warehouse', 'rd_warehouse_operator', 'super_admin'].includes(role)
    )
)
const groups = computed(() => {
  const result = new Map<
    string,
    { kind: string; taskId: number; count: number; conflicts: number }
  >()
  for (const row of rows.value) {
    const key = `${row.kind}:${row.taskId}`
    const group = result.get(key) || { kind: row.kind, taskId: row.taskId, count: 0, conflicts: 0 }
    group.count++
    if (['CONFLICT', 'PROCESSED'].includes(row.state)) group.conflicts++
    result.set(key, group)
  }
  return [...result.values()]
})
const load = async () => {
  if (!permitted.value) return
  const generation = ++sequence
  busy.value = true
  try {
    const values = await box.listOwner(props.session.ownerId, props.session.tenant)
    if (generation === sequence && permitted.value) {
      rows.value = values
      error.value = ''
    }
  } catch {
    if (generation === sequence && permitted.value)
      error.value = '本机待同步箱读取失败，原记录未删除'
  } finally {
    if (generation === sequence) busy.value = false
  }
}
const open = (group: { kind: string; taskId: number }) => {
  const permission = group.kind === 'loading' ? 'rd:loading-task:query' : 'rd:putaway-task:query'
  if (permitted.value && props.session.allowed(permission))
    window.location.assign(`/pda/warehouse?section=${group.kind}&taskId=${group.taskId}`)
}
onMounted(() => {
  void load()
  window.addEventListener('duoxu-scan-drafts-changed', load)
})
onBeforeUnmount(() => {
  sequence++
  window.removeEventListener('duoxu-scan-drafts-changed', load)
  box.close()
})
</script>
<template>
  <section v-if="permitted" class="pending-overview" aria-label="本机待同步箱总览">
    <header
      ><strong><Icon icon="lucide:inbox" />本机待同步箱</strong
      ><span>{{ rows.length }} 包候选 · {{ groups.length }} 个任务</span
      ><el-tooltip content="刷新本机候选"
        ><el-button circle :loading="busy" aria-label="刷新本机候选" @click="load"
          ><Icon icon="lucide:refresh-cw" /></el-button></el-tooltip
    ></header>
    <el-alert v-if="error" :title="error" type="warning" :closable="false" />
    <el-collapse v-if="groups.length"
      ><el-collapse-item title="待核任务" name="drafts"
        ><div class="draft-groups"
          ><article v-for="group in groups" :key="`${group.kind}:${group.taskId}`"
            ><strong>{{ group.kind === 'loading' ? '装车' : '上架' }}任务 {{ group.taskId }}</strong
            ><span>{{ group.count }} 包候选 · {{ group.conflicts }} 包冲突/已处理</span
            ><el-button link type="primary" @click="open(group)"
              ><Icon icon="lucide:arrow-up-right" />打开原任务</el-button
            ></article
          ></div
        ></el-collapse-item
      ></el-collapse
    >
  </section>
</template>
<style scoped>
.pending-overview {
  padding: 12px 0;
  margin-bottom: 14px;
  border-top: 1px solid var(--el-border-color-lighter);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.pending-overview header {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.pending-overview header strong {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.pending-overview header span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.draft-groups {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(100%, 220px), 1fr));
  gap: 8px;
}

.draft-groups article {
  display: flex;
  min-width: 0;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  flex-direction: column;
  align-items: start;
  gap: 4px;
}

.draft-groups strong,
.draft-groups span {
  overflow-wrap: anywhere;
}

.draft-groups span {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
