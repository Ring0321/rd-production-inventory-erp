"""Exercise real PNG/JPEG/PDF inference using non-sensitive synthetic documents."""
import http.client
import json
from pathlib import Path
import time

BASE = Path('/srv/apps/duoxu-erp-demo')


def main():
    evidence = []
    for extension in ['png','jpeg','pdf']:
        data = (BASE / 'data/ocr/fixtures' / ('synthetic-pickup.' + extension)).read_bytes()
        start = time.monotonic()
        connection = http.client.HTTPConnection('127.0.0.1',48110,timeout=75)
        connection.request('POST','/recognize',data,{'Content-Type':'application/octet-stream'})
        response = connection.getresponse()
        value = json.loads(response.read())
        connection.close()
        assert response.status == 200, str(value)
        assert value['engine'] == 'PaddleOCR' and value['device'] == 'cpu'
        assert len(value['rawLines']) >= 6
        for field, expected in [('order','000042'),('bagCount','12'),('weightKg','1250')]:
            assert expected in [item['value'] for item in value['candidates'][field]['candidates']]
        evidence.append({'format':extension,'engine':value['engine'],'lines':len(value['rawLines']),'elapsedSeconds':round(time.monotonic()-start,3),'syntheticOnly':True})
    (BASE / 'logs/ocr-cloud-verification.json').write_text(json.dumps(evidence,indent=2))
    print(json.dumps(evidence),flush=True)


if __name__ == '__main__':
    main()
