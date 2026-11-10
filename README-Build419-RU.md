# Сборка 419 — ESC сразу же закрывает меню взлома (`HackPanel377`)

**Версия:** `0.8.0-build419-esc-immediate-close`
**База:** 418 (`settledlands-0.8.0-build418-abort-panel-key-guard.jar`)
**Файл:** `settledlands-0.8.0-build419-esc-immediate-close.jar` (613 записей)
**sha256:** `43e7d97312e08ca0dc591a9bf11a6831197b5958ae316e4d26b71c05109c7036`

---

## Что было не так в сборке 418 (и 387–417)

В меню взлома (`HackPanel377`) после перехода в состояние **«ВЗЛОМ ПРИНЯТ»** (`solved`) или **«ВЗЛОМ ПРЕРВАН»** (`aborted`) одиночное нажатие **`ESC`** блокировалось — панель требовала **удерживать `ESC` 800 мс** («ESC удержи ~1 с, если клавиша залипла»). Из-за этого игрок не мог сразу закрыть меню нажатием `ESC`.

---

## Что сделано в сборке 419

1. **`HackPanel377` (`build303-src/src/dev/settledlands/door/HackPanel377.java`):**
   * `keyPressed(256, ...)` и `keyReleased(256, ...)` теперь передают `ESC` напрямую в `Screen#keyPressed` / `Screen#keyReleased` **без проверки `solved`/`aborted`** и без задержки: первое же нажатие `ESC` закрывает меню взлома **мгновенно** во всех состояниях (во время взлома, после принятия взлома и после срыва свидетелем).
   * Удалён 800-мс таймер удержания (`ESC_HOLD_MS`, `escHeldSince`, `escHeldTick()`, `settledlands$escDown()`, `settledlands$escHeld()`).
   * Подсказка на экране обновлена на **«ESC - закрыть меню сразу»**.
   * При этом защита от утечки остальных зажатых клавиш (`allReleased()` + пауза `450 мс` при срыве) **сохранена**, когда игрок не нажимает `ESC`: если вы просто отпускаете клавиши (`P`, `W`, `Q` и др.), панель держит их до отпускания, чтобы не открыть «Социальные взаимодействия».

2. **Расширены гейты и проба:**
   * `tools/panel_release_check.py`: проверяет, что удержанные клавиши удерживают панель через `pressed`/`allReleased()`/`stillDown()`, а `ESC` в `keyPressed` не привязан к `solved`/`aborted` и в классе нет таймера `escHeldSince`/`escHeldTick`.
   * `tools/illegal_hack_check.py` + `tools/harness/AbortPanel418Probe.java`: живая JVM-проба проверяет удержание `P`/`W`/`Q` при срыве `X|`, защитное окно 450 мс и **мгновенное закрытие по одиночному `ESC`** во всех трёх состояниях (`aborted`, `solved`, и во время взлома с отправкой `!cancel`).

---

## Проверка

* **37 из 37** гейтов (`tools/*_check.py`): **PASS** (сборка 418 на обновлённых `panel_release_check.py` и `illegal_hack_check.py` закономерно падает).
* `check_mixin_class.py --base settledlands-0.8.0-build418-abort-panel-key-guard.jar`: **OK** (104 миксина, `0` новых внешних ссылок).
* `verify_jar.py --base settledlands-0.8.0-build418-abort-panel-key-guard.jar`: **PASS** (`jvm_verify` 561 класс, `dangling_refs` 0 оборванных ссылок).
* **В игре пока не проверено.**
