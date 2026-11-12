const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { createVerificationService } = require('../browser/verification.cjs');
let handler;
let forwarded;
const windows = [];
class Window extends EventEmitter {
    constructor(options) { super(); this.options = options; this.dead = false; this.result = null; this.webContents = new EventEmitter(); this.webContents.getURL = () => this.url; this.webContents.executeJavaScript = async () => this.result; this.webContents.setWindowOpenHandler = () => {}; windows.push(this); }
    removeMenu() {}
    loadURL(url) { this.url = url; return Promise.resolve(); }
    isDestroyed() { return this.dead; }
    destroy() { this.dead = true; this.emit('closed'); }
}
const forum = { protocol: { handle: (scheme, callback) => { assert.equal(scheme, 'https'); handler = callback; } }, fetch: async (request, options) => { forwarded = { request, options }; return new Response('upstream'); } };
const service = createVerificationService(forum, Window);
const input = { id:'11111111-1111-1111-1111-111111111111', origin:'https://www.nodeseek.com', document:'<html><head></head><body>verification fixture</body></html>' };
async function main() {
    await assert.rejects(service.verify({ ...input, origin:'https://nodeseek.com.evil.example' }));
    await assert.rejects(service.verify({ ...input, origin:'http://www.nodeseek.com' }));
    await assert.rejects(service.verify({ ...input, document:'x'.repeat(65537) }));
    const pending = service.verify(input);
    try {
        const window = windows.at(-1);
        assert.equal(window.options.webPreferences.nodeIntegration, false);
        assert.equal(window.options.webPreferences.sandbox, true);
        const ownDocument = await handler(new Request(window.url));
        assert.match(await ownDocument.text(), /__nsBridge/);
        assert.equal(ownDocument.headers.get('cache-control'), 'no-store');
        await assert.rejects(service.verify({ ...input, id:'22222222-2222-2222-2222-222222222222' }));
        const request = new Request('https://www.nodeseek.com/api/fixture', { method:'POST', body:'fixture-only' });
        assert.equal(await (await handler(request)).text(), 'upstream');
        assert.equal(forwarded.request, request);
        assert.equal(forwarded.options.bypassCustomProtocolHandlers, true);
        window.result = { kind:'token', value:'verification-fixture-token' };
        assert.equal((await pending).value, 'verification-fixture-token');
        assert.equal(window.dead, true);
        assert.equal(await (await handler(new Request(window.url))).text(), 'upstream');
        const cancelled = service.verify({ ...input, id:'33333333-3333-3333-3333-333333333333' });
        service.close('33333333-3333-3333-3333-333333333333');
        assert.equal((await cancelled).kind, 'cancelled');
        assert.equal(windows.at(-1).dead, true);
        const count = windows.length;
        service.close('44444444-4444-4444-4444-444444444444');
        assert.equal((await service.verify({ ...input, id:'44444444-4444-4444-4444-444444444444' })).kind, 'cancelled');
        assert.equal(windows.length, count, 'Cancel arriving before start must not leave an orphan window');
        console.log('PASS: verification origin validation, sandbox, exact document routing, POST forwarding, token delivery and cancellation cleanup');
    } finally { service.close(input.id); }
}
main().catch(error => { console.error(error); process.exitCode = 1; });
