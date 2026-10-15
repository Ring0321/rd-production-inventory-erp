import fs from 'node:fs/promises'
import path from 'node:path'
import {createRequire} from 'node:module'
import {createHash} from 'node:crypto'
import {fileURLToPath} from 'node:url'

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..'), frontend=path.join(root,'frontend')
const destination=path.join(frontend,'public/licenses/assist-workbench')
const roots=['vis-timeline','konva','vue-konva','@pdfme/common','@pdfme/ui','@pdfme/generator','@pdfme/schemas']
const hash=b=>createHash('sha256').update(b).digest('hex')
const exists=async file=>{try{await fs.access(file);return true}catch{return false}}
const directoryOf=async(name,from)=>{
  const req=createRequire(path.join(from,'package.json'))
  try{return path.dirname(req.resolve(`${name}/package.json`))}catch{}
  try{
    let directory=path.dirname(req.resolve(name))
    for(let i=0;i<10;i++){
      const file=path.join(directory,'package.json')
      if(await exists(file)){const p=JSON.parse(await fs.readFile(file,'utf8'));if(p.name===name)return directory}
      directory=path.dirname(directory)
    }
  }catch{}
  let directory=from
  for(let i=0;i<12;i++){
    const candidate=path.join(directory,'node_modules',name)
    if(await exists(path.join(candidate,'package.json')))return candidate
    directory=path.dirname(directory)
  }
  throw new Error(`Cannot locate ${name}`)
}
await fs.mkdir(destination,{recursive:true})
const visited=new Set(),manifest=[]
const visit=async(name,from)=>{
  if(!/^(?:@[a-z0-9._-]+\/)?[a-z0-9._-]+$/i.test(name))throw new Error('Invalid dependency identity')
  const directory=await directoryOf(name,from), p=JSON.parse(await fs.readFile(path.join(directory,'package.json'),'utf8')),key=`${p.name}@${p.version}`
  if(visited.has(key))return;visited.add(key)
  if(p.name!==name||!/^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$/.test(p.version))throw new Error('Invalid package metadata')
  const names=(await fs.readdir(directory)).filter(n=>/^(?:licen[sc]e|notice|copying)(?:[._-].*)?$/i.test(n))
  const notices=[]
  const prefix=key.replaceAll('/','--').replaceAll('@','')
  for(const n of names){const input=path.join(directory,n);if(!(await fs.stat(input)).isFile())continue;const bytes=await fs.readFile(input),out=`${prefix}-${n}`;await fs.writeFile(path.join(destination,out),bytes);notices.push({file:out,sha256:hash(bytes)})}
  const metadataOnly=!notices.length
  if(metadataOnly){
    if(!['MIT','ISC','Apache-2.0','BSD-2-Clause','BSD-3-Clause','0BSD'].includes(p.license))throw new Error(`Missing original notice for ${key} (${p.license})`)
    const out=`${prefix}-DECLARATION.json`,bytes=Buffer.from(JSON.stringify({name:p.name,version:p.version,license:p.license,repository:p.repository,author:p.author,notice:'Published metadata declares this license but ships no separate notice. Retained as provenance, not a legal compliance certification.'},null,2)+'\n')
    await fs.writeFile(path.join(destination,out),bytes);notices.push({file:out,sha256:hash(bytes)})
    if(await exists(path.join(directory,'README.md'))){const bytes=await fs.readFile(path.join(directory,'README.md')),out=`${prefix}-README.md`;await fs.writeFile(path.join(destination,out),bytes);notices.push({file:out,sha256:hash(bytes)})}
  }
  manifest.push({name:p.name,version:p.version,license:p.license,repository:p.repository,metadataOnly,notices})
  for(const dependency of Object.keys(p.dependencies||{}))await visit(dependency,directory)
}
for(const name of roots)await visit(name,frontend)
const iconSource=path.join(await directoryOf('@iconify/json',frontend),'json/lucide.json')
const icons=JSON.parse(await fs.readFile(iconSource,'utf8')),local=await fs.readFile(path.join(frontend,'src/plugins/svgIcon/assist-icons.json'))
const selected=JSON.parse(local)
for(const [name,value] of Object.entries(selected.icons))if(JSON.stringify(value)!==JSON.stringify(icons.icons[name]))throw new Error('Icon differs from licensed upstream source')
await fs.writeFile(path.join(destination,'MANIFEST.json'),JSON.stringify({roots,packages:manifest.sort((a,b)=>`${a.name}@${a.version}`.localeCompare(`${b.name}@${b.version}`)),icons:{source:'@iconify/json/json/lucide.json',sourceSha256:hash(await fs.readFile(iconSource)),selectionSha256:hash(local),licenseFile:'../lucide-LICENSE.txt',license:icons.info.license,keys:Object.keys(selected.icons)},notice:'Libraries are bundled locally. No cloud service is required by this integration. Upstream notices retained; this manifest is not a legal opinion.'},null,2)+'\n')
console.log(JSON.stringify({packages:manifest.length,metadataOnly:manifest.filter(p=>p.metadataOnly).map(p=>`${p.name}@${p.version}`),destination:'frontend/public/licenses/assist-workbench'}))
