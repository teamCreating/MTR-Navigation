"""Download pinned MTR bundles into the project, in verified HTTP ranges."""
import concurrent.futures
import hashlib
import json
import pathlib
import urllib.request
import time

ROOT = pathlib.Path(__file__).resolve().parents[1]
CACHE = ROOT / '.cache' / 'downloads'

def download(version):
    item = next(f for f in version['files'] if f['primary'])
    target = CACHE / item['filename']
    if target.exists() and hashlib.sha512(target.read_bytes()).hexdigest() == item['hashes']['sha512']:
        print(target.name, 'already verified', flush=True)
        return
    parts_dir = CACHE / (item['filename'] + '.parts')
    parts_dir.mkdir(parents=True, exist_ok=True)
    size = item['size']
    block = 4 * 1024 * 1024
    def part(start):
        end = min(start + block, size) - 1
        file = parts_dir / str(start)
        if file.exists() and file.stat().st_size == end - start + 1:
            return file
        for attempt in range(4):
            try:
                req = urllib.request.Request(item['url'], headers={'Range': f'bytes={start}-{end}', 'User-Agent': 'MTR-Navigation-development/0.1'})
                with urllib.request.urlopen(req, timeout=90) as response:
                    if response.status != 206:
                        raise RuntimeError('Server did not honor range request')
                    data = response.read(block + 1)
                if len(data) != end - start + 1:
                    raise RuntimeError('Incorrect range length')
                file.write_bytes(data)
                print(target.name, start, 'ok', flush=True)
                return file
            except Exception:
                if attempt == 3:
                    raise
                time.sleep(1)
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        parts = list(pool.map(part, range(0, size, block)))
    assembled = target.with_suffix('.verified-part')
    digest = hashlib.sha512()
    with assembled.open('wb') as output:
        for file in parts:
            data = file.read_bytes()
            output.write(data)
            digest.update(data)
    if digest.hexdigest() != item['hashes']['sha512']:
        raise RuntimeError('MTR SHA512 mismatch')
    assembled.replace(target)
    print(target.name, 'verified', flush=True)

if __name__ == '__main__':
    CACHE.mkdir(parents=True, exist_ok=True)
    metadata = CACHE / 'mtr-versions.json'
    if not metadata.exists():
        with urllib.request.urlopen('https://api.modrinth.com/v2/project/minecraft-transit-railway/version') as response:
            versions = json.load(response)
        metadata.write_text(json.dumps([v for v in versions if v['version_number'] in {
            'NEOFORGE-4.1.0-beta.2+1.21.1', 'FABRIC-4.1.0-beta.2+1.21.1'}]), encoding='utf-8')
    for version in json.loads(metadata.read_text(encoding='utf-8-sig')):
        download(version)
