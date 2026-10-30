# SettledLands 0.8.0 — Build 274 «Pursuit hitPort fix»

## Причина вылета при создании ноды

```
Caused by: org.spongepowered.asm.mixin.injection.callback.CancellationException:
    The call hitPort is not cancellable.
    at CallbackInfo.cancel(CallbackInfo.java:101)
    at CallbackInfoReturnable.setReturnValue(CallbackInfoReturnable.java:106)
    at NpcGraphCanvas.handler$...$settledlands$pursuitHitPort(...)
    at NpcGraphCanvas.hitPort(NpcGraphCanvas.java)
    at NpcDialogueGraphScreen.wires(NpcDialogueGraphScreen.java:2664)
    at NpcDialogueGraphScreen.renderBackground(NpcDialogueGraphScreen.java:2243)
```

`NpcPursuitCanvasMixin272#settledlands$pursuitHitPort` объявлен как
`@Inject(at = @At("TAIL"))` **без** `cancellable = true`, но внутри вызывает
`cir.setReturnValue(-1)`. В Mixin `setReturnValue()` = отмена метода, и для
не-cancellable инжектора это `CancellationException`. Срабатывает ровно тогда,
когда нода преследования впервые оказывается под курсором — то есть сразу
после её создания (рендер `wires()` каждый кадр опрашивает `hitPort`).

Ошибка унаследована из Build 272 (в 273 этот метод не менялся).

## Что изменено в Build 274

Диф по записям JAR: **изменено 3, добавлено 0, удалено 0**.

* `dev/settledlands/mixin/NpcPursuitCanvasMixin272.class` — в аннотацию
  `@Inject` метода `settledlands$pursuitHitPort` добавлена пара
  `cancellable = true` (3 → 4 пары). Байткод метода (`Code`) не изменён —
  проверено побайтово.
* `META-INF/neoforge.mods.toml` — `version = 0.8.0-build274-pursuit-hitport-fix`,
  строка Build 274 первой в `description`.
* `settledlands-patch-info.txt` — описание сборки.

Остальные 437 записей байт в байт из Build 273.

## Gates

```
validate_jar: OK      440 записей, toml/JSON/CRC
jvm_verify: OK        392 класса, опкоды, CP-ссылки, ветки, exception-таблицы
check_mixin_class: OK 32 mixin-класса
verify_jar: PASS
```

В `check_mixin_class.py` добавлена фаза, которая ловит именно этот дефект:
если обработчик `@Inject` вызывает `cancel()` / `setReturnValue()`, а в
аннотации нет `cancellable = true` — gate падает. На Build 273 он показывал
одно попадание (этот самый `hitPort`), на 274 — чисто. Ранее этот класс
ошибок gates не проверяли, поэтому 272/273 и проскочили.

## Что проверить в игре

1. Открыть редактор графа.
2. ПКМ по пустому месту → «Преследование» → создать ноду.
3. Навести курсор на новую карточку, подвигать, переключить dropdown и
   тумблер, Enter/Esc.

Ввод текста в поле всё ещё недоступен (см. `README-Build273-RU.md`): у
`NpcDialogueGraphScreen` нет своего `charTyped`, это правится только сборкой
из исходников.

## Исходники

`build274-src/` — оба mixin-а с исправлением (`cancellable = true` с
комментарием) + `patch_build274.py`, которым собран JAR.
