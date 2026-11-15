"""Native router integration. Unique test registry identities; never changes UserChoice."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import uuid
import winreg

root = Path(__file__).resolve().parents[2]
csc = Path(os.environ.get("WINDIR", "C:/Windows")) / "Microsoft.NET/Framework64/v4.0.30319/csc.exe"
identity = uuid.uuid4().hex
regbase = "Software\\SeekTop.RouterTest." + identity
browser = "RouterFixtureBrowser." + identity
router_classes = [f"Software\\Classes\\SeekTop.RouterTest.{identity}.{s}" for s in ("http", "https")]

def remove(key):
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key, 0, winreg.KEY_READ | winreg.KEY_WRITE) as h:
            children = []
            for i in range(winreg.QueryInfoKey(h)[0]): children.append(winreg.EnumKey(h, i))
        for child in children: remove(key + "\\" + child)
        winreg.DeleteKey(winreg.HKEY_CURRENT_USER, key)
    except FileNotFoundError: pass

with tempfile.TemporaryDirectory(prefix="seektop-link-router-") as tmp:
    folder = Path(tmp)
    exe = folder / "RouterTest.exe"
    subprocess.run([str(csc), "/nologo", "/target:winexe", "/define:ROUTER_TEST", "/codepage:65001", "/reference:System.Web.Extensions.dll", "/reference:System.Windows.Forms.dll", "/out:" + str(exe), str(root / "desktop/link-router/Program.cs")], check=True)
    fake_source = folder / "Browser.cs"
    fake_source.write_text('using System; using System.IO; class Browser { [STAThread] static void Main(string[] args) { File.WriteAllText(Environment.GetEnvironmentVariable("SEEKTOP_ROUTER_CAPTURE"), args[0]); } }', encoding="utf-8")
    fake = folder / "Browser.exe"
    subprocess.run([str(csc), "/nologo", "/target:winexe", "/out:" + str(fake), str(fake_source)], check=True)
    app = folder / "SeekTop.exe"; app.write_bytes(b"fixture-never-launched")
    capture = folder / "captured.txt"
    env = dict(os.environ, SEEKTOP_ROUTER_TEST_ID=identity, SEEKTOP_ROUTER_TEST_DIR=str(folder / "profile"), SEEKTOP_ROUTER_CAPTURE=str(capture))
    listener = None
    def call(*args, success=True):
        q = subprocess.run([str(exe), *args], env=env, capture_output=True, text=True, encoding="utf-8", timeout=15)
        if success: assert q.returncode == 0, (args, q.stdout, q.stderr)
        else: assert q.returncode != 0, args
        return q.stdout.strip()
    def choice(http, https):
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, regbase + "\\Choices") as key:
            for name, val in [("http", http), ("https", https), ("httpName", "默认浏览器 Chrome"), ("httpsName", "默认浏览器 Chrome")]: winreg.SetValueEx(key, name, 0, winreg.REG_SZ, val)
    try:
        with winreg.CreateKey(winreg.HKEY_CURRENT_USER, "Software\\Classes\\" + browser + "\\shell\\open\\command") as key:
            winreg.SetValueEx(key, "", 0, winreg.REG_SZ, '"' + str(fake) + '" "%1"')
        choice(browser, "Missing.RouterFixtureBrowser")
        call("--prepare", str(app), success=False)
        assert not (folder / "profile/routing.json").exists(), "Failed capture must not create active configuration"
        choice(browser, browser)
        assert json.loads(call("--status"))["enabled"] is False
        first = json.loads(call("--prepare", str(app)))
        assert first["enabled"] and not first["httpActive"] and first["httpName"] == "默认浏览器 Chrome"
        config_file = folder / "profile/routing.json"
        initial = json.loads(config_file.read_text(encoding="utf-8-sig"))
        assert initial["http"] == browser and initial["https"] == browser
        # Re-enabling after user selection must preserve the real browser, never save ourselves.
        choice("SeekTop.RouterTest." + identity + ".http", "SeekTop.RouterTest." + identity + ".https")
        second = json.loads(call("--prepare", str(app)))
        assert second["httpActive"] and second["httpsActive"]
        assert json.loads(config_file.read_text(encoding="utf-8-sig"))["https"] == browser
        # Shell-launched winexe has no console: UTF-8 setup must also work in real GUI activation.
        test_keys = {k:v for k,v in env.items() if k.startswith("SEEKTOP_ROUTER_")}
        previous = {k:os.environ.get(k) for k in test_keys}
        try:
            os.environ.update(test_keys)
            os.startfile(str(exe), arguments="--disable")
            for _ in range(100):
                if not json.loads(config_file.read_text(encoding="utf-8-sig"))["enabled"]: break
                time.sleep(.02)
            assert not json.loads(config_file.read_text(encoding="utf-8-sig"))["enabled"]
        finally:
            for k,v in previous.items():
                if v is None: os.environ.pop(k,None)
                else: os.environ[k]=v
        call("--prepare", str(app))
        for url in ["https://www.nodeseek.com/post-123-2#29", "https://nodeseek.com/space/99", "https://NODESEEK.com/post-1-1?x=2"]:
            assert call("--classify", url) == "native", url
        for url in ["https://nodeseek.com.evil.test/post-1-1", "https://nodeseek.com/account", "https://nodeseek.com:444/post-1-1", "https://example.com/", "https://nodeseek.com/post-0-1"]:
            assert call("--classify", url) == "browser", url
        for url in ["file:///C:/Windows/System32/calc.exe", "javascript:alert(1)", "https://u:p@nodeseek.com/post-1-1", "https://nodeseek.com/post-1-1\n"]:
            call("--classify", url, success=False)
        # Actual Win32 ShellExecuteEx honors the saved class even while the simulated default is ours.
        url = "https://example.invalid/path?quoted=%22hello%22&x=1#section"
        call("--browser", url)
        for _ in range(100):
            if capture.exists(): break
            time.sleep(.02)
        assert capture.read_text() == url
        assert json.loads(call("--disable"))["enabled"] is False
        capture.unlink(); call(url)
        for _ in range(100):
            if capture.exists(): break
            time.sleep(.02)
        assert capture.read_text() == url
        # Real per-user named pipe delivers a post/floor to an already running client without launching a JVM.
        call("--prepare", str(app))
        listener = subprocess.Popen([str(exe), "--listen", str(os.getpid())], env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8")
        time.sleep(.3)
        post = "https://www.nodeseek.com/post-123-2?source=中文#29"
        call(post)
        assert json.loads(listener.stdout.readline())["url"] == post
        listener.terminate(); listener.wait(timeout=5); listener.stdout.close(); listener.stderr.close(); listener = None
        fake_app_source = folder / "Client.cs"
        fake_app_source.write_text(r'''using System; using System.IO; using System.Diagnostics; using System.Threading;
class Client { [STAThread] static void Main(string[] args) {
 string log=Environment.GetEnvironmentVariable("SEEKTOP_ROUTER_CLIENT_LOG");
 File.WriteAllText(Environment.GetEnvironmentVariable("SEEKTOP_ROUTER_CLIENT_PID"),Process.GetCurrentProcess().Id.ToString());
 File.AppendAllText(log,"START:"+args[1]+"\n");
 var info=new ProcessStartInfo(Environment.GetEnvironmentVariable("SEEKTOP_ROUTER_TEST_EXE"),"--listen "+Process.GetCurrentProcess().Id) {UseShellExecute=false,RedirectStandardOutput=true,CreateNoWindow=true};
 var receiver=Process.Start(info);
 var thread=new Thread(()=> {string line;while((line=receiver.StandardOutput.ReadLine())!=null)File.AppendAllText(log,"PIPE:"+line+"\n");});thread.IsBackground=true;thread.Start();
 Thread.Sleep(20000); receiver.Kill();
} }''', encoding="utf-8")
        subprocess.run([str(csc), "/nologo", "/target:winexe", "/out:" + str(app), str(fake_app_source)], check=True)
        client_log = folder / "client.log"; client_pid = folder / "client.pid"
        env.update(SEEKTOP_ROUTER_CLIENT_LOG=str(client_log), SEEKTOP_ROUTER_CLIENT_PID=str(client_pid), SEEKTOP_ROUTER_TEST_EXE=str(exe))
        cold = [subprocess.Popen([str(exe), "https://www.nodeseek.com/post-123-2#29"], env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE) for _ in range(2)]
        try:
            for proc in cold:
                proc.wait(timeout=20)
                assert proc.returncode == 0, proc.returncode
            for _ in range(100):
                if client_log.exists() and "PIPE:" in client_log.read_text(): break
                time.sleep(.03)
            content = client_log.read_text()
            assert content.count("START:") == 1 and content.count("PIPE:") == 1, content
        finally:
            for proc in cold:
                if proc.poll() is None: proc.kill(); proc.wait()
                proc.stdout.close(); proc.stderr.close()
            if client_pid.exists():
                # Kill only our fixture client's recorded PID; receiver follows that parent automatically.
                subprocess.run(["taskkill", "/PID", client_pid.read_text().strip(), "/F"], capture_output=True)

        # A disabled/uninstalled app must continue forwarding until Windows associations are restored.
        retired = json.loads(call("--retire"))
        assert retired["enabled"] is False
        assert (folder / "profile/SeekTopLinkRouter.exe").exists()
        choice(browser, browser)
        call("--retire")
        for key in router_classes:
            try: winreg.OpenKey(winreg.HKEY_CURRENT_USER, key); raise AssertionError("owned registration not cleaned")
            except FileNotFoundError: pass
        print("PASS: captured browser, no recapture loop, Win32 fallback, strict URL routes, native pipe/floor delivery, disable and uninstall forwarding")
    finally:
        if listener:
            listener.terminate(); listener.wait(timeout=5); listener.stdout.close(); listener.stderr.close()
        remove(regbase)
        for key in router_classes: remove(key)
        remove("Software\\Classes\\" + browser)
