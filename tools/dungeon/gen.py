"""Prototype: stitch the author's schematics into one dungeon."""
import sys, random
from collections import deque
sys.path.insert(0,'/tmp')
from stitch import load, rot

LAPIS='minecraft:lapis_block'; RED='minecraft:redstone_block'
AIR=('minecraft:air','minecraft:void_air')
OPEN=AIR+(LAPIS,RED)

def bbox(g):
    xs=[p[0] for p in g];ys=[p[1] for p in g];zs=[p[2] for p in g]
    return min(xs),min(ys),min(zs),max(xs),max(ys),max(zs)

def markers(g):
    e=[p for p,v in g.items() if v==LAPIS]
    x=[p for p,v in g.items() if v==RED]
    return (e[0] if e else None), x

def facing(g):
    """direction from the entrance marker into the body, unit horizontal"""
    e,x=markers(g)
    if e is None: return None
    b=bbox(g)
    cx=(b[0]+b[3])/2.0; cz=(b[2]+b[5])/2.0
    dx,dz=cx-e[0],cz-e[2]
    if abs(dx)>abs(dz): return (1 if dx>0 else -1,0)
    return (0,1 if dz>0 else -1)

def turned(name, want):
    """the piece rotated so its body extends along `want`"""
    piece=load(name)
    for t in range(4):
        g=rot(piece['blocks'],t)
        if facing(g)==want:
            return g,piece,t
    return None,piece,None

def carve(seed=1,count=8,pieces=None,verbose=False):
    rnd=random.Random(seed)
    start=load('старт')
    grid={p:v for p,v in start['blocks'].items() if v not in AIR}
    e,exits=markers(start['blocks'])
    queue=[]
    for m in exits:
        b=bbox(start['blocks'])
        cx,cz=(b[0]+b[3])/2.0,(b[2]+b[5])/2.0
        dx,dz=m[0]-cx,m[2]-cz
        w=(1,0) if (abs(dx)>abs(dz) and dx>0) else (-1,0) if abs(dx)>abs(dz) else (0,1) if dz>0 else (0,-1)
        queue.append((w,m))
    placed=[('старт',(0,0,0),0)]
    made=0
    LIMIT=400
    while queue and made<count and len(grid)<400000:
        w,seam=queue.pop(0)
        cands=list(pieces or [])
        rnd.shuffle(cands)
        for name in cands:
            g,piece,t=turned(name,w)
            if g is None: continue
            e2,_=markers(g)
            # adjacent, not overlapping: the new piece's entrance face sits one block
            # beyond the seam, so the two walls touch and nothing is overwritten
            off=(seam[0]-e2[0]+w[0], seam[1]-e2[1], seam[2]-e2[2]+w[1])
            body={(x+off[0],y+off[1],z+off[2]):v for (x,y,z),v in g.items() if v not in AIR}
            clash=sum(1 for p in body if p in grid)
            if clash>max(2,len(body)*0.02): continue
            # the seam must stay walkable: two cells on each side of the marker, 2 high
            ok=True
            for d in (-1,1):
                for up in (1,2):
                    q=(seam[0]+w[0]*d, seam[1]+up, seam[2]+w[1]*d)
                    if grid.get(q) not in (None,)+OPEN and body.get(q) not in (None,)+OPEN: ok=False
            if not ok: continue
            grid.update(body); made+=1
            placed.append((name,off,t))
            _,ex2=markers(g)
            for m in ex2:
                if (m[0]+off[0]-w[0],m[1]+off[1],m[2]+off[2]-w[1])==seam: continue
                dx,dz=m[0]-e2[0], m[2]-e2[2]
                nw=(1,0) if (abs(dx)>abs(dz) and dx>0) else (-1,0) if abs(dx)>abs(dz) else (0,1) if dz>0 else (0,-1)
                queue.append((nw,(m[0]+off[0],m[1]+off[1],m[2]+off[2])))
            break
    return grid,placed

def walk(grid,startpos,limit=400000):
    seen={startpos}; dq=deque([startpos])
    while dq:
        x,y,z=dq.popleft()
        for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            for dy in (0,1,-1):
                q=(x+dx,y+dy,z+dz)
                if q in seen: continue
                v=grid.get(q)
                if v is not None and v in OPEN:
                    if dy!=0 and (grid.get((x,y+dy,z)) not in (None,)+OPEN): continue
                    seen.add(q); dq.append(q)
                    if len(seen)>limit: return seen
                    break
    return seen
