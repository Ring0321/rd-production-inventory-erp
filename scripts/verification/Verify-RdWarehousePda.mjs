import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import {
  prepareWarehouse,
  api,
  request,
  root,
  output,
  baseUrl,
} from "./RdWarehouseLocalFixture.mjs";

const fixture = await prepareWarehouse();
const {
  tokens,
  accounts,
  taskId,
  upper,
  extra,
  lower,
  locationIds,
  ready,
  createLoading,
  fingerprint,
  sql,
} = fixture;
const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
const pageErrors = [];
const screens = [];
const commands = [];
const mutationPaths = new Set([
  "/admin-api/rd/putaway-task/batch-scan/confirm",
  "/admin-api/rd/putaway-task/confirm",
  "/admin-api/rd/putaway-task/reassign",
  "/admin-api/rd/loading-task/batch-scan/confirm",
  "/admin-api/rd/loading-task/confirm-outbound",
  "/admin-api/rd/loading-task/exception/resolve",
]);
const contexts = [];
const phases = {};
const signIn = async (account, suffix = "") => {
  const context = await browser.newContext({
    viewport: { width: 390, height: 900 },
  });
  contexts.push(context);
  const page = await context.newPage();
  page.on("pageerror", (error) => pageErrors.push(error.message));
  page.on("request", (req) => {
    const pathname = new URL(req.url()).pathname;
    if (mutationPaths.has(pathname))
      commands.push({ path: pathname, body: req.postDataJSON() });
  });
  await page.goto(`${baseUrl}/pda/warehouse${suffix}`, {
    waitUntil: "networkidle",
  });
  await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname === "/pda/warehouse");
  await page
    .getByRole("heading", { name: "仓管工作台", exact: true })
    .waitFor();
  assert.equal(await page.locator(".v-layout").count(), 0);
  return { page, context };
};
const confirmDialog = async (page) => {
  const dialog = page.getByRole("dialog");
  await dialog.waitFor();
  await dialog.getByRole("button", { name: "确定", exact: true }).click();
};
const openSelect = (page, label) =>
  page
    .locator(".el-select")
    .filter({ has: page.getByLabel(label, { exact: true }) })
    .locator(".el-select__wrapper")
    .click();
const select = async (page, code) => {
  const checkbox = page.getByRole("checkbox", {
    name: `选择包件 ${code}`,
    exact: true,
  });
  const label = page.locator("label.el-checkbox").filter({ has: checkbox });
  if (!(await checkbox.isChecked())) await label.click();
  assert.equal(await checkbox.isChecked(), true);
};
const screen = async (page, name) => {
  await page.mouse.move(0, 0);
  await page.getByRole("dialog").waitFor({ state: "hidden" });
  await page.waitForFunction(() =>
    [...document.querySelectorAll('[role="tooltip"]')].every(
      (node) => !node.getClientRects().length,
    ),
  );
  const width = await page.evaluate(() => ({
    client: document.documentElement.clientWidth,
    scroll: document.documentElement.scrollWidth,
    inner: document.querySelector(".pda-content")?.clientWidth,
    innerScroll: document.querySelector(".pda-content")?.scrollWidth,
  }));
  assert.ok(
    width.scroll <= width.client &&
      (width.innerScroll ?? 0) <= (width.inner ?? width.client),
    JSON.stringify(width),
  );
  await page.screenshot({
    path: path.join(output, `${name}.png`),
    fullPage: true,
  });
  screens.push(name);
};
const getPutaway = () =>
  api(`/rd/putaway-task/get?id=${ready.task.id}`, tokens.warehouse);
const getLocation = async (id) =>
  (await api("/rd/putaway-task/location-map", tokens.warehouse)).find(
    (row) => row.id === id,
  );
const awaitState = async (url, status) => {
  for (let attempt = 0; attempt < 40; attempt++) {
    const detail = await api(url, tokens.warehouse);
    if (detail.task.status === status) return detail;
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  assert.fail(`Expected ${status} at ${url}`);
};

try {
  const before = await fingerprint();
  const actor = await signIn(
    accounts.warehouse,
    `?section=putaway&taskId=${ready.task.id}`,
  );
  const page = actor.page;
  await page
    .locator(".putaway-panel")
    .getByRole("heading", { name: ready.task.taskNo, exact: true })
    .waitFor();
  await page
    .getByLabel("包号 / 二维码", { exact: true })
    .first()
    .fill(upper.bags[0].qrCode);
  await page.getByRole("button", { name: "定位上架任务", exact: true }).click();
  await page
    .getByRole("checkbox", {
      name: `选择包件 ${upper.bags[0].packageCode}`,
      exact: true,
    })
    .waitFor({ state: "attached" });
  assert.equal(
    await page
      .getByRole("checkbox", {
        name: `选择包件 ${upper.bags[0].packageCode}`,
        exact: true,
      })
      .isChecked(),
    false,
  );
  assert.equal(commands.length, 0);
  assert.equal(await fingerprint(), before);
  await screen(page, "warehouse-putaway-readonly-390");

  const mapPage = await actor.context.newPage();
  mapPage.on("pageerror", (error) => pageErrors.push(error.message));
  await mapPage.goto(`${baseUrl}/pkg/${upper.bags[0].packageCode}`, {
    waitUntil: "networkidle",
  });
  await mapPage
    .getByRole("button", { name: "入库扫码核验", exact: true })
    .click();
  await mapPage.getByRole("tab", { name: "库位与派单", exact: true }).click();
  await mapPage.getByText(`D4-${taskId}-A`, { exact: true }).first().waitFor();
  const mapResponses = [];
  mapPage.on("response", async (response) => {
    if (
      new URL(response.url()).pathname.endsWith("/putaway-task/location-map")
    ) {
      const json = await response.json().catch(() => null);
      if (json?.code === 0) mapResponses.push(json.data);
    }
  });

  await api("/rd/putaway-task/exception", tokens.driver, "POST", {
    id: ready.task.id,
    exceptionType: "LOCATION_OCCUPIED",
    description: "本地模拟计划位不可用，请仓管改派，非现场数据",
  });
  await page.bringToFront();
  await page
    .getByRole("button", { name: "重新读取上架任务", exact: true })
    .click();
  await page
    .getByRole("button", { name: "处理异常并改派", exact: true })
    .waitFor();
  await openSelect(page, "实际 / 调整库位");
  await page
    .getByRole("option", {
      name: `D4-${taskId}-A · 仓管验证仓库-${taskId}（非现场）`,
      exact: true,
    })
    .click();
  await page
    .getByLabel("异常处理说明", { exact: true })
    .fill("本地模拟仓管确认改派至 A，非现场摆放");
  await page
    .getByRole("button", { name: "处理异常并改派", exact: true })
    .click();
  await confirmDialog(page);
  await awaitState(`/rd/putaway-task/get?id=${ready.task.id}`, "ASSIGNED");
  assert.equal((await getPutaway()).task.plannedLocationId, locationIds[0]);
  assert.equal((await getLocation(locationIds[0])).reservedPackages, 3);
  assert.equal((await getLocation(locationIds[1])).reservedPackages, 0);

  const captures = [];
  let signalCommit;
  const lostResponseCommitted = new Promise((resolve) => {
    signalCommit = resolve;
  });
  await page.route(
    "**/admin-api/rd/putaway-task/batch-scan/confirm",
    async (route) => {
      captures.push(route.request().postDataJSON());
      const response = await route.fetch();
      const result = await response.json();
      signalCommit(result);
      await route.abort("failed");
    },
  );
  await select(page, upper.bags[0].packageCode);
  await select(page, upper.bags[1].packageCode);
  await page
    .getByRole("button", { name: "核验所选已到位包", exact: true })
    .click();
  await confirmDialog(page);
  await page
    .getByRole("button", { name: "重试原上架核验", exact: true })
    .waitFor();
  const committed = await lostResponseCommitted;
  assert.equal(committed.code, 0, committed.msg);
  assert.equal((await getPutaway()).task.scannedPackageCount, 2);
  await page.unroute("**/admin-api/rd/putaway-task/batch-scan/confirm");
  await page.route(
    "**/admin-api/rd/putaway-task/batch-scan/confirm",
    async (route) => {
      captures.push(route.request().postDataJSON());
      await route.continue();
    },
  );
  await page.reload({ waitUntil: "networkidle" });
  await page
    .getByRole("button", { name: "重试原上架核验", exact: true })
    .click();
  await page.getByText("原请求已核验，未重复计数", { exact: true }).waitFor();
  assert.deepEqual(captures[0], captures[1]);
  assert.equal((await getPutaway()).task.scannedPackageCount, 2);
  await screen(page, "warehouse-putaway-partial-replayed-390");
  await select(page, upper.bags[2].packageCode);
  await page
    .getByRole("button", { name: "核验所选已到位包", exact: true })
    .click();
  await confirmDialog(page);
  await page
    .getByRole("button", { name: "确认最终入库", exact: true })
    .waitFor();
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    await screen(page, `warehouse-putaway-ready-${width}`);
  }
  await page.getByRole("button", { name: "确认最终入库", exact: true }).click();
  await confirmDialog(page);
  await awaitState(
    `/rd/putaway-task/get?id=${ready.task.id}`,
    "PUTAWAY_COMPLETED",
  );
  assert.equal((await getLocation(locationIds[0])).occupiedPackages, 6);
  assert.equal((await getLocation(locationIds[0])).reservedPackages, 0);
  await mapPage.bringToFront();
  await mapPage.waitForResponse(
    async (response) => {
      if (
        !new URL(response.url()).pathname.endsWith("/putaway-task/location-map")
      )
        return false;
      const json = await response.json().catch(() => null);
      return (
        json?.code === 0 &&
        json.data.some(
          (row) =>
            row.id === locationIds[0] &&
            row.occupiedPackages === 6 &&
            row.reservedPackages === 0,
        )
      );
    },
    { timeout: 15000 },
  );
  assert.ok(
    mapResponses.some((rows) =>
      rows.some(
        (row) => row.id === locationIds[0] && row.occupiedPackages === 6,
      ),
    ),
  );
  const mapTile = mapPage
    .locator(".location-tile")
    .filter({ has: mapPage.getByText(`D4-${taskId}-A`, { exact: true }) });
  await mapTile.getByText("在库 6", { exact: true }).waitFor();
  await mapTile.getByText("预留 0", { exact: true }).waitFor();
  const ledgerPage = await actor.context.newPage();
  ledgerPage.on("pageerror", (error) => pageErrors.push(error.message));
  await ledgerPage.setViewportSize({ width: 1440, height: 1000 });
  await ledgerPage.goto(`${baseUrl}/rd/stock/lot`, {
    waitUntil: "networkidle",
  });
  await ledgerPage
    .getByPlaceholder("请输入货位", { exact: true })
    .fill(`D4-${taskId}-A`);
  await ledgerPage.getByRole("button", { name: "搜索", exact: true }).click();
  const stockRow = ledgerPage
    .locator(".el-table__body tr")
    .filter({ has: ledgerPage.getByText(`D4-${taskId}-A`, { exact: true }) })
    .first();
  await stockRow
    .locator("td")
    .nth(11)
    .getByText("6", { exact: true })
    .waitFor();

  const loading = await createLoading(
    [...upper.bags, ...lower.bags.slice(0, 2)],
    "MAIN",
  );
  phases.loadingTaskId = loading.task.id;
  assert.equal(loading.task.plannedPackageCount, 5);
  assert.equal(loading.task.plannedWeight, 4600);
  assert.equal(
    new Set(loading.lines.map((line) => line.plannedLocationCode)).size,
    2,
  );
  await page.bringToFront();
  await page.goto(
    `${baseUrl}/pda/warehouse?section=loading&taskId=${loading.task.id}`,
    { waitUntil: "networkidle" },
  );
  await page
    .locator(".loading-panel")
    .getByRole("heading", { name: loading.task.taskNo, exact: true })
    .waitFor();
  const firstLine = loading.lines[0];
  await select(page, firstLine.packageCode);
  await page
    .getByRole("button", { name: "核验所选已装车包", exact: true })
    .click();
  await confirmDialog(page);
  await page
    .locator(".loading-panel")
    .getByText("已核验 1 包", { exact: true })
    .waitFor();
  let loaded = await api(
    `/rd/loading-task/get?id=${loading.task.id}`,
    tokens.warehouse,
  );
  assert.equal(loaded.task.scannedPackageCount, 1);
  assert.equal((await getLocation(locationIds[0])).occupiedPackages, 6);
  for (const line of loaded.lines.filter(
    (line) => line.lineStatus === "PLANNED",
  ))
    await select(page, line.packageCode);
  await page
    .getByRole("button", { name: "核验所选已装车包", exact: true })
    .click();
  await confirmDialog(page);
  await page
    .getByRole("button", { name: "确认最终出库", exact: true })
    .waitFor();
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    await screen(page, `warehouse-loading-ready-${width}`);
  }
  loaded = await api(
    `/rd/loading-task/get?id=${loading.task.id}`,
    tokens.warehouse,
  );
  const outboundRequest = {
    taskId: loading.task.id,
    version: loaded.task.version,
  };
  await page.getByRole("button", { name: "确认最终出库", exact: true }).click();
  await confirmDialog(page);
  await awaitState(`/rd/loading-task/get?id=${loading.task.id}`, "OUTBOUNDED");
  const outboundSnapshot = await sql(
    `SELECT id,version,confirm_time FROM rd_loading_task WHERE id=${loading.task.id}`,
  );
  const recovered = await api(
    "/rd/loading-task/confirm-outbound",
    tokens.warehouse,
    "POST",
    outboundRequest,
  );
  assert.equal(recovered.outboundPackageCount, 5);
  assert.equal(recovered.outboundWeight, 4600);
  assert.equal(
    await sql(
      `SELECT id,version,confirm_time FROM rd_loading_task WHERE id=${loading.task.id}`,
    ),
    outboundSnapshot,
  );
  assert.equal((await getLocation(locationIds[0])).occupiedPackages, 3);
  assert.equal((await getLocation(locationIds[1])).occupiedPackages, 1);
  const originalLoading = commands.find(
    (entry) =>
      entry.path.endsWith("/loading-task/batch-scan/confirm") &&
      entry.body.taskId === loading.task.id,
  ).body;
  const beforeReplay = await sql(
    `SELECT id,version,confirm_time,scanned_package_count,scanned_weight FROM rd_loading_task WHERE id=${loading.task.id}`,
  );
  const replayed = await api(
    "/rd/loading-task/batch-scan/confirm",
    tokens.warehouse,
    "POST",
    originalLoading,
  );
  assert.equal(replayed.replayed, true);
  assert.equal(replayed.confirmedCount, 0);
  for (const [actorToken, body] of [
    [tokens.admin, originalLoading],
    [
      tokens.warehouse,
      { ...originalLoading, version: originalLoading.version + 1 },
    ],
    [
      tokens.warehouse,
      { ...originalLoading, taskLineIds: [loading.lines[1].id] },
    ],
  ]) {
    const refused = await request(
      "/rd/loading-task/batch-scan/confirm",
      actorToken,
      "POST",
      body,
    );
    assert.equal(refused.code, 1080530023);
  }
  assert.equal(
    await sql(
      `SELECT id,version,confirm_time,scanned_package_count,scanned_weight FROM rd_loading_task WHERE id=${loading.task.id}`,
    ),
    beforeReplay,
  );
  await ledgerPage.bringToFront();
  await stockRow
    .locator("td")
    .nth(11)
    .getByText("3", { exact: true })
    .waitFor({ timeout: 15000 });
  await page.bringToFront();

  const swap = await createLoading(extra.bags.slice(0, 2), "SWAP");
  phases.swapTaskId = swap.task.id;
  await api("/rd/loading-task/exception", tokens.driver, "POST", {
    taskId: swap.task.id,
    taskLineId: swap.lines[0].id,
    packageCode: swap.lines[0].packageCode,
    exceptionType: "WRONG_PACKAGE",
    description: "本地模拟换包，非现场数据",
  });
  await page.goto(
    `${baseUrl}/pda/warehouse?section=loading&taskId=${swap.task.id}`,
    { waitUntil: "networkidle" },
  );
  await openSelect(page, "装车异常处理方式");
  await page.getByRole("option", { name: "替换包后继续", exact: true }).click();
  await openSelect(page, "原任务包");
  await page
    .getByRole("option", {
      name: `${swap.lines[0].packageCode} · ${swap.lines[0].plannedLocationCode}`,
      exact: true,
    })
    .click();
  await page
    .getByLabel("替换包号或二维码", { exact: true })
    .fill(extra.bags[2].qrCode);
  await page
    .getByLabel("装车异常处理说明", { exact: true })
    .fill("仓管本地模拟确认替代包，非现场换货");
  await page
    .getByRole("button", { name: "确认处理装车异常", exact: true })
    .click();
  await confirmDialog(page);
  const replaced = await awaitState(
    `/rd/loading-task/get?id=${swap.task.id}`,
    "ASSIGNED",
  );
  assert.equal(replaced.lines[0].packageId, extra.bags[2].id);
  assert.equal(replaced.lines[0].id, swap.lines[0].id);
  const stale = await request(
    "/rd/loading-task/batch-scan/confirm",
    tokens.warehouse,
    "POST",
    {
      taskId: swap.task.id,
      version: swap.task.version,
      representativePackageCode: swap.lines[1].packageCode,
      taskLineIds: [swap.lines[0].id],
      idempotencyKey: `D4-stale-${Date.now()}`,
    },
  );
  assert.equal(stale.code, 1080530014);
  assert.equal(
    (await api(`/rd/loading-task/get?id=${swap.task.id}`, tokens.warehouse))
      .task.scannedPackageCount,
    0,
  );
  await screen(page, "warehouse-loading-replacement-320");
  const rollbackRequest = {
    taskId: swap.task.id,
    version: replaced.task.version,
    representativePackageCode: replaced.lines[1].packageCode,
    taskLineIds: replaced.lines.map((line) => line.id),
    idempotencyKey: `D4-rollback-${taskId}`,
    deviceCode: "D4-NOT-FIELD",
  };
  const trigger = `rd_d4_loading_${taskId}`;
  const rollbackSnapshot = JSON.stringify(
    await api(`/rd/loading-task/get?id=${swap.task.id}`, tokens.warehouse),
  );
  const beforeRows = await sql(
    `SELECT COUNT(*) FROM rd_loading_scan_record WHERE task_id=${swap.task.id}`,
  );
  const rollbackPackageIds = replaced.lines.map((line) => line.packageId);
  assert.ok(
    rollbackPackageIds.every((id) => Number.isSafeInteger(id) && id > 0),
  );
  const beforeEvents = await sql(
    `SELECT COUNT(*) FROM rd_package_task_event WHERE package_id IN (${rollbackPackageIds.join(",")}) AND source_type='LOADING_SCAN'`,
  );
  await sql(
    `CREATE TRIGGER ${trigger} BEFORE INSERT ON rd_loading_scan_record FOR EACH ROW SET NEW.task_id = IF(NEW.task_id=${swap.task.id} AND NEW.idempotency_key='${rollbackRequest.idempotencyKey}', NULL, NEW.task_id)`,
  );
  try {
    const failed = await request(
      "/rd/loading-task/batch-scan/confirm",
      tokens.warehouse,
      "POST",
      rollbackRequest,
    );
    assert.notEqual(failed.code, 0);
  } finally {
    await sql(`DROP TRIGGER ${trigger}`);
  }
  assert.equal(
    JSON.stringify(
      await api(`/rd/loading-task/get?id=${swap.task.id}`, tokens.warehouse),
    ),
    rollbackSnapshot,
  );
  assert.equal(
    await sql(
      `SELECT COUNT(*) FROM rd_loading_scan_record WHERE task_id=${swap.task.id}`,
    ),
    beforeRows,
  );
  assert.equal(
    await sql(
      `SELECT COUNT(*) FROM rd_package_task_event WHERE package_id IN (${rollbackPackageIds.join(",")}) AND source_type='LOADING_SCAN'`,
    ),
    beforeEvents,
  );
  await api(
    "/rd/loading-task/batch-scan/confirm",
    tokens.warehouse,
    "POST",
    rollbackRequest,
  );
  const healthyRetry = await api(
    "/rd/loading-task/batch-scan/confirm",
    tokens.warehouse,
    "POST",
    rollbackRequest,
  );
  assert.equal(healthyRetry.replayed, true);
  assert.equal(
    Number(
      await sql(
        `SELECT COUNT(*) FROM information_schema.triggers WHERE trigger_schema=DATABASE() AND trigger_name='${trigger}'`,
      ),
    ),
    0,
  );

  const cancel = await createLoading([lower.bags[2]], "CANCEL");
  phases.cancelTaskId = cancel.task.id;
  await api("/rd/loading-task/exception", tokens.driver, "POST", {
    taskId: cancel.task.id,
    exceptionType: "OTHER",
    description: "本地模拟取消提货，非现场数据",
  });
  await page.goto(
    `${baseUrl}/pda/warehouse?section=loading&taskId=${cancel.task.id}`,
    { waitUntil: "networkidle" },
  );
  await openSelect(page, "装车异常处理方式");
  await page.getByRole("option", { name: "取消任务", exact: true }).click();
  await page
    .getByLabel("装车异常处理说明", { exact: true })
    .fill("仓管本地取消，保持货物在库，非现场数据");
  await page
    .getByRole("button", { name: "确认处理装车异常", exact: true })
    .click();
  await confirmDialog(page);
  await awaitState(`/rd/loading-task/get?id=${cancel.task.id}`, "CANCELLED");
  const last = await api(
    `/rd/package-unit/get?id=${lower.bags[2].id}`,
    tokens.warehouse,
  );
  assert.equal(last.status, "IN_STOCK");

  for (const name of ["driver", "quality", "sales"]) {
    const restricted = await signIn(accounts[name]);
    assert.equal(
      await restricted.page
        .getByRole("button", {
          name: /核验所选|确认最终|处理异常并改派|确认处理装车异常/,
        })
        .count(),
      0,
    );
    await restricted.context.close();
  }
  const result = {
    result: "PASS",
    fieldAcceptance: false,
    taskId,
    putawayTaskId: ready.task.id,
    loadingTaskId: loading.task.id,
    swapTaskId: swap.task.id,
    cancelTaskId: cancel.task.id,
    packageCount: 9,
    mainOutboundCount: 5,
    mainOutboundWeight: 4600,
    readonlyPreview: true,
    putawayReassignByKeeper: true,
    actualPartialSelection: true,
    realLostResponseAndReloadRetry: true,
    retainedPerPackageIdentity: true,
    webLocationAutoSync: true,
    webLedgerAutoSync: true,
    finalOutboundRecovery: true,
    staleReplacementSelectionDenied: true,
    loadingReplayBoundToActorAndContent: true,
    mysqlBatchRecordFailureRollback: true,
    sameRequestHealthyRetry: true,
    noTestTriggerLeft: true,
    roleBoundary: true,
    pageErrors,
    screenshots: screens,
  };
  assert.deepEqual(pageErrors, []);
  await fs.writeFile(
    path.join(output, "pda-result.json"),
    JSON.stringify(result, null, 2),
  );
  await fs.writeFile(
    path.join(output, "commands.json"),
    JSON.stringify(commands, null, 2),
  );
  process.stdout.write(`${JSON.stringify(result)}\n`);
} catch (error) {
  await fs.writeFile(
    path.join(output, "failure-context.json"),
    JSON.stringify(
      { taskId, putawayTaskId: ready.task.id, phases, commands },
      null,
      2,
    ),
  );
  for (const [index, context] of contexts.entries())
    for (const [pageIndex, page] of context.pages().entries()) {
      await page
        .screenshot({
          path: path.join(output, `failure-${index}-${pageIndex}.png`),
          fullPage: true,
        })
        .catch(() => {});
      const visible = await page
        .locator(".warehouse-pda")
        .evaluate((node) => node.outerHTML)
        .catch(() => "");
      if (visible)
        await fs.writeFile(
          path.join(output, `failure-${index}-${pageIndex}.html`),
          visible,
        );
    }
  throw error;
} finally {
  await browser.close();
}
