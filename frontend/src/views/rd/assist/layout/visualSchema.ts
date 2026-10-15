export const exactObject = (value: unknown, keys: readonly string[]): Record<string, unknown> => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('配置必须是对象')
  const object = value as Record<string, unknown>
  if (Object.keys(object).some((key) => !keys.includes(key))) throw new Error('配置含不允许的属性')
  return object
}

export const finiteNumber = (value: unknown, min: number, max: number): number => {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < min || value > max) {
    throw new Error(`数值必须在 ${min} 到 ${max} 之间`)
  }
  return value
}

export const plainText = (value: unknown, max = 100, allowEmpty = false): string => {
  if (
    typeof value !== 'string' ||
    (!allowEmpty && !value.trim()) ||
    value.length > max ||
    /[<>\u0000-\u001f\u007f]|(?:https?:|javascript:|data:|www\.)/iu.test(value)
  )
    throw new Error('仅允许有长度限制的纯文本，不接受链接或标记')
  return value
}

export const rect = (value: Record<string, unknown>, width: number, height: number) => {
  const x = finiteNumber(value.x, 0, width)
  const y = finiteNumber(value.y, 0, height)
  const w = finiteNumber(value.width, 1, width)
  const h = finiteNumber(value.height, 1, height)
  if (x + w > width + 0.000001 || y + h > height + 0.000001) throw new Error('对象超出画布边界')
  return { x, y, width: w, height: h }
}

export const copy = <T>(value: T): T => JSON.parse(JSON.stringify(value))
