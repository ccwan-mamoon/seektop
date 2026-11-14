"""Build independent signed Windows tools and a signed marketplace catalog.

The signing key is required explicitly and is never copied into an artifact.
Only plugins/keys/official.pem is public. Keep the private key in a backed-up
secret store; future marketplace updates must use the same publisher key.
"""
import argparse
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parent.parent


def encoded(value):
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def sign(payload, key):
    script = "const c=require('node:crypto'),f=require('node:fs');process.stdout.write(c.sign(null,f.readFileSync(0),f.readFileSync(process.argv[1])));"
    signature = subprocess.check_output(["node", "-e", script, str(key)], input=payload)
    # Refuse a key that the shipped host cannot verify.
    verify = "const c=require('node:crypto'),f=require('node:fs');const b=f.readFileSync(0);if(!c.verify(null,b.subarray(64),f.readFileSync(process.argv[1]),b.subarray(0,64)))process.exit(1);"
    subprocess.run(["node", "-e", verify, str(ROOT / "plugins/keys/official.pem")], input=signature + payload, check=True)
    return signature


def package(args):
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    engine = ROOT / "plugins/network-tools"
    subprocess.run([str(args.go), "mod", "vendor"], cwd=engine, check=True)
    subprocess.run([str(args.go), "test", "./..."], cwd=engine, check=True)
    # Complete corresponding source, including fixed dependency source/licenses.
    source_buffer = io.BytesIO()
    with zipfile.ZipFile(source_buffer, "w", zipfile.ZIP_DEFLATED) as source:
        for path in sorted(engine.rglob("*")):
            if path.is_file():
                source.write(path, "network-tools/" + path.relative_to(engine).as_posix())
        source.write(ROOT / "LICENSE", "LICENSE")
        source.write(ROOT / "plugins/README.md", "BUILD.md")
        source.write(Path(__file__), "package_plugins.py")
        for path in sorted((ROOT / "plugins/manifests").glob("*.json")):
            source.write(path, "manifests/" + path.name)
    common = {
        "SOURCE.zip": source_buffer.getvalue(),
        "LICENSE.txt": (ROOT / "LICENSE").read_bytes(),
        "NOTICE.txt": (ROOT / "plugins/NOTICE.md").read_bytes(),
        "IPERF-LICENSE.txt": (engine / "vendor/github.com/appshubcc/go-iperf3/LICENSE").read_bytes(),
        "XSYS-LICENSE.txt": (engine / "vendor/golang.org/x/sys/LICENSE").read_bytes(),
        "GO-LICENSE.txt": (args.go.resolve().parent.parent / "LICENSE").read_bytes(),
    }
    listings = []
    for path in sorted((ROOT / "plugins/manifests").glob("*.json")):
        kind = path.stem
        worker = output / f"{kind}-worker.exe"
        env = dict(os.environ, GOOS="windows", GOARCH="amd64", CGO_ENABLED="0")
        subprocess.run([str(args.go), "build", "-mod=vendor", "-trimpath", "-buildvcs=false", "-ldflags", f"-s -w -H=windowsgui -X main.toolKind={kind}", "-o", str(worker), "."], cwd=engine, env=env, check=True)
        files = dict(common, **{"worker.exe": worker.read_bytes()})
        manifest = json.loads(path.read_text(encoding="utf-8"))
        manifest["files"] = {name: sha(data) for name, data in files.items()}
        payload = encoded(manifest)
        name = f"SeekTop-{kind}-{manifest['version']}-windows-x64.stplugin"
        target = output / name
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("manifest.json", payload)
            archive.writestr("manifest.sig", sign(payload, args.key))
            for filename, data in files.items():
                archive.writestr(filename, data)
        listings.append(dict(manifest=manifest, url=args.base_url.rstrip("/") + "/" + name, sha256=sha(target.read_bytes()), size=target.stat().st_size))
        print(f"Built {name}: {target.stat().st_size:,} bytes")
    payload = encoded(dict(revision=args.revision, plugins=listings))
    (output / "catalog.json").write_bytes(encoded(dict(payload=base64.b64encode(payload).decode(), signature=base64.b64encode(sign(payload, args.key)).decode())))
    print("Verified publisher signatures and wrote catalog.json")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--go", type=Path, required=True)
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "build/plugins")
    parser.add_argument("--revision", type=int, default=int(time.time()))
    parser.add_argument("--base-url", default="https://github.com/ccwan-mamoon/seektop/releases/download/plugin-market")
    package(parser.parse_args())
