import assert from 'node:assert/strict'
import { test } from 'node:test'
import { evaluate, plain } from '../../pda/warehouse/testHelpers.mjs'

const m = evaluate(new URL('./scanCandidates.ts', import.meta.url), {}, { URL })
const snapshot = () => ({
  kind: 'loading',
  taskId: 7,
  taskNo: 'LT-7',
  version: 3,
  status: 'ASSIGNED',
  lines: [
    { lineId: 1, packageCode: 'LD-001', packageWeight: 1000, eligible: true },
    { lineId: 2, packageCode: 'LD-002', packageWeight: 800, eligible: true },
    { lineId: 3, packageCode: 'LD-003', packageWeight: 900, eligible: false }
  ]
})
const draft = () => ({
  ownerId: 5,
  tenant: '1',
  kind: 'loading',
  taskId: 7,
  id: '1',
  packageCode: 'LD-002',
  observedVersion: 3,
  seenAt: 1,
  state: 'LOCAL'
})

test('scan accepts exact package identity and stable local/historical links without following a URL', () => {
  assert.equal(m.parseScanCode('LD-002'), 'LD-002')
  assert.equal(m.parseScanCode('https://example.test/pkg/LD-002'), 'LD-002')
  assert.equal(m.parseScanCode('http://127.0.0.1:9000/pkg/LD-002'), 'LD-002')
  assert.equal(m.parseScanCode('legacy.001'), 'legacy.001')
  assert.equal(m.parseScanCode('ld-002'), 'ld-002')
})
test('scan rejects credentials, scripts, non-package URLs, parameters, path escapes and unbounded input', () => {
  for (const raw of [
    'javascript:alert(1)',
    'https://a:b@host/pkg/LD-002',
    'https://host/x',
    'https://host/pkg/LD-002?internal=1',
    'https://host/pkg/%2fLD-002',
    'LD 2',
    'a'.repeat(1025)
  ])
    assert.equal(m.parseScanCode(raw), undefined)
})
test('matched, duplicate, wrong, invalid and processed are distinct; no operation mutates the task', () => {
  const s = snapshot()
  const before = JSON.stringify(s)
  assert.equal(m.classifyScan('LD-002', s, []).kind, 'MATCH')
  assert.equal(m.classifyScan('LD-002', s, ['LD-002']).kind, 'DUPLICATE')
  assert.equal(m.classifyScan('ld-002', s, []).kind, 'WRONG')
  assert.equal(m.classifyScan('LD-003', s, []).kind, 'UNAVAILABLE')
  assert.equal(m.classifyScan('bad input', s, []).kind, 'INVALID')
  assert.equal(JSON.stringify(s), before)
})
test('closed task and ambiguous membership cannot authorize scanned candidates', () => {
  const s = snapshot()
  s.status = 'OUTBOUNDED'
  assert.equal(m.classifyScan('LD-001', s, []).kind, 'UNAVAILABLE')
  s.lines.push({ ...s.lines[0], lineId: 99 })
  assert.equal(m.snapshotValid(s), false)
})
test('revalidation preserves original version and rejects identity or membership changes', () => {
  const s = snapshot()
  const d = draft()
  assert.equal(m.reconcileDraft(d, s).state, 'VERIFIED')
  s.version++
  assert.equal(m.reconcileDraft(d, s).state, 'CONFLICT')
  assert.equal(d.observedVersion, 3)
  s.taskId++
  assert.throws(() => m.reconcileDraft(d, s), /身份/)
})
test('processed, unknown weight and nonmember drafts never become verified', () => {
  const s = snapshot()
  const d = draft()
  d.packageCode = 'LD-003'
  assert.equal(m.reconcileDraft(d, s).state, 'PROCESSED')
  d.packageCode = 'UNKNOWN'
  assert.equal(m.reconcileDraft(d, s).state, 'CONFLICT')
  d.packageCode = 'LD-002'
  delete s.lines[1].packageWeight
  assert.equal(m.reconcileDraft(d, s).state, 'CONFLICT')
})
test('candidate weight sums actual tail bag weights once and keeps missing facts unknown', () => {
  const s = snapshot()
  assert.equal(m.candidateWeight(['LD-001', 'LD-002', 'LD-002'], s), 1800)
  assert.equal(m.candidateWeight(['UNKNOWN'], s), undefined)
  s.lines[0].packageWeight = 0.1
  s.lines[1].packageWeight = 0.2
  assert.equal(m.candidateWeight(['LD-001', 'LD-002'], s), 0.3)
})
test('scope separates role/task/tenant/user and rejects invalid owners', () => {
  assert.notEqual(m.scanScopeKey(draft()), m.scanScopeKey({ ...draft(), tenant: '2' }))
  assert.notEqual(m.scanScopeKey(draft()), m.scanScopeKey({ ...draft(), ownerId: 6 }))
  assert.notEqual(m.scanScopeKey(draft()), m.scanScopeKey({ ...draft(), kind: 'putaway' }))
  assert.throws(() => m.scanScopeKey({ ...draft(), ownerId: 0 }), /身份/)
  assert.deepEqual(plain(m.reconcileDraft(draft(), snapshot())).state, 'VERIFIED')
})
