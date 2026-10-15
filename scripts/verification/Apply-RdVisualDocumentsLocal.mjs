import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { spawn } from 'node:child_process'
import { createHash } from 'node:crypto'
import { collectUtf8Process } from './RdUtf8ProcessText.mjs'

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../..')
const settings=Object.fromEntries((await fs.readFile(path.join(root,'temp/local-runtime-patent/.env.local'),'utf8')).split(/\r?\n/).filter(l=>l&&!l.startsWith('#')&&l.includes('=')).map(l=>[l.slice(0,l.indexOf('=')),l.slice(l.indexOf('=')+1)]))
assert.equal(settings.RD_DB_PORT,'3310','Only the pre-existing isolated local instance')
const mysql=async(sql)=>{
  const child=spawn(process.env.RD_MYSQL_BIN||'mysql',['--host=127.0.0.1','--user=root','--port=3310','--batch','--raw','--skip-column-names','--default-character-set=utf8mb4','rd_erp'],{env:{...process.env,MYSQL_PWD:settings.RD_DB_PASSWORD},windowsHide:true})
  const captured=collectUtf8Process(child)
  child.stdin.end(sql)
  const {stdout,stderr,code}=await captured
  if(code!==0)throw new Error(`Local SQL failed (${code}): ${stderr.replaceAll(settings.RD_DB_PASSWORD,'[redacted]')}`)
  return stdout
}
const tables=['rd_package_unit','rd_stock_transaction','rd_location','rd_stock_lot','rd_loading_task','rd_putaway_task']
const fingerprint=async()=>{
  const result={}
  for(const table of tables){const raw=await mysql(`SELECT * FROM ${table} ORDER BY id;`); result[table]=createHash('sha256').update(raw).digest('hex')}
  return result
}
const before=await fingerprint()
const sql=await fs.readFile(path.join(root,'backend/sql/mysql/rd_visual_documents.sql'),'utf8')
assert.equal((sql.match(/CREATE TABLE IF NOT EXISTS/g)||[]).length,2)
assert.ok(!/(?:^|;)\s*(?:DROP|DELETE|UPDATE|ALTER|TRUNCATE|REPLACE|INSERT|CALL)\b/i.test(sql.replace(/^--.*$/gm,'')))
const checkOnly=process.argv.includes('--check')
if(!checkOnly){await mysql(sql);await mysql(sql)}
else assert.equal((await mysql("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='rd_erp' AND table_name IN ('rd_visual_document','rd_visual_document_event');")).trim(),'2')
const after=await fingerprint();assert.deepEqual(after,before)
await fs.mkdir(path.join(root,'temp/reuse-verification'),{recursive:true})
const result={result:'PASS',scope:checkOnly?'read-only schema presence and stable UTF8 fingerprints':'two display tables only; repeated application',businessFingerprints:after,checkedAt:new Date().toISOString()}
await fs.writeFile(path.join(root,'temp/reuse-verification/schema-result.json'),JSON.stringify(result,null,2))
console.log(JSON.stringify(result))
