"""Explicit operator-only download of pinned public model artifacts, never called by the service."""
import hashlib
import json
from pathlib import Path
import urllib.request

BASE = Path(__file__).resolve().parent


def main():
    lock = json.loads((BASE / "models.lock.json").read_text(encoding="utf-8"))
    root = BASE / ".runtime" / "models"
    manifest = {"source": lock["source"], "license": lock["license"], "models": {}}
    for name, model in lock["models"].items():
        entry = {"revision": model["revision"], "files": {}}
        destination = root / name
        destination.mkdir(parents=True, exist_ok=True)
        for filename, expected in model["files"].items():
            url = f"{lock['source']}/{name}/resolve/{model['revision']}/{filename}"
            target = destination / filename
            if target.is_file():
                data = target.read_bytes()
            else:
                with urllib.request.urlopen(url, timeout=60) as response:
                    data = response.read(64 * 1024 * 1024 + 1)
            if len(data) > 64 * 1024 * 1024:
                raise RuntimeError("Model file limit")
            digest = hashlib.sha256(data).hexdigest()
            blob = hashlib.sha1(f"blob {len(data)}\0".encode() + data).hexdigest()
            if ("sha256" in expected and digest != expected["sha256"]) or ("gitBlob" in expected and blob != expected["gitBlob"]):
                raise RuntimeError(f"Model hash mismatch: {name}/{filename}")
            if filename == "README.md" and b"license: apache-2.0" not in data:
                raise RuntimeError("Model license declaration missing")
            target.write_bytes(data)
            entry["files"][filename] = {"sha256": digest, "bytes": len(data), "url": url}
            print(f"Verified {name}/{filename} {digest}")
        manifest["models"][name] = entry
    (root / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
