"""Exercise real per-user install/uninstall with a unique identity and disposable fixture."""
import argparse
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import uuid
import winreg

parser = argparse.ArgumentParser()
parser.add_argument("--iscc", required=True, type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix="installer-smoke-", dir=repo / "build") as temporary:
    root = Path(temporary)
    identity = "SeekTop.InstallerTest." + uuid.uuid4().hex
    state = root / "installer state"
    app = root / "app image"
    (app / "app/resources").mkdir(parents=True)
    (app / "runtime").mkdir()
    (app / "SeekTop.exe").write_bytes(b"MZ not executed in silent fixture")
    (app / "app/SeekTop.cfg").write_text("fixture")
    (app / "runtime/release").write_text("fixture runtime")
    legacy = app / "app/resources/browser-runtime"
    legacy.mkdir()
    (legacy / "electron.exe").write_bytes(b"obsolete helper")
    (root / "SOURCE.txt").write_text("fixture source marker")
    profile = root / "user profile"
    profile.mkdir()
    sentinel = profile / "keep-user-data.txt"
    sentinel.write_text("preserve")
    subprocess.run([
        str(args.iscc.resolve()), "--quiet", "--define=AppVersion=9.9.9",
        f"--define=AppImage={app}", f"--define=RepoRoot={repo}",
        f"--define=MetadataDir={root}", f"--define=ReleaseDir={root}",
        f"--define=AppIdentity={identity}", f"--define=InstallerStateDir={state}",
        str(repo / "desktop/installer/SeekTop.iss"),
    ], check=True)
    setup = root / "SeekTop-9.9.9-windows-x64-setup.exe"
    target = root / "installed application"
    installed = False
    try:
        subprocess.run([str(setup), "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", "/NOICONS", "/TASKS=", f"/DIR={target}", f"/LOG={root / 'install.log'}"], check=True, timeout=60)
        installed = True
        assert (target / "app/SeekTop.cfg").read_text() == "fixture"
        assert (target / "SOURCE.txt").read_text() == "fixture source marker"
        assert not (target / "app/resources/browser-runtime/electron.exe").exists()
        key_path = rf"Software\Microsoft\Windows\CurrentVersion\Uninstall\{identity}_is1"
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key_path) as key:
            uninstall = winreg.QueryValueEx(key, "UninstallString")[0].strip('"')
        assert Path(uninstall).parent == state
        # Simulate the client's ZIP updater replacing the entire application directory.
        backup = root / "old application"
        target.rename(backup)
        shutil.copytree(app, target)
        (target / "app/new-version.jar").write_bytes(b"new runtime file not in Inno's original log")
        assert Path(uninstall).is_file()
        subprocess.run([uninstall, "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART", f"/LOG={root / 'uninstall.log'}"], check=True, timeout=60)
        installed = False
        for _ in range(50):
            if not target.exists():
                break
            time.sleep(.1)
        assert not (target / "app").exists() and not (target / "runtime").exists()
        assert sentinel.read_text() == "preserve"
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key_path):
                raise AssertionError("Uninstall registration was retained")
        except FileNotFoundError:
            pass
    finally:
        if installed:
            for executable in state.glob("unins*.exe"):
                subprocess.run([str(executable), "/VERYSILENT", "/SUPPRESSMSGBOXES", "/NORESTART"], timeout=60)
print("PASS: per-user installation, external uninstall state, ZIP directory replacement and removal of new runtime files")
