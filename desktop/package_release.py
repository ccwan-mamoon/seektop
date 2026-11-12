"""Create a Windows application ZIP and matching Git source archive."""
import argparse
import hashlib
from pathlib import Path
import re
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("--app-dir", required=True, type=Path)
parser.add_argument("--output-dir", required=True, type=Path)
parser.add_argument("--version", default="0.3.0")
parser.add_argument("--source-ref", default="HEAD")
args = parser.parse_args()
if not re.fullmatch(r"\d+\.\d+\.\d+", args.version):
    raise SystemExit("Version must use major.minor.patch")
repo = Path(__file__).resolve().parents[1]
application = args.app_dir.resolve()
if not (application / "SeekTop.exe").is_file() or not (application / "runtime/release").is_file():
    raise SystemExit("Expected a complete SeekTop Windows app-image")
args.output_dir.mkdir(parents=True, exist_ok=True)
output = args.output_dir.resolve()
commit = subprocess.check_output(["git", "-C", str(repo), "rev-parse", "--verify", args.source_ref + "^{commit}"], text=True).strip()
source = output / f"SeekTop-{args.version}-source.zip"
subprocess.run(["git", "-C", str(repo), "archive", "--format=zip", "--prefix=SeekTop-source/", f"--output={source}", commit], check=True)
notes = (repo / "desktop/RELEASE_NOTES.md").read_text(encoding="utf-8")
instructions = "SeekTop · 寻桌\n\n完整解压此目录后双击 SeekTop.exe。请保留所有文件。\n无需额外安装 Java、Node.js 或 Python。\n\n" + notes
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
    archive.writestr("SeekTop/SOURCE.txt", f"Source repository: https://github.com/ccwan-mamoon/seektop\nSource commit: {commit}\nCorresponding source: {source.name}\nUpstream: https://github.com/5151561/nodyssey\nLicense: GPL-3.0; upstream copyright retained.\n")
checksums = []
for file in (binary, source):
    digest = hashlib.sha256()
    with file.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    checksums.append(f"{digest.hexdigest()}  {file.name}")
    print(f"Created {file.name} ({file.stat().st_size} bytes)", flush=True)
(output / "SHA256SUMS.txt").write_text("\n".join(checksums) + "\n", encoding="utf-8")
(output / "RELEASE_NOTES.md").write_text(notes, encoding="utf-8")
