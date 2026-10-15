import json
import os
from pathlib import Path
import socket
import traceback

from .files import OcrError, decode_pages
from .parser import MAX_LINES, extract_candidates, normalize_results


def _offline(*args, **kwargs):
    raise OSError("OCR worker network disabled")


def run_job(connection, data, root):
    # This is a disposable child. Suppress native library output too, not just Python logging.
    with open(os.devnull, "w") as sink:
        os.dup2(sink.fileno(), 1)
        os.dup2(sink.fileno(), 2)
    os.environ["PADDLE_PDX_MODEL_SOURCE"] = "BOS"
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["MODELSCOPE_OFFLINE"] = "1"
    os.environ["PADDLE_PDX_CACHE_HOME"] = str(Path(root).parent / "paddlex-cache")
    for name in ("HOME", "USERPROFILE", "HF_HOME", "MODELSCOPE_CACHE", "PADDLE_HOME"):
        os.environ[name] = str(Path(root).parent / "isolated-home")
    home = Path(root).parent / "isolated-home"
    home.mkdir(parents=True, exist_ok=True)
    temporary = Path(root).parent / "tmp"
    temporary.mkdir(exist_ok=True)
    os.environ["TEMP"] = os.environ["TMP"] = str(temporary)
    import tempfile
    tempfile.tempdir = str(temporary)
    os.environ["OMP_NUM_THREADS"] = "2"
    # Disable Python-level downloads/telemetry as a second boundary. Production should also deny egress.
    socket.socket.connect = _offline
    socket.socket.connect_ex = _offline
    socket.create_connection = _offline
    phase = "DECODE"
    try:
        pages = decode_pages(data)
        phase = "IMPORT"
        from paddleocr import PaddleOCR
        phase = "MODEL_LOAD"
        # Paddle's Windows native loader cannot open this checkout's Unicode absolute model paths.
        # Python changes the disposable child's cwd using the Unicode API; native model paths stay ASCII.
        os.chdir(root)
        engine = PaddleOCR(
            text_detection_model_name="PP-OCRv5_mobile_det",
            text_detection_model_dir="PP-OCRv5_mobile_det",
            text_recognition_model_name="PP-OCRv5_mobile_rec",
            text_recognition_model_dir="PP-OCRv5_mobile_rec",
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
            device="cpu",
            cpu_threads=2,
            enable_mkldnn=False,
            text_det_limit_side_len=1600,
            text_det_limit_type="max",
        )
        lines = []
        phase = "PREDICT"
        for page, image in enumerate(pages, 1):
            lines.extend(normalize_results(engine.predict(image), page))
            if len(lines) > MAX_LINES:
                raise OcrError("OCR_OUTPUT_LIMIT")
        result = {"schemaVersion": 1, "engine": "PaddleOCR", "model": "PP-OCRv5_mobile",
                  "device": "cpu", "pages": len(pages), "rawLines": lines,
                  "candidates": extract_candidates(lines)}
    except OcrError as error:
        result = {"error": error.code}
    except Exception as error:
        # HTTP discards this operator metadata; document-aware prediction errors never include exception text.
        result = {"error": "OCR_INFERENCE_FAILED", "failureStage": phase, "failureType": type(error).__name__,
                  "frames": [{"file": Path(frame.filename).name, "line": frame.lineno, "function": frame.name}
                             for frame in traceback.extract_tb(error.__traceback__)[-6:]]}
        if isinstance(error, ModuleNotFoundError):
            result["missingModule"] = error.name
        if phase == "MODEL_LOAD":
            # No document has been passed to the model yet; operator diagnosis may show this fixed-model failure.
            result["modelLoadMessage"] = str(error).replace(root, "<fixed-local-model-root>")[:1200]
    try:
        payload = json.dumps(result, ensure_ascii=False, allow_nan=False).encode("utf-8")
        connection.send_bytes(payload if len(payload) <= 1024 * 1024 else b'{"error":"OCR_OUTPUT_LIMIT"}')
    finally:
        connection.close()
