import { ref, watch, onBeforeUnmount, onDeactivated } from 'vue'
import { RdPackageUnitApi, type RdPackageUnitVO } from '@/api/rd/stock/package'
import { RdStockLotApi, type RdStockLotVO } from '@/api/rd/stock/lot'
import { extractPackageCode } from '@/utils/rdDigitalLink'
import type { RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'
import { isLocationIdentityValid } from './spatialLayout'

const positiveId = (id: unknown): id is number => Number.isSafeInteger(id) && Number(id) > 0
const isStock = (unit: RdPackageUnitVO) => ['IN_STOCK', 'OUTBOUND_PENDING'].includes(unit.status)
const snapshot = (unit: RdPackageUnitVO) =>
  JSON.stringify([
    unit.id,
    unit.packageCode,
    unit.status,
    unit.locationId,
    unit.stockLotId,
    unit.version
  ])

export const useMapPackageLookup = (options: {
  allowed: () => boolean
  locations: () => RdPutawayLocationMapVO[]
  locate: (id: number, current: () => boolean) => Promise<void>
}) => {
  const raw = ref('')
  const busy = ref(false)
  const feedback = ref('')
  let sequence = 0
  const clear = () => {
    sequence++
    busy.value = false
    feedback.value = ''
  }
  watch([raw, options.allowed, options.locations], clear, { deep: true, flush: 'sync' })
  onBeforeUnmount(clear)
  onDeactivated(clear)
  const lookup = async () => {
    clear()
    const version = sequence
    const current = () => version === sequence && options.allowed()
    if (!current()) return
    const code = extractPackageCode(raw.value)
    if (!code || code.length > 128 || /[/\\?#\u0000-\u0020\u007f]/.test(code)) {
      feedback.value = '包码格式不正确，请核对二维码内容'
      return
    }
    busy.value = true
    try {
      const rows = await RdPackageUnitApi.trace({ packageCode: code })
      if (!current()) return
      if (rows.length !== 1 || rows[0].packageCode !== code) {
        feedback.value = '未找到唯一包件，不能确定当前库位'
        return
      }
      const unit = rows[0]
      if (!isStock(unit)) {
        feedback.value = '该包不处于已入库或待出库状态，不能定位当前在库货物'
        return
      }
      if (
        !positiveId(unit.id) ||
        !positiveId(unit.locationId) ||
        !positiveId(unit.stockLotId) ||
        !Number.isSafeInteger(unit.version) ||
        unit.version < 0
      ) {
        feedback.value = '包件库位或库存关联待核，请联系仓管'
        return
      }
      const stock: RdStockLotVO = await RdStockLotApi.get(unit.stockLotId)
      if (!current()) return
      if (
        stock.id !== unit.stockLotId ||
        stock.locationId !== unit.locationId ||
        !Number.isFinite(stock.warehouseQty) ||
        stock.warehouseQty <= 0
      ) {
        feedback.value = '包件与当前库存位置不一致，请仓管核对'
        return
      }
      const check = await RdPackageUnitApi.trace({ packageCode: code })
      if (!current()) return
      if (check.length !== 1 || snapshot(check[0]) !== snapshot(unit)) {
        feedback.value = '查询期间包件记录已变化，请重新查询'
        return
      }
      if (!isLocationIdentityValid(options.locations(), unit.locationId)) {
        feedback.value = '当前范围没有唯一对应库位，请刷新库位数据后核对'
        return
      }
      await options.locate(unit.locationId, current)
      if (current())
        feedback.value = `已定位登记库位 ${options.locations().find((l) => l.id === unit.locationId)?.locationCode}，以仓管确认记录为准`
    } catch {
      if (current()) feedback.value = '货物位置读取失败，请重试；未改变库存或库位'
    } finally {
      if (version === sequence) busy.value = false
    }
  }
  return { raw, busy, feedback, lookup, clear }
}
