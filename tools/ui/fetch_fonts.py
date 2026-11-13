"""Fetch the two pinned, OFL-licensed UI comparison fonts into the mod resources."""
from pathlib import Path
import concurrent.futures
import hashlib
import json
import time
import urllib.request
import zipfile
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2] / '.cache/ui-python'))
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[2]
CACHE = ROOT / '.cache/ui-fonts'
DEST = ROOT / 'src/main/resources/assets/mtrnav/font/ui'
FUSION_TAG = '2026.11.10'
FUSION_ARCHIVE = 'fusion-pixel-font-10px-proportional-ttf-v2026.11.10.zip'
FUSION_SHA256 = 'cccdaef5abca366b014895981c1856e7831e617a0cff7f2326515848fd5c0935'
NOTO_COMMIT = 'f8d157532fbfaeda587e826d4cd5b21a49186f7c'
NOTO_BLOB = '5371a543be5fc670c7cdee9760c03554ee3e9b8e'


def fetch(url, path):
    if path.exists():
        return path.read_bytes()
    for attempt in range(4):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'MTR-Navigation-ui-design/1.0'})
            with urllib.request.urlopen(req, timeout=90) as response:
                data = response.read()
            temp = path.with_suffix(path.suffix + '.download')
            temp.write_bytes(data)
            temp.replace(path)
            return data
        except Exception:
            if attempt == 3:
                raise
            time.sleep(1)


def main():
    CACHE.mkdir(parents=True, exist_ok=True)
    DEST.mkdir(parents=True, exist_ok=True)
    fusion_url = f'https://github.com/TakWolf/fusion-pixel-font/releases/download/{FUSION_TAG}/{FUSION_ARCHIVE}'
    noto_url = f'https://raw.githubusercontent.com/notofonts/noto-cjk/{NOTO_COMMIT}/Sans/Variable/TTF/Subset/NotoSansSC-VF.ttf'
    requests = [
        (fusion_url, CACHE / FUSION_ARCHIVE),
        (noto_url, CACHE / 'NotoSansSC-VF.ttf'),
        (f'https://raw.githubusercontent.com/TakWolf/fusion-pixel-font/{FUSION_TAG}/LICENSE-OFL', CACHE / 'Fusion-LICENSE-OFL.txt'),
        (f'https://raw.githubusercontent.com/notofonts/noto-cjk/{NOTO_COMMIT}/Sans/LICENSE', CACHE / 'Noto-LICENSE-OFL.txt'),
    ]
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        data = list(pool.map(lambda item: fetch(*item), requests))
    if hashlib.sha256(data[0]).hexdigest() != FUSION_SHA256:
        raise ValueError('Fusion release archive does not match its published SHA256')
    if hashlib.sha1(f'blob {len(data[1])}\0'.encode() + data[1]).hexdigest() != NOTO_BLOB:
        raise ValueError('Noto font does not match its pinned Git blob')
    with zipfile.ZipFile(CACHE / FUSION_ARCHIVE) as archive:
        faces = [n for n in archive.namelist() if n.endswith('.ttf') and 'zh_hans' in n.lower().replace('-', '_')]
        if len(faces) != 1:
            raise ValueError(f'Expected one Simplified Chinese face, found {faces}')
        fusion = archive.read(faces[0])
    # Minecraft's native provider accepts TrueType outlines, not CFF OTF. Pin weight 400.
    noto = TTFont(CACHE / 'NotoSansSC-VF.ttf', recalcTimestamp=False)
    instantiateVariableFont(noto, {'wght': 400}, inplace=True)
    noto.save(DEST / 'noto_sans_sc_regular.ttf')
    for name, value in [('fusion_pixel_10.ttf', fusion),
                        ('fusion-ofl.txt', data[2]), ('noto-ofl.txt', data[3])]:
        (DEST / name).write_bytes(value)
    # Keep ASCII glyphs from the player's Minecraft default font, including its resource-pack changes.
    skip = ''.join(chr(n) for n in range(32, 127))
    for name, filename, oversample in [('pixel', 'fusion_pixel_10.ttf', 1.0), ('smooth', 'noto_sans_sc_regular.ttf', 3.0)]:
        definition = {'providers': [
            {'type': 'reference', 'id': 'minecraft:include/space'},
            {'type': 'reference', 'id': 'minecraft:include/default'},
            {'type': 'ttf', 'file': f'mtrnav:ui/{filename}', 'size': 10.0, 'oversample': oversample,
             'shift': [0.0, 0.0], 'skip': skip},
            {'type': 'reference', 'id': 'minecraft:default'},
        ]}
        (DEST.parent / f'ui_{name}.json').write_text(json.dumps(definition, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    manifest = {
        'fusion': {'release': FUSION_TAG, 'url': fusion_url, 'archive_sha256': FUSION_SHA256,
                   'face': faces[0], 'sha256': hashlib.sha256(fusion).hexdigest(), 'license': 'OFL-1.1'},
        'noto': {'commit': NOTO_COMMIT, 'url': noto_url, 'git_blob': NOTO_BLOB,
                 'source_sha256': hashlib.sha256(data[1]).hexdigest(),
                 'sha256': hashlib.sha256((DEST / 'noto_sans_sc_regular.ttf').read_bytes()).hexdigest(),
                 'derivation': 'fonttools 4.61.1 instantiateVariableFont wght=400; TrueType outlines',
                 'license': 'OFL-1.1'},
    }
    # Keep metadata outside font/: Minecraft interprets every JSON there as a font definition.
    metadata = DEST.parent.parent / 'ui/font-sources.json'
    metadata.parent.mkdir(parents=True, exist_ok=True)
    metadata.write_text(json.dumps(manifest, indent=2) + '\n', encoding='utf-8')
    print('Verified and installed both local UI preview fonts:', DEST)


if __name__ == '__main__':
    main()
