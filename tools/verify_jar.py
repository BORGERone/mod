#!/usr/bin/env python3
"""Проверка jar Settled Lands без Minecraft, javac и javap (только stdlib Python).

    python3 tools/verify_jar.py settledlands-0.8.0-buildNNN-tag.jar [--base prev.jar] [--marker healStuckIvyBodies ...]

Что проверяет:
  1. Целостность zip (testzip) и отсутствие дублей записей.
  2. META-INF/neoforge.mods.toml через tomllib (урок 223: без парсинга — краш).
  3. Языки: assets/settledlands/lang/{en_us,ru_ru}.json — валидный JSON + число ключей.
  4. mixins.json: каждый миксин из mixins/client/server реально есть в jar (с учётом package).
  5. Версии classfile и битые заголовки.
  6. --marker: наличие строк в constant pool указанного класса (по умолчанию PlayerCorpsePossession).
  7. --base: побайтовый дифф записей (что именно изменилось относительно прошлого билда).

Примеры:
  python3 tools/verify_jar.py settledlands-0.8.0-build228-scenefix.jar \
      --base settledlands-0.8.0-build227-restore.jar \
      --marker healStuckIvyBodies --marker "stuck ivy scene rebased"
"""
import argparse, json, struct, sys, zipfile


def check_integrity(z):
    print("=== 1. целостность ===")
    print("  testzip:", z.testzip(), "| записей:", len(z.namelist()),
          "| классов:", sum(1 for n in z.namelist() if n.endswith(".class")))
    names = z.namelist()
    dups = {n for n in names if names.count(n) > 1}
    print("  дубли записей:", dups or "нет")
    return not dups


def check_toml(z):
    print("\n=== 2. toml (tomllib) ===")
    try:
        import tomllib
    except ImportError:
        print("  SKIP: нужен python>=3.11")
        return None
    raw = z.read("META-INF/neoforge.mods.toml")
    try:
        d = tomllib.loads(raw.decode("utf-8"))
    except Exception as e:
        print("  ОШИБКА парсинга toml:", e)
        return None
    m = d["mods"][0]
    print(f"  modId={m['modId']} | version={m['version']} | displayName={m.get('displayName')}")
    lines = [l for l in m.get("description", "").strip().splitlines() if l.strip()]
    build_hits = [l for l in lines if l.startswith("Build ")]
    newest = build_hits[0].split(":")[0] if build_hits else "?"
    print(f"  описание: строк {len(lines)}, первая строка ченджлога = {newest}")
    if newest not in m["version"]:
        print(f"  ВНИМАНИЕ: в описании нет строки про текущую сборку ({m['version']}) — "
              f"в меню модов ченджлог отстаёт")
    deps = d.get("dependencies", {}).get(m["modId"], [])
    print(f"  зависимостей: {len(deps)}")
    return m["version"]


def check_lang(z):
    print("\n=== 3. языки ===")
    out = {}
    for f in ("assets/settledlands/lang/en_us.json", "assets/settledlands/lang/ru_ru.json"):
        try:
            j = json.loads(z.read(f))
            out[f] = len(j)
            print(f"  {f.split('/')[-1]}: {len(j)} ключей")
        except KeyError:
            print(f"  {f}: ОТСУТСТВУЕТ")
        except Exception as e:
            print(f"  {f}: битый JSON — {e}")
    if len(set(out.values())) == 1:
        print("  en/ru согласованы по числу ключей")
    elif len(out) == 2:
        print("  ВНИМАНИЕ: число ключей en и ru различается")
    return out


def check_mixins(z):
    print("\n=== 4. mixins ===")
    names = set(z.namelist())
    ok = True
    for mf in ("settledlands.mixins.json", "settledlands.compat.mixins.json"):
        if mf not in names:
            print(f"  {mf}: отсутствует (проверьте, нужен ли)")
            continue
        j = json.loads(z.read(mf))
        pkg = j.get("package", "").replace(".", "/")
        total, miss = 0, []
        for k in ("mixins", "client", "server"):
            for m in j.get(k, []):
                total += 1
                p = f"{pkg}/{m.replace('.', '/')}.class"
                if p not in names:
                    miss.append(p)
        print(f"  {mf}: миксинов {total}, отсутствует: {miss or 'нет'}")
        ok = ok and not miss
    return ok


def check_classes(z):
    print("\n=== 5. classfile ===")
    versions, bad = {}, []
    for n in z.namelist():
        if not n.endswith(".class"):
            continue
        b = z.read(n)
        if len(b) < 8 or b[:4] != b"\xca\xfe\xba\xbe":
            bad.append(n)
            continue
        v = struct.unpack(">H", b[6:8])[0]
        versions[v] = versions.get(v, 0) + 1
    print("  версии:", dict(sorted(versions.items())), "| битых:", bad or "нет")
    return not bad


def check_markers(z, cls, markers):
    print(f"\n=== 6. маркеры в {cls} ===")
    path = cls.replace(".", "/") + ".class"
    if path not in z.namelist():
        print("  класс отсутствует:", path)
        return False
    b = z.read(path)
    ok = True
    for m in markers:
        found = m.encode() in b
        print(f"  {m:40} {'НАЙДЕНО' if found else 'НЕТ'}")
        ok = ok and found
    return ok


def diff_base(z, base_path):
    print("\n=== 7. дифф против базы ===")
    b = zipfile.ZipFile(base_path)
    na, nb = z.namelist(), b.namelist()
    only_new = sorted(set(na) - set(nb))
    only_old = sorted(set(nb) - set(na))
    changed = [n for n in na if n in nb and z.read(n) != b.read(n)]
    print(f"  записей: {len(na)} vs {len(nb)}")
    print(f"  только в новом: {only_new or 'нет'}")
    print(f"  только в базе:  {only_old or 'нет'}")
    for n in changed:
        print(f"  изменено: {n:60} {len(b.read(n)):>8} -> {len(z.read(n)):>8}")
    print(f"  идентичных записей: {len(na) - len(changed)}")
    return not only_new and not only_old


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("jar")
    ap.add_argument("--base")
    ap.add_argument("--class", default="dev.settledlands.npc.PlayerCorpsePossession")
    ap.add_argument("--marker", action="append", default=[])
    a = ap.parse_args()
    z = zipfile.ZipFile(a.jar)
    fails = []
    fails.append(("целостность", check_integrity(z)))
    ver = check_toml(z)
    fails.append(("toml", ver is not None))
    check_lang(z)
    fails.append(("mixins", check_mixins(z)))
    fails.append(("classfile", check_classes(z)))
    if a.marker:
        fails.append(("маркеры", check_markers(z, a.cls if hasattr(a, "cls") else a.__dict__["class"], a.marker)))
    if a.base:
        fails.append(("дифф структуры", diff_base(z, a.base)))
    print("\n" + "-" * 60)
    bad = [n for n, ok in fails if not ok]
    if bad:
        print("ПРОБЛЕМЫ:", ", ".join(bad))
        return 1
    print("ВСЕ ПРОВЕРКИ ПРОЙДЕНЫ" + (f" (версия {ver})" if ver else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
