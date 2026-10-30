# Build 272: Single Pursuit Node

## Изменения

Build 271 добавлял **три отдельные ноды** в контекстное меню:
- Преследование — начать: NPC
- Преследование — начать: игрок  
- Преследование — прекратить

**Build 272 заменяет их одной нодой** с кастомным UI прямо на графе:
- **Одна строка** в контекстном меню: «Преследование»
- Нода отрисовывается как **карточка 280×140** (как Stats node) с тремя элементами управления:
  1. **Выпадающий список** (dropdown): «NPC» или «Игрок»
  2. **Поле ввода**: идентификатор NPC (для типа NPC) или ник игрока (для типа Игрок)
  3. **Тумблер**: «Завершить преследование» (вкл/выкл)

## Технические детали

### Совместимость графа
- Нода сохраняет тип `TRIGGER` для совместимости с сериализацией графа
- Action строки остаются теми же:
  - `trigger:pursuit:start:npc:<identifier>` — начать преследование NPC
  - `trigger:pursuit:start:player:<nickname>` — начать преследование игрока
  - `trigger:pursuit:stop` — прекратить преследование
- Серверная логика `NpcPursuit269` не изменилась

### UI реализация
- `PursuitGraphNode272.java` — клиентский код для отрисовки карточки и обработки ввода
- `NpcPursuitContextMenuMixin272.java` — одна строка в меню (вместо трёх из Build271)
- `NpcPursuitGraphUiMixin272.java` — кастомная отрисовка ноды + обработка кликов/ввода
- `NpcPursuitCanvasMixin272.java` — переопределение hit/visible/focus для pursuit нод

### Взаимодействие
- Клик по dropdown открывает выпадающий список с выбором типа цели
- Клик по полю ввода активирует режим редактирования (подсветка рамки)
- Ввод символов добавляет текст, Backspace удаляет последний символ, Enter завершает редактирование
- Клик по тумблеру переключает флаг "Завершить преследование"
- Все изменения сохраняются через `settledlands$remember()` / `settledlands$changed()`

## Сборка

Требуется Java 21 и ECJ compiler.

```bash
cd build272-workspace
python3 tools/patch_pursuit272.py --java /path/to/java --base baseline271.jar --out settledlands-0.8.0-build272-pursuit-single-node.jar
```

## Статус

✅ Исходный код готов  
✅ Patch-скрипт создан  
⏳ Требуется Java 21 для компиляции bytecode  
⏳ Gates не запущены (нет Java)  

## Файлы

- `src/build272/` — исходный код Build 272
- `tools/patch_pursuit272.py` — скрипт сборки
- `baseline271.jar` — базовый JAR для патча
- `SettledLands-Build272-Pursuit-SingleNode-Source.tar.gz` — архив исходников

## Следующие шаги

1. Установить Java 21
2. Запустить `patch_pursuit272.py`
3. Пройти gates (validate_jar.py, jvm_verify.py)
4. Упаковать финальный JAR и Source ZIP
5. Загрузить на Drive и GitHub
6. Создать PR в ветку `arena/3069fd2a-mod`
