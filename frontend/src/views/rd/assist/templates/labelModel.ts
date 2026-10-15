import type { Template } from '@pdfme/common'
import type { RdPackageUnitVO } from '@/api/rd/stock/package'
import { exactObject, finiteNumber, plainText, rect } from '../layout/visualSchema'

export const FIELD_NAMES = {
  brand: '品牌',
  packageCode: '包件编号',
  qrCode: '二维码',
  customerName: '客户',
  productModel: '产品型号',
  furnaceNo: '炉号',
  furnaceLayer: '炉层',
  packageWeight: '包件重量',
  productionDate: '生产日期'
} as const
export type LabelKey = keyof typeof FIELD_NAMES
export interface LabelField {
  key: LabelKey
  x: number
  y: number
  width: number
  height: number
  fontSize: number
}
export interface LabelContent {
  schemaVersion: 1
  name: string
  widthMm: number
  heightMm: number
  fields: LabelField[]
}

export const defaultLabel = (): LabelContent => ({
  schemaVersion: 1,
  name: '包件标签',
  widthMm: 100,
  heightMm: 76,
  fields: [
    { key: 'brand', x: 4, y: 3, width: 62, height: 7, fontSize: 12 },
    { key: 'packageCode', x: 4, y: 12, width: 62, height: 8, fontSize: 12 },
    { key: 'qrCode', x: 70, y: 5, width: 26, height: 26, fontSize: 10 },
    { key: 'customerName', x: 4, y: 24, width: 62, height: 7, fontSize: 10 },
    { key: 'productModel', x: 4, y: 33, width: 62, height: 7, fontSize: 10 },
    { key: 'furnaceNo', x: 4, y: 42, width: 42, height: 7, fontSize: 10 },
    { key: 'furnaceLayer', x: 50, y: 42, width: 45, height: 7, fontSize: 10 },
    { key: 'packageWeight', x: 4, y: 52, width: 92, height: 7, fontSize: 10 },
    { key: 'productionDate', x: 4, y: 62, width: 92, height: 7, fontSize: 10 }
  ]
})

export const parseLabel = (input: unknown): LabelContent => {
  const value = exactObject(input, ['schemaVersion', 'name', 'widthMm', 'heightMm', 'fields'])
  if (value.schemaVersion !== 1) throw new Error('标签 schema 版本无效')
  const name = plainText(value.name, 80)
  const widthMm = finiteNumber(value.widthMm, 50, 210)
  const heightMm = finiteNumber(value.heightMm, 30, 297)
  if (!Array.isArray(value.fields) || value.fields.length > 9) throw new Error('标签字段数量无效')
  const keys = new Set<string>()
  const fields = value.fields.map((raw): LabelField => {
    const item = exactObject(raw, ['key', 'x', 'y', 'width', 'height', 'fontSize'])
    if (typeof item.key !== 'string' || !Object.hasOwn(FIELD_NAMES, item.key) || keys.has(item.key))
      throw new Error('标签字段不在白名单内或重复')
    keys.add(item.key)
    const geometry = rect(item, widthMm, heightMm)
    if (
      item.key === 'qrCode' &&
      (geometry.width < 20 ||
        geometry.width > 80 ||
        geometry.height < 20 ||
        geometry.height > 80 ||
        Math.abs(geometry.width - geometry.height) >= 0.01)
    )
      throw new Error('二维码区域须为20到80毫米的正方形')
    return {
      key: item.key as LabelKey,
      ...geometry,
      fontSize: finiteNumber(item.fontSize, 6, 36)
    }
  })
  if (!keys.has('packageCode') || !keys.has('qrCode'))
    throw new Error('包件编号与二维码各须保留一个')
  return { schemaVersion: 1, name, widthMm, heightMm, fields }
}

export const toPdfmeTemplate = (input: LabelContent): Template => {
  const content = parseLabel(input)
  return {
    basePdf: { width: content.widthMm, height: content.heightMm, padding: [0, 0, 0, 0] },
    schemas: [
      content.fields.map((field) => ({
        name: field.key,
        type: field.key === 'qrCode' ? 'qrcode' : 'text',
        position: { x: field.x, y: field.y },
        width: field.width,
        height: field.height,
        content: field.key === 'qrCode' ? 'PREVIEW-QR' : FIELD_NAMES[field.key],
        ...(field.key === 'qrCode'
          ? { barColor: '#000000', backgroundColor: '#ffffff', rotate: 0 }
          : {
              fontName: 'NotoSansSC',
              fontSize: field.fontSize,
              fontColor: '#000000',
              backgroundColor: '#ffffff',
              alignment: 'left',
              verticalAlignment: 'middle',
              lineHeight: 1,
              characterSpacing: 0,
              rotate: 0,
              strikethrough: false,
              underline: false,
              textFormat: 'plain',
              dynamicFontSize: { min: 6, max: field.fontSize, fit: 'vertical' },
              overflow: 'visible'
            })
      }))
    ]
  }
}

export const fromPdfmeTemplate = (input: Template, previous: LabelContent): LabelContent => {
  const base = exactObject(input.basePdf, ['width', 'height', 'padding'])
  if (
    base.width !== previous.widthMm ||
    base.height !== previous.heightMm ||
    JSON.stringify(base.padding) !== '[0,0,0,0]' ||
    input.schemas.length !== 1
  )
    throw new Error('不接受原 PDF、静态图形或额外页面')
  if (input.schemas[0].length !== previous.fields.length)
    throw new Error('请通过字段面板增删允许字段')
  const fields = input.schemas[0].map((item): LabelField => {
    const key = item.name as LabelKey
    const old = previous.fields.find((field) => field.key === key)
    if (!old || item.type !== (key === 'qrCode' ? 'qrcode' : 'text'))
      throw new Error('字段名称与类型不可替换')
    return {
      key,
      x: item.position.x,
      y: item.position.y,
      width: item.width,
      height: item.height,
      fontSize: key === 'qrCode' ? old.fontSize : Number(item.fontSize ?? old.fontSize)
    }
  })
  return parseLabel({ ...previous, fields })
}

const dateText = (value: unknown) =>
  Array.isArray(value) && value.length >= 3
    ? `${value[0]}-${String(value[1]).padStart(2, '0')}-${String(value[2]).padStart(2, '0')}`
    : String(value ?? '').slice(0, 10)

export const packageInputs = (item: RdPackageUnitVO, brand: string): Record<LabelKey, string> => {
  if (!item.packageCode || !item.qrCode || item.qrCode.length > 4096)
    throw new Error('原始包件编号或二维码缺失或过长')
  return {
    brand,
    packageCode: item.packageCode,
    qrCode: item.qrCode,
    customerName: String(item.customerName ?? ''),
    productModel: String(item.productModel ?? ''),
    furnaceNo: String(item.furnaceNo ?? ''),
    furnaceLayer: String(item.furnaceLayer ?? ''),
    packageWeight: `${item.packageWeight} kg`,
    productionDate: dateText(item.productionDate)
  }
}
