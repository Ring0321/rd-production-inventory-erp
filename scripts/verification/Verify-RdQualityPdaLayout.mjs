import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { root, baseUrl, api, login } from "./RdQrLocalFixture.mjs";

const fixture = JSON.parse(
  await fs.readFile(path.join(root, "temp/qr-e4-fixture.json"), "utf8"),
);
const account = fixture.accounts.quality;
const token = await login(account);
const bags = await api(
  `/rd/package-unit/trace?productionOutputId=${fixture.labelOutputId}`,
  token,
);
assert.ok(bags.length > 0);
const rows = bags.map((bag) => ({ ...bag, eligible: true }));
const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
const out = path.join(root, "temp/qr-quality-batch-verification/layout-only");
await fs.mkdir(out, { recursive: true });
const mutations = [];
const errors = [];
try {
  const context = await browser.newContext({
    viewport: { width: 390, height: 900 },
  });
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (request) => {
    if (request.url().includes("/admin-api/rd/") && request.method() !== "GET")
      mutations.push(request.method());
  });
  // Rendering evidence only: this mocked scope does not validate real eligibility or backend behavior.
  await page.route("**/admin-api/rd/quality-inspection/scope?*", (route) =>
    route.fulfill({
      json: {
        code: 0,
        data: {
          representative: rows[0],
          productionOutputId: fixture.labelOutputId,
          packages: { list: rows, total: rows.length },
        },
      },
    }),
  );
  await page.goto(`${baseUrl}/pda/quality`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname === "/pda/quality");
  await page
    .getByLabel("包号 / 二维码", { exact: true })
    .fill(bags[0].packageCode);
  await page.getByRole("button", { name: "读取检验范围", exact: true }).click();
  await page.getByLabel("检测项1项目编码", { exact: true }).fill("TEST-SIC");
  await page
    .getByLabel("检测项1项目名称", { exact: true })
    .fill("界面排版验证（非现场测量）");
  await page
    .getByRole("spinbutton", { name: "检测项1标准下限", exact: true })
    .fill("97");
  await page
    .getByRole("spinbutton", { name: "检测项1实测值", exact: true })
    .fill("98");
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    await page.mouse.move(0, 0);
    await page.waitForFunction(() =>
      Array.from(document.querySelectorAll('[role="tooltip"]')).every(
        (node) => !node.getClientRects().length,
      ),
    );
    await page
      .getByLabel("检测项1项目名称", { exact: true })
      .scrollIntoViewIfNeeded();
    const measure = await page.evaluate(() => ({
      width: document.documentElement.clientWidth,
      scroll: document.documentElement.scrollWidth,
      inner: document.querySelector(".pda-content").clientWidth,
      innerScroll: document.querySelector(".pda-content").scrollWidth,
    }));
    await page.screenshot({
      path: path.join(out, `layout-${width}.png`),
      fullPage: true,
    });
    if (measure.scroll > measure.width || measure.innerScroll > measure.inner) {
      const overflowing = await page.evaluate(() =>
        Array.from(document.querySelectorAll(".quality-pda *"))
          .filter(
            (node) =>
              node.getBoundingClientRect().right >
              document.documentElement.clientWidth,
          )
          .slice(0, 12)
          .map((node) => ({
            tag: node.tagName,
            class: node.className,
            right: node.getBoundingClientRect().right,
            width: node.getBoundingClientRect().width,
          })),
      );
      console.log(JSON.stringify({ measure, overflowing }));
    }
    assert.ok(
      measure.scroll <= measure.width && measure.innerScroll <= measure.inner,
    );
  }
  assert.deepEqual(mutations, []);
  assert.deepEqual(errors, []);
  const result = {
    result: "PASS",
    scope: "mocked-scope-layout-only",
    widths: [1440, 390, 320],
    businessMutations: 0,
    realBackendAcceptance: false,
    fieldAcceptance: false,
  };
  await fs.writeFile(
    path.join(out, "result.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result));
} finally {
  await browser.close();
}
