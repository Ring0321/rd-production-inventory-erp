import assert from "node:assert/strict";
import test from "node:test";
import {
  PLAN,
  MARKER,
  createJournal,
  newManifest,
  createClient,
  validateLocalSettings,
  summarizePackages,
  assertQuality,
  seedFlow,
  collectFacts,
  validateFacts,
  main,
  DATE,
} from "./Seed-RdFlowDemo.mjs";

test("nine synthetic outputs cover eight scenarios, 30 bags and 29800 kg", () => {
  assert.equal(PLAN.length, 9);
  assert.equal(new Set(PLAN.map((row) => row.scenario)).size, 8);
  assert.equal(
    PLAN.reduce((sum, row) => sum + row.count, 0),
    30,
  );
  assert.equal(
    PLAN.reduce((sum, row) => sum + row.count * row.weight, 0),
    29800,
  );
  const shipped = PLAN.filter((row) => row.scenario === "shipped");
  assert.equal(shipped.length, 2);
  assert.equal(shipped[0].furnaceNo, shipped[1].furnaceNo);
  assert.notEqual(shipped[0].furnaceLayer, shipped[1].furnaceLayer);
  assert.equal(shipped[1].weight, 800);
  assert.equal(
    new Set(PLAN.map((row) => `${row.furnaceNo}:${row.furnaceLayer}`)).size,
    9,
  );
  for (const row of PLAN) {
    assert.match(row.productModel, /演示/);
    assert.match(row.furnaceNo, /演示/);
    assert.match(row.furnaceLayer, /演示/);
  }
});

test("only the existing local 3310/48080 runtime is accepted", () => {
  const settings = { RD_DB_PORT: "3310", RD_BACKEND_PORT: "48080" };
  validateLocalSettings(settings);
  for (const invalid of [
    { RD_DB_PORT: "3306" },
    { RD_BACKEND_PORT: "8080" },
    { RD_DB_HOST: "192.168.1.2" },
  ])
    assert.throws(() => validateLocalSettings({ ...settings, ...invalid }));
});

test("read-only client permits login and GET but refuses all business writes", async () => {
  const calls = [];
  const client = createClient({
    check: true,
    fetchImpl: async (url, options) => {
      calls.push({ url, ...options });
      return { ok: true, json: async () => ({ code: 0, data: 7 }) };
    },
  });
  assert.equal(await client.call("/system/auth/login", null, "POST", {}), 7);
  assert.equal(await client.call("/rd/production-task/get?id=7", "dummy"), 7);
  await assert.rejects(
    client.call("/rd/production-task/create", "dummy", "POST", {}),
  );
  await assert.rejects(client.call("https://outside.invalid/", "dummy"));
  assert.ok(
    calls.every((call) =>
      call.url.startsWith("http://127.0.0.1:48080/admin-api/"),
    ),
  );
  assert.ok(calls.every((call) => call.redirect === "error"));
});

test("API rejection and transport errors never expose server messages or credentials", async () => {
  const client = createClient({
    fetchImpl: async () => ({
      ok: true,
      json: async () => ({ code: 403, msg: "secret-credential-value" }),
    }),
  });
  await assert.rejects(
    client.call("/rd/production-task/get?id=1", "not-a-real-token"),
    (error) =>
      error.code === "API_REJECTED" &&
      error.details.apiCode === 403 &&
      !JSON.stringify(error).includes("secret-credential-value"),
  );
  const broken = createClient({
    fetchImpl: async () => {
      throw new Error("secret-transport-message");
    },
  });
  await assert.rejects(
    broken.call("/rd/production-task/get?id=1", "not-a-real-token"),
    (error) =>
      error.code === "API_TRANSPORT_FAILED" &&
      !JSON.stringify(error).includes("secret-transport-message"),
  );
});

test("driver cannot journal keeper scanning or posting actions", async () => {
  const journal = createJournal(newManifest(), async () =>
    assert.fail("must not persist"),
  );
  for (const [method, url] of [
    ["POST", "/rd/loading-task/batch-scan/confirm"],
    ["POST", "/rd/loading-task/confirm-outbound"],
    ["PUT", "/rd/putaway-task/confirm"],
  ])
    await assert.rejects(
      journal.run("forbidden", { role: "driver", method, url }, async () =>
        assert.fail("must not mutate"),
      ),
    );
});

test("journal persists intent before write and success afterward; replays do not write", async () => {
  const events = [];
  const journal = createJournal(newManifest(), async (value) => {
    events.push(value.events.at(-1).type);
  });
  let calls = 0;
  const spec = {
    role: "production",
    method: "POST",
    url: "/rd/production-task/create",
  };
  const run = () =>
    journal.run("task", spec, async () => {
      assert.deepEqual(events, ["intent"]);
      calls++;
      return 42;
    });
  assert.equal(await run(), 42);
  assert.equal(await run(), 42);
  assert.equal(calls, 1);
  assert.deepEqual(events, ["intent", "success"]);
  assert.equal(journal.value.marker, MARKER);
  assert.equal(
    createJournal(structuredClone(journal.value), async () => {}).result(
      "task",
    ),
    42,
  );
});

test("lost mutation response is durable and cannot be retried automatically", async () => {
  let saved;
  const journal = createJournal(newManifest(), async (value) => {
    saved = structuredClone(value);
  });
  const spec = {
    role: "production",
    method: "POST",
    url: "/rd/production-task/create",
  };
  await assert.rejects(
    journal.run("task", spec, async () => {
      throw new Error("secret server text");
    }),
    (error) =>
      error.code === "WRITE_UNCERTAIN" && !error.message.includes("secret"),
  );
  const recovered = createJournal(saved, async () =>
    assert.fail("must not persist"),
  );
  await assert.rejects(
    recovered.run("task", spec, async () => assert.fail("must not retry")),
    (error) => error.code === "WRITE_UNCERTAIN",
  );
});

test("checkpoint persistence failure prevents any API mutation", async () => {
  const journal = createJournal(newManifest(), async () => {
    throw new Error("disk full");
  });
  await assert.rejects(
    journal.run(
      "task",
      { role: "production", method: "POST", url: "/rd/production-task/create" },
      async () => assert.fail("API must not run"),
    ),
  );
});

test("journal rejects incompatible plans, edited history and duplicate completion", () => {
  const changed = newManifest();
  changed.planHash = "different";
  assert.throws(() => createJournal(changed, async () => {}));
  const corrupt = newManifest();
  corrupt.events.push({ type: "success", key: "task", result: 1 });
  assert.throws(() => createJournal(corrupt, async () => {}));
});

test("weight/state summary refuses missing weights and duplicate identities", () => {
  assert.deepEqual(
    summarizePackages([
      { id: 1, status: "OUTBOUNDED", packageWeight: 1000 },
      { id: 2, status: "OUTBOUNDED", packageWeight: "800" },
    ]),
    { count: 2, weightKg: 1800, states: { OUTBOUNDED: 2 } },
  );
  assert.throws(() => summarizePackages([{ id: 1, status: "IN_STOCK" }]));
  assert.throws(() =>
    summarizePackages([
      { id: 1, status: "IN_STOCK", packageWeight: 1000 },
      { id: 1, status: "IN_STOCK", packageWeight: 1000 },
    ]),
  );
});

test("QC requires persisted complete coverage, actual sample and separate reviewer", () => {
  const bags = [
    { id: 1, packageCode: "A", packageWeight: 1000 },
    { id: 2, packageCode: "B", packageWeight: 1000 },
  ];
  const inspection = {
    status: "PASSED",
    conclusion: "PASSED",
    coverageCount: 2,
    sampleCount: 1,
    productionOutputId: 9,
    packageId: 1,
    registrarId: 2,
    submitterId: 2,
    reviewerId: 3,
    members: bags.map((bag, index) => ({
      packageId: bag.id,
      packageCode: bag.packageCode,
      packageWeight: bag.packageWeight,
      sampled: index === 0,
    })),
    items: [
      {
        samplePackageId: 1,
        qualified: true,
        standardMin: 97,
        standardMax: 100,
        measuredValue: 98.6,
        unit: "%",
        itemCode: "DEMO-FC",
        itemName: "固定碳（演示模拟值，非实测）",
      },
    ],
  };
  assertQuality(inspection, bags, 9, "PASSED", { quality: 2, admin: 3 });
  for (const bad of [
    { members: inspection.members.slice(0, 1) },
    {
      members: inspection.members.map((member) => ({
        ...member,
        sampled: null,
      })),
    },
    { reviewerId: 2 },
    { items: [{ ...inspection.items[0], samplePackageId: 999 }] },
    { items: [{ ...inspection.items[0], measuredValue: 92 }] },
  ])
    assert.throws(() =>
      assertQuality({ ...inspection, ...bad }, bags, 9, "PASSED", {
        quality: 2,
        admin: 3,
      }),
    );
});

// In-memory contract fixtures only. No fetch, credentials, filesystem or running services.
function memoryRuntime() {
  const actors = {
    admin: 101,
    production: 102,
    quality: 103,
    warehouse: 104,
    driver: 105,
    sales: 106,
  };
  const tokens = Object.fromEntries(
    Object.keys(actors).map((role) => [role, role]),
  );
  const db = Object.fromEntries(
    [
      "production-task",
      "warehouse-location",
      "production-output",
      "quality-inspection",
      "putaway-task",
      "stock-lot",
      "stock-transaction",
      "sale-order",
      "order-fulfillment",
      "loading-task",
      "inbound-order",
      "outbound-order",
      "pickup-slip",
    ].map((name) => [name, []]),
  );
  const bags = [];
  const writes = [];
  let sequence = 1000;
  let interrupt;
  const add = (domain, value) => {
    const record = { id: ++sequence, ...structuredClone(value) };
    db[domain].push(record);
    return record;
  };
  const find = (domain, id) =>
    db[domain].find((entry) => entry.id === Number(id));
  const physical = (locationId) =>
    bags.filter(
      (bag) => bag.locationId === locationId && bag.status === "IN_STOCK",
    );
  const recalculateLocations = () => {
    for (const location of db["warehouse-location"]) {
      const stock = physical(location.id);
      const reserved = db["putaway-task"].filter(
        (task) =>
          task.plannedLocationId === location.id &&
          task.status !== "PUTAWAY_COMPLETED",
      );
      Object.assign(location, {
        occupiedPackages: stock.length,
        occupiedWeight: stock.reduce((sum, bag) => sum + bag.packageWeight, 0),
        reservedPackages: reserved.reduce(
          (sum, task) => sum + task.packageCount,
          0,
        ),
        reservedWeight: reserved.reduce(
          (sum, task) => sum + task.totalWeight,
          0,
        ),
      });
    }
  };
  const taskLines = (domain, bagId) =>
    db[domain].filter((task) =>
      task.lines.some((line) => line.packageId === bagId),
    );
  const client = {
    async call(url, token, method = "GET", body) {
      const parsed = new URL(url, "http://memory.invalid");
      const route = parsed.pathname;
      const parts = route.split("/").filter(Boolean);
      const domain = parts[1];
      const action = parts.at(-1);
      const params = Object.fromEntries(parsed.searchParams);
      if (method === "GET") {
        if (interrupt?.(route, db)) {
          interrupt = undefined;
          throw new Error("simulated read interruption");
        }
        recalculateLocations();
        if (route === "/rd/putaway-task/location-map")
          return structuredClone(db["warehouse-location"]);
        if (domain === "package-unit") {
          if (action === "trace")
            return structuredClone(
              bags.filter(
                (bag) =>
                  bag.productionOutputId === Number(params.productionOutputId),
              ),
            );
          if (action === "task-packages")
            return structuredClone(
              bags.filter(
                (bag) =>
                  bag.productionTaskId === Number(params.productionTaskId),
              ),
            );
          const bag = bags.find(
            (bag) =>
              bag.packageCode ===
              (params.packageCode || decodeURIComponent(action)),
          );
          const qc = db["quality-inspection"].find((inspection) =>
            inspection.members.some((member) => member.packageId === bag.id),
          );
          if (parts[2] === "trace-detail")
            return {
              ...bag,
              internalView: true,
              qualityInspectionId: qc.id,
              qualityStatus: qc.status,
              qualityCoverageCount: qc.coverageCount,
              qualitySampleCount: qc.sampleCount,
              qualitySampled: qc.packageId === bag.id,
              events: [
                "PRODUCTION",
                "LABEL",
                "QUALITY",
                ...(taskLines("putaway-task", bag.id).length
                  ? ["PUTAWAY"]
                  : []),
                ...(bag.stockLotId ? ["STOCK"] : []),
                ...(taskLines("loading-task", bag.id).length
                  ? ["LOADING"]
                  : []),
                ...(taskLines("loading-task", bag.id).some(
                  (task) => task.exceptions.length,
                )
                  ? ["EXCEPTION"]
                  : []),
              ].map((stage) => ({ stage })),
            };
          const putaways = taskLines("putaway-task", bag.id);
          const loadings = taskLines("loading-task", bag.id);
          if (parts[2] === "work-context")
            return {
              packageCode: bag.packageCode,
              tasks: [
                ...putaways
                  .filter((task) => task.status !== "PUTAWAY_COMPLETED")
                  .map((task) => ({
                    type: "PUTAWAY",
                    taskId: task.id,
                    status: task.status,
                  })),
                ...loadings
                  .filter((task) => task.status !== "OUTBOUNDED")
                  .map((task) => ({
                    type: "LOADING",
                    taskId: task.id,
                    status: task.status,
                  })),
              ],
              pickupSlipId: loadings.find(
                (task) => task.status !== "OUTBOUNDED",
              )?.pickupSlipId,
            };
          const node = (kind, referenceId) => ({
            kind,
            referenceId,
            nodeKey: `${kind}:${referenceId}`,
          });
          return {
            rootPackageCode: bag.packageCode,
            rootNodeKey: `PACKAGE:${bag.id}`,
            sections: [{ kind: "QUALITY", state: "COMPLETE" }],
            nodes: [
              node("PRODUCTION_TASK", bag.productionTaskId),
              node("QUALITY_INSPECTION", qc.id),
              ...putaways.map((task) => node("PUTAWAY_TASK", task.id)),
              ...loadings.map((task) => node("LOADING_TASK", task.id)),
            ],
            edges: [
              {
                fromNodeKey: `QUALITY_INSPECTION:${qc.id}`,
                toNodeKey: `PACKAGE:${bag.id}`,
                relation: "QUALITY_COVERAGE",
                evidenceType: "QUALITY_INSPECTION_MEMBER",
                sampled: qc.packageId === bag.id,
              },
            ],
          };
        }
        if (action === "available-package-page") {
          const claimed = new Set(
            db["loading-task"]
              .filter((task) => task.status !== "OUTBOUNDED")
              .flatMap((task) => task.lines.map((line) => line.packageId)),
          );
          const list = bags.filter(
            (bag) => bag.status === "IN_STOCK" && !claimed.has(bag.id),
          );
          return { total: list.length, list: structuredClone(list) };
        }
        const table = route.includes("pickup-slip") ? "pickup-slip" : domain;
        if (action === "page") {
          let list = db[table];
          for (const [key, value] of Object.entries(params)) {
            if (["pageNo", "pageSize", "keyword"].includes(key)) continue;
            list = list.filter((entry) => String(entry[key]) === value);
          }
          const offset = (Number(params.pageNo) - 1) * Number(params.pageSize);
          return {
            total: list.length,
            list: structuredClone(
              list.slice(offset, offset + Number(params.pageSize)),
            ),
          };
        }
        const record = find(table, params.id);
        if (table === "putaway-task" || table === "loading-task") {
          const { lines, exceptions, ...task } = record;
          return structuredClone({ task, lines, exceptions });
        }
        if (table === "pickup-slip") {
          const { lines, ...slip } = record;
          return structuredClone({ slip, lines });
        }
        if (table === "outbound-order" && action === "detail") {
          const { lines, ...order } = record;
          return structuredClone({ order, lines });
        }
        const result = structuredClone(record);
        if (table === "production-output") delete result.clientRequestId; // Actual response DTO omits this field.
        return result;
      }
      writes.push({ route, role: token, body: structuredClone(body) });
      if (route === "/rd/production-task/create")
        return add(domain, {
          ...body,
          managerId: actors[token],
          status: "ACTIVE",
          version: 0,
        }).id;
      if (route === "/rd/warehouse-location/create")
        return add(domain, {
          ...body,
          occupiedPackages: 0,
          occupiedWeight: 0,
          reservedPackages: 0,
          reservedWeight: 0,
        }).id;
      if (route === "/rd/production-output/create")
        return add(domain, { ...body, status: "DRAFT", version: 0 }).id;
      if (route === "/rd/production-output/confirm") {
        Object.assign(find(domain, body.id), {
          status: "CONFIRMED",
          version: 1,
        });
        return true;
      }
      if (route === "/rd/package-unit/generate") {
        const output = find("production-output", body.productionOutputId);
        const created = Array.from({ length: body.packageCount }, () => ({
          ...output,
          id: ++sequence,
          productionOutputId: output.id,
          packageCode: `MEMORY-${sequence}`,
          status: "CREATED",
          version: 0,
        }));
        bags.push(...created);
        return structuredClone(created);
      }
      if (route === "/rd/package-unit/print-batch") {
        for (const bag of bags)
          if (body.packageIds.includes(bag.id)) bag.status = "LABELED";
        return true;
      }
      if (route === "/rd/quality-inspection/create-batch") {
        const units = bags.filter((bag) => body.packageIds.includes(bag.id));
        return add(domain, {
          ...body,
          status: "DRAFT",
          productionOutputId: units[0].productionOutputId,
          registrarId: actors[token],
          coverageCount: units.length,
          sampleCount: body.samplePackageIds.length,
          members: units.map((bag) => ({
            packageId: bag.id,
            packageCode: bag.packageCode,
            packageWeight: bag.packageWeight,
            sampled: body.samplePackageIds.includes(bag.id),
          })),
          items: body.items.map((item) => ({
            ...item,
            qualified:
              item.measuredValue >= item.standardMin &&
              item.measuredValue <= item.standardMax,
          })),
        }).id;
      }
      if (domain === "quality-inspection") {
        const inspection = find(domain, params.id || body.id);
        inspection.status =
          action === "submit"
            ? "SUBMITTED"
            : action === "approve"
              ? "PASSED"
              : "REJECTED";
        if (action === "submit") inspection.submitterId = actors[token];
        else
          Object.assign(inspection, {
            conclusion: inspection.status,
            reviewerId: actors[token],
          });
        for (const bag of bags.filter((bag) =>
          inspection.packageIds.includes(bag.id),
        ))
          bag.status =
            action === "submit"
              ? "QC_PENDING"
              : action === "approve"
                ? "QC_PASSED"
                : "QC_REJECTED";
        return true;
      }
      if (route === "/rd/putaway-task/create") {
        const units = bags.filter((bag) => body.packageIds.includes(bag.id));
        units.forEach((bag) => {
          bag.status = "INBOUND_PENDING";
        });
        return add(domain, {
          ...body,
          status: "ASSIGNED",
          version: 0,
          plannedLocationCode: find(
            "warehouse-location",
            body.plannedLocationId,
          ).locationCode,
          warehouseKeeperId: actors[token],
          packageCount: units.length,
          scannedPackageCount: 0,
          totalWeight: units.reduce((sum, bag) => sum + bag.packageWeight, 0),
          exceptions: [],
          lines: units.map((bag, index) => ({
            id: ++sequence,
            lineNo: index + 1,
            packageId: bag.id,
            packageCode: bag.packageCode,
            packageWeight: bag.packageWeight,
            lineStatus: "PENDING",
            scanCount: 0,
          })),
        }).id;
      }
      if (route === "/rd/putaway-task/batch-scan/confirm") {
        const task = find(domain, body.id);
        task.lines.forEach((line) =>
          Object.assign(line, { lineStatus: "SCANNED", scanCount: 1 }),
        );
        Object.assign(task, {
          status: "PENDING_CONFIRMATION",
          scannedPackageCount: task.packageCount,
          version: 1,
        });
        return { recordId: ++sequence, confirmedCount: task.packageCount };
      }
      if (route === "/rd/putaway-task/confirm") {
        const task = find(domain, body.id);
        const location = find("warehouse-location", body.finalLocationId);
        const inbound = add("inbound-order", {
          status: "POSTED",
          sourceType: "PUTAWAY_TASK",
          bizDate: DATE,
          posterId: actors[token],
          totalPackages: task.packageCount,
          totalWeight: task.totalWeight,
          lines: [],
        });
        for (const line of task.lines) {
          const bag = bags.find((bag) => bag.id === line.packageId);
          let lot = db["stock-lot"].find(
            (lot) =>
              lot.locationId === location.id &&
              lot.furnaceNo === bag.furnaceNo &&
              lot.furnaceLayer === bag.furnaceLayer,
          );
          if (!lot)
            lot = add("stock-lot", {
              ...bag,
              id: undefined,
              locationId: location.id,
              warehouseQty: 0,
              stockWeight: 0,
              inboundQty: 0,
              outboundQty: 0,
              lockedQty: 0,
              availableQty: 0,
            });
          if (!lot.id) lot.id = ++sequence;
          const before = lot.warehouseQty;
          lot.warehouseQty++;
          lot.availableQty++;
          lot.inboundQty++;
          lot.stockWeight += bag.packageWeight;
          Object.assign(bag, {
            status: "IN_STOCK",
            locationId: location.id,
            locationCode: location.locationCode,
            stockLotId: lot.id,
          });
          const tx = add("stock-transaction", {
            sourceLineNo: bag.packageCode,
            sourceType: "PUTAWAY_TASK",
            customerName: bag.customerName,
            transactionType: "PRODUCTION_IN",
            stockLotId: lot.id,
            locationId: location.id,
            operatorId: actors[token],
            bizDate: DATE,
            quantity: 1,
            weight: bag.packageWeight,
            beforeQty: before,
            afterQty: before + 1,
          });
          const inboundLine = {
            id: ++sequence,
            packageId: bag.id,
            locationId: location.id,
            stockLotId: lot.id,
            stockTransactionId: tx.id,
            quantity: 1,
            weight: bag.packageWeight,
          };
          inbound.lines.push(inboundLine);
          Object.assign(line, {
            lineStatus: "CONFIRMED",
            inboundOrderId: inbound.id,
            inboundOrderLineId: inboundLine.id,
            stockLotId: lot.id,
            stockTransactionId: tx.id,
          });
          find("production-output", bag.productionOutputId).status =
            "INBOUNDED";
        }
        Object.assign(task, {
          status: "PUTAWAY_COMPLETED",
          finalLocationId: location.id,
        });
        return inbound.id;
      }
      if (route === "/rd/sale-order/create")
        return add(domain, { ...body, status: "OPEN", allocatedQty: 0 }).id;
      if (route === "/rd/order-fulfillment/allocate") {
        const sale = find("sale-order", body.orderId);
        sale.allocatedQty += body.quantity;
        sale.status = "FULFILLED";
        const lot = find("stock-lot", body.stockLotId);
        lot.lockedQty += body.quantity;
        lot.availableQty -= body.quantity;
        return add(domain, {
          ...body,
          status: "ALLOCATED",
          allocatedQty: body.quantity,
          outboundQty: 0,
        }).id;
      }
      if (route === "/rd/loading-task/pickup-slip/create")
        return add("pickup-slip", {
          ...body,
          status: "ISSUED",
          lines: body.lines.map((line) => ({ ...line, id: ++sequence })),
        }).id;
      if (route === "/rd/loading-task/create") {
        const pickup = find("pickup-slip", body.pickupSlipId);
        pickup.status = "DISPATCHED";
        const lines = body.selections.map((selection, index) => {
          const bag = bags.find((bag) => bag.id === selection.packageId);
          const slipLine = pickup.lines.find(
            (line) => line.id === selection.pickupSlipLineId,
          );
          const allocation = db["order-fulfillment"].find(
            (entry) =>
              entry.orderId === slipLine.saleOrderId &&
              entry.stockLotId === bag.stockLotId,
          );
          assert.ok(allocation, "exact lot allocation must exist");
          return {
            id: ++sequence,
            lineNo: index + 1,
            ...selection,
            packageCode: bag.packageCode,
            stockLotId: bag.stockLotId,
            saleOrderId: slipLine.saleOrderId,
            fulfillmentId: allocation.id,
            plannedLocationId: bag.locationId,
            packageWeight: bag.packageWeight,
            lineStatus: "PLANNED",
          };
        });
        return add(domain, {
          ...body,
          customerName: pickup.customerName,
          warehouseKeeperId: actors[token],
          status: "ASSIGNED",
          version: 0,
          plannedPackageCount: lines.length,
          scannedPackageCount: 0,
          plannedWeight: lines.reduce(
            (sum, line) => sum + line.packageWeight,
            0,
          ),
          scannedWeight: 0,
          lines,
          exceptions: [],
        }).id;
      }
      if (route === "/rd/loading-task/batch-scan/confirm") {
        const task = find(domain, body.taskId);
        for (const line of task.lines)
          if (body.taskLineIds.includes(line.id)) line.lineStatus = "SCANNED";
        task.scannedPackageCount = body.taskLineIds.length;
        task.scannedWeight = task.lines
          .filter((line) => line.lineStatus === "SCANNED")
          .reduce((sum, line) => sum + line.packageWeight, 0);
        task.status =
          task.scannedPackageCount === task.lines.length
            ? "PENDING_REVIEW"
            : "LOADING";
        task.version++;
        return {
          success: true,
          recordId: ++sequence,
          confirmedCount: body.taskLineIds.length,
        };
      }
      if (route === "/rd/loading-task/confirm-outbound") {
        const task = find(domain, body.taskId);
        const orders = [];
        for (const allocationId of new Set(
          task.lines.map((line) => line.fulfillmentId),
        )) {
          const allocation = find("order-fulfillment", allocationId);
          const lines = task.lines.filter(
            (line) => line.fulfillmentId === allocationId,
          );
          const lot = find("stock-lot", allocation.stockLotId);
          const order = add("outbound-order", {
            status: "POSTED",
            outboundDate: DATE,
            orderId: allocation.orderId,
            fulfillmentId: allocation.id,
            stockLotId: lot.id,
            quantity: lines.length,
            weight: lines.reduce((sum, line) => sum + line.packageWeight, 0),
            remark: body.remark,
            lines: [],
          });
          for (const line of lines) {
            const bag = bags.find((bag) => bag.id === line.packageId);
            const before = lot.warehouseQty;
            lot.warehouseQty--;
            lot.outboundQty++;
            lot.lockedQty--;
            lot.stockWeight -= bag.packageWeight;
            bag.status = "OUTBOUNDED";
            const tx = add("stock-transaction", {
              sourceLineNo: bag.packageCode,
              customerName: bag.customerName,
              sourceType: "OUTBOUND_ORDER",
              transactionType: "OUTBOUND",
              stockLotId: lot.id,
              locationId: bag.locationId,
              operatorId: actors[token],
              bizDate: DATE,
              quantity: -1,
              weight: -bag.packageWeight,
              beforeQty: before,
              afterQty: before - 1,
            });
            const outboundLine = {
              id: ++sequence,
              packageId: bag.id,
              stockTransactionId: tx.id,
              packageWeight: bag.packageWeight,
            };
            order.lines.push(outboundLine);
            Object.assign(line, {
              lineStatus: "OUTBOUNDED",
              outboundOrderId: order.id,
              outboundOrderLineId: outboundLine.id,
              stockTransactionId: tx.id,
            });
          }
          allocation.status = "OUTBOUNDED";
          allocation.outboundQty = lines.length;
          find("sale-order", allocation.orderId).status = "CLOSED";
          orders.push(order.id);
        }
        Object.assign(task, {
          status: "OUTBOUNDED",
          reviewerId: actors[token],
        });
        find("pickup-slip", task.pickupSlipId).status = "SHIPPED";
        return {
          outboundOrderIds: orders,
          outboundPackageCount: task.lines.length,
          outboundWeight: task.plannedWeight,
        };
      }
      if (route === "/rd/loading-task/exception") {
        const task = find(domain, body.taskId);
        const exception = {
          id: ++sequence,
          ...body,
          exceptionStatus: "OPEN",
          reporterId: actors[token],
        };
        task.exceptions.push(exception);
        task.status = "EXCEPTION_PENDING";
        return exception.id;
      }
      assert.fail(`unexpected in-memory contract: ${method} ${route}`);
    },
  };
  let persisted;
  const persist = async (manifest) => {
    persisted = structuredClone(manifest);
  };
  const journal = createJournal(newManifest(actors), persist);
  return {
    client,
    tokens,
    actors,
    journal,
    db,
    bags,
    writes,
    persist,
    get persisted() {
      return persisted;
    },
    stopReadWhen: (fn) => {
      interrupt = fn;
    },
  };
}

test("pure full-flow contract simulation validates all relationships and reruns with zero new writes", async () => {
  const runtime = memoryRuntime();
  validateFacts(await collectFacts(runtime), runtime.journal, runtime.actors);
  await seedFlow(runtime);
  const facts = await collectFacts(runtime, true);
  const summary = validateFacts(facts, runtime.journal, runtime.actors, true);
  assert.equal(summary.count, 30);
  assert.equal(summary.weightKg, 29800);
  assert.equal(runtime.db["loading-task"].length, 3);
  assert.equal(runtime.db["outbound-order"].length, 2);
  const count = runtime.writes.length;
  runtime.journal = createJournal(runtime.persisted, runtime.persist);
  validateFacts(await collectFacts(runtime), runtime.journal, runtime.actors);
  await seedFlow(runtime);
  assert.equal(runtime.writes.length, count);
  validateFacts(
    await collectFacts(runtime, true),
    runtime.journal,
    runtime.actors,
    true,
  );
  assert.ok(
    runtime.writes
      .filter((write) => /scan|confirm-outbound/.test(write.route))
      .every((write) => write.role === "warehouse"),
  );
  assert.equal(
    runtime.writes.filter((write) => write.role === "driver").length,
    1,
  );
  assert.equal(
    runtime.writes.find((write) => write.role === "driver").route,
    "/rd/loading-task/exception",
  );
});

test("settled partial seed resumes after read failure without duplicating persisted records", async () => {
  const runtime = memoryRuntime();
  runtime.stopReadWhen(
    (route, db) =>
      route === "/rd/package-unit/trace" &&
      db["production-output"].length === 2,
  );
  await assert.rejects(seedFlow(runtime), /simulated read interruption/);
  runtime.journal = createJournal(runtime.persisted, runtime.persist);
  runtime.journal.settled();
  validateFacts(await collectFacts(runtime), runtime.journal, runtime.actors);
  await seedFlow(runtime);
  validateFacts(
    await collectFacts(runtime, true),
    runtime.journal,
    runtime.actors,
    true,
  );
  assert.equal(runtime.db["production-task"].length, 1);
  assert.equal(runtime.db["production-output"].length, 9);
  assert.equal(runtime.bags.length, 30);
});

test("validation refuses drift, lost manifest and non-exact tail allocation", async () => {
  const runtime = memoryRuntime();
  await seedFlow(runtime);
  const facts = await collectFacts(runtime, true);
  const validate = (value) =>
    validateFacts(value, runtime.journal, runtime.actors, true);
  for (const mutate of [
    (value) => {
      value.bags[0].packageWeight = null;
    },
    (value) => {
      value.locationMap[0].reservedPackages = 0;
    },
    (value) => {
      value.loading.shipped.lines[0].fulfillmentId =
        value.loading.shipped.lines.at(-1).fulfillmentId;
    },
    (value) => {
      value.qc.rejected.members[0].sampled = false;
    },
    (value) => {
      value.sales.push({ ...value.sales[0], id: 999999 });
    },
    (value) => {
      value.loading.loading.task.status = "OUTBOUNDED";
    },
    (value) => {
      value.transactions.push({ ...value.transactions[0], id: 999999 });
    },
    (value) => {
      value.relations[value.bags[0].id].sections[0].state = "REDACTED";
    },
  ]) {
    const changed = structuredClone(facts);
    mutate(changed);
    assert.throws(() => validate(changed));
  }
  assert.throws(() =>
    validateFacts(
      facts,
      createJournal(newManifest(runtime.actors), runtime.persist),
      runtime.actors,
    ),
  );
});

test("no mode is a no-I/O usage response, never an implicit seed", async () => {
  assert.equal((await main([])).result, "USAGE");
  assert.equal((await main(["--help"])).result, "USAGE");
  await assert.rejects(main(["--seed", "--check"]));
});
