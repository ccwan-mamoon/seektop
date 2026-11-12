import os,subprocess,json,urllib.request,pathlib,tempfile,shutil,threading
root=pathlib.Path(__file__).resolve().parents[1] / 'resources/windows/browser-runtime'
root=pathlib.Path(os.environ.get('SEEKTOP_TEST_RUNTIME',str(root)))
data=pathlib.Path(tempfile.mkdtemp(prefix='nodyssey-browser-test-'))
env=os.environ.copy();env['NODYSSEY_BRIDGE_TOKEN']='local-test-only';env['NODYSSEY_BROWSER_DATA']=str(data)
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
  try:call('info',auth=False);raise AssertionError('unauthorized accepted')
  except urllib.error.HTTPError as e:assert e.code==403
  call('set-cookie',{'url':'https://www.nodeseek.com','cookie':'bridge_test=example; Path=/'})
  assert 'bridge_test=example' in call('cookies',{'url':'https://www.nodeseek.com'})['header']
  assert 'bridge_test' not in call('cookies',{'url':'https://example.com'})['header']
  try:call('fetch',{'url':'https://example.com','method':'GET','forum':True});raise AssertionError('cross origin session accepted')
  except urllib.error.HTTPError as e:assert e.code==500
  verified = call('verify', {
   'id':'11111111-1111-1111-1111-111111111111',
   'origin':'https://www.nodeseek.com',
   'document':"<html><head></head><body><script>__nsBridge.post('token',document.cookie.includes('bridge_test=example')?'shared-session-fixture':'missing-cookie')</script></body></html>"
  })
  assert verified == {'kind':'token','value':'shared-session-fixture'}
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
