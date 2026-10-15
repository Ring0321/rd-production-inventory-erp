"""Rebuild the local OFL Chinese TrueType asset from pinned official Noto blobs."""

import base64
import hashlib
import io
import json
import platform
import sys
import urllib.request
from pathlib import Path

import fontTools
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont


HERE = Path(__file__).resolve().parent
PUBLIC = HERE.parents[2] / "frontend" / "public"
NOTICES = PUBLIC / "licenses" / "assist-fonts"
OUTPUT = PUBLIC / "fonts" / "assist" / "NotoSansSC-Regular.ttf"
REPOSITORY = "https://github.com/notofonts/noto-cjk"
COMMIT = "f8d157532fbfaeda587e826d4cd5b21a49186f7c"
FONT_PATH = "Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf"
LICENSE_PATH = "Sans/LICENSE"
FONT_BLOB = "5371a543be5fc670c7cdee9760c03554ee3e9b8e"
LICENSE_BLOB = "d952d62c065f3f35fb83a173496e90b21525aef3"
FAMILY = "Warehouse Assist SC"
POSTSCRIPT = "WarehouseAssistSC-Regular"
SAMPLE = (
    "\u4ed3\u5e93\u8d27\u4f4d\u7269\u6599\u540d\u79f0"
    "\u89c4\u683c\u578b\u53f7\u6279\u6b21\u5e93\u5b58"
    "\u6570\u91cf\u5355\u4f4d\u5165\u5e93\u51fa\u5e93"
    "\u751f\u4ea7\u65e5\u671f\u8ffd\u6eaf\u6807\u7b7e"
    "\u5408\u683c\u5f85\u68c0\u5f02\u5e38\u82af\u73e9"
    "\u79d1\u6280\u5929\u51c0\u9686\u9f0e\u7b80\u4f53"
    "\u4e2d\u6587\u6d4b\u8bd5\uff1a\u4e00\u4e8c\u4e09"
    "\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341\u96f6"
    " ABC xyz 0123456789 -/()."
)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def github_json(url):
    request = urllib.request.Request(
        url,
        headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "REUSE-font-provenance",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def official_blob(path, expected_sha, expected_size):
    api = "https://api.github.com/repos/notofonts/noto-cjk"
    metadata_url = f"{api}/contents/{path}?ref={COMMIT}"
    metadata = github_json(metadata_url)
    if metadata["sha"] != expected_sha or metadata["size"] != expected_size:
        raise ValueError(f"Pinned upstream metadata changed: {path}")
    blob_url = f"{api}/git/blobs/{expected_sha}"
    blob = metadata if metadata.get("encoding") == "base64" else github_json(blob_url)
    if blob.get("encoding") != "base64":
        raise ValueError(f"Unexpected GitHub encoding: {path}")
    data = base64.b64decode(blob["content"])
    git_sha = hashlib.sha1(f"blob {len(data)}\0".encode("ascii") + data).hexdigest()
    if len(data) != expected_size or git_sha != expected_sha:
        raise ValueError(f"Official Git blob integrity check failed: {path}")
    return data, {
        "path": path,
        "commit_url": f"{REPOSITORY}/blob/{COMMIT}/{path}",
        "metadata_url": metadata_url,
        "git_blob_url": blob_url,
        "git_blob_sha1": git_sha,
        "sha256": sha256(data),
        "bytes": len(data),
        "retrieval": "Official GitHub REST API; base64 decoded; no raw.githubusercontent.com",
    }


def names(font, ids):
    return [
        {
            "name_id": record.nameID,
            "platform_id": record.platformID,
            "encoding_id": record.platEncID,
            "language_id": record.langID,
            "value": record.toUnicode(),
        }
        for record in font["name"].names
        if record.nameID in ids
    ]


def main():
    source_data, source_meta = official_blob(FONT_PATH, FONT_BLOB, 17773132)
    license_data, license_meta = official_blob(LICENSE_PATH, LICENSE_BLOB, 4301)
    source = TTFont(io.BytesIO(source_data), recalcTimestamp=False)
    if source.sfntVersion != "\x00\x01\x00\x00" or "glyf" not in source:
        raise ValueError("Official source is not TrueType glyf/loca")
    axes = {
        axis.axisTag: [axis.minValue, axis.defaultValue, axis.maxValue]
        for axis in source["fvar"].axes
    }
    if set(axes) != {"wght"}:
        raise ValueError(f"Unexpected axes; full static pinning required: {axes}")
    source_names = names(source, {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 11, 13, 14, 16, 17})
    source_cmap = source.getBestCmap()
    source_glyph_count = source["maxp"].numGlyphs
    print(f"Verified official source: {source_meta['sha256']}", flush=True)

    static = instantiateVariableFont(source, {"wght": 400}, inplace=False, updateFontNames=True)
    static.recalcTimestamp = False
    renamed_ids = {1, 2, 3, 4, 5, 6, 16, 17, 18, 21, 22, 25}
    static["name"].names = [
        record for record in static["name"].names if record.nameID not in renamed_ids
    ]
    replacement_names = {
        1: FAMILY,
        2: "Regular",
        3: f"{POSTSCRIPT};wght400;source{FONT_BLOB[:12]}",
        4: f"{FAMILY} Regular",
        5: "Version 2.004; static wght=400; renamed Warehouse Assist SC",
        6: POSTSCRIPT,
        16: FAMILY,
        17: "Regular",
        18: f"{FAMILY} Regular",
        21: FAMILY,
        22: "Regular",
    }
    for name_id, value in replacement_names.items():
        static["name"].setName(value, name_id, 3, 1, 0x0409)
        static["name"].setName(value, name_id, 1, 0, 0)
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    static.save(OUTPUT, reorderTables=True)
    static.close()
    source.close()

    data = OUTPUT.read_bytes()
    font = TTFont(OUTPUT, recalcTimestamp=False, checkChecksums=2)
    font.ensureDecompiled()
    cmap = font.getBestCmap()
    variable_tables = sorted(set(font.keys()) & {"fvar", "gvar", "avar", "HVAR", "VVAR", "MVAR", "cvar"})
    missing = [f"U+{ord(char):04X}" for char in sorted(set(SAMPLE)) if ord(char) not in cmap]
    if data[:4] != b"\0\1\0\0" or not {"glyf", "loca"}.issubset(font.keys()):
        raise ValueError("Output is not a genuine TrueType TTF")
    if {"CFF ", "CFF2"} & set(font.keys()) or variable_tables or missing:
        raise ValueError(f"Static/glyph validation failed: {variable_tables}, {missing}")
    if set(cmap) != set(source_cmap) or font["maxp"].numGlyphs != source_glyph_count:
        raise ValueError("Unexpected glyph/codepoint subsetting")
    if font["OS/2"].usWeightClass != 400 or font["OS/2"].fsType != 0:
        raise ValueError("Unexpected weight or embedding restrictions")
    if names(font, {0, 7, 8, 9, 11, 13, 14}) != [
        record for record in source_names if record["name_id"] in {0, 7, 8, 9, 11, 13, 14}
    ]:
        raise ValueError("Upstream copyright/license attribution changed")

    copyright_values = list(dict.fromkeys(record["value"] for record in source_names if record["name_id"] == 0))
    notice = (
        "Warehouse Assist SC Regular - derived from official Noto Sans SC\n\n"
        + "\n".join(copyright_values)
        + "\n\nLicense: SIL Open Font License 1.1 (OFL-1.1).\n"
        "The complete, byte-identical upstream license is in OFL.txt.\n\n"
        "Modification: pinned the sole variable axis wght to 400 using fontTools;\n"
        "no glyph subsetting; renamed the derivative's internal family to Warehouse\n"
        "Assist SC and its PostScript name to WarehouseAssistSC-Regular.\n"
        "The filename NotoSansSC-Regular.ttf is an application compatibility alias,\n"
        "not a claim that the modified binary is an original upstream release.\n"
        "Original copyright, trademark, author and license name records are retained.\n"
        "No endorsement by Google, Adobe or the Noto project is claimed.\n\n"
        "Redistribute this font under OFL-1.1 with this notice and OFL.txt. Do not\n"
        "sell it by itself. OFL does not require PDFs created with it to be OFL.\n"
        "No proprietary OS font is used; runtime loading is local only.\n"
    )
    provenance = {
        "repository": REPOSITORY,
        "commit": COMMIT,
        "source_font": source_meta,
        "upstream_license": license_meta,
        "upstream_name_records": source_names,
        "transformation": {
            "python": platform.python_version(),
            "fonttools": fontTools.__version__,
            "entrypoint": "rebuild-font.py",
            "api": "fontTools.varLib.instancer.instantiateVariableFont",
            "arguments": {"axisLimits": {"wght": 400}, "inplace": False, "updateFontNames": True},
            "original_axes_min_default_max": axes,
            "glyph_subsetting": False,
            "internal_family": FAMILY,
            "postscript_name": POSTSCRIPT,
            "name_ids_replaced": sorted(renamed_ids),
            "head_modified_timestamp_policy": "Retain upstream timestamp; recalcTimestamp=False",
        },
        "output": {
            "path_relative_to_public": OUTPUT.relative_to(PUBLIC).as_posix(),
            "sha256": sha256(data),
            "bytes": len(data),
            "sfnt_signature_hex": data[:4].hex(),
            "outline_format": "TrueType glyf/loca; not CFF/CFF2",
            "weight": font["OS/2"].usWeightClass,
            "fsType": font["OS/2"].fsType,
            "glyphs": font["maxp"].numGlyphs,
            "mapped_codepoints": len(cmap),
            "variable_tables": variable_tables,
            "sample": SAMPLE,
            "sample_missing_codepoints": missing,
            "cmap_preserved": True,
            "all_tables_decompiled": True,
            "copyright_license_name_records_preserved": True,
        },
    }
    (NOTICES / "OFL.txt").write_bytes(license_data)
    (NOTICES / "NOTICE.txt").write_text(notice, encoding="utf-8", newline="\n")
    (NOTICES / "provenance.json").write_text(json.dumps(provenance, indent=2, ensure_ascii=True) + "\n", encoding="utf-8", newline="\n")
    font.close()
    print(json.dumps(provenance["output"], indent=2, ensure_ascii=True))
    print(f"Python: {sys.executable}")


if __name__ == "__main__":
    main()
