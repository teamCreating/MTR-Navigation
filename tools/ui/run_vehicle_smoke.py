"""Real MTR vehicle-condition checks in explicitly isolated project worlds; no OS input."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--target',required=True,choices=[f'{v}-{l}' for v in ('1.21.1','1.21.4') for l in ('neoforge','fabric')])
p.add_argument('--attach',action='store_true')
a=p.parse_args()
label=a.target+'-ore';instance=ROOT/'run/ui-review'/label
native=[sys.executable,str(ROOT/'tools/ui/ore_native_input.py'),'--target',label]
driver=[sys.executable,str(ROOT/'tools/ui/ore_review.py'),'--target',a.target]
checks=[];captures=[];process=None;output=None;last={}

def read():
    global last
    try:last=json.loads((instance/'mtrnav-ore-audit.json').read_text(encoding='utf-8'))
    except (FileNotFoundError,json.JSONDecodeError):pass
    return last

def wait(predicate,timeout=25):
    end=time.monotonic()+timeout
    while time.monotonic()<end:
        d=read()
        if predicate(d):return d
        if process and process.poll() is not None:raise RuntimeError('Game exited: '+str(log))
        time.sleep(.15)
    raise AssertionError('Timed out: '+json.dumps(read(),ensure_ascii=False)[:1500])

def command(*args):
    result=subprocess.run(driver+list(args),cwd=ROOT,capture_output=True,text=True)
    if result.returncode:raise RuntimeError(result.stderr+result.stdout)

def send(action,*args):
    result=subprocess.run(native+[action,*map(str,args)],cwd=ROOT,capture_output=True,text=True)
    if result.returncode:raise RuntimeError(result.stderr+result.stdout)
    time.sleep(.2)
    return result.stdout

def click(label):command('click',label)
def back():send('key','escape')
def check(name,value):
    if not value:raise AssertionError(name)
    checks.append(name);print('Passed',name,flush=True)
def capture(name):
    command('capture',name);captures.append(name);print('Captured',name,flush=True)
def screen(name):return wait(lambda d:d.get('screen')==name)
def query():
    click('查询');wait(lambda d:d.get('status')!='mtrnav.searching' and d.get('journeys',0)>0)
def selector():click('车型条件');screen('VehicleSelectionScreen')
def choose(model):
    d=read();control=next(c for c in d['controls'] if c.get('resourceId')==model)
    click(control['label'])

def flow():
    send('resize',1920,1080);wait(lambda d:d.get('viewport')==[960,540]);time.sleep(.8)
    if read().get('screen')=='StationDirectoryScreen':back()
    screen('NavigationScreen');wait(lambda d:len(d.get('fleet',[]))==6 and d.get('vehicleSupported'))
    entered=read()['enteredScale']
    check('real catalog, route categories and observed/planned model IDs',not read()['fixtures'] and set(read()['routeTypes'])=={'NORMAL','LIGHT_RAIL','HIGH_SPEED'})
    for field,text in [('起点','车型测试起点'),('终点','车型测试终点')]:click(field);send('type',text)
    query();d=read()
    check('five real usable journeys with actual and planned consists',d['journeys']==5 and all(any(s['cars'] for s in legs) for legs in d['journeyStocks']))
    cards=[c for c in d['controls'] if c.get('role')=='result'];body=d['body']
    check('five complete cards and ordered icon chains at 1920x1080 / 2x',len(cards)==5 and all('chain' in c and c['rect'][1]>=body[1] and c['rect'][1]+c['rect'][3]<=body[1]+body[3] for c in cards))
    capture('vehicle-query-real')
    click('查看编组顺序与车型 ID');capture('vehicle-consist-real');click('收起编组详情')
    selector();capture('vehicle-search-focused');send('type','sp1900')
    wait(lambda d:all(c.get('resourceId','').startswith('sp1900') for c in d['controls'] if c.get('resourceId')))
    check('typed model search filters the native list',all(c.get('resourceId','').startswith('sp1900') for c in read()['controls'] if c.get('resourceId')))
    capture('vehicle-search-typed')
    for _ in range(6):send('key','backspace')
    wait(lambda d:sum(bool(c.get('resourceId')) for c in d['controls'])==6)
    choose('sp1900_cab_1');click('必须乘坐');capture('vehicle-selector-real');click('应用');query();d=read()
    check('strict query matches any ridden leg using real server search',d['vehicleMode']=='REQUIRE' and all(any('sp1900_cab_1' in s['cars'] for s in legs) for legs in d['journeyStocks']))
    capture('vehicle-require-real')
    selector();click('优先乘坐');click('应用');query();d=read()
    check('preferred model is ranked first',any('sp1900_cab_1' in s['cars'] for s in d['journeyStocks'][0]))
    capture('vehicle-prefer-real')
    selector();send('resize',854,480);wait(lambda d:d.get('viewport')==[427,240] and d.get('screen')=='VehicleSelectionScreen')
    check('selection survives resize',any(c.get('resourceId')=='sp1900_cab_1' and c.get('selected') for c in read()['controls']) or read()['vehicleModels']==['sp1900_cab_1'])
    capture('vehicle-selector-small');send('resize',1920,1080);wait(lambda d:d.get('viewport')==[960,540] and d.get('screen')=='VehicleSelectionScreen');click('清空条件');click('应用');query()
    check('clearing conditions restores normal search',read()['vehicleMode']=='NONE')
    selector();choose('sp1900_trailer');click('必须乘坐');click('应用');query()
    check('mixed-consist trailer matches regardless of cab model',all(any('sp1900_trailer' in s['cars'] for s in legs) for legs in read()['journeyStocks']))
    click('开始导航');wait(lambda d:bool(d.get('tracking')));capture('vehicle-active-real')
    click('结束导航');wait(lambda d:not d.get('tracking'))
    click('车站到发');click('查看到发');wait(lambda d:d.get('arrivals',0)>0);capture('vehicle-board-real')
    check('departure board remains populated while strict filter is active',read()['vehicleMode']=='REQUIRE' and read()['arrivals']>0)
    click('…');click('HUD 设置');screen('HudSettingsScreen');capture('vehicle-hud-real')
    for percent in (100,150,200):
        d=read();slider=next(c for c in d['controls'] if c['label'].endswith('%'));x,y,w,h=slider['rect'];send('click',round((x+4+(w-8)*(percent-75)/125)*d['scale']),round((y+h/2)*d['scale']));wait(lambda d:d['hudPercent']==percent)
        nav,rem=read()['hudBounds'],read()['reminderBounds'];vw,vh=read()['viewport']
        check(f'HUD {percent}% respects edges and avoids the second HUD',all(0<=b[0] and 0<=b[1] and b[0]+b[2]<=vw and b[1]+b[3]<=vh for b in [nav,rem]) and (nav[0]+nav[2]<=rem[0] or rem[0]+rem[2]<=nav[0] or nav[1]+nav[3]<=rem[1] or rem[1]+rem[3]<=nav[1]))
    click('恢复所选框默认值')
    for _ in range(5):
        info=json.loads(send('info'))
        if not info['screen']:break
        back()
    info=json.loads(send('info'));check('closing screens restores entry GUI scale',not info['screen'] and info['scale']==entered)

if not a.attach:
    command('prepare');world=instance/'saves/VehicleReview'
    if not world.exists():
        fixture=ROOT/'build/fixtures/vehicles-release'
        if not fixture.is_dir():raise RuntimeError('Generate the operational vehicle fixture first')
        shutil.copytree(instance/'saves/NavTest',world,ignore=shutil.ignore_patterns('mtr','session.lock'))
        shutil.copytree(fixture,world/'mtr')
log=ROOT/'build'/('vehicle-smoke-'+a.target+'.log');started=time.time()
try:
    if not a.attach:
        mc,loader=a.target.split('-',1);output=log.open('w',encoding='utf-8')
        args=['pwsh','-NoProfile','-File',str(ROOT/'dev.ps1'),'-Platform',loader,'-MinecraftVersion',mc,'runClient','-PoreReview','-PoreBackground','-Pquickplay=VehicleReview','--offline']
        process=subprocess.Popen(args,cwd=ROOT,stdout=output,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        wait(lambda d:(instance/'mtrnav-ore-audit.json').exists() and (instance/'mtrnav-ore-audit.json').stat().st_mtime>=started and bool(d.get('controls')),180)
    flow()
finally:
    if process and process.poll() is None:send('close');process.wait(timeout=90)
    if output:output.close()
report=ROOT/'docs/validation/vehicles';report.mkdir(parents=True,exist_ok=True)
(report/(a.target+'.json')).write_text(json.dumps({'target':a.target,'world':'VehicleReview','fixtures':False,'checks':checks,'captures':captures,'exitCode':process.returncode if process else None},ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(report/(a.target+'.json'),flush=True)
