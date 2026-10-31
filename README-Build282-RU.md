# SettledLands 0.8.0 — Build 282 «Тело вселённого снова анимируется»

## Что было

Анимации пропадали только у тела, в которое вселился игрок. Лог это подтверждает:

```
[SettledLands/FreshMotion251] Fresh Animations Player Extension is unavailable
or disabled for this player; player is shown in a neutral pose to avoid conflicting clips.
```

В сборке нет ресурспака **Fresh Animations: Player Extension** — в списке
загруженных паков его нет (vanilla, mod-паки, `moonlight:merged_pack`,
`quark-emote-pack`). Версии при этом совпадают точно (Touch'n Motion 1.0.4,
EMF 3.3.9, ETF 7.2.4, EMF Compat Core 2.3.2), мост запускается, но EMF не может
подготовить прогон Fresh Animations без пака.

Дальше `PlayerMotionBridge#setup` сознательно уводил тело в «нейтральную позу»:

```java
if (!applied) {
    cancel(scope);
    if (freshPath && !disabled) {
        model.getRootPart().getAllParts().forEach(ModelPart::resetPose);   // заморожено
    } else {
        model.setupAnimRaw(...);                                           // анимировано
    }
}
```

Правило писалось против конфликта встроенных клипов с **работающим** FA. Когда
FA не запускается вообще, конфликтовать не с чем — тело просто стоит в rest-позе.
Это и есть баг. NPC при этом живы, потому что у `NpcMotionBridge256` свой откат
на встроенную актёрскую анимацию.

## Что сделано

Ветка «нейтральной позы» больше не выполняется. Одна инструкция в `setup`
переписана на месте:

```
1012: 15 11      iload 17 (freshPath)   ->   03 00   iconst_0 ; nop
1014: 99 00 1d   ifeq -> 1043                  (ifeq остаётся и всегда переходит)
```

Длина та же, эффект на стек тот же, границы инструкций и все кадры
`StackMapTable` сохраняют смещения — класс остаётся верифицируемым. Меняется
только случай «FA не применился»: тело рисуется встроенной актёрской анимацией
(покой, ходьба, бег, разговор) — тем же откатом, что был до 251.

Где FA и Touch'n Motion действительно работают, `setup` выходит раньше, на
проверке `if (!applied)`, — для таких сборок не меняется ничего.

Текст предупреждения исправлен на месте (та же длина, выровнен пробелами): он
больше не обещает нейтральную позу.

## Диф по записям JAR

**изменено 3, добавлено 0, удалено 0** (443 → 443):

```
META-INF/neoforge.mods.toml
dev/settledlands/model/PlayerMotionBridge.class
settledlands-patch-info.txt
```

## Как собрано

```
python3 tools/patch_build282.py settledlands-0.8.0-build281-pursuit-no-los-crash.jar
```

Патчер сам находит точку правки (`iload` перед `ifeq` в `setup`, за которым
идут `getstatic disabled; ifne` и вызов `BbModel.setupAnimRaw`), падает с
внятной ошибкой, если шаблон не совпал, и перепроверяет результат: длина кода,
границы инструкций, хвост атрибута (exception table и стековые карты) не
изменились.

## Gates

```
validate_jar: OK      443 записи, 395 классов, toml/JSON/CRC
jvm_verify: OK        395 классов
check_mixin_class: OK 34 mixin-класса, новых внешних API 0
verify_jar: PASS
```

## Что проверить в игре

1. Вселиться в NPC — тело двигается: покой, ходьба, бег, разговор.
2. Сторонний вид: тело в третьем лице анимируется так же.
3. NPC вокруг анимируются как раньше.
4. Удар/использование предмета рукой не «залипают».
5. В логе вместо «player is shown in a neutral pose» — строка «the embedded
   actor animation is used instead».
