import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { randomBytes } from "node:crypto";
import {
  prepareQuality,
  batchRequest,
  output,
  api,
  request,
} from "./RdQualityLocalFixture.mjs";

const { tokens, marker, taskId, createOutput, sql } = await prepareQuality();
const group = await createOutput(4, "scope");
const other = await createOutput(1, "other", "QR-QC-B", "下层");
const ids = group.bags.map((bag) => bag.id);
const units = async (record = group) =>
  api(`/rd/package-unit/trace?productionOutputId=${record.id}`, tokens.quality);
const states = (bags) =>
  [...bags].sort((a, b) => a.id - b.id).map((bag) => bag.status);
const scope = (code) =>
  api(
    `/rd/quality-inspection/scope?packageCode=${encodeURIComponent(code)}&pageNo=1&pageSize=100`,
    tokens.quality,
  );
const create = (body, token = tokens.quality) =>
  api("/rd/quality-inspection/create-batch", token, "POST", body);
const rejectCreate = async (body) =>
  assert.notEqual(
    (
      await request(
        "/rd/quality-inspection/create-batch",
        tokens.quality,
        "POST",
        body,
      )
    ).code,
    0,
  );
const fingerprint = async (bagIds) => {
  assert.ok(bagIds.every((id) => Number.isSafeInteger(id) && id > 0));
  return sql(
    `SELECT id,version,status,location_id,stock_lot_id FROM rd_package_unit WHERE id IN (${bagIds.join(",")}) ORDER BY id; SELECT COUNT(*) FROM rd_stock_transaction WHERE package_id IN (${bagIds.join(",")});`,
  );
};
const initial = await fingerprint(ids);
const located = await scope(group.bags[0].packageCode);
assert.equal(located.packages.total, 4);
assert.equal(located.representative.eligible, true);
assert.equal(located.packages.list.filter((bag) => bag.eligible).length, 4);
assert.equal(await fingerprint(ids), initial);
assert.equal(
  (
    await request(
      `/rd/quality-inspection/scope?packageCode=${group.bags[0].packageCode}`,
    )
  ).code,
  401,
);
for (const role of ["driver", "sales"]) {
  assert.equal(
    (
      await request(
        "/rd/quality-inspection/create-batch",
        tokens[role],
        "POST",
        batchRequest(group.bags, [0], [0]),
      )
    ).code,
    403,
  );
}
const invalidSample = batchRequest(group.bags, [0, 1], [2]);
await rejectCreate(invalidSample);
await rejectCreate({
  ...batchRequest(group.bags, [0, 1], [0]),
  packageIds: [ids[0], other.bags[0].id],
});
await rejectCreate({ ...batchRequest(group.bags, [0], [0]), items: [] });
const body = batchRequest(group.bags, [0, 1, 2], [0]);
const inspectionId = await create(body);
assert.equal(await create(body), inspectionId);
await rejectCreate({ ...body, remark: "different-content" });
assert.notEqual(
  (
    await request(
      "/rd/quality-inspection/create-batch",
      tokens.admin,
      "POST",
      body,
    )
  ).code,
  0,
);
let detail = await api(
  `/rd/quality-inspection/get?id=${inspectionId}`,
  tokens.quality,
);
assert.equal(detail.coverageCount, 3);
assert.equal(detail.sampleCount, 1);
assert.deepEqual(
  new Set(detail.members.map((bag) => bag.packageId)),
  new Set(ids.slice(0, 3)),
);
assert.equal(detail.items[0].samplePackageId, ids[0]);
assert.deepEqual(states(await units()), [
  "LABELED",
  "LABELED",
  "LABELED",
  "LABELED",
]);
const occupied = await scope(group.bags[2].packageCode);
assert.equal(occupied.representative.activeInspectionId, inspectionId);
assert.equal(occupied.packages.list.filter((bag) => bag.eligible).length, 1);
await rejectCreate(batchRequest(group.bags, [1, 3], [1]));
assert.notEqual(
  (
    await request("/rd/quality-inspection/create", tokens.quality, "POST", {
      packageId: ids[1],
      items: [{ itemCode: "TEST", itemName: "legacy", measuredValue: 1 }],
    })
  ).code,
  0,
);
assert.equal(
  (await scope(group.bags[3].packageCode)).representative.eligible,
  true,
);
await api(
  `/rd/quality-inspection/submit?id=${inspectionId}`,
  tokens.quality,
  "POST",
);
detail = await api(
  `/rd/quality-inspection/get?id=${inspectionId}`,
  tokens.quality,
);
assert.deepEqual(states(await units()), [
  "QC_PENDING",
  "QC_PENDING",
  "QC_PENDING",
  "LABELED",
]);
const afterSubmit = await fingerprint(ids);
await api(
  `/rd/quality-inspection/submit?id=${inspectionId}`,
  tokens.quality,
  "POST",
);
assert.equal(
  (await api(`/rd/quality-inspection/get?id=${inspectionId}`, tokens.quality))
    .submitTime,
  detail.submitTime,
);
assert.equal(await fingerprint(ids), afterSubmit);
assert.notEqual(
  (
    await request(
      `/rd/quality-inspection/approve?id=${inspectionId}`,
      tokens.quality,
      "POST",
    )
  ).code,
  0,
);
await api(
  `/rd/quality-inspection/approve?id=${inspectionId}`,
  tokens.admin,
  "POST",
);
const passed = await api(
  `/rd/quality-inspection/get?id=${inspectionId}`,
  tokens.quality,
);
assert.deepEqual(states(await units()), [
  "QC_PASSED",
  "QC_PASSED",
  "QC_PASSED",
  "LABELED",
]);
assert.deepEqual(states(await units(other)), ["LABELED"]);
const afterApproval = await fingerprint(ids);
await api(
  `/rd/quality-inspection/approve?id=${inspectionId}`,
  tokens.admin,
  "POST",
);
assert.equal(
  (await api(`/rd/quality-inspection/get?id=${inspectionId}`, tokens.quality))
    .reviewTime,
  passed.reviewTime,
);
assert.equal(await create(body), inspectionId);
assert.equal(await fingerprint(ids), afterApproval);
const domainEvents = (
  await api(`/rd/quality-inspection/get?id=${inspectionId}`, tokens.quality)
).events;
assert.deepEqual(
  domainEvents.map((event) => event.action),
  ["CREATE", "SUBMIT", "APPROVE"],
);
assert.equal(domainEvents[0].actorId, passed.registrarId);
assert.equal(domainEvents[1].actorId, passed.submitterId);
assert.equal(domainEvents[2].actorId, passed.reviewerId);
for (let index = 0; index < 3; index++) {
  const trace = await api(
    `/rd/package-unit/trace-detail/${group.bags[index].packageCode}`,
    tokens.quality,
  );
  assert.equal(trace.qualityInspectionId, inspectionId);
  assert.equal(trace.qualityCoverageCount, 3);
  assert.equal(trace.qualitySampleCount, 1);
  assert.equal(trace.qualitySampled, index === 0);
  const publicTrace = await api(
    `/rd/package-unit/public-trace/${group.bags[index].packageCode}`,
  );
  for (const key of [
    "qualityInspectionId",
    "qualityCoverageCount",
    "qualitySampleCount",
    "qualitySampled",
    "customerName",
    "locationCode",
    "members",
    "registrarName",
  ])
    assert.ok(!(key in publicTrace), key);
  assert.equal(publicTrace.qualityConclusion, "PASSED");
}
const untouched = await api(
  `/rd/package-unit/trace-detail/${group.bags[3].packageCode}`,
  tokens.quality,
);
assert.ok(!untouched.qualityInspectionId);
const failedBody = batchRequest(group.bags, [3], [3], 50);
const failedId = await create(failedBody);
await api(
  `/rd/quality-inspection/submit?id=${failedId}`,
  tokens.quality,
  "POST",
);
assert.equal(
  (await api(`/rd/quality-inspection/get?id=${failedId}`, tokens.quality))
    .items[0].qualified,
  false,
);
assert.notEqual(
  (
    await request(
      `/rd/quality-inspection/approve?id=${failedId}`,
      tokens.admin,
      "POST",
    )
  ).code,
  0,
);
assert.notEqual(
  (
    await request("/rd/quality-inspection/reject", tokens.admin, "POST", {
      id: failedId,
      reason: " ",
    })
  ).code,
  0,
);
await api("/rd/quality-inspection/reject", tokens.admin, "POST", {
  id: failedId,
  reason: marker,
});
const rejected = await api(
  `/rd/quality-inspection/get?id=${failedId}`,
  tokens.quality,
);
await api("/rd/quality-inspection/reject", tokens.admin, "POST", {
  id: failedId,
  reason: marker,
});
assert.equal(
  (await api(`/rd/quality-inspection/get?id=${failedId}`, tokens.quality))
    .reviewTime,
  rejected.reviewTime,
);
assert.deepEqual(states(await units()), [
  "QC_PASSED",
  "QC_PASSED",
  "QC_PASSED",
  "QC_REJECTED",
]);
const pendingStock = await api(
  `/rd/putaway-task/pending-package-page?customerName=${encodeURIComponent(marker)}&pageNo=1&pageSize=100`,
  tokens.warehouse,
);
assert.deepEqual(
  new Set(pendingStock.list.map((bag) => bag.id)),
  new Set(ids.slice(0, 3)),
);
const concurrent = await createOutput(3, "concurrent");
const concurrentBodies = [
  batchRequest(concurrent.bags, [0, 1], [0]),
  batchRequest(concurrent.bags, [1, 2], [2]),
];
const concurrencyResults = await Promise.all(
  concurrentBodies.map((payload) =>
    request(
      "/rd/quality-inspection/create-batch",
      tokens.quality,
      "POST",
      payload,
    ),
  ),
);
assert.equal(
  concurrencyResults.filter((result) => result.code === 0).length,
  1,
);
const winner = concurrencyResults.find((result) => result.code === 0).data;
const winnerDetail = await api(
  `/rd/quality-inspection/get?id=${winner}`,
  tokens.quality,
);
assert.equal(winnerDetail.members.length, 2);
assert.equal(
  (await scope(concurrent.bags[0].packageCode)).packages.list.filter(
    (bag) => bag.eligible,
  ).length,
  1,
);
const rollback = await createOutput(2, "rollback");
const rollbackId = await create(batchRequest(rollback.bags, [0, 1], [0]));
const trigger = `rd_qc_verify_${randomBytes(5).toString("hex")}`;
assert.match(trigger, /^rd_qc_verify_[a-f0-9]+$/);
const failSecond = async (status, action) => {
  await sql(
    `CREATE TRIGGER ${trigger} BEFORE UPDATE ON rd_package_unit FOR EACH ROW SET NEW.status = IF(NEW.id=${rollback.bags[1].id} AND NEW.status='${status}', NULL, NEW.status)`,
  );
  try {
    assert.notEqual((await action()).code, 0);
  } finally {
    await sql(`DROP TRIGGER IF EXISTS ${trigger}`);
  }
};
const beforeFailedSubmit = await fingerprint(
  rollback.bags.map((bag) => bag.id),
);
await failSecond("QC_PENDING", () =>
  request(
    `/rd/quality-inspection/submit?id=${rollbackId}`,
    tokens.quality,
    "POST",
  ),
);
assert.equal(
  await fingerprint(rollback.bags.map((bag) => bag.id)),
  beforeFailedSubmit,
);
assert.equal(
  (await api(`/rd/quality-inspection/get?id=${rollbackId}`, tokens.quality))
    .status,
  "DRAFT",
);
await api(
  `/rd/quality-inspection/submit?id=${rollbackId}`,
  tokens.quality,
  "POST",
);
const beforeFailedReview = await fingerprint(
  rollback.bags.map((bag) => bag.id),
);
await failSecond("QC_PASSED", () =>
  request(
    `/rd/quality-inspection/approve?id=${rollbackId}`,
    tokens.admin,
    "POST",
  ),
);
assert.equal(
  await fingerprint(rollback.bags.map((bag) => bag.id)),
  beforeFailedReview,
);
assert.equal(
  (await api(`/rd/quality-inspection/get?id=${rollbackId}`, tokens.quality))
    .status,
  "SUBMITTED",
);
await api(
  `/rd/quality-inspection/approve?id=${rollbackId}`,
  tokens.admin,
  "POST",
);
assert.equal(
  await sql(
    `SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='rd_erp' AND trigger_name='${trigger}'`,
  ),
  "0",
);
const auditFailure = await createOutput(2, "audit-rollback");
const auditId = await create(batchRequest(auditFailure.bags, [0, 1], [0]));
const auditTrigger = `rd_qc_audit_${randomBytes(5).toString("hex")}`;
await sql(`CREATE TRIGGER ${auditTrigger} BEFORE INSERT ON rd_quality_inspection_event FOR EACH ROW
  SET NEW.inspection_id = IF(NEW.inspection_id=${auditId} AND NEW.action='SUBMIT', NULL, NEW.inspection_id)`);
const beforeAuditFailure = await fingerprint(
  auditFailure.bags.map((bag) => bag.id),
);
try {
  assert.notEqual(
    (
      await request(
        `/rd/quality-inspection/submit?id=${auditId}`,
        tokens.quality,
        "POST",
      )
    ).code,
    0,
  );
} finally {
  await sql(`DROP TRIGGER IF EXISTS ${auditTrigger}`);
}
assert.equal(
  await fingerprint(auditFailure.bags.map((bag) => bag.id)),
  beforeAuditFailure,
);
let auditDetail = await api(
  `/rd/quality-inspection/get?id=${auditId}`,
  tokens.quality,
);
assert.equal(auditDetail.status, "DRAFT");
assert.deepEqual(
  auditDetail.events.map((event) => event.action),
  ["CREATE"],
);
await api(
  `/rd/quality-inspection/submit?id=${auditId}`,
  tokens.quality,
  "POST",
);
await sql(`CREATE TRIGGER ${auditTrigger} BEFORE INSERT ON rd_quality_inspection_event FOR EACH ROW
  SET NEW.inspection_id = IF(NEW.inspection_id=${auditId} AND NEW.action='APPROVE', NULL, NEW.inspection_id)`);
const beforeReviewAuditFailure = await fingerprint(
  auditFailure.bags.map((bag) => bag.id),
);
try {
  assert.notEqual(
    (
      await request(
        `/rd/quality-inspection/approve?id=${auditId}`,
        tokens.admin,
        "POST",
      )
    ).code,
    0,
  );
} finally {
  await sql(`DROP TRIGGER IF EXISTS ${auditTrigger}`);
}
assert.equal(
  await fingerprint(auditFailure.bags.map((bag) => bag.id)),
  beforeReviewAuditFailure,
);
auditDetail = await api(
  `/rd/quality-inspection/get?id=${auditId}`,
  tokens.quality,
);
assert.equal(auditDetail.status, "SUBMITTED");
assert.deepEqual(
  auditDetail.events.map((event) => event.action),
  ["CREATE", "SUBMIT"],
);
assert.equal(
  (await scope(auditFailure.bags[0].packageCode)).packages.list.filter(
    (bag) => bag.eligible,
  ).length,
  0,
);
await api(`/rd/quality-inspection/approve?id=${auditId}`, tokens.admin, "POST");
assert.equal(
  await sql(
    `SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='rd_erp' AND trigger_name='${auditTrigger}'`,
  ),
  "0",
);
const auditCreateSource = await createOutput(2, "audit-create-rollback");
const auditCreateBody = batchRequest(auditCreateSource.bags, [0, 1], [0]);
assert.match(auditCreateBody.clientRequestId, /^[a-f0-9-]{36}$/);
const auditCreateBefore = await fingerprint(
  auditCreateSource.bags.map((bag) => bag.id),
);
const orphanItemSql =
  "SELECT COUNT(*) FROM rd_quality_inspection_item item LEFT JOIN rd_quality_inspection header ON item.inspection_id=header.id WHERE header.id IS NULL";
const orphanItemsBefore = await sql(orphanItemSql);
await sql(`CREATE TRIGGER ${auditTrigger} BEFORE INSERT ON rd_quality_inspection_event FOR EACH ROW
  SET NEW.inspection_id = IF(NEW.client_request_id='${auditCreateBody.clientRequestId}' AND NEW.action='CREATE', NULL, NEW.inspection_id)`);
try {
  await rejectCreate(auditCreateBody);
} finally {
  await sql(`DROP TRIGGER IF EXISTS ${auditTrigger}`);
}
assert.equal(
  await fingerprint(auditCreateSource.bags.map((bag) => bag.id)),
  auditCreateBefore,
);
assert.equal(
  await sql(
    `SELECT COUNT(*) FROM rd_quality_inspection WHERE client_request_id='${auditCreateBody.clientRequestId}'`,
  ),
  "0",
);
assert.equal(
  await sql(
    `SELECT COUNT(*) FROM rd_quality_inspection_member WHERE package_id IN (${auditCreateSource.bags.map((bag) => bag.id).join(",")})`,
  ),
  "0",
);
assert.equal(await sql(orphanItemSql), orphanItemsBefore);
assert.equal(
  (await scope(auditCreateSource.bags[0].packageCode)).packages.list.filter(
    (bag) => bag.eligible,
  ).length,
  2,
);
const auditCreateId = await create(auditCreateBody);
assert.deepEqual(
  (
    await api(`/rd/quality-inspection/get?id=${auditCreateId}`, tokens.quality)
  ).events.map((event) => event.action),
  ["CREATE"],
);
await api(
  `/rd/quality-inspection/submit?id=${auditCreateId}`,
  tokens.quality,
  "POST",
);
await api(
  `/rd/quality-inspection/approve?id=${auditCreateId}`,
  tokens.admin,
  "POST",
);

const auditRejectSource = await createOutput(2, "audit-reject-rollback");
const auditRejectId = await create(
  batchRequest(auditRejectSource.bags, [0, 1], [0], 50),
);
await api(
  `/rd/quality-inspection/submit?id=${auditRejectId}`,
  tokens.quality,
  "POST",
);
const auditRejectBefore = await fingerprint(
  auditRejectSource.bags.map((bag) => bag.id),
);
const auditRejectHeader = await api(
  `/rd/quality-inspection/get?id=${auditRejectId}`,
  tokens.quality,
);
await sql(`CREATE TRIGGER ${auditTrigger} BEFORE INSERT ON rd_quality_inspection_event FOR EACH ROW
  SET NEW.inspection_id = IF(NEW.inspection_id=${auditRejectId} AND NEW.action='REJECT', NULL, NEW.inspection_id)`);
try {
  assert.notEqual(
    (
      await request("/rd/quality-inspection/reject", tokens.admin, "POST", {
        id: auditRejectId,
        reason: marker,
      })
    ).code,
    0,
  );
} finally {
  await sql(`DROP TRIGGER IF EXISTS ${auditTrigger}`);
}
assert.equal(
  await fingerprint(auditRejectSource.bags.map((bag) => bag.id)),
  auditRejectBefore,
);
assert.deepEqual(
  await api(`/rd/quality-inspection/get?id=${auditRejectId}`, tokens.quality),
  auditRejectHeader,
);
assert.equal(
  (await scope(auditRejectSource.bags[0].packageCode)).packages.list.filter(
    (bag) => bag.eligible,
  ).length,
  0,
);
await api("/rd/quality-inspection/reject", tokens.admin, "POST", {
  id: auditRejectId,
  reason: marker,
});
assert.deepEqual(
  (
    await api(`/rd/quality-inspection/get?id=${auditRejectId}`, tokens.quality)
  ).events.map((event) => event.action),
  ["CREATE", "SUBMIT", "REJECT"],
);
assert.equal(
  await sql(
    `SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema='rd_erp' AND trigger_name='${auditTrigger}'`,
  ),
  "0",
);
const submittedPage = await api(
  "/rd/quality-inspection/page?pageNo=1&pageSize=1&status=SUBMITTED",
  tokens.quality,
);
assert.ok(submittedPage.list.every((record) => record.status === "SUBMITTED"));
const allPage = await api(
  `/rd/quality-inspection/page?pageNo=1&pageSize=100&inspectionNo=${encodeURIComponent(passed.inspectionNo)}`,
  tokens.quality,
);
assert.equal(allPage.total, 1);
assert.equal(allPage.list[0].id, inspectionId);
let audit;
for (let attempt = 0; attempt < 20; attempt++) {
  audit = (
    await sql(`SELECT COUNT(*),COUNT(DISTINCT sub_type),COUNT(DISTINCT user_id) FROM system_operate_log
    WHERE biz_id=${inspectionId} AND type='RD 质量检验' AND success=b'1'`)
  )
    .split("\t")
    .map(Number);
  if (audit[0] >= 3 && audit[1] >= 3 && audit[2] === 2) break;
  await new Promise((resolve) => setTimeout(resolve, 250));
}
assert.ok(
  audit[0] >= 3 && audit[1] >= 3 && audit[2] === 2,
  `QC audit missing: ${audit}`,
);
const fixture = {
  marker,
  taskId,
  groups: { group, other, concurrent, rollback },
  inspectionId,
  failedId,
  winner,
  rollbackId,
};
await fs.writeFile(
  path.join(output, "fixture.json"),
  JSON.stringify(fixture, null, 2),
);
const result = {
  result: "PASS",
  taskId,
  inspectionId,
  coverage: 3,
  samples: 1,
  sourcePackages: 4,
  unselectedNotInherited: true,
  originalRequestReplay: true,
  exactScopeTrace: true,
  publicPrivacy: true,
  failedItemCannotPass: true,
  selfReviewDenied: true,
  overlappingConcurrencySingleWinner: true,
  submitAndReviewRollback: true,
  requiredAuditFailureRollback: true,
  auditedTransitionsVerified: ["CREATE", "SUBMIT", "APPROVE", "REJECT"],
  immutableDomainAudit: true,
  scopedTriggerCleaned: true,
  notPassedExcludedFromPutaway: true,
  serverFilters: true,
  persistedAudit: {
    successfulEntries: audit[0],
    actionTypes: audit[1],
    actors: audit[2],
  },
  fieldAcceptance: false,
};
await fs.writeFile(
  path.join(output, "batch-result.json"),
  JSON.stringify(result, null, 2),
);
console.log(JSON.stringify(result));
