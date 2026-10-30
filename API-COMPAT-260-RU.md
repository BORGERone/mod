# Build 260 — звуки NPC, направление корпуса и lookAtPlayer

## База и состав

Build 260 строится от точного build 259: `settledlands-0.8.0-build259-npc-combo-tuning.jar` (SHA-256 `ab44f422a423a428f198ae4393e55ad8409857e10e85674d1ccfa093790f5da7`). Меняются ровно пять JAR-записей: `NpcAutoAiEngine255.class`, `GuideNpc.class`, `NpcMotionBridge256.class`, TOML и patch-info. Остальные 418 из 423 entries идентичны build 259. Сигнатура `AttributeInstance.removeModifier(ResourceLocation)Z` сохранена.

## Исправления

- Причина звуков скелета подтверждена непосредственно в `GuideNpc`: `getAmbientSound`, `getHurtSound` и `getDeathSound` возвращали `SKELETON_AMBIENT/HURT/DEATH`. Они заменены на `VILLAGER_AMBIENT/HURT/DEATH`, подходящие обычному говорящему NPC.
- Корень поворота при тумблере «смотрит на игрока» находился в `GuideNpc.customServerAiStep`: он вызывал `LookControl.setLookAt(Player,...)`, который мог разворачивать всё тело во время движения. В build 260 этот вызов заменён на head-only helper: сохраняется взгляд на ближайшего игрока, но сервер не задаёт телу поворот к игроку.
- `NpcMotionBridge256` во время наземного движения временно ориентирует отрисовываемый корпус по горизонтальному вектору перемещения, независимо от состояния тумблера; исходный synchronized yaw восстанавливается в `finally`. Это также исправляет боковую ходьбу с выключенным тумблером.
- Диалог принудительно включает player-look через `shouldLookAtPlayer`, даже если профильный переключатель выключен. Рендерер перестаёт занулять yaw головы, пока NPC разговаривает; значит, диалоговый head look виден при любом состоянии тумблера.

## Проверки и ограничения

`tests/gate_build260.py` проверяет воспроизводимую сборку, точный diff, методы звуков (без ссылок на скелетные события), замену `LookControl` на head-only helper, путь отрисовки по движению, диалоговое условие, сохранение ABI, Java 21 linkage, runtime-тесты переключателя/диалога/головы и пять verifier-valid мутантов.

Основной и независимый ZIP gate — PASS. Полный Minecraft/NeoForge/modpack не запускался; пользовательская проверка нужна на копии мира по `CHECKLIST-TEST-BUILD260-RU.md`.
