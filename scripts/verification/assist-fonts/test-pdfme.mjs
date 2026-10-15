import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import { constants } from 'node:fs';
import http from 'node:http';
import https from 'node:https';
import net from 'node:net';
import path from 'node:path';
import { createRequire, syncBuiltinESMExports } from 'node:module';
import { fileURLToPath, pathToFileURL } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '../../..');
const frontend = path.join(root, 'frontend');
const requireFrontend = createRequire(path.join(frontend, 'package.json'));
const provenance = JSON.parse(await fs.readFile(path.join(frontend, 'public/licenses/assist-fonts/provenance.json'), 'utf8'));
const fontPath = path.join(frontend, 'public/fonts/assist/NotoSansSC-Regular.ttf');
await fs.access(fontPath, constants.R_OK);
const data = await fs.readFile(fontPath);
const fontSha256 = crypto.createHash('sha256').update(data).digest('hex');
assert.equal(fontSha256, provenance.output.sha256);
assert.equal(data.subarray(0, 4).toString('hex'), '00010000');

const networkAttempts = [];
const blockNetwork = (...args) => {
  networkAttempts.push(String(args[0]));
  throw new Error('Network is disabled for the local-font PDF smoke test');
};
globalThis.fetch = blockNetwork;
http.request = http.get = https.request = https.get = blockNetwork;
net.Socket.prototype.connect = blockNetwork;
syncBuiltinESMExports();

const generatorEntry = requireFrontend.resolve('@pdfme/generator');
const requireGenerator = createRequire(generatorEntry);
const fontkit = requireGenerator('fontkit');
const { generate } = await import(pathToFileURL(generatorEntry).href);
const { text } = await import(pathToFileURL(requireFrontend.resolve('@pdfme/schemas')).href);
const font = fontkit.create(data);
assert.equal(font.familyName, 'Warehouse Assist SC');
assert.equal(font.postscriptName, 'WarehouseAssistSC-Regular');
assert.equal(font.numGlyphs, 31036);
assert.deepEqual(Object.keys(font.variationAxes), []);
const sample = provenance.output.sample;
const missing = [...new Set(sample)].filter((char) => !font.hasGlyphForCodePoint(char.codePointAt(0)));
assert.deepEqual(missing, []);
const emptyOutlines = [...new Set(sample)].filter((char) =>
  char.trim() && font.glyphForCodePoint(char.codePointAt(0)).path.commands.length === 0);
assert.deepEqual(emptyOutlines, []);
const run = font.layout(sample);
assert(run.glyphs.length > 0 && run.glyphs.every((glyph) => glyph.id !== 0));

const schema = (name, y, height, fontSize) => ({
  ...structuredClone(text.propPanel.defaultSchema),
  name,
  position: { x: 10, y },
  width: 130,
  height,
  fontSize,
  fontName: 'NotoSansSC',
  fontColor: '#000000',
  lineHeight: 1.3,
});
const template = {
  basePdf: { width: 150, height: 100, padding: [0, 0, 0, 0] },
  schemas: [[schema('title', 8, 12, 16), schema('body', 26, 48, 12), schema('footer', 86, 7, 9)]],
};
const title = '\u4ed3\u5e93\u8d27\u4f4d\u6807\u7b7e / LOCAL CJK';
const inputs = [
  {
    title,
    body: '\u8d27\u4f4d\uff1aA-01-02\n\u7269\u6599\uff1a\u82af\u73e9\u79d1\u6280\n\u6279\u6b21\uff1aB20261015\n\u6570\u91cf\uff1a123.50 kg',
    footer: 'pdfme 6.2.5 | local static TTF | smoke test 1/2',
  },
  { title, body: sample, footer: 'pdfme 6.2.5 | glyph coverage | smoke test 2/2' },
];
const result = await generate({
  template,
  inputs,
  plugins: { text },
  options: {
    font: { NotoSansSC: { data: new Uint8Array(data), fallback: true, subset: true } },
    lang: 'zh',
    creationDate: new Date('2026-10-15T00:00:00Z'),
    modificationDate: new Date('2026-10-15T00:00:00Z'),
  },
});
assert(Buffer.from(result).subarray(0, 5).equals(Buffer.from('%PDF-')));
assert.equal(networkAttempts.length, 0);
const validation = path.join(root, 'temp/reuse-font-verification');
await fs.mkdir(validation, { recursive: true });
await fs.writeFile(path.join(validation, 'pdfme-local-cjk-smoke.pdf'), result);
const report = {
  node: process.version,
  packages: Object.fromEntries(await Promise.all(['@pdfme/common', '@pdfme/generator', '@pdfme/schemas'].map(async (name) => {
    const pkg = JSON.parse(await fs.readFile(path.join(frontend, 'node_modules', name, 'package.json'), 'utf8'));
    return [name, pkg.version];
  }))),
  fontkit_entry: requireGenerator.resolve('fontkit'),
  font_file_readable: true,
  font_sha256: fontSha256,
  fontkit_family: font.familyName,
  fontkit_postscript: font.postscriptName,
  fontkit_glyphs: font.numGlyphs,
  fontkit_variable_axes: font.variationAxes,
  sample_missing_codepoints: missing,
  sample_empty_outlines: emptyOutlines,
  layout_glyphs: run.glyphs.length,
  network_disabled: true,
  network_attempts: networkAttempts,
  font_source: 'Local Uint8Array, no URL or built-in font fallback',
  subset_embedding: true,
  expected_pages: 2,
  expected_inputs: inputs,
  pdf_bytes: result.length,
  pdf_sha256: crypto.createHash('sha256').update(result).digest('hex'),
};
await fs.writeFile(path.join(validation, 'pdfme-test.json'), JSON.stringify(report, null, 2) + '\n');
console.log(JSON.stringify({ ...report, expected_inputs: '(saved in validation/pdfme-test.json)' }, null, 2));
