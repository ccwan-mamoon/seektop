const assert = require("node:assert/strict");
const { EventEmitter } = require("node:events");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const vm = require("node:vm");
const targets = require("../browser/notification-target.cjs");
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
    const activationFile = path.join(temporary, "notification-activation");
    const service = createNotificationService({ app, Notification, shell }, process.execPath, activationFile);
    try {
        assert.equal(service.configure().available, true);
        assert.equal(written.appUserModelId, AUMID);
        assert.equal(written.toastActivatorClsid, CLSID);
        await service.show({ title: "SeekTop", body: "A < B & C" });
        assert.equal(last.options.body, "A < B & C");
        assert.match(last.options.toastXml, /A &lt; B &amp; C/);
        last.emit("click");
        assert.equal(service.status().clicked, true);
        activate({ type: "click" });
        assert.equal(service.status().clicked, true);
        assert.equal(service.status().clicked, false); // A click must not repeatedly raise the window.
        const specific = { tab: "INTERACTIONS", postId: 782, floor: "#27", account: 42 };
        await service.show({ title: "reply", body: "<script>&", destination: specific });
        const replyToast = last;
        const replyActivation = replyToast.options.toastXml.match(/launch="([^"]+)"/)[1];
        await service.show({ title: "message", body: "someone", destination: { tab: "MESSAGES", uid: 88, userName: "member" } });
        // Clicking the older toast must still navigate to that toast's own thread and floor.
        replyToast.emit("click");
        activate({ type: "click", arguments: replyActivation });
        assert.deepEqual(service.status().activations[0].destination, specific);
        assert.equal(service.status().clicked, false); // COM + instance callback is one activation.
        const messageActivation = last.options.toastXml.match(/launch="([^"]+)"/)[1];
        activate({ type: "click", arguments: messageActivation });
        assert.equal(service.status().activations[0].destination.uid, 88);
        await assert.rejects(service.show({ title: "bad", body: "bad", destination: { tab: "MESSAGES", uid: -1 } }), /identifier/);
        fs.writeFileSync(activationFile, "open-notifications");
        assert.equal(service.status().clicked, true); // A COM-launched helper hands off to the live shell.
        assert.equal(fs.existsSync(activationFile), false);
        assert.equal(service.status().clicked, false);
        fs.writeFileSync(activationFile, "stale");
        const stale = new Date(Date.now() - 60000);
        fs.utimesSync(activationFile, stale, stale);
        assert.equal(service.status().clicked, false);
        // Exercise the actual COM/Start Menu bootstrap without launching an app.
        const resourcesPath = path.join(temporary, "app/resources/browser-runtime/resources");
        const launcher = path.join(temporary, "SeekTop.exe");
        fs.writeFileSync(launcher, "test launcher");
        for (const live of [true, false]) {
            let timer, spawned, coldActivate, quit = false;
            const mockProcess = { env: { SEEKTOP_BROWSER_DATA: temporary }, resourcesPath };
            vm.runInNewContext(fs.readFileSync(path.join(__dirname, "../browser/main.cjs"), "utf8"), {
                process: mockProcess,
                setTimeout(callback) { timer = callback; },
                require(name) {
                    if (name === "electron") return { app: { ...app, quit() { quit = true; } }, Notification: { handleActivation(callback) { coldActivate = callback; } } };
                    if (name === "node:child_process") return { spawn(exe, args) { spawned = { exe, args: Array.from(args) }; return { unref() {} }; } };
                    return name.startsWith("./") ? require(path.join(__dirname, "../browser", name)) : require(name);
                },
            });
            const cold = targets.activation({ tab: "REMINDERS", postId: 901, reminderId: "keyword:901" });
            coldActivate({ type: "click", arguments: "seektop:" + targets.encode(cold) });
            assert.equal(fs.existsSync(activationFile), true);
            if (live) assert.deepEqual(service.status().activations[0], cold);
            timer();
            assert.equal(quit, true);
            if (live) assert.equal(spawned, undefined); // Never open a duplicate desktop window.
            else {
                assert.equal(spawned.exe, launcher);
                assert.equal(spawned.args[0], "--notification-target");
                assert.deepEqual(targets.decode(spawned.args[1]), cold);
            }
        }
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
