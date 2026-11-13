"""Record actual game framebuffers and native widget events without changing desktop focus."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--target',required=True)
p.add_argument('--kind',choices=['press','switch'],required=True)
a=p.parse_args()
root=ROOT/'run/ui-review'/a.target
if not (root/'mtrnav-ore-background-ready.json').exists():p.error('Isolated native QA instance required')
driver=[sys.executable,str(ROOT/'tools/ui/ore_native_input.py'),'--target',a.target]
def send(action,*args):subprocess.run(driver+[action,*map(str,args)],check=True,capture_output=True)
def read():
    for _ in range(15):
        try:return json.loads((root/'mtrnav-ore-audit.json').read_text(encoding='utf-8'))
        except json.JSONDecodeError:time.sleep(.01)
    raise RuntimeError('No complete audit')
d=read()
c=next(c for c in d['controls'] if (c.get('role')=='switch' if a.kind=='switch' else c['label'].endswith(' ▾')))
label=c['label'];x,y,w,h=c['rect'];x=round((x+w/2)*d['scale']);y=round((y+h/2)*d['scale'])
prefix='ore-'+a.kind+'-'+str(time.time_ns())
send('record',prefix,3200)
time.sleep(.1)
if a.kind=='press':
    send('hold',x,y,x,y);time.sleep(.35)
    held=next(c for c in read()['controls'] if c['label']==label)
    assert held['pressDepth']==1
    send('release',4,4);time.sleep(.3)
    assert any(c['label']==label for c in read()['controls'])
    send('hold',x,y,x,y);time.sleep(.35);send('release',x,y);time.sleep(.15)
    assert not any(c['label']==label for c in read()['controls'])
    checks=['pointer-down holds state','held face reaches full travel','outside release cancels','inside release activates']
else:
    initial=c['selected'];send('click',x,y);time.sleep(.65)
    assert next(c for c in read()['controls'] if c['label']==label)['selected']!=initial
    send('click',x,y);time.sleep(.65)
    assert next(c for c in read()['controls'] if c['label']==label)['selected']==initial
    checks=['switch toggles in both directions','preference returns to its initial value','native 250 ms stepped thumb motion']
time.sleep(2)
frames=sorted((root/'screenshots').glob(prefix+'-*.png'))
assert len(frames)>=15,len(frames)
out=ROOT/'docs/ui-design/exports/game'/a.target
out.mkdir(parents=True,exist_ok=True)
video=out/(a.kind+'-motion.mp4')
subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','20','-i',str(root/'screenshots'/(prefix+'-%04d.png')),'-vf','scale=1280:-2','-c:v','libx264','-preset','fast','-pix_fmt','yuv420p',str(video)],check=True)
video.with_suffix('.json').write_text(json.dumps({'target':a.target,'source':'Minecraft Screenshot.grab framebuffer, native Screen events on the game thread','frames':len(frames),'checks':checks,'control':label},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(video)
