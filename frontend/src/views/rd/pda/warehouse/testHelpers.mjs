import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'

export const plain = (value) => JSON.parse(JSON.stringify(value))
export const evaluate = (file, imports = {}, globals = {}) => {
  const source = readFileSync(new URL(file, import.meta.url), 'utf8')
  const module = { exports: {} }
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  })
  runInNewContext(outputText, {
    module,
    exports: module.exports,
    require: (name) => {
      assert.ok(name in imports, `Unexpected dependency ${name}`)
      return imports[name]
    },
    ...globals
  })
  return module.exports
}
