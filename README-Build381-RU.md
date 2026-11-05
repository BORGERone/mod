# Settled Lands — сборка 381 (двери наконец открываются)

Файл мода: `settledlands-0.8.0-build381-door-open.jar`
Версия в игре: `0.8.0-build381-door-open`
Собрано из базы: `settledlands-0.8.0-build380-door-hack-watch.jar`

## Нашлось по твоему логу

```
door: door_hack arrived from BORGER: key=minecraft:overworld@43,65,-251
door: a solved panel could not be applied: java.lang.NoSuchMethodError:
  'net.minecraft.world.level.block.state.BlockState
   net.minecraft.world.level.block.state.BlockState.setValue(
     net.minecraft.world.level.block.state.properties.Property, java.lang.Comparable)'
```

Пакет доходил, панель собиралась — падал сам **вызов, который пишет двери**. Причина:

* в 1.21.1 метод `setValue` объявлен не в `BlockState`, а в
  `net.minecraft.world.level.block.state.StateHolder<O, S>` и выглядит так:
  `<T extends Comparable<T>, V extends T> S setValue(Property<T> property, V value)`;
* `S` — без ограничения, поэтому **стёртый тип возврата — `Object`**, а не `BlockState`;
* моя заготовка сигнатуры объявляла `BlockState setValue(Property<T>, T)`, и компилятор выпускал вызов
  `setValue(Property, Comparable)BlockState` — **такого метода в игре нет**. Каждое открытие двери
  падало с `NoSuchMethodError`.

**Это касалось не только взлома.** Через `Doors368.setOpen` работают три вещи, и все три падали, потому
что исключение глушилось своим же `try/catch`:

1. **взлом** (`DoorHack377` открывает дверь);
2. **НПС-двери** из сборки 368 — проводник открывал и закрывал двери (это твоё старое требование);
3. **сторож** из 371, который закрывает запертую дверь, если что-то её открыло.

Исправлено в одном месте: заготовка объявлена так, как метод реально опубликован (сырые `Property`,
`Comparable`, возврат `Object`), а результат проверяется через `instanceof BlockState`. Полученный вызов
теперь `setValue(Property, Comparable)Object` — ровно то, что есть в игре.

## Что из этого следует для тебя

* Взлом открывает дверь.
* Проводник НПС **открывает и закрывает** двери на ходу — это тоже должно заработать впервые.
* Запертая дверь, которую что-то открыло, снова закрывается сторожем.

## Что остаётся как было

* Буквы `Q W E R T Y U I O P`, 4–10 кнопок, все зажаты одновременно; каждые 5 с одна клавиша меняется,
  ввод не сбрасывается; `Esc` закрывает панель.
* Запертую дверь нельзя сломать, ключ ставит/снимает замок, обычные двери мгновенные.
* Отчёты в логе и сообщения в action bar из 380 остались: если что-то опять пойдёт не так, будет видно,
  где именно.

## Проверки перед выпуском

Все гейты зелёные, `verify_jar --base 380` — PASS (0 удалённых членов, 0 новых внешних ссылок).
`door_lock_check` получил правило, которое проверяет **дескриптор** вызова
`BlockState#setValue` в собранном jar, а не имя: имя было верным все десять сборок, а форма — нет. На
380-м jar это правило падает с текстом ошибки из твоего лога. Запись в белом списке `check_mixin_class`
тоже исправлена на `(Property, Comparable)Object` — со ссылкой на javadoc `StateHolder` и на этот лог.
