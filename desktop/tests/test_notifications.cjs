const assert = require("node:assert/strict");
const { EventEmitter } = require("node:events");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { createNotificationService, AUMID, CLSID } = require("../browser/notifications.cjs");

async function main() {
    const temporary = fs.mkdtempSync(path.join(os.tmpdir(), "seektop-toast-test-"));
    let activate, last, fail = false, written, shortcutId;
    class Notification extends EventEmitter {
        static isSupported() { return true; }
        static handleActivation(callback) { activate = callback; }
        constructor(options) { super(); this.options = options; last = this; }
        show() { queueMicrotask(() => this.emit(fail ? "failed" : "show", {}, fail ? "denied" : undefined)); }
    }
    const app = {
        setName() {}, setAppUserModelId(id) { assert.equal(id, AUMID); },
        setToastActivatorCLSID(id) { assert.equal(id, CLSID); }, getPath() { return temporary; },
    };
    const shell = {
        readShortcutLink() { return { appUserModelId: shortcutId }; },
        writeShortcutLink(file, _operation, options) { written = options; return true; },
    };
    const service = createNotificationService({ app, Notification, shell }, process.execPath);
    try {
        assert.equal(service.configure().available, true);
        assert.equal(written.appUserModelId, AUMID);
        assert.equal(written.toastActivatorClsid, CLSID);
        await service.show({ title: "SeekTop", body: "A < B & C" });
        assert.equal(last.options.body, "A < B & C"); // Plain API text, never raw XML.
        last.emit("click");
        assert.equal(service.status().clicked, true);
        activate({ type: "click" });
        assert.equal(service.status().clicked, true);
        assert.equal(service.status().clicked, false); // A click must not repeatedly raise the window.
        fail = true;
        await assert.rejects(service.show({ title: "SeekTop", body: "failure" }), /denied/);
        assert.equal(service.status().available, false); // Never advertise successful native delivery on failure.
        fail = false;
        await service.show({ title: "SeekTop", body: "recovered" });
        assert.equal(service.status().available, true);
        await assert.rejects(service.show({ title: {}, body: "invalid" }), /Invalid/);
        service.dispose();
        activate({ type: "click" });
        assert.equal(service.status().clicked, false);
        await assert.rejects(service.show({ title: "SeekTop", body: "closed" }), /stopped/);

        const programs = path.join(temporary, "Microsoft/Windows/Start Menu/Programs");
        fs.writeFileSync(path.join(programs, "SeekTop.lnk"), "unrelated shortcut");
        shortcutId = "another.app";
        const conflict = createNotificationService({ app, Notification, shell }, process.execPath);
        assert.equal(conflict.configure().available, false);
        assert.match(conflict.status().error, /another application/);
        conflict.dispose();
        console.log("PASS: native identity, activation acknowledgement, failure recovery and shortcut ownership");
    } finally { service.dispose(); fs.rmSync(temporary, { recursive: true, force: true }); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
