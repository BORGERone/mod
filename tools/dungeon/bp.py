import sys,struct,gzip,os
sys.path.insert(0,'/tmp')
from nbt import R, payload

def read_bp(path):
    d=open(path,'rb').read()
    # NBT header: find 0x0A root at some offset (Axiom writes 8 bytes then 0x0A)
    off=None
    for o in range(0,24):
        r=R(d); r.i=o
        try:
            if r.u1()!=10: continue
            r.st(); root=payload(r,10)
            if 'Version' in root: off=o; end=r.i; break
        except Exception: pass
    if off is None: raise ValueError('no header nbt')
    rest=d[end:]
    pnglen=struct.unpack('>i',rest[:4])[0]
    after=rest[4+pnglen:]
    blen=struct.unpack('>i',after[:4])[0]
    blob=after[4:4+blen]
    raw=gzip.decompress(blob)
    r2=R(raw); r2.u1(); r2.st(); data=payload(r2,10)
    return root, data

def blocks(data):
    """yield (x,y,z,name,props) for one-block or multi-block blueprint"""
    for region in data.get('BlockRegion') or []:
        ox,oy,oz=region.get('X',0),region.get('Y',0),region.get('Z',0)
        bs=region.get('BlockStates') or {}
        pal=bs.get('palette') or []
        arr=bs.get('data') or []
        # find size
        mn=[0,0,0]; mx=[0,0,0]
        if 'Size' in region: 
            pass
        yield region, pal, arr, (ox,oy,oz)
