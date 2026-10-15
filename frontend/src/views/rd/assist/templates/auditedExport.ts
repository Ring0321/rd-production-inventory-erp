import { reactive } from 'vue'
import type { RdPackageUnitVO, RdPackagePrintReqVO } from '@/api/rd/stock/package'
import type { LabelContent } from './labelModel'

export type PrintMode = 'print' | 'reprint'
type Phase = 'IDLE' | 'PREPARING' | 'REGISTERING' | 'EXPORTED' | 'UNKNOWN' | 'FAILED'
export const createAuditedExport = (options: {
  current: () => boolean
  canPrint: (mode: PrintMode) => boolean
  getPackage: (id: number) => Promise<RdPackageUnitVO>
  generate: (template: LabelContent, item: RdPackageUnitVO) => Promise<Uint8Array>
  assertPublished?: (template: LabelContent, version: number) => Promise<void>
  audit: (mode: PrintMode, data: RdPackagePrintReqVO) => Promise<boolean>
  download: (pdf: Uint8Array, item: RdPackageUnitVO) => void
}) => {
  const state = reactive({ phase: 'IDLE' as Phase, packageId: null as number | null, error: '' })
  const attempted = reactive(new Set<number>())
  let generation = 0
  let bytes: Uint8Array | null = null
  let exportedItem: RdPackageUnitVO | null = null
  let exportedMode: PrintMode | null = null
  const clear = () => {
    generation++
    bytes = null
    exportedItem = null
    exportedMode = null
    Object.assign(state, { phase: 'IDLE', packageId: null, error: '' })
  }
  const run = async (request: {
    mode: PrintMode
    item: RdPackageUnitVO
    template: LabelContent
    publishedVersion: number
    reason: string
  }) => {
    const { mode, item } = request
    if (
      !options.current() ||
      !options.canPrint(mode) ||
      ['PREPARING', 'REGISTERING'].includes(state.phase) ||
      attempted.has(item.id)
    )
      throw new Error('不可重复登记；请先在原标签记录中核对结果')
    const reason = request.reason.trim()
    if (
      !request.publishedVersion ||
      (mode === 'print'
        ? item.status !== 'CREATED'
        : ['CREATED', 'VOIDED'].includes(item.status)) ||
      (mode === 'reprint' && (!reason || reason.length > 200))
    )
      throw new Error('请核对已发布版本、包件状态与补打原因')
    clear()
    const revision = generation
    const fresh = () => generation === revision && options.current() && options.canPrint(mode)
    state.packageId = item.id
    state.phase = 'PREPARING'
    let sent = false
    try {
      const live = await options.getPackage(item.id)
      if (!fresh()) return
      if (
        live.id !== item.id ||
        live.version !== item.version ||
        live.status !== item.status ||
        live.packageCode !== item.packageCode ||
        live.qrCode !== item.qrCode
      )
        throw new Error('包件信息已变化，请刷新预览')
      const pdf = await options.generate(request.template, live)
      if (!fresh()) return
      if (pdf.length < 6 || ![37, 80, 68, 70, 45].every((value, index) => pdf[index] === value))
        throw new Error('未生成有效 PDF，未登记打印')
      await options.assertPublished?.(request.template, request.publishedVersion)
      if (!fresh()) return
      state.phase = 'REGISTERING'
      attempted.add(item.id)
      sent = true
      const confirmed = await options.audit(mode, {
        packageId: item.id,
        ...(mode === 'reprint' ? { reprintReason: reason } : {})
      })
      if (!fresh()) return
      if (confirmed !== true) throw new Error('登记结果未确认')
      bytes = pdf
      exportedItem = live
      exportedMode = mode
      state.phase = 'EXPORTED'
      options.download(pdf, live)
    } catch {
      if (!fresh()) return
      if (state.phase === 'EXPORTED') {
        state.error = '已登记打印，文件下载失败。请下载同一份 PDF，不要再次登记'
      } else {
        state.phase = sent ? 'UNKNOWN' : 'FAILED'
        state.error = sent
          ? '打印登记结果未知，已阻断本包再次提交。请在原标签打印记录核对，勿盲目重试'
          : 'PDF 准备失败或原包件/发布版本已变化，未登记打印。请刷新后核对'
      }
    }
  }
  const downloadAgain = () => {
    if (
      !options.current() ||
      !exportedMode ||
      !options.canPrint(exportedMode) ||
      !exportedItem ||
      !bytes ||
      state.phase !== 'EXPORTED'
    )
      throw new Error('无已登记的 PDF 可下载')
    options.download(bytes, exportedItem)
  }
  const dispose = () => {
    clear()
    attempted.clear()
  }
  return { state, run, clear, dispose, downloadAgain, blocked: (id: number) => attempted.has(id) }
}
