import argparse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import socket
import threading
import time

from .files import MAX_BYTES, OcrError, inspect_magic
from .runner import LocalRunner


class BoundedServer(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 4

    def __init__(self, address, handler, runner):
        self.runner = runner
        self.connections = threading.BoundedSemaphore(4)
        super().__init__(address, handler)

    def process_request(self, request, client_address):
        if not self.connections.acquire(blocking=False):
            request.close()
            return
        try:
            super().process_request(request, client_address)
        except Exception:
            self.connections.release()
            raise

    def process_request_thread(self, request, client_address):
        try:
            super().process_request_thread(request, client_address)
        finally:
            self.connections.release()

    def handle_error(self, request, client_address):
        pass


class Handler(BaseHTTPRequestHandler):
    server_version = "LocalOCR"
    sys_version = ""
    timeout = 10

    def log_message(self, format, *args):
        pass

    def send_json(self, status, body):
        payload = json.dumps(body, ensure_ascii=False, allow_nan=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(payload)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(payload)
        self.close_connection = True

    def browser_request(self):
        return any(name.lower() == "origin" or name.lower().startswith("sec-fetch-") for name in self.headers)

    def do_GET(self):
        if self.browser_request():
            self.send_json(403, {"error": "OCR_FORBIDDEN"})
        elif self.path != "/status":
            self.send_json(404, {"error": "OCR_NOT_FOUND"})
        else:
            self.send_json(200, self.server.runner.status())

    def do_OPTIONS(self):
        self.send_json(405, {"error": "OCR_METHOD_NOT_ALLOWED"})

    def do_POST(self):
        try:
            if self.browser_request():
                raise OcrError("OCR_FORBIDDEN", 403)
            if self.path != "/recognize":
                raise OcrError("OCR_NOT_FOUND", 404)
            if self.headers.get("Content-Type") != "application/octet-stream":
                raise OcrError("OCR_INVALID_FILE", 415)
            lengths = self.headers.get_all("Content-Length", [])
            if len(lengths) != 1 or not lengths[0].isdigit() or self.headers.get("Transfer-Encoding"):
                raise OcrError("OCR_INVALID_REQUEST", 400)
            length = int(lengths[0])
            if not 1 <= length <= MAX_BYTES:
                raise OcrError("OCR_FILE_TOO_LARGE", 413)
            deadline = time.monotonic() + 10
            body = bytearray()
            while len(body) < length:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise OcrError("OCR_TIMEOUT", 408)
                self.connection.settimeout(remaining)
                chunk = self.rfile.read1(min(65536, length - len(body)))
                if not chunk:
                    raise OcrError("OCR_INVALID_REQUEST", 400)
                body.extend(chunk)
            inspect_magic(body)
            self.send_json(200, self.server.runner.recognize(bytes(body)))
        except OcrError as error:
            self.send_json(error.status, {"error": error.code})
        except (socket.timeout, TimeoutError):
            self.send_json(408, {"error": "OCR_TIMEOUT"})
        except (BrokenPipeError, ConnectionResetError):
            pass
        except Exception:
            self.send_json(503, {"error": "OCR_UNAVAILABLE"})


def create_server(port=48110, runner=None):
    return BoundedServer(("127.0.0.1", port), Handler, runner or LocalRunner())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=48110)
    args = parser.parse_args()
    server = create_server(args.port)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
