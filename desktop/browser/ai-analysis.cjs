"use strict";
const fs = require("node:fs/promises");
const path = require("node:path");
const { randomUUID } = require("node:crypto");

function baseEndpoint(raw) {
    const u = new URL(raw);
    if ((u.protocol !== "https:" && !(u.protocol === "http:" && ["127.0.0.1", "[::1]", "localhost"].includes(u.hostname))) || u.username || u.password || u.search || u.hash) throw Error("AI_INVALID_ENDPOINT");
    return u.href.replace(/\/+$/, "");
}
function imageURL(raw) {
    const u = new URL(raw);
    if (u.protocol !== "https:" || u.username || u.password || raw.length > 8192 || /^(localhost|127\.|10\.|192\.168\.|169\.254\.|\[|172\.(1[6-9]|2\d|3[01])\.)/i.test(u.hostname)) throw Error("AI_INVALID_IMAGE");
    return u.href;
}
function settingsShape(q, previous) {
    if (!Array.isArray(q.providers) || q.providers.length < 1 || q.providers.length > 8) throw Error("AI_INVALID_SETTINGS");
    const ids = new Set();
    const providers = q.providers.map(p => {
        if (!/^[a-z0-9-]{1,40}$/.test(p.id) || ids.has(p.id) || typeof p.name !== "string" || p.name.length > 80 || typeof p.model !== "string" || !p.model.trim() || p.model.length > 128) throw Error("AI_INVALID_SETTINGS");
        ids.add(p.id);
        const baseUrl = baseEndpoint(p.baseUrl);
        const old = previous?.providers?.find(x => x.id === p.id);
        const supplied = q.newKeys?.[p.id];
        if (supplied != null && (typeof supplied !== "string" || supplied.length > 4096 || /[\r\n]/.test(supplied))) throw Error("AI_INVALID_KEY");
        return { id:p.id, name:p.name.trim(), baseUrl, model:p.model.trim(), vision:p.vision === true, thinking:p.thinking === true, key:supplied?.trim() || (old?.baseUrl === baseUrl ? old.key : "") || "" };
    });
    if (!ids.has(q.selectedProvider) || typeof q.defaultPrompt !== "string" || q.defaultPrompt.length > 12000 || !q.boardPrompts || typeof q.boardPrompts !== "object" || Array.isArray(q.boardPrompts) || Object.keys(q.boardPrompts).length > 100) throw Error("AI_INVALID_SETTINGS");
    const boardPrompts = Object.fromEntries(Object.entries(q.boardPrompts).map(([key,value]) => {
        if (!key || key.length > 80 || typeof value !== "string" || value.length > 12000) throw Error("AI_INVALID_SETTINGS");
        return [key,value];
    }));
    return { providers, selectedProvider:q.selectedProvider, includeImages:q.includeImages === true, defaultPrompt:q.defaultPrompt, boardPrompts };
}
const publicSettings = c => ({...c, providers:c.providers.map(({key,...p}) => ({...p,keySet:!!key}))});

function contentText(value) {
    if (typeof value === "string") return value;
    if (Array.isArray(value)) return value.filter(p=>p && ["text","output_text"].includes(p.type)).map(p=>typeof p.text==="string"?p.text:(typeof p.text?.value==="string"?p.text.value:"")).join("");
    return "";
}
function requestBody(provider,messages) {
    const official=new URL(provider.baseUrl).hostname === "api.deepseek.com";
    const body={model:provider.model,messages,stream:true,max_tokens:official&&provider.thinking?32768:8192};
    // DeepSeek enables thinking by default. Explicitly reserve it for the user's opt-in.
    // Other OpenAI-compatible services must not receive DeepSeek-specific parameters.
    if(official)body.thinking={type:provider.thinking?"enabled":"disabled"};
    return body;
}

/** SSE framing, text content parts, reasoning progress and terminal reasons are distinct. */
class ChatStream {
    constructor(onText,onThinking=()=>{}) {
        this.onText=onText; this.onThinking=onThinking; this.buffer=""; this.data=[];
        this.done=false; this.finished=false; this.finishReason=""; this.reasoning=false;
    }
    push(chunk) {
        this.buffer+=chunk;
        if(this.buffer.length>256*1024)throw Error("AI_RESPONSE_LIMIT");
        for(;;) {
            const i=this.buffer.search(/[\r\n]/);if(i<0)return;
            if(this.buffer[i]==="\r"&&i===this.buffer.length-1)return;
            const line=this.buffer.slice(0,i), n=this.buffer[i]==="\r"&&this.buffer[i+1]==="\n"?2:1;
            this.buffer=this.buffer.slice(i+n);
            if(!line)this.event();
            else if(line.startsWith("data:"))this.data.push(line.slice(5).replace(/^ /,""));
        }
    }
    event() {
        if(!this.data.length)return;
        const raw=this.data.join("\n");this.data=[];
        if(raw.trim()==="[DONE]"){this.done=true;return;}
        let q;try{q=JSON.parse(raw);}catch{throw Error("AI_INVALID_RESPONSE");}
        this.accept(q);
    }
    accept(q,stream=true) {
        if(!q||typeof q!=="object"||(q.choices&&!Array.isArray(q.choices)))throw Error("AI_INVALID_RESPONSE");
        if(q.error)throw Error("AI_PROVIDER_ERROR");
        const choice=q.choices?.find(x=>x.index===0)||q.choices?.[0];
        if(!choice)return;
        const delta=stream?(choice.delta||choice.message||{}):(choice.message||{});
        if(choice.finish_reason){this.finished=true;this.finishReason=choice.finish_reason;}
        if(contentText(delta.reasoning_content)||contentText(delta.reasoning)||q.usage?.completion_tokens_details?.reasoning_tokens>0){this.reasoning=true;this.onThinking();}
        const text=contentText(delta.content)||contentText(delta.refusal)||contentText(choice.text);
        if(text)this.onText(text);
    }
    finish() {if(this.buffer.length)this.push("\n\n");this.event();}
    validate(text) {
        if(this.finishReason==="length")throw Error("AI_OUTPUT_LIMIT");
        if(this.finishReason==="content_filter")throw Error("AI_CONTENT_FILTER");
        if(["tool_calls","function_call"].includes(this.finishReason))throw Error("AI_UNSUPPORTED_RESPONSE");
        if(!text.trim())throw Error(this.reasoning?"AI_REASONING_ONLY":"AI_EMPTY_RESPONSE");
    }
}

function createAiService({session,safeStorage,directory,defaultSettings}) {
    let current=null, saving=false, requesting=false, loading=null;
    const jobs=new Map(), cancelled=new Set(), destinations=new Set();
    session.webRequest.onBeforeRequest((details,callback) => callback({cancel:!destinations.has(details.url)}));
    const configFile=path.join(directory,"provider-settings.enc");
    async function persist(c) {
        if(!await safeStorage.isAsyncEncryptionAvailable()) throw Error("AI_ENCRYPTION_UNAVAILABLE");
        const bytes=await safeStorage.encryptStringAsync(JSON.stringify(c));
        await fs.mkdir(directory,{recursive:true});
        const temp=path.join(directory,"settings-"+randomUUID()+".tmp");
        try { await fs.writeFile(temp,bytes,{mode:0o600}); await fs.rename(temp,configFile); }
        finally { await fs.rm(temp,{force:true}).catch(()=>{}); }
    }
    async function loadUnlocked() {
        if(current) return current;
        try {
            const bytes=await fs.readFile(configFile);
            if(bytes.length>2*1024*1024) throw Error("AI_SETTINGS_LIMIT");
            const decoded=await safeStorage.decryptStringAsync(bytes);
            const parsed=JSON.parse(decoded.result);
            current=settingsShape({...parsed,newKeys:Object.fromEntries(parsed.providers.map(p=>[p.id,p.key]))},null);
            if(decoded.shouldReEncrypt) await persist(current);
        } catch(e) { if(e.code==="ENOENT") current=settingsShape({...defaultSettings,newKeys:{}},null); else throw Error("AI_SETTINGS_UNAVAILABLE"); }
        return current;
    }
    async function load() {
        if(current) return current;
        if(!loading) loading=loadUnlocked().finally(()=>{loading=null;});
        return loading;
    }
    async function fetchAt(url,options) {
        // Electron 45 cannot expose manual redirects. Only this exact destination is
        // allowed while a request is active; redirects are rejected before I/O.
        destinations.add(url);
        try { return await session.fetch(url,{...options,credentials:"omit",redirect:"follow",cache:"no-store"}); }
        finally { /* Kept through body consumption; callers release it in finally. */ }
    }
    function selected(c,id) { const p=c.providers.find(p=>p.id===id); if(!p?.key) throw Error("AI_KEY_REQUIRED"); return p; }
    async function run(job,provider,messages) {
        const url=provider.baseUrl+"/chat/completions";
        let reader;
        const timeout=setTimeout(()=>{job.timeout=true;job.controller.abort();},180000);
        try {
            const response=await fetchAt(url,{method:"POST",headers:{"Content-Type":"application/json","Authorization":"Bearer "+provider.key},body:JSON.stringify(requestBody(provider,messages)),signal:job.controller.signal});
            if(response.status!==200) { await response.body?.cancel(); throw Error("AI_HTTP_"+response.status); }
            const parser=new ChatStream(text=>{
                job.text+=text;job.phase="answering";
                if(job.text.length>200000)throw Error("AI_RESPONSE_LIMIT");
            },()=>{if(!job.text)job.phase="thinking";});
            reader=response.body?.getReader();if(!reader)throw Error("AI_EMPTY_RESPONSE");
            const decoder=new TextDecoder();let bytes=0, probe="", json="";
            let streaming=(response.headers.get("content-type")||"").toLowerCase().includes("text/event-stream")?true:null;
            function consume(text) {
                if(streaming===null){
                    probe+=text;const trimmed=probe.trimStart();
                    if(trimmed.startsWith("{"))streaming=false;
                    else if(/^(data:|event:|:)/.test(trimmed))streaming=true;
                    else if(probe.length>4096)throw Error("AI_INVALID_RESPONSE");
                    else return;
                    text=probe;probe="";
                }
                if(streaming)parser.push(text);else json+=text;
            }
            for(;;){
                const {done,value}=await reader.read();if(done)break;
                bytes+=value.length;if(bytes>16*1024*1024)throw Error("AI_RESPONSE_LIMIT");
                consume(decoder.decode(value,{stream:true}));
                if(streaming&&(parser.done||parser.finished))break;
            }
            consume(decoder.decode());
            if(streaming){if(!parser.done&&!parser.finished)parser.finish();if(!parser.done&&!parser.finished)throw Error("AI_INCOMPLETE_RESPONSE");}
            else {
                let q;try{q=JSON.parse(json||probe);}catch{throw Error("AI_INVALID_RESPONSE");}
                parser.accept(q,false);
            }
            parser.validate(job.text);
        } catch(e) {
            job.error=job.controller.signal.aborted?(job.timeout?"AI_TIMEOUT":"AI_CANCELLED"):(/^AI_[A-Z0-9_]+$/.test(e.message)?e.message:"AI_NETWORK_ERROR");
        } finally {clearTimeout(timeout);await reader?.cancel().catch(()=>{});destinations.delete(url);job.done=true;}
    }
    return {
        async settings(){return publicSettings(await load());},
        async save(q){if(saving||requesting||[...jobs.values()].some(j=>!j.done))throw Error("AI_BUSY");saving=true;try{const next=settingsShape(q,await load());await persist(next);current=next;return publicSettings(next);}finally{saving=false;}},
        async test(id){
            if(saving||requesting||[...jobs.values()].some(j=>!j.done))throw Error("AI_BUSY");
            requesting=true;let url;
            try {const p=selected(await load(),id);url=p.baseUrl+"/models";
            try {const r=await fetchAt(url,{headers:{Authorization:"Bearer "+p.key},signal:AbortSignal.timeout(20000)});await r.body?.cancel();if(r.status!==200)throw Error("AI_HTTP_"+r.status);return {};}
            finally{destinations.delete(url);}
            }finally{requesting=false;}
        },
        async start(q){
            if(saving||requesting||[...jobs.values()].some(j=>!j.done))throw Error("AI_BUSY");
            requesting=true;
            try {
            if(typeof q.system!=="string"||q.system.length>24000||!Array.isArray(q.messages)||q.messages.length>12)throw Error("AI_INVALID_REQUEST");
            const c=await load(),p=selected(c,c.selectedProvider);
            let length=0, imageCount=0;
            const messages=[{role:"system",content:q.system},...q.messages.map(m=>{
                if(!["user","assistant"].includes(m.role))throw Error("AI_INVALID_REQUEST");
                if(typeof m.content==="string"){length+=m.content.length;return {role:m.role,content:m.content};}
                if(m.role!=="user"||!Array.isArray(m.content)||m.content.length>9)throw Error("AI_INVALID_REQUEST");
                return {role:m.role,content:m.content.map(part=>{
                    if(part.type==="text"&&typeof part.text==="string"){length+=part.text.length;return {type:"text",text:part.text};}
                    if(part.type==="image_url"&&p.vision){if(++imageCount>8)throw Error("AI_REQUEST_LIMIT");return {type:"image_url",image_url:{url:imageURL(part.image_url.url)}};}
                    throw Error("AI_VISION_UNAVAILABLE");
                })};
            })];
            if(length>200000)throw Error("AI_REQUEST_LIMIT");
            const id=q.id;
            if(typeof id!=="string"||!/^[a-f0-9-]{36}$/.test(id))throw Error("AI_INVALID_REQUEST");
            if(cancelled.delete(id))throw Error("AI_CANCELLED");
            for(const [oldId,oldJob] of jobs)if(oldJob.done)jobs.delete(oldId);
            const job={text:"",phase:"waiting",done:false,error:"",controller:new AbortController()};jobs.set(id,job);
            job.promise=run(job,p,messages);return {id};
            } finally {requesting=false;}
        },
        status(id){const j=jobs.get(id);if(!j)throw Error("AI_JOB_MISSING");return {text:j.text,phase:j.phase,done:j.done,error:j.error};},
        async cancel(id){const j=jobs.get(id);if(j){j.controller.abort();await j.promise;jobs.delete(id);}else{if(cancelled.size>100)cancelled.clear();cancelled.add(id);}return {};},
        dispose(){for(const j of jobs.values())j.controller.abort();current=null;},
    };
}
module.exports={createAiService,ChatStream,settingsShape,baseEndpoint,imageURL,requestBody};
