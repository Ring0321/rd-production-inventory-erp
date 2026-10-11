import assert from 'node:assert/strict'
import { test } from 'node:test'
import * as vue from 'vue'
import { evaluate } from './testHelpers.mjs'

const setup = () => {
  const identity = vue.reactive({ id: 7, tenant: 1, visit: 2 })
  let dispose
  let activate, deactivate
  const module = evaluate('./useWarehouseSession.ts', {
    vue: {
      onActivated: (callback) => {
        activate = callback
      },
      onDeactivated: (callback) => {
        deactivate = callback
      },
      onBeforeUnmount: (callback) => {
        dispose = callback
      }
    },
    '@/store/modules/user': { useUserStore: () => ({ getUser: identity }) },
    '@/utils/auth': { getTenantId: () => identity.tenant, getVisitTenantId: () => identity.visit },
    '@/directives/permission/hasPermi': {
      hasPermission: (values) => values.includes('rd:putaway-task:query')
    }
  })
  return {
    session: module.useWarehouseSession(),
    identity,
    dispose: () => dispose(),
    activate: () => activate(),
    deactivate: () => deactivate()
  }
}

test('warehouse context captures the effective visit tenant and only existing permissions', () => {
  const h = setup()
  assert.equal(h.session.ownerId, 7)
  assert.equal(h.session.tenant, '2')
  assert.equal(h.session.allowed('rd:putaway-task:query'), true)
  assert.equal(h.session.allowed('rd:putaway-task:confirm'), false)
})

test('account or effective tenant changes invalidate the old warehouse context', () => {
  for (const change of [
    (value) => {
      value.id = 8
    },
    (value) => {
      value.visit = 3
    },
    (value) => {
      value.visit = undefined
    }
  ]) {
    const h = setup()
    change(h.identity)
    assert.equal(h.session.current(), false)
    assert.equal(h.session.allowed('rd:putaway-task:query'), false)
    assert.throws(h.session.assertCurrent)
    assert.equal(h.session.ownerId, 7)
    assert.equal(h.session.tenant, '2')
  }
})

test('unmounted warehouse pages cannot continue commands despite an unchanged login', () => {
  const h = setup()
  h.dispose()
  assert.equal(h.session.current(), false)
  assert.throws(h.session.assertCurrent)
})

test('cached Web warehouse pages invalidate a dialog generation when deactivated', () => {
  const h = setup()
  const generation = h.session.generation()
  h.deactivate()
  assert.equal(h.session.current(), false)
  h.activate()
  assert.equal(h.session.current(), true)
  assert.notEqual(h.session.generation(), generation)
})
