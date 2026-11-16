const { app, BrowserWindow, session, Menu } = require("electron");
const http = require("http");
const token = process.env.SEEKTOP_BRIDGE_TOKEN;
const notificationTargets = require("./notification-target.cjs");
const activationPath = require("node:path").join(
    process.env.SEEKTOP_BROWSER_DATA || require("node:path").join(process.env.LOCALAPPDATA || require("node:path").join(require("node:os").homedir(), "AppData/Local"), "SeekTop/browser"),
    "notification-activation",
);
// A Start Menu / Action Center launch must open the desktop shell, rather than
// leave an invisible Chromium helper running without its authenticated parent.
if (!token) {
    const path = require("node:path");
    const launcher = path.resolve(process.resourcesPath, "../../../../SeekTop.exe");
    const fs = require("node:fs");
    fs.mkdirSync(path.dirname(activationPath), { recursive: true });
    const writeActivation = value => fs.writeFileSync(activationPath, notificationTargets.encode(value));
    let activationReceived = false;
    const { Notification } = require("electron");
    const { AUMID, CLSID } = require("./notifications.cjs");
    app.setName("SeekTop");
    app.setAppUserModelId(AUMID);
    app.setToastActivatorCLSID(CLSID);
    Notification.handleActivation(details => {
        activationReceived = true;
        try { writeActivation(notificationTargets.decode(details.arguments)); }
        catch { writeActivation(notificationTargets.activation()); }
    });
    // Give the live shell a chance to acknowledge the click; otherwise cold-start it.
    setTimeout(() => {
        if (!activationReceived) writeActivation(notificationTargets.activation());
        if (fs.existsSync(activationPath) && fs.existsSync(launcher)) {
            const target = fs.readFileSync(activationPath, "utf8");
            fs.unlinkSync(activationPath);
            require("node:child_process").spawn(launcher, ["--notification-target", target], { detached: true, stdio: "ignore", windowsHide: true }).unref();
        }
        app.quit();
    }, 2000);
} else {
app.setPath("userData", process.env.SEEKTOP_BROWSER_DATA);
const notifications = require("./notifications.cjs").createNotificationService(require("electron"), process.env.SEEKTOP_DESKTOP_EXE, activationPath);
app.on("before-quit", () => notifications.dispose());
app.whenReady().then(() => {
    // Storage compatibility: retain existing SeekTop users' forum cookies.
    const forum = session.fromPartition("persist:nodyssey");
    const third = session.fromPartition("persist:third-party");
    const updatesSession = session.fromPartition("updates");
    const pluginSession = session.fromPartition("plugin-market");
    const aiSession = session.fromPartition("ai-analysis");
    const mailSession = session.fromPartition("seekli-mail");
    const seekli = require("./seekli.cjs").createSeekLiService({session:mailSession,safeStorage:require("electron").safeStorage,directory:require("node:path").join(process.env.SEEKTOP_BROWSER_DATA,"../mail")});
    const fxSession = session.fromPartition("exchange-rates");
    const fetchExchangeRates = require("./exchange-rates.cjs").createExchangeRateFetcher(fxSession);
    const ai = require("./ai-analysis.cjs").createAiService({session:aiSession,safeStorage:require("electron").safeStorage,directory:require("node:path").join(process.env.SEEKTOP_BROWSER_DATA,"../ai"),defaultSettings:{providers:[{id:"deepseek",name:"DeepSeek",baseUrl:"https://api.deepseek.com",model:"deepseek-flash",vision:true}],selectedProvider:"deepseek",defaultPrompt:"",boardPrompts:{}}});
    app.on("before-quit",()=>ai.dispose());
    const pluginDownload = require("./plugin-download.cjs").createPluginDownloader(pluginSession);
    const updates = require("./updater.cjs").createUpdateService(updatesSession, require("node:path").join(process.env.SEEKTOP_BROWSER_DATA, "../updates/downloads"));
    app.on("before-quit", () => updates.dispose());
    let login = null;
    const verification = require("./verification.cjs").createVerificationService(forum, BrowserWindow);
    const allowed = (u) => {
        try {
            const x = new URL(u);
            return (
                x.protocol === "https:" &&
                ["nodeseek.com", "www.nodeseek.com"].includes(x.hostname)
            );
        } catch {
            return false;
        }
    };
    let proxyCredentials = {};
    app.on("login", (event, contents, details, auth, callback) => {
        if (auth.isProxy) {
            event.preventDefault();
            callback(
                proxyCredentials.username || "",
                proxyCredentials.password || "",
            );
        }
    });
    const server = http.createServer(async (req, res) => {
        if (req.headers.authorization !== `Bearer ${token}`) {
            res.writeHead(403);
            return res.end();
        }
        try {
            let body = "";
            for await (const c of req) {
                body += c;
                if (body.length > 40 * 1024 * 1024)
                    throw Error("Request too large");
            }
            const q = body ? JSON.parse(body) : {};
            let value;
            switch (req.url) {
                case "/mail-settings": value = await seekli.settings(); break;
                case "/mail-save": value = await seekli.save(q); break;
                case "/mail-clear": value = await seekli.clear(); break;
                case "/mail-request": value = await seekli.request(q); break;
                case "/ai-settings": value = await ai.settings(); break;
                case "/ai-save": value = await ai.save(q); break;
                case "/ai-test": value = await ai.test(q.id); break;
                case "/ai-start": value = await ai.start(q); break;
                case "/ai-status": value = ai.status(q.id); break;
                case "/ai-cancel": value = await ai.cancel(q.id); break;
                case "/exchange-rates": value = await fetchExchangeRates(); break;
                case "/plugin-download": value = await pluginDownload(q); break;
                case "/update-releases": value = await updates.releases(); break;
                case "/update-start": value = await updates.start(q); break;
                case "/update-status": value = updates.status(q.id); break;
                case "/update-cancel": value = await updates.cancel(q.id); break;
                case "/notification-configure":
                    value = notifications.configure();
                    break;
                case "/notification-status":
                    value = notifications.status();
                    break;
                case "/notification-show":
                    value = await notifications.show(q);
                    break;
                case "/info":
                    value = { userAgent: forum.getUserAgent() };
                    break;
                case "/shutdown":
                    value = {};
                    setTimeout(() => app.quit(), 100);
                    break;
                case "/proxy": {
                    proxyCredentials = q;
                    const config = q.enabled
                        ? {
                              mode: "fixed_servers",
                              proxyRules: `${q.type === "SOCKS" ? "socks5" : "http"}://${q.host}:${q.port}`,
                          }
                        : { mode: "system" };
                    await mailSession.setProxy(q.scope === "FORUM_ONLY" ? { mode: "system" } : config);
                    await mailSession.closeAllConnections();
                    await forum.setProxy(config);
                    await third.setProxy(
                        q.scope === "FORUM_ONLY" ? { mode: "system" } : config,
                    );
                    await updatesSession.setProxy(q.scope === "FORUM_ONLY" ? { mode: "system" } : config);
                    await pluginSession.setProxy(q.scope === "FORUM_ONLY" ? { mode: "system" } : config);
                    await pluginSession.closeAllConnections();
                    await aiSession.setProxy(q.scope === "FORUM_ONLY" ? { mode: "system" } : config);
                    await aiSession.closeAllConnections();
                    await fxSession.setProxy(q.scope === "FORUM_ONLY" ? { mode: "system" } : config);
                    await fxSession.closeAllConnections();
                    await updatesSession.closeAllConnections();
                    await forum.closeAllConnections();
                    await third.closeAllConnections();
                    value = {};
                    break;
                }
                case "/cookies":
                    value = {
                        header: (await forum.cookies.get({ url: q.url }))
                            .map((c) => `${c.name}=${c.value}`)
                            .join("; "),
                    };
                    break;
                case "/set-cookie": {
                    if (!allowed(q.url)) throw Error("Invalid cookie origin");
                    const chunks = q.cookie.split(";").map((x) => x.trim()),
                        i = chunks[0].indexOf("=");
                    const c = {
                        url: q.url,
                        name: chunks[0].slice(0, i),
                        value: chunks[0].slice(i + 1),
                    };
                    for (const attr of chunks.slice(1)) {
                        const [k, ...v] = attr.split("=");
                        const val = v.join("=");
                        switch (k.toLowerCase()) {
                            case "path":
                                c.path = val;
                                break;
                            case "domain":
                                c.domain = val;
                                break;
                            case "max-age":
                                c.expirationDate =
                                    Date.now() / 1000 + Number(val);
                                break;
                            case "secure":
                                c.secure = true;
                                break;
                            case "httponly":
                                c.httpOnly = true;
                                break;
                        }
                    }
                    await forum.cookies.set(c);
                    value = {};
                    break;
                }
                case "/clear-cookies":
                    await forum.clearStorageData({ storages: ["cookies"] });
                    value = {};
                    break;
                case "/flush":
                    await forum.cookies.flushStore();
                    value = {};
                    break;
                case "/cache-size":
                    value = { size: await forum.getCacheSize() };
                    break;
                case "/clear-cache":
                    await forum.clearCache();
                    value = {};
                    break;
                case "/verify":
                    value = await verification.verify(q);
                    break;
                case "/verification-window":
                    value = verification.windowHandle(q.id);
                    break;
                case "/verification-show":
                    value = verification.show(q.id);
                    break;
                case "/verification-close":
                    verification.close(q.id);
                    value = {};
                    break;
                case "/login": {
                    if (!allowed(q.url)) throw Error("Invalid login URL");
                    if (login) {
                        login.focus();
                        throw Error("Login window already open");
                    }
                    login = new BrowserWindow({
                        width: 1050,
                        height: 800,
                        title: "SeekTop · 登录与验证",
                        webPreferences: {
                            session: forum,
                            contextIsolation: true,
                            nodeIntegration: false,
                            sandbox: true,
                        },
                    });
                    login.setMenu(
                        Menu.buildFromTemplate([
                            {
                                label: "完成并返回应用",
                                click: () => login?.close(),
                            },
                            { role: "editMenu" },
                            { role: "viewMenu" },
                        ]),
                    );
                    login.webContents.setWindowOpenHandler(() => ({
                        action: "deny",
                    }));
                    login.webContents.on("will-navigate", (event) => {
                        if (!allowed(event.url)) event.preventDefault();
                    });
                    login.webContents.on("will-redirect", (event) => {
                        if (event.isMainFrame !== false && !allowed(event.url)) event.preventDefault();
                    });
                    const closed = new Promise((resolve) =>
                        login.once("closed", resolve),
                    );
                    try {
                        await login.loadURL(q.url);
                        await closed;
                    } finally {
                        if (login && !login.isDestroyed()) login.destroy();
                        login = null;
                        await forum.cookies.flushStore();
                    }
                    value = {};
                    break;
                }
                case "/fetch": {
                    const url = new URL(q.url);
                    if (!["https:", "http:"].includes(url.protocol))
                        throw Error("Invalid URL");
                    const s = q.forum ? forum : third;
                    if (q.forum && !allowed(q.url))
                        throw Error(
                            "Forum session cannot be sent to another host",
                        );
                    // Chromium owns fetch metadata; forwarding mobile Sec-Fetch-* values makes
                    // session.fetch reject otherwise valid XHRs with ERR_INVALID_ARGUMENT.
                    const headers = Object.fromEntries(Object.entries(q.headers || {}).filter(([name]) => !/^sec-fetch-/i.test(name)));
                    if (
                        q.forum &&
                        !Object.keys(headers).some(
                            (x) => x.toLowerCase() === "referer",
                        )
                    )
                        headers.Referer = "https://www.nodeseek.com/";
                    let payload;
                    if (q.body?.kind === "text") {
                        payload = q.body.content;
                        headers["Content-Type"] = q.body.contentType;
                    } else if (q.body?.kind === "empty") payload = "";
                    else if (q.body?.kind === "multipart") {
                        payload = new FormData();
                        for (const [k, v] of Object.entries(q.body.fields))
                            payload.append(k, v);
                        payload.append(
                            q.body.fileField,
                            new Blob([Buffer.from(q.body.bytes, "base64")], {
                                type: q.body.mime,
                            }),
                            q.body.name,
                        );
                    }
                    const response = await s.fetch(q.url, {
                        method: q.method,
                        headers,
                        body: payload,
                        signal: AbortSignal.timeout(60000),
                    });
                    const limit = q.maxBytes || 16 * 1024 * 1024,
                        chunks = [];
                    let length = 0;
                    if (response.body) {
                        const reader = response.body.getReader();
                        for (;;) {
                            const { done, value: chunk } = await reader.read();
                            if (done) break;
                            length += chunk.length;
                            if (length > limit) {
                                await reader.cancel();
                                throw Error("Response exceeds size limit");
                            }
                            chunks.push(Buffer.from(chunk));
                        }
                    }
                    const bytes = Buffer.concat(chunks);
                    value = {
                        code: response.status,
                        url: response.url || q.url,
                        headers: Object.fromEntries(response.headers),
                        body: q.binary
                            ? bytes.toString("base64")
                            : bytes.toString("utf8"),
                    };
                    break;
                }
                default:
                    throw Error("Unknown operation");
            }
            res.setHeader("Content-Type", "application/json");
            res.end(JSON.stringify(value));
        } catch (e) {
            res.writeHead(500, { "Content-Type": "application/json" });
            res.end(JSON.stringify({ error: e.message }));
        }
    });
    server.listen(0, "127.0.0.1", () =>
        process.stdout.write(
            JSON.stringify({ port: server.address().port }) + "\n",
        ),
    );
    if (process.env.SEEKTOP_PARENT_PID)
        setInterval(() => {
            try {
                process.kill(Number(process.env.SEEKTOP_PARENT_PID), 0);
            } catch {
                app.quit();
            }
        }, 3000);
});
app.on("window-all-closed", () => {});
}
