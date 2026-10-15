import json
from pathlib import Path

import cv2
import fitz
import numpy as np

folder = Path(__file__).parent / "artifacts"
results = []
for filename, code, customer in [
    ("first-print.pdf", "LOCAL-ORIGINAL-QR-SYNTH-0007", "合成客户甲"),
    ("reprint.pdf", "LOCAL-ORIGINAL-QR-SYNTH-0008", "合成客户乙"),
]:
    document = fitz.open(folder / filename)
    assert len(document) == 1
    page = document[0]
    text = page.get_text()
    assert customer in text, text
    assert "中层" in text or "上层" in text, text
    assert abs(page.rect.width * 25.4 / 72 - 100) < 0.1
    assert abs(page.rect.height * 25.4 / 72 - 76) < 0.1
    fonts = [font[3] for font in page.get_fonts()]
    assert any("WarehouseAssistSC" in font or "NotoSansSC" in font for font in fonts), fonts
    pixmap = page.get_pixmap(matrix=fitz.Matrix(4, 4), alpha=False)
    pixmap.save(folder / (filename + ".png"))
    image = np.frombuffer(pixmap.samples, dtype=np.uint8).reshape(pixmap.height, pixmap.width, 3)
    decoded, points, _ = cv2.QRCodeDetector().detectAndDecode(image)
    assert decoded == code, (decoded, points)
    results.append({"file": filename, "pages": 1, "sizeMm": [100, 76], "chinese": customer, "fonts": fonts, "qr": decoded})
(folder / "pdf-results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(results, ensure_ascii=False, indent=2))
