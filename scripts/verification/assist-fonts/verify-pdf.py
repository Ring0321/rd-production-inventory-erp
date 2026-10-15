"""Independently inspect and render the pdfme font smoke test, not production UI."""

import hashlib
import io
import json
from pathlib import Path

import fitz
from PIL import Image
from fontTools.ttLib import TTFont
from fontTools.ttLib.sfnt import calcChecksum


HERE = Path(__file__).resolve().parents[3] / "temp" / "reuse-font-verification"
report = json.loads((HERE / "pdfme-test.json").read_text(encoding="utf-8"))
pdf_path = HERE / "pdfme-local-cjk-smoke.pdf"
document = fitz.open(pdf_path)
assert len(document) == report["expected_pages"] == 2
pages = []
font_records = []
checked_fonts = set()

for index, page in enumerate(document):
    extracted = page.get_text()
    normalized = "".join(extracted.split())
    for value in report["expected_inputs"][index].values():
        assert "".join(value.split()) in normalized, (index + 1, value, extracted)
    assert "\ufffd" not in extracted
    pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), alpha=False)
    image = Image.frombytes("RGB", (pixmap.width, pixmap.height), pixmap.samples)
    dark_pixels = sum(image.convert("L").histogram()[:200])
    assert dark_pixels > 1000
    image_path = HERE / f"pdfme-local-cjk-smoke-page-{index + 1}.png"
    image.save(image_path)
    for info in page.get_fonts(full=True):
        xref, extension, kind, name, resource, encoding, *_ = info
        assert extension == "ttf" and encoding == "Identity-H", info
        if xref in checked_fonts:
            continue
        checked_fonts.add(xref)
        base_name, font_extension, font_type, data = document.extract_font(xref)
        # fontkit's PDF-only subset omits table checksums; the shipped full TTF
        # is separately validated with checkChecksums=2 by rebuild-font.py.
        embedded = TTFont(io.BytesIO(data), checkChecksums=0)
        embedded.ensureDecompiled()
        assert data[:4] in {b"\0\1\0\0", b"true"}
        assert "glyf" in embedded and "loca" in embedded
        assert not {"CFF ", "CFF2", "fvar", "gvar"}.intersection(embedded.keys())
        assert "WarehouseAssistSC-Regular" in base_name
        omitted_checksums = []
        for tag, entry in embedded.reader.tables.items():
            embedded.reader.file.seek(entry.offset)
            table_data = embedded.reader.file.read(entry.length)
            if tag == "head":
                table_data = table_data[:8] + b"\0\0\0\0" + table_data[12:]
            checksum = calcChecksum(table_data)
            if entry.checkSum != checksum:
                assert entry.checkSum == 0, (tag, entry.checkSum, checksum)
                omitted_checksums.append(tag)
        font_records.append({
            "xref": xref,
            "name": base_name,
            "extension": font_extension,
            "type": font_type,
            "encoding": encoding,
            "bytes": len(data),
            "sfnt_signature_hex": data[:4].hex(),
            "glyphs": embedded["maxp"].numGlyphs,
            "sha256": hashlib.sha256(data).hexdigest(),
            "true_type_static": True,
            "pdf_subset_zero_checksum_tables": omitted_checksums,
            "checksum_note": "fontkit PDF-only subset; not a standalone font. The shipped full TTF passed strict checksums.",
        })
        embedded.close()
    pages.append({
        "page": index + 1,
        "all_expected_text_extracted": True,
        "extracted_text": extracted,
        "render_dimensions": [pixmap.width, pixmap.height],
        "dark_pixels": dark_pixels,
        "render_file": image_path.name,
    })

assert font_records
result = {
    "pymupdf": fitz.VersionBind,
    "pdf_bytes": pdf_path.stat().st_size,
    "pdf_sha256": hashlib.sha256(pdf_path.read_bytes()).hexdigest(),
    "pages": pages,
    "embedded_fonts": font_records,
    "network_not_required": True,
    "scope": "Asset smoke test only; production label UI/PDF remains parent-owned",
}
assert result["pdf_sha256"] == report["pdf_sha256"]
(HERE / "pdf-independent-validation.json").write_text(
    json.dumps(result, indent=2, ensure_ascii=True) + "\n", encoding="utf-8", newline="\n"
)
document.close()
print(json.dumps(result, indent=2, ensure_ascii=True))
