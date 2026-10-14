import request from '@/config/axios'

export interface PackageRelationNode {
  nodeKey: string
  kind: string
  label: string
  status?: string
  referenceId?: number | string
  packageCode?: string
  locationId?: number | string
  locationCode?: string
  description?: string
}
export interface PackageRelationEdge {
  edgeKey: string
  fromNodeKey: string
  toNodeKey: string
  relation: string
  label: string
  evidenceType: string
  evidenceId?: number | string
  sampled?: boolean
  lineStatus?: string
  sourceVersion?: number
}
export interface PackageRelations {
  rootPackageCode: string
  rootNodeKey: string
  scope: 'ROOT_GROUPS'
  nodes: PackageRelationNode[]
  edges: PackageRelationEdge[]
  sections: Array<{
    kind: string
    state: 'COMPLETE' | 'REDACTED' | 'LIMITED' | 'INCOMPLETE'
    returnedCount: number
    reason: string
  }>
}
export const getPackageRelations = (packageCode: string) =>
  request.get<PackageRelations>({
    url: '/rd/package-unit/relations',
    params: { packageCode }
  })
