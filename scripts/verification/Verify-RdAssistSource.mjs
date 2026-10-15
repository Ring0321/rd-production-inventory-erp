import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..')
const base = 'c0ead420488e94c557e104712ad406d0bc394a01'
const head = process.argv.includes('--head')
const git = args => execFileSync('git', args, {cwd:root,maxBuffer:32*1024*1024})
const prefix = head ? 'HEAD:' : ':'
const names = git(head?['diff',base,'HEAD','--name-only','-z']:['diff','--cached','--name-only','-z']).toString('utf8').split('\0').filter(Boolean)
assert.ok(names.length)
const protectedSecrets = Object.fromEntries(fs.readFileSync(path.join(root,'temp/local-runtime-patent/.env.local'),'utf8').split(/\r?\n/).filter(l=>l.includes('=')).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]))
const secret = /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}|AKIA[A-Z0-9]{16}|sk-[A-Za-z0-9_-]{30,}/
const sql = new Set(['backend/sql/mysql/rd_erp_clean.sql','backend/sql/mysql/rd_erp_upgrade_slim.sql','backend/sql/mysql/rd_visual_documents.sql'])
for(const name of names) {
  assert.ok(!/(^|\/)(temp|outputs|node_modules|dist-prod|target|artifacts|\.runtime|\.venv|__pycache__)(\/|$)|(^|\/)\.env(\.|$)/.test(name),`Private/generated path: ${name}`)
  assert.ok(!/\.(dump|jar|zip|png|pdf|y4m|pem|key|p12|pyc)$/i.test(name),`Unrequested binary: ${name}`)
  if(name.endsWith('.sql')) assert.ok(sql.has(name),`Unrequested SQL: ${name}`)
  const bytes=git(['show',prefix+name])
  if(name.endsWith('.ttf')) {
    assert.equal(name,'frontend/public/fonts/assist/NotoSansSC-Regular.ttf')
    assert.equal(hash(bytes),'4e6874dc1e8f4873a18a8f54238a49fa2c7f3632a0fc303c729492a84f3ba609')
  } else {
    const text=bytes.toString('utf8')
    assert.ok(!secret.test(text),`Possible credential: ${name}`)
    for(const key of ['RD_ADMIN_INITIAL_PASSWORD','RD_DB_PASSWORD','RD_AI_GLM_API_KEY']) {
      const value=protectedSecrets[key]
      if(value?.length>=8) assert.ok(!text.includes(value),`Protected local credential literal: ${name}`)
    }
  }
}
function hash(bytes) {return createHash('sha256').update(bytes).digest('hex')}
let newNotices=0
const noticeBase='frontend/public/licenses/assist-workbench/'
const manifest=JSON.parse(git(['show',prefix+noticeBase+'MANIFEST.json']).toString('utf8'))
for(const pkg of manifest.packages) for(const notice of pkg.notices) {
  assert.equal(path.basename(notice.file),notice.file)
  assert.equal(hash(git(['show',prefix+noticeBase+notice.file])),notice.sha256,`Notice bytes changed in Git: ${notice.file}`)
  newNotices++
}
const oldBase='frontend/public/licenses/qr-workbench/'
const oldManifest=JSON.parse(git(['show',prefix+oldBase+'MANIFEST.json']).toString('utf8'))
let originalNotices=0
for(const pkg of oldManifest.packages) for(const notice of pkg.licenses) {
  assert.equal(hash(git(['show',prefix+oldBase+notice.file])),notice.sha256)
  assert.deepEqual(git(['show',prefix+oldBase+notice.file]),git(['show',base+':'+oldBase+notice.file]))
  originalNotices++
}
const oldIcons=JSON.parse(git(['show',prefix+oldBase+'Lucide-source-declaration.json']).toString('utf8'))
assert.equal(hash(git(['show',prefix+'frontend/src/plugins/svgIcon/qr-workbench-icons.json'])),oldIcons.selectedIconsSha256)
assert.equal(hash(fs.readFileSync(path.join(root,'frontend/dist-prod/assets/zxing_reader-DUHi_9Vq.wasm'))),oldManifest.reader.sha256)
const result={mode:head?'HEAD':'INDEX',result:'PASS',changedFiles:names.length,newPackages:manifest.packages.length,newNoticeFiles:newNotices,originalNoticesUnchanged:originalNotices,originalIconsAndWasmUnchanged:true,fontHashVerified:true,protectedCredentialMatches:0,commercialLicenseClearance:false}
if(process.argv.includes('--record')) {
  const folder=path.join(root,'temp/reuse-verification');fs.mkdirSync(folder,{recursive:true})
  fs.writeFileSync(path.join(folder,head?'source-head-result.json':'source-index-result.json'),JSON.stringify(result,null,2))
}
console.log(JSON.stringify(result))
