# Build 259 — NPC movement/combo tuning

## База и состав

Build 259 собран детерминированно от точного `settledlands-0.8.0-build258-stats-abi-fix.jar` (SHA-256 `15698d90c57f2aca7b38e30474a130be1d9c17a489c08bf73e61b221553ad19e`). Изменены ровно три JAR-записи:

- `dev/settledlands/npc/NpcAutoAiEngine255.class` — AI-поведение;
- `META-INF/neoforge.mods.toml` — версия и changelog;
- `settledlands-patch-info.txt` — patch-info.

`GuideNpc.class`, классы анимационного рендера, UI, модели и прочие 420 записей тождественны build 258. Сигнатура `AttributeInstance.removeModifier(ResourceLocation)Z` из ABI hotfix build 258 сохранена.

## Изменения

- При навигации к боевой цели больше не вызываются явный `faceTarget` и `LookControl.setLookAt(target, ...)`: корпус не должен специально разворачиваться к цели, пока NPC идёт. Поворот головы к цели намеренно не включали — предпочтён надёжный вариант без активного взгляда на цель во время ходьбы. Во время стационарного боевого действия NPC по-прежнему смотрит на цель.
- Спокойный множитель navigation speed повышен с `0.50` до `0.55` (+10%). Боевая скорость сохранена на `1.0`; множитель Stats и компенсация inverse movement-attribute не изменены.
- Обычный выбор атаки теперь всегда использует одну из двух многосоставных Ivy-анимаций: `orthodox_left_jab_right_cross` или `orthodox_jab_jab_hook`. В обоих вариантах заданы соответствующие несколько impact ticks. Случайный дальний рывок также заканчивается одной из этих серий; короткое скольжение при импульсе сохранено.

## Проверки и ограничения

`tests/gate_build259.py` проверяет воспроизводимую сборку, точный трёх-entry diff, сохранение boolean ABI, отсутствие принудительного поворота к цели в walking branch, две многосоставные анимации, повышенный множитель, JVM linkage и runtime сценарий второго попадания в серии. Четыре verifier-valid негативных мутанта должны быть обнаружены. Gate прошёл в основном checkout и отдельно в распаковке source ZIP.

Полноценный Minecraft/NeoForge/modpack запуск не выполнялся. Перед использованием — копия мира и ручная проверка по `CHECKLIST-TEST-BUILD259-RU.md`.
