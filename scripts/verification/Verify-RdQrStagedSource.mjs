import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const git = (args, encoding = "utf8") =>
  execFileSync("git", args, {
    cwd: root,
    encoding,
    maxBuffer: 12 * 1024 * 1024,
  });
const head = process.argv.includes("--head");
const prefix = head ? "HEAD:" : ":";
const files = git(
  head
    ? [
        "diff",
        "11c7809899301012264df5b6d0d2f156a42eb413",
        "HEAD",
        "--name-only",
        "-z",
      ]
    : ["diff", "--cached", "--name-only", "-z"],
)
  .split("\0")
  .filter(Boolean);
assert.ok(files.length > 0, "Expected scoped QR changes");
const secret =
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{30,}|AKIA[A-Z0-9]{16}|sk-[A-Za-z0-9_-]{30,}/;
for (const file of files) {
  assert.ok(
    !/(^|\/)(?:temp|outputs|node_modules|dist-prod|target|\.env(?:\.|$))/.test(
      file,
    ),
    `Runtime/private path staged: ${file}`,
  );
  assert.ok(
    !/\.(?:dump|sql|jar|zip|png|y4m|pem|key|p12)$/i.test(file),
    `Unrequested artifact staged: ${file}`,
  );
  assert.ok(
    !secret.test(git(["show", prefix + file])),
    `Possible credential in ${file}`,
  );
}
const base = "frontend/public/licenses/qr-workbench/";
const manifest = JSON.parse(git(["show", prefix + base + "MANIFEST.json"]));
assert.equal(manifest.packages.length, 102);
let verifiedNotices = 0;
const hash = (bytes) => createHash("sha256").update(bytes).digest("hex");
for (const entry of manifest.packages)
  for (const notice of entry.licenses) {
    assert.equal(path.basename(notice.file), notice.file);
    assert.equal(
      hash(git(["show", prefix + base + notice.file], null)),
      notice.sha256,
      `Notice bytes changed in Git: ${notice.file}`,
    );
    verifiedNotices++;
  }
const iconNotice = JSON.parse(
  git(["show", prefix + base + "Lucide-source-declaration.json"]),
);
assert.equal(
  hash(
    git(
      ["show", prefix + "frontend/src/plugins/svgIcon/qr-workbench-icons.json"],
      null,
    ),
  ),
  iconNotice.selectedIconsSha256,
);
const reader = fs.readFileSync(
  path.join(root, "frontend/dist-prod/assets/zxing_reader-DUHi_9Vq.wasm"),
);
assert.equal(hash(reader), manifest.reader.sha256);
console.log(
  JSON.stringify({
    mode: head ? "HEAD" : "INDEX",
    checkedFiles: files.length,
    secretFindings: 0,
    privatePaths: 0,
    verifiedNotices,
    metadataOnly: manifest.packages
      .filter((row) => row.metadataOnly)
      .map((row) => row.name),
    localIconsVerified: true,
    builtReaderVerified: true,
  }),
);
