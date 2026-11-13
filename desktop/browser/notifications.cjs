const path = require("node:path");
const fs = require("node:fs");
const AUMID = "ccwan.SeekTop.Desktop";
const CLSID = "{A274136F-8EC4-419A-A415-451745E39130}";

// Electron 45 registers its native COM activator. Use a stable identity and its
// official shortcut API so toasts participate in Windows Notification Center.
function createNotificationService({ app, Notification, shell }, desktopExe) {
    let configured = false;
    let error = "";
    let clicked = false;
    let disposed = false;
    const active = new Set();
    app.setName("SeekTop");
    if (process.platform === "win32") {
        app.setAppUserModelId(AUMID);
        app.setToastActivatorCLSID(CLSID);
    }
    Notification.handleActivation(() => { if (!disposed) clicked = true; });

    function configure() {
        if (configured) return status();
        try {
            if (process.platform !== "win32" || !Notification.isSupported())
                throw Error("Windows Toast is unavailable");
            if (!desktopExe || !fs.existsSync(desktopExe))
                throw Error("SeekTop launcher is missing");
            const programs = path.join(app.getPath("appData"), "Microsoft/Windows/Start Menu/Programs");
            const shortcut = path.join(programs, "SeekTop.lnk");
            fs.mkdirSync(programs, { recursive: true });
            if (fs.existsSync(shortcut) && shell.readShortcutLink(shortcut).appUserModelId !== AUMID)
                throw Error("SeekTop shortcut belongs to another application");
            // The helper also delegates a cold start to SeekTop.exe (see main.cjs).
            if (!shell.writeShortcutLink(shortcut, "create", {
                target: process.execPath,
                cwd: path.dirname(process.execPath),
                description: "SeekTop · 寻桌",
                icon: desktopExe,
                iconIndex: 0,
                appUserModelId: AUMID,
                toastActivatorClsid: CLSID,
            })) throw Error("Could not register the SeekTop notification shortcut");
            configured = true;
            error = "";
        } catch (failure) {
            error = failure.message;
        }
        return status();
    }
    function status() {
        const result = { available: configured && !error && !disposed, clicked, error };
        clicked = false;
        return result;
    }
    async function show({ title, body }) {
        if (disposed) throw Error("Notification service has stopped");
        if (!configured && !configure().available) throw Error(error);
        if (typeof title !== "string" || typeof body !== "string" || title.length > 256 || body.length > 4096)
            throw Error("Invalid notification content");
        // Retain a bounded set: close removes a toast from Action Center. Never
        // close on timeout, otherwise reminders disappear before they are read.
        if (active.size >= 128) active.delete(active.values().next().value);
        const notification = new Notification({ title, body, icon: path.join(__dirname, "seektop.png"), groupId: "seektop", groupTitle: "SeekTop" });
        active.add(notification);
        await new Promise((resolve, reject) => {
            let settled = false;
            const timer = setTimeout(() => finish(Error("Windows Toast delivery timed out")), 15000);
            const finish = (failure) => {
                if (settled) return;
                settled = true;
                clearTimeout(timer);
                error = failure ? failure.message : "";
                if (failure) { active.delete(notification); reject(failure); }
                else resolve();
            };
            notification.once("show", () => finish());
            notification.on("failed", (_event, message) => {
                const failure = Error(message || "Windows rejected this notification");
                error = failure.message;
                finish(failure);
                active.delete(notification);
            });
            notification.on("click", () => {
                if (!disposed) clicked = true;
                active.delete(notification);
            });
            notification.on("close", (_event, details) => {
                if (details?.reason === "userCanceled" || details?.reason === "applicationHidden") active.delete(notification);
            });
            try { notification.show(); } catch (failure) { finish(failure); }
        });
        return { delivered: true };
    }
    return { configure, status, show, dispose() { disposed = true; active.clear(); } };
}
module.exports = { createNotificationService, AUMID, CLSID };
