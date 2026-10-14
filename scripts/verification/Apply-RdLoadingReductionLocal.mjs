import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { execFile } from "node:child_process";
import { promisify } from "node:util";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const settings = Object.fromEntries(
  (
    await fs.readFile(
      path.join(root, "temp/local-runtime-patent/.env.local"),
      "utf8",
    )
  )
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => [
      line.slice(0, line.indexOf("=")),
      line.slice(line.indexOf("=") + 1),
    ]),
);
assert.equal(
  Number(settings.RD_DB_PORT),
  3310,
  "Only the isolated local patent database may be upgraded",
);
const source = await fs.readFile(
  path.join(root, "backend/sql/mysql/rd_erp_upgrade_slim.sql"),
  "utf8",
);
const tables = [
  ...source.matchAll(
    /CREATE TABLE IF NOT EXISTS `rd_loading_reduction` \([\s\S]*?\) ENGINE=InnoDB[^;]+;/g,
  ),
];
assert.equal(tables.length, 1);
const menu = source
  .split(/\r?\n/)
  .filter((line) => line.startsWith("(600286,'少装商务核定'"));
assert.equal(menu.length, 1);
assert.match(menu[0], /'rd:pickup-slip:approve-reduction'/);
const migration = `SET NAMES utf8mb4; ${tables[0][0]}
INSERT INTO system_menu (id,name,permission,type,sort,parent_id,path,icon,component,component_name,status,visible,keep_alive,always_show,creator,create_time,updater,update_time,deleted) VALUES
${menu[0].replace(/[,;]$/, "")}
ON DUPLICATE KEY UPDATE name=VALUES(name),permission=VALUES(permission),type=VALUES(type),parent_id=VALUES(parent_id),status=0,deleted=b'0';
INSERT INTO system_role_menu (role_id,menu_id,creator,create_time,updater,update_time,deleted,tenant_id)
SELECT 600003,600286,'rd-local-c5-r1',NOW(),'rd-local-c5-r1',NOW(),b'0',1
WHERE NOT EXISTS (SELECT 1 FROM system_role_menu WHERE role_id=600003 AND menu_id=600286 AND tenant_id=1 AND deleted=0);`;
const run = async (sql) =>
  promisify(execFile)(
    process.env.RD_MYSQL_BIN || "mysql",
    [
      "--host=127.0.0.1",
      "--port=3310",
      "--user=root",
      "--default-character-set=utf8mb4",
      "--batch",
      "--skip-column-names",
      "rd_erp",
      "-e",
      sql,
    ],
    { env: { ...process.env, MYSQL_PWD: settings.RD_DB_PASSWORD } },
  );
// Repair only duplicate grants created by the earlier local verifier, not pre-existing role data.
await run(
  "DELETE a FROM system_role_menu a JOIN system_role_menu b ON a.role_id=b.role_id AND a.menu_id=b.menu_id AND a.tenant_id=b.tenant_id AND a.id>b.id WHERE a.role_id=600003 AND a.menu_id=600286 AND a.tenant_id=1 AND a.creator='rd-local-c5-r1' AND b.creator='rd-local-c5-r1' AND a.deleted=0 AND b.deleted=0",
);
await run(migration);
await run(migration);
const audit = (
  await run(
    "SELECT COUNT(*) FROM system_role_menu WHERE menu_id=600286 AND role_id IN (600002,600004,600008) AND deleted=0; SELECT COUNT(*) FROM system_role_menu WHERE menu_id=600286 AND role_id=600003 AND deleted=0; SELECT NUMERIC_SCALE FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='rd_loading_reduction' AND column_name='reduced_weight';",
  )
).stdout
  .trim()
  .split(/\s+/);
assert.deepEqual(audit, ["0", "1", "6"]);
console.log(
  "PASS: C5-R1 additive local migration replayed; sales-only review permission; six-decimal registered weight. No other local configuration or goods changed.",
);
