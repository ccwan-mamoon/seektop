// Use the site's own HTTPS document and ordinary Chromium networking. Intercepting every
// HTTPS request with protocol.handle strips browser response metadata and breaks CF frames.
const readWidget = `(() => {
    const field = document.querySelector('input[name="cf-turnstile-response"]');
    const holder = field?.parentElement || document.querySelector('.cf-turnstile');
    // Never reveal a parent that includes account/password fields.
    if (!holder || holder.querySelector('input:not([type="hidden"]),textarea,button[type="submit"]')) return null;
    if (holder && !document.getElementById('seektop-widget-style')) {
        holder.setAttribute('data-seektop-widget', '');
        const style = document.createElement('style');
        style.id = 'seektop-widget-style';
        style.textContent = 'html,body{margin:0!important;background:#fff!important;overflow:hidden!important}body{visibility:hidden}[data-seektop-widget],[data-seektop-widget] *{visibility:visible!important}[data-seektop-widget]{position:fixed!important;left:50%!important;top:42%!important;transform:translate(-50%,-50%)!important;width:300px!important;z-index:2147483647!important}';
        document.head.appendChild(style);
    }
    // getResponse is Turnstile's public API. Never manufacture or substitute a token.
    if (typeof window.turnstile?.getResponse !== 'function') return {kind:'ready',value:''};
    try { const token = window.turnstile.getResponse(); return token ? {kind:'token',value:token} : {kind:'ready',value:''}; }
    catch { return {kind:'ready',value:''}; }
})()`;

function createVerificationService(forum, BrowserWindow) {
    const active = new Map();
    const cancelled = new Map();
    function rememberCancellation(id) {
        const now = Date.now();
        for (const [key, until] of cancelled) if (until < now) cancelled.delete(key);
        cancelled.set(id, now + 180000);
        while (cancelled.size > 256) cancelled.delete(cancelled.keys().next().value);
    }
    function close(id) {
        if (typeof id !== 'string') return;
        rememberCancellation(id);
        active.get(id)?.finish({kind:'cancelled',value:''});
    }
    async function verify(q) {
        if (typeof q.id !== 'string' || !/^[a-f0-9-]{36}$/.test(q.id)) throw Error('Invalid verification request');
        const origin = new URL(q.origin);
        if (origin.protocol !== 'https:' || !['nodeseek.com','www.nodeseek.com'].includes(origin.hostname) || origin.port || origin.pathname !== '/' || origin.search || origin.hash || origin.username || origin.password) throw Error('Invalid verification origin');
        if (cancelled.delete(q.id)) return Promise.resolve({kind:'cancelled',value:''});
        if (active.size) throw Error('Verification already open');
        const url = origin.origin + '/signIn.html';
        const window = new BrowserWindow({width:480,height:360,resizable:false,show:false,frame:!q.embedded,skipTaskbar:!!q.embedded,title:'SeekTop · 安全验证',backgroundColor:'#ffffff',webPreferences:{session:forum,contextIsolation:true,nodeIntegration:false,sandbox:true}});
        window.removeMenu();
        window.webContents.setWindowOpenHandler(() => ({action:'deny'}));
        const trusted = target => { try { const u = new URL(target); return u.protocol === 'https:' && ['nodeseek.com','www.nodeseek.com'].includes(u.hostname) && !u.port; } catch { return false; } };
        window.webContents.on('will-navigate',event=>{if(!trusted(event.url))event.preventDefault()});
        // Restrict the top page; CF's cross-origin frames must retain normal redirects.
        window.webContents.on('will-redirect',event=>{if(event.isMainFrame !== false && !trusted(event.url))event.preventDefault()});
        let timer, timeout, checking=false, settled=false, visible=false, startedAt=0;
        return new Promise(resolve => {
            const finish = result => {
                if(settled)return;
                settled=true;clearInterval(timer);clearTimeout(timeout);active.delete(q.id);
                if(!window.isDestroyed())window.destroy();
                resolve(result);
            };
            const start = () => { startedAt=Date.now(); return window.loadURL(url).catch(error=>finish({kind:'error',value:'page-load: '+String(error.code || error.message).slice(0,100)})); };
            active.set(q.id,{finish,window,start,started:!q.embedded});
            window.once('closed',()=>finish({kind:'cancelled',value:''}));
            window.webContents.on('did-fail-load',(_event,code,description,_url,mainFrame)=>{
                if(mainFrame && code !== -3)finish({kind:'error',value:'page-load: '+code+' '+String(description).slice(0,80)});
            });
            window.webContents.on('render-process-gone',(_event,details)=>finish({kind:'error',value:'renderer-'+String(details.reason).slice(0,60)}));
            window.webContents.on('console-message',event=>{
                if (!/turnstile/i.test(event.message || '')) return;
                const code = event.message.match(/\b(?:110|200|300|600)\d{3}\b/);
                if(code)finish({kind:'error',value:'Turnstile '+code[0]});
            });
            timeout=setTimeout(()=>finish({kind:'error',value:'verification-timeout'}),180000);
            timer=setInterval(async()=>{
                if(checking || settled || window.isDestroyed() || !trusted(window.webContents.getURL()))return;
                checking=true;
                try {
                    const result=await window.webContents.executeJavaScript(readWidget);
                    if(result?.kind==='ready' && !visible){visible=true;window.showInactive()}
                    if(!visible && !result && startedAt && Date.now()-startedAt>30000)finish({kind:'error',value:'widget-not-ready'});
                    if(result?.kind==='token' && typeof result.value==='string' && result.value.length>0 && result.value.length<=4096)finish(result);
                } catch { /* Navigations destroy the execution context; the next loaded page retries. */ }
                finally{checking=false}
            },300);
            if(!q.embedded)start();
        });
    }
    function windowHandle(id) {
        const item=active.get(id);
        if(!item || item.window.isDestroyed())return {handle:''};
        const bytes=item.window.getNativeWindowHandle();
        return {handle:(bytes.length===8?bytes.readBigUInt64LE():BigInt(bytes.readUInt32LE())).toString()};
    }
    function show(id) {
        const item=active.get(id);
        if(!item || item.window.isDestroyed())return {};
        // The native child remains hidden behind the host's loading view until isolated.
        if(!item.started){item.started=true;item.start()}
        return {};
    }
    return {verify,close,windowHandle,show};
}
module.exports={createVerificationService,readWidget};
