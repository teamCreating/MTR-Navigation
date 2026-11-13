"""Capture README images from the opt-in fictional Mosslight Isles game scenario."""
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT=Path(__file__).resolve().parents[2]
LABEL='1.21.1-neoforge-ore-fixtures';INSTANCE=ROOT/'run/ui-review'/LABEL
DRIVER=[sys.executable,str(ROOT/'tools/ui/ore_review.py'),'--target','1.21.1-neoforge','--fixtures']
NATIVE=[sys.executable,str(ROOT/'tools/ui/ore_native_input.py'),'--target',LABEL]
OUT=ROOT/'docs/screenshots/0.1.1';OUT.mkdir(parents=True,exist_ok=True)
process=None;last={};captures=[]

def read():
    global last
    try:last=json.loads((INSTANCE/'mtrnav-ore-audit.json').read_text(encoding='utf-8'))
    except (FileNotFoundError,json.JSONDecodeError):pass
    return last

def wait(predicate,timeout=20):
    until=time.monotonic()+timeout
    while time.monotonic()<until:
        d=read()
        if predicate(d):return d
        if process and process.poll() is not None:raise RuntimeError('Game exited; see build/readme-screenshots.log')
        time.sleep(.15)
    raise AssertionError(json.dumps(read(),ensure_ascii=False)[:1800])

def command(*args):
    r=subprocess.run(DRIVER+list(args),cwd=ROOT,capture_output=True,text=True)
    if r.returncode:raise RuntimeError(r.stderr+r.stdout)
def send(*args):
    r=subprocess.run(NATIVE+list(map(str,args)),cwd=ROOT,capture_output=True,text=True)
    if r.returncode:raise RuntimeError(r.stderr+r.stdout)
    time.sleep(.2)
def click(value):command('click',value)
def screen(value):return wait(lambda d:d.get('screen')==value)
def back():send('key','escape')
def capture(name):
    command('capture','readme-'+name)
    source=ROOT/'docs/ui-design/exports/game'/LABEL/('readme-'+name+'.png')
    shutil.copyfile(source,OUT/(name+'.png'))
    audit=json.loads(source.with_suffix('.audit.json').read_text(encoding='utf-8'))
    assert audit['fixtures'] and audit['skin']=='ORE'
    captures.append({'file':name+'.png','screen':audit['screen'],'viewport':audit['viewport']})
    print('Captured',name,flush=True)
def station_row(name):
    click('搜索全部车站');send('type',name)
    time.sleep(.3)
    d=read();field=next(c for c in d['controls'] if 'value' in c)
    x,y,w,h=field['rect'];send('click',round((x+40)*d['scale']),round((y+78)*d['scale']))
def choose_station(button,name):
    click(button);screen('StationDirectoryScreen');station_row(name);click('使用所选车站');screen('WalkingEditorScreen')

command('prepare');started=time.time()
with (ROOT/'build/readme-screenshots.log').open('w',encoding='utf-8') as log:
    try:
        args=['pwsh','-NoProfile','-File',str(ROOT/'dev.ps1'),'-Platform','neoforge','-MinecraftVersion','1.21.1','runClient','-PoreReview','-PoreFixtures','-PoreBackground','-Pquickplay=NavTest','--offline']
        process=subprocess.Popen(args,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
        wait(lambda d:(INSTANCE/'mtrnav-ore-audit.json').exists() and (INSTANCE/'mtrnav-ore-audit.json').stat().st_mtime>=started and d.get('screen')=='NavigationScreen',180)
        send('resize',1920,1080);wait(lambda d:d.get('viewport')==[960,540]);capture('journey-query')
        click('车型条件');screen('VehicleSelectionScreen');row=next(c for c in read()['controls'] if c.get('resourceId')=='sp1900_cab_1');click(row['label']);click('优先乘坐');capture('vehicle-selection');back();screen('NavigationScreen')
        click('全部车站');screen('StationDirectoryScreen');station_row('风铃渡');click('搜索全部车站');send('key','backspace');send('key','backspace');send('key','backspace');capture('station-directory');back();screen('NavigationScreen')
        click('车站到发');wait(lambda d:d.get('arrivals',0)>0);capture('departures')
        click('…');click('HUD 设置');screen('HudSettingsScreen');capture('hud-settings');back();back();screen('NavigationScreen')
        click('换乘查询');click('步行换乘');screen('WalkingManagementScreen');click('仅自己');click('新建连接');screen('WalkingEditorScreen')
        choose_station('选择出发车站…','风铃渡');choose_station('选择到达车站…','星沼湾')
        click('分钟');send('type','3');capture('walking-editor')
        for _ in range(6):
            info=subprocess.run(NATIVE+['info'],capture_output=True,text=True,check=True)
            if not json.loads(info.stdout)['screen']:break
            back()
    finally:
        if process and process.poll() is None:send('close');process.wait(timeout=90)
metadata={'version':'0.1.1','world':'苔灯群岛 / Mosslight Isles','fictional':True,'captureMethod':'Actual Minecraft framebuffer; native game-thread input; no image retouching','target':'Minecraft 1.21.1 / NeoForge with Modern UI','stations':['苔灯港','风铃渡','雾松丘','星沼湾'],'lines':['星汐快线','萤环线','岚湾轻轨'],'captures':captures,'exitCode':process.returncode}
(OUT/'capture.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(OUT,flush=True)
