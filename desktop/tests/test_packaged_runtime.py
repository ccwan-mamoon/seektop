"""Catch missing Java modules and native libraries in the real packaged application."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--app-dir", type=Path, required=True)
args = parser.parse_args()
launcher = args.app_dir.resolve() / "SeekTop.exe"
with tempfile.TemporaryDirectory(prefix="seektop-runtime-") as temporary:
    report = Path(temporary) / "result.txt"
    subprocess.run([str(launcher), "--check-runtime", str(report)], check=True, timeout=60)
    result = report.read_text(encoding="utf-8")
    if not result.startswith("PASS:"):
        raise AssertionError(result)
    print(result.strip())
