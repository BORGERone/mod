# Сборка 421 — исправление якоря: графы не копировались из-за `IncompatibleClassChangeError`

**Версия:** `0.8.0-build421-anchor-registry-fix`
**База:** 420 (`settledlands-0.8.0-build420-graph-anchor.jar`)
**Файл:** `settledlands-0.8.0-build421-anchor-registry-fix.jar` (625 записей)
**sha256:** `a9e90656a1babcabf62802b8d41385d94d0ce9b7a8ee8435ad6f5cf73fe246f9`

---

## Что было не так

В сборке 420 якорь не работал вообще: маркер ни разу не был поставлен, поэтому графы никуда не уезжали —
«ГРАФОВ НЕТ». Автор прислал `latest.log`, и в нём одна строка объясняет всё:

```
[Server thread/WARN] [SettledLands/Anchor420/]: anchor: the marker could not be placed:
java.lang.IncompatibleClassChangeError: Found interface net.minecraft.core.DefaultedRegistry, but class was expected
```

**Причина — моя.** В игре `net.minecraft.core.DefaultedRegistry` — **интерфейс**. Для компиляции в 420 я
объявил его как **класс** (в заглушке `build303-src/stubs420-src/.../DefaultedRegistry.java`). Дескрипторы
при этом совпали идеально, поэтому ни одна проверка 420 не возразила — но изменился **вид вызова**:

| | вызов получения реестра |
|---|---|
| в базе мода (уже работает в игре) | `invokeinterface DefaultedRegistry.getKey(...)` |
| в сборке 420 | `invokevirtual DefaultedRegistry.getKey(...)` |

`invokevirtual` на интерфейсе — это `IncompatibleClassChangeError` в момент первого исполнения, то есть
при попытке поставить маркер. Ошибка не компиляционная и не в логике: правило якоря (повороты, отпечаток,
раскладка) было верным и осталось **побайтово тем же**.

**Вторая, независимая ошибка того же происхождения:** заглушка `DefaultedRegistry` была ещё и внутренне
противоречива (класс объявлял методы, пришедшие из интерфейса `Registry`) — теперь `DefaultedRegistry`
объявлен интерфейсом, как в игре, а поля `BuiltInRegistries.BLOCK` / `ENTITY_TYPE` — `null` (зеркало
существует только для компилятора; в jar значения не попадают, там только чтение настоящих полей игры).

---

## Что сделано

1. `build303-src/stubs420-src/net/minecraft/core/DefaultedRegistry.java` — теперь **интерфейс**
   `interface DefaultedRegistry<T> extends Registry<T>`.
2. `.../registries/BuiltInRegistries.java` — поля `BLOCK` и `ENTITY_TYPE` типизированы `DefaultedRegistry`
   и инициализированы `null` (зеркало для компилятора; `new` на интерфейсе невозможен и не нужен).
3. Пересобраны `NpcGraphAnchorFiles420` и `NpcGraphAnchor420`; остальные 10 классов якоря **побайтово
   совпадают с 420** — это проверяется патчем.
4. Две диагностические строки в лог, чтобы следующий тест сам говорил, что произошло:
   * при установке маркера — `anchor: a marker was placed at <pos> - this building's graphs can now travel`;
   * при раскладке — `anchor: pasted at <pos> - ... (<n> entries carried, turn <t>)`.

### Новый гейт против всего этого класса ошибок

`tools/invoke_kind_check.py` — сверяет **вид** каждого вызова (`invokevirtual` / `invokeinterface` /
`invokestatic` / `invokespecial`) с тем, каким его делает база мода:

* **5638** целей вызова проверено;
* сборка **420 на нём падает** ровно на той строке, что в логе автора:
  `DefaultedRegistry.getKey(...)` и `get(...)` — `invokevirtual`, а база зовёт `invokeinterface`;
* сборка **421 проходит**;
* три вызова, которых база не делает вовсе (`Entity.moveTo(DDD)`, `Entity.setYRot(F)`,
  `BlockState.isAir()`), внесены в белый список с обоснованием, что все три — **классы**, то есть
  `invokevirtual` для них верен.

---

## Проверка

* **39 из 39** гейтов (`tools/*_check.py`) — PASS.
* `tools/invoke_kind_check.py --base 420` — **OK** (420 — FAIL, как и должно быть).
* `check_mixin_class.py --base 420` — OK (104 миксина, **0** новых внешних ссылок).
* `verify_jar.py --base 420` — **PASS** (0 оборванных ссылок, 0 удалённых членов).
* Патч воспроизводим: повторная сборка из 420 даёт тот же sha256.
* **В игре пока не проверено.**

---

## Как проверить в игре

1. Постройка с графами на блоке, двери и сундуке. Постоять рядом ~2 с.
2. В `latest.log` должна появиться строка `anchor: a marker was placed at ...`. Если её **нет** — маркер не
   ставится, и дальше идти смысла нет: пришлите лог, там будет причина (например, `guide_trigger` не
   зарегистрирован).
3. В Axiom скопировать постройку **с включённым `Copy Entities`**, вставить в стороне (можно `Ctrl+R` /
   `Ctrl+F`). Постоять рядом ~2 с.
4. В логе — `anchor: pasted at ... (N entries carried, turn T)`. Затем `/settledlands anchor list` и
   проверка жезлом: блок, дверь (с замками) и сундук редактируются и работают по отдельности.

Если строка `pasted at` есть, а графы «не работают» — важен `turn` в ней: он говорит, какой поворот выбран
по отпечатку блоков (0 — без поворота, 1–3 — четверти, 4–7 — с зеркалом).
