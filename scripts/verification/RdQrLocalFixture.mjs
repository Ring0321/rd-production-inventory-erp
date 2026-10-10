import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { randomBytes } from "node:crypto";
import { fileURLToPath } from "node:url";
import { execFile } from "node:child_process";
import { promisify } from "node:util";

export const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
export const marker = "二维码岗位本地验证（非现场数据）";
const fixtureFile = path.join(root, "temp/qr-e4-fixture.json");
const envFile = path.join(root, "temp/local-runtime-patent/.env.local");
const settings = Object.fromEntries(
  (await fs.readFile(envFile, "utf8"))
    .split(/\r?\n/)
    .filter((line) => line && !line.startsWith("#") && line.includes("="))
    .map((line) => [
      line.slice(0, line.indexOf("=")),
      line.slice(line.indexOf("=") + 1),
    ]),
);
assert.equal(
  Number(settings.RD_DB_PORT),
  3310,
  "Only the isolated patent local instance may be used",
);
const webPort = Number(settings.RD_WEB_PORT);
assert.ok(Number.isInteger(webPort) && webPort > 0 && webPort <= 65535);
export const baseUrl = `http://127.0.0.1:${webPort}`;

export const request = async (url, token, method = "GET", body) => {
  const response = await fetch(`${baseUrl}/admin-api${url}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      "tenant-id": "1",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    ...(body ? { body: JSON.stringify(body) } : {}),
    signal: AbortSignal.timeout(20000),
  });
  return response.json();
};
export const api = async (...args) => {
  const response = await request(...args);
  assert.equal(response.code, 0, `${args[0]}: ${response.msg}`);
  return response.data;
};
export const login = async (account) =>
  (await api("/system/auth/login", undefined, "POST", account)).accessToken;

export const readFlowCounts = async (packageIds, taskId) => {
  assert.ok(
    [...packageIds, taskId].every((id) => Number.isSafeInteger(id) && id > 0),
  );
  const sql = `SELECT COUNT(*) FROM rd_stock_transaction WHERE deleted=0 AND package_id IN (${packageIds.join(",")}); SELECT COUNT(*) FROM rd_loading_scan_record WHERE deleted=0 AND task_id=${taskId}`;
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
      sql,
    ],
    { env: { ...process.env, MYSQL_PWD: settings.RD_DB_PASSWORD } },
  );
  return result.stdout.trim().split(/\s+/).map(Number);
};

export const prepareFixture = async () => {
  const admin = {
    username: "admin",
    password: settings.RD_ADMIN_INITIAL_PASSWORD,
  };
  const tokens = { admin: await login(admin) };
  let fixture;
  try {
    fixture = JSON.parse(await fs.readFile(fixtureFile, "utf8"));
    assert.equal(fixture.marker, marker);
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    fixture = { marker, accounts: {}, inspections: {} };
  }
  const save = () =>
    fs.writeFile(fixtureFile, JSON.stringify(fixture, null, 2), {
      mode: 0o600,
    });
  for (const [name, roleId] of Object.entries({
    production: 600001,
    quality: 600006,
    warehouse: 600002,
    driver: 600008,
    sales: 600003,
    otherDriver: 600008,
  })) {
    await api(`/system/role/get?id=${roleId}`, tokens.admin);
    if (!fixture.accounts[name]) {
      const account = {
        username: `e4${name}${randomBytes(3).toString("hex")}`,
        password: `Qe4A${randomBytes(5).toString("hex")}!`,
      };
      account.userId = await api("/system/user/create", tokens.admin, "POST", {
        ...account,
        nickname: `E4本地${name}`,
        remark: marker,
      });
      fixture.accounts[name] = account;
      await save();
    }
    const account = fixture.accounts[name];
    if (!account.assigned) {
      await api("/system/permission/assign-user-role", tokens.admin, "POST", {
        userId: account.userId,
        roleIds: [roleId],
      });
      account.assigned = true;
      await save();
    }
    tokens[name] = await login(account);
  }
  const createOutput = async (name, quantity) => {
    if (!fixture[name]) {
      fixture[name] = await api(
        "/rd/production-output/create",
        tokens.production,
        "POST",
        {
          productionDate: "2026-10-11",
          customerName: marker,
          productModel: "QR-E4",
          furnaceNo: `E4-${Date.now()}`,
          furnaceLayer: "上层",
          outputQty: quantity,
          packageWeight: 1000,
          sourceSheet: marker,
          remark: marker,
        },
      );
      await save();
    }
    const output = await api(
      `/rd/production-output/get?id=${fixture[name]}`,
      tokens.production,
    );
    assert.equal(output.sourceSheet, marker);
    if (output.status === "DRAFT") {
      await api("/rd/production-output/confirm", tokens.production, "POST", {
        id: output.id,
        version: output.version,
      });
    }
    let packages = await api(
      `/rd/package-unit/trace?productionOutputId=${output.id}`,
      tokens.production,
    );
    if (!packages.length)
      packages = await api(
        "/rd/package-unit/generate",
        tokens.production,
        "POST",
        {
          productionOutputId: output.id,
          packageCount: quantity,
        },
      );
    const pendingIds = packages
      .filter((pkg) => pkg.status === "CREATED")
      .map((pkg) => pkg.id);
    if (pendingIds.length)
      await api("/rd/package-unit/print-batch", tokens.production, "POST", {
        packageIds: pendingIds,
      });
    return api(
      `/rd/package-unit/trace?productionOutputId=${output.id}`,
      tokens.production,
    );
  };
  fixture.labeledPackages = await createOutput("labelOutputId", 2);
  let loadingPackages = await createOutput("loadingOutputId", 2);
  for (const pkg of loadingPackages) {
    if (
      ["QC_PASSED", "IN_STOCK", "OUTBOUND_PENDING", "OUTBOUNDED"].includes(
        pkg.status,
      )
    )
      continue;
    if (!fixture.inspections[pkg.id]) {
      fixture.inspections[pkg.id] = await api(
        "/rd/quality-inspection/create",
        tokens.quality,
        "POST",
        {
          packageId: pkg.id,
          remark: marker,
          items: [
            {
              itemCode: "LOCAL_TEST",
              itemName: "接口验证示例（非检测值）",
              standardMin: 0,
              standardMax: 10,
              measuredValue: 5,
              unit: "示例",
            },
          ],
        },
      );
      await save();
    }
    const id = fixture.inspections[pkg.id];
    let inspection = await api(
      `/rd/quality-inspection/get?id=${id}`,
      tokens.quality,
    );
    assert.equal(inspection.packageId, pkg.id);
    if (inspection.status === "DRAFT")
      await api(
        `/rd/quality-inspection/submit?id=${id}`,
        tokens.quality,
        "POST",
      );
    inspection = await api(
      `/rd/quality-inspection/get?id=${id}`,
      tokens.quality,
    );
    if (["SUBMITTED", "RESUBMITTED"].includes(inspection.status)) {
      await api(
        `/rd/quality-inspection/approve?id=${id}`,
        tokens.admin,
        "POST",
      );
    }
  }
  loadingPackages = await api(
    `/rd/package-unit/trace?productionOutputId=${fixture.loadingOutputId}`,
    tokens.warehouse,
  );
  if (!fixture.inboundId) {
    const locations = await api(
      "/rd/putaway-task/location-map",
      tokens.warehouse,
    );
    const location = locations.find(
      (item) =>
        item.enabled &&
        !item.frozen &&
        (item.availablePackages == null || item.availablePackages >= 2),
    );
    assert.ok(location, "The isolated sample needs an available location");
    fixture.inboundId = await api(
      "/rd/inbound-order/create",
      tokens.warehouse,
      "POST",
      {
        bizDate: "2026-10-11",
        productionOutputId: fixture.loadingOutputId,
        remark: marker,
        lines: loadingPackages.map((pkg) => ({
          packageId: pkg.id,
          locationId: location.id,
          locationCode: location.locationCode,
          quantity: 1,
          weight: pkg.packageWeight,
          remark: marker,
        })),
      },
    );
    await save();
  }
  for (const [status, action, actor] of [
    ["DRAFT", "submit", "warehouse"],
    ["SUBMITTED", "verify", "admin"],
    ["VERIFIED", "post", "warehouse"],
  ]) {
    const inbound = await api(
      `/rd/inbound-order/get?id=${fixture.inboundId}`,
      tokens.warehouse,
    );
    assert.equal(inbound.productionOutputId, fixture.loadingOutputId);
    if (inbound.status === status)
      await api(
        `/rd/inbound-order/${action}?id=${fixture.inboundId}`,
        tokens[actor],
        "POST",
      );
  }
  fixture.loadingPackages = await api(
    `/rd/package-unit/trace?productionOutputId=${fixture.loadingOutputId}`,
    tokens.warehouse,
  );
  if (!fixture.pickupId) {
    fixture.pickupId = await api(
      "/rd/loading-task/pickup-slip/create",
      tokens.sales,
      "POST",
      {
        slipNo: `E4-${Date.now()}`,
        customerName: marker,
        requestedPackageCount: 2,
        requestedWeight: 2000,
        remark: marker,
        lines: [
          {
            lineNo: 1,
            productModel: "QR-E4",
            requestedPackageCount: 2,
            requestedWeight: 2000,
            remark: marker,
          },
        ],
      },
    );
    await save();
  }
  const pickup = await api(
    `/rd/loading-task/pickup-slip/get?id=${fixture.pickupId}`,
    tokens.sales,
  );
  assert.equal(pickup.slip.customerName, marker);
  if (!fixture.loadingTaskId) {
    fixture.loadingTaskId = await api(
      "/rd/loading-task/create",
      tokens.warehouse,
      "POST",
      {
        pickupSlipId: fixture.pickupId,
        forkliftDriverId: fixture.accounts.driver.userId,
        selections: fixture.loadingPackages.map((pkg) => ({
          pickupSlipLineId: pickup.lines[0].id,
          packageId: pkg.id,
        })),
        remark: marker,
      },
    );
    await save();
  }
  const task = await api(
    `/rd/loading-task/get?id=${fixture.loadingTaskId}`,
    tokens.warehouse,
  );
  assert.equal(
    task.task.status,
    "ASSIGNED",
    "Use a new isolated fixture after this task has been executed",
  );
  fixture.loadingTaskNo = task.task.taskNo;
  fixture.pickupSlipNo = pickup.slip.slipNo;
  await save();
  return { fixture, tokens, accounts: { ...fixture.accounts, admin } };
};
