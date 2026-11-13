"""Check isolated v2 clients sequentially; never operates on a user's game window."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--targets', nargs='+', default=['1.21.1-fabric', '1.21.4-neoforge', '1.21.4-fabric'])
a = p.parse_args()
allowed = {f'{mc}-{loader}' for mc in ('1.21.1', '1.21.4') for loader in ('neoforge', 'fabric')}
if not set(a.targets) <= allowed:
    p.error('Unsupported project review target')
records = []
report = ROOT/'docs/ui-design/v2/client-smoke.json'
for target in a.targets:
    mc, loader = target.split('-', 1)
    label = target+'-v2'
    helper = [sys.executable, str(ROOT/'tools/ui/review_window.py'), '--target', label]
    log = ROOT/'build'/('v2-smoke-'+target+'.log')
    started = time.time()
    with log.open('w', encoding='utf-8') as output:
        proc = subprocess.Popen(['pwsh', '-NoProfile', '-File', str(ROOT/'dev.ps1'),
            '-Platform', loader, '-MinecraftVersion', mc, 'runClient', '-Pv2Review',
            '-Pquickplay=NavTest', '--offline'], cwd=ROOT, stdout=output, stderr=subprocess.STDOUT)
        try:
            audit = ROOT/'run/ui-review'/label/'mtrnav-v2-audit.json'
            deadline = time.monotonic()+180
            while time.monotonic() < deadline:
                if proc.poll() is not None:
                    raise RuntimeError(f'{target} exited before the review: see {log}')
                if audit.exists() and audit.stat().st_mtime >= started:
                    state = json.loads(audit.read_text(encoding='utf-8'))
                    if state['view'] == 'JOURNEYS' and state['visibleCards'] > 0:
                        break
                time.sleep(2)
            else:
                raise RuntimeError(f'{target}: no fresh review audit in 180 seconds')
            subprocess.run([sys.executable, str(ROOT/'tools/ui/capture_review.py'),
                'graphite-client-smoke', '--target', label, '--size', '1920', '1080'], check=True, cwd=ROOT)
            record = {'target': target, 'status': 'captured-awaiting-image-review',
                'screenshot': f'docs/ui-design/exports/game/{label}/graphite-client-smoke.png',
                'log': str(log.relative_to(ROOT)).replace('\\', '/'), 'label': label}
            records.append(record)
            report.write_text(json.dumps(records, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
            print('Captured:', target, flush=True)
        finally:
            if proc.poll() is None:
                subprocess.run(helper+['close'], check=False, cwd=ROOT)
                proc.wait(timeout=90)
        if proc.returncode:
            raise RuntimeError(f'{target}: client returned {proc.returncode}, see {log}')
    print('Closed normally:', target, flush=True)
