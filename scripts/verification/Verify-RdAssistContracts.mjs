import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import {spawn} from 'node:child_process'
import {createHash} from 'node:crypto'
import {root,baseUrl,api,request,login} from './RdQrLocalFixture.mjs'
import {collectUtf8Process} from './RdUtf8ProcessText.mjs'
import {CUSTOMER,MARKER} from './Seed-RdFlowDemo.mjs'

assert.equal(baseUrl,'http://127.0.0.1:9000')
const settings=Object.fromEntries((await fs.readFile(path.join(root,'temp/local-runtime-patent/.env.local'),'utf8')).split(/\r?\n/).filter(l=>l&&!l.startsWith('#')&&l.includes('=')).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]))
assert.equal(settings.RD_DB_PORT,'3310')
const out=path.join(root,'temp/reuse-verification');await fs.mkdir(out,{recursive:true})
const mysql=async sql=>{
  const child=spawn(process.env.RD_MYSQL_BIN||'mysql',['--host=127.0.0.1','--user=root','--port=3310','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','rd_erp'],{env:{...process.env,MYSQL_PWD:settings.RD_DB_PASSWORD},windowsHide:true})
  const captured=collectUtf8Process(child);child.stdin.end(sql);const result=await captured;assert.equal(result.code,0,'local read-only SQL failed');return result.stdout
}
const fingerprints=async()=>{
  const value={};for(const table of ['rd_package_unit','rd_stock_transaction','rd_stock_lot','rd_location','rd_loading_task','rd_putaway_task'])value[table]=createHash('sha256').update(await mysql(`SELECT * FROM ${table} ORDER BY id;`)).digest('hex');return value
}
const before=await fingerprints()
const token=await login({username:'admin',password:settings.RD_ADMIN_INITIAL_PASSWORD})
const fixture=JSON.parse(await fs.readFile(path.join(root,'temp/qr-e4-fixture.json'),'utf8'))
const driver=await login(fixture.accounts.driver),warehouse=await login(fixture.accounts.warehouse),production=await login(fixture.accounts.production)
const tasks=await api(`/rd/production-task/page?pageNo=1&pageSize=100&customerName=${encodeURIComponent(CUSTOMER)}`,token)
const task=tasks.list.find(t=>t.customerName===CUSTOMER && t.remark?.includes(MARKER));assert.ok(task)
const view=await api(`/rd/warehouse-replay/task?productionTaskId=${task.id}`,token)
assert.equal(view.members.length,30);assert.equal(view.events.length,22);assert.equal(view.complete,true)
assert.ok(view.events.every(e=>/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}\+08:00$/.test(e.eventTime)))
assert.ok(view.events.every(e=>view.members.some(m=>m.id===e.packageId && m.packageCode===e.packageCode)))
for(const denied of [undefined,driver,production])assert.notEqual((await request(`/rd/warehouse-replay/task?productionTaskId=${task.id}`,denied)).code,0)
const map=await api('/rd/putaway-task/location-map',token)
const groups=Map.groupBy(map,l=>l.warehouseCode)
const candidate=[...groups].find(([,rows])=>rows.length && rows.every(l=>l.warehouseName?.includes('演示')||l.warehouseName?.includes('DEMO')) && rows.some(l=>l.enabled))
assert.ok(candidate,'Use only the original clearly synthetic demo warehouse')
const [code,locations]=candidate,enabled=locations.filter(l=>l.enabled)
const layout={schemaVersion:1,warehouseCode:code,width:1000,height:800,elements:[
  {id:'reuse-verification-zone',kind:'ZONE',x:20,y:10,width:900,height:40,label:'本地演示库区'},
  ...enabled.map((l,i)=>({id:`bin-${l.id}`,kind:'LOCATION',locationId:l.id,x:40+(i%4)*225,y:80+Math.floor(i/4)*145,width:180,height:100,label:l.locationCode}))
]}
assert.ok(layout.elements.every(e=>e.y+e.height<=800))
const query=`/rd/visual-document/get?kind=LAYOUT&scopeId=${encodeURIComponent(code)}`
let doc=await api(query,token)
assert.ok(!doc.draft || doc.draft.elements?.some(e=>e.id==='reuse-verification-zone'),'Refuse to replace an unrecognized user layout')
doc=await api('/rd/visual-document/save',token,'POST',{kind:'LAYOUT',scopeId:code,expectedVersion:doc.version,content:layout})
const race=await Promise.all([1,2].map(()=>request('/rd/visual-document/save',token,'POST',{kind:'LAYOUT',scopeId:code,expectedVersion:doc.version,content:layout})))
assert.equal(race.filter(r=>r.code===0).length,1);assert.equal(race.filter(r=>r.code===1080900001).length,1)
doc=await api(query,token)
doc=await api('/rd/visual-document/publish',token,'POST',{kind:'LAYOUT',scopeId:code,expectedVersion:doc.version})
assert.deepEqual(doc.published,layout);assert.equal(doc.publishedVersion,doc.version)
assert.deepEqual((await api(query,warehouse)).published,layout)
assert.notEqual((await request(query,driver)).code,0)
const invalid=structuredClone(layout);invalid.elements.push({...layout.elements[1],id:'duplicate-bin'})
assert.notEqual((await request('/rd/visual-document/save',token,'POST',{kind:'LAYOUT',scopeId:code,expectedVersion:doc.version,content:invalid})).code,0)
assert.equal((await api(query,token)).version,doc.version)
const foreign=map.find(l=>l.warehouseCode!==code && l.enabled);assert.ok(foreign)
const cross=structuredClone(layout);cross.elements[1].locationId=foreign.id
assert.notEqual((await request('/rd/visual-document/save',token,'POST',{kind:'LAYOUT',scopeId:code,expectedVersion:doc.version,content:cross})).code,0)
const labelQuery='/rd/visual-document/get?kind=LABEL&scopeId=GLOBAL'
const template=await api(labelQuery,token)
assert.notEqual((await request('/rd/visual-document/publish',production,'POST',{kind:'LABEL',scopeId:'GLOBAL',expectedVersion:template.version})).code,0)
assert.notEqual((await request('/rd/visual-document/publish',driver,'POST',{kind:'LABEL',scopeId:'GLOBAL',expectedVersion:template.version})).code,0)
const after=await fingerprints();assert.deepEqual(after,before)
const result={result:'PASS',taskId:task.id,packageCount:view.members.length,eventCount:view.events.length,layoutWarehouseCode:code,publishedVersion:doc.publishedVersion,
  checks:['actual persisted task ledger','strict offset timestamp','anonymous/driver/partial denial','real warehouse binding','save/reload/publish','concurrent version conflict','duplicate/cross-warehouse rejection','print authority not template admin','six business tables unchanged'],businessFingerprints:after}
await fs.writeFile(path.join(out,'contracts-result.json'),JSON.stringify(result,null,2));console.log(JSON.stringify(result))
