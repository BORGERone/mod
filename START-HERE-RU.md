# START HERE (новая сессия)

Ты продолжаешь работу над модом SettledLands 0.8.0 (Minecraft 1.21 / NeoForge).
Открытая задача: **у трупа игрока нет модели** («прозрачный» труп).

Читай по порядку, до любых действий и сборок:
1. **`HANDOFF-NEXT-SESSION-RU.md`** — вся картина: путь рендера по байткоду,
   что уже сделано и отозвано, билды с хешами, маркеры для поиска в логе,
   дерево решений A/B/C/D, инструменты, шлюз проверок, грабли байткода, правила юзера.
2. `AI-GUIDE-SettledLands.md` — правила и уроки (особенно §2 шлюз, §11 JVM-верификация).
3. `SESSION-STATE.md` — журнал решений по билдам.

Первые три команды:
```bash
cd /home/user/mod
git fetch origin && git log --oneline -3 origin/main -- latest.log
git checkout origin/main -- latest.log && grep -an "SettledLands\\|VerifyError\\|is not a jar" latest.log | tail -30
```
Ветка работы: `arena/829e0ad0-mod` (только в неё пушить, PR — из неё).
Сборка — без компилятора: только правка байткода (`tools/classedit.py`, `tools/smt.py`)
и новые классы. Перед любой раздачей — шлюз из HANDOFF §8 целиком.
