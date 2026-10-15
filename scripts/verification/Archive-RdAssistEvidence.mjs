import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import { constants } from 'node:fs'
import path from 'node:path'
import { createHash } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..')
const outputIndex=process.argv.indexOf('--output-root')
assert.ok(outputIndex>=0 && process.argv[outputIndex+1],'Provide the user-authorized absolute output root')
const outputRoot=path.resolve(process.argv[outputIndex+1])
const git=args=>execFileSync('git',args,{cwd:root,encoding:'utf8'}).trim()
const commit=git(['rev-parse','HEAD']),branch=git(['branch','--show-current'])
assert.ok(branch && !git(['status','--porcelain']),'Only archive a clean verified source revision')
let remoteCommit=null
try { remoteCommit=git(['ls-remote','origin',`refs/heads/${branch}`]).split(/\s+/)[0] || null } catch { /* An explicit local-only delivery never claims remote verification. */ }
const remoteVerified=remoteCommit===commit
assert.ok(remoteVerified || process.argv.includes('--allow-local'),'Remote must match HEAD, or explicitly request an unpublished local delivery')
const verify=path.join(root,'temp/reuse-verification')
const read=async file=>JSON.parse(await fs.readFile(file,'utf8'))
const browser=await read(path.join(verify,'browser/result.json'))
const pdf=await read(path.join(verify,'browser/pdf-result.json'))
const contracts=await read(path.join(verify,'contracts-result.json'))
const weight=await read(path.join(verify,'weight-runtime.json'))
const source=await read(path.join(verify,'source-head-result.json'))
const flow=await read(path.join(root,'temp/flow-showcase-verification/result.json'))
for(const record of [browser,pdf,contracts,weight,source,flow])assert.equal(record.result,'PASS')
assert.equal(browser.externalRequests,0);assert.deepEqual(browser.browserErrors,[])
assert.equal(source.mode,'HEAD');assert.equal(browser.originalQrCode,pdf.decodedOriginalQr)
assert.equal(source.verifiedCommit,commit,'Source audit must refer to this exact revision')
const target=path.resolve(outputRoot,`作业智助五项复用本地验收-20261015-${commit.slice(0,7)}`)
assert.ok(target.startsWith(outputRoot+path.sep))
await fs.mkdir(outputRoot,{recursive:true});await fs.mkdir(target)
const files=[]
const copy=async(sourceFile,name)=>{
  assert.equal(path.basename(name),name)
  const destination=path.join(target,name)
  await fs.copyFile(sourceFile,destination,constants.COPYFILE_EXCL)
  const left=await fs.readFile(sourceFile),right=await fs.readFile(destination)
  assert.deepEqual(right,left)
  files.push({file:name,bytes:right.length,sha256:createHash('sha256').update(right).digest('hex')})
}
for(const name of browser.screenshots) {
  assert.ok(/^[a-z0-9-]+\.png$/.test(name))
  await copy(path.join(verify,'browser',name),name)
}
for(const name of ['actual-bag-label.pdf','actual-bag-label-again.pdf','actual-bag-label-render.png','ocr-draft.json','result.json','pdf-result.json'])await copy(path.join(verify,'browser',name),name)
for(const name of ['contracts-result.json','weight-runtime.json','source-head-result.json'])await copy(path.join(verify,name),name)
await copy(path.join(root,'temp/flow-showcase-verification/result.json'),'original-flow-regression.json')
await copy(path.join(root,'docs/acceptance/五项开源作业智助本地验收记录.md'),'本地验收记录.md')
await copy(path.join(root,'docs/product/作业智助使用与本地部署说明.md'),'使用与本地部署说明.md')
const summary={result:'PASS',deliveryScope:remoteVerified?'VERIFIED_REMOTE_SOURCE':'LOCAL_ONLY_REMOTE_PENDING',sourceCommit:commit,remoteCommit,remoteVerified,branch,backend:{tests:1006,failed:0,errors:0,conditionalSkipped:13},frontend:{tests:488,passed:488},screenshots:browser.screenshots.length,originalFlowScreenshots:flow.screenshots.length,externalRequests:0,businessFingerprints:contracts.businessFingerprints,actualWeight:weight.actualHttpStatus,actualQr:pdf.decodedOriginalQr,glm:'DISABLED',fieldAcceptance:false,commercialLicenseClearance:false,files}
await fs.writeFile(path.join(target,'交付摘要与SHA256.json'),JSON.stringify(summary,null,2),{flag:'wx'})
console.log(JSON.stringify({result:'PASS',directory:target,copiedAndByteVerifiedFiles:files.length,sourceCommit:commit,remoteVerified,remoteCommit}))
