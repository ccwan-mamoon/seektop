const { randomUUID } = require('node:crypto');

// Only the JVM-authenticated bridge can install a document. Other HTTPS requests retain the
// normal Chromium networking/cookie stack. No Node APIs are exposed to the verification page.
function createVerificationService(forum, BrowserWindow) {
    const documents = new Map();
    const active = new Map();
    const cancelled = new Map();
    function rememberCancellation(id) {
        const now = Date.now();
        for (const [key, until] of cancelled) if (until < now) cancelled.delete(key);
        cancelled.set(id, now + 180000);
        while (cancelled.size > 256) cancelled.delete(cancelled.keys().next().value);
    }
    forum.protocol.handle('https', (request) => {
        const document = documents.get(request.url);
        if (request.method === 'GET' && document !== undefined) {
            return new Response(document, { headers: {
                'content-type': 'text/html; charset=utf-8',
                'cache-control': 'no-store',
                'content-security-policy': "default-src 'none'; script-src 'unsafe-inline' https://challenges.cloudflare.com; frame-src https://challenges.cloudflare.com; connect-src https://challenges.cloudflare.com; style-src 'unsafe-inline'; img-src data: https://challenges.cloudflare.com",
            }});
        }
        return forum.fetch(request, { bypassCustomProtocolHandlers: true });
    });
    function close(id) {
        if (typeof id !== 'string') return;
        rememberCancellation(id);
        active.get(id)?.finish({ kind: 'cancelled', value: '' });
    }
    async function verify(q) {
        if (typeof q.id !== 'string' || !/^[a-f0-9-]{36}$/.test(q.id)) throw Error('Invalid verification request');
        const origin = new URL(q.origin);
        if (origin.protocol !== 'https:' || !['nodeseek.com', 'www.nodeseek.com'].includes(origin.hostname) || origin.port) throw Error('Invalid verification origin');
        if (typeof q.document !== 'string' || q.document.length > 65536) throw Error('Invalid verification document');
        if (cancelled.delete(q.id)) return { kind: 'cancelled', value: '' };
        if (active.size) throw Error('Verification already open');
        const url = origin.origin + '/__seektop_verification/' + randomUUID();
        const bridge = `<script>Object.defineProperty(window,'__nsBridge',{value:Object.freeze({post:function(kind,value){window.__seekTopResult={kind:String(kind),value:String(value||'')}}})});</script>`;
        const document = q.document.replace('<head>', '<head>' + bridge).replace('</head>', '<style>html,body{height:100%}#w{min-height:100vh}</style></head>');
        documents.set(url, document);
        const window = new BrowserWindow({ width: 480, height: 260, resizable: false, title: 'SeekTop · 安全验证', backgroundColor: q.dark ? '#14191c' : '#ffffff', webPreferences: { session: forum, contextIsolation: true, nodeIntegration: false, sandbox: true } });
        window.removeMenu();
        window.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
        window.webContents.on('will-navigate', (event, target) => { if (target !== url) event.preventDefault(); });
        window.webContents.on('will-redirect', (event) => event.preventDefault());
        let timer;
        let timeout;
        let checking = false;
        let settled = false;
        return new Promise((resolve) => {
            const finish = (result) => {
                if (settled) return;
                settled = true;
                clearInterval(timer); clearTimeout(timeout);
                documents.delete(url); active.delete(q.id);
                if (!window.isDestroyed()) window.destroy();
                resolve(result);
            };
            active.set(q.id, { finish });
            window.once('closed', () => finish({ kind: 'cancelled', value: '' }));
            timeout = setTimeout(() => finish({ kind: 'expired', value: '' }), 180000);
            timer = setInterval(async () => {
                if (checking || settled || window.isDestroyed() || window.webContents.getURL() !== url) return;
                checking = true;
                try {
                    const result = await window.webContents.executeJavaScript('window.__seekTopResult || null');
                    if (settled || !result) return;
                    if (result.kind === 'token' && typeof result.value === 'string' && result.value.length > 0 && result.value.length <= 4096) finish(result);
                    else finish({ kind: result.kind === 'expired' ? 'expired' : 'error', value: '' });
                } catch { if (!settled) finish({ kind: 'error', value: '' }); }
                finally { checking = false; }
            }, 250);
            window.loadURL(url).catch(() => finish({ kind: 'error', value: '' }));
        });
    }
    return { verify, close };
}
module.exports = { createVerificationService };
