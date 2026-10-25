"""Send a key to the project's Minecraft development window for local smoke checks."""
import ctypes
import sys
import time

user32 = ctypes.windll.user32
windows = []
@ctypes.WINFUNCTYPE(ctypes.c_bool, ctypes.c_void_p, ctypes.c_void_p)
def visit(hwnd, _):
    name = ctypes.create_unicode_buffer(512)
    user32.GetWindowTextW(hwnd, name, len(name))
    if 'Minecraft' in name.value and '1.21.1' in name.value and user32.IsWindowVisible(hwnd):
        windows.append((hwnd, name.value))
    return True
user32.EnumWindows(visit, 0)
if len(windows) != 1:
    raise SystemExit('Expected exactly one Minecraft development window')
hwnd, name = windows[0]
user32.ShowWindow(hwnd, 5)
user32.SetForegroundWindow(hwnd)
time.sleep(0.2)
keys = {'n': 0x4E, 'escape': 0x1B, 'enter': 0x0D, 't': 0x54, 'f2': 0x71, 'f11': 0x7A}
if sys.argv[1].lower() == 'close':
    user32.PostMessageW(ctypes.c_void_p(hwnd), 0x0010, 0, 0)
    print('Requested normal close:', name)
    raise SystemExit(0)
if sys.argv[1].lower() == 'text':
    for char in sys.argv[2]: user32.PostMessageW(ctypes.c_void_p(hwnd), 0x0102, ord(char), 1)
    print(name, 'text entered')
    raise SystemExit(0)
key = keys[sys.argv[1].lower()]
scan = user32.MapVirtualKeyW(key, 0)
user32.PostMessageW(ctypes.c_void_p(hwnd), 0x0100, key, 1 | (scan << 16))
time.sleep(0.12)
user32.PostMessageW(ctypes.c_void_p(hwnd), 0x0101, key, 1 | (scan << 16) | 0xC0000000)
print(name, sys.argv[1])
