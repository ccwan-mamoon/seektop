"""Prepare the separately licensed Windows browser runtime for a desktop build."""
import argparse
import hashlib
from pathlib import Path
import shutil
import tomllib
import urllib.request
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--mirror", default="https://github.com/electron/electron/releases/download")
args = parser.parse_args()
desktop = Path(__file__).resolve().parent
version = tomllib.loads((desktop.parent / "gradle/libs.versions.toml").read_text(encoding="utf-8"))["versions"]["electronBrowser"]
prefix = "v" if "github.com" in args.mirror else ""
base = f"{args.mirror.rstrip('/')}/{prefix}{version}/"
name = f"electron-v{version}-win32-x64.zip"
with urllib.request.urlopen(base + "SHASUMS256.txt", timeout=30) as response:
    sums = response.read().decode()
expected = next(line.split()[0] for line in sums.splitlines() if line.split()[-1].lstrip("*") == name)
downloads = desktop / "build/browser-download"
downloads.mkdir(parents=True, exist_ok=True)
archive = downloads / name
if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
    with urllib.request.urlopen(base + name, timeout=60) as response, archive.open("wb") as output:
        shutil.copyfileobj(response, output)
if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
    raise RuntimeError("Browser archive checksum mismatch")
destination = desktop / "resources/windows/browser-runtime"
with zipfile.ZipFile(archive) as files:
    files.extractall(destination)
(destination / "electron.exe").replace(destination / "SeekTopBrowser.exe")
shutil.copytree(desktop / "browser", destination / "resources/app", dirs_exist_ok=True)
shutil.copy2(desktop / "icons/seektop-1024.png", destination / "resources/app/seektop.png")
print(f"Prepared verified Electron {version} runtime")
