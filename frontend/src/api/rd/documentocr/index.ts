import request from '@/config/axios'

export const fieldKeys = ['customer', 'order', 'model', 'bagCount', 'weightKg'] as const
export type FieldKey = (typeof fieldKeys)[number]
export interface OcrLine {
  id: string
  page: number
  text: string
  confidence?: number
}
export interface OcrCandidate {
  value: string
  sourceLineIds: string[]
  evidence: string
  confidence?: number
}
export interface OcrField {
  state: 'UNKNOWN' | 'CANDIDATE'
  candidates: OcrCandidate[]
}
export interface OcrResult {
  schemaVersion: 1
  engine: 'PaddleOCR'
  model: string
  device: 'cpu'
  pages: number
  rawLines: OcrLine[]
  candidates: Record<FieldKey, OcrField>
}
export interface OcrStatus {
  available: boolean
  state: string
  maxBytes: number
  maxPages: number
}

export const getOcrStatus = (signal: AbortSignal) =>
  request.get<OcrStatus>({ url: '/rd/document-ocr/status', signal, timeout: 5000 })

export const recognizeDocument = (file: File, signal: AbortSignal) => {
  const data = new FormData()
  data.append('file', file)
  return request.post<OcrResult>({
    url: '/rd/document-ocr/recognize',
    data,
    signal,
    timeout: 80000,
    headersType: 'multipart/form-data'
  })
}
