"""Exercise only exact isolated MTRNav review windows; capture native F2 images and measured audits."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/ui-design/paradigms'
ALLOWED = {f'{mc}-{loader}' for mc in ('1.21.1', '1.21.4') for loader in ('neoforge', 'fabric')}
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--target', choices=sorted(ALLOWED), default='1.21.1-neoforge')
p.add_argument('--full', action='store_true', help='Capture nine comparisons, hybrid, edge states and interactive assertions')
p.add_argument('--attach', action='store_true', help='Use an already running exact review window')
p.add_argument('--initial-variant', choices=['ae2', 'bedrock', 'java', 'hybrid'], default='java')
p.add_argument('--recapture-details', action='store_true', help='Refresh corrected reference/departure screenshots and assert displayed times')
a = p.parse_args()
label = a.target + '-paradigms'
instance = ROOT / 'run/ui-review' / label
audit_file = instance / 'mtrnav-paradigm-audit.json'
helper = [sys.executable, str(ROOT / 'tools/ui/review_window.py'), '--target', label]
checks = []
captures = []


def prepare():
    """Seed from project-owned test instances only; never reads or edits an external game installation."""
    seed = ROOT / 'run/ui-review' / (a.target + '-v2')
    if not (seed / 'saves/NavTest').exists():
        seed = ROOT / 'run/ui-review' / a.target
    if not (seed / 'saves/NavTest').exists():
        raise RuntimeError(f'No existing project NavTest fixture for {a.target}')
    instance.mkdir(parents=True, exist_ok=True)
    if not (instance / 'saves/NavTest').exists():
        shutil.copytree(seed / 'saves/NavTest', instance / 'saves/NavTest')
    for name in ('mods', 'config'):
        if (seed / name).is_dir():
            shutil.copytree(seed / name, instance / name, dirs_exist_ok=True)
    options = instance / 'options.txt'
    if not options.exists() and (seed / 'options.txt').exists():
        shutil.copy2(seed / 'options.txt', options)
    if options.exists():
        entries = dict(line.split(':', 1) for line in options.read_text(encoding='utf-8').splitlines() if ':' in line)
        entries.update(guiScale='3', overrideWidth='1920', overrideHeight='1080', renderDistance='5', simulationDistance='5', maxFps='40', pauseOnLostFocus='false', fullscreen='false')
        options.write_text(''.join(f'{key}:{value}\n' for key, value in entries.items()), encoding='utf-8')


def send(action, *values):
    if action not in ('info', 'close'):
        subprocess.run(helper + ['focus'], cwd=ROOT, check=True, capture_output=True, text=True)
    subprocess.run(helper + [action, *map(str, values)], cwd=ROOT, check=True, capture_output=True, text=True)


def read():
    for _ in range(4):
        try:
            return json.loads(audit_file.read_text(encoding='utf-8'))
        except (FileNotFoundError, json.JSONDecodeError):
            time.sleep(.025)
    return None


def wait_for(predicate, timeout=12):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        state = read()
        if state and predicate(state):
            return state
        if process and process.poll() is not None:
            raise RuntimeError(f'Review client exited: see {log}')
        time.sleep(.15)
    raise AssertionError(f'Review condition timed out; latest audit: {read()}')


def key(name):
    send('key', name)
    time.sleep(.4)


def control(name):
    state = wait_for(lambda s: any(c['id'] == name for c in s['controls']))
    return state, next(c for c in state['controls'] if c['id'] == name)


def click(name):
    state, c = control(name)
    if not c['active']:
        raise AssertionError(f'Cannot activate disabled review control: {name}')
    x, y, w, h = c['rect']
    send('click', round((x + w / 2) * state['guiScale']), round((y + h / 2) * state['guiScale']))
    time.sleep(.5)


def select_view(index):
    expected = ('ROUTES', 'DEPARTURES', 'ACTIVE')[index]
    for _ in range(3):
        click('nav' + str(index))
        try:
            wait_for(lambda s: s['view'] == expected and s['page'] == 'QUERY', timeout=2)
            return
        except AssertionError:
            pass
    raise AssertionError(f'View tab did not activate: {expected}')


def set_page(page):
    for _ in range(5):
        if read()['page'] == page:
            return
        key('f8')
    raise AssertionError(f'Cannot select page {page}')


def set_variant(variant):
    for _ in range(5):
        if read()['variant'] == variant:
            return
        key('f5')
    raise AssertionError(f'Cannot select variant {variant}')


def set_scenario(scenario):
    for _ in range(8):
        if read()['scenario'] == scenario:
            return
        key('f7')
    raise AssertionError(f'Cannot select scenario {scenario}')


def resize(w, h):
    send('resize', w, h)
    wait_for(lambda s: s['pixelSize'] == [w, h])


def capture(name):
    time.sleep(.6)
    result = subprocess.run([sys.executable, str(ROOT / 'tools/ui/capture_review.py'), name, '--target', label], cwd=ROOT, capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError(result.stdout + result.stderr)
    state = read()
    captures.append({'name': name, 'variant': state['variant'], 'page': state['page'], 'scenario': state['scenario'],
                     'font': state['font'], 'size': state['pixelSize'], 'file': f'../exports/game/{label}/{name}.png'})
    print('Captured:', name, flush=True)


def check(name, condition):
    if not condition:
        raise AssertionError(name)
    checks.append(name)
    print('Passed:', name, flush=True)


def scroll(region, steps):
    state = read()
    x, y, w, h = state[region]
    send('scroll', round((x + w / 2) * state['guiScale']), round((y + h / 2) * state['guiScale']), steps)
    time.sleep(.5)


def contains(parent, child):
    x, y, w, h = parent
    cx, cy, cw, ch = child
    return cx >= x and cy >= y and cx + cw <= x + w and cy + ch <= y + h


def overlaps(a, b):
    x, y, w, h = a
    bx, by, bw, bh = b
    return x < bx + bw and x + w > bx and y < by + bh and y + h > by


def full_review():
    resize(1920, 1080)
    set_scenario('NORMAL')
    for variant in ('AE2', 'BEDROCK', 'JAVA', 'HYBRID'):
        set_variant(variant)
        set_page('QUERY')
        select_view(0)
        if read()['queryCollapsed']:
            click('expandQuery')
        state = wait_for(lambda s: s['page'] == 'QUERY' and s['view'] == 'ROUTES')
        check(variant + ': five double-line journeys at full HD / 2x', state['visibleRows'] >= 5 and state['guiScale'] == 2)
        capture(variant.lower() + '-query')
        set_page('STATIONS')
        click('station-central')
        capture(variant.lower() + '-stations')
        set_page('HUD')
        state = read()
        check(variant + ': separate HUD settings, contained nonoverlapping boxes',
              not overlaps(state['settings'], state['preview']) and not overlaps(state['navigationHud'], state['reminderHud'])
              and all(contains(state['preview'], state[k]) for k in ('navigationHud', 'reminderHud')))
        capture(variant.lower() + '-hud')
    set_page('QUERY')
    set_scenario('MANY')
    click('row3')
    scroll('list', -3)
    scroll('detail', -2)
    before = read()
    for _ in range(4):
        key('f5')
        current = read()
        check('variant state retained: ' + current['variant'], all(current[k] == before[k] for k in ('origin', 'destination', 'selectedJourney', 'listScroll', 'detailScroll')))
    key('f6')
    current = read()
    check('font switch preserves selection and independent scroll offsets', all(current[k] == before[k] for k in ('selectedJourney', 'listScroll', 'detailScroll')))
    capture('hybrid-smooth-many')
    key('f6')
    set_scenario('NORMAL')
    click('pickDestination')
    click('stationSearch')
    send('type', 'Central')
    wait_for(lambda s: s['stationSearch'] == 'Central' and s['stationCount'] == 1)
    click('station-central')
    click('applyStation')
    check('station selection returns to the preserved query', read()['page'] == 'QUERY' and read()['destination'] == '中央车站 Central')
    click('pickOrigin')
    click('clearSearch')
    click('stationSearch')
    send('type', '机场')
    wait_for(lambda s: s['stationSearch'] == '机场' and s['stationCount'] == 1)
    capture('hybrid-chinese-input')
    key('enter')
    wait_for(lambda s: s['page'] == 'QUERY')
    check('Chinese character input and Enter selection', '机场' in read()['origin'])
    click('pickOrigin')
    click('clearSearch')
    click('nearbyStations')
    state, apply = control('applyStation')
    check('Nearby filtering rejects a previously selected distant station', not apply['active'] and state['stationCount'] == 7)
    click('stationSort')
    check('Distance sort puts the closest station first', next(c['id'] for c in read()['controls'] if c['id'].startswith('station-')) == 'station-nanyuan')
    click('index')
    click('index3')
    check('Alphabet index filters to Central', read()['index'] == 'C' and read()['stationCount'] == 1)
    click('station-central')
    click('applyStation')
    click('primary')
    check('start activates only the simulated trip', read()['activeTrip'] and read()['view'] == 'ACTIVE')
    capture('hybrid-active')
    click('primary')
    select_view(1)
    click('primary')
    check('departure reminder stays in preview', '测试提醒' in read()['notice'])
    capture('hybrid-departures')
    select_view(0)
    for scenario in ('LONG_NAMES', 'EMPTY', 'LOADING', 'ERROR', 'REFERENCE'):
        set_scenario(scenario)
        if scenario != 'LONG_NAMES':
            state, primary = control('primary')
            check(scenario + ': navigation disabled', not primary['active'] and not state['canStart'])
        capture('hybrid-' + scenario.lower())
        if scenario == 'LONG_NAMES':
            key('f6')
            capture('hybrid-smooth-long-names')
            key('f6')
    set_scenario('NORMAL')
    set_page('HUD')
    for percent in (200, 100, 150):
        state, slider = control('hudSetting1')
        x, y, w, h = slider['rect']
        send('click', round((x + 4 + (w - 8) * (percent - 100) / 100) * state['guiScale']), round((y + h - 5) * state['guiScale']))
        time.sleep(.5)
        check(f'HUD scale {percent}%', read()['hud']['navigationPercent'] == percent)
        capture(f'hybrid-hud-{percent}')
    click('saveHud')
    saved = read()['hud']
    state, slider = control('hudSetting1')
    x, y, w, h = slider['rect']
    send('click', round((x + w - 4) * state['guiScale']), round((y + h - 5) * state['guiScale']))
    time.sleep(.5)
    # Scroll settings to its restore button, then restore the saved in-memory snapshot.
    for _ in range(12):
        if any(c['id'] == 'hudSetting9' for c in read()['controls']):
            break
        scroll('settings', -1)
    click('hudSetting9')
    check('HUD save/restore is session local', read()['hudSaved'] and read()['hud'] == saved)
    state = read()
    x, y, w, h = state['navigationHud']
    px, py, pw, ph = state['preview']
    scale = state['guiScale']
    send('hold', round((x + w / 2) * scale), round((y + h / 2) * scale), round((px + pw - w / 2 - 6) * scale), round((py + ph - h / 2 - 6) * scale))
    wait_for(lambda s: s['dragging'])
    check('HUD drag hides settings controls', not any(c['id'].startswith('hudSetting') for c in read()['controls']))
    capture('hybrid-hud-dragging')
    send('release', round((px + pw - w / 2 - 6) * scale), round((py + ph - h / 2 - 6) * scale))
    wait_for(lambda s: not s['dragging'])
    check('HUD drag keeps both boxes on screen', all(contains(read()['preview'], read()[k]) for k in ('navigationHud', 'reminderHud')))
    set_page('COMPONENTS')
    capture('hybrid-components')
    key('tab')
    check('Tab reaches native controls', any(c['focused'] for c in read()['controls']))
    for variant in ('AE2', 'BEDROCK', 'JAVA', 'HYBRID'):
        set_variant(variant)
        set_page('QUERY')
        resize(854, 480)
        if not read()['queryCollapsed']:
            click('collapseQuery')
        state = read()
        check(variant + ': narrow layout retains three rows and primary action', state['compact'] and state['visibleRows'] >= 3 and any(c['id'] == 'primary' for c in state['controls']))
        capture(variant.lower() + '-narrow-query')
        click('details')
        capture(variant.lower() + '-narrow-details')
        click('details')
        set_page('STATIONS')
        capture(variant.lower() + '-narrow-stations')
        set_page('HUD')
        capture(variant.lower() + '-narrow-hud')
    resize(1920, 1080)


def write_gallery():
    from html import escape
    reports = [json.loads(f.read_text(encoding='utf-8')) for f in sorted(OUT.glob('*-verification.json'))]
    cards = []
    for report in reports:
        for image in report['captures']:
            title = f"{image['variant']} · {image['page']} · {image['scenario']}"
            cards.append(f'<article data-page="{escape(image["page"])}" data-variant="{escape(image["variant"])}">'
                         f'<a href="{escape(image["file"])}" target="_blank"><img loading="lazy" src="{escape(image["file"])}" alt="{escape(title)}"></a>'
                         f'<h2>{escape(title)}</h2><p>{escape(report["target"])} · {escape(image["font"])} · {image["size"][0]} × {image["size"][1]}</p></article>')
    (OUT / 'gallery.html').write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>站间物语 · 游戏内 UI 对照</title><style>
*{box-sizing:border-box}body{margin:0;background:#151719;color:#eceff0;font:15px/1.6 system-ui,"Microsoft YaHei",sans-serif;padding:28px}main{max-width:1600px;margin:auto}h1{font-size:25px;margin:0 0 8px}p{color:#bbc2c6;margin:4px 0 18px}nav{display:flex;gap:12px;flex-wrap:wrap;margin:20px 0}label{display:flex;gap:8px;align-items:center}select{font:inherit;background:#303437;color:#fff;padding:7px 12px;border:1px solid #68747b}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(420px,1fr));gap:20px}article{border:1px solid #41484d;padding:10px;background:#202427}article[hidden]{display:none}img{width:100%;display:block}h2{font-size:16px;margin:10px 0 0}article p{font-size:13px;margin:2px 0}a{color:#add3e2}@media(max-width:520px){body{padding:14px}.grid{grid-template-columns:1fr}}</style>
<main><h1>站间物语 · 游戏内 UI 对照</h1><p>AE2 / 基岩版 Ore UI / Java / 融合方案。图片均来自隔离项目实例的原生 F2 截图，使用固定测试数据；点击图片查看原尺寸。</p>
<nav><label>页面<select id="page"><option value="">全部</option><option>QUERY</option><option>STATIONS</option><option>HUD</option><option>COMPONENTS</option></select></label>
<label>方向<select id="variant"><option value="">全部</option><option>AE2</option><option>BEDROCK</option><option>JAVA</option><option>HYBRID</option></select></label></nav>
<div class="grid">''' + ''.join(cards) + '''</div><p>字体与融合方案等待视觉选择；正式界面仍保留当前 v2。中文字符注入检查不等同于系统输入法组合输入验收。</p></main>
<script>const page=document.querySelector('#page'),variant=document.querySelector('#variant');function filter(){for(const card of document.querySelectorAll('article'))card.hidden=!!((page.value&&card.dataset.page!==page.value)||(variant.value&&card.dataset.variant!==variant.value))}page.onchange=variant.onchange=filter;</script></html>''', encoding='utf-8')


OUT.mkdir(parents=True, exist_ok=True)
process = None
log = ROOT / 'build' / ('paradigm-review-' + a.target + '.log')
started = time.time()
preferences_before = None
try:
    if not a.attach:
        prepare()
        preferences = instance / 'config/mtrnav-client.properties'
        preferences_before = preferences.read_bytes() if preferences.exists() else None
        mc, loader = a.target.split('-', 1)
        output = log.open('w', encoding='utf-8')
        process = subprocess.Popen(['pwsh', '-NoProfile', '-File', str(ROOT / 'dev.ps1'), '-Platform', loader,
                                    '-MinecraftVersion', mc, 'runClient', '-PparadigmPreview', '-PuiPreviewVariant='+a.initial_variant, '-Pquickplay=NavTest', '--offline'],
                                   cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, creationflags=subprocess.CREATE_NO_WINDOW)
    wait_for(lambda s: a.attach or audit_file.stat().st_mtime >= started, timeout=180)
    if a.recapture_details:
        resize(1920, 1080)
        set_variant('HYBRID')
        select_view(1)
        click('row3')
        wait_for(lambda s: any('08:24 上车' in line for line in s.get('detailText', [])))
        check('Departure details use the selected departure time', any('15 分钟后' in line for line in read()['detailText']))
        capture('hybrid-departures')
        select_view(0)
        set_scenario('REFERENCE')
        check('Unscheduled reference never displays invented times', all('08:' not in line for line in read()['detailText']))
        capture('hybrid-reference')
    elif a.full:
        full_review()
    else:
        resize(1920, 1080)
        for page in ('QUERY', 'STATIONS', 'HUD'):
            set_page(page)
            capture('client-smoke-' + page.lower())
        set_page('STATIONS')
        click('stationSearch')
        send('type', '中央')
        wait_for(lambda s: s['stationSearch'] == '中央' and s['stationCount'] == 1)
        key('f6')
        check('Chinese search survives the font switch', read()['stationSearch'] == '中央')
        capture('client-smoke-smooth-input')
        key('enter')
        wait_for(lambda s: s['page'] == 'QUERY')
        check('Station selection returns to the query', read()['destination'] == '中央车站 Central')
        click('primary')
        check('Simulated navigation starts', read()['activeTrip'] and read()['view'] == 'ACTIVE')
        capture('client-smoke-active')
        set_page('HUD')
        click('saveHud')
        check('HUD snapshot saves in memory', read()['hudSaved'])
        set_page('QUERY')
        select_view(0)
        resize(854, 480)
        if not read()['queryCollapsed']:
            click('collapseQuery')
        check('Narrow query keeps three rows and the primary action', read()['visibleRows'] >= 3)
        capture('client-smoke-narrow')
        resize(1920, 1080)
    set_page('QUERY')
    closed_at = time.time()
    key('escape')
    exit_file = instance / 'mtrnav-paradigm-exit.json'
    deadline = time.monotonic() + 8
    while time.monotonic() < deadline:
        if exit_file.exists() and exit_file.stat().st_mtime >= closed_at:
            exit_state = json.loads(exit_file.read_text(encoding='utf-8'))
            check('Closing preview restores the original GUI scale', exit_state['enteredScale'] == exit_state['restoredScale'])
            break
        time.sleep(.1)
    else:
        raise AssertionError('No fresh GUI scale restoration audit')
    if not a.attach:
        preferences_after = preferences.read_bytes() if preferences.exists() else None
        check('Formal client preferences unchanged', preferences_before == preferences_after)
    report = {'target': a.target, 'checks': checks, 'captures': captures, 'log': str(log.relative_to(ROOT)),
              'mods': [{'name': f.name, 'sha256': hashlib.sha256(f.read_bytes()).hexdigest()} for f in sorted((instance / 'mods').glob('*.jar'))],
              'limitations': ['Injected Chinese characters do not establish system IME composition/candidate-window acceptance.',
                              'Formal-screen migration awaits design and font selection.']}
    report_file = OUT / (a.target + '-verification.json')
    if a.recapture_details and report_file.exists():
        previous = json.loads(report_file.read_text(encoding='utf-8'))
        names = {image['name'] for image in captures}
        report['captures'] = [image for image in previous['captures'] if image['name'] not in names] + captures
        report['checks'] = list(dict.fromkeys(previous['checks'] + checks))
    report_file.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    write_gallery()
    print('Saved review:', a.target, flush=True)
finally:
    if process and process.poll() is None:
        send('close')
        process.wait(timeout=90)
    if process:
        output.close()
