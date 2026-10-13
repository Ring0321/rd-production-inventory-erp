import assert from 'node:assert/strict'
import { test } from 'node:test'
import { readFileSync } from 'node:fs'
import { evaluateSource } from '../../trace/package/testHelpers.mjs'

const { createSceneTap } = evaluateSource(readFileSync(new URL('./sceneTap.ts', import.meta.url), 'utf8'))
const point = (pointerId, clientX = 20, clientY = 30, button = 0) => ({ pointerId, clientX, clientY, button })
test('a deliberate single-pointer tap is allowed, right-click or cancelled pointer is not', () => {
  const tap = createSceneTap()
  tap.start(point(1))
  assert.equal(tap.end(point(1,21,31)),true)
  tap.start(point(2,20,30,2))
  assert.equal(tap.end(point(2,20,30,2)),false)
  tap.start(point(3))
  tap.cancel(point(3))
  assert.equal(tap.end(point(3)),false)
})
test('an orbit dragged far away and back to its origin is never a tap', () => {
  const tap = createSceneTap()
  tap.start(point(1))
  tap.move(point(1,80,30))
  tap.move(point(1))
  assert.equal(tap.end(point(1)),false)
})
test('a pinch never selects when the stationary second finger is released last', () => {
  const tap = createSceneTap()
  tap.start(point(1))
  tap.start(point(2,80,30))
  tap.move(point(1,5,30))
  assert.equal(tap.end(point(1,5,30)),false)
  assert.equal(tap.end(point(2,80,30)),false)
  tap.start(point(3))
  assert.equal(tap.end(point(3)),true)
})
