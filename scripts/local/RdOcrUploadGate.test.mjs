import assert from 'node:assert/strict';
import test from 'node:test';
import { PassThrough } from 'node:stream';
import { readOcrUpload } from './RdOcrUploadGate.mjs';

const input = (headers = {}) => Object.assign(new PassThrough(), { headers });
test('bounded multipart accepts exact bytes, never interprets document text', async () => {
  const stream = input({ 'content-length': '4' });
  const result = readOcrUpload(stream, { limit: 4 });
  stream.end(Buffer.from([0, 1, 2, 255]));
  assert.deepEqual(await result, Buffer.from([0, 1, 2, 255]));
});
test('oversized declared and chunked uploads are rejected before proxying', async () => {
  await assert.rejects(readOcrUpload(input({ 'content-length': '5' }), { limit: 4 }), { status: 413 });
  const stream = input();
  const result = readOcrUpload(stream, { limit: 4 });
  stream.end(Buffer.alloc(5));
  await assert.rejects(result, { status: 413 });
});
test('unfinished uploads have an absolute deadline and release listeners', async () => {
  const stream = input();
  await assert.rejects(readOcrUpload(stream, { timeoutMs: 10 }), { status: 408 });
  assert.equal(stream.listenerCount('data'), 0);
  assert.equal(stream.listenerCount('end'), 0);
  stream.end();
});
