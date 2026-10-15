import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { createRequire } from "node:module";
import { root, baseUrl, api, login } from "./RdQrLocalFixture.mjs";
import { CUSTOMER, MARKER } from "./Seed-RdFlowDemo.mjs";

assert.equal(baseUrl, "http://127.0.0.1:9000");
const output = path.join(root, "temp/flow-showcase-verification");
await fs.mkdir(output, { recursive: true });
const settings = Object.fromEntries(
  (
    await fs.readFile(
      path.join(root, "temp/local-runtime-patent/.env.local"),
      "utf8",
    )
  )
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => [
      line.slice(0, line.indexOf("=")),
      line.slice(line.indexOf("=") + 1),
    ]),
);
const privateFixture = JSON.parse(
  await fs.readFile(path.join(root, "temp/qr-e4-fixture.json"), "utf8"),
);
const admin = {
  username: "admin",
  password: settings.RD_ADMIN_INITIAL_PASSWORD,
};
const token = await login(admin);
const hash = (value) =>
  createHash("sha256").update(JSON.stringify(value)).digest("hex");
const allPackages = async () => {
  const records = [];
  for (let pageNo = 1; pageNo <= 200; pageNo++) {
    const page = await api(
      `/rd/package-unit/page?pageNo=${pageNo}&pageSize=100`,
      token,
    );
    assert.ok(Array.isArray(page.list) && Number.isSafeInteger(page.total));
    records.push(...page.list);
    if (records.length >= page.total) {
      assert.equal(records.length, page.total);
      assert.equal(new Set(records.map((row) => row.id)).size, records.length);
      return records.sort((a, b) => a.id - b.id);
    }
  }
  throw new Error("Package pagination did not finish");
};
if (process.argv.includes("--baseline")) {
  const packages = await allPackages();
  const locations = await api("/rd/putaway-task/location-map", token);
  const baseline = { packages, locations };
  await fs.writeFile(
    path.join(output, "preexisting-private-baseline.json"),
    JSON.stringify(baseline),
    { mode: 0o600 },
  );
  console.log(
    JSON.stringify({
      mode: "baseline",
      packageCount: packages.length,
      locationCount: locations.length,
      hash: hash(baseline),
    }),
  );
} else {
  const candidates = await api(
    `/rd/production-task/page?pageNo=1&pageSize=100&customerName=${encodeURIComponent(CUSTOMER)}`,
    token,
  );
  const matching = candidates.list.filter(
    (row) => row.customerName === CUSTOMER && row.remark?.includes(MARKER),
  );
  assert.equal(
    matching.length,
    1,
    "one curated demo task, not arbitrary verification fixtures",
  );
  const task = matching[0];
  const bags = await api(
    `/rd/package-unit/task-packages?productionTaskId=${task.id}`,
    token,
  );
  assert.equal(bags.length, 30);
  assert.equal(
    bags.reduce((n, bag) => n + bag.packageWeight, 0),
    29800,
  );
  const expectedCounts = {
    QC_PENDING: 3,
    QC_REJECTED: 2,
    QC_PASSED: 4,
    INBOUND_PENDING: 4,
    IN_STOCK: 12,
    OUTBOUNDED: 5,
  };
  assert.deepEqual(
    Object.fromEntries(
      Object.keys(expectedCounts).map((key) => [
        key,
        bags.filter((row) => row.status === key).length,
      ]),
    ),
    expectedCounts,
  );
  const preexisting = JSON.parse(
    await fs.readFile(
      path.join(output, "preexisting-private-baseline.json"),
      "utf8",
    ),
  );
  const originalIds = new Set(preexisting.packages.map((row) => row.id));
  const originalLocations = new Set(preexisting.locations.map((row) => row.id));
  const currentGoods = await allPackages();
  const currentLocations = await api("/rd/putaway-task/location-map", token);
  assert.deepEqual(
    currentGoods.filter((row) => originalIds.has(row.id)),
    preexisting.packages,
    "seeding preserves every pre-existing package field",
  );
  assert.deepEqual(
    currentLocations.filter((row) => originalLocations.has(row.id)),
    preexisting.locations,
    "seeding preserves every pre-existing location",
  );
  const before = hash({ goods: currentGoods, locations: currentLocations });
  const require = createRequire(
    path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
  );
  const { chromium } = require("playwright");
  const sharp = require("sharp");
  const browser = await chromium.launch({
    channel: "msedge",
    headless: true,
    args: ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"],
  });
  const errors = [],
    external = [],
    writes = [],
    checks = [],
    screenshots = [],
    contexts = [];
  const makePage = async (width = 1440) => {
    const context = await browser.newContext({
      viewport: { width, height: width < 600 ? 1000 : 1080 },
    });
    contexts.push(context);
    await context.route("**/*", (route) =>
      new URL(route.request().url()).origin === new URL(baseUrl).origin
        ? route.continue()
        : route.abort(),
    );
    const page = await context.newPage();
    page.on("pageerror", (error) => errors.push(error.message));
    page.on("console", (message) => {
      if (message.type() === "error") errors.push(message.text());
    });
    page.on("request", (request) => {
      const url = new URL(request.url());
      if (url.origin !== new URL(baseUrl).origin) external.push(url.pathname);
      if (
        url.pathname.startsWith("/admin-api/rd/") &&
        !["GET", "HEAD"].includes(request.method())
      )
        writes.push({ path: url.pathname, method: request.method() });
    });
    return page;
  };
  const logIn = async (page, account) => {
    await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
    await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
    await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
    await page.getByRole("button", { name: "登录", exact: true }).click();
    await page.waitForURL((url) => !url.pathname.includes("/login"));
  };
  const screenshot = async (page, name, locator) => {
    await page.mouse.move(0, 0);
    if (locator) await locator.scrollIntoViewIfNeeded();
    const bounds = locator ? await locator.boundingBox() : undefined;
    if (locator && bounds && bounds.height < page.viewportSize().height - 130)
      await locator.screenshot({
        path: path.join(output, name),
        animations: "disabled",
      });
    else
      await page.screenshot({
        path: path.join(output, name),
        animations: "disabled",
      });
    screenshots.push(name);
  };
  try {
    const page = await makePage();
    await logIn(page, admin);
    await page.goto(`${baseUrl}/business-flow`, { waitUntil: "networkidle" });
    const main = page.getByRole("main", { name: "业务全景", exact: true });
    await main.locator(".package-evidence img").waitFor();
    assert.equal(await main.locator(".pipeline-track button").count(), 8);
    assert.match(
      await main.locator(".task-identity").innerText(),
      /演示客户·宁川新材/,
    );
    assert.match(await main.locator(".flow-metrics").innerText(), /29,800/);
    assert.match(await main.locator(".demo-notice").innerText(), /非现场实货/);
    assert.equal(await main.locator(".flow-metrics dl").count(), 5);
    await screenshot(page, "01-business-flow-desktop.png");
    await screenshot(
      page,
      "02-business-flow-pipeline.png",
      main.locator(".flow-pipeline"),
    );
    checks.push(
      "Eight-stage workflow and 30 bag / 29800 kg totals from actual task membership",
    );

    await main
      .getByRole("button", { name: "查看入库上架流程", exact: true })
      .click();
    assert.match(
      await main.locator(".stage-handoff").innerText(),
      /仓管确认实际位置和入库/,
    );
    await main.getByRole("button", { name: /^已出库\s+5$/ }).click();
    const tail = bags.find(
      (bag) => bag.status === "OUTBOUNDED" && bag.packageWeight === 800,
    );
    assert.ok(tail);
    await main
      .getByRole("button", { name: tail.packageCode, exact: true })
      .click();
    await page.waitForFunction(
      (code) =>
        document.querySelector(".package-heading h2")?.textContent === code &&
        document.querySelector(".bag-identity img")?.naturalWidth > 0,
      tail.packageCode,
    );
    assert.match(await main.locator(".bag-identity").innerText(), /800 kg/);
    await main
      .getByRole("button", { name: "刷新业务全景", exact: true })
      .click();
    await main.locator(".package-evidence img").waitFor();
    assert.match(
      await main.locator(".package-heading h2").innerText(),
      new RegExp(tail.packageCode),
    );
    const trace = await api(
      `/rd/package-unit/trace-detail/${tail.packageCode}`,
      token,
    );
    assert.equal(
      await main.locator(".event-timeline li").count(),
      trace.events.length,
    );
    const qr = await main.locator(".bag-identity img").getAttribute("src");
    assert.ok(
      (
        await sharp(Buffer.from(qr.split(",")[1], "base64")).stats()
      ).channels.some((channel) => channel.stdev > 50),
    );
    await screenshot(
      page,
      "03-shipped-tail-evidence.png",
      main.locator(".package-evidence"),
    );
    await main
      .getByRole("button", { name: "查看追溯关系图", exact: true })
      .click();
    const graph = page.getByRole("dialog", {
      name: "货物追溯关系",
      exact: true,
    });
    await graph.locator("canvas").first().waitFor();
    await page.waitForFunction(
      () =>
        document.querySelector('[aria-label="适配关系图"]')?.disabled === false,
    );
    assert.match(await graph.innerText(), new RegExp(tail.packageCode));
    await screenshot(page, "04-persisted-trace-relations.png", graph);
    await graph.locator(".el-dialog__headerbtn").click();
    checks.push(
      "Actual shipped tail, original QR image, exact trace event count and persisted graph",
    );

    await main.getByRole("button", { name: /^检验不合格\s+2$/ }).click();
    await main.locator(".package-evidence img").waitFor();
    assert.match(await main.locator(".package-facts").innerText(), /2 \/ 1 包/);
    await screenshot(
      page,
      "05-rejected-batch-coverage.png",
      main.locator(".package-evidence"),
    );
    checks.push(
      "Rejected lot remains separate and exact 2 covered / 1 sampled are shown",
    );
    await main.getByRole("button", { name: /^在库\s+6$/ }).click();
    await main.locator(".package-evidence img").waitFor();
    const selectedCode = (
      await main.locator(".package-heading h2").innerText()
    ).trim();
    const selected = bags.find((row) => row.packageCode === selectedCode);
    assert.ok(selected?.locationId);
    await main
      .locator(".facts-grid")
      .getByRole("button", { name: selected.locationCode, exact: false })
      .click();
    await page.waitForURL(
      (url) =>
        url.pathname === "/warehouse-space" &&
        url.searchParams.get("locationId") === String(selected.locationId),
    );
    const space = page.getByRole("region", {
      name: "仓储空间总览",
      exact: true,
    });
    const canvas = space.locator('canvas[data-ready="true"]');
    await canvas.waitFor();
    const pixels = await canvas.evaluate((el) => {
      const gl = el.getContext("webgl2"),
        data = new Uint8Array(el.width * el.height * 4);
      gl.readPixels(0, 0, el.width, el.height, gl.RGBA, gl.UNSIGNED_BYTE, data);
      let ink = 0;
      for (let i = 0; i < data.length; i += 4)
        if (Math.min(data[i], data[i + 1], data[i + 2]) < 190) ink++;
      return ink;
    });
    assert.ok(pixels > 1000);
    await screenshot(page, "06-exact-demo-location-3d.png", space);
    checks.push(
      "Original registered location ID opens nonblank real 3D view, not guessed placement",
    );

    for (const width of [390, 320]) {
      await page.setViewportSize({ width, height: 1000 });
      await page.goto(`${baseUrl}/business-flow?taskId=${task.id}`, {
        waitUntil: "networkidle",
      });
      await main.locator(".package-evidence img").waitFor();
      await page.waitForFunction(() => {
        const el = document.querySelector(".business-flow");
        return el && el.scrollWidth <= el.clientWidth + 1;
      });
      assert.equal(await main.locator(".mobile-packages").isVisible(), true);
      await main.locator(".flow-heading").scrollIntoViewIfNeeded();
      await screenshot(page, `07-flow-${width}.png`);
      await screenshot(
        page,
        `07-pipeline-${width}.png`,
        main.locator(".flow-pipeline"),
      );
      await main.locator(".package-heading").scrollIntoViewIfNeeded();
      await screenshot(page, `08-flow-evidence-${width}.png`);
    }
    checks.push(
      "390 and 320 px native layout, mobile package selection and evidence remain inside container",
    );

    await page.setViewportSize({ width: 1440, height: 1080 });
    await page.goto(`${baseUrl}/business-flow?taskId=${task.id}`, {
      waitUntil: "networkidle",
    });
    await main.locator(".package-evidence img").waitFor();
    await main.getByRole("button", { name: /^装车异常\s+2$/ }).click();
    await main
      .locator(".package-evidence .task-evidence")
      .getByText("异常待处理", { exact: true })
      .waitFor();
    await screenshot(
      page,
      "09-loading-exception-evidence.png",
      main.locator(".package-evidence"),
    );
    await main.getByRole("button", { name: /^待出库\s+4$/ }).click();
    await main
      .locator(".package-evidence .task-evidence")
      .getByText("装车中", { exact: true })
      .waitFor();
    const activeCode = (
      await main.locator(".package-heading h2").innerText()
    ).trim();
    const activeContext = await api(
      `/rd/package-unit/work-context/${activeCode}`,
      token,
    );
    const actualLoading = activeContext.tasks.find(
      (item) => item.type === "LOADING",
    );
    assert.ok(actualLoading);
    await main
      .getByRole("button", { name: "查看提货装车流程", exact: true })
      .click();
    await main
      .getByRole("button", { name: "进入提货装车", exact: false })
      .click();
    await page.waitForURL(
      (url) =>
        url.searchParams.get("taskId") === String(actualLoading.taskId) &&
        url.searchParams.get("mode") === "view",
    );
    await page
      .getByText(actualLoading.taskNo, { exact: false })
      .first()
      .waitFor();
    await screenshot(page, "10-original-loading-task-view.png");
    checks.push(
      "Actual loading and exception tasks split from IN_STOCK; stage opens exact task with mode=view",
    );

    await page.goto(`${baseUrl}/business-flow?taskId=${task.id}`, {
      waitUntil: "networkidle",
    });
    await main.locator(".package-evidence img").waitFor();
    let injectedRenewal = false;
    await page.route("**/rd/production-task/get?*", async (route) => {
      if (injectedRenewal) return route.continue();
      injectedRenewal = true;
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          code: 401,
          msg: "Verification: token renewal path",
          data: null,
        }),
      });
    });
    await main
      .getByRole("button", { name: "刷新业务全景", exact: true })
      .click();
    await main
      .getByRole("button", { name: "重新载入并核对权限", exact: true })
      .waitFor();
    assert.equal(await main.locator(".flow-metrics").count(), 0);
    await main
      .getByRole("button", { name: "重新载入并核对权限", exact: true })
      .click();
    await main.locator(".package-evidence img").waitFor();
    assert.match(await main.locator(".flow-metrics").innerText(), /29,800/);
    await page.unroute("**/rd/production-task/get?*");
    checks.push(
      "Injected expiry triggers actual session renewal, clears old data, and explicit reload rechecks server permissions",
    );

    await page.goto(`${baseUrl}/business-flow?taskId=invalid`, {
      waitUntil: "networkidle",
    });
    assert.match(await main.innerText(), /链接标识无效/);
    assert.equal(await main.locator(".flow-metrics").count(), 0);
    await screenshot(page, "09-invalid-task-no-fallback.png", main);
    const driverPage = await makePage();
    await logIn(driverPage, privateFixture.accounts.driver);
    let driverReads = 0;
    driverPage.on("request", (request) => {
      if (/\/rd\/(production-task|package-unit)/.test(request.url()))
        driverReads++;
    });
    await driverPage.goto(`${baseUrl}/business-flow`, {
      waitUntil: "networkidle",
    });
    assert.equal(new URL(driverPage.url()).pathname, "/403");
    assert.equal(driverReads, 0);
    const publicPage = await makePage();
    let anonymousReads = 0;
    publicPage.on("request", (request) => {
      if (request.url().includes("/admin-api/rd/")) anonymousReads++;
    });
    await publicPage.goto(`${baseUrl}/business-flow`, {
      waitUntil: "networkidle",
    });
    assert.equal(new URL(publicPage.url()).pathname, "/login");
    assert.equal(anonymousReads, 0);
    checks.push(
      "Invalid task never falls back to demo; driver gets 403; anonymous gets login, no goods reads",
    );

    assert.deepEqual(writes, []);
    assert.deepEqual(external, []);
    assert.deepEqual(errors, []);
    const after = hash({
      goods: await allPackages(),
      locations: await api("/rd/putaway-task/location-map", token),
    });
    assert.equal(
      after,
      before,
      "all original and demo goods/locations unchanged by read-only showcase",
    );
    checks.push(
      "All pre-existing records preserved by seeding; all goods/locations unchanged by viewing; zero business writes/external requests/browser errors",
    );
    const result = {
      result: "PASS",
      taskId: task.id,
      packageCount: 30,
      weightKg: 29800,
      expectedCounts,
      checks,
      screenshots,
      fingerprint: before,
      browserErrors: errors,
      external,
      writes,
    };
    await fs.writeFile(
      path.join(output, "result.json"),
      JSON.stringify(result, null, 2),
    );
    console.log(JSON.stringify(result, null, 2));
  } catch (error) {
    await fs.writeFile(
      path.join(output, "failure.json"),
      JSON.stringify(
        {
          result: "FAIL",
          message: error.message,
          checks,
          screenshots,
          errors,
          external,
          writes,
        },
        null,
        2,
      ),
    );
    throw error;
  } finally {
    for (const context of contexts) await context.close();
    await browser.close();
  }
}
