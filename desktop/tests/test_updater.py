"""Exercise the real Windows updater in isolated, disposable application directories."""
import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid

repo = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser()
parser.add_argument("--app-dir", type=Path)
args = parser.parse_args()
helper = repo / "desktop/resources/windows/SeekTopUpdater.exe"
assert helper.is_file(), "Build :desktop:compileWindowsUpdater first"
root = repo / "build" / ("u" + uuid.uuid4().hex[:8])
root.mkdir(parents=True)
source = root / "Stub.cs"
source.write_text("""using System; using System.IO; using System.Threading;
class Stub { static void Main() { var cwd=AppDomain.CurrentDomain.BaseDirectory; var hold=Path.Combine(cwd,"hold"); while(File.Exists(hold)) Thread.Sleep(50); var ack=Environment.GetEnvironmentVariable("SEEKTOP_UPDATE_ACK"); if(!String.IsNullOrEmpty(ack)) File.WriteAllText(ack,"ready"); File.WriteAllText(Path.Combine(cwd,"restart-proof.txt"),"started"); } }
""", encoding="utf-8")
stub = root / "Stub.exe"
compiler = Path(os.environ.get("WINDIR", "C:/Windows")) / "Microsoft.NET/Framework64/v4.0.30319/csc.exe"
subprocess.run([str(compiler), "/nologo", "/target:winexe", "/out:" + str(stub), str(source)], check=True)

def bundle(path, marker, executable=stub):
    (path / "app").mkdir(parents=True)
    (path / "runtime").mkdir()
    shutil.copy2(executable, path / "SeekTop.exe")
    (path / "app/SeekTop.cfg").write_text("fixture", encoding="utf-8")
    (path / "runtime/release").write_text("fixture", encoding="utf-8")
    (path / "marker").write_text(marker, encoding="utf-8")

def transaction(parent, target, stage, token, pids=(), restart=True):
    directory = parent / "transactions" / token
    directory.mkdir(parents=True)
    shutil.copy2(helper, directory / "SeekTopUpdater.exe")
    backup = target.parent / ("." + target.name + ".backup-" + token)
    plan = dict(target=str(target), stage=str(stage), backup=str(backup), token=token, pids=list(pids), restart=restart, interactive=False)
    planfile = directory / "plan.json"
    planfile.write_text(json.dumps(plan), encoding="utf-8")
    return directory, planfile, backup

def wait_status(directory, expected, timeout=100):
    deadline = time.monotonic() + timeout
    last = None
    while time.monotonic() < deadline:
        try:
            last = json.loads((directory / "result.json").read_text(encoding="utf-8-sig"))
            if last["status"] in expected: return last
        except (FileNotFoundError, json.JSONDecodeError): pass
        time.sleep(.1)
    raise AssertionError(("Timed out", last))

def run_helper(planfile, env=None):
    return subprocess.Popen([str(planfile.parent / "SeekTopUpdater.exe"), str(planfile)], env=env, creationflags=subprocess.CREATE_NO_WINDOW)

# Wait for the old process, replace atomically, restart, preserve backup and unrelated profile data.
parent = root / "success"; parent.mkdir()
target = parent / "SeekTop"; bundle(target, "old")
token = uuid.uuid4().hex
stage = parent / (".SeekTop.update-" + token); bundle(stage, "new")
profile = parent / "profile-sentinel"; profile.write_text("keep", encoding="utf-8")
(target / "hold").write_text("hold", encoding="utf-8")
old = subprocess.Popen([str(target / "SeekTop.exe")], creationflags=subprocess.CREATE_NO_WINDOW)
browser_dir = target / "app/resources/browser-runtime"
browser_dir.mkdir(parents=True)
shutil.copy2(stub, browser_dir / "SeekTopBrowser.exe")
(browser_dir / "hold").write_text("hold", encoding="utf-8")
browser = subprocess.Popen([str(browser_dir / "SeekTopBrowser.exe")], creationflags=subprocess.CREATE_NO_WINDOW)
directory, plan, backup = transaction(parent, target, stage, token, [old.pid, browser.pid])
worker = run_helper(plan)
wait_status(directory, {"ready"})
assert (target / "marker").read_text() == "old"
(target / "hold").unlink()
old.wait(timeout=10)
assert worker.poll() is None
assert (target / "marker").read_text() == "old"
(browser_dir / "hold").unlink()
browser.wait(timeout=10)
assert worker.wait(timeout=100) == 0
assert wait_status(directory, {"installed"})["status"] == "installed"
assert (target / "marker").read_text() == "new"
assert (backup / "marker").read_text() == "old"
assert (target / "restart-proof.txt").is_file()
assert profile.read_text() == "keep"
print("PASS: wait, swap, restart acknowledgement, backup and profile preservation", flush=True)

# A restart failure rolls the old application directory back into place.
parent = root / "rollback"; parent.mkdir()
target = parent / "SeekTop"; bundle(target, "old")
token = uuid.uuid4().hex
stage = parent / (".SeekTop.update-" + token); bundle(stage, "new")
(stage / "SeekTop.exe").write_bytes(b"MZ invalid executable fixture")
directory, plan, backup = transaction(parent, target, stage, token)
worker = run_helper(plan)
assert worker.wait(timeout=100) == 1
assert wait_status(directory, {"rolledBack"})["status"] == "rolledBack"
assert (target / "marker").read_text() == "old"
assert (stage / "marker").read_text() == "new"
print("PASS: failed restart rolls back to the intact old application", flush=True)

# Paths outside the declared sibling staging directory are never moved.
parent = root / "reject"; parent.mkdir()
target = parent / "SeekTop"; bundle(target, "old")
foreign = parent / "unrelated"; bundle(foreign, "foreign")
directory, plan, backup = transaction(parent, target, foreign, uuid.uuid4().hex, restart=False)
worker = run_helper(plan)
assert worker.wait(timeout=10) == 1
assert wait_status(directory, {"failed"})["status"] == "failed"
assert (target / "marker").read_text() == "old"
assert (foreign / "marker").read_text() == "foreign"
print("PASS: unrelated paths rejected without modification", flush=True)

# Optional end-to-end smoke uses the packaged Compose app and real Chromium cleanup.
if args.app_dir:
    parent = root / "real"; parent.mkdir()
    target = parent / "SeekTop"
    shutil.copytree(args.app_dir.resolve(), target)
    token = uuid.uuid4().hex
    stage = parent / (".SeekTop.update-" + token)
    shutil.copytree(args.app_dir.resolve(), stage)
    cfg = stage / "app/SeekTop.cfg"
    cfg.write_text(cfg.read_text(encoding="utf-8").replace("-Djpackage.app-version=0.4.0", "-Djpackage.app-version=0.4.1"), encoding="utf-8")
    profile = parent / "profile"
    cache = profile / "SeekTop/updates"
    cache.mkdir(parents=True)
    sentinel = profile / "keep-user-data.txt"; sentinel.write_text("keep", encoding="utf-8")
    env = dict(os.environ, LOCALAPPDATA=str(profile))
    old = subprocess.Popen([str(target / "SeekTop.exe")], env=env)
    # Process lifecycle control targets only children of the launcher we just created.
    powershell = "powershell.exe"
    query = f"Get-CimInstance Win32_Process -Filter \"ParentProcessId={old.pid} AND Name='SeekTop.exe'\" | ForEach-Object {{ $p=Get-Process -Id $_.ProcessId; if ($p.MainWindowTitle -like '*本地验收版*') {{ $p.Id }} }}"
    child = None
    for _ in range(40):
        result = subprocess.check_output([powershell, "-NoProfile", "-Command", query], text=True).strip()
        if result: child = int(result); break
        time.sleep(.5)
    assert child, "Old app did not start"
    descendants_query = f"$all=Get-CimInstance Win32_Process; $ids=@({old.pid},{child}); do {{ $more=@($all | Where-Object {{ $_.ParentProcessId -in $ids -and $_.ProcessId -notin $ids }}); $ids+=@($more | ForEach-Object {{ $_.ProcessId }}) }} while($more.Count -gt 0); $all | Where-Object {{ $_.ProcessId -in $ids -and ($_.Name -eq 'SeekTop.exe' -or $_.Name -eq 'SeekTopBrowser.exe') }} | ForEach-Object {{ $_.ProcessId }}"
    pids = [int(value) for value in subprocess.check_output([powershell, "-NoProfile", "-Command", descendants_query], text=True).split()]
    directory, plan, backup = transaction(cache, target, stage, token, pids)
    worker = run_helper(plan, env)
    wait_status(directory, {"ready"})
    subprocess.run([powershell, "-NoProfile", "-Command", f"(Get-Process -Id {child}).CloseMainWindow() | Out-Null"], check=True)
    result_code = worker.wait(timeout=180)
    if result_code != 0:
        print((directory / "result.json").read_text(encoding="utf-8-sig"), flush=True)
        if (directory / "failure.log").is_file(): print((directory / "failure.log").read_text(encoding="utf-8-sig"), flush=True)
    assert result_code == 0
    assert wait_status(directory, {"installed"})["status"] == "installed"
    assert (directory / "launch.ok").is_file()
    assert "-Djpackage.app-version=0.4.1" in (target / "app/SeekTop.cfg").read_text(encoding="utf-8")
    assert "-Djpackage.app-version=0.4.0" in (backup / "app/SeekTop.cfg").read_text(encoding="utf-8")
    assert sentinel.read_text() == "keep"
    assert not (profile / "SeekTop/startup-error.log").exists()
    # Close only the restarted app in this exact isolated installation directory.
    path = str(target / "SeekTop.exe").replace("'", "''")
    close = f"Get-CimInstance Win32_Process -Filter \"Name='SeekTop.exe'\" | Where-Object {{ $_.ExecutablePath -eq '{path}' }} | ForEach-Object {{ $p=Get-Process -Id $_.ProcessId; if ($p.MainWindowTitle -like '*本地验收版*') {{ $p.CloseMainWindow() | Out-Null }} }}"
    subprocess.run([powershell, "-NoProfile", "-Command", close], check=True)
    print("PASS: actual packaged SeekTop exited, upgraded 0.4.0 -> fixture 0.4.1, restarted and acknowledged startup", flush=True)
print("Updater smoke fixtures: " + str(root), flush=True)
