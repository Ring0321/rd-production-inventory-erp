import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import {
  prepareWarehouse,
  api,
  root,
  baseUrl,
} from "./RdWarehouseLocalFixture.mjs";

const fixture = await prepareWarehouse();
const { accounts, tokens, ready, extra, lower, fingerprint } = fixture;
const loading = await fixture.createLoading(
  [extra.bags[0], lower.bags[0]],
  "D5-READS",
);
const output = path.join(root, "temp/qr-driver-reads-verification");
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
const reads = [];
const writes = [];
const checks = [];
const before = await fingerprint();
const observe = (page) => {
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (req) => {
    const url = new URL(req.url());
    if (!url.pathname.startsWith("/admin-api/rd/")) return;
    if (req.method() === "GET")
      reads.push({ path: url.pathname, query: url.search });
    else writes.push({ path: url.pathname, method: req.method() });
  });
};
const signIn = async (account, start) => {
  const context = await browser.newContext({
    viewport: { width: 390, height: 900 },
  });
  contexts.push(context);
  const page = await context.newPage();
  observe(page);
  await page.goto(`${baseUrl}${start}`, { waitUntil: "networkidle" });
  await page.waitForURL((url) => url.pathname === "/login");
  await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname !== "/login");
  return { page, context };
};
const screen = async (page, name) => {
  await page.waitForLoadState("networkidle");
  const width = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    scroll: document.documentElement.scrollWidth,
    content: document.querySelector(".pda-content")?.clientWidth,
    contentScroll: document.querySelector(".pda-content")?.scrollWidth,
  }));
  assert.ok(
    width.scroll <= width.viewport &&
      (width.contentScroll ?? 0) <= (width.content ?? width.viewport),
  );
  await page.screenshot({
    path: path.join(output, `${name}.png`),
    fullPage: true,
  });
};
try {
  for (const [kind, detail, detailHeading] of [
    ["putaway", ready, "执行上架任务"],
    ["loading", loading, "装车任务详情"],
  ]) {
    const url = `/pda/driver-${kind}?taskId=${detail.task.id}`;
    const own = await signIn(accounts.driver, url);
    await own.page
      .getByRole("heading", { name: detailHeading, exact: true })
      .waitFor();
    await own.page
      .getByText(detail.task.taskNo, { exact: true })
      .first()
      .waitFor();
    assert.equal(await own.page.locator(".v-layout").count(), 0);
    assert.equal(
      await own.page.getByRole("textbox", { name: /扫描|二维码/ }).count(),
      0,
    );
    for (const width of [320, 390, 1440]) {
      await own.page.setViewportSize({ width, height: 900 });
      await screen(own.page, `${kind}-own-${width}`);
    }
    checks.push(`${kind}-own-task-and-three-widths`);
    await own.page.setViewportSize({ width: 390, height: 900 });
    const match = (value) => value.pathname.endsWith(`/rd/${kind}-task/my-get`);
    await own.page.route(match, (route) => route.abort("failed"));
    await own.page
      .getByRole("button", { name: "刷新任务详情", exact: true })
      .click();
    await own.page
      .getByRole("button", { name: "重试任务详情", exact: true })
      .waitFor();
    assert.equal(
      await own.page
        .getByText(
          `当前没有分配给你的${kind === "putaway" ? "上架" : "装车"}任务`,
          { exact: true },
        )
        .count(),
      0,
    );
    await screen(own.page, `${kind}-disconnected-390`);
    await own.page.unroute(match);
    await own.page
      .getByRole("button", { name: "重试任务详情", exact: true })
      .click();
    await own.page
      .getByRole("heading", { name: detailHeading, exact: true })
      .waitFor();
    await own.page
      .getByText(detail.task.taskNo, { exact: true })
      .first()
      .waitFor();
    checks.push(`${kind}-disconnected-not-empty-and-bound-retry`);

    const other = await signIn(accounts.otherDriver, url);
    await other.page
      .getByRole("button", { name: "重试任务详情", exact: true })
      .waitFor();
    assert.equal(
      await other.page.getByText(detail.task.taskNo, { exact: true }).count(),
      0,
    );
    await screen(other.page, `${kind}-foreign-driver-denied-390`);
    checks.push(`${kind}-foreign-driver-denied`);
    const nonDriver = await signIn(accounts.sales, url);
    await nonDriver.page
      .getByRole("alert")
      .filter({
        hasText: `无本人${kind === "putaway" ? "上架" : "装车"}任务权限`,
      })
      .waitFor();
    assert.equal(
      await nonDriver.page
        .getByText(detail.task.taskNo, { exact: true })
        .count(),
      0,
    );
    checks.push(`${kind}-non-driver-capability-denied`);

    const list = await api(
      `/rd/${kind}-task/my-page?pageNo=1&pageSize=20${kind === "loading" ? "&activeOnly=true" : ""}`,
      tokens.driver,
    );
    assert.ok(list.list.length >= 2);
    const [a, b] = list.list;
    await own.page.goto(`${baseUrl}/pda/driver-${kind}`, {
      waitUntil: "networkidle",
    });
    await own.page
      .locator(".task-card")
      .filter({ hasText: a.taskNo })
      .waitFor();
    let startedResolve, releaseResolve, doneResolve;
    const started = new Promise((resolve) => {
      startedResolve = resolve;
    });
    const release = new Promise((resolve) => {
      releaseResolve = resolve;
    });
    const done = new Promise((resolve) => {
      doneResolve = resolve;
    });
    const held = (value) =>
      match(value) && Number(value.searchParams.get("id")) === a.id;
    await own.page.route(held, async (route) => {
      startedResolve();
      await release;
      const response = await route.fetch();
      await route.fulfill({ response });
      doneResolve();
    });
    await own.page.locator(".task-card").filter({ hasText: a.taskNo }).click();
    await started;
    await own.page.locator(".task-card").filter({ hasText: b.taskNo }).click();
    await own.page.getByText(b.taskNo, { exact: true }).first().waitFor();
    releaseResolve();
    await done;
    await own.page.waitForLoadState("networkidle");
    assert.equal(
      await own.page.getByText(a.taskNo, { exact: true }).count(),
      0,
    );
    assert.equal(
      await own.page.getByText(b.taskNo, { exact: true }).count(),
      1,
    );
    await own.page.unroute(held);
    checks.push(`${kind}-real-older-response-never-overwrites-later-task`);
  }
  assert.deepEqual(writes, []);
  assert.deepEqual(errors, []);
  assert.equal(await fingerprint(), before);
  const result = {
    result: "PASS",
    marker: "D5-1本地读取验证（非现场）",
    productionTaskId: fixture.taskId,
    putawayTaskId: ready.task.id,
    loadingTaskId: loading.task.id,
    checks,
    reads: reads.length,
    businessWrites: writes.length,
    pageErrors: errors,
    fieldAcceptance: false,
  };
  await fs.writeFile(
    path.join(output, "result.json"),
    JSON.stringify(result, null, 2),
  );
  await fs.writeFile(
    path.join(output, "reads.json"),
    JSON.stringify(reads, null, 2),
  );
  console.log(JSON.stringify(result));
} catch (error) {
  for (const [i, context] of contexts.entries())
    for (const [j, page] of context.pages().entries()) {
      await page
        .screenshot({
          path: path.join(output, `failure-${i}-${j}.png`),
          fullPage: true,
        })
        .catch(() => {});
      await fs
        .writeFile(
          path.join(output, `failure-${i}-${j}.html`),
          await page.content(),
        )
        .catch(() => {});
    }
  throw error;
} finally {
  await browser.close();
}
