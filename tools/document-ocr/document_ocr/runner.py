import hashlib
import importlib.util
import json
import multiprocessing
from pathlib import Path
import threading

from .files import OcrError, inspect_magic

MAX_RESPONSE = 1024 * 1024
DEFAULT_ROOT = Path(__file__).resolve().parents[1] / ".runtime" / "models"
MODEL_NAMES = ("PP-OCRv5_mobile_det", "PP-OCRv5_mobile_rec")
MODEL_FILES = ("inference.json", "inference.pdiparams", "inference.yml")


def verify_models(root):
    try:
        manifest = json.loads((Path(__file__).resolve().parents[1] / "models.lock.json").read_text(encoding="utf-8"))
        for name in MODEL_NAMES:
            entry = manifest["models"][name]
            for filename in MODEL_FILES:
                file = root / name / filename
                if not file.is_file() or file.is_symlink() or file.stat().st_size > 64 * 1024 * 1024:
                    return False
                if hashlib.sha256(file.read_bytes()).hexdigest() != entry["files"][filename]["sha256"]:
                    return False
        return True
    except (OSError, KeyError, TypeError, ValueError):
        return False


class LocalRunner:
    def __init__(self, root=DEFAULT_ROOT, timeout=60):
        self.root = Path(root).resolve()
        self.timeout = timeout
        self.slot = threading.BoundedSemaphore(1)
        # No import of Paddle/PaddleX and no network or inference on GET /status.
        dependencies = all(importlib.util.find_spec(name) for name in ("paddle", "paddleocr", "PIL", "pypdfium2"))
        self.ready = dependencies and verify_models(self.root)
        self.state = "LOCAL_FILES_READY" if self.ready else "MODELS_OR_RUNTIME_MISSING"

    def status(self):
        return {"available": bool(self.ready), "state": self.state, "device": "cpu",
                "engine": "PaddleOCR", "model": "PP-OCRv5_mobile", "maxBytes": 10485760,
                "maxPages": 3, "timeoutSeconds": self.timeout, "concurrency": 1}

    def recognize(self, data):
        inspect_magic(data)
        if not self.ready:
            raise OcrError("OCR_UNAVAILABLE", 503)
        if not self.slot.acquire(blocking=False):
            raise OcrError("OCR_BUSY", 429)
        process = None
        parent = child = None
        try:
            from .worker import run_job
            context = multiprocessing.get_context("spawn")
            parent, child = context.Pipe(duplex=False)
            process = context.Process(target=run_job, args=(child, data, str(self.root)), daemon=True)
            process.start()
            child.close()
            if not parent.poll(self.timeout):
                raise OcrError("OCR_TIMEOUT", 504)
            try:
                result = json.loads(parent.recv_bytes(MAX_RESPONSE))
            except (EOFError, OSError, ValueError):
                raise OcrError("OCR_INFERENCE_FAILED", 503) from None
            if "error" in result:
                safe = {"OCR_INVALID_FILE": 415, "OCR_PIXEL_LIMIT": 422, "OCR_PAGE_LIMIT": 422,
                        "OCR_OUTPUT_LIMIT": 422, "OCR_UNAVAILABLE": 503}
                code = result["error"] if result["error"] in safe else "OCR_INFERENCE_FAILED"
                if code in ("OCR_INFERENCE_FAILED", "OCR_UNAVAILABLE"):
                    self.ready = False
                    self.state = "INFERENCE_FAILED"
                raise OcrError(code, safe.get(code, 503))
            return result
        finally:
            if process is not None and process.pid:
                if process.is_alive():
                    process.kill()
                process.join(timeout=3)
                process.close()
            if parent is not None:
                parent.close()
            if child is not None:
                child.close()
            self.slot.release()
