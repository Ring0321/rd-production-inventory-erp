import assert from 'node:assert/strict'
import {test} from 'node:test'
import {EventEmitter} from 'node:events'
import {PassThrough} from 'node:stream'
import {collectUtf8Process} from './RdUtf8ProcessText.mjs'

test('fingerprint capture preserves multibyte Chinese even when every byte is a separate chunk',async()=>{
  const child=new EventEmitter(); child.stdout=new PassThrough();child.stderr=new PassThrough()
  const captured=collectUtf8Process(child),source='垛序 ERP\t库存吨袋\nA-01\t800kg\n'
  for(const b of Buffer.from(source))child.stdout.write(Buffer.of(b))
  const warning='中文诊断'
  for(const b of Buffer.from(warning))child.stderr.write(Buffer.of(b))
  child.stdout.end();child.stderr.end();child.emit('close',0)
  const result=await captured
  assert.equal(result.stdout,source);assert.equal(result.stderr,warning);assert.equal(result.code,0)
  assert.ok(!result.stdout.includes('\ufffd'))
})
