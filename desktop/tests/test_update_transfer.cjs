const assert=require('node:assert/strict');
const fs=require('node:fs');const os=require('node:os');const path=require('node:path');
const {createUpdateService:createService}=require('../browser/updater.cjs');
function createUpdateService(session,cache){
 return createService({...session,fetch(url,options){
  // Actual Electron 45 rejects manual redirects, including GitHub's asset redirect.
  if(options.redirect==='manual')return Promise.reject(Error('Redirect was cancelled'));
  return session.fetch(url,options);
 },webRequest:{onBeforeRequest(handler){
  for(const [url,cancel] of [['https://github.com/test',false],['https://release-assets.githubusercontent.com/test',false],['https://evil.example/file',true],['http://github.com/test',true]]){
   let result;handler({url},value=>{result=value;});assert.equal(result.cancel,cancel);
  }
 }}},cache);
}
const q={url:'https://github.com/ccwan-mamoon/seektop/releases/download/desktop-v2.0.0/SeekTop-2.0.0-windows-x64.zip',digest:'a'.repeat(64),size:8};
const pause=ms=>new Promise(r=>setTimeout(r,ms));
async function finished(service,id){for(let i=0;i<500;i++){const s=service.status(id);if(s.done||s.error)return s;await pause(10);}throw Error('Timed out');}
async function main(){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'seektop-transfer-'));
 try{
  // A partially downloaded prefix must survive a helper restart and not be counted twice.
  fs.writeFileSync(path.join(root,q.digest+'.part'),'abcd');let seen;
  let service=createUpdateService({fetch:async(url,options)=>{seen=options;return new Response('efgh',{status:206,headers:{'content-range':'bytes 4-7/8','content-length':'4'}});}},root);
  let {id}=await service.start(q);let state=await finished(service,id);assert.equal(state.done,true);assert.equal(seen.headers.Range,'bytes=4-');assert.equal(seen.credentials,'omit');assert.equal(fs.readFileSync(state.file,'utf8'),'abcdefgh');await service.cancel(id);
  // A dropped connection is retried from the prefix already written to disk.
  fs.unlinkSync(path.join(root,q.digest+'.part'));let attempts=0,pulls=0;
  service=createUpdateService({fetch:async(url,options)=>{
   attempts++;
   if(attempts===1)return new Response(new ReadableStream({pull(c){if(pulls++===0)c.enqueue(new TextEncoder().encode('abcd'));else c.error(Error('lost connection'));}}));
   assert.equal(options.headers.Range,'bytes=4-');
   return new Response('efgh',{status:206,headers:{'content-range':'bytes 4-7/8','content-length':'4'}});
  }},root);
  ({id}=await service.start(q));state=await finished(service,id);assert.equal(state.done,true);assert.equal(attempts,2);assert.equal(fs.readFileSync(state.file,'utf8'),'abcdefgh');await service.cancel(id);
  // Ignoring Range is legal: restart the file instead of appending a second archive.
  fs.writeFileSync(path.join(root,q.digest+'.part'),'abcd');
  service=createUpdateService({fetch:async()=>new Response('ABCDEFGH',{headers:{'content-length':'8'}})},root);
  ({id}=await service.start(q));state=await finished(service,id);assert.equal(fs.readFileSync(state.file,'utf8'),'ABCDEFGH');await service.cancel(id);
  // An inconsistent Content-Range must never append untrusted bytes to the prefix.
  fs.writeFileSync(path.join(root,q.digest+'.part'),'abcd');
  service=createUpdateService({fetch:async()=>new Response('efgh',{status:206,headers:{'content-range':'bytes 3-6/8'}})},root);
  ({id}=await service.start(q));state=await finished(service,id);assert.equal(state.done,false);assert.equal(state.error,'network');assert.equal(fs.readFileSync(path.join(root,q.digest+'.part'),'utf8'),'abcd');await service.cancel(id);
  // Cancellation must interrupt a stalled stream, release its writer and retain only received bytes.
  fs.unlinkSync(path.join(root,q.digest+'.part'));
  service=createUpdateService({fetch:async(url,options)=>new Response(new ReadableStream({start(controller){controller.enqueue(new TextEncoder().encode('abcd'));options.signal.addEventListener('abort',()=>controller.error(Error('aborted')));}}))},root);
  ({id}=await service.start(q));for(let i=0;i<100&&service.status(id).bytes<4;i++)await pause(10);
  await service.cancel(id);assert.equal(fs.readFileSync(path.join(root,q.digest+'.part'),'utf8'),'abcd');
  let calls=0;service=createUpdateService({fetch:async()=>{calls++;return new Response(null,{status:302,headers:{location:'https://evil.example/payload'}});}},root);
  ({id}=await service.start(q));state=await finished(service,id);assert.equal(state.done,false);assert.equal(calls,3);await service.cancel(id);
  await assert.rejects(service.start({...q,digest:'../outside'}),/Invalid/);
  await assert.rejects(service.start({...q,url:'https://github.com.evil.example/releases/download/desktop-v1/a.zip'}),/Invalid/);
  console.log('PASS: persisted resume, ignored range fallback, corrupt range refusal, cancellation and redirect boundaries');
 }finally{fs.rmSync(root,{recursive:true,force:true});}
}
main().catch(e=>{console.error(e);process.exitCode=1;});
