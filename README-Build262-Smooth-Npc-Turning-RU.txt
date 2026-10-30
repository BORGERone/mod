SettledLands 0.8.0 — Build 262

Файл: settledlands-0.8.0-build262-smooth-npc-turning.jar
Размер: 2 260 085 байт
SHA-256: 21fc8fa7c861c3652f020ce9bb10bac133ac253675084982e65f16cc734f2531

Что изменено:
• При ходьбе корпус и yaw сущности направляются по горизонтальному вектору движения.
• После остановки NPC сохраняет направление последнего шага: первая цель idle-сканирования набирается плавно, без резкого обратного разворота.
• В idle голова плавно ведёт случайный взгляд; корпус и yaw сущности медленнее следуют той же случайной цели, поэтому меняется и направление торса.
• Диалог, бой, активная навигация, движение и взгляд на ближайшего игрока сохраняют приоритет над idle-сканированием.
• Build 261 renderer для Ivy/FA/TnM, Blockbench clips, движение ног и first-person NPC arms/Punchy path не менялись.
• Сохранены все предшествующие механики Build 261 и ранее.

Точная база: settledlands-0.8.0-build261-idle-look-punchy-animation.jar
База SHA-256: 979d64831b47336a84a72ec0d803da4f19d5ee6e1fdb85e542e7e08b03647df6

`tests/gate_build262.py` PASS: Java 21 runtime-сценарии проверяют travel-facing, первую плавную каденцию после остановки, диапазон случайных yaw корпуса/головы, приоритет диалога и регрессии AI/combo; отдельно пройдены Build 261 animation/first-person сценарии, точный diff, воспроизводимая сборка и verifier-valid negative control. Minecraft/NeoForge/modpack **не запускались**; необходима проверка в игре по `CHECKLIST-TEST-BUILD262-RU.md`.

Source ZIP: SettledLands-Build262-Smooth-Npc-Turning-Source.zip. Проверить `BACKUP-MANIFEST.sha256`, самостоятельное воспроизведение архива и независимый gate; Java 21 runtime и исходный архив Gson не включены. Checksum-файл: `SHA256SUMS-build262.txt`.
