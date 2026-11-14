#!/usr/bin/env bash
# Собирает .xpi (это просто zip с manifest.json в корне).
#   ./build.sh          → dist/bottom-gap-<версия>.xpi
set -euo pipefail
cd "$(dirname "$0")"

VERSION=$(python3 -c "import json;print(json.load(open('extension/manifest.json'))['version'])")
OUT="dist/bottom-gap-${VERSION}.xpi"
mkdir -p dist

rm -f "$OUT"
python3 - "$OUT" <<'PY'
import os, sys, zipfile
out = sys.argv[1]
src = "extension"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    for root, dirs, files in os.walk(src):
        dirs.sort()
        for name in sorted(files):
            full = os.path.join(root, name)
            z.write(full, os.path.relpath(full, src))
print("built:", out)
PY

echo "Проверка содержимого:"
python3 -c "import zipfile,sys;print('\n'.join(zipfile.ZipFile(sys.argv[1]).namelist()))" "$OUT"
