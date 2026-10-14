import assert from 'node:assert/strict'
import { test } from 'node:test'
import { evaluate, plain } from '../../pda/warehouse/testHelpers.mjs'
const m = evaluate(new URL('./packageRelations.ts', import.meta.url))
const data = () => ({
  rootPackageCode: 'LD-1',
  rootNodeKey: 'PACKAGE:1',
  scope: 'ROOT_GROUPS',
  nodes: [
    { nodeKey: 'PACKAGE:1', kind: 'PACKAGE', label: 'LD-1', packageCode: 'LD-1' },
    { nodeKey: 'PACKAGE:2', kind: 'PACKAGE', label: 'LD-2', packageCode: 'LD-2' },
    { nodeKey: 'PACKAGE:3', kind: 'PACKAGE', label: 'decoy', packageCode: 'decoy' },
    { nodeKey: 'QUALITY_INSPECTION:4', kind: 'QUALITY_INSPECTION', label: 'QC4' }
  ],
  edges: [
    {
      edgeKey: 'QC:1',
      fromNodeKey: 'QUALITY_INSPECTION:4',
      toNodeKey: 'PACKAGE:1',
      relation: 'QUALITY_COVERAGE',
      label: '覆盖',
      evidenceType: 'QUALITY_INSPECTION_MEMBER',
      evidenceId: 1
    },
    {
      edgeKey: 'QC:2',
      fromNodeKey: 'QUALITY_INSPECTION:4',
      toNodeKey: 'PACKAGE:2',
      relation: 'QUALITY_COVERAGE',
      label: '覆盖',
      evidenceType: 'QUALITY_INSPECTION_MEMBER',
      evidenceId: 2,
      sampled: false
    }
  ],
  sections: [{ kind: 'QUALITY', state: 'COMPLETE', returnedCount: 2, reason: 'actual' }]
})
test('graph identity and actual edges are validated and never coerce large IDs', () => {
  const graph = data()
  graph.nodes[0].referenceId = '9007199254740999'
  assert.equal(m.validateRelations(graph, 'LD-1').nodes[0].referenceId, '9007199254740999')
  assert.throws(() => m.validateRelations(graph, 'LD-2'), /身份/)
})
test('quality graph links only actual coverage, not decoys or legacy representatives', () => {
  const graph = data()
  const qc = graph.nodes[3]
  assert.deepEqual(
    plain(m.qualityAffectedPackages(graph, qc)).map((node) => node.packageCode),
    ['LD-1', 'LD-2']
  )
  graph.edges[1].relation = 'QC_LEGACY_REPRESENTATIVE'
  assert.equal(m.qualityAffectedPackages(graph, qc).length, 1)
})
test('root mode filters peers and edges but preserves loaded relation facts', () => {
  const graph = data()
  const before = JSON.stringify(graph)
  assert.equal(m.relationView(graph, 'ROOT').nodes.length, 2)
  assert.equal(m.relationView(graph, 'ROOT').edges.length, 1)
  assert.equal(m.relationView(graph, 'RELATED').nodes.length, 4)
  assert.equal(JSON.stringify(graph), before)
})
test('quality sample labels keep actual true, false and missing flags distinct and never infer from the representative', () => {
  const g = data()
  assert.equal(m.qualitySampleLabel(g, 'QUALITY_INSPECTION:4', 'PACKAGE:1'), '样包标识待核')
  assert.equal(m.qualitySampleLabel(g, 'QUALITY_INSPECTION:4', 'PACKAGE:2'), '覆盖包（非样包）')
  g.edges[0].sampled = true
  assert.equal(m.qualitySampleLabel(g, 'QUALITY_INSPECTION:4', 'PACKAGE:1'), '抽检样包')
  g.edges[0].relation = 'LEGACY_REPRESENTATIVE'
  assert.equal(m.qualitySampleLabel(g, 'QUALITY_INSPECTION:4', 'PACKAGE:1'), '样包标识待核')
})
test('ambiguous node keys, dangling evidence and hidden counts are rejected', () => {
  const g = data()
  g.nodes.push({ ...g.nodes[0] })
  assert.throws(() => m.validateRelations(g, 'LD-1'), /节点/)
  const bad = data()
  bad.edges[0].toNodeKey = 'PACKAGE:99'
  assert.throws(() => m.validateRelations(bad, 'LD-1'), /证据/)
  const hidden = data()
  hidden.sections[0].state = 'REDACTED'
  assert.throws(() => m.validateRelations(hidden, 'LD-1'), /范围/)
})
test('the readable current-package mainline hides loaded history details without deleting their actual records', () => {
  const g = data()
  g.nodes.push({ nodeKey: 'TASK_EVENT:5', kind: 'TASK_EVENT', label: 'History' })
  g.edges.push({
    edgeKey: 'H:5',
    fromNodeKey: 'PACKAGE:1',
    toNodeKey: 'TASK_EVENT:5',
    evidenceType: 'PACKAGE_TASK_EVENT',
    label: 'History'
  })
  assert.ok(!m.relationView(g, 'ROOT').nodes.some((node) => node.kind === 'TASK_EVENT'))
  assert.ok(m.relationView(g, 'RELATED').nodes.some((node) => node.kind === 'TASK_EVENT'))
  assert.equal(g.nodes.length, 5)
})
test('quality focus is a readable exact member subgraph, never an attribute-based or legacy-expanded group', () => {
  const g = data()
  const focus = m.relationView(g, 'QUALITY', 'QUALITY_INSPECTION:4')
  assert.equal(focus.nodes.length, 3)
  assert.equal(focus.edges.length, 2)
  assert.ok(!focus.nodes.some((node) => node.packageCode === 'decoy'))
  g.edges[1].relation = 'LEGACY_REPRESENTATIVE'
  assert.equal(m.relationView(g, 'QUALITY', 'QUALITY_INSPECTION:4').nodes.length, 2)
  assert.equal(m.relationView(g, 'QUALITY', 'PACKAGE:1').nodes.length, 0)
})
