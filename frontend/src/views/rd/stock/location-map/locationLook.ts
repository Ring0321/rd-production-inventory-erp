import type { RdPutawayLocationMapVO } from '@/api/rd/stock/putaway'

export const LOCATION_STATES = [
  { code: 'EMPTY', label: '空闲', fill: '#f8faf9', stroke: '#81958c', tag: 'info' as const },
  {
    code: 'PARTIAL',
    label: '部分占用',
    fill: '#e2f1e8',
    stroke: '#3f8f65',
    tag: 'success' as const
  },
  { code: 'FULL', label: '已满', fill: '#fff2cf', stroke: '#b88b2c', tag: 'warning' as const },
  {
    code: 'BLOCKED',
    label: '冻结 / 不可用',
    fill: '#fce7e7',
    stroke: '#b85050',
    tag: 'danger' as const
  },
  { code: 'UNKNOWN', label: '状态待核', fill: '#f1f2f4', stroke: '#9097a2', tag: 'info' as const }
]

export const locationLook = (location: RdPutawayLocationMapVO) =>
  LOCATION_STATES.find(
    (s) =>
      s.code ===
      (!location.enabled ||
      location.frozen ||
      ['FROZEN', 'DISABLED', 'MAINTENANCE', 'RESTRICTED'].includes(location.displayStatus)
        ? 'BLOCKED'
        : location.displayStatus)
  ) || LOCATION_STATES[4]
