"""Verify release assets and hashes using a tiny app-image and the real installer compiler."""
import argparse
import hashlib
from pathlib import Path
import subprocess
import sys
import tempfile
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--iscc", required=True, type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="release-package-", dir=repo / "build") as temporary:
    root = Path(temporary)
    app = root / "application with spaces"
    files = {
        "SeekTop.exe": b"MZ fixture - not executable",
        "runtime/release": b'JAVA_VERSION="21"',
        "app/SeekTop.cfg": b"java-options=-Djpackage.app-version=9.9.9\n",
        "app/resources/SeekTopUpdater.exe": b"MZ updater fixture",
    }
    for relative, content in files.items():
        file = app / relative
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_bytes(content)
    legacy = app / "app/resources/browser-runtime"
    legacy.mkdir()
    (legacy / "electron.exe").write_bytes(b"obsolete helper")
    output = root / "release with spaces"
    command = [sys.executable, str(repo / "desktop/package_release.py"), "--app-dir", str(app), "--output-dir", str(output), "--version", "9.9.9", "--iscc", str(args.iscc.resolve())]
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL)
    expected = {"SeekTop-9.9.9-windows-x64.zip", "SeekTop-9.9.9-source.zip", "SeekTop-9.9.9-windows-x64-setup.exe"}
    rows = {}
    for row in (output / "SHA256SUMS.txt").read_text(encoding="utf-8").splitlines():
        digest, name = row.split("  ", 1)
        rows[name] = digest
        assert hashlib.sha256((output / name).read_bytes()).hexdigest() == digest
    assert set(rows) == expected
    with zipfile.ZipFile(output / "SeekTop-9.9.9-windows-x64.zip") as archive:
        for relative, content in files.items():
            assert archive.read("SeekTop/" + relative) == content
        assert "Source commit:" in archive.read("SeekTop/SOURCE.txt").decode()
        assert "SeekTop/NOTICE.md" in archive.namelist()
        assert "SeekTop/app/resources/browser-runtime/electron.exe" not in archive.namelist()
    portable = root / "portable"
    subprocess.run(command[:command.index("--output-dir") + 1] + [str(portable), "--version", "9.9.9", "--skip-installer"], check=True, stdout=subprocess.DEVNULL)
    assert not list(portable.glob("*-setup.exe"))
    assert len((portable / "SHA256SUMS.txt").read_text().splitlines()) == 2
    invalid = root / "invalid"
    rejected = subprocess.run([sys.executable, str(repo / "desktop/package_release.py"), "--app-dir", str(app), "--output-dir", str(invalid), "--version", "9.9.8", "--iscc", str(args.iscc.resolve())], capture_output=True, text=True)
    assert rejected.returncode != 0 and "version does not match" in rejected.stderr
    assert not invalid.exists()
print("PASS: installer + portable + source checksums, space-containing paths, portable opt-out and version mismatch refusal")
