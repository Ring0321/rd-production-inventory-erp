import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { randomBytes } from "node:crypto";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { root, api, request, prepareFixture } from "./RdQrLocalFixture.mjs";

const { tokens } = await prepareFixture();
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
assert.equal(Number(settings.RD_DB_PORT), 3310);
const sql = async (text) =>
  (
    await promisify(execFile)(
      "mysql",
      [
        "--host=127.0.0.1",
        "--port=3310",
        "--user=root",
        "--default-character-set=utf8mb4",
        "--batch",
        "--skip-column-names",
        "rd_erp",
        "-e",
        text,
      ],
      { env: { ...process.env, MYSQL_PWD: settings.RD_DB_PASSWORD } },
    )
  ).stdout.trim();
const fixture = JSON.parse(
  await fs.readFile(
    path.join(root, "temp/qr-production-task-verification/fixture.json"),
    "utf8",
  ),
);
assert.equal(fixture.marker, "二维码生产任务标签验证（非现场数据）");
const task = await api(
  `/rd/production-task/get?id=${fixture.taskId}`,
  tokens.production,
);
const scope = new URLSearchParams({
  customerName: task.customerName,
  productModel: "QR-TASK-A",
  keyword: `TASK-${fixture.taskId}`,
  limit: "1",
});
assert.deepEqual(
  await api(
    `/rd/production-output/furnace-options?${scope}`,
    tokens.production,
  ),
  [`TASK-${fixture.taskId}`],
);
assert.deepEqual(
  await api(
    `/rd/production-output/furnace-options?${new URLSearchParams({ customerName: task.customerName, productModel: "NOT-A-MODEL" })}`,
    tokens.production,
  ),
  [],
);
const historical = await api(
  `/rd/production-output/layer-options?${new URLSearchParams({ customerName: task.customerName, productModel: "QR-TASK-A" })}`,
  tokens.production,
);
assert.ok(
  historical.some(
    (option) => option.value === "上层" && option.source === "HISTORY",
  ),
);
for (const role of ["quality", "driver", "otherDriver", "sales"]) {
  const denied = await request(
    `/rd/production-output/furnace-options?${scope}`,
    tokens[role],
  );
  assert.equal(denied.code, 403, role);
}
assert.equal(
  (await request(`/rd/production-output/layer-options?${scope}`)).code,
  401,
);
const marker = `PDA_CHOICES_${randomBytes(5).toString("hex")}`;
assert.match(marker, /^PDA_CHOICES_[a-f0-9]+$/);
const before = await sql(
  `SELECT COUNT(*) FROM rd_stock_transaction; SELECT id,version,status,location_id FROM rd_package_unit WHERE id IN (${fixture.packageIds.join(",")}) ORDER BY id`,
);
try {
  await sql(`INSERT INTO rd_furnace_layer (tenant_id,layer_code,layer_name,status,sort,remark) VALUES
    (1,'${marker}A','${marker}启用',0,0,'${marker}'),
    (1,'${marker}B','${marker}停用',1,1,'${marker}'),
    (2,'${marker}C','${marker}其他租户',0,2,'${marker}');`);
  await sql(`INSERT INTO rd_production_output
    (tenant_id,production_date,customer_name,product_model,furnace_no,furnace_layer,remark) VALUES
    (1,CURRENT_DATE(),'${marker}','PDA-COLLATION-VERIFY','${marker}F','${marker}L','${marker}'),
    (1,CURRENT_DATE(),'${marker}','PDA-COLLATION-VERIFY','${marker}f','${marker}l','${marker}'),
    (1,CURRENT_DATE(),'${marker}','PDA-COLLATION-VERIFY','${marker}F ','${marker}L ','${marker}'),
    (1,CURRENT_DATE(),'${marker}','PDA-COLLATION-VERIFY','${marker}-disabled-a','${marker}B','${marker}'),
    (1,CURRENT_DATE(),'${marker}','PDA-COLLATION-VERIFY','${marker}-disabled-b','${marker}b','${marker}');`);
  assert.equal(
    await sql(`SELECT COUNT(DISTINCT furnace_no),COUNT(DISTINCT CAST(furnace_no AS BINARY))
      FROM rd_production_output WHERE tenant_id=1 AND remark='${marker}' AND furnace_no LIKE '${marker}F%';`),
    "1\t3",
  );
  const rawScope = {
    customerName: marker,
    productModel: "PDA-COLLATION-VERIFY",
    limit: "100",
  };
  const rawFurnaces = await api(
    `/rd/production-output/furnace-options?${new URLSearchParams({ ...rawScope, keyword: `${marker}F` })}`,
    tokens.production,
  );
  assert.deepEqual(
    new Set(rawFurnaces),
    new Set([`${marker}F`, `${marker}f`, `${marker}F `]),
  );
  const rawLayers = await api(
    `/rd/production-output/layer-options?${new URLSearchParams({ ...rawScope, keyword: `${marker}L` })}`,
    tokens.production,
  );
  assert.deepEqual(
    new Set(rawLayers.map((option) => option.value)),
    new Set([`${marker}L`, `${marker}l`, `${marker}L `]),
  );
  const disabledHistory = await api(
    `/rd/production-output/layer-options?${new URLSearchParams({ ...rawScope, keyword: `${marker}B` })}`,
    tokens.production,
  );
  assert.deepEqual(
    disabledHistory.map((option) => option.value),
    [`${marker}b`],
  );
  const options = await api(
    `/rd/production-output/layer-options?${new URLSearchParams({ keyword: marker, limit: "10000" })}`,
    tokens.production,
  );
  const dictionaryOptions = options.filter(
    (option) => option.source === "DICTIONARY",
  );
  assert.equal(dictionaryOptions.length, 1);
  assert.equal(dictionaryOptions[0].value, `${marker}启用`);
  const fingerprint = await sql(
    `SELECT id,tenant_id,layer_code,layer_name,status,create_time,update_time FROM rd_furnace_layer WHERE remark='${marker}' ORDER BY id`,
  );
  await api(
    `/rd/production-output/layer-options?${new URLSearchParams({ keyword: marker })}`,
    tokens.production,
  );
  assert.equal(
    await sql(
      `SELECT id,tenant_id,layer_code,layer_name,status,create_time,update_time FROM rd_furnace_layer WHERE remark='${marker}' ORDER BY id`,
    ),
    fingerprint,
  );
} finally {
  await sql(
    `DELETE FROM rd_production_output WHERE tenant_id=1 AND remark='${marker}' AND customer_name='${marker}' AND product_model='PDA-COLLATION-VERIFY' AND production_task_id IS NULL`,
  );
  await sql(
    `DELETE FROM rd_furnace_layer WHERE remark='${marker}' AND layer_code IN ('${marker}A','${marker}B','${marker}C')`,
  );
}
assert.equal(
  await sql(
    `SELECT COUNT(*) FROM rd_stock_transaction; SELECT id,version,status,location_id FROM rd_package_unit WHERE id IN (${fixture.packageIds.join(",")}) ORDER BY id`,
  ),
  before,
);
const result = {
  result: "PASS",
  authenticatedTenant: true,
  customerModelScoped: true,
  enabledDictionaryOnly: true,
  originalHistoricalValue: true,
  byteExactCaseAndSpaces: true,
  disabledHistoryExactMatch: true,
  queryUnchanged: true,
  temporaryDictionaryCleaned: true,
  physicalAcceptance: false,
};
const out = path.join(root, "temp/qr-pda-workspace-verification");
await fs.mkdir(out, { recursive: true });
await fs.writeFile(
  path.join(out, "choices-result.json"),
  JSON.stringify(result, null, 2),
);
console.log(JSON.stringify(result));
