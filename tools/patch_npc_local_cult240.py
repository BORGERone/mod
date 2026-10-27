#!/usr/bin/env python3
"""Build 240: possession requires the target NPC's own local `культ` >= 1.

Build 239 already checks that exact local integer in PossessionSystem.cult.
However, PossessionCultBridge first copies a player's personal/shared points to
that NPC's local variable, making the check pass even when the NPC was not
marked. Disable only that event bridge; preserve the existing possession gate,
NPC-variable implementation, resurrection, events, and all other behavior.
"""
import argparse
import hashlib
import json
import pathlib
import sys
import tomllib
import zipfile

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import classedit
import decode
from patch_corpse_pose237 import assert_pool_roundtrip, find_invoke

BASE_SHA = 'b68fd79357da507fff35939964816c2a301101ac3afb31298bf4ea31ab7a4290'
OLD_VERSION = '0.8.0-build239-empty-corpse-cleanup'
NEW_VERSION = '0.8.0-build240-npc-local-cult'
BRIDGE = 'dev/settledlands/npc/PossessionCultBridge'
PLAYER = 'net/minecraft/world/entity/player/Player'
ENTITY = 'net/minecraft/world/entity/Entity'
EVENT = 'net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$EntityInteractSpecific'
GATE = 'dev/settledlands/npc/PossessionSystem'
DIALOGUE = 'dev/settledlands/npc/GuideDialogue'
LANG_KEY = 'message.settledlands.possession.no_cult'
NEW_MESSAGES = {
    'ru_ru': 'Вселение невозможно: локальная переменная NPC «культ» должна быть не меньше 1.',
    'en_us': 'Cannot possess this NPC: its local variable "культ" must be at least 1.',
}
CHANGELOG = ('Build 240: possession requires the target NPC\'s own local variable "культ" to be at least 1; '
             'player points no longer mark NPCs automatically.')
PATCHINFO = ('сборка 240: вселение только в NPC с его локальной переменной «культ» от 1; '
             'очки игрока больше не создают переменную у NPC автоматически.')
EXPECTED_DIFF = {BRIDGE + '.class', 'assets/settledlands/lang/ru_ru.json',
                 'assets/settledlands/lang/en_us.json', 'META-INF/neoforge.mods.toml',
                 'settledlands-patch-info.txt'}


def patch_bridge(raw):
    k = classedit.Klass(raw)
    assert_pool_roundtrip(k, raw)
    m = k.find_method('onInteractSpecific', '(L' + EVENT + ';)V')
    assert m is not None
    parts = classedit.code_parts(k, m)
    code = parts['code']
    # The subscriber is retained for binary compatibility and its annotations
    # and all other methods are untouched. No-op the sole call to `mirror`.
    # Keep the instruction stream length and final return, preserving metadata.
    assert len(code) == 12 and code[-1] == 0xb1 and not parts['exc']
    assert not decode.targets(code)
    pos, length = find_invoke(k, code, BRIDGE, 'mirror', '(L' + PLAYER + ';L' + ENTITY + ';)V')
    assert pos == 8 and length == 3 and code[:1] == b'\x2a' and code[4:5] == b'\x2a'
    parts['code'] = b'\x00' * (len(code) - 1) + b'\xb1'
    return k.dump(m, classedit.build_code_body(k, parts))


def replace_message(raw, language):
    text = raw.decode('utf-8')
    before = json.loads(text)
    new = NEW_MESSAGES[language]
    # Change the single value, not the formatting or any other translation.
    old_line = json.dumps(LANG_KEY, ensure_ascii=False) + ': ' + json.dumps(before[LANG_KEY], ensure_ascii=False)
    new_line = json.dumps(LANG_KEY, ensure_ascii=False) + ': ' + json.dumps(new, ensure_ascii=False)
    assert text.count(old_line) == 1
    out = text.replace(old_line, new_line, 1)
    after = json.loads(out)
    assert after[LANG_KEY] == new and {k: v for k, v in after.items() if k != LANG_KEY} == \
           {k: v for k, v in before.items() if k != LANG_KEY}
    return out.encode('utf-8')


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--base', required=True, type=pathlib.Path)
    ap.add_argument('--out', required=True, type=pathlib.Path)
    args = ap.parse_args()
    digest = hashlib.sha256(args.base.read_bytes()).hexdigest()
    if digest != BASE_SHA:
        raise SystemExit('unexpected base SHA-256: ' + digest)
    if args.base.resolve() == args.out.resolve():
        raise SystemExit('refusing to overwrite the input jar')
    with zipfile.ZipFile(args.base) as base:
        assert base.testzip() is None and len(base.namelist()) == len(set(base.namelist()))
        entries = {i.filename: base.read(i.filename) for i in base.infolist()}
        updated = dict(entries)
        updated[BRIDGE + '.class'] = patch_bridge(entries[BRIDGE + '.class'])
        for lang in NEW_MESSAGES:
            path = f'assets/settledlands/lang/{lang}.json'
            updated[path] = replace_message(entries[path], lang)
        toml = entries['META-INF/neoforge.mods.toml'].decode('utf-8')
        needle = 'version="' + OLD_VERSION + '"'
        assert toml.count(needle) == 1 and toml.count("description='''\n") == 1
        toml = toml.replace(needle, 'version="' + NEW_VERSION + '"', 1)
        toml = toml.replace("description='''\n", "description='''\n" + CHANGELOG + '\n', 1)
        assert tomllib.loads(toml)['mods'][0]['version'] == NEW_VERSION
        updated['META-INF/neoforge.mods.toml'] = toml.encode('utf-8')
        updated['settledlands-patch-info.txt'] = (PATCHINFO + '\n' +
            entries['settledlands-patch-info.txt'].decode('utf-8')).encode('utf-8')
        changed = {n for n in entries if updated[n] != entries[n]}
        assert changed == EXPECTED_DIFF and set(updated) == set(entries), changed
        with zipfile.ZipFile(args.out, 'w') as out:
            for info in base.infolist():
                out.writestr(info, updated[info.filename])
    with zipfile.ZipFile(args.out) as z:
        assert z.testzip() is None and len(z.namelist()) == len(set(z.namelist()))
    data = args.out.read_bytes()
    print('changed:', sorted(changed))
    print(f'{args.out}: {len(data)} bytes sha256 {hashlib.sha256(data).hexdigest()}')


if __name__ == '__main__':
    main()
