import type { OcrResult, FieldKey } from '@/api/rd/documentocr'
export const fieldKeys: FieldKey[]
export function fileKind(bytes: Uint8Array): 'image/png' | 'image/jpeg' | 'application/pdf' | null
export function previewDimensions(
  bytes: Uint8Array,
  kind: string
): { width: number; height: number }
export function allowedScope(scope: {
  userId: number
  roles: string[]
  permissions: string[]
}): boolean
export function draftFromResult(result?: OcrResult): Record<FieldKey, string>
export interface Ticket {
  generation: number
  signal: AbortSignal
}
export function createOcrSession(): {
  clear(): void
  begin(): Ticket
  current(ticket: Ticket): boolean
  dispose(): void
}
