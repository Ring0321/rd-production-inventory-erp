"""Operator-only synthetic inference diagnosis: no document text or exception messages printed."""
import json
import multiprocessing
from pathlib import Path
import sys

from document_ocr.runner import DEFAULT_ROOT
from document_ocr.worker import run_job


def main():
    sys.path.insert(0, "tests")
    from synthetic_document import synthetic_document
    context = multiprocessing.get_context("spawn")
    parent, child = context.Pipe(duplex=False)
    process = context.Process(target=run_job, args=(child, synthetic_document(), str(DEFAULT_ROOT)))
    process.start()
    child.close()
    try:
        if not parent.poll(60):
            raise RuntimeError("Synthetic diagnosis timed out")
        result = json.loads(parent.recv_bytes(1024 * 1024))
        if "error" in result:
            print(json.dumps(result, ensure_ascii=True))
            return 1
        target = Path(__file__).resolve().parent / ".runtime" / "synthetic-diagnostic-result.json"
        target.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps({"engine": result["engine"], "device": result["device"], "lines": len(result["rawLines"])}))
        return 0
    finally:
        if process.is_alive():
            process.kill()
        process.join()
        process.close()
        parent.close()


if __name__ == "__main__":
    raise SystemExit(main())
