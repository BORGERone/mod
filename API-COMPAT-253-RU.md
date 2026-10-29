# Совместимость API в сборке 253

## 1. Целевая платформа
- Minecraft `1.21.1`, NeoForge `21.1.x`, Java `21` (`major=65`).

## 2. Проверенные контракты Minecraft 1.21.1

### 2.1. `DataComponents.ENTITY_DATA` и `CustomData.CODEC_WITH_ID`
- В `net.minecraft.world.item.component.CustomData` (MC 1.21.1):
  - сетевой `CustomData.STREAM_CODEC` сериализует `CompoundTag` без проверки поля `"id"`;
  - постоянный `CustomData.CODEC_WITH_ID` (используется `DataComponents.ENTITY_DATA` при любом `ItemStack.save(...)`) проверяет `tag.getUnsafe().contains("id", 8)` (`8` = `Tag.TAG_STRING`) и при отсутствии возвращает `DataResult.error(() -> "Missing id for entity in: " + tag)`.
- В `SpawnEggItem.getType(ItemStack)` чтение типа сущности из `DataComponents.ENTITY_DATA` также выполняется по строковому полю `"id"` (`BuiltInRegistries.ENTITY_TYPE.byNameCodec().fieldOf("id")`).
- В `NpcCopyEggFix253.withEntityId(GuideNpc, CompoundTag)` записывается:
  - `"settledlands:guide"` для `GuideNpc` (а также безопасный откат при случайном выборе `GuideCorpseNpc` без выдачи `CoffinItem`),
  - `"settledlands:guide_trigger"` (с предметом `NpcContent.triggerEgg()`) для `GuideTriggerNpc`.

### 2.2. `BbModel.renderActorHeldItem`
- Удалён только блок смещений `184..228` (`entity.isBlocking()` + `stack.getUseAnimation() == UseAnim.BLOCK` → `Axis.XP.rotationDegrees(40.0F)` + `translate(0.0F, 0.15F, -0.05F)`).
- Стек операндов пуст на `184` и `228`; замена `184..228` на `nop` сохраняет неизменными все смещения переходов, `StackMapTable`, `LineNumberTable` и оба вызова `PlayerMotionBridge` (`itemPosition` на `152` и `renderHeldItem` на `264`).
- Все классы 252/251 (`FirstPersonSwordBridge252`, `FirstPersonPalEmfAccess252`, `PlayerBodyRenderer`, `PlayerMotionBridge`, `HackSlashAccess`, `FreshEmfAccess`, `PossessionSystem`, `PossessionLoginSync251`, `GuideNpc`, `GuideRenderer`) сохранены байт-в-байт.
