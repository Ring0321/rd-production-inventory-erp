import fs from "node:fs/promises";
import path from "node:path";
import { createHash } from "node:crypto";
import { fileURLToPath, pathToFileURL } from "node:url";

export const MARKER = "DUOXU-DEMO-FLOW-V1";
export const CUSTOMER = "演示客户·宁川新材";
export const DATE = "2026-10-15";
export const BACKEND = "http://127.0.0.1:48080";
const ROOT = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const DIRECTORY = path.join(ROOT, "temp/flow-showcase-demo");
const MANIFEST = path.join(DIRECTORY, "manifest.json");
const LOCK = path.join(DIRECTORY, "seed.lock");
const SHIFT = "白班（演示）";
const MODEL = "人造石墨 NC-320（演示）";
const NOTE = `${MARKER}；全合成本地演示，非真实生产或交易；检测值为模拟值，非实测；库位坐标为示意。`;
const outputPlan = (
  key,
  scenario,
  count,
  weight,
  furnace,
  layer,
  location,
) => ({
  key,
  scenario,
  count,
  weight,
  location,
  productModel: MODEL,
  furnaceNo: `演示1-15#0${furnace}`,
  furnaceLayer: `${layer}层（演示）`,
});
export const PLAN = Object.freeze(
  [
    outputPlan("waiting", "waitingQC", 3, 1000, 1, "上"),
    outputPlan("rejected", "rejected", 2, 1000, 1, "中"),
    outputPlan("passed", "passedNoAssignment", 4, 1000, 1, "下"),
    outputPlan("assigned", "assignedPutaway", 4, 1000, 2, "上", "reserved"),
    outputPlan("stock", "stockAvailable", 6, 1000, 2, "中", "stock"),
    outputPlan("loading", "loadingInProgress", 4, 1000, 2, "下", "loading"),
    outputPlan("shipped", "shipped", 4, 1000, 3, "上", "shipped"),
    outputPlan("tail", "shipped", 1, 800, 3, "中", "shipped"),
    outputPlan("exception", "loadingException", 2, 1000, 3, "下", "exception"),
  ].map(Object.freeze),
);
const WAREHOUSE = "DEMO-FLOW-V1";
const LOCATIONS = [
  ["reserved", "待上架预留位", 4, 4000, "01", "01"],
  ["stock", "合格品在库位", 6, 6000, "01", "02"],
  ["loading", "装车核验位", 4, 4000, "01", "03"],
  ["shipped", "已发运周转位", 5, 5000, "02", "01"],
  ["exception", "装车异常暂存位", 2, 2000, "02", "02"],
  ["empty", "空闲备用位", 8, 8000, "02", "03"],
  ["frozen", "冻结隔离位", 8, 8000, "03", "01"],
].map(([key, name, capacityPackages, capacityWeight, rowCode, columnCode]) => ({
  key,
  body: {
    warehouseCode: WAREHOUSE,
    warehouseName: "DEMO业务全景演示仓（合成示意）",
    areaCode: "DEMO-A",
    areaName: "石墨成品演示区（非现场）",
    rowCode,
    columnCode,
    levelCode: "01",
    locationCode: `${WAREHOUSE}-${rowCode}-${columnCode}-01`,
    locationName: `${name}（演示）`,
    capacityPackages,
    capacityWeight,
    enabled: true,
    frozen: key === "frozen",
    locationStatus: "AVAILABLE",
    remark: NOTE,
  },
}));
const SHIPMENTS = [
  { key: "loading", outputs: ["loading"], status: "LOADING", scanned: 2 },
  {
    key: "shipped",
    outputs: ["shipped", "tail"],
    status: "OUTBOUNDED",
    scanned: 5,
  },
  {
    key: "exception",
    outputs: ["exception"],
    status: "EXCEPTION_PENDING",
    scanned: 0,
  },
];
const CHECKPOINT_KEYS = [
  "task",
  ...LOCATIONS.map((location) => `location.${location.key}`),
  ...PLAN.flatMap((row) => {
    const actions = [
      "create",
      "confirm",
      "generate",
      "print",
      "qc.create",
      "qc.submit",
    ];
    if (row.key !== "waiting") actions.push("qc.review");
    if (row.location) actions.push("putaway.create");
    if (row.location && row.key !== "assigned")
      actions.push("putaway.scan", "putaway.post");
    if (SHIPMENTS.some((shipment) => shipment.outputs.includes(row.key)))
      actions.push("sale", "allocation");
    return actions.map((action) => `output.${row.key}.${action}`);
  }),
  ...SHIPMENTS.flatMap((shipment) =>
    [
      "pickup",
      "create",
      ...(shipment.scanned ? ["scan"] : []),
      ...(shipment.key === "shipped" ? ["outbound"] : []),
      ...(shipment.key === "exception" ? ["exception"] : []),
    ].map((action) => `shipment.${shipment.key}.${action}`),
  ),
];
const hash = (value) =>
  createHash("sha256").update(JSON.stringify(value)).digest("hex");
const PLAN_HASH = hash({ PLAN, LOCATIONS, SHIPMENTS, CUSTOMER, DATE, NOTE });
const fail = (code, details = {}) =>
  Object.assign(new Error(code), { code, details });
const requireFact = (condition, check) => {
  if (!condition) throw fail("VALIDATION_FAILED", { check });
};
const equal = (actual, expected, check) =>
  requireFact(actual === expected, check);
const numeric = (value, check = "numeric-api-field") => {
  requireFact(
    typeof value === "number" ||
      (typeof value === "string" && /^-?\d+(\.\d+)?$/.test(value)),
    check,
  );
  const result = Number(value);
  requireFact(Number.isFinite(result), check);
  return result;
};
const id = (value, check = "positive-safe-id") => {
  requireFact(Number.isSafeInteger(value) && value > 0, check);
  return value;
};
const idsEqual = (actual, expected, check) => {
  requireFact(Array.isArray(actual) && Array.isArray(expected), check);
  equal(new Set(actual).size, actual.length, `${check}:unique`);
  equal(
    [...actual].sort((a, b) => a - b).join(","),
    [...expected].sort((a, b) => a - b).join(","),
    check,
  );
};
const query = (url, values) => `${url}?${new URLSearchParams(values)}`;
const getUrl = (domain, value) => `/rd/${domain}/get?id=${id(value)}`;
const outputKey = (row, action) => `output.${row.key}.${action}`;
const shipmentKey = (shipment, action) => `shipment.${shipment.key}.${action}`;

const WRITE_ROLES = new Map([
  ["POST /rd/production-task/create", "production"],
  ["POST /rd/production-output/create", "production"],
  ["POST /rd/production-output/confirm", "production"],
  ["POST /rd/package-unit/generate", "production"],
  ["POST /rd/package-unit/print-batch", "production"],
  ["POST /rd/quality-inspection/create-batch", "quality"],
  ["POST /rd/quality-inspection/submit", "quality"],
  ["POST /rd/quality-inspection/approve", "admin"],
  ["POST /rd/quality-inspection/reject", "admin"],
  ["POST /rd/warehouse-location/create", "admin"],
  ["POST /rd/putaway-task/create", "warehouse"],
  ["POST /rd/putaway-task/batch-scan/confirm", "warehouse"],
  ["PUT /rd/putaway-task/confirm", "warehouse"],
  ["POST /rd/sale-order/create", "sales"],
  ["POST /rd/order-fulfillment/allocate", "sales"],
  ["POST /rd/loading-task/pickup-slip/create", "sales"],
  ["POST /rd/loading-task/create", "warehouse"],
  ["POST /rd/loading-task/batch-scan/confirm", "warehouse"],
  ["POST /rd/loading-task/confirm-outbound", "warehouse"],
  ["POST /rd/loading-task/exception", "driver"],
]);
const READ_PATHS = new Set([
  "/system/auth/get-permission-info",
  ...[
    "production-task",
    "production-output",
    "sale-order",
    "stock-lot",
    "stock-transaction",
    "inbound-order",
    "outbound-order",
    "quality-inspection",
    "putaway-task",
    "loading-task",
  ].flatMap((domain) => [`/rd/${domain}/get`, `/rd/${domain}/page`]),
  "/rd/package-unit/get",
  "/rd/package-unit/trace",
  "/rd/package-unit/task-packages",
  "/rd/package-unit/relations",
  "/rd/warehouse-location/page",
  "/rd/putaway-task/location-map",
  "/rd/putaway-task/driver-options",
  "/rd/loading-task/driver-options",
  "/rd/order-fulfillment/page",
  "/rd/loading-task/pickup-slip/get",
  "/rd/loading-task/pickup-slip/page",
  "/rd/loading-task/available-package-page",
  "/rd/loading-task/my-get",
  "/rd/outbound-order/detail",
]);

export function validateLocalSettings(settings) {
  equal(settings.RD_DB_PORT, "3310", "existing-mysql3310-only");
  equal(settings.RD_BACKEND_PORT, "48080", "existing-backend48080-only");
  for (const key of ["RD_DB_HOST", "RD_BACKEND_HOST"])
    if (settings[key])
      requireFact(
        ["localhost", "127.0.0.1", "::1"].includes(settings[key]),
        "localhost-only",
      );
}

export function createClient({ check = false, fetchImpl = fetch } = {}) {
  return {
    async call(url, token, method = "GET", body) {
      requireFact(
        typeof url === "string" &&
          /^\/(rd|system)\//.test(url) &&
          !/[\\#]/.test(url),
        "relative-api-only",
      );
      const target = new URL(`${BACKEND}/admin-api${url}`);
      equal(target.origin, BACKEND, "fixed-origin-only");
      const pathname = target.pathname.slice("/admin-api".length);
      const login = method === "POST" && pathname === "/system/auth/login";
      const readable =
        READ_PATHS.has(pathname) ||
        /^\/rd\/package-unit\/(trace-detail|work-context)\/[^/]+$/.test(
          pathname,
        );
      requireFact(
        method === "GET"
          ? readable
          : login || (!check && WRITE_ROLES.has(`${method} ${pathname}`)),
        "api-method-allowlist",
      );
      let response;
      let result;
      try {
        response = await fetchImpl(target.href, {
          method,
          redirect: "error",
          signal: AbortSignal.timeout(20000),
          headers: {
            "Content-Type": "application/json",
            "tenant-id": "1",
            ...(token ? { Authorization: `Bearer ${token}` } : {}),
          },
          ...(body === undefined ? {} : { body: JSON.stringify(body) }),
        });
        requireFact(response.ok, "api-http-success");
        result = await response.json();
      } catch {
        throw fail("API_TRANSPORT_FAILED", { endpoint: pathname });
      }
      if (result?.code !== 0)
        throw fail("API_REJECTED", {
          endpoint: pathname,
          apiCode: Number.isSafeInteger(result?.code) ? result.code : null,
        });
      return result.data;
    },
  };
}

export function newManifest(actors = {}) {
  return {
    schemaVersion: 1,
    marker: MARKER,
    customerName: CUSTOMER,
    date: DATE,
    planHash: PLAN_HASH,
    actors,
    events: [],
  };
}

// Events never change or disappear. Atomic snapshots keep the append-only history valid JSON.
export function createJournal(value, persist) {
  equal(value.schemaVersion, 1, "manifest-schema");
  equal(value.marker, MARKER, "manifest-marker");
  equal(value.customerName, CUSTOMER, "manifest-customer");
  equal(value.date, DATE, "manifest-date");
  equal(value.planHash, PLAN_HASH, "manifest-plan-hash");
  requireFact(Array.isArray(value.events), "manifest-events");
  const done = new Map();
  let pending;
  let previousHash = PLAN_HASH;
  for (const [index, event] of value.events.entries()) {
    const { hash: eventHash, ...content } = event;
    equal(event.seq, index + 1, "manifest-sequence");
    equal(event.previousHash, previousHash, "manifest-chain");
    equal(eventHash, hash(content), "manifest-integrity");
    if (event.type === "intent") {
      requireFact(!pending && !done.has(event.key), "manifest-single-intent");
      pending = event;
    } else {
      requireFact(
        event.type === "success" && pending?.key === event.key,
        "manifest-success-matches-intent",
      );
      done.set(event.key, { ...pending, result: event.result });
      pending = undefined;
    }
    previousHash = eventHash;
  }
  const append = async (content) => {
    const event = {
      seq: value.events.length + 1,
      at: new Date().toISOString(),
      previousHash,
      ...content,
    };
    const next = {
      ...value,
      events: [...value.events, { ...event, hash: hash(event) }],
    };
    await persist(next);
    value = next;
    previousHash = next.events.at(-1).hash;
  };
  return {
    get value() {
      return value;
    },
    get pending() {
      return pending;
    },
    has(key) {
      return done.has(key);
    },
    result(key) {
      return done.get(key)?.result;
    },
    settled() {
      if (pending)
        throw fail("WRITE_UNCERTAIN", {
          step: pending.key,
          endpoint: pending.spec.url,
          readUrls: readUrls(this),
        });
    },
    async run(key, spec, mutate, request) {
      this.settled();
      const pathname = new URL(spec.url, BACKEND).pathname;
      equal(
        WRITE_ROLES.get(`${spec.method} ${pathname}`),
        spec.role,
        "mutation-role-boundary",
      );
      if (done.has(key)) {
        equal(
          hash(done.get(key).spec),
          hash(spec),
          "checkpoint-operation-contract",
        );
        return done.get(key).result;
      }
      await append({
        type: "intent",
        key,
        spec,
        ...(request === undefined ? {} : { request }),
      });
      pending = value.events.at(-1);
      try {
        const result = await mutate();
        requireFact(result !== undefined, "checkpoint-result-present");
        await append({ type: "success", key, result });
        done.set(key, { ...pending, result });
        pending = undefined;
        return result;
      } catch (error) {
        throw fail("WRITE_UNCERTAIN", {
          step: key,
          endpoint: spec.url,
          ...(Number.isSafeInteger(error.details?.apiCode)
            ? { apiCode: error.details.apiCode }
            : {}),
        });
      }
    },
  };
}

export function summarizePackages(packages) {
  const seen = new Set();
  const states = {};
  let weightKg = 0;
  for (const bag of packages) {
    id(bag.id);
    requireFact(!seen.has(bag.id), "unique-package-identity");
    seen.add(bag.id);
    const weight = numeric(bag.packageWeight, "registered-package-weight");
    requireFact(weight > 0, "positive-package-weight");
    requireFact(
      typeof bag.status === "string" && bag.status.length > 0,
      "known-package-status",
    );
    weightKg += weight;
    states[bag.status] = (states[bag.status] || 0) + 1;
  }
  return { count: packages.length, weightKg, states };
}

export function assertQuality(
  inspection,
  bags,
  outputId,
  status,
  actors,
  rejected = status === "REJECTED",
) {
  equal(inspection.productionOutputId, outputId, "qc-output-source");
  equal(inspection.packageId, bags[0].id, "qc-representative");
  equal(inspection.status, status, "qc-status");
  equal(inspection.registrarId, actors.quality, "qc-registrar");
  equal(inspection.coverageCount, bags.length, "qc-actual-coverage-count");
  equal(inspection.sampleCount, 1, "qc-actual-sample-count");
  idsEqual(
    inspection.members?.map((member) => member.packageId),
    bags.map((bag) => bag.id),
    "qc-persisted-members",
  );
  for (const member of inspection.members) {
    const bag = bags.find((entry) => entry.id === member.packageId);
    equal(member.packageCode, bag.packageCode, "qc-member-code");
    equal(
      numeric(member.packageWeight, "qc-member-weight"),
      numeric(bag.packageWeight),
      "qc-member-weight",
    );
    equal(
      member.sampled,
      member.packageId === bags[0].id,
      "qc-persisted-sample",
    );
  }
  equal(inspection.items?.length, 1, "qc-item-count");
  const item = inspection.items[0];
  equal(item.samplePackageId, bags[0].id, "qc-item-sample");
  equal(item.itemCode, "DEMO-FC", "qc-synthetic-item");
  equal(item.itemName, "固定碳（演示模拟值，非实测）", "qc-non-measured-label");
  equal(numeric(item.standardMin), 97, "qc-lower-bound");
  equal(numeric(item.standardMax), 100, "qc-upper-bound");
  equal(item.unit, "%", "qc-unit");
  equal(
    numeric(item.measuredValue),
    rejected ? 92 : 98.6,
    "qc-simulated-value",
  );
  equal(item.qualified, !rejected, "qc-calculated-qualified");
  if (status !== "DRAFT")
    equal(inspection.submitterId, actors.quality, "qc-submitter");
  if (["PASSED", "REJECTED"].includes(status)) {
    equal(inspection.conclusion, status, "qc-final-conclusion");
    equal(inspection.reviewerId, actors.admin, "qc-reviewer");
    requireFact(
      inspection.reviewerId !== inspection.submitterId,
      "qc-no-self-review",
    );
  }
}

async function readJson(filename, optional = false) {
  try {
    return JSON.parse(await fs.readFile(filename, "utf8"));
  } catch (error) {
    if (optional && error.code === "ENOENT") return null;
    throw fail("LOCAL_FILE_MISSING_OR_INVALID");
  }
}

// Reuse the E4 fixture's existing credentials, not its prepareFixture mutation routine.
async function localSession(client) {
  const settings = Object.fromEntries(
    (
      await fs.readFile(
        path.join(ROOT, "temp/local-runtime-patent/.env.local"),
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
  validateLocalSettings(settings);
  const fixture = await readJson(path.join(ROOT, "temp/qr-e4-fixture.json"));
  equal(
    fixture.marker,
    "二维码岗位本地验证（非现场数据）",
    "existing-credential-fixture",
  );
  const tokens = {};
  const actors = {};
  for (const role of [
    "admin",
    "production",
    "quality",
    "warehouse",
    "driver",
    "sales",
  ]) {
    const account =
      role === "admin"
        ? { username: "admin", password: settings.RD_ADMIN_INITIAL_PASSWORD }
        : fixture.accounts?.[role];
    requireFact(
      typeof account?.username === "string" &&
        typeof account?.password === "string" &&
        account.password.length > 0,
      "existing-local-account-required",
    );
    const login = await client.call("/system/auth/login", undefined, "POST", {
      username: account.username,
      password: account.password,
    });
    requireFact(
      typeof login?.accessToken === "string" && login.accessToken.length > 0,
      "authenticated-local-session",
    );
    tokens[role] = login.accessToken;
    const permission = await client.call(
      "/system/auth/get-permission-info",
      tokens[role],
    );
    actors[role] = id(permission?.user?.id);
    if (role !== "admin")
      equal(actors[role], account.userId, "existing-account-identity");
  }
  requireFact(actors.quality !== actors.admin, "separate-qc-reviewer");
  const drivers = await client.call(
    "/rd/loading-task/driver-options",
    tokens.warehouse,
  );
  requireFact(
    drivers.some((driver) => driver.id === actors.driver),
    "existing-forklift-driver-role",
  );
  return { tokens, actors };
}

async function persistManifest(value) {
  const temporary = `${MANIFEST}.next`;
  const handle = await fs.open(temporary, "w", 0o600);
  try {
    await handle.writeFile(`${JSON.stringify(value, null, 2)}\n`, "utf8");
    await handle.sync();
  } finally {
    await handle.close();
  }
  await fs.rename(temporary, MANIFEST);
}

async function allPages(read, endpoint, params = {}) {
  const list = [];
  let total;
  for (let pageNo = 1; pageNo <= 10000; pageNo++) {
    const page = await read(
      query(endpoint, { ...params, pageNo, pageSize: 100 }),
    );
    requireFact(
      Array.isArray(page?.list) &&
        Number.isSafeInteger(page.total) &&
        page.total >= 0,
      "page-contract",
    );
    if (total === undefined) total = page.total;
    equal(page.total, total, "page-total-stable");
    list.push(...page.list);
    if (list.length >= total) {
      equal(list.length, total, "page-complete");
      idsEqual(
        list.map((row) => row.id),
        list.map((row) => row.id),
        "page-no-duplicate-rows",
      );
      return list;
    }
    requireFact(page.list.length > 0, "page-not-truncated");
  }
  throw fail("PAGE_LIMIT_EXCEEDED");
}

const outputRemark = (row) => `${NOTE} 场景：${row.scenario}。`;
const orderNo = (key) => `DEMO-FLOW-V1-SO-${key.toUpperCase()}`;
const slipNo = (key) => `DEMO-FLOW-V1-TH-${key.toUpperCase()}`;
const qcStatus = (journal, row) =>
  journal.has(outputKey(row, "qc.review"))
    ? row.key === "rejected"
      ? "REJECTED"
      : "PASSED"
    : journal.has(outputKey(row, "qc.submit"))
      ? "SUBMITTED"
      : "DRAFT";

export async function seedFlow({ client, tokens, actors, journal }) {
  const read = (url) => client.call(url, tokens.admin);
  const write = (
    key,
    role,
    url,
    body,
    method = "POST",
    project = (data) => data,
  ) =>
    journal.run(
      key,
      { role, method, url },
      async () => project(await client.call(url, tokens[role], method, body)),
      body,
    );
  const taskId = await write(
    "task",
    "production",
    "/rd/production-task/create",
    {
      customerName: CUSTOMER,
      productionDate: DATE,
      shiftCode: SHIFT,
      remark: NOTE,
    },
    "POST",
    id,
  );
  for (const location of LOCATIONS)
    await write(
      `location.${location.key}`,
      "admin",
      "/rd/warehouse-location/create",
      location.body,
      "POST",
      id,
    );

  for (const row of PLAN) {
    const key = (action) => outputKey(row, action);
    const outputId = await write(
      key("create"),
      "production",
      "/rd/production-output/create",
      {
        productionTaskId: taskId,
        clientRequestId: `${MARKER}:output:${row.key}`,
        productionDate: DATE,
        furnaceOutDate: DATE,
        shiftCode: SHIFT,
        customerName: CUSTOMER,
        productModel: row.productModel,
        furnaceNo: row.furnaceNo,
        furnaceLayer: row.furnaceLayer,
        outputQty: row.count,
        packageWeight: row.weight,
        sourceSheet: NOTE,
        remark: outputRemark(row),
      },
      "POST",
      id,
    );
    if (!journal.has(key("confirm"))) {
      const output = await read(getUrl("production-output", outputId));
      equal(output.status, "DRAFT", "confirm-original-output-draft");
      await write(
        key("confirm"),
        "production",
        "/rd/production-output/confirm",
        { id: outputId, version: output.version },
      );
    }
    await write(
      key("generate"),
      "production",
      "/rd/package-unit/generate",
      {
        productionOutputId: outputId,
        packageCount: row.count,
      },
      "POST",
      (data) => {
        equal(data.length, row.count, "generated-count");
        return data
          .map((bag) => ({ id: id(bag.id), packageCode: bag.packageCode }))
          .sort((a, b) => a.id - b.id);
      },
    );
    const bags = (
      await read(`/rd/package-unit/trace?productionOutputId=${outputId}`)
    ).sort((a, b) => a.id - b.id);
    idsEqual(
      bags.map((bag) => bag.id),
      journal.result(key("generate")).map((bag) => bag.id),
      "generated-actual-identities",
    );
    await write(key("print"), "production", "/rd/package-unit/print-batch", {
      packageIds: bags.map((bag) => bag.id),
    });
    const inspectionId = await write(
      key("qc.create"),
      "quality",
      "/rd/quality-inspection/create-batch",
      {
        packageId: bags[0].id,
        packageIds: bags.map((bag) => bag.id),
        samplePackageIds: [bags[0].id],
        clientRequestId: `${MARKER}:qc:${row.key}`,
        remark: outputRemark(row),
        items: [
          {
            samplePackageId: bags[0].id,
            itemCode: "DEMO-FC",
            itemName: "固定碳（演示模拟值，非实测）",
            standardMin: 97,
            standardMax: 100,
            measuredValue: row.key === "rejected" ? 92 : 98.6,
            unit: "%",
            remark: NOTE,
          },
        ],
      },
      "POST",
      id,
    );
    if (!journal.has(key("qc.submit"))) {
      equal(
        (await read(getUrl("quality-inspection", inspectionId))).status,
        "DRAFT",
        "qc-draft-before-submit",
      );
      await write(
        key("qc.submit"),
        "quality",
        `/rd/quality-inspection/submit?id=${inspectionId}`,
      );
    }
    if (row.key !== "waiting" && !journal.has(key("qc.review"))) {
      equal(
        (await read(getUrl("quality-inspection", inspectionId))).status,
        "SUBMITTED",
        "qc-submitted-before-review",
      );
      if (row.key === "rejected")
        await write(
          key("qc.review"),
          "admin",
          "/rd/quality-inspection/reject",
          {
            id: inspectionId,
            reason: `${NOTE} 固定碳模拟值92%，低于演示下限97%，演示不合格隔离。`,
          },
        );
      else
        await write(
          key("qc.review"),
          "admin",
          `/rd/quality-inspection/approve?id=${inspectionId}`,
        );
    }
    if (!row.location) continue;
    const locationId = journal.result(`location.${row.location}`);
    const putawayId = await write(
      key("putaway.create"),
      "warehouse",
      "/rd/putaway-task/create",
      {
        plannedLocationId: locationId,
        forkliftDriverId: actors.driver,
        packageIds: bags.map((bag) => bag.id),
        remark: outputRemark(row),
      },
      "POST",
      id,
    );
    if (row.key === "assigned") continue;
    if (!journal.has(key("putaway.scan"))) {
      const detail = await read(getUrl("putaway-task", putawayId));
      equal(detail.task.status, "ASSIGNED", "unscanned-putaway-task");
      await write(
        key("putaway.scan"),
        "warehouse",
        "/rd/putaway-task/batch-scan/confirm",
        {
          id: putawayId,
          version: detail.task.version,
          packageCode: bags[0].packageCode,
          taskLineIds: detail.lines.map((line) => line.id),
          idempotencyKey: `${MARKER}:putaway:${row.key}`,
          deviceCode: "DEMO-FLOW-SYNTHETIC-KEEPER",
        },
        "POST",
        (result) => {
          equal(
            result.confirmedCount,
            row.count,
            "putaway-scan-confirmed-count",
          );
          return {
            recordId: id(result.recordId),
            confirmedCount: result.confirmedCount,
          };
        },
      );
    }
    if (!journal.has(key("putaway.post"))) {
      const detail = await read(getUrl("putaway-task", putawayId));
      equal(
        detail.task.status,
        "PENDING_CONFIRMATION",
        "putaway-scanned-before-post",
      );
      await write(
        key("putaway.post"),
        "warehouse",
        "/rd/putaway-task/confirm",
        {
          id: putawayId,
          version: detail.task.version,
          finalLocationId: locationId,
        },
        "PUT",
        id,
      );
    }
  }

  for (const shipment of SHIPMENTS) {
    const rows = shipment.outputs.map((key) =>
      PLAN.find((row) => row.key === key),
    );
    const groups = [];
    for (const row of rows) {
      const bags = (
        await read(
          `/rd/package-unit/trace?productionOutputId=${journal.result(outputKey(row, "create"))}`,
        )
      ).sort((a, b) => a.id - b.id);
      equal(
        new Set(bags.map((bag) => id(bag.stockLotId))).size,
        1,
        "one-real-lot-per-pickup-line",
      );
      const saleId = await write(
        outputKey(row, "sale"),
        "sales",
        "/rd/sale-order/create",
        {
          orderNo: orderNo(row.key),
          orderDate: DATE,
          customerName: CUSTOMER,
          productModel: row.productModel,
          requiredQty: row.count,
          actualOutputQty: 0,
          remark: outputRemark(row),
        },
        "POST",
        id,
      );
      await write(
        outputKey(row, "allocation"),
        "sales",
        "/rd/order-fulfillment/allocate",
        {
          orderId: saleId,
          stockLotId: bags[0].stockLotId,
          quantity: row.count,
          remark: outputRemark(row),
        },
        "POST",
        id,
      );
      groups.push({ row, bags, saleId });
    }
    const bags = groups.flatMap((group) => group.bags);
    const total = summarizePackages(bags);
    const pickupId = await write(
      shipmentKey(shipment, "pickup"),
      "sales",
      "/rd/loading-task/pickup-slip/create",
      {
        slipNo: slipNo(shipment.key),
        customerName: CUSTOMER,
        pickupDate: DATE,
        requestedPackageCount: total.count,
        requestedWeight: total.weightKg,
        remark: NOTE,
        lines: groups.map(({ row, saleId }, index) => ({
          lineNo: index + 1,
          saleOrderId: saleId,
          productName: "人造石墨成品（演示）",
          productModel: row.productModel,
          furnaceNo: row.furnaceNo,
          furnaceLayer: row.furnaceLayer,
          requestedPackageCount: row.count,
          requestedWeight: row.count * row.weight,
          remark: outputRemark(row),
        })),
      },
      "POST",
      id,
    );
    const pickup = await read(getUrl("loading-task/pickup-slip", pickupId));
    const loadingId = await write(
      shipmentKey(shipment, "create"),
      "warehouse",
      "/rd/loading-task/create",
      {
        pickupSlipId: pickupId,
        forkliftDriverId: actors.driver,
        remark: NOTE,
        selections: groups.flatMap(({ row, bags: units, saleId }) => {
          const line = pickup.lines.find(
            (line) =>
              line.saleOrderId === saleId &&
              line.furnaceNo === row.furnaceNo &&
              line.furnaceLayer === row.furnaceLayer,
          );
          requireFact(line, "exact-pickup-line-required");
          return units.map((bag) => ({
            pickupSlipLineId: line.id,
            packageId: bag.id,
          }));
        }),
      },
      "POST",
      id,
    );
    const mine = await client.call(
      `/rd/loading-task/my-get?id=${loadingId}`,
      tokens.driver,
    );
    equal(
      mine.task.forkliftDriverId,
      actors.driver,
      "driver-only-own-loading-task",
    );
    if (shipment.scanned && !journal.has(shipmentKey(shipment, "scan"))) {
      const detail = await read(getUrl("loading-task", loadingId));
      equal(detail.task.status, "ASSIGNED", "new-loading-plan-before-scan");
      const selected = [...detail.lines]
        .sort((a, b) => a.lineNo - b.lineNo)
        .slice(0, shipment.scanned);
      await write(
        shipmentKey(shipment, "scan"),
        "warehouse",
        "/rd/loading-task/batch-scan/confirm",
        {
          taskId: loadingId,
          version: detail.task.version,
          representativePackageCode: selected[0].packageCode,
          taskLineIds: selected.map((line) => line.id),
          idempotencyKey: `${MARKER}:loading:${shipment.key}`,
          deviceCode: "DEMO-FLOW-SYNTHETIC-KEEPER",
        },
        "POST",
        (result) => {
          equal(result.success, true, "loading-scan-success");
          equal(
            result.confirmedCount,
            shipment.scanned,
            "loading-scan-confirmed-count",
          );
          return {
            recordId: id(result.recordId),
            confirmedCount: result.confirmedCount,
          };
        },
      );
    }
    if (
      shipment.key === "shipped" &&
      !journal.has(shipmentKey(shipment, "outbound"))
    ) {
      const detail = await read(getUrl("loading-task", loadingId));
      equal(
        detail.task.status,
        "PENDING_REVIEW",
        "keeper-review-before-outbound",
      );
      await write(
        shipmentKey(shipment, "outbound"),
        "warehouse",
        "/rd/loading-task/confirm-outbound",
        {
          taskId: loadingId,
          version: detail.task.version,
          remark: NOTE,
        },
        "POST",
        (result) => {
          equal(result.outboundPackageCount, 5, "outbound-five-bags");
          equal(
            numeric(result.outboundWeight),
            4800,
            "outbound-actual-tail-weight",
          );
          equal(
            result.outboundOrderIds?.length,
            2,
            "two-lot-orders-one-truck-task",
          );
          return {
            outboundOrderIds: result.outboundOrderIds.map((value) => id(value)),
            count: 5,
            weightKg: 4800,
          };
        },
      );
    }
    if (
      shipment.key === "exception" &&
      !journal.has(shipmentKey(shipment, "exception"))
    ) {
      await write(
        shipmentKey(shipment, "exception"),
        "driver",
        "/rd/loading-task/exception",
        {
          taskId: loadingId,
          exceptionType: "LOCATION_MISMATCH",
          description: `${NOTE} 模拟装车前整车库位核对不符，等待仓管处理，未过账。`,
          observedLocationCode: LOCATIONS.find(
            (location) => location.key === "empty",
          ).body.locationCode,
        },
        "POST",
        id,
      );
    }
  }
}

export async function collectFacts(
  { client, tokens, journal },
  withTrace = false,
) {
  const read = (url) => client.call(url, tokens.admin);
  const page = (domain, params) => allPages(read, `/rd/${domain}/page`, params);
  const taskId = journal.result("task");
  const tasks = (
    await page("production-task", { customerName: CUSTOMER })
  ).filter((task) => task.customerName === CUSTOMER);
  const locations = (
    await page("warehouse-location", { keyword: WAREHOUSE })
  ).filter((location) => location.warehouseCode === WAREHOUSE);
  const locationMap = (await read("/rd/putaway-task/location-map")).filter(
    (location) => location.warehouseCode === WAREHOUSE,
  );
  const facts = {
    tasks,
    locations,
    locationMap,
    outputs: await page(
      "production-output",
      taskId ? { productionTaskId: taskId } : { customerName: CUSTOMER },
    ),
    bags: taskId
      ? await read(`/rd/package-unit/task-packages?productionTaskId=${taskId}`)
      : [],
    sales: (await page("sale-order", { customerName: CUSTOMER })).filter(
      (sale) => sale.customerName === CUSTOMER,
    ),
    pickups: (
      await allPages(read, "/rd/loading-task/pickup-slip/page", {
        customerName: CUSTOMER,
      })
    ).filter((slip) => slip.customerName === CUSTOMER),
    loadings: (await page("loading-task", { customerName: CUSTOMER })).filter(
      (task) => task.customerName === CUSTOMER,
    ),
    lots: (await page("stock-lot", { customerName: CUSTOMER })).filter(
      (lot) => lot.customerName === CUSTOMER,
    ),
    transactions: (
      await page("stock-transaction", { customerName: CUSTOMER })
    ).filter((transaction) => transaction.customerName === CUSTOMER),
    inspections: (await page("quality-inspection")).filter((inspection) =>
      inspection.remark?.includes(MARKER),
    ),
    putaways: [],
    qc: {},
    putaway: {},
    allocation: {},
    pickup: {},
    loading: {},
    inbound: {},
    outbound: {},
    traces: {},
    relations: {},
    contexts: {},
    available: await allPages(read, "/rd/loading-task/available-package-page", {
      customerName: CUSTOMER,
    }),
  };
  for (const location of locations)
    facts.putaways.push(
      ...(await page("putaway-task", { plannedLocationId: location.id })),
    );
  for (const row of PLAN) {
    const qcId = journal.result(outputKey(row, "qc.create"));
    if (qcId)
      facts.qc[row.key] = await read(getUrl("quality-inspection", qcId));
    const putawayId = journal.result(outputKey(row, "putaway.create"));
    if (putawayId) {
      const detail = await read(getUrl("putaway-task", putawayId));
      facts.putaway[row.key] = detail;
      for (const line of detail.lines)
        if (line.inboundOrderId && !facts.inbound[line.inboundOrderId])
          facts.inbound[line.inboundOrderId] = await read(
            getUrl("inbound-order", line.inboundOrderId),
          );
    }
    const saleId = journal.result(outputKey(row, "sale"));
    if (saleId)
      facts.allocation[row.key] = await page("order-fulfillment", {
        orderId: saleId,
      });
  }
  for (const shipment of SHIPMENTS) {
    const pickupId = journal.result(shipmentKey(shipment, "pickup"));
    if (pickupId)
      facts.pickup[shipment.key] = await read(
        getUrl("loading-task/pickup-slip", pickupId),
      );
    const loadingId = journal.result(shipmentKey(shipment, "create"));
    if (!loadingId) continue;
    const detail = await read(getUrl("loading-task", loadingId));
    facts.loading[shipment.key] = detail;
    for (const line of detail.lines)
      if (line.outboundOrderId && !facts.outbound[line.outboundOrderId])
        facts.outbound[line.outboundOrderId] = await read(
          `/rd/outbound-order/detail?id=${line.outboundOrderId}`,
        );
  }
  if (withTrace)
    for (const bag of facts.bags) {
      const code = encodeURIComponent(bag.packageCode);
      facts.traces[bag.id] = await read(
        `/rd/package-unit/trace-detail/${code}`,
      );
      facts.relations[bag.id] = await read(
        `/rd/package-unit/relations?packageCode=${code}`,
      );
      facts.contexts[bag.id] = await read(
        `/rd/package-unit/work-context/${code}`,
      );
    }
  return facts;
}

function assertSyntheticFields(actual, expected, fields, check) {
  requireFact(actual, `${check}:present`);
  for (const field of fields)
    equal(actual[field], expected[field], `${check}:${field}`);
}

function expectedBagStatus(journal, row) {
  if (
    ["shipped", "tail"].includes(row.key) &&
    journal.has("shipment.shipped.outbound")
  )
    return "OUTBOUNDED";
  if (journal.has(outputKey(row, "putaway.post"))) return "IN_STOCK";
  if (journal.has(outputKey(row, "putaway.create"))) return "INBOUND_PENDING";
  if (journal.has(outputKey(row, "qc.review")))
    return row.key === "rejected" ? "QC_REJECTED" : "QC_PASSED";
  if (journal.has(outputKey(row, "qc.submit"))) return "QC_PENDING";
  return journal.has(outputKey(row, "print")) ? "LABELED" : "CREATED";
}

function assertPutaway(facts, journal, actors, row, bags) {
  const detail = facts.putaway[row.key];
  if (!journal.has(outputKey(row, "putaway.create"))) {
    requireFact(!detail, "no-unrecorded-putaway");
    return;
  }
  const posted = journal.has(outputKey(row, "putaway.post"));
  const scanned = journal.has(outputKey(row, "putaway.scan"));
  const location = facts.locations.find(
    (location) => location.id === journal.result(`location.${row.location}`),
  );
  equal(
    detail.task.id,
    journal.result(outputKey(row, "putaway.create")),
    "putaway-checkpoint-id",
  );
  equal(
    detail.task.status,
    posted
      ? "PUTAWAY_COMPLETED"
      : scanned
        ? "PENDING_CONFIRMATION"
        : "ASSIGNED",
    "putaway-current-state",
  );
  equal(detail.task.plannedLocationId, location.id, "putaway-planned-location");
  equal(
    detail.task.plannedLocationCode,
    location.locationCode,
    "putaway-planned-code",
  );
  equal(detail.task.forkliftDriverId, actors.driver, "putaway-driver");
  equal(detail.task.warehouseKeeperId, actors.warehouse, "putaway-keeper");
  equal(detail.task.remark, outputRemark(row), "putaway-synthetic-marker");
  equal(detail.task.packageCount, row.count, "putaway-count");
  equal(
    numeric(detail.task.totalWeight),
    row.count * row.weight,
    "putaway-weight",
  );
  equal(
    detail.task.scannedPackageCount,
    scanned ? row.count : 0,
    "putaway-scan-progress",
  );
  equal(detail.exceptions?.length, 0, "no-unexpected-putaway-exception");
  idsEqual(
    detail.lines.map((line) => line.packageId),
    bags.map((bag) => bag.id),
    "putaway-member-packages",
  );
  for (const line of detail.lines) {
    const bag = bags.find((entry) => entry.id === line.packageId);
    equal(line.packageCode, bag.packageCode, "putaway-member-code");
    equal(
      line.lineStatus,
      posted ? "CONFIRMED" : scanned ? "SCANNED" : "PENDING",
      "putaway-line-state",
    );
    equal(line.scanCount, scanned ? 1 : 0, "keeper-scan-count");
    equal(numeric(line.packageWeight), row.weight, "putaway-line-weight");
    if (!posted) {
      requireFact(
        !line.inboundOrderId && !line.stockTransactionId,
        "unposted-putaway-has-no-stock-documents",
      );
      continue;
    }
    equal(detail.task.finalLocationId, location.id, "putaway-final-location");
    equal(
      line.inboundOrderId,
      journal.result(outputKey(row, "putaway.post")),
      "inbound-checkpoint-id",
    );
    const inbound = facts.inbound[line.inboundOrderId];
    equal(inbound?.status, "POSTED", "inbound-posted");
    equal(inbound.bizDate, DATE, "inbound-local-date");
    equal(inbound.sourceType, "PUTAWAY_TASK", "inbound-original-source");
    equal(inbound.posterId, actors.warehouse, "inbound-keeper-posting");
    equal(numeric(inbound.totalPackages), row.count, "inbound-total-count");
    equal(
      numeric(inbound.totalWeight),
      row.count * row.weight,
      "inbound-total-weight",
    );
    idsEqual(
      inbound.lines.map((entry) => entry.packageId),
      bags.map((entry) => entry.id),
      "inbound-exact-packages",
    );
    const inboundLine = inbound.lines.find(
      (entry) => entry.id === line.inboundOrderLineId,
    );
    equal(inboundLine?.packageId, bag.id, "inbound-real-line-link");
    equal(inboundLine.locationId, location.id, "inbound-location");
    equal(inboundLine.stockLotId, bag.stockLotId, "inbound-lot");
    equal(line.stockLotId, bag.stockLotId, "putaway-posted-lot");
    equal(
      inboundLine.stockTransactionId,
      line.stockTransactionId,
      "inbound-stock-transaction-link",
    );
    equal(numeric(inboundLine.quantity), 1, "inbound-package-quantity");
    equal(numeric(inboundLine.weight), row.weight, "inbound-line-weight");
  }
}

function assertShipment(facts, journal, actors, shipment) {
  const pickupId = journal.result(shipmentKey(shipment, "pickup"));
  const detail = facts.loading[shipment.key];
  const pickup = facts.pickup[shipment.key];
  const rows = shipment.outputs.map((key) =>
    PLAN.find((row) => row.key === key),
  );
  const bags = facts.bags.filter((bag) =>
    rows.some(
      (row) =>
        journal.result(outputKey(row, "create")) === bag.productionOutputId,
    ),
  );
  const totalCount = rows.reduce((sum, row) => sum + row.count, 0);
  const totalWeight = rows.reduce(
    (sum, row) => sum + row.count * row.weight,
    0,
  );
  const posted = journal.has(shipmentKey(shipment, "outbound"));
  const scanned = journal.has(shipmentKey(shipment, "scan"));
  const excepted = journal.has(shipmentKey(shipment, "exception"));
  if (!pickupId) {
    requireFact(!pickup && !detail, "no-unrecorded-shipment");
    return;
  }
  equal(pickup.slip.id, pickupId, "pickup-checkpoint-id");
  equal(pickup.slip.customerName, CUSTOMER, "pickup-customer");
  equal(
    pickup.slip.slipNo,
    slipNo(shipment.key),
    "pickup-deterministic-number",
  );
  equal(pickup.slip.pickupDate, DATE, "pickup-date");
  equal(pickup.slip.remark, NOTE, "pickup-synthetic-marker");
  equal(pickup.slip.requestedPackageCount, totalCount, "pickup-count");
  equal(numeric(pickup.slip.requestedWeight), totalWeight, "pickup-weight");
  equal(
    pickup.slip.status,
    posted ? "SHIPPED" : detail ? "DISPATCHED" : "ISSUED",
    "pickup-current-state",
  );
  equal(pickup.lines.length, rows.length, "one-pickup-line-per-real-lot");
  for (const row of rows) {
    const line = pickup.lines.find(
      (line) => line.saleOrderId === journal.result(outputKey(row, "sale")),
    );
    assertSyntheticFields(
      line,
      row,
      ["productModel", "furnaceNo", "furnaceLayer"],
      "pickup-exact-lot-fields",
    );
    equal(line.requestedPackageCount, row.count, "pickup-line-count");
    equal(
      numeric(line.requestedWeight),
      row.count * row.weight,
      "pickup-line-weight",
    );
  }
  if (!detail) return;
  equal(
    detail.task.id,
    journal.result(shipmentKey(shipment, "create")),
    "loading-checkpoint-id",
  );
  equal(
    detail.task.status,
    posted
      ? "OUTBOUNDED"
      : excepted
        ? "EXCEPTION_PENDING"
        : scanned
          ? shipment.key === "shipped"
            ? "PENDING_REVIEW"
            : "LOADING"
          : "ASSIGNED",
    "loading-current-state",
  );
  equal(detail.task.pickupSlipId, pickupId, "single-truck-pickup-link");
  equal(detail.task.forkliftDriverId, actors.driver, "loading-driver");
  equal(detail.task.warehouseKeeperId, actors.warehouse, "loading-keeper");
  equal(detail.task.customerName, CUSTOMER, "loading-customer");
  equal(detail.task.remark, NOTE, "loading-synthetic-marker");
  equal(detail.task.plannedPackageCount, totalCount, "loading-plan-count");
  equal(numeric(detail.task.plannedWeight), totalWeight, "loading-plan-weight");
  equal(
    detail.task.scannedPackageCount,
    scanned ? shipment.scanned : 0,
    "loading-scan-count",
  );
  idsEqual(
    detail.lines.map((line) => line.packageId),
    bags.map((bag) => bag.id),
    "one-whole-truck-task-membership",
  );
  const sorted = [...detail.lines].sort((a, b) => a.lineNo - b.lineNo);
  let scannedWeight = 0;
  for (const [index, line] of sorted.entries()) {
    const bag = bags.find((entry) => entry.id === line.packageId);
    const row = rows.find(
      (entry) =>
        journal.result(outputKey(entry, "create")) === bag.productionOutputId,
    );
    const isScanned = scanned && index < shipment.scanned;
    equal(
      line.lineStatus,
      posted ? "OUTBOUNDED" : isScanned ? "SCANNED" : "PLANNED",
      "loading-line-state",
    );
    equal(line.packageCode, bag.packageCode, "loading-package-code");
    equal(numeric(line.packageWeight), row.weight, "loading-registered-weight");
    equal(line.stockLotId, bag.stockLotId, "loading-actual-lot");
    equal(
      line.saleOrderId,
      journal.result(outputKey(row, "sale")),
      "loading-sale-order-link",
    );
    equal(
      line.fulfillmentId,
      journal.result(outputKey(row, "allocation")),
      "loading-exact-allocation-link",
    );
    equal(
      line.pickupSlipLineId,
      pickup.lines.find((entry) => entry.saleOrderId === line.saleOrderId)?.id,
      "loading-pickup-line-link",
    );
    equal(line.plannedLocationId, bag.locationId, "loading-planned-location");
    if (isScanned) scannedWeight += row.weight;
    if (!posted) {
      requireFact(
        !line.outboundOrderId && !line.stockTransactionId,
        "no-premature-outbound-documents",
      );
      continue;
    }
    const outbound = facts.outbound[line.outboundOrderId];
    equal(outbound?.order.status, "POSTED", "outbound-posted");
    equal(outbound.order.outboundDate, DATE, "outbound-date");
    equal(outbound.order.orderId, line.saleOrderId, "outbound-sale-order");
    equal(
      outbound.order.fulfillmentId,
      line.fulfillmentId,
      "outbound-allocation",
    );
    equal(outbound.order.stockLotId, line.stockLotId, "outbound-real-lot");
    equal(numeric(outbound.order.quantity), row.count, "outbound-lot-count");
    equal(
      numeric(outbound.order.weight),
      row.count * row.weight,
      "outbound-lot-weight",
    );
    const outboundLine = outbound.lines.find(
      (entry) => entry.id === line.outboundOrderLineId,
    );
    equal(outboundLine?.packageId, bag.id, "outbound-persisted-member");
    equal(
      outboundLine.stockTransactionId,
      line.stockTransactionId,
      "outbound-stock-transaction-link",
    );
    equal(
      numeric(outboundLine.packageWeight),
      row.weight,
      "outbound-line-real-weight",
    );
    requireFact(
      outbound.order.remark?.includes(MARKER),
      "outbound-synthetic-marker",
    );
  }
  equal(
    numeric(detail.task.scannedWeight),
    scannedWeight,
    "loading-scanned-actual-weight",
  );
  equal(detail.exceptions.length, excepted ? 1 : 0, "exact-loading-exceptions");
  if (excepted) {
    const exception = detail.exceptions[0];
    equal(
      exception.id,
      journal.result(shipmentKey(shipment, "exception")),
      "exception-checkpoint-id",
    );
    equal(exception.exceptionStatus, "OPEN", "exception-unresolved");
    equal(exception.exceptionType, "LOCATION_MISMATCH", "exception-type");
    equal(exception.reporterId, actors.driver, "exception-real-driver");
    requireFact(
      !exception.taskLineId && !exception.packageId,
      "whole-truck-exception-no-subgroup",
    );
    requireFact(
      exception.description?.includes(MARKER),
      "exception-synthetic-marker",
    );
  }
  if (posted) {
    equal(
      detail.task.reviewerId,
      actors.warehouse,
      "keeper-only-outbound-posting",
    );
    idsEqual(
      [...new Set(detail.lines.map((line) => line.outboundOrderId))],
      journal.result(shipmentKey(shipment, "outbound")).outboundOrderIds,
      "outbound-checkpoint-documents",
    );
  }
}

export function validateFacts(facts, journal, actors, complete = false) {
  journal.settled();
  const checkpoints = journal.value.events
    .filter((event) => event.type === "success")
    .map((event) => event.key);
  requireFact(
    checkpoints.every((key) => CHECKPOINT_KEYS.includes(key)),
    "known-checkpoint-steps-only",
  );
  const taskId = journal.result("task");
  idsEqual(
    facts.tasks.map((task) => task.id),
    taskId ? [taskId] : [],
    "one-task-or-no-orphaned-runtime-seed",
  );
  if (taskId) {
    const task = facts.tasks[0];
    assertSyntheticFields(
      task,
      {
        customerName: CUSTOMER,
        productionDate: DATE,
        shiftCode: SHIFT,
        remark: NOTE,
        status: "ACTIVE",
      },
      ["customerName", "productionDate", "shiftCode", "remark", "status"],
      "demo-production-task",
    );
    equal(task.managerId, actors.production, "production-task-manager");
  }
  idsEqual(
    facts.locations.map((location) => location.id),
    LOCATIONS.filter((location) => journal.has(`location.${location.key}`)).map(
      (location) => journal.result(`location.${location.key}`),
    ),
    "no-extra-demo-locations",
  );
  idsEqual(
    facts.outputs.map((output) => output.id),
    PLAN.filter((row) => journal.has(outputKey(row, "create"))).map((row) =>
      journal.result(outputKey(row, "create")),
    ),
    "no-extra-or-missing-production-outputs",
  );
  idsEqual(
    facts.inspections.map((inspection) => inspection.id),
    PLAN.filter((row) => journal.has(outputKey(row, "qc.create"))).map((row) =>
      journal.result(outputKey(row, "qc.create")),
    ),
    "no-extra-quality-documents",
  );
  idsEqual(
    facts.putaways.map((task) => task.id),
    PLAN.filter((row) => journal.has(outputKey(row, "putaway.create"))).map(
      (row) => journal.result(outputKey(row, "putaway.create")),
    ),
    "no-extra-putaway-tasks",
  );
  idsEqual(
    facts.sales.map((sale) => sale.id),
    PLAN.filter((row) => journal.has(outputKey(row, "sale"))).map((row) =>
      journal.result(outputKey(row, "sale")),
    ),
    "no-extra-sale-orders",
  );
  idsEqual(
    facts.pickups.map((slip) => slip.id),
    SHIPMENTS.filter((shipment) =>
      journal.has(shipmentKey(shipment, "pickup")),
    ).map((shipment) => journal.result(shipmentKey(shipment, "pickup"))),
    "no-extra-pickup-documents",
  );
  idsEqual(
    facts.loadings.map((task) => task.id),
    SHIPMENTS.filter((shipment) =>
      journal.has(shipmentKey(shipment, "create")),
    ).map((shipment) => journal.result(shipmentKey(shipment, "create"))),
    "one-task-per-shipment-no-subgroups",
  );
  const expectedPackages = [];
  const expectedTransactions = [];
  const expectedLots = [];
  for (const row of PLAN) {
    const outputId = journal.result(outputKey(row, "create"));
    if (!outputId) continue;
    const output = facts.outputs.find((output) => output.id === outputId);
    assertSyntheticFields(
      output,
      {
        ...row,
        productionTaskId: taskId,
        productionDate: DATE,
        furnaceOutDate: DATE,
        customerName: CUSTOMER,
        shiftCode: SHIFT,
        sourceSheet: NOTE,
        remark: outputRemark(row),
      },
      [
        "productionTaskId",
        "productionDate",
        "furnaceOutDate",
        "customerName",
        "shiftCode",
        "productModel",
        "furnaceNo",
        "furnaceLayer",
        "sourceSheet",
        "remark",
      ],
      `output:${row.key}`,
    );
    equal(numeric(output.outputQty), row.count, "output-quantity");
    equal(
      numeric(output.packageWeight),
      row.weight,
      "output-registered-weight",
    );
    const posted = journal.has(outputKey(row, "putaway.post"));
    equal(
      output.status,
      posted
        ? "INBOUNDED"
        : journal.has(outputKey(row, "confirm"))
          ? "CONFIRMED"
          : "DRAFT",
      "output-current-state",
    );
    const bags = facts.bags
      .filter((bag) => bag.productionOutputId === outputId)
      .sort((a, b) => a.id - b.id);
    const checkpointBags = journal.result(outputKey(row, "generate")) || [];
    idsEqual(
      bags.map((bag) => bag.id),
      checkpointBags.map((bag) => bag.id),
      "package-checkpoint-identities",
    );
    expectedPackages.push(...checkpointBags.map((bag) => bag.id));
    if (checkpointBags.length)
      equal(bags.length, row.count, "output-generated-count");
    for (const bag of bags) {
      assertSyntheticFields(
        bag,
        {
          ...row,
          productionTaskId: taskId,
          productionDate: DATE,
          customerName: CUSTOMER,
          shiftCode: SHIFT,
        },
        [
          "productionTaskId",
          "productionDate",
          "customerName",
          "shiftCode",
          "productModel",
          "furnaceNo",
          "furnaceLayer",
        ],
        "package-inherited-source",
      );
      equal(
        bag.packageCode,
        checkpointBags.find((entry) => entry.id === bag.id)?.packageCode,
        "package-stable-code",
      );
      equal(
        numeric(bag.packageWeight),
        row.weight,
        "individual-registered-weight",
      );
      equal(
        bag.status,
        expectedBagStatus(journal, row),
        "package-current-state",
      );
      if (posted) {
        equal(
          bag.locationId,
          journal.result(`location.${row.location}`),
          "package-real-location-id",
        );
        equal(
          bag.locationCode,
          LOCATIONS.find((location) => location.key === row.location).body
            .locationCode,
          "package-real-location-code",
        );
        id(bag.stockLotId);
      } else
        requireFact(
          !bag.locationId && !bag.locationCode && !bag.stockLotId,
          "no-inferred-location-before-posting",
        );
    }
    if (journal.has(outputKey(row, "qc.create"))) {
      const inspection = facts.qc[row.key];
      equal(
        inspection?.id,
        journal.result(outputKey(row, "qc.create")),
        "qc-checkpoint-id",
      );
      equal(
        inspection.clientRequestId,
        `${MARKER}:qc:${row.key}`,
        "qc-request-identity",
      );
      equal(inspection.remark, outputRemark(row), "qc-synthetic-marker");
      assertQuality(
        inspection,
        bags,
        outputId,
        qcStatus(journal, row),
        actors,
        row.key === "rejected",
      );
    }
    assertPutaway(facts, journal, actors, row, bags);
    if (!posted) continue;
    equal(
      new Set(bags.map((bag) => bag.stockLotId)).size,
      1,
      "exact-one-stock-lot-per-output",
    );
    const lot = facts.lots.find((entry) => entry.id === bags[0].stockLotId);
    expectedLots.push(id(lot?.id));
    assertSyntheticFields(
      lot,
      { ...row, customerName: CUSTOMER },
      ["customerName", "productModel", "furnaceNo", "furnaceLayer"],
      "stock-lot-facts",
    );
    equal(lot.locationId, bags[0].locationId, "stock-lot-position");
    equal(numeric(lot.packageWeight), row.weight, "stock-lot-single-weight");
    const outbound = bags.every((bag) => bag.status === "OUTBOUNDED");
    const quantity = outbound ? 0 : row.count;
    const allocated = journal.has(outputKey(row, "allocation"));
    equal(numeric(lot.warehouseQty), quantity, "lot-remaining-count");
    equal(numeric(lot.stockWeight), quantity * row.weight, "lot-actual-weight");
    equal(numeric(lot.inboundQty), row.count, "lot-historical-inbound");
    equal(
      numeric(lot.outboundQty),
      outbound ? row.count : 0,
      "lot-historical-outbound",
    );
    equal(
      numeric(lot.lockedQty),
      allocated && !outbound ? row.count : 0,
      "lot-exact-order-reservation",
    );
    equal(
      numeric(lot.availableQty),
      allocated ? 0 : quantity,
      "lot-available-quantity",
    );
    for (const line of facts.putaway[row.key].lines)
      expectedTransactions.push({
        id: line.stockTransactionId,
        bagId: line.packageId,
        type: "PRODUCTION_IN",
        source: "PUTAWAY_TASK",
      });
    const saleId = journal.result(outputKey(row, "sale"));
    if (!saleId) continue;
    const sale = facts.sales.find((entry) => entry.id === saleId);
    assertSyntheticFields(
      sale,
      {
        orderNo: orderNo(row.key),
        orderDate: DATE,
        customerName: CUSTOMER,
        productModel: row.productModel,
        remark: outputRemark(row),
      },
      ["orderNo", "orderDate", "customerName", "productModel", "remark"],
      "sale-order",
    );
    equal(numeric(sale.requiredQty), row.count, "order-required-packages");
    equal(numeric(sale.actualOutputQty), 0, "no-invented-order-output-link");
    equal(
      numeric(sale.allocatedQty),
      allocated ? row.count : 0,
      "order-allocation-count",
    );
    equal(
      sale.status,
      outbound ? "CLOSED" : allocated ? "FULFILLED" : "OPEN",
      "order-match-status-is-not-shipment-status",
    );
    const allocations = facts.allocation[row.key];
    idsEqual(
      allocations.map((entry) => entry.id),
      allocated ? [journal.result(outputKey(row, "allocation"))] : [],
      "exact-single-lot-allocation",
    );
    if (allocated) {
      const allocation = allocations[0];
      equal(allocation.orderId, saleId, "allocation-order");
      equal(allocation.stockLotId, lot.id, "allocation-lot");
      equal(numeric(allocation.allocatedQty), row.count, "allocation-count");
      equal(
        numeric(allocation.outboundQty),
        outbound ? row.count : 0,
        "allocation-actual-outbound-count",
      );
      equal(
        allocation.status,
        outbound ? "OUTBOUNDED" : "ALLOCATED",
        "allocation-status",
      );
    }
  }
  idsEqual(
    facts.bags.map((bag) => bag.id),
    expectedPackages,
    "task-exact-package-membership",
  );
  idsEqual(
    facts.lots.map((lot) => lot.id),
    expectedLots,
    "no-merged-mixed-weight-or-extra-lots",
  );
  for (const shipment of SHIPMENTS) {
    assertShipment(facts, journal, actors, shipment);
    for (const line of facts.loading[shipment.key]?.lines || [])
      if (line.stockTransactionId)
        expectedTransactions.push({
          id: line.stockTransactionId,
          bagId: line.packageId,
          type: "OUTBOUND",
          source: "OUTBOUND_ORDER",
        });
  }
  idsEqual(
    facts.transactions.map((transaction) => transaction.id),
    expectedTransactions.map((transaction) => transaction.id),
    "exact-original-ledger-no-duplicate-posting",
  );
  for (const expected of expectedTransactions) {
    const transaction = facts.transactions.find(
      (entry) => entry.id === expected.id,
    );
    const bag = facts.bags.find((entry) => entry.id === expected.bagId);
    const sign = expected.type === "OUTBOUND" ? -1 : 1;
    equal(
      transaction.sourceLineNo,
      bag.packageCode,
      "ledger-package-identity-through-original-source-line",
    );
    equal(transaction.transactionType, expected.type, "ledger-type");
    equal(transaction.sourceType, expected.source, "ledger-original-source");
    equal(transaction.stockLotId, bag.stockLotId, "ledger-lot");
    equal(transaction.locationId, bag.locationId, "ledger-location");
    equal(transaction.operatorId, actors.warehouse, "ledger-keeper-actor");
    equal(transaction.bizDate, DATE, "ledger-local-date");
    equal(numeric(transaction.quantity), sign, "ledger-package-quantity");
    equal(
      numeric(transaction.weight),
      sign * numeric(bag.packageWeight),
      "ledger-real-weight",
    );
    equal(
      numeric(transaction.afterQty) - numeric(transaction.beforeQty),
      sign,
      "ledger-balanced-movement",
    );
  }
  for (const location of LOCATIONS) {
    if (!journal.has(`location.${location.key}`)) continue;
    const registered = facts.locations.find(
      (entry) => entry.id === journal.result(`location.${location.key}`),
    );
    assertSyntheticFields(
      registered,
      location.body,
      [
        "warehouseCode",
        "warehouseName",
        "areaCode",
        "areaName",
        "rowCode",
        "columnCode",
        "levelCode",
        "locationCode",
        "locationName",
        "enabled",
        "frozen",
        "remark",
      ],
      "registered-demo-position",
    );
    const mapped = facts.locationMap.find(
      (entry) => entry.id === registered.id,
    );
    requireFact(mapped, "original-location-map-position");
    assertSyntheticFields(
      mapped,
      location.body,
      [
        "warehouseCode",
        "warehouseName",
        "areaCode",
        "areaName",
        "rowCode",
        "columnCode",
        "levelCode",
        "locationCode",
        "locationName",
        "enabled",
        "frozen",
      ],
      "mapped-registered-coordinates",
    );
    const physical = facts.bags.filter(
      (bag) =>
        bag.locationId === registered.id &&
        ["IN_STOCK", "OUTBOUND_PENDING"].includes(bag.status),
    );
    const reserved = Object.values(facts.putaway).filter(
      (detail) =>
        detail.task.plannedLocationId === registered.id &&
        detail.task.status !== "PUTAWAY_COMPLETED",
    );
    const occupiedWeight = physical.reduce(
      (sum, bag) => sum + numeric(bag.packageWeight),
      0,
    );
    const reservedCount = reserved.reduce(
      (sum, detail) => sum + detail.task.packageCount,
      0,
    );
    const reservedWeight = reserved.reduce(
      (sum, detail) => sum + numeric(detail.task.totalWeight),
      0,
    );
    for (const entry of [registered, mapped])
      for (const [field, expected] of Object.entries({
        capacityPackages: location.body.capacityPackages,
        capacityWeight: location.body.capacityWeight,
        occupiedPackages: physical.length,
        occupiedWeight,
        reservedPackages: reservedCount,
        reservedWeight,
      }))
        equal(
          numeric(entry[field]),
          expected,
          `location-capacity-and-counters:${field}`,
        );
    requireFact(
      physical.length + reservedCount <= location.body.capacityPackages &&
        occupiedWeight + reservedWeight <= location.body.capacityWeight,
      "compatible-location-capacity",
    );
    equal(mapped.frozen, location.key === "frozen", "map-real-freeze-state");
  }
  const claimed = new Set(
    Object.values(facts.loading)
      .filter((detail) => detail.task.status !== "OUTBOUNDED")
      .flatMap((detail) => detail.lines.map((line) => line.packageId)),
  );
  idsEqual(
    facts.available.map((bag) => bag.id),
    facts.bags
      .filter((bag) => bag.status === "IN_STOCK" && !claimed.has(bag.id))
      .map((bag) => bag.id),
    "real-loading-claims-vs-available-stock",
  );
  if (complete) {
    equal(
      [...checkpoints].sort().join(","),
      [...CHECKPOINT_KEYS].sort().join(","),
      "all-planned-checkpoints-complete",
    );
    equal(facts.outputs.length, 9, "final-nine-outputs");
    equal(facts.inspections.length, 9, "final-nine-real-batch-inspections");
    equal(facts.putaways.length, 6, "final-six-putaway-tasks");
    equal(facts.sales.length, 4, "final-four-exact-lot-orders");
    equal(facts.pickups.length, 3, "final-three-pickup-slips");
    equal(facts.loadings.length, 3, "final-three-whole-truck-loading-tasks");
    equal(Object.keys(facts.inbound).length, 5, "final-five-inbound-documents");
    equal(
      Object.keys(facts.outbound).length,
      2,
      "final-two-lot-outbound-documents",
    );
    equal(
      facts.transactions.length,
      22,
      "final-seventeen-inbound-five-outbound-movements",
    );
    const summary = summarizePackages(facts.bags);
    equal(summary.count, 30, "final-thirty-bags");
    equal(summary.weightKg, 29800, "final-registered-29800kg");
    for (const [status, count] of Object.entries({
      QC_PENDING: 3,
      QC_REJECTED: 2,
      QC_PASSED: 4,
      INBOUND_PENDING: 4,
      IN_STOCK: 12,
      OUTBOUNDED: 5,
    }))
      equal(
        summary.states[status],
        count,
        "final-mutually-exclusive-package-states",
      );
    equal(facts.available.length, 6, "final-six-unclaimed-stock-bags");
    for (const row of PLAN)
      requireFact(
        journal.has(outputKey(row, "qc.submit")),
        "final-all-batches-submitted",
      );
    for (const shipment of SHIPMENTS)
      equal(
        facts.loading[shipment.key]?.task.status,
        shipment.status,
        "final-eight-scenario-completion",
      );
    assertTraces(facts, journal);
  }
  return summarizePackages(facts.bags);
}

function assertTraces(facts, journal) {
  for (const row of PLAN) {
    const bags = facts.bags
      .filter(
        (bag) =>
          bag.productionOutputId === journal.result(outputKey(row, "create")),
      )
      .sort((a, b) => a.id - b.id);
    for (const bag of bags) {
      const trace = facts.traces[bag.id];
      equal(trace?.internalView, true, "authenticated-internal-trace");
      equal(trace.packageCode, bag.packageCode, "trace-package-code");
      equal(
        trace.productionTaskId,
        journal.result("task"),
        "trace-original-production-task",
      );
      equal(
        trace.productionOutputId,
        bag.productionOutputId,
        "trace-original-output",
      );
      equal(trace.status, bag.status, "trace-current-state");
      equal(
        numeric(trace.packageWeight),
        row.weight,
        "trace-registered-net-weight",
      );
      equal(
        trace.qualityInspectionId,
        journal.result(outputKey(row, "qc.create")),
        "trace-persisted-batch-link",
      );
      equal(trace.qualityCoverageCount, row.count, "trace-real-coverage");
      equal(trace.qualitySampleCount, 1, "trace-real-sample-count");
      equal(
        trace.qualitySampled,
        bag.id === bags[0].id,
        "trace-real-sample-identity",
      );
      equal(
        trace.qualityStatus,
        qcStatus(journal, row),
        "trace-original-qc-status",
      );
      requireFact(
        Array.isArray(trace.events) && trace.events.length > 0,
        "trace-persisted-event-timeline",
      );
      const stages = new Set(trace.events.map((event) => event.stage));
      for (const stage of [
        "PRODUCTION",
        "LABEL",
        "QUALITY",
        ...(row.location ? ["PUTAWAY"] : []),
        ...(journal.has(outputKey(row, "putaway.post")) ? ["STOCK"] : []),
        ...(SHIPMENTS.some((shipment) => shipment.outputs.includes(row.key))
          ? ["LOADING"]
          : []),
        ...(row.key === "exception" ? ["EXCEPTION"] : []),
      ])
        requireFact(stages.has(stage), "trace-original-persisted-stages");
      const graph = facts.relations[bag.id];
      equal(graph?.rootPackageCode, bag.packageCode, "relations-root-identity");
      requireFact(
        Array.isArray(graph.sections) &&
          graph.sections.every((section) => section.state === "COMPLETE"),
        "relations-complete-not-redacted-or-truncated",
      );
      const nodes = (kind) => graph.nodes.filter((node) => node.kind === kind);
      idsEqual(
        nodes("PRODUCTION_TASK").map((node) => node.referenceId),
        [journal.result("task")],
        "relations-original-task",
      );
      idsEqual(
        nodes("QUALITY_INSPECTION").map((node) => node.referenceId),
        [journal.result(outputKey(row, "qc.create"))],
        "relations-no-extra-qc-documents",
      );
      const qualityNode = nodes("QUALITY_INSPECTION")[0];
      const member = graph.edges.find(
        (edge) =>
          edge.fromNodeKey === qualityNode.nodeKey &&
          edge.toNodeKey === graph.rootNodeKey &&
          edge.relation === "QUALITY_COVERAGE",
      );
      requireFact(
        member?.evidenceType === "QUALITY_INSPECTION_MEMBER",
        "relations-real-member-evidence",
      );
      equal(
        member.sampled,
        bag.id === bags[0].id,
        "relations-persisted-sample-evidence",
      );
      const putaway = facts.putaway[row.key];
      idsEqual(
        nodes("PUTAWAY_TASK").map((node) => node.referenceId),
        putaway ? [putaway.task.id] : [],
        "relations-original-putaway-task",
      );
      const shipment = SHIPMENTS.find((shipment) =>
        shipment.outputs.includes(row.key),
      );
      const loading = shipment ? facts.loading[shipment.key] : undefined;
      idsEqual(
        nodes("LOADING_TASK").map((node) => node.referenceId),
        loading ? [loading.task.id] : [],
        "relations-whole-truck-task",
      );
      const context = facts.contexts[bag.id];
      equal(context?.packageCode, bag.packageCode, "work-context-root");
      const active = [];
      if (row.key === "assigned")
        active.push({
          type: "PUTAWAY",
          taskId: putaway.task.id,
          status: "ASSIGNED",
        });
      if (shipment && shipment.key !== "shipped")
        active.push({
          type: "LOADING",
          taskId: loading.task.id,
          status: loading.task.status,
        });
      equal(
        context.tasks?.length,
        active.length,
        "work-context-only-actual-active-tasks",
      );
      for (const task of active)
        requireFact(
          context.tasks.some(
            (entry) =>
              entry.type === task.type &&
              entry.taskId === task.taskId &&
              entry.status === task.status,
          ),
          "work-context-persisted-task",
        );
      if (loading && shipment.key !== "shipped")
        equal(
          context.pickupSlipId,
          loading.task.pickupSlipId,
          "work-context-pickup-link",
        );
    }
  }
}

function readUrls(journal, facts) {
  const taskId = journal?.result("task");
  if (!taskId) return {};
  const prefix = `${BACKEND}/admin-api`;
  const sample = facts?.bags?.[0];
  return {
    task: `${prefix}/rd/production-task/get?id=${taskId}`,
    packages: `${prefix}/rd/package-unit/task-packages?productionTaskId=${taskId}`,
    outputs: `${prefix}/rd/production-output/page?productionTaskId=${taskId}&pageNo=1&pageSize=100`,
    locationMap: `${prefix}/rd/putaway-task/location-map`,
    loadingTasks: `${prefix}${query("/rd/loading-task/page", { customerName: CUSTOMER, pageNo: 1, pageSize: 100 })}`,
    ledger: `${prefix}${query("/rd/stock-transaction/page", { customerName: CUSTOMER, pageNo: 1, pageSize: 100 })}`,
    ...(sample
      ? {
          trace: `${prefix}/rd/package-unit/trace-detail/${encodeURIComponent(sample.packageCode)}`,
          relations: `${prefix}/rd/package-unit/relations?packageCode=${encodeURIComponent(sample.packageCode)}`,
          workContext: `${prefix}/rd/package-unit/work-context/${encodeURIComponent(sample.packageCode)}`,
        }
      : {}),
  };
}

function resultSummary(mode, facts, journal, summary) {
  const scenarios = {};
  for (const row of PLAN) {
    const bags = facts.bags.filter(
      (bag) =>
        bag.productionOutputId === journal.result(outputKey(row, "create")),
    );
    const totals = summarizePackages(bags);
    const old = scenarios[row.scenario] || { count: 0, weightKg: 0 };
    scenarios[row.scenario] = {
      count: old.count + totals.count,
      weightKg: old.weightKg + totals.weightKg,
    };
  }
  return {
    result: "PASS",
    mode,
    marker: MARKER,
    synthetic: true,
    productionTaskId: journal.result("task"),
    ...summary,
    scenarios,
    documents: {
      outputs: facts.outputs.length,
      inspections: facts.inspections.length,
      putawayTasks: facts.putaways.length,
      inboundOrders: Object.keys(facts.inbound).length,
      saleOrders: facts.sales.length,
      pickupSlips: facts.pickups.length,
      loadingTasks: facts.loadings.length,
      outboundOrders: Object.keys(facts.outbound).length,
      stockTransactions: facts.transactions.length,
    },
    shipmentTasks: SHIPMENTS.map((shipment) => ({
      scenario: shipment.key,
      id: facts.loading[shipment.key].task.id,
      status: facts.loading[shipment.key].task.status,
    })),
    readUrls: readUrls(journal, facts),
  };
}

const exists = async (filename) => {
  try {
    await fs.stat(filename);
    return true;
  } catch (error) {
    if (error.code === "ENOENT") return false;
    throw error;
  }
};

export async function main(argv = process.argv.slice(2)) {
  if (argv.length === 0 || (argv.length === 1 && argv[0] === "--help"))
    return {
      result: "USAGE",
      commands: [
        "node scripts/verification/Seed-RdFlowDemo.mjs --seed",
        "node scripts/verification/Seed-RdFlowDemo.mjs --check",
      ],
      recovery:
        "Keep the manifest. Uncertain operations and stale locks require inspection; there is no force/reset option.",
    };
  requireFact(
    argv.length === 1 && ["--seed", "--check"].includes(argv[0]),
    "exactly-one-explicit-mode",
  );
  const check = argv[0] === "--check";
  const stored = await readJson(MANIFEST, true);
  if (check && !stored) throw fail("MANIFEST_REQUIRED");
  let journal = stored ? createJournal(stored, persistManifest) : undefined;
  journal?.settled();
  if (await exists(`${MANIFEST}.next`)) throw fail("CHECKPOINT_FILE_UNCERTAIN");
  if (await exists(LOCK)) throw fail("SEED_LOCK_PRESENT");
  const ignore = await fs.readFile(path.join(ROOT, ".gitignore"), "utf8");
  requireFact(
    /^temp\/\r?$/m.test(ignore) && !/^!\/?temp(?:\/|$)/m.test(ignore),
    "recovery-directory-must-stay-ignored",
  );
  const client = createClient({ check });
  const { tokens, actors } = await localSession(client);
  if (journal)
    equal(
      hash(journal.value.actors),
      hash(actors),
      "same-credential-actors-on-resume",
    );
  else journal = createJournal(newManifest(actors), persistManifest);
  let lock;
  try {
    if (!check) {
      await fs.mkdir(DIRECTORY, { recursive: true });
      try {
        lock = await fs.open(LOCK, "wx", 0o600);
      } catch {
        throw fail("SEED_LOCK_PRESENT");
      }
      await lock.writeFile(
        `${JSON.stringify({ pid: process.pid, marker: MARKER })}\n`,
      );
      await lock.sync();
    }
    const context = { client, tokens, actors, journal };
    const before = await collectFacts(context);
    validateFacts(before, journal, actors);
    if (!check) {
      // Backend-created posting dates use its local clock; never alter that clock or its config.
      const date = new Intl.DateTimeFormat("en-CA", {
        timeZone: "Asia/Shanghai",
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
      }).format(new Date());
      if (!CHECKPOINT_KEYS.every((key) => journal.has(key)))
        equal(date, DATE, "seed-on-declared-local-date-only");
      if (!stored) await persistManifest(journal.value);
      await seedFlow(context);
    }
    const after = await collectFacts(context, true);
    const summary = validateFacts(after, journal, actors, true);
    return resultSummary(check ? "check" : "seed", after, journal, summary);
  } catch (error) {
    error.details = { ...error.details, readUrls: readUrls(journal) };
    throw error;
  } finally {
    if (lock) {
      await lock.close();
      await fs.unlink(LOCK);
    }
  }
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href
) {
  try {
    process.stdout.write(`${JSON.stringify(await main())}\n`);
  } catch (error) {
    // Do not emit server messages, raw fetch errors, account objects, environment or tokens.
    const code =
      typeof error.code === "string" && /^[A-Z_]+$/.test(error.code)
        ? error.code
        : "LOCAL_SEED_FAILED";
    process.stdout.write(
      `${JSON.stringify({ result: "FAIL", code, ...(error.details || {}) })}\n`,
    );
    process.exitCode =
      code === "WRITE_UNCERTAIN"
        ? 3
        : code === "MANIFEST_REQUIRED" || code === "VALIDATION_FAILED"
          ? 2
          : 1;
  }
}
