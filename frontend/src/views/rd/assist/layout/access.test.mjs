import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'

const setup = (kind, roles = ['super_admin'], permissions = ['*:*:*']) => {
  const identity = vue.reactive({ id: 1, roles, permissions: new Set(permissions) })
  let revision = 0
  let cachedId = 1
  let token = true
  let authListener
  let tick
  const events = {}
  const lifecycle = { onBeforeUnmount: [], onDeactivated: [] }
  const store = {
    get getUser() {
      return identity
    },
    get getRoles() {
      return identity.roles
    },
    get getPermissions() {
      return identity.permissions
    }
  }
  const module = evaluateSource(
    readFileSync(new URL('./useVisualAccess.ts', import.meta.url), 'utf8'),
    {
      vue: {
        ...vue,
        ...Object.fromEntries(
          Object.keys(lifecycle).map((key) => [key, (fn) => lifecycle[key].push(fn)])
        )
      },
      '@/store/modules/user': { useUserStore: () => store },
      '@/directives/permission/hasPermi': {
        hasPermission: (keys) =>
          identity.permissions.has('*:*:*') || keys.some((key) => identity.permissions.has(key))
      },
      '@/utils/auth': {
        getAccessToken: () => token,
        getAuthContextRevision: () => revision,
        getCurrentUserId: () => cachedId,
        getTenantId: () => 1,
        getVisitTenantId: () => null,
        getCachedAuthScope: () => ({
          userId: cachedId,
          roles: identity.roles,
          permissions: [...identity.permissions]
        }),
        onAuthContextChange: (fn) => {
          authListener = fn
          return () => {
            authListener = null
          }
        }
      }
    },
    {
      window: {
        setInterval: (fn) => {
          tick = fn
          return 1
        },
        clearInterval: () => {
          tick = null
        },
        addEventListener: (key, fn) => {
          events[key] = fn
        },
        removeEventListener: (key) => {
          delete events[key]
        }
      }
    }
  )
  const scope = vue.effectScope()
  const access = scope.run(() => module.useVisualAccess(kind))
  let cleared = 0
  access.onClear(() => cleared++)
  return {
    identity,
    access,
    get cleared() {
      return cleared
    },
    revoke: () => {
      token = false
      revision++
      authListener?.()
    },
    replaceCachedUser: () => {
      cachedId = 2
      tick?.()
    },
    deactivate: () => lifecycle.onDeactivated.forEach((fn) => fn()),
    dispose: () => {
      lifecycle.onBeforeUnmount.forEach((fn) => fn())
      scope.stop()
      assert.equal(tick, null)
      assert.deepEqual(events, {})
    }
  }
}

test('only original super_admin role edits LABEL; print permission is distinct', () => {
  const admin = setup('LABEL', ['admin'], ['rd:package-unit:query'])
  assert.equal(admin.access.canRead.value, true)
  assert.equal(admin.access.canEdit.value, false)
  assert.equal(admin.access.canPrint('print'), false)
  admin.dispose()
  const actualAdmin = setup('LABEL', ['super_admin'], ['rd:package-unit:query'])
  assert.equal(actualAdmin.access.canEdit.value, true)
  assert.equal(actualAdmin.access.canPrint('print'), false)
  actualAdmin.dispose()
})

test('LAYOUT accepts either original read permission, writes only original update permission', () => {
  for (const query of ['rd:putaway-task:query', 'rd:warehouse-location:query']) {
    const ctx = setup('LAYOUT', ['warehouse'], [query])
    assert.equal(ctx.access.canRead.value, true)
    assert.equal(ctx.access.canEdit.value, false)
    ctx.dispose()
  }
})

test('role/permission changes synchronously clear and permanently invalidate mounted editors', () => {
  for (const change of [
    (v) => v.identity.roles.push('changed'),
    (v) => v.identity.permissions.delete('*:*:*'),
    (v) => v.revoke(),
    (v) => v.replaceCachedUser(),
    (v) => v.deactivate()
  ]) {
    const ctx = setup('LABEL')
    assert.equal(ctx.access.canEdit.value, true)
    change(ctx)
    assert.equal(ctx.access.current(), false)
    assert.equal(ctx.access.canEdit.value, false)
    assert.equal(ctx.cleared, 1)
    ctx.dispose()
  }
})
