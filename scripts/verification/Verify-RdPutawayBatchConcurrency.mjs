import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { root, api, request, prepareFixture } from "./RdQrLocalFixture.mjs";

const { tokens, accounts } = await prepareFixture();
const marker = "二维码批次入库并发验证（非现场数据）";
const output = path.join(root, "temp/qr-putaway-concurrency");
await fs.mkdir(output, { recursive: true });
const id = await api(
  "/rd/production-output/create",
  tokens.production,
  "POST",
  {
    productionDate: "2026-10-11",
    customerName: marker,
    productModel: "QR-PUT-RACE",
    furnaceNo: `RACE-${Date.now()}`,
    furnaceLayer: "上层",
    outputQty: 2,
    packageWeight: 1000,
    sourceSheet: marker,
    remark: marker,
  },
);
const production = await api(
  `/rd/production-output/get?id=${id}`,
  tokens.production,
);
await api("/rd/production-output/confirm", tokens.production, "POST", {
  id,
  version: production.version,
});
const packages = await api(
  "/rd/package-unit/generate",
  tokens.production,
  "POST",
  { productionOutputId: id, packageCount: 2 },
);
await api("/rd/package-unit/print-batch", tokens.production, "POST", {
  packageIds: packages.map((pkg) => pkg.id),
});
for (const pkg of packages) {
  const inspectionId = await api(
    "/rd/quality-inspection/create",
    tokens.quality,
    "POST",
    {
      packageId: pkg.id,
      remark: marker,
      items: [
        {
          itemCode: "LOCAL_TEST",
          itemName: "并发接口验证（非检测值）",
          standardMin: 0,
          standardMax: 10,
          measuredValue: 5,
        },
      ],
    },
  );
  await api(
    `/rd/quality-inspection/submit?id=${inspectionId}`,
    tokens.quality,
    "POST",
  );
  await api(
    `/rd/quality-inspection/approve?id=${inspectionId}`,
    tokens.admin,
    "POST",
  );
}
const locations = await api("/rd/putaway-task/location-map", tokens.warehouse);
const location = locations.find(
  (item) =>
    item.enabled &&
    !item.frozen &&
    (item.availablePackages == null || item.availablePackages >= 2),
);
assert.ok(location);
const taskId = await api("/rd/putaway-task/create", tokens.warehouse, "POST", {
  plannedLocationId: location.id,
  forkliftDriverId: accounts.driver.userId,
  packageIds: packages.map((pkg) => pkg.id),
  remark: marker,
});
const detail = await api(`/rd/putaway-task/get?id=${taskId}`, tokens.warehouse);
const payload = {
  id: taskId,
  version: detail.task.version,
  packageCode: packages[0].packageCode,
  taskLineIds: detail.lines.map((line) => line.id),
  deviceCode: "LOCAL-CONCURRENT-REQUEST",
};
const results = await Promise.all([
  request("/rd/putaway-task/batch-scan/confirm", tokens.warehouse, "POST", {
    ...payload,
    idempotencyKey: crypto.randomUUID(),
  }),
  request("/rd/putaway-task/batch-scan/confirm", tokens.warehouse, "POST", {
    ...payload,
    idempotencyKey: crypto.randomUUID(),
  }),
]);
assert.equal(results.filter((result) => result.code === 0).length, 1);
assert.equal(results.filter((result) => result.code !== 0).length, 1);
const after = await api(`/rd/putaway-task/get?id=${taskId}`, tokens.warehouse);
assert.equal(after.task.scannedPackageCount, 2);
assert.equal(after.task.status, "PENDING_CONFIRMATION");
assert.ok(after.lines.every((line) => line.scanCount === 1));
const beforePost = await api(
  `/rd/package-unit/trace?productionOutputId=${id}`,
  tokens.warehouse,
);
assert.ok(beforePost.every((pkg) => pkg.status === "INBOUND_PENDING"));
const original = results.find((result) => result.code === 0);
const successfulIndex = results.findIndex((result) => result.code === 0);
await api("/rd/putaway-task/confirm", tokens.warehouse, "PUT", {
  id: taskId,
  version: after.task.version,
  finalLocationId: location.id,
});
const posted = await api(
  `/rd/package-unit/trace?productionOutputId=${id}`,
  tokens.warehouse,
);
assert.ok(posted.every((pkg) => pkg.status === "IN_STOCK"));
await fs.writeFile(
  path.join(output, "result.json"),
  JSON.stringify(
    {
      result: "PASS",
      outputId: id,
      taskId,
      successCount: 1,
      rejectedCount: 1,
      acceptedRequestIndex: successfulIndex,
      recordId: original.data.recordId,
      errorCode: results.find((result) => result.code !== 0).code,
      perPackageScanCounts: after.lines.map((line) => line.scanCount),
      packageCodes: packages.map((pkg) => pkg.packageCode),
    },
    null,
    2,
  ),
);
console.log(
  JSON.stringify({
    result: "PASS",
    taskId,
    successCount: 1,
    rejectedCount: 1,
    output,
  }),
);
