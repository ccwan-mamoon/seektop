import os,subprocess,json,urllib.request,pathlib,tempfile,shutil
root=pathlib.Path(__file__).resolve().parents[1] / 'resources/windows/browser-runtime'
data=pathlib.Path(tempfile.mkdtemp(prefix='nodyssey-browser-test-'))
env=os.environ.copy();env['NODYSSEY_BRIDGE_TOKEN']='local-test-only';env['NODYSSEY_BROWSER_DATA']=str(data)
with (data/'stderr.log').open('w') as err:
 p=subprocess.Popen([str(root/'SeekTopBrowser.exe')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=err,env=env,text=True)
 try:
  while True:
   first=p.stdout.readline()
   if not first: raise RuntimeError('Browser did not start')
   if first.strip().startswith('{'):break
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
  call('clear-cookies');assert not call('cookies',{'url':'https://www.nodeseek.com'})['header']
  call('proxy',{'enabled':False,'scope':'EVERYTHING'})
  print('PASS: bridge authentication, origin isolation, cookie operations and proxy reset',flush=True)
 finally:
  p.stdin.close()
  p.terminate()
  try:p.wait(timeout=75)
  except subprocess.TimeoutExpired:p.terminate()

shutil.rmtree(data, ignore_errors=True)
