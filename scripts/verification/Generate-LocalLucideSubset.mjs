import fs from 'node:fs'
import path from 'node:path'
import { createRequire } from 'node:module'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const require = createRequire(path.join(root, 'frontend/package.json'))
const original = require('@iconify/json/json/lucide.json')
const names = new Set()
const scan = directory => {
  for (const item of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, item.name)
    if (item.isDirectory()) scan(file)
    else if (item.isFile() && /\.(vue|ts|tsx)$/.test(item.name)) {
      for (const match of fs.readFileSync(file, 'utf8').matchAll(/lucide:([a-z0-9-]+)/g))
        names.add(match[1])
    }
  }
}
scan(path.join(root, 'frontend/src'))
const icons = {}, aliases = {}
const add = name => {
  if (original.icons[name]) icons[name] = original.icons[name]
  else if (original.aliases?.[name]) {
    aliases[name] = original.aliases[name]
    add(original.aliases[name].parent)
  } else throw Error(`Locked Lucide source does not contain ${name}`)
}
for (const name of [...names].sort()) add(name)
const subset = { prefix: original.prefix, info: original.info,
  width: original.width, height: original.height, icons,
  ...(Object.keys(aliases).length ? { aliases } : {}) }
fs.writeFileSync(path.join(root, 'frontend/src/plugins/svgIcon/xiaoduo-icons.json'), JSON.stringify(subset, null, 2) + '\n')
console.log(JSON.stringify({ source: '@iconify/json/json/lucide.json', literalIcons: names.size,
  icons: Object.keys(icons).length, aliases: Object.keys(aliases).length, originalLicensePreserved: true }))
