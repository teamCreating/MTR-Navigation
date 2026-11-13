"""Generate the original, seamless graphite lining without reading reference pixels."""
from pathlib import Path
import struct
import zlib

ROOT = Path(__file__).resolve().parents[2]
out = ROOT / 'src/main/resources/assets/mtrnav/textures/gui/graphite_lining.png'
raw = bytearray()
for y in range(64):
    raw.append(0)
    for x in range(64):
        offset = ([0, 3, -2, 1][(x+y) % 4] if ((x//2+y//2) & 1) == 0
                  else [1, -1, 2, 0][(x-y) % 4])
        value = 49 + offset
        raw.extend((value, value, value-1, 255))

def chunk(name, data):
    return (struct.pack('>I', len(data)) + name + data
            + struct.pack('>I', zlib.crc32(name+data) & 0xffffffff))

out.write_bytes(b'\x89PNG\r\n\x1a\n'
                + chunk(b'IHDR', struct.pack('>IIBBBBB', 64, 64, 8, 6, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(bytes(raw), 9))
                + chunk(b'IEND', b''))
print(out)
