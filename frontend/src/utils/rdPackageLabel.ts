import type { RdPackageUnitVO } from '@/api/rd/stock/package'
import { productBrand } from '@/config/brand'

export type PackageLabelPaper = 'LABEL_100_76' | 'A4'
export type PackageLabel = { item: RdPackageUnitVO; qrDataUrl: string }

const escapeHtml = (value: unknown) =>
  String(value ?? '-').replace(
    /[&<>'"]/g,
    (char) =>
      ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char] || char
  )

const field = (name: string, value: unknown) => {
  const text = String(value ?? '-')
  const compact = text.length > 30 ? ' compact' : ''
  return `<div class="field${compact}"><span>${name}</span><strong>${escapeHtml(text)}</strong></div>`
}

export const buildPackageLabelHtml = (
  labels: PackageLabel[],
  paper: PackageLabelPaper,
  autoPrint = false,
  labelTitle = productBrand.labelTitle
) => {
  const cards = labels.map(({ item, qrDataUrl }) => {
    const fields = [
      ...(item.productionTaskNo ? [field('生产任务', item.productionTaskNo)] : []),
      field('客户', item.customerName),
      field('产品', item.productName || item.productModel),
      field('型号', item.productModel),
      field('炉号 / 炉层', `${item.furnaceNo} / ${item.furnaceLayer}`),
      field('净重', `${item.packageWeight} kg`),
      field('生产日期', item.productionDate),
      field('出炉 / 班次', `${item.furnaceOutDate || '-'} / ${item.shiftCode || '-'}`)
    ].join('')
    return `<article class="goods-label"><h1>${escapeHtml(labelTitle)}</h1><div class="label-body"><div>${fields}</div><div class="qr-column"><img src="${escapeHtml(qrDataUrl)}" alt="${escapeHtml(item.packageCode)} 二维码"/><div class="weight">${escapeHtml(item.packageWeight)} kg</div></div></div><div class="package-code">${escapeHtml(item.packageCode)}</div></article>`
  })
  const page = paper === 'A4' ? 'A4 portrait;margin:6mm' : '100mm 76mm;margin:4mm'
  const printScript = autoPrint
    ? '<script>window.addEventListener("load",()=>window.print())<\/script>'
    : ''
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><title>吨袋标签 ${labels.length} 张</title><style>
@page{size:${page}}*{box-sizing:border-box}body{margin:0;color:#000;background:#fff;font-family:Arial,"Microsoft YaHei",sans-serif}
.label-grid{display:grid;grid-template-columns:${paper === 'A4' ? '92mm 92mm' : '92mm'};gap:${paper === 'A4' ? '3mm 6mm' : '0'};justify-content:center}
.goods-label{width:92mm;height:68mm;border:0.4mm solid #000;padding:2mm;break-inside:avoid;page-break-inside:avoid}
h1{margin:0;padding-bottom:1.5mm;border-bottom:0.3mm solid #000;font-size:14pt;line-height:6mm;text-align:center}
.label-body{display:grid;grid-template-columns:minmax(0,1fr) 28mm;gap:2mm;padding-top:2mm;min-height:43mm}
.field{display:grid;grid-template-columns:18mm minmax(0,1fr);gap:1mm;font-size:8pt;line-height:5mm;overflow-wrap:anywhere}
.field strong{font-weight:600}.field.compact{font-size:6.5pt;line-height:3.5mm}.qr-column{align-self:center;text-align:center}
.qr-column img{display:block;width:28mm;height:28mm;image-rendering:pixelated}.weight{font-size:12pt;font-weight:700;margin-top:1mm}
.package-code{border-top:0.3mm solid #000;padding-top:1mm;font-size:11pt;line-height:5mm;font-weight:700;text-align:center;overflow-wrap:anywhere}
@media print{${paper === 'A4' ? '.goods-label:nth-child(8n){break-after:page;page-break-after:always}' : '.goods-label{break-after:page;page-break-after:always}.goods-label:last-child{break-after:auto;page-break-after:auto}'}}
@media screen{body{padding:12px}.label-grid{grid-template-columns:minmax(0,1fr);max-width:92mm;gap:12px}.goods-label{width:100%;height:auto;min-height:68mm}.field{grid-template-columns:18mm minmax(0,1fr)}}
@media screen and (max-width:340px){.label-body{grid-template-columns:1fr;gap:2mm}.qr-column{display:flex;align-items:center;justify-content:center;gap:3mm}.weight{margin-top:0}}
</style></head><body><main class="label-grid">${cards.join('')}</main>${printScript}</body></html>`
}
