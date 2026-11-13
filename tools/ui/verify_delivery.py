"""Verify packaged UI fonts, licenses, obsolete assets and recorded screenshot sizes."""
from pathlib import Path
import hashlib
import json
import struct
import zipfile

ROOT=Path(__file__).resolve().parents[2]
base='assets/mtrnav/'
manifest=json.loads((ROOT/'build/multiversion/manifest.json').read_text(encoding='utf-8-sig'))
assert len(manifest)==4,'Expected all four target packages'
packages=[]
for item in manifest:
    jar=ROOT/'build/multiversion'/item['file']
    assert hashlib.sha256(jar.read_bytes()).hexdigest()==item['sha256']
    with zipfile.ZipFile(jar) as z:
        provenance=json.loads(z.read(base+'ui/font-sources.json'))
        for key,file in [('fusion','fusion_pixel_10.ttf'),('noto','noto_sans_sc_regular.ttf')]:
            data=z.read(base+'font/ui/'+file)
            assert data[:4] in (b'\x00\x01\x00\x00',b'true'),f'{file}: not TrueType outlines'
            assert hashlib.sha256(data).hexdigest()==provenance[key]['sha256']
        for file in ['fusion-ofl.txt','noto-ofl.txt']:
            assert 'SIL OPEN FONT LICENSE' in z.read(base+'font/ui/'+file).decode('utf-8')
        for face in ['pixel','smooth']:
            providers=json.loads(z.read(base+f'font/ui_{face}.json'))['providers']
            assert providers[0]['id']=='minecraft:include/space'
            assert providers[1]['id']=='minecraft:include/default'
            assert 'providers' in json.loads(z.read(base+f'font/ui_{face}.json'))
        for stale in ['font/ui/noto_sans_sc_regular.otf','font/ui/sources.json','font/ui/SOURCES.json']:
            assert base+stale not in z.namelist(),f'Obsolete asset packaged: {item["file"]}: {stale}'
    packages.append(item)
screens=[]
for png in sorted((ROOT/'docs/ui-design/exports/game').rglob('*.png')):
    size=list(struct.unpack('>II',png.read_bytes()[16:24]))
    for wanted in [(854,480),(1280,720),(1920,1080),(2560,1440)]:
        if f'{wanted[0]}x{wanted[1]}' in png.stem:
            assert size==list(wanted),f'Mislabelled screenshot: {png}'
    screens.append({'file':png.relative_to(ROOT).as_posix(),'size':size})
result={'date':'2026-11-12','status':'design-review-awaiting-user','packages':packages,'screenshots':screens,
        'visual_acceptance':False,'formal_screen_migration':False,'axolotl_modified':False}
(ROOT/'docs/ui-design/delivery.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print(f'Verified {len(packages)} packages and {len(screens)} actual game screenshots')
