import { ref, shallowRef } from 'vue'
import type { WarehouseSession } from './useWarehouseSession'
import {
  batchPendingKey,
  captureBatchPending,
  parseBatchPending,
  type WarehouseBatchPending
} from './batchPending'

export const useBatchPending = (session: WarehouseSession, kind: 'putaway' | 'loading') => {
  const pending = shallowRef<WarehouseBatchPending>()
  const storageError = ref('')
  const key = batchPendingKey(kind, session.ownerId, session.tenant)
  const assertStored = (original: WarehouseBatchPending) => {
    const raw = localStorage.getItem(key)
    if (!raw) return
    const stored = parseBatchPending(raw, kind, session.ownerId, session.tenant)
    if (JSON.stringify(stored.request) !== JSON.stringify(original.request))
      throw new Error('本机已有另一份待核请求，请刷新后核对，不能覆盖或清除它')
  }
  const persist = () => {
    session.assertCurrent()
    if (!pending.value) throw new Error('没有原核验请求')
    try {
      assertStored(pending.value)
      localStorage.setItem(key, JSON.stringify(pending.value))
      storageError.value = ''
    } catch (cause) {
      storageError.value = cause instanceof Error ? cause.message : '原核验请求保存失败，暂不能提交'
      throw new Error(storageError.value)
    }
  }
  const capture = (value: WarehouseBatchPending) => {
    session.assertCurrent()
    if (pending.value) throw new Error('请先核定原请求')
    if (value.kind !== kind || value.ownerId !== session.ownerId || value.tenant !== session.tenant)
      throw new Error('核验请求身份不一致')
    const next = captureBatchPending(value)
    try {
      assertStored(next)
    } catch (cause) {
      storageError.value = cause instanceof Error ? cause.message : '本机待核记录无法核对'
      throw cause
    }
    pending.value = next
    persist()
  }
  const markUncertain = () => {
    if (pending.value) {
      const raw = localStorage.getItem(key)
      if (!raw) return
      const stored = parseBatchPending(raw, kind, session.ownerId, session.tenant)
      if (stored.request.idempotencyKey !== pending.value.request.idempotencyKey) return
      pending.value = { ...pending.value, uncertain: true }
      persist()
    }
  }
  const assertOriginal = (original: WarehouseBatchPending) => {
    session.assertCurrent()
    if (
      !pending.value ||
      JSON.stringify(pending.value.request) !== JSON.stringify(original.request)
    )
      throw new Error('待核请求已变化，请重新读取')
    assertStored(original)
  }
  const clear = (original = pending.value) => {
    session.assertCurrent()
    if (!original) throw new Error('没有可核定的原请求')
    assertOriginal(original)
    try {
      localStorage.removeItem(key)
      pending.value = undefined
      storageError.value = ''
    } catch {
      storageError.value = '核验成功，但本机待核记录清除失败，请核对原请求'
      throw new Error(storageError.value)
    }
  }
  try {
    const raw = localStorage.getItem(key)
    if (raw) {
      pending.value = {
        ...parseBatchPending(raw, kind, session.ownerId, session.tenant),
        uncertain: true
      }
      persist()
    }
  } catch {
    storageError.value = '原核验请求读取失败，暂不能开始新的核验'
  }
  return { pending, storageError, persist, capture, markUncertain, clear, assertOriginal }
}
