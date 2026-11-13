"""Check the loader, Minecraft requirement and bytecode of a built Navigation JAR."""
import argparse
import json
from pathlib import Path
import tomllib
import zipfile


def verify(path, minecraft, loader, version):
    targets = json.loads((Path(__file__).resolve().parents[1] / 'gradle/minecraft-targets.json').read_text())
    target = targets[minecraft]
    with zipfile.ZipFile(path) as jar:
        bad = jar.testzip()
        if bad:
            raise ValueError(f'Damaged archive entry: {bad}')
        names = set(jar.namelist())
        if loader == 'neoforge':
            metadata = tomllib.loads(jar.read('META-INF/neoforge.mods.toml').decode())
            assert metadata['mods'][0]['modId'] == 'mtrnav'
            assert metadata['mods'][0]['version'] == version
            requirements = {d['modId']: d for d in metadata['dependencies']['mtrnav']}
            assert requirements['minecraft']['versionRange'] == target['minecraft_range']
            assert requirements['neoforge']['versionRange'] == target['neoforge_range']
            assert requirements['mtr']['type'] == 'required'
            assert 'fabric.mod.json' not in names
            entrypoint = 'io/github/teamcreating/mtrnav/platform/neoforge/NeoNavigation.class'
        else:
            metadata = json.loads(jar.read('fabric.mod.json'))
            assert metadata['id'] == 'mtrnav' and metadata['version'] == version
            assert metadata['depends']['minecraft'] == minecraft
            assert metadata['depends']['mtr'] == target['mtr']
            assert 'META-INF/neoforge.mods.toml' not in names
            entrypoint = 'io/github/teamcreating/mtrnav/platform/fabric/FabricNavigation.class'
        assert entrypoint in names
        assert 'LICENSE' in names and 'NOTICE' in names
        assert not any(n.startswith('org/mtr/') and n.endswith('.class') for n in names), 'MTR must stay external'
        for name in names:
            if name.startswith('io/github/teamcreating/mtrnav/') and name.endswith('.class'):
                header = jar.read(name)[:8]
                assert header[:4] == b'\xca\xfe\xba\xbe' and int.from_bytes(header[6:8]) == 65, name
        mixins = json.loads(jar.read('mtrnav.mixins.json'))
        for name in mixins.get('mixins', []) + mixins.get('client', []):
            assert mixins['package'].replace('.', '/') + '/' + name + '.class' in names, name
    print(f'Verified {minecraft} / {loader}: {path}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--minecraft', required=True)
    parser.add_argument('--loader', choices=['neoforge', 'fabric'], required=True)
    parser.add_argument('--version', required=True)
    args = parser.parse_args()
    verify(args.jar, args.minecraft, args.loader, args.version)
