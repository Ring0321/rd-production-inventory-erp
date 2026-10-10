import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import {
  root,
  baseUrl,
  api,
  request,
  prepareFixture,
  readFlowCounts,
} from "./RdQrLocalFixture.mjs";

const requireFrontend = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = process.env.RD_BROWSER_MODULES
  ? requireFrontend(path.join(process.env.RD_BROWSER_MODULES, "playwright"))
  : requireFrontend("playwright");
const { fixture, tokens, accounts } = await prepareFixture();
const output = path.join(root, "temp/qr-e4-role-verification");
await fs.mkdir(output, { recursive: true });
const pkg = fixture.loadingPackages[0];
const labelPkg = fixture.labeledPackages[0];
const fingerprint = async () => {
  const packages = await api(
    `/rd/package-unit/trace?productionOutputId=${fixture.loadingOutputId}`,
    tokens.admin,
  );
  const detail = await api(
    `/rd/loading-task/get?id=${fixture.loadingTaskId}`,
    tokens.admin,
  );
  const lots = await Promise.all(
    [...new Set(packages.map((pkg) => pkg.stockLotId))].map((id) =>
      api(`/rd/stock-lot/get?id=${id}`, tokens.admin),
    ),
  );
  return {
    flowCounts: await readFlowCounts(
      packages.map((pkg) => pkg.id),
      fixture.loadingTaskId,
    ),
    lots: lots.map(
      ({
        id,
        warehouseQty,
        stockWeight,
        lockedQty,
        packageWeight,
        locationId,
        locationCode,
        version,
      }) => {
        assert.notEqual(warehouseQty, undefined);
        assert.notEqual(stockWeight, undefined);
        return {
          id,
          warehouseQty,
          stockWeight,
          lockedQty,
          packageWeight,
          locationId,
          locationCode,
          version,
        };
      },
    ),
    packages: packages.map(
      ({ id, status, version, stockLotId, locationId, locationCode }) => ({
        id,
        status,
        version,
        stockLotId,
        locationId,
        locationCode,
      }),
    ),
    task: {
      status: detail.task.status,
      version: detail.task.version,
      scannedPackageCount: detail.task.scannedPackageCount,
    },
    lines: detail.lines.map(({ id, lineStatus, scanTime }) => ({
      id,
      lineStatus,
      scanTime,
    })),
  };
};
const before = await fingerprint();
assert.deepEqual(before.flowCounts, [fixture.loadingPackages.length, 0]);
const publicTrace = await api(
  `/rd/package-unit/public-trace/${pkg.packageCode}`,
);
for (const key of [
  "customerName",
  "locationCode",
  "productionOutputId",
  "qualityInspectionId",
]) {
  assert.equal(publicTrace[key], undefined, `Public trace leaked ${key}`);
}
assert.equal(
  (await request(`/rd/package-unit/work-context/${pkg.packageCode}`)).code,
  401,
);
const contexts = {};
for (const role of [
  "production",
  "quality",
  "warehouse",
  "driver",
  "sales",
  "otherDriver",
]) {
  contexts[role] = await api(
    `/rd/package-unit/work-context/${pkg.packageCode}`,
    tokens[role],
  );
}
assert.equal(contexts.warehouse.tasks[0].taskId, fixture.loadingTaskId);
assert.equal(contexts.driver.tasks[0].taskId, fixture.loadingTaskId);
assert.ok(contexts.driver.tasks[0].myTask);
assert.deepEqual(contexts.otherDriver.tasks, []);
assert.deepEqual(contexts.sales.tasks, []);
assert.equal(contexts.sales.pickupSlipId, fixture.pickupId);
assert.equal(contexts.driver.pickupSlipId, undefined);
assert.notEqual(
  (
    await request(
      `/rd/loading-task/my-get?id=${fixture.loadingTaskId}`,
      tokens.otherDriver,
    )
  ).code,
  0,
);
assert.notEqual(
  (
    await request(
      `/rd/package-unit/trace-detail/${pkg.packageCode}`,
      tokens.sales,
    )
  ).code,
  0,
);

const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL,
});
const results = [];
const errors = [];
let currentPage;
try {
  for (const role of [
    "production",
    "quality",
    "warehouse",
    "driver",
    "sales",
    "otherDriver",
    "admin",
  ]) {
    console.log(`Verifying local QR role: ${role}`);
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1080 },
    });
    const page = await context.newPage();
    currentPage = page;
    page.on("pageerror", (error) =>
      errors.push({ role, message: error.message }),
    );
    await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
    await page
      .getByPlaceholder("账号", { exact: true })
      .fill(accounts[role].username);
    await page
      .getByPlaceholder("密码", { exact: true })
      .fill(accounts[role].password);
    await page.getByRole("button", { name: "登录", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.includes("/login"));
    await page.goto(`${baseUrl}/pkg/${pkg.packageCode}`, {
      waitUntil: "networkidle",
    });
    await page
      .getByRole("heading", { name: pkg.packageCode, exact: true })
      .waitFor();
    const internal = ["production", "quality", "warehouse", "admin"].includes(
      role,
    );
    assert.equal(
      await page.getByRole("heading", { name: "内部作业信息" }).count(),
      internal ? 1 : 0,
    );
    const actions = await page
      .locator(".action-entry-list button")
      .allTextContents();
    await page.screenshot({
      path: path.join(output, `${role}-same-package.png`),
      fullPage: true,
      animations: "disabled",
    });
    results.push({
      role,
      packageCode: pkg.packageCode,
      internal,
      actions: actions.map((value) => value.trim()),
    });
    if (role === "production") {
      await page
        .getByRole("button", { name: "补打吨袋标签", exact: true })
        .click();
      const preview = page.getByRole("dialog", { name: "货品标识卡预览" });
      await preview.waitFor();
      await preview
        .frameLocator("iframe")
        .getByText(pkg.packageCode, { exact: true })
        .waitFor();
      await page.goto(`${baseUrl}/pkg/${labelPkg.packageCode}`, {
        waitUntil: "networkidle",
      });
      await page.getByRole("button", { name: "生产记录", exact: true }).click();
      await page.waitForURL((url) => url.pathname === "/rd/production/output");
      assert.equal(
        new URL(page.url()).searchParams.get("productionOutputId"),
        String(fixture.labelOutputId),
      );
      await page.locator(".el-table__body-wrapper tbody tr").first().waitFor();
      assert.equal(
        await page.locator(".el-table__body-wrapper tbody tr").count(),
        1,
      );
    } else if (role === "quality") {
      await page.getByRole("button", { name: "质检记录", exact: true }).click();
      const detail = page.getByRole("dialog", { name: "质检详情" });
      await detail.waitFor();
      assert.equal(
        new URL(page.url()).searchParams.get("inspectionId"),
        String(fixture.inspections[pkg.id]),
      );
      assert.ok((await detail.innerText()).includes("接口验证示例"));
    } else if (role === "warehouse") {
      assert.ok(
        await page
          .getByRole("button", { name: "库存台账", exact: true })
          .isVisible(),
      );
      assert.ok(
        await page
          .getByRole("button", { name: "关联提货单", exact: true })
          .isVisible(),
      );
      await page
        .getByRole("button", { name: "出库扫码复核", exact: true })
        .click();
      await page.waitForURL((url) => url.pathname === "/rd/stock/loading");
      await page
        .getByText(fixture.loadingTaskNo, { exact: true })
        .first()
        .waitFor();
      assert.equal(
        new URL(page.url()).searchParams.get("taskId"),
        String(fixture.loadingTaskId),
      );
      await page.screenshot({
        path: path.join(output, "warehouse-linked-task.png"),
        fullPage: true,
        animations: "disabled",
      });
    } else if (role === "driver") {
      await page
        .getByRole("button", { name: "查看本人装车任务", exact: true })
        .click();
      await page.waitForURL(
        (url) => url.pathname === "/rd/stock/driver-loading",
      );
      await page.getByText(pkg.packageCode, { exact: true }).first().waitFor();
      assert.equal(
        new URL(page.url()).searchParams.get("taskId"),
        String(fixture.loadingTaskId),
      );
      assert.equal(
        await page.getByRole("button", { name: /扫码确认|确认出库/ }).count(),
        0,
      );
      await page.screenshot({
        path: path.join(output, "driver-owned-task.png"),
        fullPage: true,
        animations: "disabled",
      });
    } else if (role === "sales") {
      await page
        .getByRole("button", { name: "关联提货单", exact: true })
        .click();
      await page.waitForURL((url) => url.pathname === "/rd/order/pickup");
      await page
        .getByRole("dialog")
        .filter({ hasText: fixture.pickupSlipNo })
        .waitFor();
      assert.equal(
        new URL(page.url()).searchParams.get("pickupSlipId"),
        String(fixture.pickupId),
      );
    } else if (role === "otherDriver") {
      assert.equal(
        await page
          .getByRole("button", { name: "查看本人装车任务", exact: true })
          .count(),
        0,
      );
      assert.equal(
        await page
          .getByRole("button", { name: "查看本人上架任务", exact: true })
          .count(),
        0,
      );
    } else if (role === "admin") {
      assert.ok(
        (await page.locator(".internal-section").innerText()).includes(
          String(fixture.loadingOutputId),
        ),
      );
      await page.goto(`${baseUrl}/pkg/${labelPkg.packageCode}`, {
        waitUntil: "networkidle",
      });
      const nextPackage = fixture.labeledPackages[1].packageCode;
      let releaseResponse;
      const responseReleased = new Promise((resolve) => {
        releaseResponse = resolve;
      });
      await page.route(
        `**/rd/package-unit/trace-detail/${nextPackage}`,
        async (route) => {
          const response = await route.fetch();
          await new Promise((resolve) => setTimeout(resolve, 1000));
          await route.fulfill({ response });
          releaseResponse();
        },
      );
      await page.getByRole("link", { name: nextPackage, exact: true }).click();
      await page.goBack();
      await page
        .getByRole("heading", { name: labelPkg.packageCode, exact: true })
        .waitFor();
      await responseReleased;
      assert.equal(
        await page.locator("#package-heading").innerText(),
        labelPkg.packageCode,
      );
      for (const width of [390, 320]) {
        await page.setViewportSize({ width, height: 844 });
        assert.ok(
          await page.evaluate(
            () => document.documentElement.scrollWidth <= window.innerWidth,
          ),
        );
        await page.screenshot({
          path: path.join(output, `trace-${width}.png`),
          fullPage: true,
          animations: "disabled",
        });
      }
    }
    await context.close();
    currentPage = undefined;
  }
  const after = await fingerprint();
  assert.deepEqual(
    after,
    before,
    "QR navigation must not change package, task or scan states",
  );
  assert.deepEqual(errors, []);
  await fs.writeFile(
    path.join(output, "result.json"),
    JSON.stringify(
      {
        result: "PASS",
        roles: results,
        ownedTaskOnly: true,
        publicFieldsIsolated: true,
        stockUnchanged: true,
        rapidBackNavigation: true,
        before,
        after,
      },
      null,
      2,
    ),
  );
  console.log(
    JSON.stringify({
      result: "PASS",
      roles: results.length,
      packageCode: pkg.packageCode,
      loadingTaskId: fixture.loadingTaskId,
      stockUnchanged: true,
      output,
    }),
  );
} catch (error) {
  if (currentPage && !currentPage.url().includes("/login")) {
    await currentPage.screenshot({
      path: path.join(output, "failure.png"),
      fullPage: true,
      animations: "disabled",
    });
    await fs.writeFile(
      path.join(output, "failure.txt"),
      await currentPage.locator("body").innerText(),
    );
  }
  throw error;
} finally {
  await browser.close();
}
