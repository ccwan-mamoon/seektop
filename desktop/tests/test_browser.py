import os,subprocess,json,urllib.request,pathlib,tempfile,shutil,threading
root=pathlib.Path(__file__).resolve().parents[1] / 'resources/windows/browser-runtime'
root=pathlib.Path(os.environ.get('SEEKTOP_TEST_RUNTIME',str(root)))
data=pathlib.Path(tempfile.mkdtemp(prefix='nodyssey-browser-test-'))
env=os.environ.copy();env['NODYSSEY_BRIDGE_TOKEN']='local-test-only';env['NODYSSEY_BROWSER_DATA']=str(data/'browser')
with (data/'stderr.log').open('w') as err:
 p=subprocess.Popen([str(root/'SeekTopBrowser.exe')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=err,env=env,text=True)
 startup_timer=threading.Timer(25,p.terminate);startup_timer.start()
 try:
  while True:
   first=p.stdout.readline()
   if not first: raise RuntimeError('Browser did not start')
   if first.strip().startswith('{'):break
  startup_timer.cancel()
  port=json.loads(first)['port'];print('Bridge port',port, 'process',p.poll(),flush=True)
  def call(path,body={},auth=True):
   req=urllib.request.Request(f'http://127.0.0.1:{port}/{path}',data=json.dumps(body).encode(),headers={'Authorization':'Bearer local-test-only' if auth else ''})
   return json.load(urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req,timeout=75))
  assert call('info')['userAgent']
  # Real Chromium integration: both metadata and binary requests must use the app proxy.
  from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
  import time
  proxy_hosts=[]
  class RejectProxy(BaseHTTPRequestHandler):
   def do_CONNECT(self):
    proxy_hosts.append(self.path);self.send_response(502);self.end_headers()
   def log_message(self,*args):pass
  proxy=ThreadingHTTPServer(('127.0.0.1',0),RejectProxy)
  threading.Thread(target=proxy.serve_forever,daemon=True).start()
  try:
   call('proxy',{'enabled':True,'type':'HTTP','host':'127.0.0.1','port':proxy.server_port,'scope':'EVERYTHING'})
   try:call('update-releases');raise AssertionError('Proxy rejection should fail update metadata')
   except urllib.error.HTTPError as e:assert e.code==500
   assert 'api.github.com:443' in proxy_hosts,'Update checks bypassed configured proxy'
   request=call('update-start',{'url':'https://github.com/ccwan-mamoon/seektop/releases/download/desktop-v2.0.0/SeekTop-2.0.0-windows-x64.zip','digest':'a'*64,'size':8})
   for _ in range(200):
    status=call('update-status',request)
    if status['error']:break
    time.sleep(.05)
   assert status['error'] and not status['done']
   assert 'github.com:443' in proxy_hosts,'Update downloads bypassed configured proxy'
   call('update-cancel',request)
   print('PASS: actual Chromium update metadata and downloads honor the app proxy',flush=True)
  finally:
   call('proxy',{'enabled':False,'scope':'EVERYTHING'})
   proxy.shutdown();proxy.server_close()
  try:call('info',auth=False);raise AssertionError('unauthorized accepted')
  except urllib.error.HTTPError as e:assert e.code==403
  call('set-cookie',{'url':'https://www.nodeseek.com','cookie':'bridge_test=example; Path=/'})
  assert 'bridge_test=example' in call('cookies',{'url':'https://www.nodeseek.com'})['header']
  assert 'bridge_test' not in call('cookies',{'url':'https://example.com'})['header']
  try:call('fetch',{'url':'https://example.com','method':'GET','forum':True});raise AssertionError('cross origin session accepted')
  except urllib.error.HTTPError as e:assert e.code==500
  from concurrent.futures import ThreadPoolExecutor
  import time
  with ThreadPoolExecutor(max_workers=1) as executor:
   request_id='11111111-1111-1111-1111-111111111111'
   pending=executor.submit(call,'verify',{'id':request_id,'origin':'https://www.nodeseek.com','embedded':True})
   for _ in range(50):
    if call('verification-window',{'id':request_id})['handle']:break
    time.sleep(.1)
   else:raise AssertionError('Verification native window did not appear')
   call('verification-close',{'id':request_id})
   assert pending.result(timeout=10)['kind']=='cancelled'
  # Real Chromium regression: forwarding mobile fetch metadata rejected every XHR.
  from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
  class Echo(BaseHTTPRequestHandler):
   def do_GET(self):
    self.send_response(200);self.end_headers();self.wfile.write(b'{"ok":true}')
   def log_message(self,*args):pass
  echo=ThreadingHTTPServer(('127.0.0.1',0),Echo)
  threading.Thread(target=echo.serve_forever,daemon=True).start()
  try:
   response=call('fetch',{'url':f'http://127.0.0.1:{echo.server_port}/fixture','method':'GET','forum':False,'headers':{'Sec-Fetch-Dest':'empty','Sec-Fetch-Mode':'cors','Sec-Fetch-Site':'same-origin','X-Requested-With':'XMLHttpRequest'}})
   assert response['code']==200 and json.loads(response['body'])=={'ok':True},'XHR metadata must not cause ERR_INVALID_ARGUMENT'
  finally:echo.shutdown();echo.server_close()
  call('clear-cookies');assert not call('cookies',{'url':'https://www.nodeseek.com'})['header']
  call('proxy',{'enabled':False,'scope':'EVERYTHING'})
  print('PASS: bridge authentication, origin isolation, cookie operations and proxy reset',flush=True)
 finally:
  startup_timer.cancel()
  p.stdin.close()
  p.terminate()
  try:p.wait(timeout=75)
  except subprocess.TimeoutExpired:p.terminate()

shutil.rmtree(data, ignore_errors=True)
