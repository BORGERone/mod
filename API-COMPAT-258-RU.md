# Build 258 — исправление JVM ABI для Minecraft 1.21.1

## Диагноз из игрового crash log

Приложенный `latest-2.log` (SHA-256 `9a4bb92e96813944bddebbe335317d9609eae734e1a85c39b97eae4bc349291a`) сообщает Minecraft `1.21.1`, NeoForge `21.1.253`, FML `4.0.45`, Java `21.0.7`. Фатальная причина:

```text
java.lang.NoSuchMethodError:
  'void net.minecraft.world.entity.ai.attributes.AttributeInstance.removeModifier(ResourceLocation)'
  at NpcAutoAiEngine255.setModifier
  at NpcAutoAiEngine255.syncStatsFromGraph
  at NpcAutoAiEngine255.syncStats
  at GuideNpc.customServerAiStep
```

Это серверный crash `Ticking entity` на первом AI-тике GuideNpc. Предшествующие ошибки моделей, рецептов, Sable/JEI в логе не являются причиной указанного стека.

В Minecraft 1.21.1 реальная сигнатура — `boolean removeModifier(ResourceLocation)`. Build 257 compile-time API silhouette ошибочно описывал её как `void`; структурный валидатор и фикстура с тем же ошибочным stub не могли обнаружить ABI несовпадение. Источник метода проверен по Java source mapping для 1.21.1 и сопоставлен с crash message. Исправляемая ошибка относится к моему build-257 тестовому API-стенду — это не сторонний мод и не ошибка сохранения мира.

## Состав build 258

Точная база — `settledlands-0.8.0-build257-calm-ai-stats.jar`, SHA `93013d8380263c3fdc4f5245a6ce4178f30bcda61478081bd9d10c93e42c45b1`. Меняется ровно три записи:

- `dev/settledlands/npc/NpcAutoAiEngine255.class` — вызов имеет дескриптор `(Lnet/minecraft/resources/ResourceLocation;)Z`, возвращаемый boolean снимается со стека;
- `META-INF/neoforge.mods.toml` — версия и краткая запись hotfix;
- `settledlands-patch-info.txt` — описание hotfix.

`GuideNpc.class` и все остальные 420 записей совпадают с базой 257 побайтно. Поведение AI и графовых значений не менялось.

## Повторное тестирование

`tests/gate_build258.py` проверяет точный descriptor против точной 1.21.1 API-сигнатуры, deterministic rebuild, три-entry diff, Java 21, реальные engine bytecode на fixture с boolean return, профильные значения 10/10/10 (удаление нулевых modifiers), обновления и возврат к дефолту, inherited Auto-AI 255 сценарии и 4 verifier-valid mutants.

Это фиксирует причину из crash log и подтверждает исправление на JVM-фике, но не является повторным полноценным запуском пользовательской modpack-сборки. Остальные ошибки из её лога отдельно не диагностировались, так как они не стоят в стеке фатального вылета.
