"""Fetch the pinned official LDLib2 trial dependency; normal builds do not use it."""
from pathlib import Path
import hashlib
import json
import urllib.request
import zipfile

ROOT=Path(__file__).resolve().parents[2]
CACHE=ROOT/'.cache/ldlib2'
VERSION='2.2.46.a'
SHA256='3d27ef69bbf8e3b65c03ff41c8e9cad8c82d9684323d7331b42b82d2ada5e596'
COMMIT='6cc1f974c55b8a551abbd467d5b96c1d944a9d83'
URL=f'https://maven.firstdark.dev/snapshots/com/lowdragmc/ldlib2/ldlib2-neoforge-1.21.1/{VERSION}/ldlib2-neoforge-1.21.1-{VERSION}-all.jar'
CACHE.mkdir(parents=True,exist_ok=True)
jar=CACHE/f'ldlib2-{VERSION}-all.jar'
if not jar.exists():
    with urllib.request.urlopen(URL,timeout=90) as response:
        data=response.read()
    if hashlib.sha256(data).hexdigest()!=SHA256:
        raise SystemExit('Official LDLib2 trial artifact hash mismatch')
    jar.write_bytes(data)
assert hashlib.sha256(jar.read_bytes()).hexdigest()==SHA256
with zipfile.ZipFile(jar) as z:
    for name in z.namelist():
        if name.startswith('META-INF/jarjar/') and name.endswith('.jar'):
            output=CACHE/'compile'/Path(name).name
            output.parent.mkdir(parents=True,exist_ok=True)
            output.write_bytes(z.read(name))
record={'version':VERSION,'minecraft':'1.21.1','loader':'neoforge','url':URL,
        'sha256':SHA256,'source_commit':COMMIT,'license':'LGPL-3.0',
        'source':f'https://github.com/Low-Drag-MC/LDLib2/tree/{COMMIT}',
        'scope':'opt-in ldlib2Preview build only'}
target=ROOT/'docs/ui-design/ldlib2'
target.mkdir(parents=True,exist_ok=True)
(target/'dependency.lock.json').write_text(json.dumps(record,indent=2)+'\n',encoding='utf-8')
print('Verified LDLib2 trial dependency',VERSION)
