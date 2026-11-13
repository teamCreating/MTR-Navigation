"""Prepare or operate only isolated project-owned Ore UI game instances."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--target', choices=[f'{v}-{l}' for v in ('1.21.1', '1.21.4') for l in ('neoforge', 'fabric')], default='1.21.1-neoforge')
p.add_argument('--fixtures', action='store_true')
p.add_argument('action', choices=['prepare', 'read', 'click', 'key', 'type', 'capture', 'resize', 'close', 'scroll'])
p.add_argument('values', nargs='*')
a = p.parse_args()
label = a.target + '-ore' + ('-fixtures' if a.fixtures else '')
instance = ROOT/'run/ui-review'/label
helper = [sys.executable, str(ROOT/'tools/ui/review_window.py'), '--target', label]

def state():
    for _ in range(10):
        try:return json.loads((instance/'mtrnav-ore-audit.json').read_text(encoding='utf-8'))
        except (FileNotFoundError,json.JSONDecodeError):time.sleep(.02)
    raise RuntimeError('No complete Ore audit snapshot')

def send(action, *args):
    if (instance/'mtrnav-ore-background-ready.json').exists():
        command=[sys.executable,str(ROOT/'tools/ui/ore_native_input.py'),'--target',label]
        result=subprocess.run(command+[action,*map(str,args)],capture_output=True,text=True)
        if result.returncode:raise RuntimeError(result.stderr+result.stdout)
    else:
        subprocess.run(helper+['focus'], check=True, capture_output=True)
        subprocess.run(helper+[action, *map(str,args)], check=True, capture_output=True)

if a.action == 'prepare':
    seed = ROOT/'run/ui-review'/(a.target+'-v2')
    if not (seed/'saves/NavTest').exists():
        seed = ROOT/'run/ui-review'/a.target
    if not (seed/'saves/NavTest').exists():
        raise SystemExit('Missing project-owned NavTest seed')
    instance.mkdir(parents=True, exist_ok=True)
    for stale in ('mtrnav-ore-background-ready.json','mtrnav-ore-command.json','mtrnav-ore-command-ack.json'):
        (instance/stale).unlink(missing_ok=True)
    if not (instance/'saves/NavTest').exists():
        shutil.copytree(seed/'saves/NavTest',instance/'saves/NavTest')
    for name in ('mods','config'):
        if (seed/name).is_dir(): shutil.copytree(seed/name,instance/name,dirs_exist_ok=True)
    options=instance/'options.txt'
    source=options if options.exists() else seed/'options.txt'
    entries=dict(line.split(':',1) for line in source.read_text(encoding='utf-8').splitlines() if ':' in line)
    entries.update(guiScale='3',overrideWidth='1920',overrideHeight='1080',renderDistance='5',simulationDistance='5',maxFps='40',pauseOnLostFocus='false',fullscreen='false')
    options.write_text(''.join(f'{key}:{value}\n' for key,value in entries.items()),encoding='utf-8')
    print(instance)
elif a.action == 'read':
    print(json.dumps(state(),ensure_ascii=False,indent=2))
elif a.action == 'click':
    value=' '.join(a.values)
    data=state()
    matches=[c for c in data['controls'] if c['label']==value]
    if len(matches)!=1: raise SystemExit(f'Expected one control {value}: {matches}')
    c=matches[0]
    if not c['active']: raise SystemExit(f'Disabled: {value}')
    x,y,w,h=c['rect']
    send('click',round((x+w/2)*data['scale']),round((y+h/2)*data['scale']))
    time.sleep(.5)
elif a.action == 'capture':
    send('move',4,4)
    time.sleep(.3)
    subprocess.run([sys.executable,str(ROOT/'tools/ui/capture_review.py'),' '.join(a.values),'--target',label],check=True)
else:
    send(a.action,*a.values)
    time.sleep(.4)
