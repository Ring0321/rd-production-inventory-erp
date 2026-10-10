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

const output = path.join(root, "temp/qr-putaway-verification");
await fs.mkdir(output, { recursive: true });
const fixtureFile = path.join(output, "fixture.json");
const marker = "二维码仓管批次入库验证（非现场数据）";
const { tokens, accounts } = await prepareFixture();
const envFile = await fs.readFile(
  path.join(root, "temp/local-runtime-patent/.env.local"),
  "utf8",
);
const password = envFile
  .split(/\r?\n/)
  .find((line) => line.startsWith("RD_DB_PASSWORD="))
  .slice("RD_DB_PASSWORD=".length);
const sql = async (query) =>
  (
    await promisify(execFile)(
      "mysql",
      [
        "--host=127.0.0.1",
        "--user=root",
        "--port=3310",
        "--batch",
        "--skip-column-names",
        "rd_erp",
        "-e",
        query,
      ],
      { env: { ...process.env, MYSQL_PWD: password } },
    )
  ).stdout.trim();
let fixture;
try {
  fixture = JSON.parse(await fs.readFile(fixtureFile, "utf8"));
  assert.equal(fixture.marker, marker);
} catch (error) {
  if (error.code !== "ENOENT") throw error;
  fixture = { marker, inspections: {} };
}
const save = () => fs.writeFile(fixtureFile, JSON.stringify(fixture, null, 2));
if (!fixture.outputId) {
  fixture.outputId = await api(
    "/rd/production-output/create",
    tokens.production,
    "POST",
    {
      productionDate: "2026-10-11",
      customerName: marker,
      productModel: "QR-PUT",
      furnaceNo: `QR-PUT-${Date.now()}`,
      furnaceLayer: "上层",
      outputQty: 3,
      packageWeight: 1000,
      sourceSheet: marker,
      remark: marker,
    },
  );
  await save();
}
const production = await api(
  `/rd/production-output/get?id=${fixture.outputId}`,
  tokens.production,
);
assert.equal(production.sourceSheet, marker);
if (production.status === "DRAFT")
  await api("/rd/production-output/confirm", tokens.production, "POST", {
    id: production.id,
    version: production.version,
  });
let packages = await api(
  `/rd/package-unit/trace?productionOutputId=${fixture.outputId}`,
  tokens.production,
);
if (!packages.length)
  packages = await api("/rd/package-unit/generate", tokens.production, "POST", {
    productionOutputId: fixture.outputId,
    packageCount: 3,
  });
const unprinted = packages.filter((pkg) => pkg.status === "CREATED");
if (unprinted.length)
  await api("/rd/package-unit/print-batch", tokens.production, "POST", {
    packageIds: unprinted.map((pkg) => pkg.id),
  });
packages = await api(
  `/rd/package-unit/trace?productionOutputId=${fixture.outputId}`,
  tokens.production,
);
for (const pkg of packages) {
  if (["QC_PASSED", "INBOUND_PENDING", "IN_STOCK"].includes(pkg.status))
    continue;
  if (!fixture.inspections[pkg.id]) {
    fixture.inspections[pkg.id] = await api(
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
    await save();
  }
  const id = fixture.inspections[pkg.id];
  let inspection = await api(
    `/rd/quality-inspection/get?id=${id}`,
    tokens.quality,
  );
  if (inspection.status === "DRAFT")
    await api(`/rd/quality-inspection/submit?id=${id}`, tokens.quality, "POST");
  inspection = await api(`/rd/quality-inspection/get?id=${id}`, tokens.quality);
  if (inspection.status === "SUBMITTED")
    await api(`/rd/quality-inspection/approve?id=${id}`, tokens.admin, "POST");
}
if (!fixture.taskId) {
  const locations = await api(
    "/rd/putaway-task/location-map",
    tokens.warehouse,
  );
  const location = locations.find(
    (item) =>
      item.enabled &&
      !item.frozen &&
      (item.availablePackages == null || item.availablePackages >= 3),
  );
  assert.ok(location);
  fixture.locationId = location.id;
  fixture.locationCode = location.locationCode;
  fixture.taskId = await api(
    "/rd/putaway-task/create",
    tokens.warehouse,
    "POST",
    {
      plannedLocationId: location.id,
      forkliftDriverId: accounts.driver.userId,
      packageIds: packages.map((pkg) => pkg.id),
      remark: marker,
    },
  );
  await save();
}
const taskUrl = `/rd/putaway-task/get?id=${fixture.taskId}`;
const summary = () => api("/rd/dashboard/overview", tokens.admin);
const records = async () => {
  assert.ok(Number.isSafeInteger(fixture.taskId));
  return (
    await sql(
      `SELECT COUNT(*) FROM rd_putaway_scan_record WHERE task_id=${fixture.taskId} AND deleted=0; SELECT COUNT(*) FROM rd_putaway_batch_scan_record WHERE task_id=${fixture.taskId} AND deleted=0`,
    )
  )
    .split(/\s+/)
    .map(Number);
};
let detail = await api(taskUrl, tokens.warehouse);
const uiOnly = process.env.RD_PUTAWAY_UI_ONLY === "true";
const representativeCode = packages[0].packageCode;
let initial;
if (!uiOnly) {
  assert.equal(
    detail.task.status,
    "ASSIGNED",
    "Use UI-only mode after this marked fixture has been completed",
  );
  initial = await summary();
  assert.equal(
    (
      await request("/rd/putaway-task/scan", tokens.driver, "POST", {
        id: fixture.taskId,
        packageCode: representativeCode,
        idempotencyKey: "driver-denied",
      })
    ).code,
    403,
  );
  assert.equal(
    (
      await request(
        "/rd/putaway-task/batch-scan/confirm",
        tokens.driver,
        "POST",
        {
          id: fixture.taskId,
          version: detail.task.version,
          packageCode: representativeCode,
          idempotencyKey: "driver-batch-denied",
          taskLineIds: [detail.lines[0].id],
        },
      )
    ).code,
    403,
  );
  const preview = await api(
    "/rd/putaway-task/batch-scan/preview",
    tokens.warehouse,
    "POST",
    {
      packageCode: `https://id.tjldsic.com/pkg/${representativeCode}`,
    },
  );
  assert.equal(preview.task.id, fixture.taskId);
  assert.equal(preview.lines.length, 3);
  assert.deepEqual(await records(), [0, 0]);
  assert.notEqual(
    (
      await request(
        "/rd/putaway-task/batch-scan/preview",
        tokens.warehouse,
        "POST",
        {
          id: fixture.taskId,
          packageCode: "WRONG-LOCAL-CODE",
        },
      )
    ).code,
    0,
  );
  const partial = {
    id: fixture.taskId,
    version: preview.task.version,
    packageCode: representativeCode,
    idempotencyKey: crypto.randomUUID(),
    taskLineIds: [preview.lines[0].id],
    deviceCode: "LOCAL-QRCHECK",
  };
  const first = await api(
    "/rd/putaway-task/batch-scan/confirm",
    tokens.warehouse,
    "POST",
    partial,
  );
  assert.equal(first.confirmedCount, 1);
  assert.equal(first.task.scannedPackageCount, 1);
  assert.equal(first.task.status, "MOVING");
  const replay = await api(
    "/rd/putaway-task/batch-scan/confirm",
    tokens.warehouse,
    "POST",
    partial,
  );
  assert.ok(replay.replayed);
  assert.equal(replay.confirmedCount, 0);
  assert.equal(replay.recordId, first.recordId);
  assert.notEqual(
    (
      await request(
        "/rd/putaway-task/batch-scan/confirm",
        tokens.warehouse,
        "POST",
        {
          ...partial,
          taskLineIds: [preview.lines[1].id],
        },
      )
    ).code,
    0,
  );
  assert.notEqual(
    (
      await request(
        "/rd/putaway-task/batch-scan/confirm",
        tokens.warehouse,
        "POST",
        {
          ...partial,
          idempotencyKey: crypto.randomUUID(),
          taskLineIds: [preview.lines[1].id],
        },
      )
    ).code,
    0,
  );
  assert.deepEqual(await records(), [1, 1]);
  const afterPartial = await api(
    `/rd/package-unit/trace?productionOutputId=${fixture.outputId}`,
    tokens.warehouse,
  );
  assert.ok(afterPartial.every((pkg) => pkg.status === "INBOUND_PENDING"));
  assert.deepEqual(await summary(), initial);
}
const requireFrontend = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = requireFrontend(
  path.join(process.env.RD_BROWSER_MODULES, "playwright"),
);
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL,
});
const errors = [];
let page;
try {
  for (const role of ["driver", "warehouse"]) {
    console.log(`Verifying putaway QR role: ${role}`);
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    page = await context.newPage();
    page.on("pageerror", (error) => errors.push(error.message));
    await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
    await page
      .getByPlaceholder("账号", { exact: true })
      .fill(accounts[role].username);
    await page
      .getByPlaceholder("密码", { exact: true })
      .fill(accounts[role].password);
    await page.getByRole("button", { name: "登录", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.includes("/login"));
    if (role === "driver") {
      await page.goto(
        `${baseUrl}/rd/stock/driver-putaway?taskId=${fixture.taskId}&packageCode=${representativeCode}`,
        { waitUntil: "networkidle" },
      );
      await page.getByRole("heading", { name: "执行上架任务" }).waitFor();
      assert.equal(
        await page.getByPlaceholder("扫描二维码或输入包码").count(),
        0,
      );
      assert.equal(
        await page.getByRole("heading", { name: "扫码校验" }).count(),
        0,
      );
      assert.equal(
        await page.getByRole("button", { name: /确认入库|确认上架/ }).count(),
        0,
      );
      for (const width of [390, 320]) {
        await page.setViewportSize({ width, height: 844 });
        assert.ok(
          await page.evaluate(
            () => document.documentElement.scrollWidth <= innerWidth,
          ),
        );
        await page.screenshot({
          path: path.join(output, `driver-view-${width}.png`),
          fullPage: true,
          animations: "disabled",
        });
      }
    } else {
      await page.goto(
        `${baseUrl}/rd/stock/putaway?mode=scan&taskId=${fixture.taskId}&packageCode=${representativeCode}`,
        { waitUntil: "networkidle" },
      );
      await page.getByRole("tab", { name: "扫码核验", exact: true }).waitFor();
      await page
        .getByText(detail.task.taskNo, { exact: true })
        .first()
        .waitFor();
      await page.screenshot({
        path: path.join(output, "warehouse-before-final-check.png"),
        fullPage: true,
        animations: "disabled",
      });
      if (!uiOnly) {
        await page
          .getByRole("button", { name: "核验所选已到位包件", exact: true })
          .click();
        await page
          .getByRole("button", { name: "确定", exact: true })
          .last()
          .click();
        await page
          .getByRole("button", { name: "确认库位并入库", exact: true })
          .waitFor({ state: "visible" });
        await page.waitForFunction(() => {
          const button = [...document.querySelectorAll("button")].find(
            (element) => element.textContent.includes("确认库位并入库"),
          );
          return button && !button.disabled;
        });
        assert.deepEqual(await records(), [3, 2]);
        assert.deepEqual(await summary(), initial);
        await page
          .getByRole("button", { name: "确认库位并入库", exact: true })
          .click();
        const dialog = page.getByRole("dialog", { name: "确认最终库位并入库" });
        await dialog.waitFor();
        await dialog
          .getByRole("button", { name: "确认入库", exact: true })
          .click();
        await dialog.waitFor({ state: "hidden" });
        const finalPackages = await api(
          `/rd/package-unit/trace?productionOutputId=${fixture.outputId}`,
          tokens.warehouse,
        );
        assert.ok(
          finalPackages.every(
            (pkg) =>
              pkg.status === "IN_STOCK" &&
              pkg.locationId === fixture.locationId,
          ),
        );
        detail = await api(taskUrl, tokens.warehouse);
        assert.equal(detail.task.status, "PUTAWAY_COMPLETED");
        const transactionCount = Number(
          await sql(
            `SELECT COUNT(*) FROM rd_stock_transaction WHERE deleted=0 AND package_id IN (${finalPackages.map((pkg) => pkg.id).join(",")})`,
          ),
        );
        assert.equal(transactionCount, 3);
        await page.screenshot({
          path: path.join(output, "warehouse-completed.png"),
          fullPage: true,
          animations: "disabled",
        });
      }
    }
    await context.close();
    page = undefined;
  }
  assert.deepEqual(errors, []);
  await fs.writeFile(
    path.join(output, uiOnly ? "ui-result.json" : "result.json"),
    JSON.stringify(
      {
        result: "PASS",
        uiOnly,
        outputId: fixture.outputId,
        taskId: fixture.taskId,
        taskStatus: detail.task.status,
        packageCodes: packages.map((pkg) => pkg.packageCode),
        records: await records(),
        driverHasNoScanOrStockConfirmation: true,
      },
      null,
      2,
    ),
  );
  console.log(
    JSON.stringify({ result: "PASS", uiOnly, taskId: fixture.taskId, output }),
  );
} catch (error) {
  if (page && !page.url().includes("/login")) {
    await page.screenshot({
      path: path.join(output, "failure.png"),
      fullPage: true,
      animations: "disabled",
    });
    await fs.writeFile(
      path.join(output, "failure.txt"),
      await page.locator("body").innerText(),
    );
  }
  throw error;
} finally {
  await browser.close();
}
