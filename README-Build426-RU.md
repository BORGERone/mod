# Settled Lands — Сборка 426 (`dungeon-command-fix`)

Хотфикс сборки **425** (`hell-dungeon`), устраняющий падение на загрузке мода в реальном NeoForge `21.1.265` (Minecraft `1.21.1`).

## Причина краша в сборке 425 (из `latest.log`)

При конструкторе модов NeoForge регистрирует классы с аннотацией `@EventBusSubscriber`:

```text
FMLModContainer.constructMod -> AutomaticEventSubscriber.inject -> Class.getDeclaredMethods()
java.lang.NoClassDefFoundError: com/mojang/brigadier/CommandSyntaxException
Caused by: java.lang.ClassNotFoundException: com.mojang.brigadier.CommandSyntaxException
```

- В Mojang Brigadier класс `CommandSyntaxException` находится в пакете `com.mojang.brigadier.exceptions`, а не `com.mojang.brigadier`.
- В стабах ещё со сборки 420 файл лежал по ошибочному пути `build303-src/stubs420-src/com/mojang/brigadier/CommandSyntaxException.java`, и `Command.run(...)` / `CommandSourceStack#getPlayerOrException()` бросали этот несуществующий тип.
- Из-за этого лямбды в `DungeonCommand425` (`.executes(ctx -> enter(...))`, `.executes(ctx -> createNew(...))`) записали `throws com/mojang/brigadier/CommandSyntaxException` в атрибут `Exceptions`, а `AutomaticEventSubscriber.inject` при вызове `Class.getDeclaredMethods()` упал до старта игры.

## Что исправлено в сборке 426

1. Заглушка `CommandSyntaxException` перенесена в правильный пакет `com.mojang.brigadier.exceptions` (`build303-src/stubs420-src/com/mojang/brigadier/exceptions/CommandSyntaxException.java`), старый ошибочный файл удалён; обновлены `Command.java`, `CommandSourceStack.java` и `DungeonCommand425.java`.
2. `DungeonCommand425.class` пересобран против исправленных стабов — теперь его пул констант и лямбды используют только настоящий `com/mojang/brigadier/exceptions/CommandSyntaxException` (как все родные команды базового мода: `ModCommands`, `NpcPointsCommand`, `NpcEscortCommands`, `CutsceneCommands`, `PlayAudioCommand`).
3. Все остальные 594 класса и ресурсы «Адского данжа» (`settledlands:hell_dungeon`, `start.slbp`, фильтр спавна `DungeonEvents425`, сетка `512 × 512`, интеграция в узел «Телепорт») побайтово идентичны сборке 425.
4. Проведён аудит **всех** внешних классов и стабов (`595` классов JAR, `11` слоёв стабов) против реального Minecraft `1.21.1` / NeoForge `21.1` / Brigadier — ни одного другого неверного пакета или отсутствующего класса не обнаружено.
5. Усилены офлайн-гейты, чтобы такой баг больше не мог пройти проверку:
   - `tools/check_mixin_class.py` (Phase D) теперь проверяет все внешние `CONSTANT_Class`, дескрипторы и атрибуты `Exceptions` против базового JAR (`FORBIDDEN_EXTERNAL_CLASSES` + `CONFIRMED_EXTERNAL_CLASSES`) и явно падает на старой сборке 425.
   - `tools/hell_dungeon_check.py` и `tools/harness/HellDungeon425Probe.java` проверяют отсутствие `com/mojang/brigadier/CommandSyntaxException` во всём JAR и вызывают `Class.getDeclaredMethods()` + `onRegisterCommands(...)` на реальном classpath без фантомных стабов.

## Установка и проверка в игре

1. Замените в папке `mods/` файл `settledlands-0.8.0-build425-hell-dungeon.jar` на `settledlands-0.8.0-build426-dungeon-command-fix.jar`.
2. Для появления измерения `settledlands:hell_dungeon` создайте **новый мир** (датапак новых измерений регистрируется при создании мира; на старом сохранении телепортация безопасно отменяется сообщением без вылета).
3. Быстрая проверка в мире:
   - `/settledlands dungeon info` — проверка готовности измерения `settledlands:hell_dungeon`;
   - `/settledlands dungeon enter` — вход в стартовую комнату (`start.slbp`);
   - `/settledlands dungeon leave` — возврат в исходную точку.

## Проверки сборки 426

- `tools/verify_jar.py settledlands-0.8.0-build426-dungeon-command-fix.jar --expect-version 0.8.0-build426-dungeon-command-fix --base settledlands-0.8.0-build425-hell-dungeon.jar` (и против `--base settledlands-0.8.0-build424-teleport.jar`) — **PASS** (`595` классов, `109` миксинов, `0` запрещённых и `0` неподтверждённых внешних классов).
- `tools/hell_dungeon_check.py` (`6 293` JVM-проверки, включая рефлексию `getDeclaredMethods()` и регистрацию команд) — **PASS**.
- `tools/teleport_check.py` (`88` JVM-проверок) — **PASS**.
- Все **41/41** гейты `tools/*_check.py`, `invoke_kind_check.py`, `mixin_clash_check.py` и побайтовая воспроизводимость (`cmp -s`) JAR и Source ZIP — **PASS**.
