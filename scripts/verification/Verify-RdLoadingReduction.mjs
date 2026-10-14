import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";
import { createRequire } from "node:module";
import { login } from "./RdQrLocalFixture.mjs";
import {
  prepareWarehouse,
  root,
  api,
  request,
  baseUrl,
} from "./RdWarehouseLocalFixture.mjs";

const fixture = await prepareWarehouse({ extraCount: 5, lowerCount: 5 });
const { tokens, accounts, extra, lower, createLoading, sql } = fixture;
const output = path.join(root, "temp/loading-reduction-verification");
await fs.mkdir(output, { recursive: true });
const prefix = "/rd/loading-task/reduction";
const getTask = (id) => api(`/rd/loading-task/get?id=${id}`, tokens.warehouse);
const checks = [],
  screenshots = [],
  errors = [],
  writes = [],
  external = [],
  contexts = [];
const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
let a, b, c;
const report = async (detail, selected) => {
  await api("/rd/loading-task/exception", tokens.driver, "POST", {
    taskId: detail.task.id,
    exceptionType: "OTHER",
    description: "C5-R1本地少装核定，不是现场提货",
    ...(selected
      ? { taskLineId: selected.id, packageCode: selected.packageCode }
      : {}),
  });
  return getTask(detail.task.id);
};
const propose = (detail, id, key = randomUUID()) => ({
  taskId: detail.task.id,
  taskVersion: detail.task.version,
  taskLineIds: [id],
  reason: "C5-R1本地真实接口验证，不是现场数据",
  idempotencyKey: key,
});
const list = (id, token = tokens.warehouse) =>
  api(`${prefix}/list?taskId=${id}`, token);
const denied = async (name, url, token, method = "GET", body, code = 403) => {
  const response = await request(url, token, method, body);
  assert.equal(
    response.code,
    code,
    `${name}: unexpected refusal ${response.code}`,
  );
};
const fingerprint = async (taskIds = [a.task.id, b.task.id, c.task.id]) => {
  const goods = await fixture.fingerprint();
  const bags = [...extra.bags, ...lower.bags].map((bag) => bag.id).join(",");
  const parts = [];
  for (const query of [
    `SELECT * FROM rd_loading_task WHERE id IN (${taskIds.join(",")}) ORDER BY id`,
    `SELECT * FROM rd_loading_task_line WHERE task_id IN (${taskIds.join(",")}) ORDER BY id`,
    `SELECT * FROM rd_loading_package_claim WHERE task_id IN (${taskIds.join(",")}) ORDER BY id`,
    `SELECT * FROM rd_pickup_slip WHERE id IN (SELECT pickup_slip_id FROM rd_loading_task WHERE id IN (${taskIds.join(",")})) ORDER BY id`,
    `SELECT * FROM rd_pickup_slip_line WHERE slip_id IN (SELECT pickup_slip_id FROM rd_loading_task WHERE id IN (${taskIds.join(",")})) ORDER BY id`,
    `SELECT * FROM rd_stock_lot WHERE id IN (SELECT stock_lot_id FROM rd_package_unit WHERE id IN (${bags})) ORDER BY id`,
    `SELECT * FROM rd_stock_transaction WHERE package_id IN (${bags}) ORDER BY id`,
    `SELECT * FROM rd_loading_scan_record WHERE task_id IN (${taskIds.join(",")}) ORDER BY id`,
  ])
    parts.push(await sql(query));
  return JSON.stringify({ goods, parts });
};
const signIn = async (account, suffix, width = 1440) => {
  const context = await browser.newContext({
    viewport: { width, height: 1000 },
  });
  contexts.push(context);
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (req) => {
    const url = new URL(req.url());
    if (
      !url.hostname.match(/^(127\.0\.0\.1|localhost)$/) &&
      url.protocol.startsWith("http")
    )
      external.push(url.origin);
    if (/\/loading-task\/reduction\/(create|review)$/.test(url.pathname))
      writes.push({ path: url.pathname, body: req.postDataJSON() });
  });
  await page.goto(`${baseUrl}${suffix}`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname !== "/login");
  return page;
};
const panel = (page) =>
  page.getByRole("region", { name: "少装申请与商务核定", exact: true });
const screen = async (page, filename, locator = panel(page)) => {
  await locator.scrollIntoViewIfNeeded();
  const element = await locator.elementHandle();
  await page.waitForFunction(
    (panel) =>
      [...panel.querySelectorAll(".record-heading .el-tag")].every(
        (tag) =>
          Number(getComputedStyle(tag).opacity) >= 0.95 &&
          tag.getBoundingClientRect().width > 30,
      ),
    element,
  );
  await page.screenshot({ path: path.join(output, filename), fullPage: true });
  const layout = await locator.evaluate((element) => {
    const rect = element.getBoundingClientRect();
    const tags = [...element.querySelectorAll(".record-heading .el-tag")].map(
      (tag) => {
        const bounds = tag.getBoundingClientRect();
        const style = getComputedStyle(tag);
        return {
          left: bounds.left,
          right: bounds.right,
          width: bounds.width,
          color: style.color,
          opacity: style.opacity,
          text: tag.textContent,
        };
      },
    );
    return {
      left: rect.left,
      right: rect.right,
      width: rect.width,
      viewport: window.innerWidth,
      tags,
    };
  });
  assert.ok(
    layout.tags.every(
      (tag) =>
        tag.left >= -1 &&
        tag.right <= layout.viewport + 1 &&
        tag.width > 30 &&
        Number(tag.opacity) >= 0.8,
    ),
    `${filename}: status tags must remain visible and within viewport`,
  );
  assert.ok(
    layout.left >= -1 && layout.right <= layout.viewport + 1,
    `${filename}: reduction panel must fit viewport`,
  );
  screenshots.push({ filename, layout });
};
const pick = async (page, code) => {
  await panel(page)
    .getByRole("checkbox", { name: `拟少装包 ${code}`, exact: true })
    .waitFor({ state: "attached" });
  await panel(page)
    .locator("label.el-checkbox")
    .filter({ hasText: code })
    .click();
};

try {
  a = await report(
    await createLoading(
      [extra.bags[0], extra.bags[1], lower.bags[0]],
      "C5-R1-A",
    ),
  );
  b = await report(
    await createLoading(
      [extra.bags[2], lower.bags[1], lower.bags[2]],
      "C5-R1-B",
    ),
  );
  c = await createLoading(
    [extra.bags[3], extra.bags[4], lower.bags[3], lower.bags[4]],
    "C5-R1-C",
  );
  await api("/rd/loading-task/batch-scan/confirm", tokens.warehouse, "POST", {
    taskId: c.task.id,
    version: c.task.version,
    representativePackageCode: c.lines[0].packageCode,
    taskLineIds: [c.lines[0].id],
    idempotencyKey: randomUUID(),
    deviceCode: "C5-R1-NOT-FIELD",
  });
  c = await report(await getTask(c.task.id), c.lines[0]);
  const baseline = await fingerprint();
  const auditAccount = {
    username: `c5audit${a.task.id}`,
    password: `Q5aA${randomUUID().replaceAll("-", "").slice(0, 10)}!`,
  };
  const auditId = await api("/system/user/create", tokens.admin, "POST", {
    ...auditAccount,
    nickname: "C5-R1本地审计权限验证",
    remark: "C5-R1仅本地样本",
  });
  await api("/system/permission/assign-user-role", tokens.admin, "POST", {
    userId: auditId,
    roleIds: [600004],
  });
  const auditToken = await login(auditAccount);
  await denied(
    "legacy audit cannot propose",
    `${prefix}/create`,
    auditToken,
    "POST",
    propose(a, a.lines[0].id),
    1080530031,
  );
  for (const actor of ["driver", "otherDriver", "production", "quality"]) {
    await denied(
      `${actor} query`,
      `${prefix}/list?taskId=${a.task.id}`,
      tokens[actor],
    );
    await denied(
      `${actor} create`,
      `${prefix}/create`,
      tokens[actor],
      "POST",
      propose(a, a.lines[0].id),
    );
    await denied(`${actor} review`, `${prefix}/review`, tokens[actor], "PUT", {
      id: 1,
      version: 0,
      decision: "APPROVED",
      reviewRemark: "不得核定",
    });
  }
  await denied(
    "warehouse cannot approve",
    `${prefix}/review`,
    tokens.warehouse,
    "PUT",
    { id: 1, version: 0, decision: "APPROVED", reviewRemark: "不得核定" },
  );
  await denied(
    "sales cannot propose",
    `${prefix}/create`,
    tokens.sales,
    "POST",
    propose(a, a.lines[0].id),
  );
  await denied(
    "explicit context required",
    `${prefix}/list`,
    tokens.warehouse,
    "GET",
    undefined,
    1080530024,
  );
  await denied(
    "scanned exception member",
    `${prefix}/create`,
    tokens.warehouse,
    "POST",
    propose(c, c.lines[0].id),
    1080530024,
  );
  const all = propose(a, a.lines[0].id);
  all.taskLineIds = a.lines.map((line) => line.id);
  await denied(
    "zero remaining",
    `${prefix}/create`,
    tokens.warehouse,
    "POST",
    all,
    1080530024,
  );
  checks.push(
    "independent commercial permission, driver/other-role rejection, required context, previously-scanned member and zero remaining rejected",
  );

  const keeper = await signIn(
    accounts.warehouse,
    `/rd/stock/loading?mode=view&taskId=${a.task.id}`,
  );
  await panel(keeper)
    .getByRole("heading", { name: "拟少装清单", exact: true })
    .waitFor();
  const removedA = a.lines.find((line) => Number(line.packageWeight) === 800);
  assert.ok(removedA);
  await pick(keeper, removedA.packageCode);
  await panel(keeper)
    .getByRole("textbox", { name: "真实少装原因", exact: true })
    .fill("本地验证：800kg包待协调少装，仓管提出、商务核定");
  await screen(keeper, "01-warehouse-proposal-1440.png");
  await panel(keeper)
    .getByRole("button", { name: "申请少装", exact: true })
    .click();
  await panel(keeper).getByText("待商务核定", { exact: true }).last().waitFor();
  const recordA = (await list(a.task.id))[0];
  assert.equal(recordA.reducedPackageCount, 1);
  assert.equal(Number(recordA.reducedWeight), 800);
  assert.equal(recordA.remainingPackageCount, 2);
  assert.equal(Number(recordA.remainingWeight), 2000);
  assert.deepEqual(
    recordA.lines.filter((line) => line.removed).map((line) => line.id),
    [removedA.id],
  );
  assert.equal(recordA.proposerId, accounts.warehouse.userId);
  assert.equal(typeof recordA.originalWeight, "string");
  assert.equal(
    (
      await api(
        `${prefix}/get?taskId=${a.task.id}&idempotencyKey=${recordA.idempotencyKey}`,
        tokens.warehouse,
      )
    ).id,
    recordA.id,
  );
  assert.equal(
    (await api(`${prefix}/get?id=${recordA.id}`, tokens.sales)).id,
    recordA.id,
  );
  const originalCreate = writes.find((write) =>
    write.path.endsWith("/create"),
  ).body;
  assert.equal(
    (await api(`${prefix}/create`, tokens.warehouse, "POST", originalCreate))
      .id,
    recordA.id,
  );
  await denied(
    "idempotency content conflict",
    `${prefix}/create`,
    tokens.warehouse,
    "POST",
    { ...originalCreate, taskLineIds: [a.lines[0].id] },
    1080530026,
  );
  const sales = await signIn(
    accounts.sales,
    `/rd/order/pickup?pickupSlipId=${a.task.pickupSlipId}`,
  );
  await panel(sales).getByText("待商务核定", { exact: true }).waitFor();
  await screen(sales, "02-sales-review-1440.png");
  const remarkA = "商务本地核定800kg少装申请，原计划与库位保持待仓管变更";
  await panel(sales)
    .getByRole("textbox", {
      name: `少装申请 ${recordA.id} 核定说明`,
      exact: true,
    })
    .fill(remarkA);
  await panel(sales)
    .getByRole("button", { name: "核定通过", exact: true })
    .click();
  await panel(sales)
    .getByText("已核定待变更", { exact: true })
    .last()
    .waitFor();
  await screen(sales, "03-sales-approved-1440.png");
  const approvedA = (await list(a.task.id))[0];
  assert.equal(approvedA.status, "APPROVED");
  assert.equal(approvedA.reviewerId, accounts.sales.userId);
  assert.equal(approvedA.reviewRemark, remarkA);
  assert.equal(
    (
      await api(`${prefix}/review`, tokens.sales, "PUT", {
        id: recordA.id,
        version: recordA.version,
        decision: "APPROVED",
        reviewRemark: remarkA,
      })
    ).id,
    recordA.id,
  );
  const pda = await signIn(
    accounts.warehouse,
    `/pda/warehouse?section=loading&taskId=${a.task.id}`,
    390,
  );
  await panel(pda).getByText("已核定待变更", { exact: true }).waitFor();
  assert.equal(
    await panel(pda)
      .getByRole("button", { name: "核定通过", exact: true })
      .count(),
    0,
  );
  await screen(pda, "04-warehouse-approved-390.png");
  checks.push(
    "actual keeper Web proposal, sales approve, keeper PDA read-back, server-computed exact package/weight snapshot and idempotent create/review",
  );

  const mobile = await signIn(
    accounts.warehouse,
    `/pda/warehouse?section=loading&taskId=${b.task.id}`,
    320,
  );
  const removeB = b.lines.find((line) => Number(line.packageWeight) === 1000);
  assert.ok(removeB);
  await pick(mobile, removeB.packageCode);
  await panel(mobile)
    .getByRole("textbox", { name: "真实少装原因", exact: true })
    .fill("本地失联验证：1000kg包申请少装");
  let lostRecord;
  await mobile.route(
    `**/admin-api${prefix}/create`,
    async (route) => {
      const response = await route.fetch();
      const body = await response.json();
      assert.equal(body.code, 0);
      lostRecord = body.data;
      await route.abort("failed");
    },
    { times: 1 },
  );
  await panel(mobile)
    .getByRole("button", { name: "申请少装", exact: true })
    .click();
  await panel(mobile).getByText("少装写入结果待核", { exact: true }).waitFor();
  await screen(mobile, "05-lost-response-pending-320.png");
  assert.ok(lostRecord);
  const priorWrites = writes.filter(
    (write) =>
      write.path.endsWith("/create") && write.body.taskId === b.task.id,
  ).length;
  await mobile.route(
    `**/admin-api${prefix}/list?**`,
    (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({ code: 0, data: [], msg: "" }),
      }),
    { times: 1 },
  );
  const exactRecovery = mobile.waitForResponse((response) =>
    new URL(response.url()).pathname.endsWith(`${prefix}/get`),
  );
  await mobile.reload({ waitUntil: "networkidle" });
  const recoveredResponse = await exactRecovery;
  assert.equal((await recoveredResponse.json()).data.id, lostRecord.id);
  await panel(mobile).getByText("待商务核定", { exact: true }).last().waitFor();
  assert.equal(
    writes.filter(
      (write) =>
        write.path.endsWith("/create") && write.body.taskId === b.task.id,
    ).length,
    priorWrites,
  );
  await screen(mobile, "06-read-only-recovery-320.png");
  const decline = await signIn(
    accounts.sales,
    `/rd/order/pickup?pickupSlipId=${b.task.pickupSlipId}`,
  );
  await panel(decline)
    .getByRole("textbox", {
      name: `少装申请 ${lostRecord.id} 核定说明`,
      exact: true,
    })
    .fill("商务退回：本地测试记录需要重新沟通");
  await panel(decline)
    .getByRole("button", { name: "商务退回", exact: true })
    .click();
  await panel(decline).getByText("商务退回", { exact: true }).last().waitFor();
  checks.push(
    "320px keeper lost-response remains pending; reload reconciles by GET without re-send; sales can return proposal",
  );

  const contenders = [propose(c, c.lines[1].id), propose(c, c.lines[2].id)];
  const results = await Promise.all(
    contenders.map((body) =>
      request(`${prefix}/create`, tokens.warehouse, "POST", body),
    ),
  );
  assert.equal(results.filter((result) => result.code === 0).length, 1);
  assert.equal(
    results.filter((result) => result.code === 1080530027).length,
    1,
  );
  const currentC = (await list(c.task.id))[0];
  const own = await api(
    `${prefix}/create`,
    tokens.admin,
    "POST",
    propose(b, removeB.id),
  );
  await denied(
    "cannot approve own request",
    `${prefix}/review`,
    tokens.admin,
    "PUT",
    {
      id: own.id,
      version: own.version,
      decision: "APPROVED",
      reviewRemark: "自己不得核定",
    },
    1080530029,
  );
  assert.equal(
    await fingerprint(),
    baseline,
    "R1 proposal/review cannot alter tasks, pickup quantities, claims, packages, bins, lots, stock/scan journals",
  );
  checks.push(
    "same-version concurrent requests yield one proposal; own-proposal review denied; full task/slip/claim/stock fingerprint unchanged",
  );

  await sql(
    `UPDATE rd_package_unit SET version=version+1 WHERE id=${removedA.packageId} AND tenant_id=1 AND status='IN_STOCK'`,
  );
  assert.equal(
    (await api(`${prefix}/get?id=${recordA.id}`, tokens.warehouse))
      .effectiveStatus,
    "STALE",
  );
  const reproposal = await api(
    `${prefix}/create`,
    tokens.warehouse,
    "POST",
    propose(a, removedA.id),
  );
  assert.equal(reproposal.status, "PENDING");
  assert.notEqual(reproposal.id, recordA.id);
  await panel(keeper)
    .getByRole("button", { name: "刷新少装清单", exact: true })
    .click();
  await panel(keeper).getByText("已失效", { exact: true }).waitFor();
  await screen(keeper, "07-physical-version-stale-reproposal-1440.png");
  checks.push(
    "deliberate isolated package-version injection invalidates historical approval; fresh current-version proposal is not blocked by the stale approval",
  );

  await api("/rd/loading-task/exception/resolve", tokens.warehouse, "PUT", {
    taskId: c.task.id,
    version: c.task.version,
    resolutionAction: "RETURN_TO_DRIVER",
    handlingResult: "本地版本失效回归，非实际派单",
  });
  assert.equal((await list(c.task.id))[0].effectiveStatus, "STALE");
  await denied(
    "obsolete commercial approval refused",
    `${prefix}/review`,
    tokens.sales,
    "PUT",
    {
      id: currentC.id,
      version: currentC.version,
      decision: "APPROVED",
      reviewRemark: "过期申请不得通过",
    },
    1080530014,
  );
  await api(`${prefix}/review`, tokens.sales, "PUT", {
    id: currentC.id,
    version: currentC.version,
    decision: "REJECTED",
    reviewRemark: "旧计划失效，商务退回",
  });
  checks.push(
    "original keeper return invalidates approval snapshot; stale approval denied; stale pending request can be returned without stock change",
  );
  assert.deepEqual(errors, []);
  assert.deepEqual([...new Set(external)], []);
  const result = {
    status: "PASS",
    marker: "C5-R1 local software verification, not factory acceptance",
    taskIds: [a.task.id, b.task.id, c.task.id],
    requestIds: [recordA.id, lostRecord.id, currentC.id, own.id],
    checks,
    screenshots,
    errors,
    external,
    uiWrites: writes,
    goodsAndTaskUnchangedDuringR1: true,
    deliberatePackageVersionInjectionAfterBaseline: true,
    remaining: [
      "C5-R2 keeper applies approved exact reduction",
      "C5-R3 reduced task supplemental scans/outbound",
      "C6 full audit/concurrency",
      "physical equipment and factory signoff",
    ],
  };
  await fs.writeFile(
    path.join(output, "result.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(
    JSON.stringify({
      status: result.status,
      taskIds: result.taskIds,
      checks,
      screenshots: screenshots.map((item) => item.filename),
      noGoodsChanges: true,
    }),
  );
} catch (error) {
  await fs.writeFile(
    path.join(output, "failure.json"),
    JSON.stringify(
      { message: error.message, checks, screenshots, errors },
      null,
      2,
    ),
  );
  for (const [index, context] of contexts.entries())
    for (const page of context.pages())
      await page
        .screenshot({
          path: path.join(output, `failure-${index}.png`),
          fullPage: true,
        })
        .catch(() => {});
  throw error;
} finally {
  for (const context of contexts) await context.close();
  await browser.close();
}
