#!/usr/bin/env python3
"""Build 229 «sablesweep» — хотфикс развёртывания EntityGetter без компилятора.

Что делает (zip-хирургия поверх jar228):
  1. dev/settledlands/npc/PlayerCorpsePossession.class — константы ±3.0E7 в constant pool
     заменяются на ±49999.0 (побайтово, тот же размер записи, пул не растёт).
     Причина: Sable 2.0.5 в SubLevelInclusiveLevelEntityGetter.get(AABB, Consumer)
     отбрасывает запрос, если box.getSize() > 100000.0, и пишет ERROR со стектрейсом.
     getSize() = (xSize+ySize+zSize)/3 → у бокса ±3.0E7 это 6.0E7 → запрос отменялся
     ВСЕГДА: sweep не находил ни одной сущности (баг 228 не работал) и 1 раз/сек
     заливал лог. У бокса ±49999.0 size = 99998.0 < 100000.0 → запрос проходит.
     Ограничение: бокс симметричен относительно НУЛЯ координат (так было в исходнике 228),
     поэтому чинит сущности в пределах ±49999 блоков по каждой оси.
  2. META-INF/neoforge.mods.toml — версия 0.8.0-build229-sablesweep + строка ченджлога
     (описание проверяется tomllib после записи).
  3. settledlands-patch-info.txt — строка про 229.

Ничего больше не меняется: все остальные записи копируются побайтово.

    python3 tools/patch_sweep_aabb.py --base settledlands-0.8.0-build228-scenefix.jar \
        --out settledlands-0.8.0-build229-sablesweep.jar
"""
import argparse, io, struct, sys, zipfile, tomllib

TARGET_CLASS = "dev/settledlands/npc/PlayerCorpsePossession.class"
OLD, NEW = 3.0e7, 49999.0          # NEW*3/3 = 99998 < 100000 (порог Sable)
NEW_VERSION = "0.8.0-build229-sablesweep"
CHANGELOG = (
    "Build 229: sweep fix - the stuck-death-scene watchdog no longer uses a +-30000000 block AABB. "
    "Sable 2.0.5 (SubLevelInclusiveLevelEntityGetter) aborts any entity lookup whose box size exceeds "
    "100000 and logs an ERROR stack trace, so build 228's watchdog silently found nothing and spammed "
    "the log ~1x/second; the box is now +-49999 blocks and the lookup actually runs (covers +-49999 "
    "blocks around world origin on every axis). Watchdog hits are logged as 'stuck ivy body discarded' "
    "or 'stuck ivy scene rebased' - if you never see those lines, the leftover entity is not a stuck "
    "death-scene body and needs a different fix."
)
PATCHINFO = (
    "сборка 229: починка watchdog застрявшей сцены смерти (build 228). Бокс ±3.0E7 блоков заменён на ±49999: "
    "Sable 2.0.5 отменяет любой EntityGetter-запрос с размером бокса > 100000 и пишет ERROR со стектрейсом, "
    "поэтому в 228 sweep не находил ни одной сущности (фикс не работал) и заливал лог ~1 раз/с. "
    "Теперь проход реально выполняется: застрявшее тело НПС из IvyDeathScene снимается или перебазируется, "
    "и в лог идут строки «stuck ivy body discarded» / «stuck ivy scene rebased»."
)


def patch_doubles(data: bytes):
    """Заменяет в constant pool double +3.0E7/-3.0E7 на ±NEW. Тот же размер, пул не растёт."""
    if data[:4] != b"\xca\xfe\xba\xbe":
        raise SystemExit("не class-файл")
    buf = bytearray(data)
    cp_count = struct.unpack(">H", data[8:10])[0]
    pos = 10
    replaced = []
    i = 1
    while i < cp_count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            ln = struct.unpack(">H", data[pos:pos + 2])[0]
            pos += 2 + ln
        elif tag == 6:                      # double
            val = struct.unpack(">d", data[pos:pos + 8])[0]
            if abs(abs(val) - OLD) < 1.0:
                newval = NEW if val > 0 else -NEW
                buf[pos:pos + 8] = struct.pack(">d", newval)
                replaced.append((i, val, newval))
            pos += 8
            i += 1
        elif tag == 5:                      # long
            pos += 8
            i += 1
        elif tag in (7, 8, 16, 19, 20):
            pos += 2
        elif tag == 15:
            pos += 3
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 4
        else:
            raise SystemExit(f"неизвестный тег constant pool: {tag}")
        i += 1
    if not replaced:
        raise SystemExit("константы ±3.0E7 не найдены — база не та?")
    return bytes(buf), replaced


def bump_toml(raw: bytes) -> bytes:
    text = raw.decode("utf-8")
    if f'version="{NEW_VERSION}"' in text:
        return raw
    text = text.replace('version="0.8.0-build228-scenefix"', f'version="{NEW_VERSION}"', 1)
    if f'version="{NEW_VERSION}"' not in text:
        raise SystemExit("в toml не найдена строка версии 228")
    marker = "description='''\n"
    if marker not in text:
        raise SystemExit("в toml не найден блок description")
    text = text.replace(marker, marker + CHANGELOG + "\n", 1)
    out = text.encode("utf-8")
    tomllib.loads(out.decode("utf-8"))          # урок 223: без валидации — краш
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    src = zipfile.ZipFile(a.base)
    out = zipfile.ZipFile(a.out, "w")
    changed = []
    for info in src.infolist():
        data = src.read(info.filename)
        if info.filename == TARGET_CLASS:
            data, rep = patch_doubles(data)
            print("константы:", ", ".join(f"#{i} {o:g} -> {n:g}" for i, o, n in rep))
            changed.append(info.filename)
        elif info.filename == "META-INF/neoforge.mods.toml":
            data = bump_toml(data)
            changed.append(info.filename)
        elif info.filename == "settledlands-patch-info.txt":
            data = data.rstrip(b"\n") + b"\n" + PATCHINFO.encode("utf-8") + b"\n"
            changed.append(info.filename)
        zi = zipfile.ZipInfo(info.filename, date_time=info.date_time)
        zi.compress_type = info.compress_type
        zi.external_attr = info.external_attr
        zi.internal_attr = info.internal_attr
        zi.create_system = info.create_system
        out.writestr(zi, data)
    out.close()
    print("изменено записей:", len(changed), "->", ", ".join(changed))
    print("готово:", a.out)


if __name__ == "__main__":
    sys.exit(main())
