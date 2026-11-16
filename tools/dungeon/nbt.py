import struct
class R:
    def __init__(s,d): s.d=d; s.i=0
    def u1(s):
        v=s.d[s.i]; s.i+=1; return v
    def u2(s):
        v=struct.unpack('>H',s.d[s.i:s.i+2])[0]; s.i+=2; return v
    def i2(s):
        v=struct.unpack('>h',s.d[s.i:s.i+2])[0]; s.i+=2; return v
    def i4(s):
        v=struct.unpack('>i',s.d[s.i:s.i+4])[0]; s.i+=4; return v
    def i8(s):
        v=struct.unpack('>q',s.d[s.i:s.i+8])[0]; s.i+=8; return v
    def f4(s):
        v=struct.unpack('>f',s.d[s.i:s.i+4])[0]; s.i+=4; return v
    def f8(s):
        v=struct.unpack('>d',s.d[s.i:s.i+8])[0]; s.i+=8; return v
    def st(s):
        n=s.u2()
        if n>4096: raise ValueError('bad string len %d'%n)
        v=s.d[s.i:s.i+n].decode('utf-8','replace'); s.i+=n; return v
def payload(r,tid):
    if tid==1: return r.u1()
    if tid==2: return r.i2()
    if tid==3: return r.i4()
    if tid==4: return r.i8()
    if tid==5: return r.f4()
    if tid==6: return r.f8()
    if tid==7:
        n=r.i4()
        if n<0 or n>50_000_000: raise ValueError('bad array %d'%n)
        v=r.d[r.i:r.i+n]; r.i+=n; return v
    if tid==8: return r.st()
    if tid==9:
        it=r.u1(); n=r.i4()
        if n<0 or n>5_000_000: raise ValueError('bad list %d'%n)
        return [payload(r,it) for _ in range(n)]
    if tid==10:
        out={}
        while True:
            t=r.u1()
            if t==0: break
            k=r.st(); out[k]=payload(r,t)
        return out
    if tid==11:
        n=r.i4(); return [r.i4() for _ in range(n)]
    if tid==12:
        n=r.i4(); return [r.i8() for _ in range(n)]
    raise ValueError('tag %d'%tid)
def load(path):
    d=open(path,'rb').read()
    for off in range(0,24):
        r=R(d); r.i=off
        try:
            t=r.u1()
            if t!=10: continue
            name=r.st()
            root=payload(r,10)
            if 'Blocks' in root or 'Version' in root:
                return d, off, name, root, r.i
        except Exception:
            pass
    raise ValueError('no nbt found')
