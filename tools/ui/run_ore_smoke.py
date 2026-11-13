"""Serial, isolated Ore UI game checks. Every input targets one exact verified project window."""
import argparse
import json
from pathlib import Path
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--target',choices=[f'{v}-{l}' for v in ('1.21.1','1.21.4') for l in ('neoforge','fabric')],default='1.21.1-neoforge')
p.add_argument('--live',action='store_true')
p.add_argument('--full',action='store_true')
p.add_argument('--attach',action='store_true')
a=p.parse_args()
label=a.target+'-ore'+('' if a.live else '-fixtures')
instance=ROOT/'run/ui-review'/label
driver=[sys.executable,str(ROOT/'tools/ui/ore_review.py'),'--target',a.target]+([] if a.live else ['--fixtures'])
helper=[sys.executable,str(ROOT/'tools/ui/review_window.py'),'--target',label]
checks=[];captures=[];process=None
last_data={}
def read():
    global last_data
    for _ in range(10):
        try:
            last_data=json.loads((instance/'mtrnav-ore-audit.json').read_text(encoding='utf-8'))
            return last_data
        except (FileNotFoundError,json.JSONDecodeError):time.sleep(.01)
    return last_data
def wait(predicate,timeout=15):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        d=read()
        if predicate(d):return d
        if process and process.poll() is not None:raise RuntimeError('Client exited; inspect '+str(log))
        time.sleep(.1)
    raise AssertionError('Condition timed out: '+json.dumps(read(),ensure_ascii=False))
def command(*args):subprocess.run(driver+list(args),cwd=ROOT,check=True,capture_output=True)
def send(action,*values):
    if (instance/'mtrnav-ore-background-ready.json').exists():
        subprocess.run([sys.executable,str(ROOT/'tools/ui/ore_native_input.py'),'--target',label,action,*map(str,values)],check=True,capture_output=True)
    else:
        subprocess.run(helper+['focus'],check=True,capture_output=True)
        subprocess.run(helper+[action,*map(str,values)],check=True,capture_output=True)
    time.sleep(.2)
def click(label):command('click',label)
def screen(name):return wait(lambda d:d.get('screen')==name)
def capture(name):
    command('capture',name);captures.append(name);print('Captured',name,flush=True)
def check(name,condition):
    if not condition:raise AssertionError(name)
    checks.append(name);print('Passed',name,flush=True)
def back():command('key','escape');time.sleep(.2)
def hud():
    click('…');screen('NavigationMenuScreen');click('HUD 设置');screen('HudSettingsScreen')
def fixture_flow():
    d=screen('NavigationScreen');check('five actual fixture results',d['journeys']==5 and d['fixtures'])
    body=d['body'];cards=[c for c in d['controls'] if c.get('role')=='result']
    check('five whole cards at 1920x1080 / 2x',sum(c['rect'][1]>=body[1] and c['rect'][1]+c['rect'][3]<=body[1]+body[3] for c in cards)==5)
    capture('query-wide')
    click('途经 1 站 · 点击展开');wait(lambda d:d.get('expandedStops')==1);capture('stops-expanded')
    click('途经 1 站 · 点击收起');wait(lambda d:d.get('expandedStops')==0)
    click('途经 1 站 · 点击展开');click('途经 1 站 · 点击收起')
    check('repeated direct expand/collapse',read()['expandedStops']==0)
    click('开始导航');wait(lambda d:bool(d.get('tracking')));capture('active-start');click('结束导航');wait(lambda d:not d.get('tracking'))
    if a.full:
        subprocess.run([sys.executable,str(ROOT/'tools/ui/record_ore_native.py'),'--target',label,'--kind','press'],check=True,capture_output=True)
        check('press hold / outside cancel / release activation recording',True)
        old=next(c['label'] for c in read()['controls'] if c['label'].endswith(' ▾'))
        command('key','enter')
        wait(lambda d:any(c['label'].endswith(' ▾') and c['label']!=old for c in d.get('controls',[])))
        check('keyboard release activates the focused sort button',True)
    click('全部车站');screen('StationDirectoryScreen');capture('stations');back();screen('NavigationScreen')
    check('directory return preserves query',read()['journeys']==5 and read()['origin']=='a' and read()['destination']=='d')
    if a.full:
        click('车站到发');d=screen('NavigationScreen');wait(lambda d:d.get('arrivals',0)>0);capture('departures')
        bx,by,bw,bh=d['body'];send('click',round((bx+80)*d['scale']),round((by+46)*d['scale']))
        wait(lambda d:any(c['label']=='设置提醒' for c in d.get('controls',[])))
        click('设置提醒');wait(lambda d:d.get('reminder'));click('…');click('提醒');screen('ReminderSettingsScreen');capture('reminder');back();back()
        click('换乘查询')
    hud();capture('hud')
    if a.full:subprocess.run([sys.executable,str(ROOT/'tools/ui/record_ore_native.py'),'--target',label,'--kind','switch'],check=True,capture_output=True)
    if a.full:
        for percent in (100,150,200):
            d=read();slider=next(c for c in d['controls'] if c['label'].endswith('%'))
            x,y,w,h=slider['rect'];send('click',round((x+4+(w-8)*(percent-75)/125)*d['scale']),round((y+h/2)*d['scale']))
            wait(lambda d:d.get('hudPercent')==percent)
        check('HUD native slider 100 / 150 / 200',True)
        click('班次提醒框');capture('hud-reminder');click('始终展开提醒条');click('始终展开提醒条')
        click('导航框');click('恢复所选框默认值')
        d=read();x,y,w,h=d['hudBounds'];scale=d['scale']
        send('hold',round((x+w/2)*scale),round((y+h/2)*scale),round((x+w/2-80)*scale),round((y+h/2+40)*scale))
        wait(lambda d:not d.get('controls'));check('HUD controls hide during drag',True)
        send('release',round((x+w/2-80)*scale),round((y+h/2+40)*scale));wait(lambda d:len(d.get('controls',[]))>0)
        click('恢复所选框默认值')
    click('完成');click('返回');screen('NavigationScreen')
    if a.full:
        click('步行换乘');screen('WalkingManagementScreen');capture('walking')
        click('仅自己');click('新建连接');screen('WalkingEditorScreen');capture('walking-editor');back()
        click('查询设置');screen('WalkingSettingsScreen');capture('walking-settings');back();back();screen('NavigationScreen')
        for scene,name in ((1,'long-names'),(2,'empty-selection'),(3,'no-service'),(4,'departures-scene'),(5,'hud-scene'),(6,'active'),(7,'loading'),(8,'error'),(9,'no-result')):
            command('key','f7');capture(name)
            if scene==5:back()
        command('key','f7')
    command('resize','854','480');d=wait(lambda d:d.get('viewport')==[427,240]);body=d['body']
    cards=[c for c in d['controls'] if c.get('role')=='result']
    check('three whole double-line cards at 854x480',sum(c['rect'][1]>=body[1] and c['rect'][1]+c['rect'][3]<=body[1]+body[3] for c in cards)>=3)
    capture('query-narrow');click('行程详情');capture('details-narrow');hud();capture('hud-narrow');click('预览');capture('hud-preview-narrow');click('返回设置');back();back()
def live_flow():
    d=wait(lambda d:d.get('stations',0)>=3,60)
    if d['screen']=='StationDirectoryScreen':back()
    screen('NavigationScreen')
    # Resizing can trigger the existing automatic nearby-station workflow.
    time.sleep(.6)
    if read()['screen']=='StationDirectoryScreen':back()
    for field,value in (('起点','滨海北'),('终点','甘地')):click(field);command('type',value)
    wait(lambda d:any(c['label']=='查询' and c['active'] for c in d.get('controls',[])))
    click('查询');wait(lambda d:d.get('status')!='mtrnav.searching',30)
    check('real MTR catalog and query path',not read()['fixtures'] and read()['stations']>=3 and bool(read()['origin']) and bool(read()['destination']))
    capture('query-real')

    if read()['journeys']==0:check('no timetable keeps start disabled',all(not c['active'] for c in read()['controls'] if c['label']=='开始导航'))
    click('全部车站');screen('StationDirectoryScreen');capture('stations-real');back();screen('NavigationScreen')
    hud();capture('hud-real');back();back();click('步行换乘');screen('WalkingManagementScreen');capture('walking-real')

if not a.attach:command('prepare')
log=ROOT/'build'/('ore-smoke-'+label+'.log');started=time.time()
output=None
try:
    if not a.attach:
        mc,loader=a.target.split('-',1);output=log.open('w',encoding='utf-8')
        args=['pwsh','-NoProfile','-File',str(ROOT/'dev.ps1'),'-Platform',loader,'-MinecraftVersion',mc,'runClient','-PoreReview','-PoreBackground','-Pquickplay=NavTest','--offline']
        if not a.live:args.append('-PoreFixtures')
        process=subprocess.Popen(args,cwd=ROOT,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        wait(lambda d:(instance/'mtrnav-ore-audit.json').exists() and (instance/'mtrnav-ore-audit.json').stat().st_mtime>=started and bool(d.get('controls')),180)
    command('resize','1920','1080');wait(lambda d:d.get('viewport')==[960,540])
    initial_scale=read().get('enteredScale')
    live_flow() if a.live else fixture_flow()
    for _ in range(4):
        result=subprocess.run([sys.executable,str(ROOT/'tools/ui/ore_native_input.py'),'--target',label,'info'],check=True,capture_output=True,text=True)
        info=json.loads(result.stdout)
        if not info['screen']:break
        back()
    check('closing the workspace restores entry GUI scale',initial_scale is None or info['scale']==initial_scale)
finally:
    if process and process.poll() is None:
        send('close');process.wait(timeout=90)
    if output:output.close()
report=ROOT/'docs/ui-design/ore'/(label+'.json')
report.write_text(json.dumps({'target':a.target,'fixtures':not a.live,'checks':checks,'captures':captures,'exitCode':process.returncode if process else None},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(report,flush=True)
