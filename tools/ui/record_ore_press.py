"""Record only the exact project Ore window and verify release activation from its audit."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--target',default='1.21.1-neoforge-ore-fixtures')
a=p.parse_args()
if a.target not in {f'{v}-{l}-ore{s}' for v in ('1.21.1','1.21.4') for l in ('neoforge','fabric') for s in ('','-fixtures')}:
    p.error('Only a project Ore review target is allowed')
helper=[sys.executable,str(ROOT/'tools/ui/review_window.py'),'--target',a.target]
audit=ROOT/'run/ui-review'/a.target/'mtrnav-ore-audit.json'
out=ROOT/'docs/ui-design/exports/game'/a.target
out.mkdir(parents=True,exist_ok=True)
def read(): return json.loads(audit.read_text(encoding='utf-8'))
def send(action,*args): subprocess.run(helper+[action,*map(str,args)],check=True,capture_output=True)
send('info');send('focus');send('borderless',1920,1080);time.sleep(.4)
data=read()
assert data['screen']=='NavigationScreen',data['screen']
c=next(c for c in data['controls'] if c['label'].endswith(' ▾'))
original=c['label']
x,y,w,h=c['rect'];x=round((x+w/2)*data['scale']);y=round((y+h/2)*data['scale'])
send('move',4,4)
with (out/'press-motion.log').open('w',encoding='utf-8') as log:
    recorder=subprocess.Popen(['ffmpeg','-hide_banner','-y','-f','gdigrab','-framerate','40','-draw_mouse','0',
        '-i','title=MTRNav UI Review '+a.target,'-t','7','-vf','scale=1280:-2','-c:v','libx264','-preset','ultrafast','-pix_fmt','yuv420p',str(out/'press-motion.mp4')],stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    try:
        time.sleep(1)
        send('hold',x,y,x,y);time.sleep(.5)
        held=read();assert held['screen']=='NavigationScreen','A mouse-down must not navigate'
        assert any(c['label']==original for c in held['controls']),'Mouse-down activated the sort'
        pressed=next(c for c in held['controls'] if c['label']==original)
        assert pressed['pressDepth']==1,pressed
        # Moving out and releasing cancels the action; the face returns to rest.
        send('release',4,4);time.sleep(.4)
        assert read()['screen']=='NavigationScreen','Release outside must cancel'
        assert any(c['label']==original for c in read()['controls']),'Outside release activated the sort'
        time.sleep(.7)
        send('hold',x,y,x,y);time.sleep(.7)
        assert read()['screen']=='NavigationScreen'
        send('release',x,y);time.sleep(.5)
        assert read()['screen']=='NavigationScreen'
        assert not any(c['label']==original for c in read()['controls']),'Release inside must change the sort'
        recorder.wait(timeout=15)
        if recorder.returncode: raise RuntimeError('Recording failed; inspect press-motion.log')
        (out/'press-motion.json').write_text(json.dumps({'target':a.target,'source':'gdigrab exact named project window','checks':['held button face sinks','pointer-down does not navigate','outside release cancels','inside release activates once'],'held':pressed},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    finally:
        if recorder.poll() is None: recorder.terminate()
        send("decorated");send("resize",1920,1080)
print(out/'press-motion.mp4')
