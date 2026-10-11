import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluate } from '../pda/warehouse/testHelpers.mjs'

const setup = (refresh = async () => true) => {
  const hooks = {}
  const events = new Map()
  const identity = vue.reactive({ id: 7, tenant: 1, visit: undefined, allowed: true, ready: true })
  const document = {
    visibilityState: 'visible',
    addEventListener: (name, callback) => events.set(name, callback),
    removeEventListener: (name) => events.delete(name)
  }
  let scheduled,
    cleared = 0
  const module = evaluate(
    '../../stock/useStockRefresh.ts',
    {
      vue: {
        ...vue,
        onMounted: (fn) => {
          hooks.mount = fn
        },
        onActivated: (fn) => {
          hooks.activate = fn
        },
        onDeactivated: (fn) => {
          hooks.deactivate = fn
        },
        onBeforeUnmount: (fn) => {
          hooks.unmount = fn
        }
      },
      '@/store/modules/user': { useUserStore: () => ({ getUser: identity }) },
      '@/utils/auth': { getTenantId: () => identity.tenant, getVisitTenantId: () => identity.visit }
    },
    {
      document,
      setInterval: (fn, delay) => {
        assert.equal(delay, 5000)
        scheduled = fn
        return 42
      },
      clearInterval: () => {
        cleared++
      }
    }
  )
  const work = module.useStockRefresh(
    () => identity.allowed,
    refresh,
    () => identity.ready
  )
  return {
    work,
    identity,
    hooks,
    document,
    events,
    scheduled: () => scheduled,
    cleared: () => cleared
  }
}

test('stock auto-refresh is read-only, permission-gated, visible and idle only', async () => {
  let calls = 0
  const h = setup(async () => {
    calls++
    return true
  })
  h.hooks.mount()
  assert.equal(typeof h.scheduled(), 'function')
  for (const change of [
    () => {
      h.identity.allowed = false
    },
    () => {
      h.identity.ready = false
    },
    () => {
      h.document.visibilityState = 'hidden'
    }
  ]) {
    h.identity.allowed = true
    h.identity.ready = true
    h.document.visibilityState = 'visible'
    change()
    await h.work.run()
  }
  assert.equal(calls, 0)
  h.document.visibilityState = 'visible'
  await h.work.run()
  assert.equal(calls, 1)
})

test('an in-flight refresh is not duplicated and busy state does not invalidate its own response', async () => {
  let release,
    calls = 0
  const h = setup((canApply) => {
    calls++
    h.identity.ready = false
    return new Promise((resolve) => {
      release = () => {
        assert.equal(canApply(), true)
        resolve(true)
      }
    })
  })
  const first = h.work.run()
  await h.work.run()
  assert.equal(calls, 1)
  release()
  await first
})

test('identity, tenant, permission or activation changes invalidate late responses', async () => {
  for (const change of [
    (h) => {
      h.identity.id = 8
    },
    (h) => {
      h.identity.visit = 2
    },
    (h) => {
      h.identity.allowed = false
    },
    (h) => {
      h.hooks.deactivate()
    },
    (h) => {
      h.hooks.unmount()
    }
  ]) {
    let release
    const h = setup(
      (canApply) =>
        new Promise((resolve) => {
          release = () => {
            assert.equal(canApply(), false)
            resolve(false)
          }
        })
    )
    h.hooks.mount()
    const running = h.work.run()
    change(h)
    release()
    await running
  }
})

test('failed automatic reads surface an error and a healthy refresh clears it', async () => {
  let fail = true
  const h = setup(async () => {
    if (fail) throw new Error('Offline')
    return true
  })
  await h.work.run()
  assert.match(h.work.error.value, /未刷新/)
  fail = false
  await h.work.run()
  assert.equal(h.work.error.value, '')
})

test('deactivation stops polling, activation resumes, and teardown removes the listener', async () => {
  const h = setup()
  h.hooks.mount()
  h.hooks.deactivate()
  assert.equal(h.cleared(), 1)
  assert.equal(h.work.current(), false)
  h.hooks.activate()
  assert.equal(h.work.current(), true)
  h.hooks.unmount()
  assert.equal(h.events.size, 0)
  assert.equal(h.cleared(), 2)
})

test('reactivation cannot overlap an unresolved quiet refresh', async () => {
  const releases = []
  const h = setup(() => new Promise((resolve) => releases.push(resolve)))
  h.hooks.mount()
  const first = h.work.run()
  h.hooks.deactivate()
  h.hooks.activate()
  assert.equal(releases.length, 1)
  releases[0](true)
  await first
  const second = h.work.run()
  assert.equal(releases.length, 2)
  releases[1](true)
  await second
})
