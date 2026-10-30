# Build271: runtime crash и graph node API

Build270 `latest-10.log` выявил: (1) `ScreenAccessorPursuit269` abstract invoker стал abstract method в class `Screen` без реализации, вызывая `AbstractMethodError` при попытке зарегистрировать dropdown; (2) `NpcPursuit269.saveNpcData` ссылался на `CompoundTag.remove(Ljava/lang/String;)Ljava/lang/Object;`, поскольку compile-only stub был неверен, тогда как NBT API 1.21.1 возвращает Tag. При сохранении сущности это приводило к `NoSuchMethodError`.

Build271 полностью удаляет Screen accessor и старый dropdown; отдельные menu rows создают видимые pursuit action nodes, которые остаются обычными Trigger type nodes с `action` строками pursuit, сохраняя graph serialization/execution. Inactive NBT marker теперь явно сохраняется false, без вызова remove.

Gate проверяет отсутствие Screen accessor class/config, runtime annotation metadata для mouse/menu injections и ModifyConstant, три row-action mapping fixtures, exact JAR diff, JVM links и отсутствие CompoundTag.remove method ref. Ни один из этих checks не является реальным NeoForge/Minecraft запуском.
