"""Validate actual review screenshots/audits and the four final build artifacts; emit the delivery record."""
from pathlib import Path
import hashlib
import json
import struct
import xml.etree.ElementTree as ET
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'docs/ui-design/paradigms'
TARGETS = [f'{mc}-{loader}' for mc in ('1.21.1', '1.21.4') for loader in ('neoforge', 'fabric')]


def load(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def contains(parent, child):
    x, y, w, h = parent
    cx, cy, cw, ch = child
    return cw >= 0 and ch >= 0 and cx >= x and cy >= y and cx + cw <= x + w and cy + ch <= y + h


def overlaps(a, b):
    x, y, w, h = a
    bx, by, bw, bh = b
    return x < bx + bw and x + w > bx and y < by + bh and y + h > by


images = []
client_checks = {}
for target in TARGETS:
    report = load(OUT / (target + '-verification.json'))
    assert report['target'] == target
    client_checks[target] = report['checks']
    for capture in report['captures']:
        path = (OUT / capture['file']).resolve()
        assert path.is_relative_to(ROOT / 'docs/ui-design/exports/game')
        raw = path.read_bytes()
        assert raw[:8] == b'\x89PNG\r\n\x1a\n'
        size = list(struct.unpack('>II', raw[16:24]))
        assert size == capture['size'], (path, size, capture['size'])
        audit = load(path.with_suffix('.audit.json'))
        for key in ('variant', 'page', 'scenario', 'font'):
            assert audit[key] == capture[key], (path, key)
        assert audit['pixelSize'] == size
        for control in audit['controls']:
            assert contains(audit['frame'], control['rect']), (path, control)
        if audit['page'] == 'HUD':
            assert not overlaps(audit['settings'], audit['preview']), path
            assert not overlaps(audit['navigationHud'], audit['reminderHud']), path
            for key in ('navigationHud', 'reminderHud'):
                assert contains(audit['preview'], audit[key]), (path, key)
        images.append({'target': target, 'name': capture['name'], 'size': size,
                       'file': str(path.relative_to(ROOT)).replace('\\', '/'), 'sha256': hashlib.sha256(raw).hexdigest()})

main = ROOT / 'docs/ui-design/exports/game/1.21.1-neoforge-paradigms'
canonical = []
for variant in ('AE2', 'BEDROCK', 'JAVA', 'HYBRID'):
    for page, suffix in (('QUERY', 'query'), ('STATIONS', 'stations'), ('HUD', 'hud')):
        audit = load(main / f'{variant.lower()}-{suffix}.audit.json')
        assert audit['variant'] == variant and audit['page'] == page
        assert audit['pixelSize'] == [1920, 1080] and audit['guiScale'] == 2
        assert audit['font'] == 'PIXEL' and audit['scenario'] == 'NORMAL'
        assert audit['origin'] == '南苑 Nanyuan' and audit['destination'] == '滨海北 Binhai North'
        if page == 'QUERY':
            assert audit['visibleRows'] >= 5
            assert not any('换乘' in line or '步行' in line for line in audit['detailText'])
        canonical.append(f'{variant.lower()}-{suffix}')
    narrow = load(main / f'{variant.lower()}-narrow-query.audit.json')
    assert narrow['pixelSize'] == [854, 480] and narrow['visibleRows'] >= 3
    assert narrow['queryCollapsed'] and any(c['id'] == 'primary' for c in narrow['controls'])

reference = load(main / 'hybrid-reference.audit.json')
assert not reference['canStart'] and all('08:' not in line for line in reference['detailText'])
assert not next(c['active'] for c in reference['controls'] if c['id'] == 'primary')

builds = []
for artifact in load(ROOT / 'build/multiversion/manifest.json'):
    path = ROOT / 'build/multiversion' / artifact['file']
    assert hashlib.sha256(path.read_bytes()).hexdigest() == artifact['sha256']
    with ZipFile(path) as z:
        names = z.namelist()
        assert any(n.endswith('/ParadigmPreviewScreen.class') for n in names)
        assert any(n.endswith('/UiComparison.class') for n in names)
        assert any(n.endswith('/UiComparisonLayout.class') for n in names)
        assert not any(n.startswith('assets/mtrnav/textures/gui/create/') for n in names)
        assert not any(n.endswith('/Ldlib2NavigationPreview.class') for n in names)
    builds.append(artifact)
assert len(builds) == 4

regressions = []
for relative in ('build/test-results/test', 'build/test-results/testFabricCore',
                 'build/targets/1.21.4-neoforge/test-results/test', 'build/targets/1.21.4-neoforge/test-results/testFabricCore'):
    totals = dict.fromkeys(('tests', 'failures', 'errors', 'skipped'), 0)
    files = list((ROOT / relative).glob('TEST-*.xml'))
    assert files, relative
    for path in files:
        suite = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(suite.attrib.get(key, 0))
    assert totals['failures'] == totals['errors'] == 0
    regressions.append({'suite': relative, **totals})

(OUT / 'build-verification.json').write_text(json.dumps({'artifacts': builds, 'regressions': regressions}, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
summary = {'date': '2026-11-13', 'status': 'preview-ready-awaiting-design-and-font-selection',
           'baseline_screens': 9, 'hybrid_screens': 3, 'canonical_images': canonical,
           'in_game_screenshots': len(images), 'client_checks': client_checks, 'screenshots': images,
           'visual_review': 'Canonical nine screens, hybrid, long names, reference and narrow layouts inspected against source-review.md; user acceptance pending.',
           'limitations': ['System IME composition/candidate window still requires manual acceptance.',
                           'Modern UI check covers the isolated MTR + Modern UI configuration, not every mod in the user pack.',
                           'Formal v2 screens stay unchanged until the user selects the design and font.']}
(OUT / 'verification.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(f'Validated: {len(canonical)} canonical views, {len(images)} real screenshots, {len(builds)} artifacts.')
print('Core regression suites:', regressions)
