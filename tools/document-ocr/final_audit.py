"""Audit the exclusive new-file write set and generate its recoverable source manifest."""
import hashlib
from pathlib import Path
import subprocess

BASE = Path(__file__).resolve().parent
REPO = BASE.parents[1]
PREFIXES = (
    "tools/document-ocr/", "frontend/src/views/rd/assist/ocr/", "frontend/src/api/rd/documentocr/",
    "backend/rd-erp-inventory/src/main/java/com/ruiding/erp/controller/admin/documentocr/",
    "backend/rd-erp-inventory/src/main/java/com/ruiding/erp/service/documentocr/",
    "backend/rd-erp-inventory/src/test/java/com/ruiding/erp/service/documentocr/",
)


def main():
    run = subprocess.run(["rtk", "proxy", "git", "status", "--porcelain=v1", "-z", "--untracked-files=all"],
                         cwd=REPO, check=True, capture_output=True)
    entries = [entry for entry in run.stdout.decode("utf-8").split("\0") if entry]
    manifest = []
    for entry in entries:
        state, name = entry[:2], entry[3:]
        if state != "??" or not name.startswith(PREFIXES):
            raise RuntimeError("Write-set audit failed; inspect git status without reverting other work")
        if name.endswith("/SOURCE_MANIFEST.sha256"):
            continue
        file = REPO / name
        if not file.is_file() or file.is_symlink():
            raise RuntimeError("Only regular new files may be handed off")
        manifest.append(f"{hashlib.sha256(file.read_bytes()).hexdigest()}  {name}")
    (BASE / "SOURCE_MANIFEST.sha256").write_text("\n".join(sorted(manifest)) + "\n", encoding="utf-8")
    print(f"PASS: {len(manifest)} new scoped files; no tracked baseline file changed")


if __name__ == "__main__":
    main()
