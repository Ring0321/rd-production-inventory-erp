import http.client
import json
import os
import sys
from pathlib import Path
import time
import unittest

from synthetic_document import synthetic_document


@unittest.skipUnless(os.environ.get("RUN_REAL_OCR") == "1" or "--real" in sys.argv, "Opt-in real Paddle CPU test; not a mock")
class RealInferenceTest(unittest.TestCase):
    def test_synthetic_jpeg_and_pdf_through_actual_model(self):
        matrix = []
        for kind in ("JPEG", "PDF"):
            with self.subTest(kind=kind):
                connection = http.client.HTTPConnection("127.0.0.1", 48110, timeout=75)
                start = time.monotonic()
                connection.request("POST", "/recognize", synthetic_document(kind), {"Content-Type": "application/octet-stream"})
                response = connection.getresponse()
                result = json.loads(response.read())
                connection.close()
                self.assertEqual(200, response.status, result.get("error", "non-success recognition response"))
                self.assertEqual("PaddleOCR", result["engine"])
                self.assertEqual("cpu", result["device"])
                self.assertEqual(1, result["pages"])
                self.assertGreaterEqual(len(result["rawLines"]), 5)
                self.assertIn("000042", [candidate["value"] for candidate in result["candidates"]["order"]["candidates"]])
                matrix.append({"format": kind, "engine": result["engine"], "device": result["device"], "pages": result["pages"],
                               "lines": len(result["rawLines"]), "elapsedSeconds": round(time.monotonic() - start, 3),
                               "candidateStates": {key: field["state"] for key, field in result["candidates"].items()}})
        root = Path(__file__).resolve().parents[1] / ".runtime"
        (root / "real-model-formats.json").write_text(json.dumps(matrix, indent=2), encoding="utf-8")

    def test_synthetic_chinese_png_through_loopback_service(self):
        body = synthetic_document()
        connection = http.client.HTTPConnection("127.0.0.1", 48110, timeout=75)
        start = time.monotonic()
        connection.request("POST", "/recognize", body, {"Content-Type": "application/octet-stream"})
        response = connection.getresponse()
        result = json.loads(response.read())
        connection.close()
        self.assertEqual(200, response.status, result.get("error", "non-success recognition response"))
        self.assertEqual("PaddleOCR", result["engine"])
        self.assertEqual("cpu", result["device"])
        self.assertGreaterEqual(len(result["rawLines"]), 6)
        for field, expected in [("order", "000042"), ("bagCount", "12"), ("weightKg", "1250")]:
            self.assertIn(expected, [candidate["value"] for candidate in result["candidates"][field]["candidates"]])
        self.assertTrue(result["candidates"]["customer"]["candidates"])
        self.assertTrue(result["candidates"]["model"]["candidates"])
        root = Path(__file__).resolve().parents[1] / ".runtime"
        root.mkdir(exist_ok=True)
        (root / "synthetic-pickup.png").write_bytes(body)
        (root / "real-inference.json").write_text(json.dumps({"elapsedSeconds": round(time.monotonic() - start, 3),
                                                                  "fixture": "synthetic printed Chinese only", "result": result}, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    if "--real" in sys.argv:
        sys.argv.remove("--real")
    unittest.main()
