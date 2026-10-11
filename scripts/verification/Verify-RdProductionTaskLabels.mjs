import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { randomUUID, randomBytes } from "node:crypto";
import { createRequire } from "node:module";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import {
  root,
  baseUrl,
  api,
  request,
  prepareFixture,
  login,
} from "./RdQrLocalFixture.mjs";

const directory = path.join(root, "temp/qr-production-task-verification");
await fs.mkdir(directory, { recursive: true });
const { tokens, accounts } = await prepareFixture();
const env = Object.fromEntries(
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
assert.equal(Number(env.RD_DB_PORT), 3310);
const sql = async (text) =>
  (
    await promisify(execFile)(
      "mysql",
      [
        "--host=127.0.0.1",
        "--port=3310",
        "--user=root",
        "--default-character-set=utf8mb4",
        "--batch",
        "--skip-column-names",
        "rd_erp",
        "-e",
        text,
      ],
      { env: { ...process.env, MYSQL_PWD: env.RD_DB_PASSWORD } },
    )
  ).stdout.trim();
const marker = "二维码生产任务标签验证（非现场数据）";
const taskGet = (id) =>
  api(`/rd/production-task/get?id=${id}`, tokens.production);
const packagesFor = (id) =>
  api(
    `/rd/package-unit/task-packages?productionTaskId=${id}`,
    tokens.production,
  );
const outputGet = (id) =>
  api(`/rd/production-output/get?id=${id}`, tokens.production);
const taskCreate = (customerName, shiftCode = "甲班") =>
  api("/rd/production-task/create", tokens.production, "POST", {
    customerName,
    productionDate: "2026-10-11",
    shiftCode,
    remark: marker,
  });
const payloadFor = (task, model, layer, count, weight) => ({
  productionTaskId: task.id,
  productionDate: task.productionDate,
  customerName: task.customerName,
  shiftCode: task.shiftCode,
  productModel: model,
  furnaceNo: `TASK-${task.id}`,
  furnaceLayer: layer,
  outputQty: count,
  packageWeight: weight,
  furnaceOutDate: "2026-10-10",
  clientRequestId: randomUUID(),
  remark: marker,
});
const generate = async (id, count) => {
  const row = await outputGet(id);
  await api("/rd/production-output/confirm", tokens.production, "POST", {
    id,
    version: row.version,
  });
  return api("/rd/package-unit/generate", tokens.production, "POST", {
    productionOutputId: id,
    packageCount: count,
  });
};
const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
const pageErrors = [];
const signIn = async (account, width = 390) => {
  const context = await browser.newContext({
    viewport: { width, height: 1000 },
  });
  const page = await context.newPage();
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.goto(`${baseUrl}/login`, { waitUntil: "networkidle" });
  await page.getByPlaceholder("账号", { exact: true }).fill(account.username);
  await page.getByPlaceholder("密码", { exact: true }).fill(account.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => !url.pathname.includes("/login"));
  return { context, page };
};
let fixture;
try {
  if (process.env.RD_TASK_UI_ONLY === "true") {
    fixture = JSON.parse(
      await fs.readFile(path.join(directory, "fixture.json"), "utf8"),
    );
    assert.equal(fixture.marker, marker);
  } else {
    const customer = `${marker}-${Date.now()}`;
    const mainId = await taskCreate(customer);
    const task = await taskGet(mainId);
    const body = payloadFor(task, "QR-TASK-A", "上层", 6, 1000);
    const simultaneous = await Promise.all([
      api("/rd/production-output/create", tokens.production, "POST", body),
      api("/rd/production-output/create", tokens.production, "POST", body),
    ]);
    assert.equal(simultaneous[0], simultaneous[1]);
    const firstId = simultaneous[0];
    assert.equal(
      Number(
        await sql(
          `SELECT COUNT(*) FROM rd_production_output WHERE production_task_id=${mainId}`,
        ),
      ),
      1,
    );
    assert.notEqual(
      (
        await request(
          "/rd/production-output/create",
          tokens.production,
          "POST",
          { ...body, packageWeight: 999 },
        )
      ).code,
      0,
    );
    assert.notEqual(
      (
        await request(
          "/rd/production-output/create",
          tokens.admin,
          "POST",
          body,
        )
      ).code,
      0,
    );
    const before = await outputGet(firstId);
    assert.equal(before.operatorId, accounts.production.userId);
    assert.equal(before.productionTaskNo, task.taskNo);
    assert.ok(before.locationCode == null);
    assert.notEqual(
      (
        await request(
          "/rd/production-output/update",
          tokens.production,
          "PUT",
          {
            ...body,
            id: firstId,
            version: before.version,
            clientRequestId: randomUUID(),
          },
        )
      ).code,
      0,
    );
    assert.equal(
      await api(
        "/rd/production-output/create",
        tokens.production,
        "POST",
        body,
      ),
      firstId,
    );
    assert.notEqual(
      (
        await request("/rd/production-task/close", tokens.production, "PUT", {
          id: mainId,
          version: task.version,
        })
      ).code,
      0,
    );

    const { page, context } = await signIn(accounts.production);
    await page.goto(
      `${baseUrl}/rd/production/production-entry?productionTaskId=${mainId}`,
      { waitUntil: "networkidle" },
    );
    await page
      .getByRole("heading", { name: "实际产出", exact: true })
      .waitFor();
    await page
      .locator(".production-pda .el-select input")
      .first()
      .fill("QR-TASK-B");
    await page.getByRole("option", { name: "QR-TASK-B", exact: true }).click();
    await page.getByLabel("炉号", { exact: true }).fill(`TASK-${mainId}`);
    await page.getByLabel("炉层", { exact: true }).fill("下层");
    await page
      .getByRole("spinbutton", { name: "实际整包数", exact: true })
      .fill("4");
    await page
      .getByRole("spinbutton", { name: "实称单包重量 kg", exact: true })
      .fill("600");
    let committedBody,
      committedId,
      lose = true;
    await page.route(
      "**/admin-api/rd/production-output/create",
      async (route) => {
        if (lose) {
          lose = false;
          committedBody = route.request().postDataJSON();
          committedId = await api(
            "/rd/production-output/create",
            tokens.production,
            "POST",
            committedBody,
          );
          await route.abort("failed");
        } else await route.continue();
      },
    );
    await page.getByRole("button", { name: "保存草稿", exact: true }).click();
    try {
      await page
        .getByRole("button", { name: "重试原请求", exact: true })
        .waitFor();
    } catch (error) {
      await page.screenshot({
        path: path.join(directory, "pda-submit-failure.png"),
        fullPage: true,
      });
      console.log(
        JSON.stringify({
          date: task.productionDate,
          dateArray: Array.isArray(task.productionDate),
          form: await page.locator(".production-pda").innerText(),
        }),
      );
      throw error;
    }
    await page.screenshot({
      path: path.join(directory, "pda-lost-response.png"),
    });
    assert.equal(
      Number(
        await sql(
          `SELECT COUNT(*) FROM rd_production_output WHERE production_task_id=${mainId}`,
        ),
      ),
      2,
    );
    await page.getByRole("button", { name: "重试原请求", exact: true }).click();
    await page
      .getByRole("button", { name: "重试原请求", exact: true })
      .waitFor({ state: "hidden" });
    assert.equal(
      Number(
        await sql(
          `SELECT COUNT(*) FROM rd_production_output WHERE production_task_id=${mainId}`,
        ),
      ),
      2,
    );
    assert.equal((await outputGet(committedId)).clientRequestId, undefined);
    assert.equal(committedBody.productionTaskId, mainId);
    assert.equal(committedBody.packageWeight, 600);
    assert.equal(
      (await outputGet(committedId)).operatorId,
      accounts.production.userId,
    );
    await page.unroute("**/admin-api/rd/production-output/create");
    const firstPacks = await generate(firstId, 6);
    await page
      .getByRole("button", { name: "刷新任务与生产记录", exact: true })
      .click();
    const secondCard = page
      .locator(".record-item")
      .filter({ hasText: "QR-TASK-B" });
    await secondCard
      .getByRole("button", { name: "确认产出", exact: true })
      .click();
    await page
      .getByRole("button", { name: "确定", exact: true })
      .last()
      .click();
    await secondCard.getByText("已确认", { exact: true }).waitFor();
    await secondCard
      .getByRole("button", { name: "生成包码", exact: true })
      .click();
    await page
      .getByRole("button", { name: "确定", exact: true })
      .last()
      .click();
    await secondCard
      .getByRole("button", { name: "生成包码", exact: true })
      .waitFor();
    await page.waitForFunction(() =>
      [...document.querySelectorAll(".record-item")].some(
        (item) =>
          item.textContent?.includes("QR-TASK-B") &&
          item.textContent?.includes("包码数") &&
          item.querySelector(".el-button.is-disabled"),
      ),
    );
    const secondPacks = (await packagesFor(mainId)).filter(
      (pkg) => pkg.productionOutputId === committedId,
    );
    const packs = await packagesFor(mainId);
    assert.equal(packs.length, 10);
    assert.equal(firstPacks.length + secondPacks.length, 10);
    assert.equal(
      packs.reduce((sum, pkg) => sum + Number(pkg.packageWeight), 0),
      8400,
    );
    assert.ok(
      packs.every(
        (pkg) =>
          pkg.productionTaskId === mainId &&
          pkg.productionTaskNo === task.taskNo &&
          pkg.shiftCode === "甲班",
      ),
    );
    assert.notEqual(
      (
        await request("/rd/package-unit/generate", tokens.production, "POST", {
          productionOutputId: firstId,
          packageCount: 6,
        })
      ).code,
      0,
    );
    const otherId = await taskCreate(`${customer}-对照`);
    const other = await taskGet(otherId);
    const otherOutput = await api(
      "/rd/production-output/create",
      tokens.production,
      "POST",
      payloadFor(other, "QR-OTHER", "中层", 1, 1000),
    );
    const otherPackages = await generate(otherOutput, 1);
    assert.notEqual(
      (
        await request(
          "/rd/package-unit/print-batch",
          tokens.production,
          "POST",
          {
            productionTaskId: mainId,
            packageIds: [packs[0].id, otherPackages[0].id],
          },
        )
      ).code,
      0,
    );
    assert.equal(
      Number(
        await sql(
          `SELECT COUNT(*) FROM rd_label_print_log WHERE package_id IN (${packs.map((p) => p.id).join(",")})`,
        ),
      ),
      0,
    );
    await api("/rd/production-task/close", tokens.production, "PUT", {
      id: mainId,
      version: (await taskGet(mainId)).version,
    });
    assert.equal((await taskGet(mainId)).status, "CLOSED");
    assert.equal(
      await api(
        "/rd/production-output/create",
        tokens.production,
        "POST",
        body,
      ),
      firstId,
    );
    assert.notEqual(
      (
        await request(
          "/rd/production-output/create",
          tokens.production,
          "POST",
          { ...body, clientRequestId: randomUUID() },
        )
      ).code,
      0,
    );
    assert.equal((await packagesFor(mainId)).length, 10);
    const publicView = await api(
      `/rd/package-unit/public-trace/${packs[0].packageCode}`,
    );
    assert.equal(JSON.stringify(publicView).includes(task.taskNo), false);
    assert.equal(publicView.productionTaskId, undefined);
    const privateView = await api(
      `/rd/package-unit/trace-detail/${packs[0].packageCode}`,
      tokens.production,
    );
    assert.equal(privateView.productionTaskId, mainId);

    await page.goto(
      `${baseUrl}/rd/stock/package?productionTaskId=${mainId}&labelAction=taskPrint`,
      { waitUntil: "networkidle" },
    );
    const dialog = page.getByRole("dialog", {
      name: "打印吨袋标签",
      exact: true,
    });
    await dialog.waitFor();
    assert.equal(
      await dialog.locator(".el-table__body-wrapper tbody tr").count(),
      10,
    );
    const popupPromise = page.waitForEvent("popup");
    await dialog
      .getByRole("button", { name: "打开打印窗口", exact: true })
      .click();
    const popup = await popupPromise;
    await popup.waitForLoadState("load");
    assert.equal(await popup.locator(".goods-label").count(), 10);
    for (const pkg of packs) {
      const label = popup
        .locator(".goods-label")
        .filter({ hasText: pkg.packageCode });
      assert.equal(await label.count(), 1);
      assert.ok((await label.innerText()).includes(task.taskNo));
      assert.ok((await label.innerText()).includes(String(pkg.packageWeight)));
    }
    assert.equal(
      await popup
        .getByText(otherPackages[0].packageCode, { exact: true })
        .count(),
      0,
    );
    await popup.pdf({
      path: path.join(directory, "task-labels.pdf"),
      preferCSSPageSize: true,
    });
    await dialog
      .getByRole("button", { name: "确认打印成功", exact: true })
      .click();
    await page
      .getByRole("button", { name: "确定", exact: true })
      .last()
      .click();
    await dialog.waitFor({ state: "hidden" });
    assert.ok(
      (await packagesFor(mainId)).every((pkg) => pkg.status === "LABELED"),
    );
    assert.equal(
      Number(
        await sql(
          `SELECT COUNT(*) FROM rd_label_print_log WHERE package_id IN (${packs.map((p) => p.id).join(",")})`,
        ),
      ),
      10,
    );
    await context.close();

    const editableId = await taskCreate(`${customer}-重试快照`);
    const editable = await taskGet(editableId);
    const original = payloadFor(editable, "QR-CANCEL", "表皮", 1, 200);
    const cancelledOutputId = await api(
      "/rd/production-output/create",
      tokens.production,
      "POST",
      original,
    );
    await api("/rd/production-output/cancel", tokens.production, "PUT", {
      id: cancelledOutputId,
      version: (await outputGet(cancelledOutputId)).version,
    });
    await api("/rd/production-task/update", tokens.production, "PUT", {
      ...editable,
      customerName: `${customer}-已调整`,
      shiftCode: "乙班",
    });
    assert.equal(
      await api(
        "/rd/production-output/create",
        tokens.production,
        "POST",
        original,
      ),
      cancelledOutputId,
    );
    assert.equal((await outputGet(cancelledOutputId)).status, "CANCELLED");

    const raceId = await taskCreate(`${customer}-并发`);
    const race = await taskGet(raceId);
    const raceBody = payloadFor(race, "QR-RACE", "中层", 1, 300);
    const raceReplies = await Promise.all([
      request(
        "/rd/production-output/create",
        tokens.production,
        "POST",
        raceBody,
      ),
      request("/rd/production-task/cancel", tokens.production, "PUT", {
        id: raceId,
        version: race.version,
      }),
    ]);
    assert.equal(raceReplies.filter((reply) => reply.code === 0).length, 1);
    const raceState = await taskGet(raceId);
    const raceOutputs = Number(
      await sql(
        `SELECT COUNT(*) FROM rd_production_output WHERE production_task_id=${raceId}`,
      ),
    );
    assert.equal(
      raceState.status === "CANCELLED" ? raceOutputs === 0 : raceOutputs === 1,
      true,
    );
    if (raceState.status === "ACTIVE") {
      const raceOutId = raceReplies[0].data;
      const row = await outputGet(raceOutId);
      await api("/rd/production-output/confirm", tokens.production, "POST", {
        id: raceOutId,
        version: row.version,
      });
      const generateRace = await Promise.all([
        request("/rd/package-unit/generate", tokens.production, "POST", {
          productionOutputId: raceOutId,
          packageCount: 1,
        }),
        request("/rd/production-task/close", tokens.production, "PUT", {
          id: raceId,
          version: race.version,
        }),
      ]);
      assert.equal(generateRace[0].code, 0);
      assert.equal((await packagesFor(raceId)).length, 1);
      if (generateRace[1].code !== 0)
        await api("/rd/production-task/close", tokens.production, "PUT", {
          id: raceId,
          version: (await taskGet(raceId)).version,
        });
      assert.equal((await taskGet(raceId)).status, "CLOSED");
    }
    for (const role of [
      "warehouse",
      "quality",
      "driver",
      "sales",
      "otherDriver",
    ])
      assert.equal(
        (
          await request("/rd/production-task/create", tokens[role], "POST", {
            productionDate: "2026-10-11",
            customerName: customer,
            shiftCode: "甲班",
          })
        ).code,
        403,
      );
    const readonlyAccount = {
      username: `e3readonly${randomBytes(3).toString("hex")}`,
      password: `Qt3A${randomBytes(5).toString("hex")}!`,
    };
    readonlyAccount.userId = await api(
      "/system/user/create",
      tokens.admin,
      "POST",
      { ...readonlyAccount, nickname: "E3本地只读", remark: marker },
    );
    await api("/system/permission/assign-user-role", tokens.admin, "POST", {
      userId: readonlyAccount.userId,
      roleIds: [600005],
    });
    const readonlyToken = await login(readonlyAccount);
    assert.equal(
      (await api(`/rd/production-task/get?id=${mainId}`, readonlyToken)).id,
      mainId,
    );
    assert.equal(
      (
        await request("/rd/production-task/cancel", readonlyToken, "PUT", {
          id: mainId,
          version: 1,
        })
      ).code,
      403,
    );
    fixture = {
      marker,
      taskId: mainId,
      taskNo: task.taskNo,
      outputIds: [firstId, committedId],
      packageIds: packs.map((p) => p.id),
      codes: packs.map((p) => p.packageCode),
      otherTaskId: otherId,
      otherCode: otherPackages[0].packageCode,
      readonlyAccount,
      requestReplay: true,
      raceChecked: true,
    };
    await fs.writeFile(
      path.join(directory, "fixture.json"),
      JSON.stringify(fixture, null, 2),
    );
  }

  const before = await sql(
    `SELECT COUNT(*) FROM rd_package_unit WHERE production_task_id=${fixture.taskId}; SELECT COUNT(*) FROM rd_stock_transaction WHERE package_id IN (${fixture.packageIds.join(",")}); SELECT COUNT(*) FROM rd_label_print_log WHERE package_id IN (${fixture.packageIds.join(",")})`,
  );
  const viewChecks = [];
  for (const [name, account] of [
    ["production", accounts.production],
    ["readonly", fixture.readonlyAccount],
  ]) {
    const { context, page } = await signIn(account);
    await page.goto(
      `${baseUrl}/rd/production/task?productionTaskId=${fixture.taskId}`,
      { waitUntil: "networkidle" },
    );
    await page.getByText(fixture.taskNo, { exact: true }).first().waitFor();
    if (name === "readonly")
      assert.equal(
        await page
          .getByRole("button", { name: "新增任务", exact: true })
          .count(),
        0,
      );
    for (const width of [1440, 390, 320]) {
      await page.setViewportSize({ width, height: 1000 });
      if (name === "production") {
        await page.goto(
          `${baseUrl}/rd/production/production-entry?productionTaskId=${fixture.taskId}`,
          { waitUntil: "networkidle" },
        );
        await page.getByText(fixture.taskNo, { exact: true }).first().waitFor();
        assert.equal(
          await page
            .getByRole("button", { name: "保存草稿", exact: true })
            .count(),
          0,
        );
      }
      await page.screenshot({
        path: path.join(directory, `${name}-${width}.png`),
        fullPage: true,
      });
      const dims = await page.evaluate(() => ({
        width: document.documentElement.clientWidth,
        scroll: document.documentElement.scrollWidth,
      }));
      assert.ok(dims.scroll <= dims.width, `${name} ${width} overflow`);
      viewChecks.push({ name, width, overflow: false });
    }
    await context.close();
  }
  assert.equal(
    await sql(
      `SELECT COUNT(*) FROM rd_package_unit WHERE production_task_id=${fixture.taskId}; SELECT COUNT(*) FROM rd_stock_transaction WHERE package_id IN (${fixture.packageIds.join(",")}); SELECT COUNT(*) FROM rd_label_print_log WHERE package_id IN (${fixture.packageIds.join(",")})`,
    ),
    before,
  );
  assert.deepEqual(pageErrors, []);
  const result = {
    marker,
    taskId: fixture.taskId,
    taskNo: fixture.taskNo,
    codes: fixture.codes,
    packageCount: 10,
    weightKg: 8400,
    firstPrintLogs: 10,
    requestReplay: fixture.requestReplay,
    raceChecked: fixture.raceChecked,
    viewChecks,
    queryUnchanged: true,
    fieldAcceptance: false,
  };
  await fs.writeFile(
    path.join(directory, "result.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result, null, 2));
} finally {
  await browser.close();
}
