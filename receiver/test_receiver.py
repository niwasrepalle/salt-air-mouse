import json, socket, threading, time, unittest
from receiver import Server, Mouse, INPUT
import ctypes

class RecordingMouse(Mouse):
    def __init__(self): super().__init__(True); self.events=[]
    def send(self,*args,**kwargs): self.events.append((args,kwargs))
    def key(self,vk,up=False): self.events.append((('key',vk,up),{}))

class ProtocolTests(unittest.TestCase):
    def setUp(self):
        self.mouse=RecordingMouse(); self.server=Server(('127.0.0.1',0),self.mouse)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True); self.thread.start()
        self.clients=[]
    def tearDown(self):
        for s,f in self.clients: f.close();s.close()
        self.server.shutdown();self.server.server_close()
    def client(self):
        s=socket.create_connection(self.server.server_address);s.settimeout(2);f=s.makefile('rwb');self.clients.append((s,f));return f
    def request(self,f,p):
        f.write((json.dumps(p)+'\n').encode());f.flush();return json.loads(f.readline())
    def pair(self):
        f=self.client(); self.assertTrue(self.request(f,{'op':'pair','pin':self.server.pin})['ok']);return f
    def test_wrong_pin_and_no_input(self):
        f=self.client();self.assertFalse(self.request(f,{'op':'pair','pin':'bad'})['ok']);self.assertEqual(self.mouse.events,[])
    def test_expired_pin(self):
        self.server.pin_until=0;f=self.client();self.assertFalse(self.request(f,{'op':'pair','pin':self.server.pin})['ok'])
    def test_single_controller_and_valid_commands(self):
        f=self.pair();second=self.client();self.assertFalse(self.request(second,{'op':'pair','pin':self.server.pin})['ok'])
        for p in [{'op':'move','x':0.6,'y':-0.6},{'op':'move','x':0.6,'y':-0.6},{'op':'left'},{'op':'right'},{'op':'scroll','amount':120},{'op':'center'},{'op':'ping'}]:
            self.assertTrue(self.request(f,p)['ok'])
        self.assertEqual(self.mouse.events[0][0],(1,1,-1));self.assertEqual(len(self.mouse.events),6)
    def test_invalid_nan(self):
        f=self.pair();self.assertFalse(self.request(f,{'op':'move','x':float('nan'),'y':0})['ok']);self.assertFalse(self.mouse.events)
    def test_invalid_operation(self):
        f=self.pair();self.assertFalse(self.request(f,{'op':'shell'})['ok'])
    def test_oversized_packet(self):
        f=self.pair();f.write(b'x'*2050+b'\n');f.flush();self.assertFalse(json.loads(f.readline())['ok'])
    def test_rate_limit(self):
        for i in range(5): self.assertFalse(self.request(self.client(),{'op':'pair','pin':'bad'})['ok'])
        self.assertIn('Too many',self.request(self.client(),{'op':'pair','pin':self.server.pin})['error'])
    def test_drag_release_on_disconnect(self):
        f=self.pair();self.assertTrue(self.request(f,{'op':'left_down'})['ok'])
        self.assertTrue(self.mouse.left_held)
        s,_=self.clients.pop();f.close();s.close()
        until=time.monotonic()+2
        while self.server.active and time.monotonic()<until:time.sleep(.01)
        self.assertFalse(self.server.active);self.assertFalse(self.mouse.left_held)
        self.assertEqual([e[0][0] for e in self.mouse.events],[2,4])
    def test_zoom_modifier_release(self):
        f=self.pair();self.assertTrue(self.request(f,{'op':'zoom','amount':120})['ok'])
        self.assertEqual(self.mouse.events[0][0],('key',17,False))
        self.assertEqual(self.mouse.events[1],((0x800,),{'data':120}))
        self.assertEqual(self.mouse.events[2][0],('key',17,True))
    def test_zoom_releases_modifier_on_error(self):
        def failed(*a,**kw):raise RuntimeError('injected failure')
        self.mouse.send=failed
        with self.assertRaises(RuntimeError):self.mouse.apply({'op':'zoom','amount':120})
        self.assertEqual(self.mouse.events[-1][0],('key',17,True))
    def test_horizontal_scroll(self):
        f=self.pair();self.assertTrue(self.request(f,{'op':'hscroll','amount':-60})['ok'])
        self.assertEqual(self.mouse.events[-1],((0x1000,),{'data':-60}))
    def test_invalid_scroll_type(self):
        f=self.pair();self.assertFalse(self.request(f,{'op':'zoom','amount':'120'})['ok']);self.assertFalse(self.mouse.events)
    def test_drag_down_is_idempotent(self):
        f=self.pair()
        for op in ['left_down','left_down','left_up','left_up']:self.assertTrue(self.request(f,{'op':op})['ok'])
        self.assertEqual([e[0][0] for e in self.mouse.events],[2,4])
    def test_existing_control_key_is_not_released(self):
        self.mouse.control_pressed=lambda:True
        self.mouse.apply({'op':'zoom','amount':120})
        self.assertEqual(len(self.mouse.events),1)
        self.assertEqual(self.mouse.events[0][0],(0x800,))
    def test_windows_structure_size(self):
        self.assertEqual(ctypes.sizeof(INPUT),40 if ctypes.sizeof(ctypes.c_void_p)==8 else 28)

if __name__=='__main__': unittest.main()
