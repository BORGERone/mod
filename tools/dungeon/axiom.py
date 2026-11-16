import sys,math; sys.path.insert(0,'/tmp')
from bp import read_bp

def region_blocks(r):
    bs=r['BlockStates']; pal=bs['palette']; longs=bs['data']
    bits=max(4, math.ceil(math.log2(max(2,len(pal)))))
    per=64//bits; mask=(1<<bits)-1; out=[]
    for v in longs:
        u=v & 0xFFFFFFFFFFFFFFFF
        for k in range(per): out.append((u>>(k*bits))&mask)
    return pal,out[:4096]

def blueprint(path):
    root,data=read_bp(path)
    grid={}
    for r in data['BlockRegion']:
        pal,out=region_blocks(r)
        if len(out)<4096: raise ValueError('short region %d'%len(out))
        ox,oy,oz=r['X']*16,r['Y']*16,r['Z']*16
        for i,idx in enumerate(out):
            y=i>>8; z=(i>>4)&15; x=i&15
            nm=pal[idx]['Name'] if idx<len(pal) else '?'
            if nm in ('minecraft:void_air','minecraft:air'): continue
            grid[(ox+x, oy+y, oz+z)]=nm
    return root,grid

def props(path):
    root,data=read_bp(path)
    return root,data
