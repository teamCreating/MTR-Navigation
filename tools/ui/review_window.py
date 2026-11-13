"""Operate only a verified MTRNav UI Review window, using directed window messages."""
import argparse
import ctypes
import time

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--target',default='1.21.4-neoforge')
parser.add_argument('action',choices=['info','focus','key','click','move','type','resize','close','drag','hold','release','scroll','borderless','decorated'])
parser.add_argument('values',nargs='*')
args=parser.parse_args()
u=ctypes.windll.user32
u.SetThreadDpiAwarenessContext(ctypes.c_void_p(-4))
u.PostMessageW.argtypes=[ctypes.c_void_p,ctypes.c_uint,ctypes.c_size_t,ctypes.c_ssize_t]
u.GetClientRect.argtypes=[ctypes.c_void_p,ctypes.c_void_p]
u.GetWindowRect.argtypes=[ctypes.c_void_p,ctypes.c_void_p]
u.MoveWindow.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_int,ctypes.c_int,ctypes.c_int,ctypes.c_bool]
windows=[]
expected='MTRNav UI Review '+args.target
@ctypes.WINFUNCTYPE(ctypes.c_bool,ctypes.c_void_p,ctypes.c_void_p)
def visit(h,_):
    b=ctypes.create_unicode_buffer(512);u.GetWindowTextW(h,b,512)
    if b.value==expected and u.IsWindowVisible(h):windows.append(h)
    return True
u.EnumWindows(visit,0)
if len(windows)!=1:raise SystemExit(f'Expected exactly one project review window: {expected}; found {len(windows)}')
h=windows[0]
if args.action=='focus':
    u.SetForegroundWindow(ctypes.c_void_p(h))
    print('Focused only',expected)
    raise SystemExit(0)
if u.IsIconic(ctypes.c_void_p(h)):
    u.ShowWindow(ctypes.c_void_p(h),4)
    time.sleep(.5)
class R(ctypes.Structure):_fields_=[('left',ctypes.c_long),('top',ctypes.c_long),('right',ctypes.c_long),('bottom',ctypes.c_long)]
client=R();outer=R();u.GetClientRect(h,ctypes.byref(client));u.GetWindowRect(h,ctypes.byref(outer))
if args.action=='info':
    print(expected,'client',client.right,client.bottom,'dpi',u.GetDpiForWindow(ctypes.c_void_p(h)))
elif args.action=='close':u.PostMessageW(h,0x0010,0,0);print('Requested normal close of',expected)
elif args.action in ('borderless','decorated'):
    import json
    from pathlib import Path
    saved=Path(__file__).resolve().parents[2]/'.tmp'/('review-style-'+args.target+'.json')
    u.GetWindowLongPtrW.argtypes=[ctypes.c_void_p,ctypes.c_int];u.GetWindowLongPtrW.restype=ctypes.c_ssize_t
    u.SetWindowLongPtrW.argtypes=[ctypes.c_void_p,ctypes.c_int,ctypes.c_ssize_t]
    u.SetWindowPos.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_int,ctypes.c_int,ctypes.c_int,ctypes.c_int,ctypes.c_uint]
    if args.action=='borderless':
        style=u.GetWindowLongPtrW(h,-16)
        if not saved.exists():saved.write_text(json.dumps({'style':style}))
        u.SetWindowLongPtrW(h,-16,(style&~0x00CF0000)|0x80000000)
        w,hh=map(int,args.values);u.SetWindowPos(h,0,0,0,w,hh,0x0034|0x0400)
    else:
        if not saved.exists():raise SystemExit('No saved project window decoration')
        u.SetWindowLongPtrW(h,-16,json.loads(saved.read_text())['style']);u.SetWindowPos(h,0,0,0,1920,1080,0x0034|0x0400);saved.unlink()
    print('Changed decoration only on',expected)
elif args.action=='resize':
    w,hh=map(int,args.values)
    if not u.MoveWindow(h,outer.left,outer.top,w+(outer.right-outer.left-client.right),hh+(outer.bottom-outer.top-client.bottom),True):
        raise SystemExit('Windows did not resize the project review window')
    print('Resized only',expected,'to client',w,hh)
elif args.action=='move':
    x,y=map(int,args.values);u.PostMessageW(h,0x0200,0,x|(y<<16))
elif args.action=='click':
    x,y=map(int,args.values);pos=x|(y<<16)
    u.PostMessageW(h,0x0200,0,pos);time.sleep(.1)
    u.PostMessageW(h,0x0201,1,pos);time.sleep(.1);u.PostMessageW(h,0x0202,0,pos)
    print('Directed click to',expected)
elif args.action in ('drag','hold'):
    x,y,dx,dy=map(int,args.values)
    u.PostMessageW(h,0x0200,0,x|(y<<16));time.sleep(.1)
    u.PostMessageW(h,0x0201,1,x|(y<<16));time.sleep(.1)
    for i in range(1,11):
        px=round(x+(dx-x)*i/10);py=round(y+(dy-y)*i/10)
        u.PostMessageW(h,0x0200,1,px|(py<<16));time.sleep(.04)
    if args.action=='drag':u.PostMessageW(h,0x0202,0,dx|(dy<<16))
    print('Directed drag to',expected)
elif args.action=='release':
    x,y=map(int,args.values);u.PostMessageW(h,0x0200,1,x|(y<<16));time.sleep(.05);u.PostMessageW(h,0x0202,0,x|(y<<16))
elif args.action=='scroll':
    x,y,steps=map(int,args.values)
    u.PostMessageW(h,0x0200,0,x|(y<<16));time.sleep(.1)
    class P(ctypes.Structure):_fields_=[('x',ctypes.c_long),('y',ctypes.c_long)]
    pt=P(x,y);u.ClientToScreen(ctypes.c_void_p(h),ctypes.byref(pt))
    u.PostMessageW(h,0x020A,((steps*120)&0xFFFF)<<16,(pt.x&0xFFFF)|((pt.y&0xFFFF)<<16))
    print('Directed scroll to',expected)
elif args.action=='type':
    for c in ' '.join(args.values):u.PostMessageW(h,0x0102,ord(c),1)
    print('Entered test text in',expected)
else:
    keys={'f1':0x70,'f2':0x71,'f5':0x74,'f6':0x75,'f7':0x76,'f8':0x77,'f9':0x78,'f10':0x79,'n':0x4E,'escape':0x1B,'enter':0x0D,'tab':0x09,'backspace':0x08,'home':0x24,'end':0x23,'left':0x25,'right':0x27}
    name=args.values[0].lower();key=keys[name];scan=u.MapVirtualKeyW(key,0)
    u.PostMessageW(h,0x0100,key,1|(scan<<16));time.sleep(.1);u.PostMessageW(h,0x0101,key,1|(scan<<16)|0xC0000000)
    print('Sent',name,'only to',expected)
