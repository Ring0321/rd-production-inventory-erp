import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { spawn } from 'node:child_process'
import { root, baseUrl, api, request, login } from './RdQrLocalFixture.mjs'
import { collectUtf8Process } from './RdUtf8ProcessText.mjs'

assert.equal(baseUrl, 'http://127.0.0.1:9000')
const java = 'C:/Users/Ring/.jdks/openjdk-26.0.1/bin/java.exe'
const jar = path.join(root,'backend/rd-erp-server/target/rd-erp-server.jar')
const output = path.join(root,'temp/reuse-verification'); await fs.mkdir(output,{recursive:true})
const base = {bags:[
  {packageId:1,packageCode:'SYNTH-W1',packageWeightKg:500.001,locationId:20,locationCode:'SYNTH-A',stockLotId:10},
  {packageId:2,packageCode:'SYNTH-W2',packageWeightKg:499.999,locationId:20,locationCode:'SYNTH-A',stockLotId:10}
],lotCaps:{10:2},maxCount:2,targetKg:1000,toleranceKg:0,seconds:2}
const worker = []
for(const [patch,expected] of [[{},'OPTIMAL'],[{lotCaps:{10:1}},'INFEASIBLE'],[{seconds:0},'UNKNOWN']]) {
  const env = Object.fromEntries(['SystemRoot','WINDIR','TEMP','TMP'].filter(k=>process.env[k]).map(k=>[k,process.env[k]]))
  env.PATH = `${path.dirname(java)};${env.SystemRoot || env.WINDIR}/System32`
  const child=spawn(java,['-Xmx192m','--enable-native-access=ALL-UNNAMED','-jar',jar,'--weight-solver-worker'],{env,windowsHide:true})
  const timer=setTimeout(()=>child.kill(),6500)
  const captured=collectUtf8Process(child); child.stdin.end(JSON.stringify({...base,...patch}))
  const response=await captured; clearTimeout(timer)
  assert.equal(response.code,0); const result=JSON.parse(response.stdout); assert.equal(result.status,expected)
  if(expected==='OPTIMAL') {assert.equal(result.totalWeightKg,1000);assert.deepEqual(result.bags.map(b=>b.packageId),[1,2])}
  worker.push({status:result.status,weightKg:result.totalWeightKg})
}
if(process.argv.includes('--worker-only')) {console.log(JSON.stringify({result:'PASS',actualFatJarWorker:worker}));process.exit(0)}
const settings=Object.fromEntries((await fs.readFile(path.join(root,'temp/local-runtime-patent/.env.local'),'utf8')).split(/\r?\n/).filter(l=>l&&!l.startsWith('#')&&l.includes('=')).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]))
const admin=await login({username:'admin',password:settings.RD_ADMIN_INITIAL_PASSWORD})
const fixture=JSON.parse(await fs.readFile(path.join(root,'temp/qr-e4-fixture.json'),'utf8'))
const pickupPage=await api('/rd/loading-task/pickup-slip/page?pageNo=1&pageSize=100&slipNo=DEMO-REUSE-WEIGHT-20261015',admin)
const slip=pickupPage.list.find(s=>s.slipNo==='DEMO-REUSE-WEIGHT-20261015' && s.remark?.includes('DUOXU-DEMO-FLOW-V1'));assert.ok(slip)
const before=await api('/rd/package-unit/task-packages?productionTaskId=90',admin)
const result=await api('/rd/weight-assistant/suggest',admin,'POST',{pickupSlipId:slip.id,targetWeightKg:2000,toleranceKg:0})
assert.equal(result.solverStatus,'OPTIMAL');assert.equal(result.totalWeightKg,2000);assert.equal(result.bags.length,2)
assert.equal(result.candidateCount,6);assert.ok(result.bags.every(b=>before.some(p=>p.id===b.packageId && p.packageWeight===b.packageWeightKg && p.locationId===b.locationId)))
const infeasible=await api('/rd/weight-assistant/suggest',admin,'POST',{pickupSlipId:slip.id,targetWeightKg:2500,toleranceKg:0})
assert.equal(infeasible.solverStatus,'INFEASIBLE');assert.equal(infeasible.totalWeightKg,null);assert.deepEqual(infeasible.bags,[])
assert.notEqual((await request('/rd/weight-assistant/suggest',admin,'POST',{pickupSlipId:slip.id,targetWeightKg:6001,toleranceKg:0})).code,0)
for(const account of [fixture.accounts.driver,fixture.accounts.production,fixture.accounts.sales]) {
  const token=await login(account)
  assert.notEqual((await request('/rd/weight-assistant/suggest',token,'POST',{pickupSlipId:slip.id,targetWeightKg:2000,toleranceKg:0})).code,0)
}
assert.deepEqual(await api('/rd/package-unit/task-packages?productionTaskId=90',admin),before)
const report={result:'PASS',actualFatJarWorker:worker,pickupSlipId:slip.id,actualHttpStatus:result.solverStatus,actualHttpSelectedPackageIds:result.bags.map(b=>b.packageId),candidateCount:result.candidateCount,weightKg:result.totalWeightKg,infeasibleStatus:infeasible.solverStatus,checks:['exact grams and tail bags','capacity constrained infeasible','zero budget unknown','same packaged jar without Spring startup','original pickup and original quality/stock facts','actual HTTP native child route','authorization refusal','no package changes']}
await fs.writeFile(path.join(output,'weight-runtime.json'),JSON.stringify(report,null,2));console.log(JSON.stringify(report))
