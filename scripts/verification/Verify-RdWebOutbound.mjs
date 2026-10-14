import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { randomBytes, randomUUID } from "node:crypto";
import { createRequire } from "node:module";
import {
  prepareWarehouse,
  api,
  request,
  root,
  baseUrl,
} from "./RdWarehouseLocalFixture.mjs";
import { login } from "./RdQrLocalFixture.mjs";

const fixture = await prepareWarehouse();
const { tokens, accounts, extra, lower, createLoading, sql } = fixture;
const output = path.join(root, "temp/qr-web-outbound-verification");
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
const commands = [];
const errors = [];
const checks = [];
const tasks = [];
const screenshots = [];
const makeTask = async (bags, suffix) => {
  let detail = await createLoading(bags, suffix);
  tasks.push(detail.task.id);
  await api("/rd/loading-task/batch-scan/confirm", tokens.warehouse, "POST", {
    taskId: detail.task.id,
    version: detail.task.version,
    representativePackageCode: detail.lines[0].packageCode,
    taskLineIds: detail.lines.map((line) => line.id),
    idempotencyKey: randomUUID(),
    deviceCode: "R12-LOCAL-NOT-FIELD",
  });
  detail = await api(
    `/rd/loading-task/get?id=${detail.task.id}`,
    tokens.warehouse,
  );
  assert.equal(detail.task.status, "PENDING_REVIEW");
  return detail;
};
const positive = await makeTask([extra.bags[0], lower.bags[0]], "WEB-POSITIVE");
const race = await makeTask([extra.bags[1]], "WEB-RACE");
const other = await makeTask([lower.bags[1]], "WEB-OTHER");
assert.equal(
  race.task.version,
  other.task.version,
  "Race fixtures must have the same displayed version",
);
const ids = [...extra.bags, ...lower.bags].map((bag) => bag.id);
const fingerprint = async () =>
  JSON.stringify([
    await fixture.fingerprint(),
    await sql(
      `SELECT * FROM rd_loading_task WHERE id IN (${tasks.join(",")}) ORDER BY id`,
    ),
    await sql(
      `SELECT * FROM rd_loading_task_line WHERE task_id IN (${tasks.join(",")}) ORDER BY id`,
    ),
    await sql(
      `SELECT * FROM rd_loading_scan_record WHERE task_id IN (${tasks.join(",")}) ORDER BY id`,
    ),
    await sql(
      `SELECT * FROM rd_stock_transaction WHERE package_id IN (${ids.join(",")}) ORDER BY id`,
    ),
    await sql(
      `SELECT * FROM rd_package_task_event WHERE package_id IN (${ids.join(",")}) ORDER BY id`,
    ),
  ]);
const observe = (page) => {
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (req) => {
    const pathname = new URL(req.url()).pathname;
    if (
      /\/loading-task\/(batch-scan\/(preview|confirm|status)|confirm-outbound)$/.test(
        pathname,
      )
    )
      commands.push({ path: pathname, body: req.postDataJSON() });
  });
};
const fillLogin = async (page, account) => {
  await page.waitForURL((url) => url.pathname === "/login");
  await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname !== "/login");
};
const signIn = async (
  account = accounts.warehouse,
  start = "/rd/stock/loading",
) => {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  contexts.push(context);
  const page = await context.newPage();
  observe(page);
  await page.goto(`${baseUrl}${start}`, { waitUntil: "networkidle" });
  await fillLogin(page, account);
  await page
    .getByRole("heading", {
      name: start === "/pda" ? "叉车工作台" : "出库装车",
      exact: true,
    })
    .waitFor();
  return { page, context };
};
const scan = async (page, detail) => {
  await page.getByRole("tab", { name: "扫码复核", exact: true }).click();
  await page
    .getByPlaceholder(
      "扫描包件二维码，或输入完整包号",
    )
    .fill(detail.lines[0].packageCode);
  await page.getByPlaceholder("扫描包件二维码，或输入完整包号").press("Enter");
  await page.getByText(detail.task.taskNo, { exact: true }).first().waitFor();
  await page
    .getByRole("button", { name: "确认出库过账", exact: true })
    .waitFor();
};
const showFinal = async (page, detail) => {
  await scan(page, detail);
  await page.getByRole("button", { name: "确认出库过账", exact: true }).click();
  await page
    .getByRole("dialog")
    .filter({ hasText: detail.task.taskNo })
    .waitFor();
};
const approveFinal = async (page) => {
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("button", { name: "确定", exact: true }).click();
  await dialog.waitFor({ state: "hidden" });
};
const screen = async (page, name) => {
  await page.mouse.move(0, 0);
  await page.getByRole("dialog").waitFor({ state: "hidden" });
  const width = await page.evaluate(() => ({
    client: document.documentElement.clientWidth,
    scroll: document.documentElement.scrollWidth,
  }));
  assert.ok(width.scroll <= width.client);
  await page.screenshot({
    path: path.join(output, `${name}.png`),
    fullPage: true,
  });
  screenshots.push(name);
};
const noOutbound = async (name, action) => {
  const before = await fingerprint();
  const count = commands.filter((item) =>
    item.path.endsWith("/confirm-outbound"),
  ).length;
  await action();
  assert.equal(
    commands.filter((item) => item.path.endsWith("/confirm-outbound")).length,
    count,
    `${name}: stale dialog issued a post`,
  );
  assert.equal(await fingerprint(), before, `${name}: domain state changed`);
  checks.push({ name, noPost: true, noDomainWrites: true });
};
try {
  const actor = await signIn();
  const { page } = actor;
  await noOutbound("cancel-final-dialog", async () => {
    await showFinal(page, race);
    await page
      .getByRole("dialog")
      .getByRole("button", { name: "取消", exact: true })
      .click();
    await page.getByRole("dialog").waitFor({ state: "hidden" });
  });

  await page.goto(`${baseUrl}/pkg/${other.lines[0].packageCode}`, {
    waitUntil: "networkidle",
  });
  await page.getByRole("button", { name: "出库扫码复核", exact: true }).click();
  await page.getByText(other.task.taskNo, { exact: true }).first().waitFor();
  const otherRoute = page.url();
  await page.getByRole("menuitem", { name: "出库装车", exact: true }).click();
  await page.waitForURL(
    (url) => url.pathname === "/rd/stock/loading" && !url.search,
  );
  await noOutbound("same-component-task-route-changed", async () => {
    await showFinal(page, race);
    await page.goBack();
    await page.waitForURL(otherRoute);
    await approveFinal(page);
  });
  await page.goForward();
  await page.waitForURL(
    (url) => url.pathname === "/rd/stock/loading" && !url.search,
  );
  await noOutbound("leave-and-return-does-not-reuse-old-dialog", async () => {
    await showFinal(page, race);
    await page.goBack();
    await page.waitForURL(otherRoute);
    await page.goForward();
    await page.waitForURL(
      (url) => url.pathname === "/rd/stock/loading" && !url.search,
    );
    await approveFinal(page);
  });
  await screen(page, "web-route-change-denied-1440");

  await noOutbound(
    "cross-tab-login-change-cannot-confirm-old-task",
    async () => {
      await showFinal(page, race);
      const accountPage = await actor.context.newPage();
      await accountPage.goto(`${baseUrl}/pda`, {
        waitUntil: "networkidle",
      });
      await accountPage
        .getByRole("button", { name: "退出登录", exact: true })
        .click();
      await accountPage
        .getByRole("dialog")
        .getByRole("button", { name: "退出", exact: true })
        .click();
      await fillLogin(accountPage, accounts.admin);
      await accountPage
        .getByRole("heading", { name: "查询工作台", exact: true })
        .waitFor();
      await page.bringToFront();
      await approveFinal(page);
      await page
        .getByText("登录身份或页面已变化，请用原账号和租户核对原请求", {
          exact: true,
        })
        .waitFor();
    },
  );

  const actual = await signIn();
  await showFinal(actual.page, positive);
  const submitted = actual.page.waitForRequest((req) =>
    new URL(req.url()).pathname.endsWith("/loading-task/confirm-outbound"),
  );
  await approveFinal(actual.page);
  const body = (await submitted).postDataJSON();
  assert.deepEqual(body, {
    taskId: positive.task.id,
    version: positive.task.version,
  });
  await actual.page
    .getByRole("alert")
    .filter({ hasText: "该任务已完成出库" })
    .waitFor();
  const completed = await api(
    `/rd/loading-task/get?id=${positive.task.id}`,
    tokens.warehouse,
  );
  assert.equal(completed.task.status, "OUTBOUNDED");
  assert.equal(completed.task.scannedPackageCount, 2);
  assert.equal(completed.task.scannedWeight, 1800);
  for (const bag of [extra.bags[0], lower.bags[0]])
    assert.equal(
      (await api(`/rd/package-unit/get?id=${bag.id}`, tokens.warehouse)).status,
      "OUTBOUNDED",
    );
  await noOutbound("completed-Web-task-is-disabled", async () => {
    assert.equal(
      await actual.page
        .getByRole("button", { name: "确认出库过账", exact: true })
        .isDisabled(),
      true,
    );
  });
  await screen(actual.page, "web-outbound-completed-1440");
  checks.push({
    name: "actual-Web-final-button",
    taskId: positive.task.id,
    packages: 2,
    weight: 1800,
    postedOriginalVersion: true,
  });

  const roleId = await api("/system/role/create", tokens.admin, "POST", {
    name: `R12本地装车只读-${fixture.taskId}`,
    code: `r12_loading_read_${Date.now()}`,
    sort: 999,
    status: 0,
    remark: "R12非现场权限验证",
  });
  await api("/system/permission/assign-role-menu", tokens.admin, "POST", {
    roleId,
    menuIds: [600000, 600200, 600282],
  });
  const readonly = {
    username: `r12read${randomBytes(3).toString("hex")}`,
    password: `R12Q${randomBytes(5).toString("hex")}!`,
  };
  readonly.userId = await api("/system/user/create", tokens.admin, "POST", {
    ...readonly,
    nickname: "R12本地装车只读",
    remark: "非现场账号",
  });
  await api("/system/permission/assign-user-role", tokens.admin, "POST", {
    userId: readonly.userId,
    roleIds: [roleId],
  });
  const readonlyToken = await login(readonly);
  for (const [name, account, token] of [
    ["query-only", readonly, readonlyToken],
    ["driver", accounts.driver, tokens.driver],
  ]) {
    await noOutbound(`${name}-cannot-preview-or-post`, async () => {
      const restricted = await signIn(
        account,
        name === "driver" ? "/pda" : undefined,
      );
      const before = commands.length;
      if (name === "query-only") {
        await restricted.page
          .getByRole("tab", { name: "扫码复核", exact: true })
          .click();
        const input = restricted.page.getByPlaceholder(
          "扫描包件二维码，或输入完整包号",
        );
        assert.equal(await input.isDisabled(), true, 'query-only account cannot edit a dispatch scan');
        await restricted.page
          .getByRole("heading", { name: "出库装车", exact: true })
          .waitFor();
        await restricted.page.waitForLoadState("networkidle");
        assert.equal(await restricted.page.getByRole("tab", { name: "扫码复核", exact: true }).getAttribute("aria-selected"), "true");
      } else {
        assert.equal(
          await restricted.page
            .getByRole("button", { name: "确认最终出库", exact: true })
            .count(),
          0,
        );
      }
      assert.equal(
        await restricted.page
          .getByRole("button", { name: "识别任务", exact: true })
          .count(),
        0,
      );
      assert.equal(commands.length, before);
      assert.equal(
        (
          await request("/rd/loading-task/batch-scan/preview", token, "POST", {
            taskId: race.task.id,
            packageCode: race.lines[0].packageCode,
          })
        ).code,
        403,
      );
      assert.equal(
        (
          await request("/rd/loading-task/confirm-outbound", token, "POST", {
            taskId: race.task.id,
            version: race.task.version,
          })
        ).code,
        403,
      );
      await screen(restricted.page, `web-${name}-readonly-1440`);
    });
  }
  assert.deepEqual(errors, []);
  await fs.writeFile(
    path.join(output, "commands.json"),
    JSON.stringify(commands, null, 2),
  );
  const result = {
    marker: "R12本地软件验证，非现场装车或签字验收",
    productionTaskId: fixture.taskId,
    positiveTaskId: positive.task.id,
    raceTaskId: race.task.id,
    otherTaskId: other.task.id,
    readonlyRoleId: roleId,
    readonlyUserId: readonly.userId,
    checks,
    screenshots,
    pageErrors: errors,
    result: "PASS",
  };
  await fs.writeFile(
    path.join(output, "result.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result));
} catch (error) {
  await fs.writeFile(
    path.join(output, "commands.json"),
    JSON.stringify(commands, null, 2),
  );
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
