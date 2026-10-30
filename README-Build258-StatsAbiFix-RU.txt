SettledLands 0.8.0 — build 258
Срочное исправление вылета build 257

Build 257 запускал синхронизацию характеристик в серверном AI-тика NPC, но вызывал несовместимую JVM-сигнатуру `AttributeInstance.removeModifier`. В Minecraft 1.21.1 метод возвращает `boolean`; build 257 был скомпилирован как `void`, и при первом тике NPC сервер падал с `NoSuchMethodError`.

Build 258 меняет только точную сигнатуру API на `removeModifier(ResourceLocation)Z`. Значение возврата игнорируется. Исправление покрывает и стандартные значения Stats (10/10/10, когда модификатор нужно удалить/не добавлять), и переход к другим значениям, и возврат к 10. Поведение build 257 — половинная спокойная скорость, полный боевой темп, 20–45 секунд ожидания, hostile auto-attack и per-NPC Stats — не менялось.

Кандидат построен от точного JAR build 257. Изменены только три JAR entries: `NpcAutoAiEngine255.class`, `META-INF/neoforge.mods.toml`, `settledlands-patch-info.txt`; `GuideNpc` и прочие классы не менялись. Полный gate build 258, включая тест точной boolean-сигнатуры и четыре негативных мутанта, прошёл в основном checkout и независимо из source ZIP. Отдельно учтён реальный crash log пользователя от Minecraft 1.21.1 / NeoForge 21.1.253. Другой полный игровой прогон не выполнялся.

При обновлении удалите build 257 из `mods`, поставьте только build 258 и сначала используйте копию мира. См. `CHECKLIST-TEST-BUILD258-RU.md`.
