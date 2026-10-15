import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import * as vue from 'vue'
import { renderToString } from 'vue/server-renderer'
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'
const read = (file) => readFileSync(new URL(file, import.meta.url), 'utf8')
const spatial = evaluateSource(read('./spatialLayout.ts'))
const overview = evaluateSource(read('./warehouseOverview.ts'), { './spatialLayout': spatial })
const f = evaluateSource(read('./publishedLayout.ts'), { './warehouseOverview': overview })
const location = { id: 1, warehouseCode: 'A', locationCode: 'A-01', occupiedPackages: 5 }
const base = {
  groups: [
    {
      key: 'a',
      warehouseCode: 'A',
      nodes: [{ location, box: { x: 0, y: 0, width: 10, height: 10 } }]
    }
  ],
  unplaced: []
}
const doc = {
  schemaVersion: 1,
  warehouseCode: 'A',
  width: 800,
  height: 600,
  elements: [
    {
      id: 'l1',
      kind: 'LOCATION',
      locationId: 1,
      x: 300,
      y: 200,
      width: 100,
      height: 80,
      label: 'A-01'
    }
  ]
}
test('published geometry changes display box only, preserving real inventory object and IDs', () => {
  const result = f.applyPublishedLayout(base, doc)
  assert.equal(result.groups[0].nodes[0].box.x, 300)
  assert.equal(result.groups[0].nodes[0].box.y, -200)
  assert.equal(result.groups[0].nodes[0].location, location)
  assert.equal(location.occupiedPackages, 5)
  assert.equal(base.groups[0].nodes[0].box.x, 0)
})
test('unconfigured bins are explicitly unplaced, not silently laid out as if published', () => {
  const result = f.applyPublishedLayout(base, { ...doc, elements: [] })
  assert.equal(result.groups[0].nodes.length, 0)
  assert.equal(result.unplaced[0].reason, 'POSITION_MISSING')
})
test('cross-warehouse, duplicates and invalid coordinates cannot move another warehouse', () => {
  const unchanged = f.applyPublishedLayout(base, { ...doc, warehouseCode: 'B' })
  assert.equal(unchanged.groups[0].nodes[0].box.x, 0)
  assert.throws(() =>
    f.applyPublishedLayout(base, { ...doc, elements: [doc.elements[0], doc.elements[0]] })
  )
  assert.throws(() =>
    f.applyPublishedLayout(base, { ...doc, elements: [{ ...doc.elements[0], x: 790 }] })
  )
})

const rows = [
  {
    ...location,
    warehouseName: 'A',
    areaCode: 'A',
    levelCode: 'L2',
    enabled: true,
    frozen: false,
    displayStatus: 'EMPTY'
  },
  {
    ...location,
    id: 2,
    locationCode: 'A-02',
    warehouseName: 'A',
    areaCode: 'Z',
    levelCode: 'L1',
    enabled: false,
    frozen: true,
    displayStatus: 'BLOCKED'
  },
  {
    ...location,
    id: 3,
    locationCode: 'A-03',
    warehouseName: 'A',
    areaCode: 'Z',
    levelCode: 'L1',
    enabled: true,
    frozen: false,
    displayStatus: 'EMPTY'
  }
]
const published = {
  ...doc,
  elements: [
    { ...doc.elements[0], locationId: 2, id: 'l2', x: 60, y: 80 },
    { ...doc.elements[0], x: 540, y: 360 }
  ]
}
const setupView = () => {
  const component = read('./RdSpatialLocationMap.vue')
  const source = component
    .match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1]
    .replace('import.meta.env.BASE_URL', "'/'")
  const props = vue.reactive({
    locations: rows,
    configuredLayout: published,
    overview: true,
    highlightedIds: [],
    highlightedCodes: [],
    canSelect: false
  })
  const look = evaluateSource(read('./locationLook.ts'))
  const effects = vue.effectScope()
  const result = effects.run(() =>
    evaluateSource(
      source +
        '\nexport { group, groupKey, mode, layout, summary, scopedUnplaced, inspect, activeLocation, matchingIds, focusLocation };',
      {
        vue: { ...vue, onMounted() {}, onActivated() {}, onDeactivated() {}, onBeforeUnmount() {} },
        'leaflet/dist/leaflet.css': {},
        '@/directives/permission/hasPermi': { hasPermission: () => true },
        '@/store/modules/user': {
          useUserStoreWithOut: () => ({
            getUser: { id: 1 },
            getPermissions: new Set(['rd:putaway-task:query'])
          })
        },
        '@/utils/auth': {
          getAccessToken: () => true,
          getAuthContextRevision: () => 0,
          onAuthContextChange: () => () => {}
        },
        './spatialLayout': spatial,
        './publishedLayout': f,
        './warehouseOverview': overview,
        './locationLook': look,
        './warehouseMapQuery': evaluateSource(read('./warehouseMapQuery.ts'), {
          './spatialLayout': spatial,
          './locationLook': look
        }),
        './useMapPackageLookup': {
          useMapPackageLookup: () => ({
            raw: vue.ref(''),
            busy: vue.ref(false),
            feedback: vue.ref(''),
            clear() {}
          })
        },
        './LocationDetailsPanel.vue': {}
      },
      {
        defineProps: () => props,
        withDefaults: (p) => p,
        defineEmits: () => () => {},
        defineExpose() {}
      }
    )
  )
  return { ...result, props, component, dispose: () => effects.stop() }
}

test('published overview retains whole-warehouse XY across areas and levels without repacking or reordering', () => {
  const s = setupView()
  try {
    assert.deepEqual(
      Array.from(s.group.value.nodes, (n) => [n.location.id, n.box.x, n.box.y]),
      [
        [1, 540, -360],
        [2, 60, -80]
      ]
    )
    assert.equal(s.group.value.nodes[0].location, s.props.locations[0])
    assert.equal(s.group.value.nodes[1].location, s.props.locations[1])
    s.props.locations = [...rows].reverse()
    assert.deepEqual(
      Array.from(s.group.value.nodes, (n) => [n.location.id, n.box.x, n.box.y]),
      [
        [1, 540, -360],
        [2, 60, -80]
      ]
    )
    s.props.configuredLayout = undefined
    assert.deepEqual(
      JSON.parse(JSON.stringify(s.group.value)),
      JSON.parse(JSON.stringify(overview.buildWarehouseOverview(s.layout.value.groups)))
    )
  } finally {
    s.dispose()
  }
})

test('published schematic exposes counted unconfigured real bins and reconciles warehouse and area totals', async () => {
  const s = setupView()
  try {
    const details = s.component.match(/<details\b[\s\S]*?<\/details\s*>/)[0]
    const renderList = () =>
      renderToString(
        vue.createSSRApp({
          setup: () => ({ ...s, ...vue.toRefs(s.props) }),
          render: vue.compile(details)
        })
      )
    assert.equal(s.summary.value.total, 3)
    assert.equal(s.group.value.nodes.length + s.scopedUnplaced.value.length, s.summary.value.total)
    const html = await renderList()
    assert.match(html, /<summary>[^<]*1/)
    assert.match(html, /A-03/)
    assert.match(html, /未配置/)
    s.inspect(3)
    assert.equal(s.activeLocation.value.id, 3)
    assert.equal(s.activeLocation.value, s.props.locations[2])
    s.groupKey.value = s.layout.value.groups.find((g) => g.areaCode === 'Z').key
    await vue.nextTick()
    assert.equal(s.summary.value.total, 2)
    assert.equal(s.group.value.nodes.length + s.scopedUnplaced.value.length, 2)
    assert.match(await renderList(), /A-03/)
    await s.focusLocation(3)
    assert.equal(s.activeLocation.value.id, 3)
    assert.equal(
      s.group.value.nodes.some((n) => n.location.id === 3),
      false
    )
  } finally {
    s.dispose()
  }
})

test('all-unconfigured published warehouse keeps every real bin inspectable and generates no geometry', async () => {
  const s = setupView()
  try {
    s.props.configuredLayout = { ...published, elements: [] }
    assert.equal(s.group.value.nodes.length, 0)
    assert.equal(s.scopedUnplaced.value.length, 3)
    assert.equal(s.summary.value.total, 3)
    for (const row of rows) {
      s.inspect(row.id)
      assert.equal(s.activeLocation.value.id, row.id)
    }
    const before = JSON.stringify(rows)
    s.mode.value = 'recorded'
    await vue.nextTick()
    assert.equal(s.scopedUnplaced.value.length, 3)
    assert.equal(JSON.stringify(rows), before)
  } finally {
    s.dispose()
  }
})

test('published-map SFC script and template compile with actual integration bindings', () => {
  const filename = 'RdSpatialLocationMap.vue'
  const { descriptor, errors } = parse(read(`./${filename}`), { filename })
  assert.deepEqual(errors, [])
  const script = compileScript(descriptor, { id: 'published-layout' })
  const template = compileTemplate({
    source: descriptor.template.content,
    filename,
    id: 'published-layout',
    compilerOptions: { bindingMetadata: script.bindings, expressionPlugins: ['typescript'] }
  })
  assert.deepEqual(template.errors, [])
})
