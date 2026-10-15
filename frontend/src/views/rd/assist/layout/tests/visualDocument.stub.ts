export interface VisualDocumentVO {
  kind: 'LAYOUT' | 'LABEL'
  scopeId: string
  version: number
  draft: unknown | null
  published: unknown | null
  publishedVersion: number
  updatedAt: string | null
}
// Type-only integration fixture. Production must use the parent's real API module.
export const getVisualDocument = async (
  _kind: 'LAYOUT' | 'LABEL',
  _scopeId: string
): Promise<VisualDocumentVO> => {
  throw new Error('type fixture')
}
export const saveVisualDocument = async (_data: {
  kind: 'LAYOUT' | 'LABEL'
  scopeId: string
  expectedVersion: number
  content: unknown
}): Promise<VisualDocumentVO> => {
  throw new Error('type fixture')
}
export const publishVisualDocument = async (_data: {
  kind: 'LAYOUT' | 'LABEL'
  scopeId: string
  expectedVersion: number
}): Promise<VisualDocumentVO> => {
  throw new Error('type fixture')
}
