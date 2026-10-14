import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import {
  prepareWarehouse,
  root,
  api,
  baseUrl,
} from "./RdWarehouseLocalFixture.mjs";

const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const fixture = await prepareWarehouse();
const { accounts, tokens, ready, extra, createLoading, sql } = fixture;
const loading = await createLoading(extra.bags.slice(0, 2), "QR-WEB-PENDING");
const output = path.join(root, "temp/qr-web-pending-verification");
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
const checks = [],
  screenshots = [],
  errors = [];
const contexts = [];
const packages = [
  ...fixture.upper.bags,
  ...fixture.extra.bags,
  ...fixture.lower.bags,
].map((bag) => bag.id);
assert.ok(packages.every((id) => Number.isSafeInteger(id) && id > 0));
const stock = async () =>
  JSON.stringify(
    await sql(
      `SELECT id,status,version,location_id,stock_lot_id FROM rd_package_unit WHERE id IN (${packages.join(",")}) ORDER BY id`,
    ),
  );
const screen = async (page, name) => {
  await page.screenshot({ path: path.join(output, name), fullPage: true });
  screenshots.push(name);
};
let failure;
try {
  const baseline = await stock();
  for (const kind of ["loading", "putaway"]) {
    const context = await browser.newContext({
      viewport: { width: 1440, height: 1000 },
    });
    contexts.push(context);
    const page = await context.newPage();
    page.on("pageerror", (error) => errors.push(error.message));
    const taskId = kind === "loading" ? loading.task.id : ready.task.id;
    const code =
      kind === "loading"
        ? loading.lines[0].packageCode
        : ready.lines[0].packageCode;
    const url = `/rd/stock/${kind}?mode=scan&taskId=${taskId}&packageCode=${code}`;
    await page.goto(`${baseUrl}/login?redirect=${encodeURIComponent(url)}`, {
      waitUntil: "networkidle",
    });
    await page
      .getByPlaceholder("账号", { exact: true })
      .fill(accounts.warehouse.username);
    await page
      .getByPlaceholder("密码", { exact: true })
      .fill(accounts.warehouse.password);
    await page.getByRole("button", { name: "登录", exact: true }).click();
    await page.waitForURL((u) => u.pathname !== "/login");
    await page
      .getByRole("button", { name: "连续扫码 / 待同步箱", exact: true })
      .waitFor();
    const attempts = [];
    let applied;
    let completeLost;
    const lostSettled = new Promise((resolve) => {
      completeLost = resolve;
    });
    await page.route(
      `**/admin-api/rd/${kind === "loading" ? "loading-task" : "putaway-task"}/batch-scan/confirm`,
      async (route) => {
        attempts.push(route.request().postDataJSON());
        const response = await route.fetch();
        const body = await response.json();
        assert.equal(body.code, 0);
        applied = body.data;
        if (attempts.length === 1) {
          await route.abort("failed");
          completeLost();
        } else await route.fulfill({ response });
      },
    );
    await page
      .getByRole("button", {
        name: kind === "loading" ? "确认已装包件" : "核验所选已到位包件",
        exact: true,
      })
      .click();
    await page
      .locator(".el-message-box")
      .getByRole("button", { name: "确定", exact: true })
      .click();
    await page
      .getByRole("button", { name: "重试原核验请求", exact: true })
      .waitFor();
    await lostSettled;
    await page.waitForFunction(() =>
      [...document.querySelectorAll("button")].some(
        (button) =>
          button.textContent.includes("重试原核验请求") &&
          !button.classList.contains("is-loading"),
      ),
    );
    assert.equal(attempts.length, 1);
    assert.ok(applied.recordId && applied.confirmedCount >= 2);
    assert.ok(
      await page
        .getByRole("button", { name: "连续扫码 / 待同步箱", exact: true })
        .isDisabled(),
    );
    const storedBefore = await page.evaluate(() =>
      Object.fromEntries(
        Object.entries(localStorage).filter(([key]) =>
          key.startsWith("rd-warehouse-pending:"),
        ),
      ),
    );
    assert.equal(Object.keys(storedBefore).length, 1);
    await screen(page, `${kind}-lost-response-locked.png`);
    await page.reload({ waitUntil: "networkidle" });
    await page
      .getByRole("button", { name: "重试原核验请求", exact: true })
      .waitFor();
    assert.equal(
      await page
        .getByRole("button", { name: "连续扫码 / 待同步箱", exact: true })
        .count(),
      0,
    );
    const restored = await page.evaluate(() =>
      Object.fromEntries(
        Object.entries(localStorage).filter(([key]) =>
          key.startsWith("rd-warehouse-pending:"),
        ),
      ),
    );
    const before = JSON.parse(Object.values(storedBefore)[0]),
      after = JSON.parse(Object.values(restored)[0]);
    assert.deepEqual(after.request, before.request);
    assert.equal(after.uncertain, true);
    if (kind === "loading") {
      await page
        .getByRole("button", { name: "重试原核验请求", exact: true })
        .click();
      await page
        .getByRole("button", { name: "重试原核验请求", exact: true })
        .waitFor({ state: "hidden" });
      assert.equal(attempts.length, 2);
      assert.deepEqual(attempts[0], attempts[1]);
      assert.equal(applied.replayed, true);
    } else {
      await page
        .getByRole("button", { name: "核定原上架请求", exact: true })
        .click();
      await page
        .getByRole("button", { name: "重试原核验请求", exact: true })
        .waitFor({ state: "hidden" });
      assert.equal(
        attempts.length,
        1,
        "status reconciliation must not send a new confirmation",
      );
    }
    assert.equal(
      (
        await page.evaluate(() =>
          Object.keys(localStorage).filter((key) =>
            key.startsWith("rd-warehouse-pending:"),
          ),
        )
      ).length,
      0,
    );
    const detail = await api(
      `/rd/${kind === "loading" ? "loading-task" : "putaway-task"}/get?id=${taskId}`,
      tokens.warehouse,
    );
    assert.equal(detail.task.scannedPackageCount, kind === "loading" ? 2 : 3);
    assert.equal(
      detail.task.status,
      kind === "loading" ? "PENDING_REVIEW" : "PENDING_CONFIRMATION",
    );
    assert.equal(
      await stock(),
      baseline,
      "arrival/loading verification cannot perform final inventory or position posting",
    );
    await screen(page, `${kind}-original-request-reconciled.png`);
    checks.push(
      `${kind}: actual server applied once then response deliberately lost; scan/transfer locked; reload retained exact request; ${kind === "loading" ? "retry used identical key/lines and replayed" : "APPLIED status plus original-task read resolved without repost"}; final stock untouched`,
    );
  }
  assert.equal(errors.length, 0);
} catch (cause) {
  failure = cause;
} finally {
  await fs.writeFile(
    path.join(output, "result.json"),
    JSON.stringify(
      {
        pass: !failure,
        checks,
        screenshots,
        errors,
        fixture: {
          marker: "QR-S Web pending local verification, not field data",
          putawayTaskId: ready.task.id,
          loadingTaskId: loading.task.id,
        },
        ...(failure ? { failure: failure.message } : {}),
      },
      null,
      2,
    ),
  );
  for (const context of contexts) await context.close();
  await browser.close();
}
if (failure) throw failure;
console.log(
  JSON.stringify({
    pass: true,
    checks: checks.length,
    screenshots: screenshots.length,
    output,
  }),
);
