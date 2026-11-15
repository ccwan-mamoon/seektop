"use strict";
const assert = require("node:assert/strict");
const {mkdtemp,readFile,rm} = require("node:fs/promises");
const path = require("node:path");
const os = require("node:os");
const {randomUUID,createCipheriv,createDecipheriv,randomBytes} = require("node:crypto");
const {createAiService,ChatStream,settingsShape,baseEndpoint,imageURL} = require("../browser/ai-analysis.cjs");
const defaults={providers:[{id:"test",name:"Test",baseUrl:"https://provider.invalid/v1",model:"vision-test",vision:true}],selectedProvider:"test",defaultPrompt:"",boardPrompts:{}};
const encryptionKey=randomBytes(32);
const safeStorage={
 async isAsyncEncryptionAvailable(){return true;},
 async encryptStringAsync(text){const iv=randomBytes(12),c=createCipheriv("aes-256-gcm",encryptionKey,iv);return Buffer.concat([iv,c.update(text,"utf8"),c.final(),c.getAuthTag()]);},
 async decryptStringAsync(bytes){const d=createDecipheriv("aes-256-gcm",encryptionKey,bytes.subarray(0,12));d.setAuthTag(bytes.subarray(-16));return {result:Buffer.concat([d.update(bytes.subarray(12,-16)),d.final()]).toString("utf8"),shouldReEncrypt:false};}
};
async function fixture(run){
 const directory=await mkdtemp(path.join(os.tmpdir(),"seektop-ai-"));
 let beforeRequest,handler; const requests=[];
 const session={webRequest:{onBeforeRequest(callback){beforeRequest=callback;}},async fetch(url,options){let cancelled;beforeRequest({url},x=>cancelled=x.cancel);assert.equal(cancelled,false);requests.push({url,options});return handler(url,options);}};
 const service=createAiService({session,safeStorage,directory,defaultSettings:defaults});
 try{await run({service,directory,requests,setHandler:h=>{handler=h;},blocked:url=>{let result;beforeRequest({url},x=>result=x.cancel);return result;}});}finally{service.dispose();await rm(directory,{recursive:true,force:true});}
}
const request=()=>({id:randomUUID(),system:"Analyze supplied data only",messages:[{role:"user",content:[{type:"text",text:"title and opening post"}]}]});
async function completed(service,id){for(let n=0;n<300;n++){const status=service.status(id);if(status.done)return status;await new Promise(r=>setTimeout(r,2));}throw Error("stream timed out");}
(async()=>{
 assert.throws(()=>baseEndpoint("http://remote.invalid"));
 assert.equal(baseEndpoint("http://127.0.0.1:8080/v1/"),"http://127.0.0.1:8080/v1");
 for(const url of ["https://u:p@host.invalid","https://host.invalid/?secret=1"])assert.throws(()=>baseEndpoint(url));
 for(const url of ["http://public.invalid/x","https://127.0.0.1/a","https://10.0.0.1/a","https://[::1]/a"])assert.throws(()=>imageURL(url));
 const previous=settingsShape({...defaults,newKeys:{test:"secret"}},null);
 assert.equal(settingsShape({...defaults,newKeys:{}},previous).providers[0].key,"secret");
 assert.equal(settingsShape({...defaults,providers:[{...defaults.providers[0],baseUrl:"https://new.invalid"}],newKeys:{}},previous).providers[0].key,"");
 let text="";const parser=new ChatStream(t=>text+=t);
 const wire='data: {"choices":[{"delta":{"reasoning_content":"hidden","content":"正文"}}]}\r\n\r\ndata: {"choices":[{"delta":{"content":"✅"},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n';
 for(const char of wire)parser.push(char);parser.finish();assert.equal(text,"正文✅");assert.equal(parser.done,true);
 await fixture(async({service,directory,requests,setHandler,blocked})=>{
  assert.equal((await service.settings()).providers[0].keySet,false);
  await service.save({...defaults,includeImages:true,newKeys:{test:"local-fixture-key"}});
  assert.equal((await service.settings()).includeImages,true);
  const raw=await readFile(path.join(directory,"provider-settings.enc"));assert.equal(raw.includes(Buffer.from("local-fixture-key")),false);
  assert.equal(JSON.stringify(await service.settings()).includes("local-fixture-key"),false);
  setHandler(async()=>new Response(new ReadableStream({start(c){const bytes=new TextEncoder().encode(wire);for(const byte of bytes)c.enqueue(Uint8Array.of(byte));c.close();}}),{headers:{"content-type":"text/event-stream"}}));
  const q=request();await service.start(q);const out=await completed(service,q.id);assert.equal(out.text,"正文✅");assert.equal(out.error,"");
  assert.equal(requests[0].options.credentials,"omit");assert.equal(requests[0].options.headers.Authorization,"Bearer local-fixture-key");
  assert.equal(JSON.stringify(requests[0].options).includes("Cookie"),false);
  assert.equal(blocked("https://steal.invalid/redirect"),true);
  const sent=JSON.parse(requests[0].options.body);assert.equal(sent.messages[0].role,"system");assert.equal(sent.messages[1].content.length,1);
  const bad=request();bad.messages[0].content.push(...Array.from({length:9},()=>({type:"image_url",image_url:{url:"https://public.invalid/i.png"}})));await assert.rejects(service.start(bad));
  setHandler(async()=>new Response("secret provider body",{status:401}));const fail=request();await service.start(fail);assert.equal((await completed(service,fail.id)).error,"AI_HTTP_401");
  setHandler(async()=>new Response('data: {"choices":[{"delta":{"content":"partial"}}]}\n\n',{headers:{"content-type":"text/event-stream"}}));const cut=request();await service.start(cut);const incomplete=await completed(service,cut.id);assert.equal(incomplete.text,"partial");assert.equal(incomplete.error,"AI_INCOMPLETE_RESPONSE");
  setHandler(async(url,options)=>new Promise((resolve,reject)=>{options.signal.addEventListener("abort",()=>reject(Error("aborted")),{once:true});}));
  const cancelled=request();await service.start(cancelled);await assert.rejects(service.save({...defaults,newKeys:{}}),/AI_BUSY/);await service.cancel(cancelled.id);assert.throws(()=>service.status(cancelled.id),/AI_JOB_MISSING/);
  const early=request();await service.cancel(early.id);await assert.rejects(service.start(early),/AI_CANCELLED/);
  // A provider URL change cannot reuse the previous provider's secret.
  await service.save({...defaults,providers:[{...defaults.providers[0],baseUrl:"https://new.invalid"}],newKeys:{}});
  await assert.rejects(service.start(request()),/AI_KEY_REQUIRED/);
 });
 await fixture(async({service,setHandler})=>{
  await service.save({...defaults,newKeys:{test:"fixture-key"}});
  setHandler(async()=>new Response(JSON.stringify({choices:[{message:{content:"non-streamed result"}}]}),{headers:{"content-type":"application/json"}}));
  const q=request();await service.start(q);assert.equal((await completed(service,q.id)).text,"non-streamed result");
 });
 console.log("AI broker: SSE/UTF-8, encryption, credentials, endpoint isolation, limits, incomplete streams and cancellation passed");
})().catch(e=>{console.error(e);process.exitCode=1;});
