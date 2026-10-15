"""Validate the real authenticated browser's PDF against its original package QR."""
import json
import subprocess
from pathlib import Path

import cv2
import fitz
import numpy as np

ROOT = Path(__file__).resolve().parents[2]
FOLDER = ROOT / "temp" / "reuse-verification" / "browser"
record = FOLDER / "result.json"
if not record.exists():
    record = FOLDER / "partial-result.json"
facts = json.loads(record.read_text(encoding="utf-8"))
document = fitz.open(FOLDER / "actual-bag-label.pdf")
assert len(document) == 1
page = document[0]
text = page.get_text()
assert facts["packageCode"] in text
assert "垛序" in text
assert "演示" in text
assert abs(page.rect.width * 25.4 / 72 - 100) < 0.1
assert abs(page.rect.height * 25.4 / 72 - 76) < 0.1
fonts = [font[3] for font in page.get_fonts()]
assert any("WarehouseAssistSC" in font or "NotoSansSC" in font for font in fonts)
pixmap = page.get_pixmap(matrix=fitz.Matrix(4, 4), alpha=False)
pixmap.save(FOLDER / "actual-bag-label-render.png")
pixels = np.frombuffer(pixmap.samples, dtype=np.uint8).reshape(pixmap.height, pixmap.width, 3)
value, points, _ = cv2.QRCodeDetector().detectAndDecode(pixels)
# Also use the ERP's actual local decoder; OpenCV detects but cannot decode this mask.
decoded = json.loads(subprocess.run(
    ["node", str(ROOT / "scripts/verification/Verify-RdAssistLabelQr.mjs")],
    cwd=ROOT, check=True, capture_output=True, encoding="utf-8"
).stdout)
assert decoded["decodedOriginalQr"] == facts["originalQrCode"]
report = {
    "result": "PASS", "pages": 1, "sizeMm": [100, 76], "fonts": fonts,
    "originalPackageCode": facts["packageCode"], "decodedOriginalQr": decoded["decodedOriginalQr"],
    "decoder": decoded["decoder"], "openCvDetected": points is not None,
    "openCvDecoded": bool(value),
    "chineseText": True, "render": "actual-bag-label-render.png"
}
(FOLDER / "pdf-result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(report, ensure_ascii=False))
