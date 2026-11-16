import sys; sys.path.insert(0,'/tmp')
from axiom import blueprint

LAPIS='minecraft:lapis_block'; RED='minecraft:redstone_block'

def load(n):
    root,g=blueprint('/tmp/dungeon/%s.bp'%n)
    g={p:v for p,v in g.items() if v not in ('minecraft:void_air','minecraft:air')}
    xs=[p[0] for p in g];ys=[p[1] for p in g];zs=[p[2] for p in g]
    bb=(min(xs),min(ys),min(zs),max(xs),max(ys),max(zs))
    E=[p for p,v in g.items() if v==LAPIS]
    X=[p for p,v in g.items() if v==RED]
    return {'name':n,'blocks':g,'bb':bb,'E':E,'X':X}

def rot(g,turns):
    """rotate blocks clockwise `turns`*90 about own bbox, re-based to origin"""
    turns%=4
    out=dict(g)
    for _ in range(turns):
        xs=[p[0] for p in out]; zs=[p[2] for p in out]
        x0,z0=min(xs),min(zs); w=max(xs)-x0; d=max(zs)-z0
        out={(z-z0, y, w-(x-x0)):v for (x,y,z),v in out.items()}
    xs=[p[0] for p in out]; zs=[p[2] for p in out]
    x0,z0=min(xs),min(zs)
    return {(x-x0,y,z-z0):v for (x,y,z),v in out.items()}

def normal_dir(fromX, fromE):
    """direction from entrance marker to exit marker (unit), in bounds"""
    dx=fromX[0]-fromE[0]; dz=fromX[2]-fromE[2]
    n=max(abs(dx),abs(dz)) or 1
    return (round(dx/n),round(dz/n))
