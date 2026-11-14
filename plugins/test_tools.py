"""Real Windows worker protocol smoke tests, using loopback only (no account or public speed tests)."""
import argparse
import json
from pathlib import Path
import queue
import socket
import subprocess
import threading
import time


class Worker:
    def __init__(self, executable, parameters):
        self.process = subprocess.Popen([str(executable)], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        self.events = queue.Queue()
        threading.Thread(target=self._read, daemon=True).start()
        self.process.stdin.write(json.dumps(parameters) + "\n")
        self.process.stdin.flush()

    def _read(self):
        for line in self.process.stdout:
            self.events.put(json.loads(line))

    def wait(self, predicate, timeout=15):
        end = time.monotonic() + timeout
        while time.monotonic() < end:
            try:
                event = self.events.get(timeout=max(0.01, end - time.monotonic()))
            except queue.Empty:
                break
            if predicate(event):
                return event
        raise AssertionError("worker did not produce expected event")

    def close(self):
        if self.process.poll() is None:
            self.process.stdin.write("cancel\n")
            self.process.stdin.flush()
            try:
                self.process.wait(timeout=3)
            except subprocess.TimeoutExpired:
                self.process.kill()
                self.process.wait()
                raise AssertionError("worker ignored cancellation")
        self.process.stdin.close()
        self.process.stdout.close()
        self.process.stderr.close()


def test(directory):
    engine = directory / "iperf-worker.exe"
    # Reserve an ephemeral loopback port. No wildcard listener or firewall changes.
    with socket.socket() as temporary:
        temporary.bind(("127.0.0.1", 0))
        port = temporary.getsockname()[1]
    defaults = dict(mode="client", host="127.0.0.1", port=str(port), protocol="tcp", direction="download", parallel="2", duration="1", bandwidth="10")
    server = Worker(engine, dict(defaults, mode="server", duration="30"))
    try:
        server.wait(lambda e: e["phase"]["en"].startswith("Listening"))
        for protocol, direction in [("tcp", "download"), ("tcp", "upload"), ("tcp", "bidir"), ("udp", "download")]:
            client = Worker(engine, dict(defaults, protocol=protocol, direction=direction))
            try:
                final = client.wait(lambda e: e["done"])
                assert not final["error"], final
                result = [m for m in final["metrics"] if m["label"]["en"] in {"Download result", "Upload result"}]
                assert result and all(float(m["value"].split()[0]) > 0 for m in result), final
                assert client.process.wait(timeout=3) == 0
                print("PASS: real iperf", protocol, direction, "loopback transfer")
            finally:
                client.close()
            time.sleep(0.2)
    finally:
        server.close()
    with socket.socket() as probe:
        probe.settimeout(1)
        assert probe.connect_ex(("127.0.0.1", port)) != 0, "server socket remained open after cancellation"
    print("PASS: cancellation exits worker and releases listener")
    invalid = Worker(engine, dict(defaults, bandwidth="NaN"))
    try:
        assert invalid.wait(lambda e: e["done"])["error"], "NaN must not disable rate limits"
    finally:
        invalid.close()
    for name, parameters in [("speed", dict(duration="-1", streams="2")), ("connectivity", dict(targets="custom", url="http://example.com"))]:
        worker = Worker(directory / (name + "-worker.exe"), parameters)
        try:
            assert worker.wait(lambda e: e["done"])["error"]
        finally:
            worker.close()
    print("PASS: invalid speed and insecure URL inputs fail without a public network request")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--directory", type=Path, default=Path(__file__).resolve().parent.parent / "build/plugins")
    parser.add_argument("--build-with", help="Go executable; build test workers without a publisher signing key")
    args = parser.parse_args()
    directory = args.directory.resolve()
    if args.build_with:
        directory.mkdir(parents=True, exist_ok=True)
        source = Path(__file__).resolve().parent / "network-tools"
        for kind in ("speed", "connectivity", "iperf"):
            subprocess.run([args.build_with, "build", "-mod=readonly", "-trimpath", "-buildvcs=false", "-ldflags", f"-s -w -H=windowsgui -X main.toolKind={kind}", "-o", str(directory / (kind + "-worker.exe")), "."], cwd=source, check=True)
    test(directory)
