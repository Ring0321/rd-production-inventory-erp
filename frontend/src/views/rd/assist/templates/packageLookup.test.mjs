import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'
const load = () =>
  evaluateSource(readFileSync(new URL('./packageLookup.ts', import.meta.url), 'utf8'))
test('full package code uses original exact trace, not the unfiltered legacy page', async () => {
  const calls = []
  const row = { id: 7, packageCode: 'PK-7' }
  const result = await load().queryLabelPackages(
    {
      trace: async (args) => {
        calls.push(args)
        return [row]
      },
      getPage: () => {
        throw Error('wrong endpoint')
      }
    },
    ' PK-7 ',
    1
  )
  assert.equal(result.total, 1)
  assert.equal(result.list[0].id, 7)
  assert.equal(calls[0].packageCode, 'PK-7')
})
test('empty query keeps bounded original pagination and unknown code stays empty', async () => {
  const fn = load().queryLabelPackages
  assert.equal(
    (await fn({ getPage: async (args) => ({ list: [], total: 20, page: args.pageNo }) }, '', 2))
      .page,
    2
  )
  assert.equal((await fn({ trace: async () => [] }, 'MISSING', 1)).total, 0)
})
test('a contradictory or broadened package identity is never accepted', async () => {
  await assert.rejects(
    load().queryLabelPackages({ trace: async () => [{ id: 8, packageCode: 'OTHER' }] }, 'PK-7', 1)
  )
})
