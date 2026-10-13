import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync, existsSync } from 'node:fs'
import * as three from 'three'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'

const source = readFileSync(new URL('./warehouseScene.ts', import.meta.url), 'utf8')
test('partial scene initialization rolls back canvas, controls, listeners and renderer', () => {
  const counts = { renderer: 0, controls: 0, listeners: 0 }
  const host = { children: [], appendChild(canvas) { this.children.push(canvas) } }
  const canvas = {
    setAttribute() {},
    addEventListener() { counts.listeners++ },
    removeEventListener() { counts.listeners-- },
    remove() { host.children = host.children.filter(child => child !== canvas) }
  }
  class Renderer {
    domElement = canvas
    shadowMap = {}
    setPixelRatio() {}
    setClearColor() {}
    dispose() { counts.renderer++ }
    getContext() { return { isContextLost: () => false } }
    forceContextLoss() {}
  }
  class Controls {
    constructor() {}
    connect() {}
    listenToKeyEvents() {}
    addEventListener() { counts.listeners++ }
    removeEventListener() { counts.listeners-- }
    dispose() { counts.controls++ }
  }
  const imports = {
    three: { ...three, WebGLRenderer: Renderer },
    'three/examples/jsm/controls/OrbitControls.js': { OrbitControls: Controls },
    './warehouse3DAssets': { createBinSymbol() {}, disposeSceneObjects() {} }
  }
  if (existsSync(new URL('./sceneTap.ts', import.meta.url))) imports['./sceneTap'] = evaluateSource(readFileSync(new URL('./sceneTap.ts', import.meta.url), 'utf8'))
  const { createWarehouseScene } = evaluateSource(source, imports, {
    window: { devicePixelRatio: 1 },
    ResizeObserver: class { constructor() { throw new Error('injected observer construction failure') } }
  })
  assert.throws(() => createWarehouseScene(host, { select() {}, frame() {}, unavailable() {} }), /injected observer/)
  assert.equal(host.children.length, 0)
  assert.deepEqual(counts, { renderer: 1, controls: 1, listeners: 0 })
})
