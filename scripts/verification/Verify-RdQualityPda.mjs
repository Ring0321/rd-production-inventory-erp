import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import {
  prepareQuality,
  batchRequest,
  root,
  output,
  api,
  baseUrl,
} from "./RdQualityLocalFixture.mjs";

const { tokens, accounts, taskId, createOutput } = await prepareQuality();
const source = await createOutput(4, "pda");
const pagesSource = await createOutput(21, "pagination");
const rejectedSource = await createOutput(2, "stale-scope");
const identitySource = await createOutput(1, "identity-boundary");
const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
const pageErrors = [];
const businessCalls = [];
const captures = [];
const screens = [];
const loginHere = async (page, account) => {
  await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname === "/pda/quality");
  await page.getByRole("heading", { name: "质量检验", exact: true }).waitFor();
};
const logoutHere = async (page) => {
  await page.getByRole("button", { name: "退出登录", exact: true }).click();
  await page.getByRole("button", { name: "退出", exact: true }).last().click();
  await page.waitForURL((url) => url.pathname === "/login");
};
const signIn = async (account, suffix = "") => {
  const context = await browser.newContext({
    viewport: { width: 390, height: 900 },
  });
  const page = await context.newPage();
  page.on("pageerror", (error) => pageErrors.push(error.message));
  page.on("request", (request) => {
    if (request.url().includes("/admin-api/rd/") && request.method() !== "GET")
      businessCalls.push({
        path: new URL(request.url()).pathname,
        method: request.method(),
      });
  });
  await page.goto(`${baseUrl}/pda/quality${suffix}`, {
    waitUntil: "networkidle",
  });
  await loginHere(page, account);
  assert.equal(await page.locator(".v-layout").count(), 0);
  return { context, page };
};
const scan = async (page, code) => {
  await page
    .getByLabel("包号 / 二维码", { exact: true })
    .fill(`https://id.tjldsic.com/pkg/${code}`);
  await page.getByRole("button", { name: "读取检验范围", exact: true }).click();
  await page
    .getByRole("checkbox", { name: `覆盖 ${code}`, exact: true })
    .waitFor({ state: "attached" });
};
const setBagCheck = async (page, kind, code, checked = true) => {
  const checkbox = page.getByRole("checkbox", {
    name: `${kind} ${code}`,
    exact: true,
  });
  if ((await checkbox.isChecked()) !== checked) {
    const label = page.locator("label.el-checkbox").filter({ has: checkbox });
    await label.click();
  }
  assert.equal(await checkbox.isChecked(), checked);
};
const selectCoverage = (page, code) => setBagCheck(page, "覆盖", code);
const fill = async (page) => {
  await page.getByLabel("检测项1项目编码", { exact: true }).fill("TEST-SIC");
  await page
    .getByLabel("检测项1项目名称", { exact: true })
    .fill("PDA验证（非现场测量）");
  await page
    .getByRole("spinbutton", { name: "检测项1标准下限", exact: true })
    .fill("97");
  await page
    .getByRole("spinbutton", { name: "检测项1标准上限", exact: true })
    .fill("100");
  await page
    .getByRole("spinbutton", { name: "检测项1实测值", exact: true })
    .fill("98");
};
const screen = async (page, name) => {
  await page.mouse.move(0, 0);
  await page.waitForFunction(() =>
    Array.from(document.querySelectorAll('[role="tooltip"]')).every(
      (node) => !node.getClientRects().length,
    ),
  );
  const measurements = await page.evaluate(() => ({
    doc: document.documentElement.clientWidth,
    scroll: document.documentElement.scrollWidth,
    content: document.querySelector(".pda-content").clientWidth,
    contentScroll: document.querySelector(".pda-content").scrollWidth,
  }));
  assert.ok(
    measurements.scroll <= measurements.doc &&
      measurements.contentScroll <= measurements.content,
  );
  await page.screenshot({
    path: path.join(output, `${name}.png`),
    fullPage: true,
  });
  screens.push(name);
};
try {
  const { context, page } = await signIn(accounts.quality);
  const last = pagesSource.bags.at(-1);
  await scan(page, last.packageCode);
  assert.equal(
    await page
      .getByRole("checkbox", { name: `覆盖 ${last.packageCode}`, exact: true })
      .isChecked(),
    true,
  );
  await selectCoverage(page, pagesSource.bags[0].packageCode);
  await page.getByRole("button", { name: "下一页范围", exact: true }).click();
  await page.getByText("2 / 2", { exact: true }).waitFor();
  assert.ok(
    (await page.getByRole("region", { name: "检验范围" }).innerText()).includes(
      "已选 2 包",
    ),
  );
  await page.getByRole("button", { name: "选择可检整批", exact: true }).click();
  await page
    .getByRole("region", { name: "检验范围" })
    .getByText(/已选 21 包/)
    .waitFor();
  assert.equal(businessCalls.length, 0);
  await scan(page, source.bags[0].packageCode);
  await page.getByRole("button", { name: "选择可检整批", exact: true }).click();
  await page
    .getByRole("region", { name: "检验范围" })
    .getByText(/已选 4 包/)
    .waitFor();
  await setBagCheck(page, "覆盖", source.bags[3].packageCode, false);
  assert.equal(
    await page
      .getByRole("checkbox", {
        name: `覆盖 ${source.bags[3].packageCode}`,
        exact: true,
      })
      .isChecked(),
    false,
  );
  assert.equal(
    await page
      .getByRole("checkbox", {
        name: `抽样 ${source.bags[1].packageCode}`,
        exact: true,
      })
      .isChecked(),
    false,
  );
  await page
    .getByLabel("包号 / 二维码", { exact: true })
    .scrollIntoViewIfNeeded();
  await screen(page, "quality-batch-scope-390");
  await fill(page);
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    await page
      .getByLabel("检测项1项目名称", { exact: true })
      .scrollIntoViewIfNeeded();
    await screen(page, `quality-form-${width}`);
  }
  let createdId;
  await page.route(
    "**/admin-api/rd/quality-inspection/create-batch",
    async (route) => {
      captures.push(route.request().postDataJSON());
      const response = await route.fetch();
      const result = await response.json();
      assert.equal(result.code, 0, result.msg);
      createdId = result.data;
      if (captures.length === 1) await route.abort("connectionfailed");
      else await route.fulfill({ response });
    },
  );
  const dropped = page.waitForEvent("requestfailed", {
    predicate: (request) =>
      request.url().includes("/quality-inspection/create-batch"),
  });
  await page.getByRole("button", { name: "保存并提交", exact: true }).click();
  await dropped;
  await page.getByRole("button", { name: "重试原提交", exact: true }).waitFor();
  assert.equal(
    (await api(`/rd/quality-inspection/get?id=${createdId}`, tokens.quality))
      .status,
    "DRAFT",
  );
  await page.reload({ waitUntil: "networkidle" });
  await page.getByRole("button", { name: "重试原提交", exact: true }).click();
  await page
    .locator(".inspection-detail")
    .getByText("待复核", { exact: true })
    .first()
    .waitFor();
  assert.equal(captures.length, 2);
  assert.deepEqual(captures[0], captures[1]);
  assert.deepEqual(
    new Set(captures[0].packageIds),
    new Set(source.bags.slice(0, 3).map((bag) => bag.id)),
  );
  assert.deepEqual(captures[0].samplePackageIds, [source.bags[0].id]);
  assert.equal(
    await page.getByRole("button", { name: "复核通过", exact: true }).count(),
    0,
  );
  assert.deepEqual(
    await page.locator(".quality-audit strong").allTextContents(),
    ["登记", "提交"],
  );
  await screen(page, "quality-submitted-320");
  const readOnlyStart = businessCalls.length;
  await page.goto(`${baseUrl}/pkg/${source.bags[1].packageCode}?workMode=pda`, {
    waitUntil: "networkidle",
  });
  await page.getByRole("button", { name: "质检记录", exact: true }).click();
  await page.waitForURL(
    (url) =>
      url.pathname === "/pda/quality" &&
      url.searchParams.get("inspectionId") === String(createdId),
  );
  await page
    .locator(".inspection-detail")
    .getByText(source.bags[1].packageCode, { exact: true })
    .waitFor();
  assert.equal(await page.locator(".member-list li").count(), 3);
  assert.equal(businessCalls.length, readOnlyStart);
  await context.close();

  const reviewer = await signIn(accounts.admin, `?inspectionId=${createdId}`);
  await reviewer.page
    .getByRole("button", { name: "复核通过", exact: true })
    .click();
  await reviewer.page
    .locator(".el-message-box__btns .el-button--primary")
    .click();
  await reviewer.page
    .locator(".inspection-detail")
    .getByText("已通过", { exact: true })
    .waitFor();
  assert.deepEqual(
    await reviewer.page.locator(".quality-audit strong").allTextContents(),
    ["登记", "提交", "复核通过"],
  );
  for (const width of [1440, 390, 320]) {
    await reviewer.page.setViewportSize({ width, height: 900 });
    await reviewer.page.locator(".inspection-detail").scrollIntoViewIfNeeded();
    await screen(reviewer.page, `quality-approved-${width}`);
  }
  await reviewer.page.locator(".quality-audit").scrollIntoViewIfNeeded();
  await screen(reviewer.page, "quality-audit-320");
  await reviewer.context.close();
  const latest = await api(
    `/rd/package-unit/trace?productionOutputId=${source.id}`,
    tokens.quality,
  );
  assert.equal(latest.filter((bag) => bag.status === "QC_PASSED").length, 3);
  assert.equal(
    latest.find((bag) => bag.id === source.bags[3].id).status,
    "LABELED",
  );

  const remaining = await signIn(accounts.quality);
  await scan(remaining.page, source.bags[0].packageCode);
  assert.equal(
    await remaining.page
      .getByRole("checkbox", {
        name: `覆盖 ${source.bags[0].packageCode}`,
        exact: true,
      })
      .isEnabled(),
    false,
  );
  await remaining.page
    .getByRole("button", { name: "选择可检整批", exact: true })
    .click();
  await remaining.page
    .getByRole("region", { name: "检验范围" })
    .getByText(/已选 1 包/)
    .waitFor();
  await setBagCheck(remaining.page, "抽样", source.bags[3].packageCode);
  await fill(remaining.page);
  const remainderRequest = remaining.page.waitForResponse(
    (response) =>
      response.url().includes("/quality-inspection/create-batch") &&
      response.request().method() === "POST",
  );
  await remaining.page
    .getByRole("button", { name: "保存并提交", exact: true })
    .click();
  const remainderId = (await (await remainderRequest).json()).data;
  await remaining.page
    .locator(".inspection-detail")
    .getByText("待复核", { exact: true })
    .first()
    .waitFor();
  const remainder = await api(
    `/rd/quality-inspection/get?id=${remainderId}`,
    tokens.quality,
  );
  assert.deepEqual(
    remainder.members.map((member) => member.packageId),
    [source.bags[3].id],
  );
  assert.equal(remainder.packageId, source.bags[3].id);
  assert.equal(remainder.sampleCount, 1);
  assert.deepEqual(
    remainder.events.map((event) => event.action),
    ["CREATE", "SUBMIT"],
  );
  await screen(remaining.page, "quality-remaining-after-passed-locator-390");
  await remaining.context.close();
  await api(
    `/rd/quality-inspection/approve?id=${remainderId}`,
    tokens.admin,
    "POST",
  );

  const stale = await signIn(accounts.quality);
  await scan(stale.page, rejectedSource.bags[0].packageCode);
  await fill(stale.page);
  const held = await api(
    "/rd/quality-inspection/create-batch",
    tokens.admin,
    "POST",
    batchRequest(rejectedSource.bags, [0], [0]),
  );
  await stale.page
    .getByRole("button", { name: "保存并提交", exact: true })
    .click();
  await stale.page.getByText(/服务器已拒绝本次登记/).waitFor();
  assert.equal(
    await stale.page
      .getByRole("button", { name: "重试原提交", exact: true })
      .count(),
    0,
  );
  assert.equal(
    await stale.page.getByLabel("包号 / 二维码", { exact: true }).isEnabled(),
    true,
  );
  await screen(stale.page, "quality-stale-scope-rejected-390");
  await stale.context.close();
  await api(`/rd/quality-inspection/submit?id=${held}`, tokens.admin, "POST");
  await api("/rd/quality-inspection/reject", tokens.quality, "POST", {
    id: held,
    reason: "清理验证占用（非现场结果）",
  });

  for (const role of ["driver", "sales", "production"]) {
    const start = businessCalls.length;
    const denied = await signIn(accounts[role]);
    assert.equal(
      await denied.page
        .getByRole("button", { name: "保存并提交", exact: true })
        .count(),
      0,
    );
    assert.equal(
      await denied.page
        .getByRole("button", { name: /复核通过|复核不通过/ })
        .count(),
      0,
    );
    assert.equal(businessCalls.length, start);
    await denied.context.close();
  }
  const boundary = await signIn(accounts.quality);
  await scan(boundary.page, identitySource.bags[0].packageCode);
  await fill(boundary.page);
  let releaseResponse, signalCreated;
  const creationReady = new Promise((resolve) => {
    signalCreated = resolve;
  });
  await boundary.page.route(
    "**/admin-api/rd/quality-inspection/create-batch",
    async (route) => {
      const response = await route.fetch();
      const result = await response.json();
      assert.equal(result.code, 0, result.msg);
      const release = new Promise((resolve) => {
        releaseResponse = resolve;
      });
      signalCreated(result.data);
      await release;
      await route.fulfill({ response });
    },
  );
  const boundaryStart = businessCalls.length;
  await boundary.page
    .getByRole("button", { name: "保存并提交", exact: true })
    .click();
  const boundaryId = await creationReady;
  const draft = await api(
    `/rd/quality-inspection/get?id=${boundaryId}`,
    tokens.quality,
  );
  assert.equal(draft.status, "DRAFT");
  const ownerKey = `rd-quality-pending:1:${draft.registrarId}`;
  await logoutHere(boundary.page);
  releaseResponse();
  await boundary.page.waitForFunction(
    ({ key, id }) => {
      const raw = localStorage.getItem(key);
      return raw && JSON.parse(raw).inspectionId === id;
    },
    { key: ownerKey, id: boundaryId },
  );
  await boundary.page.waitForLoadState("networkidle");
  assert.equal(
    businessCalls
      .slice(boundaryStart)
      .some((call) => call.path.endsWith("/quality-inspection/submit")),
    false,
  );
  assert.equal(
    (await api(`/rd/quality-inspection/get?id=${boundaryId}`, tokens.quality))
      .status,
    "DRAFT",
  );
  await boundary.page.unroute(
    "**/admin-api/rd/quality-inspection/create-batch",
  );
  await loginHere(boundary.page, accounts.admin);
  assert.equal(
    await boundary.page
      .getByRole("button", { name: "重试原提交", exact: true })
      .count(),
    0,
  );
  await logoutHere(boundary.page);
  await loginHere(boundary.page, accounts.quality);
  await boundary.page
    .getByRole("button", { name: "重试原提交", exact: true })
    .click();
  await boundary.page
    .locator(".inspection-detail")
    .getByText("待复核", { exact: true })
    .first()
    .waitFor();
  assert.equal(
    (await api(`/rd/quality-inspection/get?id=${boundaryId}`, tokens.quality))
      .status,
    "SUBMITTED",
  );
  await screen(boundary.page, "quality-original-owner-recovered-390");
  await boundary.context.close();
  await api(
    `/rd/quality-inspection/approve?id=${boundaryId}`,
    tokens.admin,
    "POST",
  );
  const result = {
    result: "PASS",
    taskId,
    inspectionId: createdId,
    sourceCount: 4,
    coverage: 3,
    sampleCount: 1,
    paginatedSelection: true,
    lateRepresentativeSupported: true,
    wholeBatchAcrossPagesWithoutWrites: true,
    processedLocatorCanInspectOnlyRemainingEligibleBags: true,
    transactionAuditVisible: true,
    coveredNonSampleBagHasExactPdaTraceEntry: true,
    logoutDuringAcknowledgementDoesNotSubmit: true,
    anotherLoginCannotConsumeOwnerSnapshot: true,
    originalOwnerCanResumeAcknowledgedDraft: true,
    actualLostResponseAndReloadRetry: true,
    sameRequestTwice: true,
    selfReviewHidden: true,
    anotherReviewerApproved: true,
    knownCreateRejectionEditable: true,
    unrelatedRolesNoOperations: true,
    pageErrors,
    screenshots: screens,
    fieldAcceptance: false,
  };
  assert.deepEqual(pageErrors, []);
  await fs.writeFile(
    path.join(output, "pda-result.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result));
} finally {
  await browser.close();
}
