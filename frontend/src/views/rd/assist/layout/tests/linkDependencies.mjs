import { existsSync, mkdirSync, readdirSync, symlinkSync, realpathSync } from 'node:fs'
import { resolve, join } from 'node:path'

const destination = resolve('node_modules')
const local = resolve('node_modules/.reuse-toolchain/node_modules')
const baseline = resolve('../../warehouse-pda-next-phase/frontend/node_modules')
const link = (source, target) => {
  if (!existsSync(target)) symlinkSync(realpathSync(source), target, 'junction')
}
for (const root of [local, baseline]) {
  if (!existsSync(root)) continue
  for (const entry of readdirSync(root, { withFileTypes: true })) {
    if (entry.name.startsWith('.')) continue
    const source = join(root, entry.name)
    const target = join(destination, entry.name)
    if (entry.name.startsWith('@')) {
      mkdirSync(target, { recursive: true })
      for (const child of readdirSync(source)) link(join(source, child), join(target, child))
    } else link(source, target)
  }
}
console.log(
  'Read-only dependency links installed in isolated checkout node_modules; no manifests changed'
)
