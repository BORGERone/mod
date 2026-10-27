#!/usr/bin/env bash
# Settled Lands — проверка способностей песочницы. Запускать ПЕРВЫМ ДЕЛОМ в любой новой сессии.
# Печатает вердикт: COMPILE-CAPABLE (можно assembly из исходников) или JAR-ONLY (только готовый jar).
#
#   bash tools/check_env.sh
#
# Ничего не качает и не меняет — только измеряет.

set -u
say() { printf '%s\n' "$*"; }
hr() { say "------------------------------------------------------------"; }

say "=== 1. EGRESS (нужны все 5 для компиляции) ==="
HOSTS="https://api.adoptium.net/v3/info/available_releases https://piston-meta.mojang.com/mc/game/version_manifest_v2.json https://repo1.maven.org/maven2/ https://maven.neoforged.net/releases/ https://pypi.org/simple/"
OPEN=0; TOTAL=0
for u in $HOSTS; do
  TOTAL=$((TOTAL+1))
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 8 "$u" 2>/dev/null)
  case "$code" in 2*|3*|4*) mark="OK  "; OPEN=$((OPEN+1));; *) mark="BLOCKED";; esac
  printf '  %-8s %-4s %s\n' "$mark" "$code" "$u"
done

say ""
say "=== 2. КОМПИЛЯТОР (javac или java с модулем jdk.compiler) ==="
JAVAC=""
if command -v javac >/dev/null 2>&1; then JAVAC=$(command -v javac); fi
if [ -z "$JAVAC" ]; then
  for cand in \
    "$HOME/settledlands/jdk21/bin/javac" \
    "$HOME/settledlands/jdk/bin/javac" \
    /usr/lib/jvm/*/bin/javac; do
    [ -x "$cand" ] && JAVAC="$cand" && break
  done
fi
if [ -n "$JAVAC" ]; then
  say "  OK   javac: $JAVAC ($("$JAVAC" -version 2>&1))"
else
  say "  НЕТ  javac не найден"
fi
JAVA=""
if command -v java >/dev/null 2>&1; then JAVA=$(command -v java); fi
if [ -z "$JAVA" ] && [ -x "$HOME/settledlands/jdk21/bin/java" ]; then JAVA="$HOME/settledlands/jdk21/bin/java"; fi
if [ -z "$JAVA" ]; then
  for cand in "$HOME"/tools/jdkpkg/*/java-runtime/bin/java; do
    [ -x "$cand" ] && JAVA="$cand" && break
  done
fi
if [ -n "$JAVA" ]; then
  if "$JAVA" --list-modules 2>/dev/null | grep -q '^jdk.compiler'; then
    say "  OK   $JAVA содержит jdk.compiler (source-file mode доступен)"
  else
    say "  НЕТ  $JAVA без jdk.compiler (это JRE: java -source mode невозможен)"
  fi
else
  say "  НЕТ  java не найден"
fi

say ""
say "=== 3. КЭШИ КОМПИЛЯЦИИ (персистятся только если влезают в снапшот) ==="
for d in "$HOME/settledlands/cache/cpmin" "$HOME/settledlands/cache/cp228" "$HOME/settledlands/jdk21"; do
  if [ -d "$d" ]; then
    n=$(find "$d" -type f 2>/dev/null | wc -l); sz=$(du -sh "$d" 2>/dev/null | cut -f1)
    say "  есть $d ($n файлов, $sz)"
  else
    say "  нет  $d"
  fi
done
CP="$HOME/settledlands/cache/cpmin/classpath.txt"
if [ -f "$CP" ]; then say "  есть classpath.txt ($(wc -c <"$CP") байт)"; else say "  нет  classpath.txt (make: prepare_classpath.py)"; fi

say ""
say "=== 4. АРТЕФАКТЫ (база хирургии) ==="
for f in "$HOME/settledlands/build92/settledlands-0.8.0-build228-scenefix.jar" \
         "$HOME/mod/settledlands-0.8.0-build228-scenefix.jar" \
         "$HOME/settledlands/build92/settledlands-0.8.0-build227-restore.jar"; do
  [ -f "$f" ] && say "  есть $f ($(sha256sum "$f" | cut -c1-16)…)" || say "  нет  $f"
done
for f in "$HOME/mod/SettledLands-Build228-SceneFix-Source.zip" "$HOME/settledlands/build92/AI-GUIDE-SettledLands.md"; do
  [ -f "$f" ] && say "  есть $f" || say "  нет  $f"
done

say ""
hr
if [ "$OPEN" -eq "$TOTAL" ] || { [ -n "$JAVAC" ] && [ -f "$CP" ]; }; then
  say "ВЕРДИКТ: COMPILE-CAPABLE — есть компилятор и/или classpath; сборка из исходников возможна."
  say "  Если javac нет, но egress открыт: bash settledlands/ensure_jdk.sh (~11 c), затем prepare_classpath.py."
else
  say "ВЕРДИКТ: JAR-ONLY — компиляции не будет. Рабочий путь: готовый jar из репозитория/Drive,"
  say "  плюс инспекция/патч байткода. Не тратить ход на попытки поставить JDK через pip (там JRE)."
fi
hr
say "Напоминание: -sourcepath НЕ использовать при компиляции (подтянет устаревшие NpcContent и др. из patch-src)."
