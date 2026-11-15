"""Real Electron/Windows safeStorage and cookie-free SSE integration; no account or paid API."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

runtime = Path(os.environ.get("SEEKTOP_TEST_RUNTIME", str(Path(__file__).resolve().parents[1] / "resources/windows/browser-runtime")))
directory = Path(tempfile.mkdtemp(prefix="seektop-ai-browser-"))
requests = []
class Provider(BaseHTTPRequestHandler):
    def log_message(self, *args): pass
    def do_GET(self):
        requests.append((self.path, dict(self.headers), None))
        self.send_response(200); self.send_header("Content-Type", "application/json"); self.send_header("Set-Cookie", "provider_session=must-not-return; Path=/"); self.end_headers(); self.wfile.write(b'{"data":[]}')
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        requests.append((self.path, dict(self.headers), body))
        self.send_response(200); self.send_header("Content-Type", "text/event-stream"); self.end_headers()
        for text in ["local ", "中文 ", "✅"]:
            self.wfile.write(("data: " + json.dumps({"choices":[{"delta":{"content":text}}]}, ensure_ascii=False) + "\n\n").encode()); self.wfile.flush(); time.sleep(.03)
        self.wfile.write(b'data: [DONE]\n\n'); self.wfile.flush()
server = ThreadingHTTPServer(("127.0.0.1",0), Provider)
threading.Thread(target=server.serve_forever, daemon=True).start()
process = None
log = None
try:
    def start():
        global process, log, port
        env = dict(os.environ, SEEKTOP_BROWSER_DATA=str(directory / "browser"), SEEKTOP_BRIDGE_TOKEN="ai-integration-local-only")
        log = open(directory / "error.log", "a", encoding="utf-8")
        process = subprocess.Popen([str(runtime / "SeekTopBrowser.exe")], stdout=subprocess.PIPE, stderr=log, env=env, text=True)
        timer = threading.Timer(25, process.terminate); timer.start()
        try:
            for line in process.stdout:
                if line.strip().startswith('{"port":'):
                    port = json.loads(line)["port"]; break
            else: raise AssertionError("Browser failed to start")
        finally: timer.cancel()
    def call(op, value=None):
        req = urllib.request.Request(f"http://127.0.0.1:{port}/{op}", data=json.dumps(value or {}).encode(), headers={"Authorization":"Bearer ai-integration-local-only","Content-Type":"application/json"})
        return json.load(urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req, timeout=30))
    def stop():
        global process
        try: call("shutdown")
        except Exception: pass
        process.wait(timeout=15); process.stdout.close(); log.close(); process = None
    start()
    cfg = call("ai-settings")
    assert cfg["providers"][0]["keySet"] is False
    cfg = {"providers":[{"id":"fixture","name":"Local test","baseUrl":f"http://127.0.0.1:{server.server_port}/v1","model":"fixture","vision":True}],"selectedProvider":"fixture","includeImages":True,"defaultPrompt":"local prompt","boardPrompts":{"trade":"custom trading rule"},"newKeys":{"fixture":"fixture-not-a-real-api-key"}}
    saved = call("ai-save", cfg)
    assert saved["providers"][0]["keySet"] is True
    assert "fixture-not-a-real-api-key" not in json.dumps(saved)
    encrypted = (directory / "ai/provider-settings.enc").read_bytes()
    assert b"fixture-not-a-real-api-key" not in encrypted
    assert b"custom trading rule" not in encrypted
    cfg["newKeys"] = {}
    call("ai-save", cfg)  # Replacing the encrypted file must preserve a blank unchanged key.
    call("ai-test", {"id":"fixture"})
    job = {"id":str(uuid.uuid4()),"system":"local integration test","messages":[{"role":"user","content":[{"type":"text","text":"public synthetic opening post"}]}]}
    call("ai-start", job)
    for _ in range(200):
        status = call("ai-status", {"id":job["id"]})
        if status["done"]: break
        time.sleep(.02)
    assert status == {"text":"local 中文 ✅","done":True,"error":""}, status
    assert requests[0][0] == "/v1/models" and requests[1][0] == "/v1/chat/completions"
    for _, headers, _ in requests:
        headers = {k.lower(): v for k, v in headers.items()}
        assert headers.get("authorization") == "Bearer fixture-not-a-real-api-key"
        assert not headers.get("cookie"), headers
    call("ai-cancel", {"id":job["id"]})
    stop(); start()
    restored = call("ai-settings")
    assert restored["providers"][0]["keySet"] is True
    assert restored["includeImages"] is True
    assert restored["boardPrompts"]["trade"] == "custom trading rule"
    stop()
    print("PASS: real Windows encrypted persistence/restart, Electron SSE, authorization and cookie isolation")
finally:
    if process is not None:
        process.terminate()
        try: process.wait(timeout=15)
        except subprocess.TimeoutExpired: process.kill(); process.wait()
        process.stdout.close(); log.close()
    server.shutdown(); server.server_close()
    shutil.rmtree(directory, ignore_errors=True)
