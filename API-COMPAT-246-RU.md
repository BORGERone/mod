# Аудит API сборки 246

Компиляция новых классов использует локальные compile-only силуэты
`tools/gen_api246.py`; ни один силуэт не попадает в JAR. Подтверждены по
публичным исходникам MCP 1.21 и NeoForge/FML ветки 1.21.1 следующие особенно
важные сигнатуры/семантика:

- `TargetingConditions.test(LivingEntity,LivingEntity)Z`, Mixin HEAD и
  cancellable; `LivingChangeTargetEvent` разрешает заменить новую цель на null;
  `Brain.getMemoryInternal` возвращает null для незарегистрированной памяти.
- `MobEffect.addAttributeModifier(Holder,ResourceLocation,double,Operation)`
  и `addAttributeModifiers(AttributeMap,int)`; `Attributes.GRAVITY` и
  `ADD_MULTIPLIED_TOTAL`. Для постоянной половины используется уровень 0.
- `LivingIncomingDamageEvent.getSource/getAmount/setAmount`, серверный владелец
  урона через `DamageSource.getEntity`; умножение до стандартных поглощений.
- `AttributeInstance` с `getAttribute/getModifiers/getModifier`, постоянными
  модификаторами и операциями; `Holder.is`, `Attribute.sanitizeValue`, состояние
  обновляется через `LivingEntity.onAttributeUpdated(Holder)`.
- `PlayerInteractEvent` и два события ПКМ, `SUCCESS` при перехвате;
  `IPayloadContext.enqueueWork`, `PayloadRegistrar.playToClient/playToServer`,
  сигнатуры записи `FriendlyByteBuf` и GUI `GuiGraphics`/`Screen`.
- `EventBusSubscriber` автоматически выбирает шину по типу события и по
  умолчанию работает на обеих физических сторонах. События 246 подписаны
  аннотациями; старые пути подключены через проверенные мосты в байткоде.

Это не проверка запуска NeoForge/Minecraft и не подтверждение реального
применения Mixin: сигнатуры реконструировались, доступны не все бинарные
зависимости, и есть риск несовпадения/побочных эффектов в конкретном наборе
модов. JVM-фикстуры моделируют API и поведение, а не выполняют игру.
