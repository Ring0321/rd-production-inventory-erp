import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { createRequire } from "node:module";
import {
  prepareWarehouse,
  api,
  request,
  root,
  baseUrl,
} from "./RdWarehouseLocalFixture.mjs";

const fixture = await prepareWarehouse({ extraCount: 5, lowerCount: 5 });
const { tokens, accounts, extra, lower, createLoading, sql } = fixture;
const output = path.join(root, "temp/qr-loading-replacements-verification");
await fs.mkdir(output, { recursive: true });
const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
const contexts = [];
const errors = [];
const mutations = [];
const checks = [];
const screens = [];
const tasks = [];
const trigger = `c5_swap_${fixture.taskId}`;
let triggerCreated = false;

const getTask = (id) => api(`/rd/loading-task/get?id=${id}`, tokens.warehouse);
const report = async (detail) => {
  await api("/rd/loading-task/exception", tokens.driver, "POST", {
    taskId: detail.task.id,
    exceptionType: "WRONG_PACKAGE",
    description: "C5整任务换货验证，非现场数据",
  });
  return getTask(detail.task.id);
};
const fingerprint = async () =>
  JSON.stringify([
    await fixture.fingerprint(),
    ...(await Promise.all(
      [
        "rd_loading_task",
        "rd_loading_task_line",
        "rd_loading_exception",
        "rd_loading_package_claim",
        "rd_loading_scan_record",
      ].map((table) =>
        sql(
          `SELECT * FROM ${table} WHERE ${table === "rd_loading_task" ? "id" : "task_id"} IN (${tasks.join(",")}) ORDER BY id`,
        ),
      ),
    )),
    await sql(
      `SELECT * FROM rd_package_task_event WHERE source_type IN ('LOADING_TASK','LOADING_EXCEPTION') AND package_id IN (${[...extra.bags, ...lower.bags].map((bag) => bag.id).join(",")}) ORDER BY id`,
    ),
  ]);
const signIn = async (account, url, width = 390) => {
  const context = await browser.newContext({
    viewport: { width, height: 1000 },
  });
  contexts.push(context);
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (req) => {
    if (
      /\/loading-task\/(exception\/resolve|batch-scan\/confirm|confirm-outbound)$/.test(
        new URL(req.url()).pathname,
      )
    )
      mutations.push({
        path: new URL(req.url()).pathname,
        body: req.postDataJSON(),
      });
  });
  await page.goto(`${baseUrl}${url}`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname !== "/login");
  return page;
};
const selectLine = async (page, index, line) => {
  const label = `第 ${index} 行原任务包`;
  await page
    .locator(".el-select")
    .filter({ has: page.getByLabel(label, { exact: true }) })
    .locator(".el-select__wrapper")
    .click();
  const listId = await page
    .getByRole("combobox", { name: label, exact: true })
    .getAttribute("aria-controls");
  assert.ok(
    listId,
    "The original-package picker must identify its own listbox",
  );
  await page
    .locator(`[id="${listId}"]`)
    .getByRole("option", {
      name: `${line.packageCode} · ${line.plannedLocationCode}`,
      exact: true,
    })
    .click();
  await page.locator(`[id="${listId}"]`).waitFor({ state: "hidden" });
};
const approve = async (page) => {
  const dialog = page.getByRole("dialog").last();
  await dialog.waitFor();
  await dialog.getByRole("button", { name: "确定", exact: true }).click();
};
const screen = async (page, name, width) => {
  await page.setViewportSize({ width, height: 1000 });
  const dimensions = await page.evaluate(() => ({
    scroll: document.documentElement.scrollWidth,
    client: document.documentElement.clientWidth,
  }));
  assert.ok(dimensions.scroll <= dimensions.client, name);
  if (name.startsWith("web-")) {
    const box = await page
      .getByRole("dialog")
      .filter({ hasText: "处理装车异常" })
      .boundingBox();
    assert.ok(
      box && box.y >= 0 && box.y + box.height <= 1000,
      `${name}: modal header/footer must fit`,
    );
  }
  await page.screenshot({
    path: path.join(output, `${name}.png`),
    fullPage: true,
  });
  screens.push(name);
};
const fillPairs = async (page, detail, newBags) => {
  await selectLine(page, 1, detail.lines[0]);
  await page
    .getByLabel("第 1 行替换包号或二维码", { exact: true })
    .fill(newBags[0].qrCode);
  await page.getByRole("button", { name: "增加替换包", exact: true }).click();
  await selectLine(page, 2, detail.lines[1]);
  await page
    .getByLabel("第 2 行替换包号或二维码", { exact: true })
    .fill(newBags[1].qrCode);
};
const command = (detail, bags) => ({
  taskId: detail.task.id,
  version: detail.task.version,
  resolutionAction: "REPLACE_PACKAGE",
  handlingResult: "仓管核定整份换货清单，本地非现场数据",
  replacements: bags.map((bag, index) => ({
    taskLineId: detail.lines[index].id,
    newPackageCode: bag.qrCode,
  })),
});

try {
  const exactPackage = await api(
    `/rd/package-unit/get?id=${extra.bags[3].id}`,
    tokens.warehouse,
  );
  const exactQuery = new URLSearchParams({
    pageNo: "1",
    pageSize: "100",
    packageCode: exactPackage.packageCode,
    keyword: exactPackage.locationCode,
  });
  const exactPage = await api(
    `/rd/loading-task/available-package-page?${exactQuery}`,
    tokens.warehouse,
  );
  assert.equal(exactPage.total, 1);
  assert.equal(exactPage.list[0].packageCode, exactPackage.packageCode);
  checks.push(
    "exact-package-filter-is-not-truncated-by-broad-location-matches",
  );
  let detail = await createLoading(
    [extra.bags[0], extra.bags[1], lower.bags[0]],
    "C5-MULTI-PDA",
  );
  tasks.push(detail.task.id);
  await api("/rd/loading-task/batch-scan/confirm", tokens.warehouse, "POST", {
    taskId: detail.task.id,
    version: detail.task.version,
    representativePackageCode: lower.bags[0].packageCode,
    taskLineIds: [detail.lines[2].id],
    idempotencyKey: randomUUID(),
    deviceCode: "C5-LOCAL-NOT-FIELD",
  });
  detail = await report(await getTask(detail.task.id));
  const pairs = [extra.bags[3], extra.bags[4]];
  const input = command(detail, pairs);
  let before = await fingerprint();
  const bad = structuredClone(input);
  bad.replacements[1].newPackageCode = lower.bags[1].packageCode;
  assert.notEqual(
    (
      await request(
        "/rd/loading-task/exception/resolve",
        tokens.warehouse,
        "PUT",
        bad,
      )
    ).code,
    0,
  );
  assert.equal(await fingerprint(), before);
  checks.push("second-pair-mismatch-rejects-whole-plan-without-writes");

  for (const token of [tokens.driver, tokens.sales]) {
    assert.equal(
      (await request("/rd/loading-task/exception/resolve", token, "PUT", input))
        .code,
      403,
    );
    assert.equal(await fingerprint(), before);
  }
  checks.push("driver-and-sales-cannot-replace-packages");

  const fulfillmentId = detail.lines[0].fulfillmentId;
  await sql(
    `UPDATE rd_order_fulfillment SET allocated_qty=1 WHERE id=${fulfillmentId}`,
  );
  try {
    before = await fingerprint();
    assert.equal(
      (
        await request(
          "/rd/loading-task/exception/resolve",
          tokens.warehouse,
          "PUT",
          input,
        )
      ).code,
      1080530010,
    );
    assert.equal(await fingerprint(), before);
  } finally {
    await sql(
      `UPDATE rd_order_fulfillment SET allocated_qty=2 WHERE id=${fulfillmentId}`,
    );
  }
  checks.push("real-ledger-aggregate-fulfillment-shortage-rejected");

  before = await fingerprint();
  await sql(
    `CREATE TRIGGER ${trigger} BEFORE INSERT ON rd_loading_package_claim FOR EACH ROW SET NEW.package_id=IF(NEW.package_id=${pairs[1].id},NULL,NEW.package_id)`,
  );
  triggerCreated = true;
  assert.notEqual(
    (
      await request(
        "/rd/loading-task/exception/resolve",
        tokens.warehouse,
        "PUT",
        input,
      )
    ).code,
    0,
  );
  assert.equal(await fingerprint(), before);
  await sql(`DROP TRIGGER ${trigger}`);
  triggerCreated = false;
  checks.push(
    "actual-second-claim-failure-rolls-back-exceptions-claims-lines-history-and-versions",
  );

  const page = await signIn(
    accounts.warehouse,
    `/pda/warehouse?section=loading&taskId=${detail.task.id}`,
  );
  await page
    .getByRole("heading", { name: detail.task.taskNo, exact: true })
    .waitFor();
  await page
    .locator(".el-select")
    .filter({ has: page.getByLabel("装车异常处理方式", { exact: true }) })
    .locator(".el-select__wrapper")
    .click();
  await page.getByRole("option", { name: "替换包后继续", exact: true }).click();
  await fillPairs(page, detail, pairs);
  await page
    .getByLabel("装车异常处理说明", { exact: true })
    .fill(input.handlingResult);
  await page
    .getByLabel("第 2 行替换包号或二维码", { exact: true })
    .fill(pairs[0].qrCode);
  before = await fingerprint();
  await page.getByRole("button", { name: "核对替换货", exact: true }).click();
  await page.getByText("替换包不能重复使用", { exact: true }).waitFor();
  assert.equal(await fingerprint(), before);
  assert.equal(mutations.length, 0);
  checks.push("pda-duplicate-new-bag-never-submits");
  await page
    .getByLabel("第 2 行替换包号或二维码", { exact: true })
    .fill(pairs[1].qrCode);
  await page.getByRole("button", { name: "核对替换货", exact: true }).click();
  await page
    .getByText("已核对 2 包 · 原重 2000 kg · 替换重 2000 kg", { exact: true })
    .waitFor();
  await screen(page, "pda-multi-replacement-320", 320);
  await page
    .getByRole("button", { name: "核对替换货", exact: true })
    .scrollIntoViewIfNeeded();
  await screen(page, "pda-multi-replacement-actions-320", 320);
  await screen(page, "pda-multi-replacement-390", 390);
  await page
    .getByRole("button", { name: "确认处理装车异常", exact: true })
    .click();
  await approve(page);
  await page.getByText("装车异常已处理", { exact: true }).waitFor();
  const replaced = await getTask(detail.task.id);
  assert.equal(replaced.task.status, "ASSIGNED");
  assert.equal(replaced.task.version, detail.task.version + 1);
  assert.equal(replaced.task.planVersion, detail.task.planVersion + 1);
  assert.equal(replaced.task.scannedPackageCount, 1);
  assert.equal(replaced.lines[2].lineStatus, "SCANNED");
  for (let index = 0; index < 2; index++) {
    assert.equal(replaced.lines[index].id, detail.lines[index].id);
    assert.equal(replaced.lines[index].packageCode, pairs[index].packageCode);
    const old = await api(
      `/rd/package-unit/get?id=${extra.bags[index].id}`,
      tokens.warehouse,
    );
    assert.equal(old.status, "IN_STOCK");
    assert.equal(old.locationCode, detail.lines[index].plannedLocationCode);
    assert.equal(old.qrCode, extra.bags[index].qrCode);
  }
  assert.equal(
    (
      await sql(
        `SELECT COUNT(*) FROM rd_loading_package_claim WHERE task_id=${detail.task.id}`,
      )
    )[0],
    "3",
  );
  assert.equal(
    (
      await sql(
        `SELECT COUNT(*) FROM rd_loading_task WHERE pickup_slip_id=${detail.task.pickupSlipId}`,
      )
    )[0],
    "1",
  );
  assert.equal(
    (
      await sql(
        `SELECT COUNT(*) FROM rd_package_task_event WHERE source_type='LOADING_TASK' AND source_id=${detail.task.id} AND event_code IN ('REPLACED_IN','REPLACED_OUT')`,
      )
    )[0],
    "4",
  );
  checks.push(
    "pda-two-pairs-one-task-old-bags-stay-in-stock-and-four-linked-history-events",
  );
  const driver = await signIn(
    accounts.driver,
    `/pda/driver-loading?taskId=${detail.task.id}`,
  );
  await driver.getByText(replaced.task.taskNo, { exact: true }).waitFor();
  for (const bag of pairs)
    await driver.getByText(bag.packageCode, { exact: true }).first().waitFor();
  assert.equal(
    await driver.getByRole("textbox", { name: /扫码|二维码|更新库位/ }).count(),
    0,
  );
  await screen(driver, "driver-whole-adjusted-task-390", 390);
  checks.push("driver-sees-updated-whole-task-without-scan-or-location-input");

  assert.equal(
    await page
      .getByLabel("仓管出库办理", { exact: true })
      .getByPlaceholder("扫描吨袋二维码", { exact: true })
      .inputValue(),
    pairs[0].packageCode,
  );
  await page.getByRole("button", { name: "选择待装车包", exact: true }).click();
  await page
    .getByRole("button", { name: "核验所选已装车包", exact: true })
    .click();
  await approve(page);
  await page
    .getByRole("button", { name: "确认最终出库", exact: true })
    .waitFor();
  await page.getByRole("button", { name: "确认最终出库", exact: true }).click();
  await approve(page);
  await page.getByText("出库已确认", { exact: true }).waitFor();
  const outbound = await getTask(detail.task.id);
  assert.equal(outbound.task.status, "OUTBOUNDED");
  assert.equal(outbound.task.scannedPackageCount, 3);
  assert.equal(outbound.task.scannedWeight, 2800);
  assert.equal(
    new Set(outbound.lines.map((line) => line.outboundOrderId)).size,
    2,
  );
  for (const bag of [...pairs, lower.bags[0]])
    assert.equal(
      (await api(`/rd/package-unit/get?id=${bag.id}`, tokens.warehouse)).status,
      "OUTBOUNDED",
    );
  for (const bag of extra.bags.slice(0, 2))
    assert.equal(
      (await api(`/rd/package-unit/get?id=${bag.id}`, tokens.warehouse)).status,
      "IN_STOCK",
    );
  await screen(page, "pda-multi-source-outbound-390", 390);
  checks.push(
    "representative-code-after-replacement-posts-only-three-actual-bags-2800kg-two-output-orders",
  );

  let webTask = await createLoading(lower.bags.slice(1, 3), "C5-MULTI-WEB");
  tasks.push(webTask.task.id);
  webTask = await report(webTask);
  const web = await signIn(
    accounts.warehouse,
    `/rd/stock/loading?taskId=${webTask.task.id}&mode=view`,
    1440,
  );
  await web.getByRole("button", { name: "替换包后继续", exact: true }).click();
  await fillPairs(web, webTask, lower.bags.slice(3, 5));
  await web
    .getByRole("dialog")
    .last()
    .getByRole("textbox", { name: "装车异常处理说明", exact: true })
    .fill("Web整批核定，非现场数据");
  await web.getByRole("button", { name: "核对替换货", exact: true }).click();
  await web
    .getByText("已核对 2 包 · 原重 1600 kg · 替换重 1600 kg", { exact: true })
    .waitFor();
  await screen(web, "web-multi-replacement-1440", 1440);
  let races;
  await web.route("**/rd/loading-task/exception/resolve", async (route) => {
    const duplicate = request(
      "/rd/loading-task/exception/resolve",
      tokens.warehouse,
      "PUT",
      route.request().postDataJSON(),
    );
    const [first, second] = await Promise.all([route.fetch(), duplicate]);
    races = [(await first.json()).code, second.code];
    await route.abort("failed");
  });
  const aborted = web.waitForEvent("requestfailed", {
    predicate: (req) =>
      new URL(req.url()).pathname.endsWith(
        "/rd/loading-task/exception/resolve",
      ),
    timeout: 30000,
  });
  await web
    .getByRole("dialog")
    .last()
    .getByRole("button", { name: "确认处理", exact: true })
    .click();
  await approve(web);
  await aborted;
  const confirmButton = web
    .getByRole("dialog")
    .filter({ hasText: "处理装车异常" })
    .getByRole("button", { name: "确认处理", exact: true });
  await confirmButton.waitFor();
  await web.waitForFunction(() =>
    [...document.querySelectorAll("button")].some(
      (item) =>
        item.textContent.trim() === "确认处理" &&
        item.disabled &&
        !item.classList.contains("is-loading"),
    ),
  );
  assert.deepEqual(
    races.slice().sort((a, b) => a - b),
    [0, 1080530014],
  );
  assert.equal(
    mutations.filter(
      (item) =>
        item.path.endsWith("/exception/resolve") &&
        item.body.taskId === webTask.task.id,
    ).length,
    1,
  );
  await web
    .getByRole("dialog")
    .filter({ hasText: "核定装车异常" })
    .waitFor({ state: "hidden" });
  const actualWeb = await getTask(webTask.task.id);
  await web.getByText("处理装车异常 · 结果待核", { exact: true }).waitFor();
  const pendingTitle = await web
    .getByText("处理装车异常 · 结果待核", { exact: true })
    .boundingBox();
  assert.ok(
    pendingTitle &&
      pendingTitle.y >= 0 &&
      pendingTitle.y + pendingTitle.height <= 1000,
  );
  await web.getByText("装车异常处理结果待核", { exact: true }).waitFor();
  assert.equal(actualWeb.task.status, "ASSIGNED");
  assert.deepEqual(
    actualWeb.lines.map((line) => line.packageCode),
    lower.bags.slice(3, 5).map((bag) => bag.packageCode),
  );
  assert.equal(actualWeb.task.version, webTask.task.version + 1);
  assert.equal(
    (
      await sql(
        `SELECT COUNT(*) FROM rd_package_task_event WHERE source_type='LOADING_TASK' AND source_id=${webTask.task.id} AND event_code IN ('REPLACED_IN','REPLACED_OUT')`,
      )
    )[0],
    "4",
  );
  await screen(web, "web-lost-response-awaiting-verification-1440", 1440);
  checks.push(
    "web-real-multi-pair-race-only-one-version-applies-lost-response-never-retries",
  );
  await web.unroute("**/rd/loading-task/exception/resolve");
  await web.goto(
    `${baseUrl}/rd/stock/loading?taskId=${webTask.task.id}&mode=view`,
    { waitUntil: "networkidle" },
  );
  for (const bag of lower.bags.slice(3, 5))
    await web.getByText(bag.packageCode, { exact: true }).first().waitFor();
  checks.push("web-explicit-reread-restores-real-adjusted-task");
  assert.deepEqual(errors, []);
  const result = {
    result: "PASS",
    marker: "C5批量换货本地验证（非现场）",
    productionTaskId: fixture.taskId,
    taskIds: tasks,
    checks,
    screenshots: screens,
    mutationRequests: mutations.map((item) => ({
      path: item.path,
      taskId: item.body.taskId,
      replacementCount: item.body.replacements?.length,
    })),
    pageErrors: errors,
    fieldAcceptance: false,
  };
  await fs.writeFile(
    path.join(output, "result.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result));
} finally {
  if (triggerCreated) await sql(`DROP TRIGGER ${trigger}`);
  for (const context of contexts) await context.close();
  await browser.close();
}
