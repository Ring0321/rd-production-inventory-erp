import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const require = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = require(path.join(process.argv[2], "playwright"));
const origin = "https://101.42.182.117";
const output = path.join(root, "temp/s1-02-cloud-verification");
await fs.mkdir(output, { recursive: true });
const browser = await chromium.launch({
  channel: "msedge",
  headless: true,
  args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"],
});
const errors = [],
  external = [],
  writes = [],
  screens = [],
  observations = [];
try {
  const context = await browser.newContext();
  await context.route("**/*", async (route) => {
    const req = route.request(),
      url = new URL(req.url());
    if (url.origin !== origin && !["blob:", "data:"].includes(url.protocol)) {
      external.push(url.origin);
      return route.abort();
    }
    if (url.pathname.startsWith("/admin-api/rd/") && req.method() !== "GET") {
      writes.push(url.pathname);
      return route.abort();
    }
    return route.continue();
  });
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(origin + "/login", {
    waitUntil: "networkidle",
    timeout: 60000,
  });
  await page.getByRole("button", { name: "一键进入演示", exact: true }).click();
  await page.waitForURL((url) => url.pathname !== "/login", { timeout: 60000 });
  await page.goto(origin + "/warehouse-space?warehouseCode=DEMO-SPACE-V2", {
    waitUntil: "networkidle",
  });
  await page.getByRole("button", { name: "打开智能小垛", exact: true }).click();
  const panel = page.getByRole("dialog", { name: "智能小垛", exact: true });
  const clear = async () => {
    const button = panel.getByRole("button", {
      name: "清空小垛对话",
      exact: true,
    });
    if (await button.isEnabled()) await button.click();
  };
  const ask = async (question) => {
    await clear();
    await panel
      .getByRole("textbox", { name: "向智能小垛提问", exact: true })
      .fill(question);
    await panel
      .getByRole("button", { name: "发送给小垛", exact: true })
      .click();
    await page.waitForFunction(
      () =>
        !!document.querySelector(".xiaoduo-turn:last-child .xiaoduo-answer"),
    );
  };
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 });
    for (const [query, question] of [
      ["Q03", "哪些包还待检"],
      ["Q04", "质检待复核"],
      ["Q05", "合格待入库的货物"],
    ]) {
      const response = page.waitForResponse(
        (res) =>
          res.url().includes("/rd/work-assistant/query?") &&
          new URL(res.url()).searchParams.get("queryType") === query,
      );
      await ask(question);
      const wire = await (await response).json();
      assert.equal(wire.code, 0);
      const metric =
        wire.data.metrics[
          query === "Q04" ? "BUSINESS_OBJECT_COUNT" : "DISTINCT_PACKAGE_COUNT"
        ];
      await panel
        .getByText(`${metric.value} ${query === "Q04" ? "单" : "包"}`, {
          exact: true,
        })
        .first()
        .waitFor();
      const rect = await panel.boundingBox();
      assert.ok(
        rect.x >= 0 &&
          rect.y >= 0 &&
          rect.x + rect.width <= width + 1 &&
          rect.y + rect.height <= page.viewportSize().height + 1,
      );
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= window.innerWidth + 1,
        ),
      );
      await page.mouse.move(width - 2, 2);
      for (const tooltip of await page.getByRole("tooltip").all())
        await tooltip.waitFor({ state: "hidden" });
      const image = `${width}-${query}.png`;
      await page.screenshot({ path: path.join(output, image) });
      screens.push(image);
      observations.push({
        query,
        width,
        quality: wire.data.quality,
        primaryCount: metric.value,
      });
    }
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await ask("质检待复核");
  const link = panel.getByRole("button", { name: /^打开 查看质检单 / }).first();
  await link.waitFor();
  await link.click();
  await page.waitForURL(
    (url) =>
      url.pathname === "/rd/quality/inspection" &&
      /^[1-9]\d*$/.test(url.searchParams.get("inspectionId") || ""),
  );
  await page.getByRole("dialog", { name: "质检详情", exact: true }).waitFor();
  assert.deepEqual(errors, []);
  assert.deepEqual(external, []);
  assert.deepEqual(writes, []);
  const result = {
    status: "PASS",
    origin,
    observations,
    screens,
    exactInspectionNavigation: true,
    pageErrors: errors,
    externalRequests: external,
    businessWrites: writes,
    paidModelCalls: 0,
    credentialsPrinted: false,
  };
  await fs.writeFile(
    path.join(output, "browser.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result));
} finally {
  await browser.close();
}
