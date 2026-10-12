export interface ProductBrand {
  name: string
  shortName: string
  subtitle: string
  labelTitle: string
  logoUrl: string
}

const brandText = (value: string | undefined, fallback: string) => {
  const text = value?.trim() || fallback
  if (text.length > 64 || /[<>"'\u0000-\u001f]/u.test(text)) {
    throw new Error('Product brand must be plain text within 64 characters')
  }
  return text
}

export const createProductBrand = (
  env: Record<string, string | undefined> = {}
): Readonly<ProductBrand> => {
  const name = brandText(env.VITE_APP_TITLE, '垛序 ERP')
  const shortName = brandText(env.VITE_PRODUCT_SHORT_NAME, '垛序')
  const base = env.BASE_URL?.startsWith('/') && !env.BASE_URL.startsWith('//') ? env.BASE_URL : '/'
  const configuredLogo = env.VITE_PRODUCT_LOGO?.trim() || 'duoxu-mark.png'
  const logo = /^(?:[A-Za-z0-9_-]+\/)*[A-Za-z0-9_-]+\.(?:png|webp|jpg|jpeg)$/i.test(configuredLogo)
    ? configuredLogo
    : 'duoxu-mark.png'
  return Object.freeze({
    name,
    shortName,
    subtitle: brandText(env.VITE_PRODUCT_SUBTITLE, '生产与仓储协同系统'),
    labelTitle: shortName + ' · 货品标识卡',
    logoUrl: base.replace(/\/+$/, '') + '/' + logo
  })
}

export const productBrand = createProductBrand(import.meta.env ?? {})

export const applyProductBrandHtml = (html: string, brand: Readonly<ProductBrand>) => {
  const escape = (value: string) =>
    value.replace(
      /[&<>"']/g,
      (char) =>
        ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char] || char
    )
  return html
    .replaceAll('__PRODUCT_NAME__', escape(brand.name))
    .replaceAll('__PRODUCT_SUBTITLE__', escape(brand.subtitle))
    .replaceAll('__PRODUCT_LOGO__', escape(brand.logoUrl))
}
