import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { evaluateSource } from '../../views/rd/trace/package/testHelpers.mjs'

test('a 401 on a print or versioned mutation never refreshes, queues or replays it', async () => {
  let response,
    refreshes = 0,
    replays = 0
  const service = Object.assign(
    () => {
      replays++
      return Promise.resolve({})
    },
    {
      interceptors: {
        request: { use() {} },
        response: {
          use(value) {
            response = value
          }
        }
      }
    }
  )
  const axios = {
    create: () => service,
    defaults: { headers: { common: {} } },
    post: async () => {
      refreshes++
      return { data: { data: { accessToken: 'renewed' } } }
    }
  }
  const helper = evaluateSource(
    readFileSync(new URL('./nonReplayable.ts', import.meta.url), 'utf8'),
    {},
    { URL }
  )
  evaluateSource(
    readFileSync(new URL('./service.ts', import.meta.url), 'utf8'),
    {
      axios,
      'element-plus': {
        ElMessage: { error() {} },
        ElMessageBox: {},
        ElNotification: { error() {} }
      },
      qs: {},
      '@/config/axios/config': { config: { result_code: 0 } },
      '@/utils/auth': {
        getAccessToken: () => 'old',
        getRefreshToken: () => 'refresh',
        setToken() {}
      },
      './errorCode': { default: 'error' },
      '@/router': {},
      '@/hooks/web/useCache': {},
      '@/utils/encrypt': { ApiEncrypt: { getEncryptHeader: () => 'x-encrypted' } },
      './nonReplayable': helper
    },
    { useI18n: () => ({ t: (key) => key }) }
  )
  for (const url of [
    '/rd/package-unit/print',
    '/rd/package-unit/reprint',
    '/rd/package-unit/print-batch',
    '/rd/package-unit/reprint-batch',
    '/rd/visual-document/save',
    '/rd/visual-document/publish',
    '/rd/document-ocr/recognize'
  ]) {
    await assert.rejects(
      response({
        data: { code: 401, msg: 'expired' },
        config: { url, method: 'post', headers: {} },
        headers: {},
        request: {}
      }),
      { businessCode: 401 }
    )
  }
  assert.equal(refreshes, 0)
  assert.equal(replays, 0)
  assert.equal(helper.isNonReplayableOperation('GET', '/rd/visual-document/get'), false)
  assert.equal(helper.isNonReplayableOperation('POST', '/system/auth/login'), false)
})
