# Build270: совместимость Mixin accessor

Build269 объявлял `ScreenAccessorPursuit269` как Java-интерфейс с `@Mixin(targets = "net.minecraft.client.gui.screens.Screen")`. `Screen` является классом; пользовательский NeoForge log зафиксировал отказ Mixin 0.8.7 на PREPARE с `@Mixin target type mismatch` до старта клиента.

В Build270 accessor — `public abstract class` с тем же target и публичным `@Invoker("addRenderableWidget")`. UI mixin не приводит screen к accessor-интерфейсу, а получает добавленный метод через `Class.getMethod("settledlands$addRenderableWidget", AbstractWidget.class)` и вызывает через reflection. Так преобразованный `Screen` предоставляет invoker без неверного interface-cast.

Gate проверяет JVM class access flags (ACC_INTERFACE отсутствует, ACC_ABSTRACT присутствует), наличие target имени `Screen`, runtime-retained `@Invoker` method annotation, отсутствие интерфейсного cast в source, exact diff и deterministic rebuild. Это не прогон настоящего Mixin transformer/NeoForge/Minecraft: пользовательский запуск остаётся обязательным.
