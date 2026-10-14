import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { createHash } from "node:crypto";
import {
  prepareWarehouse,
  root,
  api,
  request,
  baseUrl,
} from "./RdWarehouseLocalFixture.mjs";

const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const sharp = require("sharp");
const frontendRequire = createRequire(path.join(root, "frontend/package.json"));
const QRCode = frontendRequire("qrcode");
const output = path.join(root, "temp/qr-workbench-verification");
await fs.mkdir(output, { recursive: true });
const checks = [],
  screenshots = [],
  errors = [],
  external = [],
  writes = [],
  wasm = [];
const contexts = [];
const fixture = await prepareWarehouse({ extraCount: 3, lowerCount: 3 });
const { tokens, accounts, extra, lower, upper, ready, createLoading, sql } =
  fixture;
const loading = await createLoading(
  [extra.bags[0], extra.bags[1], lower.bags[0]],
  "QR-S",
);
const taskUrl = (kind, id) => `/pda/warehouse?section=${kind}&taskId=${id}`;
const getTask = () =>
  api(`/rd/loading-task/get?id=${loading.task.id}`, tokens.warehouse);
const bagIds = [...upper.bags, ...extra.bags, ...lower.bags].map((b) => b.id);
assert.ok(bagIds.every((id) => Number.isSafeInteger(id) && id > 0));
const fingerprint = async () =>
  JSON.stringify({
    goods: await fixture.fingerprint(),
    loading: await getTask(),
    journal: await sql(
      `SELECT * FROM rd_stock_transaction WHERE package_id IN (${bagIds.join(",")}) ORDER BY id`,
    ),
    claims: await sql(
      `SELECT * FROM rd_loading_package_claim WHERE task_id=${loading.task.id} ORDER BY id`,
    ),
    scans: await sql(
      `SELECT * FROM rd_loading_scan_record WHERE task_id=${loading.task.id} ORDER BY id`,
    ),
  });
const hash = (value) => createHash("sha256").update(value).digest("hex");
const image = async (name, bags) => {
  const buffers = await Promise.all(
    bags.map((bag) =>
      QRCode.toBuffer(`${baseUrl}/pkg/${bag.packageCode}`, {
        width: 256,
        margin: 4,
        errorCorrectionLevel: "M",
      }),
    ),
  );
  const filename = path.join(output, name);
  await sharp({
    create: { width: 840, height: 360, channels: 3, background: "#ffffff" },
  })
    .composite(
      buffers.map((input, index) => ({
        input,
        left: 24 + index * 280,
        top: 52,
      })),
    )
    .png()
    .toFile(filename);
  return filename;
};
const sheet = await image("three-visible-labels.png", [
  extra.bags[0],
  lower.bags[0],
  lower.bags[1],
]);
const upperSheet = await image("putaway-visible-labels.png", upper.bags);
const cameraFile = path.join(output, "visible-labels-camera.y4m");
await promisify(execFile)(
  "ffmpeg",
  [
    "-hide_banner",
    "-loglevel",
    "error",
    "-y",
    "-loop",
    "1",
    "-i",
    sheet,
    "-t",
    "3",
    "-r",
    "5",
    "-vf",
    "scale=960:540,format=yuv420p",
    "-f",
    "yuv4mpegpipe",
    cameraFile,
  ],
  { maxBuffer: 1024 * 1024 },
);

const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
  args: [
    "--use-fake-ui-for-media-stream",
    "--use-fake-device-for-media-stream",
    `--use-file-for-fake-video-capture=${cameraFile}`,
  ],
});
const attach = (page) => {
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (req) => {
    const url = new URL(req.url());
    if (
      url.protocol.startsWith("http") &&
      !/^(127\.0\.0\.1|localhost)$/.test(url.hostname)
    )
      external.push({
        origin: url.origin,
        path: url.pathname,
        icons: url.searchParams.get("icons"),
      });
    if (/\.wasm$/.test(url.pathname)) wasm.push(url.pathname);
    if (/\/admin-api\/rd\//.test(url.pathname) && req.method() !== "GET")
      writes.push(url.pathname);
  });
};
const signIn = async (account, suffix, width = 1440) => {
  const context = await browser.newContext({
    viewport: { width, height: 1000 },
  });
  contexts.push(context);
  const page = await context.newPage();
  attach(page);
  await page.goto(`${baseUrl}/login?redirect=${encodeURIComponent(suffix)}`, {
    waitUntil: "networkidle",
  });
  await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname !== "/login");
  return page;
};
const work = (page) =>
  page.getByRole("region", { name: "连续扫码工作区", exact: true });
const openWork = async (page) => {
  await page
    .getByRole("button", { name: "连续扫码 / 待同步箱", exact: true })
    .filter({ visible: true })
    .click();
  await work(page).waitFor();
  return work(page);
};
const capture = async (page, raw) => {
  await work(page)
    .getByRole("textbox", { name: "采集包号", exact: true })
    .fill(raw);
  await work(page)
    .getByRole("button", { name: "采集到本机", exact: true })
    .click();
};
const screenshot = async (page, filename, locator, pixel = false) => {
  if (locator) await locator.scrollIntoViewIfNeeded();
  const bounds = locator ? await locator.boundingBox() : null;
  if (bounds) {
    const width = page.viewportSize().width;
    assert.ok(
      bounds.x >= -1 && bounds.x + bounds.width <= width + 1,
      `${filename}: viewport overflow`,
    );
  }
  if (locator && (await locator.locator(".candidate-row").count())) {
    const readable = await locator
      .locator(".candidate-row")
      .evaluateAll((rows) =>
        rows.every((row) => {
          const text = [
            ...row.querySelectorAll(
              ":scope > div > strong, :scope > div > span, :scope > div > small",
            ),
          ].map((el) => el.getBoundingClientRect());
          return text.every(
            (rect, index) =>
              rect.height >= 10 &&
              (!index || rect.top >= text[index - 1].bottom - 1),
          );
        }),
      );
    assert.ok(readable, `${filename}: candidate fields must not overlap`);
  }
  const filenameFull = path.join(output, filename);
  await page.mouse.move(0, 0);
  if (await page.locator(".relations-workspace").isVisible()) {
    await page.waitForFunction(() => {
      const button = document.querySelector('button[aria-label="放大关系图"]');
      return (
        button &&
        !button.disabled &&
        [...document.querySelectorAll(".relation-detail .el-tag")].every(
          (tag) => Number(getComputedStyle(tag).opacity) >= 0.9,
        )
      );
    });
  }
  if (locator)
    await locator.locator(".el-loading-mask").waitFor({ state: "hidden" });
  await page.screenshot({ path: filenameFull, fullPage: true });
  if (pixel) {
    const buffer = await locator.screenshot();
    const { data, info } = await sharp(buffer)
      .removeAlpha()
      .raw()
      .toBuffer({ resolveWithObject: true });
    const distinct = new Set();
    for (
      let i = 0;
      i < data.length;
      i += Math.max(info.channels, info.channels * 7)
    )
      distinct.add(`${data[i]},${data[i + 1]},${data[i + 2]}`);
    assert.ok(distinct.size > 24, `${filename}: canvas must be nonblank`);
    screenshots.push({ filename, bounds, distinctColors: distinct.size });
  } else screenshots.push({ filename, bounds });
};
const closeWork = async (page) => {
  await page
    .getByRole("dialog", { name: "扫码工作区", exact: true })
    .locator(".el-dialog__headerbtn")
    .click();
  await work(page).waitFor({ state: "hidden" });
};
const clickGraphFill = async (page, locator, color) => {
  await page.waitForFunction(
    () => !document.querySelector('button[aria-label="放大关系图"]')?.disabled,
  );
  const bounds = await locator.boundingBox();
  const { data, info } = await sharp(await locator.screenshot())
    .removeAlpha()
    .raw()
    .toBuffer({ resolveWithObject: true });
  let left = info.width,
    right = -1,
    top = info.height,
    bottom = -1;
  for (let y = 0; y < info.height; y++)
    for (let x = 0; x < info.width; x++) {
      const index = (y * info.width + x) * info.channels;
      if (
        color.every(
          (value, channel) => Math.abs(data[index + channel] - value) <= 2,
        )
      ) {
        left = Math.min(left, x);
        right = Math.max(right, x);
        top = Math.min(top, y);
        bottom = Math.max(bottom, y);
      }
    }
  assert.ok(
    right - left > 40 && bottom - top > 20,
    "Actual visible graph node fill must exist",
  );
  await page.mouse.click(
    bounds.x + (left + right) / 2,
    bounds.y + (top + bottom) / 2,
  );
};
const assertCounts = async (page, local, verified, conflicts) => {
  await page.waitForFunction(
    ({ local, verified, conflicts }) => {
      const el = document.querySelector(".scan-summary");
      return (
        el &&
        el.textContent.includes(`本机待核 ${local} 包`) &&
        el.textContent.includes(`本次核对 ${verified} 包`) &&
        el.textContent.includes(`冲突 / 已处理 ${conflicts} 包`)
      );
    },
    { local, verified, conflicts },
  );
};

let failure;
try {
  const code = extra.bags[1].packageCode;
  const graph = await api(
    `/rd/package-unit/relations?packageCode=${code}`,
    tokens.admin,
  );
  assert.equal(graph.rootPackageCode, code);
  assert.ok(
    graph.nodes.some((node) => node.packageCode === extra.bags[0].packageCode),
  );
  const qc = graph.nodes.find((node) => node.kind === "QUALITY_INSPECTION");
  assert.ok(qc, "actual quality coverage must be available");
  const coverage = graph.edges.filter(
    (edge) =>
      edge.fromNodeKey === qc.nodeKey && edge.relation === "QUALITY_COVERAGE",
  );
  assert.equal(coverage.length, 3);
  assert.equal(coverage.filter((edge) => edge.sampled).length, 1);
  assert.ok(
    coverage.every(
      (edge) =>
        edge.evidenceType === "QUALITY_INSPECTION_MEMBER" && edge.evidenceId,
    ),
  );
  assert.ok(
    !graph.nodes.some((node) => node.packageCode === lower.bags[1].packageCode),
    "same task or warehouse must not imply coverage",
  );
  assert.ok(
    !JSON.stringify(graph).match(
      /customerName|personName|driverPhone|idempotencyKey|remark/,
    ),
  );
  for (const actor of ["driver", "otherDriver", "production", "quality"]) {
    const response = await request(
      `/rd/package-unit/relations?packageCode=${code}`,
      tokens[actor],
    );
    if (actor === "driver" || actor === "otherDriver")
      assert.equal(response.code, 403);
    else assert.ok([0, 403].includes(response.code));
  }
  assert.ok(
    [401, 403].includes(
      (await request(`/rd/package-unit/relations?packageCode=${code}`)).code,
    ),
  );
  checks.push(
    "live persisted production-output and quality-member relationships, one actual sample, unrelated peer excluded, no private identity fields, driver/anonymous denied",
  );
  const baseline = await fingerprint();
  const keeper = await signIn(
    accounts.warehouse,
    taskUrl("loading", loading.task.id),
    1440,
  );
  await openWork(keeper);
  await work(keeper)
    .getByLabel("本机标签图片", { exact: true })
    .setInputFiles(sheet);
  await assertCounts(keeper, 2, 0, 1);
  assert.ok((await work(keeper).textContent()).includes("1800 kg"));
  assert.ok(
    await work(keeper)
      .getByRole("button", { name: "带回所选核验清单", exact: true })
      .isDisabled(),
  );
  await screenshot(
    keeper,
    "01-desktop-image-multicode.png",
    work(keeper),
    true,
  );
  await work(keeper)
    .getByLabel("本机标签图片", { exact: true })
    .setInputFiles(sheet);
  await work(keeper)
    .locator(".observations")
    .getByText("重复识别", { exact: true })
    .first()
    .waitFor();
  await assertCounts(keeper, 2, 0, 1);
  await work(keeper)
    .getByRole("button", { name: "重新核对服务器", exact: true })
    .click();
  await assertCounts(keeper, 0, 2, 1);
  const tailChoice = work(keeper)
    .locator(".candidate-row")
    .filter({ hasText: lower.bags[0].packageCode });
  await tailChoice.locator("label.el-checkbox").click();
  assert.ok(!(await tailChoice.getByRole("checkbox").isChecked()));
  await work(keeper)
    .getByRole("button", { name: "带回所选核验清单", exact: true })
    .click();
  await work(keeper).waitFor({ state: "hidden" });
  await keeper.getByText("本次 1 包 / 1000 kg", { exact: true }).waitFor();
  assert.equal(
    await fingerprint(),
    baseline,
    "selection transfer cannot post stock, create scans, claims or update positions",
  );
  checks.push(
    "real local-WASM image decoded three visible labels; two exact task members + one wrong package; duplicates not counted; actual 1800kg including 800kg tail; explicit recheck and selected-subset transfer only",
  );

  await openWork(keeper);
  await assertCounts(keeper, 2, 0, 1);
  await keeper.context().setOffline(true);
  await capture(keeper, extra.bags[1].packageCode);
  await assertCounts(keeper, 3, 0, 1);
  await work(keeper)
    .getByRole("button", { name: "重新核对服务器", exact: true })
    .click();
  await work(keeper).getByRole("alert").waitFor();
  assert.ok(
    await work(keeper)
      .getByRole("button", { name: "带回所选核验清单", exact: true })
      .isDisabled(),
  );
  await screenshot(keeper, "02-offline-pending.png", work(keeper));
  await keeper.context().setOffline(false);
  await keeper.reload({ waitUntil: "networkidle" });
  await keeper
    .getByRole("region", { name: "本机待同步箱总览", exact: true })
    .getByText("4 包候选 · 1 个任务", { exact: true })
    .waitFor();
  await openWork(keeper);
  await assertCounts(keeper, 3, 0, 1);
  assert.ok(
    await work(keeper)
      .getByRole("button", { name: "带回所选核验清单", exact: true })
      .isDisabled(),
  );
  for (const width of [390, 320]) {
    await keeper.setViewportSize({ width, height: 900 });
    await screenshot(keeper, `03-mobile-${width}-pending.png`, work(keeper));
  }
  await work(keeper)
    .getByRole("button", { name: "重新核对服务器", exact: true })
    .click();
  await assertCounts(keeper, 0, 3, 1);
  await closeWork(keeper);
  assert.equal(await fingerprint(), baseline);
  checks.push(
    "offline collection remains local, server check fails closed, reload preserves user/task-scoped drafts, previously verified records need fresh recheck, 390/320px layouts fit, no automatic posting",
  );

  const camera = await signIn(
    accounts.warehouse,
    taskUrl("loading", loading.task.id),
    1440,
  );
  await openWork(camera);
  await work(camera)
    .getByRole("button", { name: "开启相机", exact: true })
    .click();
  await assertCounts(camera, 2, 0, 1);
  await work(camera).locator("video").waitFor();
  await screenshot(
    camera,
    "04-real-decoder-camera-testframes.png",
    work(camera),
    true,
  );
  await closeWork(camera);
  await camera.waitForFunction(() => !document.querySelector("video"));
  assert.equal(await fingerprint(), baseline);
  checks.push(
    "real getUserMedia/QrcodeStream decoded synthetic video frames, multi-code classification and close stopped camera; not a physical-device acceptance",
  );

  const putaway = await signIn(
    accounts.warehouse,
    taskUrl("putaway", ready.task.id),
    390,
  );
  await openWork(putaway);
  await work(putaway)
    .getByLabel("本机标签图片", { exact: true })
    .setInputFiles(upperSheet);
  await assertCounts(putaway, 3, 0, 0);
  await work(putaway)
    .getByRole("button", { name: "重新核对服务器", exact: true })
    .click();
  await assertCounts(putaway, 0, 3, 0);
  await screenshot(putaway, "05-putaway-mobile.png", work(putaway), true);
  await closeWork(putaway);
  assert.equal(await fingerprint(), baseline);
  checks.push(
    "putaway scanner uses actual assigned members and net weights, never confirms warehouse location or stock by itself",
  );

  const trace = await signIn(accounts.admin, `/pkg/${code}?internal=1`, 1440);
  await trace
    .getByRole("button", { name: "查看追溯关系图", exact: true })
    .click();
  const relation = trace.getByRole("region", {
    name: "货物追溯关系图",
    exact: true,
  });
  await relation
    .getByRole("button", { name: "放大关系图", exact: true })
    .waitFor();
  await trace.waitForFunction(
    () =>
      document.querySelector(".relations-canvas canvas") &&
      !document.querySelector(".node-fallback"),
  );
  await screenshot(
    trace,
    "06-relations-desktop.png",
    relation.getByRole("img", { name: "真实记录关系画布", exact: true }),
    true,
  );
  await relation
    .getByRole("button", { name: "放大关系图", exact: true })
    .click();
  await relation
    .getByRole("button", { name: "适配关系图", exact: true })
    .click();
  await relation
    .getByRole("combobox", { name: "选择关系节点", exact: true })
    .press("ArrowDown");
  await trace
    .getByRole("option", { name: `质检记录 · ${qc.label}`, exact: true })
    .click();
  await relation
    .getByRole("heading", { name: "已加载实际覆盖 3 包", exact: true })
    .waitFor();
  const canvas = relation.getByRole("img", {
    name: "真实记录关系画布",
    exact: true,
  });
  await clickGraphFill(trace, canvas, [231, 248, 238]);
  await relation.getByRole("heading", { name: "包件", exact: true }).waitFor();
  await clickGraphFill(trace, canvas, [255, 246, 220]);
  await relation
    .getByRole("heading", { name: "已加载实际覆盖 3 包", exact: true })
    .waitFor();
  await screenshot(trace, "07-quality-actual-coverage.png", relation);
  await trace.setViewportSize({ width: 390, height: 900 });
  await screenshot(trace, "08-relations-mobile.png", relation);
  await trace.setViewportSize({ width: 1440, height: 1000 });
  const location = graph.nodes.find(
    (node) =>
      node.kind === "LOCATION" &&
      Number(node.locationId) === fixture.locationIds[0],
  );
  assert.ok(location);
  await relation
    .getByRole("combobox", { name: "选择关系节点", exact: true })
    .press("ArrowDown");
  await trace
    .getByRole("option", { name: `登记库位 · ${location.label}`, exact: true })
    .click();
  await relation
    .getByRole("button", { name: "查看登记库位空间", exact: true })
    .click();
  await trace.waitForURL(
    (url) =>
      url.pathname === "/warehouse-space" &&
      url.searchParams.get("locationId") === String(location.locationId),
  );
  await trace.waitForFunction(() =>
    document.querySelector(".warehouse-space canvas"),
  );
  await screenshot(
    trace,
    "09-relations-warehouse-space.png",
    trace.locator(".warehouse-space"),
    true,
  );
  assert.equal(await fingerprint(), baseline);
  checks.push(
    "G6 actual-record graph rendered nonblank, zoom/fit, actual quality-member highlight, mobile layout, registered-ID-only 3D warehouse link, readonly fingerprint unchanged",
  );

  const anonymous = await browser.newContext({
    viewport: { width: 390, height: 900 },
  });
  contexts.push(anonymous);
  const publicPage = await anonymous.newPage();
  attach(publicPage);
  const publicGraph = [];
  publicPage.on("request", (req) => {
    if (req.url().includes("/package-unit/relations"))
      publicGraph.push(req.url());
  });
  await publicPage.goto(`${baseUrl}/pkg/${code}`, { waitUntil: "networkidle" });
  assert.equal(
    await publicPage
      .getByRole("button", { name: "查看追溯关系图", exact: true })
      .count(),
    0,
  );
  assert.equal(publicGraph.length, 0);
  const driver = await signIn(accounts.driver, "/pda/warehouse", 390);
  assert.equal(
    await driver
      .getByRole("button", { name: "连续扫码 / 待同步箱", exact: true })
      .count(),
    0,
  );
  assert.equal(
    await driver
      .getByRole("region", { name: "本机待同步箱总览", exact: true })
      .count(),
    0,
  );
  checks.push(
    "public passport remains separate and makes no graph request; driver lacks keeper scanner and local draft overview",
  );

  await keeper.setViewportSize({ width: 390, height: 900 });
  await openWork(keeper);
  const beforeVersionChange = await fingerprint();
  await api("/rd/loading-task/exception", tokens.driver, "POST", {
    taskId: loading.task.id,
    exceptionType: "OTHER",
    description:
      "QR-S stale-version verification (local sample, not field exception)",
  });
  const afterVersionChange = await fingerprint();
  assert.notEqual(afterVersionChange, beforeVersionChange);
  await work(keeper)
    .getByRole("button", { name: "重新核对服务器", exact: true })
    .click();
  await assertCounts(keeper, 0, 0, 4);
  assert.ok(
    await work(keeper)
      .getByRole("button", { name: "带回所选核验清单", exact: true })
      .isDisabled(),
  );
  await screenshot(keeper, "10-stale-task-conflicts.png", work(keeper));
  assert.equal(
    await fingerprint(),
    afterVersionChange,
    "stale rejection must not add business writes",
  );
  checks.push(
    "actual server task version change marks all old-version drafts as conflict; no silent rebasing, transfer or posting",
  );
  assert.equal(
    writes.length,
    0,
    "all new browser operations must be read-only",
  );
  assert.equal(
    external.length,
    0,
    "no external decoder, graph, cloud or customer export",
  );
  assert.ok(
    wasm.some((url) => url.includes("zxing_reader")),
    "image reader must fetch a local WASM asset",
  );
  assert.equal(errors.length, 0, "no browser runtime error");
  checks.push(
    "no browser business writes, external requests or runtime errors; local reader WASM loaded",
  );
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
        external,
        writes,
        wasm: [...new Set(wasm)],
        fixture: {
          marker: "QR-S local non-field samples",
          productionTaskId: fixture.taskId,
          loadingTaskId: loading.task.id,
          putawayTaskId: ready.task.id,
          packageCodes: bagIds.map(
            (id) =>
              [...upper.bags, ...extra.bags, ...lower.bags].find(
                (bag) => bag.id === id,
              ).packageCode,
          ),
          locationIds: fixture.locationIds,
        },
        screenshotHashes: await Promise.all(
          screenshots.map(async ({ filename }) => ({
            filename,
            sha256: hash(await fs.readFile(path.join(output, filename))),
          })),
        ),
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
