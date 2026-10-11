import { reactive } from 'vue'

export interface TaskFeedState<T> {
  status: 'unauthorized' | 'loading' | 'ready' | 'empty' | 'error'
  rows: T[]
  total?: number
  pageNo: number
  pageSize: number
}

export const createTaskFeed = <T>(
  allowed: () => boolean,
  fetchPage: (params: { pageNo: number; pageSize: number }) => Promise<{ list: T[]; total: number }>
) => {
  const state = reactive({
    status: 'unauthorized',
    rows: [],
    total: undefined,
    pageNo: 1,
    pageSize: 5
  }) as TaskFeedState<T>
  let revision = 0

  const reset = () => {
    revision++
    state.rows = []
    state.total = undefined
    state.status = 'unauthorized'
    state.pageNo = 1
  }

  const load = async (pageNo = state.pageNo) => {
    if (!allowed()) {
      reset()
      return
    }
    const request = ++revision
    state.rows = []
    state.total = undefined
    state.status = 'loading'
    state.pageNo = Number.isSafeInteger(pageNo) && pageNo > 0 ? pageNo : 1
    try {
      const data = await fetchPage({ pageNo: state.pageNo, pageSize: state.pageSize })
      if (request !== revision || !allowed()) return
      if (
        !data ||
        !Array.isArray(data.list) ||
        !Number.isSafeInteger(data.total) ||
        data.total < 0
      ) {
        throw new Error('Invalid PageResult')
      }
      state.rows = data.list
      state.total = data.total
      state.status = data.list.length ? 'ready' : 'empty'
    } catch {
      if (request === revision && allowed()) state.status = 'error'
    }
  }

  return { state, reset, load }
}
