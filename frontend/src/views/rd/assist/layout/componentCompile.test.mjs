import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { parse, compileScript, compileTemplate } from 'vue/compiler-sfc'

for (const file of [
  './WarehouseLayoutEditor.vue',
  './KonvaLayoutSurface.vue',
  '../templates/LabelTemplateStudio.vue',
  '../templates/PdfmeSurface.vue'
]) {
  test(`SFC compiles after repository formatting: ${file}`, () => {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8')
    const result = parse(source, { filename: file })
    assert.deepEqual(result.errors, [])
    const script = compileScript(result.descriptor, {
      id: 'fixture',
      fs: { fileExists: () => true, readFile: (path) => readFileSync(path, 'utf8') }
    })
    const template = compileTemplate({
      source: result.descriptor.template.content,
      filename: file,
      id: 'fixture',
      compilerOptions: { bindingMetadata: script.bindings, expressionPlugins: ['typescript'] }
    })
    assert.deepEqual(template.errors, [])
  })
}
