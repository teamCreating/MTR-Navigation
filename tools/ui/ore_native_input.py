"""Send one test event to the exact isolated game's opt-in native input dispatcher."""
import argparse
import json
from pathlib import Path
import os
import time

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--target',required=True)
p.add_argument('action')
p.add_argument('args',nargs='*')
a=p.parse_args()
allowed={f'{v}-{l}-ore{s}' for v in ('1.21.1','1.21.4') for l in ('neoforge','fabric') for s in ('','-fixtures')}
if a.target not in allowed:p.error('Only isolated project Ore instances are allowed')
root=ROOT/'run/ui-review'/a.target
if not (root/'mtrnav-ore-background-ready.json').is_file():raise SystemExit('Background QA is not enabled in this instance')
values=[' '.join(a.args)] if a.action=='type' else a.args
command_id=time.time_ns()
temp=root/'mtrnav-ore-command.pending'
temp.write_text(json.dumps({'id':command_id,'action':a.action,'args':values}),encoding='utf-8')
os.replace(temp,root/'mtrnav-ore-command.json')
end=time.monotonic()+30
while time.monotonic()<end:
    try:
        ack=json.loads((root/'mtrnav-ore-command-ack.json').read_text(encoding='utf-8'))
        if ack['id']==command_id:
            if ack['error']:raise SystemExit(ack['error'])
            if a.action=='info':print(json.dumps(ack))
            break
    except (FileNotFoundError,json.JSONDecodeError):pass
    time.sleep(.02)
else:raise SystemExit('The isolated game did not acknowledge this event')
