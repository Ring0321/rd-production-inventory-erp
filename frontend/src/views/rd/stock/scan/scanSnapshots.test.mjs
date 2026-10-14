import assert from 'node:assert/strict'
import { test } from 'node:test'
import { evaluate } from '../../pda/warehouse/testHelpers.mjs'
const candidates = evaluate(new URL('./scanCandidates.ts', import.meta.url), {}, { URL })
const m = evaluate(new URL('./scanSnapshots.ts', import.meta.url), {
  './scanCandidates': candidates
})
const source = {
  task: { id: 7, taskNo: 'LT7', version: 3, status: 'ASSIGNED' },
  lines: [
    { id: 1, packageCode: 'LD-1', packageWeight: 1000, lineStatus: 'PLANNED' },
    { id: 2, packageCode: 'LD-2', packageWeight: 800, lineStatus: 'SCANNED' }
  ]
}
test('task snapshots use only real member states and refuse unknown task versions', () => {
  const snapshot = m.loadingSnapshot(source)
  assert.equal(snapshot.lines[0].eligible, true)
  assert.equal(snapshot.lines[1].eligible, false)
  assert.equal(
    candidates.snapshotValid(
      m.loadingSnapshot({ ...source, task: { ...source.task, version: undefined } })
    ),
    false
  )
})
test('selection transfer requires exact ordered member identities and original task/version', () => {
  const s = m.loadingSnapshot(source)
  const request = { taskId: 7, version: 3, packageCodes: ['LD-1'], lineIds: [1] }
  assert.equal(m.verifyScanSelection(s, request), true)
  for (const bad of [
    { ...request, version: 4 },
    { ...request, taskId: 8 },
    { ...request, packageCodes: ['WRONG'] },
    { ...request, lineIds: [2], packageCodes: ['LD-2'] },
    { ...request, lineIds: [1, 1], packageCodes: ['LD-1', 'LD-1'] }
  ])
    assert.equal(m.verifyScanSelection(s, bad), false)
})
test('putaway candidates exclude any scanned line and no scan changes physical or inventory facts', () => {
  const detail = {
    ...source,
    lines: source.lines.map((line, i) => ({ ...line, lineStatus: 'PENDING', scanCount: i }))
  }
  const before = JSON.stringify(detail)
  const snapshot = m.putawaySnapshot(detail)
  assert.equal(snapshot.lines[0].eligible, true)
  assert.equal(snapshot.lines[1].eligible, false)
  assert.equal(JSON.stringify(detail), before)
})
