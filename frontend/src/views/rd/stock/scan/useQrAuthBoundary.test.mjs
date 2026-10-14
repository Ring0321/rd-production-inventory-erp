import assert from 'node:assert/strict'
import { test } from 'node:test'
import { ref } from 'vue'
import { evaluate } from '../../pda/warehouse/testHelpers.mjs'

const setup = () => {
  let revision = 0,
    token = true,
    listener,
    unmount,
    cleared = 0
  const m = evaluate(new URL('./useQrAuthBoundary.ts', import.meta.url), {
    vue: {
      ref,
      onBeforeUnmount: (fn) => {
        unmount = fn
      }
    },
    '@/utils/auth': {
      getAccessToken: () => token,
      getAuthContextRevision: () => revision,
      onAuthContextChange: (fn) => {
        listener = fn
        return () => {
          listener = undefined
        }
      }
    }
  })
  const boundary = m.useQrAuthBoundary(() => {
    cleared++
  })
  return {
    boundary,
    cleared: () => cleared,
    change: () => {
      revision++
      listener?.()
    },
    silentChange: () => {
      revision++
    },
    tokenRemoved: () => {
      token = false
    },
    unmount: () => unmount(),
    hasListener: () => !!listener
  }
}
test('token or tenant revision synchronously clears old QR work without needing role or permission changes', () => {
  const h = setup()
  assert.equal(h.boundary.current(), true)
  h.change()
  assert.equal(h.cleared(), 1)
  assert.equal(h.boundary.valid.value, false)
  assert.equal(h.boundary.current(), false)
})
test('asynchronous guards check the actual captured revision and token, not a cached computed permission', () => {
  const h = setup()
  h.silentChange()
  assert.equal(h.boundary.current(), false)
  const another = setup()
  another.tokenRemoved()
  assert.equal(another.boundary.current(), false)
})
test('unmount invalidates guards and removes the context subscription', () => {
  const h = setup()
  h.unmount()
  assert.equal(h.boundary.current(), false)
  assert.equal(h.hasListener(), false)
  h.change()
  assert.equal(h.cleared(), 0)
})
