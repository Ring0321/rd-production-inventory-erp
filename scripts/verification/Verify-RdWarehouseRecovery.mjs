import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { spawn } from "node:child_process";
import {
  prepareWarehouse,
  api,
  request,
  root,
  baseUrl,
} from "./RdWarehouseLocalFixture.mjs";

const fixture = await prepareWarehouse();
const {
  accounts,
  tokens,
  ready,
  upper,
  extra,
  lower,
  locationIds,
  createLoading,
  sql,
} = fixture;
const output = path.join(root, "temp/qr-warehouse-recovery-verification");
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
const errors = [];
const evidence = [];
const commands = [];
const loadingIds = [];
const bagIds = [...upper.bags, ...extra.bags, ...lower.bags].map(
  (bag) => bag.id,
);
assert.ok(bagIds.every((id) => Number.isSafeInteger(id) && id > 0));
const fingerprint = async () =>
  JSON.stringify([
    await fixture.fingerprint(),
    await sql(
      `SELECT * FROM rd_stock_lot WHERE id IN (SELECT stock_lot_id FROM rd_package_unit WHERE id IN (${bagIds.join(",")})) ORDER BY id`,
    ),
    await sql(
      `SELECT * FROM rd_stock_transaction WHERE package_id IN (${bagIds.join(",")}) ORDER BY id`,
    ),
    await sql(
      `SELECT * FROM rd_scan_event WHERE package_id IN (${bagIds.join(",")}) ORDER BY id`,
    ),
    await sql(
      `SELECT * FROM rd_package_task_event WHERE package_id IN (${bagIds.join(",")}) ORDER BY id`,
    ),
    await sql(
      `SELECT * FROM rd_putaway_batch_scan_record WHERE task_id=${ready.task.id} ORDER BY id`,
    ),
    await sql(
      `SELECT * FROM rd_putaway_task_line WHERE task_id=${ready.task.id} ORDER BY id`,
    ),
    ...(loadingIds.length
      ? [
          await sql(
            `SELECT * FROM rd_loading_task WHERE id IN (${loadingIds.join(",")}) ORDER BY id`,
          ),
          await sql(
            `SELECT * FROM rd_loading_task_line WHERE task_id IN (${loadingIds.join(",")}) ORDER BY id`,
          ),
          await sql(
            `SELECT * FROM rd_loading_scan_record WHERE task_id IN (${loadingIds.join(",")}) ORDER BY id`,
          ),
        ]
      : []),
  ]);
const unchanged = async (name, action) => {
  const before = await fingerprint();
  const value = await action();
  assert.equal(await fingerprint(), before, `${name}: domain state changed`);
  evidence.push({ name, noDomainWrites: true });
  return value;
};
const signIn = async (suffix) => {
  const context = await browser.newContext({
    viewport: { width: 390, height: 900 },
  });
  contexts.push(context);
  const page = await context.newPage();
  page.on("pageerror", (error) => errors.push(error.message));
  page.on("request", (req) => {
    const pathname = new URL(req.url()).pathname;
    if (
      /\/(batch-scan\/(confirm|status)|confirm-outbound|putaway-task\/confirm)$/.test(
        pathname,
      )
    )
      commands.push({ path: pathname, body: req.postDataJSON() });
  });
  await page.goto(`${baseUrl}/pda/warehouse${suffix}`, {
    waitUntil: "networkidle",
  });
  await page
    .getByPlaceholder("账号", { exact: true })
    .fill(accounts.warehouse.username);
  await page
    .getByPlaceholder("密码", { exact: true })
    .fill(accounts.warehouse.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname === "/pda/warehouse");
  await page
    .getByRole("heading", { name: "仓管工作台", exact: true })
    .waitFor();
  return page;
};
const dialog = async (page, cancel = false) => {
  const box = page.getByRole("dialog");
  await box.waitFor();
  await box
    .getByRole("button", { name: cancel ? "取消" : "确定", exact: true })
    .click();
  await box.waitFor({ state: "hidden" });
};
const select = async (page, code) => {
  const checkbox = page.getByRole("checkbox", {
    name: `选择包件 ${code}`,
    exact: true,
  });
  if (!(await checkbox.isChecked()))
    await page.locator("label.el-checkbox").filter({ has: checkbox }).click();
};
const screenshot = async (page, name) => {
  await page.mouse.move(0, 0);
  await page.getByRole("dialog").waitFor({ state: "hidden" });
  await page.waitForFunction(() =>
    [...document.querySelectorAll('[role="tooltip"]')].every(
      (node) => !node.getClientRects().length,
    ),
  );
  await page.locator(".pda-content").evaluate((node) => {
    node.scrollTop = 0;
  });
  const width = await page.evaluate(() => ({
    viewport: document.documentElement.clientWidth,
    scroll: document.documentElement.scrollWidth,
    content: document.querySelector(".pda-content").clientWidth,
    contentScroll: document.querySelector(".pda-content").scrollWidth,
  }));
  assert.ok(
    width.scroll <= width.viewport && width.contentScroll <= width.content,
  );
  await page.screenshot({
    path: path.join(output, `${name}.png`),
    fullPage: true,
  });
};
const state = async (kind, id) =>
  api(`/rd/${kind}-task/get?id=${id}`, tokens.warehouse);

const lockedReplay = async (kind, original) => {
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
  assert.equal(Number(settings.RD_DB_PORT), 3310);
  const id = kind === "putaway" ? original.id : original.taskId;
  const table = kind === "putaway" ? "rd_putaway_task" : "rd_loading_task";
  assert.ok(Number.isSafeInteger(id) && id > 0);
  const child = spawn(
    "mysql",
    [
      "--host=127.0.0.1",
      "--port=3310",
      "--user=root",
      "--batch",
      "--skip-column-names",
      "--unbuffered",
      "rd_erp",
    ],
    {
      env: { ...process.env, MYSQL_PWD: settings.RD_DB_PASSWORD },
      stdio: ["pipe", "pipe", "pipe"],
    },
  );
  const closed = new Promise((resolve, reject) => {
    child.once("error", reject);
    child.once("close", (code) =>
      code === 0
        ? resolve()
        : reject(new Error("Owned MySQL lock session failed")),
    );
  });
  let timer;
  const locked = new Promise((resolve, reject) => {
    timer = setTimeout(
      () => reject(new Error("Owned MySQL row lock was not acquired")),
      10000,
    );
    let text = "";
    child.stdout.on("data", (chunk) => {
      text += chunk.toString();
      if (text.includes("R11_LOCKED")) {
        clearTimeout(timer);
        resolve();
      }
    });
    child.once("error", reject);
  });
  child.stdin.write(
    `START TRANSACTION;\nSELECT id FROM ${table} WHERE id=${id} FOR UPDATE;\nSELECT 'R11_LOCKED';\n`,
  );
  try {
    await locked;
    let statusDone = false;
    let replayDone = false;
    const status = api(
      `/rd/${kind}-task/batch-scan/status`,
      tokens.warehouse,
      "POST",
      original,
    ).then((value) => {
      statusDone = true;
      return value;
    });
    const replay = api(
      `/rd/${kind}-task/batch-scan/confirm`,
      tokens.warehouse,
      "POST",
      original,
    ).then((value) => {
      replayDone = true;
      return value;
    });
    let waiting = 0;
    for (let attempt = 0; attempt < 40; attempt++) {
      waiting = Number(
        await sql(
          `SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.data_locks l ON w.REQUESTING_ENGINE_LOCK_ID=l.ENGINE_LOCK_ID WHERE l.OBJECT_SCHEMA='rd_erp' AND l.OBJECT_NAME='${table}' AND l.LOCK_DATA='${id}'`,
        ),
      );
      if (waiting >= 2) break;
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
    assert.ok(
      waiting >= 2,
      "Both HTTP paths must wait on the same real MySQL task row",
    );
    assert.equal(statusDone, false);
    assert.equal(replayDone, false);
    child.stdin.end("COMMIT;\n");
    await closed;
    const [resolvedStatus, resolvedReplay] = await Promise.all([
      status,
      replay,
    ]);
    assert.equal(resolvedStatus.state, "APPLIED");
    assert.equal(resolvedStatus.recordId, resolvedReplay.recordId);
    assert.equal(resolvedReplay.replayed, true);
  } finally {
    clearTimeout(timer);
    if (!child.stdin.destroyed) child.stdin.end("ROLLBACK;\n");
    await closed;
  }
};

const snapshotDuringCommit = async (kind, original, execute) => {
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
  assert.equal(Number(settings.RD_DB_PORT), 3310);
  const id = kind === "putaway" ? original.id : original.taskId;
  const table =
    kind === "putaway"
      ? "rd_putaway_batch_scan_record"
      : "rd_loading_scan_record";
  const taskTable = kind === "putaway" ? "rd_putaway_task" : "rd_loading_task";
  assert.ok(Number.isSafeInteger(id) && id > 0);
  assert.match(original.idempotencyKey, /^[\w-]{1,128}$/);
  const condition = `task_id=${id} AND idempotency_key='${original.idempotencyKey}'`;
  const child = spawn(
    "mysql",
    [
      "--host=127.0.0.1",
      "--port=3310",
      "--user=root",
      "--batch",
      "--skip-column-names",
      "--unbuffered",
      "rd_erp",
    ],
    {
      env: { ...process.env, MYSQL_PWD: settings.RD_DB_PASSWORD },
      stdio: ["pipe", "pipe", "pipe"],
    },
  );
  let transcript = "";
  child.stdout.on("data", (chunk) => {
    transcript += chunk.toString();
  });
  child.stderr.resume();
  const closed = new Promise((resolve, reject) => {
    child.once("error", reject);
    child.once("close", (code) =>
      code === 0
        ? resolve()
        : reject(new Error("Owned MySQL snapshot session failed")),
    );
  });
  const wait = async (marker) => {
    for (let attempt = 0; attempt < 100; attempt++) {
      if (transcript.includes(marker)) return;
      await new Promise((resolve) => setTimeout(resolve, 50));
    }
    throw new Error(`Owned snapshot stage missing: ${marker}`);
  };
  child.stdin.write(
    `SELECT @@SESSION.transaction_isolation;\nSET SESSION TRANSACTION ISOLATION LEVEL REPEATABLE READ;\nSTART TRANSACTION;\nSELECT CONCAT('BEFORE:',COUNT(*)) FROM ${table} WHERE ${condition};\nSELECT 'SNAPSHOT_READY';\n`,
  );
  try {
    await wait("SNAPSHOT_READY");
    assert.match(transcript, /REPEATABLE-READ/);
    assert.match(transcript, /BEFORE:0/);
    const result = await execute();
    assert.equal(result.code, 0, result.msg);
    child.stdin.write(
      `SELECT CONCAT('SNAPSHOT_AFTER:',COUNT(*)) FROM ${table} WHERE ${condition};\nSELECT CONCAT('CURRENT_VERSION:',version) FROM ${taskTable} WHERE id=${id} FOR UPDATE;\nSELECT CONCAT('CURRENT_RECORD:',id) FROM ${table} WHERE ${condition} FOR UPDATE;\nSELECT 'CURRENT_READ_DONE';\n`,
    );
    await wait("CURRENT_READ_DONE");
    assert.match(transcript, /SNAPSHOT_AFTER:0/);
    assert.match(
      transcript,
      new RegExp(`CURRENT_RECORD:${result.data.recordId}(?:\\r?\\n|$)`),
    );
    assert.match(
      transcript,
      new RegExp(`CURRENT_VERSION:${result.data.task.version}(?:\\r?\\n|$)`),
    );
    child.stdin.end("COMMIT;\n");
    await closed;
    evidence.push({
      name: `${kind}-real-REPEATABLE-READ-snapshot-miss-and-current-read`,
      snapshotRecordCount: 0,
      committedRecordId: result.data.recordId,
      taskVersion: result.data.task.version,
    });
    return result;
  } finally {
    if (!child.stdin.destroyed) child.stdin.end("ROLLBACK;\n");
    await closed;
  }
};

const exercise = async (kind, initial, bags) => {
  const putaway = kind === "putaway";
  const page = await signIn(`?section=${kind}&taskId=${initial.task.id}`);
  const panel = page.locator(`.${kind}-panel`);
  await panel
    .getByRole("heading", { name: initial.task.taskNo, exact: true })
    .waitFor();
  const confirmName = putaway ? "核验所选已到位包" : "核验所选已装车包";
  const statusName = putaway ? "核定原上架请求" : "核定原装车请求";
  const retryName = putaway ? "重试原上架核验" : "重试原装车核验";
  const url = `/rd/${kind}-task/batch-scan/confirm`;
  const statusUrl = `/rd/${kind}-task/batch-scan/status`;
  const routeUrl = `**/admin-api${url}`;
  let original;
  let forwarded = false;
  await page.route(routeUrl, async (route) => {
    original = route.request().postDataJSON();
    await route.abort("failed");
  });
  await select(page, bags[0].packageCode);
  await page.getByRole("button", { name: confirmName, exact: true }).click();
  await dialog(page);
  await page.getByRole("button", { name: retryName, exact: true }).waitFor();
  await page
    .getByRole("button", { name: statusName, exact: true })
    .waitFor({ state: "visible" });
  await page.reload({ waitUntil: "networkidle" });
  await page.getByRole("button", { name: statusName, exact: true }).waitFor();
  const unknown = await unchanged(`${kind}-same-version-absence`, () =>
    api(statusUrl, tokens.warehouse, "POST", original),
  );
  assert.equal(unknown.state, "UNKNOWN");
  const future = {
    ...original,
    version: original.version + 1,
    idempotencyKey: `${original.idempotencyKey}-future`,
  };
  const futureStatus = await unchanged(
    `${kind}-future-version-remains-unknown`,
    () => api(statusUrl, tokens.warehouse, "POST", future),
  );
  assert.equal(futureStatus.state, "UNKNOWN");
  await unchanged(`${kind}-unknown-PDA-keeps-pending`, async () => {
    await page.getByRole("button", { name: statusName, exact: true }).click();
    await page
      .getByRole("alert")
      .filter({ hasText: "原请求结果仍未知" })
      .waitFor();
    assert.equal(
      await page.getByRole("button", { name: retryName, exact: true }).count(),
      1,
    );
  });
  await screenshot(page, `${kind}-unknown-390`);
  const pendingKey = `rd-warehouse-pending:1:${accounts.warehouse.userId}:${kind}`;
  const ownedRaw = await page.evaluate(
    (key) => localStorage.getItem(key),
    pendingKey,
  );
  const simulatedNewer = JSON.parse(ownedRaw);
  assert.deepEqual(simulatedNewer.request, original);
  simulatedNewer.request.idempotencyKey = `UI-SIMULATED-NEWER-${Date.now()}`;
  // Fault-inject only this test actor's business snapshot, never authentication storage.
  await page.evaluate(({ key, raw }) => localStorage.setItem(key, raw), {
    key: pendingKey,
    raw: JSON.stringify(simulatedNewer),
  });
  try {
    await unchanged(
      `${kind}-simulated-newer-browser-snapshot-survives-old-retry`,
      async () => {
        const before = commands.filter((item) =>
          item.path.endsWith("/batch-scan/confirm"),
        ).length;
        await page
          .getByRole("button", { name: retryName, exact: true })
          .click();
        await page
          .getByRole("alert")
          .filter({ hasText: "另一份待核请求" })
          .waitFor();
        assert.equal(
          commands.filter((item) => item.path.endsWith("/batch-scan/confirm"))
            .length,
          before,
        );
        const stored = await page.evaluate(
          (key) => JSON.parse(localStorage.getItem(key)),
          pendingKey,
        );
        assert.deepEqual(stored.request, simulatedNewer.request);
      },
    );
  } finally {
    await page.evaluate(({ key, raw }) => localStorage.setItem(key, raw), {
      key: pendingKey,
      raw: ownedRaw,
    });
  }
  await page.reload({ waitUntil: "networkidle" });
  await page.getByRole("button", { name: statusName, exact: true }).waitFor();
  await api(
    `/rd/${kind}-task/exception`,
    tokens.driver,
    "POST",
    putaway
      ? {
          id: initial.task.id,
          exceptionType: "LOCATION_OCCUPIED",
          description: "R11本地模拟任务调整，非现场",
        }
      : {
          taskId: initial.task.id,
          exceptionType: "WRONG_PACKAGE",
          description: "R11本地模拟任务调整，非现场",
        },
  );
  const changed = await state(kind, initial.task.id);
  await api(
    putaway
      ? "/rd/putaway-task/reassign"
      : "/rd/loading-task/exception/resolve",
    tokens.warehouse,
    "PUT",
    putaway
      ? {
          id: initial.task.id,
          version: changed.task.version,
          plannedLocationId: locationIds[0],
          forkliftDriverId: accounts.driver.userId,
          handlingResult: "本地仓管重新核定，非现场数据",
        }
      : {
          taskId: initial.task.id,
          version: changed.task.version,
          resolutionAction: "RETURN_TO_DRIVER",
          handlingResult: "本地仓管重新核定，非现场数据",
        },
  );
  const stale = await unchanged(`${kind}-stale-server-status`, () =>
    api(statusUrl, tokens.warehouse, "POST", original),
  );
  assert.equal(stale.state, "NOT_APPLIED_STALE");
  await unchanged(`${kind}-cancel-stale-keeps-pending`, async () => {
    await page.getByRole("button", { name: statusName, exact: true }).click();
    await dialog(page, true);
    assert.equal(
      await page.getByRole("button", { name: retryName, exact: true }).count(),
      1,
    );
  });
  await unchanged(
    `${kind}-approve-stale-resets-physical-selection`,
    async () => {
      await page.getByRole("button", { name: statusName, exact: true }).click();
      await dialog(page);
      await page
        .getByRole("status")
        .filter({ hasText: "旧版未执行请求已核定结束" })
        .waitFor();
      assert.equal(
        await page
          .getByRole("button", { name: retryName, exact: true })
          .count(),
        0,
      );
      assert.equal(
        await panel.getByRole("checkbox", { checked: true }).count(),
        0,
      );
    },
  );
  await screenshot(page, `${kind}-stale-ended-390`);
  const denied = await unchanged(`${kind}-old-version-cannot-arrive-late`, () =>
    request(url, tokens.warehouse, "POST", original),
  );
  assert.notEqual(denied.code, 0);
  await page.unroute(routeUrl);
  let committedResolve, committedReject;
  const committed = new Promise((resolve, reject) => {
    committedResolve = resolve;
    committedReject = reject;
  });
  committed.catch(() => {});
  let appliedRequest;
  await page.route(routeUrl, async (route) => {
    appliedRequest = route.request().postDataJSON();
    forwarded = true;
    try {
      committedResolve(
        await snapshotDuringCommit(kind, appliedRequest, async () =>
          (await route.fetch()).json(),
        ),
      );
    } catch (error) {
      committedReject(error);
    } finally {
      await route.abort("failed");
    }
  });
  for (const bag of bags) await select(page, bag.packageCode);
  await page.getByRole("button", { name: confirmName, exact: true }).click();
  await dialog(page);
  const acknowledged = await committed;
  assert.equal(acknowledged.code, 0, acknowledged.msg);
  assert.equal(forwarded, true);
  await page.getByRole("button", { name: retryName, exact: true }).waitFor();
  await page.reload({ waitUntil: "networkidle" });
  const applied = await unchanged(
    `${kind}-committed-lost-response-status`,
    () => api(statusUrl, tokens.warehouse, "POST", appliedRequest),
  );
  assert.equal(applied.state, "APPLIED");
  assert.equal(applied.recordId, acknowledged.data.recordId);
  await unchanged(`${kind}-PDA-applied-recovery-zero-write`, async () => {
    await page.getByRole("button", { name: statusName, exact: true }).click();
    await page
      .getByRole("status")
      .filter({ hasText: "原核验已执行" })
      .waitFor();
    assert.equal(
      await page.getByRole("button", { name: retryName, exact: true }).count(),
      0,
    );
  });
  await screenshot(page, `${kind}-applied-recovered-390`);
  await page.unroute(routeUrl);
  for (const [name, token, body, expected] of [
    [
      "foreign-actor",
      tokens.admin,
      appliedRequest,
      putaway ? 1080520017 : 1080530023,
    ],
    [
      "altered-selection",
      tokens.warehouse,
      { ...appliedRequest, taskLineIds: [appliedRequest.taskLineIds[0]] },
      putaway ? 1080520017 : 1080530023,
    ],
    ["driver-forbidden", tokens.driver, appliedRequest, 403],
  ]) {
    const result = await unchanged(`${kind}-${name}`, () =>
      request(statusUrl, token, "POST", body),
    );
    assert.equal(result.code, expected);
  }
  const recordTable = putaway
    ? "rd_putaway_batch_scan_record"
    : "rd_loading_scan_record";
  const digest = await sql(
    `SELECT request_digest FROM ${recordTable} WHERE id=${applied.recordId}`,
  );
  assert.match(digest, /^[a-f0-9]{64}$/i);
  try {
    await sql(
      `UPDATE ${recordTable} SET request_digest=${putaway ? "''" : "NULL"} WHERE id=${applied.recordId}`,
    );
    const legacy = await unchanged(`${kind}-legacy-no-digest-refused`, () =>
      request(statusUrl, tokens.warehouse, "POST", appliedRequest),
    );
    assert.equal(legacy.code, putaway ? 1080520017 : 1080530023);
  } finally {
    await sql(
      `UPDATE ${recordTable} SET request_digest='${digest}' WHERE id=${applied.recordId}`,
    );
  }
  await page
    .getByRole("button", {
      name: putaway ? "确认最终入库" : "确认最终出库",
      exact: true,
    })
    .click();
  await dialog(page);
  await page
    .getByRole("status")
    .filter({ hasText: putaway ? "入库已确认" : "出库已确认" })
    .waitFor();
  const completed = await state(kind, initial.task.id);
  assert.equal(
    completed.task.status,
    putaway ? "PUTAWAY_COMPLETED" : "OUTBOUNDED",
  );
  const afterFinal = await unchanged(
    `${kind}-original-applied-after-final`,
    () => api(statusUrl, tokens.warehouse, "POST", appliedRequest),
  );
  assert.equal(afterFinal.state, "APPLIED");
  assert.equal(afterFinal.recordId, applied.recordId);
  evidence.push({
    name: `${kind}-explicit-final-post`,
    taskId: initial.task.id,
    status: completed.task.status,
  });
  return {
    original,
    appliedRequest,
    recordId: applied.recordId,
    finalTask: completed.task,
  };
};

try {
  const putaway = await exercise("putaway", ready, upper.bags);
  const loading = await createLoading([extra.bags[2], lower.bags[2]], "R11");
  loadingIds.push(loading.task.id);
  const loaded = await exercise("loading", loading, [
    extra.bags[2],
    lower.bags[2],
  ]);
  await unchanged("putaway-real-MySQL-status-and-confirm-share-row-lock", () =>
    lockedReplay("putaway", putaway.appliedRequest),
  );
  await unchanged("loading-real-MySQL-status-and-confirm-share-row-lock", () =>
    lockedReplay("loading", loaded.appliedRequest),
  );
  assert.deepEqual(errors, []);
  const result = {
    marker: "R11本地软件验证（非现场设备或签字验收）",
    productionTaskId: fixture.taskId,
    putaway,
    loading: loaded,
    evidence,
    pageErrors: errors,
    screens: 6,
    result: "PASS",
  };
  await fs.writeFile(
    path.join(output, "result.json"),
    JSON.stringify(result, null, 2),
  );
  await fs.writeFile(
    path.join(output, "commands.json"),
    JSON.stringify(commands, null, 2),
  );
  console.log(
    JSON.stringify({
      result: "PASS",
      productionTaskId: fixture.taskId,
      putawayTaskId: ready.task.id,
      loadingTaskId: loading.task.id,
      checks: evidence.length,
      pageErrors: errors,
    }),
  );
} catch (error) {
  await fs.writeFile(
    path.join(output, "commands.json"),
    JSON.stringify(commands, null, 2),
  );
  for (const [index, context] of contexts.entries()) {
    for (const [pageIndex, page] of context.pages().entries()) {
      await page
        .screenshot({
          path: path.join(output, `failure-${index}-${pageIndex}.png`),
          fullPage: true,
        })
        .catch(() => {});
      await fs
        .writeFile(
          path.join(output, `failure-${index}-${pageIndex}.html`),
          await page.content(),
        )
        .catch(() => {});
    }
  }
  throw error;
} finally {
  await browser.close();
}
