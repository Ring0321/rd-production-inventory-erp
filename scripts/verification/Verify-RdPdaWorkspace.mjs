import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { root, baseUrl, prepareFixture } from "./RdQrLocalFixture.mjs";

const { accounts } = await prepareFixture();
const taskFixture = JSON.parse(
  await fs.readFile(
    path.join(root, "temp/qr-production-task-verification/fixture.json"),
    "utf8",
  ),
);
const { readonlyAccount } = taskFixture;
const output = path.join(root, "temp/qr-pda-workspace-verification");
await fs.mkdir(output, { recursive: true });
const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
const checks = [],
  errors = [];
const cases = [
  ["production", accounts.production, "生产工作台"],
  ["quality", accounts.quality, "质检工作台"],
  ["warehouse", accounts.warehouse, "仓管工作台"],
  ["driver", accounts.driver, "叉车工作台"],
  ["sales", accounts.sales, "销售查询工作台"],
  ["readonly", readonlyAccount, "查询工作台"],
  ["admin", accounts.admin, "查询工作台"],
];
try {
  for (const [role, account, heading] of cases) {
    const context = await browser.newContext({
      viewport: { width: 390, height: 900 },
    });
    const page = await context.newPage();
    const calls = [];
    page.on("pageerror", (error) =>
      errors.push({ role, message: error.message }),
    );
    page.on("request", (request) => {
      if (request.url().includes("/admin-api/rd/"))
        calls.push({
          method: request.method(),
          path: new URL(request.url()).pathname,
        });
    });
    await page.goto(`${baseUrl}/pda`, { waitUntil: "networkidle" });
    await page.waitForURL((url) => url.pathname === "/login", {
      timeout: 15000,
    });
    assert.ok(page.url().includes("/login"));
    await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
    await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
    await page.getByRole("button", { name: "登录", exact: true }).click();
    await page.waitForURL((url) => url.pathname === "/pda");
    await page.getByRole("heading", { name: heading, exact: true }).waitFor();
    assert.equal(await page.locator(".v-layout").count(), 0);
    const texts = await page
      .getByRole("navigation", { name: "业务入口" })
      .innerText();
    if (role === "production")
      assert.ok(texts.includes("生产录入") && !texts.includes("出库装车"));
    if (role === "quality")
      assert.ok(texts.includes("质量检验") && !texts.includes("生产录入"));
    if (role === "warehouse")
      assert.ok(
        texts.includes("入库上架") &&
          texts.includes("出库装车") &&
          !texts.includes("生产录入"),
      );
    if (role === "driver") {
      assert.ok(
        texts.includes("本人上架任务") && texts.includes("本人装车任务"),
      );
      assert.equal(await page.getByLabel("包码查询").count(), 0);
      assert.ok(
        calls.some((call) => call.path.endsWith("/putaway-task/my-page")),
      );
      assert.ok(
        calls.some((call) => call.path.endsWith("/loading-task/my-page")),
      );
      assert.equal(
        calls.some(
          (call) =>
            call.path.endsWith("/putaway-task/page") ||
            call.path.endsWith("/loading-task/page"),
        ),
        false,
      );
    }
    if (role === "readonly")
      assert.equal(
        texts.includes("生产录入") ||
          texts.includes("入库上架") ||
          texts.includes("质量检验"),
        false,
      );
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 900 });
      await page.screenshot({
        path: path.join(output, `${role}-${width}.png`),
        fullPage: true,
      });
      const dimensions = await page.evaluate(() => ({
        root: document.documentElement.clientWidth,
        scroll: document.documentElement.scrollWidth,
        content: document.querySelector(".pda-content").clientWidth,
        contentScroll: document.querySelector(".pda-content").scrollWidth,
      }));
      assert.ok(
        dimensions.scroll <= dimensions.root &&
          dimensions.contentScroll <= dimensions.content,
        `${role} ${width} overflow`,
      );
    }
    await page.reload({ waitUntil: "networkidle" });
    await page.getByRole("heading", { name: heading, exact: true }).waitFor();
    if (role === "production") {
      await page.getByRole("link", { name: /生产录入/ }).click();
      await page.waitForURL((url) => url.pathname === "/pda/production");
      await page
        .getByRole("heading", { name: "选择生产任务", exact: true })
        .waitFor();
      await page.getByRole("button", { name: "岗位首页", exact: true }).click();
      await page.getByRole("heading", { name: heading, exact: true }).waitFor();
      await page
        .getByLabel("包码查询")
        .fill(`https://id.tjldsic.com/pkg/${taskFixture.codes[0]}`);
      await page.getByRole("button", { name: "查询包码", exact: true }).click();
      await page.waitForURL(
        (url) =>
          url.pathname.endsWith(`/pkg/${taskFixture.codes[0]}`) &&
          url.searchParams.get("workMode") === "pda",
      );
      await page.getByRole("button", { name: "生产任务", exact: true }).click();
      await page.waitForURL(
        (url) =>
          url.pathname === "/pda/production" &&
          url.searchParams.get("productionTaskId") ===
            String(taskFixture.taskId),
      );
      await page
        .getByText(taskFixture.taskNo, { exact: true })
        .first()
        .waitFor();
    }
    if (role === "quality") {
      assert.equal(
        await page
          .getByRole("navigation", { name: "业务入口" })
          .getByRole("link")
          .count(),
        1,
      );
      await page.getByRole("link", { name: /质量检验/ }).click();
      await page.waitForURL((url) => url.pathname === "/pda/quality");
      await page
        .getByRole("heading", { name: "质量检验", exact: true })
        .waitFor();
      assert.equal(await page.locator(".v-layout").count(), 0);
      await page.getByRole("button", { name: "岗位首页", exact: true }).click();
      await page.getByRole("heading", { name: heading, exact: true }).waitFor();
    }
    if (role === "driver") {
      const loading = calls.filter((call) =>
        call.path.endsWith("/loading-task/my-page"),
      );
      assert.ok(loading.length);
      const taskLink = page.locator('a[href^="/pda/driver-loading?"]').first();
      if (await taskLink.count()) {
        await taskLink.click();
        await page.waitForURL((url) => url.pathname === "/pda/driver-loading");
        assert.equal(
          await page
            .getByRole("button", { name: /扫码确认|确认出库|修改库位/ })
            .count(),
          0,
        );
      }
    }
    if (role === "readonly") {
      await page.getByRole("button", { name: "退出登录", exact: true }).click();
      await page
        .getByRole("button", { name: "退出", exact: true })
        .last()
        .click();
      await page.waitForURL((url) => url.pathname === "/login");
      await page
        .getByPlaceholder("账号", { exact: true })
        .fill(account.username);
      await page
        .getByPlaceholder("密码", { exact: true })
        .fill(account.password);
      await page.getByRole("button", { name: "登录", exact: true }).click();
      await page.waitForURL((url) => url.pathname === "/pda");
      await page.getByRole("heading", { name: heading, exact: true }).waitFor();
    }
    checks.push({
      role,
      heading,
      refresh: true,
      widths: [1440, 390, 320],
      businessMutations: calls.filter((call) => call.method !== "GET").length,
    });
    assert.equal(checks.at(-1).businessMutations, 0);
    await context.close();
  }
  assert.deepEqual(errors, []);
  const result = {
    result: "PASS",
    checks,
    roleActionsSource: "existing permissions",
    fieldAcceptance: false,
  };
  await fs.writeFile(
    path.join(output, "workspace-result.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result, null, 2));
} finally {
  await browser.close();
}
