"""Verify final Ore artifacts against measured game captures and actual regression reports."""
import hashlib
import html
import json
from pathlib import Path
import struct
import xml.etree.ElementTree as ET
import zipfile

ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'docs/ui-design/ore'
targets=[f'{v}-{l}' for v in ('1.21.1','1.21.4') for l in ('neoforge','fabric')]
screens=[];clients=[]
for target in targets:
    for fixture in (True,False):
        label=target+'-ore'+('-fixtures' if fixture else '')
        report=json.loads((OUT/(label+'.json')).read_text(encoding='utf-8'))
        assert report['exitCode']==0,report
        clients.append(report)
        for name in report['captures']:
            path=ROOT/'docs/ui-design/exports/game'/label/(name+'.png')
            raw=path.read_bytes();assert raw[:8]==b'\x89PNG\r\n\x1a\n'
            size=list(struct.unpack('>II',raw[16:24]))
            meta=json.loads(path.with_suffix('.json').read_text(encoding='utf-8'))
            audit=json.loads(path.with_suffix('.audit.json').read_text(encoding='utf-8'))
            assert size==meta['actual_image_size']
            assert audit['fixtures']==fixture and audit['skin']=='ORE'
            assert audit['font']=='minecraft-current-default'
            fx,fy,fw,fh=audit.get('frame',[0,0,*audit['viewport']])
            for c in audit['controls']:
                if c.get('role')=='result':continue
                x,y,w,h=c['rect'];assert x>=fx and y>=fy and x+w<=fx+fw and y+h<=fy+fh,(path,c)
            if name in ('query-wide','query-narrow'):
                assert size==([1920,1080] if name=='query-wide' else [854,480])
                bx,by,bw,bh=audit['body'];cards=[c for c in audit['controls'] if c.get('role')=='result']
                count=sum(c['rect'][1]>=by and c['rect'][1]+c['rect'][3]<=by+bh for c in cards)
                assert count>=(5 if name=='query-wide' else 3),(path,count)
            if not fixture and name=='query-real':
                assert audit['stations']>=3 and audit['origin'] and audit['destination']
                if audit['journeys']==0:assert all(not c['active'] for c in audit['controls'] if c['label']=='开始导航')
            screens.append({'target':target,'fixtures':fixture,'name':name,'size':size,'file':str(path.relative_to(OUT.parent)).replace('\\','/')})

artifacts=[]
manifest=json.loads((ROOT/'build/multiversion/manifest.json').read_text(encoding='utf-8-sig'))
for entry in manifest:
    path=ROOT/'build/multiversion'/entry['file'];raw=path.read_bytes()
    assert hashlib.sha256(raw).hexdigest()==entry['sha256']
    with zipfile.ZipFile(path) as jar:
        for cls in ('client/OreUi','client/NavigationScreen','client/HudSettingsScreen','core/UiPress','core/UiToggle'):
            assert 'io/github/teamcreating/mtrnav/'+cls+'.class' in jar.namelist()
        design=json.loads(jar.read('assets/mtrnav/ui/design.json'))
        assert design['version']==3 and design['font']=='minecraft-current-default'
    artifacts.append(entry)
assert len(artifacts)==4

suites=[]
for mc in ('1.21.1','1.21.4'):
    build=ROOT/'build' if mc=='1.21.1' else ROOT/'build/targets/1.21.4-neoforge'
    for task in ('test','testFabricCore'):
        totals=dict(tests=0,failures=0,errors=0,skipped=0)
        files=list((build/'test-results'/task).glob('TEST-*.xml'));assert files
        for path in files:
            root=ET.parse(path).getroot()
            for key in totals:totals[key]+=int(root.attrib.get(key,0))
        assert totals['failures']==totals['errors']==0 and totals['tests']>=113,totals
        suites.append({'minecraft':mc,'task':task,**totals})

motion=[]
for kind in ('press','switch'):
    path=ROOT/'docs/ui-design/exports/game/1.21.1-neoforge-ore-fixtures'/(kind+'-motion.mp4')
    data=json.loads(path.with_suffix('.json').read_text(encoding='utf-8'))
    assert path.stat().st_size>10000 and data['frames']>=15
    motion.append(data)

result={'status':'passed','clients':clients,'captureCount':len(screens),'captures':screens,'artifacts':artifacts,'regressions':suites,'motion':motion,
    'limits':['Visual review remains subject to user feedback.','Real test worlds returned route references without usable timetables; positive navigation/reminder flows used explicitly labelled fixtures.','Background checks dispatch native Screen events on the game thread and capture Minecraft framebuffers; they do not establish Windows IME composition or controller/touch support.','Real dedicated multiplayer, dimension/disconnect recovery and a large live network were not repeated in this UI-only round.']}
(OUT/'verification.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')

labels={'query-wide':'查询 · 五方案','stops-expanded':'途经站直接展开','active-start':'开始导航','stations':'选站','departures':'车站到发','reminder':'提醒设置','hud':'HUD 设置','hud-reminder':'提醒框编辑','walking':'步行管理','walking-editor':'步行编辑与对齐','walking-settings':'步行查询设置','query-narrow':'窄窗口 · 三方案','details-narrow':'窄窗口详情','hud-narrow':'窄窗口设置','hud-preview-narrow':'窄窗口完整 HUD 预览','long-names':'长站名','no-service':'线路参考 · 无可乘班次','loading':'加载中','error':'查询失败','no-result':'无结果','query-real':'真实目录与查询','stations-real':'真实车站目录','hud-real':'正式 HUD 设置','walking-real':'正式步行管理'}
parts=['<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>站间物语 · Ore UI 实机检查</title><style>body{margin:0;background:#242425;color:#fff;font:16px/1.6 system-ui,sans-serif}main{max-width:1440px;margin:auto;padding:32px}h1{font-size:28px}h2{margin-top:40px}p{color:#d0d1d4}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(320px,1fr));gap:20px}figure{margin:0;padding:12px;background:#313233}img,video{display:block;width:100%;height:auto}figcaption{margin-bottom:8px}a{color:#6cc349}</style><main><h1>站间物语 · Ore UI</h1><p>正式页面使用真实服务与当前 Minecraft 字体。下方区分真实测试世界和明确标注的场景数据；所有图像与动画来自游戏实际帧缓冲。</p><h2>按钮与开关动效</h2><div class="grid">']
for kind,title in [('press','按住、移出取消、松开执行'),('switch','I / O 开关 · 位移、拉伸与回返')]:
    parts.append(f'<figure><figcaption>{title}</figcaption><video controls loop preload="metadata" src="../exports/game/1.21.1-neoforge-ore-fixtures/{kind}-motion.mp4"></video></figure>')
parts.append('</div>')
for client in clients:
    target=client['target'];fixture=client['fixtures']
    parts.append(f'<h2>{html.escape(target)} · {"标注场景数据" if fixture else "真实 MTR 测试世界"}</h2><div class="grid">')
    for shot in screens:
        if shot['target']!=target or shot['fixtures']!=fixture:continue
        src='../'+shot['file'];caption=labels.get(shot['name'],shot['name'])
        parts.append(f'<figure><figcaption>{html.escape(caption)}</figcaption><a href="{src}"><img loading="lazy" alt="{html.escape(caption)}" src="{src}"></a></figure>')
    parts.append('</div>')
parts.append('<p>构建、实际检查和限制见 <a href="verification.json">验证记录</a>；控件细节见 <a href="components.md">组件规范</a>。实际世界本轮无可乘班次，启动导航与提醒使用标注数据检查。中文输入法组合态仍需人工复核。</p></main></html>')
(OUT/'gallery.html').write_text(''.join(parts),encoding='utf-8')
print(json.dumps({'captures':len(screens),'clients':len(clients),'artifacts':len(artifacts),'tests':sum(s['tests'] for s in suites),'failures':0},ensure_ascii=False))
