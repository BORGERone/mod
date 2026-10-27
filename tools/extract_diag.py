#!/usr/bin/env python3
"""Выжимка диагностики из latest.log пользователя (Settled Lands).

    python3 tools/extract_diag.py latest.log

Печатает:
  * версию мода, которая РЕАЛЬНО загрузилась (первое, что нужно проверить — урок «репорт против 227 на jar 222»);
  * все строки Settled Lands про труп/тело/сцену смерти;
  * сработал ли sweep 228 (stuck ivy body discarded / rebased);
  * предупреждения рендера/моделей (bbmodel, renderer, texture, missing model);
  * ошибки и исключения, связанные с settledlands.
"""
import re
import sys

PATS = [
    ("ВЕРСИЯ", r"loaded 0\.8\.0-build|Settled Lands: сборка|Found mod file .*settledlands|-> 0\.8\.0-build"),
    ("ТРУП/ТЕЛО", r"stock corpse|corpse watch|spawned our own|body stacks|corpse guarantee|dressBodyCorpse|"
                  r"player death|possession|re-possess|body items|awaiting_purgatory"),
    ("SWEEP 228", r"stuck ivy body discarded|stuck ivy scene rebased|ivydie"),
    ("МОДЕЛЬ/РЕНДЕР", r"bbmodel|uvshift|GuideRenderer|renderer|texture|missing model|Model part|"
                      r"GeckoLib|applying UV shift"),
    ("ОШИБКА", r"ERROR|Exception|Caused by|at dev\.settledlands"),
]


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    text = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
    print(f"строк в логе: {len(text)}\n")
    for title, pat in PATS:
        rx = re.compile(pat)
        hits = [l.rstrip() for l in text if rx.search(l)]
        print(f"=== {title} ({len(hits)}) ===")
        for l in hits[:40]:
            print("  " + l[:240])
        if len(hits) > 40:
            print(f"  ... ещё {len(hits) - 40}")
        print()
    return 0


if __name__ == "__main__":
    sys.exit(main())
