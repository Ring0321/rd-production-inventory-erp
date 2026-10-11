import json
from pathlib import Path

import cv2
import fitz
import numpy as np

root = Path(__file__).resolve().parents[2]
output = root / "temp" / "qr-production-task-verification"
fixture = json.loads((output / "fixture.json").read_text(encoding="utf-8"))
decoder = cv2.QRCodeDetector()
with fitz.open(output / "task-labels.pdf") as pdf:
    assert len(pdf) == 10, len(pdf)
    decoded_codes = []
    for page, code in zip(pdf, fixture["codes"]):
        text = page.get_text()
        assert text.count(code) == 1, (code, text)
        assert fixture["taskNo"] in text, (code, "missing task")
        assert fixture["otherCode"] not in text
        pixmap = page.get_pixmap(dpi=300, alpha=False)
        pixels = np.frombuffer(pixmap.samples, dtype=np.uint8).reshape(pixmap.height, pixmap.width, 3)
        decoded, _, _ = decoder.detectAndDecode(cv2.cvtColor(pixels, cv2.COLOR_RGB2BGR))
        assert decoded == f"https://id.tjldsic.com/pkg/{code}", (code, decoded)
        decoded_codes.append(code)
    pdf[0].get_pixmap(dpi=160, alpha=False).save(output / "task-label-rendered.png")
result = {"result": "PASS", "taskId": fixture["taskId"], "pages": 10,
          "printedQrDecoded": len(decoded_codes), "taskFieldsVerified": True,
          "foreignTaskExcluded": True, "physicalPrinting": False}
(output / "pdf-result.json").write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
print(json.dumps(result, ensure_ascii=False))
