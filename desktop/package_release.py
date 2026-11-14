"""Create matching Windows installer, portable ZIP and Git source archive."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import zipfile
import tomllib
import os
import shutil
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument("--app-dir", required=True, type=Path)
parser.add_argument("--output-dir", required=True, type=Path)
parser.add_argument("--version")
parser.add_argument("--source-ref", default="HEAD")
parser.add_argument("--iscc", type=Path, help="Inno Setup 7 compiler (or set SEEKTOP_ISCC)")
parser.add_argument("--skip-installer", action="store_true", help="Local portable-only build; releases must include the installer")
args = parser.parse_args()
if args.version is None:
    args.version = tomllib.loads((Path(__file__).resolve().parents[1] / "gradle/libs.versions.toml").read_text(encoding="utf-8"))["versions"]["seektop"]
if not re.fullmatch(r"\d+\.\d+\.\d+", args.version):
    raise SystemExit("Version must use major.minor.patch")
repo = Path(__file__).resolve().parents[1]
compiler = args.iscc or os.environ.get("SEEKTOP_ISCC") or shutil.which("ISCC.exe")
if not args.skip_installer and (not compiler or not Path(compiler).is_file()):
    raise SystemExit("Inno Setup compiler required: run desktop/provision-installer.ps1 and pass --iscc (or use --skip-installer for local builds)")
application = args.app_dir.resolve()
if not (application / "app/resources/SeekTopUpdater.exe").is_file() or not (application / "SeekTop.exe").is_file() or not (application / "runtime/release").is_file():
    raise SystemExit("Expected a complete SeekTop Windows app-image")
config = application / "app/SeekTop.cfg"
if not config.is_file() or f"java-options=-Djpackage.app-version={args.version}" not in config.read_text(encoding="utf-8").splitlines():
    raise SystemExit("App-image version does not match release version")
args.output_dir.mkdir(parents=True, exist_ok=True)
output = args.output_dir.resolve()
commit = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "--verify", args.source_ref + "^{commit}"], text=True).strip()
source = output / f"SeekTop-{args.version}-source.zip"
subprocess.run(["git", "-C", str(repo), "archive", "--format=zip", "--prefix=SeekTop-source/", f"--output={source}", commit], check=True)
notes = (repo / "desktop/RELEASE_NOTES.md").read_text(encoding="utf-8")
instructions = "SeekTop · 寻桌\n\n完整解压此目录后双击 SeekTop.exe。请保留所有文件。\n无需额外安装 Java、Node.js 或 Python。\n\n" + notes
source_notice = f"Source repository: https://github.com/ccwan-mamoon/seektop\nSource commit: {commit}\nCorresponding source: {source.name}\nUpstream: https://github.com/5151561/nodyssey\nLicense: GPL-3.0; upstream copyright retained.\n"
binary = output / f"SeekTop-{args.version}-windows-x64.zip"
with zipfile.ZipFile(binary, "w", zipfile.ZIP_DEFLATED, compresslevel=4) as archive:
    for file in sorted(application.rglob("*")):
        if file.is_file():
            relative = file.relative_to(application)
            if relative.as_posix() in {
                "app/resources/browser-runtime/NodysseyBrowser.exe",
                "app/resources/browser-runtime/electron.exe",
            }:
                continue
            archive.write(file, Path("SeekTop") / relative)
    archive.writestr("SeekTop/LICENSE-Nodyssey.txt", (repo / "LICENSE").read_bytes())
    archive.writestr("SeekTop/NOTICE.md", (repo / "NOTICE.md").read_bytes())
    archive.writestr("SeekTop/使用说明.txt", instructions.encode("utf-8"))
    archive.writestr("SeekTop/SOURCE.txt", source_notice)
artifacts = [binary, source]
if not args.skip_installer:
    installer = output / f"SeekTop-{args.version}-windows-x64-setup.exe"
    installer.unlink(missing_ok=True)
    with tempfile.TemporaryDirectory(prefix="seektop-installer-") as directory:
        metadata = Path(directory)
        (metadata / "SOURCE.txt").write_text(source_notice, encoding="utf-8")
        subprocess.run([
            str(Path(compiler).resolve()),
            f"--define=AppVersion={args.version}", f"--define=AppImage={application}",
            f"--define=RepoRoot={repo}", f"--define=MetadataDir={metadata}", f"--define=ReleaseDir={output}",
            str(repo / "desktop/installer/SeekTop.iss"),
        ], check=True)
    if not installer.is_file():
        raise SystemExit("Inno Setup did not produce a Windows installer")
    with installer.open("rb") as stream:
        if stream.read(2) != b"MZ":
            raise SystemExit("Inno Setup output is not a Windows executable")
    artifacts.append(installer)
checksums = []
for file in artifacts:
    digest = hashlib.sha256()
    with file.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    checksums.append(f"{digest.hexdigest()}  {file.name}")
    print(f"Created {file.name} ({file.stat().st_size} bytes)", flush=True)
(output / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")
(output / "RELEASE_NOTES.md").write_text(notes, encoding="utf-8")
