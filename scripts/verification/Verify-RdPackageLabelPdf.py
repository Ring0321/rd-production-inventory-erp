import json
from pathlib import Path

import cv2
import fitz
import numpy as np


root = Path(__file__).resolve().parents[2]
output = root / 'temp' / 'qr-label-verification'
expected = json.loads((output / 'expected.json').read_text(encoding='utf-8'))
decoder = cv2.QRCodeDetector()
for item in expected:
    image = cv2.imdecode(np.frombuffer((output / f"{item['packageCode']}.png").read_bytes(), dtype=np.uint8),
                        cv2.IMREAD_COLOR)
    decoded, _, _ = decoder.detectAndDecode(image)
    assert decoded == item['qrCode'], (item['packageCode'], decoded)

for paper, count in [('LABEL_100_76', 10), ('A4', 2)]:
    with fitz.open(output / f'{paper}.pdf') as pdf:
        assert len(pdf) == count, (paper, len(pdf))
        text = '\n'.join(page.get_text() for page in pdf)
        for item in expected:
            assert text.count(item['packageCode']) == 1, (paper, item['packageCode'])
        if paper == 'LABEL_100_76':
            for page, item in zip(pdf, expected):
                pixmap = page.get_pixmap(dpi=300, alpha=False)
                pixels = np.frombuffer(pixmap.samples, dtype=np.uint8).reshape(pixmap.height, pixmap.width, 3)
                decoded, _, _ = decoder.detectAndDecode(cv2.cvtColor(pixels, cv2.COLOR_RGB2BGR))
                assert decoded == item['qrCode'], ('printed QR', item['packageCode'], decoded)
            pdf[0].get_pixmap(dpi=160, alpha=False).save(output / 'printed-label.png')
        else:
            pdf[0].get_pixmap(dpi=140, alpha=False).save(output / 'printed-A4.png')

print(json.dumps({'result': 'PASS', 'rawQrDecoded': 10, 'printedQrDecoded': 10,
                  'labelPages': 10, 'a4Pages': 2, 'uniquePackageCodes': 10}))
