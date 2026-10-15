"""Compile only this increment against an existing build's classpath; no Maven or DB.

This is a supplemental isolated check, not the parent's reactor integration gate.
"""
import argparse
from pathlib import Path
import subprocess
import sys
import xml.etree.ElementTree as ET

BASE = Path(__file__).resolve().parent
REPO = BASE.parents[1]
sys.stdout.reconfigure(encoding="utf-8", errors="replace")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--classpath-report", type=Path, required=True)
    parser.add_argument("--launcher", type=Path, required=True)
    parser.add_argument("--real", action="store_true", help="Opt-in fixed loopback real-model test with the synthetic fixture")
    args = parser.parse_args()
    root = ET.parse(args.classpath_report).getroot()
    classpath_property = root.find("./properties/property[@name='java.class.path']")
    classpath = classpath_property.get("value") + ";" + str(args.launcher.resolve())
    paths = classpath.split(";")
    print("JUnit artifacts: " + ", ".join(Path(p).name for p in paths if "junit" in Path(p).name))
    destination = BASE / ".runtime" / "java-classes"
    destination.mkdir(parents=True, exist_ok=True)
    source = REPO / "backend" / "rd-erp-inventory" / "src"
    sources = []
    for area in ("main", "test"):
        for package in ("service/documentocr", "controller/admin/documentocr"):
            directory = source / area / "java" / "com/ruiding/erp" / package
            if directory.is_dir():
                sources.extend(directory.glob("*.java"))
    sources.append(BASE / "tests" / "OcrJUnitRunner.java")
    arguments = ["--release", "17", "-encoding", "UTF-8", "-parameters", "-proc:none", "-classpath", classpath,
                 "-d", str(destination), *map(str, sources)]
    argfile = BASE / ".runtime" / "javac.args"
    argfile.write_text("\n".join('"' + value.replace("\\", "/") + '"' for value in arguments), encoding="utf-8")
    subprocess.run(["rtk", "proxy", "javac", "@" + str(argfile)], check=True)
    real = ["-Drd.ocr.real=true", "-Drd.ocr.synthetic=" + str(BASE / ".runtime" / "synthetic-pickup.png")] if args.real else []
    run = subprocess.run(["rtk", "proxy", "java", "-Dfile.encoding=UTF-8", "-Dnet.bytebuddy.experimental=true", *real, "-cp", str(destination) + ";" + classpath,
                          "OcrJUnitRunner"], capture_output=True, encoding="utf-8", errors="replace")
    (BASE / ".runtime" / "java-tests.log").write_text(run.stdout + run.stderr, encoding="utf-8")
    print((run.stdout + run.stderr)[-7000:])
    raise SystemExit(run.returncode)


if __name__ == "__main__":
    main()
