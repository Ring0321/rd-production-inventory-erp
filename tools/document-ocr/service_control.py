"""Hidden, module-owned test-service lifecycle. Never stops an unrelated listener."""
import argparse
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import time
import urllib.request

BASE = Path(__file__).resolve().parent
STATE = BASE / ".runtime" / "service.json"


def owned_process():
    import psutil
    if not STATE.is_file():
        return None
    try:
        saved = json.loads(STATE.read_text(encoding="utf-8"))
        process = psutil.Process(saved["pid"])
        if (abs(process.create_time() - saved["created"]) > 0.01
                or Path(process.cwd()).resolve() != BASE
                or "document_ocr.server" not in process.cmdline()
                or Path(process.exe()).resolve() != Path(sys.executable).resolve()):
            raise RuntimeError("Refusing to control an unverified process")
        return process
    except (psutil.NoSuchProcess, ProcessLookupError):
        return None


def main():
    import psutil
    parser = argparse.ArgumentParser()
    parser.add_argument("action", choices=("start", "status", "stop"))
    parser.add_argument("--port", type=int, default=48110)
    args = parser.parse_args()
    if not 1024 <= args.port <= 65535:
        parser.error("Use an unprivileged loopback port")
    process = owned_process()
    if args.action == "stop":
        if process:
            for child in process.children(recursive=True):
                child.kill()
            process.kill()
            process.wait(timeout=5)
        STATE.unlink(missing_ok=True)
        print("Owned OCR service stopped; no unrelated process was touched")
        return
    if args.action == "start":
        if process:
            raise RuntimeError("Owned OCR service already running")
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", args.port))
        environment = {key: os.environ[key] for key in ("SYSTEMROOT", "WINDIR", "PATH", "TEMP", "TMP") if key in os.environ}
        environment["PYTHONUTF8"] = "1"
        environment["PYTHONNOUSERSITE"] = "1"
        runtime = BASE / ".runtime"
        runtime.mkdir(exist_ok=True)
        environment["USERPROFILE"] = str(runtime / "isolated-home")
        environment["HOME"] = environment["USERPROFILE"]
        with open(os.devnull, "wb") as sink:
            child = subprocess.Popen([sys.executable, "-m", "document_ocr.server", "--port", str(args.port)],
                                     cwd=BASE, env=environment, stdin=subprocess.DEVNULL, stdout=sink, stderr=sink,
                                     creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        process = psutil.Process(child.pid)
        STATE.write_text(json.dumps({"pid": child.pid, "created": process.create_time(), "port": args.port}), encoding="utf-8")
        time.sleep(1)
    saved = json.loads(STATE.read_text(encoding="utf-8")) if STATE.exists() else {"port": args.port}
    # Explicitly bypass ambient HTTP proxy configuration even for the status command.
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(f"http://127.0.0.1:{saved['port']}/status", timeout=5) as response:
        print(response.read(4096).decode("utf-8"))


if __name__ == "__main__":
    main()
