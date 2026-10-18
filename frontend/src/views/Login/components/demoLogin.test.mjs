import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { evaluateSource, plain } from '../../rd/trace/package/testHelpers.mjs'

const demo = evaluateSource(
  readFileSync(new URL('./demoLogin.ts', import.meta.url), 'utf8'),
  {},
  {
    AbortSignal
  }
)
const fixture = {
  enabled: true,
  syntheticDemoOnly: true,
  username: 'admin',
  password: 'ExampleOnly12345'
}

test('only explicitly enabled synthetic demo credentials are accepted', () => {
  assert.deepEqual(plain(demo.parsePublicDemoLogin(fixture)), {
    username: 'admin',
    password: fixture.password
  })
  for (const value of [
    null,
    {},
    { ...fixture, enabled: false },
    { ...fixture, syntheticDemoOnly: false },
    { ...fixture, username: 'maintenance' },
    { ...fixture, password: 'short' },
    { ...fixture, password: '<script>bad</script>' }
  ]) {
    assert.equal(demo.parsePublicDemoLogin(value), null)
  }
})

test('runtime configuration uses same-site base path, no identity and no cache', async () => {
  let requested
  const result = await demo.loadPublicDemoLogin('/erp/', async (url, options) => {
    requested = { url, ...options }
    return { ok: true, json: async () => fixture }
  })
  assert.equal(result.username, 'admin')
  assert.equal(requested.url, '/erp/demo-login.json')
  assert.equal(requested.credentials, 'omit')
  assert.equal(requested.cache, 'no-store')
  assert.equal(requested.redirect, 'error')
  assert.ok(requested.signal)
})

test('absent, malformed and unreachable demo configuration leaves normal login available', async () => {
  for (const fetcher of [
    async () => ({ ok: false }),
    async () => ({
      ok: true,
      json: async () => {
        throw new Error('HTML fallback')
      }
    }),
    async () => {
      throw new Error('offline')
    }
  ])
    assert.equal(await demo.loadPublicDemoLogin('/', fetcher), null)
})

test('demo button uses normal password and captcha login, never a token bypass', () => {
  const source = readFileSync(new URL('./LoginForm.vue', import.meta.url), 'utf8')
  assert.ok(source.includes('aria-label="admin 演示账号"'))
  assert.ok(source.includes('await getCode()'))
  assert.ok(source.includes('LoginApi.login({'))
  assert.ok(source.includes('if (loginLoading.value) return'))
  assert.ok(!source.includes('localStorage.setItem'))
  assert.ok(!source.includes(fixture.password))
})
