import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { execFile } from "node:child_process";
import { promisify } from "node:util";
import { randomUUID, randomBytes } from "node:crypto";
import { root, api, prepareFixture } from "./RdQrLocalFixture.mjs";

export { root, api, request, baseUrl } from "./RdQrLocalFixture.mjs";
export const output = path.join(root, "temp/qr-quality-batch-verification");
export const prepareQuality = async () => {
  const { tokens, accounts } = await prepareFixture();
  const marker = `批次抽检本地验证（非现场数据）-${randomBytes(4).toString("hex")}`;
  const taskId = await api(
    "/rd/production-task/create",
    tokens.production,
    "POST",
    {
      customerName: marker,
      productionDate: "2026-10-11",
      shiftCode: "验证班",
      remark: marker,
    },
  );
  const createOutput = async (
    count,
    name,
    model = "QR-QC-A",
    layer = "上层",
  ) => {
    const id = await api(
      "/rd/production-output/create",
      tokens.production,
      "POST",
      {
        productionTaskId: taskId,
        clientRequestId: randomUUID(),
        productionDate: "2026-10-11",
        customerName: marker,
        productModel: model,
        furnaceNo: `QC-${taskId}-${name}`,
        furnaceLayer: layer,
        outputQty: count,
        packageWeight: 1000,
        sourceSheet: marker,
        remark: marker,
      },
    );
    const record = await api(
      `/rd/production-output/get?id=${id}`,
      tokens.production,
    );
    await api("/rd/production-output/confirm", tokens.production, "POST", {
      id,
      version: record.version,
    });
    const bags = await api(
      "/rd/package-unit/generate",
      tokens.production,
      "POST",
      { productionOutputId: id, packageCount: count },
    );
    await api("/rd/package-unit/print-batch", tokens.production, "POST", {
      packageIds: bags.map((bag) => bag.id),
    });
    return {
      id,
      bags: (
        await api(
          `/rd/package-unit/trace?productionOutputId=${id}`,
          tokens.production,
        )
      ).sort((a, b) => a.id - b.id),
    };
  };
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
  const sql = async (statement) =>
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
          statement,
        ],
        { env: { ...process.env, MYSQL_PWD: settings.RD_DB_PASSWORD } },
      )
    ).stdout.trim();
  await fs.mkdir(output, { recursive: true });
  return { tokens, accounts, marker, taskId, createOutput, sql };
};
export const batchRequest = (bags, coverage, samples, measuredValue = 98) => ({
  packageId: bags[coverage[0]].id,
  packageIds: coverage.map((index) => bags[index].id),
  samplePackageIds: samples.map((index) => bags[index].id),
  clientRequestId: randomUUID(),
  items: samples.map((index) => ({
    samplePackageId: bags[index].id,
    itemCode: "TEST-SIC",
    itemName: "系统验证项目（非现场测量）",
    standardMin: 97,
    standardMax: 100,
    measuredValue,
    unit: "%",
    qualified: true,
  })),
  remark: "抽检闭环验证（非现场结果）",
});
