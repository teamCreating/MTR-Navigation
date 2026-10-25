"""Create a NEW, disposable flat Minecraft 1.21.1 save inside this project."""
from pathlib import Path
import gzip
import struct

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'run' / 'saves' / 'NavTest'

def string(value):
    data = value.encode('utf-8')
    return struct.pack('>H', len(data)) + data

def payload(kind, value):
    if kind == 1: return struct.pack('>b', value)
    if kind == 3: return struct.pack('>i', value)
    if kind == 4: return struct.pack('>q', value)
    if kind == 8: return string(value)
    if kind == 9:
        subtype, entries = value
        return bytes([subtype]) + struct.pack('>i', len(entries)) + b''.join(payload(subtype, e) for e in entries)
    if kind == 10:
        return b''.join(bytes([t]) + string(k) + payload(t, v) for k, (t, v) in value.items()) + b'\0'
    raise ValueError(kind)

if __name__ == '__main__':
    if TARGET.exists(): raise SystemExit('Refusing to overwrite an existing save: ' + str(TARGET))
    flat = {'type': (8, 'minecraft:flat'), 'settings': (10, {'biome': (8, 'minecraft:plains'), 'features': (1, 0), 'lakes': (1, 0),
        'layers': (9, (10, [{'block': (8, 'minecraft:bedrock'), 'height': (3, 1)}, {'block': (8, 'minecraft:dirt'), 'height': (3, 2)},
                          {'block': (8, 'minecraft:grass_block'), 'height': (3, 1)}])), 'structure_overrides': (9, (8, []))})}
    dimensions = {'minecraft:overworld': (10, {'type': (8, 'minecraft:overworld'), 'generator': (10, flat)})}
    for name, preset, source in [('the_nether', 'nether', {'type': (8, 'minecraft:multi_noise'), 'preset': (8, 'minecraft:nether')}),
                                 ('the_end', 'end', {'type': (8, 'minecraft:the_end')})]:
        dimensions['minecraft:' + name] = (10, {'type': (8, 'minecraft:' + name), 'generator': (10, {'type': (8, 'minecraft:noise'),
            'settings': (8, 'minecraft:' + preset), 'biome_source': (10, source)})})
    data = {'DataVersion': (3, 3955), 'version': (3, 19133), 'LevelName': (8, 'MTR Navigation Test'), 'GameType': (3, 1),
        'Difficulty': (1, 0), 'allowCommands': (1, 1), 'initialized': (1, 1), 'hardcore': (1, 0), 'Time': (4, 6000), 'DayTime': (4, 6000),
        'SpawnX': (3, 2000), 'SpawnY': (3, -59), 'SpawnZ': (3, 2000), 'LastPlayed': (4, 0),
        'Version': (10, {'Id': (3, 3955), 'Name': (8, '1.21.1'), 'Series': (8, 'main'), 'Snapshot': (1, 0)}),
        'WorldGenSettings': (10, {'seed': (4, 0), 'generate_features': (1, 0), 'bonus_chest': (1, 0), 'dimensions': (10, dimensions)}),
        'GameRules': (10, {'doDaylightCycle': (8, 'false'), 'doMobSpawning': (8, 'false')})}
    TARGET.mkdir(parents=True)
    with gzip.open(TARGET / 'level.dat', 'wb') as output: output.write(b'\x0a\0\0' + payload(10, {'Data': (10, data)}))
    print('Created', TARGET)
