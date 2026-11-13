"""Capture a labelled F2 screenshot from the exact project review window."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import struct
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('label')
p.add_argument('--target', default='1.21.4-neoforge')
p.add_argument('--keys', nargs='*', default=[])
p.add_argument('--size', nargs=2, type=int)
a = p.parse_args()
if not all(c.isalnum() or c in '-_' for c in a.label):
    p.error('Use an ordinary filename label')
helper = [sys.executable, str(ROOT/'tools/ui/review_window.py'), '--target', a.target]
def send(*args):
    ready=ROOT/'run/ui-review'/a.target/'mtrnav-ore-background-ready.json'
    if '-ore' in a.target and ready.exists():
        action='capture' if args[0]=='key' and args[1]=='f2' else args[0]
        values=[] if action=='capture' else args[1:]
        subprocess.run([sys.executable,str(ROOT/'tools/ui/ore_native_input.py'),'--target',a.target,action,*map(str,values)],check=True)
    else:subprocess.run(helper+list(map(str,args)),check=True)
if a.size:
    send('resize',*a.size)
    time.sleep(1)
for key in a.keys:
    send('key',key)
    time.sleep(.4)
screens = ROOT/'run/ui-review'/a.target/'screenshots'
before = set(screens.glob('*.png'))
send('key','f2')
deadline = time.monotonic()+15
while time.monotonic()<deadline:
    fresh = set(screens.glob('*.png'))-before
    if fresh:
        src = max(fresh,key=lambda x:x.stat().st_mtime)
        time.sleep(.5)
        out = ROOT/'docs/ui-design/exports/game'/a.target/(a.label+'.png')
        out.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(src,out)
        actual_size = list(struct.unpack('>II',out.read_bytes()[16:24]))
        if a.size and actual_size != a.size:
            raise SystemExit(f'Screenshot size {actual_size} differs from requested {a.size}; reject this capture')
        out.with_suffix('.json').write_text(json.dumps({'target':a.target,'source':str(src.relative_to(ROOT)),
            'requested_client_size':a.size,'actual_image_size':actual_size,
            'keys_before_capture':a.keys},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        review_kind = 'ore' if '-ore' in a.target else 'paradigm' if a.target.endswith('-paradigms') else 'v2'
        audit = ROOT/'run/ui-review'/a.target/f'mtrnav-{review_kind}-audit.json'
        if (a.target.endswith(('-v2', '-paradigms')) or '-ore' in a.target) and audit.exists():
            data=json.loads(audit.read_text(encoding='utf-8'))
            fx,fy,fw,fh=data.get('frame',[0,0,*data['viewport']])
            for control in data['controls']:
                x,y,w,h=control['rect']
                if control.get('role')=='result': continue  # Scroll-clipped result rectangles are intentional.
                if not (x>=fx and y>=fy and x+w<=fx+fw and y+h<=fy+fh):
                    raise SystemExit(f"Control outside frame: {control}")
            out.with_suffix('.audit.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
        print(out)
        break
    time.sleep(.2)
else:
    raise SystemExit('No new in-game screenshot; do not claim a successful capture')
