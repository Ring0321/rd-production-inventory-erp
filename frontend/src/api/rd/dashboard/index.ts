import request from '@/config/axios'

export type RdDashboardDate = string | number[]

export interface RdDashboardStockMetricVO {
  stockLotCount: number
  warehouseQty: number
  lockedQty: number
  availableQty: number
  stockWeight: number
}

export interface RdDashboardOrderMetricVO {
  openOrderCount: number
  requiredQty: number
  remainingQty: number
  fulfilledQty: number
}

export interface RdDashboardMovementVO {
  productionQty: number
  inboundQty: number
  outboundQty: number
  transferInQty: number
  transferOutQty: number
  returnQty: number
  netChangeQty: number
}

export interface RdDashboardTrendVO extends RdDashboardMovementVO {
  bizDate: RdDashboardDate
}

export interface RdDashboardRankingVO {
  name: string
  stockLotCount: number
  warehouseQty: number
  stockWeight: number
}

export interface RdDashboardOrderStatusVO {
  status: string
  orderCount: number
  requiredQty: number
  fulfilledQty: number
}

export interface RdDashboardOpenOrderVO {
  id: number
  orderNo: string
  orderDate: RdDashboardDate
  customerName: string
  productModel: string
  requiredQty: number
  actualOutputQty: number
  adjustedDifferenceQty: number
  fulfilledQty: number
  status: string
}

export interface RdInventoryDashboardVO {
  asOfDate: RdDashboardDate
  stock: RdDashboardStockMetricVO
  order: RdDashboardOrderMetricVO
  todayMovement: RdDashboardMovementVO
  trend: RdDashboardTrendVO[]
  customerRanking: RdDashboardRankingVO[]
  modelRanking: RdDashboardRankingVO[]
  orderStatuses: RdDashboardOrderStatusVO[]
  openOrders: RdDashboardOpenOrderVO[]
}

export const RdInventoryDashboardApi = {
  getOverview: async (asOfDate?: string) => {
    return await request.get({ url: '/rd/dashboard/overview', params: { asOfDate } })
  }
}
