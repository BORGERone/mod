#!/usr/bin/env python3
"""Build 260 compile-time API silhouettes derived from the corrected Build 258 ABI."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parent))
import gen_api258

FILES = dict(gen_api258.FILES)
_profile = FILES['dev/settledlands/npc/NpcProfile.java']
_old = 'public boolean idleWalking;'
assert _profile.count(_old) == 1
FILES['dev/settledlands/npc/NpcProfile.java'] = _profile.replace(
    _old, _old + '\n    public boolean lookAtPlayer = true;', 1
)


def main(out: Path):
    for rel, source in FILES.items():
        path = out / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(source, encoding='utf-8')

if __name__ == '__main__':
    main(Path(sys.argv[1]))
