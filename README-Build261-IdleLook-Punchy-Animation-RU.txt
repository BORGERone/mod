SettledLands 0.8.0 — Build 261

Файл: settledlands-0.8.0-build261-idle-look-punchy-animation.jar
Размер: 2 259 841 байт
SHA-256: 979d64831b47336a84a72ec0d803da4f19d5ee6e1fdb85e542e7e08b03647df6

Изменения:
• Idle NPC плавно и случайно оглядывается головой, не разворачивая корпус и не вмешиваясь в навигацию. Разговор, взгляд на игрока и бой сохраняют приоритет.
• На модели Ivy Fresh Animations/Touch’n Motion остаются активными при получении урона, во время диалога и активных Blockbench-клипов. Авторские клипы накладываются, а ходовые движения ног не исчезают под полнотельной анимацией.
• Первый-person PlayerModel-проход интегрирован с моделью/текстурой вселённого NPC, включая совместимый путь для Punchy.
• Сохранены звук, поведение корпуса, движение/бой, характеристики и прежние согласованные механики Build 260 и ниже.

База: settledlands-0.8.0-build260-npc-facing-sounds.jar
База SHA-256: 8d47171845652c1a3499643ca1936a7d6b742d9e88811751d94d84bef47d2ebc

Основной release gate: `tests/gate_build261.py` — PASS. Включает воспроизводимость, структурную проверку JAR, Java 21 JVM-тесты idle-взгляда, NPC-анимаций и first-person replacement, плюс два убитых verifier-valid мутанта (idle scan / first-person context). Minecraft/NeoForge/modpack не запускался. Punchy не предоставлялся для теста; версия пользователя неизвестна, поэтому обязательна реальная проверка по `CHECKLIST-TEST-BUILD261-RU.md`.

Исходный архив: `SettledLands-Build261-IdleLook-Punchy-Animation-Source.zip` (сборка, source/tests, Build 260 baseline, Build 261 candidate, manifest). Внешние Gson source и Java runtime не включены; контрольные суммы находятся в `SHA256SUMS-build261.txt`.
