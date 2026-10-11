import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { root, baseUrl, api, prepareFixture } from "./RdQrLocalFixture.mjs";
const { accounts, tokens } = await prepareFixture();
const fixture = JSON.parse(
  await fs.readFile(
    path.join(root, "temp/qr-production-task-verification/fixture.json"),
    "utf8",
  ),
);
const source = await api(
  `/rd/production-task/get?id=${fixture.taskId}`,
  tokens.production,
);
const marker = "PDA真实候选界面验证（非现场数据）";
const taskId = await api(
  "/rd/production-task/create",
  tokens.production,
  "POST",
  {
    customerName: source.customerName,
    productionDate: "2026-10-11",
    shiftCode: source.shiftCode,
    remark: marker,
  },
);
const out = path.join(root, "temp/qr-pda-workspace-verification");
await fs.mkdir(out, { recursive: true });
const require = createRequire(
  path.join(process.env.RD_BROWSER_MODULES || root, "package.json"),
);
const { chromium } = require("playwright");
const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL || "msedge",
});
try {
  const page = await browser.newPage({ viewport: { width: 390, height: 900 } });
  await page.goto(`${baseUrl}/pda/production?productionTaskId=${taskId}`, {
    waitUntil: "networkidle",
  });
  await page
    .getByPlaceholder("账号", { exact: true })
    .fill(accounts.production.username);
  await page
    .getByPlaceholder("密码", { exact: true })
    .fill(accounts.production.password);
  await page.getByRole("button", { name: "登录", exact: true }).click();
  await page.waitForURL((url) => url.pathname === "/pda/production");
  await page.getByRole("heading", { name: "实际产出", exact: true }).waitFor();
  const model = page.locator(".production-pda .el-select input").first();
  await model.fill("QR-TASK-A");
  await page.getByRole("option", { name: "QR-TASK-A", exact: true }).click();
  const choices = page.locator(".furnace-fields .el-select");
  await choices.nth(0).click();
  await page
    .getByRole("option", { name: `TASK-${fixture.taskId}`, exact: true })
    .click();
  await choices.nth(1).click();
  await page.getByRole("option", { name: "上层", exact: true }).click();
  assert.equal(await choices.nth(0).innerText(), `TASK-${fixture.taskId}`);
  assert.equal(await choices.nth(1).innerText(), "上层");
  for (const width of [1440, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    await page.screenshot({
      path: path.join(out, `choices-${width}.png`),
      fullPage: true,
    });
    const dimensions = await page.evaluate(() => ({
      doc: document.documentElement.clientWidth,
      scroll: document.documentElement.scrollWidth,
      main: document.querySelector(".pda-content").clientWidth,
      mainScroll: document.querySelector(".pda-content").scrollWidth,
    }));
    assert.ok(
      dimensions.scroll <= dimensions.doc &&
        dimensions.mainScroll <= dimensions.main,
    );
  }
  const recordCount = (
    await api(
      `/rd/production-output/page?productionTaskId=${taskId}&pageNo=1&pageSize=10`,
      tokens.production,
    )
  ).total;
  assert.equal(recordCount, 0);
  await api("/rd/production-task/cancel", tokens.production, "PUT", {
    id: taskId,
    version: 0,
  });
  const own = await api(
    "/rd/loading-task/my-page?pageNo=1&pageSize=100&activeOnly=true",
    tokens.driver,
  );
  assert.ok(
    own.list.every(
      (task) =>
        ["ASSIGNED", "LOADING", "PENDING_REVIEW", "EXCEPTION_PENDING"].includes(
          task.status,
        ) && task.forkliftDriverId === accounts.driver.userId,
    ),
  );
  const all = await api(
    "/rd/loading-task/my-page?pageNo=1&pageSize=100",
    tokens.driver,
  );
  const explicitHistory = await api(
    "/rd/loading-task/my-page?pageNo=1&pageSize=100&activeOnly=false",
    tokens.driver,
  );
  assert.deepEqual(explicitHistory, all);
  assert.equal(
    own.total,
    all.list.filter((task) =>
      ["ASSIGNED", "LOADING", "PENDING_REVIEW", "EXCEPTION_PENDING"].includes(
        task.status,
      ),
    ).length,
  );
  const terminalIntersection = await api(
    "/rd/loading-task/my-page?pageNo=1&pageSize=100&activeOnly=true&status=CANCELLED",
    tokens.driver,
  );
  assert.equal(terminalIntersection.total, 0);
  assert.deepEqual(terminalIntersection.list, []);
  const assignedIntersection = await api(
    "/rd/loading-task/my-page?pageNo=1&pageSize=100&activeOnly=true&status=ASSIGNED",
    tokens.driver,
  );
  const assignedIds = own.list
    .filter((task) => task.status === "ASSIGNED")
    .map((task) => task.id);
  assert.equal(assignedIntersection.total, assignedIds.length);
  assert.deepEqual(
    assignedIntersection.list.map((task) => task.id),
    assignedIds,
  );
  for (const [activeOnly, expected] of [
    [true, own],
    [false, all],
  ]) {
    const page = await api(
      `/rd/loading-task/my-page?pageNo=1&pageSize=1&activeOnly=${activeOnly}`,
      tokens.driver,
    );
    assert.equal(page.total, expected.total);
    assert.deepEqual(
      page.list.map((task) => task.id),
      expected.list.slice(0, 1).map((task) => task.id),
    );
  }
  const forged = await api(
    `/rd/loading-task/my-page?pageNo=1&pageSize=100&activeOnly=true&forkliftDriverId=${accounts.otherDriver.userId}`,
    tokens.driver,
  );
  assert.deepEqual(
    forged.list.map((task) => task.id),
    own.list.map((task) => task.id),
  );
  const result = {
    result: "PASS",
    taskId,
    realExistingChoices: true,
    choicesDidNotCreateOutput: true,
    activeOwnLoadingTasks: own.total,
    historyLoadingTasks: all.total,
    foreignDriverIgnored: true,
    explicitHistoryCompatibility: true,
    activeStatusIntersection: true,
    serverPaginationTotals: true,
    widths: [1440, 390, 320],
    physicalAcceptance: false,
  };
  await fs.writeFile(
    path.join(out, "choices-ui-result.json"),
    JSON.stringify(result, null, 2),
  );
  console.log(JSON.stringify(result));
} finally {
  await browser.close();
}
