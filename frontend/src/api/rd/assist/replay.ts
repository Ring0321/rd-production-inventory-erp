import request from '@/config/axios'
import type { ReplayMember, ReplayEvent } from '@/views/rd/assist/replay/replayFacts'
export interface ReplayView {
  taskId: number
  taskNo: string
  customerName: string
  members: ReplayMember[]
  events: ReplayEvent[]
  complete: boolean
  timeZone: string
  timeBasis: string
}
export const getWarehouseReplay = (productionTaskId: number) =>
  request.get<ReplayView>({ url: '/rd/warehouse-replay/task', params: { productionTaskId } })
