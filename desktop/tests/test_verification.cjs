const assert = require('node:assert/strict');
const { EventEmitter } = require('node:events');
const { createVerificationService, readWidget } = require('../browser/verification.cjs');
const windows=[];
class Window extends EventEmitter {
 constructor(options){super();this.options=options;this.dead=false;this.result=null;this.loads=0;this.webContents=new EventEmitter();this.webContents.getURL=()=>this.url||'about:blank';this.webContents.executeJavaScript=async script=>{assert.match(script,/turnstile.getResponse/);return this.result};this.webContents.setWindowOpenHandler=()=>{};windows.push(this)}
 removeMenu(){}
 loadURL(url){this.url=url;this.loads++;return Promise.resolve()}
 showInactive(){this.visible=true}
 getNativeWindowHandle(){const b=Buffer.alloc(8);b.writeBigUInt64LE(1234n);return b}
 isDestroyed(){return this.dead}
 destroy(){this.dead=true;this.emit('closed')}
}
const forum={protocol:{handle(){throw Error('Verification must not intercept HTTPS for the shared session')}}};
const service=createVerificationService(forum,Window);
const input={id:'11111111-1111-1111-1111-111111111111',origin:'https://www.nodeseek.com'};
async function main(){
 const vm=require('node:vm');
 const unsafe={querySelector:()=>({type:'password'})};
 assert.equal(vm.runInNewContext(readWidget,{document:{querySelector:()=>({parentElement:unsafe})}}),null,'A container holding credentials must never be revealed');

 for(const origin of ['https://nodeseek.com.evil.example','http://www.nodeseek.com','https://user:secret@www.nodeseek.com','https://www.nodeseek.com/elsewhere'])await assert.rejects(service.verify({...input,origin}));
 const pending=service.verify(input);
 try{
  const w=windows.at(-1);
  assert.equal(w.options.show,false,'A raw login document must never flash before isolation');
  assert.equal(w.options.webPreferences.session,forum);
  assert.equal(w.options.webPreferences.nodeIntegration,false);assert.equal(w.options.webPreferences.sandbox,true);
  assert.equal(w.url,'https://www.nodeseek.com/signIn.html');
  await assert.rejects(service.verify({...input,id:'22222222-2222-2222-2222-222222222222'}));
  let prevented=false;w.webContents.emit('will-redirect',{url:'https://evil.example/',isMainFrame:true,preventDefault(){prevented=true}});assert.equal(prevented,true);
  prevented=false;w.webContents.emit('will-redirect',{url:'https://challenges.cloudflare.com/cdn-cgi/challenge-platform/fixture',isMainFrame:false,preventDefault(){prevented=true}});assert.equal(prevented,false,'CF frame redirects must not be blocked by top-page rules');
  w.result={kind:'token',value:'verification-fixture-token'};
  assert.deepEqual(await pending,w.result);assert.equal(w.dead,true);
  const embedded=service.verify({...input,id:'33333333-3333-3333-3333-333333333333',embedded:true});
  const e=windows.at(-1);assert.equal(e.loads,0,'Hidden hosted widgets must wait for the host before loading');
  assert.equal(service.windowHandle('33333333-3333-3333-3333-333333333333').handle,'1234');
  service.show('33333333-3333-3333-3333-333333333333');service.show('33333333-3333-3333-3333-333333333333');assert.equal(e.loads,1);
  assert.equal(e.visible,undefined,'Starting the document must not expose the login form');
  e.result={kind:'ready',value:''};await new Promise(resolve=>setTimeout(resolve,400));assert.equal(e.visible,true);
  service.close('33333333-3333-3333-3333-333333333333');assert.equal((await embedded).kind,'cancelled');assert.equal(e.dead,true);
  const count=windows.length;service.close('44444444-4444-4444-4444-444444444444');assert.equal((await service.verify({...input,id:'44444444-4444-4444-4444-444444444444'})).kind,'cancelled');assert.equal(windows.length,count);
  const failed=service.verify({...input,id:'55555555-5555-5555-5555-555555555555'});
  windows.at(-1).webContents.emit('did-fail-load',{},-105,'ERR_NAME_NOT_RESOLVED','https://www.nodeseek.com/signIn.html',true);
  assert.equal((await failed).kind,'error');assert.equal(windows.at(-1).dead,true);
  console.log('PASS: official HTTPS document, untouched shared session, navigation restrictions, delayed load, token relay, cancellation and page-load errors');
 }finally{service.close(input.id)}
}
main().catch(e=>{console.error(e);process.exitCode=1});
