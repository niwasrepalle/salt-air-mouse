import argparse, ctypes, json, math, secrets, socket, socketserver, threading, time
from ctypes import wintypes

class MOUSEINPUT(ctypes.Structure):
    _fields_ = [('dx',wintypes.LONG),('dy',wintypes.LONG),('mouseData',wintypes.DWORD),('dwFlags',wintypes.DWORD),('time',wintypes.DWORD),('dwExtraInfo',ctypes.c_size_t)]
class KEYBDINPUT(ctypes.Structure):
    _fields_ = [('wVk',wintypes.WORD),('wScan',wintypes.WORD),('dwFlags',wintypes.DWORD),('time',wintypes.DWORD),('dwExtraInfo',ctypes.c_size_t)]
class UNION(ctypes.Union):
    _fields_ = [('mi',MOUSEINPUT),('ki',KEYBDINPUT),('padding',ctypes.c_byte * (32 if ctypes.sizeof(ctypes.c_void_p)==8 else 24))]
class INPUT(ctypes.Structure):
    _anonymous_ = ('u',)
    _fields_ = [('type',wintypes.DWORD),('u',UNION)]

class Mouse:
    def __init__(self, dry=False):
        self.dry=dry; self.rx=self.ry=0.0; self.left_held=False
        if not dry:
            self.api=ctypes.WinDLL('user32',use_last_error=True)
            self.api.SendInput.argtypes=[wintypes.UINT,ctypes.POINTER(INPUT),ctypes.c_int]
            self.api.SendInput.restype=wintypes.UINT
    def send(self, flags, x=0, y=0, data=0):
        if self.dry: return
        event=INPUT(type=0,mi=MOUSEINPUT(x,y,data & 0xffffffff,flags,0,0))
        if self.api.SendInput(1,ctypes.byref(event),ctypes.sizeof(INPUT))!=1:
            raise RuntimeError('Windows rejected input; elevated windows may block it')
    def key(self, vk, up=False):
        if self.dry: return
        event=INPUT(type=1,ki=KEYBDINPUT(vk,0,2 if up else 0,0,0))
        if self.api.SendInput(1,ctypes.byref(event),ctypes.sizeof(INPUT))!=1:
            raise RuntimeError('Windows rejected keyboard modifier')
    def control_pressed(self):
        return False if self.dry else bool(self.api.GetAsyncKeyState(0x11) & 0x8000)
    def release(self):
        if self.left_held:
            try: self.send(4)
            finally: self.left_held=False
        self.rx=self.ry=0.0
    def apply(self,p):
        op=p.get('op')
        if op=='move':
            x,y=p.get('x'),p.get('y')
            if type(x) not in (int,float) or type(y) not in (int,float) or not math.isfinite(x) or not math.isfinite(y): raise ValueError('Invalid movement')
            self.rx+=max(-500,min(500,x)); self.ry+=max(-500,min(500,y))
            dx,dy=int(self.rx),int(self.ry); self.rx-=dx; self.ry-=dy
            if dx or dy: self.send(1,dx,dy)
        elif op=='left_down':
            if not self.left_held:
                self.send(2); self.left_held=True
        elif op=='left_up':
            self.release()
        elif op in ('left','right'):
            down,up=(2,4) if op=='left' else (8,16)
            self.send(down); self.send(up)
        elif op in ('scroll','hscroll','zoom'):
            value=p.get('amount')
            if type(value) is not int: raise ValueError('Invalid scroll')
            value=max(-1200,min(1200,value))
            if op=='zoom':
                own_control=not self.control_pressed()
                try:
                    if own_control: self.key(0x11)
                    self.send(0x800,data=value)
                finally:
                    if own_control: self.key(0x11,up=True)
            else: self.send(0x1000 if op=='hscroll' else 0x800,data=value)
        elif op=='center':
            self.rx=self.ry=0
            if not self.dry:
                # Absolute coordinates cover the complete virtual desktop.
                self.send(0x8000|0x4000|1,32768,32768)
        elif op!='ping': raise ValueError('Unknown operation')

class Server(socketserver.ThreadingTCPServer):
    allow_reuse_address=True; daemon_threads=True
    def __init__(self,address,mouse):
        self.mouse=mouse; self.lock=threading.Lock(); self.active=False
        self.pin=f'{secrets.randbelow(1000000):06d}'; self.pin_until=time.monotonic()+300
        self.failures=[]
        super().__init__(address,Handler)

class Handler(socketserver.StreamRequestHandler):
    def reply(self,p):
        self.wfile.write((json.dumps(p)+'\n').encode()); self.wfile.flush()
    def read(self):
        raw=self.rfile.readline(2049)
        if not raw: raise EOFError()
        if len(raw)>2048 or not raw.endswith(b'\n'): raise ValueError('Oversized packet')
        p=json.loads(raw)
        if not isinstance(p,dict): raise ValueError('Object required')
        return p
    def handle(self):
        owned=False; self.request.settimeout(5)
        try:
            p=self.read(); now=time.monotonic()
            with self.server.lock:
                self.server.failures=[x for x in self.server.failures if now-x<60]
                if len(self.server.failures)>=5:
                    self.reply({'ok':False,'error':'Too many attempts. Wait 60 seconds.'}); return
                if p.get('op')!='pair' or not isinstance(p.get('pin'),str) or not secrets.compare_digest(p['pin'],self.server.pin) or now>self.server.pin_until:
                    self.server.failures.append(now); self.reply({'ok':False,'error':'Code wrong or expired. Restart receiver for a fresh code.'}); return
                if self.server.active:
                    self.reply({'ok':False,'error':'Another phone is connected'}); return
                self.server.active=True; owned=True
            self.reply({'ok':True,'protocol':2}); print('Phone connected:',self.client_address[0],flush=True)
            self.request.settimeout(8)
            while True:
                p=self.read(); self.server.mouse.apply(p); self.reply({'ok':True})
        except (EOFError,ConnectionError,TimeoutError): pass
        except Exception as e:
            try: self.reply({'ok':False,'error':str(e)})
            except OSError: pass
        finally:
            if owned:
                try: self.server.mouse.release()
                except Exception as e: print('Input release failed:',e,flush=True)
                with self.server.lock: self.server.active=False
                print('Phone disconnected',flush=True)

def addresses():
    return sorted({x[4][0] for x in socket.getaddrinfo(socket.gethostname(),None,socket.AF_INET) if not x[4][0].startswith('127.')})

def main():
    a=argparse.ArgumentParser(description='Salt Air Mouse receiver. Ctrl+C stops control.')
    a.add_argument('--host',default='0.0.0.0'); a.add_argument('--port',type=int,default=8765)
    a.add_argument('--dry-run',action='store_true',help='Validate commands without moving the cursor')
    args=a.parse_args()
    with Server((args.host,args.port),Mouse(args.dry_run)) as server:
        print('Laptop IPv4 addresses:',', '.join(addresses()) or 'Run ipconfig to find Wi-Fi IPv4')
        print(f'Port: {args.port} | Pairing code: {server.pin} (valid 5 minutes)')
        print('Use trusted private Wi-Fi only. Allow Python on PRIVATE networks in Windows Firewall.')
        print('DRY RUN: cursor will not move' if args.dry_run else 'Ready. Ctrl+C stops receiver.',flush=True)
        try: server.serve_forever()
        except KeyboardInterrupt: print('\nStopped')
        finally: server.mouse.release()
if __name__=='__main__': main()
