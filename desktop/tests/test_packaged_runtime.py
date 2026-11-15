"""Catch missing Java modules and native libraries in the real packaged application."""
import argparse
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--app-dir", type=Path, required=True)
args = parser.parse_args()
launcher = args.app_dir.resolve() / "SeekTop.exe"
desktop = Path(__file__).resolve().parents[1]
resources = args.app_dir.resolve() / "app/resources"
# Compose app-image caching once shipped stale helpers after prepareAppResources changed.
# Verify actual payloads so a Java-only runtime smoke cannot hide an outdated browser or native tool.
for source in (desktop / "browser").glob("*.cjs"):
    target = resources / "browser-runtime/resources/app" / source.name
    assert target.read_bytes() == source.read_bytes(), "Stale packaged browser helper: " + source.name
for name in ("SeekTopUpdater.exe", "SeekTopLinkRouter.exe"):
    assert (resources / name).read_bytes() == (desktop / "resources/windows" / name).read_bytes(), "Stale packaged native helper: " + name
print("PASS: packaged browser and native helpers match provisioned source artifacts")
with tempfile.TemporaryDirectory(prefix="seektop-runtime-") as temporary:
    report = Path(temporary) / "result.txt"
    subprocess.run([str(launcher), "--check-runtime", str(report)], check=True, timeout=60)
    result = report.read_text(encoding="utf-8")
    if not result.startswith("PASS:"):
        raise AssertionError(result)
    print(result.strip())
