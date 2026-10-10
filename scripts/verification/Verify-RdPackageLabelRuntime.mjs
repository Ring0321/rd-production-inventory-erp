import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { execFile } from "node:child_process";
import { promisify } from "node:util";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const output = path.join(root, "temp/qr-label-runtime-verification");
await fs.mkdir(output, { recursive: true });
const envFile =
  process.env.RD_ERP_ENV_FILE ||
  path.join(root, "temp/local-runtime-patent/.env.local");
const settings = Object.fromEntries(
  (await fs.readFile(envFile, "utf8"))
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => [
      line.slice(0, line.indexOf("=")),
      line.slice(line.indexOf("=") + 1),
    ]),
);
const baseUrl = `http://127.0.0.1:${settings.RD_WEB_PORT}`;
const requireFrontend = createRequire(path.join(root, "frontend/package.json"));
const { chromium } = process.env.RD_BROWSER_MODULES
  ? requireFrontend(path.join(process.env.RD_BROWSER_MODULES, "playwright"))
  : requireFrontend("playwright");
let accessToken;
const call = async (url, method = "GET", data) => {
  const response = await fetch(`${baseUrl}/admin-api${url}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      "tenant-id": "1",
      ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
    },
    ...(data ? { body: JSON.stringify(data) } : {}),
  });
  return response.json();
};
const api = async (url, method = "GET", data) => {
  const response = await call(url, method, data);
  assert.equal(response.code, 0, `${url}: ${response.msg}`);
  return response.data;
};
const login = await api("/system/auth/login", "POST", {
  username: "admin",
  password: settings.RD_ADMIN_INITIAL_PASSWORD,
});
accessToken = login.accessToken;
const existingProductionId = Number(process.env.RD_QR_LABEL_PRODUCTION_ID || 0);
const uiOnly = process.env.RD_QR_LABEL_UI_ONLY === "true";
assert.ok(
  !uiOnly || existingProductionId,
  "UI-only verification requires an existing marked test sample",
);
const productionId =
  existingProductionId ||
  (await api("/rd/production-output/create", "POST", {
    productionDate: "2026-10-11",
    customerName: "本地标签验证示例（非现场数据）",
    productModel: "E3-LABEL",
    furnaceNo: `E3-${Date.now()}`,
    furnaceLayer: "上层",
    outputQty: 10,
    packageWeight: 1000,
    sourceSheet: "E3 本地技术验收",
    remark: "模拟标签打印确认，不代表实际打印机或生产检测数据",
  }));
const production = await api(`/rd/production-output/get?id=${productionId}`);
assert.equal(
  production.sourceSheet,
  "E3 本地技术验收",
  "Only the marked local test sample can be used",
);
if (!existingProductionId) {
  await api("/rd/production-output/confirm", "POST", {
    id: productionId,
    version: production.version,
  });
}
const packages = existingProductionId
  ? await api(`/rd/package-unit/trace?productionOutputId=${productionId}`)
  : await api("/rd/package-unit/generate", "POST", {
      productionOutputId: productionId,
      packageCount: 10,
    });
assert.equal(packages.length, 10);
assert.ok(
  uiOnly || packages.every((pkg) => pkg.status === "CREATED"),
  "The local test sample must not have been printed already",
);
const packageIds = packages.map((pkg) => pkg.id);
const readLogs = async () => {
  const query = `SELECT COUNT(*), COALESCE(SUM(reprint),0) FROM rd_label_print_log WHERE deleted=0 AND package_id IN (${packageIds.join(",")})`;
  const result = await promisify(execFile)(
    process.env.RD_MYSQL_BIN || "mysql",
    [
      "--host=127.0.0.1",
      "--user=root",
      `--port=${settings.RD_DB_PORT}`,
      "--batch",
      "--skip-column-names",
      "rd_erp",
      "-e",
      query,
    ],
    { env: { ...process.env, MYSQL_PWD: settings.RD_DB_PASSWORD } },
  );
  return result.stdout.trim().split(/\s+/).map(Number);
};
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL,
});
const page = await browser.newPage({ viewport: { width: 1440, height: 1050 } });
const errors = [];
page.on("pageerror", (error) => errors.push(error.message));
try {
  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("账号", { exact: true }).fill("admin");
  await page
    .getByPlaceholder("密码", { exact: true })
    .fill(settings.RD_ADMIN_INITIAL_PASSWORD);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"));
  await page.goto(
    `${baseUrl}/rd/stock/package?productionOutputId=${productionId}`,
    { waitUntil: "networkidle" },
  );
  await page
    .getByText(packages[0].packageCode, { exact: true })
    .first()
    .waitFor();
  if (!uiOnly) {
    await page.getByRole("button", { name: "整批打印", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "打印吨袋标签" });
    await dialog.waitFor();
    assert.equal(
      await dialog.locator(".el-table__body-wrapper tbody tr").count(),
      10,
    );
    assert.deepEqual(await readLogs(), [0, 0]);
    await page.screenshot({
      path: path.join(output, "batch-preview.png"),
      fullPage: true,
    });
    const popupPromise = page.waitForEvent("popup");
    await dialog
      .getByRole("button", { name: "打开打印窗口", exact: true })
      .click();
    const popup = await popupPromise;
    await popup.waitForLoadState("load");
    assert.equal(await popup.locator(".goods-label").count(), 10);
    await popup.pdf({
      path: path.join(output, "runtime-batch.pdf"),
      preferCSSPageSize: true,
    });
    assert.deepEqual(await readLogs(), [0, 0]);
    await dialog
      .getByRole("button", { name: "确认打印成功", exact: true })
      .click();
    await page
      .getByRole("button", { name: "确定", exact: true })
      .last()
      .click();
    await dialog.waitFor({ state: "hidden" });
    const printed = await api(
      `/rd/package-unit/trace?productionOutputId=${productionId}`,
    );
    assert.ok(printed.every((pkg) => pkg.status === "LABELED"));
    assert.deepEqual(await readLogs(), [10, 0]);
    const repeated = await call("/rd/package-unit/print-batch", "POST", {
      packageIds,
    });
    assert.notEqual(repeated.code, 0);
    assert.deepEqual(await readLogs(), [10, 0]);
    const noReason = await call("/rd/package-unit/reprint-batch", "POST", {
      packageIds: [packageIds[0]],
    });
    assert.notEqual(noReason.code, 0);
    assert.deepEqual(await readLogs(), [10, 0]);
    const row = page
      .locator(".el-table__body-wrapper tbody tr")
      .filter({ hasText: packages[0].packageCode })
      .first();
    await row.getByRole("button", { name: "补打", exact: true }).click();
    const reprintDialog = page.getByRole("dialog", { name: "补打标签" });
    await reprintDialog.waitFor();
    await reprintDialog.locator("textarea").fill("本地测试：标签破损补打");
    const reprintPopupPromise = page.waitForEvent("popup");
    await reprintDialog
      .getByRole("button", { name: "打开打印窗口", exact: true })
      .click();
    const reprintPopup = await reprintPopupPromise;
    await reprintPopup.waitForLoadState("load");
    assert.equal(
      await reprintPopup.locator(".package-code").innerText(),
      packages[0].packageCode,
    );
    await reprintDialog
      .getByRole("button", { name: "确认打印成功", exact: true })
      .click();
    await page
      .getByRole("button", { name: "确定", exact: true })
      .last()
      .click();
    await reprintDialog.waitFor({ state: "hidden" });
    assert.deepEqual(await readLogs(), [11, 1]);
    const afterReprint = await api(`/rd/package-unit/get?id=${packageIds[0]}`);
    assert.equal(afterReprint.status, "LABELED");
    assert.equal(afterReprint.packageCode, packages[0].packageCode);
  }
  const row = page
    .locator(".el-table__body-wrapper tbody tr")
    .filter({ hasText: packages[0].packageCode })
    .first();
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await row.getByRole("button", { name: "标签预览", exact: true }).click();
    const preview = page.getByRole("dialog", { name: "货品标识卡预览" });
    await preview.waitFor();
    const label = preview.frameLocator("iframe").locator(".goods-label");
    await label.waitFor();
    assert.ok((await label.innerText()).includes(packages[0].packageCode));
    assert.ok(
      await label.evaluate(
        (element) =>
          element.getBoundingClientRect().bottom <= window.innerHeight,
      ),
      `Label and QR must fit inside the preview at ${width}px`,
    );
    await page.screenshot({
      path: path.join(output, `label-preview-${width}.png`),
      fullPage: true,
      animations: "disabled",
    });
    const bounds = await preview.boundingBox();
    assert.ok(
      bounds && bounds.x >= 0 && bounds.x + bounds.width <= width + 1,
      `Dialog overflow at ${width}px`,
    );
    await preview.getByRole("button", { name: "关闭", exact: true }).click();
  }
  assert.deepEqual(errors, []);
  const record = uiOnly
    ? {
        result: "PASS",
        mode: "UI_ONLY",
        productionId,
        viewports: [1440, 390, 320],
      }
    : {
        result: "PASS",
        mode: "FULL",
        productionId,
        packageCodes: packages.map((pkg) => pkg.packageCode),
        firstPrintLogs: 10,
        reprintLogs: 1,
        duplicateRejected: true,
        missingReasonRejected: true,
        previewAndPrintWindowCreateNoLogs: true,
        viewports: [1440, 390, 320],
      };
  await fs.writeFile(
    path.join(output, uiOnly ? "ui-result.json" : "result.json"),
    JSON.stringify(record, null, 2),
  );
  console.log(
    JSON.stringify({
      result: "PASS",
      mode: uiOnly ? "UI_ONLY" : "FULL",
      productionId,
      output,
    }),
  );
} catch (error) {
  if (!page.url().includes("/login")) {
    await page.screenshot({
      path: path.join(output, "failure.png"),
      fullPage: true,
    });
    await fs.writeFile(
      path.join(output, "failure.txt"),
      await page.locator("body").innerText(),
    );
  }
  throw error;
} finally {
  await browser.close();
}
