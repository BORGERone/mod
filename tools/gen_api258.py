#!/usr/bin/env python3
"""Compile-time API silhouettes for build 258; exact 1.21.1 removeModifier ABI."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_api257

FILES = dict(gen_api257.FILES)
_key = 'net/minecraft/world/entity/ai/attributes/AttributeInstance.java'
_src = FILES[_key]
_old = 'public void removeModifier(ResourceLocation id) {}'
_new = 'public boolean removeModifier(ResourceLocation id) { return false; }'
assert _src.count(_old) == 1
FILES[_key] = _src.replace(_old, _new, 1)


def main(out: Path):
    for rel, source in FILES.items():
        path = out / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')

if __name__ == '__main__':
    main(Path(sys.argv[1]))
