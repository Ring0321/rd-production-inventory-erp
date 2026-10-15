import request from '@/config/axios'

export type VisualDocumentKind = 'LAYOUT' | 'LABEL'
export interface VisualDocumentVO {
  kind: VisualDocumentKind
  scopeId: string
  version: number
  draft: unknown | null
  published: unknown | null
  publishedVersion: number
  updatedAt: string | number | null
}
export const getVisualDocument = (kind: VisualDocumentKind, scopeId: string) =>
  request.get<VisualDocumentVO>({ url: '/rd/visual-document/get', params: { kind, scopeId } })
export const saveVisualDocument = (data: {
  kind: VisualDocumentKind
  scopeId: string
  expectedVersion: number
  content: unknown
}) => request.post<VisualDocumentVO>({ url: '/rd/visual-document/save', data })
export const publishVisualDocument = (data: {
  kind: VisualDocumentKind
  scopeId: string
  expectedVersion: number
}) => request.post<VisualDocumentVO>({ url: '/rd/visual-document/publish', data })
