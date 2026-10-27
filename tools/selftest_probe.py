#!/usr/bin/env python3
"""Проверка самописного класса РЕАЛЬНОЙ JVM (не только парсером).

    python3 tools/selftest_probe.py [--java <path-to-java>]

TexProbe не зависит от Minecraft, поэтому его можно по-настоящему загрузить,
верифицировать и выполнить в JRE из песочницы. Это ловит VerifyError вида
«Illegal target of jump or branch» (инцидент 234), который парсеры не видят.

Проверяется: класс грузится без VerifyError, log() дедуплицирует (2 строки вместо 3),
logObj(null) печатает NULL.
"""
import argparse, os, subprocess, sys, tempfile

sys.path.insert(0, __file__.rsplit("/", 1)[0])
import patch_probe233 as gen

DEFAULT_JAVA = "/home/user/tools/jdkpkg/jdk4py/java-runtime/bin/java"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--java", default=DEFAULT_JAVA)
    a = ap.parse_args()

    cls, _ = gen.build_probe_class(with_main=True)
    d = tempfile.mkdtemp()
    path = os.path.join(d, "dev", "settledlands", "diag")
    os.makedirs(path)
    with open(os.path.join(path, "TexProbe.class"), "wb") as f:
        f.write(cls)

    cmd = [a.java, "-Xshare:off", "-cp", d, gen.PROBE]
    print("$", " ".join(cmd))
    r = subprocess.run(cmd, capture_output=True, text=True)
    print(r.stdout)
    print(r.stderr)
    if r.returncode != 0:
        print("ПРОВАЛ: JVM не загрузила класс")
        return 1
    out = r.stdout
    if "VerifyError" in r.stderr or "VerifyError" in out:
        print("ПРОВАЛ: VerifyError")
        return 1
    if "SELFTEST OK" not in out:
        print("ПРОВАЛ: main не доработал")
        return 1
    if out.count("selftest = A") != 1 or out.count("selftest = B") != 1:
        print("ПРОВАЛ: дедупликация не работает")
        return 1
    if "obj = NULL" not in out:
        print("ПРОВАЛ: logObj(null) не напечатал NULL")
        return 1
    print("JVM-САМОТЕСТ: OK (класс верифицирован и выполнен реальной JVM)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
