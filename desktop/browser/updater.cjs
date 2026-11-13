const fs = require('node:fs');
const path = require('node:path');
const { randomUUID } = require('node:crypto');
const repo = 'https://github.com/ccwan-mamoon/seektop';
const releases = 'https://api.github.com/repos/ccwan-mamoon/seektop/releases?per_page=100';
function trusted(url) {
    try { const u = new URL(url); return u.protocol === 'https:' && !u.username && !u.password && ['github.com','release-assets.githubusercontent.com','objects.githubusercontent.com','github-releases.githubusercontent.com'].includes(u.hostname); }
    catch { return false; }
}
function createUpdateService(session, cache) {
    const jobs = new Map();
    // Electron 45 session.fetch rejects redirect:'manual'. A dedicated session
    // follows redirects while its request boundary checks every destination before I/O.
    session.webRequest.onBeforeRequest((details, callback) => {
        callback({cancel: !trusted(details.url) && details.url !== releases});
    });
    async function fetch(url, headers, signal) {
        if (!trusted(url)) throw Error('Untrusted update URL');
        return session.fetch(url, {headers, signal, redirect:'follow', credentials:'omit', cache:'no-store'});
    }
    async function run(job, q, file) {
        for (let attempt=0;attempt<3;attempt++) {
            const controller=new AbortController();
            const abort=()=>controller.abort();job.controller.signal.addEventListener('abort',abort,{once:true});
            let timer;const tick=()=>{clearTimeout(timer);timer=setTimeout(()=>controller.abort(),30000);};
            let reader,fd,response;
            try {
                job.controller.signal.throwIfAborted();
                let offset=fs.existsSync(file)?fs.statSync(file).size:0;
                if(offset>q.size){fs.truncateSync(file,0);offset=0;}
                job.bytes=offset;
                if(offset===q.size){job.done=true;return;}
                tick();
                response=await fetch(q.url, {'Accept-Encoding':'identity', ...(offset?{Range:`bytes=${offset}-`}:{})},controller.signal);
                if(response.status===206){
                    const m=/^bytes (\d+)-(\d+)\/(\d+)$/.exec(response.headers.get('content-range')||'');
                    if(!m || +m[1]!==offset || +m[2]<offset || +m[2]>=q.size || +m[3]!==q.size) throw Error('Invalid update range');
                } else if(response.status===200){offset=0;job.bytes=0;}
                else throw Error(`Update HTTP ${response.status}`);
                const declared=Number(response.headers.get('content-length'));
                if(declared && declared!==q.size-offset) throw Error('Invalid update size');
                fd=fs.openSync(file,offset?'a':'w'); reader=response.body.getReader();
                for(;;){
                    const {done,value}=await reader.read(); if(done)break;
                    tick(); job.controller.signal.throwIfAborted();
                    if(job.bytes+value.length>q.size)throw Error('Update exceeds expected size');
                    let written=0;while(written<value.length)written+=fs.writeSync(fd,value,written,value.length-written);
                    job.bytes+=value.length;
                }
                if(job.bytes!==q.size)throw Error('Incomplete update');
                job.done=true;return;
            } catch(error) {
                if(job.controller.signal.aborted)throw error;
                if(attempt===2)throw error;
            } finally {
                clearTimeout(timer);job.controller.signal.removeEventListener('abort',abort);
                if(reader)await reader.cancel().catch(()=>{});else await response?.body?.cancel().catch(()=>{}); if(fd!==undefined)fs.closeSync(fd);
            }
            await new Promise(resolve=>setTimeout(resolve,1000*(attempt+1)));
        }
    }
    return {
        async releases(){
            const r=await session.fetch(releases,{credentials:'omit',cache:'no-store',signal:AbortSignal.timeout(30000)});
            if(r.status!==200)throw Error(`Update HTTP ${r.status}`);
            const body=await r.text();if(body.length>2000000)throw Error('Release response too large');return {body};
        },
        async start(q){
            if(!/^[a-f0-9]{64}$/.test(q.digest)||!Number.isSafeInteger(q.size)||q.size<=0||q.size>4*1024**3||!q.url.startsWith(repo+'/releases/download/desktop-v')||!trusted(q.url))throw Error('Invalid update');
            for(const j of jobs.values())if(!j.done&&!j.error)throw Error('Download already running');
            fs.mkdirSync(cache,{recursive:true});
            const file=path.join(fs.realpathSync(cache),q.digest+'.part');
            const id=randomUUID(),job={bytes:fs.existsSync(file)?fs.statSync(file).size:0,done:false,error:'',file,controller:new AbortController()};jobs.set(id,job);
            job.promise=run(job,q,file).catch(()=>{job.error=job.controller.signal.aborted?'cancelled':'network';});
            return {id};
        },
        status(id){const j=jobs.get(id);if(!j)throw Error('Unknown download');return {bytes:j.bytes,done:j.done,error:j.error,file:j.done?j.file:''};},
        async cancel(id){const j=jobs.get(id);if(j){j.controller.abort();await j.promise;jobs.delete(id);}return {};},
        dispose(){for(const j of jobs.values())j.controller.abort();}
    };
}
module.exports={createUpdateService,trusted};
