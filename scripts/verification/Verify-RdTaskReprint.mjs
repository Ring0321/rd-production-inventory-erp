import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { root, api, request, prepareFixture } from "./RdQrLocalFixture.mjs";

const { tokens } = await prepareFixture();
const output = path.join(root, "temp/qr-production-task-verification");
const fixture = JSON.parse(
  await fs.readFile(path.join(output, "fixture.json"), "utf8"),
);
assert.equal(fixture.marker, "二维码生产任务标签验证（非现场数据）");
const packages = await api(
  `/rd/package-unit/task-packages?productionTaskId=${fixture.taskId}`,
  tokens.production,
);
assert.equal(packages.length, 10);
const pkg = packages[0];
const before = await api(
  `/rd/package-unit/get?id=${pkg.id}`,
  tokens.production,
);
assert.notEqual(
  (
    await request("/rd/package-unit/reprint-batch", tokens.production, "POST", {
      productionTaskId: fixture.otherTaskId,
      packageIds: [pkg.id],
      reprintReason: "本地跨任务拒绝验证",
    })
  ).code,
  0,
);
assert.notEqual(
  (
    await request("/rd/package-unit/reprint-batch", tokens.production, "POST", {
      productionTaskId: fixture.taskId,
      packageIds: [pkg.id],
    })
  ).code,
  0,
);
await api("/rd/package-unit/reprint-batch", tokens.production, "POST", {
  productionTaskId: fixture.taskId,
  packageIds: [pkg.id],
  reprintReason: "本地任务范围补打软件验证，非实体打印",
});
assert.deepEqual(
  await api(`/rd/package-unit/get?id=${pkg.id}`, tokens.production),
  before,
);
const trace = await api(
  `/rd/package-unit/trace-detail/${pkg.packageCode}`,
  tokens.production,
);
assert.equal(
  trace.events.filter((event) => event.title === "标签补打已登记").length,
  1,
);
const result = {
  result: "PASS",
  taskId: fixture.taskId,
  packageCode: pkg.packageCode,
  wrongTaskRejected: true,
  reasonRequired: true,
  identityAndInventoryUnchanged: true,
  physicalPrinting: false,
};
await fs.writeFile(
  path.join(output, "reprint-result.json"),
  JSON.stringify(result, null, 2),
);
console.log(JSON.stringify(result));
