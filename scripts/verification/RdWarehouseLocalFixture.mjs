import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { randomUUID } from "node:crypto";
import {
  prepareQuality,
  batchRequest,
  api,
  root,
} from "./RdQualityLocalFixture.mjs";

export { root, api, request, baseUrl } from "./RdQrLocalFixture.mjs";
export const output = path.join(root, "temp/qr-warehouse-pda-verification");

export const prepareWarehouse = async ({
  extraCount = 3,
  lowerCount = 3,
} = {}) => {
  const source = await prepareQuality();
  const { tokens, accounts, taskId, marker, createOutput, sql } = source;
  const upper = await createOutput(3, "warehouse-upper", "D4-A", "上层", 1000);
  const extra = await createOutput(
    extraCount,
    "warehouse-upper",
    "D4-A",
    "上层",
    1000,
  );
  const lower = await createOutput(
    lowerCount,
    "warehouse-lower",
    "D4-B",
    "下层",
    800,
  );
  for (const record of [upper, extra, lower]) {
    const indices = record.bags.map((_, index) => index);
    const inspectionId = await api(
      "/rd/quality-inspection/create-batch",
      tokens.quality,
      "POST",
      batchRequest(record.bags, indices, [0]),
    );
    await api(
      `/rd/quality-inspection/submit?id=${inspectionId}`,
      tokens.quality,
      "POST",
    );
    await api(
      `/rd/quality-inspection/approve?id=${inspectionId}`,
      tokens.admin,
      "POST",
    );
  }
  const locationIds = [];
  for (const suffix of ["A", "B"]) {
    locationIds.push(
      await api("/rd/warehouse-location/create", tokens.admin, "POST", {
        warehouseCode: `D4-${taskId}`,
        warehouseName: `仓管验证仓库-${taskId}（非现场）`,
        areaCode: "TEST",
        areaName: "本地验证区",
        locationCode: `D4-${taskId}-${suffix}`,
        locationName: marker,
        capacityPackages: 20,
        capacityWeight: 20000,
        frozen: false,
        enabled: true,
        remark: "仓管PDA验证（非现场数据）",
      }),
    );
  }
  const createPutaway = async (record, locationId) => {
    const id = await api("/rd/putaway-task/create", tokens.warehouse, "POST", {
      plannedLocationId: locationId,
      forkliftDriverId: accounts.driver.userId,
      packageIds: record.bags.map((bag) => bag.id),
      remark: "仓管PDA验证（非现场数据）",
    });
    return await api(`/rd/putaway-task/get?id=${id}`, tokens.warehouse);
  };
  const ready = await createPutaway(upper, locationIds[1]);
  const stock = async (record, locationId) => {
    const detail = await createPutaway(record, locationId);
    await api("/rd/putaway-task/batch-scan/confirm", tokens.warehouse, "POST", {
      id: detail.task.id,
      version: detail.task.version,
      packageCode: detail.lines[0].packageCode,
      taskLineIds: detail.lines.map((line) => line.id),
      idempotencyKey: randomUUID(),
      deviceCode: "D4-FIXTURE-NOT-FIELD",
    });
    const verified = await api(
      `/rd/putaway-task/get?id=${detail.task.id}`,
      tokens.warehouse,
    );
    await api("/rd/putaway-task/confirm", tokens.warehouse, "PUT", {
      id: detail.task.id,
      version: verified.task.version,
      finalLocationId: locationId,
    });
    return detail.task.id;
  };
  const extraPutawayId = await stock(extra, locationIds[0]);
  const lowerPutawayId = await stock(lower, locationIds[1]);
  const createLoading = async (bags, suffix) => {
    const entries = new Map();
    for (const bag of bags) {
      const key = JSON.stringify([
        bag.productModel,
        bag.furnaceNo,
        bag.furnaceLayer,
      ]);
      if (!entries.has(key)) entries.set(key, []);
      entries.get(key).push(bag);
    }
    const groups = [...entries.values()];
    const orderIds = [];
    for (const [index, group] of groups.entries()) {
      const units = [];
      for (const bag of group)
        units.push(
          await api(`/rd/package-unit/get?id=${bag.id}`, tokens.warehouse),
        );
      assert.ok(
        units.every(
          (bag) =>
            bag.status === "IN_STOCK" && bag.stockLotId === units[0].stockLotId,
        ),
        "Allocation requires one actual in-stock lot per pickup line",
      );
      const orderId = await api("/rd/sale-order/create", tokens.sales, "POST", {
        orderNo: `D4-SO-${taskId}-${suffix}-${index}-${Date.now()}`,
        orderDate: "2026-10-11",
        customerName: marker,
        productModel: group[0].productModel,
        requiredQty: group.length,
        actualOutputQty: 0,
        remark: "仓管PDA验证（非现场销售订单）",
      });
      await api("/rd/order-fulfillment/allocate", tokens.sales, "POST", {
        orderId,
        stockLotId: units[0].stockLotId,
        quantity: group.length,
        remark: "非现场数据，完整订单履约验证",
      });
      orderIds.push(orderId);
    }
    const slipId = await api(
      "/rd/loading-task/pickup-slip/create",
      tokens.sales,
      "POST",
      {
        slipNo: `D4-${taskId}-${suffix}-${Date.now()}`,
        customerName: marker,
        requestedPackageCount: bags.length,
        requestedWeight: bags.reduce((sum, bag) => sum + bag.packageWeight, 0),
        vehiclePlate: "LOCAL-D4",
        remark: "本地模拟提货，非现场装车",
        lines: groups.map((group, index) => ({
          lineNo: index + 1,
          saleOrderId: orderIds[index],
          productModel: group[0].productModel,
          furnaceNo: group[0].furnaceNo,
          furnaceLayer: group[0].furnaceLayer,
          requestedPackageCount: group.length,
          requestedWeight: group.reduce(
            (sum, bag) => sum + bag.packageWeight,
            0,
          ),
          remark: "非现场数据",
        })),
      },
    );
    const slip = await api(
      `/rd/loading-task/pickup-slip/get?id=${slipId}`,
      tokens.sales,
    );
    const id = await api("/rd/loading-task/create", tokens.warehouse, "POST", {
      pickupSlipId: slipId,
      forkliftDriverId: accounts.driver.userId,
      remark: "仓管PDA验证（非现场数据）",
      selections: bags.map((bag) => {
        const line = slip.lines.find(
          (line) =>
            line.productModel === bag.productModel &&
            line.furnaceNo === bag.furnaceNo &&
            line.furnaceLayer === bag.furnaceLayer,
        );
        assert.ok(
          line,
          "Every simulated package must match a real pickup line",
        );
        return { packageId: bag.id, pickupSlipLineId: line.id };
      }),
    });
    return await api(`/rd/loading-task/get?id=${id}`, tokens.warehouse);
  };
  const fingerprint = async () => {
    const ids = [...upper.bags, ...extra.bags, ...lower.bags].map(
      (bag) => bag.id,
    );
    assert.ok(ids.every((id) => Number.isSafeInteger(id) && id > 0));
    const values = await sql(
      `SELECT JSON_OBJECT('id',id,'status',status,'version',version,'locationId',location_id,'stockLotId',stock_lot_id) FROM rd_package_unit WHERE id IN (${ids.join(",")}) ORDER BY id`,
    );
    const locations = await sql(
      `SELECT JSON_OBJECT('id',id,'occupiedPackages',occupied_packages,'occupiedWeight',occupied_weight,'reservedPackages',reserved_packages,'reservedWeight',reserved_weight,'version',version) FROM rd_location WHERE id IN (${locationIds.join(",")}) ORDER BY id`,
    );
    const task = await api(
      `/rd/putaway-task/get?id=${ready.task.id}`,
      tokens.warehouse,
    );
    return JSON.stringify({ values, locations, task });
  };
  await fs.mkdir(output, { recursive: true });
  const fixture = {
    marker: "仓管PDA验证（非现场数据）",
    taskId,
    upper,
    extra,
    lower,
    locationIds,
    putawayTaskId: ready.task.id,
    extraPutawayId,
    lowerPutawayId,
  };
  await fs.writeFile(
    path.join(output, "fixture.json"),
    JSON.stringify(fixture, null, 2),
  );
  return {
    ...source,
    upper,
    extra,
    lower,
    locationIds,
    ready,
    fixture,
    createLoading,
    fingerprint,
  };
};
