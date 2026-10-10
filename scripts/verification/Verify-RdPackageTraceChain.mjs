import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import {
  root,
  baseUrl,
  api,
  request,
  prepareFixture,
} from "./RdQrLocalFixture.mjs";

const output = path.join(root, "temp/qr-e5-trace-verification");
await fs.mkdir(output, { recursive: true });
const { tokens, accounts } = await prepareFixture();
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
const sql = async (query) =>
  (
    await promisify(execFile)(
      process.env.RD_MYSQL_BIN || "mysql",
      [
        "--host=127.0.0.1",
        "--port=3310",
        "--user=root",
        "--batch",
        "--skip-column-names",
        "--default-character-set=utf8mb4",
        "rd_erp",
        "-e",
        query,
      ],
      { env: { ...process.env, MYSQL_PWD: settings.RD_DB_PASSWORD } },
    )
  ).stdout.trim();
const trace = (code, internal = true) =>
  api(
    `/rd/package-unit/${internal ? "trace-detail" : "public-trace"}/${encodeURIComponent(code)}`,
    internal ? tokens.warehouse : undefined,
  );
const hasTitle = (result, title) =>
  result.events.some((event) => event.title === title);
const historyCount = async (ids) => {
  assert.ok(ids.every((id) => Number.isSafeInteger(id) && id > 0));
  return Number(
    await sql(
      `SELECT COUNT(*) FROM rd_package_task_event WHERE package_id IN (${ids.join(",")}) AND deleted=0`,
    ),
  );
};
const withFaultTrigger = async (table, condition, action) => {
  assert.ok(
    ["rd_package_task_event", "rd_loading_package_claim"].includes(table),
  );
  const name = `rd_e5_rollback_${Date.now()}`;
  assert.match(name, /^rd_e5_rollback_\d+$/);
  await sql(
    `CREATE TRIGGER ${name} BEFORE INSERT ON ${table} FOR EACH ROW SET NEW.package_id = IF(${condition}, NULL, NEW.package_id)`,
  );
  try {
    await action();
  } finally {
    await sql(`DROP TRIGGER ${name}`);
  }
};
let fixture;
if (process.env.RD_TRACE_UI_ONLY === "true") {
  fixture = JSON.parse(
    await fs.readFile(path.join(output, "fixture.json"), "utf8"),
  );
  assert.equal(fixture.marker, "二维码完整追溯验证（非现场数据）");
} else {
  const marker = "二维码完整追溯验证（非现场数据）";
  const customerName = `${marker}-${Date.now()}`;
  const productionId = await api(
    "/rd/production-output/create",
    tokens.production,
    "POST",
    {
      productionDate: "2026-10-11",
      customerName,
      productModel: "QR-E5",
      furnaceNo: `E5-${Date.now()}`,
      furnaceLayer: "下层",
      outputQty: 4,
      packageWeight: 1000,
      sourceSheet: marker,
      remark: marker,
    },
  );
  const production = await api(
    `/rd/production-output/get?id=${productionId}`,
    tokens.production,
  );
  await api("/rd/production-output/confirm", tokens.production, "POST", {
    id: productionId,
    version: production.version,
  });
  const packages = await api(
    "/rd/package-unit/generate",
    tokens.production,
    "POST",
    { productionOutputId: productionId, packageCount: 4 },
  );
  await api("/rd/package-unit/print-batch", tokens.production, "POST", {
    packageIds: packages.map((pkg) => pkg.id),
  });
  await api("/rd/package-unit/reprint", tokens.production, "POST", {
    packageId: packages[0].id,
    reprintReason: "本地补打追溯验证，非实体打印",
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
            itemName: "本地软件验证（非检测值）",
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
  const locations = (
    await api("/rd/putaway-task/location-map", tokens.warehouse)
  ).filter(
    (item) =>
      item.enabled &&
      !item.frozen &&
      (item.availablePackages == null || item.availablePackages >= 4) &&
      (item.availableWeight == null || item.availableWeight >= 4000),
  );
  assert.ok(
    locations.length >= 2,
    "Two available locations are required in the isolated local instance",
  );
  const putawayTaskId = await api(
    "/rd/putaway-task/create",
    tokens.warehouse,
    "POST",
    {
      plannedLocationId: locations[0].id,
      forkliftDriverId: accounts.driver.userId,
      packageIds: packages.map((pkg) => pkg.id),
      remark: marker,
    },
  );
  const putawayUrl = `/rd/putaway-task/get?id=${putawayTaskId}`;
  const assignment = await sql(
    `SELECT id,package_id,location_code,event_time FROM rd_package_task_event WHERE source_type='PUTAWAY_TASK' AND source_id=${putawayTaskId} AND event_code='ASSIGNED' ORDER BY id`,
  );
  await api("/rd/putaway-task/exception", tokens.driver, "POST", {
    id: putawayTaskId,
    exceptionType: "LOCATION_OCCUPIED",
    description: "本地模拟计划位占用，需要仓管改派",
    attachmentRefs: "",
  });
  let detail = await api(putawayUrl, tokens.warehouse);
  await api("/rd/putaway-task/reassign", tokens.warehouse, "PUT", {
    id: putawayTaskId,
    version: detail.task.version,
    plannedLocationId: locations[1].id,
    forkliftDriverId: accounts.driver.userId,
    handlingResult: "本地模拟仓管调整至第二库位",
  });
  assert.equal(
    await sql(
      `SELECT id,package_id,location_code,event_time FROM rd_package_task_event WHERE source_type='PUTAWAY_TASK' AND source_id=${putawayTaskId} AND event_code='ASSIGNED' ORDER BY id`,
    ),
    assignment,
  );
  for (const pkg of packages) {
    const result = await trace(pkg.packageCode);
    assert.ok(hasTitle(result, "现场异常已上报"));
    assert.ok(hasTitle(result, "仓管已处理现场异常"));
    assert.ok(hasTitle(result, "上架任务已改派"));
    assert.ok(
      result.events
        .find((event) => event.title === "已派上架任务")
        .detail.includes(locations[0].locationCode),
    );
    assert.ok(
      result.events
        .find((event) => event.title === "上架任务已改派")
        .detail.includes(locations[1].locationCode),
    );
  }
  detail = await api(putawayUrl, tokens.warehouse);
  const firstReq = {
    id: putawayTaskId,
    version: detail.task.version,
    packageCode: packages[0].qrCode,
    idempotencyKey: `e5-put-first-${Date.now()}`,
    taskLineIds: detail.lines.slice(0, 2).map((line) => line.id),
    deviceCode: "LOCAL-E5",
  };
  await api(
    "/rd/putaway-task/batch-scan/confirm",
    tokens.warehouse,
    "POST",
    firstReq,
  );
  const afterFirst = await historyCount(packages.map((pkg) => pkg.id));
  assert.equal(
    (
      await api(
        "/rd/putaway-task/batch-scan/confirm",
        tokens.warehouse,
        "POST",
        firstReq,
      )
    ).replayed,
    true,
  );
  assert.equal(await historyCount(packages.map((pkg) => pkg.id)), afterFirst);
  detail = await api(putawayUrl, tokens.warehouse);
  await api("/rd/putaway-task/batch-scan/confirm", tokens.warehouse, "POST", {
    ...firstReq,
    version: detail.task.version,
    idempotencyKey: `e5-put-rest-${Date.now()}`,
    taskLineIds: detail.lines
      .filter((line) => line.lineStatus === "PENDING")
      .map((line) => line.id),
  });
  for (const pkg of packages) {
    const result = await trace(pkg.packageCode);
    assert.ok(hasTitle(result, "仓管已核验到位包件"));
    assert.equal(result.status, "INBOUND_PENDING");
    assert.equal(hasTitle(result, "已完成入库"), false);
  }
  detail = await api(putawayUrl, tokens.warehouse);
  await api("/rd/putaway-task/confirm", tokens.warehouse, "PUT", {
    id: putawayTaskId,
    version: detail.task.version,
    finalLocationId: locations[1].id,
  });
  const inStock = await api(
    `/rd/package-unit/trace?productionOutputId=${productionId}`,
    tokens.warehouse,
  );
  assert.ok(
    inStock.every(
      (pkg) => pkg.status === "IN_STOCK" && pkg.locationId === locations[1].id,
    ),
  );
  const orderId = await api("/rd/sale-order/create", tokens.sales, "POST", {
    orderNo: `E5-SO-${Date.now()}`,
    orderDate: "2026-10-11",
    customerName,
    productModel: "QR-E5",
    requiredQty: 2,
    actualOutputQty: 0,
    remark: marker,
  });
  await api("/rd/order-fulfillment/allocate", tokens.sales, "POST", {
    orderId,
    stockLotId: inStock[0].stockLotId,
    quantity: 2,
    remark: marker,
  });
  const pickupId = await api(
    "/rd/loading-task/pickup-slip/create",
    tokens.sales,
    "POST",
    {
      slipNo: `E5-PS-${Date.now()}`,
      customerName,
      requestedPackageCount: 2,
      requestedWeight: 2000,
      remark: marker,
      lines: [
        {
          lineNo: 1,
          saleOrderId: orderId,
          productModel: "QR-E5",
          requestedPackageCount: 2,
          requestedWeight: 2000,
          remark: marker,
        },
      ],
    },
  );
  const pickup = await api(
    `/rd/loading-task/pickup-slip/get?id=${pickupId}`,
    tokens.sales,
  );
  const loadingTaskId = await api(
    "/rd/loading-task/create",
    tokens.warehouse,
    "POST",
    {
      pickupSlipId: pickupId,
      forkliftDriverId: accounts.driver.userId,
      remark: marker,
      selections: packages.slice(0, 2).map((pkg) => ({
        packageId: pkg.id,
        pickupSlipLineId: pickup.lines[0].id,
      })),
    },
  );
  const loadingUrl = `/rd/loading-task/get?id=${loadingTaskId}`;
  let loading = await api(loadingUrl, tokens.warehouse);
  const oldLine = loading.lines.find(
    (line) => line.packageId === packages[0].id,
  );
  await api("/rd/loading-task/exception", tokens.driver, "POST", {
    taskId: loadingTaskId,
    taskLineId: oldLine.id,
    exceptionType: "LABEL_DAMAGED",
    description: "本地模拟标签异常，由仓管选择同批替代包",
  });
  loading = await api(loadingUrl, tokens.warehouse);
  const beforeBadReplacement = await historyCount(
    packages.map((pkg) => pkg.id),
  );
  assert.notEqual(
    (
      await request(
        "/rd/loading-task/exception/resolve",
        tokens.warehouse,
        "PUT",
        {
          taskId: loadingTaskId,
          version: loading.task.version,
          resolutionAction: "REPLACE_PACKAGE",
          handlingResult: "本地模拟错误替代码，必须整笔回滚",
          replacements: [
            { taskLineId: oldLine.id, newPackageCode: "E5-NOT-EXIST" },
          ],
        },
      )
    ).code,
    0,
  );
  assert.equal(
    await historyCount(packages.map((pkg) => pkg.id)),
    beforeBadReplacement,
  );
  assert.equal(
    (await api(loadingUrl, tokens.warehouse)).exceptions[0].exceptionStatus,
    "OPEN",
  );
  const beforeClaimFailure = JSON.stringify(
    await api(loadingUrl, tokens.warehouse),
  );
  const oldClaim = await sql(
    `SELECT id,package_id,task_id,task_line_id FROM rd_loading_package_claim WHERE task_id=${loadingTaskId} ORDER BY id`,
  );
  await withFaultTrigger(
    "rd_loading_package_claim",
    `NEW.package_id=${packages[2].id} AND NEW.task_id=${loadingTaskId}`,
    async () => {
      assert.notEqual(
        (
          await request(
            "/rd/loading-task/exception/resolve",
            tokens.warehouse,
            "PUT",
            {
              taskId: loadingTaskId,
              version: loading.task.version,
              resolutionAction: "REPLACE_PACKAGE",
              handlingResult: "本地故障注入：换包新占用失败必须整笔回滚",
              replacements: [
                { taskLineId: oldLine.id, newPackageCode: packages[2].qrCode },
              ],
            },
          )
        ).code,
        0,
      );
    },
  );
  assert.equal(
    JSON.stringify(await api(loadingUrl, tokens.warehouse)),
    beforeClaimFailure,
  );
  assert.equal(
    await historyCount(packages.map((pkg) => pkg.id)),
    beforeBadReplacement,
  );
  assert.equal(
    await sql(
      `SELECT id,package_id,task_id,task_line_id FROM rd_loading_package_claim WHERE task_id=${loadingTaskId} ORDER BY id`,
    ),
    oldClaim,
  );
  await api("/rd/loading-task/exception/resolve", tokens.warehouse, "PUT", {
    taskId: loadingTaskId,
    version: loading.task.version,
    resolutionAction: "REPLACE_PACKAGE",
    handlingResult: "本地模拟同批第三包替代第一包",
    replacements: [
      { taskLineId: oldLine.id, newPackageCode: packages[2].qrCode },
    ],
  });
  assert.ok(
    hasTitle(await trace(packages[0].packageCode), "包件已从装车任务换出"),
  );
  const newTrace = await trace(packages[2].packageCode);
  assert.ok(hasTitle(newTrace, "包件作为替代货加入装车任务"));
  assert.equal(hasTitle(newTrace, "已派装车任务"), false);
  assert.notEqual(
    (
      await request(
        "/rd/loading-task/batch-scan/preview",
        tokens.warehouse,
        "POST",
        {
          taskId: loadingTaskId,
          packageCode: packages[0].qrCode,
        },
      )
    ).code,
    0,
  );
  loading = await api(loadingUrl, tokens.warehouse);
  const beforeBatchFailure = JSON.stringify(loading);
  const beforeBatchHistory = await historyCount(packages.map((pkg) => pkg.id));
  const beforeBatchRows = await sql(
    `SELECT COUNT(*) FROM rd_loading_scan_record WHERE task_id=${loadingTaskId}`,
  );
  const second = loading.lines[1];
  await withFaultTrigger(
    "rd_package_task_event",
    `NEW.source_type='LOADING_SCAN' AND NEW.package_id=${second.packageId}`,
    async () => {
      assert.notEqual(
        (
          await request(
            "/rd/loading-task/batch-scan/confirm",
            tokens.warehouse,
            "POST",
            {
              taskId: loadingTaskId,
              representativePackageCode: packages[1].qrCode,
              taskLineIds: loading.lines.map((line) => line.id),
              idempotencyKey: `e5-batch-rollback-${Date.now()}`,
            },
          )
        ).code,
        0,
      );
    },
  );
  assert.equal(
    JSON.stringify(await api(loadingUrl, tokens.warehouse)),
    beforeBatchFailure,
  );
  assert.equal(
    await historyCount(packages.map((pkg) => pkg.id)),
    beforeBatchHistory,
  );
  assert.equal(
    await sql(
      `SELECT COUNT(*) FROM rd_loading_scan_record WHERE task_id=${loadingTaskId}`,
    ),
    beforeBatchRows,
  );
  for (const line of loading.lines)
    assert.equal(
      hasTitle(await trace(line.packageCode), "仓管已核验装车包件"),
      false,
    );
  for (const line of loading.lines) {
    const req = {
      taskId: loadingTaskId,
      representativePackageCode: packages[1].qrCode,
      taskLineIds: [line.id],
      idempotencyKey: `e5-load-${line.id}-${Date.now()}`,
      deviceCode: "LOCAL-E5",
    };
    assert.equal(
      (
        await request(
          "/rd/loading-task/batch-scan/confirm",
          tokens.driver,
          "POST",
          req,
        )
      ).code,
      403,
    );
    await api(
      "/rd/loading-task/batch-scan/confirm",
      tokens.warehouse,
      "POST",
      req,
    );
    const after = await historyCount(packages.map((pkg) => pkg.id));
    assert.equal(
      (
        await api(
          "/rd/loading-task/batch-scan/confirm",
          tokens.warehouse,
          "POST",
          req,
        )
      ).replayed,
      true,
    );
    assert.equal(await historyCount(packages.map((pkg) => pkg.id)), after);
    assert.equal(
      (await trace(line.packageCode)).events.filter(
        (event) => event.title === "仓管已核验装车包件",
      ).length,
      1,
    );
  }
  assert.equal(
    hasTitle(await trace(packages[0].packageCode), "仓管已核验装车包件"),
    false,
  );
  loading = await api(loadingUrl, tokens.warehouse);
  await api("/rd/loading-task/confirm-outbound", tokens.warehouse, "POST", {
    taskId: loadingTaskId,
    version: loading.task.version,
    remark: marker,
  });
  assert.equal((await trace(packages[0].packageCode)).status, "IN_STOCK");
  assert.equal(
    hasTitle(await trace(packages[0].packageCode), "已完成出库"),
    false,
  );
  for (const pkg of [packages[1], packages[2]]) {
    const result = await trace(pkg.packageCode);
    assert.equal(result.status, "OUTBOUNDED");
    assert.ok(hasTitle(result, "已完成出库"));
  }
  const cancelPickupId = await api(
    "/rd/loading-task/pickup-slip/create",
    tokens.sales,
    "POST",
    {
      slipNo: `E5-CANCEL-${Date.now()}`,
      customerName,
      requestedPackageCount: 1,
      requestedWeight: 1000,
      remark: marker,
      lines: [
        {
          lineNo: 1,
          productModel: "QR-E5",
          requestedPackageCount: 1,
          requestedWeight: 1000,
          remark: marker,
        },
      ],
    },
  );
  const cancelPickup = await api(
    `/rd/loading-task/pickup-slip/get?id=${cancelPickupId}`,
    tokens.sales,
  );
  const cancelTaskId = await api(
    "/rd/loading-task/create",
    tokens.warehouse,
    "POST",
    {
      pickupSlipId: cancelPickupId,
      forkliftDriverId: accounts.driver.userId,
      remark: marker,
      selections: [
        {
          packageId: packages[3].id,
          pickupSlipLineId: cancelPickup.lines[0].id,
        },
      ],
    },
  );
  await api("/rd/loading-task/exception", tokens.driver, "POST", {
    taskId: cancelTaskId,
    exceptionType: "VEHICLE_CHANGED",
    description: "本地模拟任务取消，库存保持在库",
  });
  const cancel = await api(
    `/rd/loading-task/get?id=${cancelTaskId}`,
    tokens.warehouse,
  );
  await api("/rd/loading-task/exception/resolve", tokens.warehouse, "PUT", {
    taskId: cancelTaskId,
    version: cancel.task.version,
    resolutionAction: "CANCEL",
    handlingResult: "本地模拟取消提货安排",
  });
  const cancelled = await trace(packages[3].packageCode);
  assert.ok(hasTitle(cancelled, "装车任务已取消"));
  assert.equal(cancelled.status, "IN_STOCK");
  assert.equal(hasTitle(cancelled, "已完成出库"), false);
  fixture = {
    marker,
    productionId,
    packageIds: packages.map((pkg) => pkg.id),
    codes: packages.map((pkg) => pkg.packageCode),
    putawayTaskId,
    loadingTaskId,
    cancelTaskId,
    locationId: locations[1].id,
    sourceLocationCode: locations[0].locationCode,
    locationCode: locations[1].locationCode,
    orderId,
    pickupId,
    cancelPickupId,
    rollbackChecks: [
      "invalid-replacement",
      "replacement-claim-after-snapshots",
      "batch-second-history-after-first",
    ],
  };
  await fs.writeFile(
    path.join(output, "fixture.json"),
    JSON.stringify(fixture, null, 2),
  );
}

const mainCode = fixture.codes[1];
const internal = await trace(mainCode);
const publicView = await trace(mainCode, false);
assert.deepEqual(
  new Set(internal.events.map((event) => event.stage)),
  new Set([
    "PRODUCTION",
    "LABEL",
    "QUALITY",
    "PUTAWAY",
    "EXCEPTION",
    "LOADING",
    "STOCK",
  ]),
);
const publicJson = JSON.stringify(publicView);
for (const forbidden of [
  "customerName",
  "locationCode",
  "productionOutputId",
  "qualityInspectionId",
  fixture.locationCode,
  fixture.sourceLocationCode,
  "本地模拟",
  "叉车司机",
  "E4本地",
  "上架任务",
  "装车任务",
])
  assert.equal(publicJson.includes(forbidden), false, forbidden);
assert.ok(
  publicView.events.every((event) =>
    ["PRODUCTION", "LABEL", "QUALITY", "STOCK"].includes(event.stage),
  ),
);
assert.ok(publicView.events.some((event) => event.title === "质检通过"));
for (let index = 1; index < internal.events.length; index++)
  assert.ok(
    new Date(internal.events[index - 1].eventTime) <=
      new Date(internal.events[index].eventTime),
  );
const readFingerprint = () =>
  sql(
    `SELECT id,status,version,location_id,stock_lot_id FROM rd_package_unit WHERE id IN (${fixture.packageIds.join(",")}) ORDER BY id; SELECT id,warehouse_qty,stock_weight,locked_qty FROM rd_stock_lot WHERE id IN (SELECT stock_lot_id FROM rd_package_unit WHERE id IN (${fixture.packageIds.join(",")})) ORDER BY id; SELECT COUNT(*) FROM rd_stock_transaction WHERE package_id IN (${fixture.packageIds.join(",")}); SELECT COUNT(*) FROM rd_package_task_event WHERE package_id IN (${fixture.packageIds.join(",")})`,
  );
const before = await readFingerprint();
const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
const browserChecks = [];
try {
  for (const [name, account] of [
    ["internal", accounts.warehouse],
    ["public", null],
  ]) {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    const page = await context.newPage();
    const pageErrors = [];
    page.on("pageerror", (error) => pageErrors.push(error.message));
    if (account) {
      await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
      await page
        .getByPlaceholder("账号", { exact: true })
        .fill(account.username);
      await page
        .getByPlaceholder("密码", { exact: true })
        .fill(account.password);
      await page.getByRole("button", { name: "登录", exact: true }).click();
      await page.waitForURL((url) => !url.pathname.includes("/login"));
    }
    await page.goto(`${baseUrl}/pkg/${mainCode}`, { waitUntil: "networkidle" });
    await page.getByRole("heading", { name: mainCode }).waitFor();
    assert.equal(
      await page.locator(".trace-timeline strong").count(),
      (name === "internal" ? internal : publicView).events.length,
    );
    if (name === "internal") {
      await page.locator(".timeline-filter__select").click();
      await page.getByRole("option", { name: "异常", exact: true }).click();
      assert.equal(
        await page.locator(".trace-timeline strong").count(),
        internal.events.filter((event) => event.stage === "EXCEPTION").length,
      );
      await page.locator(".timeline-filter__select").click();
      await page.getByRole("option", { name: "全部阶段", exact: true }).click();
    } else
      assert.equal(
        await page.getByRole("heading", { name: "内部作业信息" }).count(),
        0,
      );
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 1000 });
      await page
        .locator(".el-select__popper:visible")
        .waitFor({ state: "hidden" });
      await page
        .getByRole("heading", { name: mainCode })
        .scrollIntoViewIfNeeded();
      await page.screenshot({
        path: path.join(output, `${name}-${width}.png`),
        fullPage: true,
      });
      const dimensions = await page.evaluate(() => ({
        width: document.documentElement.clientWidth,
        scroll: document.documentElement.scrollWidth,
        traceWidth: document.querySelector(".package-trace-page").clientWidth,
        traceScroll: document.querySelector(".package-trace-page").scrollWidth,
      }));
      if (dimensions.scroll > dimensions.width) {
        console.log(
          JSON.stringify(
            await page.evaluate(() =>
              Array.from(document.querySelectorAll("body *"))
                .map((element) => ({
                  tag: element.tagName,
                  className: element.className,
                  right: element.getBoundingClientRect().right,
                  width: element.getBoundingClientRect().width,
                }))
                .filter(
                  (item) =>
                    item.width > 0 &&
                    item.right > document.documentElement.clientWidth,
                )
                .slice(0, 12),
            ),
            null,
            2,
          ),
        );
      }
      assert.ok(
        dimensions.scroll <= dimensions.width &&
          dimensions.traceScroll <= dimensions.traceWidth,
        `${name} ${width}: horizontal overflow`,
      );
      const scrollBefore = await page
        .locator(".package-trace-page")
        .evaluate((element) => element.scrollTop);
      await page.mouse.move(width - 30, 400);
      await page.mouse.wheel(0, 600);
      await page.waitForFunction(
        (before) =>
          document.querySelector(".package-trace-page").scrollTop > before,
        scrollBefore,
      );
      await page
        .getByRole("heading", { name: "流转记录", exact: true })
        .scrollIntoViewIfNeeded();
      await page.screenshot({
        path: path.join(output, `${name}-${width}-timeline.png`),
      });
      await page
        .locator(".trace-timeline .el-timeline-item")
        .last()
        .scrollIntoViewIfNeeded();
      await page.screenshot({
        path: path.join(output, `${name}-${width}-timeline-end.png`),
      });
      browserChecks.push({
        view: name,
        width,
        milestones: await page.locator(".trace-timeline strong").count(),
        horizontalOverflow: false,
        wheelScrolling: true,
      });
    }
    assert.deepEqual(pageErrors, []);
    await context.close();
  }
} finally {
  await browser.close();
}
assert.equal(
  await readFingerprint(),
  before,
  "Querying and filtering the trace must not change inventory or history",
);
const result = {
  marker: fixture.marker,
  baseUrl,
  productionId: fixture.productionId,
  codes: fixture.codes,
  tasks: {
    putaway: fixture.putawayTaskId,
    loading: fixture.loadingTaskId,
    cancelled: fixture.cancelTaskId,
  },
  internalMilestones: internal.events.length,
  publicMilestones: publicView.events.length,
  historyRows: await historyCount(fixture.packageIds),
  browserChecks,
  publicPrivacy: true,
  readOnlyFingerprint: true,
  rollbackChecks: fixture.rollbackChecks,
  fieldAcceptance: false,
};
await fs.writeFile(
  path.join(output, "result.json"),
  JSON.stringify(result, null, 2),
);
await fs.writeFile(
  path.join(output, "internal-trace.json"),
  JSON.stringify(internal, null, 2),
);
await fs.writeFile(
  path.join(output, "public-trace.json"),
  JSON.stringify(publicView, null, 2),
);
console.log(JSON.stringify(result, null, 2));
