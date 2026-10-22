import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { createHash, randomBytes, randomUUID } from "node:crypto";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const require = createRequire(path.join(root, "frontend/package.json"));
const origin = "http://127.0.0.1:9001";
const runtime = path.join(root, "temp/local-runtime-s102");
const output = path.join(root, "temp/s1-02-live-verification");
const privateFile = path.join(runtime, "work-query-fixture.private.json");
const settings = Object.fromEntries(
  (await fs.readFile(path.join(runtime, ".env.local"), "utf8"))
    .split(/\r?\n/)
    .filter((line) => line.includes("="))
    .map((line) => [
      line.slice(0, line.indexOf("=")),
      line.slice(line.indexOf("=") + 1),
    ]),
);
assert.equal(Number(settings.RD_DB_PORT), 3312);
assert.equal(Number(settings.RD_BACKEND_PORT), 48082);
assert.equal(Number(settings.RD_WEB_PORT), 9001);
const sql = async (statement) =>
  (
    await promisify(execFile)(
      "mysql",
      [
        "--host=127.0.0.1",
        "--port=3312",
        "--user=root",
        "--default-character-set=utf8mb4",
        "--batch",
        "--skip-column-names",
        "rd_erp",
        "-e",
        statement,
      ],
      { env: { ...process.env, MYSQL_PWD: settings.RD_DB_PASSWORD } },
    )
  ).stdout.trim();
const normalizePath = (value) =>
  value
    .replaceAll("\\", "/")
    .replace(/\/+/g, "/")
    .replace(/\/$/, "")
    .toLowerCase();
assert.equal(
  normalizePath(await sql("SELECT @@datadir;")),
  normalizePath(path.join(process.env.LOCALAPPDATA, "RdErp/mysql-data-s102")),
);
const request = async (url, token, method = "GET", body, tenant = "1") =>
  (
    await fetch(origin + "/admin-api" + url, {
      method,
      headers: {
        "Content-Type": "application/json",
        "tenant-id": tenant,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
      signal: AbortSignal.timeout(20000),
    })
  ).json();
const api = async (...args) => {
  const response = await request(...args);
  assert.equal(response.code, 0, `${args[0]}: ${response.msg}`);
  return response.data;
};
const login = async (account) =>
  (await api("/system/auth/login", null, "POST", account)).accessToken;
const admin = {
  username: "admin",
  password: settings.RD_ADMIN_INITIAL_PASSWORD,
};
const token = await login(admin);
const marker = "S1-02-ISOLATED-READ-VERIFICATION";
let fixture;
try {
  fixture = JSON.parse(await fs.readFile(privateFile, "utf8"));
  assert.equal(fixture.marker, marker);
} catch (error) {
  if (error.code !== "ENOENT") throw error;
  fixture = { marker, accounts: {} };
}
const save = () =>
  fs.writeFile(privateFile, JSON.stringify(fixture), { mode: 0o600 });
if (!fixture.packageIds) {
  assert.equal(
    Number(await sql("SELECT COUNT(*) FROM rd_package_unit;")),
    0,
    "Fresh isolated instance required; never reset existing business records",
  );
  const taskId = await api("/rd/production-task/create", token, "POST", {
    customerName: marker,
    productionDate: "2026-10-22",
    shiftCode: "合成验证",
    remark: marker,
  });
  const outputId = await api("/rd/production-output/create", token, "POST", {
    productionTaskId: taskId,
    clientRequestId: randomUUID(),
    productionDate: "2026-10-22",
    customerName: marker,
    productModel: "WORK-QUERY-DEMO",
    furnaceNo: "S1-02-DEMO",
    furnaceLayer: "上层",
    outputQty: 6,
    packageWeight: 1000.123456,
    sourceSheet: marker,
    remark: marker,
  });
  const production = await api(
    `/rd/production-output/get?id=${outputId}`,
    token,
  );
  await api("/rd/production-output/confirm", token, "POST", {
    id: outputId,
    version: production.version,
  });
  const bags = await api("/rd/package-unit/generate", token, "POST", {
    productionOutputId: outputId,
    packageCount: 6,
  });
  assert.equal(bags.length, 6);
  const statuses = [
    "QC_PENDING",
    "QC_PENDING",
    "QC_PASSED",
    "INBOUND_PENDING",
    "IN_STOCK",
    "QC_REJECTED",
  ];
  // Synthetic status injection is restricted to this new, isolated local fixture, not a claimed production workflow.
  for (const [index, bag] of bags.entries()) {
    assert.ok(Number.isSafeInteger(bag.id));
    await sql(
      `UPDATE rd_package_unit SET status='${statuses[index]}' WHERE id=${bag.id} AND production_output_id=${outputId};`,
    );
  }
  await sql(
    `INSERT INTO rd_quality_inspection (tenant_id,inspection_no,package_id,production_output_id,status,submit_time,remark) VALUES (1,'S1-02-QI',${bags[0].id},${outputId},'SUBMITTED',NOW(),'${marker}');`,
  );
  const inspectionId = Number(
    await sql(
      "SELECT id FROM rd_quality_inspection WHERE inspection_no='S1-02-QI' AND tenant_id=1;",
    ),
  );
  assert.ok(Number.isSafeInteger(inspectionId) && inspectionId > 0);
  await sql(
    `INSERT INTO rd_quality_inspection_member (tenant_id,inspection_id,package_id,package_code,package_weight,sampled) SELECT 1,${inspectionId},id,package_code,package_weight,(id=${bags[0].id}) FROM rd_package_unit WHERE id IN (${bags
      .slice(0, 2)
      .map((item) => item.id)
      .join(",")});`,
  );
  fixture = {
    ...fixture,
    taskId,
    outputId,
    packageIds: bags.map((bag) => bag.id),
    inspectionId,
  };
  await save();
}
const tokens = { admin: token };
for (const [name, roleIds] of Object.entries({
  production: [600001],
  quality: [600006],
  warehouse: [600002],
  readonly: [600005],
  audit: [600004],
  operator: [600007],
  sales: [600003],
  driver: [600008],
  mixedDriver: [600008, 600002],
})) {
  if (!fixture.accounts[name]) {
    const account = {
      username: `s102${name}${randomBytes(3).toString("hex")}`,
      password: `S1a!${randomBytes(6).toString("hex")}`,
    };
    account.userId = await api("/system/user/create", token, "POST", {
      ...account,
      nickname: `合成${name}`,
      remark: marker,
    });
    fixture.accounts[name] = account;
    await save();
  }
  await api("/system/permission/assign-user-role", token, "POST", {
    userId: fixture.accounts[name].userId,
    roleIds,
  });
  tokens[name] = await login(fixture.accounts[name]);
}
const fingerprint = async () =>
  createHash("sha256")
    .update(
      await sql(
        "SELECT * FROM rd_package_unit ORDER BY id; SELECT * FROM rd_quality_inspection ORDER BY id; SELECT * FROM rd_quality_inspection_member ORDER BY id; SELECT * FROM rd_stock_transaction ORDER BY id;",
      ),
    )
    .digest("hex");
const before = await fingerprint(),
  results = {};
for (const type of ["Q03", "Q04", "Q05"]) {
  const data = await api(`/rd/work-assistant/query?queryType=${type}`, token);
  assert.equal(data.quality, "KNOWN", JSON.stringify(data.notices));
  assert.equal(Number(data.metrics.DISTINCT_PACKAGE_COUNT.value), 2);
  assert.equal(data.metrics.DISTINCT_PACKAGE_COUNT.unit, "PACKAGE");
  assert.equal(data.items.length, type === "Q04" ? 1 : 2);
  if (type === "Q04") {
    assert.equal(data.items[0].sourceId, String(fixture.inspectionId));
    assert.equal(data.items[0].members.length, 2);
  } else assert.equal(data.metrics.REGISTERED_WEIGHT_KG.value, "2000.246912");
  results[type] = {
    quality: data.quality,
    count: data.metrics.DISTINCT_PACKAGE_COUNT.value,
    total: data.total,
    rows: data.items.length,
  };
}
for (const name of ["driver", "mixedDriver", "sales"])
  for (const type of ["Q03", "Q04", "Q05"]) {
    const data = await request(
      `/rd/work-assistant/query?queryType=${type}`,
      tokens[name],
    );
    assert.equal(data.code, 403, `${name}:${type}`);
  }
for (const name of ["production", "quality", "warehouse", "readonly", "audit"])
  for (const type of ["Q03", "Q04", "Q05"])
    await api(`/rd/work-assistant/query?queryType=${type}`, tokens[name]);
for (const type of ["Q03", "Q05"])
  await api(`/rd/work-assistant/query?queryType=${type}`, tokens.operator);
assert.equal(
  (await request("/rd/work-assistant/query?queryType=Q04", tokens.operator))
    .code,
  403,
);
for (const suffix of [
  "queryType=Q03&tenantId=2",
  "queryType=Q03&queryType=Q05",
  "queryType=Q03&pageSize=101",
  "queryType=Q03&warehouseCode=DEMO",
])
  assert.equal(
    (await request("/rd/work-assistant/query?" + suffix, token)).code,
    400,
  );
assert.equal(
  (await request("/rd/work-assistant/query?queryType=Q03", null)).code,
  401,
);
assert.notEqual(
  (
    await request(
      "/rd/work-assistant/query?queryType=Q03",
      token,
      "GET",
      undefined,
      "2",
    )
  ).code,
  0,
);
assert.equal(await fingerprint(), before);
await fs.mkdir(output, { recursive: true });

const { chromium } = require(path.join(process.argv[2], "playwright"));
const browser = await chromium.launch({ channel: "msedge", headless: true });
const errors = [],
  external = [],
  writes = [],
  screens = [];
try {
  const context = await browser.newContext();
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(error.message));
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
  await page.goto(origin + "/login", { waitUntil: "networkidle" });
  await page.getByPlaceholder("账号", { exact: true }).fill(admin.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(admin.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname !== "/login", { timeout: 60000 });
  const open = async () => {
    await page
      .getByRole("button", { name: "打开智能小垛", exact: true })
      .click();
    await page.getByRole("dialog", { name: "智能小垛", exact: true }).waitFor();
  };
  const panel = () =>
    page.getByRole("dialog", { name: "智能小垛", exact: true });
  const clear = async () => {
    const button = panel().getByRole("button", {
      name: "清空小垛对话",
      exact: true,
    });
    if (await button.isEnabled()) await button.click();
  };
  const ask = async (question) => {
    await panel()
      .getByRole("textbox", { name: "向智能小垛提问", exact: true })
      .fill(question);
    await panel()
      .getByRole("button", { name: "发送给小垛", exact: true })
      .click();
    await page.waitForFunction(
      () =>
        !!document.querySelector(".xiaoduo-turn:last-child .xiaoduo-answer"),
    );
  };
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: width === 1440 ? 1000 : 844 });
    if ((await panel().count()) === 0) await open();
    for (const [name, question] of [
      ["pending", "待检包件有多少"],
      ["review", "质检待复核"],
      ["inbound", "合格待入库的货物"],
    ]) {
      await clear();
      await ask(question);
      await panel()
        .getByText("2 包", { exact: true })
        .first()
        .waitFor()
        .catch(async (error) => {
          await page.screenshot({ path: path.join(output, "failure.png") });
          await fs.writeFile(
            path.join(output, "failure-panel.txt"),
            await panel().innerText(),
          );
          throw error;
        });
      const rect = await panel().boundingBox();
      assert.ok(rect.x >= 0 && rect.y >= 0 && rect.x + rect.width <= width + 1);
      assert.ok(
        await page.evaluate(
          () => document.documentElement.scrollWidth <= window.innerWidth + 1,
        ),
      );
      const image = `${width}-${name}.png`;
      await page.screenshot({ path: path.join(output, image) });
      screens.push(image);
    }
  }
  await page.setViewportSize({ width: 1440, height: 1000 });
  await clear();
  await ask("质检待复核");
  await panel()
    .getByRole("button", { name: "打开 查看质检单 S1-02-QI", exact: true })
    .click();
  await page.waitForURL(
    (url) =>
      url.pathname === "/rd/quality/inspection" &&
      url.searchParams.get("inspectionId") === String(fixture.inspectionId),
  );
  await page.getByRole("dialog").filter({ hasText: "S1-02-QI" }).waitFor();
  assert.equal(errors.length, 0, errors.join("\n"));
  assert.equal(external.length, 0, external.join("\n"));
  assert.equal(writes.length, 0);
  assert.equal(await fingerprint(), before);
  const result = {
    status: "PASS",
    origin,
    syntheticFixture: true,
    directFixtureStatusInjection: true,
    queries: results,
    roleCombinations: 10,
    mutationParametersRejected: true,
    exactInspectionNavigation: true,
    sourceFingerprintsUnchanged: true,
    screens,
    pageErrors: errors,
    externalRequests: external,
    browserBusinessWrites: writes,
    paidModelCalls: 0,
    credentialsRecorded: false,
  };
  await fs.writeFile(
    path.join(output, "result.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result));
} catch (error) {
  await fs.writeFile(
    path.join(output, "failure.json"),
    JSON.stringify({
      message: error.message,
      pageErrors: errors,
      externalRequests: external,
      writes,
    }),
  );
  throw error;
} finally {
  await browser.close();
}
