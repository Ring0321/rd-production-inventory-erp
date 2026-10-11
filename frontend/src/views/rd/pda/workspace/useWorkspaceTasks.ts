import { computed, onScopeDispose, watch, type Ref } from 'vue'
import { RdProductionTaskApi } from '@/api/rd/production/task'
import { RdPutawayTaskApi } from '@/api/rd/stock/putaway'
import { RdLoadingApi } from '@/api/rd/loading'
import { createTaskFeed } from './taskFeed'
import {
  getTaskQueries,
  type RouteAvailable,
  type WorkspacePermissions,
  type WorkspaceProfileId
} from './profile'

export const useWorkspaceTasks = (
  profile: Ref<WorkspaceProfileId | undefined>,
  permissions: Ref<WorkspacePermissions>,
  hasRoute: RouteAvailable
) => {
  const queries = computed(() => getTaskQueries(profile.value, permissions.value, hasRoute))
  const production = createTaskFeed(
    () => queries.value.includes('production'),
    (params) => RdProductionTaskApi.getPage({ ...params, status: 'ACTIVE' })
  )
  const putaway = createTaskFeed(
    () => queries.value.includes('putaway'),
    (params) => RdPutawayTaskApi.getMyPage(params)
  )
  const loading = createTaskFeed(
    () => queries.value.includes('loading'),
    (params) => RdLoadingApi.getMyTaskPage({ ...params, activeOnly: true })
  )
  const feeds = { production, putaway, loading }
  const reset = () => Object.values(feeds).forEach((feed) => feed.reset())
  const refresh = () => Promise.all(queries.value.map((query) => feeds[query].load()))

  watch(
    () => [profile.value, [...permissions.value].sort().join('|'), queries.value.join('|')],
    () => {
      reset()
      void refresh()
    },
    { immediate: true, flush: 'sync' }
  )
  onScopeDispose(reset)

  return { ...feeds, queries, refresh }
}
