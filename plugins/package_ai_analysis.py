"""Build a signed declarative AI pack. No executable code or API keys are packaged."""
import argparse
import base64
import io
import json
import time
from pathlib import Path
import zipfile
from package_plugins import ROOT, encoded, sha, sign


def package(args):
    args.output.mkdir(parents=True, exist_ok=True)
    source = io.BytesIO()
    with zipfile.ZipFile(source, "w", zipfile.ZIP_DEFLATED) as archive:
        for name in ["plugins/ai-analysis/analysis.json", "plugins/manifests/ai-analysis.json", "plugins/package_ai_analysis.py", "plugins/package_plugins.py", "plugins/README.md", "LICENSE", "NOTICE.md"]:
            archive.write(ROOT / name, name)
    files = {"analysis.json": (ROOT / "plugins/ai-analysis/analysis.json").read_bytes(), "SOURCE.zip": source.getvalue(), "LICENSE.txt": (ROOT / "LICENSE").read_bytes(), "NOTICE.txt": (ROOT / "NOTICE.md").read_bytes()}
    manifest = json.loads((ROOT / "plugins/manifests/ai-analysis.json").read_text(encoding="utf-8"))
    manifest["files"] = {name: sha(data) for name, data in files.items()}
    payload = encoded(manifest)
    name = f"SeekTop-ai-analysis-{manifest['version']}.stplugin"
    target = args.output / name
    with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("manifest.json", payload)
        archive.writestr("manifest.sig", sign(payload, args.key))
        for filename, data in files.items():
            archive.writestr(filename, data)
    listing = dict(manifest=manifest, url=args.base_url.rstrip("/") + "/" + name, sha256=sha(target.read_bytes()), size=target.stat().st_size)
    payload = encoded(dict(revision=args.revision, plugins=[listing]))
    (args.output / "catalog.json").write_bytes(encoded(dict(payload=base64.b64encode(payload).decode(), signature=base64.b64encode(sign(payload, args.key)).decode())))
    print(f"Built and verified {name}: {target.stat().st_size:,} bytes")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "build/ai-plugin")
    parser.add_argument("--revision", type=int, default=int(time.time()))
    parser.add_argument("--base-url", default="https://github.com/ccwan-mamoon/seektop/releases/download/plugin-market-v2")
    package(parser.parse_args())
