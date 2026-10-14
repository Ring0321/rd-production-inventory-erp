import fs from "node:fs/promises";
import path from "node:path";
import { createRequire } from "node:module";
import { createHash } from "node:crypto";
import { fileURLToPath } from "node:url";

const root = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../..",
);
const frontend = path.join(root, "frontend");
const destination = path.join(frontend, "public/licenses/qr-workbench");
const roots = [
  "vue-qrcode-reader",
  "barcode-detector",
  "zxing-wasm",
  "dexie",
  "@antv/g6",
];
const hash = (value) => createHash("sha256").update(value).digest("hex");
const exists = async (file) => {
  try {
    await fs.access(file);
    return true;
  } catch {
    return false;
  }
};
const packageDirectory = async (name, from) => {
  const require = createRequire(path.join(from, "package.json"));
  try {
    return path.dirname(require.resolve(`${name}/package.json`));
  } catch {}
  try {
    let directory = path.dirname(require.resolve(name));
    for (let n = 0; n < 8; n++) {
      const file = path.join(directory, "package.json");
      if (await exists(file)) {
        const data = JSON.parse(await fs.readFile(file, "utf8"));
        if (data.name === name) return directory;
      }
      directory = path.dirname(directory);
    }
  } catch {}
  let directory = from;
  for (let n = 0; n < 10; n++) {
    const candidate = path.join(directory, "node_modules", name);
    if (await exists(path.join(candidate, "package.json"))) return candidate;
    directory = path.dirname(directory);
  }
  throw new Error(`Cannot locate licensed package ${name}`);
};
await fs.mkdir(destination, { recursive: true });
const visited = new Set();
const manifest = [];
const visit = async (name, from) => {
  if (!/^(?:@[a-z0-9._-]+\/)?[a-z0-9._-]+$/i.test(name))
    throw new Error("Invalid dependency name");
  const directory = await packageDirectory(name, from);
  const pkg = JSON.parse(
    await fs.readFile(path.join(directory, "package.json"), "utf8"),
  );
  if (
    pkg.name !== name ||
    !/^\d+\.\d+\.\d+(?:[-+][A-Za-z0-9.-]+)?$/.test(pkg.version)
  )
    throw new Error("Invalid licensed package identity");
  const key = `${pkg.name}@${pkg.version}`;
  if (visited.has(key)) return;
  visited.add(key);
  const names = (await fs.readdir(directory)).filter((file) =>
    /^(?:licen[sc]e|notice|copying)(?:[._-].*)?$/i.test(file),
  );
  const metadataOnly = !names.length;
  if (metadataOnly) {
    if (
      !["MIT", "ISC", "Apache-2.0", "BSD-3-Clause", "BSD-2-Clause"].includes(
        pkg.license,
      )
    )
      throw new Error(`Unknown license for ${key}`);
    const declaration = Buffer.from(
      JSON.stringify(
        {
          name: pkg.name,
          version: pkg.version,
          license: pkg.license,
          repository: pkg.repository,
          author: pkg.author,
          notice:
            "This published package declares the license above but ships no separate LICENSE/NOTICE file. Original package metadata and README are retained. This is not a legal compliance certification.",
        },
        null,
        2,
      ) + "\n",
    );
    const file = `${pkg.name.replaceAll("/", "--").replaceAll("@", "")}-${pkg.version}-DECLARED-LICENSE.json`;
    await fs.writeFile(path.join(destination, file), declaration);
    if (await exists(path.join(directory, "README.md")))
      names.push("README.md");
  }
  const licenses = [];
  for (const source of names) {
    const input = path.join(directory, source);
    if (!(await fs.stat(input)).isFile()) continue;
    const bytes = await fs.readFile(input);
    const output = `${pkg.name.replaceAll("/", "--").replaceAll("@", "")}-${pkg.version}-${source}`;
    await fs.writeFile(path.join(destination, output), bytes);
    licenses.push({ file: output, sha256: hash(bytes) });
  }
  if (!licenses.length && !metadataOnly)
    throw new Error(`License source is not a file for ${key}`);
  manifest.push({
    name: pkg.name,
    version: pkg.version,
    license: pkg.license,
    metadataOnly,
    licenses,
  });
  for (const dependency of Object.keys(pkg.dependencies || {}))
    await visit(dependency, directory);
};
for (const name of roots) await visit(name, frontend);
const iconDirectory = await packageDirectory("@iconify/json", frontend);
const lucide = JSON.parse(
  await fs.readFile(path.join(iconDirectory, "json/lucide.json"), "utf8"),
);
const iconNames = [
  "workflow",
  "git-fork",
  "plus",
  "minus",
  "scan",
  "refresh-cw",
  "arrow-up-right",
  "scan-line",
  "camera-off",
  "camera",
  "image-up",
  "trash-2",
  "check",
  "inbox",
  "boxes",
  "circle-check",
  "clipboard-check",
  "database",
  "file-check-2",
  "folder-open",
  "history",
  "link-2",
  "list-filter",
  "message-square-text",
  "package-check",
  "printer",
  "send",
  "server-cog",
  "shield-check",
  "copy",
  "download",
  "file-json",
  "list-tree",
  "log-in",
];
if (lucide.prefix !== "lucide" || lucide.info.license.spdx !== "ISC")
  throw new Error("Unexpected Lucide icon source");
const selectedIcons = Object.fromEntries(
  iconNames.map((name) => {
    if (!lucide.icons[name]) throw new Error(`Missing local icon ${name}`);
    return [name, lucide.icons[name]];
  }),
);
const prettier = createRequire(path.join(frontend, "package.json"))("prettier");
const iconPath = path.join(
  frontend,
  "src/plugins/svgIcon/qr-workbench-icons.json",
);
const localIcons = await prettier.format(
  JSON.stringify(
    {
      prefix: lucide.prefix,
      width: lucide.width,
      height: lucide.height,
      info: lucide.info,
      lastModified: lucide.lastModified,
      icons: selectedIcons,
    },
    null,
    2,
  ),
  { ...(await prettier.resolveConfig(iconPath)), parser: "json" },
);
await fs.writeFile(
  path.join(frontend, "src/plugins/svgIcon/qr-workbench-icons.json"),
  localIcons,
);
await fs.writeFile(
  path.join(destination, "Lucide-source-declaration.json"),
  JSON.stringify(
    {
      source: `@iconify/json@${JSON.parse(await fs.readFile(path.join(iconDirectory, "package.json"), "utf8")).version}/json/lucide.json`,
      licenseFile: "../lucide-LICENSE.txt",
      license: lucide.info.license,
      author: lucide.info.author,
      originalIconsSha256: hash(
        await fs.readFile(path.join(iconDirectory, "json/lucide.json")),
      ),
      selectedIconsSha256: hash(localIcons),
      icons: iconNames,
    },
    null,
    2,
  ) + "\n",
);
const apache = await fs.readFile(
  path.join(await packageDirectory("dexie", frontend), "LICENSE"),
);
await fs.writeFile(path.join(destination, "ZXing-Cpp-Apache-2.0.txt"), apache);
const reader = await fs.readFile(
  path.join(
    await packageDirectory("zxing-wasm", frontend),
    "dist/reader/zxing_reader.wasm",
  ),
);
await fs.writeFile(
  path.join(destination, "MANIFEST.json"),
  JSON.stringify(
    {
      roots,
      reader: { package: "zxing-wasm", version: "1.1.3", sha256: hash(reader) },
      packages: manifest.sort((a, b) => a.name.localeCompare(b.name)),
      notice:
        "Reader-only ZXing-C++ WebAssembly is bundled locally. ZXing-C++ is licensed under Apache-2.0; wrapper licenses are retained separately. No cloud addon or writer WASM is shipped.",
    },
    null,
    2,
  ) + "\n",
);
console.log(
  JSON.stringify({
    packages: manifest.length,
    metadataOnly: manifest
      .filter((row) => row.metadataOnly)
      .map((row) => row.name + "@" + row.version),
    readerSha256: hash(reader),
    destination: "frontend/public/licenses/qr-workbench",
  }),
);
