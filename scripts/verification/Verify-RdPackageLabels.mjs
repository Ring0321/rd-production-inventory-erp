import assert from "node:assert/strict";
import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const requireFrontend = createRequire(path.join(root, "frontend/package.json"));
const ts = requireFrontend("typescript");
const QRCode = requireFrontend("qrcode");
const { chromium } = process.env.RD_BROWSER_MODULES
  ? requireFrontend(path.join(process.env.RD_BROWSER_MODULES, "playwright"))
  : requireFrontend("playwright");
const output = path.join(root, "temp/qr-label-verification");
await fs.mkdir(output, { recursive: true });
const source = await fs.readFile(
  path.join(root, "frontend/src/utils/rdPackageLabel.ts"),
  "utf8",
);
const javascript = ts.transpileModule(source, {
  compilerOptions: {
    module: ts.ModuleKind.ESNext,
    target: ts.ScriptTarget.ES2022,
  },
}).outputText;
const { buildPackageLabelHtml } = await import(
  `data:text/javascript;base64,${Buffer.from(javascript).toString("base64")}`
);
const labels = [];
for (let i = 1; i <= 10; i++) {
  const packageCode = `LD20261011A${String(i).padStart(4, "0")}`;
  const digitalLink = `https://id.tjldsic.com/pkg/${packageCode}`;
  const qrDataUrl = await QRCode.toDataURL(digitalLink, {
    width: 420,
    margin: 4,
    errorCorrectionLevel: "M",
  });
  const item = {
    id: i,
    packageCode,
    qrCode: digitalLink,
    productionOutputId: 1,
    productionDate: "2026-10-11",
    furnaceOutDate: "2026-10-10",
    shiftCode: "甲班",
    customerName:
      i === 10
        ? "标签验证用超长客户名称宁夏天净隆鼎碳化硅有限公司（本地示例）"
        : `本地示例客户 ${i}`,
    productName: "碳化硅",
    productModel: `SC-${i}`,
    furnaceNo: "1-13#28",
    furnaceLayer: i % 2 ? "上层" : "下层",
    packageWeight: i === 10 ? 697 : 1000,
    status: "CREATED",
    version: 0,
  };
  labels.push({ item, qrDataUrl });
  await fs.writeFile(
    path.join(output, `${packageCode}.png`),
    Buffer.from(qrDataUrl.split(",")[1], "base64"),
  );
}
await fs.writeFile(
  path.join(output, "expected.json"),
  JSON.stringify(
    labels.map(({ item }) => item),
    null,
    2,
  ),
);

const browser = await chromium.launch({
  headless: true,
  channel: process.env.RD_BROWSER_CHANNEL,
});
const page = await browser.newPage({ viewport: { width: 1000, height: 900 } });
const errors = [];
page.on("pageerror", (error) => errors.push(error.message));
try {
  for (const paper of ["LABEL_100_76", "A4"]) {
    const html = buildPackageLabelHtml(labels, paper);
    await fs.writeFile(path.join(output, `${paper}.html`), html);
    await page.setContent(html, { waitUntil: "load" });
    assert.equal(await page.locator(".goods-label").count(), 10);
    for (let i = 0; i < labels.length; i++) {
      const content = await page.locator(".goods-label").nth(i).innerText();
      for (const field of [
        "packageCode",
        "customerName",
        "productModel",
        "furnaceNo",
        "furnaceLayer",
        "productionDate",
        "shiftCode",
      ]) {
        assert.ok(
          content.includes(labels[i].item[field]),
          `Label ${i + 1} missing ${field}`,
        );
      }
      assert.ok(content.includes(`${labels[i].item.packageWeight} kg`));
    }
    await page.emulateMedia({ media: "print" });
    const overflow = await page.locator(".goods-label").evaluateAll((cards) =>
      cards.flatMap((card, index) => {
        const boundary = card.getBoundingClientRect();
        return [...card.querySelectorAll("strong,.package-code,img")]
          .filter((element) => {
            const box = element.getBoundingClientRect();
            return (
              box.bottom > boundary.bottom + 1 || box.right > boundary.right + 1
            );
          })
          .map((element) => ({ index, text: element.textContent }));
      }),
    );
    assert.deepEqual(overflow, [], `${paper}: printed content overflow`);
    await page.pdf({
      path: path.join(output, `${paper}.pdf`),
      preferCSSPageSize: true,
      printBackground: false,
    });
    await page.emulateMedia({ media: "screen" });
  }
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 720 });
    await page.setContent(buildPackageLabelHtml([labels[9]], "LABEL_100_76"), {
      waitUntil: "load",
    });
    assert.ok(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    );
    await page.screenshot({
      path: path.join(output, `preview-${width}.png`),
      fullPage: true,
    });
  }
  const unsafe = structuredClone(labels[0]);
  unsafe.item.customerName = '<img src=x onerror="alert(1)">';
  await page.setContent(buildPackageLabelHtml([unsafe], "LABEL_100_76"), {
    waitUntil: "load",
  });
  assert.equal(await page.locator("img").count(), 1);
  assert.ok(
    (await page.locator(".goods-label").innerText()).includes(
      unsafe.item.customerName,
    ),
  );
  assert.deepEqual(errors, []);
  console.log(
    JSON.stringify({
      result: "PASS",
      labels: 10,
      papers: 2,
      viewports: [390, 320],
      escapedFields: true,
      output,
    }),
  );
} finally {
  await browser.close();
}
