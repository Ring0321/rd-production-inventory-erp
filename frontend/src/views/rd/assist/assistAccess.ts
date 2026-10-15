export const assistTools = [
  {
    key: 'weight',
    name: '智能配吨',
    icon: 'ep:scale-to-original',
    required: ['rd:pickup-slip:query', 'rd:package-unit:query', 'rd:loading-task:dispatch']
  },
  {
    key: 'replay',
    name: '时间回放',
    icon: 'ep:clock',
    required: ['rd:production-task:query', 'rd:package-unit:query', 'rd:stock-transaction:query']
  },
  {
    key: 'ocr',
    name: '单据识别',
    icon: 'ep:document-checked',
    any: ['rd:pickup-slip:create', 'rd:quality-inspection:create']
  },
  {
    key: 'layout',
    name: '布局编辑',
    icon: 'ep:office-building',
    required: ['rd:warehouse-location:query', 'rd:putaway-task:query']
  },
  { key: 'templates', name: '标签模板', icon: 'ep:printer', required: ['rd:package-unit:query'] }
] as const
export const availableAssistTools = (roles: string[], permissions: Set<string>) => {
  if (roles.includes('rd_forklift_driver')) return []
  const has = (p: string) => permissions.has('*:*:*') || permissions.has(p)
  return assistTools.filter((tool) =>
    'required' in tool ? tool.required.every(has) : tool.any.some(has)
  )
}
export const canViewAssist = (roles: string[], permissions: Set<string>) =>
  availableAssistTools(roles, permissions).length > 0
