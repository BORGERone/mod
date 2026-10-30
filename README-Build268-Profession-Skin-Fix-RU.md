# Build268 — исправление применения Mixin-аннотаций профессии

Build268 собран от точной базы Build262 (`21fc8fa7c861c3652f020ce9bb10bac133ac253675084982e65f16cc734f2531`). Build263 CUTSCENE fade не включён.

## Найденная причина

В логе игры используется SpongePowered Mixin 0.8.7. В compile-time API-stubs `tools/api264` аннотации `@Inject`, `@At`, `@Shadow` и `@Final` ошибочно имели `RetentionPolicy.CLASS`. Поэтому ECJ помещал `@Inject` в `RuntimeInvisibleAnnotations`. Mixin 0.8.7 обнаруживает обработчики через `Annotations.getSingleVisible`, и такой callback не регистрировался. Это совпадает с наблюдениями: старый вручную сформированный HEAD-hook Build266 с `RuntimeVisibleAnnotations` срабатывал, а compiled callbacks Build266/267 — нет; ошибок запуска не было.

Build268 изолированно исправляет compiler stubs в `tools/api268`: эти аннотации теперь runtime-visible. Проверка итоговых class-файлов подтверждает `RuntimeVisibleAnnotations` у трёх `@Inject`-методов и у shadow-полей. Основной функциональный код сохранён: profession dropdown, HEAD/TAIL установка пути профессии, разрешение модели и адресные метки `[SL-PROFESSION-268]`.

## Проверки и ограничение

`tests/gate_build268.py` проверяет детерминированную пересборку, точный diff, наличие runtime-visible Mixin metadata, UI/NBT, текстуры, материализацию модели и JVM-сценарии; `tests/gate_backup268.py` повторяет полный gate из чистой распаковки исходного ZIP. Gate не запускает Minecraft/NeoForge. Этот кандидат ещё требует игрового подтверждения: после сохранения ожидаются метки `early/apply`, затем `resolve ... exists=true`, и видимое изменение скина. Использовать копию мира. Видео и SHA установленного JAR не запрашивать.
