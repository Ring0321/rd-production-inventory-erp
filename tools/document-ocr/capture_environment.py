"""Generate exact tested package pins and license metadata from this isolated venv."""
import importlib.metadata as metadata
import json
from pathlib import Path
import platform
import re
import sys

BASE = Path(__file__).resolve().parent


def main():
    if Path(sys.prefix).resolve() != (BASE / ".venv").resolve():
        raise RuntimeError("Run only with this module's .venv interpreter")
    distributions = sorted(metadata.distributions(), key=lambda distribution: distribution.metadata["Name"].lower())
    pins = []
    inventory = []
    for distribution in distributions:
        name = distribution.metadata["Name"]
        if name.lower() == "pip":
            continue
        version = distribution.version
        if not re.fullmatch(r"[A-Za-z0-9_.-]+", name) or not re.fullmatch(r"[A-Za-z0-9_.+!-]+", version):
            raise RuntimeError("Non-index package in isolated environment")
        pins.append(f"{name}=={version}")
        inventory.append({"name": name, "version": version,
                          "licenseExpression": distribution.metadata.get("License-Expression"),
                          "license": (distribution.metadata.get("License") or "")[:500],
                          "licenseClassifiers": [value for value in distribution.metadata.get_all("Classifier", []) if value.startswith("License ::")],
                          "licenseFiles": distribution.metadata.get_all("License-File", [])})
    (BASE / "requirements.lock.txt").write_text("# Generated from the isolated tested Python environment.\n" + "\n".join(pins) + "\n", encoding="utf-8")
    (BASE / "THIRD_PARTY.json").write_text(json.dumps({"python": platform.python_version(), "platform": platform.platform(),
                                                      "packages": inventory}, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
    print(f"Recorded {len(pins)} exact packages; retain all upstream wheel/native LICENSE and NOTICE files")


if __name__ == "__main__":
    main()
