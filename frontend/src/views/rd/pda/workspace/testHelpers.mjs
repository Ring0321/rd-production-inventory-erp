import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { runInNewContext } from 'node:vm'
import ts from 'typescript'
import { compileScript, parse } from 'vue/compiler-sfc'

export const evaluate = (file, imports = {}, exportName) => {
  let source = readFileSync(new URL(file, import.meta.url), 'utf8')
  if (file.endsWith('.vue')) {
    const { descriptor, errors } = parse(source)
    assert.equal(errors.length, 0)
    source = compileScript(descriptor, { id: 'workspace-test' }).content
  } else if (exportName) {
    const parsed = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true)
    const statement = parsed.statements.find(
      (item) =>
        ts.isVariableStatement(item) &&
        item.declarationList.declarations.some(
          (declaration) => declaration.name.getText(parsed) === exportName
        )
    )
    assert.ok(statement)
    source = ts.createPrinter().printNode(ts.EmitHint.Unspecified, statement, parsed)
  }
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 }
  })
  const exports = {}
  runInNewContext(outputText, {
    exports,
    require: (name) => {
      assert.ok(name in imports, `Unexpected import: ${name}`)
      return imports[name]
    },
    URL
  })
  return exports
}

export const digitalLink = evaluate('../../../../utils/rdDigitalLink.ts', {}, 'extractPackageCode')
export const profileModule = evaluate('./profile.ts', { '@/utils/rdDigitalLink': digitalLink })
