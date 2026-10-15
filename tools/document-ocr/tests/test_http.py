import http.client
import threading
import unittest

from document_ocr.server import create_server
from document_ocr.files import OcrError


class FakeRunner:
    def __init__(self):
        self.calls = 0

    def status(self):
        return {"available": False, "state": "MODELS_MISSING", "device": "cpu"}

    def recognize(self, data):
        self.calls += 1
        raise OcrError("OCR_UNAVAILABLE", 503)


class HttpBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.runner = FakeRunner()
        self.server = create_server(0, self.runner)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self):
        self.server.shutdown()
        self.server.server_close()
        self.thread.join()

    def request(self, method, path, body=None, headers=None):
        client = http.client.HTTPConnection("127.0.0.1", self.server.server_port, timeout=3)
        client.request(method, path, body, headers or {})
        response = client.getresponse()
        result = response.status, response.read(), dict(response.getheaders())
        client.close()
        return result

    def test_status_does_not_load_or_run_model_and_is_not_cors_enabled(self):
        status, body, headers = self.request("GET", "/status")
        self.assertEqual(200, status)
        self.assertIn(b'MODELS_MISSING', body)
        self.assertEqual(0, self.runner.calls)
        self.assertNotIn("Access-Control-Allow-Origin", headers)

    def test_browser_url_path_and_oversize_requests_are_refused(self):
        for method, path, headers, expected in [
            ("POST", "/recognize", {"Origin": "http://localhost", "Content-Type": "application/octet-stream"}, 403),
            ("POST", "/recognize", {"Content-Type": "text/plain"}, 415),
            ("POST", "/recognize?url=http://evil", {"Content-Type": "application/octet-stream"}, 404),
            ("POST", "/recognize", {"Content-Type": "application/octet-stream", "Content-Length": "10485761"}, 413),
            ("OPTIONS", "/recognize", {}, 405),
        ]:
            with self.subTest(path=path, headers=headers):
                self.assertEqual(expected, self.request(method, path, b"bad", headers)[0])
        self.assertEqual(0, self.runner.calls)

    def test_invalid_file_and_unavailable_are_safe_error_codes(self):
        self.assertEqual(415, self.request("POST", "/recognize", b"MZbad", {"Content-Type": "application/octet-stream"})[0])
        status, body, _ = self.request("POST", "/recognize", b"%PDF-1.7\n", {"Content-Type": "application/octet-stream"})
        self.assertEqual(503, status)
        self.assertIn(b"OCR_UNAVAILABLE", body)


if __name__ == "__main__":
    unittest.main()
