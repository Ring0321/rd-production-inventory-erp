import { reactive } from 'vue'
import type { VisualDocumentVO } from '@/api/rd/assist/visualDocument'

type Kind = 'LAYOUT' | 'LABEL'
export interface VisualApi {
  getVisualDocument(kind: Kind, scopeId: string): Promise<VisualDocumentVO>
  saveVisualDocument(data: {
    kind: Kind
    scopeId: string
    expectedVersion: number
    content: unknown
  }): Promise<VisualDocumentVO>
  publishVisualDocument(data: {
    kind: Kind
    scopeId: string
    expectedVersion: number
  }): Promise<VisualDocumentVO>
}
interface DocumentState<T> {
  scopeId: string
  document: VisualDocumentVO | null
  content: T | null
  dirty: boolean
  busy: boolean
  conflict: boolean
  error: string
}

export const createDocumentSession = <T>(options: {
  kind: Kind
  current: () => boolean
  canEdit: () => boolean
  api: VisualApi
  parse: (value: unknown) => T
  empty: () => T
}) => {
  const state = reactive({
    scopeId: '',
    document: null,
    content: null,
    dirty: false,
    busy: false,
    conflict: false,
    error: ''
  }) as DocumentState<T>
  let generation = 0
  let saved = ''
  const clear = () => {
    generation++
    saved = ''
    Object.assign(state, {
      scopeId: '',
      document: null,
      content: null,
      dirty: false,
      busy: false,
      conflict: false,
      error: ''
    })
  }
  const fresh = (revision: number) => generation === revision && options.current()
  const accept = (record: VisualDocumentVO) => {
    if (
      record.kind !== options.kind ||
      record.scopeId !== state.scopeId ||
      !Number.isSafeInteger(record.version) ||
      record.version < 0
    )
      throw new Error('配置响应作用域或版本无效')
    const draft = record.draft == null ? null : options.parse(record.draft)
    const published = record.published == null ? null : options.parse(record.published)
    const next = draft ?? published ?? options.empty()
    state.document = { ...record, draft, published }
    state.content = next
    saved = JSON.stringify(next)
    state.dirty = false
    state.conflict = false
  }
  const load = async (scopeId: string) => {
    clear()
    if (!scopeId || !options.current()) return
    state.scopeId = scopeId
    const revision = generation
    state.busy = true
    try {
      const record = await options.api.getVisualDocument(options.kind, scopeId)
      if (fresh(revision)) accept(record)
    } catch {
      if (fresh(revision)) state.error = '配置加载失败，请重新加载；未使用本地替代数据'
    } finally {
      if (fresh(revision)) state.busy = false
    }
  }
  const change = (value: unknown) => {
    if (!options.current() || !options.canEdit() || state.busy || !state.document)
      throw new Error('当前不可编辑')
    state.content = options.parse(value)
    state.dirty = JSON.stringify(state.content) !== saved
  }
  const write = async (mode: 'save' | 'publish') => {
    if (
      !options.current() ||
      !options.canEdit() ||
      state.busy ||
      state.conflict ||
      !state.document ||
      !state.content
    )
      throw new Error('请核对权限并重新加载配置')
    if (mode === 'publish' && (state.dirty || state.document.draft == null))
      throw new Error('请先保存草稿再发布')
    const content = options.parse(state.content)
    const revision = generation
    const request = {
      kind: options.kind,
      scopeId: state.scopeId,
      expectedVersion: state.document.version
    }
    state.busy = true
    state.error = ''
    try {
      const record =
        mode === 'save'
          ? await options.api.saveVisualDocument({ ...request, content })
          : await options.api.publishVisualDocument(request)
      if (fresh(revision)) accept(record)
    } catch {
      if (fresh(revision)) {
        // Any ambiguous write must be reconciled by reading, never by blind retry.
        state.conflict = true
        state.error = '版本冲突或提交结果未确认。本地草稿已保留，请重新加载核对服务端版本'
      }
    } finally {
      if (fresh(revision)) state.busy = false
    }
  }
  return { state, clear, load, change, save: () => write('save'), publish: () => write('publish') }
}
